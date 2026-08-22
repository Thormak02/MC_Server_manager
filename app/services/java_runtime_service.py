from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import urllib.request
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from threading import RLock
from typing import Callable

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.java_profile import JavaProfile
from app.models.server import Server

_JAVA_VERSION_QUOTED = re.compile(r'version\s+"([^"]+)"', re.IGNORECASE)
_JAVA_VERSION_FALLBACK = re.compile(r"\b(\d+)(?:\.(\d+))?(?:\.(\d+))?")
_MAJOR_IN_LABEL = re.compile(r"\b(?:java\s*)?(\d{1,2})\b", re.IGNORECASE)

_KNOWN_JAVA_ROOTS = (
    Path(r"C:\Program Files\Java"),
    Path(r"C:\Program Files (x86)\Java"),
    Path(r"C:\Program Files\Eclipse Adoptium"),
    Path(r"C:\Program Files\Amazon Corretto"),
    Path(r"C:\Program Files\BellSoft"),
    Path(r"C:\Program Files\Zulu"),
    Path(r"C:\Program Files\Microsoft"),
)

_WINGET_TEMURIN_IDS = {
    8: "EclipseAdoptium.Temurin.8.JDK",
    11: "EclipseAdoptium.Temurin.11.JDK",
    17: "EclipseAdoptium.Temurin.17.JDK",
    21: "EclipseAdoptium.Temurin.21.JDK",
    23: "EclipseAdoptium.Temurin.23.JDK",
    # Java 25 (LTS) wird fuer das neue jahresbasierte MC-Schema (26.x) benoetigt.
    25: "EclipseAdoptium.Temurin.25.JDK",
}

_LAST_SCAN_AT: datetime | None = None
_SCAN_CACHE_SECONDS = 300

# Serialisiert automatische Java-Installationen (verhindert Doppel-Downloads,
# wenn mehrere Server gleichzeitig starten).
_JAVA_INSTALL_LOCK = RLock()

# Adoptium/Temurin liefert fertige JDK-ZIPs ohne winget/Adminrechte.
_ADOPTIUM_ASSETS_URL = (
    "https://api.adoptium.net/v3/assets/latest/{major}/hotspot"
    "?architecture=x64&image_type=jdk&os=windows&vendor=eclipse"
)


@dataclass
class JavaProbeResult:
    java_path: Path
    version: str
    major: int | None
    vendor: str
    raw_output: str


def _extract_vendor(raw: str) -> str:
    text = (raw or "").lower()
    if "temurin" in text or "adoptium" in text:
        return "Temurin"
    if "corretto" in text or "amazon" in text:
        return "Corretto"
    if "zulu" in text:
        return "Zulu"
    if "bellsoft" in text or "liberica" in text:
        return "Liberica"
    if "oracle" in text:
        return "Oracle"
    if "microsoft" in text:
        return "Microsoft"
    if "openjdk" in text:
        return "OpenJDK"
    return "Java"


def _extract_major(version: str) -> int | None:
    value = (version or "").strip()
    if not value:
        return None
    if value.startswith("1."):
        parts = value.split(".")
        if len(parts) > 1 and parts[1].isdigit():
            return int(parts[1])
    match = re.match(r"(\d+)", value)
    if not match:
        return None
    return int(match.group(1))


def _parse_java_version_output(raw: str) -> tuple[str, int | None]:
    quoted = _JAVA_VERSION_QUOTED.search(raw or "")
    if quoted:
        version = quoted.group(1)
        return version, _extract_major(version)
    fallback = _JAVA_VERSION_FALLBACK.search(raw or "")
    if fallback:
        version = fallback.group(0)
        return version, _extract_major(version)
    return "unknown", None


