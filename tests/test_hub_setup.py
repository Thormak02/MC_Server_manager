"""Tests fuer die Ein-Klick-Einrichtung des Universal-Hubs (Standard-Eingang)."""

from __future__ import annotations


def _login(client):
    client.post("/login", data={"username": "admin", "password": "admin123!"})


def _patch_reconcilers(monkeypatch, running: bool):
    from app.services import gateway_service, hub_lobby_service, viaproxy_service

    monkeypatch.setattr(hub_lobby_service, "reconcile_hub_lobby", lambda: None)
    monkeypatch.setattr(viaproxy_service, "reconcile_viaproxy", lambda: None)
    monkeypatch.setattr(gateway_service, "reconcile_gateway", lambda: None)
    monkeypatch.setattr(hub_lobby_service, "is_running", lambda: running)


def test_create_auto_hub_sets_core_switches(client, monkeypatch):
    _patch_reconcilers(monkeypatch, running=True)

    import app.db.session as dbs
    from app.services import app_setting_service as A
    from app.services import hub_setup_service

    with dbs.SessionLocal() as db:
        ok, msg = hub_setup_service.create_auto_hub(db, initiated_by_user_id=None)
        assert ok is True
        assert "eingerichtet" in msg
        assert A.get_network_mode(db) == "gateway"
        assert A.get_dispatcher_enabled(db) is True
        assert A.get_hub_lobby_enabled(db) is True


def test_create_auto_hub_warns_when_listener_down(client, monkeypatch):
    _patch_reconcilers(monkeypatch, running=False)

    import app.db.session as dbs
    from app.services import hub_setup_service

    with dbs.SessionLocal() as db:
        ok, msg = hub_setup_service.create_auto_hub(db, initiated_by_user_id=None)
        assert ok is True
        assert "Hinweis" in msg  # Domain fehlt und/oder Listener laeuft nicht


def test_auto_create_hub_route(client, monkeypatch):
    _patch_reconcilers(monkeypatch, running=False)
    _login(client)

    resp = client.post("/settings/hub/auto-create")
    assert resp.status_code == 200  # folgt Redirect -> /settings

    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        assert A.get_hub_lobby_enabled(db) is True
        assert A.get_dispatcher_enabled(db) is True
        assert A.get_network_mode(db) == "gateway"


def test_settings_page_shows_auto_hub_button(client):
    _login(client)
    resp = client.get("/settings")
    assert resp.status_code == 200
    assert 'action="/settings/hub/auto-create"' in resp.text
