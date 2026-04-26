import json
import re
import shutil
import hashlib
import uuid
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
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

_ASSISTANT_SYSTEM_FIELDS = {
    "__assistant_field_keys",
    "__assistant_existing_keys",
    "__assistant_json_meta",
    "__assistant_json_base",
    "extras_text",
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

_SERVER_PROPERTIES_FIELD_MAP: dict[str, dict[str, Any]] = {
    str(item["key"]): item for item in _SERVER_PROPERTIES_FIELDS
}

_ACCESS_LIST_SCHEMAS: dict[str, dict[str, Any]] = {
    "whitelist": {
        "file": "whitelist.json",
        "identity_field": "name",
        "label": "Whitelist",
        "entry_builder": "player",
    },
    "ops": {
        "file": "ops.json",
        "identity_field": "name",
        "label": "OPs",
        "entry_builder": "op",
    },
    "banned_players": {
        "file": "banned-players.json",
        "identity_field": "name",
        "label": "Bans (Spieler)",
        "entry_builder": "ban_player",
    },
    "banned_ips": {
        "file": "banned-ips.json",
        "identity_field": "ip",
        "label": "Bans (IP)",
        "entry_builder": "ban_ip",
    },
}


def _server_base_path(server: Server) -> Path:
    return Path(server.base_path).expanduser().resolve()


def _is_text_file(path: Path) -> bool:
    return path.suffix.lower() in _TEXT_EXTENSIONS


def _is_inside_base(base: Path, target: Path) -> bool:
    return target == base or base in target.parents


def _normalize_relative_path(relative_path: str) -> str:
    cleaned = (relative_path or "").strip().replace("\\", "/")
    if not cleaned:
        raise ValueError("Dateipfad fehlt.")
    if cleaned.startswith("/"):
        raise ValueError("Nur relative Pfade innerhalb des Serverordners sind erlaubt.")
    if re.match(r"^[A-Za-z]:", cleaned):
        raise ValueError("Absolute Windows-Pfade sind nicht erlaubt.")
    return cleaned


def resolve_server_path(
    server: Server,
    relative_path: str,
    *,
    must_exist: bool = True,
    expect_file: bool | None = None,
) -> Path:
    base = _server_base_path(server)
    if not base.exists() or not base.is_dir():
        raise ValueError("Serverordner existiert nicht.")

    cleaned = _normalize_relative_path(relative_path)
    resolved = (base / cleaned).resolve()
    if not _is_inside_base(base, resolved):
        raise ValueError("Dateizugriff ausserhalb des Serverordners ist nicht erlaubt.")

    if must_exist and not resolved.exists():
        raise ValueError("Datei oder Ordner nicht gefunden.")
    if expect_file is True and resolved.exists() and not resolved.is_file():
        raise ValueError("Es wurde eine Datei erwartet.")
    if expect_file is False and resolved.exists() and not resolved.is_dir():
        raise ValueError("Es wurde ein Ordner erwartet.")
    return resolved


def validate_server_relative_path(server: Server, relative_path: str) -> Path:
    resolved = resolve_server_path(
        server,
        relative_path,
        must_exist=True,
        expect_file=True,
    )
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


def create_text_file(server: Server, relative_path: str, content: str = "") -> str:
    target = resolve_server_path(
        server,
        relative_path,
        must_exist=False,
        expect_file=None,
    )
    if target.exists():
        raise ValueError("Datei oder Ordner existiert bereits.")
    if not _is_inside_base(_server_base_path(server), target):
        raise ValueError("Dateizugriff ausserhalb des Serverordners ist nicht erlaubt.")
    if not _is_text_file(target):
        raise ValueError("Neue Datei muss eine Text-Endung haben.")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    return target.relative_to(_server_base_path(server)).as_posix()


def create_directory(server: Server, relative_dir: str) -> str:
    target = resolve_server_path(
        server,
        relative_dir,
        must_exist=False,
        expect_file=None,
    )
    if target.exists() and not target.is_dir():
        raise ValueError("Unter diesem Pfad existiert bereits eine Datei.")
    target.mkdir(parents=True, exist_ok=True)
    return target.relative_to(_server_base_path(server)).as_posix()


def upload_file(
    server: Server,
    *,
    target_dir: str,
    original_filename: str,
    content_bytes: bytes,
    overwrite: bool = False,
) -> str:
    directory = resolve_server_path(
        server,
        target_dir,
        must_exist=True,
        expect_file=False,
    )
    safe_name = Path(original_filename or "").name.strip()
    if not safe_name or safe_name in {".", ".."}:
        raise ValueError("Ungueltiger Dateiname.")

    target = (directory / safe_name).resolve()
    if not _is_inside_base(_server_base_path(server), target):
        raise ValueError("Dateizugriff ausserhalb des Serverordners ist nicht erlaubt.")
    if target.exists() and target.is_dir():
        raise ValueError("Unter diesem Namen existiert bereits ein Ordner.")
    if target.exists() and not overwrite:
        raise ValueError("Datei existiert bereits. Bitte erst loeschen oder ueberschreiben aktivieren.")

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content_bytes)
    return target.relative_to(_server_base_path(server)).as_posix()


