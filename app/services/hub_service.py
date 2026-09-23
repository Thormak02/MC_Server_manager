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
from dataclasses import dataclass

from app.services import app_setting_service
from app.services import mc_dispatch as mcd
from app.services import mc_play as pl
from app.services import mc_protocol as mp
from app.services import replay_service as rp

# --- Welt-/Timing-Konstanten (spiegeln die in Phase 1 bewiesenen Werte) ---
_GRID_RADIUS = 4               # 9x9-Chunk-Grid -> ~112x112 sichtbare Plattform
_FLOOR_SECTION = 7             # Boden y 48..63, Spawn auf y=64
_SPAWN = (8.5, 64.0, 8.5)
_BAKE_RADIUS = 6               # 13x13-Chunk-Fenster (~208x208) um den Lobby-Spawn (gebackene Welt)
_KEEPALIVE_INTERVAL = 10.0
_READ_TIMEOUT = 0.5
_CONFIG_WAIT_TIMEOUT = 6.0
_BOT_TICK = 0.1
# Rueckfrage bei unpassendem Client: so lange gilt ein zweiter Klick als "trotzdem".
_CONFIRM_TTL = 60.0

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

# Bukkit-Legacy-Farbcode -> Vanilla-Farbname (fuer mc_play-TextComponents).
_LEGACY_COLORS = {
    "0": "black", "1": "dark_blue", "2": "dark_green", "3": "dark_aqua",
    "4": "dark_red", "5": "dark_purple", "6": "gold", "7": "gray",
    "8": "dark_gray", "9": "blue", "a": "green", "b": "aqua",
    "c": "red", "d": "light_purple", "e": "yellow", "f": "white",
}


def _plain(text: str) -> str:
    """Legacy-&-Farbcodes entfernen (der Hub nutzt Component-custom_name, kein &)."""
    return _COLOR_CODE.sub("", text or "").strip()


def _legacy_runs(text: str) -> list[tuple[str, str | None]]:
    """Bukkit-Legacy-String (``&a...&7...``) -> Liste von (Text, Farbname|None)-Runs, wie
    mc_play-TextComponents sie erwarten. So sieht das Kompass-Menue exakt wie die Bukkit-Lobby
    aus (gruener Name, grauer Zusatz, farbige Lore). Format-Codes (&l/&o/&r ...) setzen die
    Farbe zurueck bzw. werden ignoriert - der Hub rendert nur Farben, keine Extra-Stile."""
    s = text or ""
    runs: list[tuple[str, str | None]] = []
    color: str | None = None
    buf: list[str] = []
    i = 0
    while i < len(s):
        if s[i] in "&§" and i + 1 < len(s):
            code = s[i + 1].lower()
            if code in _LEGACY_COLORS:
                if buf:
                    runs.append(("".join(buf), color)); buf = []
                color = _LEGACY_COLORS[code]; i += 2; continue
            if code == "r":
                if buf:
                    runs.append(("".join(buf), color)); buf = []
                color = None; i += 2; continue
            i += 2; continue                      # &l/&o/&m/&n/&k: ignorieren
        buf.append(s[i]); i += 1
    if buf:
        runs.append(("".join(buf), color))
    return runs or [("", None)]


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


@dataclass(frozen=True)
class _Verdict:
    """Ersatz-Urteil, wenn der Manager keines liefert - Felder wie lobby_service.JoinVerdict.

    Die Voreinstellung ist bewusst "erlaubt": fehlt das Urteil, wird durchgelassen.
    """

    ok: bool = True
    reason: str = ""
    confirm: bool = False
    note: str = ""
    code: str = "ok"


_VERDICT_OPEN = _Verdict()


def _client_info(session):
    """Client-Fingerabdruck der Session als join_match_service.ClientInfo (None bei Fehler)."""
    try:
        from app.services import join_match_service as jm

        return jm.ClientInfo(brand=getattr(session, "brand", "") or "",
                             mods=getattr(session, "mods", None) or frozenset(),
                             source="hub")
    except Exception:  # noqa: BLE001
        return None


def _join_verdict(server_id: int, session, *, override: bool):
    """Vorab-Urteil des Managers zu einem Serverwechsel holen - FAIL-OPEN.

    Der Client-Fingerabdruck geht mit, kann aber hoechstens eine Rueckfrage ausloesen:
    der Brand ist ein freier String vom Client und faelschbar.
    """
    try:
        from app.services import lobby_service

        verdict = lobby_service.evaluate_join_by_id(
            int(server_id), session.name, client=_client_info(session), override=bool(override))
        # Unbrauchbares Urteil (None, altes Tupel) heisst: kein Urteil -> durchlassen.
        return verdict if hasattr(verdict, "ok") else _VERDICT_OPEN
    except Exception:  # noqa: BLE001 - eine kaputte Pruefung darf den Wechsel nie blockieren
        return _VERDICT_OPEN


