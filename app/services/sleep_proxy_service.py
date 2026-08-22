"""On-Demand-/Sleep-Proxy fuer Minecraft-Server (lazymc-artig).

Fuer jeden Server mit ``sleep_enabled`` sitzt ein transparenter Proxy auf dem
oeffentlichen Port. Schlaeft der Server, beantwortet der Proxy Serverlisten-Pings
mit einer "schlaeft"-MOTD und weckt den Server erst bei einem echten Login-
Versuch. Sobald der echte Server (auf ``sleep_internal_port``) bereit ist, wird
die Verbindung transparent weitergeleitet. Ein Idle-Monitor faehrt Server ohne
Spieler nach ``sleep_delay_seconds`` wieder herunter.

Threading: ein Accept-Thread je Server, je Verbindung ein Handler-Thread, ein
globaler Idle-Monitor-Thread. Reine Protokoll-Logik liegt in ``mc_protocol``.
"""

from __future__ import annotations

import socket
from dataclasses import dataclass, field
from threading import Event, RLock, Thread
from time import monotonic, sleep

from sqlalchemy import select

from app.db.session import SessionLocal
from app.models.server import Server
from app.services import audit_service, mc_protocol, process_service

_HANDSHAKE_READ_TIMEOUT = 5.0
_WAKE_READY_TIMEOUT = 180.0
_BACKEND_CONNECT_TIMEOUT = 5.0
_IDLE_CHECK_INTERVAL = 15.0
_BUFFER_SIZE = 8192


@dataclass
class _ProxyListener:
    server_id: int
    public_port: int
    internal_port: int
    sock: socket.socket
    bind_host: str = "0.0.0.0"
    stop_event: Event = field(default_factory=Event)
    thread: Thread | None = None


_PROXIES: dict[int, _ProxyListener] = {}
_PROXY_LOCK = RLock()
# Server-IDs, deren Bind zuletzt fehlschlug -> nur einmal je Episode loggen
# (der Idle-Monitor ruft reconcile alle 15s auf, das wuerde sonst spammen).
_BIND_FAILED: set[int] = set()

# server_id -> monotonic-Zeitpunkt, seit dem der Server leer ist (0 Spieler).
_EMPTY_SINCE: dict[int, float] = {}
_IDLE_LOCK = RLock()

_IDLE_THREAD: Thread | None = None
_IDLE_STOP = Event()


# --------------------------------------------------------------------------- #
# Port-Hilfen
# --------------------------------------------------------------------------- #
def find_free_port(preferred: int | None = None) -> int:
    """Einen freien lokalen TCP-Port finden (bevorzugt ``preferred``)."""
    candidates = []
    if preferred and 1 <= preferred <= 65535:
        candidates.append(preferred)
    for candidate in candidates:
        if _port_is_free(candidate):
            return candidate
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return int(probe.getsockname()[1])


