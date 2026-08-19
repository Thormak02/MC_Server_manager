"""Lobby-/Gateway-Routing-Proxy (Phase 1: Backend-Kern).

EIN MC-Eingangspunkt auf ``gateway_port``. Beim Verbindungsaufbau wird der
Handshake gelesen, anhand **Hostname-Alias** (Fallback: **Protokoll-Version**)
das Ziel-Backend gewaehlt und **transparent** weitergeleitet (inkl. Aufwecken
schlafender Server). Es gibt keinen begehbaren Cross-Version-Hub – das Gateway
ist ein unsichtbarer Router.

Baut auf ``sleep_proxy_service`` (Forward/Wake/Status-Antwort) und ``mc_protocol``
auf. Grundprinzipien:
- **Opt-in & standardmaessig AUS** (``gateway_enabled``). Solange das Gateway
  nicht gestartet wird, aendert sich fuer bestehende Server nichts.
- **Transparentes Byte-Splicing** (kein Protokoll-Eingriff) -> Forge/Fabric/
  Modpacks funktionieren, weil der Client bereits zum Ziel passt.
- **Bind ohne SO_REUSEADDR** (korrekte Port-Belegung, auch unter Windows).

Reine Routing-Logik (``clean_hostname``, ``decide_route``, ``build_gateway_routes``)
ist socketfrei und damit vollstaendig unit-testbar.
"""

from __future__ import annotations

import socket
import time
from dataclasses import dataclass, field
from threading import Event, RLock, Thread

from sqlalchemy import select

from app.services import mc_protocol, process_service, sleep_proxy_service

_HANDSHAKE_READ_TIMEOUT = 5.0
_BUFFER_SIZE = 8192

# mc_version -> Wire-Protokollversion (Best-Effort, gaengige Versionen). Dient nur
# dem *Versions-Fallback*, wenn kein Alias passt und kein Default/Lobby gesetzt
# ist. Unbekannte Versionen nehmen am Fallback einfach nicht teil (die Apex-/
# Default-Route deckt sie ab).
_MC_PROTOCOL_VERSIONS: dict[str, int] = {
    "1.7.2": 4, "1.7.10": 5,
    "1.8": 47, "1.8.1": 47, "1.8.2": 47, "1.8.8": 47, "1.8.9": 47,
    "1.9": 107, "1.9.1": 108, "1.9.2": 109, "1.9.3": 110, "1.9.4": 110,
    "1.10": 210, "1.10.1": 210, "1.10.2": 210,
    "1.11": 315, "1.11.1": 316, "1.11.2": 316,
    "1.12": 335, "1.12.1": 338, "1.12.2": 340,
    "1.13": 393, "1.13.1": 401, "1.13.2": 404,
    "1.14": 477, "1.14.1": 480, "1.14.2": 485, "1.14.3": 490, "1.14.4": 498,
    "1.15": 573, "1.15.1": 575, "1.15.2": 578,
    "1.16": 735, "1.16.1": 736, "1.16.2": 751, "1.16.3": 753, "1.16.4": 754, "1.16.5": 754,
    "1.17": 755, "1.17.1": 756,
    "1.18": 757, "1.18.1": 757, "1.18.2": 758,
    "1.19": 759, "1.19.1": 760, "1.19.2": 760, "1.19.3": 761, "1.19.4": 762,
    "1.20": 763, "1.20.1": 763, "1.20.2": 764, "1.20.3": 765, "1.20.4": 765,
    "1.20.5": 766, "1.20.6": 766,
    "1.21": 767, "1.21.1": 767, "1.21.2": 768, "1.21.3": 768, "1.21.4": 769,
    "1.21.5": 770,
}


def protocol_for_mc_version(mc_version: str | None) -> int | None:
    key = str(mc_version or "").strip()
    return _MC_PROTOCOL_VERSIONS.get(key)