def _menu_fits(servers: list[dict], session) -> dict:
    """Server-ID -> Fit fuer die Eintraege, die NICHT zum Client passen (level "confirm").

    Nur Treffer landen im Dict; ein fehlender Eintrag heisst "passt". Fehler werden
    geschluckt - ein Marker ist Kosmetik, das Menue muss trotzdem aufgehen.
    """
    fits: dict = {}
    client = _client_info(session)
    if client is None or (not client.brand and not client.mods):
        # Ohne jedes Client-Merkmal kann die Pruefung nur "passt" sagen - dann sparen wir
        # uns bis zu 27 DB-Abfragen pro Menue-Oeffnung.
        return fits
    try:
        from app.services import lobby_service

        # Gebuendelt: EINE DB-Session fuers ganze Menue. Nur die sichtbaren Slots.
        ids = [srv.get("id") for srv in servers[:27] if srv.get("id") is not None]
        fits = lobby_service.evaluate_fits_by_ids(ids, client)
    except Exception as exc:  # noqa: BLE001
        print(f"[hub] Menue-Marker nicht ermittelbar: {exc!r}")
    return fits

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


def _sniff_client(fields: bytes) -> tuple[str, frozenset]:
    """Ein serverbound Config-Paket auf Client-Merkmale absuchen -> (brand, mods).

    Leere Rueckgabewerte heissen "dieses Paket sagt nichts". Gesucht wird nach KANAL,
    nicht nach Paket-ID oder Position: der Kanalname ist ueber Protokollversionen und
    Loader hinweg stabil, die IDs und die Paketreihenfolge sind es nicht.
    """
    try:
        channel, data = mcd.parse_custom_payload(fields)
    except Exception:  # noqa: BLE001 - kein custom_payload -> einfach nichts zu holen
        return "", frozenset()
    if channel == mcd.MINECRAFT_BRAND:
        return mcd.parse_brand(data).strip()[:64], frozenset()
    if channel == mcd.NEOFORGE_REGISTER:
        return "", frozenset(mcd.extract_mod_namespaces(data))
    return "", frozenset()


def _play_config_phase(sock: socket.socket, reader: "_Reader", steps: list) -> tuple[str, frozenset]:
    """Aufgezeichnete Config-Phase abspielen und dabei den Client-Fingerabdruck mitlesen.

    Der Client schickt Brand und (bei NeoForge) sein Mod-Manifest ohnehin durch diese
    Warteschleife - frueher wurde der Rueckgabewert von read_packet nur weggeworfen.

    NICHT VERHANDELBAR: Anzahl und Reihenfolge der read_packet-Aufrufe bleiben exakt wie
    vorher - nichts zusaetzlich lesen, nie frueher abbrechen. Sonst geriete die Config-Phase
    bei Clients, deren Paketfolge vom Capture abweicht, aus dem Tritt und jeder Join haengt
    sekundenlang im _CONFIG_WAIT_TIMEOUT.
    """
    brand = ""
    mods: frozenset = frozenset()
    for step in steps:
        if step.send:
            for raw in step.send:
                sock.sendall(raw)
        else:
            for _ in range(step.wait):
                try:
                    got = reader.read_packet(_CONFIG_WAIT_TIMEOUT)
                except (OSError, ConnectionError):
                    break
                try:
                    found_brand, found_mods = _sniff_client(got[1])
                    if found_brand:
                        brand = found_brand
                    if found_mods:
                        # Vereinigen statt ersetzen: NeoForge darf sein Manifest splitten.
                        mods = mods | found_mods
                except Exception:  # noqa: BLE001 - Mitlesen darf die Config-Phase nie stoeren
                    pass
    return brand, mods


