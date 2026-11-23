"""Tests fuer den Lobby-Welt-Bake (world_bake_service + mc_play-Chunk-Encoder).

Asset-unabhaengig: NBT/Chunk-Strukturen werden im Test synthetisch gebaut, damit die Suite
ohne die (grosse) echte Lobby-Welt gruen bleibt. Deckt ab: NBT-Leser (inkl. der frueheren
Name/Payload-Reihenfolge-Bug-Regression), Block-State-ID-Formel, Chunk-Packet-Roundtrip
(Bloecke + Framing-Alignment) und den Sicherheits-Fallback ohne /setworldspawn.
"""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

from app.services import mc_play as pl
from app.services import world_bake_service as wb


# --- Mini-NBT-Encoder (nur fuer die Tests) --------------------------------------
def _nbt_str(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _nbt_int(name: str, v: int) -> bytes:
    return bytes([3]) + _nbt_str(name) + struct.pack(">i", v)


def _nbt_compound(name: str, body: bytes) -> bytes:
    return bytes([10]) + _nbt_str(name) + body + b"\x00"


def _level_dat_bytes(spawn: tuple[int, int, int] | None) -> bytes:
    """Gzip-komprimiertes level.dat mit/ohne SpawnX/Y/Z (ALTES Format)."""
    data = b""
    if spawn is not None:
        sx, sy, sz = spawn
        data += _nbt_int("SpawnX", sx) + _nbt_int("SpawnY", sy) + _nbt_int("SpawnZ", sz)
    data += _nbt_int("thunderTime", 42)                       # irgendein weiteres Feld
    root = _nbt_compound("", _nbt_compound("Data", data))     # namenloser Root -> "Data"
    return gzip.compress(root)


def _nbt_int_array(name: str, vals) -> bytes:
    out = bytes([11]) + _nbt_str(name) + struct.pack(">i", len(vals))   # 11 = TAG_Int_Array
    for v in vals:
        out += struct.pack(">i", int(v))
    return out


def _level_dat_new_spawn(pos: tuple[int, int, int]) -> bytes:
    """NEUES Format (1.21.x/26.x): Data.spawn = {pos:[x,y,z], ...}."""
    spawn_body = _nbt_int_array("pos", list(pos)) + _nbt_int("yaw", 0)
    data = _nbt_compound("spawn", spawn_body) + _nbt_int("thunderTime", 42)
    root = _nbt_compound("", _nbt_compound("Data", data))
    return gzip.compress(root)


# --- NBT-Leser ------------------------------------------------------------------
def test_read_nbt_compound_field_order():
    """Regression: der COMPOUND-Leser muss ERST den Namen, DANN das Payload lesen."""
    raw = gzip.decompress(_level_dat_bytes((10, 64, -20)))
    nbt = wb.read_nbt(raw)
    assert "Data" in nbt
    d = nbt["Data"]
    assert d["SpawnX"] == 10 and d["SpawnY"] == 64 and d["SpawnZ"] == -20
    assert d["thunderTime"] == 42


def test_overworld_region_dir_old_and_new_layout(tmp_path: Path):
    """Overworld-Regionen finden: alt <world>/region, neu (26.x) dimensions/minecraft/overworld/region."""
    old = tmp_path / "old"
    (old / "region").mkdir(parents=True)
    (old / "region" / "r.0.0.mca").write_bytes(b"")
    assert wb.overworld_region_dir(old).name == "region"

    new = tmp_path / "new"
    nr = new / "dimensions" / "minecraft" / "overworld" / "region"
    nr.mkdir(parents=True)
    (nr / "r.0.0.mca").write_bytes(b"")
    assert wb.overworld_region_dir(new) == nr


def test_world_spawn_new_format(tmp_path: Path):
    """1.21.x/26.x speichert den Spawn als Data.spawn.pos (nicht mehr SpawnX/Y/Z)."""
    wd = tmp_path / "world"
    wd.mkdir()
    (wd / "level.dat").write_bytes(_level_dat_new_spawn((300, 50, -1171)))
    assert wb.world_spawn(wd) == (300, 50, -1171, True)


def test_world_spawn_explicit_and_missing(tmp_path: Path):
    wd = tmp_path / "world"
    wd.mkdir()
    (wd / "level.dat").write_bytes(_level_dat_bytes((100, 70, -8)))
    assert wb.world_spawn(wd) == (100, 70, -8, True)

    (wd / "level.dat").write_bytes(_level_dat_bytes(None))
    x, y, z, explicit = wb.world_spawn(wd)
    assert explicit is False and (x, y, z) == (0, 64, 0)


# --- Block-State-ID -------------------------------------------------------------
def test_block_state_id_formula():
    assert wb.block_state_id("air") == 0
    assert wb.block_state_id("minecraft:air") == 0
    # Ohne Properties -> minStateId (alle Props am ersten Wert).
    assert wb.block_state_id("oak_stairs") == 2874
    # Default-State (facing=north, half=bottom, shape=straight, waterlogged=false) = minId+11.
    # Erste Property am hoechstwertigen -> gegen minecraft-datas defaultState verifiziert.
    assert wb.block_state_id("oak_stairs", {
        "facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false",
    }) == 2885
    # unbekannter Block (in 1.21.1 nicht vorhanden) -> Stein-Fallback (1)
    assert wb.block_state_id("minecraft:definitely_not_a_block") == 1


# --- Palettenformen (vier gemessene Gestalten) ----------------------------------
def test_palette_entry_vier_formen():
    """Alle vier Gestalten, die ein Paletteneintrag am 18.11.2026 in einer Paper-26.3-Welt
    hatte - eine einzige Sektion trug drei davon nebeneinander.

    Die Zahlen sind gemessen und NICHT aus palette_entry abgeleitet. Grundgestein steht
    als erste Zeile, weil 79 weder 0 (Luft) noch 1 (Ersatzblock) ist: An dieser Zahl laesst
    sich ein gelesener Name nicht mit einem Fehlschlag verwechseln.
    """
    assert wb._palette_ids(["minecraft:bedrock"]) == [79]
    assert wb._palette_ids([{"": "minecraft:stone"}]) == [1]
    assert wb._palette_ids([{"Name": "minecraft:stone"}]) == [1]
    assert wb._palette_ids([{"id": "minecraft:oak_planks"}]) == [15]


def test_palette_liest_properties_in_beiden_schreibungen():
    """Ab 26.x heisst es ``properties`` KLEIN - und ohne das kaeme jeder Block im
    Vorgabezustand durch: jede Treppe falsch gedreht, jede Tuer falsch herum, leise genug,
    dass es niemand als Fehler meldet."""
    assert wb._palette_ids(
        [{"id": "minecraft:kelp", "properties": {"age": "21"}}]) == [12781]
    assert wb._palette_ids(
        [{"Name": "minecraft:kelp", "Properties": {"age": "21"}}]) == [12781]
    # 12760 ist der Vorgabezustand von kelp. Ohne diese Zeile waere die Probe auch gruen,
    # wenn die Properties ignoriert werden und jeder Zustand auf den Vorgabewert faellt.
    assert wb._palette_ids([{"id": "minecraft:kelp"}]) == [12760]


def test_palette_unbekannte_form_ist_laut(capsys):
    """Eine FUENFTE Form ist kein unbekannter Block, sondern ein unbekanntes Format.

    Geworfen wird nicht: ``bake_lobby_packets`` frisst jede Ausnahme und macht daraus
    ``None``, und ``None`` spricht der Hub als fehlenden ``/setworldspawn`` aus. Ein
    gelesener Wert mit einer gedruckten Zeile ueberlebt die Fangstelle, ein Wurf nicht.
    """
    wb._REPORTED_SHAPES.clear()   # Prozesszustand - sonst haengt die Probe an der
    assert wb._palette_ids([{"foo": 1}]) == [0]      # Reihenfolge der Dateien
    assert "unbekannter Form" in capsys.readouterr().out
    # Zweiter Ruf derselben Gestalt: STUMM. _palette_ids laeuft je Sektion, bei Radius 4
    # sind das 81 Chunks mal bis zu 24 Sektionen - eine Zeile je Eintrag waere vierstellig
    # und damit so unlesbar wie Schweigen.
    assert wb._palette_ids([{"foo": 2}]) == [0]
    assert capsys.readouterr().out == ""


def test_palette_leerer_string_zaehlt_auch_als_unbekannt(capsys):
    wb._REPORTED_SHAPES.clear()
    assert wb._palette_ids([""]) == [0]
    assert "unbekannter Form" in capsys.readouterr().out


def test_palette_id_als_zahl_faellt_nicht_darauf_herein(capsys):
    """Traegt ein kuenftiges Format unter ``id`` eine Zahl, darf sie nicht als Name
    durchgehen - ``Name`` daneben gewinnt dann."""
    wb._REPORTED_SHAPES.clear()
    assert wb._palette_ids([{"id": 7, "Name": "minecraft:bedrock"}]) == [79]
    assert capsys.readouterr().out == ""


# --- mc_play: Paletted Container ------------------------------------------------
def test_paletted_block_states_modes():
    single = pl._paletted_block_states([7] * 4096)
    assert single[0] == 0                                     # single-valued
    indirect = pl._paletted_block_states(([7] * 2048) + ([9] * 2048))
    assert indirect[0] == 4                                   # 2 Werte -> 4 Bit (Minimum)
    # >16 Palette-Werte -> >4 Bit
    many = pl._paletted_block_states([i % 20 for i in range(4096)])
    assert many[0] == 5                                       # ceil(log2(20)) = 5 Bit


# --- Chunk-Packet-Roundtrip -----------------------------------------------------
def _rvarint(b: bytes, p: int) -> tuple[int, int]:
    val = 0
    sh = 0
    while True:
        x = b[p]
        p += 1
        val |= (x & 0x7F) << sh
        if not (x & 0x80):
            break
        sh += 7
    return val, p


def _decode_sections(blob: bytes, count: int = 24) -> tuple[list, int]:
    p = 0
    secs = []
    for _ in range(count):
        block_count = struct.unpack_from(">h", blob, p)[0]
        p += 2
        bits = blob[p]
        p += 1
        if bits == 0:
            val, p = _rvarint(blob, p)
            _lc, p = _rvarint(blob, p)
            arr = [val] * 4096
        elif bits <= 8:
            plen, p = _rvarint(blob, p)
            pal = []
            for _ in range(plen):
                v, p = _rvarint(blob, p)
                pal.append(v)
            lc, p = _rvarint(blob, p)
            longs = struct.unpack_from(">%dQ" % lc, blob, p)
            p += 8 * lc
            per = 64 // bits
            mask = (1 << bits) - 1
            arr = []
            for lo in longs:
                for _ in range(per):
                    arr.append(pal[lo & mask])
                    lo >>= bits
                    if len(arr) >= 4096:
                        break
                if len(arr) >= 4096:
                    break
            arr = arr[:4096]
        else:
            lc, p = _rvarint(blob, p)
            longs = struct.unpack_from(">%dQ" % lc, blob, p)
            p += 8 * lc
            per = 64 // bits
            mask = (1 << bits) - 1
            arr = []
            for lo in longs:
                for _ in range(per):
                    arr.append(lo & mask)
                    lo >>= bits
                    if len(arr) >= 4096:
                        break
                if len(arr) >= 4096:
                    break
            arr = arr[:4096]
        # Biomes-Container (single-valued) ueberspringen
        p += 1
        _bv, p = _rvarint(blob, p)
        _blc, p = _rvarint(blob, p)
        secs.append((block_count, arr))
    return secs, p


def _synthetic_chunk(cx: int, cz: int, *, form: str = "Name") -> dict:
    """Anvil-Chunk-NBT mit zwei Sektionen: single-valued (Stein) + Multi-Block (Stein/Eiche).

    ``form`` waehlt die Palettengestalt: ``"Name"`` ist das Format bis 1.21.x, ``"id"`` das
    ab 26.x (klein geschrieben). Beide muessen dieselben Block-IDs ergeben.
    """
    if form == "id":
        stone = {"id": "minecraft:stone"}
        oak = {"id": "minecraft:oak_planks"}
    else:
        stone = {"Name": "minecraft:stone"}
        oak = {"Name": "minecraft:oak_planks"}
    # Section Y=-4: komplett Stein (nur Palette, keine data -> single-valued)
    sec_low = {"Y": -4, "block_states": {"palette": [stone]}}
    # Section Y=0: 2er-Palette, data = 4 Bit/Block. Erste 2048 = index0 (Stein), Rest index1 (Eiche).
    idx = ([0] * 2048) + ([1] * 2048)
    bits = 4
    per = 64 // bits
    longs = []
    cur = 0
    cnt = 0
    for v in idx:
        cur |= (v & 0xF) << (bits * cnt)
        cnt += 1
        if cnt == per:
            longs.append(struct.unpack(">q", struct.pack(">Q", cur))[0])  # signed wie NBT
            cur = 0
            cnt = 0
    if cnt:
        longs.append(struct.unpack(">q", struct.pack(">Q", cur))[0])
    sec_mid = {"Y": 0, "block_states": {"palette": [stone, oak], "data": longs}}
    return {"xPos": cx, "zPos": cz, "sections": [sec_low, sec_mid]}


def test_chunk_packet_roundtrip():
    ch = _synthetic_chunk(3, -5)
    packet = wb.build_chunk_column_packet(ch)

    p = 0
    _tot, p = _rvarint(packet, p)
    pid, p = _rvarint(packet, p)
    assert pid == pl.PLAY_CB_CHUNK_DATA
    x = struct.unpack_from(">i", packet, p)[0]
    p += 4
    z = struct.unpack_from(">i", packet, p)[0]
    p += 4
    assert (x, z) == (3, -5)
    # Heightmap-NBT (namenloser Root 0x0A ... 0x00) ueberspringen
    assert packet[p] == 0x0A
    p += 1
    while packet[p] != 0x00:
        tag = packet[p]
        p += 1
        nl = struct.unpack_from(">H", packet, p)[0]
        p += 2 + nl
        assert tag == 0x0C                                    # Long-Array
        n = struct.unpack_from(">i", packet, p)[0]
        p += 4 + 8 * n
    p += 1
    seclen, p = _rvarint(packet, p)
    blob = packet[p:p + seclen]
    secs, consumed = _decode_sections(blob)
    assert consumed == seclen                                 # Framing-Alignment exakt

    src = wb.chunk_section_blocks(ch)                          # {section_Y -> [4096]}
    for sy, arr in src.items():
        idx_net = sy - (-4)
        assert secs[idx_net][1] == arr                        # Bloecke exakt reproduziert
    # Multi-Block-Sektion: block_count = alle 4096 (Stein+Eiche, keine Luft)
    assert secs[0 - (-4)][0] == 4096


def test_palette_nackter_string_wirft_nicht():
    """Bis zum 23.11.2026 ein AttributeError - und Grundgestein liegt in JEDEM Chunk.

    Der Wurf fiel tief in ``_palette_ids`` und wurde erst in ``bake_lobby_packets`` zu
    ``None``; der Hub sprach das als "Kein /setworldspawn in der Lobby-Welt" aus. Der
    Betreiber setzte einen Spawn, der gesetzt war.
    """
    sek = wb.chunk_section_blocks({"xPos": 0, "zPos": 0, "sections": [
        {"Y": -4, "block_states": {"palette": ["minecraft:bedrock"]}}]})
    assert sek == {-4: [79] * 4096}


def test_chunk_section_blocks_liest_die_26x_palette():
    """Gemessen rot vor der Behebung: Y=-4 fehlte ganz, Y=0 war vollstaendig Luft.

    Geprueft wird NICHT "beide Gestalten sind gleich" - das waere auch gruen, wenn beide
    ganz aus Luft bestehen. Geprueft werden die gemessenen IDs selbst.
    """
    for form in ("Name", "id"):
        sek = wb.chunk_section_blocks(_synthetic_chunk(0, 0, form=form))
        assert sorted(sek) == [-4, 0], form
        assert set(sek[-4]) == {1}, form                  # Stein
        assert sorted(set(sek[0])) == [1, 15], form       # Stein + Eichenbretter


# --- Sicherheits-Fallback -------------------------------------------------------
def test_bake_lobby_packets_no_spawn_returns_none(tmp_path: Path):
    wd = tmp_path / "world"
    (wd / "region").mkdir(parents=True)
    (wd / "level.dat").write_bytes(_level_dat_bytes(None))    # KEIN Spawn -> Fallback
    assert wb.bake_lobby_packets(wd, radius=4) is None


def test_diagnose_lobby_bake_no_spawn(tmp_path: Path):
    wd = tmp_path / "world"
    (wd / "region").mkdir(parents=True)
    (wd / "level.dat").write_bytes(_level_dat_bytes(None))
    d = wb.diagnose_lobby_bake(wd, radius=2)
    assert d["level_dat"] is True
    assert d["spawn_explicit"] is False
    assert d["chunks_baked"] == 0
    assert d["error"] is None
    assert d["chunks_in_window"] == 25


def test_diagnose_lobby_bake_missing_level_dat(tmp_path: Path):
    d = wb.diagnose_lobby_bake(tmp_path / "nope", radius=1)
    assert d["level_dat"] is False and d["error"] is None


def test_diagnose_sample_names_liest_die_26x_palette(tmp_path: Path, monkeypatch):
    """Der zweite Weg zur selben Tat - und der heiklere.

    ``diagnose_lobby_bake`` soll den Rueckfall auf die Plattform ERKLAEREN. Mit einem
    eigenen Palettenleser meldete die Route bei einer 26.x-Welt selbst einen
    AttributeError statt der Blocknamen: Die Erklaerung brauchte eine Erklaerung.
    """
    wd = tmp_path / "world"
    (wd / "region").mkdir(parents=True)
    (wd / "level.dat").write_bytes(_level_dat_bytes((0, 64, 0)))
    (wd / "region" / "r.0.0.mca").write_bytes(b"platzhalter")
    monkeypatch.setattr(wb, "read_region",
                        lambda _p: {(0, 0): _synthetic_chunk(0, 0, form="id")})

    d = wb.diagnose_lobby_bake(wd, radius=0)

    assert d["error"] is None
    assert d["sample_names"] == ["oak_planks", "stone"]
    assert d["chunks_baked"] == 1


def test_bake_abbruch_nennt_seinen_grund(tmp_path: Path, monkeypatch, capsys):
    """``None`` allein ist eine falsche Ursache.

    Der Aufrufer spricht ``None`` als "Kein /setworldspawn in der Lobby-Welt (oder keine
    Chunks)" aus - bei JEDEM Bake-Fehler. Die gedruckte Zeile ist der einzige Ort, an dem
    die echte Ursache ueberhaupt steht.
    """
    wd = tmp_path / "world"
    (wd / "region").mkdir(parents=True)
    (wd / "level.dat").write_bytes(_level_dat_bytes((0, 64, 0)))

    def _wirf(*_a, **_k):
        raise RuntimeError("Region gesperrt")

    monkeypatch.setattr(wb, "bake_area", _wirf)
    assert wb.bake_lobby_packets(wd, radius=1) is None
    assert "Region gesperrt" in capsys.readouterr().out


def test_hub_world_status_not_running():
    from app.services import hub_lobby_service as hl

    st = hl.hub_world_status()
    assert st["running"] is False and st["baked"] is False


def test_world_spawn_raises_on_corrupt_level_dat(tmp_path: Path):
    """world_spawn ist bewusst NICHT defensiv (roher IO). Aufrufer (bake_lobby_packets, die
    Rebake-Route) MUESSEN es kapseln - z.B. wenn Paper level.dat gerade sperrt/halb schreibt."""
    import pytest

    wd = tmp_path / "world"
    wd.mkdir()
    (wd / "level.dat").write_bytes(b"not-a-gzip-file")   # halb geschrieben / gesperrt-Simulation
    with pytest.raises(Exception):
        wb.world_spawn(wd)
