"""Presence-Bridge: gespiegelte Avatare zwischen getrennten Lobby-Instanzen.

Zentraler, sprach-neutraler Praesenz-Bus im Manager (Stern-Topologie). Jede Instanz
PUBLIZIERT ihre lokalen Spieler und KONSUMIERT die der anderen; jede Seite rendert die
Vereinigung MINUS der eigenen (Self-Filter per ``origin``). So sehen sich Spieler
getrennter Instanzen als live-synchrone Fake-Avatare -> EINE Lobby per Projektion.

- Instanz "hub"     = der Python-Spoof-Hub (modded + vanilla bis 1.21.1), in-process.
- Instanz "vanilla" = Velocity + Paper-26.2 (die brandneuen Vanilla-Clients), spaeter
  ueber einen TCP/JSON-Endpoint (Phase 2) an denselben Bus angebunden.

Phase 1 nutzt die IN-PROCESS-API (Hub laeuft im Manager-Prozess). Gated hinter
``presence_bridge_enabled`` (Default aus -> kein Einfluss auf den bestehenden Hub).

Design (aus der Machbarkeitsrecherche):
- Retained last-value je UUID (Snapshot fuer spaete Joiner).
- Monotone ``seq`` je Spieler -> veraltete Updates verwerfen.
- TTL-Heartbeat -> verwaiste Avatare (Crash/Server-Wechsel) aufraeumen.
- Chat cross-instance nur als SYSTEM-Nachricht (kein signierter Spielerchat moeglich).
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field
from typing import Callable

# Events, die Subscriber erhalten. payload = Presence (add/update/remove) bzw. dict (chat).
EVENT_ADD = "add"
EVENT_UPDATE = "update"
EVENT_REMOVE = "remove"
EVENT_CHAT = "chat"

# Instanz-Kennungen (Origin) - jede Seite spiegelt nur die JEWEILS ANDERE.
ORIGIN_HUB = "hub"          # der Python-Spoof-Hub (modded + vanilla <=1.21.1)
ORIGIN_VANILLA = "vanilla"  # Velocity + Paper-26.2 (brandneue Vanilla-Clients)

_TTL_SECONDS = 30.0          # ohne Update so lange -> Avatar gilt als weg
_SWEEP_INTERVAL = 5.0        # wie oft verwaiste Praesenzen entfernt werden


@dataclass
class Presence:
    """Der versionsneutrale Zustand EINES Spielers (egal welche Instanz/Version)."""
    uuid: str                 # echte Spieler-UUID (Hex ohne Bindestriche bevorzugt)
    name: str
    origin: str               # Ursprungs-Instanz ("hub" | "vanilla")
    x: float = 0.0
    y: float = 64.0
    z: float = 0.0
    yaw: float = 0.0
    pitch: float = 0.0
    head_yaw: float = 0.0
    textures: str = ""        # signierter base64-Skin-Blob (leer = Steve/Alex nach UUID)
    textures_sig: str = ""
    flags: int = 0            # Bitmaske (sneaking/sprinting/... - spaeter)
    seq: int = 0             # monoton je Spieler (Staleness/Ordering)
    updated: float = field(default_factory=time.monotonic)

    def to_dict(self) -> dict:
        return {
            "uuid": self.uuid, "name": self.name, "origin": self.origin,
            "x": self.x, "y": self.y, "z": self.z,
            "yaw": self.yaw, "pitch": self.pitch, "head_yaw": self.head_yaw,
            "textures": self.textures, "textures_sig": self.textures_sig,
            "flags": self.flags, "seq": self.seq,
        }


class PresenceBus:
    """In-Process Praesenz-Registry + Pub/Sub (thread-safe). Callbacks laufen OHNE Lock."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._presences: dict[str, Presence] = {}
        self._subs: list[Callable[[str, object], None]] = []
        self._last_sweep = 0.0

    # --- Subscriber (Konsumenten: injizieren Avatare) --------------------------
    def subscribe(self, callback: Callable[[str, object], None]) -> None:
        with self._lock:
            if callback not in self._subs:
                self._subs.append(callback)

    def unsubscribe(self, callback: Callable[[str, object], None]) -> None:
        with self._lock:
            try:
                self._subs.remove(callback)
            except ValueError:
                pass

    def _emit(self, event: str, payload: object) -> None:
        with self._lock:
            subs = list(self._subs)
        for cb in subs:
            try:
                cb(event, payload)
            except Exception:  # noqa: BLE001 - ein Subscriber darf die Bridge nie stoeren
                pass

    # --- Producer (Instanzen melden ihre lokalen Spieler) ----------------------
    def upsert(self, p: Presence) -> None:
        """Praesenz anlegen/aktualisieren. Emit ADD beim ersten Mal, sonst UPDATE.
        Veraltete Updates (kleinere seq) werden verworfen."""
        p.updated = time.monotonic()
        event = EVENT_ADD
        with self._lock:
            prev = self._presences.get(p.uuid)
            if prev is not None:
                if p.seq and prev.seq and p.seq < prev.seq:
                    return  # stale -> ignorieren
                event = EVENT_UPDATE
            self._presences[p.uuid] = p
        self._emit(event, p)

    def remove(self, uuid: str) -> None:
        with self._lock:
            p = self._presences.pop(uuid, None)
        if p is not None:
            self._emit(EVENT_REMOVE, p)

    def remove_origin(self, origin: str) -> None:
        """Alle Praesenzen einer Instanz entfernen (z.B. wenn diese Instanz weg ist)."""
        with self._lock:
            gone = [p for p in self._presences.values() if p.origin == origin]
            for p in gone:
                self._presences.pop(p.uuid, None)
        for p in gone:
            self._emit(EVENT_REMOVE, p)

    def chat(self, name: str, origin: str, text: str) -> None:
        """Cross-instance Chat -> als SYSTEM-Nachricht an die ANDEREN Instanzen."""
        self._emit(EVENT_CHAT, {"name": name, "origin": origin, "text": text})

    # --- Konsum-Helfer ---------------------------------------------------------
    def snapshot(self, exclude_origin: str | None = None) -> list[Presence]:
        """Alle aktuellen Fremd-Praesenzen (fuer eine neu verbundene Instanz/Spieler)."""
        with self._lock:
            return [p for p in self._presences.values() if p.origin != exclude_origin]

    def sweep(self) -> None:
        """Verwaiste Praesenzen (kein Update seit TTL) entfernen -> emit REMOVE.
        Vom Idle-Monitor getaktet aufrufen; selbst gedrosselt."""
        now = time.monotonic()
        if now - self._last_sweep < _SWEEP_INTERVAL:
            return
        self._last_sweep = now
        with self._lock:
            stale = [p for p in self._presences.values() if now - p.updated > _TTL_SECONDS]
            for p in stale:
                self._presences.pop(p.uuid, None)
        for p in stale:
            self._emit(EVENT_REMOVE, p)


