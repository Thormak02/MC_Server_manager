"""Tests fuer das Velocity-Netzwerk (echter Proxy + neueste-Version-Lobby, alle Versionen)."""

from __future__ import annotations

from pathlib import Path


def _login(client):
    client.post("/login", data={"username": "admin", "password": "admin123!"})


# --- velocity.toml Rendering ---------------------------------------------------
def test_render_velocity_toml_modern_forwarding():
    from app.services import proxy_service

    cfg = {"bind_port": 25565, "domain": "mc.example.de", "motd": "Hallo", "max_players": 80}
    backends = [
        {"name": "lobby", "address": "127.0.0.1:30001", "alias": "lobby", "is_lobby": True},
        {"name": "smp", "address": "127.0.0.1:30002", "alias": "smp", "is_lobby": False},
    ]
    toml = proxy_service.render_velocity_toml(cfg, backends, "lobby")
    assert 'bind = "127.0.0.1:25565"' in toml
    assert 'player-info-forwarding-mode = "modern"' in toml
    assert 'forwarding-secret-file = "forwarding.secret"' in toml
    assert '"lobby" = "127.0.0.1:30001"' in toml   # QUOTED key
    assert '"smp" = "127.0.0.1:30002"' in toml
    assert 'try = ["lobby"]' in toml
    assert '"lobby.mc.example.de" = ["lobby"]' in toml
    assert '"smp.mc.example.de" = ["smp"]' in toml
    assert "read-timeout = 185000" in toml


def test_render_velocity_toml_no_backends():
    from app.services import proxy_service

    cfg = {"bind_port": 25565, "domain": "", "motd": "x", "max_players": 20}
    toml = proxy_service.render_velocity_toml(cfg, [], None)
    assert "try = []" in toml           # kein Backend -> leere try-Liste, valides TOML
    assert 'bind = "127.0.0.1:25565"' in toml


def test_render_velocity_toml_quotes_dotted_backend_name():
    """Ein Alias mit Punkt (z.B. '1.21.1') muss als QUOTED key raus, sonst zerfaellt
    er in verschachtelte TOML-Tabellen und das Backend existiert nie."""
    from app.services import proxy_service

    be = [{"name": "1.21.1", "address": "127.0.0.1:30001", "alias": "1.21.1", "is_lobby": True}]
    toml = proxy_service.render_velocity_toml(
        {"bind_port": 25565, "domain": "mc.x.de", "motd": "x", "max_players": 10}, be, "1.21.1")
    assert '"1.21.1" = "127.0.0.1:30001"' in toml
    assert '"1.21.1.mc.x.de" = ["1.21.1"]' in toml   # forced-host mit dem quoted Namen


def test_render_velocity_toml_escapes_control_chars_in_motd():
    """Mehrzeilige MOTD darf die velocity.toml nicht zerstoeren (Basic-String verbietet
    rohe Steuerzeichen)."""
    from app.services import proxy_service

    toml = proxy_service.render_velocity_toml(
        {"bind_port": 25565, "domain": "", "motd": "Zeile1\nZeile2\tTab", "max_players": 10}, [], None)
    motd_line = next(l for l in toml.splitlines() if l.startswith("motd"))
    assert motd_line == 'motd = "Zeile1\\nZeile2\\tTab"'   # literal \n \t, kein roher Umbruch
    assert "\n" not in motd_line[7:]


# --- Backend-Auswahl (nur Bukkit-Typen sind Velocity-Backends) -----------------
def test_velocity_backends_only_bukkit_types(client):
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import proxy_service

    with dbs.SessionLocal() as db:
        lobby = Server(
            name="lobby", slug="vlob", server_type="paper", mc_version="26.2",
            base_path="C:/tmp/vlob", gateway_enabled=True, gateway_hostname="lobby",
            gateway_is_default=True, port=30001,
        )
        atm = Server(
            name="atm", slug="vatm", server_type="neoforge", mc_version="1.21.1",
            base_path="C:/tmp/vatm", gateway_enabled=True, gateway_hostname="atm10",
            port=30002,
        )
        db.add_all([lobby, atm])
        db.commit()

        backends, lobby_name = proxy_service.velocity_backends(db)
        names = {b["name"] for b in backends}
        assert "lobby" in names           # Paper -> Backend
        assert "atm10" not in names       # NeoForge -> KEIN Backend (nativer Transfer)
        assert lobby_name == "lobby"
        lobby_backend = next(b for b in backends if b["name"] == "lobby")
        assert lobby_backend["address"] == "127.0.0.1:30001"


