import pytest

from app.services import mc_protocol as mp


def _build_handshake(protocol, address, port, next_state):
    payload = (
        mp.encode_varint(0x00)
        + mp.encode_varint(protocol)
        + mp.encode_string(address)
        + int(port).to_bytes(2, "big")
        + mp.encode_varint(next_state)
    )
    return mp.encode_varint(len(payload)) + payload


@pytest.mark.parametrize("value", [0, 1, 127, 128, 255, 25565, 2097151, 2147483647])
def test_varint_roundtrip(value):
    encoded = mp.encode_varint(value)
    decoded, offset = mp.read_varint(encoded, 0)
    assert decoded == value
    assert offset == len(encoded)


def test_parse_handshake_login():
    data = _build_handshake(765, "mc.example.com", 25565, mp.NEXT_STATE_LOGIN)
    hs = mp.parse_handshake(data)
    assert hs.protocol_version == 765
    assert hs.server_address == "mc.example.com"
    assert hs.server_port == 25565
    assert hs.next_state == mp.NEXT_STATE_LOGIN
    assert hs.consumed == len(data)


def test_parse_handshake_status():
    data = _build_handshake(47, "host", 25577, mp.NEXT_STATE_STATUS)
    hs = mp.parse_handshake(data)
    assert hs.next_state == mp.NEXT_STATE_STATUS


def test_parse_handshake_incomplete_raises():
    data = _build_handshake(765, "host", 25565, mp.NEXT_STATE_LOGIN)
    with pytest.raises(mp.IncompletePacket):
        mp.parse_handshake(data[:-3])


def test_parse_handshake_legacy_ping_rejected():
    with pytest.raises(mp.ProtocolError):
        mp.parse_handshake(b"\xfe\x01")


def test_status_response_and_ping_roundtrip():
    status = mp.build_status_json(
        motd="Schlaeft", version_name="1.21", protocol_version=765,
        players_online=0, players_max=20,
    )
    packet = mp.build_status_response_packet(status)
    # Laenge + Packet-ID 0x00 + String -> parsebar
    length, offset = mp.read_varint(packet, 0)
    pid, offset = mp.read_varint(packet, offset)
    assert pid == 0x00

    ping = mp.encode_varint(9) + mp.encode_varint(0x01) + (123456789).to_bytes(8, "big", signed=True)
    assert mp.try_read_ping_payload(ping) == 123456789
    pong = mp.build_pong_packet(123456789)
    assert mp.try_read_ping_payload(pong) == 123456789


def test_login_disconnect_packet_contains_message():
    packet = mp.build_login_disconnect_packet("Server wird gestartet")
    assert b"Server wird gestartet" in packet
