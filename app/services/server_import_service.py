import re
from pathlib import Path

from sqlalchemy.orm import Session

from app.schemas.server import ServerImportConfirm, ServerImportPreview
from app.services.memory_settings_service import RAM_MAX_MB, RAM_MIN_MB, RAM_STEP_MB
from app.services.server_service import create_server_from_import, slugify


_MC_VERSION_PATTERN = re.compile(r"\b(1\.\d{1,2}(?:\.\d{1,2})?)\b")
_MEMORY_PATTERN = re.compile(r"(?i)-X(?P<kind>ms|mx)(?P<value>\d+)(?P<unit>[KMG])\b")


def analyze_directory(base_path: str) -> ServerImportPreview:
    root_path = Path(base_path).expanduser().resolve()
    if not root_path.exists() or not root_path.is_dir():
        raise ValueError("Der angegebene Serverordner existiert nicht.")

    notes: list[str] = []
    server_type = detect_server_type(root_path)
    start_bat_path = detect_start_bat_file(root_path)
    start_command = extract_start_command(start_bat_path) if start_bat_path else None
    mc_version = detect_minecraft_version(root_path, start_command=start_command)
    detected_port = detect_server_port(root_path)
    detected_memory_min, detected_memory_max = detect_memory_settings(
        root_path,
        start_command=start_command,
        start_bat_path=start_bat_path,
    )

    if start_bat_path:
        notes.append(f"Startdatei erkannt: {Path(start_bat_path).name}")
    else:
        notes.append("Keine .bat Startdatei automatisch erkannt.")
    if detected_port is not None:
        notes.append(f"Port erkannt: {detected_port}")
    if detected_memory_min is not None or detected_memory_max is not None:
        notes.append(
            "RAM erkannt: "
            f"min={detected_memory_min if detected_memory_min is not None else '-'} MB, "
            f"max={detected_memory_max if detected_memory_max is not None else '-'} MB"
        )

    server_name = root_path.name
    return ServerImportPreview(
        name=server_name,
        slug=slugify(server_name),
        base_path=str(root_path),
        server_type=server_type,
        mc_version=mc_version,
        start_mode="bat" if start_bat_path else "command",
        start_bat_path=start_bat_path,
        start_command=start_command,
        memory_min_mb=detected_memory_min,
        memory_max_mb=detected_memory_max,
        port=detected_port,
        notes=notes,
    )


def detect_server_type(root_path: Path) -> str:
    jar_names = [file.name.lower() for file in root_path.glob("*.jar")]
    if any("paper" in name or "paperclip" in name for name in jar_names):
        return "paper"
    if any("spigot" in name for name in jar_names):
        return "spigot"
    if any("craftbukkit" in name or "bukkit" in name for name in jar_names):
        return "bukkit"
    if any("fabric" in name for name in jar_names):
        return "fabric"
    if any("neoforge" in name for name in jar_names):
        return "neoforge"
    if any("forge" in name for name in jar_names):
        return "forge"

    lowered_script_text = " ".join(_read_text(script).lower() for script in _iter_start_scripts(root_path))
    if "paper" in lowered_script_text or "paperclip" in lowered_script_text:
        return "paper"
    if "spigot" in lowered_script_text:
        return "spigot"
    if "craftbukkit" in lowered_script_text or "bukkit" in lowered_script_text:
        return "bukkit"
    if "fabric" in lowered_script_text:
        return "fabric"
    if "neoforge" in lowered_script_text:
        return "neoforge"
    if "forge" in lowered_script_text:
        return "forge"

    if (root_path / "plugins").exists():
        return "spigot"
    if (root_path / "mods").exists():
        return "forge"
    return "vanilla"


def detect_minecraft_version(root_path: Path, *, start_command: str | None = None) -> str:
    for file in root_path.glob("*.jar"):
        match = _MC_VERSION_PATTERN.search(file.name)
        if match:
            return match.group(1)

    if start_command:
        match = _find_mc_version(start_command)
        if match:
            return match

    for script in _iter_start_scripts(root_path):
        match = _find_mc_version(_read_text(script))
        if match:
            return match

    for log_candidate in (
        root_path / "logs" / "latest.log",
        root_path / "logs" / "latest.txt",
        root_path / "latest.log",
    ):
        if not log_candidate.exists():
            continue
        match = _find_mc_version(_read_text(log_candidate))
        if match:
            return match

    return "unknown"


def detect_start_bat_file(root_path: Path) -> str | None:
    preferred_names = ["start.bat", "run.bat", "launch.bat", "server.bat"]
    for name in preferred_names:
        candidate = root_path / name
        if candidate.exists():
            return str(candidate.resolve())

    all_bat_files = sorted(root_path.glob("*.bat"))
    if not all_bat_files:
        return None
    return str(all_bat_files[0].resolve())