# --- is_velocity_backend -------------------------------------------------------
def test_is_velocity_backend_gating():
    from app.models.server import Server
    from app.services import server_service

    paper = Server(name="p", slug="p", server_type="paper", base_path="C:/tmp/p",
                   gateway_enabled=True, port=1)
    forge = Server(name="f", slug="f", server_type="neoforge", base_path="C:/tmp/f",
                   gateway_enabled=True, port=2)

    assert server_service.is_velocity_backend(paper, network_mode="velocity") is True
    assert server_service.is_velocity_backend(forge, network_mode="velocity") is False
    assert server_service.is_velocity_backend(paper, network_mode="gateway") is False
    paper.gateway_enabled = False
    assert server_service.is_velocity_backend(paper, network_mode="velocity") is False


# --- Modern Forwarding schreiben ----------------------------------------------
def test_apply_velocity_backend_forwarding_writes_files(tmp_path):
    import yaml

    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "backend"
    base.mkdir()
    srv = Server(name="b", slug="b", server_type="paper", base_path=str(base),
                 gateway_enabled=True, port=30010)

    notes = server_service.apply_velocity_backend_forwarding(srv, "s3cr3t")
    assert any("Velocity-Forwarding aktiv" in n for n in notes)

    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=false" in props
    assert "server-ip=127.0.0.1" in props

    data = yaml.safe_load((base / "config" / "paper-global.yml").read_text(encoding="utf-8"))
    vel = data["proxies"]["velocity"]
    assert vel["enabled"] is True
    assert vel["online-mode"] is True
    assert vel["secret"] == "s3cr3t"


def test_apply_velocity_backend_forwarding_failsafe_online_mode(tmp_path):
    """Wenn paper-global.yml NICHT schreibbar ist, darf der Server NICHT als offline-mode
    starten (sonst Username-Spoofing) -> Fail-safe online-mode=true."""
    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "backend2"
    base.mkdir()
    (base / "config").write_text("ich bin eine datei, kein ordner", encoding="utf-8")  # mkdir schlaegt fehl
    srv = Server(name="b2", slug="b2", server_type="paper", base_path=str(base),
                 gateway_enabled=True, port=30012)

    notes = server_service.apply_velocity_backend_forwarding(srv, "s3cr3t")
    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=true" in props        # Fail-safe: KEIN offline-mode ohne Forwarding
    assert "online-mode=false" not in props
    assert any("online-mode=true erzwungen" in n for n in notes)


def test_cleanup_velocity_leftovers_reverts(tmp_path):
    """Nicht-Backends (Modded-Transfer-Ziele) muessen wieder oeffentlich + online werden."""
    from app.models.server import Server
    from app.services import server_service

    base = tmp_path / "modded"
    base.mkdir()
    (base / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\n", encoding="utf-8")
    srv = Server(name="m", slug="m", server_type="neoforge", base_path=str(base),
                 gateway_enabled=True, port=30011)

    server_service.cleanup_velocity_leftovers(srv)
    props = (base / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=true" in props
    assert "server-ip=\n" in props or props.rstrip().endswith("server-ip=")


# --- Menue-Ziele: Modded direkt, Backend ueber Proxy ---------------------------
def test_menu_targets_velocity_mode(client):
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import app_setting_service as A
    from app.services import lobby_service

    with dbs.SessionLocal() as db:
        A.set_network_domain(db, "mc.example.de")
        A.set_network_port(db, 25565)
        A.set_network_mode(db, "velocity")
        lobby = Server(name="lobby", slug="mlob", server_type="paper", mc_version="26.2",
                       base_path="C:/tmp/mlob", gateway_enabled=True, gateway_hostname="lobby",
                       gateway_is_default=True, port=30001)
        smp = Server(name="smp", slug="msmp", server_type="paper", mc_version="26.2",
                     base_path="C:/tmp/msmp", gateway_enabled=True, gateway_hostname="smp",
                     port=30002)
        atm = Server(name="atm", slug="matm", server_type="neoforge", mc_version="1.21.1",
                     base_path="C:/tmp/matm", gateway_enabled=True, gateway_hostname="atm10",
                     port=30003)
        db.add_all([lobby, smp, atm])
        db.commit()

        menu = {s["key"]: s for s in lobby_service.get_menu_servers(db, exclude_id=lobby.id)}
        # Modded -> DIREKTER Port (nativer Transfer, bypass Proxy)
        assert menu["atm10"]["port"] == 30003
        # Vanilla/Bukkit-Backend -> Proxy-Port (forced-host routet intern)
        assert menu["smp"]["port"] == 25565


def test_menu_targets_gateway_mode_unchanged(client):
    """Regression: im gateway-Modus zeigen ALLE Ziele weiter auf den network_port."""
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import app_setting_service as A
    from app.services import lobby_service

    with dbs.SessionLocal() as db:
        A.set_network_domain(db, "mc.example.de")
        A.set_network_port(db, 25565)
        A.set_network_mode(db, "gateway")
        lobby = Server(name="lobby", slug="glob", server_type="paper", mc_version="1.21.1",
                       base_path="C:/tmp/glob", gateway_enabled=True, gateway_hostname="lobby",
                       gateway_is_default=True, port=30001)
        atm = Server(name="atm", slug="gatm", server_type="forge", mc_version="1.20.1",
                     base_path="C:/tmp/gatm", gateway_enabled=True, gateway_hostname="atm10",
                     port=30003)
        db.add_all([lobby, atm])
        db.commit()

        menu = {s["key"]: s for s in lobby_service.get_menu_servers(db, exclude_id=lobby.id)}
        assert menu["atm10"]["port"] == 25565   # gateway: ueber den network_port


# --- Runtime-Config + Secret ---------------------------------------------------
def test_velocity_config_runtime_enabled_and_secret(client):
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "velocity")

    cfg = A.get_velocity_config_runtime()
    assert cfg["enabled"] is True
    assert cfg["secret"]                       # wird beim ersten Abruf erzeugt
    assert len(cfg["secret"]) >= 16

    with dbs.SessionLocal() as db:
        # Zweiter Abruf -> selbes Secret (persistiert, nicht neu erzeugt)
        assert A.get_velocity_forwarding_secret(db) == cfg["secret"]


def test_velocity_config_runtime_disabled_off_mode(client):
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "off")
    cfg = A.get_velocity_config_runtime()
    assert cfg["enabled"] is False


