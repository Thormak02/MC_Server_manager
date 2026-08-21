"""Config-Capture eines Modpack-Servers fuer den Universal-Hub (manueller Knopf).

Ablauf einer Aufnahme (im Daemon-Thread, UI pollt capture_status):
  1. online-mode=false + network-compression-threshold=-1 am Ziel-Server setzen (alte
     Werte merken) und den Server neu starten -> die Config-Phase wird unverschluesselt
     und unkomprimiert, also aufnehmbar.
  2. Recording-Relay auf einem Capture-Port binden -> 127.0.0.1:effective_server_port.
     Der modded Client verbindet sich dorthin; ALLE Frames werden transparent
     weitergereicht UND als data/replays/<slug>_capture.replay mitgeschnitten.
  3. Danach: Properties zuruecksetzen, Server neu starten, Hub reconcilen -> das Pack
     ist ab sofort spoofbar (Path-A-Routing waehlt es automatisch).

Der Capture-Port muss vom Client erreichbar sein (Firewall/Forward) - wie beim
bewaehrten CLI-Aufnahmeweg. Der Recorder-Kern ist aus scripts/capture_handshake.py
uebernommen (uncompressed frame relay + .replay-Writer im MCRP\\x01-Format).
"""

from __future__ import annotations

import socket
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

_LOCK = threading.RLock()
_SESSIONS: dict[int, "CaptureSession"] = {}

_DEFAULT_CAPTURE_PORT = 25610
_ACCEPT_TIMEOUT = 300.0        # bis 5 min auf den Client warten (Modpack-Neustart dauert)
# Aufnahme laeuft bis die Config-Phase KOMPLETT ist (S->C PLAY-Login 0x2B gesehen), dann
# noch die Setup-Pakete bis zum ersten Chunk (0x27) - NICHT feste Sekunden: grosse Packs
# (z.B. ATM10-Sky) brauchen laenger als ein festes Fenster, kleine sollen nicht warten.
_PLAY_LOGIN_ID = 0x2B          # S->C PLAY-Login = Config-Phase fertig
_FIRST_CHUNK_ID = 0x27         # erster Chunk = ab hier nur noch Weltdaten-Ballast
_GRACE_AFTER_LOGIN = 8.0       # falls kein Chunk folgt: nach dem Login noch sammeln (grosse
                               # Packs flushen Setup-Pakete evtl. verzoegert; normal wird
                               # ohnehin am ersten Chunk gestoppt, die Grace greift selten)
_MAX_RECORD_SECONDS = 180.0    # Sicherheitsobergrenze (falls PLAY-Login nie kommt)
_ACTIVE_STATES = ("preparing", "waiting", "recording", "saving")


@dataclass
class CaptureSession:
    server_id: int
    slug: str = ""
    status: str = "preparing"     # preparing|waiting|recording|saving|done|error
    message: str = ""
    relay_port: int = _DEFAULT_CAPTURE_PORT
    packets: int = 0
    saved_path: Optional[str] = None
    started_at: float = 0.0


# --------------------------------------------------------------------------- #
# Recorder-Kern (uncompressed frame relay + .replay-Writer)
# --------------------------------------------------------------------------- #
def _read_varint(buf: bytearray, start: int = 0):
    result = 0
    for i in range(5):
        if start + i >= len(buf):
            return None
        b = buf[start + i]
        result |= (b & 0x7F) << (7 * i)
        if not b & 0x80:
            return result, i + 1
    raise ValueError("VarInt zu lang")


def _frame_pid(raw: bytes) -> int | None:
    """Packet-ID eines rohen (unkomprimierten) Frames [len][id][...]."""
    ba = bytearray(raw)
    head = _read_varint(ba, 0)
    if head is None:
        return None
    _length, n = head
    pid = _read_varint(ba, n)
    return pid[0] if pid else None


def _pipe(src, dst, direction: int, log: list, lock: threading.Lock,
          stop: threading.Event, state: dict) -> None:
    """Frames von src -> dst durchreichen UND (direction, raw) protokollieren.
    direction: 0 = S->C (spaeter abspielen), 1 = C->S (Checkpoint). Auf der S->C-Seite
    den PLAY-Login (0x2B) und den ersten Chunk (0x27) markieren -> Aufnahme kann enden,
    sobald die Config-Phase samt Setup-Paketen vollstaendig ist."""
    buf = bytearray()
    try:
        while not stop.is_set():
            got = _read_varint(buf, 0)
            if got is not None:
                length, n = got
                total = n + length
                if length >= 0 and len(buf) >= total:
                    raw = bytes(buf[:total])
                    del buf[:total]
                    dst.sendall(raw)
                    with lock:
                        log.append((direction, raw))
                    if direction == 0:
                        pid = _frame_pid(raw)
                        if pid == _PLAY_LOGIN_ID and state.get("play_login_at") is None:
                            state["play_login_at"] = time.monotonic()
                        elif (pid == _FIRST_CHUNK_ID and state.get("play_login_at") is not None
                              and state.get("first_chunk_at") is None):
                            state["first_chunk_at"] = time.monotonic()
                    continue
            chunk = src.recv(16384)
            if not chunk:
                break
            buf.extend(chunk)
    except (OSError, ConnectionError, ValueError):
        pass
    finally:
        stop.set()


