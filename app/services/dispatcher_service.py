"""Modpack-Dispatcher.

Nimmt Verbindungen an der blanken Domain im **Offline-Mode** an, erkennt den Client
(Vanilla vs. modded) und leitet per **Transfer-Paket** an den passenden Backend-Server
weiter - modded Clients an ihren Modpack-Server, Vanilla an die Lobby. Der Dispatcher
haelt nie eine Spielsitzung; die echte Auth passiert am Ziel-Backend (das braucht
``accepts-transfers=true`` - setzt der Manager fuer Gateway-Server bereits).

Ablauf (MC 1.21.x, Protokoll 767+):
  Handshake (schon vom Gateway gelesen) -> LoginStart -> LoginSuccess -> LoginAck
  -> Config: Brand/register lesen; modded? -> neoforge:register-Query -> Manifest ->
     Mod-Namespaces -> Backend matchen -> Transfer  (sonst Disconnect mit Hinweis).
"""

from __future__ import annotations

import socket
from time import monotonic

from app.services import mc_dispatch as mcd
from app.services.mc_protocol import Handshake, ProtocolError

_LOGIN_TIMEOUT = 8.0
_CONFIG_READ_TIMEOUT = 4.0
_MAX_CONFIG_PACKETS = 12
# Puffer-Obergrenze je Verbindung (DoS-Schutz gegen langsames Vollmuellen).
_MAX_BUFFER = 4 * 1024 * 1024

# Fehler, die einen Lese-Versuch beenden (Socket zu, kaputtes/boesartiges Paket).
_READ_ERRORS = (OSError, ConnectionError, ProtocolError)


class _Reader:
    """Liest ganze (unkomprimierte) Pakete aus Puffer + Socket.

    ``timeout`` ist eine **Gesamt-Deadline** (nicht nur pro recv), damit ein Client,
    der Bytes nur tropfenweise sendet, den Thread nicht endlos beschaeftigt.
    """

    def __init__(self, sock: socket.socket, initial: bytes = b"") -> None:
        self.sock = sock
        self.buf = bytearray(initial)

    def read_packet(self, timeout: float) -> tuple[int, bytes]:
        deadline = monotonic() + timeout
        while True:
            got = mcd.try_read_packet(bytes(self.buf))
            if got is not None:
                pid, fields, consumed = got
                del self.buf[:consumed]
                return pid, fields
            if len(self.buf) > _MAX_BUFFER:
                raise ProtocolError("Puffer-Limit ueberschritten")
            remaining = deadline - monotonic()
            if remaining <= 0:
                raise ConnectionError("Lese-Timeout")
            self.sock.settimeout(remaining)
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("Verbindung geschlossen")
            self.buf.extend(chunk)


def _glog(event: str, detail: str = "") -> None:
    try:
        from app.services import gateway_service

        gateway_service._glog(f"dispatcher.{event}", detail)  # gemeinsames Logging
    except Exception:  # noqa: BLE001
        pass