# --- UNIVERSAL-Routing (Gateway vorn, Velocity intern) -------------------------
def test_needs_velocity_vanilla_gating():
    """UNIVERSAL-Modus: nicht-767-Joins -> Velocity; 767 + Status + gateway-Modus nicht."""
    from types import SimpleNamespace

    from app.services import gateway_service as gw
    from app.services.mc_protocol import JOIN_NEXT_STATES

    routes = gw.GatewayRoutes(velocity_enabled=True, velocity_internal_port=25605)
    join = next(iter(JOIN_NEXT_STATES))
    assert gw._needs_velocity_vanilla(routes, SimpleNamespace(protocol_version=774, next_state=join)) is True
    assert gw._needs_velocity_vanilla(routes, SimpleNamespace(protocol_version=767, next_state=join)) is False
    off = gw.GatewayRoutes(velocity_enabled=False, velocity_internal_port=25605)
    assert gw._needs_velocity_vanilla(off, SimpleNamespace(protocol_version=774, next_state=join)) is False
    assert gw._needs_velocity_vanilla(routes, SimpleNamespace(protocol_version=774, next_state=999)) is False


def test_build_gateway_routes_velocity_vs_gateway(client):
    import app.db.session as dbs
    from app.services import app_setting_service as A
    from app.services import gateway_service as gw

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "velocity")
        A.set_velocity_internal_port(db, 25605)
        routes = gw.build_gateway_routes(db)
    assert routes.velocity_enabled is True and routes.velocity_internal_port == 25605

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "gateway")
        routes = gw.build_gateway_routes(db)
    assert routes.velocity_enabled is False and routes.velocity_internal_port == 0


def test_velocity_backend_ids_only_bukkit(client):
    """Alias auf ein Bukkit-Backend (Lobby) laeuft ueber Velocity; modded-Server nicht."""
    import app.db.session as dbs
    from app.models.server import Server
    from app.services import app_setting_service as A
    from app.services import gateway_service as gw

    with dbs.SessionLocal() as db:
        A.set_network_mode(db, "velocity")
        A.set_network_domain(db, "mc.example.de")
        lobby = Server(name="lobby", slug="vbid_l", server_type="paper", mc_version="26.2",
                       base_path="C:/tmp/vbidl", gateway_enabled=True, gateway_hostname="lobby",
                       gateway_is_default=True, port=25569)
        atm = Server(name="atm", slug="vbid_a", server_type="neoforge", mc_version="1.21.1",
                     base_path="C:/tmp/vbida", gateway_enabled=True, gateway_hostname="atm10",
                     port=25590)
        db.add_all([lobby, atm])
        db.commit()
        routes = gw.build_gateway_routes(db)
        assert lobby.id in routes.velocity_backend_ids      # Bukkit -> ueber Velocity
        assert atm.id not in routes.velocity_backend_ids    # modded -> nativ/Hub


# --- UI ------------------------------------------------------------------------
def test_settings_page_shows_velocity_button(client):
    _login(client)
    resp = client.get("/settings")
    assert resp.status_code == 200
    assert 'action="/settings/velocity/auto-create"' in resp.text
    assert '<option value="velocity"' in resp.text
