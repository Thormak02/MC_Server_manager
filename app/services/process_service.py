import re
import subprocess
from dataclasses import dataclass, field
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
from app.services.java_runtime_service import prepare_server_java_runtime

try:
    import psutil  # type: ignore[import-not-found]
except Exception:  # pragma: no cover
    psutil = None  # type: ignore[assignment]


@dataclass
class ManagedProcess:
    process: subprocess.Popen[str]
    log_file_path: str
    started_at: datetime
    max_players: int | None = None
    players: set[str] = field(default_factory=set)
    player_count_hint: int | None = None


_PROCESS_REGISTRY: dict[int, ManagedProcess] = {}
_PROCESS_LOCK = RLock()

_PENDING_RESTARTS: dict[int, Event] = {}
_RESTART_LOCK = RLock()

_INGAME_RESTART_PATTERNS = [
    re.compile(r"issued server command:\s*/restart\b", re.IGNORECASE),
    re.compile(r"executed command:\s*/restart\b", re.IGNORECASE),
]
_PLAYER_JOIN_PATTERN = re.compile(r"]:\s*(?P<name>.+?) joined the game", re.IGNORECASE)
_PLAYER_LEFT_PATTERN = re.compile(r"]:\s*(?P<name>.+?) left the game", re.IGNORECASE)
_PLAYER_LIST_PATTERN = re.compile(
    r"There are\s+(?P<current>\d+)\s+of a max of\s+(?P<max>\d+)\s+players online",
    re.IGNORECASE,
)


def _build_creation_flags() -> int:
    flags = 0
    if hasattr(subprocess, "CREATE_NEW_PROCESS_GROUP"):
        flags |= subprocess.CREATE_NEW_PROCESS_GROUP  # type: ignore[attr-defined]
    if hasattr(subprocess, "CREATE_NO_WINDOW"):
        flags |= subprocess.CREATE_NO_WINDOW  # type: ignore[attr-defined]
    return flags


def _terminate_process_tree(
    process: subprocess.Popen[str],
    *,
    timeout_seconds: float = 5.0,
    force: bool = False,
) -> None:
    if process.poll() is not None:
        return

    if psutil is not None:
        try:
            root = psutil.Process(process.pid)
            children = root.children(recursive=True)
            if force:
                for child in children:
                    try:
                        child.kill()
                    except Exception:
                        pass
                try:
                    root.kill()
                except Exception:
                    pass
            else:
                for child in children:
                    try:
                        child.terminate()
                    except Exception:
                        pass
                try:
                    root.terminate()
                except Exception:
                    pass

            wait_targets = [*children, root]
            _, alive = psutil.wait_procs(wait_targets, timeout=timeout_seconds)
            if alive:
                for proc in alive:
                    try:
                        proc.kill()
                    except Exception:
                        pass
                psutil.wait_procs(alive, timeout=2.0)
            return
        except Exception:
            pass

    taskkill_cmd = ["taskkill", "/PID", str(process.pid), "/T"]
    if force:
        taskkill_cmd.append("/F")
    try:
        subprocess.run(
            taskkill_cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
            creationflags=_build_creation_flags(),
            timeout=max(1.0, timeout_seconds + 1.0),
        )
    except Exception:
        pass


def _cmd_quote(value: str) -> str:
    return '"' + value.replace('"', '""') + '"'


def _normalize_windows_path(value: str) -> str:
    return value.replace("/", "\\")


def _is_unc_path(path: Path | str) -> bool:
    return _normalize_windows_path(str(path)).startswith("\\\\")


def _escape_cmd_token(value: str) -> str:
    escaped: list[str] = []
    for ch in value:
        if ch in {" ", "^", "&", "|", "<", ">", "(", ")"}:
            escaped.append("^" + ch)
        else:
            escaped.append(ch)
    return "".join(escaped)


def _ensure_nogui(command: str) -> str:
    if re.search(r"(?<!\S)nogui(?!\S)", command, flags=re.IGNORECASE):
        return command
    return f"{command.strip()} nogui".strip()


