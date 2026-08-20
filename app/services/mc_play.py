"""PLAY-Phasen-Bausteine fuer die Universal-Lobby (MC 1.21.1 / Protokoll 767).

Reine Byte-Funktionen (Bauen von clientbound PLAY-Paketen) - komplett unit-testbar,
kein Socket-Handling. Die Paket-IDs und Feld-Layouts sind gegen einen 767-gepinnten
Dump UND das echte ATM10-Capture verifiziert (siehe Design-Analyse).

WICHTIG (die zwei Registry-Klassen):
  * Netzwerk-synchronisierte Registries (dimension_type, worldgen/biome ...) werden in
    der Config-Phase als registry_data geschickt; Login-``Dimension Type`` und der
    ``Biome``-Container jeder Chunk-Section sind VarInt-Indizes DAHINEIN und damit
    pack-abhaengig. -> In Phase 1 verwenden wir den mitgeschnittenen Login verbatim und
    einen sicheren Biome-Index (0).
  * Known-Pack-Registries (block/block_state, item ...) baut der Client aus vanilla+mods;
    VANILLA-Eintraege behalten ihre vanilla-Nummern, Mods haengen nur hinten an. Deshalb
    rendert eine Plattform aus Vanilla-Bloecken (z.B. stone = block-state-id 1) unter
    JEDEM Loader identisch.

Encodings: Position = i64 ``((x&0x3FFFFFF)<<38)|((z&0x3FFFFFF)<<12)|(y&0xFFF)``;
BitSet = VarInt(longCount)+longs (big-endian); alles Mehrbyte big-endian.
"""

from __future__ import annotations

import struct

from app.services.mc_protocol import _wrap_packet, encode_string, encode_varint

# --------------------------------------------------------------------------- #
# Clientbound PLAY Packet-IDs (Protokoll 767 / MC 1.21.1)
# --------------------------------------------------------------------------- #
PLAY_CB_ADD_ENTITY = 0x01      # Spawn Entity (seit 1.20.2 auch fuer Spieler, Type=128)
PLAY_CB_PLAYER_INFO_UPDATE = 0x3E
PLAY_CB_LOGIN = 0x2B            # Login (Join Game)
PLAY_CB_GAME_EVENT = 0x22       # Game Event
PLAY_CB_KEEP_ALIVE = 0x26       # Keep Alive
PLAY_CB_CHUNK_DATA = 0x27       # Chunk Data & Update Light
PLAY_CB_CHUNK_BATCH_FINISHED = 0x0C  # Chunk Batch Finished (VarInt batch size)
PLAY_CB_CHUNK_BATCH_START = 0x0D     # Chunk Batch Start (keine Felder)
PLAY_CB_PLAYER_ABILITIES = 0x38
PLAY_CB_SYNC_POSITION = 0x40    # Synchronize Player Position
PLAY_CB_SET_HELD_ITEM = 0x53    # Set Held Item (Byte in 767!)
PLAY_CB_SET_CENTER_CHUNK = 0x54
PLAY_CB_SET_DEFAULT_SPAWN = 0x56
PLAY_CB_SYSTEM_CHAT = 0x6C

# Serverbound PLAY Packet-IDs (fuer den Aufrufer, der Client-Pakete auswertet)
PLAY_SB_CONFIRM_TELEPORT = 0x00
PLAY_SB_KEEP_ALIVE = 0x18

# Game-Event 13 = "Start waiting for level chunks" (schliesst den "Lade Gelaende"-Screen)
GAME_EVENT_WAIT_FOR_CHUNKS = 13

# Overworld-Standard (Phase 1: fest angenommen; Phase 2 leitet es aus registry_data ab)
OVERWORLD_MIN_Y = -64
OVERWORLD_HEIGHT = 384
OVERWORLD_SECTIONS = OVERWORLD_HEIGHT // 16  # 24

# Vanilla-Block-State-IDs (1.21.1, aus dem ATM10-Capture bestaetigt)
BLOCK_AIR = 0
BLOCK_STONE = 1

