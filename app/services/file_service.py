from pathlib import Path

from app.models.server import Server
from app.schemas.file_editor import FileReadResponse


_ROOT_LEVEL_CANDIDATES = [
    "server.properties",
    "eula.txt",
    "whitelist.json",
    "ops.json",
    "banned-players.json",
    "banned-ips.json",
]

_TEXT_EXTENSIONS = {
    ".txt",
    ".cfg",
    ".conf",
    ".ini",
    ".json",
    ".yaml",
    ".yml",
    ".toml",
    ".properties",
    ".xml",
    ".log",
    ".md",
}


def _server_base_path(server: Server) -> Path:
    return Path(server.base_path).expanduser().resolve()


def _is_text_file(path: Path) -> bool:
    return path.suffix.lower() in _TEXT_EXTENSIONS


def _is_inside_base(base: Path, target: Path) -> bool:
    return target == base or base in target.parents


def validate_server_relative_path(server: Server, relative_path: str) -> Path:
    base = _server_base_path(server)
    if not base.exists() or not base.is_dir():
        raise ValueError("Serverordner existiert nicht.")

    cleaned = (relative_path or "").strip().replace("\\", "/")
    if not cleaned:
        raise ValueError("Dateipfad fehlt.")
    if cleaned.startswith("/"):
        raise ValueError("Nur relative Pfade innerhalb des Serverordners sind erlaubt.")

    resolved = (base / cleaned).resolve()
    if not _is_inside_base(base, resolved):
        raise ValueError("Dateizugriff ausserhalb des Serverordners ist nicht erlaubt.")
    if not resolved.exists() or not resolved.is_file():
        raise ValueError("Datei nicht gefunden.")
    if not _is_text_file(resolved):
        raise ValueError("Nur Textdateien koennen bearbeitet werden.")
    return resolved


def list_files(server: Server, max_files: int = 500) -> list[str]:
    base = _server_base_path(server)
    if not base.exists() or not base.is_dir():
        return []

    found: set[str] = set()
    for filename in _ROOT_LEVEL_CANDIDATES:
        candidate = base / filename
        if candidate.exists() and candidate.is_file():
            found.add(filename)

    for directory_name in ["config", "plugins", "mods"]:
        root = base / directory_name
        if not root.exists() or not root.is_dir():
            continue
        for file in root.rglob("*"):
            if not file.is_file() or not _is_text_file(file):
                continue
            relative = file.relative_to(base).as_posix()
            found.add(relative)
            if len(found) >= max_files:
                break
        if len(found) >= max_files:
            break

    return sorted(found)


def read_text_file(server: Server, relative_path: str) -> FileReadResponse:
    resolved = validate_server_relative_path(server, relative_path)
    content = resolved.read_text(encoding="utf-8", errors="replace")
    return FileReadResponse(
        relative_path=relative_path.replace("\\", "/"),
        content=content,
        is_editable=True,
    )


def write_text_file(server: Server, relative_path: str, content: str) -> None:
    resolved = validate_server_relative_path(server, relative_path)
    resolved.write_text(content, encoding="utf-8")