def _resolve_start_bat_path(server: Server, base_path: Path) -> Path:
    start_bat_path = Path(server.start_bat_path or str(base_path / "start.bat")).expanduser()
    if not start_bat_path.is_absolute():
        start_bat_path = base_path / start_bat_path
    return start_bat_path.resolve()


def _read_max_players_from_server_properties(base_path: str) -> int | None:
    properties = Path(base_path).expanduser().resolve() / "server.properties"
    if not properties.exists() or not properties.is_file():
        return None
    try:
        for raw in properties.read_text(encoding="utf-8", errors="ignore").splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("max-players="):
                value = line.split("=", 1)[1].strip()
                return int(value)
    except Exception:
        return None
    return None


def _command_for_server(server: Server, base_path: Path) -> list[str]:
    base_path_value = _normalize_windows_path(str(base_path))
    use_pushd = _is_unc_path(base_path)
    pushd_prefix = ""
    if use_pushd:
        pushd_prefix = f"pushd {_escape_cmd_token(base_path_value)} && "

    if server.start_mode == "command":
        if not server.start_command:
            raise ValueError("Startbefehl fehlt.")
        command = _ensure_nogui(server.start_command.strip())
        return ["cmd", "/d", "/c", pushd_prefix + command]

    if server.start_mode == "bat":
        start_bat_path = _resolve_start_bat_path(server, base_path)
        if not start_bat_path.exists():
            raise ValueError(f"Startdatei nicht gefunden: {start_bat_path}")

        try:
            bat_target = _normalize_windows_path(str(start_bat_path.relative_to(base_path)))
        except ValueError:
            bat_target = _normalize_windows_path(str(start_bat_path))

        bat_cmd = _escape_cmd_token(bat_target)
        bat_cmd = _ensure_nogui(bat_cmd)
        return ["cmd", "/d", "/c", pushd_prefix + bat_cmd]

    raise ValueError(f"Unbekannter Startmodus: {server.start_mode}")


def _append_subprocess_output(server_id: int, text: str, *, tag: str) -> None:
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line:
            console_service.append_output(server_id, f"[{tag}] {line}")


def _prepare_loader_runtime_if_needed(
    server: Server,
    base_path: Path,
    runtime_env: dict[str, str] | None,
) -> tuple[bool, str]:
    if server.server_type not in {"forge", "neoforge"} or server.start_mode != "bat":
        return True, ""

    start_bat_path = _resolve_start_bat_path(server, base_path)
    if start_bat_path.exists():
        return True, ""

    is_neoforge = server.server_type == "neoforge"
    install_script_name = "install_neoforge.bat" if is_neoforge else "install_forge.bat"
    display_name = "NeoForge" if is_neoforge else "Forge"
    install_tag = "neoforge-install" if is_neoforge else "forge-install"

    install_script = (base_path / install_script_name).resolve()
    if not install_script.exists():
        return False, f"Startdatei nicht gefunden: {start_bat_path}"

    console_service.append_output(server.id, f"{display_name} Installation wird vorbereitet ...")
    use_pushd = _is_unc_path(base_path)
    if use_pushd:
        install_step = f"pushd {_escape_cmd_token(_normalize_windows_path(str(base_path)))} && call {install_script_name}"
    else:
        install_step = f"call {install_script_name}"

    install_command = [
        "cmd",
        "/d",
        "/c",
        install_step,
    ]
    try:
        completed = subprocess.run(
            install_command,
            cwd=None if use_pushd else str(base_path),
            env=runtime_env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=300,
            creationflags=_build_creation_flags(),
        )
    except Exception as exc:
        return False, f"{display_name} Installation fehlgeschlagen: {exc}"

    _append_subprocess_output(server.id, completed.stdout or "", tag=install_tag)
    if completed.returncode != 0:
        return False, f"{display_name} Installation fehlgeschlagen (Exit-Code {completed.returncode})."

    if not start_bat_path.exists():
        return False, f"{display_name} Installation abgeschlossen, aber Startdatei fehlt weiterhin: {start_bat_path}"

    console_service.append_output(server.id, f"{display_name} Installation abgeschlossen.")
    return True, ""


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
        if server.status == "stopping":
            server.status = "stopped"
        elif server.status == "restarting":
            server.status = "restarting"
        elif server.status in {"starting", "running"}:
            server.status = "crashed"
        elif server.status == "backup_running":
            server.status = "stopped"
        elif server.status == "provisioning":
            server.status = "error"
        else:
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


