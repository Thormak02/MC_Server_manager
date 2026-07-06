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
