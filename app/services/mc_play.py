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
PLAY_CB_ENTITY_POS_ROT = 0x2F  # Update Entity Position & Rotation (kleine Bewegung, Delta)
PLAY_CB_TELEPORT_ENTITY = 0x70 # Teleport Entity (grosse Bewegung, absolute Koordinaten)
PLAY_CB_HEAD_ROTATION = 0x48   # Set Head Rotation
PLAY_CB_REMOVE_ENTITIES = 0x42 # Remove Entities
PLAY_CB_PLAYER_INFO_REMOVE = 0x3D
PLAY_CB_PLAYER_INFO_UPDATE = 0x3E
PLAY_CB_LOGIN = 0x2B            # Login (Join Game)
PLAY_CB_GAME_EVENT = 0x22       # Game Event
PLAY_CB_UPDATE_TIME = 0x64      # Update Time (worldAge Long + timeOfDay Long); aus minecraft-data 767
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
# 0x77 declare_recipes = ClientboundUpdateRecipesPacket. Traegt normalerweise ALLE Rezepte
# (bei ATM10 mehrere MB, per neoforge:split zerlegt). Wir schicken NUR ein LEERES Paket:
# das feuert clientseitig RecipesUpdatedEvent, wodurch JEI & Co. schon BEIM JOIN (hinter dem
# Ladebildschirm) initialisieren statt beim ersten Inventar-Oeffnen 30-40s zu freezen.
# 0x41 unlock_recipes (Rezeptbuch) feuert dieses Event NICHT - deshalb kam JEI bisher zu spaet.
PLAY_CB_DECLARE_RECIPES = 0x77

# --- Kisten-Menue: Kompass -> Server-Auswahl -> Transfer (Phase 4c/4d) --------- #
# Clientbound (Feld-Layouts aus minecraft-data 1.21.1 verifiziert)
PLAY_CB_OPEN_SCREEN = 0x33         # open_window: windowId(varint), menuType(varint), title(NBT)
PLAY_CB_CONTAINER_CONTENT = 0x13   # window_items: windowId(u8), stateId(varint), items[Slot], carried(Slot)
PLAY_CB_CONTAINER_SET_SLOT = 0x15  # set_slot:   windowId(u8), stateId(varint), slot(i16), item(Slot)
PLAY_CB_CONTAINER_CLOSE = 0x12     # close_window (clientbound): windowId(u8)
PLAY_CB_TRANSFER = 0x73            # transfer:   host(String), port(varint)
# Serverbound (fuer den Aufrufer, der Client-Pakete auswertet)
PLAY_SB_HELD_ITEM = 0x2F           # held_item_slot: slotId(i16)
PLAY_SB_USE_ITEM = 0x39            # use_item (Rechtsklick Luft): hand,seq,rot
PLAY_SB_USE_ITEM_ON = 0x38         # block_place (Rechtsklick auf Block)
PLAY_SB_CONTAINER_CLICK = 0x0E     # window_click: windowId(u8), stateId(varint), slot(i16), ...
PLAY_SB_CONTAINER_CLOSE = 0x0F     # close_window (serverbound): windowId(u8)

# Menue-Typen (minecraft:menu-Registry, vanilla-Reihenfolge - Mods haengen nur an)
MENU_GENERIC_9X3 = 2               # 27 Container- + 36 Spieler-Slots = 63
MENU_GENERIC_9X6 = 5               # 54 + 36 = 90

# Datenkomponenten-ID (net.minecraft.core.component.DataComponents Reihenfolge)
DATA_COMPONENT_CUSTOM_NAME = 5     # custom_name
DATA_COMPONENT_LORE = 7            # ...custom_name(5), item_name(6), lore(7), rarity(8)...

# Vanilla-Item-IDs (1.21.1; Vanilla behaelt seine Nummern unter Mods)
ITEM_COMPASS = 928
ITEM_GRASS_BLOCK = 27
ITEM_DIAMOND = 805
ITEM_EMERALD = 806
ITEM_NETHER_STAR = 1110
ITEM_ENDER_PEARL = 993
ITEM_BEACON = 396
ITEM_ENCHANTED_BOOK = 1114
ITEM_GRAY_STAINED_GLASS_PANE = 494

