"""Protokoll-Bausteine fuer den Modpack-Dispatcher (MC 1.21.x, Protokoll 767+).

Der Dispatcher nimmt eine Verbindung im **Offline-Mode** an, fuehrt Login + Config-
Phase, liest die vom Client verlangten (networked) Mods aus und schickt dann ein
**Transfer-Paket** zum passenden Backend. Hier liegen nur die **reinen** Byte-
Funktionen (Bauen/Parsen) - vollstaendig unit-testbar; das Socket-Handling ist im
``dispatcher_service``.

Fakten (Protokoll 767 = MC 1.21/1.21.1; ab 768/1.21.2 entfaellt strictErrorHandling):
  Login:  C->S LoginStart(0x00){username,uuid}; S->C LoginSuccess(0x02); C->S LoginAck(0x03)
  Config S->C: custom_payload(0x01), disconnect(0x02), finish(0x03), transfer(0x0B)
  Config C->S: client_information(0x00), custom_payload(0x02), finish(0x03)
  Transfer(0x0B): String host, VarInt port
  NeoForge: S->C custom_payload channel "neoforge:register" mit leerer Map (VarInt 0)
            -> C->S custom_payload "neoforge:register" mit Channel-Manifest.
"""

from __future__ import annotations

import re
import struct

from app.services.mc_protocol import (
    IncompletePacket,
    ProtocolError,
    _read_string,
    _wrap_packet,
    encode_string,
    encode_varint,
    read_varint,
)

# Obergrenze fuer eine Paketlaenge (DoS-Schutz): ein Config-/Login-Paket ist winzig,
# selbst ein grosses Mod-Manifest bleibt weit darunter.
_MAX_PACKET = 2 * 1024 * 1024

# Protokollversionen
PROTOCOL_1_21 = 767       # 1.21 / 1.21.1 -> LoginSuccess mit strictErrorHandling-Bool
PROTOCOL_1_21_2 = 768     # ab hier OHNE strictErrorHandling

# Login-Packet-IDs
LOGIN_START = 0x00
LOGIN_SUCCESS = 0x02
LOGIN_ACK = 0x03
# Config clientbound (S->C)
CFG_CB_CUSTOM = 0x01
CFG_CB_DISCONNECT = 0x02
CFG_CB_FINISH = 0x03
CFG_CB_TRANSFER = 0x0B
# Config serverbound (C->S)
CFG_SB_CLIENT_INFO = 0x00
CFG_SB_CUSTOM = 0x02
CFG_SB_FINISH = 0x03

NEOFORGE_REGISTER = "neoforge:register"
MINECRAFT_REGISTER = "minecraft:register"
MINECRAFT_BRAND = "minecraft:brand"

# Built-in/Loader-Namespaces, die keine Mods sind.
_BUILTIN_NAMESPACES = {"minecraft", "neoforge", "forge", "fml", "c", "cpw"}
# Resource-Location: namespace:path
_RESLOC_RE = re.compile(rb"[a-z0-9_.\-]+:[a-z0-9_./\-]+")
# Fuehrende Nicht-Buchstaben (Varint-Laengenbyte-Verschmutzung) entfernen -
# Mod-Namespaces beginnen immer mit a-z.
_LEADING_JUNK = re.compile(r"^[^a-z]+")


# --------------------------------------------------------------------------- #
# Bauen (clientbound)
# --------------------------------------------------------------------------- #
def build_login_success(uuid16: bytes, username: str, protocol: int) -> bytes:
    body = bytearray()
    body += encode_varint(LOGIN_SUCCESS)
    body += (uuid16 or b"")[:16].ljust(16, b"\x00")
    body += encode_string(username)
    body += encode_varint(0)  # properties: 0 (offline)
    if protocol < PROTOCOL_1_21_2:
        body += b"\x00"  # strictErrorHandling = false (nur 767)
    return _wrap_packet(bytes(body))


def build_config_custom_payload(channel: str, data: bytes) -> bytes:
    body = encode_varint(CFG_CB_CUSTOM) + encode_string(channel) + data
    return _wrap_packet(body)


def build_neoforge_query() -> bytes:
    """Leere ModdedNetworkQueryPayload (Map mit 0 Eintraegen) -> Client antwortet mit
    seinem Channel-Manifest."""
    return build_config_custom_payload(NEOFORGE_REGISTER, encode_varint(0))


def build_config_transfer(host: str, port: int) -> bytes:
    body = encode_varint(CFG_CB_TRANSFER) + encode_string(host) + encode_varint(int(port))
    return _wrap_packet(body)


def build_config_disconnect(message: str) -> bytes:
    """Disconnect mit Netzwerk-NBT Text-Component {"text": message}."""
    body = encode_varint(CFG_CB_DISCONNECT) + _nbt_text_component(message)
    return _wrap_packet(body)