# Vanilla-Entity-Type-ID fuer minecraft:player (1.21.1) - bleibt unter Mods erhalten.
ENTITY_TYPE_PLAYER = 128


def _angle(degrees: float) -> int:
    """MC-Winkel: 1 Byte, 256 = 360 Grad; als signed Byte (-128..127)."""
    v = int(round(degrees / 360.0 * 256.0)) & 0xFF
    return v - 256 if v >= 128 else v


# --------------------------------------------------------------------------- #
# Low-Level-Encoder
# --------------------------------------------------------------------------- #
def encode_position(x: int, y: int, z: int) -> bytes:
    """Gepackte Block-Position als big-endian i64."""
    val = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return struct.pack(">Q", val)


def encode_bitset(mask: int) -> bytes:
    """Minecraft-BitSet: VarInt(longCount) + longs (big-endian).

    ``mask`` als grosses Integer; trailing-Null-Longs werden (wie Java
    ``BitSet.toLongArray``) weggelassen. mask=0 -> longCount 0.
    """
    longs: list[int] = []
    m = mask
    while m > 0:
        longs.append(m & 0xFFFFFFFFFFFFFFFF)
        m >>= 64
    out = bytearray(encode_varint(len(longs)))
    for lo in longs:
        out += struct.pack(">Q", lo)
    return bytes(out)


def _single_valued_container(state_or_biome_id: int) -> bytes:
    """Paletted Container mit genau EINEM Wert: bitsPerEntry=0, Palette=id, dataLen=0."""
    return b"\x00" + encode_varint(state_or_biome_id) + encode_varint(0)


def _chunk_section(block_state_id: int, biome_id: int, *, solid: bool) -> bytes:
    """Eine 16x16x16-Section, komplett aus einem Blockzustand (single-valued Palette)."""
    block_count = 4096 if solid else 0
    out = bytearray(struct.pack(">h", block_count))       # Block Count (Short)
    out += _single_valued_container(block_state_id)       # Block States
    out += _single_valued_container(biome_id)             # Biomes
    return bytes(out)


def _pack_heightmap(height_value: int, *, columns: int = 256, bits: int = 9) -> list[int]:
    """256 Spalten-Hoehen als gepacktes Long-Array (MC-Format: KEIN Spanning, 7 Werte/Long).

    ``height_value`` = Anzahl Bloecke von min_y bis (hoechster_block_y + 1); bei 384er-Welt
    passen die Werte 0..384 in 9 Bit -> floor(64/9)=7 Werte pro Long -> 37 Longs fuer 256 Spalten.
    """
    per_long = 64 // bits
    mask = (1 << bits) - 1
    longs: list[int] = []
    cur = 0
    cnt = 0
    for _ in range(columns):
        cur |= (height_value & mask) << (bits * cnt)
        cnt += 1
        if cnt == per_long:
            longs.append(cur)
            cur = 0
            cnt = 0
    if cnt:
        longs.append(cur)
    return longs


def _nbt_long_array(name: str, longs: list[int]) -> bytes:
    nb = name.encode("utf-8")
    out = bytearray([0x0C])                        # TAG_Long_Array
    out += struct.pack(">H", len(nb)) + nb
    out += struct.pack(">i", len(longs))
    for lo in longs:
        out += struct.pack(">q", lo)
    return bytes(out)


def _heightmaps_nbt(height_value: int) -> bytes:
    """Namenloses Netzwerk-NBT-Compound mit MOTION_BLOCKING + WORLD_SURFACE (Sodium braucht sie)."""
    longs = _pack_heightmap(height_value)
    out = bytearray([0x0A])                        # Root-Compound (kein Name)
    out += _nbt_long_array("MOTION_BLOCKING", longs)
    out += _nbt_long_array("WORLD_SURFACE", longs)
    out.append(0x00)                               # TAG_End
    return bytes(out)


