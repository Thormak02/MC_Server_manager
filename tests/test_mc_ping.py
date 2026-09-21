"""Status-Ping als Client (auth-freie Vorab-Pruefung fuer den Lobby-Wechsel)."""
import json
import socket
import struct
import threading

import pytest

from app.services import mc_ping
from app.services.mc_protocol import (
    IncompletePacket,
    _wrap_packet,
    encode_string,
    encode_varint,
    read_varint,
)


def _drain_requests(conn, count=2) -> bytes:
    """Handshake UND Status-Request vollstaendig abholen.

    Unverzichtbar: laesst der Fakeserver Bytes im Empfangspuffer liegen und schliesst
    dann, schickt Windows ein RST und verwirft die bereits gesendete Antwort -> flaky.
    """
    buffer = bytearray()
    done = 0
    while done < count:
        offset = 0
        while done < count:
            try:
                length, body = read_varint(bytes(buffer), offset)
            except IncompletePacket:
                break
            if len(buffer) - body < length:
                break
            offset = body + length
            done += 1
        if done >= count:
            break
        chunk = conn.recv(4096)
        if not chunk:
            break
        buffer.extend(chunk)
    return bytes(buffer)


def _serve(responder, *, host="127.0.0.1"):
    """Ein Ein-Verbindungs-Fakeserver; gibt (port, thread) zurueck."""
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.bind((host, 0))
    listener.listen(1)
    port = listener.getsockname()[1]

    def run():
        try:
            conn, _ = listener.accept()
            with conn:
                responder(conn)
        except Exception:  # noqa: BLE001 - ein Fakeserver-Fehler darf den Lauf nicht
            pass           # mit einer unbehandelten Thread-Ausnahme verrauschen
        finally:
            try:
                listener.close()
            except OSError:
                pass

    thread = threading.Thread(target=run, daemon=True)
    thread.start()
    return port, thread


def _status_responder(payload: dict, *, chunked: bool = False):
    def respond(conn):
        _drain_requests(conn)  # Handshake UND Status-Request komplett abholen
        packet = _wrap_packet(encode_varint(0x00) + encode_string(json.dumps(payload)))
        if chunked:
            for i in range(0, len(packet), 7):  # absichtlich zerstueckelt
                conn.sendall(packet[i : i + 7])
        else:
            conn.sendall(packet)
    return respond


def test_ping_returns_status_json():
    payload = {"version": {"name": "1.21.1", "protocol": 767},
               "players": {"online": 3, "max": 20}}
    port, _ = _serve(_status_responder(payload))
    assert mc_ping.ping("127.0.0.1", port) == payload


def test_ping_reassembles_split_packets():
    """TCP liefert Bruchstuecke - der Leser muss bis zum vollen Paket sammeln."""
    payload = {"players": {"online": 1, "max": 5}, "description": "x" * 400}
    port, _ = _serve(_status_responder(payload, chunked=True))
    assert mc_ping.ping("127.0.0.1", port) == payload


def test_ping_sends_valid_handshake():
    seen = {}

    def respond(conn):
        data = _drain_requests(conn)
        length, off = read_varint(data, 0)
        packet_id, off = read_varint(data, off)
        protocol, off = read_varint(data, off)
        host_len, off = read_varint(data, off)
        host = data[off : off + host_len].decode()
        off += host_len
        (port_field,) = struct.unpack(">H", data[off : off + 2])
        off += 2
        next_state, _ = read_varint(data, off)
        seen.update(packet_id=packet_id, protocol=protocol, host=host,
                    port=port_field, next_state=next_state)
        conn.sendall(_wrap_packet(encode_varint(0x00) + encode_string("{}")))

    port, thread = _serve(respond)
    mc_ping.ping("127.0.0.1", port)
    thread.join(timeout=5)
    assert seen["packet_id"] == 0x00
    assert seen["host"] == "127.0.0.1"
    assert seen["port"] == port
    assert seen["next_state"] == 1  # Status, NICHT Login -> kein Join, keine Auth


def test_ping_unreachable_port_is_none():
    assert mc_ping.ping("127.0.0.1", 1) is None


def test_ping_rejects_invalid_targets():
    assert mc_ping.ping("", 25565) is None
    assert mc_ping.ping("127.0.0.1", 0) is None
    assert mc_ping.ping("127.0.0.1", 70000) is None
    assert mc_ping.ping("127.0.0.1", "nope") is None


def test_ping_on_silent_close_is_none():
    port, _ = _serve(lambda conn: conn.close())
    assert mc_ping.ping("127.0.0.1", port) is None


def test_ping_on_garbage_is_none():
    def respond(conn):
        _drain_requests(conn)
        conn.sendall(_wrap_packet(encode_varint(0x00) + encode_string("nicht json")))

    port, _ = _serve(respond)
    assert mc_ping.ping("127.0.0.1", port) is None


def test_ping_on_wrong_packet_id_is_none():
    def respond(conn):
        _drain_requests(conn)
        conn.sendall(_wrap_packet(encode_varint(0x42) + encode_string("{}")))

    port, _ = _serve(respond)
    assert mc_ping.ping("127.0.0.1", port) is None


def test_ping_oversized_length_is_rejected():
    """Ein defektes/boesartiges Gegenueber darf den Leser nicht endlos fuettern."""
    def respond(conn):
        _drain_requests(conn)
        conn.sendall(encode_varint(64 * 1024 * 1024) + b"x" * 64)

    port, _ = _serve(respond)
    assert mc_ping.ping("127.0.0.1", port) is None


def test_ping_timeout_is_none():
    port, _ = _serve(lambda conn: threading.Event().wait(3))
    assert mc_ping.ping("127.0.0.1", port, timeout=0.3) is None


@pytest.mark.parametrize(
    "status,expected",
    [
        ({"players": {"online": 3, "max": 20}}, (3, 20)),
        ({"players": {}}, (None, None)),
        ({}, (None, None)),
        (None, (None, None)),
        ({"players": {"online": True, "max": 20}}, (None, 20)),   # bool ist keine Zahl
        ({"players": {"online": "3", "max": "20"}}, (None, None)),
        ({"players": "kaputt"}, (None, None)),
    ],
)
def test_player_counts(status, expected):
    assert mc_ping.player_counts(status) == expected


def test_is_online():
    assert mc_ping.is_online({"players": {}}) is True
    assert mc_ping.is_online({}) is False
    assert mc_ping.is_online(None) is False