def dispatch(client: socket.socket, handshake: Handshake, initial: bytes) -> None:
    """Eine an der blanken Domain eingehende Join-Verbindung bedienen."""
    proto = int(handshake.protocol_version or mcd.PROTOCOL_1_21)
    reader = _Reader(client, initial)
    try:
        # --- Login (offline) ---
        pid, fields = reader.read_packet(_LOGIN_TIMEOUT)
        if pid != mcd.LOGIN_START:
            return
        username, uuid16 = mcd.parse_login_start(fields)
        client.sendall(mcd.build_login_success(uuid16, username or "Player", proto))
        pid, _ = reader.read_packet(_LOGIN_TIMEOUT)
        if pid != mcd.LOGIN_ACK:
            return

        # --- Config: Client-Eroeffnung lesen (Brand + register) ---
        brand, channels = _read_client_opening(reader)
        # NeoForge/Forge brauchen ein Pack-Replay; Fabric/Quilt/Vanilla sind lenient und
        # teilen sich das Vanilla-Replay -> nur echte NeoForge/Forge in den Modpack-Zweig.
        neoforge = mcd.is_neoforge_client(brand, channels)

        from app.db.session import SessionLocal

        with SessionLocal() as db:
            from app.services import app_setting_service, gateway_service

            if not neoforge:
                # Vanilla ODER Fabric/Quilt -> gemeinsame Lobby ueber das Vanilla-Profil,
                # aber nur wenn ein Vanilla-Config-Replay konfiguriert ist (sonst wuerde
                # der Hub sie mit dem Modpack-Spoof kicken).
                if (app_setting_service.get_hub_lobby_enabled(db)
                        and app_setting_service.get_hub_lobby_vanilla_replay(db)):
                    vt = _hub_target(db, gateway_service.HUB_VANILLA_ALIAS)
                    if vt:
                        _finish(client, vt, _no_lobby_message())
                        _glog("route_vanilla_hub", str(vt))
                        return
                target = _server_target(db, _lobby_id(db))
                _finish(client, target, _no_lobby_message())
                _glog("route_vanilla", str(target))
                return

            # --- NeoForge/Forge: Mod-Manifest anfragen + passenden Pack matchen ---
            client.sendall(mcd.build_neoforge_query())
            mods = _read_neoforge_manifest(reader)
            from app.services.modpack_router_service import match_backend_for_client

            sid, reason = match_backend_for_client(db, mods)

            # In die gemeinsame Python-Lobby (Kompass-Menue). Auswahl (Path A):
            #  - Pack hat EIGENES Replay -> modlobby-<server_id> (exakter Spoof)
            #  - gematchter Server ohne eigenes Replay -> DIESEN Server aufnehmen
            #    (NIE ein fremdes/aehnliches Replay servieren: Registry-Sync ist exakt,
            #     eine fehlende Registry kickt den Client -> "tide:... not found").
            #  - kein passender Server -> nichts zum Aufnehmen: Default nur bei Voll-
            #    Abdeckung, sonst klarer Hinweis-Disconnect.
            if app_setting_service.get_hub_lobby_enabled(db):
                from app.models.server import Server
                from app.services import hub_replay_service

                if sid is not None:
                    srv = db.get(Server, sid)
                    slug = srv.slug if srv is not None else None
                    if slug and hub_replay_service.has_replay(slug):
                        _finish(client, _hub_target(db, f"{gateway_service.HUB_LOBBY_ALIAS}-{sid}"),
                                _no_match_message(db))
                        _glog("route_modded_hub", f"server={sid} tagged ({reason})")
                        return
                    # Kein eigenes (exaktes) Replay -> diesen Server einmalig aufnehmen.
                    _route_auto_capture(client, db, sid, srv)
                    return
                # Kein Server gematcht -> Default-Profil nur, wenn der Client es VOLL
                # abdeckt (sonst Registry-Kick), andernfalls klarer Hinweis.
                default_replay = app_setting_service.get_hub_lobby_replay(db)
                if hub_replay_service.client_matches_replay(mods, default_replay, threshold=1.0):
                    _finish(client, _hub_target(db, gateway_service.HUB_LOBBY_ALIAS),
                            _no_match_message(db))
                    _glog("route_modded_hub_default", "server=None matches-default (voll)")
                    return
                client.sendall(mcd.build_config_disconnect(_no_pack_message(db)))
                _glog("route_modded_hub_nomatch", f"server=None ({reason}) mods={len(mods)}")
                return

            # --- NeoForge/Forge ohne Hub: direkt zum passenden Modpack-Server ---
            if sid is not None:
                target = _server_target(db, sid)
                _finish(client, target, _no_match_message(db))
                _glog("route_modded", f"server={sid} ({reason}) mods={len(mods)}")
            else:
                client.sendall(mcd.build_config_disconnect(_no_match_message(db)))
                _glog("route_modded_nomatch", f"{reason} mods={len(mods)}")
    except (OSError, ConnectionError, ProtocolError) as exc:
        _glog("connection_error", repr(exc))
    except Exception as exc:  # noqa: BLE001 - Dispatcher darf nie den Thread crashen
        _glog("unexpected_error", repr(exc))
    finally:
        try:
            client.close()
        except OSError:
            pass


def _finish(client: socket.socket, target: tuple[str, int] | None, fallback_msg: str) -> None:
    if target:
        client.sendall(mcd.build_config_transfer(target[0], target[1]))
    else:
        client.sendall(mcd.build_config_disconnect(fallback_msg))


def _route_auto_capture(client: socket.socket, db, sid: int, srv) -> None:
    """Auto-Capture eines Packs ohne Replay: Aufnahme anstossen und den Client mit klarer
    Meldung fuehren. Der erste Durchlauf dauert einmalig laenger (Pack-Server-Neustart)."""
    from app.services import app_setting_service, gateway_service, hub_capture_service

    name = srv.name if srv is not None else f"Pack {sid}"
    st = hub_capture_service.capture_status(sid)

    # Relay bereit -> Client zum Capture-Port lotsen (dort wird transparent aufgenommen).
    if st and st.get("status") == "waiting" and st.get("active"):
        domain = gateway_service.clean_hostname(app_setting_service.get_network_domain(db))
        port = st.get("relay_port")
        if domain and port:
            _finish(client, (domain, int(port)), "")
            _glog("auto_capture_forward", f"server={sid} -> {domain}:{port}")
            return

    if st and st.get("active"):
        client.sendall(mcd.build_config_disconnect(
            f"Ersteinrichtung von '{name}' laeuft (Server startet neu). "
            "Bitte in ~1-2 Minuten erneut verbinden."))
        _glog("auto_capture_wait", f"server={sid} status={st.get('status')}")
        return

    ok, _msg = hub_capture_service.start_capture_for_server(sid, None)
    client.sendall(mcd.build_config_disconnect(
        f"Modpack '{name}' wird erstmalig fuer die Lobby eingerichtet "
        "(Server startet kurz neu, ~1-2 Min). Bitte danach erneut verbinden - "
        "ab dann landest du in der gemeinsamen Lobby."))
    _glog("auto_capture_start", f"server={sid} ok={ok}")


