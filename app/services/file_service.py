import json
from pathlib import Path
from typing import Any

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

_SERVER_PROPERTIES_FIELDS: list[dict[str, Any]] = [
    {"key": "motd", "label": "MOTD", "kind": "text", "placeholder": "A Minecraft Server"},
    {
        "key": "gamemode",
        "label": "GameMode",
        "kind": "select",
        "options": ["survival", "creative", "adventure", "spectator"],
    },
    {
        "key": "difficulty",
        "label": "Schwierigkeit",
        "kind": "select",
        "options": ["peaceful", "easy", "normal", "hard"],
    },
    {"key": "online-mode", "label": "Online Mode", "kind": "select", "options": ["true", "false"]},
    {"key": "pvp", "label": "PvP", "kind": "select", "options": ["true", "false"]},
    {"key": "max-players", "label": "Max Players", "kind": "number", "placeholder": "20"},
    {"key": "server-port", "label": "Server Port", "kind": "number", "placeholder": "25565"},
    {"key": "view-distance", "label": "View Distance", "kind": "number", "placeholder": "10"},
    {"key": "simulation-distance", "label": "Simulation Distance", "kind": "number", "placeholder": "10"},
    {
        "key": "enable-command-block",
        "label": "Command Blocks",
        "kind": "select",
        "options": ["true", "false"],
    },
    {"key": "white-list", "label": "Whitelist aktiv", "kind": "select", "options": ["true", "false"]},
]


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


def _parse_properties(content: str) -> tuple[dict[str, str], list[str]]:
    parsed: dict[str, str] = {}
    extras: list[str] = []
    for raw in content.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            extras.append(line)
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if key:
            parsed[key] = value
    return parsed, extras


def _dump_properties(values: dict[str, str], extras_text: str | None = None) -> str:
    lines = [f"{key}={value}" for key, value in sorted(values.items(), key=lambda item: item[0])]
    if extras_text:
        for row in extras_text.splitlines():
            if row.strip():
                lines.append(row.strip())
    return "\n".join(lines).strip() + "\n"


def get_assistant_payload(relative_path: str, content: str) -> dict[str, Any] | None:
    normalized = relative_path.replace("\\", "/").lower()

    if normalized.endswith("server.properties"):
        values, extras = _parse_properties(content)
        fields = []
        for spec in _SERVER_PROPERTIES_FIELDS:
            field = dict(spec)
            field["value"] = values.get(spec["key"], "")
            fields.append(field)
        handled_keys = {item["key"] for item in _SERVER_PROPERTIES_FIELDS}
        extra_pairs = [f"{key}={value}" for key, value in values.items() if key not in handled_keys]
        extra_pairs.extend(extras)
        return {
            "mode": "server_properties",
            "title": "Server Properties Assistent",
            "fields": fields,
            "extras_text": "\n".join(extra_pairs).strip(),
        }

    if normalized.endswith("eula.txt"):
        accepted = "false"
        for raw in content.splitlines():
            line = raw.strip()
            if line.startswith("eula="):
                accepted = line.split("=", 1)[1].strip().lower()
                break
        return {
            "mode": "eula",
            "title": "EULA Assistent",
            "fields": [
                {
                    "key": "eula",
                    "label": "EULA akzeptiert",
                    "kind": "select",
                    "options": ["false", "true"],
                    "value": "true" if accepted == "true" else "false",
                }
            ],
            "extras_text": "",
        }

    if normalized.endswith(".json"):
        try:
            obj = json.loads(content)
            pretty = json.dumps(obj, indent=2, ensure_ascii=False)
        except Exception:
            pretty = content
        return {
            "mode": "json_text",
            "title": "JSON Assistent",
            "fields": [],
            "extras_text": pretty,
        }

    return None


def build_content_from_assistant(relative_path: str, form_data: dict[str, str]) -> str:
    normalized = relative_path.replace("\\", "/").lower()

    if normalized.endswith("server.properties"):
        values: dict[str, str] = {}
        for spec in _SERVER_PROPERTIES_FIELDS:
            key = spec["key"]
            raw = form_data.get(key, "").strip()
            if raw:
                values[key] = raw
        extras_text = form_data.get("extras_text", "")
        return _dump_properties(values, extras_text=extras_text)

    if normalized.endswith("eula.txt"):
        value = form_data.get("eula", "false").strip().lower()
        if value not in {"true", "false"}:
            value = "false"
        return f"eula={value}\n"

    if normalized.endswith(".json"):
        raw_json = form_data.get("extras_text", "")
        parsed = json.loads(raw_json)
        return json.dumps(parsed, indent=2, ensure_ascii=False) + "\n"

    raise ValueError("Assistent fuer diese Datei nicht verfuegbar.")
