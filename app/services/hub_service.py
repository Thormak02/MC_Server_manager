"""Threaded Multi-Client-Hub (Phase 3b) - die geteilte Universal-Lobby.

Jede Verbindung laeuft in einem eigenen Thread: Offline-Login -> Config-Replay
(spoof des Modpacks, damit modded Clients durchkommen) -> eigene Vanilla-PLAY-Welt.
Danach teilen sich ALLE verbundenen Clients denselben Welt-Zustand: jeder Spieler wird
den anderen als Vanilla-Spieler-Entity gespawnt, seine Bewegung und sein Chat werden an
alle anderen gebroadcastet. So sehen sich modded und (spaeter) vanilla/fabric/quilt
Clients gemeinsam in EINER Welt - ohne dass ein einziger Mod auf Serverseite laeuft.

``Lobby-Bot`` ist ein virtueller Spieler im Roster (kein Socket): er durchlaeuft dieselbe
Spawn-/Bewegungs-/Broadcast-Maschinerie wie echte Spieler und macht den Hub schon mit
einem einzigen echten Client testbar.

Die reinen Protokoll-Bausteine liegen in ``mc_play`` / ``mc_dispatch`` / ``replay_service``;
hier ist nur die Zustands- und Socket-Orchestrierung.
"""

from __future__ import annotations

import math
import re
import socket
import struct
import threading
import time

from app.services import app_setting_service
from app.services import mc_dispatch as mcd
from app.services import mc_play as pl
from app.services import mc_protocol as mp
from app.services import replay_service as rp

# --- Welt-/Timing-Konstanten (spiegeln die in Phase 1 bewiesenen Werte) ---
_GRID_RADIUS = 4               # 9x9-Chunk-Grid -> ~112x112 sichtbare Plattform
_FLOOR_SECTION = 7             # Boden y 48..63, Spawn auf y=64
_SPAWN = (8.5, 64.0, 8.5)
_KEEPALIVE_INTERVAL = 10.0
_READ_TIMEOUT = 0.5
_CONFIG_WAIT_TIMEOUT = 6.0
_BOT_TICK = 0.1

# Serverbound PLAY Packet-IDs (767)
_SB_CONFIRM_TELEPORT = 0x00
_SB_CHAT_COMMAND = 0x04
_SB_CHAT = 0x06
_SB_KEEP_ALIVE = 0x18
_SB_SET_POS = 0x1A            # x,y,z, flags
_SB_SET_POS_ROT = 0x1B        # x,y,z, yaw,pitch, flags
_SB_SET_ROT = 0x1C           # yaw,pitch, flags
_SB_HELD_ITEM = 0x2F         # held_item_slot (i16) - aktueller Hotbar-Slot
_SB_USE_ITEM = 0x39          # Rechtsklick in die Luft
_SB_USE_ITEM_ON = 0x38       # Rechtsklick auf einen Block
_SB_CONTAINER_CLICK = 0x0E   # Klick in einem offenen Kisten-Menue
_SB_CONTAINER_CLOSE = 0x0F   # Kisten-Menue geschlossen (Esc)

_BOT_KEY = 0                  # reservierter Roster-Key fuer den virtuellen Bot

# --- Kisten-Menue: Ziel-Server aus der Manager-DB -----------------------------
# Die Server-Auswahl kommt aus lobby_service.get_menu_servers(db) - dieselbe Quelle
# wie das Java-Lobby-Plugin (gateway_enabled-Server; Transfer-Ziel <alias>.<domain>:
# <network_port>, laeuft also ueber dasselbe Gateway). KEINE hardcodierten Adressen.
_MENU_WINDOW = 1                       # Fenster-ID des Kisten-Menues (!= 0 Spieler-Inv)

# Bukkit-Material (aus lobby_service._TYPE_MATERIAL) -> Vanilla-Item-ID fuers Icon.
_MATERIAL_ITEM = {
    "PAPER": pl.ITEM_GRASS_BLOCK,
    "PURPUR_BLOCK": pl.ITEM_GRASS_BLOCK,
    "GRASS_BLOCK": pl.ITEM_GRASS_BLOCK,
    "ANVIL": pl.ITEM_NETHER_STAR,      # Forge/NeoForge-Modpacks
    "LOOM": pl.ITEM_EMERALD,           # Fabric/Quilt
}
_COLOR_CODE = re.compile(r"&.")        # Bukkit-Legacy-Farbcodes (&a, &7 ...)


