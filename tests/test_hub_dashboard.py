"""Tests fuer das Hub-Tile im Dashboard, Start/Stop und die editierbaren Felder."""

from __future__ import annotations


def _login(client):
    client.post("/login", data={"username": "admin", "password": "admin123!"})


def _patch_reconcilers(monkeypatch, running: bool):
    from app.services import gateway_service, hub_lobby_service

    monkeypatch.setattr(hub_lobby_service, "reconcile_hub_lobby", lambda: None)
    monkeypatch.setattr(gateway_service, "reconcile_gateway", lambda: None)
    monkeypatch.setattr(hub_lobby_service, "is_running", lambda: running)


def test_dashboard_hub_tile_hidden_when_disabled(client, monkeypatch):
    from app.services import hub_lobby_service

    monkeypatch.setattr(hub_lobby_service, "is_running", lambda: False)
    _login(client)
    resp = client.get("/dashboard")
    assert resp.status_code == 200
    assert "Universal-Hub (Lobby)" not in resp.text


def test_dashboard_hub_tile_shown_running(client, monkeypatch):
    from app.services import hub_lobby_service

    monkeypatch.setattr(hub_lobby_service, "is_running", lambda: True)
    _login(client)
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_hub_lobby_enabled(db, True)

    resp = client.get("/dashboard")
    assert "Universal-Hub (Lobby)" in resp.text
    assert 'action="/hub/stop"' in resp.text  # laeuft -> Stop-Button


def test_dashboard_hub_tile_shown_stopped(client, monkeypatch):
    from app.services import hub_lobby_service

    monkeypatch.setattr(hub_lobby_service, "is_running", lambda: False)
    _login(client)
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_hub_lobby_enabled(db, True)

    resp = client.get("/dashboard")
    assert "Universal-Hub (Lobby)" in resp.text
    assert 'action="/hub/start"' in resp.text  # gestoppt -> Start-Button


def test_hub_start_stop_routes(client, monkeypatch):
    _patch_reconcilers(monkeypatch, running=True)
    _login(client)

    import app.db.session as dbs
    from app.services import app_setting_service as A

    resp = client.post("/hub/start")
    assert resp.status_code == 200
    with dbs.SessionLocal() as db:
        assert A.get_hub_lobby_enabled(db) is True

    _patch_reconcilers(monkeypatch, running=False)
    resp = client.post("/hub/stop")
    assert resp.status_code == 200
    with dbs.SessionLocal() as db:
        assert A.get_hub_lobby_enabled(db) is False


def test_hub_presentation_fields_saved(client):
    _login(client)
    resp = client.post(
        "/settings/universal-lobby",
        data={
            "hub_lobby_port": "25599",
            "hub_lobby_vanilla_port": "25600",
            "viaproxy_port": "25601",
            "hub_lobby_replay": "atm10_capture.replay",
            "hub_name": "Meine Lobby",
            "hub_motd": "Hallo Welt!",
            "hub_max_players": "64",
            "hub_whitelist_enabled": "true",
            "hub_whitelist": "Alice, Bob",
        },
    )
    assert resp.status_code == 200

    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        assert A.get_hub_name(db) == "Meine Lobby"
        assert A.get_hub_motd(db) == "Hallo Welt!"
        assert A.get_hub_max_players(db) == 64
        assert A.get_hub_whitelist_enabled(db) is True
        assert A._parse_name_list(A.get_hub_whitelist(db)) == {"alice", "bob"}
