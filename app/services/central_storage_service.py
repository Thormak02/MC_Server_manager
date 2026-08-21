"""Zentrales NAS-Verzeichnis fuer Logs + DB-Snapshots (mit lokalem Fallback).

Gated ueber das Setting ``central_storage_root`` (leer = alles lokal). Ist es gesetzt,
landen Server-/Manager-Logs unter ``<root>/logs`` und konsistente DB-Snapshots unter
``<root>/db-snapshots``. Ist die NAS gerade NICHT beschreibbar, faellt alles automatisch
auf ``data/`` zurueck - Logging und Manager laufen also weiter, keine harte Abhaengigkeit.

Snapshots werden per SQLite-Backup-API erzeugt (konsistent, kein naiver Datei-Kopie);
die Live-DB bleibt lokal. UNC-Pfad bevorzugen (SYSTEM-Dienst sieht ``Z:`` meist nicht).
"""

from __future__ import annotations

import sqlite3
import time
from pathlib import Path

from app.core.config import get_settings

_SUBDIR_LOGS = "logs"
_SUBDIR_DB = "db-snapshots"
_SNAPSHOT_KEEP = 20
_SNAPSHOT_MIN_INTERVAL = 1800.0   # 30 min zwischen automatischen Snapshots

_last_snapshot = 0.0
# kurzer Cache der Beschreibbarkeits-Pruefung, damit nicht bei jeder Log-Zeile geprobt wird
_usable_cache: dict[str, tuple[float, bool]] = {}
_USABLE_TTL = 60.0


def _central_root() -> str:
    try:
        from app.services import app_setting_service

        return app_setting_service.get_central_storage_root_runtime().strip()
    except Exception:  # noqa: BLE001
        return ""


def is_usable(path: Path) -> bool:
    """True, wenn ``path`` anlegbar + beschreibbar ist (kurz gecacht)."""
    key = str(path)
    now = time.monotonic()
    cached = _usable_cache.get(key)
    if cached and now - cached[0] < _USABLE_TTL:
        return cached[1]
    ok = False
    try:
        path.mkdir(parents=True, exist_ok=True)
        probe = path / ".mcsm_write_probe"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink(missing_ok=True)
        ok = True
    except Exception:  # noqa: BLE001
        ok = False
    _usable_cache[key] = (now, ok)
    return ok


def probe_now(root: str) -> tuple[bool, str]:
    """Einmalige (ungecachte) Schreibprobe fuer die UI. (ok, Nachricht)."""
    root = (root or "").strip()
    if not root:
        return False, "Kein Pfad gesetzt (alles bleibt lokal)."
    target = Path(root) / _SUBDIR_LOGS
    try:
        target.mkdir(parents=True, exist_ok=True)
        probe = target / ".mcsm_write_probe"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink(missing_ok=True)
        _usable_cache.pop(str(target), None)
        return True, f"Schreibzugriff OK: {target}"
    except Exception as exc:  # noqa: BLE001
        return False, f"Kein Schreibzugriff auf {target}: {exc}"


def _local_logs_dir() -> Path:
    return get_settings().data_dir / "logs"


def logs_dir() -> Path:
    """Basis-Log-Verzeichnis: NAS ``<root>/logs`` wenn gesetzt+beschreibbar, sonst lokal."""
    root = _central_root()
    if root:
        candidate = Path(root) / _SUBDIR_LOGS
        if is_usable(candidate):
            return candidate
    local = _local_logs_dir()
    local.mkdir(parents=True, exist_ok=True)
    return local


def db_snapshot_dir() -> Path | None:
    root = _central_root()
    if not root:
        return None
    candidate = Path(root) / _SUBDIR_DB
    return candidate if is_usable(candidate) else None


def _db_file() -> Path | None:
    try:
        from app.db.session import engine

        db = engine.url.database
        if db and db != ":memory:":
            return Path(db)
    except Exception:  # noqa: BLE001
        pass
    return None


def _prune_snapshots(dest: Path) -> None:
    try:
        snaps = sorted(dest.glob("mc_server_manager-*.db"),
                       key=lambda p: p.stat().st_mtime, reverse=True)
        for old in snaps[_SNAPSHOT_KEEP:]:
            old.unlink(missing_ok=True)
    except Exception:  # noqa: BLE001
        pass


def snapshot_db() -> tuple[bool, str]:
    """Konsistenten SQLite-Snapshot auf die NAS legen (Backup-API). (ok, Nachricht)."""
    dest = db_snapshot_dir()
    if dest is None:
        return False, "Kein/kein beschreibbares NAS-Verzeichnis."
    src = _db_file()
    if src is None or not src.exists():
        return False, "DB-Datei nicht gefunden (evtl. nicht-lokale DB)."
    out = dest / f"mc_server_manager-{time.strftime('%Y%m%d-%H%M%S')}.db"
    try:
        source = sqlite3.connect(str(src))
        try:
            target = sqlite3.connect(str(out))
            try:
                source.backup(target)
            finally:
                target.close()
        finally:
            source.close()
    except Exception as exc:  # noqa: BLE001
        return False, f"Snapshot fehlgeschlagen: {exc}"
    _prune_snapshots(dest)
    return True, f"Snapshot -> {out.name}"


def maybe_snapshot_db() -> None:
    """Gedrosselter Snapshot fuer den Idle-Tick. No-op wenn NAS aus / kuerzlich gemacht."""
    global _last_snapshot
    if not _central_root():
        return
    now = time.monotonic()
    if now - _last_snapshot < _SNAPSHOT_MIN_INTERVAL:
        return
    _last_snapshot = now
    snapshot_db()