def _plain(text: str) -> str:
    """Legacy-&-Farbcodes entfernen (der Hub nutzt Component-custom_name, kein &)."""
    return _COLOR_CODE.sub("", text or "").strip()


def _menu_servers() -> list[dict]:
    """DB-getriebene Server-Auswahl fuers Kompass-Menue (leer bei DB-Problemen)."""
    try:
        from app.db.session import SessionLocal
        from app.services import lobby_service
        with SessionLocal() as db:
            return lobby_service.get_menu_servers(db)
    except Exception as exc:  # noqa: BLE001 - Menue darf den Hub-Thread nie crashen
        print(f"[hub] Menue-Serverliste nicht ladbar: {exc!r}")
        return []

# PLAY-Setup-Pakete aus dem Capture, die wir mit-abspielen, damit der modded Client
# in einen konsistenten Zustand kommt und beim Oeffnen eines Screens (Inventar, spaeter
# Kisten-Menue) nicht crasht.
#
# Vanilla-Datenpakete: 0x11 Commands, 0x41/0x74/0x75 (Recipes/Advancements/Mod-Daten).
_SETUP_PACKET_IDS = frozenset({0x11, 0x41, 0x74, 0x75})
# 0x19 = custom_payload (Mod-Sync). Wir spielen ALLE Mod-Sync-Pakete ab AUSSER den paar
# riesigen Kanaelen (12 MB neoforge:split + FTB-Quests/Buecher ~2 MB), die fuer eine
# kosmetische Lobby unnoetig sind. Damit kommt u.a. apothic_enchanting:enchantment_info
# (1 KB) durch -> Apotheosis-Enchantment-Daten geladen -> JEI/Inventar crasht nicht mehr.
_CB_CUSTOM_PAYLOAD = 0x19
_SKIP_PAYLOAD_CHANNELS = frozenset({
    "neoforge:split",                     # ~12 MB gesplittete Registry/Rezept-Daten
    "ftbquests:sync_translation_table",   # ~1 MB Quest-Uebersetzungen
    "ftbquests:sync_quests_message",      # ~0.6 MB Quests
    "rechiseled:main",                    # ~0.5 MB Textur-Daten
    "modonomicon:sync_book_data",         # ~0.2 MB Buch
    "modonomicon:sync_multiblock_data",
    "modonomicon:sync_book_unlock_states",
    "productivebees:beedata",             # ~0.14 MB
    "silentgear:sync_materials",
})


class _Reader:
    """Gepufferter Socket-Leser (Handshake + laengenpraefigierte Pakete)."""

    def __init__(self, sock: socket.socket):
        self.sock = sock
        self.buf = bytearray()

    def _fill(self, timeout: float) -> None:
        self.sock.settimeout(timeout)
        chunk = self.sock.recv(16384)
        if not chunk:
            raise ConnectionError("closed")
        self.buf.extend(chunk)

    def read_handshake(self, timeout: float = 10.0) -> mp.Handshake:
        while True:
            try:
                hs = mp.parse_handshake(bytes(self.buf))
                del self.buf[: hs.consumed]
                return hs
            except mp.IncompletePacket:
                self._fill(timeout)

    def read_packet(self, timeout: float = 10.0):
        while True:
            got = mcd.try_read_packet(bytes(self.buf))
            if got is not None:
                pid, fields, consumed = got
                del self.buf[:consumed]
                return pid, fields
            self._fill(timeout)


class _Session:
    """Ein Spieler im Hub. ``sock is None`` => virtueller Bot (empfaengt nichts)."""

    def __init__(self, conn_id, sock, eid, uuid16, name, x, y, z, yaw=0.0, pitch=0.0):
        self.conn_id = conn_id
        self.sock = sock
        self.eid = eid
        self.uuid16 = uuid16
        self.name = name
        self.x = x
        self.y = y
        self.z = z
        self.yaw = yaw
        self.pitch = pitch
        self.alive = True
        self.send_lock = threading.Lock()   # serialisiert Writes auf DIESES Socket
        self.held_slot = 0                  # aktueller Hotbar-Slot (0 = Kompass)
        self.menu_open = False              # ist gerade das Server-Kisten-Menue offen?
        self.menu_servers: list[dict] = []  # DB-Liste, aus der das offene Menue gebaut wurde