# Spieler-Inventar-Container (windowId 0): Hotbar-Slot 0 = Container-Slot 36.
INV_HOTBAR0_SLOT = 36

# Serverbound PLAY Packet-IDs (fuer den Aufrufer, der Client-Pakete auswertet)
PLAY_SB_CONFIRM_TELEPORT = 0x00
PLAY_SB_KEEP_ALIVE = 0x18

# Game-Event 13 = "Start waiting for level chunks" (schliesst den "Lade Gelaende"-Screen)
GAME_EVENT_WAIT_FOR_CHUNKS = 13
# Game-Event 3 = "Change game mode"; Value = Gamemode (0 survival, 1 creative, 2 adventure, 3 spec.)
GAME_EVENT_CHANGE_GAMEMODE = 3
GAMEMODE_ADVENTURE = 2
GAME_EVENT_END_RAINING = 1      # Regen aus (Lobby = klares Wetter, nicht abgedunkelt)
GAME_EVENT_RAIN_LEVEL = 7       # Value 0.0 = kein Regen
GAME_EVENT_THUNDER_LEVEL = 8    # Value 0.0 = kein Gewitter

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


def _pack_longs(values: list[int], bits: int) -> bytes:
    """Netzwerk-Long-Array (MC 1.16+): jeder Wert ``bits`` breit, KEIN Spanning ueber
    Long-Grenzen (Padding oben). Rueckgabe = VarInt(longCount) + longs (big-endian)."""
    per_long = 64 // bits
    mask = (1 << bits) - 1
    longs: list[int] = []
    cur = 0
    cnt = 0
    for v in values:
        cur |= (v & mask) << (bits * cnt)
        cnt += 1
        if cnt == per_long:
            longs.append(cur)
            cur = 0
            cnt = 0
    if cnt:
        longs.append(cur)
    out = bytearray(encode_varint(len(longs)))
    for lo in longs:
        out += struct.pack(">Q", lo)
    return bytes(out)


# Block-States: <=8 Bit -> indirekte Palette; darueber direkt (globale IDs). 1.21.1 hat ~28k
# Block-States -> ceil(log2)=15 Bit fuer den Direct-Modus.
_BLOCKSTATE_DIRECT_BITS = 15


def _paletted_block_states(states: list[int]) -> bytes:
    """Paletted Container fuer 4096 Block-State-IDs (YZX-Reihenfolge).

    single-valued (bits=0, nur eine ID), indirekt (4..8 Bit, lokale Palette) oder direkt
    (15 Bit, globale IDs) je nach Anzahl verschiedener Bloecke in der Sektion."""
    index: dict[int, int] = {}
    uniq: list[int] = []
    for s in states:
        if s not in index:
            index[s] = len(uniq)
            uniq.append(s)
    if len(uniq) == 1:
        return _single_valued_container(uniq[0])
    bits = max(4, (len(uniq) - 1).bit_length())
    if bits <= 8:                                  # indirekt: lokale Palette
        out = bytearray([bits])
        out += encode_varint(len(uniq))
        for v in uniq:
            out += encode_varint(v)
        out += _pack_longs([index[s] for s in states], bits)
        return bytes(out)
    # direkt: keine Palette, Werte = globale State-IDs
    out = bytearray([_BLOCKSTATE_DIRECT_BITS])
    out += _pack_longs(states, _BLOCKSTATE_DIRECT_BITS)
    return bytes(out)


def _chunk_section_from_states(states: list[int], biome_id: int = 0) -> bytes:
    """Eine 16x16x16-Section aus 4096 Block-State-IDs (YZX). BLOCK_AIR zaehlt nicht als Block."""
    block_count = sum(1 for s in states if s != BLOCK_AIR)
    out = bytearray(struct.pack(">h", block_count))
    out += _paletted_block_states(states)
    out += _single_valued_container(biome_id)
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