def uuid16_from(uuid_str: str) -> bytes:
    """Fremd-UUID (Hex, mit/ohne Bindestriche) -> stabile 16 Bytes fuer die Fake-Entity.
    Echte 128-bit-UUID wird direkt genutzt (spaeter Skin-faehig); sonst SHA1-Fallback."""
    s = (uuid_str or "").replace("-", "").strip()
    try:
        b = bytes.fromhex(s)
        if len(b) == 16:
            return b
    except ValueError:
        pass
    import hashlib

    return hashlib.sha1((uuid_str or "").encode("utf-8")).digest()[:16]


# Prozessweiter Bus-Singleton (Hub + spaeter der TCP-Endpoint teilen ihn).
BUS = PresenceBus()

# --- Synthetischer Proof-Feeder (Phase 1) --------------------------------------
# Publiziert EINEN sich bewegenden 'vanilla'-Avatar, damit im Hub sichtbar wird, dass
# Bridge-Avatare gerendert werden. In Phase 2 ersetzt das echte Paper-Plugin diesen
# Feeder. Nur aktiv, wenn die Bridge eingeschaltet ist.
_synth_stop: "Optional[threading.Event]" = None  # type: ignore[name-defined]


def start_synthetic_feeder() -> None:
    global _synth_stop
    if _synth_stop is not None:
        return  # laeuft bereits
    import math

    _synth_stop = threading.Event()
    ev = _synth_stop

    def _run() -> None:
        t0 = time.monotonic()
        seq = 0
        while not ev.is_set():
            time.sleep(0.1)
            ang = time.monotonic() - t0
            seq += 1
            BUS.upsert(Presence(
                uuid="synthetic-vanilla", name="Vanilla-Test", origin=ORIGIN_VANILLA,
                x=8.5 + 3.0 * math.cos(ang), y=64.0, z=13.5 + 3.0 * math.sin(ang),
                yaw=(math.degrees(ang) % 360.0), head_yaw=(math.degrees(ang) % 360.0), seq=seq,
            ))
        BUS.remove("synthetic-vanilla")

    threading.Thread(target=_run, daemon=True, name="presence-synth").start()


