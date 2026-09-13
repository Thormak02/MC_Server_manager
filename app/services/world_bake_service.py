"""Lobby-Welt fuer den Python-Hub 'backen': eine echte Minecraft-Welt (Anvil-Region-Dateien)
lesen und in 1.21.1-Chunk-Daten uebersetzen, damit der Hub sie modded Clients (1.21.1) statt der
Plattform servieren kann. Der Vanilla-Backend-Server (Paper 26.x) nutzt DIESELBE Welt und upgradet
sie automatisch -> eine gemeinsame Lobby-Welt auf beiden Seiten (Option B, kein 26->1.21-Convert).

Kern: Anvil speichert Block-Zustaende als NAMEN (``minecraft:oak_stairs`` + Properties), NICHT als
versions-spezifische Zahlen-IDs. Wir lesen die Namen und mappen sie ueber die gebuendelte
1.21.1-Block-State-Registry (``assets/block_states_1_21_1.json``) auf die 1.21.1-Zahlen-IDs. So ist
die Welt-Version egal (1.21.11, 26.x ...); Bloecke, die es in 1.21.1 nicht gibt, werden ersetzt.

Hier: NBT-Leser + Anvil-Region-Parser + Block-State-ID-Aufloesung. Das 1.21.1-Chunk-Encoding und die
Hub-Anbindung bauen darauf auf (mc_play / hub_service).
"""

from __future__ import annotations

import gzip
import json
import struct
import zlib
from collections import defaultdict
from pathlib import Path

# Overworld-Geometrie (1.21.1): min_y=-64 -> unterste Section-Y=-4; 384 hoch -> 24 Sections.
_MIN_SECTION_Y = -4
_SECTION_COUNT = 24
_MIN_WORLD_Y = _MIN_SECTION_Y * 16     # -64

# --- Block-State-Registry (Name+Properties -> 1.21.1 Block-State-ID) ------------
_REGISTRY_ASSET = Path(__file__).resolve().parents[1] / "assets" / "block_states_1_21_1.json"
_REGISTRY: dict | None = None
_AIR_ID = 0                      # minecraft:air = 0 in 1.21.1
_FALLBACK_ID = 1                 # unbekannter Block (in 1.21.1 nicht vorhanden) -> stone