def clean_hostname(raw: str | None) -> str:
    """Hostnamen aus dem Handshake normalisieren.

    - Forge haengt einen FML-Marker an (``host\\0FML\\0`` / ``\\0FML2\\0`` / ...);
      nur der Teil **vor dem ersten \\0** ist der echte Hostname.
    - Trailing-Dot (FQDN) entfernen, Kleinschreibung.
    """
    if not raw:
        return ""
    head = str(raw).split("\x00", 1)[0]
    return head.strip().rstrip(".").lower()


@dataclass(frozen=True)
class GatewayRoutes:
    by_hostname: dict[str, int] = field(default_factory=dict)
    by_version: dict[int, int] = field(default_factory=dict)
    default_server_id: int | None = None
    internal_ports: dict[int, int] = field(default_factory=dict)
    aliases: tuple[str, ...] = ()
    domain: str = ""


@dataclass(frozen=True)
class RouteDecision:
    server_id: int | None
    reason: str  # "alias" | "default" | "version" | "none"


def decide_route(
    hostname: str | None,
    protocol_version: int | None,
    routes: GatewayRoutes,
) -> RouteDecision:
    """Ziel-Server fuer eine Verbindung bestimmen.

    Das Schema ist ``<alias>.<domain>`` (der Alias selbst darf Punkte enthalten,
    z.B. ``1.21.11-spigot``). Domain-verankertes Matching:
      (1) exakter Match des ganzen Hostnamens (voll-qualifizierte Aliase);
      (2) Domain gesetzt:
          - Hostname == Domain (Apex) -> KEIN Alias, faellt auf Default/Lobby;
          - Hostname endet auf ``.<domain>`` -> der Teil davor ist der Alias
            (exakt; ein unbekannter Alias faellt auf Default, wird NICHT auf ein
            kuerzeres Praefix wie ``play`` fehlgeleitet);
          - sonst (fremde Domain/IP) -> kein Alias, Default;
      (3) Domain NICHT gesetzt: erstes DNS-Label als Komfort-Fallback.
    Danach: (4) Apex/unbekannt -> Default/Lobby; (5) Versions-Fallback (nur wenn
    diese Protokollversion eindeutig einem Server zugeordnet ist).
    """
    cleaned = clean_hostname(hostname)
    if cleaned:
        server_id = routes.by_hostname.get(cleaned)
        if server_id is None:
            if routes.domain:
                suffix = "." + routes.domain
                if cleaned == routes.domain:
                    server_id = None  # Apex -> Default (kein Alias-Match)
                elif cleaned.endswith(suffix) and len(cleaned) > len(suffix):
                    # Unter unserer Domain: Alias ist exakt der Teil davor.
                    # Unbekannt -> Default, NICHT auf ein kuerzeres Label raten.
                    server_id = routes.by_hostname.get(cleaned[: -len(suffix)])
            else:
                # Ohne konfigurierte Domain: erstes Label als Komfort-Fallback.
                first_label = cleaned.split(".", 1)[0]
                if first_label != cleaned:
                    server_id = routes.by_hostname.get(first_label)
        if server_id is not None:
            return RouteDecision(server_id, "alias")

    if routes.default_server_id is not None:
        return RouteDecision(routes.default_server_id, "default")

    try:
        version = int(protocol_version)
    except (TypeError, ValueError):
        version = -1
    server_id = routes.by_version.get(version)
    if server_id is not None:
        return RouteDecision(server_id, "version")

    return RouteDecision(None, "none")