@lru_cache(maxsize=256)
def _probe_java_cached(path_value: str) -> JavaProbeResult | None:
    java_path = Path(path_value).expanduser().resolve()
    if not java_path.exists() or not java_path.is_file():
        return None
    try:
        completed = subprocess.run(
            [str(java_path), "-version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=8,
        )
    except Exception:
        return None
    output = (completed.stdout or "").strip()
    if completed.returncode != 0 and not output:
        return None
    version, major = _parse_java_version_output(output)
    return JavaProbeResult(
        java_path=java_path,
        version=version,
        major=major,
        vendor=_extract_vendor(output),
        raw_output=output,
    )


def _is_java_executable(path: Path) -> bool:
    return path.name.lower() == "java.exe"


def _candidate_paths_from_where() -> set[Path]:
    found: set[Path] = set()
    try:
        completed = subprocess.run(
            ["where", "java"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=4,
        )
    except Exception:
        return found
    if completed.returncode != 0:
        return found
    for raw in (completed.stdout or "").splitlines():
        line = raw.strip()
        if not line:
            continue
        path = Path(line).expanduser()
        if path.exists() and path.is_file() and _is_java_executable(path):
            found.add(path.resolve())
    return found


def _scan_root_for_java(root: Path) -> set[Path]:
    found: set[Path] = set()
    if not root.exists() or not root.is_dir():
        return found

    patterns = (
        "*/bin/java.exe",
        "*/*/bin/java.exe",
        "*/*/*/bin/java.exe",
    )
    for pattern in patterns:
        for candidate in root.glob(pattern):
            if candidate.exists() and candidate.is_file():
                found.add(candidate.resolve())
    return found


def _candidate_paths() -> list[Path]:
    found: set[Path] = set()
    found.update(_candidate_paths_from_where())

    for root in _KNOWN_JAVA_ROOTS:
        found.update(_scan_root_for_java(root))

    user_profile = os.environ.get("USERPROFILE", "").strip()
    if user_profile:
        found.update(_scan_root_for_java(Path(user_profile) / ".jdks"))

    # Vom Manager selbst installierte JDKs (data_dir/java/temurin-<major>/...).
    found.update(_scan_root_for_java(managed_java_root()))

    return sorted(found, key=lambda item: str(item).lower())


def detect_java_installations() -> list[JavaProbeResult]:
    results: list[JavaProbeResult] = []
    for java_path in _candidate_paths():
        probe = _probe_java_cached(str(java_path))
        if probe is None:
            continue
        results.append(probe)
    # Highest version first (roughly), then path.
    results.sort(key=lambda item: (item.major or 0, item.version, str(item.java_path)), reverse=True)
    return results


def _profile_major_from_label(value: str | None) -> int | None:
    text = (value or "").strip()
    if not text:
        return None
    match = _MAJOR_IN_LABEL.search(text)
    if not match:
        return None
    try:
        return int(match.group(1))
    except ValueError:
        return None


def _profile_major(profile: JavaProfile) -> int | None:
    from_label = _profile_major_from_label(profile.version_label)
    if from_label is not None:
        return from_label
    probe = _probe_java_cached(str(Path(profile.java_path).expanduser().resolve()))
    return probe.major if probe else None


def _unique_profile_name(db: Session, base_name: str, *, exclude_profile_id: int | None = None) -> str:
    name = base_name.strip() or "Auto Java"
    candidate = name
    index = 2
    while True:
        stmt = select(JavaProfile).where(JavaProfile.name == candidate)
        existing = db.scalar(stmt)
        if existing is None or (exclude_profile_id is not None and existing.id == exclude_profile_id):
            return candidate
        candidate = f"{name} #{index}"
        index += 1


def sync_detected_java_profiles(
    db: Session,
    *,
    force: bool = False,
) -> tuple[int, int, int]:
    global _LAST_SCAN_AT
    now = datetime.now(timezone.utc)
    if not force and _LAST_SCAN_AT is not None:
        age = (now - _LAST_SCAN_AT).total_seconds()
        if age < _SCAN_CACHE_SECONDS:
            return 0, 0, 0

    detected = detect_java_installations()
    created = 0
    updated = 0

    existing_by_path: dict[str, JavaProfile] = {}
    for profile in db.scalars(select(JavaProfile)).all():
        resolved = str(Path(profile.java_path).expanduser().resolve()).lower()
        existing_by_path[resolved] = profile

    for item in detected:
        resolved_key = str(item.java_path).lower()
        version_label = f"Java {item.major or '?'} ({item.version})"
        description = f"Auto erkannt ({item.vendor})"
        existing = existing_by_path.get(resolved_key)

        if existing is None:
            desired_name = _unique_profile_name(db, f"Auto Java {item.major or '?'} {item.vendor}".strip())
            row = JavaProfile(
                name=desired_name,
                java_path=str(item.java_path),
                version_label=version_label,
                description=description,
                is_default=False,
            )
            db.add(row)
            db.flush()
            existing_by_path[resolved_key] = row
            created += 1
            continue

        changed = False
        if Path(existing.java_path).expanduser().resolve() != item.java_path:
            existing.java_path = str(item.java_path)
            changed = True
        if (existing.version_label or "") != version_label:
            existing.version_label = version_label
            changed = True
        if (existing.description or "") != description:
            existing.description = description
            changed = True
        if changed:
            db.add(existing)
            updated += 1

    if created or updated:
        db.commit()
    _LAST_SCAN_AT = now
    return len(detected), created, updated


def managed_java_root() -> Path:
    """Ordner fuer vom Manager selbst installierte JDKs (data_dir/java)."""
    return (get_settings().data_dir / "java").resolve()


def _find_java_exe(root: Path) -> Path | None:
    if not root.exists() or not root.is_dir():
        return None
    for candidate in sorted(root.glob("**/bin/java.exe")):
        if candidate.is_file():
            return candidate.resolve()
    return None


def _download_to_file(url: str, target: Path, *, timeout: float = 900.0) -> None:
    """Streamt einen (grossen) Download chunkweise auf die Platte."""
    from app.providers.server.common import USER_AGENT

    target.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response, target.open("wb") as handle:
        shutil.copyfileobj(response, handle, length=1024 * 256)


def _sha256_of_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 256), b""):
            digest.update(chunk)
    return digest.hexdigest()


