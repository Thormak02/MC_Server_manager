import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, RLock, Thread
from time import sleep

from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.session import SessionLocal
from app.models.server import Server
from app.services import audit_service
from app.services.console_service import console_service


@dataclass
class ManagedProcess:
    process: subprocess.Popen[str]
    log_file_path: str


_PROCESS_REGISTRY: dict[int, ManagedProcess] = {}
_PROCESS_LOCK = RLock()

_PENDING_RESTARTS: dict[int, Event] = {}
_RESTART_LOCK = RLock()

_INGAME_RESTART_PATTERNS = [
    re.compile(r"issued server command:\s*/restart\b", re.IGNORECASE),
    re.compile(r"executed command:\s*/restart\b", re.IGNORECASE),
]


def _build_creation_flags() -> int:
    flags = 0
    if hasattr(subprocess, "CREATE_NEW_PROCESS_GROUP"):
        flags |= subprocess.CREATE_NEW_PROCESS_GROUP  # type: ignore[attr-defined]
    if hasattr(subprocess, "CREATE_NO_WINDOW"):
        flags |= subprocess.CREATE_NO_WINDOW  # type: ignore[attr-defined]
    return flags


def _command_for_server(server: Server) -> list[str]:
    if server.start_mode == "command":
        if not server.start_command:
            raise ValueError("Startbefehl fehlt.")
        return ["cmd", "/c", server.start_command]

    if server.start_mode == "bat":
        start_bat_path = server.start_bat_path or str(Path(server.base_path) / "start.bat")
        return ["cmd", "/c", str(Path(start_bat_path))]

    raise ValueError(f"Unbekannter Startmodus: {server.start_mode}")


def _server_log_dir(server_id: int) -> Path:
    settings = get_settings()
    log_dir = settings.data_dir / "logs" / f"server_{server_id}"
    log_dir.mkdir(parents=True, exist_ok=True)
    return log_dir


def _create_session_log_file(server_id: int) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return _server_log_dir(server_id) / f"session-{timestamp}.log"


def _cleanup_process_registry(server_id: int) -> None:
    with _PROCESS_LOCK:
        _PROCESS_REGISTRY.pop(server_id, None)


def _cancel_pending_restart(server_id: int) -> None:
    with _RESTART_LOCK:
        cancel_event = _PENDING_RESTARTS.pop(server_id, None)
    if cancel_event:
        cancel_event.set()


def _mark_server_stopped(server_id: int, exit_code: int | None) -> None:
    with SessionLocal() as db:
        server = db.get(Server, server_id)
        if not server:
            return
        if server.status != "stopped":
            server.status = "stopped"
            db.add(server)
            db.commit()
        audit_service.log_action(
            db,
            action="server.process_exit",
            server_id=server_id,
            details=f"exit_code={exit_code}",
        )


def _looks_like_ingame_restart(line: str) -> bool:
    text = line.strip()
    for pattern in _INGAME_RESTART_PATTERNS:
        if pattern.search(text):
            return True
    return False


def _stream_output(server_id: int, process: subprocess.Popen[str], log_file_path: Path) -> None:
    try:
        with log_file_path.open("a", encoding="utf-8", errors="ignore") as log_file:
            stdout = process.stdout
            if stdout:
                for line in iter(stdout.readline, ""):
                    if not line:
                        break
                    log_file.write(line)
                    log_file.flush()
                    console_service.append_output(server_id, line)
                    if _looks_like_ingame_restart(line):
                        request_restart_by_server_id(
                            server_id,
                            initiated_by_user_id=None,
                            delay_seconds=get_settings().ingame_restart_delay_seconds,
                            warning_message=get_settings().ingame_restart_warning_message,
                            source="ingame_command",
                        )
    finally:
        try:
            exit_code = process.wait(timeout=1.0)
        except Exception:
            exit_code = process.poll()
        _cleanup_process_registry(server_id)
        _mark_server_stopped(server_id, exit_code)


def is_running(server_id: int) -> bool:
    with _PROCESS_LOCK:
        managed = _PROCESS_REGISTRY.get(server_id)
        if not managed:
            return False
        return managed.process.poll() is None


