"""Byte-genaue Tests fuer das Kompass-Menue des Python-Hubs.

Deckt die neu eingefuehrte mehrfarbige Item-Beschriftung ab (custom_name + Lore als
Netzwerk-NBT-TextComponents) sowie die Tag/Nacht-/Wetter-Pakete. Ein kleiner NBT-Leser
fuer das namenlose Netzwerk-Format (seit 1.20.2) parst die erzeugten Bytes wieder ein,
damit Struktur- und Tag-Fehler sofort auffallen."""
import struct

from app.services import hub_service, mc_play as pl
from app.services.mc_protocol import read_varint


# --------------------------------------------------------------------------- #
# Mini-NBT-Leser fuer das namenlose Netzwerk-Component-Format (Root ohne Namen)
# --------------------------------------------------------------------------- #
def _read_component(data: bytes, i: int = 0):
    tag = data[i]
    return _read_payload(data, i + 1, tag)


def _read_payload(data: bytes, i: int, tag: int):
    if tag == 0x08:                                   # TAG_String
        n = struct.unpack_from(">H", data, i)[0]; i += 2
        return data[i:i + n].decode("utf-8"), i + n
    if tag == 0x01:                                   # TAG_Byte
        return struct.unpack_from(">b", data, i)[0], i + 1
    if tag == 0x0A:                                   # TAG_Compound (Felder: tag, name, payload)
        out: dict = {}
        while True:
            t = data[i]; i += 1
            if t == 0x00:                             # TAG_End
                break
            nlen = struct.unpack_from(">H", data, i)[0]; i += 2
            name = data[i:i + nlen].decode("utf-8"); i += nlen
            val, i = _read_payload(data, i, t)
            out[name] = val
        return out, i
    if tag == 0x09:                                   # TAG_List
        et = data[i]; i += 1
        n = struct.unpack_from(">i", data, i)[0]; i += 4
        items = []
        for _ in range(n):
            val, i = _read_payload(data, i, et)
            items.append(val)
        return items, i
    raise AssertionError(f"unerwarteter NBT-Tag {tag}")


# --------------------------------------------------------------------------- #
# TextComponent-Runs
# --------------------------------------------------------------------------- #
def test_plain_run_is_fast_tag_string():
    comp = pl._text_component_from_runs([("Hallo", None)])
    assert comp[0] == 0x08                              # TAG_String-Root (schneller Pfad)
    val, end = _read_component(comp)
    assert val == "Hallo" and end == len(comp)


def test_single_colored_run_is_compound():
    comp = pl._text_component_from_runs([("Grün", "green")])
    assert comp[0] == 0x0A
    val, end = _read_component(comp)
    assert val == {"text": "Grün", "color": "green"} and end == len(comp)


def test_multi_run_uses_extra_list():
    comp = pl._text_component_from_runs([("Name", "green"), (" (info)", "gray")])
    val, end = _read_component(comp)
    assert val["text"] == ""                            # Root-Text leer, Farben in extra
    assert val["extra"] == [
        {"text": "Name", "color": "green"},
        {"text": " (info)", "color": "gray"},
    ]
    assert end == len(comp)


def test_lore_run_forces_non_italic():
    comp = pl._text_component_from_runs([("Zeile", "gray")], italic=False)
    val, _ = _read_component(comp)
    assert val["italic"] == 0                            # Lore nicht kursiv


# --------------------------------------------------------------------------- #
# encode_slot mit Name + Lore
# --------------------------------------------------------------------------- #
def _parse_slot(slot: bytes):
    """(count, item_id, {component_id: raw_component_bytes}) aus einem kodierten Slot."""
    count, i = read_varint(slot, 0)
    item_id, i = read_varint(slot, i)
    n_add, i = read_varint(slot, i)
    n_rem, i = read_varint(slot, i)
    assert n_rem == 0
    comps: dict[int, tuple[int, int]] = {}
    for _ in range(n_add):
        cid, i = read_varint(slot, i)
        comps[cid] = i                                  # Startoffset der Komponente
        if cid == pl.DATA_COMPONENT_CUSTOM_NAME:
            _val, i = _read_component(slot, i)
        elif cid == pl.DATA_COMPONENT_LORE:
            cnt, i = read_varint(slot, i)
            for _ln in range(cnt):
                _val, i = _read_component(slot, i)
        else:
            raise AssertionError(f"unerwartete Komponente {cid}")
    return count, item_id, comps, i