def build_gateway_routes(db) -> GatewayRoutes:
    """Routing-Tabelle aus allen Servern mit ``gateway_enabled=True`` bauen.

    Das Gateway ist ein **reiner Router**: es leitet transparent auf den
    OEFFENTLICHEN Port des Servers (``server.port``) weiter. Die Server bleiben
    damit normal + **direkt erreichbar** (jeder Typ/jede Version). Server werden
    NICHT auf interne Ports verschoben. Das Wecken schlafender Server uebernimmt
    der Sleep-Proxy, der auf demselben oeffentlichen Port sitzt.
    """
    from app.models.server import Server
    from app.services import app_setting_service

    domain = clean_hostname(app_setting_service.get_network_domain(db))

    servers = list(
        db.scalars(select(Server).where(Server.gateway_enabled.is_(True))).all()
    )

    by_hostname: dict[str, int] = {}
    target_ports: dict[int, int] = {}  # Weiterleitungsziel = oeffentlicher Server-Port
    aliases: list[str] = []
    default_server_id: int | None = None
    version_map: dict[int, set[int]] = {}

    for server in servers:
        if not server.port:
            continue
        alias = clean_hostname(server.gateway_hostname)
        if alias:
            by_hostname[alias] = server.id
            aliases.append(alias)
        if server.gateway_is_default:
            default_server_id = server.id
        target_ports[server.id] = int(server.port)

        version = protocol_for_mc_version(server.mc_version)
        if version is not None:
            version_map.setdefault(version, set()).add(server.id)

    # Versions-Fallback nur fuer eindeutige Zuordnungen.
    by_version = {
        version: next(iter(ids))
        for version, ids in version_map.items()
        if len(ids) == 1
    }

    return GatewayRoutes(
        by_hostname=by_hostname,
        by_version=by_version,
        default_server_id=default_server_id,
        internal_ports=target_ports,
        aliases=tuple(sorted(set(aliases))),
        domain=domain,
    )


# --------------------------------------------------------------------------- #
# Listener-Lifecycle
# --------------------------------------------------------------------------- #
@dataclass
class _GatewayListener:
    port: int
    sock: socket.socket
    stop_event: Event = field(default_factory=Event)
    thread: Thread | None = None


_GATEWAY: _GatewayListener | None = None
_GATEWAY_LOCK = RLock()
# Bind-Fehler nur einmal je Episode loggen (reconcile laeuft alle 15s).
_BIND_FAILED = False

# Gecachte Routing-Tabelle. Wird per reconcile_gateway aus der DB neu gebaut, damit
# der Verbindungspfad NICHT bei jeder Connection die DB anfassen muss.
_ROUTES_CACHE: GatewayRoutes | None = None
_ROUTES_LOCK = RLock()


def _set_routes_cache(routes: GatewayRoutes | None) -> None:
    global _ROUTES_CACHE
    with _ROUTES_LOCK:
        _ROUTES_CACHE = routes


def _glog(action: str, details: str) -> None:
    from app.db.session import SessionLocal
    from app.services import audit_service

    try:
        with SessionLocal() as db:
            audit_service.log_action(db, action=action, server_id=None, details=details)
    except Exception:  # noqa: BLE001 - Logging darf das Gateway nie stoeren
        pass


def start_gateway(port: int) -> bool:
    """Gateway-Listener auf ``port`` binden und bedienen (ohne Flag-Pruefung).

    Der Opt-in-Check liegt beim Aufrufer (siehe ``start_gateway_if_enabled``).
    """
    global _GATEWAY, _BIND_FAILED
    with _GATEWAY_LOCK:
        if _GATEWAY is not None and _GATEWAY.port == int(port):
            return True
        if _GATEWAY is not None:
            _stop_locked()
        try:
            # Bewusst OHNE SO_REUSEADDR (siehe sleep_proxy_service.start_proxy).
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind(("0.0.0.0", int(port)))
            sock.listen(128)
        except OSError as exc:
            if not _BIND_FAILED:
                _glog(
                    "gateway.bind_failed",
                    f"port={port} error={exc!r} (Port belegt? bindet nach, sobald frei.)",
                )
                _BIND_FAILED = True
            return False
        _BIND_FAILED = False
        listener = _GatewayListener(port=int(port), sock=sock)
        thread = Thread(
            target=_accept_loop, args=(listener,), daemon=True, name="mc-gateway"
        )
        listener.thread = thread
        _GATEWAY = listener
        thread.start()
        _glog("gateway.started", f"port={port}")
        return True