def _pack_heightmap_columns(heights: list[int], *, bits: int = 9) -> list[int]:
    """256 Spalten-Hoehen (je 0..worldHeight) als gepacktes Long-Array (KEIN Spanning, 7/Long)."""
    per_long = 64 // bits
    mask = (1 << bits) - 1
    longs: list[int] = []
    cur = 0
    cnt = 0
    for h in heights:
        cur |= (h & mask) << (bits * cnt)
        cnt += 1
        if cnt == per_long:
            longs.append(cur)
            cur = 0
            cnt = 0
    if cnt:
        longs.append(cur)
    return longs


def _heightmaps_nbt_columns(heights: list[int]) -> bytes:
    """Wie _heightmaps_nbt, aber mit echten Spalten-Hoehen (256 Werte, Index z*16+x)."""
    longs = _pack_heightmap_columns(heights)
    out = bytearray([0x0A])
    out += _nbt_long_array("MOTION_BLOCKING", longs)
    out += _nbt_long_array("WORLD_SURFACE", longs)
    out.append(0x00)
    return bytes(out)


# --------------------------------------------------------------------------- #
# Clientbound PLAY Builder
# --------------------------------------------------------------------------- #
def build_set_center_chunk(chunk_x: int, chunk_z: int) -> bytes:
    body = encode_varint(PLAY_CB_SET_CENTER_CHUNK) + encode_varint(chunk_x) + encode_varint(chunk_z)
    return _wrap_packet(body)


def build_declare_recipes_empty() -> bytes:
    """Leeres declare_recipes (0x77) = 0 Rezepte. Loest clientseitig RecipesUpdatedEvent aus,
    damit rezeptbasierte Mod-Clients (v.a. JEI) schon beim Join initialisieren. Fuer eine
    Kosmetik-Lobby ohne Crafting ist ein leerer Rezeptsatz unkritisch: Body = VarInt(0)."""
    return _wrap_packet(encode_varint(PLAY_CB_DECLARE_RECIPES) + encode_varint(0))


# --------------------------------------------------------------------------- #
# Kisten-Menue: Item-Slots, Open Screen, Container-Inhalt, Transfer
# --------------------------------------------------------------------------- #
def _nbt_field_str(name: str, val: str) -> bytes:
    nb, vb = name.encode("utf-8"), val.encode("utf-8")
    return bytes([0x08]) + struct.pack(">H", len(nb)) + nb + struct.pack(">H", len(vb)) + vb


def _nbt_field_byte(name: str, val: int) -> bytes:
    nb = name.encode("utf-8")
    return bytes([0x01]) + struct.pack(">H", len(nb)) + nb + struct.pack(">b", val & 0xFF)


def _nbt_text_body(text: str, color: str | None = None, italic: bool | None = None) -> bytes:
    """Compound-BODY (Felder + TAG_End) einer TextComponent - OHNE fuehrendes Root-Tag 0x0A.
    So verwendbar sowohl als Compound-Root (mit vorangestelltem 0x0A) als auch als Element
    einer TAG_List (deren Header den Elementtyp 0x0A bereits deklariert)."""
    b = bytearray()
    b += _nbt_field_str("text", text)
    if color is not None:
        b += _nbt_field_str("color", color)
    if italic is not None:
        b += _nbt_field_byte("italic", 1 if italic else 0)
    b.append(0x00)                               # TAG_End
    return bytes(b)


def _text_component_from_runs(runs: "list[tuple[str, str | None]]", *, italic: bool | None = None) -> bytes:
    """Netzwerk-NBT (namenloser Root, seit 1.20.2) einer TextComponent aus Farb-Runs.

    ``runs`` = Liste von (Text, Farbname|None). Ein einzelner farbloser Run ohne ``italic``
    wird als schneller TAG_String-Root kodiert; sonst Compound-Root. Mehrere Runs ->
    Root ``text=""`` mit ``extra``-Liste (mehrere Farben in einer Zeile, wie Bukkit-Legacy)."""
    runs = [r for r in runs if r is not None] or [("", None)]
    if len(runs) == 1 and italic is None and runs[0][1] is None:
        raw = runs[0][0].encode("utf-8")
        return bytes([0x08]) + struct.pack(">H", len(raw)) + raw
    if len(runs) == 1:
        return bytes([0x0A]) + _nbt_text_body(runs[0][0], runs[0][1], italic)
    out = bytearray([0x0A])                      # Compound-Root (kein Name)
    out += _nbt_field_str("text", "")
    if italic is not None:                       # Root-Formatierung vererbt sich an extra-Kinder
        out += _nbt_field_byte("italic", 1 if italic else 0)
    name = b"extra"                              # extra: TAG_List<TAG_Compound>
    out += bytes([0x09]) + struct.pack(">H", len(name)) + name
    out += bytes([0x0A]) + struct.pack(">i", len(runs))
    for text, color in runs:
        out += _nbt_text_body(text, color, None)
    out.append(0x00)                             # Root TAG_End
    return bytes(out)