def _write_replay(out_path: str, frames: list) -> None:
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "wb") as rf:
        rf.write(b"MCRP\x01")  # Magic + Version (wie replay_service.load_replay erwartet)
        for direction, raw in frames:
            rf.write(bytes([direction]))
            rf.write(len(raw).to_bytes(4, "big"))
            rf.write(raw)


def _record_streams(client: socket.socket, backend: socket.socket, out_path: str,
                    max_seconds: float = _MAX_RECORD_SECONDS,
                    grace_after_login: float = _GRACE_AFTER_LOGIN) -> tuple[bool, int, str]:
    """Config-Phase mitschneiden: bis zum ersten Chunk NACH dem PLAY-Login (dann ist die
    Config samt Setup-Paketen vollstaendig) - oder ``grace_after_login`` nach dem Login,
    falls kein Chunk folgt. So wird der Client vor dem Weltladen getrennt (kein 'komisches'
    Offline-Spielen) UND die Aufnahme ist auch bei riesigen Packs vollstaendig."""
    log: list = []
    lock = threading.Lock()
    stop = threading.Event()
    state: dict = {"play_login_at": None, "first_chunk_at": None}
    for src, dst, direction in ((client, backend, 1), (backend, client, 0)):
        threading.Thread(target=_pipe, args=(src, dst, direction, log, lock, stop, state),
                         daemon=True).start()
    deadline = time.monotonic() + max_seconds
    while not stop.is_set():
        now = time.monotonic()
        if now >= deadline:
            break
        if state.get("first_chunk_at") is not None:
            break  # Config + Login + Setup komplett -> Schluss (vor den Chunks)
        login_at = state.get("play_login_at")
        if login_at is not None and now >= login_at + grace_after_login:
            break  # Login da, aber (noch) kein Chunk -> Setup-Pakete duerften reichen
        time.sleep(0.05)
    stop.set()
    for s in (client, backend):
        try:
            s.close()
        except OSError:
            pass
    time.sleep(0.2)
    with lock:
        frames = list(log)
    if not frames:
        return False, 0, "Nichts aufgenommen (kein Datenverkehr)."
    if state.get("play_login_at") is None:
        return False, len(frames), (
            f"{len(frames)} Pakete, aber kein PLAY-Login (0x2B) erreicht - Config "
            "unvollstaendig. Server evtl. zu langsam/gross oder Client kam nicht bis PLAY.")
    _write_replay(out_path, frames)
    via = "erster Chunk" if state.get("first_chunk_at") else f"Timeout {grace_after_login:.0f}s nach Login"
    return True, len(frames), f"{len(frames)} Pakete aufgenommen (Config komplett; Ende via {via})."


def record_capture(listen_port: int, backend_host: str, backend_port: int, out_path: str,
                   accept_timeout: float = _ACCEPT_TIMEOUT,
                   max_seconds: float = _MAX_RECORD_SECONDS) -> tuple[bool, int, str]:
    """Blockierend: einen Client auf ``listen_port`` annehmen, ueber das Backend
    aufnehmen und ``out_path`` schreiben. Gibt (ok, packet_count, message) zurueck."""
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        listener.bind(("0.0.0.0", int(listen_port)))
    except OSError as exc:
        return False, 0, f"Capture-Port {listen_port} belegt: {exc}"
    listener.listen(1)
    listener.settimeout(accept_timeout)
    try:
        client, _addr = listener.accept()
    except (OSError, socket.timeout):
        listener.close()
        return False, 0, "Kein Client verbunden (Timeout)."
    listener.close()
    try:
        backend = socket.create_connection((backend_host, int(backend_port)), timeout=15)
    except OSError as exc:
        try:
            client.close()
        except OSError:
            pass
        return False, 0, f"Backend {backend_host}:{backend_port} nicht erreichbar: {exc}"
    return _record_streams(client, backend, out_path, max_seconds)


# --------------------------------------------------------------------------- #
# Orchestrierung (Property-Flip + Restart + Record + Restore)
# --------------------------------------------------------------------------- #
def _read_property(base_path: str, key: str) -> str | None:
    p = Path(base_path).expanduser() / "server.properties"
    if not p.exists():
        return None
    prefix = f"{key}="
    for line in p.read_text(encoding="utf-8", errors="ignore").splitlines():
        s = line.strip()
        if s.startswith(prefix):
            return s[len(prefix):]
    return None