def _modified_utf8(text: str) -> bytes:
    """Java 'Modified UTF-8' (das Format von DataInput.readUTF / NBT TAG_String).

    Unterschied zu Standard-UTF-8: U+0000 -> C0 80; Zeichen > U+FFFF (Emoji) werden
    als CESU-8 Surrogatpaar (2x 3 Byte) kodiert. Fuer ASCII/BMP identisch.
    """
    out = bytearray()
    for ch in text:
        cp = ord(ch)
        if cp == 0x0000:
            out += b"\xc0\x80"
        elif cp <= 0x7F:
            out.append(cp)
        elif cp <= 0x7FF:
            out.append(0xC0 | (cp >> 6))
            out.append(0x80 | (cp & 0x3F))
        elif cp <= 0xFFFF:
            out.append(0xE0 | (cp >> 12))
            out.append(0x80 | ((cp >> 6) & 0x3F))
            out.append(0x80 | (cp & 0x3F))
        else:
            cp -= 0x10000
            for surrogate in (0xD800 | (cp >> 10), 0xDC00 | (cp & 0x3FF)):
                out.append(0xE0 | (surrogate >> 12))
                out.append(0x80 | ((surrogate >> 6) & 0x3F))
                out.append(0x80 | (surrogate & 0x3F))
    return bytes(out)


def _nbt_text_component(text: str) -> bytes:
    """Minimale Netzwerk-NBT (ab 1.20.3, Root ohne Namen): {"text": text}.

    TAG_String ist Modified-UTF-8-laengenpraefigiert (NICHT Protokoll-String).
    """
    raw = _modified_utf8(text)[:65535]
    out = bytearray()
    out.append(0x0A)                       # TAG_Compound (Root, kein Name)
    out.append(0x08)                       # TAG_String
    out += struct.pack(">H", 4) + b"text"  # Feldname
    out += struct.pack(">H", len(raw)) + raw
    out.append(0x00)                       # TAG_End
    return bytes(out)


# --------------------------------------------------------------------------- #
# Lesen (serverbound)
# --------------------------------------------------------------------------- #
def try_read_packet(buf: bytes) -> tuple[int, bytes, int] | None:
    """Ein unkomprimiertes Paket aus ``buf`` lesen.

    -> (packet_id, fields, consumed_bytes) oder ``None`` wenn noch unvollstaendig.
    """
    try:
        length, off = read_varint(buf, 0)
    except IncompletePacket:
        return None
    # length<=0 hat keine Packet-ID; zu grosse Laenge = DoS-Versuch -> hart ablehnen.
    if length <= 0 or length > _MAX_PACKET:
        raise ProtocolError(f"ungueltige Paketlaenge {length}")
    total = off + length
    if len(buf) < total:
        return None
    body = buf[off:total]
    try:
        packet_id, off2 = read_varint(body, 0)
    except IncompletePacket:
        raise ProtocolError("kaputtes Paket ohne ID")
    return packet_id, body[off2:], total


def parse_login_start(payload: bytes) -> tuple[str, bytes]:
    """LoginStart -> (username, uuid16). uuid16 = 16 Nullbytes, falls nicht vorhanden."""
    username, off = _read_string(payload, 0)
    uuid16 = payload[off:off + 16]
    if len(uuid16) < 16:
        uuid16 = uuid16.ljust(16, b"\x00")
    return username, uuid16


def parse_custom_payload(payload: bytes) -> tuple[str, bytes]:
    """Config custom_payload -> (channel, data)."""
    channel, off = _read_string(payload, 0)
    return channel, payload[off:]


def parse_brand(data: bytes) -> str:
    """minecraft:brand-Payload -> Brand-String ("vanilla"/"neoforge"/...)."""
    try:
        brand, _ = _read_string(data, 0)
        return brand
    except (IncompletePacket, Exception):  # noqa: BLE001
        return data.decode("utf-8", "ignore").strip()


def parse_register_channels(data: bytes) -> list[str]:
    """minecraft:register-Payload -> Liste der Channel-IDs (NUL-getrennt)."""
    return [c.decode("utf-8", "ignore") for c in data.split(b"\x00") if c]


def extract_mod_namespaces(payload: bytes) -> set[str]:
    """Mod-IDs aus einem NeoForge-Manifest robust extrahieren.

    Statt die exakte (nicht offiziell dokumentierte) Map-Kodierung nachzubauen,
    scannen wir alle Resource-Location-Strings (``namespace:path``) und nehmen deren
    Namespace - das sind faktisch die Mod-IDs der networked Mods (create:*, jei:* ...).
    Robust gegen Kodierungsdetails; Built-in-Namespaces werden ausgefiltert.
    """
    out: set[str] = set()
    for match in _RESLOC_RE.findall(payload):
        ns = match.split(b":", 1)[0].decode("ascii", "ignore")
        # Direkt vor der Resource-Location steht ihr Varint-Laengenbyte; ist das
        # druckbar (z.B. '-' bei Laenge 45), faengt die Regex es mit ein -> strippen.
        ns = _LEADING_JUNK.sub("", ns)
        if ns and ns not in _BUILTIN_NAMESPACES:
            out.add(ns)
    return out


def is_modded_client(brand: str, register_channels: list[str]) -> bool:
    """Grobe Loader-Erkennung aus Brand + angekuendigten Channels."""
    b = (brand or "").lower()
    if b and b != "vanilla":
        return True
    for ch in register_channels:
        low = ch.lower()
        if low.startswith("neoforge:") or low.startswith("fml:") or low.startswith("forge:"):
            return True
    return False
