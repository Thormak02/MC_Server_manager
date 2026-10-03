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
# Der Client haengt ReadTimeoutHandler(30) als ERSTEN Handler in seine Netty-
# Pipeline (vor Splitter und Decoder) und gibt nach 30 s ohne EINGEHENDES Paket
# auf - in Handshake, Login, Configuration und Play gleich; Fabric/Forge/NeoForge
# patchen das nicht. Stilles Warten muss also deutlich unter 30 s bleiben:
# 15 s Warten + 5 s _HANDSHAKE_READ_TIMEOUT = 20 s, also 10 s Luft.
# Bewusst nicht 0: schnelle Server (Paper 9-14 s, Spigot 17 s) werden damit
# weiterhin transparent durchgereicht statt den Spieler wegzuschicken.
_WAKE_READY_TIMEOUT = 15.0

# ... ES SEI DENN, wir koennen die Uhr des Clients zuruecksetzen. Jedes EINGEHENDE
# Paket tut das, und in der Login-Phase gibt es dafuer ein folgenloses: den Login
# Plugin Request. Der Client antwortet darauf laut Protokoll immer (notfalls mit
# "nicht verstanden") und BLEIBT in der Login-Phase - beliebig oft wiederholbar.
# Ein Keep-Alive gibt es in der Login-Phase nicht, also ist das das einzige Mittel,
# eine Direktverbindung ueber einen langen Serverstart zu halten.
# Erst ab Client 1.13 (Protokoll 393); davor kennt der Login-Zustand nur 0x00-0x03.
_PROTOCOL_LOGIN_PLUGIN = 393
_LOGIN_CB_PLUGIN_REQUEST = 0x04
_LOGIN_SB_PLUGIN_RESPONSE = 0x02
# Deutlich unter 30 s, damit auch ein verzoegertes Paket die Uhr noch rechtzeitig
# zurueckstellt.
_HEARTBEAT_INTERVAL = 8.0
# Mit Heartbeat ist die Haltezeit technisch unbegrenzt - trotzdem eine Obergrenze,
# damit ein gescheiterter Start den Spieler nicht ewig im Ladebildschirm laesst.
# 300 s sind gut 5x die gemessenen 56 s des langsamsten Modpacks und decken auch
# einen ersten Start mit Modpack-Install.
_HOLD_READY_TIMEOUT = 300.0
# Takt der Warteschleife; gleichzeitig das recv-Timeout, mit dem wir waehrend
# des Wartens am Client mitlesen (Abbruch-Erkennung).
_WAKE_POLL_INTERVAL = 0.5
_BACKEND_CONNECT_TIMEOUT = 5.0
_IDLE_CHECK_INTERVAL = 15.0
_BUFFER_SIZE = 8192

# Texte fuer die Direktverbindung ohne Lobby: hier gibt es keinen Warteraum, der
# Spieler muss die Meldung VOR der 30-s-Grenze bekommen.
_MSG_STARTING = "Server startet noch - bitte in ~30 Sekunden erneut verbinden."
_MSG_START_TRIGGERED = (
    "Server startet gerade (dauert bei diesem Modpack rund eine Minute). "
    "Bitte in ~45 Sekunden erneut verbinden - oder ueber die Lobby beitreten, "
    "dort wartest du im Spiel und wirst automatisch verbunden."
)
_MSG_UNREACHABLE = "Server nicht erreichbar. Bitte erneut verbinden."


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

