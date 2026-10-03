import importlib

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(tmp_path, monkeypatch):
    db_path = tmp_path / "test.db"
    monkeypatch.setenv("MCSM_DATABASE_URL", f"sqlite:///{db_path}")
    monkeypatch.setenv("MCSM_SECRET_KEY", "test-secret")
    monkeypatch.setenv("MCSM_INITIAL_SUPERADMIN_USERNAME", "admin")
    monkeypatch.setenv("MCSM_INITIAL_SUPERADMIN_PASSWORD", "admin123!")
    monkeypatch.setenv("MCSM_INGAME_RESTART_DELAY_SECONDS", "1")
    monkeypatch.setenv(
        "MCSM_INGAME_RESTART_WARNING_MESSAGE",
        "Server restartet in {seconds} Sekunden durch /restart.",
    )
    monkeypatch.setenv("MCSM_PROVISIONING_OFFLINE_MODE", "true")
    monkeypatch.setenv("MCSM_CSRF_PROTECTION_ENABLED", "false")

    import app.core.config as config

    config.get_settings.cache_clear()

    import app.db.session as db_session
    import app.db.init_db as init_db
    import app.services.process_service as process_service
    import app.services.sleep_proxy_service as sleep_proxy_service
    import app.main as main_module

    importlib.reload(db_session)
    importlib.reload(init_db)
    # process_service / sleep_proxy_service halten SessionLocal/engine als
    # Modul-Referenz (Autostart-Thread, Sleep-Proxy, Idle-Monitor). Ohne Reload
    # zeigt diese Referenz auf die DB des ersten Tests -> spaetere Tests laufen
    # gegen ein veraltetes Schema ("no such column"). Reload bindet sie an die
    # frische Test-DB.
    importlib.reload(process_service)
    importlib.reload(sleep_proxy_service)
    importlib.reload(main_module)

    with TestClient(main_module.app) as test_client:
        yield test_client


def pytest_configure(config):
    config.addinivalue_line(
        "markers",
        "real_wake: dieser Test prueft request_wake selbst und bekommt deshalb den "
        "echten Weckpfad statt der Sicherheits-Attrappe",
    )


@pytest.fixture(autouse=True)
def _never_really_start_a_server(monkeypatch, request):
    """Sicherheitsnetz: kein Test darf einen echten Serverstart ausloesen.

    ``sleep_proxy_service.request_wake`` startet absichtlich einen Hintergrund-Thread,
    der ``process_service.start_server`` ruft (Modpack-Install, Java-Prep, Popen).
    Erreicht ein Test diesen Pfad - z.B. ueber eine Join-Pruefung auf einem Server mit
    ``sleep_enabled`` -, laeuft der Thread weiter, waehrend das Fixture die Test-DB
    schon abgeraeumt hat: "no such table: servers" aus einem fremden Thread, sporadisch
    und schwer zu finden. Schlimmer noch koennte er bei einem Fixture-Server mit
    gueltigem Startbefehl tatsaechlich einen Prozess starten.

    Wer den echten Pfad pruefen will, markiert seinen Test mit
    ``@pytest.mark.real_wake``.
    """
    if request.node.get_closest_marker("real_wake"):
        return []

    gerufen: list[int] = []

    def _fake_request_wake(server_id):
        gerufen.append(int(server_id))
        return True, "Start angestossen (Test-Attrappe)"

    from app.services import sleep_proxy_service

    monkeypatch.setattr(sleep_proxy_service, "request_wake", _fake_request_wake,
                        raising=False)
    return gerufen