def _prepare(server_id: int, user_id: int | None):
    """Properties merken + auf capture-tauglich setzen und Server neu starten."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import process_service, server_service

    with SessionLocal() as db:
        srv = db.get(Server, server_id)
        if srv is None:
            raise RuntimeError("Server nicht gefunden.")
        slug, base_path = srv.slug, srv.base_path
        backend_port = server_service.effective_server_port(srv)
        old = {
            "online-mode": _read_property(base_path, "online-mode"),
            "network-compression-threshold": _read_property(base_path, "network-compression-threshold"),
        }
        server_service._upsert_server_property(srv, "online-mode", "false")
        server_service._upsert_server_property(srv, "network-compression-threshold", "-1")
        process_service.restart_server(db, srv, user_id)
    return slug, base_path, int(backend_port or 0), old


def _restore(server_id: int, old: dict, user_id: int | None) -> None:
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import process_service, server_service

    with SessionLocal() as db:
        srv = db.get(Server, server_id)
        if srv is None:
            return
        for key, val in old.items():
            if val is None:  # war nicht gesetzt -> sinnvoller Default
                val = "true" if key == "online-mode" else "256"
            server_service._upsert_server_property(srv, key, val)
        process_service.restart_server(db, srv, user_id)


def _set_status(server_id: int, status: str, message: str, *,
                slug: str | None = None, saved: str | None = None, packets: int | None = None) -> None:
    with _LOCK:
        sess = _SESSIONS.get(server_id)
        if sess is None:
            return
        sess.status = status
        sess.message = message
        if slug is not None:
            sess.slug = slug
        if saved is not None:
            sess.saved_path = saved
        if packets is not None:
            sess.packets = packets


def _run_capture(server_id: int, user_id: int | None) -> None:
    old: dict | None = None
    try:
        _set_status(server_id, "preparing", "Setze Properties (online-mode=false, compression=-1) + starte Server neu ...")
        slug, _base_path, backend_port, old = _prepare(server_id, user_id)
        with _LOCK:
            port = _SESSIONS[server_id].relay_port
        _set_status(server_id, "waiting",
                    f"Capture-Modus aktiv. Sobald der Server laeuft: {slug}-Client mit "
                    f"<server-ip>:{port} verbinden (einmal einloggen).", slug=slug)
        from app.services import hub_replay_service

        out = hub_replay_service.replay_path_for(slug)
        ok, n, msg = record_capture(port, "127.0.0.1", backend_port, out)
        if ok:
            _set_status(server_id, "saving", f"{msg} Setze Server zurueck ...", saved=out, packets=n)
        else:
            _set_status(server_id, "error", msg)
    except Exception as exc:  # noqa: BLE001 - Capture darf den Manager nie crashen
        _set_status(server_id, "error", f"Fehler: {exc}")
    finally:
        if old is not None:
            try:
                _restore(server_id, old, user_id)
            except Exception as exc:  # noqa: BLE001
                _set_status(server_id, _SESSIONS.get(server_id).status if server_id in _SESSIONS else "error",
                            f"(Restore-Fehler: {exc})")
        try:
            from app.services import hub_lobby_service

            hub_lobby_service.reconcile_hub_lobby()
        except Exception:  # noqa: BLE001
            pass
        with _LOCK:
            sess = _SESSIONS.get(server_id)
            if sess and sess.status not in ("error",):
                sess.status = "done"
                sess.message = (f"Fertig - {sess.packets} Pakete -> {sess.saved_path}. "
                                "Pack ist jetzt spoofbar." if sess.saved_path else sess.message)


def start_capture_for_server(server_id: int, user_id: int | None = None,
                             relay_port: int | None = None) -> tuple[bool, str]:
    """Aufnahme fuer einen Server starten (im Hintergrund). (ok, Nachricht)."""
    with _LOCK:
        existing = _SESSIONS.get(server_id)
        if existing and existing.status in _ACTIVE_STATES:
            return False, f"Aufnahme laeuft bereits ({existing.status})."
        port = int(relay_port or _DEFAULT_CAPTURE_PORT)
        _SESSIONS[server_id] = CaptureSession(
            server_id=server_id, status="preparing", relay_port=port,
            started_at=time.time(), message="Vorbereitung ...",
        )
    threading.Thread(target=_run_capture, args=(server_id, user_id), daemon=True,
                     name=f"hubcapture-{server_id}").start()
    return True, (f"Capture wird vorbereitet. Der Server startet kurz neu; sobald er laeuft, "
                  f"verbinde deinen Modpack-Client EINMAL mit <server-ip>:{port}. "
                  "Port ggf. in der Firewall oeffnen.")


def capture_status(server_id: int) -> dict | None:
    with _LOCK:
        sess = _SESSIONS.get(server_id)
        if sess is None:
            return None
        return {
            "server_id": sess.server_id,
            "slug": sess.slug,
            "status": sess.status,
            "message": sess.message,
            "relay_port": sess.relay_port,
            "packets": sess.packets,
            "saved_path": sess.saved_path,
            "active": sess.status in _ACTIVE_STATES,
        }