def extract_start_command(start_bat_path: str | None) -> str | None:
    if not start_bat_path:
        return None
    bat_path = Path(start_bat_path)
    if not bat_path.exists():
        return None

    with bat_path.open("r", encoding="utf-8", errors="ignore") as handle:
        for raw_line in handle:
            line = raw_line.strip()
            if not line:
                continue
            if line.lower().startswith("@echo"):
                continue
            if line.lower().startswith("rem "):
                continue
            return line
    return None


def detect_server_port(root_path: Path) -> int | None:
    properties_path = root_path / "server.properties"
    if not properties_path.exists() or not properties_path.is_file():
        return None
    raw_text = _read_text(properties_path)
    for raw_line in raw_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip().lower() != "server-port":
            continue
        try:
            parsed = int(value.strip())
        except ValueError:
            return None
        if 1 <= parsed <= 65535:
            return parsed
        return None
    return None


def detect_memory_settings(
    root_path: Path,
    *,
    start_command: str | None = None,
    start_bat_path: str | None = None,
) -> tuple[int | None, int | None]:
    xms: int | None = None
    xmx: int | None = None

    def _apply_from_text(text: str) -> None:
        nonlocal xms, xmx
        for match in _MEMORY_PATTERN.finditer(text):
            value_mb = _memory_to_mb(match.group("value"), match.group("unit"))
            normalized = _normalize_detected_memory(value_mb)
            if normalized is None:
                continue
            if match.group("kind").lower() == "ms":
                xms = normalized
            else:
                xmx = normalized

    if start_command:
        _apply_from_text(start_command)

    if start_bat_path:
        _apply_from_text(_read_text(Path(start_bat_path)))

    for script in _iter_start_scripts(root_path):
        _apply_from_text(_read_text(script))

    for args_file in (
        root_path / "user_jvm_args.txt",
        root_path / "jvm.args",
        root_path / "java.args",
        root_path / "win_args.txt",
        root_path / "unix_args.txt",
    ):
        if not args_file.exists():
            continue
        _apply_from_text(_read_text(args_file))

    return xms, xmx


def _memory_to_mb(raw_value: str, raw_unit: str) -> int | None:
    try:
        value = int(raw_value)
    except ValueError:
        return None
    unit = (raw_unit or "M").upper()
    if value <= 0:
        return None
    if unit == "G":
        return value * 1024
    if unit == "M":
        return value
    if unit == "K":
        return max(1, value // 1024)
    return None


def _normalize_detected_memory(value_mb: int | None) -> int | None:
    if value_mb is None:
        return None
    if value_mb < RAM_MIN_MB or value_mb > RAM_MAX_MB:
        return None
    if value_mb % RAM_STEP_MB != 0:
        return None
    return value_mb


def _iter_start_scripts(root_path: Path) -> list[Path]:
    scripts = list(root_path.glob("*.bat")) + list(root_path.glob("*.cmd")) + list(root_path.glob("*.sh"))
    return sorted(path for path in scripts if path.is_file())


def _read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return ""


def _find_mc_version(text: str) -> str | None:
    hint_patterns = (
        re.compile(r"(?i)starting minecraft server version\s+(1\.\d{1,2}(?:\.\d{1,2})?)"),
        re.compile(r"(?i)minecraft(?:\s+server)?(?:\s+version)?\s*[:=]?\s*(1\.\d{1,2}(?:\.\d{1,2})?)"),
        re.compile(r"(?i)for minecraft\s+(1\.\d{1,2}(?:\.\d{1,2})?)"),
    )
    for pattern in hint_patterns:
        match = pattern.search(text)
        if match:
            return match.group(1)
    generic = _MC_VERSION_PATTERN.search(text)
    if generic:
        return generic.group(1)
    return None


def import_server(db: Session, data: ServerImportConfirm):
    root_path = Path(data.base_path).expanduser().resolve()
    if not root_path.exists() or not root_path.is_dir():
        raise ValueError("Serverordner existiert nicht.")

    if data.start_mode == "bat":
        if not data.start_bat_path:
            raise ValueError("Bei Startmodus 'bat' muss eine Startdatei angegeben sein.")
        bat_path = Path(data.start_bat_path).expanduser().resolve()
        if not bat_path.exists():
            raise ValueError("Die angegebene Startdatei existiert nicht.")

    if data.start_mode == "command" and not data.start_command:
        raise ValueError("Bei Startmodus 'command' muss ein Startbefehl angegeben sein.")

    return create_server_from_import(db, data)
