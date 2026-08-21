"""Tests fuer die Per-Pack-Replay-Registry + Hub-Multi-Profil-Auswahl (Path A)."""

from __future__ import annotations

from pathlib import Path

from app.services import hub_replay_service as R
from app.services import hub_service


# ------------------------------- registry ----------------------------------- #
def test_replay_path_for_and_has_replay(monkeypatch, tmp_path):
    monkeypatch.setattr(R, "REPLAY_DIR", str(tmp_path))
    p = R.replay_path_for("atm10")
    assert p.endswith("atm10_capture.replay")
    assert R.has_replay("atm10") is False
    Path(p).write_bytes(b"MCRP\x01")
    assert R.has_replay("atm10") is True


def test_build_pack_registry(client, monkeypatch, tmp_path):
    monkeypatch.setattr(R, "REPLAY_DIR", str(tmp_path))

    import app.db.session as dbs
    from app.models.server import Server

    with dbs.SessionLocal() as db:
        srv = Server(
            name="ATM10 X", slug="atm10x", server_type="neoforge",
            mc_version="1.21.1", base_path=str(tmp_path / "srv"),
            gateway_enabled=True, gateway_hostname="atm10x",
        )
        db.add(srv)
        db.commit()
        db.refresh(srv)
        sid = srv.id

    # ohne Replay-Datei -> nicht in der Registry
    with dbs.SessionLocal() as db:
        assert R.build_pack_registry(db) == {}

    # Replay-Datei anlegen -> in der Registry
    Path(R.replay_path_for("atm10x")).write_bytes(b"MCRP\x01")
    with dbs.SessionLocal() as db:
        assert R.build_pack_registry(db) == {sid: R.replay_path_for("atm10x")}


# --------------------------- hub selection ---------------------------------- #
def _fake_hub(modded, vanilla, pack_profiles):
    hub = object.__new__(hub_service.Hub)  # ohne __init__ (kein Replay-Laden)
    hub.modded = modded
    hub.vanilla = vanilla
    hub.pack_profiles = pack_profiles
    return hub


def test_alias_of():
    assert hub_service.Hub._alias_of("modlobby-2.mc.example.com\x00FML\x00") == "modlobby-2"
    assert hub_service.Hub._alias_of("VANLOBBY.MC.Example.com") == "vanlobby"
    assert hub_service.Hub._alias_of(None) == ""
    assert hub_service.Hub._alias_of("") == ""


def test_pick_profile_per_pack():
    MOD, VAN, P2 = {"m": 1}, {"v": 1}, {"p": 2}
    hub = _fake_hub(MOD, VAN, {2: P2})
    assert hub._pick_profile("modlobby-2.mc.x", "modded") is P2
    assert hub._pick_profile("modlobby-99.mc.x", "modded") is MOD   # unbekannte id -> Default
    assert hub._pick_profile("modlobby.mc.x", "modded") is MOD      # kein Tag -> Default
    assert hub._pick_profile("modlobby-2.mc.x", None) is P2         # Standalone via Alias
    assert hub._pick_profile("vanlobby.mc.x", None) is VAN
    assert hub._pick_profile("egal", "vanilla") is VAN


def test_pick_profile_without_vanilla_falls_to_modded():
    MOD = {"m": 1}
    hub = _fake_hub(MOD, None, {})
    assert hub._pick_profile("vanlobby.mc.x", None) is MOD   # kein Vanilla-Profil geladen
    assert hub._pick_profile("x", "vanilla") is MOD          # force vanilla ohne Profil
