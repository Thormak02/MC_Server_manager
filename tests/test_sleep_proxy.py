import socket
import threading
import time

from app.services import mc_protocol as mp
from app.services import sleep_proxy_service as sp


def _handshake(next_state: int, port: int) -> bytes:
    payload = (
        mp.encode_varint(0x00)
        + mp.encode_varint(765)
        + mp.encode_string("localhost")
        + int(port).to_bytes(2, "big")
        + mp.encode_varint(next_state)
    )
    return mp.encode_varint(len(payload)) + payload


def test_sleeping_status_response(monkeypatch):
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: False)

    public_port = sp.find_free_port()
    assert sp.start_proxy(9991, public_port, sp.find_free_port())
    try:
        client = socket.create_connection(("127.0.0.1", public_port), timeout=5)
        client.sendall(_handshake(mp.NEXT_STATE_STATUS, public_port))
        client.sendall(mp.encode_varint(1) + mp.encode_varint(0x00))  # status request
        data = client.recv(4096)
        client.close()
        assert b"Schlaeft" in data  # MOTD aus der synthetischen Status-Antwort
    finally:
        sp.stop_proxy(9991)


def test_server_status_view_maps_sleeping_and_colors():
    from types import SimpleNamespace as S

    from app.services.server_service import server_status_view

    assert server_status_view(S(status="running", sleep_enabled=True)) == {
        "status": "running",
        "color": "online",
    }
    # Sleep-Server im Zustand stopped -> "sleeping" / lila.
    assert server_status_view(S(status="stopped", sleep_enabled=True)) == {
        "status": "sleeping",
        "color": "sleeping",
    }
    assert server_status_view(S(status="stopped", sleep_enabled=False)) == {
        "status": "stopped",
        "color": "offline",
    }
    assert server_status_view(S(status="starting", sleep_enabled=False))["color"] == "pending"
    # Ein Absturz wird nicht als "sleeping" maskiert.
    assert server_status_view(S(status="crashed", sleep_enabled=True))["color"] == "offline"


def test_sleep_delay_split_and_roundtrip():
    from app.services.server_service import (
        sleep_delay_to_seconds,
        split_sleep_delay_seconds,
    )

    assert split_sleep_delay_seconds(300) == {"value": 5, "unit": "minutes"}
    assert split_sleep_delay_seconds(3600) == {"value": 1, "unit": "hours"}
    assert split_sleep_delay_seconds(86400) == {"value": 1, "unit": "days"}
    assert split_sleep_delay_seconds(90) == {"value": 90, "unit": "seconds"}
    assert split_sleep_delay_seconds(0) == {"value": 0, "unit": "seconds"}

    for seconds in (0, 45, 300, 3600, 5400, 86400, 172800):
        parts = split_sleep_delay_seconds(seconds)
        assert sleep_delay_to_seconds(parts["value"], parts["unit"]) == seconds

    assert sleep_delay_to_seconds(2, "days") == 172800
    assert sleep_delay_to_seconds(None, "days") is None


def test_reconcile_starts_and_stops_proxy(client, monkeypatch):
    # Reloadetes Modul aus sys.modules verwenden (conftest reloadet es je Test).
    import app.services.sleep_proxy_service as sp_live
    from app.db.session import SessionLocal
    from app.models.server import Server

    monkeypatch.setattr(sp_live, "_log", lambda *a, **k: None)

    pub = sp_live.find_free_port()
    internal = sp_live.find_free_port()
    while internal == pub:
        internal = sp_live.find_free_port()

    with SessionLocal() as db:
        srv = Server(
            name="sleepy-rc",
            slug="sleepy-rc",
            server_type="paper",
            mc_version="1.20.1",
            base_path="C:/tmp/sleepy-rc",
            port=pub,
            sleep_enabled=True,
            sleep_internal_port=internal,
        )
        db.add(srv)
        db.commit()
        sid = srv.id

    try:
        sp_live.reconcile_proxies()
        assert sid in sp_live._PROXIES  # Proxy laeuft fuer Sleep-Server

        with SessionLocal() as db:
            srv = db.get(Server, sid)
            srv.sleep_enabled = False
            db.add(srv)
            db.commit()

        sp_live.reconcile_proxies()
        assert sid not in sp_live._PROXIES  # nach Deaktivierung gestoppt
    finally:
        sp_live.stop_proxy(sid)


def test_forward_when_running(monkeypatch):
    monkeypatch.setattr(sp, "_log", lambda *a, **k: None)
    monkeypatch.setattr(sp.process_service, "is_running", lambda sid: True)

    backend = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    backend.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    backend.bind(("127.0.0.1", 0))
    backend.listen(1)
    internal_port = backend.getsockname()[1]
    received: list[bytes] = []

    def serve():
        conn, _ = backend.accept()
        received.append(conn.recv(4096))
        conn.sendall(b"HELLO")
        conn.close()

    threading.Thread(target=serve, daemon=True).start()

    public_port = sp.find_free_port()
    assert sp.start_proxy(9992, public_port, internal_port)
    try:
        client = socket.create_connection(("127.0.0.1", public_port), timeout=5)
        hs = _handshake(mp.NEXT_STATE_LOGIN, public_port)
        client.sendall(hs)
        time.sleep(0.4)
        response = client.recv(4096)
        client.close()
        assert received and received[0] == hs  # Handshake transparent weitergeleitet
        assert response == b"HELLO"
    finally:
        sp.stop_proxy(9992)
        backend.close()