def refresh_runtime_states(db: Session, servers: list[Server]) -> None:
    changed = False
    for server in servers:
        currently_running = is_running(server.id)
        target_status = "running" if currently_running else "stopped"
        if server.status != target_status:
            server.status = target_status
            db.add(server)
            changed = True

        if not currently_running:
            _cleanup_process_registry(server.id)

    if changed:
        db.commit()


def start_server(db: Session, server: Server, initiated_by_user_id: int | None) -> tuple[bool, str]:
    if is_running(server.id):
        return False, "Server laeuft bereits."

    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists() or not base_path.is_dir():
        return False, "Serverordner existiert nicht."

    try:
        command = _command_for_server(server)
    except ValueError as exc:
        return False, str(exc)

    log_file_path = _create_session_log_file(server.id)
    process = subprocess.Popen(
        command,
        cwd=str(base_path),
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        creationflags=_build_creation_flags(),
        bufsize=1,
    )

    with _PROCESS_LOCK:
        _PROCESS_REGISTRY[server.id] = ManagedProcess(
            process=process,
            log_file_path=str(log_file_path),
        )

    stream_thread = Thread(
        target=_stream_output,
        args=(server.id, process, log_file_path),
        daemon=True,
        name=f"server-{server.id}-stdout",
    )
    stream_thread.start()

    server.status = "running"
    db.add(server)
    db.commit()

    audit_service.log_action(
        db,
        action="server.start",
        user_id=initiated_by_user_id,
        server_id=server.id,
        details=f"pid={process.pid}",
    )
    console_service.append_output(server.id, "Serverprozess gestartet.")
    return True, "Server gestartet."


def _send_server_message(server: Server, message: str) -> None:
    if not message.strip():
        return
    with SessionLocal() as db:
        send_console_command(db, server, f"say {message}", initiated_by_user_id=None)


def _warn_checkpoints(delay_seconds: int) -> list[int]:
    markers = [300, 120, 60, 30, 10, 5, 4, 3, 2, 1]
    return [value for value in markers if 0 < value < delay_seconds]


def _claim_restart_slot(server_id: int) -> Event | None:
    with _RESTART_LOCK:
        if server_id in _PENDING_RESTARTS:
            return None
        event = Event()
        _PENDING_RESTARTS[server_id] = event
        return event


def _release_restart_slot(server_id: int, token: Event) -> None:
    with _RESTART_LOCK:
        current = _PENDING_RESTARTS.get(server_id)
        if current is token:
            _PENDING_RESTARTS.pop(server_id, None)


def _restart_worker(
    server_id: int,
    token: Event,
    initiated_by_user_id: int | None,
    delay_seconds: int,
    warning_message: str,
    source: str,
) -> None:
    try:
        with SessionLocal() as db:
            server = db.get(Server, server_id)
            if server is None:
                return

            if delay_seconds > 0:
                _send_server_message(server, warning_message.format(seconds=delay_seconds))
                checkpoints = set(_warn_checkpoints(delay_seconds))
                for seconds_left in range(delay_seconds - 1, -1, -1):
                    if token.is_set():
                        return
                    sleep(1)
                    if seconds_left in checkpoints:
                        _send_server_message(server, warning_message.format(seconds=seconds_left))
            elif warning_message.strip():
                _send_server_message(server, warning_message.format(seconds=0))

        if token.is_set():
            return

        with SessionLocal() as db:
            server = db.get(Server, server_id)
            if server is None:
                return
            restart_server(db, server, initiated_by_user_id)
            audit_service.log_action(
                db,
                action="server.restart_requested",
                user_id=initiated_by_user_id,
                server_id=server_id,
                details=f"source={source} delay={delay_seconds}",
            )
    finally:
        _release_restart_slot(server_id, token)