def test_encode_slot_name_and_lore_components():
    slot = pl.encode_slot(
        pl.ITEM_COMPASS,
        name_runs=[("Server", "green"), (" (spigot 1.21.11)", "gray")],
        lore_runs=[[("host:25591", "gray")], [("Klick", "green")]],
    )
    count, item_id, comps, end = _parse_slot(slot)
    assert count == 1 and item_id == pl.ITEM_COMPASS
    assert set(comps) == {pl.DATA_COMPONENT_CUSTOM_NAME, pl.DATA_COMPONENT_LORE}
    assert end == len(slot)                             # exakt konsumiert, kein Rest

    # Name: extra-Liste mit zwei Farb-Runs
    name_val, _ = _read_component(slot, comps[pl.DATA_COMPONENT_CUSTOM_NAME])
    assert name_val["extra"][0]["color"] == "green"
    assert name_val["extra"][1]["color"] == "gray"

    # Lore: zwei Zeilen, beide nicht kursiv
    li = comps[pl.DATA_COMPONENT_LORE]
    cnt, li = read_varint(slot, li)
    assert cnt == 2
    line0, li = _read_component(slot, li)
    assert line0 == {"text": "host:25591", "color": "gray", "italic": 0}


def test_encode_slot_backwards_compatible_plain_name():
    slot = pl.encode_slot(pl.ITEM_COMPASS, custom_name="Server-Menü")
    count, item_id, comps, end = _parse_slot(slot)
    assert set(comps) == {pl.DATA_COMPONENT_CUSTOM_NAME}
    name_val, _ = _read_component(slot, comps[pl.DATA_COMPONENT_CUSTOM_NAME])
    assert name_val == "Server-Menü" and end == len(slot)


def test_encode_slot_empty_for_zero_count():
    assert pl.encode_slot(pl.ITEM_COMPASS, count=0) == pl.encode_slot_empty()


# --------------------------------------------------------------------------- #
# Legacy-&-Farbcodes -> Runs
# --------------------------------------------------------------------------- #
def test_legacy_runs_two_tone():
    runs = hub_service._legacy_runs("&aName &7(spigot 1.21.11)")
    assert runs == [("Name ", "green"), ("(spigot 1.21.11)", "gray")]


def test_legacy_runs_reset_and_leading_text():
    assert hub_service._legacy_runs("plain") == [("plain", None)]
    assert hub_service._legacy_runs("&cRot&rNormal") == [("Rot", "red"), ("Normal", None)]


def test_legacy_runs_ignores_format_codes():
    # &l (bold) wird ignoriert, Farbe bleibt gruen
    assert hub_service._legacy_runs("&a&lFett") == [("Fett", "green")]


# --------------------------------------------------------------------------- #
# _menu_slots: Ende-zu-Ende ueber die Hub-Serverliste
# --------------------------------------------------------------------------- #
def test_menu_slots_render_name_and_lore():
    servers = [{
        "display": "&aMein Server &7(spigot 1.21.11)",
        "host": "1.21.11-spigot.mc.example.de",
        "port": 25591,
        "material": "GRASS_BLOCK",
        "sleep": True,
    }]
    slots = hub_service.Hub._menu_slots(servers)
    assert len(slots) == 27 + 36
    count, item_id, comps, _ = _parse_slot(slots[0])
    assert count == 1
    assert set(comps) == {pl.DATA_COMPONENT_CUSTOM_NAME, pl.DATA_COMPONENT_LORE}
    # Sleep-Server -> 3 Lore-Zeilen (Adresse, Sleep-Hinweis, Klick)
    li = comps[pl.DATA_COMPONENT_LORE]
    cnt, _ = read_varint(slots[0], li)
    assert cnt == 3
    # Kompass-Spiegel in Slot 54 gesetzt, leere Container-Slots dazwischen
    assert slots[27 + 27] != pl.encode_slot_empty()
    assert slots[5] == pl.encode_slot_empty()


def test_menu_slots_non_sleep_has_two_lore_lines():
    servers = [{"display": "&aWach", "host": "h", "port": 1, "material": "", "sleep": False}]
    slots = hub_service.Hub._menu_slots(servers)
    _c, _id, comps, _ = _parse_slot(slots[0])
    cnt, _ = read_varint(slots[0], comps[pl.DATA_COMPONENT_LORE])
    assert cnt == 2                                     # nur Adresse + Klick, kein Sleep-Hinweis


# --------------------------------------------------------------------------- #
# Tag/Nacht + Wetter
# --------------------------------------------------------------------------- #
def _unwrap(packet: bytes) -> bytes:
    plen, off = read_varint(packet, 0)
    assert off + plen == len(packet)
    return packet[off:]


def test_build_update_time_freezes_noon():
    body = _unwrap(pl.build_update_time(6000, -6000))
    pid, off = read_varint(body, 0)
    assert pid == pl.PLAY_CB_UPDATE_TIME
    world_age, time_of_day = struct.unpack_from(">qq", body, off)
    assert world_age == 6000
    assert time_of_day == -6000                         # negativ = Sonne eingefroren


def test_build_game_event_rain_level():
    body = _unwrap(pl.build_game_event(pl.GAME_EVENT_RAIN_LEVEL, 0.0))
    pid, off = read_varint(body, 0)
    assert pid == pl.PLAY_CB_GAME_EVENT
    event = body[off]
    value = struct.unpack_from(">f", body, off + 1)[0]
    assert event == pl.GAME_EVENT_RAIN_LEVEL and value == 0.0