def _text_component_nbt(text: str, *, color: str | None = None, italic: bool | None = None) -> bytes:
    """Einfache Einzelfarb-TextComponent (Bequemlichkeits-Wrapper um _text_component_from_runs)."""
    return _text_component_from_runs([(text, color)], italic=italic)


def encode_slot_empty() -> bytes:
    """Leerer Item-Slot: itemCount = 0 (danach folgt nichts)."""
    return encode_varint(0)


def encode_slot(item_id: int, count: int = 1, custom_name: str | None = None,
                *, name_color: str | None = None,
                lore: "list[tuple[str, str | None]] | None" = None,
                name_runs: "list[tuple[str, str | None]] | None" = None,
                lore_runs: "list[list[tuple[str, str | None]]] | None" = None) -> bytes:
    """Item-Stack im 1.21.1-Slot-Format:
    VarInt count; wenn >0: VarInt itemId, VarInt add-Komponenten, VarInt remove-Komponenten,
    dann die Komponenten (aufsteigend nach ID).

    Anzeigename (Komponente 5): entweder ``name_runs`` (mehrfarbig) oder ``custom_name``
    (+ optional ``name_color``). Lore (Komponente 7): entweder ``lore_runs`` (jede Zeile
    ein Run-Liste, mehrfarbig) oder ``lore`` (einfarbige (Text, Farbe)-Zeilen). Lore wird
    stets auf italic=false gesetzt (Vanilla rendert Lore sonst kursiv)."""
    if count <= 0:
        return encode_slot_empty()
    out = bytearray(encode_varint(count) + encode_varint(item_id))
    comps = bytearray()
    n_add = 0
    # custom_name (5)
    if name_runs is not None:
        name_comp = _text_component_from_runs(name_runs)
    elif custom_name is not None:
        name_comp = _text_component_nbt(custom_name, color=name_color)
    else:
        name_comp = None
    if name_comp is not None:
        comps += encode_varint(DATA_COMPONENT_CUSTOM_NAME) + name_comp
        n_add += 1
    # lore (7)
    if lore_runs is not None:
        lore_lines = [_text_component_from_runs(line, italic=False) for line in lore_runs]
    elif lore:
        lore_lines = [_text_component_nbt(t, color=c, italic=False) for (t, c) in lore]
    else:
        lore_lines = []
    if lore_lines:
        comps += encode_varint(DATA_COMPONENT_LORE) + encode_varint(len(lore_lines))
        for lc in lore_lines:
            comps += lc
        n_add += 1
    out += encode_varint(n_add) + encode_varint(0) + comps               # n add, 0 remove
    return bytes(out)


def build_open_screen(window_id: int, menu_type: int, title: str) -> bytes:
    """Open Screen (0x33): oeffnet clientseitig ein Container-Fenster."""
    body = (encode_varint(PLAY_CB_OPEN_SCREEN) + encode_varint(window_id)
            + encode_varint(menu_type) + _text_component_nbt(title))
    return _wrap_packet(body)


def build_container_content(window_id: int, slots, *, state_id: int = 1,
                            carried: bytes | None = None) -> bytes:
    """Set Container Content (0x13): fuellt ALLE Slots eines offenen Fensters.
    ``slots`` = Liste vorkodierter Slot-Bytes (Container- UND Spieler-Inventar-Slots)."""
    body = bytearray(encode_varint(PLAY_CB_CONTAINER_CONTENT))
    body += bytes([window_id & 0xFF])                                    # ContainerID = u8
    body += encode_varint(state_id) + encode_varint(len(slots))
    for s in slots:
        body += s
    body += carried if carried is not None else encode_slot_empty()
    return _wrap_packet(bytes(body))


