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
import socket
import struct
import threading
import time

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

_BOT_KEY = 0                  # reservierter Roster-Key fuer den virtuellen Bot

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


class Hub:
    def __init__(self, replay_path: str):
        self.records = rp.load_replay_file(replay_path)
        self.config_start = rp.find_config_start(self.records)
        self.play_login = rp.find_play_login(self.records)
        if self.play_login >= len(self.records):
            raise ValueError("kein PLAY-Login (0x2B) im Replay gefunden")
        self.login_raw = self.records[self.play_login].raw
        # Self-Entity-ID des Clients (aus dem Login) - unsere Entity-IDs muessen != sein.
        _pid, body, _c = mcd.try_read_packet(self.login_raw)
        self._self_eid = struct.unpack_from(">i", body, 0)[0]
        # Config-Steps + Plattform-Pakete EINMAL vorbauen (records prozessweit geteilt).
        self.config_steps = rp.build_steps(self.records[: self.play_login], self.config_start)
        self.platform_packets = self._build_platform()
        # Kleine PLAY-Setup-Pakete (Recipes/Commands/...) aus dem Capture extrahieren.
        first_chunk = len(self.records)
        for i in range(self.play_login, len(self.records)):
            if self.records[i].to_client and self.records[i].packet_id == pl.PLAY_CB_CHUNK_DATA:
                first_chunk = i
                break
        setup = []
        for i in range(self.play_login + 1, first_chunk):
            r = self.records[i]
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
        self.setup_packets = setup

        self.lock = threading.Lock()
        self.players: dict = {}
        self._conn_ctr = 0
        self._eid_ctr = max(1000, self._self_eid + 1000)

        # Virtuellen Bot ins Roster legen (nutzt dieselbe Maschinerie wie echte Spieler).
        self._eid_ctr += 1
        self.bot = _Session(_BOT_KEY, None, self._eid_ctr, b"MCSMHB-BOT".ljust(16, b"\x00"),
                            "Lobby-Bot", 8.5, 64.0, 13.5, yaw=180.0)
        self.players[_BOT_KEY] = self.bot

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
    # Verbindungs-Handler (eigener Thread pro Client)
    # ------------------------------------------------------------------ #
    def handle(self, sock: socket.socket, addr) -> None:
        session = None
        try:
            reader = _Reader(sock)
            hs = reader.read_handshake()
            if hs.next_state == mp.NEXT_STATE_STATUS:
                return
            pid, payload = reader.read_packet()
            if pid != mcd.LOGIN_START:
                return
            username, uuid16_login = mcd.parse_login_start(payload)
            username = username or "Spieler"
            sock.sendall(mcd.build_login_success(uuid16_login, username, hs.protocol_version))
            pid, _ = reader.read_packet()
            if pid != mcd.LOGIN_ACK:
                return
            print(f"[hub] {addr} Login ok ({username}). Spiele Config-Phase ab ...")

            # --- Config-Phase abspielen (modded Client kommt durch die Mod-Pruefung) ---
            for step in self.config_steps:
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
            sock.sendall(self.login_raw)
            # Rezepte/Commands/Advancements mit-abspielen -> JEI startet sauber (kein Crash).
            for p in self.setup_packets:
                sock.sendall(p)
            for p in self.platform_packets:
                sock.sendall(p)
            sx, sy, sz = _SPAWN
            sock.sendall(pl.build_sync_position(sx, sy, sz, teleport_id=1))
            sock.sendall(pl.build_set_default_spawn(int(sx), int(sy), int(sz)))
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_WAIT_FOR_CHUNKS, 0.0))
            # Interaktions-Lock: Adventure-Mode (Welt unzerstoerbar, kein Fliegen/Bauen).
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_CHANGE_GAMEMODE, float(pl.GAMEMODE_ADVENTURE)))

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
        # 0x00 Confirm Teleport, 0x18 Keep-Alive, 0x04 Command etc.: ignorieren.

    def _on_move(self, session: _Session, nx, ny, nz, yaw, pitch) -> None:
        ox, oy, oz = session.x, session.y, session.z
        session.x, session.y, session.z, session.yaw, session.pitch = nx, ny, nz, yaw, pitch
        self._broadcast(pl.build_entity_move_rot(session.eid, ox, oy, oz, nx, ny, nz,
                                                 yaw=yaw, pitch=pitch, on_ground=True),
                        exclude=session)
        self._broadcast(pl.build_head_rotation(session.eid, yaw), exclude=session)


def serve(port: int, replay_path: str) -> None:
    hub = Hub(replay_path)
    s2c = sum(1 for r in hub.records if r.to_client)
    print(f"[hub] {len(hub.records)} Pakete geladen ({s2c} S->C), Config-Start #{hub.config_start}, "
          f"PLAY-Login #{hub.play_login}, self-eid={hub._self_eid}.")
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
