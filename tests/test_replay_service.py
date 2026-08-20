"""Replay-Kern: .replay parsen, Config-Start finden, Ablaufplan bilden."""

from app.services import replay_service as rp
from app.services.mc_protocol import _wrap_packet, encode_string, encode_varint


def _pkt(packet_id, payload=b""):
    return _wrap_packet(encode_varint(packet_id) + payload)


def _replay_bytes(records):
    out = bytearray(b"MCRP\x01")
    for to_client, raw in records:
        out.append(0 if to_client else 1)
        out += len(raw).to_bytes(4, "big")
        out += raw
    return bytes(out)


def _custom(packet_id, channel, data=b""):
    return _pkt(packet_id, encode_string(channel) + data)


def test_load_and_config_start_and_steps():
    records = [
        (False, _pkt(0x00, b"\x01handshake")),          # C Handshake
        (False, _pkt(0x00, encode_string("Steve"))),    # C LoginStart
        (True, _pkt(0x02, b"\x00" * 16)),               # S LoginSuccess
        (False, _pkt(0x03)),                            # C LoginAck
        (False, _custom(0x02, "minecraft:brand", encode_string("neoforge"))),  # C brand
        (False, _pkt(0x00, b"\x00")),                   # C client_information
        (True, _custom(0x01, "minecraft:unregister")),  # S unregister
        (True, _custom(0x01, "minecraft:register")),    # S register
        (True, _custom(0x01, "neoforge:register")),     # S query
        (False, _custom(0x02, "neoforge:register", b"MANIFEST")),  # C manifest
        (True, _custom(0x01, "neoforge:network")),      # S negotiated network
    ]
    recs = rp.load_replay(_replay_bytes(records))
    assert len(recs) == 11
    assert recs[2].to_client is True and recs[2].packet_id == 0x02

    start = rp.find_config_start(recs)
    assert start == 4  # direkt nach dem LoginAck

    steps = rp.build_steps(recs, start)
    # Erwartet: warten(2 Client-Pakete) -> senden(3) -> warten(1) -> senden(1)
    assert [(len(s.send), s.wait) for s in steps] == [(0, 2), (3, 0), (0, 1), (1, 0)]
    # Die Sende-Charge enthaelt genau die drei S->C-Rohpakete.
    assert steps[1].send == [recs[6].raw, recs[7].raw, recs[8].raw]


def test_load_replay_rejects_bad_magic():
    import pytest

    with pytest.raises(ValueError):
        rp.load_replay(b"NOPE" + b"\x00" * 10)