def delete_path(server: Server, relative_path: str, *, recursive: bool = False) -> str:
    target = resolve_server_path(
        server,
        relative_path,
        must_exist=True,
        expect_file=None,
    )
    base = _server_base_path(server)
    if target == base:
        raise ValueError("Der Server-Hauptordner darf nicht geloescht werden.")

    if target.is_dir():
        if recursive:
            shutil.rmtree(target)
        else:
            try:
                target.rmdir()
            except OSError as exc:
                raise ValueError(
                    "Ordner ist nicht leer. Fuer nicht-leere Ordner rekursives Loeschen verwenden."
                ) from exc
    else:
        target.unlink()

    return target.relative_to(base).as_posix()


def get_download_file(server: Server, relative_path: str) -> Path:
    return resolve_server_path(
        server,
        relative_path,
        must_exist=True,
        expect_file=True,
    )


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


def _parse_json_list_lenient(content: str) -> list[dict[str, Any]]:
    raw = (content or "").strip()
    if not raw:
        return []
    parsed = json.loads(raw)
    if not isinstance(parsed, list):
        raise ValueError("Datei muss ein JSON-Array enthalten.")
    normalized: list[dict[str, Any]] = []
    for item in parsed:
        if isinstance(item, dict):
            normalized.append(dict(item))
    return normalized


def _read_json_list_file(server: Server, relative_path: str) -> list[dict[str, Any]]:
    target = resolve_server_path(
        server,
        relative_path,
        must_exist=False,
        expect_file=None,
    )
    if not target.exists():
        return []
    if not target.is_file():
        raise ValueError("Erwartete Datei fehlt oder ist kein Dateipfad.")
    content = target.read_text(encoding="utf-8", errors="replace")
    return _parse_json_list_lenient(content)


