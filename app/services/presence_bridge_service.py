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


def reconcile_presence_bridge() -> None:
    """Vom Idle-Monitor getaktet: verwaiste Avatare aufraeumen (nur wenn aktiv)."""
    if not is_enabled():
        return
    try:
        BUS.sweep()
    except Exception:  # noqa: BLE001
        pass