class _Session:
    """Ein Spieler im Hub. ``sock is None`` => virtueller Bot (empfaengt nichts)."""

    def __init__(self, conn_id, sock, eid, uuid16, name, x, y, z, yaw=0.0, pitch=0.0,
                 textures="", textures_sig=""):
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
        self.textures = textures          # Base64-Skin-Textur (Bridge-Avatare echter Spieler)
        self.textures_sig = textures_sig  # Mojang-Signatur (leer = unsigniert)
        self.alive = True
        self.send_lock = threading.Lock()   # serialisiert Writes auf DIESES Socket
        self.held_slot = 0                  # aktueller Hotbar-Slot (0 = Kompass)
        self.menu_open = False              # ist gerade das Server-Kisten-Menue offen?
        self.menu_servers: list[dict] = []  # DB-Liste, aus der das offene Menue gebaut wurde
        # Client-Fingerabdruck aus der Config-Phase (siehe _play_config_phase). Beides darf
        # leer bleiben - leer heisst ueberall "keine Aussage moeglich", nie "passt nicht".
        self.brand = ""                     # "neoforge"/"fabric"/... , roh vom Client
        self.mods: frozenset = frozenset()  # networked Mod-Namespaces (nur NeoForge/Forge)
        # Server-ID -> Zeitpunkt der Rueckfrage (time.monotonic); der zweite Klick ueberstimmt.
        self.pending_confirm: dict[int, float] = {}
        self.menu_fits: dict = {}      # beim Oeffnen ermittelte Marker (zum Neuzeichnen)


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
    def __init__(self, replay_path: str, vanilla_replay_path: str | None = None,
                 pack_replays: dict[int, str] | None = None,
                 lobby_world_dir: str | None = None,
                 bake_radius: int = _BAKE_RADIUS):
        # MODDED-Profil (Pflicht, Default/Fallback). Backward-compat-Attribute zeigen darauf.
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
        # PER-PACK-Profile: server_id -> Profil. Auswahl via Path A (Dispatcher taggt das
        # erkannte Pack in den Hostnamen modlobby-<server_id>). Kaputte Replays -> ueberspringen.
        self.pack_profiles: dict[int, dict] = {}
        for sid, path in (pack_replays or {}).items():
            try:
                self.pack_profiles[int(sid)] = _load_profile(path)
            except Exception as exc:  # noqa: BLE001
                print(f"[hub] Pack-Replay {path} (server {sid}) nicht ladbar: {exc!r} -> uebersprungen")
        # Gemeinsame Vanilla-Welt (fuer ALLE Client-Typen identisch -> sie begegnen sich).
        self.platform_packets = self._build_platform()
        # Gebackene Lobby-Welt (Option B): die LIVE Vanilla-Lobby-Welt nativ als 1.21.1-Chunks
        # servieren. self.origin = Spawn (wohin der Spieler gesetzt wird + Bezugspunkt fuer die
        # spawn-relativen Bridge-Offsets). Ohne gesetzten /setworldspawn oder bei jedem Fehler ->
        # Fallback auf die flache Plattform (self.world_setup bleibt None, origin = _SPAWN).
        self.origin: tuple[float, float, float] = _SPAWN
        self.world_setup: list[bytes] | None = None
        if lobby_world_dir:
            self._bake_lobby_world(lobby_world_dir, bake_radius)

        self.lock = threading.Lock()
        self.players: dict = {}
        self._conn_ctr = 0
        eids = [self._self_eid]
        if self.vanilla:
            eids.append(self.vanilla["self_eid"])
        eids.extend(p["self_eid"] for p in self.pack_profiles.values())
        self._eid_ctr = max(1000, max(eids) + 1000)

        # Virtuellen Bot ins Roster legen (nutzt dieselbe Maschinerie wie echte Spieler).
        # 5 Bloecke vor dem Spawn (origin-relativ, damit er auch in der gebackenen Welt am Spawn steht).
        self._eid_ctr += 1
        bx, by, bz = self.origin
        self.bot = _Session(_BOT_KEY, None, self._eid_ctr, b"MCSMHB-BOT".ljust(16, b"\x00"),
                            "Lobby-Bot", bx, by, bz + 5.0, yaw=180.0)
        self.players[_BOT_KEY] = self.bot

        # Presence-Bridge: gespiegelte Avatare fremder Instanzen (Vanilla-Lobby).
        self.bridge: dict = {}          # fremde UUID (str) -> Bridge-Avatar-_Session
        self._bridge_attached = False
        self._bus_seq = 0               # monoton fuer publizierte Praesenz-Updates

    @staticmethod
    def _alias_of(server_address: str | None) -> str:
        """Erstes Host-Label aus dem Handshake-Address (FML-Suffix \\x00 wird gestrippt)."""
        host = (server_address or "").split("\x00", 1)[0].strip().rstrip(".").lower()
        return host.split(".", 1)[0] if host else ""

    def _modded_for_alias(self, alias: str) -> dict | None:
        """Per-Pack-Profil aus ``modlobby-<server_id>`` waehlen. Fehlt zu einem EXPLIZITEN
        Pack-Tag das Profil (Race: Capture eben fertig, Reconcile laeuft noch), NICHT still
        das Default servieren (fremdes Pack -> Registry-Kick), sondern None -> der Handler
        trennt mit klarer 'wird eingerichtet'-Meldung. Ohne Pack-Tag: Default-modded."""
        from app.services import gateway_service

        prefix = gateway_service.HUB_LOBBY_ALIAS + "-"  # z.B. "modlobby-"
        if alias.startswith(prefix):
            suffix = alias[len(prefix):]
            if suffix.isdigit():
                return self.pack_profiles.get(int(suffix))  # kann None sein -> Handler trennt
        return self.modded

    def _pick_profile(self, server_address: str | None, force_kind: str | None = None) -> dict | None:
        """Config-Profil waehlen. ``force_kind`` (vom Listener-Port gesetzt) trennt nur
        vanilla vs. modded. WELCHES Modpack kommt aus dem Host-Tag ``modlobby-<server_id>``
        (vom Dispatcher gesetzt, Path A) -> Per-Pack-Profil, sonst Default-modded.
        ``vanlobby.<domain>`` -> Vanilla."""
        if force_kind == "vanilla" and self.vanilla is not None:
            return self.vanilla
        alias = self._alias_of(server_address)
        if force_kind == "modded":
            return self._modded_for_alias(alias)
        if self.vanilla is not None:
            from app.services import gateway_service
            if alias == gateway_service.HUB_VANILLA_ALIAS:
                return self.vanilla
        return self._modded_for_alias(alias)

    def update_pack_profiles(self, pack_replays: dict[int, str] | None) -> None:
        """Per-Pack-Profile LIVE angleichen (nach einem neuen Capture) - ohne Hub-Neustart
        und ohne die verbundenen Lobby-Spieler zu trennen. Neue/geaenderte Replays laden,
        verschwundene entfernen; unladbare ueberspringen. Der Dict-Swap ist in CPython
        atomar, ``_modded_for_alias`` liest also stets ein konsistentes Dict."""
        loaded: dict[int, dict] = {}
        for sid, path in (pack_replays or {}).items():
            try:
                loaded[int(sid)] = _load_profile(path)
            except Exception as exc:  # noqa: BLE001 - kaputtes Replay -> ueberspringen
                print(f"[hub] Pack-Replay {path} (server {sid}) nicht ladbar: {exc!r} -> uebersprungen")
        with self.lock:
            self.pack_profiles = loaded

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

    def _bake_lobby_world(self, lobby_world_dir: str, bake_radius: int) -> None:
        """Live-Lobby-Welt backen und als self.world_setup (mit Batch-Rahmung) hinterlegen.
        Setzt self.origin auf den Welt-Spawn. Jeder Fehler/kein Spawn -> Plattform-Fallback."""
        try:
            from app.services import world_bake_service as wb
            baked = wb.bake_lobby_packets(lobby_world_dir, radius=bake_radius)
        except Exception as exc:  # noqa: BLE001
            print(f"[hub] Lobby-Welt-Bake fehlgeschlagen ({exc!r}) -> Plattform")
            return
        if not baked:
            print("[hub] Kein /setworldspawn in der Lobby-Welt (oder keine Chunks) -> Plattform. "
                  "In der Vanilla-Lobby /setworldspawn setzen und den Hub neu starten.")
            return
        cx, cz = baked["center_chunk"]
        setup = [pl.build_set_center_chunk(cx, cz), pl.build_chunk_batch_start()]
        setup.extend(baked["packets"])
        setup.append(pl.build_chunk_batch_finished(baked["chunk_count"]))
        self.world_setup = setup
        self.origin = baked["origin"]
        print(f"[hub] Lobby-Welt gebacken: {baked['chunk_count']} Chunks um Spawn "
              f"{baked['spawn_block']} (Chunk {baked['center_chunk']}).")

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
            pl.build_player_info_update(s.uuid16, s.name,
                                        textures=getattr(s, "textures", ""),
                                        signature=getattr(s, "textures_sig", "")),
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
    # Presence-Bridge: fremde Instanzen (Vanilla-Lobby) als Avatare spiegeln
    # ------------------------------------------------------------------ #
    def attach_bridge(self) -> None:
        """Den Hub an den Praesenz-Bus haengen (idempotent). Fremde Praesenzen werden ab
        jetzt als Fake-Avatare gerendert; lokale Spieler werden publiziert."""
        from app.services import presence_bridge_service as pb

        if self._bridge_attached:
            return
        self._bridge_attached = True
        pb.BUS.subscribe(self._on_bridge_event)
        for p in pb.BUS.snapshot(exclude_origin=pb.ORIGIN_HUB):   # bereits Bekannte einspielen
            self._bridge_add(p)
        threading.Thread(target=self._bridge_keepalive_loop, daemon=True,
                         name="hub-bridge-keepalive").start()

    def detach_bridge(self) -> None:
        from app.services import presence_bridge_service as pb

        if not self._bridge_attached:
            return
        pb.BUS.unsubscribe(self._on_bridge_event)
        self._bridge_attached = False
        with self.lock:
            for sess in list(self.bridge.values()):
                self.players.pop(sess.conn_id, None)
            self.bridge.clear()

    def _hub_bus_uuid(self, session: "_Session") -> str:
        return "hub-" + session.uuid16.hex()

    def _on_bridge_event(self, event: str, payload) -> None:
        from app.services import presence_bridge_service as pb

        try:
            if event == pb.EVENT_CHAT:
                if not isinstance(payload, dict) or payload.get("origin") == pb.ORIGIN_HUB:
                    return
                # Einheitliches Netz-Praefix wie auf der Vanilla-Seite ("[Lobby] <name> text"),
                # damit man sieht, dass die Nachricht aus dem gemeinsamen Netz kommt.
                self._broadcast(pl.build_system_chat(mcd._nbt_text_component(
                    f"[Lobby] <{payload.get('name', '?')}> {payload.get('text', '')}")))
                return
            if getattr(payload, "origin", None) == pb.ORIGIN_HUB:
                return  # eigene Spieler nicht als Avatar spiegeln
            if event == pb.EVENT_ADD:
                self._bridge_add(payload)
            elif event == pb.EVENT_UPDATE:
                self._bridge_update(payload)
            elif event == pb.EVENT_REMOVE:
                self._bridge_remove(payload)
        except Exception as exc:  # noqa: BLE001 - Bridge darf den Hub nie stoeren
            print(f"[hub] Bridge-Event {event} Fehler: {exc!r}")

    def _bridge_add(self, p) -> None:
        from app.services import presence_bridge_service as pb

        spawn_sess = None
        with self.lock:
            if p.uuid not in self.bridge:
                self._eid_ctr += 1
                # WICHTIG: Avatar-UUID NAMESPACEN ("br:"+uuid -> SHA1), damit sie NIE der echten
                # UUID eines lokalen Hub-Spielers gleicht. Sonst kollidiert ein per Velocity
                # eingeloggter Vanilla-Spieler, der DIESELBE Mojang-UUID wie ein Hub-Client hat
                # (z.B. dasselbe Konto auf beiden Clients), mit der Eigen-UUID des Betrachters ->
                # der Client rendert seine eigene UUID NICHT -> Avatar unsichtbar (einseitig!).
                # p.x/y/z sind spawn-relative Offsets der Gegenseite -> bei UNSEREM Spawn rendern.
                sess = _Session(f"br:{p.uuid}", None, self._eid_ctr,
                                pb.uuid16_from("br:" + p.uuid), p.name,
                                self.origin[0] + p.x, self.origin[1] + p.y, self.origin[2] + p.z,
                                yaw=p.yaw, pitch=p.pitch,
                                textures=getattr(p, "textures", "") or "",
                                textures_sig=getattr(p, "textures_sig", "") or "")
                self.bridge[p.uuid] = sess
                self.players[sess.conn_id] = sess
                spawn_sess = sess
        if spawn_sess is not None:   # ausserhalb des Locks broadcasten (wie bei echten Joins)
            self._broadcast_many(self._spawn_packets(spawn_sess))
        else:
            self._bridge_update(p)   # war schon da -> nur bewegen

    def _bridge_update(self, p) -> None:
        sess = self.bridge.get(p.uuid)
        if sess is None:
            self._bridge_add(p)
            return
        ox, oy, oz = sess.x, sess.y, sess.z
        nx, ny, nz = (self.origin[0] + p.x, self.origin[1] + p.y,
                      self.origin[2] + p.z)   # spawn-relativ -> lokal
        sess.x, sess.y, sess.z, sess.yaw, sess.pitch = nx, ny, nz, p.yaw, p.pitch
        self._broadcast(pl.build_entity_move_rot(sess.eid, ox, oy, oz, nx, ny, nz,
                                                 yaw=p.yaw, pitch=p.pitch, on_ground=True))
        self._broadcast(pl.build_head_rotation(sess.eid, p.head_yaw or p.yaw))

    def _bridge_remove(self, p) -> None:
        with self.lock:
            sess = self.bridge.pop(p.uuid, None)
            if sess is not None:
                self.players.pop(sess.conn_id, None)
        if sess is not None:
            self._broadcast_many(self._despawn_packets(sess))

    # Producer: lokale Hub-Spieler auf den Bus melden (fuer die Vanilla-Instanz)
    def _bridge_pub_join(self, session: "_Session") -> None:
        if not self._bridge_attached:
            return
        from app.services import presence_bridge_service as pb

        self._bus_seq += 1
        # SPAWN-RELATIV publizieren: der Bus traegt Offsets vom eigenen Lobby-Spawn, nicht
        # absolute Koordinaten. Die Gegenseite rendert bei IHREM Spawn + Offset -> Spieler
        # stehen auf beiden Seiten am selben Ort (kein Schweben, gegenseitig sichtbar), obwohl
        # Hub (Y=64-Plattform) und Vanilla-Superflat verschiedene Welt-Koordinaten haben.
        pb.BUS.upsert(pb.Presence(
            uuid=self._hub_bus_uuid(session), name=session.name, origin=pb.ORIGIN_HUB,
            x=session.x - self.origin[0], y=session.y - self.origin[1], z=session.z - self.origin[2],
            yaw=session.yaw, pitch=session.pitch, head_yaw=session.yaw, seq=self._bus_seq,
            # Skin MITSCHICKEN (von _bridge_ensure_skin async aus Mojang geholt) - sonst blieb der
            # modded Avatar auf der Vanilla-Seite immer Default, obwohl der Skin gefetcht wurde.
            textures=getattr(session, "textures", "") or "",
            textures_sig=getattr(session, "textures_sig", "") or ""))
        # Skin sicherstellen (idempotent, In-Flight-geguarded) - deckt auch den Fall ab, dass
        # die Bridge erst NACH dem Join attached: der Keepalive triggert es dann nach.
        self._bridge_ensure_skin(session)

    def _bridge_pub_move(self, session: "_Session") -> None:
        if not self._bridge_attached:
            return
        now = time.monotonic()
        if now - getattr(session, "_last_bus_pub", 0.0) < 0.1:   # ~10 Hz drosseln
            return
        session._last_bus_pub = now
        self._bridge_pub_join(session)   # gleiche upsert-Struktur aktualisiert die Praesenz

    def _bridge_pub_leave(self, session: "_Session") -> None:
        if not self._bridge_attached:
            return
        from app.services import presence_bridge_service as pb

        pb.BUS.remove(self._hub_bus_uuid(session))

    def _bridge_pub_chat(self, name: str, text: str) -> None:
        if not self._bridge_attached:
            return
        from app.services import presence_bridge_service as pb

        pb.BUS.chat(name, pb.ORIGIN_HUB, text)

    def _bridge_ensure_skin(self, session: "_Session") -> None:
        """Echten Skin eines Hub-Spielers von Mojang holen (async) und mit Skin neu publizieren.
        Hub-Login ist offline -> kein Profil-Skin; die Vanilla-Seite bekommt so den echten Skin.
        Die Vanilla-Instanz spawnt den Avatar bei Textur-Wechsel neu (Plugin-Seite).

        Wird auch aus _bridge_pub_join (Join + 10s-Keepalive) getriggert -> robust gegen Timing
        (Bridge erst NACH dem Join attached). In-Flight-Guard verhindert Thread-Spam; ein
        Fehlschlag wird nicht gecached -> spaeter erneut versucht."""
        if (not self._bridge_attached or getattr(session, "textures", "")
                or getattr(session, "_skin_fetching", False)):
            return
        session._skin_fetching = True

        def _run() -> None:
            try:
                from app.services import presence_bridge_service as pb

                value, sig = pb.fetch_mojang_skin(session.name)
                if value and session.alive and self._bridge_attached:
                    session.textures = value
                    session.textures_sig = sig
                    self._bridge_pub_join(session)   # jetzt MIT Skin publizieren
            except Exception as exc:  # noqa: BLE001
                print(f"[hub] Skin-Abruf {session.name!r} fehlgeschlagen: {exc!r}")
            finally:
                session._skin_fetching = False

        threading.Thread(target=_run, daemon=True, name="hub-skin-fetch").start()

    def _bridge_keepalive_loop(self) -> None:
        """Idle Hub-Spieler alle 10s neu publizieren -> refresht ihre Praesenz (updated), damit
        ihre Avatare auf der Vanilla-Seite nicht nach dem TTL-Sweep (30s) verschwinden."""
        while self._bridge_attached:
            time.sleep(10.0)
            if not self._bridge_attached:
                return
            try:
                with self.lock:
                    sessions = [s for s in self.players.values()
                                if s.sock is not None and s.alive]   # echte Spieler (kein Bot/Avatar)
                for s in sessions:
                    self._bridge_pub_join(s)   # Praesenz auffrischen (unbedingt, kein Throttle)
            except Exception as exc:  # noqa: BLE001
                print(f"[hub] Bridge-Keepalive Fehler: {exc!r}")

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
            # Eigenen Skin von Mojang holen (bounded, gecached) und ins LoginSuccess-Profil legen,
            # damit sich der Spieler SELBST mit echtem Skin sieht (Hub-Offline-Login = sonst Default).
            # Wird unten auch fuer die Bridge (session.textures) wiederverwendet -> nur EIN Fetch.
            login_tex = login_sig = ""
            try:
                from app.services import presence_bridge_service as _pb

                login_tex, login_sig = _pb.fetch_mojang_skin(username, timeout=2.5)
            except Exception:  # noqa: BLE001 - Skin ist optional, nie den Login blockieren
                login_tex = login_sig = ""
            sock.sendall(mcd.build_login_success(uuid16_login, username, hs.protocol_version,
                                                 textures=login_tex, signature=login_sig))
            pid, _ = reader.read_packet()
            if pid != mcd.LOGIN_ACK:
                return
            profile = self._pick_profile(hs.server_address, force_kind)
            if profile is None:
                # Explizites Pack-Tag, aber Profil (noch) nicht geladen (Capture eben fertig,
                # Reconcile laeuft) -> KEIN fremdes Default servieren, sauber vertroesten.
                sock.sendall(mcd.build_config_disconnect(
                    "Dieses Modpack wird gerade eingerichtet. Bitte in ~10 Sekunden erneut verbinden."))
                return
            kind = "vanilla" if profile is self.vanilla else "modded"
            print(f"[hub] {addr} Login ok ({username}, {kind}). Spiele Config-Phase ab ...")

            # --- Config-Phase abspielen (der passende Client-Typ kommt durch die Aushandlung) ---
            # Nebenbei faellt der Client-Fingerabdruck an (Brand + Mod-Manifest) - er
            # entscheidet spaeter, ob ein Serverwechsel eine Rueckfrage wert ist.
            client_brand, client_mods = _play_config_phase(sock, reader, profile["config_steps"])

            # --- PLAY: mitgeschnittener Login (korrekte Registry) + Setup + eigene Welt ---
            sock.sendall(profile["login_raw"])
            # Rezepte/Commands/Advancements mit-abspielen -> JEI startet sauber (kein Crash).
            for p in profile["setup_packets"]:
                sock.sendall(p)
            # Leeres declare_recipes -> feuert RecipesUpdatedEvent, damit JEI schon beim Join
            # initialisiert statt beim 1. Inventar-Oeffnen 30-40s einzufrieren (siehe mc_play).
            sock.sendall(pl.build_declare_recipes_empty())
            for p in (self.world_setup if self.world_setup is not None else self.platform_packets):
                sock.sendall(p)
            sx, sy, sz = self.origin
            sock.sendall(pl.build_sync_position(sx, sy, sz, teleport_id=1))
            sock.sendall(pl.build_set_default_spawn(int(sx), int(sy), int(sz)))
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_WAIT_FOR_CHUNKS, 0.0))
            # Interaktions-Lock: Adventure-Mode (Welt unzerstoerbar, kein Fliegen/Bauen).
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_CHANGE_GAMEMODE, float(pl.GAMEMODE_ADVENTURE)))
            # Lobby-Atmosphaere: fester Mittag (kein Tag/Nacht-Zyklus, sonst wird es nachts dunkel
            # trotz Voll-Skylight) + klares Wetter (kein abdunkelnder Regen).
            sock.sendall(pl.build_update_time(6000, -6000))
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_RAIN_LEVEL, 0.0))
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_THUNDER_LEVEL, 0.0))
            sock.sendall(pl.build_game_event(pl.GAME_EVENT_END_RAINING, 0.0))
            # Kompass in Hotbar-Slot 0 -> Rechtsklick oeffnet das Server-Auswahl-Menue.
            sock.sendall(pl.build_set_slot(0, pl.INV_HOTBAR0_SLOT, pl.encode_slot(
                pl.ITEM_COMPASS,
                name_runs=_legacy_runs("&bServer-Auswahl &7(Rechtsklick)"),
                lore_runs=[_legacy_runs("&7Rechtsklick öffnet die Serverliste")])))
            sock.sendall(pl.build_set_held_item(0))
            # Eigener player-info-Eintrag MIT Skin (UUID aus dem LoginSuccess = die, die der
            # Client fuer SICH SELBST nutzt). Moderne Clients (1.19.3+) rendern den Skin - auch den
            # eigenen - aus dem player-info, NICHT aus dem LoginSuccess-Profil -> sonst Default.
            if login_tex:
                sock.sendall(pl.build_player_info_update(
                    uuid16_login, username, textures=login_tex, signature=login_sig))

            # --- Ins Roster aufnehmen + gegenseitig sichtbar machen ---
            with self.lock:
                self._conn_ctr += 1
                conn_id = self._conn_ctr
                self._eid_ctr += 1
                eid = self._eid_ctr
                uuid16 = (b"MCSMHB" + struct.pack(">Q", conn_id)).ljust(16, b"\x00")
                # WICHTIG: session.textures NICHT vorab setzen. Sonst ueberspringt
                # _bridge_ensure_skin das Nachpublizieren -> das Plugin bleibt beim allerersten
                # (skinlosen) Spawn und rendert den Skin nie. Leer lassen -> ensure_skin holt den
                # Skin (Cache-Hit dank Login-Fetch) und re-published ihn -> Plugin RE-SPAWNT mit Skin.
                session = _Session(conn_id, sock, eid, uuid16, username, sx, sy, sz)
                session.brand, session.mods = client_brand, client_mods
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
            self._bridge_pub_join(session)   # der Vanilla-Instanz zeigen (triggert auch Skin-Fetch)

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
                self._bridge_pub_leave(session)   # Abgang der Vanilla-Instanz melden
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
                # Einheitliches [Lobby]-Praefix AUCH lokal -> jede Nachricht sieht ueberall
                # gleich aus (lokal wie ueber die Bridge), egal aus welcher Lobby sie kommt.
                self._broadcast(pl.build_system_chat(mcd._nbt_text_component(
                    f"[Lobby] <{session.name}> {text}")))
                self._bridge_pub_chat(session.name, text)   # Chat an die Vanilla-Instanz
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
        elif pid == _SB_CHAT_COMMAND:
            # Chat-Befehl (0x04): erstes Feld ist der Befehl OHNE fuehrenden Slash.
            try:
                raw, _ = mp._read_string(fields, 0)
            except Exception:  # noqa: BLE001
                return
            self._on_command(session, raw)
        # 0x00 Confirm Teleport, 0x18 Keep-Alive etc.: ignorieren.

    def _tell(self, session: _Session, text: str) -> None:
        """Systemnachricht nur an diesen Spieler."""
        self._send(session, pl.build_system_chat(mcd._nbt_text_component(text)))

    def _on_command(self, session: _Session, raw: str) -> None:
        """Befehle im Universal-Hub - Gegenstueck zum Bukkit-Plugin (MCSMLobby), damit
        /hub, /servers, /server <alias> und /lobby auch hier funktionieren (der Hub ist
        kein Bukkit-Server, kann also kein Plugin laden)."""
        parts = (raw or "").strip().split()
        if not parts:
            return
        cmd = parts[0].lstrip("/").lower()
        args = parts[1:]

        if cmd in ("hub", "servers"):
            if not session.menu_open:
                self._open_menu(session)
            return
        if cmd == "server":
            if not args:
                if not session.menu_open:
                    self._open_menu(session)
                return
            self._transfer_by_alias(session, args[0])
            return
        if cmd == "lobby":
            # Der Hub IST die Lobby - wie im Plugin nur ein Hinweis.
            self._tell(session, "Du bist bereits in der Lobby.")
            return

    def _transfer_by_alias(self, session: _Session, alias: str) -> None:
        """/server <alias> -> Transfer auf denselben Weg wie ein Klick im Kompass-Menue."""
        key = (alias or "").strip().lower()
        entries = _menu_servers()
        for srv in entries:
            if str(srv.get("key") or "").strip().lower() == key:
                self._try_transfer(session, srv)   # inkl. Vorab-Pruefung
                return
        known = ", ".join(str(e.get("key") or "") for e in entries) or "-"
        self._tell(session, f"Unbekannter Server: {alias}. Verfuegbar: {known}")

    # ------------------------------------------------------------------ #
    # Server-Auswahl-Menue (Kompass -> Kiste -> Transfer 0x73)
    # ------------------------------------------------------------------ #
    @staticmethod
    def _menu_slots(servers: list[dict], fits: dict | None = None) -> list[bytes]:
        """63 Slots fuer generic_9x3: 27 Container-Slots (Server) + 36 Spieler-Inv.
        Server oben (Slot 0..N-1), Kompass gespiegelt in der Menue-Hotbar (Slot 54).

        ``fits`` (Server-ID -> Fit) markiert die Ziele, die nicht zum Client passen: grau
        statt bunt, mit dem Grund als Lore-Zeile. Klickbar bleiben sie trotzdem - die
        Erkennung ist ein Verdacht, kein Urteil (siehe join_match_service)."""
        slots = [pl.encode_slot_empty()] * (27 + 36)
        for i, srv in enumerate(servers[:27]):
            item = _MATERIAL_ITEM.get(srv.get("material", ""), pl.ITEM_GRASS_BLOCK)
            display = srv.get("display") or srv.get("key") or "?"
            host, port = srv.get("host", ""), srv.get("port", "")
            sleeps = bool(srv.get("sleep"))
            fit = (fits or {}).get(srv.get("id"))
            # Name mehrfarbig wie in der Bukkit-Lobby: gruener Name + grauer (Typ Version);
            # passt der Client nicht, wird der ganze Name grau.
            name_runs = _legacy_runs("&7" + _plain(display)) if fit else _legacy_runs(display)
            # Lore wie im Java-Plugin: Adresse, Sleep-Hinweis (falls aktiv), Klick-Aufforderung.
            lore_runs = [_legacy_runs(f"&7{host}:{port}")]
            if fit is not None and getattr(fit, "short", ""):
                lore_runs.append(_legacy_runs(f"&e{fit.short}"))
            if sleeps:
                lore_runs.append(_legacy_runs("&dSchläft ggf. – Beitritt weckt ihn (kurz warten)"))
            lore_runs.append(_legacy_runs("&aKlick zum Verbinden"))
            slots[i] = pl.encode_slot(item, name_runs=name_runs, lore_runs=lore_runs)
        slots[27 + 27] = pl.encode_slot(
            pl.ITEM_COMPASS, name_runs=_legacy_runs("&bServer-Auswahl &7(Rechtsklick)"))
        return slots

    def _open_menu(self, session: _Session) -> None:
        session.menu_servers = _menu_servers()          # frische DB-Liste
        session.menu_open = True
        session.menu_fits = _menu_fits(session.menu_servers, session)
        self._send(session, pl.build_open_screen(_MENU_WINDOW, pl.MENU_GENERIC_9X3, "Server auswählen"))
        self._send(session, pl.build_container_content(
            _MENU_WINDOW, self._menu_slots(session.menu_servers, session.menu_fits)))

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
            # Das Menue erst schliessen, wenn der Wechsel wirklich laeuft. Bei einer
            # Rueckfrage MUSS der Spieler noch einmal klicken koennen ("Klick nochmal"),
            # und bei einer Absage kann er gleich etwas anderes waehlen.
            if self._try_transfer(session, srv):
                session.menu_open = False
                self._send(session, pl.build_close_container(_MENU_WINDOW))
            else:
                # Der Klick hat den Cursor des Clients bewegt - Ansicht zuruecksetzen,
                # sonst klebt das Item am Mauszeiger.
                self._send(session, pl.build_container_content(
                    _MENU_WINDOW,
                    self._menu_slots(session.menu_servers, getattr(session, "menu_fits", None))))

    def _try_transfer(self, session: _Session, srv: dict) -> bool:
        """Serverwechsel MIT Vorab-Pruefung (Graceful Rejection).

        Ein nativer Transfer trennt die Verbindung zur Lobby - wuerde das Ziel den Spieler
        ablehnen (Whitelist, Ban, offline, voll), landete er im Disconnect-Screen statt
        zurueck in der Lobby. Deshalb VORHER pruefen und ihn bei Ablehnung einfach hier
        behalten, mit Begruendung im Chat.

        Passt nur der CLIENT nicht (Loader/Mods), ist das eine Rueckfrage statt einer Absage:
        der zweite Klick innerhalb _CONFIRM_TTL geht trotzdem durch."""
        host, port = srv.get("host"), int(srv.get("port") or 0)
        label = _plain(srv.get("display") or srv.get("key") or "?")
        if not host or port <= 0:
            self._tell(session, f"{label} hat kein gueltiges Ziel.")
            return False
        try:
            sid = int(srv.get("id"))
        except (TypeError, ValueError):
            sid = None          # ohne brauchbare Server-ID keine Vorab-Pruefung
        if sid is not None:
            pending = getattr(session, "pending_confirm", None)
            if pending is None:
                pending = session.pending_confirm = {}
            # Der zweite Klick verbraucht die Rueckfrage - danach zaehlt nur noch, ob einer
            # der harten Gruende (Ban, Whitelist, offline, voll) dagegensteht.
            stamp = pending.pop(sid, None)
            override = stamp is not None and (time.monotonic() - stamp) <= _CONFIRM_TTL
            verdict = _join_verdict(sid, session, override=override)
            if verdict.confirm:
                pending[sid] = time.monotonic()
                self._tell(session, verdict.reason
                           or f"{label} passt vermutlich nicht zu deinem Client.")
                self._tell(session, "Klick nochmal, um es trotzdem zu versuchen.")
                print(f"[hub] {session.name} -> {label} RUECKFRAGE: {verdict.code}")
                return False
            if not verdict.ok:
                self._tell(session, verdict.reason or f"{label} ist gerade nicht erreichbar.")
                print(f"[hub] {session.name} -> {label} ABGELEHNT: {verdict.reason}")
                return False
            if verdict.note:
                self._tell(session, verdict.note)
        self._tell(session, f"Verbinde zu {label} ...")
        self._send(session, pl.build_transfer(host, port))
        print(f"[hub] {session.name} -> Transfer zu {host}:{port} ({label})")
        return True

    def _on_move(self, session: _Session, nx, ny, nz, yaw, pitch) -> None:
        ox, oy, oz = session.x, session.y, session.z
        session.x, session.y, session.z, session.yaw, session.pitch = nx, ny, nz, yaw, pitch
        self._broadcast(pl.build_entity_move_rot(session.eid, ox, oy, oz, nx, ny, nz,
                                                 yaw=yaw, pitch=pitch, on_ground=True),
                        exclude=session)
        self._broadcast(pl.build_head_rotation(session.eid, yaw), exclude=session)
        self._bridge_pub_move(session)   # Bewegung an die Vanilla-Instanz spiegeln


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
