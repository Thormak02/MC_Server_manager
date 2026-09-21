"""Vorab-Pruefung fuer den Serverwechsel aus der Lobby (Graceful Rejection).

Ein nativer Transfer trennt die Lobby-Verbindung - deshalb wird VOR dem Wechsel geprueft,
ob der Spieler auf dem Ziel-Server ueberhaupt landen darf.
"""
from types import SimpleNamespace

import pytest

from app.services import lobby_service


def _server(**kw):
    base = {"id": 7, "name": "David", "sleep_enabled": False, "base_path": "x"}
    base.update(kw)
    return SimpleNamespace(**base)


@pytest.fixture()
def _env(monkeypatch):
    """Default: Server laeuft, keine Whitelist, keine Bans, nicht voll."""
    monkeypatch.setattr("app.services.process_service.is_running", lambda sid: True)
    monkeypatch.setattr("app.services.process_service.get_player_counts", lambda s: (1, 20))
    monkeypatch.setattr("app.services.file_service.get_whitelist_enabled", lambda s: False)
    monkeypatch.setattr("app.services.file_service.list_access_entries", lambda s, k: [])
    return monkeypatch


def test_allowed_by_default(_env):
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True and reason == ""


def test_offline_without_sleep_is_rejected(_env):
    _env.setattr("app.services.process_service.is_running", lambda sid: False)
    ok, reason = lobby_service.check_join_allowed(None, _server(sleep_enabled=False), "Thormak")
    assert ok is False and "offline" in reason.lower()


def test_offline_with_sleep_is_allowed(_env):
    """Sleep-Server werden beim Beitritt geweckt -> kein Grund abzulehnen."""
    _env.setattr("app.services.process_service.is_running", lambda sid: False)
    ok, _reason = lobby_service.check_join_allowed(None, _server(sleep_enabled=True), "Thormak")
    assert ok is True


def test_banned_player_is_rejected(_env):
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Thormak", "reason": "Griefing"}] if k == "banned_players" else [])
    ok, reason = lobby_service.check_join_allowed(None, _server(), "thormak")   # Case egal
    assert ok is False and "gebannt" in reason.lower() and "Griefing" in reason


def test_whitelist_blocks_unlisted_player(_env):
    _env.setattr("app.services.file_service.get_whitelist_enabled", lambda s: True)
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Jemand"}] if k == "whitelist" else [])
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is False and "whitelist" in reason.lower()


def test_whitelist_allows_listed_player(_env):
    _env.setattr("app.services.file_service.get_whitelist_enabled", lambda s: True)
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Thormak"}] if k == "whitelist" else [])
    ok, _reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True


def test_empty_whitelist_does_not_lock_out(_env):
    """Fail-open: Whitelist an, aber Datei leer/unlesbar -> nicht aussperren."""
    _env.setattr("app.services.file_service.get_whitelist_enabled", lambda s: True)
    _env.setattr("app.services.file_service.list_access_entries", lambda s, k: [])
    ok, _reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True


def test_full_server_is_rejected(_env):
    _env.setattr("app.services.process_service.get_player_counts", lambda s: (20, 20))
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is False and "voll" in reason.lower()


def test_unreadable_files_fail_open(_env):
    def boom(*a, **k):
        raise OSError("Ordner weg")

    _env.setattr("app.services.file_service.get_whitelist_enabled", boom)
    _env.setattr("app.services.file_service.list_access_entries", boom)
    ok, _reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True


def test_missing_server_is_rejected(_env):
    ok, reason = lobby_service.check_join_allowed(None, None, "Thormak")
    assert ok is False and "nicht gefunden" in reason.lower()