# Server-IDs mit laufendem Weckvorgang (Hintergrund-Thread). Dedupe spart nur
# Threads bei einem ungeduldig mehrfach klickenden Spieler - start_server ist
# selbst idempotent ("Startvorgang laeuft bereits.").
_WAKE_PENDING: set[int] = set()
# Letzte Startfehlermeldung je Server. start_server laeuft im Hintergrund, der
# Fehler entsteht also NACH der Rueckkehr von request_wake; die Warteschleife
# holt ihn hier ab und sagt dem Spieler sofort Bescheid, statt das ganze
# Zeitbudget stumm abzuwarten (z.B. "Serverordner existiert nicht.").
_WAKE_ERRORS: dict[int, str] = {}

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
            # Oeffentlicher Wake-Proxy: Direkt-Verbindungen (Port/Domain) wecken + erreichen den
            # Server. Sicher, weil Backends modern forwarding mit Secret nutzen (verwerfen fremde
            # Direktverbindungen) und Nicht-Backends (Spigot/Modded) online-mode laufen (Mojang-Auth).
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
# Wecken (nicht blockierend)
# --------------------------------------------------------------------------- #
def request_wake(server_id: int) -> tuple[bool, str]:
    """Serverstart anstossen und SOFORT zurueckkehren.

    Der eigentliche Start laeuft in einem Hintergrund-Thread, weil
    ``process_service.start_server`` synchron und langsam ist (Modpack-Install,
    Java-Prep, Client-Mod-Quarantaene ueber UNC, Loader-Installer, Plugin-
    Refresh - alles vor ``subprocess.Popen``). Wuerde das einen Lobby-
    ``join_check`` blockieren, reisst dort der Client-Timeout, die Pruefung
    faellt fail-open durch und erlaubt genau den Transfer auf den noch toten
    Server, den wir verhindern wollen.

    True heisst "Start laeuft oder Server laeuft schon", nicht "fertig".
    """
    if process_service.is_running(server_id):
        return True, "laeuft bereits"

    with _IDLE_LOCK:
        if server_id in _WAKE_PENDING:
            return True, "Weckvorgang laeuft bereits"
        _WAKE_PENDING.add(server_id)
        _WAKE_ERRORS.pop(server_id, None)

    try:
        # Existenzpruefung synchron (ein lokaler DB-Zugriff, vernachlaessigbar):
        # ein unbekannter Server soll sofort als Fehler zurueckkommen und nicht
        # erst ueber die Warteschleife auffallen.
        with SessionLocal() as db:
            if db.get(Server, server_id) is None:
                with _IDLE_LOCK:
                    _WAKE_PENDING.discard(server_id)
                return False, "Server nicht gefunden."
    except Exception as exc:  # noqa: BLE001 - DB-Fehler darf den Proxy nicht toeten
        with _IDLE_LOCK:
            _WAKE_PENDING.discard(server_id)
        return False, f"Datenbankfehler: {exc!r}"

    Thread(
        target=_wake_worker,
        args=(server_id,),
        daemon=True,
        name=f"sleep-wake-{server_id}",
    ).start()
    return True, "Serverstart angestossen"


def _wake_worker(server_id: int) -> None:
    """Hintergrund-Thread: start_server aufrufen, Fehler hinterlegen."""
    try:
        with SessionLocal() as db:
            server = db.get(Server, server_id)
            if server is None:
                _set_wake_error(server_id, "Server nicht gefunden.")
                return
            ok, message = process_service.start_server(
                db, server, initiated_by_user_id=None
            )
        if ok:
            _log(server_id, "sleep_proxy.wake", "login trigger")
        elif "bereits" in message.lower():
            # Paralleler Start (anderer Spieler, Zeitplan) -> kein Fehler.
            _log(server_id, "sleep_proxy.wake", f"already starting: {message}")
        else:
            _set_wake_error(server_id, message)
            _log(server_id, "sleep_proxy.wake_failed", message)
    except Exception as exc:  # noqa: BLE001 - Thread darf nie mit Traceback enden
        _set_wake_error(server_id, repr(exc))
        _log(server_id, "sleep_proxy.wake_failed", repr(exc))
    finally:
        with _IDLE_LOCK:
            _WAKE_PENDING.discard(server_id)