def stop_synthetic_feeder() -> None:
    global _synth_stop
    if _synth_stop is not None:
        _synth_stop.set()
        _synth_stop = None


def is_enabled() -> bool:
    """Ob die Presence-Bridge aktiv ist (Default aus). Fehlertolerant."""
    try:
        from app.services import app_setting_service as s

        return s.get_presence_bridge_enabled_runtime()
    except Exception:  # noqa: BLE001
        return False


# --- TCP/JSON-Endpoint fuer das externe Paper-Avatar-Plugin (Vanilla-Instanz) ---
# Das Plugin PUBLIZIERT seine Paper-Lobby-Spieler (origin=vanilla) und KONSUMIERT die
# Hub-Praesenzen (origin=hub) -> es spawnt fuer jeden Hub-Spieler einen Fake-Avatar.
# Zeilenweises JSON, loopback-only, Token-Auth. Nachrichten:
#   Plugin->Manager: {"t":"hello","token":..}, {"t":"up",uuid,name,x,y,z,yaw,pitch,hy,tex,sig,seq},
#                    {"t":"rm",uuid}, {"t":"chat",name,text}
#   Manager->Plugin: {"t":"up",..}, {"t":"rm",uuid}, {"t":"chat",name,text}  (nur Hub-Events)
import json  # noqa: E402
import socket  # noqa: E402

_SRV_LOCK = threading.RLock()
_SRV_SOCK: "Optional[socket.socket]" = None  # type: ignore[name-defined]
_SRV_STATE: dict = {}


def _send_json(conn, lock: threading.Lock, obj: dict) -> bool:
    try:
        # allow_nan=False: kein "NaN"/"Infinity" ins JSON (der Java-Mini-Parser wuerde die
        # ganze Zeile verwerfen). Nicht-finite Werte -> Nachricht wird uebersprungen.
        data = (json.dumps(obj, separators=(",", ":"), allow_nan=False) + "\n").encode("utf-8")
        with lock:
            conn.sendall(data)
        return True
    except Exception:  # noqa: BLE001
        return False


def _presence_to_msg(p: "Presence") -> dict:
    return {"t": "up", "uuid": p.uuid, "name": p.name, "x": p.x, "y": p.y, "z": p.z,
            "yaw": p.yaw, "pitch": p.pitch, "hy": p.head_yaw or p.yaw,
            "tex": p.textures, "sig": p.textures_sig}