def _extract_setup_packets(records: list, play_login: int) -> list[bytes]:
    """Kleine PLAY-Setup-Pakete (Recipes/Commands/Mod-Sync) aus einem Replay ziehen -
    alles zwischen PLAY-Login und erstem Chunk ausser den riesigen Ballast-Kanaelen."""
    first_chunk = len(records)
    for i in range(play_login, len(records)):
        if records[i].to_client and records[i].packet_id == pl.PLAY_CB_CHUNK_DATA:
            first_chunk = i
            break
    setup: list[bytes] = []
    for i in range(play_login + 1, first_chunk):
        r = records[i]
        if not r.to_client:
            continue
        if r.packet_id in _SETUP_PACKET_IDS:
            setup.append(r.raw)
        elif r.packet_id == _CB_CUSTOM_PAYLOAD:
            _p, body, _c = mcd.try_read_packet(r.raw)
            try:
                channel, _o = mp._read_string(body, 0)
            except Exception:  # noqa: BLE001
                continue
            if channel not in _SKIP_PAYLOAD_CHANNELS:
                setup.append(r.raw)
    return setup


def _load_profile(replay_path: str) -> dict:
    """Ein Replay in ein Config-Profil laden: Config-Steps, PLAY-Login, Setup-Pakete,
    Self-Entity-ID. Pro Client-Typ (modded/vanilla) einmal beim Start gebaut."""
    records = rp.load_replay_file(replay_path)
    play_login = rp.find_play_login(records)
    if play_login >= len(records):
        raise ValueError(f"kein PLAY-Login (0x2B) im Replay {replay_path}")
    config_start = rp.find_config_start(records)
    login_raw = records[play_login].raw
    _pid, body, _c = mcd.try_read_packet(login_raw)
    self_eid = struct.unpack_from(">i", body, 0)[0]
    return {
        "config_steps": rp.build_steps(records[:play_login], config_start),
        "login_raw": login_raw,
        "setup_packets": _extract_setup_packets(records, play_login),
        "self_eid": self_eid,
    }