def _build_login_plugin_request(message_id: int) -> bytes:
    """Ein folgenloses Login-Paket, nur um die 30-s-Uhr des Clients zurueckzusetzen.

    Kanalname ist bewusst eigen ("mcsm:wake"): der Client kennt ihn nicht, antwortet
    mit "nicht verstanden" und bleibt im Login-Zustand - genau das Verhalten, das wir
    brauchen. Die Antwort wird spaeter aus dem Puffer geschnitten, siehe
    _consume_client_packets.
    """
    payload = (
        mc_protocol.encode_varint(_LOGIN_CB_PLUGIN_REQUEST)
        + mc_protocol.encode_varint(message_id)
        + mc_protocol.encode_string("mcsm:wake")
    )
    return mc_protocol._wrap_packet(payload)


def _consume_client_packets(pending: bytearray, scratch: bytearray) -> None:
    """Vollstaendige Pakete aus ``scratch`` nach ``pending`` uebernehmen - ausser
    den Antworten auf unsere Heartbeats.

    Die haben wir erfunden; das Backend hat sie nie angefragt. Blieben sie im
    Puffer, kickte das Ziel den Spieler mit "Unexpected custom data from client" -
    und zwar genau in der Sekunde, in der es endlich bereit ist.

    Unvollstaendige Pakete bleiben in ``scratch`` liegen, bis der Rest da ist.
    """
    while scratch:
        try:
            length, body = mc_protocol.read_varint(bytes(scratch), 0)
        except mc_protocol.IncompletePacket:
            return
        except Exception:  # noqa: BLE001 - unparsbarer Strom: nicht fehlleiten
            pending.extend(scratch)
            scratch.clear()
            return
        if length <= 0 or len(scratch) - body < length:
            return
        packet = bytes(scratch[body:body + length])
        framed = bytes(scratch[:body + length])
        del scratch[:body + length]
        try:
            packet_id, _ = mc_protocol.read_varint(packet, 0)
        except Exception:  # noqa: BLE001 - im Zweifel weitergeben
            pending.extend(framed)
            continue
        if packet_id == _LOGIN_SB_PLUGIN_RESPONSE:
            continue
        pending.extend(framed)


def _set_wake_error(server_id: int, message: str) -> None:
    with _IDLE_LOCK:
        _WAKE_ERRORS[server_id] = message


def _take_wake_error(server_id: int) -> str | None:
    """Fehler abholen UND entfernen, damit der naechste Versuch neu startet."""
    with _IDLE_LOCK:
        return _WAKE_ERRORS.pop(server_id, None)


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
    # Die 30-s-Geduld des Clients laeuft ab der VERBINDUNGSANNAHME, nicht erst ab
    # dem Start. Deshalb wird das Zeitbudget hier festgenagelt - sonst verbraucht
    # das Handshake-Lesen unbemerkt einen Teil davon.
    accepted_at = monotonic()
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
        join = handshake.next_state in mc_protocol.JOIN_NEXT_STATES
        if handshake.next_state == mc_protocol.NEXT_STATE_STATUS and not running:
            _respond_sleeping_status(client, buffer, handshake)
            return
        # Login (2) ODER Transfer (3, seit 1.20.5): ein echter Spieler will rein ->
        # schlafenden Server wecken. Ohne den Transfer-Fall bleibt ein per Lobby
        # weitergereichter Client haengen (Backend ist noch aus).
        if join and not running:
            if not _wake_server(
                server_id,
                client,
                pending=buffer,
                # Echte Client-Version: ViaProxy sitzt nur auf der Default-Route,
                # ein expliziter Server-Alias kommt unuebersetzt hier an. Davon
                # haengt ab, ob wir halten koennen oder absagen muessen.
                protocol_version=handshake.protocol_version,
                accepted_at=accepted_at,
            ):
                return  # Timeout/Fehler -> Client wurde informiert/geschlossen
        # Ab hier laeuft der Server (oder wurde geweckt) -> transparent forwarden.
        _forward(listener, client, bytes(buffer), join=join)
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