def _handle_plugin_client(conn: "socket.socket", token: str) -> None:
    """Eine Plugin-Verbindung bedienen: Hub-Events -> Plugin, Plugin-Events -> BUS."""
    send_lock = threading.Lock()
    origin = ORIGIN_VANILLA
    published: set[str] = set()
    alive = {"v": True}

    def on_bus(event: str, payload) -> None:
        if not alive["v"]:
            return
        try:
            if event == EVENT_CHAT:
                if isinstance(payload, dict) and payload.get("origin") != origin:
                    if not _send_json(conn, send_lock, {"t": "chat", "name": payload.get("name", "?"),
                                                        "text": payload.get("text", "")}):
                        alive["v"] = False
                return
            if getattr(payload, "origin", None) == origin:
                return  # eigene (vanilla) Praesenz nicht zurueckspiegeln
            if event in (EVENT_ADD, EVENT_UPDATE):
                if not _send_json(conn, send_lock, _presence_to_msg(payload)):
                    alive["v"] = False
            elif event == EVENT_REMOVE:
                if not _send_json(conn, send_lock, {"t": "rm", "uuid": payload.uuid}):
                    alive["v"] = False
        except Exception:  # noqa: BLE001
            alive["v"] = False

    try:
        conn.settimeout(20.0)
        buf = b""
        # 1) Auth-Hello abwarten
        while b"\n" not in buf:
            chunk = conn.recv(4096)
            if not chunk:
                return
            buf += chunk
            if len(buf) > 65536:
                return
        line, buf = buf.split(b"\n", 1)
        hello = json.loads(line.decode("utf-8"))
        if hello.get("t") != "hello" or (token and hello.get("token") != token):
            _send_json(conn, send_lock, {"t": "error", "msg": "auth"})
            return
        _send_json(conn, send_lock, {"t": "welcome"})

        # 2) Ein echtes Plugin ist da -> den synthetischen Proof-Feeder abschalten.
        stop_synthetic_feeder()

        # 3) Snapshot der Hub-Praesenzen + kuenftige Hub-Events an das Plugin.
        BUS.subscribe(on_bus)
        for p in BUS.snapshot(exclude_origin=origin):
            _send_json(conn, send_lock, _presence_to_msg(p))

        # 4) Plugin-Events lesen -> BUS (origin=vanilla).
        conn.settimeout(65.0)
        while alive["v"]:
            while b"\n" not in buf:
                chunk = conn.recv(8192)
                if not chunk:
                    alive["v"] = False
                    break
                buf += chunk
            if not alive["v"]:
                break
            line, buf = buf.split(b"\n", 1)
            if not line.strip():
                continue
            try:
                msg = json.loads(line.decode("utf-8"))
            except Exception:  # noqa: BLE001
                continue
            t = msg.get("t")
            if t == "up" and msg.get("uuid"):
                uuid = str(msg["uuid"])
                published.add(uuid)
                BUS.upsert(Presence(
                    uuid=uuid, name=str(msg.get("name", "?")), origin=origin,
                    x=float(msg.get("x", 0.0)), y=float(msg.get("y", 64.0)), z=float(msg.get("z", 0.0)),
                    yaw=float(msg.get("yaw", 0.0)), pitch=float(msg.get("pitch", 0.0)),
                    head_yaw=float(msg.get("hy", msg.get("yaw", 0.0))),
                    textures=str(msg.get("tex", "")), textures_sig=str(msg.get("sig", "")),
                    seq=int(msg.get("seq", 0)),
                ))
            elif t == "rm" and msg.get("uuid"):
                uuid = str(msg["uuid"])
                published.discard(uuid)
                BUS.remove(uuid)
            elif t == "chat":
                BUS.chat(str(msg.get("name", "?")), origin, str(msg.get("text", "")))
            elif t == "ping":
                _send_json(conn, send_lock, {"t": "pong"})
    except (OSError, ValueError):
        pass
    except Exception as exc:  # noqa: BLE001
        print(f"[presence] Plugin-Client Fehler: {exc!r}")
    finally:
        alive["v"] = False
        BUS.unsubscribe(on_bus)
        for uuid in list(published):   # Avatare dieser Instanz entfernen
            BUS.remove(uuid)
        try:
            conn.close()
        except OSError:
            pass


def _accept_loop(listener: "socket.socket", token: str) -> None:
    while True:
        try:
            conn, _addr = listener.accept()
        except OSError:
            return  # Socket geschlossen -> Server gestoppt
        threading.Thread(target=_handle_plugin_client, args=(conn, token),
                         daemon=True, name="presence-plugin").start()


def start_plugin_server(port: int, token: str) -> bool:
    global _SRV_SOCK, _SRV_STATE
    with _SRV_LOCK:
        # Token MIT in die Soll-Signatur: rotiert er, muss neu gebunden werden (der
        # Accept-Loop haelt sonst den alten Token -> Plugin mit neuem Token faellt raus).
        desired = {"port": int(port), "token": token}
        if _SRV_SOCK is not None and _SRV_STATE == desired:
            return True   # laeuft schon mit Port + Token
        _stop_server_locked()
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind(("127.0.0.1", int(port)))   # NUR loopback (Plugin ist lokal)
            sock.listen(8)
        except OSError:
            return False
        _SRV_SOCK = sock
        _SRV_STATE = desired
        threading.Thread(target=_accept_loop, args=(sock, token),
                         daemon=True, name="presence-accept").start()
        return True


def _stop_server_locked() -> None:
    global _SRV_SOCK, _SRV_STATE
    if _SRV_SOCK is not None:
        try:
            _SRV_SOCK.close()
        except OSError:
            pass
    _SRV_SOCK = None
    _SRV_STATE = {}


def stop_plugin_server() -> None:
    with _SRV_LOCK:
        _stop_server_locked()


def server_running() -> bool:
    with _SRV_LOCK:
        return _SRV_SOCK is not None


def reconcile_presence_bridge() -> None:
    """Vom Idle-Monitor getaktet: TCP-Endpoint an ``presence_bridge_enabled`` angleichen +
    verwaiste Avatare aufraeumen (nur wenn aktiv)."""
    try:
        from app.services import app_setting_service as s

        cfg = s.get_presence_bridge_runtime()
    except Exception:  # noqa: BLE001
        return
    if not cfg["enabled"]:
        if server_running():
            stop_plugin_server()
        return
    start_plugin_server(cfg["port"], cfg["token"])
    try:
        BUS.sweep()
    except Exception:  # noqa: BLE001
        pass