class Hub:
    def __init__(self, replay_path: str, vanilla_replay_path: str | None = None):
        # MODDED-Profil (Pflicht). Backward-compat-Attribute zeigen darauf.
        self.modded = _load_profile(replay_path)
        self.config_steps = self.modded["config_steps"]
        self.login_raw = self.modded["login_raw"]
        self.setup_packets = self.modded["setup_packets"]
        self._self_eid = self.modded["self_eid"]
        # VANILLA-Profil (optional; ohne Capture bleibt der Hub rein modded).
        self.vanilla: dict | None = None
        if vanilla_replay_path:
            try:
                self.vanilla = _load_profile(vanilla_replay_path)
            except Exception as exc:  # noqa: BLE001
                print(f"[hub] Vanilla-Replay {vanilla_replay_path} nicht ladbar: {exc!r} -> nur modded")
        # Gemeinsame Vanilla-Welt (fuer BEIDE Client-Typen identisch -> sie begegnen sich).
        self.platform_packets = self._build_platform()

        self.lock = threading.Lock()
        self.players: dict = {}
        self._conn_ctr = 0
        base = max(self._self_eid, self.vanilla["self_eid"] if self.vanilla else 0)
        self._eid_ctr = max(1000, base + 1000)

        # Virtuellen Bot ins Roster legen (nutzt dieselbe Maschinerie wie echte Spieler).
        self._eid_ctr += 1
        self.bot = _Session(_BOT_KEY, None, self._eid_ctr, b"MCSMHB-BOT".ljust(16, b"\x00"),
                            "Lobby-Bot", 8.5, 64.0, 13.5, yaw=180.0)
        self.players[_BOT_KEY] = self.bot

    def _pick_profile(self, server_address: str | None, force_kind: str | None = None) -> dict:
        """Config-Profil waehlen. ``force_kind`` (vom Listener-Port gesetzt) hat Vorrang:
        'vanilla' -> Vanilla-Profil, 'modded' -> Modpack. Ohne force: per Handshake-Alias
        (vanlobby.<domain> -> vanilla) - fuer Standalone-Tests ohne Zweitport."""
        if force_kind == "vanilla" and self.vanilla is not None:
            return self.vanilla
        if force_kind == "modded":
            return self.modded
        host = (server_address or "").split("\x00", 1)[0].strip().rstrip(".").lower()
        alias = host.split(".", 1)[0] if host else ""
        if self.vanilla is not None:
            from app.services import gateway_service
            if alias == gateway_service.HUB_VANILLA_ALIAS:
                return self.vanilla
        return self.modded

    # ------------------------------------------------------------------ #
    # Aufbau
    # ------------------------------------------------------------------ #
    def _build_platform(self) -> list[bytes]:
        pkts = [pl.build_set_center_chunk(0, 0), pl.build_chunk_batch_start()]
        n = 0
        for cx in range(-_GRID_RADIUS, _GRID_RADIUS + 1):
            for cz in range(-_GRID_RADIUS, _GRID_RADIUS + 1):
                pkts.append(pl.build_flat_chunk(cx, cz, floor_section_index=_FLOOR_SECTION))
                n += 1
        pkts.append(pl.build_chunk_batch_finished(n))
        return pkts

    # ------------------------------------------------------------------ #
    # Senden / Broadcast (thread-safe)
    # ------------------------------------------------------------------ #
    def _send(self, session: _Session, data: bytes) -> None:
        if session.sock is None:
            return
        try:
            with session.send_lock:
                session.sock.sendall(data)
        except OSError:
            session.alive = False

    def _broadcast(self, data: bytes, exclude: _Session | None = None) -> None:
        with self.lock:
            targets = [s for s in self.players.values()
                       if s.sock is not None and s is not exclude and s.alive]
        for s in targets:
            self._send(s, data)

    def _broadcast_many(self, packets: list[bytes], exclude: _Session | None = None) -> None:
        for p in packets:
            self._broadcast(p, exclude=exclude)

    # ------------------------------------------------------------------ #
    # Spawn / Despawn (Reihenfolge: Info-Update -> Spawn -> Head)
    # ------------------------------------------------------------------ #
    @staticmethod
    def _spawn_packets(s: _Session) -> list[bytes]:
        return [
            pl.build_player_info_update(s.uuid16, s.name),
            pl.build_add_entity(s.eid, s.uuid16, s.x, s.y, s.z, yaw=s.yaw, head_yaw=s.yaw),
            pl.build_head_rotation(s.eid, s.yaw),
        ]

    @staticmethod
    def _despawn_packets(s: _Session) -> list[bytes]:
        return [pl.build_remove_entities([s.eid]), pl.build_player_info_remove([s.uuid16])]

    # ------------------------------------------------------------------ #
    # Bot-Animation (eigener Thread)
    # ------------------------------------------------------------------ #
    def animate_bot(self) -> None:
        t0 = time.monotonic()
        bx, by, bz = self.bot.x, self.bot.y, self.bot.z
        while True:
            time.sleep(_BOT_TICK)
            now = time.monotonic()
            ang = (now - t0) * 1.2
            nx = 8.5 + 3.0 * math.sin(ang)
            ny, nz = 64.0, 13.5
            yaw = 270.0 if math.cos(ang) >= 0 else 90.0
            self.bot.x, self.bot.y, self.bot.z, self.bot.yaw = nx, ny, nz, yaw
            self._broadcast(pl.build_entity_move_rot(self.bot.eid, bx, by, bz, nx, ny, nz,
                                                     yaw=yaw, on_ground=True))
            self._broadcast(pl.build_head_rotation(self.bot.eid, yaw))
            bx, by, bz = nx, ny, nz

    # ------------------------------------------------------------------ #
    # Serverlisten-Ping + Whitelist
    # ------------------------------------------------------------------ #
    def _handle_status(self, reader: "_Reader", sock: socket.socket, hs) -> None:
        """Serverlisten-Ping beantworten (MOTD, echte Spielerzahl, Version).

        Ohne das erschiene der Hub gar nicht in der Multiplayer-Liste (frueher
        wurde der Status-Ping kommentarlos verworfen).
        """
        try:
            cfg = app_setting_service.get_hub_config_runtime()
        except Exception:  # noqa: BLE001 - Ping darf nie den Hub stoeren
            cfg = {"name": "Universal-Lobby", "motd": "Universal-Lobby", "max_players": 100}
        try:
            reader.read_packet(3.0)  # Status Request (0x00, leer)
            online = len([s for s in self.players.values() if s.alive and s.sock is not None])
            status = mp.build_status_json(
                motd=cfg["motd"],
                version_name=cfg["name"],
                protocol_version=hs.protocol_version,
                players_online=online,
                players_max=cfg["max_players"],
            )
            sock.sendall(mp.build_status_response_packet(status))
            pid, payload = reader.read_packet(3.0)  # optionaler Ping (0x01 + long)
            if pid == 0x01 and len(payload) >= 8:
                sock.sendall(mp.build_pong_packet(int.from_bytes(payload[:8], "big", signed=True)))
        except (OSError, ConnectionError):
            pass

    def _whitelist_ok(self, username: str) -> bool:
        """True, wenn der Spieler beitreten darf (Whitelist aus -> immer True)."""
        try:
            cfg = app_setting_service.get_hub_config_runtime()
        except Exception:  # noqa: BLE001 - im Zweifel niemanden aussperren
            return True
        if not cfg.get("whitelist_enabled"):
            return True
        return username.strip().lower() in cfg.get("whitelist", set())

    # ------------------------------------------------------------------ #
    # Verbindungs-Handler (eigener Thread pro Client)
    # ------------------------------------------------------------------ #
    def handle(self, sock: socket.socket, addr, force_kind: str | None = None) -> None:
        session = None
        try:
            reader = _Reader(sock)
            hs = reader.read_handshake()
            if hs.next_state == mp.NEXT_STATE_STATUS:
                self._handle_status(reader, sock, hs)
                return
            pid, payload = reader.read_packet()
            if pid != mcd.LOGIN_START:
                return
            username, uuid16_login = mcd.parse_login_start(payload)
            username = username or "Spieler"
            # Whitelist (optional) VOR dem Login-Success pruefen - danach ist der
            # Spieler bereits admitted.
            if not self._whitelist_ok(username):
                sock.sendall(mp.build_login_disconnect_packet(
                    "Du stehst nicht auf der Whitelist der Universal-Lobby."))
                return
            sock.sendall(mcd.build_login_success(uuid16_login, username, hs.protocol_version))
            pid, _ = reader.read_packet()
            if pid != mcd.LOGIN_ACK:
                return
            profile = self._pick_profile(hs.server_address, force_kind)
            kind = "vanilla" if profile is self.vanilla else "modded"
            print(f"[hub] {addr} Login ok ({username}, {kind}). Spiele Config-Phase ab ...")

            # --- Config-Phase abspielen (der passende Client-Typ kommt durch die Aushandlung) ---
            for step in profile["config_steps"]:
                if step.send:
                    for raw in step.send:
                        sock.sendall(raw)
                else:
                    for _ in range(step.wait):
                        try:
                            reader.read_packet(_CONFIG_WAIT_TIMEOUT)
                        except (OSError, ConnectionError):
                            break

            # --- PLAY: mitgeschnittener Login (korrekte Registry) + Setup + eigene Welt ---
            sock.sendall(profile["login_raw"])
            # Rezepte/Commands/Advancements mit-abspielen -> JEI startet sauber (kein Crash).
            for p in profile["setup_packets"]:
                sock.sendall(p)
            # Leeres declare_recipes -> feuert RecipesUpdatedEvent, damit JEI schon beim Join
            # initialisiert statt beim 1. Inventar-Oeffnen 30-40s einzufrieren (siehe mc_play).
            sock.sendall(pl.build_declare_recipes_empty())
            for p in self.platform_packets:
                sock.sendall(p)
            sx, sy, sz = _SPAWN
            sock.sendall(pl.build_sync_position(sx, sy, sz, teleport_id=1))
            sock.sendall(pl.build_set_default_spawn(int(sx), int(sy), int(sz)))
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_WAIT_FOR_CHUNKS, 0.0))
            # Interaktions-Lock: Adventure-Mode (Welt unzerstoerbar, kein Fliegen/Bauen).
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_CHANGE_GAMEMODE, float(pl.GAMEMODE_ADVENTURE)))
            # Kompass in Hotbar-Slot 0 -> Rechtsklick oeffnet das Server-Auswahl-Menue.
            sock.sendall(pl.build_set_slot(0, pl.INV_HOTBAR0_SLOT,
                pl.encode_slot(pl.ITEM_COMPASS, custom_name="Server-Menü (Rechtsklick)")))
            sock.sendall(pl.build_set_held_item(0))

            # --- Ins Roster aufnehmen + gegenseitig sichtbar machen ---
            with self.lock:
                self._conn_ctr += 1
                conn_id = self._conn_ctr
                self._eid_ctr += 1
                eid = self._eid_ctr
                uuid16 = (b"MCSMHB" + struct.pack(">Q", conn_id)).ljust(16, b"\x00")
                session = _Session(conn_id, sock, eid, uuid16, username, sx, sy, sz)
                existing = [s for s in self.players.values() if s.alive]  # Bot + andere Spieler
                self.players[conn_id] = session

            # bestehende Spieler dem Neuling zeigen ...
            for s in existing:
                for pkt in self._spawn_packets(s):
                    sock.sendall(pkt)
            # ... und den Neuling allen anderen.
            self._broadcast_many(self._spawn_packets(session), exclude=session)

            online = len(existing) + 1
            self._send(session, pl.build_system_chat(mcd._nbt_text_component(
                f"Willkommen in der Universal-Lobby, {username}! ({online} online)")))
            self._broadcast(pl.build_system_chat(mcd._nbt_text_component(
                f"{username} ist der Lobby beigetreten.")), exclude=session)
            print(f"[hub] {username} beigetreten (eid={eid}, online={online}).")

            # --- Hauptschleife: Bewegung/Chat lesen + broadcasten, Keep-Alive senden ---
            sock.settimeout(_READ_TIMEOUT)
            last_ka = time.monotonic()
            while session.alive:
                now = time.monotonic()
                if now - last_ka >= _KEEPALIVE_INTERVAL:
                    try:
                        sock.sendall(pl.build_keep_alive(0))
                    except OSError:
                        break
                    last_ka = now
                got = mcd.try_read_packet(bytes(reader.buf))
                if got is not None:
                    p_id, fields, consumed = got
                    del reader.buf[:consumed]
                    self._dispatch(session, p_id, fields)
                    continue
                try:
                    chunk = sock.recv(16384)
                except socket.timeout:
                    continue
                except (OSError, mp.ProtocolError):
                    break
                if not chunk:
                    break
                reader.buf.extend(chunk)
        except (OSError, ConnectionError, mp.ProtocolError) as exc:
            print(f"[hub] {addr} Verbindungsfehler: {exc!r}")
        except Exception as exc:  # noqa: BLE001
            print(f"[hub] {addr} Fehler: {exc!r}")
        finally:
            if session is not None:
                session.alive = False
                with self.lock:
                    self.players.pop(session.conn_id, None)
                self._broadcast_many(self._despawn_packets(session))
                self._broadcast(pl.build_system_chat(mcd._nbt_text_component(
                    f"{session.name} hat die Lobby verlassen.")))
                print(f"[hub] {session.name} getrennt.")
            try:
                sock.close()
            except OSError:
                pass

    # ------------------------------------------------------------------ #
    # Serverbound-Dispatch
    # ------------------------------------------------------------------ #
    def _dispatch(self, session: _Session, pid: int, fields: bytes) -> None:
        if pid == _SB_SET_POS and len(fields) >= 24:
            x, y, z = struct.unpack_from(">ddd", fields, 0)
            self._on_move(session, x, y, z, session.yaw, session.pitch)
        elif pid == _SB_SET_POS_ROT and len(fields) >= 32:
            x, y, z = struct.unpack_from(">ddd", fields, 0)
            yaw, pitch = struct.unpack_from(">ff", fields, 24)
            self._on_move(session, x, y, z, yaw, pitch)
        elif pid == _SB_SET_ROT and len(fields) >= 8:
            yaw, pitch = struct.unpack_from(">ff", fields, 0)
            self._on_move(session, session.x, session.y, session.z, yaw, pitch)
        elif pid == _SB_CHAT:
            try:
                text, _ = mp._read_string(fields, 0)
            except Exception:  # noqa: BLE001
                return
            text = text.strip()
            if text:
                self._broadcast(pl.build_system_chat(mcd._nbt_text_component(
                    f"<{session.name}> {text}")))
        elif pid == _SB_HELD_ITEM and len(fields) >= 2:
            session.held_slot = struct.unpack_from(">h", fields, 0)[0]
        elif pid in (_SB_USE_ITEM, _SB_USE_ITEM_ON):
            # Rechtsklick mit dem Kompass (Hotbar-Slot 0) -> Server-Menue oeffnen.
            if session.held_slot == 0 and not session.menu_open:
                self._open_menu(session)
        elif pid == _SB_CONTAINER_CLICK:
            self._on_menu_click(session, fields)
        elif pid == _SB_CONTAINER_CLOSE:
            session.menu_open = False
        # 0x00 Confirm Teleport, 0x18 Keep-Alive, 0x04 Command etc.: ignorieren.

    # ------------------------------------------------------------------ #
    # Server-Auswahl-Menue (Kompass -> Kiste -> Transfer 0x73)
    # ------------------------------------------------------------------ #
    @staticmethod
    def _menu_slots(servers: list[dict]) -> list[bytes]:
        """63 Slots fuer generic_9x3: 27 Container-Slots (Server) + 36 Spieler-Inv.
        Server oben (Slot 0..N-1), Kompass gespiegelt in der Menue-Hotbar (Slot 54)."""
        slots = [pl.encode_slot_empty()] * (27 + 36)
        for i, srv in enumerate(servers[:27]):
            item = _MATERIAL_ITEM.get(srv.get("material", ""), pl.ITEM_GRASS_BLOCK)
            name = _plain(srv.get("display") or srv.get("key") or "?")
            slots[i] = pl.encode_slot(item, custom_name=name)
        slots[27 + 27] = pl.encode_slot(pl.ITEM_COMPASS, custom_name="Server-Menü")
        return slots

    def _open_menu(self, session: _Session) -> None:
        session.menu_servers = _menu_servers()          # frische DB-Liste
        session.menu_open = True
        self._send(session, pl.build_open_screen(_MENU_WINDOW, pl.MENU_GENERIC_9X3, "Server auswählen"))
        self._send(session, pl.build_container_content(_MENU_WINDOW, self._menu_slots(session.menu_servers)))

    def _on_menu_click(self, session: _Session, fields: bytes) -> None:
        """Klick im Server-Menue -> Server aus der beim Oeffnen gemerkten DB-Liste
        auslesen und per Transfer (0x73) ueber das Gateway dorthin schicken."""
        if not session.menu_open or len(fields) < 1 or fields[0] != _MENU_WINDOW:
            return
        try:
            _state, off = mp.read_varint(fields, 1)          # stateId ueberspringen
            slot = struct.unpack_from(">h", fields, off)[0]  # geklickter Slot (i16)
        except Exception:  # noqa: BLE001
            return
        if 0 <= slot < len(session.menu_servers):
            srv = session.menu_servers[slot]
            host, port = srv["host"], int(srv["port"])
            session.menu_open = False
            self._send(session, pl.build_close_container(_MENU_WINDOW))
            self._send(session, pl.build_transfer(host, port))
            print(f"[hub] {session.name} -> Transfer zu {host}:{port} ({_plain(srv.get('display',''))})")

    def _on_move(self, session: _Session, nx, ny, nz, yaw, pitch) -> None:
        ox, oy, oz = session.x, session.y, session.z
        session.x, session.y, session.z, session.yaw, session.pitch = nx, ny, nz, yaw, pitch
        self._broadcast(pl.build_entity_move_rot(session.eid, ox, oy, oz, nx, ny, nz,
                                                 yaw=yaw, pitch=pitch, on_ground=True),
                        exclude=session)
        self._broadcast(pl.build_head_rotation(session.eid, yaw), exclude=session)


def serve(port: int, replay_path: str, vanilla_replay_path: str | None = None) -> None:
    hub = Hub(replay_path, vanilla_replay_path)
    profile = "modded+vanilla" if hub.vanilla else "nur modded"
    print(f"[hub] Replay geladen ({profile}): {len(hub.modded['config_steps'])} Config-Steps, "
          f"self-eid={hub.modded['self_eid']}.")
    threading.Thread(target=hub.animate_bot, daemon=True).start()

    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("0.0.0.0", port))
    listener.listen(16)
    print(f"[hub] Universal-Lobby wartet an :{port} ...")
    while True:
        conn, addr = listener.accept()
        print(f"\n[hub] Client {addr} verbunden.")
        threading.Thread(target=hub.handle, args=(conn, addr), daemon=True).start()