def _wake_server(
    server_id: int,
    client: socket.socket,
    *,
    deadline: float | None = None,
    pending: bytearray | None = None,
    protocol_version: int | None = None,
    accepted_at: float | None = None,
) -> bool:
    """Server wecken und begrenzt auf Bereitschaft warten.

    ``deadline`` (monotonic) kommt vom Aufrufer, gemessen ab Verbindungsannahme.
    ``pending`` ist der bisher gelesene Byte-Puffer: schickt der Client waehrend
    des Wartens weitere Bytes (z.B. Login Start in einem eigenen Segment),
    werden sie dort angehaengt und spaeter mitweitergeleitet.

    True -> Server ist bereit, Verbindung kann weitergeleitet werden.
    False -> Timeout/Fehler/Abbruch; der Client wurde ggf. mit Meldung getrennt.
    """
    ok, message = request_wake(server_id)
    if not ok and "bereits" not in message.lower():
        # Harte Startfehler sofort melden statt das Zeitbudget stumm abzuwarten.
        _send_login_disconnect(client, f"Serverstart nicht moeglich: {message}")
        return False

    # Kann dieser Client gehalten werden? Dann warten wir, bis der Server WIRKLICH
    # bereit ist, statt den Spieler wegzuschicken. Ohne Heartbeat bleibt nur das
    # kurze Zeitfenster, in dem die Absage ihn noch erreicht.
    hold = bool(protocol_version is not None and protocol_version >= _PROTOCOL_LOGIN_PLUGIN)
    scratch: bytearray | None = bytearray() if hold else None
    if deadline is None:
        budget = _HOLD_READY_TIMEOUT if hold else _WAKE_READY_TIMEOUT
        deadline = (accepted_at if accepted_at is not None else monotonic()) + budget
    if hold:
        _log(server_id, "sleep_proxy.hold", f"protocol={protocol_version}")

    message_id = 0
    next_beat = monotonic() + _HEARTBEAT_INTERVAL
    while monotonic() < deadline:
        if process_service.is_server_ready(server_id):
            return True
        error = _take_wake_error(server_id)
        if error:
            _send_login_disconnect(client, f"Serverstart nicht moeglich: {error}")
            return False
        if hold and monotonic() >= next_beat:
            message_id += 1
            try:
                client.sendall(_build_login_plugin_request(message_id))
            except OSError:
                _log(server_id, "sleep_proxy.wake_aborted", "client gone (heartbeat)")
                return False
            except Exception:  # noqa: BLE001 - Attrappen-Sockets in Tests
                pass
            next_beat = monotonic() + _HEARTBEAT_INTERVAL
        if not _client_still_waiting(client, pending, scratch):
            # Client weg (oder Muellflut) -> kein Backend-Socket mehr oeffnen.
            _log(server_id, "sleep_proxy.wake_aborted", "client gone")
            return False

    _send_login_disconnect(client, _MSG_START_TRIGGERED)
    return False