def install_java_from_adoptium(major_version: int) -> tuple[bool, str, Path | None]:
    """Laedt ein Temurin-JDK direkt von Adoptium und entpackt es nach
    ``data_dir/java/temurin-<major>``. Braucht weder winget noch Adminrechte.

    Serialisiert ueber ``_JAVA_INSTALL_LOCK`` (der Auto-Start-Pfad haelt den Lock
    ebenfalls; RLock -> reentrant). Download + Entpacken laufen atomar ueber einen
    Temp-Ordner ausserhalb des gescannten Java-Ordners; erst ein vollstaendig
    entpacktes JDK wird nach ``dest`` verschoben. So kann kein halb-fertiges JDK
    als "bereits vorhanden" haengenbleiben und keine zwei Laeufe kollidieren.

    Rueckgabe: (erfolg, meldung, pfad_zu_java_exe | None).
    """
    major = int(major_version)

    with _JAVA_INSTALL_LOCK:
        dest = managed_java_root() / f"temurin-{major}"

        existing = _find_java_exe(dest)
        if existing is not None:
            return True, f"Java {major} ist bereits vorhanden ({existing}).", existing

        meta_url = _ADOPTIUM_ASSETS_URL.format(major=major)
        try:
            from app.providers.server.common import USER_AGENT

            req = urllib.request.Request(meta_url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=30) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        except Exception as exc:  # noqa: BLE001
            return False, f"Adoptium-API fuer Java {major} nicht erreichbar: {exc}", None

        if not isinstance(payload, list) or not payload:
            return False, f"Keine Temurin-JDK-{major}-Version (Windows x64) gefunden.", None

        package = (((payload[0] or {}).get("binary") or {}).get("package") or {})
        link = str(package.get("link") or "")
        file_name = str(package.get("name") or f"temurin-{major}.zip")
        checksum = str(package.get("checksum") or "").strip().lower()
        if not link:
            return False, f"Adoptium-Download-Link fuer Java {major} fehlt.", None

        # Temp-Ordner ausserhalb von data_dir/java (wird nicht gescannt), damit ein
        # halb entpacktes JDK nie faelschlich erkannt wird.
        work = (get_settings().data_dir / "tmp" / f"java-install-{major}").resolve()
        shutil.rmtree(work, ignore_errors=True)
        work.mkdir(parents=True, exist_ok=True)
        archive = work / file_name
        extract = work / "jdk"
        try:
            _download_to_file(link, archive, timeout=1200)
            if checksum and _sha256_of_file(archive).lower() != checksum:
                return False, f"Java {major}: Pruefsumme des Downloads stimmt nicht.", None

            with zipfile.ZipFile(archive) as zf:
                zf.extractall(extract)
            if _find_java_exe(extract) is None:
                return False, f"Java {major} entpackt, aber java.exe wurde nicht gefunden.", None

            # Vollstaendig entpacktes JDK atomar an den Zielort verschieben.
            if dest.exists():
                shutil.rmtree(dest, ignore_errors=True)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(extract), str(dest))
        except Exception as exc:  # noqa: BLE001
            return False, f"Java {major} Installation fehlgeschlagen: {exc}", None
        finally:
            shutil.rmtree(work, ignore_errors=True)

        java_exe = _find_java_exe(dest)
        if java_exe is None:
            return False, f"Java {major} installiert, aber java.exe nicht gefunden.", None
        return True, f"Java {major} (Temurin) installiert: {java_exe}", java_exe


