"""Per-Pack-Replay-Registry fuer den Universal-Hub.

Konvention: jedes Modpack bekommt sein Config-Replay unter
``data/replays/<server-slug>_capture.replay``. Existiert die Datei, kann der Hub
dieses Pack spoofen. Die Registry ``{server_id: pfad}`` wird beim Reconcile gebaut und
dem Hub uebergeben. Die Auswahl je Client laeuft ueber "Path A": der Dispatcher taggt
das erkannte Pack (Host ``modlobby-<server_id>.<domain>``) in die Weiterleitung, der Hub
waehlt daraus das passende Profil.

``data/`` ist gitignored -> Captures sind per-Box-Laufzeitdaten, nichts wird committet.
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.server import Server

REPLAY_DIR = "data/replays"


def replay_dir() -> Path:
    return Path(REPLAY_DIR)


def replay_path_for(slug: str) -> str:
    """Konventions-Pfad des Pack-Replays (relativ zum Repo-Root/cwd)."""
    return str(Path(REPLAY_DIR) / f"{slug}_capture.replay")


_LOADABLE_CACHE: dict[str, tuple[tuple, bool]] = {}


def replay_is_loadable(replay_path: str) -> bool:
    """True, wenn das Replay einen S->C PLAY-Login (0x2B) enthaelt - NUR dann kann der Hub
    daraus ein Profil bauen (_load_profile wirft sonst 'kein PLAY-Login').

    Schliesst den Kern-Bug: der Dispatcher taggt sonst auf ein (altes/unvollstaendiges)
    Replay ohne 0x2B, der Hub kann es nicht laden und faellt STILL auf das Default-Profil
    (fremdes Pack) zurueck -> Registry-Kick. So gilt hier dieselbe Bedingung wie beim Hub.
    Gecacht per (mtime, size) - ein Re-Capture aendert die Groesse, also nie stale, und ein
    grosses Replay wird nicht bei jeder Verbindung neu geparst."""
    try:
        st = Path(replay_path).stat()
        key = (st.st_mtime, st.st_size)
    except OSError:
        return False
    cached = _LOADABLE_CACHE.get(replay_path)
    if cached and cached[0] == key:
        return cached[1]
    ok = False
    try:
        from app.services import replay_service as rp

        records = rp.load_replay_file(replay_path)
        ok = rp.find_play_login(records) < len(records)
    except Exception:  # noqa: BLE001 - defekte/leere Datei -> nicht ladbar
        ok = False
    _LOADABLE_CACHE[replay_path] = (key, ok)
    return ok


def has_replay(slug: str) -> bool:
    """True, wenn ein LADBARES Pack-Replay existiert (Datei da UND mit PLAY-Login). Muss
    zur Hub-Seite (pack_profiles) passen, sonst taggt der Dispatcher auf ein nicht ladbares
    Replay und der Hub serviert still das (fremde) Default -> Registry-Kick."""
    path = replay_path_for(slug)
    return Path(path).exists() and replay_is_loadable(path)


def build_pack_registry(db: Session) -> dict[int, str]:
    """``{server_id: replay_pfad}`` fuer gateway_enabled-Server, deren Replay existiert
    UND ladbar ist. Dieselbe Bedingung wie ``has_replay`` -> Dispatcher-Tag und Hub-Profil
    stimmen ueberein (kein stilles Zurueckfallen aufs Default)."""
    registry: dict[int, str] = {}
    for srv in db.scalars(select(Server).where(Server.gateway_enabled.is_(True))).all():
        path = replay_path_for(srv.slug)
        if Path(path).exists() and replay_is_loadable(path):
            registry[srv.id] = path
    return registry


def build_pack_registry_runtime() -> dict[int, str]:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return build_pack_registry(db)


# --- Pack-Fingerprint eines Replays (fuer den Default-Schutz bei Auto-Capture) ----
_MODS_CACHE: dict[str, tuple[float, frozenset]] = {}


def replay_mod_namespaces(replay_path: str) -> frozenset:
    """Mod-Namespaces aus dem im Replay mitgeschnittenen neoforge:register-Manifest
    (C->S). Leeres Set bei Vanilla-Replays oder wenn keins gefunden. Gecacht per mtime."""
    from app.services import mc_dispatch as mcd
    from app.services import replay_service as rp

    try:
        mtime = Path(replay_path).stat().st_mtime
    except OSError:
        return frozenset()
    cached = _MODS_CACHE.get(replay_path)
    if cached and cached[0] == mtime:
        return cached[1]

    mods: frozenset = frozenset()
    try:
        for rec in rp.load_replay_file(replay_path):
            if rec.to_client or rec.packet_id != mcd.CFG_SB_CUSTOM:
                continue
            parsed = mcd.try_read_packet(rec.raw)
            if not parsed:
                continue
            channel, data = mcd.parse_custom_payload(parsed[1])
            if channel == mcd.NEOFORGE_REGISTER:
                got = mcd.extract_mod_namespaces(data)
                if got:
                    mods = frozenset(got)
                    break
    except Exception:  # noqa: BLE001 - defekte/fehlende Datei -> leeres Set
        mods = frozenset()
    _MODS_CACHE[replay_path] = (mtime, mods)
    return mods


def client_matches_replay(client_mods: set, replay_path: str, threshold: float = 1.0) -> bool:
    """True, wenn die Client-Mods das Pack des Replays abdecken.

    WICHTIG: Registry-Sync ist exakt - fehlt dem Client auch nur EINE der im Replay
    registrierten Mod-Registries, kickt der Server ihn ("tried to apply snapshot with
    registry name X but was not found"). Deshalb Default ``threshold=1.0`` (Voll-
    Abdeckung, Pack-Mods ⊆ Client-Mods). Ein niedrigerer Schwellwert wuerde ein
    "aehnliches" Replay servieren und den Client zuverlaessig kicken.
    """
    pack = replay_mod_namespaces(replay_path)
    if not pack:
        return False
    lc = {m.lower() for m in client_mods}
    lp = {m.lower() for m in pack}
    return len(lc & lp) / max(1, len(lp)) >= threshold