def _client_still_waiting(
    client: socket.socket,
    pending: bytearray | None = None,
    scratch: bytearray | None = None,
) -> bool:
    """Waehrend des Wartens am Client mitlesen -> Abbruch frueh erkennen.

    Nichts gelesen (Timeout) heisst "wartet noch". b"" oder OSError heisst
    "Verbindung ist weg". Gelesene Bytes gehoeren zum Login-Strom und duerfen
    NICHT verworfen werden, sonst kommt der Server-seitige Decoder aus dem Takt.

    ``scratch`` != None heisst "wir senden Heartbeats": dann laeuft alles Gelesene
    erst durch _consume_client_packets, das NUR die Antworten auf unsere eigenen
    Heartbeats herausschneidet. Ohne das kickte das Ziel den Spieler mit
    "Unexpected custom data from client", sobald es bereit ist.
    """
    try:
        client.settimeout(_WAKE_POLL_INTERVAL)
        chunk = client.recv(_BUFFER_SIZE)
    except socket.timeout:
        return True
    except OSError:
        return False
    except Exception:  # noqa: BLE001 - ein Socket ohne recv darf nicht abbrechen
        sleep(_WAKE_POLL_INTERVAL)
        return True
    if not chunk:
        return False
    if pending is not None:
        if scratch is not None:
            scratch.extend(chunk)
            _consume_client_packets(pending, scratch)
            if len(scratch) > _BUFFER_SIZE * 4:
                return False
        else:
            pending.extend(chunk)
        # Ein echter Login-Start ist winzig. Wer auf dem oeffentlichen Port
        # waehrend des Wartens Megabytes schiebt, soll nicht unseren Speicher
        # fuellen - solche Verbindung fallen lassen.
        if len(pending) > _BUFFER_SIZE * 4:
            return False
    return True


def _forward(
    listener: _ProxyListener,
    client: socket.socket,
    initial: bytes,
    *,
    join: bool = True,
) -> None:
    _forward_to_backend(
        client, listener.internal_port, listener.server_id, initial, join=join
    )


def _forward_to_backend(
    client: socket.socket,
    internal_port: int,
    server_id: int,
    initial: bytes,
    *,
    join: bool = True,
) -> None:
    """Client transparent an ``127.0.0.1:internal_port`` koppeln (Byte-Splicing).

    Gemeinsamer Baustein fuer den Sleep-Proxy (ein Backend) und das Gateway
    (viele Backends). ``join`` sagt, ob der Client einen Login/Transfer wollte -
    nur dann ist ein Login-Disconnect ueberhaupt ein gueltiges Paket.
    """
    try:
        backend = socket.create_connection(
            ("127.0.0.1", internal_port),
            timeout=_BACKEND_CONNECT_TIMEOUT,
        )
    except OSError as exc:
        _log(server_id, "sleep_proxy.backend_unreachable", repr(exc))
        if not join:
            # Status-Zustand: Paket 0x00 ist hier die Status-RESPONSE. Ein
            # Login-Disconnect wird vom Client als Status-JSON ohne version/
            # players gelesen -> kaputter Serverlisten-Eintrag (und mc_ping
            # haelt denselben Muell fuer "Server antwortet").
            return
        if process_service.is_running(server_id):
            # Prozess lebt, Port noch zu -> Server bootet gerade. Ohne diesen
            # Zweig wird beim Neuversuch waehrend des Bootens gar nicht gewartet
            # (is_running ist True), sondern blind auf den toten Port geforwardet.
            _send_login_disconnect(client, _MSG_STARTING)
        else:
            _send_login_disconnect(client, _MSG_UNREACHABLE)
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
    # Velocity ZUERST abgleichen (vor dem Gateway): beide konkurrieren um den network_port.
    # So gibt ein velocity->gateway-Wechsel den Port frei (Velocity stoppt), BEVOR das
    # Gateway im selben Tick zu binden versucht; ein gateway->velocity-Wechsel stoppt das
    # Gateway in reconcile_velocity selbst, bevor Velocity bindet.
    try:
        from app.services import proxy_service

        proxy_service.reconcile_velocity()   # Velocity (network_mode==velocity) selbstheilen
    except Exception:  # noqa: BLE001 - Monitor darf nie sterben
        pass
    try:
        from app.services import presence_bridge_service

        presence_bridge_service.reconcile_presence_bridge()  # verwaiste Avatare aufraeumen
    except Exception:  # noqa: BLE001
        pass
    # Gateway abgleichen: Listener nachbinden, Routing-Tabelle auffrischen.
    # Lazy-Import bricht den Zyklus gateway_service -> sleep_proxy_service.
    try:
        from app.services import gateway_service

        gateway_service.reconcile_gateway()
    except Exception:  # noqa: BLE001
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