def _port_is_free(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(("0.0.0.0", port))
            return True
        except OSError:
            return False


# --------------------------------------------------------------------------- #
# Lifecycle
# --------------------------------------------------------------------------- #
def start_proxy(
    server_id: int,
    public_port: int,
    internal_port: int,
    *,
    bind_host: str = "0.0.0.0",
) -> bool:
    with _PROXY_LOCK:
        existing = _PROXIES.get(server_id)
        if (
            existing is not None
            and existing.public_port == public_port
            and existing.internal_port == internal_port
            and existing.bind_host == bind_host
        ):
            return True
        # Aenderte sich Port ODER Bind-Host (z.B. Wechsel Velocity <-> Standalone),
        # rebinden statt still lassen.
        if existing is not None:
            _stop_locked(server_id)

        try:
            # Bewusst OHNE SO_REUSEADDR: sonst wuerde der Proxy unter Windows
            # den oeffentlichen Port auch dann binden, wenn der Server noch
            # darauf laeuft (Port-Hijacking -> Split-Zustand). Ohne REUSEADDR
            # bindet er nur, wenn der Port wirklich frei ist.
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind((bind_host, public_port))
            sock.listen(64)
        except OSError as exc:
            if server_id not in _BIND_FAILED:
                _log(
                    server_id,
                    "sleep_proxy.bind_failed",
                    f"public_port={public_port} error={exc!r} "
                    "(Server noch auf diesem Port? Neustart noetig.)",
                )
                _BIND_FAILED.add(server_id)
            return False

        _BIND_FAILED.discard(server_id)
        listener = _ProxyListener(
            server_id=server_id,
            public_port=public_port,
            internal_port=internal_port,
            sock=sock,
            bind_host=bind_host,
        )
        thread = Thread(
            target=_accept_loop,
            args=(listener,),
            daemon=True,
            name=f"sleep-proxy-{server_id}",
        )
        listener.thread = thread
        _PROXIES[server_id] = listener
        thread.start()
        _log(
            server_id,
            "sleep_proxy.started",
            f"bind={bind_host}:{public_port} internal_port={internal_port}",
        )
        return True


def stop_proxy(server_id: int) -> None:
    with _PROXY_LOCK:
        _stop_locked(server_id)


def _stop_locked(server_id: int) -> None:
    listener = _PROXIES.pop(server_id, None)
    if listener is None:
        return
    listener.stop_event.set()
    try:
        listener.sock.close()
    except OSError:
        pass


def reconcile_proxies() -> None:
    """Proxies gemaess DB (sleep_enabled) starten/stoppen.

    Jeder Sleep-Server bekommt einen Wake-Proxy auf ``0.0.0.0:port`` (oeffentlich).
    So funktioniert die Direktverbindung UND – falls das Gateway auf diesen Port
    zeigt – der Gateway-Forward (das Gateway weckt darueber mit).
    """
    with SessionLocal() as db:
        servers = list(db.scalars(select(Server)).all())
        wanted: dict[int, tuple[str, int, int]] = {}
        for server in servers:
            if not (
                server.sleep_enabled
                and server.port
                and server.sleep_internal_port
                and server.port != server.sleep_internal_port
            ):
                continue
            wanted[server.id] = (
                "0.0.0.0",
                int(server.port),
                int(server.sleep_internal_port),
            )

    with _PROXY_LOCK:
        for server_id in list(_PROXIES.keys()):
            if server_id not in wanted:
                _stop_locked(server_id)
        for server_id, (bind_host, public_port, internal_port) in wanted.items():
            start_proxy(server_id, public_port, internal_port, bind_host=bind_host)


def sleep_server(
    db, server: Server, initiated_by_user_id: int | None
) -> tuple[bool, str]:
    """Server manuell in den Sleep-/On-Demand-Zustand versetzen.

    Faehrt den Server herunter und bindet danach sofort den Wake-Proxy auf dem
    oeffentlichen Port (identisch zum automatischen Idle-Shutdown), damit der
    naechste Beitritt den Server wieder weckt.
    """
    if not getattr(server, "sleep_enabled", False):
        return False, "Sleep-Modus (On-Demand) ist fuer diesen Server nicht aktiviert."

    status = (server.status or "stopped").strip().lower()
    if status == "stopped":
        # Bereits aus -> nur sicherstellen, dass der Proxy laeuft.
        reconcile_proxies()
        return True, "Server schlaeft bereits (On-Demand aktiv)."

    ok, message = process_service.stop_server(db, server, initiated_by_user_id)
    if not ok:
        return ok, message

    # Direkt binden, damit kein 15s-Fenster ohne Wake-Proxy entsteht.
    reconcile_proxies()
    audit_service.log_action(
        db,
        action="server.sleep",
        user_id=initiated_by_user_id,
        server_id=server.id,
        details="manual sleep",
    )
    return True, "Server schlaeft jetzt – Aufwecken automatisch beim naechsten Beitritt."


def shutdown_all() -> None:
    _IDLE_STOP.set()
    with _PROXY_LOCK:
        for server_id in list(_PROXIES.keys()):
            _stop_locked(server_id)


# --------------------------------------------------------------------------- #
# Accept-Loop + Verbindungshandhabung
# --------------------------------------------------------------------------- #
def _accept_loop(listener: _ProxyListener) -> None:
    sock = listener.sock
    while not listener.stop_event.is_set():
        try:
            client, _addr = sock.accept()
        except OSError:
            break
        Thread(
            target=_handle_connection,
            args=(listener, client),
            daemon=True,
            name=f"sleep-proxy-conn-{listener.server_id}",
        ).start()


def _handle_connection(listener: _ProxyListener, client: socket.socket) -> None:
    server_id = listener.server_id
    try:
        client.settimeout(_HANDSHAKE_READ_TIMEOUT)
        buffer = bytearray()
        handshake = None
        while True:
            try:
                chunk = client.recv(_BUFFER_SIZE)
            except socket.timeout:
                return
            if not chunk:
                return
            buffer.extend(chunk)
            try:
                handshake = mc_protocol.parse_handshake(bytes(buffer))
                break
            except mc_protocol.IncompletePacket:
                if len(buffer) > _BUFFER_SIZE * 4:
                    return
                continue
            except mc_protocol.ProtocolError:
                return

        running = process_service.is_running(server_id)
        if handshake.next_state == mc_protocol.NEXT_STATE_STATUS and not running:
            _respond_sleeping_status(client, buffer, handshake)
            return
        # Login (2) ODER Transfer (3, seit 1.20.5): ein echter Spieler will rein ->
        # schlafenden Server wecken. Ohne den Transfer-Fall bleibt ein per Lobby
        # weitergereichter Client haengen (Backend ist noch aus).
        if handshake.next_state in mc_protocol.JOIN_NEXT_STATES and not running:
            if not _wake_server(server_id, client):
                return  # Timeout/Fehler -> Client wurde informiert/geschlossen
        # Ab hier laeuft der Server (oder wurde geweckt) -> transparent forwarden.
        _forward(listener, client, bytes(buffer))
    except OSError:
        pass
    finally:
        _safe_close(client)


def _respond_sleeping_status(
    client: socket.socket,
    buffer: bytearray,
    handshake: mc_protocol.Handshake,
) -> None:
    status_json = mc_protocol.build_status_json(
        motd="§6§l§o Schlaeft – zum Aufwecken beitreten",
        version_name="Sleeping",
        protocol_version=handshake.protocol_version,
        players_online=0,
        players_max=0,
    )
    try:
        client.sendall(mc_protocol.build_status_response_packet(status_json))
        # Optionalen Ping->Pong beantworten (best effort).
        try:
            client.settimeout(2.0)
            extra = client.recv(_BUFFER_SIZE)
        except (socket.timeout, OSError):
            extra = b""
        payload = mc_protocol.try_read_ping_payload(extra) if extra else None
        if payload is not None:
            client.sendall(mc_protocol.build_pong_packet(payload))
    except OSError:
        pass


def _wake_server(server_id: int, client: socket.socket) -> bool:
    """Server starten und auf Bereitschaft warten.

    True -> Server ist bereit, Verbindung kann weitergeleitet werden.
    False -> Timeout/Fehler; der Client wurde mit einer Meldung getrennt.
    """
    try:
        with SessionLocal() as db:
            server = db.get(Server, server_id)
            if server is None:
                return False
            process_service.start_server(db, server, initiated_by_user_id=None)
        _log(server_id, "sleep_proxy.wake", "login trigger")
    except Exception as exc:  # noqa: BLE001
        _log(server_id, "sleep_proxy.wake_failed", repr(exc))
        _send_login_disconnect(client, "Serverstart fehlgeschlagen. Bitte spaeter erneut versuchen.")
        return False

    deadline = monotonic() + _WAKE_READY_TIMEOUT
    while monotonic() < deadline:
        if process_service.is_server_ready(server_id):
            return True
        sleep(0.5)

    _send_login_disconnect(
        client,
        "Server wird gestartet – bitte in ~30 Sekunden erneut verbinden.",
    )
    return False


def _forward(listener: _ProxyListener, client: socket.socket, initial: bytes) -> None:
    _forward_to_backend(client, listener.internal_port, listener.server_id, initial)


def _forward_to_backend(
    client: socket.socket,
    internal_port: int,
    server_id: int,
    initial: bytes,
) -> None:
    """Client transparent an ``127.0.0.1:internal_port`` koppeln (Byte-Splicing).

    Gemeinsamer Baustein fuer den Sleep-Proxy (ein Backend) und das Gateway
    (viele Backends).
    """
    try:
        backend = socket.create_connection(
            ("127.0.0.1", internal_port),
            timeout=_BACKEND_CONNECT_TIMEOUT,
        )
    except OSError as exc:
        _log(server_id, "sleep_proxy.backend_unreachable", repr(exc))
        _send_login_disconnect(client, "Server nicht erreichbar. Bitte erneut verbinden.")
        return

    client.settimeout(None)
    backend.settimeout(None)
    try:
        if initial:
            backend.sendall(initial)
    except OSError:
        _safe_close(backend)
        return

    up = Thread(target=_pipe, args=(client, backend), daemon=True)
    down = Thread(target=_pipe, args=(backend, client), daemon=True)
    up.start()
    down.start()
    up.join()
    down.join()
    _safe_close(backend)


def _pipe(src: socket.socket, dst: socket.socket) -> None:
    try:
        while True:
            data = src.recv(_BUFFER_SIZE)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        # Gegenrichtung entkoppeln, damit der andere _pipe-Thread endet.
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass
        try:
            src.shutdown(socket.SHUT_RD)
        except OSError:
            pass


def _send_login_disconnect(client: socket.socket, message: str) -> None:
    try:
        client.sendall(mc_protocol.build_login_disconnect_packet(message))
    except OSError:
        pass


def _safe_close(sock: socket.socket) -> None:
    try:
        sock.close()
    except OSError:
        pass


# --------------------------------------------------------------------------- #
# Idle-Monitor
# --------------------------------------------------------------------------- #
def start_idle_monitor() -> None:
    global _IDLE_THREAD
    if _IDLE_THREAD is not None and _IDLE_THREAD.is_alive():
        return
    _IDLE_STOP.clear()
    _IDLE_THREAD = Thread(
        target=_idle_monitor_loop,
        daemon=True,
        name="sleep-idle-monitor",
    )
    _IDLE_THREAD.start()


def _idle_monitor_loop() -> None:
    while not _IDLE_STOP.wait(_IDLE_CHECK_INTERVAL):
        try:
            _idle_tick()
        except Exception:  # noqa: BLE001 - Monitor darf nie sterben
            pass


def _idle_tick() -> None:
    now = monotonic()
    # Selbstheilung: sicherstellen, dass fuer jeden Sleep-Server ein Proxy auf
    # dem oeffentlichen Port laeuft. Wurde Sleep z.B. an einem laufenden Server
    # aktiviert, war der Port zunaechst belegt; sobald er frei ist (nach Stop/
    # Neustart), bindet der Proxy hier automatisch nach.
    reconcile_proxies()
    # Gateway abgleichen: Listener nachbinden, Routing-Tabelle auffrischen.
    # Lazy-Import bricht den Zyklus gateway_service -> sleep_proxy_service.
    try:
        from app.services import gateway_service

        gateway_service.reconcile_gateway()
    except Exception:  # noqa: BLE001 - Monitor darf nie sterben
        pass
    # Universal-Lobby (Python-Hub) ebenso selbstheilend abgleichen -> ein Settings-
    # Toggle greift ohne App-Neustart (Listener wird ~15s spaeter gestartet/gestoppt).
    try:
        from app.services import hub_lobby_service

        hub_lobby_service.reconcile_hub_lobby()
    except Exception:  # noqa: BLE001
        pass
    try:
        from app.services import viaproxy_service

        viaproxy_service.reconcile_viaproxy()
    except Exception:  # noqa: BLE001
        pass
    try:
        from app.services import proxy_service

        proxy_service.reconcile_velocity()   # Velocity (network_mode==velocity) selbstheilen
    except Exception:  # noqa: BLE001
        pass

    try:
        from app.services import central_storage_service

        central_storage_service.maybe_snapshot_db()  # naht-live, nur bei DB-Aenderung
        central_storage_service.maybe_prune_logs()    # alte Session-Logs entfernen (gedrosselt)
    except Exception:  # noqa: BLE001
        pass

    with SessionLocal() as db:
        servers = list(
            db.scalars(select(Server).where(Server.sleep_enabled.is_(True))).all()
        )
        for server in servers:
            # Nur wirklich bereite Server koennen idle-heruntergefahren werden.
            if not process_service.is_server_ready(server.id):
                _clear_empty(server.id)
                continue
            current, _max_players = process_service.get_player_counts(server)
            if current and current > 0:
                _clear_empty(server.id)
                continue

            empty_since = _mark_empty(server.id, now)
            delay = max(0, int(server.sleep_delay_seconds or 0))
            if now - empty_since >= delay:
                _clear_empty(server.id)
                process_service.stop_server(db, server, initiated_by_user_id=None)
                _log(
                    server.id,
                    "sleep_proxy.idle_shutdown",
                    f"after={delay}s",
                )
                # Direkt nach dem Herunterfahren den Proxy binden, damit der
                # Server sofort wieder geweckt werden kann (kein 15s-Fenster).
                reconcile_proxies()


def _mark_empty(server_id: int, now: float) -> float:
    with _IDLE_LOCK:
        return _EMPTY_SINCE.setdefault(server_id, now)


def _clear_empty(server_id: int) -> None:
    with _IDLE_LOCK:
        _EMPTY_SINCE.pop(server_id, None)


def _log(server_id: int, action: str, details: str) -> None:
    try:
        with SessionLocal() as db:
            audit_service.log_action(
                db,
                action=action,
                server_id=server_id,
                details=details,
            )
    except Exception:  # noqa: BLE001
        pass