def _update_player_runtime(server_id: int, line: str) -> None:
    with _PROCESS_LOCK:
        runtime = _PROCESS_REGISTRY.get(server_id)
        if runtime is None:
            return

        list_match = _PLAYER_LIST_PATTERN.search(line)
        if list_match:
            runtime.player_count_hint = int(list_match.group("current"))
            runtime.max_players = int(list_match.group("max"))
            return

        join_match = _PLAYER_JOIN_PATTERN.search(line)
        if join_match:
            runtime.players.add(join_match.group("name"))
            runtime.player_count_hint = max(
                runtime.player_count_hint or 0,
                len(runtime.players),
            )
            return

        left_match = _PLAYER_LEFT_PATTERN.search(line)
        if left_match:
            runtime.players.discard(left_match.group("name"))
            runtime.player_count_hint = len(runtime.players)


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
                    _update_player_runtime(server_id, line)
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
        if currently_running:
            if server.status in {"stopping", "restarting"}:
                continue
            if server.status != "running":
                server.status = "running"
                db.add(server)
                changed = True
        else:
            if server.status == "running":
                server.status = "crashed"
                db.add(server)
                changed = True
            elif server.status == "stopping":
                server.status = "stopped"
                db.add(server)
                changed = True

        if not currently_running:
            _cleanup_process_registry(server.id)

    if changed:
        db.commit()


def get_player_counts(server: Server) -> tuple[int | None, int | None]:
    with _PROCESS_LOCK:
        runtime = _PROCESS_REGISTRY.get(server.id)
        if runtime and runtime.process.poll() is None:
            current = runtime.player_count_hint
            if current is None:
                current = len(runtime.players)
            return current, runtime.max_players
    return 0, _read_max_players_from_server_properties(server.base_path)


def get_online_player_names(server_id: int) -> list[str]:
    with _PROCESS_LOCK:
        runtime = _PROCESS_REGISTRY.get(server_id)
        if runtime and runtime.process.poll() is None:
            return sorted(runtime.players, key=lambda name: name.lower())
    return []


def get_process_resource_usage(server_id: int) -> dict[str, float | int | None]:
    with _PROCESS_LOCK:
        managed = _PROCESS_REGISTRY.get(server_id)
        if not managed or managed.process.poll() is not None:
            return {
                "running": False,
                "pid": None,
                "cpu_percent": 0.0,
                "memory_mb": 0.0,
                "uptime_seconds": None,
            }
        pid = managed.process.pid
        started_at = managed.started_at

    cpu_percent = 0.0
    memory_mb = 0.0
    if psutil is not None:
        try:
            proc = psutil.Process(pid)
            cpu_percent = float(proc.cpu_percent(interval=None))
            memory_mb = float(proc.memory_info().rss / (1024 * 1024))
        except Exception:
            cpu_percent = 0.0
            memory_mb = 0.0

    uptime = (datetime.now(timezone.utc) - started_at).total_seconds()
    return {
        "running": True,
        "pid": pid,
        "cpu_percent": cpu_percent,
        "memory_mb": memory_mb,
        "uptime_seconds": max(0.0, uptime),
    }


