"""Automatische Port-Konfliktloesung: Sleep-/Gateway-Server bekommen vor dem Start
einen internen Port, der nie mit dem Gateway-Port kollidiert; server.properties
wird auf den effektiven Port angeglichen."""


def _make(tmp_path, slug, *, port, **server_kwargs):
    from app.db.session import SessionLocal
    from app.models.server import Server

    base = tmp_path / slug
    base.mkdir()
    (base / "server.properties").write_text("server-port=25565\n", encoding="utf-8")
    with SessionLocal() as db:
        srv = Server(
            name=slug, slug=slug, server_type="paper", mc_version="1.20.1",
            base_path=str(base), port=port, status="stopped", **server_kwargs,
        )
        db.add(srv)
        db.commit()
        return srv.id


def test_prepare_ports_reassigns_internal_colliding_with_gateway(client, tmp_path, monkeypatch):
    import app.services.sleep_proxy_service as sp
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc, server_service

    monkeypatch.setattr(sp, "reconcile_proxies", lambda: None)  # kein echter Socket-Bind
    with SessionLocal() as db:
        svc.set_network_mode(db, "gateway")
        svc.set_network_port(db, 25565)
    # Sleep-Server, dessen interner Port faelschlich dem Gateway-Port entspricht.
    sid = _make(tmp_path, "pp", port=25580, sleep_enabled=True, sleep_internal_port=25565)

    with SessionLocal() as db:
        server_service.prepare_ports_before_start(db, db.get(Server, sid))

    with SessionLocal() as db:
        internal = db.get(Server, sid).sleep_internal_port
    assert internal not in (25565, 25580)  # weder Gateway- noch oeffentlicher Port
    props = (tmp_path / "pp" / "server.properties").read_text(encoding="utf-8")
    assert f"server-port={internal}" in props  # server.properties angeglichen


def test_prepare_ports_warns_standalone_on_gateway_port(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service as svc, server_service

    with SessionLocal() as db:
        svc.set_network_mode(db, "gateway")
        svc.set_network_port(db, 25565)
    sid = _make(tmp_path, "sa", port=25565)  # Standalone genau auf dem Gateway-Port

    with SessionLocal() as db:
        warnings = server_service.prepare_ports_before_start(db, db.get(Server, sid))
    assert any("Netzwerk-Port" in w for w in warnings)


def test_prepare_ports_leaves_internal_when_gateway_disabled(client, tmp_path):
    """Regression: bei AUSgeschaltetem Gateway darf der interne Port 25565 NICHT
    neu vergeben werden (sonst zeigt ein laufender Sleep-Proxy ins Leere)."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import server_service

    sid = _make(tmp_path, "off", port=25570, sleep_enabled=True, sleep_internal_port=25565)
    with SessionLocal() as db:
        warnings = server_service.prepare_ports_before_start(db, db.get(Server, sid))

    with SessionLocal() as db:
        assert db.get(Server, sid).sleep_internal_port == 25565  # unveraendert
    assert not any("Netzwerk-Port" in w for w in warnings)


def test_prepare_ports_no_gateway_warning_when_disabled(client, tmp_path):
    """Regression: ein Standalone-Server auf 25565 bekommt bei AUSgeschaltetem
    Gateway KEINE (falsche) Gateway-Port-Warnung."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import server_service

    sid = _make(tmp_path, "soff", port=25565)
    with SessionLocal() as db:
        warnings = server_service.prepare_ports_before_start(db, db.get(Server, sid))
    assert not any("Netzwerk-Port" in w for w in warnings)


def test_update_settings_sleep_excludes_gateway_port(client, tmp_path, monkeypatch):
    import app.services.sleep_proxy_service as sp
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services.server_service import update_server_settings

    monkeypatch.setattr(sp, "reconcile_proxies", lambda: None)  # kein echter Socket-Bind
    from app.services import app_setting_service as svc

    with SessionLocal() as db:
        svc.set_network_mode(db, "gateway")
        svc.set_network_port(db, 25566)
    # preferred (port+1) waere genau der Gateway-Port -> muss ausgeschlossen werden.
    sid = _make(tmp_path, "slp", port=25565)

    with SessionLocal() as db:
        srv = db.get(Server, sid)
        update_server_settings(
            db, srv, mc_version=srv.mc_version, loader_version=None,
            java_profile_id=None, memory_min_mb=2048, memory_max_mb=4096,
            port=srv.port, auto_restart=False, auto_start_with_manager=False,
            start_mode=srv.start_mode, start_command=None, start_bat_path=None,
            sleep_enabled=True, sleep_delay_seconds=300,
        )
        db.refresh(srv)
        assert srv.sleep_enabled is True
        assert srv.sleep_internal_port not in (25565, 25566)
