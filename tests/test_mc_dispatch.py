"""Reine Protokoll-Bausteine des Dispatchers (Bauen/Parsen, ohne Sockets)."""

from app.services import mc_dispatch as d
from app.services.mc_protocol import encode_string, encode_varint, read_varint, _read_string


def test_login_success_strict_byte_only_on_767():
    p767 = d.build_login_success(b"\x11" * 16, "Steve", d.PROTOCOL_1_21)
    p768 = d.build_login_success(b"\x11" * 16, "Steve", d.PROTOCOL_1_21_2)
    # 767 hat genau ein zusaetzliches Byte (strictErrorHandling) mehr als 768.
    assert len(p767) == len(p768) + 1
    pid, fields, _ = d.try_read_packet(p767)
    assert pid == d.LOGIN_SUCCESS
    assert fields[:16] == b"\x11" * 16  # UUID durchgereicht


def test_transfer_roundtrip():
    packet = d.build_config_transfer("atm10-sky.mc.friedrich-dietrich.de", 25565)
    pid, fields, consumed = d.try_read_packet(packet)
    assert pid == d.CFG_CB_TRANSFER
    assert consumed == len(packet)
    host, off = _read_string(fields, 0)
    port, _ = read_varint(fields, off)
    assert host == "atm10-sky.mc.friedrich-dietrich.de"
    assert port == 25565


def test_neoforge_query_is_empty_map_on_channel():
    packet = d.build_neoforge_query()
    pid, fields, _ = d.try_read_packet(packet)
    assert pid == d.CFG_CB_CUSTOM
    channel, data = d.parse_custom_payload(fields)
    assert channel == d.NEOFORGE_REGISTER
    assert data == encode_varint(0)  # leere Map


def test_try_read_packet_partial_then_full():
    stream = d.build_neoforge_query() + d.build_config_transfer("x", 1)
    # Nur die Haelfte -> None.
    assert d.try_read_packet(stream[:2]) is None
    # Erstes Paket vollstaendig -> konsumiert genau eins.
    pid, _fields, consumed = d.try_read_packet(stream)
    assert pid == d.CFG_CB_CUSTOM
    rest = stream[consumed:]
    pid2, _f2, _c2 = d.try_read_packet(rest)
    assert pid2 == d.CFG_CB_TRANSFER


def test_parse_login_start():
    payload = encode_string("Notch") + b"\xAB" * 16
    username, uuid16 = d.parse_login_start(payload)
    assert username == "Notch"
    assert uuid16 == b"\xAB" * 16


def test_parse_brand_and_register():
    assert d.parse_brand(encode_string("neoforge")) == "neoforge"
    channels = d.parse_register_channels(b"neoforge:register\x00c:version\x00minecraft:brand")
    assert "neoforge:register" in channels and "c:version" in channels


def test_extract_mod_namespaces_from_manifest():
    # Synthetisches Manifest: ein paar Channel-Resource-Locations + Versionen + Flags.
    blob = (
        encode_string("create:network") + b"\x00\x01"
        + encode_string("mekanism:tile") + b"\x00\x00"
        + encode_string("jei:channel") + b"\x01"
        + encode_string("minecraft:brand")   # built-in -> ignoriert
        + encode_string("1.21.1")            # Version (kein ':') -> ignoriert
    )
    ns = d.extract_mod_namespaces(blob)
    assert {"create", "mekanism", "jei"} <= ns
    assert "minecraft" not in ns


def test_is_modded_client():
    assert d.is_modded_client("neoforge", []) is True
    assert d.is_modded_client("vanilla", ["neoforge:register"]) is True
    assert d.is_modded_client("vanilla", ["minecraft:brand"]) is False
    assert d.is_modded_client("", []) is False


def test_disconnect_is_wrapped_config_packet():
    packet = d.build_config_disconnect("Nutze atm10-sky.mc...")
    pid, fields, consumed = d.try_read_packet(packet)
    assert pid == d.CFG_CB_DISCONNECT
    assert consumed == len(packet)
    assert fields[0] == 0x0A  # NBT TAG_Compound
    assert b"text" in fields and "atm10-sky.mc...".encode() in fields


def test_disconnect_uses_modified_utf8_for_supplementary_chars():
    # Emoji (U+1F680): Standard-UTF-8 waere F0 9F 9A 80; NBT braucht Modified-UTF-8
    # (CESU-8 Surrogatpaar, beginnt mit 0xED) -> kein F0-Byte.
    packet = d.build_config_disconnect("hi \U0001F680")
    pid, fields, _ = d.try_read_packet(packet)
    assert pid == d.CFG_CB_DISCONNECT
    assert b"\xf0" not in fields
    assert b"\xed" in fields  # Surrogat-Lead-Byte


def test_try_read_packet_rejects_bogus_length():
    import pytest

    from app.services.mc_protocol import ProtocolError
    # Riesige Laenge (5-Byte-VarInt) -> ProtocolError statt endlosem Warten.
    with pytest.raises(ProtocolError):
        d.try_read_packet(encode_varint(2 ** 35 - 1) + b"\x00")
    # Nulllaenge -> keine Packet-ID -> ProtocolError.
    with pytest.raises(ProtocolError):
        d.try_read_packet(encode_varint(0))


def test_extract_mod_namespaces_strips_length_byte_pollution():
    # Channel der Laenge 45 -> Varint-Laengenbyte 0x2d ('-'); der Scanner darf den
    # nicht als Teil des Namespace nehmen (echter Bug aus dem ATM10-Mitschnitt).
    ch = "cyclopscore:advancement_rewards_obtain_packet"
    assert len(ch) == 45
    blob = encode_string(ch)
    assert blob[0:1] == b"-"  # das Laengenbyte ist druckbar
    ns = d.extract_mod_namespaces(blob)
    assert "cyclopscore" in ns
    assert "-cyclopscore" not in ns