def _best_profile_for_major(db: Session, required_major: int) -> JavaProfile | None:
    for profile in db.scalars(select(JavaProfile)).all():
        java_path = Path(profile.java_path).expanduser().resolve()
        if not java_path.exists() or not java_path.is_file():
            continue
        major = _profile_major(profile)
        if major is None or major < required_major:
            continue
        return profile
    return None


def ensure_java_available(
    db: Session,
    required_major: int,
    *,
    on_progress: Callable[[str], None] | None = None,
) -> tuple[bool, str]:
    """Stellt sicher, dass ein Java >= ``required_major`` vorhanden ist.

    Ist keins installiert, laedt der Manager es automatisch von Adoptium
    (Temurin) herunter und legt ein passendes Java-Profil an. Bei mehreren
    gleichzeitigen Starts wird nur einmal heruntergeladen (Lock + Re-Check).
    """

    def _log(message: str) -> None:
        if on_progress and message:
            on_progress(message)

    if _best_profile_for_major(db, required_major) is not None:
        return True, ""

    from app.providers.server.common import offline_mode_enabled

    if offline_mode_enabled():
        return False, (
            f"Java {required_major} fehlt und kann im Offline-Modus nicht automatisch "
            "geladen werden. Bitte Java manuell installieren."
        )

    with _JAVA_INSTALL_LOCK:
        # Zweiter, konkurrenzfester Blick (ein anderer Start koennte inzwischen
        # installiert haben).
        try:
            sync_detected_java_profiles(db, force=True)
        except Exception:  # noqa: BLE001
            pass
        if _best_profile_for_major(db, required_major) is not None:
            return True, ""

        _log(f"Java {required_major} fehlt – wird automatisch von Adoptium (Temurin) geladen ...")
        ok, message, _java_exe = install_java_from_adoptium(required_major)
        _log(message)
        if not ok:
            return False, message

        try:
            sync_detected_java_profiles(db, force=True)
        except Exception:  # noqa: BLE001
            pass
        if _best_profile_for_major(db, required_major) is not None:
            return True, message

        return False, (
            f"Java {required_major} wurde installiert, aber kein passendes Profil erkannt."
        )