def start_gateway_if_enabled() -> bool:
    from app.services.app_setting_service import (
        get_network_mode_runtime,
        get_network_port_runtime,
    )

    if get_network_mode_runtime() != "gateway":
        return False
    return start_gateway(get_network_port_runtime())


def reconcile_gateway() -> None:
    """Listener + Routing-Tabelle an DB/Settings angleichen (Selbstheilung).

    - Gateway aktiviert: Routing-Cache aus der DB neu bauen (allokiert ggf. interne
      Ports) und den Listener sicherstellen (Bind wird nachgeholt, sobald der Port
      frei ist).
    - Gateway deaktiviert: Listener stoppen, Cache leeren.

    Idempotent -> laeuft gefahrlos bei jedem Idle-Tick und nach Settings-Aenderungen.
    An/Aus und Port stammen aus den (UI-ueberschreibbaren) Laufzeit-Settings.
    """
    from app.services.app_setting_service import (
        get_network_port_runtime,
        get_network_mode_runtime,
    )

    # Der Netzwerk-Modus ist die einzige Wahrheit ueber die Eingangstuer: Das
    # Gateway laeuft ausschliesslich im Modus "gateway" (bei "off" gibt es
    if get_network_mode_runtime() != "gateway":
        if is_running():
            stop_gateway()
        _set_routes_cache(None)
        return

    from app.db.session import SessionLocal

    try:
        with SessionLocal() as db:
            routes = build_gateway_routes(db)
        _set_routes_cache(routes)
    except Exception as exc:  # noqa: BLE001 - Routing-Fehler darf App nicht stoeren
        _glog("gateway.reconcile_routes_failed", repr(exc))

    # Listener sicherstellen (idempotent; bindet nach, sobald der Port frei ist).
    start_gateway(get_network_port_runtime())


def stop_gateway() -> None:
    with _GATEWAY_LOCK:
        _stop_locked()


def _stop_locked() -> None:
    global _GATEWAY
    listener = _GATEWAY
    _GATEWAY = None
    if listener is None:
        return
    listener.stop_event.set()
    try:
        listener.sock.close()
    except OSError:
        pass


def is_running() -> bool:
    with _GATEWAY_LOCK:
        return _GATEWAY is not None


def gateway_status_runtime() -> dict:
    """Diagnose-Snapshot des Gateways fuer die UI.

    Zeigt, welche Aliase auf welche Server zeigen (die Routing-Tabelle) sowie ob
    der Listener laeuft. So sieht man sofort, ob ein Server (z.B. david) wirklich
    im Gateway registriert ist oder ob alle Verbindungen auf den Default fallen.
    """
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service

    status: dict = {
        "mode": "off",
        "running": is_running(),
        "domain": None,
        "port": None,
        "routes": [],
        "default": None,
    }
    try:
        with SessionLocal() as db:
            status["mode"] = app_setting_service.get_network_mode(db)
            status["domain"] = app_setting_service.get_network_domain(db)
            status["port"] = app_setting_service.get_network_port(db)
            if status["mode"] != "gateway":
                return status
            servers = db.scalars(
                select(Server).where(Server.gateway_enabled.is_(True))
            ).all()
            for srv in servers:
                alias = clean_hostname(srv.gateway_hostname)
                status["routes"].append(
                    {
                        "server": srv.name,
                        "alias": alias,
                        "is_default": bool(srv.gateway_is_default),
                        "port": srv.port,
                        "valid": bool(alias) and bool(srv.port),
                    }
                )
                if srv.gateway_is_default:
                    status["default"] = srv.name
    except Exception as exc:  # noqa: BLE001 - Diagnose darf nie crashen
        status["error"] = repr(exc)
    return status


def _accept_loop(listener: _GatewayListener) -> None:
    sock = listener.sock
    while not listener.stop_event.is_set():
        try:
            client, _addr = sock.accept()
        except OSError as exc:
            if listener.stop_event.is_set():
                break  # regulaerer Stop: Socket wurde von _stop_locked geschlossen
            # Transienter accept-Fehler (z.B. ECONNRESET/EMFILE) -> Listener am
            # Leben halten, kurzer Backoff gegen Busy-Loop bei Dauerfehlern.
            _glog("gateway.accept_error", repr(exc))
            time.sleep(0.05)
            continue
        Thread(
            target=_handle_gateway_connection,
            args=(client,),
            daemon=True,
            name="mc-gateway-conn",
        ).start()


