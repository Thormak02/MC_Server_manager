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
    assert 'bind = "0.0.0.0:25565"' in toml
    assert 'player-info-forwarding-mode = "modern"' in toml
    assert 'forwarding-secret-file = "forwarding.secret"' in toml
    assert 'lobby = "127.0.0.1:30001"' in toml
    assert 'smp = "127.0.0.1:30002"' in toml
    assert 'try = ["lobby"]' in toml
    assert '"lobby.mc.example.de" = ["lobby"]' in toml
    assert '"smp.mc.example.de" = ["smp"]' in toml
    assert "read-timeout = 185000" in toml


def test_render_velocity_toml_no_backends():
    from app.services import proxy_service

    cfg = {"bind_port": 25565, "domain": "", "motd": "x", "max_players": 20}
    toml = proxy_service.render_velocity_toml(cfg, [], None)
    assert "try = []" in toml           # kein Backend -> leere try-Liste, valides TOML
    assert 'bind = "0.0.0.0:25565"' in toml


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


# --- UI ------------------------------------------------------------------------
def test_settings_page_shows_velocity_button(client):
    _login(client)
    resp = client.get("/settings")
    assert resp.status_code == 200
    assert 'action="/settings/velocity/auto-create"' in resp.text
    assert '<option value="velocity"' in resp.text