def resolve_java_binary(
    db: Session,
    required_major: int,
    *,
    auto_install: bool = True,
    on_progress: Callable[[str], None] | None = None,
) -> str | None:
    """Pfad zu einer ``java.exe`` mit Major >= ``required_major`` (oder None).

    Fuer verwaltete Nicht-Server-Prozesse (z.B. Velocity), die kein Server-Java-Profil
    haben. Installiert bei Bedarf automatisch (Adoptium/Temurin) und gibt den besten
    passenden Profil-Pfad zurueck. None, wenn nichts Passendes verfuegbar/installierbar.
    """
    try:
        sync_detected_java_profiles(db, force=False)
    except Exception:  # noqa: BLE001
        pass
    profile = _best_profile_for_major(db, required_major)
    if profile is None and auto_install:
        ok, _msg = ensure_java_available(db, required_major, on_progress=on_progress)
        if ok:
            profile = _best_profile_for_major(db, required_major)
    if profile is None:
        return None
    java_path = Path(profile.java_path).expanduser().resolve()
    return str(java_path) if java_path.is_file() else None


def required_java_major_for_mc(mc_version: str) -> int:
    text = (mc_version or "").strip()
    match = re.match(r"^(\d+)\.(\d+)(?:\.(\d+))?", text)
    if not match:
        return 17

    first = int(match.group(1))
    second = int(match.group(2))
    patch = int(match.group(3) or 0)

    if first != 1:
        # Neues jahresbasiertes Schema (YY.D.H, z.B. 26.2). Die 2026er-Reihe
        # benoetigt Java 25 (laut PaperMC-API java.version.minimum=25).
        return 25
    if second <= 16:
        return 8
    if second == 17:
        return 16
    if second == 18 or second == 19:
        return 17
    if second == 20:
        if patch >= 5:
            return 21
        return 17
    if second >= 21:
        return 21
    return 17


def choose_best_java_profile(
    db: Session,
    *,
    mc_version: str,
) -> JavaProfile | None:
    required = required_java_major_for_mc(mc_version)
    candidates: list[tuple[int, int, int, JavaProfile]] = []
    for profile in db.scalars(select(JavaProfile)).all():
        java_path = Path(profile.java_path).expanduser().resolve()
        if not java_path.exists() or not java_path.is_file():
            continue
        major = _profile_major(profile)
        if major is None:
            continue
        if major < required:
            continue
        exact_rank = 0 if major == required else 1
        candidates.append((exact_rank, major, 0 if profile.is_default else 1, profile))

    if not candidates:
        return None
    # Prefer exact major, then lower major above requirement, then default profile.
    candidates.sort(key=lambda item: (item[0], item[1], item[2]))
    return candidates[0][3]


def ensure_server_java_profile(
    db: Session,
    server: Server,
    *,
    on_progress: Callable[[str], None] | None = None,
    auto_install: bool = True,
) -> tuple[bool, str]:
    required = required_java_major_for_mc(server.mc_version)
    selected: JavaProfile | None = None

    if server.java_profile_id is not None:
        selected = db.get(JavaProfile, server.java_profile_id)
        if selected is not None:
            java_path = Path(selected.java_path).expanduser().resolve()
            major = _profile_major(selected)
            if not java_path.exists() or not java_path.is_file() or major is None or major < required:
                selected = None

    if selected is None:
        best = choose_best_java_profile(db, mc_version=server.mc_version)
        if best is None and auto_install:
            # Kein passendes Java -> automatisch herunterladen (Adoptium/Temurin).
            installed, _msg = ensure_java_available(db, required, on_progress=on_progress)
            if installed:
                best = choose_best_java_profile(db, mc_version=server.mc_version)
        if best is None:
            return (
                False,
                f"Kein kompatibles Java gefunden (benoetigt Java {required}+ fuer MC {server.mc_version}) "
                "und die automatische Installation ist fehlgeschlagen. "
                "Bitte Java in den Einstellungen installieren.",
            )
        if server.java_profile_id != best.id:
            server.java_profile_id = best.id
            db.add(server)
            db.commit()
            db.refresh(server)
            return True, f"Java-Profil automatisch zugewiesen: {best.name}"
        return True, ""

    return True, ""