def start_server(db: Session, server: Server, initiated_by_user_id: int | None) -> tuple[bool, str]:
    if is_running(server.id):
        return False, "Server laeuft bereits."

    java_ok, java_message, runtime_env = prepare_server_java_runtime(db, server)
    if not java_ok:
        server.status = "error"
        db.add(server)
        db.commit()
        return False, java_message
    java_message_clean = (java_message or "").strip()
    if java_message_clean:
        console_service.append_output(server.id, java_message)

    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists() or not base_path.is_dir():
        return False, "Serverordner existiert nicht."

    prepared, prepare_message = _prepare_loader_runtime_if_needed(server, base_path, runtime_env)
    if not prepared:
        server.status = "error"
        db.add(server)
        db.commit()
        return False, prepare_message

    try:
        command = _command_for_server(server, base_path)
    except ValueError as exc:
        server.status = "error"
        db.add(server)
        db.commit()
        return False, str(exc)

    use_pushd = _is_unc_path(base_path)

    server.status = "starting"
    db.add(server)
    db.commit()

    log_file_path = _create_session_log_file(server.id)
    try:
        process = subprocess.Popen(
            command,
            cwd=None if use_pushd else str(base_path),
            env=runtime_env,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            creationflags=_build_creation_flags(),
            bufsize=1,
        )
    except Exception as exc:
        server.status = "error"
        db.add(server)
        db.commit()
        return False, f"Start fehlgeschlagen: {exc}"

    with _PROCESS_LOCK:
        _PROCESS_REGISTRY[server.id] = ManagedProcess(
            process=process,
            log_file_path=str(log_file_path),
            started_at=datetime.now(timezone.utc),
            max_players=_read_max_players_from_server_properties(server.base_path),
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
    message = "Server gestartet."
    if java_message_clean:
        message = f"{message} {java_message_clean}"
    return True, message


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
    if normalized.lower() in {"/stop", "stop"} and server.status == "running":
        server.status = "stopping"
        db.add(server)
        db.commit()

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
    for_restart: bool = False,
) -> tuple[bool, str]:
    _cancel_pending_restart(server.id)

    with _PROCESS_LOCK:
        managed = _PROCESS_REGISTRY.get(server.id)

    if not managed:
        server.status = "restarting" if for_restart else "stopped"
        db.add(server)
        db.commit()
        return True, "Server war nicht aktiv."

    process = managed.process
    if process.poll() is not None:
        _cleanup_process_registry(server.id)
        server.status = "restarting" if for_restart else "stopped"
        db.add(server)
        db.commit()
        return True, "Server war bereits beendet."

    if for_restart:
        if server.status != "restarting":
            server.status = "restarting"
            db.add(server)
            db.commit()
    else:
        if server.status != "stopping":
            server.status = "stopping"
            db.add(server)
            db.commit()

    if not force and process.stdin:
        try:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=graceful_timeout_seconds)
        except Exception:
            pass

    if process.poll() is None:
        _terminate_process_tree(process, timeout_seconds=5.0, force=False)
    if process.poll() is None:
        _terminate_process_tree(process, timeout_seconds=5.0, force=True)
    if process.poll() is None:
        try:
            process.kill()
        except Exception:
            pass

    _cleanup_process_registry(server.id)
    server.status = "restarting" if for_restart else "stopped"
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
    server.status = "restarting"
    db.add(server)
    db.commit()
    stop_server(db, server, initiated_by_user_id, force=False, for_restart=True)
    return start_server(db, server, initiated_by_user_id)


def shutdown_all_managed_processes(*, graceful_timeout_seconds: float = 3.0) -> None:
    with _PROCESS_LOCK:
        server_ids = list(_PROCESS_REGISTRY.keys())
    if not server_ids:
        return

    for server_id in server_ids:
        with SessionLocal() as db:
            server = db.get(Server, server_id)
            if server is None:
                _cleanup_process_registry(server_id)
                continue
            try:
                stop_server(
                    db,
                    server,
                    initiated_by_user_id=None,
                    force=False,
                    graceful_timeout_seconds=graceful_timeout_seconds,
                    for_restart=False,
                )
            except Exception:
                try:
                    stop_server(
                        db,
                        server,
                        initiated_by_user_id=None,
                        force=True,
                        graceful_timeout_seconds=0.0,
                        for_restart=False,
                    )
                except Exception:
                    _cleanup_process_registry(server_id)


def get_log_directory_for_server(server_id: int) -> Path:
    return _server_log_dir(server_id)