def _read_client_opening(reader: _Reader) -> tuple[str, list[str]]:
    """Die vom Client unaufgefordert gesendeten Config-Pakete lesen, bis Brand +
    register bekannt sind (oder Timeout/Paketlimit)."""
    brand = ""
    channels: list[str] = []
    for _ in range(_MAX_CONFIG_PACKETS):
        try:
            pid, fields = reader.read_packet(_CONFIG_READ_TIMEOUT)
        except _READ_ERRORS:
            break
        if pid == mcd.CFG_SB_CUSTOM:
            channel, data = mcd.parse_custom_payload(fields)
            if channel == mcd.MINECRAFT_BRAND:
                brand = mcd.parse_brand(data)
            elif channel == mcd.MINECRAFT_REGISTER:
                channels = mcd.parse_register_channels(data)
        # Der Brand kommt frueh und unaufgefordert (vanilla wie modded) und genuegt fuer
        # die Entscheidung. NICHT auf minecraft:register warten - ein NeoForge-Client
        # sendet seine Mods NUR im neoforge:register-Manifest (auf unsere Query), also
        # wuerde Warten hier bis zum Timeout haengen.
        if brand:
            break
    return brand, channels


def _read_neoforge_manifest(reader: _Reader) -> set[str]:
    """Auf die neoforge:register-Antwort warten und die Mod-Namespaces extrahieren."""
    for _ in range(_MAX_CONFIG_PACKETS):
        try:
            pid, fields = reader.read_packet(_CONFIG_READ_TIMEOUT)
        except _READ_ERRORS:
            break
        if pid == mcd.CFG_SB_CUSTOM:
            channel, data = mcd.parse_custom_payload(fields)
            if channel == mcd.NEOFORGE_REGISTER:
                return mcd.extract_mod_namespaces(data)
    return set()


# --------------------------------------------------------------------------- #
# Ziel-Adressen (immer ueber das Gateway: <alias>.<domain>:<network_port>)
# --------------------------------------------------------------------------- #
def _lobby_id(db) -> int | None:
    from sqlalchemy import select

    from app.models.server import Server

    lobby = db.scalar(select(Server).where(Server.gateway_is_default.is_(True)))
    return lobby.id if lobby else None


def _server_target(db, server_id: int | None) -> tuple[str, int] | None:
    if server_id is None:
        return None
    from app.models.server import Server
    from app.services import app_setting_service, gateway_service

    srv = db.get(Server, server_id)
    if srv is None:
        return None
    alias = gateway_service.clean_hostname(srv.gateway_hostname)
    domain = gateway_service.clean_hostname(app_setting_service.get_network_domain(db))
    if not alias or not domain:
        return None
    return f"{alias}.{domain}", int(app_setting_service.get_network_port(db))


def _hub_target(db, alias: str) -> tuple[str, int] | None:
    """Transfer-Ziel der Universal-Lobby: ``<alias>.<domain>`` auf dem Netzwerk-Port.
    Das Gateway leitet modlobby/vanlobby an den lokalen Hub-Port weiter."""
    from app.services import app_setting_service, gateway_service

    domain = gateway_service.clean_hostname(app_setting_service.get_network_domain(db))
    if not domain:
        return None
    return f"{alias}.{domain}", int(app_setting_service.get_network_port(db))


def _modded_pack_hosts(db) -> list[str]:
    from sqlalchemy import select

    from app.models.server import Server
    from app.services import app_setting_service, gateway_service

    domain = gateway_service.clean_hostname(app_setting_service.get_network_domain(db))
    if not domain:
        return []
    hosts: list[str] = []
    for srv in db.scalars(select(Server).where(Server.gateway_enabled.is_(True))).all():
        if str(srv.server_type or "").lower() in ("forge", "neoforge", "fabric", "quilt"):
            alias = gateway_service.clean_hostname(srv.gateway_hostname)
            if alias:
                hosts.append(f"{alias}.{domain}")
    return hosts


def _no_match_message(db) -> str:
    hosts = _modded_pack_hosts(db)
    if hosts:
        return "Kein passendes Modpack erkannt. Verbinde dich direkt: " + ", ".join(hosts)
    return "Kein passender Modpack-Server gefunden."


def _no_pack_message(db) -> str:
    """Klarer Hinweis, wenn ein modded Client keinem eingerichteten Pack zugeordnet werden
    kann (statt ihn mit einem fremden Replay in einen Registry-Kick laufen zu lassen)."""
    hosts = _modded_pack_hosts(db)
    base = "Dein Modpack ist im Universal-Netz (noch) nicht eingerichtet."
    if hosts:
        return base + " Direkt verfuegbar: " + ", ".join(hosts)
    return base


def _no_lobby_message() -> str:
    return "Keine Lobby konfiguriert. Bitte verbinde dich mit deiner Server-Adresse."