def build_java_env_from_profile(profile: JavaProfile) -> dict[str, str]:
    java_path = Path(profile.java_path).expanduser().resolve()
    java_bin = java_path.parent
    java_home = java_bin.parent if java_bin.name.lower() == "bin" else java_bin

    env = os.environ.copy()
    path_value = env.get("PATH", "")
    java_bin_str = str(java_bin)
    lowered_parts = [part.strip().lower() for part in path_value.split(";") if part.strip()]
    if java_bin_str.lower() not in lowered_parts:
        env["PATH"] = f"{java_bin_str};{path_value}" if path_value else java_bin_str
    env["JAVA_HOME"] = str(java_home)
    env["MCSM_JAVA_PATH"] = str(java_path)
    env["MCSM_JAVA_PROFILE"] = profile.name
    return env


def prepare_server_java_runtime(
    db: Session,
    server: Server,
    *,
    on_progress: Callable[[str], None] | None = None,
) -> tuple[bool, str, dict[str, str] | None]:
    # Cache-aware background synchronization
    sync_detected_java_profiles(db, force=False)

    ok, message = ensure_server_java_profile(db, server, on_progress=on_progress)
    if not ok:
        return False, message, None

    profile = db.get(JavaProfile, server.java_profile_id) if server.java_profile_id else None
    if profile is None:
        return False, "Java-Profil konnte nicht geladen werden.", None

    java_path = Path(profile.java_path).expanduser().resolve()
    if not java_path.exists() or not java_path.is_file():
        return False, f"Java-Pfad nicht gefunden: {java_path}", None

    return True, message, build_java_env_from_profile(profile)


def _resolve_winget_executable() -> str | None:
    """winget.exe zuverlaessig finden.

    Als Dienst/ohne interaktives Profil liegt ``winget`` oft nicht im PATH
    (fuehrt zu ``[WinError 2]``). Deshalb zusaetzlich die bekannten
    App-Installer-Speicherorte pruefen.
    """
    found = shutil.which("winget")
    if found:
        return found

    candidates: list[Path] = []
    local_app = os.environ.get("LOCALAPPDATA", "").strip()
    if local_app:
        candidates.append(Path(local_app) / "Microsoft" / "WindowsApps" / "winget.exe")
    program_files = os.environ.get("ProgramFiles", r"C:\Program Files")
    windows_apps = Path(program_files) / "WindowsApps"
    if windows_apps.exists():
        candidates.extend(sorted(windows_apps.glob("Microsoft.DesktopAppInstaller_*/winget.exe")))

    for candidate in candidates:
        if candidate.exists() and candidate.is_file():
            return str(candidate)
    return None


def install_java_with_winget(
    *,
    major_version: int,
    distribution: str = "temurin",
) -> tuple[bool, str]:
    distro = (distribution or "temurin").strip().lower()
    if distro != "temurin":
        return False, "Aktuell wird nur Temurin-Installation via winget unterstuetzt."

    package_id = _WINGET_TEMURIN_IDS.get(int(major_version))
    if not package_id:
        return False, "Ungueltige Java-Version. Erlaubt: 8, 11, 17, 21, 23, 25."

    winget = _resolve_winget_executable()
    if not winget:
        return False, (
            "winget wurde nicht gefunden. Der Manager kann Java stattdessen direkt "
            "von Adoptium installieren (Button unten) – dafuer ist kein winget noetig."
        )

    try:
        version_check = subprocess.run(
            [winget, "--version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=8,
        )
    except Exception as exc:
        return False, f"winget nicht verfuegbar: {exc}"

    if version_check.returncode != 0:
        details = (version_check.stdout or "").strip()
        return False, f"winget nicht verfuegbar. {details}"

    command = [
        winget,
        "install",
        "--id",
        package_id,
        "-e",
        "--silent",
        "--accept-package-agreements",
        "--accept-source-agreements",
        "--disable-interactivity",
    ]
    try:
        completed = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=1800,
        )
    except Exception as exc:
        return False, f"Java-Installation fehlgeschlagen: {exc}"

    output = (completed.stdout or "").strip()
    if completed.returncode != 0:
        tail = "\n".join(output.splitlines()[-12:])
        return False, f"winget Installation fehlgeschlagen (Code {completed.returncode}). {tail}"

    return True, f"Java {major_version} installiert ({package_id})."
