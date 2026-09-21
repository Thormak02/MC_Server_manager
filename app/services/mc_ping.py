"""Server-List-Ping (SLP) als *Client* - auth-frei und ohne Nebenwirkungen.

Damit kann die Lobby vor einem Transfer pruefen, ob das Ziel wirklich
Verbindungen annimmt (nicht nur "Prozess laeuft") und ob noch Platz frei ist.
Ein Status-Ping ist kein Login: keine Mojang-Session, kein Playerdata, kein
Eintrag in der Spielerliste - deshalb ist er fuer eine Vorab-Pruefung sicher.

Referenz (Handshake -> next_state=1):
    C->S Handshake: VarInt(0x00), VarInt(protocol), String(host),
                    UnsignedShort(port), VarInt(1)
    C->S Status Request: VarInt(0x00)
    S->C Status Response: VarInt(0x00), String(JSON)
"""

from __future__ import annotations

import json
import socket
import struct
from typing import Any

from app.services.mc_protocol import (
    NEXT_STATE_STATUS,
    IncompletePacket,
    _read_string,
    _wrap_packet,
    encode_string,
    encode_varint,
    read_varint,
)

DEFAULT_TIMEOUT = 3.0

# Beliebige gueltige Protokollnummer: Den Status-Ping beantwortet jede
# Serverversion, auch wenn die Nummer nicht zur eigenen passt.
_PING_PROTOCOL = 767

# Schutz gegen ein defektes/boesartiges Gegenueber, das endlos Daten schickt.
_MAX_STATUS_BYTES = 4 * 1024 * 1024


def ping(
    host: str,
    port: int,
    *,
    timeout: float = DEFAULT_TIMEOUT,
    protocol: int = _PING_PROTOCOL,
) -> dict[str, Any] | None:
    """Status-JSON des Servers - oder ``None``, wenn er nicht (richtig) antwortet."""
    host = str(host or "").strip()
    try:
        port = int(port)
    except (TypeError, ValueError):
        return None
    if not host or not (0 < port < 65536):
        return None

    try:
        with socket.create_connection((host, port), timeout=timeout) as sock:
            sock.settimeout(timeout)
            handshake = (
                encode_varint(0x00)
                + encode_varint(int(protocol))
                + encode_string(host)
                + struct.pack(">H", port)
                + encode_varint(NEXT_STATE_STATUS)
            )
            sock.sendall(_wrap_packet(handshake))
            sock.sendall(_wrap_packet(encode_varint(0x00)))  # Status Request
            payload = _read_packet(sock)
    except OSError:
        return None
    if payload is None:
        return None

    try:
        packet_id, offset = read_varint(payload, 0)
        if packet_id != 0x00:
            return None
        raw, _ = _read_string(payload, offset)
        data = json.loads(raw)
    except (IncompletePacket, ValueError, UnicodeError):
        return None
    return data if isinstance(data, dict) else None


def _read_packet(sock: socket.socket) -> bytes | None:
    """Ein laengenpraefixiertes Paket vollstaendig lesen."""
    buffer = bytearray()
    while True:
        try:
            length, offset = read_varint(bytes(buffer), 0)
        except IncompletePacket:
            length = offset = None
        except Exception:
            return None
        if length is not None:
            if length < 0 or length > _MAX_STATUS_BYTES:
                return None
            if len(buffer) - offset >= length:
                return bytes(buffer[offset : offset + length])
        if len(buffer) > _MAX_STATUS_BYTES:
            return None
        chunk = sock.recv(8192)
        if not chunk:
            return None
        buffer.extend(chunk)


def player_counts(status: dict[str, Any] | None) -> tuple[int | None, int | None]:
    """(online, max) aus dem Status-JSON; ``None`` wo der Server nichts liefert."""
    players = (status or {}).get("players")
    if not isinstance(players, dict):
        return None, None

    def _int(value: Any) -> int | None:
        return value if isinstance(value, int) and not isinstance(value, bool) else None

    return _int(players.get("online")), _int(players.get("max"))


def is_online(status: dict[str, Any] | None) -> bool:
    """True, wenn der Ping eine verwertbare Antwort ergeben hat."""
    return isinstance(status, dict) and bool(status)