def queue_restart(
    db: Session,
    server: Server,
    initiated_by_user_id: int | None,
    *,
    delay_seconds: int = 0,
    warning_message: str | None = None,
    source: str = "manual",
) -> tuple[bool, str]:
    safe_delay = max(0, int(delay_seconds))
    template = (warning_message or "").strip()
    if safe_delay > 0 and not template:
        template = "Server restartet in {seconds} Sekunden."

    token = _claim_restart_slot(server.id)
    if token is None:
        return False, "Es ist bereits ein Neustart geplant."

    worker = Thread(
        target=_restart_worker,
        args=(server.id, token, initiated_by_user_id, safe_delay, template, source),
        daemon=True,
        name=f"server-{server.id}-restart",
    )
    worker.start()

    if safe_delay > 0:
        return True, f"Neustart geplant in {safe_delay} Sekunden."
    return True, "Neustart wird ausgefuehrt."


def request_restart_by_server_id(
    server_id: int,
    *,
    initiated_by_user_id: int | None,
    delay_seconds: int = 0,
    warning_message: str | None = None,
    source: str = "manual",
) -> tuple[bool, str]:
    with SessionLocal() as db:
        server = db.get(Server, server_id)
        if server is None:
            return False, "Server nicht gefunden."
        return queue_restart(
            db,
            server,
            initiated_by_user_id,
            delay_seconds=delay_seconds,
            warning_message=warning_message,
            source=source,
        )


def send_console_command(
    db: Session,
    server: Server,
    command: str,
    initiated_by_user_id: int | None,
) -> tuple[bool, str]:
    normalized = command.strip()
    if not normalized:
        return False, "Leerer Befehl."

    if normalized.lower() in {"/restart", "restart"}:
        return queue_restart(
            db,
            server,
            initiated_by_user_id,
            delay_seconds=get_settings().ingame_restart_delay_seconds,
            warning_message=get_settings().ingame_restart_warning_message,
            source="console_command",
        )

    with _PROCESS_LOCK:
        managed = _PROCESS_REGISTRY.get(server.id)

    if not managed or managed.process.poll() is not None:
        return False, "Server ist nicht aktiv."
    if not managed.process.stdin:
        return False, "Serverprozess akzeptiert keine Konsolenbefehle."

    try:
        managed.process.stdin.write(normalized + "\n")
        managed.process.stdin.flush()
    except Exception as exc:
        return False, f"Befehl konnte nicht gesendet werden: {exc}"

    console_service.append_output(server.id, f"> {normalized}")
    audit_service.log_action(
        db,
        action="server.console_command",
        user_id=initiated_by_user_id,
        server_id=server.id,
        details=f"command={normalized}",
    )
    return True, "Befehl gesendet."


def stop_server(
    db: Session,
    server: Server,
    initiated_by_user_id: int | None,
    *,
    force: bool = False,
    graceful_timeout_seconds: float = 12.0,
) -> tuple[bool, str]:
    _cancel_pending_restart(server.id)

    with _PROCESS_LOCK:
        managed = _PROCESS_REGISTRY.get(server.id)

    if not managed:
        server.status = "stopped"
        db.add(server)
        db.commit()
        return True, "Server war nicht aktiv."

    process = managed.process
    if process.poll() is not None:
        _cleanup_process_registry(server.id)
        server.status = "stopped"
        db.add(server)
        db.commit()
        return True, "Server war bereits beendet."

    if not force and process.stdin:
        try:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=graceful_timeout_seconds)
        except Exception:
            pass

    if process.poll() is None:
        try:
            process.terminate()
            process.wait(timeout=5.0)
        except Exception:
            process.kill()

    _cleanup_process_registry(server.id)
    server.status = "stopped"
    db.add(server)
    db.commit()

    audit_service.log_action(
        db,
        action="server.stop",
        user_id=initiated_by_user_id,
        server_id=server.id,
        details=f"force={force} at={datetime.now(timezone.utc).isoformat()}",
    )
    console_service.append_output(server.id, "Serverprozess gestoppt.")
    return True, "Server gestoppt."


def restart_server(db: Session, server: Server, initiated_by_user_id: int | None) -> tuple[bool, str]:
    stop_server(db, server, initiated_by_user_id, force=False)
    return start_server(db, server, initiated_by_user_id)


def get_log_directory_for_server(server_id: int) -> Path:
    return _server_log_dir(server_id)
