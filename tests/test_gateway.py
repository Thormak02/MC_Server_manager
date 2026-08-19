"""Tests fuer Phase 1 des Lobby-/Gateway-Routing-Proxys.

Routing-Entscheidung ist socketfrei und wird als reine Unit-Logik geprueft;
zusaetzlich ein Socket-Test fuer transparentes Forwarding uebers Gateway.
"""

import socket
import threading
import time

from app.services import mc_protocol as mp


def _handshake(hostname: str, next_state: int, port: int, protocol: int = 765) -> bytes:
    payload = (
        mp.encode_varint(0x00)
        + mp.encode_varint(protocol)
        + mp.encode_string(hostname)
        + int(port).to_bytes(2, "big")
        + mp.encode_varint(next_state)
    )
    return mp.encode_varint(len(payload)) + payload


# --------------------------------------------------------------------------- #
# Reine Routing-Logik
# --------------------------------------------------------------------------- #
def test_clean_hostname_normalizes_and_strips_fml():
    from app.services.gateway_service import clean_hostname

    assert clean_hostname("ATM10.Thormakmc.de") == "atm10.thormakmc.de"
    assert clean_hostname("atm10.thormakmc.de.") == "atm10.thormakmc.de"
    assert clean_hostname("atm10\x00FML2\x00") == "atm10"
    assert clean_hostname("host\x00FML\x00\x01") == "host"
    assert clean_hostname("") == ""
    assert clean_hostname(None) == ""


def test_decide_route_alias_exact_and_label():
    from app.services.gateway_service import GatewayRoutes, decide_route

    exact = GatewayRoutes(by_hostname={"atm10.thormakmc.de": 1})
    d = decide_route("atm10.thormakmc.de", 765, exact)
    assert d.server_id == 1 and d.reason == "alias"

    # Alias als reines Subdomain-Label -> matcht auch die volle FQDN.
    label = GatewayRoutes(by_hostname={"atm10": 2})
    d2 = decide_route("atm10.thormakmc.de", 765, label)
    assert d2.server_id == 2 and d2.reason == "alias"


def test_decide_route_strips_fml_marker():
    from app.services.gateway_service import GatewayRoutes, decide_route

    routes = GatewayRoutes(by_hostname={"atm10": 2})
    assert decide_route("atm10\x00FML2\x00", 765, routes).server_id == 2


def test_decide_route_default_for_apex_and_unknown():
    from app.services.gateway_service import GatewayRoutes, decide_route

    routes = GatewayRoutes(by_hostname={"atm10": 1}, default_server_id=9)
    assert decide_route("unknown.host", 765, routes).reason == "default"
    assert decide_route("thormakmc.de", 765, routes).server_id == 9  # Apex
    # Ein bekannter Alias hat weiterhin Vorrang vor Default.
    assert decide_route("atm10", 765, routes).server_id == 1


def test_decide_route_version_fallback_unique_vs_ambiguous():
    from app.services.gateway_service import GatewayRoutes, decide_route

    # Eindeutige Version, kein Default -> Versions-Fallback greift.
    unique = GatewayRoutes(by_version={763: 5}, default_server_id=None)
    d = decide_route("nope.host", 763, unique)
    assert d.server_id == 5 and d.reason == "version"

    # Mehrdeutige Versionen tauchen gar nicht erst in by_version auf -> kein Treffer.
    ambiguous = GatewayRoutes(by_version={}, default_server_id=None)
    assert decide_route("nope.host", 763, ambiguous).reason == "none"


def test_decide_route_no_match():
    from app.services.gateway_service import GatewayRoutes, decide_route

    d = decide_route("whatever.host", 999, GatewayRoutes())
    assert d.server_id is None and d.reason == "none"


def test_respond_no_route_login_lists_aliases():
    import app.services.gateway_service as gw

    sent: list[bytes] = []

    class FakeSock:
        def sendall(self, data):
            sent.append(bytes(data))

        def settimeout(self, _t):
            pass

        def recv(self, _n):
            return b""

    handshake = mp.Handshake(
        protocol_version=765,
        server_address="x",
        server_port=25565,
        next_state=mp.NEXT_STATE_LOGIN,
        consumed=0,
    )
    routes = gw.GatewayRoutes(aliases=("atm10", "lobby"))
    gw._respond_no_route(FakeSock(), bytearray(), handshake, routes)

    blob = b"".join(sent)
    assert blob and b"atm10" in blob and b"lobby" in blob


# --------------------------------------------------------------------------- #
# build_gateway_routes (DB)
# --------------------------------------------------------------------------- #
def test_build_gateway_routes(client):
    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        atm = Server(
            name="atm", slug="atm", server_type="forge", mc_version="1.20.1",
            base_path="C:/tmp/atm", gateway_enabled=True, gateway_hostname="atm10",
            port=25570,
        )
        lobby = Server(
            name="lobby", slug="lobby", server_type="paper", mc_version="1.21.1",
            base_path="C:/tmp/lobby", gateway_enabled=True, gateway_hostname="lobby",
            gateway_is_default=True, port=25571,
        )
        plain = Server(
            name="plain", slug="plain", server_type="paper", mc_version="1.20.1",
            base_path="C:/tmp/plain", gateway_enabled=False, port=25572,
        )
        db.add_all([atm, lobby, plain])
        db.commit()
        atm_id, lobby_id, plain_id = atm.id, lobby.id, plain.id

    with SessionLocal() as db:
        routes = gw.build_gateway_routes(db)

    assert routes.by_hostname == {"atm10": atm_id, "lobby": lobby_id}
    assert routes.default_server_id == lobby_id
    assert plain_id not in routes.internal_ports  # Nicht-Gateway ausgeschlossen
    assert atm_id in routes.internal_ports and lobby_id in routes.internal_ports
    # Eindeutige Versionszuordnung (1.20.1 -> 763 nur atm, 1.21.1 -> 767 nur lobby).
    assert routes.by_version.get(763) == atm_id
    assert routes.by_version.get(767) == lobby_id
    assert routes.aliases == ("atm10", "lobby")

    # Reiner Router: Ziel = OEFFENTLICHER Port des Servers (kein Verschieben).
    assert routes.internal_ports[atm_id] == 25570
    assert routes.internal_ports[lobby_id] == 25571

    # Server bleibt auf seinem Port (sleep_internal_port bleibt unangetastet).
    with SessionLocal() as db:
        assert db.get(Server, atm_id).sleep_internal_port is None


