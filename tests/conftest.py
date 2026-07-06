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