def _write_json_list_file(server: Server, relative_path: str, entries: list[dict[str, Any]]) -> None:
    target = resolve_server_path(
        server,
        relative_path,
        must_exist=False,
        expect_file=None,
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(entries, indent=2, ensure_ascii=False) + "\n"
    target.write_text(payload, encoding="utf-8")


def _normalize_player_name(value: str) -> str:
    name = (value or "").strip()
    if not name:
        raise ValueError("Name darf nicht leer sein.")
    if len(name) > 16:
        raise ValueError("Name ist zu lang (max. 16 Zeichen).")
    if not re.match(r"^[A-Za-z0-9_]+$", name):
        raise ValueError("Name darf nur Buchstaben, Zahlen und Unterstrich enthalten.")
    return name


def _normalize_ip(value: str) -> str:
    ip = (value or "").strip()
    if not ip:
        raise ValueError("IP darf nicht leer sein.")
    if " " in ip:
        raise ValueError("IP darf keine Leerzeichen enthalten.")
    return ip


def _canonical_uuid(raw_uuid: str) -> str | None:
    value = (raw_uuid or "").strip().replace("-", "").lower()
    if len(value) != 32 or not re.match(r"^[0-9a-f]{32}$", value):
        return None
    return f"{value[0:8]}-{value[8:12]}-{value[12:16]}-{value[16:20]}-{value[20:32]}"


def _lookup_mojang_uuid(player_name: str) -> str | None:
    encoded_name = urllib.parse.quote(player_name)
    request = urllib.request.Request(
        f"https://api.mojang.com/users/profiles/minecraft/{encoded_name}",
        headers={"User-Agent": "mc_server_manager/1.0"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=4) as response:
            body = response.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, TimeoutError):
        return None
    except Exception:
        return None
    try:
        payload = json.loads(body)
    except Exception:
        return None
    if not isinstance(payload, dict):
        return None
    return _canonical_uuid(str(payload.get("id") or ""))


def _offline_uuid_for_name(player_name: str) -> str:
    digest = hashlib.md5(f"OfflinePlayer:{player_name}".encode("utf-8")).digest()
    generated = uuid.UUID(bytes=digest, version=3)
    return str(generated)


def _resolve_player_uuid(player_name: str) -> str:
    online_uuid = _lookup_mojang_uuid(player_name)
    if online_uuid:
        return online_uuid
    return _offline_uuid_for_name(player_name)


def _utc_now_label() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S %z")


def _build_access_entry(schema_key: str, identity: str) -> dict[str, Any]:
    normalized = schema_key.strip().lower()
    if normalized == "whitelist":
        name = _normalize_player_name(identity)
        return {"uuid": _resolve_player_uuid(name), "name": name}
    if normalized == "ops":
        name = _normalize_player_name(identity)
        return {
            "uuid": _resolve_player_uuid(name),
            "name": name,
            "level": 4,
            "bypassesPlayerLimit": True,
        }
    if normalized == "banned_players":
        name = _normalize_player_name(identity)
        return {
            "uuid": _resolve_player_uuid(name),
            "name": name,
            "created": _utc_now_label(),
            "source": "Server",
            "expires": "forever",
            "reason": "Banned by an operator.",
        }
    if normalized == "banned_ips":
        ip = _normalize_ip(identity)
        return {
            "ip": ip,
            "created": _utc_now_label(),
            "source": "Server",
            "expires": "forever",
            "reason": "Banned by an operator.",
        }
    raise ValueError("Ungueltige Zugriffsliste.")


def _get_access_schema(list_key: str) -> dict[str, Any]:
    normalized = (list_key or "").strip().lower()
    schema = _ACCESS_LIST_SCHEMAS.get(normalized)
    if schema is None:
        raise ValueError("Unbekannte Zugriffsliste.")
    return schema


def get_access_schema_key_label() -> list[dict[str, str]]:
    return [
        {"key": key, "label": str(value.get("label") or key)}
        for key, value in _ACCESS_LIST_SCHEMAS.items()
    ]


def list_access_entries(server: Server, list_key: str) -> list[dict[str, Any]]:
    schema = _get_access_schema(list_key)
    file_name = str(schema["file"])
    return _read_json_list_file(server, file_name)


def add_access_entry(server: Server, list_key: str, identity: str) -> list[dict[str, Any]]:
    schema = _get_access_schema(list_key)
    normalized_key = (list_key or "").strip().lower()
    identity_field = str(schema["identity_field"])
    file_name = str(schema["file"])

    entries = _read_json_list_file(server, file_name)
    entry = _build_access_entry(normalized_key, identity)
    desired_identity = str(entry.get(identity_field) or "").strip()
    if not desired_identity:
        raise ValueError("Eintrag ist ungueltig.")

    filtered: list[dict[str, Any]] = []
    for item in entries:
        existing_identity = str(item.get(identity_field) or "").strip()
        if normalized_key == "banned_ips":
            if existing_identity == desired_identity:
                continue
        elif existing_identity.casefold() == desired_identity.casefold():
            continue
        filtered.append(item)

    filtered.append(entry)
    _write_json_list_file(server, file_name, filtered)
    return filtered


def remove_access_entry(server: Server, list_key: str, identity: str) -> list[dict[str, Any]]:
    schema = _get_access_schema(list_key)
    normalized_key = (list_key or "").strip().lower()
    identity_field = str(schema["identity_field"])
    file_name = str(schema["file"])
    target_identity = (identity or "").strip()
    if not target_identity:
        raise ValueError("Zu loeschender Name/IP fehlt.")

    entries = _read_json_list_file(server, file_name)
    filtered: list[dict[str, Any]] = []
    removed = False
    for item in entries:
        existing_identity = str(item.get(identity_field) or "").strip()
        matches = (
            existing_identity == target_identity
            if normalized_key == "banned_ips"
            else existing_identity.casefold() == target_identity.casefold()
        )
        if matches:
            removed = True
            continue
        filtered.append(item)

    if not removed:
        raise ValueError("Eintrag nicht gefunden.")
    _write_json_list_file(server, file_name, filtered)
    return filtered


def get_whitelist_enabled(server: Server) -> bool:
    target = resolve_server_path(
        server,
        "server.properties",
        must_exist=False,
        expect_file=None,
    )
    if not target.exists():
        return False
    content = target.read_text(encoding="utf-8", errors="replace")
    values, _ = _parse_properties(content)
    return str(values.get("white-list", "")).strip().lower() == "true"


def set_whitelist_enabled(server: Server, enabled: bool) -> None:
    target = resolve_server_path(
        server,
        "server.properties",
        must_exist=False,
        expect_file=None,
    )
    values: dict[str, str] = {}
    extras_text = ""
    if target.exists() and target.is_file():
        content = target.read_text(encoding="utf-8", errors="replace")
        values, extras = _parse_properties(content)
        extras_text = "\n".join(extras).strip()
    values["white-list"] = "true" if enabled else "false"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(_dump_properties(values, extras_text=extras_text), encoding="utf-8")


def _infer_properties_field(key: str, value: str, *, label: str | None = None) -> dict[str, Any]:
    lowered = (value or "").strip().lower()
    if lowered in {"true", "false"}:
        return {
            "key": key,
            "label": label or key,
            "kind": "select",
            "options": ["true", "false"],
            "value": lowered,
            "placeholder": "",
        }
    if re.match(r"^-?\d+$", (value or "").strip()):
        return {
            "key": key,
            "label": label or key,
            "kind": "number",
            "value": (value or "").strip(),
            "placeholder": "",
        }
    return {
        "key": key,
        "label": label or key,
        "kind": "text",
        "value": value or "",
        "placeholder": "",
    }


def _assistant_fields_for_properties(
    normalized_path: str,
    values: dict[str, str],
) -> list[dict[str, Any]]:
    is_server_properties = normalized_path.endswith("server.properties")
    ordered_keys: list[str] = []
    if is_server_properties:
        ordered_keys.extend([item["key"] for item in _SERVER_PROPERTIES_FIELDS])
    for key in sorted(values.keys()):
        if key not in ordered_keys:
            ordered_keys.append(key)

    fields: list[dict[str, Any]] = []
    for key in ordered_keys:
        if key in _SERVER_PROPERTIES_FIELD_MAP:
            spec = dict(_SERVER_PROPERTIES_FIELD_MAP[key])
            spec["value"] = values.get(key, "")
            spec.setdefault("placeholder", "")
            fields.append(spec)
            continue
        fields.append(
            _infer_properties_field(
                key,
                values.get(key, ""),
                label=key,
            )
        )
    return fields


def _json_pointer_escape(token: str) -> str:
    return token.replace("~", "~0").replace("/", "~1")


def _json_pointer_unescape(token: str) -> str:
    return token.replace("~1", "/").replace("~0", "~")


def _json_pointer_label(pointer: str) -> str:
    if not pointer or pointer == "/":
        return "root"
    tokens = [_json_pointer_unescape(item) for item in pointer.strip("/").split("/") if item]
    label_parts: list[str] = []
    for token in tokens:
        if token.isdigit():
            label_parts.append(f"[{token}]")
        elif not label_parts:
            label_parts.append(token)
        else:
            label_parts.append(f".{token}")
    return "".join(label_parts) or "root"


def _flatten_json_scalars(value: Any, pointer: str = "") -> list[tuple[str, Any, str]]:
    if isinstance(value, dict):
        rows: list[tuple[str, Any, str]] = []
        for key, item in value.items():
            token = _json_pointer_escape(str(key))
            rows.extend(_flatten_json_scalars(item, f"{pointer}/{token}"))
        return rows
    if isinstance(value, list):
        rows: list[tuple[str, Any, str]] = []
        for index, item in enumerate(value):
            rows.extend(_flatten_json_scalars(item, f"{pointer}/{index}"))
        return rows
    if isinstance(value, bool):
        return [(pointer, value, "bool")]
    if isinstance(value, int) and not isinstance(value, bool):
        return [(pointer, value, "int")]
    if isinstance(value, float):
        return [(pointer, value, "float")]
    if value is None:
        return [(pointer, value, "null")]
    return [(pointer, str(value), "string")]


def _set_json_pointer_value(root: Any, pointer: str, value: Any) -> None:
    if not pointer:
        raise ValueError("Root-Wert kann nicht direkt ersetzt werden.")
    tokens = [item for item in pointer.strip("/").split("/") if item]
    cursor = root
    for index, token in enumerate(tokens):
        is_last = index == len(tokens) - 1
        if isinstance(cursor, list):
            list_index = int(token)
            if is_last:
                cursor[list_index] = value
                return
            cursor = cursor[list_index]
            continue
        if isinstance(cursor, dict):
            key = _json_pointer_unescape(token)
            if is_last:
                cursor[key] = value
                return
            cursor = cursor[key]
            continue
        raise ValueError("Ungueltiger JSON-Pfad.")



def get_assistant_payload(relative_path: str, content: str) -> dict[str, Any] | None:
    normalized = relative_path.replace("\\", "/").lower()

    if normalized.endswith(".properties"):
        values, extras = _parse_properties(content)
        fields = _assistant_fields_for_properties(normalized, values)
        handled_keys = {item["key"] for item in fields}
        extra_pairs = [f"{key}={value}" for key, value in values.items() if key not in handled_keys]
        extra_pairs.extend(extras)
        return {
            "mode": "server_properties",
            "title": "Server Properties Assistent",
            "fields": fields,
            "field_keys_json": json.dumps([item["key"] for item in fields], ensure_ascii=False),
            "existing_keys_json": json.dumps(sorted(values.keys()), ensure_ascii=False),
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
        except Exception:
            return {
                "mode": "json_text",
                "title": "JSON Assistent",
                "fields": [],
                "extras_text": content,
            }
        flattened = _flatten_json_scalars(obj)
        if not flattened:
            pretty = json.dumps(obj, indent=2, ensure_ascii=False)
            return {
                "mode": "json_text",
                "title": "JSON Assistent",
                "fields": [],
                "extras_text": pretty,
            }
        fields: list[dict[str, Any]] = []
        meta: list[dict[str, str]] = []
        for index, (pointer, value, value_type) in enumerate(flattened):
            key = f"json_field_{index}"
            label = _json_pointer_label(pointer)
            if value_type == "bool":
                field = {
                    "key": key,
                    "label": label,
                    "kind": "select",
                    "options": ["true", "false"],
                    "value": "true" if bool(value) else "false",
                    "placeholder": "",
                }
            elif value_type in {"int", "float"}:
                field = {
                    "key": key,
                    "label": label,
                    "kind": "number",
                    "value": str(value),
                    "placeholder": "",
                }
            else:
                field = {
                    "key": key,
                    "label": label,
                    "kind": "text",
                    "value": "" if value is None else str(value),
                    "placeholder": "null" if value_type == "null" else "",
                }
            fields.append(field)
            meta.append({"key": key, "pointer": pointer, "type": value_type})
        return {
            "mode": "json_fields",
            "title": "JSON Assistent",
            "fields": fields,
            "extras_text": "",
            "assistant_json_meta": json.dumps(meta, ensure_ascii=False),
            "assistant_json_base": json.dumps(obj, ensure_ascii=False),
        }

    return None


def build_content_from_assistant(relative_path: str, form_data: dict[str, str]) -> str:
    normalized = relative_path.replace("\\", "/").lower()

    if normalized.endswith(".properties"):
        values: dict[str, str] = {}
        field_keys: list[str] = []
        existing_keys: set[str] = set()
        raw_field_keys = (form_data.get("__assistant_field_keys") or "").strip()
        raw_existing_keys = (form_data.get("__assistant_existing_keys") or "").strip()
        if raw_field_keys:
            try:
                parsed = json.loads(raw_field_keys)
                if isinstance(parsed, list):
                    field_keys = [str(item) for item in parsed if str(item).strip()]
            except Exception:
                field_keys = []
        if raw_existing_keys:
            try:
                parsed_existing = json.loads(raw_existing_keys)
                if isinstance(parsed_existing, list):
                    existing_keys = {str(item) for item in parsed_existing if str(item).strip()}
            except Exception:
                existing_keys = set()
        if not field_keys:
            field_keys = [
                key
                for key in form_data.keys()
                if key not in _ASSISTANT_SYSTEM_FIELDS and not key.startswith("__")
            ]

        for key in field_keys:
            if key not in form_data:
                continue
            raw = form_data.get(key, "")
            if raw is None:
                raw = ""
            value = str(raw).strip()
            if value or key in existing_keys:
                values[key] = value
        extras_text = form_data.get("extras_text", "")
        return _dump_properties(values, extras_text=extras_text)

    if normalized.endswith("eula.txt"):
        value = form_data.get("eula", "false").strip().lower()
        if value not in {"true", "false"}:
            value = "false"
        return f"eula={value}\n"

    if normalized.endswith(".json"):
        meta_raw = (form_data.get("__assistant_json_meta") or "").strip()
        base_raw = (form_data.get("__assistant_json_base") or "").strip()
        if meta_raw and base_raw:
            meta = json.loads(meta_raw)
            base_obj = json.loads(base_raw)
            if not isinstance(meta, list):
                raise ValueError("JSON Assistent-Metadaten sind ungueltig.")
            for item in meta:
                if not isinstance(item, dict):
                    continue
                key = str(item.get("key") or "")
                pointer = str(item.get("pointer") or "")
                value_type = str(item.get("type") or "string")
                if not key or key not in form_data:
                    continue
                raw_value = str(form_data.get(key) or "").strip()
                if value_type == "bool":
                    resolved_value: Any = raw_value.lower() == "true"
                elif value_type == "int":
                    resolved_value = int(raw_value) if raw_value else 0
                elif value_type == "float":
                    resolved_value = float(raw_value) if raw_value else 0.0
                elif value_type == "null":
                    if raw_value == "" or raw_value.lower() == "null":
                        resolved_value = None
                    else:
                        resolved_value = raw_value
                else:
                    resolved_value = raw_value
                _set_json_pointer_value(base_obj, pointer, resolved_value)
            return json.dumps(base_obj, indent=2, ensure_ascii=False) + "\n"

        raw_json = form_data.get("extras_text", "")
        parsed = json.loads(raw_json)
        return json.dumps(parsed, indent=2, ensure_ascii=False) + "\n"

    raise ValueError("Assistent fuer diese Datei nicht verfuegbar.")