def test_build_gateway_routes_ambiguous_version(client):
    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        db.add_all([
            Server(
                name="a1", slug="a1", server_type="forge", mc_version="1.20.1",
                base_path="C:/tmp/a1", gateway_enabled=True, gateway_hostname="a1",
                port=25580,
            ),
            Server(
                name="b1", slug="b1", server_type="fabric", mc_version="1.20.1",
                base_path="C:/tmp/b1", gateway_enabled=True, gateway_hostname="b1",
                port=25581,
            ),
        ])
        db.commit()

    with SessionLocal() as db:
        routes = gw.build_gateway_routes(db)

    # 1.20.1 -> 763 zeigt auf ZWEI Server -> nicht im Versions-Fallback.
    assert 763 not in routes.by_version
    assert set(routes.by_hostname) == {"a1", "b1"}


# --------------------------------------------------------------------------- #
# Socket: transparentes Forwarding uebers Gateway
# --------------------------------------------------------------------------- #
def test_gateway_forwards_to_backend(monkeypatch):
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp
    from app.services import process_service as ps

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(ps, "is_running", lambda sid: True)

    backend = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    backend.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    backend.bind(("127.0.0.1", 0))
    backend.listen(1)
    internal_port = backend.getsockname()[1]
    received: list[bytes] = []

    def serve():
        conn, _ = backend.accept()
        received.append(conn.recv(4096))
        conn.sendall(b"HELLO")
        conn.close()

    threading.Thread(target=serve, daemon=True).start()

    routes = gw.GatewayRoutes(by_hostname={"atm10": 42}, internal_ports={42: internal_port})
    monkeypatch.setattr(gw, "_current_routes", lambda: routes)

    gateway_port = sp.find_free_port()
    assert gw.start_gateway(gateway_port)
    try:
        conn = socket.create_connection(("127.0.0.1", gateway_port), timeout=5)
        # Alias-Label-Match: "atm10.thormakmc.de" -> Alias "atm10".
        hs = _handshake("atm10.thormakmc.de", mp.NEXT_STATE_LOGIN, gateway_port)
        conn.sendall(hs)
        time.sleep(0.4)
        response = conn.recv(4096)
        conn.close()
        assert received and received[0] == hs  # Handshake transparent weitergeleitet
        assert response == b"HELLO"
    finally:
        gw.stop_gateway()
        backend.close()


def test_gateway_connection_survives_route_build_error(monkeypatch):
    """Ein Fehler beim Routen-Aufbau (z.B. DB-Lock) darf den Verbindungs-Thread
    nicht mit einem Traceback beenden – der Client bekommt einen sauberen
    Login-Disconnect."""
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)

    def boom():
        raise RuntimeError("database is locked")

    monkeypatch.setattr(gw, "_current_routes", boom)

    gateway_port = sp.find_free_port()
    assert gw.start_gateway(gateway_port)
    try:
        conn = socket.create_connection(("127.0.0.1", gateway_port), timeout=5)
        conn.sendall(_handshake("atm10", mp.NEXT_STATE_LOGIN, gateway_port))
        time.sleep(0.3)
        data = conn.recv(4096)  # sauberer Login-Disconnect statt Absturz/Reset
        conn.close()
        assert data  # es kam eine Disconnect-Antwort zurueck
    finally:
        gw.stop_gateway()


def test_gateway_off_by_default():
    """Ohne Aktivierung tut das Gateway nichts (Opt-in)."""
    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        assert svc.get_network_mode(db) == "off"
    assert gw.start_gateway_if_enabled() is False
    assert gw.is_running() is False


# --------------------------------------------------------------------------- #
# Phase 2: Lifecycle / Reconcile / Koexistenz
# --------------------------------------------------------------------------- #
def test_reconcile_gateway_starts_and_stops(client, monkeypatch):
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    port = sp.find_free_port()
    with SessionLocal() as db:
        svc.set_network_mode(db, "gateway")
        svc.set_network_port(db, port)
    try:
        gw.reconcile_gateway()
        assert gw.is_running()  # Modus gateway -> Listener laeuft

        with SessionLocal() as db:
            svc.set_network_mode(db, "off")
        gw.reconcile_gateway()
        assert not gw.is_running()  # off -> gestoppt
    finally:
        gw.stop_gateway()
        gw._set_routes_cache(None)


def test_reconcile_gateway_caches_routes(client, monkeypatch):
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    port = sp.find_free_port()
    with SessionLocal() as db:
        db.add(Server(
            name="g1", slug="g1", server_type="paper", mc_version="1.20.1",
            base_path="C:/tmp/g1", gateway_enabled=True, gateway_hostname="g1",
            port=25590,
        ))
        svc.set_network_mode(db, "gateway")
        svc.set_network_port(db, port)
        db.commit()

    try:
        gw.reconcile_gateway()
        routes = gw._current_routes()  # kommt aus dem Cache (kein DB-Zugriff noetig)
        assert routes.by_hostname.get("g1") is not None

        # Deaktivieren leert den Cache.
        with SessionLocal() as db:
            svc.set_network_mode(db, "off")
        gw.reconcile_gateway()
        with gw._ROUTES_LOCK:
            assert gw._ROUTES_CACHE is None
    finally:
        gw.stop_gateway()
        gw._set_routes_cache(None)