def build_set_slot(window_id: int, slot: int, item: bytes, *, state_id: int = 1) -> bytes:
    """Set Slot (0x15): setzt EINEN Slot (z.B. Kompass in die Hotbar, windowId 0)."""
    body = (encode_varint(PLAY_CB_CONTAINER_SET_SLOT) + bytes([window_id & 0xFF])
            + encode_varint(state_id) + struct.pack(">h", slot) + item)
    return _wrap_packet(body)


def build_close_container(window_id: int) -> bytes:
    """Close Container (clientbound 0x12): schliesst das Fenster beim Client."""
    return _wrap_packet(encode_varint(PLAY_CB_CONTAINER_CLOSE) + bytes([window_id & 0xFF]))


def build_transfer(host: str, port: int) -> bytes:
    """Transfer (0x73): Client trennt und verbindet sich zu host:port neu (next_state=3)."""
    return _wrap_packet(encode_varint(PLAY_CB_TRANSFER) + encode_string(host) + encode_varint(port))


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


def build_update_time(world_age: int, time_of_day: int) -> bytes:
    """Update Time (0x64): World Age (Long) + Time of Day (Long).

    Ein NEGATIVES ``time_of_day`` friert die Sonne bei ``abs(time_of_day)`` ein (kein
    Tag/Nacht-Zyklus). Fuer eine Lobby: ``time_of_day=-6000`` = fester Mittag (hell)."""
    body = encode_varint(PLAY_CB_UPDATE_TIME) + struct.pack(">qq", int(world_age), int(time_of_day))
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


def build_world_chunk(
    chunk_x: int, chunk_z: int,
    sections_by_index: dict[int, list[int]],
    heights: list[int], *,
    section_count: int = OVERWORLD_SECTIONS,
    biome_id: int = 0,
    full_bright: bool = True,
) -> bytes:
    """Chunk-Data-Packet aus echten Block-Daten (gebackene Lobby-Welt).

    ``sections_by_index``: {netz-Section-Index 0..section_count-1 -> [4096 State-IDs, YZX]}.
    Fehlende Indizes = Luft. ``heights``: 256 Spalten-Hoehen (Index z*16+x) fuer die Heightmaps.
    Spiegelt exakt build_flat_chunk (Framing, Licht), nur mit Multi-Block-Sektionen.
    """
    body = bytearray(encode_varint(PLAY_CB_CHUNK_DATA))
    body += struct.pack(">i", chunk_x)
    body += struct.pack(">i", chunk_z)
    body += _heightmaps_nbt_columns(heights)
    sections = bytearray()
    for idx in range(section_count):
        states = sections_by_index.get(idx)
        if states is None:
            sections += _chunk_section(BLOCK_AIR, biome_id, solid=False)
        else:
            sections += _chunk_section_from_states(states, biome_id)
    body += encode_varint(len(sections))
    body += sections
    body += encode_varint(0)                        # Block Entities: keine (Schilder-Text folgt spaeter)
    # --- Licht (identisch zu build_flat_chunk: voll hell) ---
    light_sections = section_count + 2
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
    if full_bright:
        body += encode_varint(light_sections)
        full = b"\xff" * 2048
        for _ in range(light_sections):
            body += encode_varint(2048) + full
    else:
        body += encode_varint(0)
    body += encode_varint(0)
    return _wrap_packet(bytes(body))


