"""Velocity-Netzwerk Phase 1: Settings + Provisionierung/Config (netzwerkfrei)."""

import pytest


# --------------------------------------------------------------------------- #
# app_setting_service: Netzwerk-Modus + Velocity-Version
# --------------------------------------------------------------------------- #
def test_network_mode_default_and_switch(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        assert svc.get_network_mode(db) == "off"  # nichts aktiv
        assert svc.get_network_mode_source(db) == "default"

        svc.set_gateway_enabled(db, True)
        assert svc.get_network_mode(db) == "gateway"  # abgeleitet vom Gateway

        svc.set_network_mode(db, "velocity")
        assert svc.get_network_mode(db) == "velocity"
        assert svc.get_network_mode_source(db) == "ui"


def test_network_mode_rejects_invalid(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        with pytest.raises(ValueError):
            svc.set_network_mode(db, "bogus")


def test_velocity_version_setting(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        assert svc.get_velocity_version(db) == ""  # leer = neueste stabile
        svc.set_velocity_version(db, "3.5.1")
        assert svc.get_velocity_version(db) == "3.5.1"
        svc.set_velocity_version(db, "")
        assert svc.get_velocity_version(db) == ""


# --------------------------------------------------------------------------- #
# velocity_service: Versionsauswahl + Download-Resolve (Fill-API gemockt)
# --------------------------------------------------------------------------- #
def test_is_stable_id():
    from app.services import velocity_service as vs

    assert vs._is_stable_id("3.5.1")
    assert not vs._is_stable_id("4.1.0-SNAPSHOT")
    assert not vs._is_stable_id("4.0.0-rc.1")


def test_list_velocity_versions_filters_snapshots(monkeypatch):
    from app.services import velocity_service as vs

    fake = {
        "versions": [
            {"version": {"id": "4.1.0-SNAPSHOT"}},
            {"version": {"id": "3.5.1"}},
            {"version": {"id": "3.5.0"}},
        ]
    }
    monkeypatch.setattr(vs, "fetch_json", lambda url: fake)
    assert vs.list_velocity_versions() == ["3.5.1", "3.5.0"]
    assert vs.latest_stable_version() == "3.5.1"


def test_required_java_for_version_reads_minimum(monkeypatch):
    from app.services import velocity_service as vs

    fake = {"versions": [{"version": {"id": "4.0.0", "java": {"version": {"minimum": 25}}}}]}
    monkeypatch.setattr(vs, "fetch_json", lambda url: fake)
    assert vs.required_java_for_version("4.0.0") == 25
    assert vs.required_java_for_version("unbekannt") == 17  # Fallback


def test_resolve_download_picks_latest_stable_build(monkeypatch):
    from app.services import velocity_service as vs

    builds = [
        {"id": 20, "channel": "STABLE",
         "downloads": {"server:default": {"name": "velocity-3.5.1-20.jar", "url": "https://x/20"}}},
        {"id": 22, "channel": "EXPERIMENTAL",
         "downloads": {"server:default": {"name": "v-22.jar", "url": "https://x/22"}}},
        {"id": 21, "channel": "STABLE",
         "downloads": {"server:default": {"name": "velocity-3.5.1-21.jar", "url": "https://x/21"}}},
    ]
    monkeypatch.setattr(vs, "fetch_json", lambda url: builds)  # blanke Liste
    url, name = vs._resolve_download("3.5.1")
    assert name == "velocity-3.5.1-21.jar" and url == "https://x/21"


# --------------------------------------------------------------------------- #
# velocity_service: velocity.toml + Forwarding-Secret
# --------------------------------------------------------------------------- #
def test_generate_velocity_toml_and_secret(tmp_path, monkeypatch):
    from app.services import velocity_service as vs

    monkeypatch.setattr(vs, "managed_velocity_dir", lambda: tmp_path)

    path = vs.generate_velocity_toml(None, bind_port=25577)
    text = path.read_text(encoding="utf-8")
    assert 'bind = "0.0.0.0:25577"' in text
    assert 'player-info-forwarding-mode = "modern"' in text
    assert 'forwarding-secret-file = "forwarding.secret"' in text

    secret_file = tmp_path / "forwarding.secret"
    assert secret_file.exists() and secret_file.read_text(encoding="utf-8").strip()

    # Secret ist stabil (idempotent), wird nicht bei jedem Aufruf neu erzeugt.
    assert vs.ensure_forwarding_secret() == vs.ensure_forwarding_secret()


def test_default_version_prefers_known_major(monkeypatch):
    from app.services import velocity_service as vs

    fake = {"versions": [{"version": {"id": v}} for v in ["4.0.0", "3.5.1", "3.5.0"]]}
    monkeypatch.setattr(vs, "fetch_json", lambda url: fake)
    # Auto-Default meidet 4.x (unbekanntes Config-Format) -> neueste 3.x.
    assert vs.default_velocity_version() == "3.5.1"
    assert vs.latest_stable_version() == "4.0.0"


# --------------------------------------------------------------------------- #
# reconcile_network: gegenseitiger Ausschluss + Reihenfolge (Port zuerst frei)
# --------------------------------------------------------------------------- #
def test_reconcile_network_ordering(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc
    from app.services import gateway_service, velocity_service as vs

    calls: list[str] = []
    monkeypatch.setattr(gateway_service, "stop_gateway", lambda: calls.append("gw_stop"))
    monkeypatch.setattr(gateway_service, "reconcile_gateway", lambda: calls.append("gw_reconcile"))
    monkeypatch.setattr(vs, "_ensure_velocity_started", lambda: calls.append("vel_start"))
    monkeypatch.setattr(vs, "stop_velocity", lambda **_k: calls.append("vel_stop"))

    with SessionLocal() as db:
        svc.set_network_mode(db, "velocity")
    vs.reconcile_network()
    assert calls == ["gw_stop", "vel_start"]  # erst Gateway freigeben, dann Velocity

    calls.clear()
    with SessionLocal() as db:
        svc.set_network_mode(db, "gateway")
    vs.reconcile_network()
    assert calls == ["vel_stop", "gw_reconcile"]  # erst Velocity freigeben, dann Gateway


def test_start_velocity_aborts_when_mode_not_velocity(client, monkeypatch, tmp_path):
    """TOCTOU-Schutz: wird der Modus waehrend der Provisionierung gewechselt,
    darf Velocity NICHT mehr starten (kein Popen)."""
    from types import SimpleNamespace

    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc
    from app.services import java_runtime_service as jrs
    from app.services import velocity_service as vs

    monkeypatch.setattr(vs, "managed_velocity_dir", lambda: tmp_path)
    monkeypatch.setattr(vs, "default_velocity_version", lambda: "3.5.1")
    monkeypatch.setattr(vs, "ensure_velocity_jar", lambda version: (True, ""))
    monkeypatch.setattr(vs, "required_java_for_version", lambda version: 17)
    monkeypatch.setattr(vs, "generate_velocity_toml", lambda db, bind_port: tmp_path / "velocity.toml")
    monkeypatch.setattr(jrs, "ensure_java_available", lambda db, major, on_progress=None: (True, ""))
    monkeypatch.setattr(
        jrs, "_best_profile_for_major", lambda db, major: SimpleNamespace(name="x", java_path="x")
    )
    monkeypatch.setattr(jrs, "build_java_env_from_profile", lambda profile: {})

    popen_calls: list[int] = []
    monkeypatch.setattr(vs.subprocess, "Popen", lambda *a, **k: popen_calls.append(1))

    with SessionLocal() as db:
        svc.set_network_mode(db, "off")  # NICHT velocity
        ok, msg = vs.start_velocity(db)

    assert ok is False and "Modus" in msg
    assert popen_calls == []  # Velocity wurde nicht gestartet


# --------------------------------------------------------------------------- #
# Phase 2: Backend-Namen, velocity.toml mit Servern, Paper-Forwarding
# --------------------------------------------------------------------------- #
def test_velocity_name_validation_and_normalize():
    from app.services import server_service as ss

    assert ss.normalize_velocity_name("  My SMP!  ") == "my-smp"  # Leerzeichen -> '-'
    assert ss.normalize_velocity_name("Lobby-1_2") == "lobby-1_2"
    assert ss.is_valid_velocity_name("lobby")
    assert ss.is_valid_velocity_name("smp-1_2")
    assert not ss.is_valid_velocity_name("")
    assert not ss.is_valid_velocity_name("-lobby")  # muss alphanumerisch starten
    assert not ss.is_valid_velocity_name("a.b")  # kein Punkt erlaubt


def test_render_servers_block_with_lobby_and_forced_hosts():
    from app.services import velocity_service as vs

    members = [
        {"name": "lobby", "port": 30001, "is_lobby": True},
        {"name": "smp", "port": 30002, "is_lobby": False},
    ]
    servers_block, forced_block = vs._render_servers_block(members, "mc.example.de")
    assert 'lobby = "127.0.0.1:30001"' in servers_block
    assert 'smp = "127.0.0.1:30002"' in servers_block
    assert 'try = ["lobby"]' in servers_block
    assert '"smp.mc.example.de" = ["smp"]' in forced_block


def test_render_servers_block_fallback_lobby():
    from app.services import velocity_service as vs

    # Keine explizite Lobby -> erstes Backend wird "try"-Ziel.
    members = [{"name": "solo", "port": 30003, "is_lobby": False}]
    servers_block, _forced = vs._render_servers_block(members, "")
    assert 'try = ["solo"]' in servers_block


def test_paper_velocity_config_merges(tmp_path):
    import yaml

    from app.services import velocity_service as vs

    # Bestehende paper-global.yml mit anderen Keys -> Velocity wird ergaenzt,
    # der Rest bleibt erhalten.
    config = tmp_path / "config"
    config.mkdir()
    (config / "paper-global.yml").write_text(
        "_version: 28\nproxies:\n  bungee-cord:\n    online-mode: true\n",
        encoding="utf-8",
    )
    vs._write_paper_velocity_config(tmp_path, "SECRET123")

    data = yaml.safe_load((config / "paper-global.yml").read_text(encoding="utf-8"))
    assert data["_version"] == 28  # unveraendert
    assert data["proxies"]["bungee-cord"]["online-mode"] is True  # erhalten
    assert data["proxies"]["velocity"] == {
        "enabled": True,
        "online-mode": True,
        "secret": "SECRET123",
    }


def test_apply_backend_forwarding_paper_writes_security_props(tmp_path, client, monkeypatch):
    from types import SimpleNamespace

    from app.services import velocity_service as vs

    monkeypatch.setattr(vs, "managed_velocity_dir", lambda: tmp_path / "velocity")
    (tmp_path / "server").mkdir()
    server = SimpleNamespace(
        id=1, server_type="paper", base_path=str(tmp_path / "server"),
    )
    warnings = vs.apply_backend_forwarding(None, server)
    assert warnings == []  # Paper -> keine Warnung
    props = (tmp_path / "server" / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=false" in props
    assert "server-ip=127.0.0.1" in props
    assert (tmp_path / "server" / "config" / "paper-global.yml").exists()


def test_apply_backend_forwarding_warns_for_non_paper(tmp_path, client, monkeypatch):
    from types import SimpleNamespace

    from app.services import velocity_service as vs

    monkeypatch.setattr(vs, "managed_velocity_dir", lambda: tmp_path / "velocity")
    (tmp_path / "srv").mkdir()
    server = SimpleNamespace(id=2, server_type="fabric", base_path=str(tmp_path / "srv"))
    warnings = vs.apply_backend_forwarding(None, server)
    assert any("Forwarding" in w for w in warnings)  # Hinweis auf fehlendes Modern Forwarding
    # Sicherheits-Properties werden trotzdem gesetzt.
    props = (tmp_path / "srv" / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=false" in props


def test_revert_backend_forwarding_restores_public_online_mode(tmp_path):
    from types import SimpleNamespace

    from app.services import velocity_service as vs

    srv = tmp_path / "srv"
    srv.mkdir()
    (srv / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nmotd=x\n", encoding="utf-8"
    )
    server = SimpleNamespace(id=1, server_type="paper", base_path=str(srv))
    notes = vs.revert_backend_forwarding(None, server)
    assert notes  # etwas wurde zurueckgesetzt
    props = (srv / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=true" in props
    assert "server-ip=\n" in props or props.rstrip().endswith("server-ip=")


def test_revert_ignores_standalone_cracked_server(tmp_path):
    from types import SimpleNamespace

    from app.services import velocity_service as vs

    srv = tmp_path / "srv"
    srv.mkdir()
    # Bewusst 'cracked', aber KEIN loopback-Bind -> keine Velocity-Signatur.
    (srv / "server.properties").write_text("online-mode=false\nmotd=x\n", encoding="utf-8")
    server = SimpleNamespace(id=2, server_type="paper", base_path=str(srv))
    notes = vs.revert_backend_forwarding(None, server)
    assert notes == []
    props = (srv / "server.properties").read_text(encoding="utf-8")
    assert "online-mode=false" in props  # unangetastet


# --------------------------------------------------------------------------- #
# Phase 3: ViaVersion (Cross-Version)
# --------------------------------------------------------------------------- #
def test_velocity_via_enabled_setting(client):
    from app.db.session import SessionLocal
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        assert svc.get_velocity_via_enabled(db) is True  # Default: an
        svc.set_velocity_via_enabled(db, False)
        assert svc.get_velocity_via_enabled(db) is False
        svc.set_velocity_via_enabled(db, True)
        assert svc.get_velocity_via_enabled(db) is True


def test_resolve_via_download_prefers_release(monkeypatch):
    from app.services import velocity_service as vs

    versions = [
        {"version_type": "beta", "loaders": ["velocity"],
         "files": [{"primary": True, "url": "https://x/snap.jar", "filename": "ViaVersion-snap.jar"}]},
        {"version_type": "release", "loaders": ["paper", "velocity"],
         "files": [{"primary": True, "url": "https://x/rel.jar", "filename": "ViaVersion-5.0.0.jar"}]},
    ]
    monkeypatch.setattr(vs, "fetch_json", lambda url: versions)
    url, filename = vs._resolve_via_download("viaversion")
    assert url == "https://x/rel.jar" and filename == "ViaVersion-5.0.0.jar"


def test_ensure_via_plugins_skips_when_present(tmp_path, monkeypatch):
    from app.services import velocity_service as vs

    monkeypatch.setattr(vs, "managed_velocity_dir", lambda: tmp_path)
    plugins = tmp_path / "plugins"
    plugins.mkdir()
    for name in ("ViaVersion-5.0.0.jar", "ViaBackwards-5.0.0.jar", "ViaRewind-4.0.0.jar"):
        (plugins / name).write_text("jar", encoding="utf-8")

    def _fail(*_a, **_k):
        raise AssertionError("download_file darf nicht aufgerufen werden")

    monkeypatch.setattr(vs, "download_file", _fail)
    assert vs.ensure_via_plugins() == []  # alle vorhanden -> kein Download


def test_remove_via_plugins(tmp_path, monkeypatch):
    from app.services import velocity_service as vs

    monkeypatch.setattr(vs, "managed_velocity_dir", lambda: tmp_path)
    plugins = tmp_path / "plugins"
    plugins.mkdir()
    (plugins / "ViaVersion-5.0.0.jar").write_text("j", encoding="utf-8")
    (plugins / "ViaBackwards-5.0.0.jar").write_text("j", encoding="utf-8")
    (plugins / "SomeOtherPlugin.jar").write_text("j", encoding="utf-8")  # bleibt

    assert vs.remove_via_plugins() is True
    assert not list(plugins.glob("Via*.jar"))
    assert (plugins / "SomeOtherPlugin.jar").exists()  # Fremd-Plugin unangetastet


def test_ensure_via_plugins_is_best_effort_on_mkdir_failure(tmp_path, monkeypatch):
    from app.services import velocity_service as vs

    monkeypatch.setattr(vs, "managed_velocity_dir", lambda: tmp_path)
    # Eine DATEI namens 'plugins' -> mkdir schlaegt fehl, darf aber NICHT werfen.
    (tmp_path / "plugins").write_text("not a dir", encoding="utf-8")

    notes = vs.ensure_via_plugins()  # darf nicht raisen
    assert notes and "plugins" in notes[0].lower()


# --------------------------------------------------------------------------- #
# Phase 4: Sleep-Integration (lokaler Sleep-Proxy vor Velocity-Backends)
# --------------------------------------------------------------------------- #
def test_velocity_target_port_sleep_vs_direct():
    from types import SimpleNamespace

    from app.services import server_service as ss

    direct = SimpleNamespace(
        velocity_enabled=True, sleep_enabled=False, gateway_enabled=False,
        port=25570, sleep_internal_port=30001,
    )
    # Ohne Sleep zeigt Velocity direkt auf den Backend-Port (intern).
    assert ss.velocity_target_port(direct) == 30001

    sleepy = SimpleNamespace(
        velocity_enabled=True, sleep_enabled=True, gateway_enabled=False,
        port=25570, sleep_internal_port=30001,
    )
    # Mit Sleep zeigt Velocity auf den lokalen Sleep-Proxy-Port (= server.port).
    assert ss.velocity_target_port(sleepy) == 25570


def test_reconcile_proxies_velocity_backend_binds_localhost(client, tmp_path):
    import app.services.sleep_proxy_service as sp
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc

    pub = sp.find_free_port()
    internal = sp.find_free_port()
    with SessionLocal() as db:
        svc.set_network_mode(db, "velocity")
        svc.set_gateway_port(db, 25565)
        base = tmp_path / "vsleep"
        base.mkdir()
        srv = Server(
            name="vsleep", slug="vsleep", server_type="paper", mc_version="1.21.1",
            base_path=str(base), port=pub, status="stopped",
            sleep_enabled=True, sleep_internal_port=internal,
            velocity_enabled=True, velocity_name="smp",
        )
        db.add(srv)
        db.commit()
        sid = srv.id

    try:
        sp.reconcile_proxies()
        listener = sp._PROXIES.get(sid)
        assert listener is not None
        assert listener.bind_host == "127.0.0.1"  # nur lokal, hinter Velocity
        assert listener.public_port == pub and listener.internal_port == internal

        # Modus weg von velocity -> Backend faellt auf oeffentlichen Wake-Proxy
        # zurueck (sonst waere der Sleep-Server unerreichbar), rebind auf 0.0.0.0.
        with SessionLocal() as db:
            svc.set_network_mode(db, "off")
        sp.reconcile_proxies()
        listener2 = sp._PROXIES.get(sid)
        assert listener2 is not None and listener2.bind_host == "0.0.0.0"
    finally:
        sp.stop_proxy(sid)


def test_velocity_sleep_port_collision_is_relocated(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc, server_service

    with SessionLocal() as db:
        svc.set_network_mode(db, "velocity")
        svc.set_gateway_port(db, 25565)
        base = tmp_path / "coll"
        base.mkdir()
        (base / "server.properties").write_text("server-port=25565\n", encoding="utf-8")
        srv = Server(
            name="coll", slug="coll", server_type="paper", mc_version="1.21.1",
            base_path=str(base), port=25565, status="stopped",
            sleep_enabled=True, sleep_delay_seconds=300,
        )
        db.add(srv)
        db.commit()
        sid = srv.id

    with SessionLocal() as db:
        server = db.get(Server, sid)
        server_service.update_server_settings(
            db, server,
            mc_version="1.21.1", loader_version=None, java_profile_id=None,
            memory_min_mb=1024, memory_max_mb=2048, port=25565,
            auto_restart=False, auto_start_with_manager=False,
            start_mode="command", start_command="java -jar x.jar", start_bat_path=None,
            sleep_enabled=True, sleep_delay_seconds=300,
            velocity_enabled=True, velocity_name="smp", velocity_is_lobby=False,
        )

    with SessionLocal() as db:
        server = db.get(Server, sid)
        # server.port (lokaler Sleep-Proxy-Port) wurde vom Velocity-Port wegverschoben.
        assert server.velocity_enabled is True
        assert server.port != 25565
        assert server.port != server.sleep_internal_port