def test_gateway_sleep_server_gets_public_wake_proxy(client, monkeypatch):
    """Reiner Router: ein Gateway-Server mit Sleep bleibt auf seinem OEFFENTLICHEN
    Port und bekommt dort einen normalen (0.0.0.0) Wake-Proxy. So funktioniert
    sowohl die Direktverbindung als auch der Gateway-Forward (der zeigt auf den
    Public-Port und weckt darueber)."""
    import app.services.sleep_proxy_service as sp
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc

    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    pub = sp.find_free_port()
    internal = sp.find_free_port()
    while internal == pub:
        internal = sp.find_free_port()

    with SessionLocal() as db:
        svc.set_network_mode(db, "gateway")
        srv = Server(
            name="gw-co", slug="gw-co", server_type="paper", mc_version="1.20.1",
            base_path="C:/tmp/gw-co", port=pub, sleep_enabled=True,
            sleep_internal_port=internal, gateway_enabled=True,
        )
        db.add(srv)
        db.commit()
        sid = srv.id

    try:
        sp.reconcile_proxies()
        listener = sp._PROXIES.get(sid)
        assert listener is not None and listener.bind_host == "0.0.0.0"
        assert listener.public_port == pub and listener.internal_port == internal
    finally:
        sp.stop_proxy(sid)


def test_effective_server_port_gateway_uses_public():
    from types import SimpleNamespace as S

    from app.services.server_service import effective_server_port

    # Gateway-Server (ohne Sleep) -> OEFFENTLICHER Port (bleibt direkt erreichbar).
    assert effective_server_port(
        S(sleep_enabled=False, gateway_enabled=True, velocity_enabled=False,
          sleep_internal_port=25601, port=25565)
    ) == 25565
    # Sleep-Server -> interner Port (der Sleep-Proxy sitzt auf dem Public-Port).
    assert effective_server_port(
        S(sleep_enabled=True, gateway_enabled=False, velocity_enabled=False,
          sleep_internal_port=25602, port=25565)
    ) == 25602
    # Normaler Server -> oeffentlicher Port.
    assert effective_server_port(
        S(sleep_enabled=False, gateway_enabled=False, velocity_enabled=False,
          sleep_internal_port=None, port=25565)
    ) == 25565
    # Gateway-Server ohne (noch) internen Port -> Fallback oeffentlicher Port.
    assert effective_server_port(
        S(sleep_enabled=False, gateway_enabled=True, sleep_internal_port=None, port=25565)
    ) == 25565