def _current_routes() -> GatewayRoutes:
    """Aktuelle Routing-Tabelle (aus dem Cache; Reconcile haelt ihn frisch).

    Fallback: ist der Cache noch leer (z.B. erste Connection vor dem ersten
    Reconcile-Tick), einmalig aus der DB bauen und cachen.
    """
    global _ROUTES_CACHE
    with _ROUTES_LOCK:
        cached = _ROUTES_CACHE
    if cached is not None:
        return cached

    from app.db.session import SessionLocal

    with SessionLocal() as db:
        routes = build_gateway_routes(db)
    # Compare-and-set: einen inzwischen von reconcile_gateway geschriebenen (frischeren)
    # Cache NICHT ueberschreiben -> Reconcile-Writes gewinnen immer.
    with _ROUTES_LOCK:
        if _ROUTES_CACHE is None:
            _ROUTES_CACHE = routes
        return _ROUTES_CACHE


def _handle_gateway_connection(client: socket.socket) -> None:
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

        try:
            routes = _current_routes()
        except Exception as exc:  # noqa: BLE001 - DB-Fehler/Port-Erschoepfung
            # Darf den Verbindungs-Thread nicht mit einem Traceback beenden.
            _glog("gateway.route_build_failed", repr(exc))
            if handshake.next_state == mc_protocol.NEXT_STATE_LOGIN:
                sleep_proxy_service._send_login_disconnect(
                    client,
                    "Netzwerk voruebergehend nicht erreichbar. Bitte erneut verbinden.",
                )
            return
        decision = decide_route(
            handshake.server_address, handshake.protocol_version, routes
        )
        target_port = (
            routes.internal_ports.get(decision.server_id)
            if decision.server_id is not None
            else None
        )
        if decision.server_id is None or not target_port:
            _respond_no_route(client, buffer, handshake, routes)
            return

        # Reiner Router: transparent auf den OEFFENTLICHEN Server-Port weiterleiten.
        # Status/Wecken uebernimmt der Sleep-Proxy, der ggf. auf diesem Port sitzt
        # (bei laufenden Servern trifft der Forward direkt den Server).
        sleep_proxy_service._forward_to_backend(
            client, int(target_port), decision.server_id, bytes(buffer)
        )
    except OSError:
        pass
    except Exception as exc:  # noqa: BLE001 - eine Verbindung darf den Listener nie stoeren
        _glog("gateway.connection_error", repr(exc))
    finally:
        sleep_proxy_service._safe_close(client)


def _respond_no_route(
    client: socket.socket,
    buffer: bytearray,
    handshake: mc_protocol.Handshake,
    routes: GatewayRoutes,
) -> None:
    if handshake.next_state == mc_protocol.NEXT_STATE_LOGIN:
        if routes.aliases:
            message = (
                "Kein Server fuer diese Adresse. Verfuegbar: "
                + ", ".join(routes.aliases)
            )
        else:
            message = "Kein Server fuer diese Adresse konfiguriert."
        sleep_proxy_service._send_login_disconnect(client, message)
        return

    # Status-Ping ohne Route -> Netzwerk-MOTD mit den verfuegbaren Servern.
    if routes.aliases:
        motd = "§6Netzwerk §7| Server: §f" + ", ".join(routes.aliases)
    else:
        motd = "§6Netzwerk §7– verbinde dich mit deiner Server-Adresse"
    status_json = mc_protocol.build_status_json(
        motd=motd,
        version_name="Gateway",
        protocol_version=handshake.protocol_version,
        players_online=0,
        players_max=0,
    )
    try:
        client.sendall(mc_protocol.build_status_response_packet(status_json))
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
