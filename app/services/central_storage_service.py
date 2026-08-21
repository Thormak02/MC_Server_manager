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
_SNAPSHOT_KEEP = 40
_SNAPSHOT_MIN_INTERVAL = 300.0    # 5 min zwischen automatischen Snapshots (frische Audit-Logs)

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


_conn_ok_cache: dict[str, float] = {}
_CONN_TTL = 120.0


def _share_root(unc: str) -> str | None:
    r"""Aus ``\\host\share\...`` die Freigabe-Wurzel ``\\host\share`` extrahieren."""
    u = (unc or "").strip().replace("/", "\\")
    if not u.startswith("\\\\"):
        return None
    parts = [p for p in u[2:].split("\\") if p]
    if len(parts) < 2:
        return None
    return "\\\\" + parts[0] + "\\" + parts[1]


def _wnet_connect(remote: str, user: str, password: str) -> int:
    """WNetAddConnection2 (wie ``net use``). 0/85 = ok. Nur Windows.

    WICHTIG: argtypes explizit setzen, sonst uebergibt ctypes die Strings ggf. als ANSI
    an die W-(Wide-)Funktion -> verstuemmelte Zugangsdaten (1326/1312).
    """
    import ctypes
    from ctypes import wintypes

    class NETRESOURCE(ctypes.Structure):
        _fields_ = [
            ("dwScope", wintypes.DWORD), ("dwType", wintypes.DWORD),
            ("dwDisplayType", wintypes.DWORD), ("dwUsage", wintypes.DWORD),
            ("lpLocalName", wintypes.LPWSTR), ("lpRemoteName", wintypes.LPWSTR),
            ("lpComment", wintypes.LPWSTR), ("lpProvider", wintypes.LPWSTR),
        ]

    mpr = ctypes.WinDLL("mpr")
    add = mpr.WNetAddConnection2W
    add.argtypes = [ctypes.POINTER(NETRESOURCE), wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.DWORD]
    add.restype = wintypes.DWORD
    cancel = mpr.WNetCancelConnection2W
    cancel.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.BOOL]
    cancel.restype = wintypes.DWORD

    nr = NETRESOURCE()
    nr.dwType = 1  # RESOURCETYPE_DISK
    nr.lpRemoteName = remote
    ret = int(add(ctypes.byref(nr), password, user, 0))
    if ret == 1219:  # ERROR_SESSION_CREDENTIAL_CONFLICT -> alte Session trennen, neu verbinden
        cancel(remote, 0, True)
        ret = int(add(ctypes.byref(nr), password, user, 0))
    return ret


def _connect_root(root: str) -> int | None:
    """Authentifizierte Verbindung zur Freigabe-Wurzel von ``root`` (falls Creds gesetzt).

    So kann auch ein SYSTEM-Dienst auf eine Freigabe schreiben, die Anmeldung verlangt -
    unabhaengig von cmdkey/Benutzerprofil. Gibt den WNet-Returncode zurueck (0/85 = ok)
    oder None (keine Creds / kein UNC / Nicht-Windows)."""
    remote = _share_root(root)
    if not remote:
        return None
    try:
        from app.services import app_setting_service

        user = app_setting_service.get_nas_user_runtime()
        password = app_setting_service.get_nas_password_runtime()
    except Exception:  # noqa: BLE001
        return None
    if not user:
        return None  # keine Creds -> Standard-Auth (Gast/Maschinenkonto)
    try:
        return _wnet_connect(remote, user, password)
    except Exception:  # noqa: BLE001 - z.B. Nicht-Windows / DLL fehlt
        return None


def _ensure_connection() -> None:
    """Wie _connect_root fuer das zentrale Verzeichnis, aber gecacht (Log-/Snapshot-Pfade)."""
    root = _central_root()
    remote = _share_root(root) if root else None
    if not remote:
        return
    now = time.monotonic()
    if now - _conn_ok_cache.get(remote, 0.0) < _CONN_TTL:
        return
    if _connect_root(root) in (0, 85):  # 85 = ALREADY_ASSIGNED
        _conn_ok_cache[remote] = now


def is_usable(path: Path) -> bool:
    """True, wenn ``path`` anlegbar + beschreibbar ist (kurz gecacht)."""
    _ensure_connection()
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
    conn = _connect_root(root)  # None = keine Creds/kein UNC; sonst WNet-Code (0/85 = ok)
    target = Path(root) / _SUBDIR_LOGS
    try:
        target.mkdir(parents=True, exist_ok=True)
        probe = target / ".mcsm_write_probe"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink(missing_ok=True)
        _usable_cache.pop(str(target), None)
        note = " (NAS-Anmeldung genutzt)" if conn in (0, 85) else ""
        return True, f"Schreibzugriff OK: {target}{note}"
    except Exception as exc:  # noqa: BLE001
        is_unc = _share_root(root) is not None
        hint = ""
        if is_unc and conn is None:
            hint = " -> Trage NAS-Benutzer + Passwort ein (die Freigabe verlangt eine Anmeldung, dieselben Daten wie fuers Z:-Laufwerk)."
        elif is_unc and conn not in (0, 85):
            hint = f" -> NAS-Anmeldung fehlgeschlagen (Code {conn}): Benutzer/Passwort pruefen."
        return False, f"Kein Schreibzugriff auf {target}: {exc}{hint}"


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
    """Ziel fuer DB-Snapshots. Bevorzugt ``<root>/db-snapshots`` NUR wenn direkt
    beschreibbar; sonst - UND immer, auch ganz ohne NAS-Setting - LOKAL
    ``data/db-snapshots``. Option D: der SYSTEM-Dienst kann nicht direkt auf die
    anmeldepflichtige NAS schreiben, also lokal snapshotten; ein Sync-Task
    (Benutzerkontext) spiegelt ``data/`` auf die NAS. (Frueher an ``central_storage_root``
    gekoppelt -> es gab GAR KEINE Snapshots, sobald der direkte NAS-Schreibtest scheiterte.)"""
    root = _central_root()
    if root:
        nas = Path(root) / _SUBDIR_DB
        if is_usable(nas):
            return nas
    local = get_settings().data_dir / _SUBDIR_DB
    try:
        local.mkdir(parents=True, exist_ok=True)
        return local
    except Exception:  # noqa: BLE001
        return None


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
    """Gedrosselter LOKALER DB-Snapshot fuer den Idle-Tick (der Sync-Task bringt
    ``data/db-snapshots`` auf die NAS). Laeuft UNABHAENGIG vom NAS-Setting - sonst gaebe
    es (wie zuvor) nie Snapshots, wenn der direkte NAS-Schreibtest aus dem SYSTEM-Dienst
    scheitert. Snapshot ist billig (SQLite-Backup-API) und auf ``_SNAPSHOT_KEEP`` begrenzt."""
    global _last_snapshot
    now = time.monotonic()
    if now - _last_snapshot < _SNAPSHOT_MIN_INTERVAL:
        return
    _last_snapshot = now
    snapshot_db()