# --------------------------------------------------------------------------- #
# Clientbound PLAY Builder
# --------------------------------------------------------------------------- #
def build_set_center_chunk(chunk_x: int, chunk_z: int) -> bytes:
    body = encode_varint(PLAY_CB_SET_CENTER_CHUNK) + encode_varint(chunk_x) + encode_varint(chunk_z)
    return _wrap_packet(body)


def build_chunk_batch_start() -> bytes:
    """Eroeffnet einen Chunk-Batch. Ohne Start/Finished-Rahmung mesht der Client nur den Spawn-Chunk."""
    return _wrap_packet(encode_varint(PLAY_CB_CHUNK_BATCH_START))


def build_chunk_batch_finished(batch_size: int) -> bytes:
    """Schliesst den Batch ab (Anzahl Chunks). Client antwortet mit Chunk Batch Received (Float)."""
    return _wrap_packet(encode_varint(PLAY_CB_CHUNK_BATCH_FINISHED) + encode_varint(batch_size))


def build_set_default_spawn(x: int, y: int, z: int, angle: float = 0.0) -> bytes:
    body = encode_varint(PLAY_CB_SET_DEFAULT_SPAWN) + encode_position(x, y, z) + struct.pack(">f", angle)
    return _wrap_packet(body)


def build_set_held_item(slot: int) -> bytes:
    """767: Slot ist ein einzelnes Byte (ab 768 VarInt)."""
    body = encode_varint(PLAY_CB_SET_HELD_ITEM) + struct.pack(">b", slot)
    return _wrap_packet(body)


def build_player_abilities(flags: int = 0x01, flying_speed: float = 0.05, fov: float = 0.1) -> bytes:
    """flags: 0x01 invuln, 0x02 flying, 0x04 allow-fly, 0x08 instabuild."""
    body = encode_varint(PLAY_CB_PLAYER_ABILITIES) + struct.pack(">b", flags)
    body += struct.pack(">f", flying_speed) + struct.pack(">f", fov)
    return _wrap_packet(body)


def build_sync_position(
    x: float, y: float, z: float, *, yaw: float = 0.0, pitch: float = 0.0,
    flags: int = 0, teleport_id: int = 1,
) -> bytes:
    """Synchronize Player Position (767): 3x double, 2x float, byte flags, VarInt teleId.

    flags=0 -> alle Werte absolut. Danach schickt der Client Confirm Teleport (0x00).
    """
    body = bytearray(encode_varint(PLAY_CB_SYNC_POSITION))
    body += struct.pack(">ddd", x, y, z)
    body += struct.pack(">ff", yaw, pitch)
    body += struct.pack(">b", flags)
    body += encode_varint(teleport_id)
    return _wrap_packet(bytes(body))


def build_game_event(event: int, value: float = 0.0) -> bytes:
    body = encode_varint(PLAY_CB_GAME_EVENT) + struct.pack(">B", event) + struct.pack(">f", value)
    return _wrap_packet(body)


def build_keep_alive(keep_alive_id: int) -> bytes:
    body = encode_varint(PLAY_CB_KEEP_ALIVE) + struct.pack(">q", keep_alive_id)
    return _wrap_packet(body)