def _registry() -> dict:
    global _REGISTRY
    if _REGISTRY is None:
        try:
            _REGISTRY = json.loads(_REGISTRY_ASSET.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            _REGISTRY = {}
    return _REGISTRY


def block_state_id(name: str, props: dict | None = None) -> int:
    """Block-Name (mit/ohne ``minecraft:``) + Properties -> 1.21.1 Block-State-ID.

    ID = minStateId + Summe( value_index_i * Produkt(num_values der NACHFOLGENDEN Properties) ).
    Erste Property ist am hoechstwertigen, letzte variiert am schnellsten (MC-Konvention;
    gegen minecraft-datas defaultState verifiziert)."""
    key = (name or "air").split(":", 1)[-1]
    entry = _registry().get(key)
    if entry is None:
        return _AIR_ID if key == "air" else _FALLBACK_ID
    min_id, prop_defs = entry
    if not prop_defs:
        return int(min_id)
    props = props or {}
    offset = 0
    weight = 1
    for pname, pvals in reversed(prop_defs):
        val = str(props.get(pname, pvals[0]))
        try:
            vi = pvals.index(val)
        except ValueError:
            vi = 0
        offset += vi * weight
        weight *= len(pvals)
    return int(min_id) + offset


# --- NBT-Leser (binaeres NBT -> Python) -----------------------------------------
_TAG_END, _TAG_BYTE, _TAG_SHORT, _TAG_INT, _TAG_LONG = 0, 1, 2, 3, 4
_TAG_FLOAT, _TAG_DOUBLE, _TAG_BYTE_ARRAY, _TAG_STRING = 5, 6, 7, 8
_TAG_LIST, _TAG_COMPOUND, _TAG_INT_ARRAY, _TAG_LONG_ARRAY = 9, 10, 11, 12


class _NBT:
    __slots__ = ("d", "i")

    def __init__(self, data: bytes) -> None:
        self.d = data
        self.i = 0

    def _take(self, n: int) -> bytes:
        v = self.d[self.i:self.i + n]
        self.i += n
        return v

    def _u16(self) -> int:
        return struct.unpack_from(">H", self.d, self._adv(2))[0]

    def _i32(self) -> int:
        return struct.unpack_from(">i", self.d, self._adv(4))[0]

    def _adv(self, n: int) -> int:
        p = self.i
        self.i += n
        return p

    def _string(self) -> str:
        n = self._u16()
        return self._take(n).decode("utf-8", "replace")

    def _payload(self, tag: int):
        d = self.d
        if tag == _TAG_BYTE:
            return struct.unpack_from(">b", d, self._adv(1))[0]
        if tag == _TAG_SHORT:
            return struct.unpack_from(">h", d, self._adv(2))[0]
        if tag == _TAG_INT:
            return self._i32()
        if tag == _TAG_LONG:
            return struct.unpack_from(">q", d, self._adv(8))[0]
        if tag == _TAG_FLOAT:
            return struct.unpack_from(">f", d, self._adv(4))[0]
        if tag == _TAG_DOUBLE:
            return struct.unpack_from(">d", d, self._adv(8))[0]
        if tag == _TAG_BYTE_ARRAY:
            n = self._i32()
            return self._take(n)
        if tag == _TAG_STRING:
            return self._string()
        if tag == _TAG_LIST:
            it = struct.unpack_from(">b", d, self._adv(1))[0]
            n = self._i32()
            return [self._payload(it) for _ in range(n)]
        if tag == _TAG_COMPOUND:
            out: dict = {}
            while True:
                t = struct.unpack_from(">b", d, self._adv(1))[0]
                if t == _TAG_END:
                    break
                # WICHTIG: erst den Namen lesen, DANN das Payload (Wire: tag, name, payload).
                # `out[self._string()] = self._payload(t)` wuerde das Payload ZUERST auswerten
                # (Python wertet die RHS vor dem Subscript-Key aus) -> Fehl-Ausrichtung.
                name = self._string()
                out[name] = self._payload(t)
            return out
        if tag == _TAG_INT_ARRAY:
            n = self._i32()
            return list(struct.unpack_from(f">{n}i", d, self._adv(4 * n)))
        if tag == _TAG_LONG_ARRAY:
            n = self._i32()
            return list(struct.unpack_from(f">{n}q", d, self._adv(8 * n)))
        raise ValueError(f"NBT: unbekannter Tag {tag}")

    def read_root(self):
        tag = struct.unpack_from(">b", self.d, self._adv(1))[0]
        if tag != _TAG_COMPOUND:
            raise ValueError("NBT-Root ist kein Compound")
        self._string()   # Root-Name (meist leer)
        return self._payload(_TAG_COMPOUND)


def read_nbt(data: bytes) -> dict:
    """Rohes (bereits dekomprimiertes) NBT -> dict."""
    return _NBT(data).read_root()


# --- Anvil-Region-Dateien (.mca) -> Chunk-NBT -----------------------------------
def read_region(mca_path: str | Path) -> dict:
    """Alle Chunks einer .mca-Region lesen. Rueckgabe {(chunk_x, chunk_z): chunk_nbt}."""
    data = Path(mca_path).read_bytes()
    if len(data) < 8192:
        return {}
    chunks: dict = {}
    for i in range(1024):
        loc = data[i * 4:i * 4 + 4]
        off = (loc[0] << 16) | (loc[1] << 8) | loc[2]
        cnt = loc[3]
        if off == 0 or cnt == 0:
            continue
        start = off * 4096
        if start + 5 > len(data):
            continue
        length = int.from_bytes(data[start:start + 4], "big")
        comp = data[start + 4]
        payload = data[start + 5:start + 4 + length]
        try:
            if comp == 1:
                raw = gzip.decompress(payload)
            elif comp == 2:
                raw = zlib.decompress(payload)
            elif comp == 3:
                raw = payload
            else:
                continue
            nbt = read_nbt(raw)
        except Exception:  # noqa: BLE001 - einzelner kaputter Chunk soll den Bake nicht kippen
            continue
        cx = nbt.get("xPos")
        cz = nbt.get("zPos")
        if cx is not None and cz is not None:
            chunks[(int(cx), int(cz))] = nbt
    return chunks


def _palette_ids(palette: list) -> list[int]:
    return [block_state_id(str(p.get("Name", "minecraft:air")), p.get("Properties"))
            for p in palette]


def chunk_section_blocks(chunk_nbt: dict) -> dict[int, list[int]]:
    """Block-State-IDs (1.21.1) je Sektion. Rueckgabe {section_Y: [4096 IDs in YZX-Reihenfolge]}.
    Leere/Luft-Sektionen werden weggelassen. Einzel-Block-Sektionen (palette=1) haben keine
    'data' und werden voll aufgefuellt."""
    out: dict[int, list[int]] = {}
    for sec in chunk_nbt.get("sections", []):
        y = sec.get("Y")
        bs = sec.get("block_states")
        if y is None or not isinstance(bs, dict):
            continue
        palette = bs.get("palette") or []
        if not palette:
            continue
        ids = _palette_ids(palette)
        data = bs.get("data")
        if not data:                      # nur ein Block-Typ in der ganzen Sektion
            if ids and ids[0] != _AIR_ID:
                out[int(y)] = [ids[0]] * 4096
            continue
        bits = max(4, (len(palette) - 1).bit_length())
        per_long = 64 // bits
        mask = (1 << bits) - 1
        blocks: list[int] = []
        for long_val in data:
            v = long_val & 0xFFFFFFFFFFFFFFFF   # signiert -> unsigniert fuer Bit-Extraktion
            for _ in range(per_long):
                pi = v & mask
                blocks.append(ids[pi] if pi < len(ids) else _AIR_ID)
                v >>= bits
                if len(blocks) >= 4096:
                    break
            if len(blocks) >= 4096:
                break
        out[int(y)] = blocks[:4096] + [_AIR_ID] * (4096 - len(blocks))
    return out


# --- Welt-Chunk -> 1.21.1-Netzwerk-Packet --------------------------------------
def _column_heightmap(sections_by_index: dict[int, list[int]]) -> list[int]:
    """256 Spalten-Hoehen (Index z*16+x) = hoechster Nicht-Luft-Block +1, relativ zu min_y.
    0 = leere Spalte. Fuer die MOTION_BLOCKING/WORLD_SURFACE-Heightmaps im Chunk-Packet."""
    heights = [0] * 256
    for z in range(16):
        for x in range(16):
            h = 0
            for idx in range(_SECTION_COUNT - 1, -1, -1):
                arr = sections_by_index.get(idx)
                if arr is None:
                    continue
                base_y = (_MIN_SECTION_Y + idx) * 16
                zx = z * 16 + x
                found = False
                for ly in range(15, -1, -1):
                    if arr[ly * 256 + zx] != _AIR_ID:
                        h = base_y + ly - _MIN_WORLD_Y + 1
                        found = True
                        break
                if found:
                    break
            heights[z * 16 + x] = h
    return heights


def build_chunk_column_packet(chunk_nbt: dict, *, biome_id: int = 0) -> bytes:
    """Ein Chunk-NBT (aus der Welt) -> fertiges 1.21.1-Chunk-Data-Packet (via mc_play)."""
    from app.services import mc_play as pl

    secs = chunk_section_blocks(chunk_nbt)                 # {section_Y -> [4096]}
    sections_by_index: dict[int, list[int]] = {}
    for sy, arr in secs.items():
        idx = sy - _MIN_SECTION_Y
        if 0 <= idx < _SECTION_COUNT:
            sections_by_index[idx] = arr
    heights = _column_heightmap(sections_by_index)
    cx = int(chunk_nbt.get("xPos", 0))
    cz = int(chunk_nbt.get("zPos", 0))
    return pl.build_world_chunk(
        cx, cz, sections_by_index, heights,
        section_count=_SECTION_COUNT, biome_id=biome_id,
    )


def world_spawn(world_dir: str | Path) -> tuple[int, int, int, bool]:
    """(x, y, z, explizit?) aus level.dat.

    Zwei Formate:
      - NEU (1.21.x / 26.x): ``Data.spawn = {pos:[x,y,z], pitch, yaw, dimension}``
      - ALT: ``Data.SpawnX/SpawnY/SpawnZ`` (Integers)
    Fehlt beides -> Default (0,64,0), explizit=False."""
    lvl = read_nbt(gzip.decompress((Path(world_dir) / "level.dat").read_bytes()))
    d = lvl.get("Data", lvl)

    # Neues Format zuerst: Data.spawn.pos (Int-Array/Liste [x,y,z]).
    spawn = d.get("spawn")
    if isinstance(spawn, dict):
        pos = spawn.get("pos")
        if isinstance(pos, (list, tuple)) and len(pos) == 3:
            return (int(pos[0]), int(pos[1]), int(pos[2]), True)

    # Altes Format.
    sx, sy, sz = d.get("SpawnX"), d.get("SpawnY"), d.get("SpawnZ")
    explicit = sx is not None and sz is not None
    return (int(sx) if sx is not None else 0,
            int(sy) if sy is not None else 64,
            int(sz) if sz is not None else 0,
            explicit)


def bake_area(world_dir: str | Path, center_cx: int, center_cz: int, radius: int,
              *, biome_id: int = 0) -> list[tuple[int, int, bytes]]:
    """Festen quadratischen Chunk-Bereich um (center_cx, center_cz) backen.

    Rueckgabe [(chunk_x, chunk_z, packet_bytes), ...] fuer alle EXISTIERENDEN Chunks im
    (2*radius+1)^2-Fenster. Fehlende Chunks werden ausgelassen (Client sieht dort Void).
    Regionen werden je Datei genau einmal gelesen."""
    region_dir = Path(world_dir) / "region"
    by_region: dict[tuple[int, int], list[tuple[int, int]]] = defaultdict(list)
    for cx in range(center_cx - radius, center_cx + radius + 1):
        for cz in range(center_cz - radius, center_cz + radius + 1):
            by_region[(cx >> 5, cz >> 5)].append((cx, cz))
    out: list[tuple[int, int, bytes]] = []
    for (rx, rz), cells in by_region.items():
        mca = region_dir / f"r.{rx}.{rz}.mca"
        if not mca.is_file():
            continue
        try:
            chunks = read_region(mca)
        except Exception:  # noqa: BLE001 - gesperrte/kaputte Region (Lobby laeuft?) -> ueberspringen
            continue
        for (cx, cz) in cells:
            ch = chunks.get((cx, cz))
            if ch is None:
                continue
            out.append((cx, cz, build_chunk_column_packet(ch, biome_id=biome_id)))
    return out


def bake_lobby_packets(world_dir: str | Path, *, radius: int, biome_id: int = 0) -> dict | None:
    """Live-Lobby-Welt fuer den Hub backen (Option B). Spawn aus level.dat, quadratischer Bereich
    (2*radius+1)^2 um den Spawn-Chunk.

    Rueckgabe dict {packets, origin, spawn_block, center_chunk, chunk_count} ODER None, wenn kein
    EXPLIZITER Spawn gesetzt ist (dann /setworldspawn noetig), keine Chunks existieren oder ein
    Fehler auftritt -> der Hub faellt sauber auf die Plattform zurueck.
    ``origin`` = (sx+0.5, sy, sz+0.5): der Spiel. wird HIERHIN gesetzt; Bridge-Offsets rechnen relativ
    dazu, damit modded/vanilla-Avatare am selben Ort stehen."""
    try:
        wd = Path(world_dir)
        if not (wd / "level.dat").is_file():
            return None
        sx, sy, sz, explicit = world_spawn(wd)
        if not explicit:
            return None
        ccx, ccz = sx >> 4, sz >> 4                       # floor-div (auch bei negativen Koord.)
        packets = bake_area(wd, ccx, ccz, radius, biome_id=biome_id)
        if not packets:
            return None
        return {
            "packets": [p for (_cx, _cz, p) in packets],
            "origin": (sx + 0.5, float(sy), sz + 0.5),
            "spawn_block": (sx, sy, sz),
            "center_chunk": (ccx, ccz),
            "chunk_count": len(packets),
        }
    except Exception:  # noqa: BLE001 - defensiv: jeder Bake-Fehler -> Plattform-Fallback
        return None


def diagnose_lobby_bake(world_dir: str | Path, *, radius: int) -> dict:
    """Nicht-destruktive Diagnose: WARUM backt der Hub die Welt (nicht)? Liest level.dat + Spawn,
    zaehlt bakebare Chunks im Fenster, sammelt ein paar Blocknamen aus dem Spawn-Chunk. Fuer das
    Settings-Panel, damit der Fallback auf die Plattform sichtbar/erklaerbar wird."""
    info: dict = {
        "world_dir": str(world_dir), "level_dat": False, "data_version": None,
        "spawn": None, "spawn_explicit": False, "center_chunk": None,
        "region_file": None, "region_exists": False,
        "chunks_in_window": (2 * radius + 1) ** 2, "chunks_baked": 0,
        "sample_names": [], "error": None, "data_keys": [], "spawn_hits": {},
    }
    try:
        wd = Path(world_dir)
        ld = wd / "level.dat"
        info["level_dat"] = ld.is_file()
        if not ld.is_file():
            return info
        d = read_nbt(gzip.decompress(ld.read_bytes())).get("Data", {})
        info["data_version"] = d.get("DataVersion")
        # Diagnose: welche Data-Schluessel gibt es (wo liegt der Spawn in dieser MC-Version?).
        info["data_keys"] = sorted(str(k) for k in d.keys())
        info["spawn_hits"] = {str(k): str(d.get(k))[:60] for k in d if "spawn" in str(k).lower()}
        sx, sy, sz, explicit = world_spawn(wd)
        info["spawn"] = [sx, sy, sz]
        info["spawn_explicit"] = explicit
        ccx, ccz = sx >> 4, sz >> 4
        info["center_chunk"] = [ccx, ccz]
        rf = wd / "region" / f"r.{ccx >> 5}.{ccz >> 5}.mca"
        info["region_file"] = rf.name
        info["region_exists"] = rf.is_file()
        info["chunks_baked"] = len(bake_area(wd, ccx, ccz, radius))
        if rf.is_file():
            ch = read_region(rf).get((ccx, ccz))
            if ch:
                names: set[str] = set()
                for sec in ch.get("sections", []):
                    for e in ((sec.get("block_states") or {}).get("palette") or []):
                        names.add(str(e.get("Name", "")).replace("minecraft:", ""))
                info["sample_names"] = sorted(names)[:12]
    except Exception as exc:  # noqa: BLE001
        info["error"] = repr(exc)
    return info