def build_player_info_update(uuid16: bytes, name: str, *, listed: bool = True,
                             textures: str = "", signature: str = "") -> bytes:
    """Player Info Update (0x3E) mit add_player(0x01)+listed(0x08).

    MUSS vor dem Spawn-Entity kommen - die Spieler-Entity holt sich Name/Skin per UUID
    aus dieser Liste. Ohne ``textures`` -> 0 Properties -> Default-Skin (Steve/Alex).
    Mit ``textures`` (Base64-Wert der Mojang-"textures"-Property) + optional ``signature``
    wird der echte Skin gerendert (fuer Bridge-Avatare echter Spieler).
    """
    actions = 0x01 | 0x08
    body = bytearray(encode_varint(PLAY_CB_PLAYER_INFO_UPDATE))
    body.append(actions)
    body += encode_varint(1)                       # ein Spieler
    body += (uuid16 or b"")[:16].ljust(16, b"\x00")
    # add_player (0x01): Name + Properties
    body += encode_string(name)
    if textures:
        body += encode_varint(1)                    # 1 Property: textures
        body += encode_string("textures")
        body += encode_string(textures)
        if signature:
            body += b"\x01"                          # is_signed = true
            body += encode_string(signature)
        else:
            body += b"\x00"                          # is_signed = false (unsignierter Skin)
    else:
        body += encode_varint(0)                    # 0 Properties (kein Skin-Texture)
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


def _delta_short(old: float, new: float) -> int:
    """Positions-Delta fuer 0x2F: (new*4096 - old*4096) als i16."""
    return int(round(new * 4096)) - int(round(old * 4096))


def build_teleport_entity(
    entity_id: int, x: float, y: float, z: float, *,
    yaw: float = 0.0, pitch: float = 0.0, on_ground: bool = True,
) -> bytes:
    body = bytearray(encode_varint(PLAY_CB_TELEPORT_ENTITY))
    body += encode_varint(entity_id)
    body += struct.pack(">ddd", x, y, z)
    body += struct.pack(">b", _angle(yaw)) + struct.pack(">b", _angle(pitch))
    body += b"\x01" if on_ground else b"\x00"
    return _wrap_packet(bytes(body))


def build_entity_move_rot(
    entity_id: int, ox: float, oy: float, oz: float, nx: float, ny: float, nz: float, *,
    yaw: float = 0.0, pitch: float = 0.0, on_ground: bool = True,
) -> bytes:
    """Kleine Bewegung als Delta (0x2F). Bei >~8 Bloecken/Achse Fallback auf Teleport (0x70)."""
    dx, dy, dz = _delta_short(ox, nx), _delta_short(oy, ny), _delta_short(oz, nz)
    if max(abs(dx), abs(dy), abs(dz)) > 32000:
        return build_teleport_entity(entity_id, nx, ny, nz, yaw=yaw, pitch=pitch, on_ground=on_ground)
    body = bytearray(encode_varint(PLAY_CB_ENTITY_POS_ROT))
    body += encode_varint(entity_id)
    body += struct.pack(">hhh", dx, dy, dz)
    body += struct.pack(">b", _angle(yaw)) + struct.pack(">b", _angle(pitch))
    body += b"\x01" if on_ground else b"\x00"
    return _wrap_packet(bytes(body))


def build_head_rotation(entity_id: int, head_yaw: float) -> bytes:
    body = encode_varint(PLAY_CB_HEAD_ROTATION) + encode_varint(entity_id) + struct.pack(">b", _angle(head_yaw))
    return _wrap_packet(body)


def build_remove_entities(entity_ids: list[int]) -> bytes:
    body = bytearray(encode_varint(PLAY_CB_REMOVE_ENTITIES))
    body += encode_varint(len(entity_ids))
    for eid in entity_ids:
        body += encode_varint(eid)
    return _wrap_packet(bytes(body))


def build_player_info_remove(uuids16: list[bytes]) -> bytes:
    body = bytearray(encode_varint(PLAY_CB_PLAYER_INFO_REMOVE))
    body += encode_varint(len(uuids16))
    for u in uuids16:
        body += (u or b"")[:16].ljust(16, b"\x00")
    return _wrap_packet(bytes(body))


def build_system_chat(nbt_text_component: bytes, *, overlay: bool = False) -> bytes:
    """System Chat (0x6C): TextComponent (Netzwerk-NBT) + Overlay-Bool.

    ``nbt_text_component`` z.B. aus ``mc_dispatch._nbt_text_component``.
    """
    body = encode_varint(PLAY_CB_SYSTEM_CHAT) + nbt_text_component + (b"\x01" if overlay else b"\x00")
    return _wrap_packet(body)
