from __future__ import annotations

import os
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

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
}

_LAST_SCAN_AT: datetime | None = None
_SCAN_CACHE_SECONDS = 300


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


def required_java_major_for_mc(mc_version: str) -> int:
    text = (mc_version or "").strip()
    match = re.match(r"^(\d+)\.(\d+)(?:\.(\d+))?", text)
    if not match:
        return 17

    first = int(match.group(1))
    second = int(match.group(2))
    patch = int(match.group(3) or 0)

    if first != 1:
        return 21
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


def ensure_server_java_profile(db: Session, server: Server) -> tuple[bool, str]:
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
        if best is None:
            return (
                False,
                f"Kein kompatibles Java gefunden (benoetigt Java {required}+ fuer MC {server.mc_version}). "
                "Bitte Java in den Einstellungen erkennen oder installieren.",
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


def prepare_server_java_runtime(db: Session, server: Server) -> tuple[bool, str, dict[str, str] | None]:
    # Cache-aware background synchronization
    sync_detected_java_profiles(db, force=False)

    ok, message = ensure_server_java_profile(db, server)
    if not ok:
        return False, message, None

    profile = db.get(JavaProfile, server.java_profile_id) if server.java_profile_id else None
    if profile is None:
        return False, "Java-Profil konnte nicht geladen werden.", None

    java_path = Path(profile.java_path).expanduser().resolve()
    if not java_path.exists() or not java_path.is_file():
        return False, f"Java-Pfad nicht gefunden: {java_path}", None

    return True, message, build_java_env_from_profile(profile)


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
        return False, "Ungueltige Java-Version. Erlaubt: 8, 11, 17, 21, 23."

    try:
        version_check = subprocess.run(
            ["winget", "--version"],
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
        "winget",
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
