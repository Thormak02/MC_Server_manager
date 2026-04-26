import re
from pathlib import Path

from sqlalchemy.orm import Session

from app.schemas.server import ServerImportConfirm, ServerImportPreview
from app.services.server_service import create_server_from_import, slugify


def analyze_directory(base_path: str) -> ServerImportPreview:
    root_path = Path(base_path).expanduser().resolve()
    if not root_path.exists() or not root_path.is_dir():
        raise ValueError("Der angegebene Serverordner existiert nicht.")

    notes: list[str] = []
    server_type = detect_server_type(root_path)
    start_bat_path = detect_start_bat_file(root_path)
    start_command = extract_start_command(start_bat_path) if start_bat_path else None
    mc_version = detect_minecraft_version(root_path)

    if start_bat_path:
        notes.append(f"Startdatei erkannt: {Path(start_bat_path).name}")
    else:
        notes.append("Keine .bat Startdatei automatisch erkannt.")

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
    return "vanilla"


def detect_minecraft_version(root_path: Path) -> str:
    version_pattern = re.compile(r"(1\.\d{1,2}(?:\.\d{1,2})?)")
    for file in root_path.glob("*.jar"):
        match = version_pattern.search(file.name)
        if match:
            return match.group(1)
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
