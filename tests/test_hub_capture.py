"""Tests fuer den Capture-Service: Recorder-Kern + Orchestrierungs-Guards."""

from __future__ import annotations

import socket
import threading
from pathlib import Path

from app.services import hub_capture_service as C


def test_record_streams_writes_replay_and_forwards(tmp_path):
    cli_relay, cli_test = socket.socketpair()   # "Client" <-> Relay
    bk_relay, bk_test = socket.socketpair()      # Relay <-> "Backend"
    out = str(tmp_path / "cap.replay")

    result: dict = {}

    def run():
        # Ende via Grace kurz nach dem PLAY-Login (kein Chunk-Frame im Test).
        result["r"] = C._record_streams(cli_relay, bk_relay, out,
                                        max_seconds=3.0, grace_after_login=0.3)

    t = threading.Thread(target=run, daemon=True)
    t.start()

    frame_cs = b"\x03" + b"\x00hi"                  # [len=3][id=0x00]"hi"  (C->S)
    frame_login = b"\x05" + b"\x2b\x00\x00\x00\x01"  # [len=5][id=0x2B PLAY-Login][eid] (S->C)
    cli_test.sendall(frame_cs)        # Client -> Relay -> Backend
    bk_test.sendall(frame_login)      # Backend -> Relay -> Client (schliesst Config ab)

    # Weiterleitung WAEHREND des Aufnahmefensters pruefen (nach dem Close verwirft
    # der Windows-Socketpair gepufferte Daten).
    bk_test.settimeout(2.0)
    cli_test.settimeout(2.0)
    assert bk_test.recv(64) == frame_cs      # Backend hat den Client-Frame bekommen
    assert cli_test.recv(64) == frame_login  # Client hat den PLAY-Login bekommen

    t.join(timeout=4)
    assert result["r"][0] is True            # PLAY-Login gesehen -> Aufnahme gilt als komplett

    data = Path(out).read_bytes()
    assert data.startswith(b"MCRP\x01")
    assert frame_cs in data           # C->S-Frame mitgeschnitten
    assert frame_login in data        # S->C PLAY-Login mitgeschnitten


def test_record_streams_without_play_login_is_error(tmp_path):
    """Config nie fertig (kein 0x2B) -> KEIN (unvollstaendiges) Replay schreiben."""
    cli_relay, cli_test = socket.socketpair()
    bk_relay, bk_test = socket.socketpair()
    out = str(tmp_path / "partial.replay")

    result: dict = {}

    def run():
        result["r"] = C._record_streams(cli_relay, bk_relay, out,
                                        max_seconds=0.6, grace_after_login=0.3)

    t = threading.Thread(target=run, daemon=True)
    t.start()
    bk_test.sendall(b"\x03" + b"\x05ab")   # irgendein Config-Frame, aber kein PLAY-Login
    t.join(timeout=3)

    ok, _n, msg = result["r"]
    assert ok is False
    assert "PLAY-Login" in msg
    assert not Path(out).exists()          # unvollstaendiges Replay nicht gespeichert


def test_record_streams_empty_is_error(tmp_path):
    cli_relay, _cli_test = socket.socketpair()
    bk_relay, _bk_test = socket.socketpair()
    out = str(tmp_path / "empty.replay")
    ok, n, msg = C._record_streams(cli_relay, bk_relay, out, max_seconds=0.3)
    assert ok is False
    assert n == 0
    assert not Path(out).exists()


def test_read_property(tmp_path):
    (tmp_path / "server.properties").write_text(
        "online-mode=true\nnetwork-compression-threshold=256\nmotd=Hi\n", encoding="utf-8"
    )
    assert C._read_property(str(tmp_path), "online-mode") == "true"
    assert C._read_property(str(tmp_path), "network-compression-threshold") == "256"
    assert C._read_property(str(tmp_path), "does-not-exist") is None
    assert C._read_property(str(tmp_path / "nope"), "online-mode") is None


def test_start_capture_guard_and_status(monkeypatch):
    monkeypatch.setattr(C, "_run_capture", lambda sid, uid: None)  # kein echter Restart
    C._SESSIONS.clear()

    assert C.capture_status(777) is None

    ok, _msg = C.start_capture_for_server(777, relay_port=25611)
    assert ok is True
    st = C.capture_status(777)
    assert st["status"] == "preparing"
    assert st["relay_port"] == 25611
    assert st["active"] is True

    # Zweiter Start waehrend aktiv -> abgelehnt
    ok2, msg2 = C.start_capture_for_server(777)
    assert ok2 is False
    assert "bereits" in msg2.lower()

    C._SESSIONS.clear()


def test_capture_route_button_and_status(client, tmp_path, monkeypatch):
    from sqlalchemy import select

    import app.db.session as dbs
    from app.models.server import Server
    from app.services import hub_capture_service

    client.post("/login", data={"username": "admin", "password": "admin123!"})

    with dbs.SessionLocal() as db:
        db.add(Server(name="ATM10 R", slug="atm10r", server_type="neoforge",
                      mc_version="1.21.1", base_path=str(tmp_path / "atm"),
                      gateway_enabled=True, gateway_hostname="atm10r", port=25601))
        db.commit()
        sid = db.scalar(select(Server).where(Server.slug == "atm10r")).id

    # Detail-Seite zeigt den Capture-Knopf (neoforge-Server)
    resp = client.get(f"/servers/{sid}")
    assert resp.status_code == 200
    assert f'action="/servers/{sid}/hub/capture"' in resp.text

    # POST startet die Aufnahme (ohne echten Server-Neustart)
    calls: dict = {}

    def fake_start(server_id, user_id=None):
        calls["sid"] = server_id
        return True, "ok"

    monkeypatch.setattr(hub_capture_service, "start_capture_for_server", fake_start)
    resp = client.post(f"/servers/{sid}/hub/capture")
    assert resp.status_code == 200  # folgt Redirect
    assert calls["sid"] == sid

    # Status-Endpoint liefert JSON
    resp = client.get(f"/servers/{sid}/hub/capture/status")
    assert resp.status_code == 200
    assert "capture" in resp.json()
