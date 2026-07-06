"""Minimales Minecraft-Protokoll fuer den Sleep-/Wake-Proxy.

Nur die Handshake-/Status-/Login-Disconnect-Teile, die der Proxy braucht, um
einen eingehenden Verbindungsversuch zu erkennen und zu beantworten. Reine
Funktionen ohne Sockets -> vollstaendig unit-testbar.

Referenz: Server-Adresse -> Handshake (Packet 0x00):
    VarInt length, VarInt packetId(0x00), VarInt protocolVersion,
    String serverAddress, UnsignedShort port, VarInt nextState
nextState: 1 = Status (Serverliste-Ping), 2 = Login (Beitritt).
"""

from __future__ import annotations

import json
from dataclasses import dataclass


class IncompletePacket(Exception):
    """Es liegen noch nicht genug Bytes fuer ein vollstaendiges Feld vor."""


class ProtocolError(Exception):
    """Ungueltige/nicht unterstuetzte Daten (z.B. Legacy-Ping)."""


NEXT_STATE_STATUS = 1
NEXT_STATE_LOGIN = 2

_LEGACY_PING = 0xFE
_MAX_VARINT_BYTES = 5


def read_varint(data: bytes, offset: int = 0) -> tuple[int, int]:
    """VarInt ab ``offset`` lesen -> (wert, neuer_offset).

    Wirft ``IncompletePacket``, wenn die Bytes noch nicht komplett vorliegen.
    """
    result = 0
    for i in range(_MAX_VARINT_BYTES):
        pos = offset + i
        if pos >= len(data):
            raise IncompletePacket()
        byte = data[pos]
        result |= (byte & 0x7F) << (7 * i)
        if not byte & 0x80:
            return result, pos + 1
    raise ProtocolError("VarInt zu lang")


def encode_varint(value: int) -> bytes:
    if value < 0:
        value &= 0xFFFFFFFF
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            break
    return bytes(out)


def _read_string(data: bytes, offset: int) -> tuple[str, int]:
    length, offset = read_varint(data, offset)
    end = offset + length
    if end > len(data):
        raise IncompletePacket()
    return data[offset:end].decode("utf-8", errors="replace"), end


def encode_string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return encode_varint(len(raw)) + raw


def _wrap_packet(payload: bytes) -> bytes:
    """payload -> VarInt(len) + payload."""
    return encode_varint(len(payload)) + payload


@dataclass(frozen=True)
class Handshake:
    protocol_version: int
    server_address: str
    server_port: int
    next_state: int
    consumed: int  # Anzahl Bytes, die der Handshake im Puffer belegt hat


def parse_handshake(data: bytes) -> Handshake:
    """Ersten Handshake aus ``data`` parsen.

    Wirft ``IncompletePacket`` (mehr Bytes lesen) oder ``ProtocolError``
    (Legacy-Ping / kaputt).
    """
    if not data:
        raise IncompletePacket()
    if data[0] == _LEGACY_PING:
        raise ProtocolError("Legacy-Ping wird nicht unterstuetzt")

    length, offset = read_varint(data, 0)
    packet_end = offset + length
    if packet_end > len(data):
        raise IncompletePacket()

    packet_id, offset = read_varint(data, offset)
    if packet_id != 0x00:
        raise ProtocolError(f"Unerwartete Packet-ID {packet_id} im Handshake")

    protocol_version, offset = read_varint(data, offset)
    server_address, offset = _read_string(data, offset)
    if offset + 2 > len(data):
        raise IncompletePacket()
    server_port = int.from_bytes(data[offset:offset + 2], "big")
    offset += 2
    next_state, offset = read_varint(data, offset)

    return Handshake(
        protocol_version=protocol_version,
        server_address=server_address,
        server_port=server_port,
        next_state=next_state,
        consumed=packet_end,
    )


def build_status_json(
    *,
    motd: str,
    version_name: str,
    protocol_version: int,
    players_online: int = 0,
    players_max: int = 0,
) -> str:
    return json.dumps(
        {
            "version": {"name": version_name, "protocol": protocol_version},
            "players": {"max": players_max, "online": players_online, "sample": []},
            "description": {"text": motd},
        },
        ensure_ascii=False,
    )


def build_status_response_packet(status_json: str) -> bytes:
    """Clientbound Status Response (Packet 0x00) mit JSON-String."""
    return _wrap_packet(encode_varint(0x00) + encode_string(status_json))


def build_pong_packet(payload: int) -> bytes:
    """Clientbound Pong (Packet 0x01) - spiegelt den Ping-Payload (long)."""
    return _wrap_packet(encode_varint(0x01) + int(payload).to_bytes(8, "big", signed=True))


def build_login_disconnect_packet(message: str) -> bytes:
    """Clientbound Login-Disconnect (Packet 0x00) mit Chat-Component."""
    component = json.dumps({"text": message}, ensure_ascii=False)
    return _wrap_packet(encode_varint(0x00) + encode_string(component))


def try_read_ping_payload(data: bytes) -> int | None:
    """Optionalen Status-Ping (Packet 0x01 + long) lesen; None wenn unvollstaendig."""
    try:
        length, offset = read_varint(data, 0)
    except (IncompletePacket, ProtocolError):
        return None
    if offset + length > len(data):
        return None
    packet_id, offset = read_varint(data, offset)
    if packet_id != 0x01 or offset + 8 > len(data):
        return None
    return int.from_bytes(data[offset:offset + 8], "big", signed=True)
