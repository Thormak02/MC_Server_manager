"""Tests fuer den manuellen Sleep-Button (Server auf On-Demand schlafen legen)."""


def _login_admin(client):
    resp = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert resp.status_code == 303


def _make_server(tmp_path, *, sleep_enabled, status):
    from app.db.session import SessionLocal
    from app.models.server import Server

    base = tmp_path / "sleepsrv"
    base.mkdir(exist_ok=True)
    with SessionLocal() as db:
        srv = Server(
            name="sleep-srv",
            slug="sleep-srv",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
            status=status,
            sleep_enabled=sleep_enabled,
            port=25565,
            sleep_internal_port=25600,
        )
        db.add(srv)
        db.commit()
        db.refresh(srv)
        return srv.id


def _patch_low_level(monkeypatch, counters):
    from app.services import process_service, sleep_proxy_service

    monkeypatch.setattr(
        sleep_proxy_service,
        "reconcile_proxies",
        lambda: counters.__setitem__("reconcile", counters.get("reconcile", 0) + 1),
    )

    def fake_stop(db, server, initiated_by_user_id, **kw):
        counters["stop"] = counters.get("stop", 0) + 1
        server.status = "stopped"
        db.add(server)
        db.commit()
        return True, "Server gestoppt."

    monkeypatch.setattr(process_service, "stop_server", fake_stop)


def test_sleep_requires_sleep_enabled(client, tmp_path, monkeypatch):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import sleep_proxy_service

    counters: dict[str, int] = {}
    _patch_low_level(monkeypatch, counters)
    sid = _make_server(tmp_path, sleep_enabled=False, status="running")

    with SessionLocal() as db:
        srv = db.get(Server, sid)
        ok, msg = sleep_proxy_service.sleep_server(db, srv, None)

    assert ok is False
    assert "nicht aktiviert" in msg
    assert counters.get("stop", 0) == 0


def test_sleep_stops_and_binds_proxy(client, tmp_path, monkeypatch):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import sleep_proxy_service

    counters: dict[str, int] = {}
    _patch_low_level(monkeypatch, counters)
    sid = _make_server(tmp_path, sleep_enabled=True, status="running")

    with SessionLocal() as db:
        srv = db.get(Server, sid)
        ok, msg = sleep_proxy_service.sleep_server(db, srv, None)

    assert ok is True
    assert "schlaeft jetzt" in msg
    assert counters.get("stop", 0) == 1
    assert counters.get("reconcile", 0) >= 1


def test_sleep_when_already_stopped_does_not_stop_again(client, tmp_path, monkeypatch):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import sleep_proxy_service

    counters: dict[str, int] = {}
    _patch_low_level(monkeypatch, counters)
    sid = _make_server(tmp_path, sleep_enabled=True, status="stopped")

    with SessionLocal() as db:
        srv = db.get(Server, sid)
        ok, msg = sleep_proxy_service.sleep_server(db, srv, None)

    assert ok is True
    assert "bereits" in msg
    assert counters.get("stop", 0) == 0
    assert counters.get("reconcile", 0) >= 1


def test_sleep_endpoint_redirects(client, tmp_path, monkeypatch):
    counters: dict[str, int] = {}
    _patch_low_level(monkeypatch, counters)
    sid = _make_server(tmp_path, sleep_enabled=True, status="running")

    _login_admin(client)
    resp = client.post(f"/servers/{sid}/sleep", follow_redirects=False)
    assert resp.status_code == 303
    assert counters.get("stop", 0) == 1