def build_flat_chunk(
    chunk_x: int, chunk_z: int, *,
    section_count: int = OVERWORLD_SECTIONS,
    floor_section_index: int = 7,
    floor_state_id: int = BLOCK_STONE,
    biome_id: int = 0,
    full_bright: bool = True,
) -> bytes:
    """Eine flache Plattform-Chunk: alle Sections Luft, ausser einer soliden Boden-Section.

    section_count MUSS = dimensionHeight/16 sein (Overworld 384 -> 24), sonst trennt der
    Client ("wrong number of sections"). Boden liegt in ``floor_section_index`` (bei
    section_count=24, floor=7 -> y 48..63, Spawn also auf y=64).
    """
    body = bytearray(encode_varint(PLAY_CB_CHUNK_DATA))
    body += struct.pack(">i", chunk_x)
    body += struct.pack(">i", chunk_z)
    # Heightmaps: Hoehe = Bloecke von min_y bis Oberkante der Boden-Section (Sodium braucht sie).
    body += _heightmaps_nbt((floor_section_index + 1) * 16)
    # Sections -> laengenpraefigiert.
    sections = bytearray()
    for idx in range(section_count):
        if idx == floor_section_index:
            sections += _chunk_section(floor_state_id, biome_id, solid=True)
        else:
            sections += _chunk_section(BLOCK_AIR, biome_id, solid=False)
    body += encode_varint(len(sections))
    body += sections
    # Block Entities: keine.
    body += encode_varint(0)
    # --- Licht ---
    light_sections = section_count + 2  # inkl. je einer Section unter/ueber der Welt
    if full_bright:
        sky_mask = (1 << light_sections) - 1
        empty_sky_mask = 0
    else:
        sky_mask = 0
        empty_sky_mask = (1 << light_sections) - 1
    block_mask = 0
    empty_block_mask = (1 << light_sections) - 1
    body += encode_bitset(sky_mask)
    body += encode_bitset(block_mask)
    body += encode_bitset(empty_sky_mask)
    body += encode_bitset(empty_block_mask)
    # Sky-Light-Arrays (nur wenn full_bright): pro gesetztem Bit 2048 Bytes 0xFF.
    if full_bright:
        body += encode_varint(light_sections)
        full = b"\xff" * 2048
        for _ in range(light_sections):
            body += encode_varint(2048) + full
    else:
        body += encode_varint(0)
    # Block-Light-Arrays: keine.
    body += encode_varint(0)
    return _wrap_packet(bytes(body))


def build_player_info_update(uuid16: bytes, name: str, *, listed: bool = True) -> bytes:
    """Player Info Update (0x3E) mit add_player(0x01)+listed(0x08).

    MUSS vor dem Spawn-Entity kommen - die Spieler-Entity holt sich Name/Skin per UUID
    aus dieser Liste. 0 Properties -> Default-Skin (Steve/Alex).
    """
    actions = 0x01 | 0x08
    body = bytearray(encode_varint(PLAY_CB_PLAYER_INFO_UPDATE))
    body.append(actions)
    body += encode_varint(1)                       # ein Spieler
    body += (uuid16 or b"")[:16].ljust(16, b"\x00")
    # add_player (0x01): Name + Properties
    body += encode_string(name)
    body += encode_varint(0)                        # 0 Properties (kein Skin-Texture)
    # listed (0x08): Bool
    body += b"\x01" if listed else b"\x00"
    return _wrap_packet(bytes(body))


def build_add_entity(
    entity_id: int, uuid16: bytes, x: float, y: float, z: float, *,
    yaw: float = 0.0, pitch: float = 0.0, head_yaw: float = 0.0,
    entity_type: int = ENTITY_TYPE_PLAYER,
) -> bytes:
    """Spawn Entity (0x01). Fuer Spieler entity_type=128 und dieselbe UUID wie im Info-Update."""
    body = bytearray(encode_varint(PLAY_CB_ADD_ENTITY))
    body += encode_varint(entity_id)
    body += (uuid16 or b"")[:16].ljust(16, b"\x00")
    body += encode_varint(entity_type)
    body += struct.pack(">ddd", x, y, z)
    body += struct.pack(">b", _angle(pitch))
    body += struct.pack(">b", _angle(yaw))
    body += struct.pack(">b", _angle(head_yaw))
    body += encode_varint(0)                        # Data
    body += struct.pack(">hhh", 0, 0, 0)            # Velocity
    return _wrap_packet(bytes(body))


def build_system_chat(nbt_text_component: bytes, *, overlay: bool = False) -> bytes:
    """System Chat (0x6C): TextComponent (Netzwerk-NBT) + Overlay-Bool.

    ``nbt_text_component`` z.B. aus ``mc_dispatch._nbt_text_component``.
    """
    body = encode_varint(PLAY_CB_SYSTEM_CHAT) + nbt_text_component + (b"\x01" if overlay else b"\x00")
    return _wrap_packet(body)
