"""Tests fuer die Manager-Update-Logik (dirty-Tree-Gating + Force-Pfad)."""

from __future__ import annotations

import app.services.update_service as update_service
from app.services.update_service import ManagerUpdateStatus


def _status(**kwargs) -> ManagerUpdateStatus:
    base = dict(
        ok=True,
        message="",
        branch="main",
        behind_count=2,
        dirty=False,
        dirty_tracked=False,
    )
    base.update(kwargs)
    return ManagerUpdateStatus(**base)


def test_can_apply_clean_tree():
    st = _status()
    assert st.has_update is True
    assert st.can_apply is True
    assert st.needs_force is False


def test_untracked_only_still_allows_normal_apply():
    # Reine untracked-Dateien duerfen das normale Update nicht blockieren.
    st = _status(dirty=True, dirty_tracked=False)
    assert st.can_apply is True
    assert st.needs_force is False


def test_tracked_dirty_requires_force():
    st = _status(dirty=True, dirty_tracked=True)
    assert st.can_apply is False
    assert st.needs_force is True


def test_no_update_never_applies():
    st = _status(behind_count=0)
    assert st.has_update is False
    assert st.can_apply is False
    assert st.needs_force is False


def test_trigger_blocks_tracked_dirty_without_force(monkeypatch):
    monkeypatch.setattr(
        update_service,
        "get_manager_update_status",
        lambda *, fetch_remote: _status(dirty=True, dirty_tracked=True),
    )
    called = False

    def _fake_popen(*args, **kwargs):  # pragma: no cover - darf nicht laufen
        nonlocal called
        called = True

    monkeypatch.setattr(update_service.subprocess, "Popen", _fake_popen)

    ok, message = update_service.trigger_manager_update(force=False)
    assert ok is False
    assert "verwerfen" in message.lower()
    assert called is False


def test_trigger_force_passes_force_flag(monkeypatch):
    monkeypatch.setattr(
        update_service,
        "get_manager_update_status",
        lambda *, fetch_remote: _status(dirty=True, dirty_tracked=True),
    )
    captured: dict[str, object] = {}

    def _fake_popen(command, *args, **kwargs):
        captured["command"] = command

    monkeypatch.setattr(update_service.subprocess, "Popen", _fake_popen)

    ok, message = update_service.trigger_manager_update(force=True)
    assert ok is True
    assert "-Force" in captured["command"]
    assert "verworfen" in message.lower()


def test_trigger_no_force_flag_on_clean_apply(monkeypatch):
    monkeypatch.setattr(
        update_service,
        "get_manager_update_status",
        lambda *, fetch_remote: _status(dirty=False, dirty_tracked=False),
    )
    captured: dict[str, object] = {}

    def _fake_popen(command, *args, **kwargs):
        captured["command"] = command

    monkeypatch.setattr(update_service.subprocess, "Popen", _fake_popen)

    ok, _message = update_service.trigger_manager_update(force=False)
    assert ok is True
    assert "-Force" not in captured["command"]