# --------------------------------------------------------------------------- #
# Phase 3: Servereinstellungen (Alias-Validierung / Default / global) + UI
# --------------------------------------------------------------------------- #
def _login_admin(client):
    resp = client.post(
        "/login", data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert resp.status_code == 303


def _make_server(tmp_path, slug, *, port):
    from app.db.session import SessionLocal
    from app.models.server import Server

    base = tmp_path / slug
    base.mkdir()
    (base / "server.properties").write_text("server-port=25565\n", encoding="utf-8")
    with SessionLocal() as db:
        srv = Server(
            name=slug, slug=slug, server_type="paper", mc_version="1.20.1",
            base_path=str(base), port=port, status="stopped",
        )
        db.add(srv)
        db.commit()
        return srv.id


def _update(db, server, **overrides):
    from app.services.server_service import update_server_settings

    params = dict(
        mc_version=server.mc_version, loader_version=server.loader_version,
        java_profile_id=None, memory_min_mb=2048, memory_max_mb=4096,
        port=server.port, auto_restart=False, auto_start_with_manager=False,
        start_mode=server.start_mode, start_command=None, start_bat_path=None,
    )
    params.update(overrides)
    return update_server_settings(db, server, **params)


def test_update_settings_gateway_valid_alias(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server

    sid = _make_server(tmp_path, "gwvalid", port=25570)
    with SessionLocal() as db:
        srv = db.get(Server, sid)
        _update(db, srv, gateway_enabled=True, gateway_hostname="ATM10")
        db.refresh(srv)
        assert srv.gateway_enabled is True
        assert srv.gateway_hostname == "atm10"  # normalisiert (lowercase)
        # Reiner Router: der Server bleibt auf seinem Port (keine interne Vergabe).
        assert srv.sleep_internal_port is None
        assert srv.port == 25570


def test_update_settings_gateway_writes_accept_transfers(client, tmp_path):
    """Gateway aktivieren schreibt accepts-transfers=true in server.properties.

    Mojangs echter Key heisst 'accepts-transfers' (mit 's'); ein falsch geschriebener
    'accept-transfers' wird vom Server ignoriert -> Transfer schlaegt fehl.
    """
    from pathlib import Path

    from app.db.session import SessionLocal
    from app.models.server import Server

    sid = _make_server(tmp_path, "gwtransfer", port=25570)
    with SessionLocal() as db:
        srv = db.get(Server, sid)
        _update(db, srv, gateway_enabled=True, gateway_hostname="atm10")
        props = (Path(srv.base_path) / "server.properties").read_text(encoding="utf-8")
    assert "accepts-transfers=true" in props  # der ECHTE Mojang-Key (mit 's')


def test_update_settings_gateway_invalid_alias(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server

    sid = _make_server(tmp_path, "gwbad", port=25571)
    with SessionLocal() as db:
        srv = db.get(Server, sid)
        _, warnings = _update(db, srv, gateway_enabled=True, gateway_hostname="AT M10!")
        db.refresh(srv)
        assert srv.gateway_enabled is False  # ungueltig -> nicht aktiviert
        assert any("Ungueltiger Alias" in w for w in warnings)


def test_update_settings_gateway_duplicate_alias(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server

    a = _make_server(tmp_path, "gwa", port=25572)
    b = _make_server(tmp_path, "gwb", port=25573)
    with SessionLocal() as db:
        srv_a = db.get(Server, a)
        _update(db, srv_a, gateway_enabled=True, gateway_hostname="shared")
    with SessionLocal() as db:
        srv_b = db.get(Server, b)
        _, warnings = _update(db, srv_b, gateway_enabled=True, gateway_hostname="shared")
        db.refresh(srv_b)
        assert srv_b.gateway_enabled is False
        assert any("bereits vergeben" in w for w in warnings)


def test_update_settings_gateway_default_resets_others(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server

    a = _make_server(tmp_path, "lob-a", port=25574)
    b = _make_server(tmp_path, "lob-b", port=25575)
    with SessionLocal() as db:
        srv_a = db.get(Server, a)
        _update(db, srv_a, gateway_enabled=True, gateway_hostname="loba", gateway_is_default=True)
        db.refresh(srv_a)
        assert srv_a.gateway_is_default is True
    with SessionLocal() as db:
        srv_b = db.get(Server, b)
        _update(db, srv_b, gateway_enabled=True, gateway_hostname="lobb", gateway_is_default=True)
    with SessionLocal() as db:
        # Beim Setzen von B als Default wurde A zurueckgesetzt (genau EINER).
        assert db.get(Server, a).gateway_is_default is False
        assert db.get(Server, b).gateway_is_default is True


def test_global_gateway_settings_roundtrip(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        assert svc.get_network_mode(db) == "off"  # Standard

        svc.set_network_mode(db, "gateway")
        svc.set_network_port(db, 25600)
        assert svc.get_network_mode(db) == "gateway"
        assert svc.get_network_port(db) == 25600
        assert svc.get_network_mode_runtime() == "gateway"
        assert svc.get_network_port_runtime() == 25600

        try:
            svc.set_network_port(db, 70000)
            assert False, "sollte ValueError werfen"
        except ValueError:
            pass


def test_settings_page_shows_gateway_and_post(client, monkeypatch):
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    _login_admin(client)

    page = client.get("/settings")
    assert page.status_code == 200
    assert "gateway" in page.text.lower()  # Netzwerk-Modus-Option

    port = sp.find_free_port()
    try:
        resp = client.post(
            "/settings/network",
            data={"network_mode": "gateway", "network_port": str(port)},
            follow_redirects=False,
        )
        assert resp.status_code == 303
        from app.db.session import SessionLocal
        from app.services import app_setting_service as svc

        with SessionLocal() as db:
            assert svc.get_network_mode(db) == "gateway"
            assert svc.get_network_port(db) == port
    finally:
        gw.stop_gateway()
        gw._set_routes_cache(None)


def test_gateway_status_motd_lists_aliases():
    import app.services.gateway_service as gw

    sent: list[bytes] = []

    class FakeSock:
        def sendall(self, data):
            sent.append(bytes(data))

        def settimeout(self, _t):
            pass

        def recv(self, _n):
            return b""

    handshake = mp.Handshake(
        protocol_version=765, server_address="unknown.host", server_port=25565,
        next_state=mp.NEXT_STATE_STATUS, consumed=0,
    )
    routes = gw.GatewayRoutes(aliases=("atm10", "lobby"))
    gw._respond_no_route(FakeSock(), bytearray(), handshake, routes)
    blob = b"".join(sent)
    assert b"atm10" in blob and b"lobby" in blob


def test_build_gateway_routes_targets_public_port(client, tmp_path, monkeypatch):
    """Reiner Router: Ziel = oeffentlicher Server-Port; server.properties wird
    NICHT umgeschrieben (der Server bleibt direkt erreichbar)."""
    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    base = tmp_path / "gwp"
    base.mkdir()
    (base / "server.properties").write_text("server-port=25580\n", encoding="utf-8")

    with SessionLocal() as db:
        svc.set_network_port(db, 25577)  # Gateway-Port (anders als der Server-Port)
        db.add(Server(
            name="gwp", slug="gwp", server_type="paper", mc_version="1.20.1",
            base_path=str(base), gateway_enabled=True, gateway_hostname="gwp",
            port=25580, status="stopped",
        ))
        db.commit()

    with SessionLocal() as db:
        routes = gw.build_gateway_routes(db)
        sid = next(iter(routes.internal_ports))

    assert routes.internal_ports[sid] == 25580  # Ziel = oeffentlicher Port
    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "server-port=25580" in props  # unveraendert (kein Verschieben)


def test_update_settings_gateway_excludes_gateway_port(client, tmp_path):
    """Der interne Port darf nie dem globalen Gateway-Port entsprechen (sonst
    wuerde das Gateway spaeter auf sich selbst routen)."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        svc.set_network_port(db, 25588)
    # Public-Port so waehlen, dass preferred (=port+1) == Gateway-Port waere.
    sid = _make_server(tmp_path, "gwexcl", port=25587)
    with SessionLocal() as db:
        srv = db.get(Server, sid)
        _update(db, srv, gateway_enabled=True, gateway_hostname="excl")
        db.refresh(srv)
        assert srv.gateway_enabled is True
        assert srv.sleep_internal_port not in (25587, 25588)


def test_non_superadmin_cannot_set_gateway(client, tmp_path):
    """Gateway-Routing wirkt global -> nur Super-Admins duerfen es je Server
    setzen. Ein Moderator mit Server-Kontrolle darf die Apex-Route nicht kapern."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.models.server_permission import ServerPermission
    from app.models.user import User

    _login_admin(client)
    client.post(
        "/users",
        data={"username": "mod1", "password": "Securepass123", "role": "moderator"},
        follow_redirects=True,
    )
    sid = _make_server(tmp_path, "modsrv", port=25595)
    with SessionLocal() as db:
        mod = db.query(User).filter(User.username == "mod1").one()
        db.add(
            ServerPermission(user_id=mod.id, server_id=sid, can_view=True, can_manage=True)
        )
        db.commit()

    # Als Moderator anmelden (ueberschreibt das Session-Cookie).
    login = client.post(
        "/login", data={"username": "mod1", "password": "Securepass123"},
        follow_redirects=False,
    )
    assert login.status_code == 303

    resp = client.post(
        f"/servers/{sid}/settings",
        data={
            "mc_version": "1.20.1", "memory_min_mb": "2048", "memory_max_mb": "4096",
            "port": "25595", "start_mode": "bat",
            "gateway_enabled": "true", "gateway_hostname": "hijack",
            "gateway_is_default": "true",
        },
        follow_redirects=False,
    )
    assert resp.status_code == 303

    with SessionLocal() as db:
        srv = db.get(Server, sid)
        assert srv.gateway_enabled is False  # Moderator konnte Gateway NICHT setzen
        assert srv.gateway_hostname is None
        assert srv.gateway_is_default is False


# --------------------------------------------------------------------------- #
# Phase 4: Randfaelle / Haertung
# --------------------------------------------------------------------------- #
def test_build_gateway_routes_duplicate_alias_is_deterministic(client, tmp_path):
    """Doppelte Aliase (z.B. via Direkt-DB) duerfen nicht crashen; genau EIN
    Mapping bleibt bestehen (die UI-Validierung verhindert Duplikate ohnehin)."""
    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        db.add_all([
            Server(name="d1", slug="d1", server_type="paper", mc_version="1.20.1",
                   base_path="C:/tmp/d1", gateway_enabled=True, gateway_hostname="dup", port=25610),
            Server(name="d2", slug="d2", server_type="paper", mc_version="1.20.1",
                   base_path="C:/tmp/d2", gateway_enabled=True, gateway_hostname="dup", port=25611),
        ])
        db.commit()

    with SessionLocal() as db:
        routes = gw.build_gateway_routes(db)

    dup_targets = [sid for host, sid in routes.by_hostname.items() if host == "dup"]
    assert len(dup_targets) == 1  # genau ein Ziel, kein Crash
    assert routes.by_hostname["dup"] in set(routes.internal_ports)


def test_decide_route_no_match_for_version_and_hostname():
    from app.services.gateway_service import GatewayRoutes, decide_route

    # Weder Alias noch Default noch (eindeutige) Version -> kein Treffer.
    routes = GatewayRoutes(by_hostname={"atm10": 1}, by_version={}, default_server_id=None)
    d = decide_route("ganz-unbekannt.example", 4711, routes)
    assert d.server_id is None and d.reason == "none"


def test_gateway_no_route_login_disconnect_socket(monkeypatch):
    """Kein Treffer bei Login -> sauberer Login-Disconnect (keine Weiterleitung)."""
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    monkeypatch.setattr(
        gw, "_current_routes",
        lambda: gw.GatewayRoutes(by_hostname={"atm10": 1}, aliases=("atm10",)),
    )

    port = sp.find_free_port()
    assert gw.start_gateway(port)
    try:
        conn = socket.create_connection(("127.0.0.1", port), timeout=5)
        conn.sendall(_handshake("nichtda.example", mp.NEXT_STATE_LOGIN, port))
        time.sleep(0.3)
        data = conn.recv(4096)
        conn.close()
        assert b"atm10" in data  # Disconnect nennt verfuegbare Aliase
    finally:
        gw.stop_gateway()


def test_gateway_forwards_with_fml_marker(monkeypatch):
    """Hostname mit Forge-FML-Marker (\\0FML2\\0) wird korrekt per Alias geroutet."""
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp
    from app.services import process_service as ps

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(ps, "is_running", lambda sid: True)

    backend = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    backend.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    backend.bind(("127.0.0.1", 0))
    backend.listen(1)
    internal_port = backend.getsockname()[1]
    received: list[bytes] = []

    def serve():
        conn, _ = backend.accept()
        received.append(conn.recv(4096))
        conn.sendall(b"OK")
        conn.close()

    threading.Thread(target=serve, daemon=True).start()

    routes = gw.GatewayRoutes(by_hostname={"atm10": 7}, internal_ports={7: internal_port})
    monkeypatch.setattr(gw, "_current_routes", lambda: routes)

    port = sp.find_free_port()
    assert gw.start_gateway(port)
    try:
        conn = socket.create_connection(("127.0.0.1", port), timeout=5)
        hs = _handshake("atm10\x00FML2\x00", mp.NEXT_STATE_LOGIN, port)
        conn.sendall(hs)
        time.sleep(0.4)
        resp = conn.recv(4096)
        conn.close()
        assert received and received[0] == hs  # transparent inkl. FML-Marker
        assert resp == b"OK"
    finally:
        gw.stop_gateway()
        backend.close()


def test_gateway_rejects_legacy_ping(monkeypatch):
    """Legacy-Ping (<1.7, 0xFE) wird sauber abgewiesen (Verbindung geschlossen)."""
    import app.services.gateway_service as gw
    import app.services.sleep_proxy_service as sp

    monkeypatch.setattr(gw, "_glog", lambda *a, **k: None)
    monkeypatch.setattr(gw, "_current_routes", lambda: gw.GatewayRoutes())

    port = sp.find_free_port()
    assert gw.start_gateway(port)
    try:
        conn = socket.create_connection(("127.0.0.1", port), timeout=5)
        conn.sendall(b"\xfe\x01")  # Legacy Server-List-Ping (1.6-)
        time.sleep(0.3)
        conn.settimeout(2)
        data = conn.recv(64)
        conn.close()
        assert data == b""  # keine Antwort, sauber getrennt (kein Crash)
    finally:
        gw.stop_gateway()


def test_wake_timeout_sends_reconnect_hint(client, monkeypatch):
    """Langsamer Wake: wird der Server nicht rechtzeitig bereit, bekommt der
    Client einen sauberen 'spaeter erneut verbinden'-Login-Disconnect."""
    import app.services.sleep_proxy_service as sp
    from app.db.session import SessionLocal
    from app.models.server import Server

    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "start_server", lambda *a, **k: (True, "ok"))
    monkeypatch.setattr(sp.process_service, "is_server_ready", lambda sid: False)
    monkeypatch.setattr(sp, "_WAKE_READY_TIMEOUT", 0.3)

    with SessionLocal() as db:
        srv = Server(
            name="wk", slug="wk", server_type="paper", mc_version="1.20.1",
            base_path="C:/tmp/wk", port=25620,
        )
        db.add(srv)
        db.commit()
        sid = srv.id

    sent: list[bytes] = []

    class FakeSock:
        def sendall(self, data):
            sent.append(bytes(data))

        def settimeout(self, _t):
            pass

    ok = sp._wake_server(sid, FakeSock())
    assert ok is False  # Timeout -> nicht weiterleiten
    assert b"Sekunden" in b"".join(sent)  # Reconnect-Hinweis wurde gesendet


def test_gateway_domain_settings_override_env(client, monkeypatch):
    """Zieldomain: UI-Wert ueberschreibt den ENV/Config-Wert; Normalisierung."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    settings = get_settings()
    monkeypatch.setattr(settings, "network_domain", "env-domain.de")
    with SessionLocal() as db:
        # Ohne UI-Override -> ENV-Wert.
        assert svc.get_network_domain(db) == "env-domain.de"
        assert svc.get_network_domain_source(db) == "env"
        # UI-Override gewinnt + wird normalisiert (Schema/Slash/Punkt weg).
        svc.set_network_domain(db, "HTTPS://Thormakmc.de/")
        assert svc.get_network_domain(db) == "thormakmc.de"
        assert svc.get_network_domain_source(db) == "ui"
        assert svc.get_network_domain_runtime() == "thormakmc.de"


def test_create_auto_lobby_gateway(client, tmp_path, monkeypatch):
    from pathlib import Path

    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc
    from app.services import lobby_service

    monkeypatch.setattr(lobby_service, "_latest_stable_lobby_version", lambda: "1.21.1")
    monkeypatch.setattr(gw, "reconcile_gateway", lambda: None)  # kein echter Socket-Bind

    with SessionLocal() as db:
        svc.set_server_storage_root(db, str(tmp_path / "servers"))
        ok, message, sid = lobby_service.create_auto_lobby(db, initiated_by_user_id=None)

    assert ok is True and sid is not None
    with SessionLocal() as db:
        lobby = db.get(Server, sid)
        base = lobby.base_path
        assert lobby.server_type == "paper"
        assert lobby.gateway_enabled is True
        assert lobby.gateway_is_default is True
        assert lobby.gateway_hostname == "lobby"
        assert svc.get_network_mode(db) == "gateway"

    props = (Path(base) / "server.properties").read_text(encoding="utf-8")
    assert "level-type=minecraft:flat" in props
    assert (Path(base) / "LOBBY-SETUP.txt").exists()

    # Idempotent: zweiter Aufruf legt keinen zweiten Server an.
    from sqlalchemy import func, select

    with SessionLocal() as db:
        ok2, _m2, sid2 = lobby_service.create_auto_lobby(db, initiated_by_user_id=None)
        count = db.scalar(select(func.count(Server.id)))
    assert ok2 and sid2 == sid and count == 1


def test_cleanup_velocity_leftovers(tmp_path):
    from types import SimpleNamespace

    import yaml

    from app.services import server_service as ss

    srv = tmp_path / "srv"
    srv.mkdir()
    (srv / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nmotd=x\n", encoding="utf-8"
    )
    cfg = srv / "config"
    cfg.mkdir()
    (cfg / "paper-global.yml").write_text(
        "_version: 28\nproxies:\n  velocity:\n    enabled: true\n    secret: abc\n",
        encoding="utf-8",
    )
    notes = ss.cleanup_velocity_leftovers(SimpleNamespace(base_path=str(srv)))
    assert notes
    props = (srv / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=true" in props
    assert "server-ip=\n" in props or props.rstrip().endswith("server-ip=")
    data = yaml.safe_load((cfg / "paper-global.yml").read_text(encoding="utf-8"))
    assert data["proxies"]["velocity"]["enabled"] is False
    assert data["proxies"]["velocity"]["secret"] == "abc"  # Rest bleibt


def test_cleanup_velocity_leftovers_ignores_cracked_server(tmp_path):
    from types import SimpleNamespace

    from app.services import server_service as ss

    srv = tmp_path / "srv2"
    srv.mkdir()
    (srv / "server.properties").write_text("online-mode=false\nmotd=x\n", encoding="utf-8")
    notes = ss.cleanup_velocity_leftovers(SimpleNamespace(base_path=str(srv)))
    assert notes == []
    assert "online-mode=false" in (srv / "server.properties").read_text(encoding="utf-8")


def test_gateway_status_runtime_reflects_registration(client):
    import app.services.app_setting_service as svc
    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        db.add_all([
            Server(
                name="Lobby", slug="lobby", server_type="paper", mc_version="1.21.1",
                base_path="C:/tmp/lobby", gateway_enabled=True, gateway_hostname="lobby",
                gateway_is_default=True, port=25571,
            ),
            Server(
                name="David", slug="david", server_type="spigot", mc_version="1.21.1",
                base_path="C:/tmp/david", gateway_enabled=True, gateway_hostname="david",
                port=25591,
            ),
            Server(  # nicht im Gateway -> darf im Status fehlen
                name="Solo", slug="solo", server_type="paper", mc_version="1.20.1",
                base_path="C:/tmp/solo", gateway_enabled=False, port=25572,
            ),
        ])
        db.commit()
        svc.set_network_mode(db, "gateway")

    status = gw.gateway_status_runtime()
    assert status["mode"] == "gateway"
    names = {r["server"]: r for r in status["routes"]}
    assert "David" in names and names["David"]["alias"] == "david" and names["David"]["valid"]
    assert "Solo" not in names  # nicht registriert -> landet sonst auf Default
    assert status["default"] == "Lobby"

    with SessionLocal() as db:
        svc.set_network_mode(db, "off")
    assert gw.gateway_status_runtime()["mode"] == "off"


def test_decide_route_dotted_alias_strips_domain():
    """Alias mit Punkten (z.B. 1.21.11-spigot) muss ueber Domain-Abzug matchen."""
    import app.services.gateway_service as gw

    routes = gw.GatewayRoutes(
        by_hostname={"1.21.11-spigot": 7, "lobby": 3},
        default_server_id=3,
        domain="mc.friedrich-dietrich.de",
    )
    # Punkt-Alias + Domain -> Server 7 (nicht Default 3)
    d = gw.decide_route("1.21.11-spigot.mc.friedrich-dietrich.de", None, routes)
    assert d.server_id == 7 and d.reason == "alias"
    # Einfacher Alias weiterhin per erstem Label
    assert gw.decide_route("lobby.mc.friedrich-dietrich.de", None, routes).server_id == 3
    # Blanke Domain -> Default
    assert gw.decide_route("mc.friedrich-dietrich.de", None, routes).server_id == 3
    # Unbekannter Punkt-Alias -> Default, nicht der falsche Server
    assert gw.decide_route("nope.mc.friedrich-dietrich.de", None, routes).server_id == 3


def test_build_gateway_routes_populates_domain(client):
    import app.services.app_setting_service as svc
    import app.services.gateway_service as gw
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        db.add(Server(
            name="spg", slug="spg", server_type="spigot", mc_version="1.21.1",
            base_path="C:/tmp/spg", gateway_enabled=True,
            gateway_hostname="1.21.11-spigot", port=25591,
        ))
        db.commit()
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")

    with SessionLocal() as db:
        routes = gw.build_gateway_routes(db)
    assert routes.domain == "mc.friedrich-dietrich.de"
    assert gw.decide_route(
        "1.21.11-spigot.mc.friedrich-dietrich.de", None, routes
    ).reason == "alias"


def test_sync_lobby_plugin_writes_menu(client, tmp_path):
    import yaml

    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    lobby_dir = tmp_path / "lobby"
    lobby_dir.mkdir()
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lobby", server_type="paper", mc_version="1.21.1",
                   base_path=str(lobby_dir), gateway_enabled=True, gateway_hostname="lobby",
                   gateway_is_default=True, port=25569),
            Server(name="David", slug="david", server_type="spigot", mc_version="1.21.11",
                   base_path=str(tmp_path / "david"), gateway_enabled=True,
                   gateway_hostname="1.21.11-spigot", port=25591),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")
        svc.set_network_port(db, 25565)
        svc.set_network_mode(db, "gateway")

    with SessionLocal() as db:
        ok, msg = lobby_service.sync_lobby_plugin(db)
    assert ok, msg

    cfg = yaml.safe_load(
        (lobby_dir / "plugins" / "MCSMLobby" / "config.yml").read_text(encoding="utf-8")
    )
    # servers ist eine LISTE (Alias mit Punkten darf kein YAML-Map-Key sein).
    servers = cfg["servers"]
    assert isinstance(servers, list)
    by_key = {s["key"]: s for s in servers}
    assert "1.21.11-spigot" in by_key
    assert by_key["1.21.11-spigot"]["host"] == "1.21.11-spigot.mc.friedrich-dietrich.de"
    assert by_key["1.21.11-spigot"]["port"] == 25565
    assert "lobby" not in by_key  # die Lobby selbst gehoert nicht ins Menue
    # Fertiges Plugin-Jar wird mitgeliefert und in die Lobby kopiert.
    assert (lobby_dir / "plugins" / "MCSMLobby.jar").exists()


def test_sync_lobby_plugin_preserves_user_regions(client, tmp_path):
    import yaml

    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    lobby_dir = tmp_path / "lobby2"
    (lobby_dir / "plugins" / "MCSMLobby").mkdir(parents=True)
    region = [{"world": "world", "min": [1, 64, 1], "max": [3, 66, 3], "target": "1.21.11-spigot"}]
    (lobby_dir / "plugins" / "MCSMLobby" / "config.yml").write_text(
        yaml.safe_dump({"regions": region, "cooldown_ms": 1234}), encoding="utf-8"
    )
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lob2", server_type="paper", mc_version="1.21.1",
                   base_path=str(lobby_dir), gateway_enabled=True, gateway_hostname="lobby",
                   gateway_is_default=True, port=25569),
            Server(name="David", slug="dav2", server_type="spigot", mc_version="1.21.11",
                   base_path=str(tmp_path / "d2"), gateway_enabled=True,
                   gateway_hostname="1.21.11-spigot", port=25591),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_mode(db, "gateway")

    with SessionLocal() as db:
        ok, _ = lobby_service.sync_lobby_plugin(db)
    assert ok
    cfg = yaml.safe_load(
        (lobby_dir / "plugins" / "MCSMLobby" / "config.yml").read_text(encoding="utf-8")
    )
    assert cfg["regions"] == region  # begehbare Portale bleiben erhalten
    assert cfg["cooldown_ms"] == 1234  # Nutzerwert bleibt erhalten
    assert "1.21.11-spigot" in {s["key"] for s in cfg["servers"]}


def test_decide_route_apex_never_hijacked_by_sibling_alias():
    """Apex (blanke Domain) muss zur Default-Lobby, auch wenn ein Server 'mc' heisst."""
    import app.services.gateway_service as gw

    routes = gw.GatewayRoutes(
        by_hostname={"mc": 9, "lobby": 3, "1.21.11-spigot": 7},
        default_server_id=3,
        domain="mc.example.de",
    )
    # Apex -> Default 3, NICHT der 'mc'-Server (9).
    assert gw.decide_route("mc.example.de", None, routes).server_id == 3
    # Dotted-Alias unter der Domain matcht weiterhin exakt.
    assert gw.decide_route("1.21.11-spigot.mc.example.de", None, routes).server_id == 7
    # Bekannter 'mc'-Alias via Subdomain funktioniert normal.
    assert gw.decide_route("mc.mc.example.de", None, routes).server_id == 9


def test_decide_route_unknown_under_domain_not_misrouted_to_prefix():
    """Unbekannter Alias unter der Domain -> Default, nicht auf ein kuerzeres Label raten."""
    import app.services.gateway_service as gw

    routes = gw.GatewayRoutes(
        by_hostname={"play": 1, "play.creative": 2},
        default_server_id=5,
        domain="mc.example.de",
    )
    # Exakter dotted-Alias unter Domain -> Server 2 (creative).
    assert gw.decide_route("play.creative.mc.example.de", None, routes).server_id == 2
    # Extra-Label (nicht im Schema) -> Default, NICHT faelschlich auf 'play' (1).
    d = gw.decide_route("play.creative.eu.mc.example.de", None, routes)
    assert d.server_id == 5 and d.reason == "default"


def test_decide_route_first_label_only_without_domain():
    """Ohne konfigurierte Domain bleibt der Komfort-Fallback (erstes Label)."""
    import app.services.gateway_service as gw

    routes = gw.GatewayRoutes(by_hostname={"david": 7}, default_server_id=3, domain="")
    assert gw.decide_route("david.irgendwas.net", None, routes).server_id == 7


def test_build_plugin_servers_skips_portless(client, tmp_path):
    """Ein Gateway-Server ohne Port darf NICHT ins Menue (Gateway hat keine Route)."""
    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lob3", server_type="paper", mc_version="1.21.1",
                   base_path=str(tmp_path / "l3"), gateway_enabled=True, gateway_hostname="lobby",
                   gateway_is_default=True, port=25569),
            Server(name="NoPort", slug="np", server_type="paper", mc_version="1.21.1",
                   base_path=str(tmp_path / "np"), gateway_enabled=True,
                   gateway_hostname="noport", port=None),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        lobby = db.scalar(__import__("sqlalchemy").select(Server).where(Server.gateway_is_default.is_(True)))
        servers, skipped = lobby_service._build_plugin_servers(db, lobby.id)
    assert "noport" not in {s["key"] for s in servers}
    assert "NoPort" in skipped


def test_sync_lobby_plugin_aborts_on_corrupt_config(client, tmp_path):
    """Kaputte config.yml wird NICHT ueberschrieben (Portale bleiben erhalten)."""
    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    lobby_dir = tmp_path / "lob4"
    (lobby_dir / "plugins" / "MCSMLobby").mkdir(parents=True)
    broken = "regions: [ this is : not valid yaml : ["
    (lobby_dir / "plugins" / "MCSMLobby" / "config.yml").write_text(broken, encoding="utf-8")
    with SessionLocal() as db:
        db.add(Server(name="Lobby", slug="lob4", server_type="paper", mc_version="1.21.1",
                      base_path=str(lobby_dir), gateway_enabled=True, gateway_hostname="lobby",
                      gateway_is_default=True, port=25569))
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        ok, msg = lobby_service.sync_lobby_plugin(db)
    assert not ok and "nicht" in msg.lower()
    # Die kaputte Datei ist unveraendert (nicht mit Defaults ueberschrieben).
    assert (lobby_dir / "plugins" / "MCSMLobby" / "config.yml").read_text(encoding="utf-8") == broken


def test_sync_installs_lobby_command_and_sleep_flag(client, tmp_path):
    """Plugin kommt auf ALLE Gateway-Bukkit-Server: Lobby mit Kompass-Menue,
    andere mit /lobby-Ziel; Sleep-Server werden im Menue markiert."""
    import yaml

    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    lobby_dir = tmp_path / "lob"
    david_dir = tmp_path / "david"
    lobby_dir.mkdir()
    david_dir.mkdir()
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                   base_path=str(lobby_dir), gateway_enabled=True, gateway_hostname="lobby",
                   gateway_is_default=True, port=25569),
            Server(name="David", slug="david", server_type="spigot", mc_version="1.21.11",
                   base_path=str(david_dir), gateway_enabled=True,
                   gateway_hostname="1.21.11-spigot", port=25591, sleep_enabled=True),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.friedrich-dietrich.de")
        svc.set_network_port(db, 25565)
        svc.set_network_mode(db, "gateway")
        ok, _ = lobby_service.sync_lobby_plugin(db)
    assert ok

    lob_cfg = yaml.safe_load((lobby_dir / "plugins" / "MCSMLobby" / "config.yml").read_text(encoding="utf-8"))
    dav_cfg = yaml.safe_load((david_dir / "plugins" / "MCSMLobby" / "config.yml").read_text(encoding="utf-8"))

    # Lobby: Kompass an, KEIN /lobby-Ziel (ist selbst die Lobby), David im Menue mit Sleep-Flag.
    assert lob_cfg["compass"]["enabled"] is True
    assert lob_cfg["lobby"]["host"] == ""
    david_entry = next(s for s in lob_cfg["servers"] if s["key"] == "1.21.11-spigot")
    assert david_entry["sleep"] is True

    # David: Kompass aus, /lobby zeigt auf die Lobby-Adresse, Lobby im Menue.
    assert dav_cfg["compass"]["enabled"] is False
    assert dav_cfg["lobby"]["host"] == "lobby.mc.friedrich-dietrich.de"
    assert dav_cfg["lobby"]["port"] == 25565
    assert "lobby" in {s["key"] for s in dav_cfg["servers"]}


def test_sync_skips_non_bukkit_servers(client, tmp_path):
    """Forge/Vanilla koennen keine Plugins laden -> kein config-Ordner, aber gemeldet."""
    import app.services.app_setting_service as svc
    import app.services.lobby_service as lobby_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    lobby_dir = tmp_path / "lobx"
    forge_dir = tmp_path / "forgex"
    lobby_dir.mkdir()
    forge_dir.mkdir()
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lobx", server_type="paper", mc_version="1.21.1",
                   base_path=str(lobby_dir), gateway_enabled=True, gateway_hostname="lobby",
                   gateway_is_default=True, port=25569),
            Server(name="ATM10", slug="atm", server_type="forge", mc_version="1.21.1",
                   base_path=str(forge_dir), gateway_enabled=True, gateway_hostname="atm10",
                   port=25572),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_mode(db, "gateway")
        ok, msg = lobby_service.sync_lobby_plugin(db)
    assert ok
    assert "ATM10" in msg  # als nicht-Bukkit gemeldet
    assert not (forge_dir / "plugins" / "MCSMLobby").exists()  # kein Plugin auf Forge
