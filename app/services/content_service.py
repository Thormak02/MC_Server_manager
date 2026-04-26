import json
import math
import re
from datetime import datetime, timezone
from difflib import SequenceMatcher
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.models.installed_content import InstalledContent
from app.models.server import Server
from app.providers.server.common import list_minecraft_versions
from app.services import audit_service
from app.services.platform_settings_service import (
    get_curseforge_api_key_runtime,
    get_modrinth_user_agent_runtime,
    is_provider_enabled_runtime,
)


MODRINTH_BASE = "https://api.modrinth.com/v2"
CURSEFORGE_BASE = "https://api.curseforge.com"
SPIGET_BASE = "https://api.spiget.org/v2"
MC_GAME_ID = 432
_VALID_RELEASE_CHANNELS = {"all", "release", "beta", "alpha"}
_SEARCH_STOPWORDS = {"a", "an", "and", "for", "of", "the", "to", "with"}
_VALID_SORT_OPTIONS = {"relevance", "downloads", "popularity", "updated", "newest"}
_SEARCH_RESULT_CAP = 200
_CURSEFORGE_CLASS_IDS = {
    "mod": 6,
    "plugin": 5,
    "modpack": 4471,
}
_CURSEFORGE_REQUIRED_RELATION_TYPES = {3, 6}
_CURSEFORGE_CLIENT_ONLY_FALLBACK_KEYS = {
    "drippyloadingscreen",
    "fancymenu",
    "iris",
    "euphoriapatcher",
    "borderlesswindow",
    "darkmodeeverywhere",
    "notenoughrecipebook",
    "keybindspurger",
    "keybindbundles",
    "controlling",
    "configured",
    "searchables",
    "mousetweaks",
}
_MAX_DEPENDENCY_DEPTH = 24
_LOADER_ALIASES = {
    "forge": "forge",
    "neo-forge": "neoforge",
    "neo_forge": "neoforge",
    "neo forge": "neoforge",
    "neoforge": "neoforge",
    "fabric": "fabric",
    "fabric-loader": "fabric",
    "quilt": "quilt",
    "quilt-loader": "quilt",
    "paper": "paper",
    "papermc": "paper",
    "spigot": "spigot",
    "bukkit": "bukkit",
}


def _modrinth_project_types_for_content_type(content_type: str | None) -> list[str]:
    normalized = (content_type or "mod").strip().lower()
    if normalized == "plugin":
        # Modrinth uses both values in the ecosystem.
        return ["plugin", "minecraft_java_server"]
    if normalized == "modpack":
        return ["modpack"]
    return ["mod"]


def _normalize_sort_by(value: str | None) -> str:
    normalized = (value or "relevance").strip().lower()
    if normalized not in _VALID_SORT_OPTIONS:
        return "relevance"
    return normalized


def _normalize_categories(values: list[str] | None) -> list[str]:
    if not values:
        return []
    seen: set[str] = set()
    normalized: list[str] = []
    for raw in values:
        token = str(raw or "").strip()
        if not token:
            continue
        key = token.lower()
        if key in seen:
            continue
        seen.add(key)
        normalized.append(token)
    return normalized


def _normalize_loader(value: str | None) -> str | None:
    token = (value or "").strip().lower()
    if not token:
        return None
    token = re.sub(r"\s+", " ", token)
    alias = _LOADER_ALIASES.get(token)
    if alias:
        return alias

    # CurseForge returns many versioned loader names, e.g. "forge-1.20.1-47.2.0".
    # For filtering we need the canonical loader family.
    normalized = token.replace("_", "-").replace(" ", "-")
    if normalized.startswith("neoforge") or normalized.startswith("neo-forge"):
        return "neoforge"
    if normalized.startswith("forge"):
        return "forge"
    if normalized.startswith("fabric"):
        return "fabric"
    if normalized.startswith("quilt"):
        return "quilt"
    if normalized.startswith("paper"):
        return "paper"
    if normalized.startswith("spigot"):
        return "spigot"
    if normalized.startswith("bukkit"):
        return "bukkit"
    return token


def _normalize_loader_list(value: str | None) -> list[str]:
    return _normalize_filter_values(value, normalize_loader=True)


def _normalize_mc_version_list(value: str | None) -> list[str]:
    return _normalize_filter_values(value, normalize_loader=False)


def _normalize_filter_values(value: str | None, *, normalize_loader: bool = False) -> list[str]:
    if not value:
        return []
    tokens = re.split(r"[,\n;]+", str(value))
    seen: set[str] = set()
    result: list[str] = []
    for raw in tokens:
        token = str(raw or "").strip()
        if not token:
            continue
        if normalize_loader:
            normalized_loader = _normalize_loader(token)
            if not normalized_loader:
                continue
            token = normalized_loader
        key = token.lower()
        if key in seen:
            continue
        seen.add(key)
        result.append(token)
    return result


def _normalized_lookup_key(value: str | None) -> str:
    return re.sub(r"[^a-z0-9]+", "", str(value or "").lower())


def _is_known_client_only_curseforge_mod(mod_data: dict[str, object]) -> bool:
    candidates = [
        str(mod_data.get("slug") or "").strip(),
        str(mod_data.get("name") or "").strip(),
    ]
    for candidate in candidates:
        key = _normalized_lookup_key(candidate)
        if key and key in _CURSEFORGE_CLIENT_ONLY_FALLBACK_KEYS:
            return True
    return False


def _request_json(url: str, headers: dict[str, str] | None = None) -> dict | list:
    req = urllib.request.Request(url, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        message = f"HTTP {exc.code}: {exc.reason}"
        try:
            body = exc.read().decode("utf-8")
            if body:
                message = f"{message} - {body}"
        except Exception:
            pass
        raise ValueError(message) from exc


def _download_file(url: str, target: Path, headers: dict[str, str] | None = None) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=60) as resp:
        target.write_bytes(resp.read())


def _modrinth_headers() -> dict[str, str]:
    if not is_provider_enabled_runtime("modrinth"):
        raise ValueError("Modrinth Provider ist deaktiviert.")
    return {"User-Agent": get_modrinth_user_agent_runtime()}


def _curseforge_headers() -> dict[str, str]:
    if not is_provider_enabled_runtime("curseforge"):
        raise ValueError("CurseForge Provider ist deaktiviert.")
    api_key = get_curseforge_api_key_runtime()
    if not api_key or api_key.upper() in {"CHANGE_ME", "CHANGEME", "YOUR_KEY", "REPLACE_ME"}:
        raise ValueError("CurseForge API Key fehlt oder ist Platzhalter (MCSM_CURSEFORGE_API_KEY).")
    return {"x-api-key": api_key}


def _spiget_headers() -> dict[str, str]:
    return {"User-Agent": "mc-server-manager/1.0"}


def _target_dir(server: Server, content_type: str) -> Path:
    folder = "mods"
    if content_type == "plugin":
        folder = "plugins"
    return Path(server.base_path) / folder


def _default_content_type(server: Server) -> str:
    if server.server_type in {"paper", "spigot", "bukkit"}:
        return "plugin"
    return "mod"


def _expected_server_loader(server: Server, content_type: str | None = None) -> str | None:
    normalized = _normalize_loader(server.server_type)
    if not normalized:
        return None

    normalized_content_type = (content_type or "").strip().lower()
    if normalized_content_type == "mod":
        if normalized in {"forge", "neoforge", "fabric", "quilt"}:
            return normalized
        return None
    if normalized_content_type == "plugin":
        if normalized in {"paper", "spigot", "bukkit"}:
            return normalized
        return None
    if normalized_content_type == "modpack":
        if normalized in {"forge", "neoforge", "fabric", "quilt"}:
            return normalized
        return None

    if normalized in {"forge", "neoforge", "fabric", "quilt", "paper", "spigot", "bukkit"}:
        return normalized
    return None


def _expected_server_mc_version(server: Server) -> str | None:
    value = str(server.mc_version or "").strip()
    if not value or value.lower() == "unknown":
        return None
    return value


def _is_loader_compatible(expected_loader: str, available_loaders: set[str]) -> bool:
    if not expected_loader:
        return True
    if not available_loaders:
        return False

    if expected_loader == "paper":
        return bool(available_loaders.intersection({"paper", "spigot", "bukkit"}))
    if expected_loader == "spigot":
        return bool(available_loaders.intersection({"spigot", "bukkit"}))
    if expected_loader == "bukkit":
        return "bukkit" in available_loaders
    return expected_loader in available_loaders


def _is_mc_version_compatible(expected_mc_version: str, available_mc_versions: set[str]) -> bool:
    if not expected_mc_version:
        return True
    if not available_mc_versions:
        return False
    if expected_mc_version in available_mc_versions:
        return True
    for candidate in available_mc_versions:
        if candidate.startswith(expected_mc_version + "."):
            return True
        if expected_mc_version.startswith(candidate + "."):
            return True
    return False


def _raise_if_incompatible_with_server(
    server: Server,
    content_type: str,
    *,
    provider_name: str,
    available_loaders: set[str],
    available_mc_versions: set[str],
) -> None:
    normalized_content_type = (content_type or "").strip().lower()
    expected_loader = _expected_server_loader(server, content_type)
    expected_mc_version = _expected_server_mc_version(server)

    if normalized_content_type in {"mod", "modpack"} and not expected_loader:
        raise ValueError(
            "Dieser Servertyp unterstuetzt keine Mod-Installation ueber den Manager."
        )

    if expected_loader and not _is_loader_compatible(expected_loader, available_loaders):
        supported = ", ".join(sorted(available_loaders)) if available_loaders else "unbekannt"
        raise ValueError(
            f"Inkompatibel fuer diesen Server-Loader ({expected_loader}). "
            f"Unterstuetzte Loader laut {provider_name}: {supported}."
        )

    if expected_mc_version and not _is_mc_version_compatible(expected_mc_version, available_mc_versions):
        supported_versions = ", ".join(sorted(available_mc_versions)) if available_mc_versions else "unbekannt"
        raise ValueError(
            f"Inkompatibel fuer Minecraft {expected_mc_version}. "
            f"Unterstuetzte Versionen laut {provider_name}: {supported_versions}."
        )


def _normalize_release_channel(value: str | None) -> str:
    normalized = (value or "all").strip().lower()
    if normalized not in _VALID_RELEASE_CHANNELS:
        return "all"
    return normalized


def _matches_release_channel(candidate: str | None, requested: str) -> bool:
    if requested == "all":
        return True
    return (candidate or "release").strip().lower() == requested


def _curseforge_release_channel(release_type: int | None) -> str:
    mapping = {
        1: "release",
        2: "beta",
        3: "alpha",
    }
    return mapping.get(int(release_type or 1), "release")


def _normalize_search_text(value: str | None) -> str:
    cleaned = re.sub(r"[^a-z0-9]+", " ", (value or "").lower())
    return " ".join(cleaned.split())


def _epoch_seconds_to_iso(value: object) -> str | None:
    try:
        seconds = int(float(value))  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None
    if seconds <= 0:
        return None
    return datetime.fromtimestamp(seconds, tz=timezone.utc).isoformat()


def _build_spiget_project_url(item: dict) -> str:
    resource_id = str(item.get("id") or "").strip()
    file_url = str((item.get("file") or {}).get("url") or "").strip()
    slug = ""
    if file_url.startswith("resources/"):
        match = re.match(r"^resources/([^/?]+)\.", file_url)
        if match:
            slug = str(match.group(1) or "").strip()
    if slug and resource_id:
        return f"https://www.spigotmc.org/resources/{slug}.{resource_id}/"
    if resource_id:
        return f"https://www.spigotmc.org/resources/{resource_id}/"
    return "https://www.spigotmc.org/resources/"


def _compact_search_text(value: str | None) -> str:
    return re.sub(r"[^a-z0-9]+", "", (value or "").lower())


def _tokenize_search_text(value: str | None) -> list[str]:
    normalized = _normalize_search_text(value)
    if not normalized:
        return []
    return [token for token in normalized.split(" ") if token]


def _build_curseforge_query_variants(query: str) -> list[str]:
    base = (query or "").strip()
    if not base:
        return []

    variants: list[str] = [base]
    words = [part for part in re.split(r"[\s\-_]+", base) if part]
    significant_words = [
        word for word in words if _normalize_search_text(word) not in _SEARCH_STOPWORDS
    ]

    if len(significant_words) > 1:
        variants.append(" ".join(significant_words))

    if len(words) > 1:
        acronym_parts: list[str] = []
        for word in words:
            cleaned = re.sub(r"[^a-z0-9]+", "", word.lower())
            if not cleaned:
                continue
            if cleaned.isdigit():
                acronym_parts.append(cleaned)
            else:
                acronym_parts.append(cleaned[:1])
        acronym = "".join(acronym_parts)
        if len(acronym) >= 3:
            variants.append(acronym)

    compact = re.sub(r"[^a-z0-9]+", "", base.lower())
    if len(compact) >= 3:
        variants.append(compact)
        tokenized_compact = re.sub(r"([a-z]+)([0-9]+)$", r"\1 \2", compact)
        if tokenized_compact != compact:
            variants.append(tokenized_compact)

    significant_compact = re.sub(r"[^a-z0-9]+", "", "".join(significant_words).lower())
    if len(significant_compact) >= 3:
        variants.append(significant_compact)

    deduped: list[str] = []
    seen: set[str] = set()
    for variant in variants:
        normalized = variant.strip().lower()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        deduped.append(variant)
    return deduped[:6]


def _score_curseforge_item(
    item: dict,
    *,
    query: str,
    query_tokens: list[str],
    base_query: str,
    base_query_tokens: list[str],
    variant_index: int,
) -> float:
    title = str(item.get("name") or "")
    slug = str(item.get("slug") or "")
    summary = str(item.get("summary") or "")

    title_norm = _normalize_search_text(title)
    slug_norm = _normalize_search_text(slug)
    query_norm = _normalize_search_text(query)
    base_query_norm = _normalize_search_text(base_query)
    title_compact = _compact_search_text(title)
    slug_compact = _compact_search_text(slug)
    query_compact = _compact_search_text(query)
    base_query_compact = _compact_search_text(base_query)

    score = 0.0
    if query_norm and title_norm == query_norm:
        score += 12000
    elif query_norm and title_norm.startswith(query_norm):
        score += 8000
    elif query_norm and query_norm in title_norm:
        score += 5000

    if query_norm and slug_norm == query_norm:
        score += 7000
    elif query_norm and query_norm in slug_norm:
        score += 3500

    if query_compact and title_compact == query_compact:
        score += 9000
    elif query_compact and title_compact.startswith(query_compact):
        score += 6500
    elif query_compact and query_compact in title_compact:
        score += 4200

    if query_compact and slug_compact == query_compact:
        score += 5200
    elif query_compact and query_compact in slug_compact:
        score += 3000

    if base_query_compact and title_compact == base_query_compact:
        score += 2200
    elif base_query_compact and base_query_compact in title_compact:
        score += 1300

    if base_query_norm and title_norm == base_query_norm:
        score += 1800
    elif base_query_norm and base_query_norm in title_norm:
        score += 900

    summary_norm = _normalize_search_text(summary)
    title_tokens = set(_tokenize_search_text(title))
    slug_tokens = set(_tokenize_search_text(slug))
    summary_tokens = set(_tokenize_search_text(summary))

    token_hits = 0
    for token in query_tokens:
        if token in title_tokens:
            score += 850
            token_hits += 1
        elif token in slug_tokens:
            score += 500
            token_hits += 1
        elif token in summary_tokens:
            score += 90
            token_hits += 1
        elif token and token in title_compact:
            score += 220
            token_hits += 1
        elif token and token in slug_compact:
            score += 130
            token_hits += 1

    for token in base_query_tokens:
        if token in title_tokens:
            score += 220
        elif token in slug_tokens:
            score += 140

    if query_tokens and all(token in (title_norm + " " + slug_norm) for token in query_tokens):
        score += 2500

    if (query_tokens or base_query_tokens) and token_hits == 0 and not (
        query_compact and query_compact in (title_compact + slug_compact)
    ):
        score -= 1200

    if query_compact:
        score += SequenceMatcher(None, query_compact, title_compact).ratio() * 1800.0
        score += SequenceMatcher(None, query_compact, slug_compact).ratio() * 1000.0

    if base_query_compact and base_query_compact != query_compact:
        score += SequenceMatcher(None, base_query_compact, title_compact).ratio() * 700.0

    if variant_index == 0:
        score += 400
    elif variant_index == 1:
        score += 150

    try:
        downloads = float(item.get("downloadCount") or 0.0)
    except (TypeError, ValueError):
        downloads = 0.0
    if downloads > 0:
        score += min(300.0, math.log10(downloads + 1.0) * 45.0)

    return score


def _safe_file_name(file_name: str) -> str:
    sanitized = Path(file_name).name.strip()
    if not sanitized:
        raise ValueError("Ungueltiger Dateiname.")
    return sanitized


def _content_file_path(server: Server, content_type: str, file_name: str) -> Path:
    return _target_dir(server, content_type) / _safe_file_name(file_name)


def _delete_content_file(server: Server, content_type: str, file_name: str) -> None:
    target = _content_file_path(server, content_type, file_name)
    if not target.exists():
        return
    try:
        target.unlink()
    except OSError as exc:
        reason = str(exc)
        raise ValueError(
            f"Datei konnte nicht geloescht werden: {target} ({reason}). "
            "Bitte Server stoppen und erneut versuchen."
        ) from exc


def _remove_existing_project_entries(
    db: Session,
    server: Server,
    *,
    provider_name: str,
    project_id: str,
    content_type: str,
) -> list[int]:
    stmt = (
        select(InstalledContent)
        .where(InstalledContent.server_id == server.id)
        .where(InstalledContent.provider_name == provider_name)
        .where(InstalledContent.external_project_id == project_id)
        .where(InstalledContent.content_type == content_type)
    )
    existing = list(db.scalars(stmt))
    if not existing:
        return []

    removed_ids: list[int] = []
    for entry in existing:
        _delete_content_file(server, entry.content_type, entry.file_name)
        removed_ids.append(entry.id)
        db.delete(entry)
    return removed_ids


def _find_installed_entry(
    db: Session,
    server: Server,
    *,
    provider_name: str,
    project_id: str,
    content_type: str,
) -> InstalledContent | None:
    stmt = (
        select(InstalledContent)
        .where(InstalledContent.server_id == server.id)
        .where(InstalledContent.provider_name == provider_name)
        .where(InstalledContent.external_project_id == project_id)
        .where(InstalledContent.content_type == content_type)
        .order_by(InstalledContent.id.desc())
    )
    return db.scalars(stmt).first()


def _modrinth_required_dependencies(payload: dict) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for item in payload.get("dependencies") or []:
        if not isinstance(item, dict):
            continue
        dep_type = str(item.get("dependency_type") or "").strip().lower()
        if dep_type != "required":
            continue
        dep_project_id = str(item.get("project_id") or "").strip()
        dep_version_id = str(item.get("version_id") or "").strip()
        if not dep_project_id and not dep_version_id:
            continue
        key = (dep_project_id, dep_version_id)
        if key in seen:
            continue
        seen.add(key)
        result.append(
            {
                "project_id": dep_project_id,
                "version_id": dep_version_id,
            }
        )
    return result


def _curseforge_required_dependencies(file_data: dict) -> list[dict[str, int | None]]:
    result: list[dict[str, int | None]] = []
    seen: set[tuple[int, int | None]] = set()
    for item in file_data.get("dependencies") or []:
        if not isinstance(item, dict):
            continue
        try:
            relation_type = int(item.get("relationType") or 0)
        except (TypeError, ValueError):
            continue
        if relation_type not in _CURSEFORGE_REQUIRED_RELATION_TYPES:
            continue
        try:
            mod_id = int(item.get("modId") or 0)
        except (TypeError, ValueError):
            continue
        if mod_id <= 0:
            continue

        file_id_value = (
            item.get("fileId")
            or item.get("dependencyFileId")
            or item.get("modFileId")
        )
        dep_file_id: int | None
        try:
            dep_file_id = int(file_id_value) if file_id_value is not None else None
        except (TypeError, ValueError):
            dep_file_id = None
        if dep_file_id is not None and dep_file_id <= 0:
            dep_file_id = None

        key = (mod_id, dep_file_id)
        if key in seen:
            continue
        seen.add(key)
        result.append(
            {
                "mod_id": mod_id,
                "file_id": dep_file_id,
            }
        )
    return result


def _modrinth_project_has_channel_match(
    project_id: str,
    mc_versions: list[str],
    loaders: list[str],
    release_channel: str,
) -> bool:
    params: dict[str, str] = {}
    if mc_versions:
        params["game_versions"] = json.dumps(mc_versions)
    if loaders:
        params["loaders"] = json.dumps(loaders)
    query = urllib.parse.urlencode(params)
    url = f"{MODRINTH_BASE}/project/{project_id}/version"
    if query:
        url = f"{url}?{query}"
    payload = _request_json(url, headers=_modrinth_headers())
    if not isinstance(payload, list):
        return False
    for item in payload:
        version_type = str(item.get("version_type") or "release").lower()
        if _matches_release_channel(version_type, release_channel):
            return True
    return False


def _curseforge_project_has_channel_match(
    mod_id: int,
    mc_version: str | None,
    loader: str | None,
    content_type: str,
    release_channel: str,
) -> bool:
    loader = _normalize_loader(loader)
    params: dict[str, str] = {"pageSize": "30"}
    if mc_version:
        params["gameVersion"] = mc_version
    loader_type = _curseforge_loader_type(loader, content_type)
    if loader_type is not None:
        params["modLoaderType"] = str(loader_type)
    url = f"{CURSEFORGE_BASE}/v1/mods/{mod_id}/files?{urllib.parse.urlencode(params)}"
    payload = _request_json(url, headers=_curseforge_headers())
    files = payload.get("data", []) if isinstance(payload, dict) else []
    for item in files:
        channel = _curseforge_release_channel(item.get("releaseType"))
        if _matches_release_channel(channel, release_channel):
            return True
    return False


def search_modrinth(
    query: str,
    mc_version: str | None,
    loader: str | None,
    content_type: str,
    release_channel: str = "all",
    sort_by: str = "relevance",
    categories: list[str] | None = None,
) -> list[dict]:
    release_channel = _normalize_release_channel(release_channel)
    sort_by = _normalize_sort_by(sort_by)
    mc_versions = _normalize_mc_version_list(mc_version)
    loaders = _normalize_loader_list(loader)
    category_tokens = _normalize_categories(categories)
    project_types = _modrinth_project_types_for_content_type(content_type)
    facets: list[list[str]] = [[f"project_type:{project_type}" for project_type in project_types]]
    if mc_versions:
        facets.append([f"versions:{version}" for version in mc_versions])
    if loaders:
        facets.append([f"categories:{item}" for item in loaders])
    if category_tokens:
        facets.append([f"categories:{token}" for token in category_tokens])
    index_mapping = {
        "relevance": "relevance",
        "downloads": "downloads",
        "popularity": "follows",
        "newest": "newest",
        "updated": "updated",
    }
    params = {
        "limit": 30,
        "facets": json.dumps(facets),
        "index": index_mapping.get(sort_by, "relevance"),
    }
    if query.strip():
        params["query"] = query.strip()
    url = f"{MODRINTH_BASE}/search?{urllib.parse.urlencode(params)}"
    payload = _request_json(url, headers=_modrinth_headers())
    results: list[dict] = []
    hits = payload.get("hits", []) if isinstance(payload, dict) else []
    for item in hits:
        project_id = str(item.get("project_id") or "")
        if not project_id:
            continue
        if release_channel != "all":
            try:
                if not _modrinth_project_has_channel_match(
                    project_id,
                    mc_versions,
                    loaders,
                    release_channel,
                ):
                    continue
            except Exception:
                continue
        results.append(
            {
                "id": project_id,
                "title": item.get("title"),
                "description": item.get("description"),
                "downloads": item.get("downloads"),
                "followers": item.get("follows"),
                "icon_url": item.get("icon_url"),
                "updated_at": item.get("date_modified"),
                "author": item.get("author"),
                "categories": item.get("categories") or [],
                "project_url": f"https://modrinth.com/{content_type}/{item.get('slug') or project_id}",
                "provider": "modrinth",
            }
        )
    return results


def list_modrinth_versions(
    project_id: str,
    mc_version: str | None,
    loader: str | None,
    release_channel: str = "all",
) -> list[dict]:
    release_channel = _normalize_release_channel(release_channel)
    mc_versions = _normalize_mc_version_list(mc_version)
    loaders = _normalize_loader_list(loader)
    params: dict[str, str] = {}
    if mc_versions:
        params["game_versions"] = json.dumps(mc_versions)
    if loaders:
        params["loaders"] = json.dumps(loaders)
    query = urllib.parse.urlencode(params)
    url = f"{MODRINTH_BASE}/project/{project_id}/version"
    if query:
        url = f"{url}?{query}"
    payload = _request_json(url, headers=_modrinth_headers())
    versions: list[dict] = []
    items = payload if isinstance(payload, list) else []
    for item in items:
        channel = str(item.get("version_type") or "release").lower()
        if not _matches_release_channel(channel, release_channel):
            continue
        versions.append(
            {
                "id": item.get("id"),
                "name": item.get("name"),
                "version_number": item.get("version_number"),
                "date": item.get("date_published"),
                "release_channel": channel,
            }
        )
    versions.sort(key=lambda item: str(item.get("date") or ""), reverse=True)
    return versions


def install_modrinth(
    db: Session,
    server: Server,
    project_id: str,
    version_id: str,
    content_type: str,
    user_id: int | None,
    _processing: set[tuple[str, str]] | None = None,
    _dependency_depth: int = 0,
    _is_dependency: bool = False,
    _auto_installed: list[InstalledContent] | None = None,
) -> InstalledContent:
    normalized_project_id = str(project_id).strip()
    normalized_version_id = str(version_id).strip()
    if not normalized_project_id or not normalized_version_id:
        raise ValueError("project_id und version_id erforderlich.")
    if _dependency_depth > _MAX_DEPENDENCY_DEPTH:
        raise ValueError("Abhaengigkeitskette zu tief. Bitte manuell pruefen.")

    processing = _processing if _processing is not None else set()
    stack_key = ("modrinth", normalized_project_id)
    if stack_key in processing:
        existing_entry = _find_installed_entry(
            db,
            server,
            provider_name="modrinth",
            project_id=normalized_project_id,
            content_type=content_type,
        )
        if existing_entry is not None:
            return existing_entry
        raise ValueError(f"Abhaengigkeitszyklus erkannt (Modrinth: {normalized_project_id}).")

    processing.add(stack_key)
    try:
        url = f"{MODRINTH_BASE}/version/{normalized_version_id}"
        payload = _request_json(url, headers=_modrinth_headers())

        payload_project_id = str(payload.get("project_id") or "").strip()
        if payload_project_id:
            normalized_project_id = payload_project_id

        if content_type == "mod":
            dependencies = _modrinth_required_dependencies(payload if isinstance(payload, dict) else {})
            expected_loader = _expected_server_loader(server, content_type)
            expected_mc_version = _expected_server_mc_version(server)
            for dependency in dependencies:
                dep_project_id = str(dependency.get("project_id") or "").strip()
                dep_version_id = str(dependency.get("version_id") or "").strip()

                if dep_version_id and not dep_project_id:
                    dep_payload = _request_json(
                        f"{MODRINTH_BASE}/version/{dep_version_id}",
                        headers=_modrinth_headers(),
                    )
                    dep_project_id = str(dep_payload.get("project_id") or "").strip()
                if not dep_project_id:
                    continue

                existing_dep = _find_installed_entry(
                    db,
                    server,
                    provider_name="modrinth",
                    project_id=dep_project_id,
                    content_type=content_type,
                )
                if existing_dep is not None and (
                    not dep_version_id or str(existing_dep.external_version_id) == dep_version_id
                ):
                    continue

                if not dep_version_id:
                    dep_versions = list_modrinth_versions(
                        dep_project_id,
                        expected_mc_version,
                        expected_loader,
                        release_channel="all",
                    )
                    if not dep_versions:
                        raise ValueError(
                            f"Abhaengigkeit konnte nicht aufgeloest werden (Modrinth: {dep_project_id})."
                        )
                    dep_version_id = str(dep_versions[0].get("id") or "").strip()
                    if not dep_version_id:
                        raise ValueError(
                            f"Abhaengigkeit ohne installierbare Version (Modrinth: {dep_project_id})."
                        )

                install_modrinth(
                    db,
                    server,
                    dep_project_id,
                    dep_version_id,
                    content_type,
                    user_id,
                    _processing=processing,
                    _dependency_depth=_dependency_depth + 1,
                    _is_dependency=True,
                    _auto_installed=_auto_installed,
                )

        version_loaders = {
            loader
            for loader in (
                _normalize_loader(str(entry or "")) for entry in (payload.get("loaders") or [])
            )
            if loader
        }
        version_mc_versions = {
            str(entry).strip()
            for entry in (payload.get("game_versions") or [])
            if str(entry).strip()
        }
        _raise_if_incompatible_with_server(
            server,
            content_type,
            provider_name="Modrinth",
            available_loaders=version_loaders,
            available_mc_versions=version_mc_versions,
        )
        files = payload.get("files", [])
        if not files:
            raise ValueError("Keine Dateien fuer diese Version gefunden.")
        primary = next((item for item in files if item.get("primary")), files[0])
        file_url = primary.get("url")
        file_name = _safe_file_name(str(primary.get("filename") or ""))
        if not file_url or not file_name:
            raise ValueError("Download-URL fehlt.")

        _remove_existing_project_entries(
            db,
            server,
            provider_name="modrinth",
            project_id=normalized_project_id,
            content_type=content_type,
        )

        target = _content_file_path(server, content_type, file_name)
        try:
            _download_file(file_url, target, headers=_modrinth_headers())
        except Exception as exc:
            raise ValueError(f"Download fehlgeschlagen: {exc}") from exc

        project = _request_json(
            f"{MODRINTH_BASE}/project/{normalized_project_id}",
            headers=_modrinth_headers(),
        )

        entry = InstalledContent(
            server_id=server.id,
            provider_name="modrinth",
            content_type=content_type,
            external_project_id=normalized_project_id,
            external_version_id=normalized_version_id,
            name=project.get("title") or normalized_project_id,
            version_label=payload.get("version_number"),
            file_name=file_name,
            installed_by_user_id=user_id,
        )
        db.add(entry)
        db.commit()
        db.refresh(entry)

        audit_service.log_action(
            db,
            action="content.install",
            user_id=user_id,
            server_id=server.id,
            details=(
                f"provider=modrinth project={normalized_project_id} "
                f"version={normalized_version_id} dependency={int(_is_dependency)}"
            ),
        )
        if _is_dependency and _auto_installed is not None:
            _auto_installed.append(entry)
        return entry
    finally:
        processing.discard(stack_key)


def _curseforge_loader_type(loader: str | None, content_type: str) -> int | None:
    loader = _normalize_loader(loader)
    if content_type == "modpack":
        mapping = {"forge": 1, "fabric": 4, "quilt": 5, "neoforge": 6}
        return mapping.get(loader)
    if content_type == "plugin":
        mapping = {"paper": 2, "spigot": 3, "bukkit": 2}
        if loader in mapping:
            return mapping[loader]
        return 2
    mapping = {"forge": 1, "fabric": 4, "quilt": 5, "neoforge": 6}
    if loader in mapping:
        return mapping[loader]
    return None


def _curseforge_class_id(content_type: str) -> int:
    normalized = (content_type or "mod").strip().lower()
    return _CURSEFORGE_CLASS_IDS.get(normalized, _CURSEFORGE_CLASS_IDS["mod"])


def _curseforge_sort_field(sort_by: str, *, has_query: bool) -> int | None:
    normalized = _normalize_sort_by(sort_by)
    if normalized == "relevance" and has_query:
        return None
    mapping = {
        "relevance": 2,
        "popularity": 2,
        "updated": 3,
        "newest": 3,
        "downloads": 6,
    }
    return mapping.get(normalized, 2)


def list_modrinth_categories(content_type: str) -> list[dict]:
    allowed_project_types = set(_modrinth_project_types_for_content_type(content_type))
    payload = _request_json(f"{MODRINTH_BASE}/tag/category", headers=_modrinth_headers())
    categories: list[dict] = []
    items = payload if isinstance(payload, list) else []
    for item in items:
        if not isinstance(item, dict):
            continue
        project_type = str(item.get("project_type") or "").strip().lower()
        if project_type not in allowed_project_types:
            continue
        slug = str(item.get("name") or "").strip()
        if not slug:
            continue
        display_label = str(item.get("display_name") or slug).strip()
        if not display_label:
            continue
        label = display_label.replace("_", " ").replace("-", " ").strip()
        if label == label.lower():
            label = label.title()
        categories.append(
            {
                "id": slug,
                "label": label,
                "provider": "modrinth",
            }
        )
    categories.sort(key=lambda item: str(item.get("label") or "").lower())
    return categories


def list_curseforge_categories(content_type: str) -> list[dict]:
    class_id = _curseforge_class_id(content_type)
    params = {
        "gameId": str(MC_GAME_ID),
        "classId": str(class_id),
    }
    url = f"{CURSEFORGE_BASE}/v1/categories?{urllib.parse.urlencode(params)}"
    payload = _request_json(url, headers=_curseforge_headers())
    data = payload.get("data", []) if isinstance(payload, dict) else []
    categories: list[dict] = []
    seen_ids: set[int] = set()
    for item in data:
        if not isinstance(item, dict):
            continue
        try:
            category_id = int(item.get("id") or 0)
        except (TypeError, ValueError):
            continue
        name = str(item.get("name") or "").strip()
        if not name or category_id <= 0:
            continue
        if category_id in seen_ids:
            continue
        seen_ids.add(category_id)
        categories.append(
            {
                "id": str(category_id),
                "label": name,
                "provider": "curseforge",
            }
        )
    categories.sort(key=lambda item: str(item.get("label") or "").lower())
    return categories


def list_modrinth_game_versions() -> list[str]:
    payload = _request_json(f"{MODRINTH_BASE}/tag/game_version", headers=_modrinth_headers())
    values: list[str] = []
    items = payload if isinstance(payload, list) else []
    for item in items:
        if not isinstance(item, dict):
            continue
        version = str(item.get("version") or "").strip()
        version_type = str(item.get("version_type") or "").strip().lower()
        if not version:
            continue
        # Fuer die UI primär stabile Versionen.
        if version_type and version_type not in {"release", "old_beta", "old_alpha"}:
            continue
        values.append(version)
    seen: set[str] = set()
    deduped: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        deduped.append(value)
    return deduped[:300]


def list_modrinth_loader_types() -> list[str]:
    payload = _request_json(f"{MODRINTH_BASE}/tag/loader", headers=_modrinth_headers())
    values: list[str] = []
    items = payload if isinstance(payload, list) else []
    for item in items:
        if not isinstance(item, dict):
            continue
        loader = str(item.get("name") or "").strip().lower()
        normalized = _normalize_loader(loader)
        if normalized:
            values.append(normalized)
    preferred_order = ["forge", "neoforge", "fabric", "quilt", "paper", "spigot", "bukkit"]
    seen: set[str] = set()
    deduped: list[str] = []
    for item in preferred_order:
        if item in values and item not in seen:
            seen.add(item)
            deduped.append(item)
    for item in values:
        if item in seen:
            continue
        seen.add(item)
        deduped.append(item)
    return deduped


def list_curseforge_game_versions() -> list[str]:
    payload = _request_json(f"{CURSEFORGE_BASE}/v1/minecraft/version", headers=_curseforge_headers())
    data = payload.get("data", []) if isinstance(payload, dict) else []
    values: list[str] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        version = str(item.get("versionString") or "").strip()
        if not version:
            continue
        values.append(version)
    seen: set[str] = set()
    deduped: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        deduped.append(value)
    return deduped[:300]


def list_curseforge_loader_types(content_type: str) -> list[str]:
    normalized_content_type = (content_type or "mod").strip().lower()
    if normalized_content_type == "plugin":
        return ["paper", "spigot", "bukkit"]

    payload = _request_json(f"{CURSEFORGE_BASE}/v1/minecraft/modloader", headers=_curseforge_headers())
    data = payload.get("data", []) if isinstance(payload, dict) else []
    values: list[str] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or "").strip().lower()
        normalized = _normalize_loader(name)
        if normalized:
            values.append(normalized)
    # CurseForge can return many version-specific names and, depending on endpoint behavior,
    # sometimes only a subset. Keep canonical loader families always selectable.
    preferred_order = ["forge", "neoforge", "fabric", "quilt", "paper", "spigot", "bukkit"]
    required_families = ["forge", "neoforge", "fabric", "quilt"]
    for required in required_families:
        if required not in values:
            values.append(required)
    seen: set[str] = set()
    deduped: list[str] = []
    for item in preferred_order:
        if item in values and item not in seen:
            seen.add(item)
            deduped.append(item)
    for item in values:
        if item in seen:
            continue
        seen.add(item)
        deduped.append(item)
    return deduped


def list_bukkit_categories(content_type: str) -> list[dict]:
    if (content_type or "mod").strip().lower() != "plugin":
        return []
    payload = _request_json(f"{SPIGET_BASE}/categories?size=200&page=1&sort=name", headers=_spiget_headers())
    data = payload if isinstance(payload, list) else []
    categories: list[dict] = []
    seen: set[str] = set()
    for item in data:
        if not isinstance(item, dict):
            continue
        category_id = str(item.get("id") or "").strip()
        category_name = str(item.get("name") or "").strip()
        if not category_id or not category_name:
            continue
        if category_id in seen:
            continue
        seen.add(category_id)
        categories.append(
            {
                "id": category_id,
                "label": category_name,
                "provider": "bukkit",
            }
        )
    categories.sort(key=lambda entry: str(entry.get("label") or "").lower())
    return categories


def list_bukkit_game_versions() -> list[str]:
    try:
        return list_minecraft_versions(minimum="1.7.10", channel="release", limit=300)
    except Exception:
        return []


def list_bukkit_loader_types(content_type: str) -> list[str]:
    if (content_type or "mod").strip().lower() != "plugin":
        return []
    return ["paper", "spigot", "bukkit"]


def _spiget_sort(sort_by: str) -> str:
    normalized = _normalize_sort_by(sort_by)
    mapping = {
        "relevance": "-downloads",
        "downloads": "-downloads",
        "popularity": "-likes",
        "updated": "-updateDate",
        "newest": "-releaseDate",
    }
    return mapping.get(normalized, "-downloads")


def search_bukkit(
    query: str,
    mc_version: str | None,
    loader: str | None,
    content_type: str,
    release_channel: str = "all",
    sort_by: str = "relevance",
    categories: list[str] | None = None,
) -> list[dict]:
    normalized_content_type = (content_type or "mod").strip().lower()
    if normalized_content_type != "plugin":
        return []
    release_channel = _normalize_release_channel(release_channel)
    if release_channel not in {"all", "release"}:
        return []

    mc_versions = _normalize_mc_version_list(mc_version)
    expected_mc_version = mc_versions[0] if mc_versions else ""
    category_tokens = {token for token in _normalize_categories(categories)}
    sort_value = _spiget_sort(sort_by)

    query_value = str(query or "").strip()
    if query_value:
        search_target = urllib.parse.quote(query_value, safe="")
        url = f"{SPIGET_BASE}/search/resources/{search_target}?size=200&page=1&sort={urllib.parse.quote(sort_value)}"
    else:
        url = f"{SPIGET_BASE}/resources/free?size=200&page=1&sort={urllib.parse.quote(sort_value)}"
    payload = _request_json(url, headers=_spiget_headers())
    items = payload if isinstance(payload, list) else []
    category_name_by_id: dict[str, str] = {}
    try:
        categories_payload = _request_json(
            f"{SPIGET_BASE}/categories?size=200&page=1&sort=name",
            headers=_spiget_headers(),
        )
        for category in (categories_payload if isinstance(categories_payload, list) else []):
            if not isinstance(category, dict):
                continue
            category_id = str(category.get("id") or "").strip()
            category_name = str(category.get("name") or "").strip()
            if category_id and category_name:
                category_name_by_id[category_id] = category_name
    except Exception:
        category_name_by_id = {}

    results: list[dict] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        resource_id = str(item.get("id") or "").strip()
        if not resource_id:
            continue
        if bool(item.get("premium")):
            continue
        if bool(item.get("external")):
            continue

        item_category_id = str((item.get("category") or {}).get("id") or "").strip()
        if category_tokens and (not item_category_id or item_category_id not in category_tokens):
            continue

        tested_versions = {
            str(version).strip()
            for version in (item.get("testedVersions") or [])
            if str(version).strip()
        }
        if expected_mc_version and tested_versions and not _is_mc_version_compatible(expected_mc_version, tested_versions):
            continue

        resource_name = str(item.get("name") or "").strip()
        icon_url = str((item.get("icon") or {}).get("url") or "").strip()
        if icon_url and not icon_url.startswith("http"):
            icon_url = f"https://www.spigotmc.org/{icon_url.lstrip('/')}"

        author_id = str((item.get("author") or {}).get("id") or "").strip()
        contributors = str(item.get("contributors") or "").strip()
        author_label = contributors or (f"Author #{author_id}" if author_id else "-")

        results.append(
            {
                "id": resource_id,
                "title": resource_name or f"Resource {resource_id}",
                "description": str(item.get("tag") or "").strip(),
                "downloads": item.get("downloads"),
                "followers": item.get("likes"),
                "icon_url": icon_url or None,
                "updated_at": _epoch_seconds_to_iso(item.get("updateDate")),
                "author": author_label,
                "categories": (
                    [category_name_by_id.get(item_category_id, f"Category {item_category_id}")]
                    if item_category_id
                    else []
                ),
                "project_url": _build_spiget_project_url(item),
                "provider": "bukkit",
            }
        )
        if len(results) >= _SEARCH_RESULT_CAP:
            break

    return results


def list_bukkit_versions(
    resource_id: int,
    mc_version: str | None,
    loader: str | None,
    release_channel: str = "all",
) -> list[dict]:
    del loader  # Bukkit/Spigot resources are plugin-only in this provider.
    normalized_channel = _normalize_release_channel(release_channel)
    if normalized_channel not in {"all", "release"}:
        return []

    expected_mc = str(mc_version or "").strip()
    detail_payload = _request_json(f"{SPIGET_BASE}/resources/{int(resource_id)}", headers=_spiget_headers())
    if not isinstance(detail_payload, dict):
        return []
    tested_versions = {
        str(version).strip()
        for version in (detail_payload.get("testedVersions") or [])
        if str(version).strip()
    }
    if expected_mc and tested_versions and not _is_mc_version_compatible(expected_mc, tested_versions):
        return []

    payload = _request_json(
        f"{SPIGET_BASE}/resources/{int(resource_id)}/versions?size=100&page=1&sort=-id",
        headers=_spiget_headers(),
    )
    items = payload if isinstance(payload, list) else []
    versions: list[dict] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        version_id = str(item.get("id") or "").strip()
        if not version_id:
            continue
        name = str(item.get("name") or "").strip() or version_id
        versions.append(
            {
                "id": version_id,
                "name": name,
                "version_number": name,
                "date": _epoch_seconds_to_iso(item.get("releaseDate")),
                "release_channel": "release",
            }
        )
    versions.sort(key=lambda entry: str(entry.get("date") or ""), reverse=True)
    return versions


def search_curseforge(
    query: str,
    mc_version: str | None,
    loader: str | None,
    content_type: str,
    release_channel: str = "all",
    sort_by: str = "relevance",
    categories: list[str] | None = None,
) -> list[dict]:
    release_channel = _normalize_release_channel(release_channel)
    sort_by = _normalize_sort_by(sort_by)
    mc_versions = _normalize_mc_version_list(mc_version)
    loaders = _normalize_loader_list(loader)
    category_tokens = _normalize_categories(categories)
    has_query = bool(query.strip())
    query_variants = _build_curseforge_query_variants(query) if has_query else [""]

    page_size = 50
    if has_query and sort_by == "relevance":
        max_pages_per_variant = 8 if len(query.strip()) >= 6 else 6
    else:
        max_pages_per_variant = 2
    base_params: dict[str, str] = {
        "gameId": str(MC_GAME_ID),
        "pageSize": str(page_size),
    }
    base_params["classId"] = str(_curseforge_class_id(content_type))
    sort_field = _curseforge_sort_field(sort_by, has_query=has_query)
    if sort_field is not None:
        base_params["sortField"] = str(sort_field)
        base_params["sortOrder"] = "desc"

    category_ids: list[int | None] = [None]
    parsed_categories: list[int] = []
    for token in category_tokens:
        try:
            parsed_categories.append(int(token))
        except ValueError:
            continue
    if parsed_categories:
        category_ids = parsed_categories

    mc_candidates: list[str | None] = [None]
    if mc_versions:
        mc_candidates = mc_versions[:4]

    loader_candidates: list[str | None] = [None]
    if loaders:
        loader_candidates = loaders[:4]

    query_tokens = [
        token for token in _tokenize_search_text(query) if token not in _SEARCH_STOPWORDS
    ]
    scored_by_id: dict[int, tuple[float, dict]] = {}
    ordered_items: list[dict] = []
    seen_ordered_ids: set[int] = set()

    for category_id in category_ids:
        for mc_candidate in mc_candidates:
            for loader_candidate in loader_candidates:
                loader_type = _curseforge_loader_type(loader_candidate, content_type)
                for variant_index, variant in enumerate(query_variants):
                    variant_tokens = [
                        token for token in _tokenize_search_text(variant) if token not in _SEARCH_STOPWORDS
                    ]
                    pages_for_variant = max_pages_per_variant
                    if has_query and sort_by == "relevance" and variant_index > 0:
                        pages_for_variant = max(2, max_pages_per_variant - 2)
                    for page in range(pages_for_variant):
                        params = dict(base_params)
                        if mc_candidate:
                            params["gameVersion"] = mc_candidate
                        if loader_type is not None:
                            params["modLoaderType"] = str(loader_type)
                        if has_query and variant:
                            params["searchFilter"] = variant
                        if category_id is not None:
                            params["categoryId"] = str(category_id)
                        params["index"] = str(page * page_size)
                        url = f"{CURSEFORGE_BASE}/v1/mods/search?{urllib.parse.urlencode(params)}"
                        payload = _request_json(url, headers=_curseforge_headers())
                        data = payload.get("data", []) if isinstance(payload, dict) else []
                        if not data:
                            break
                        for item in data:
                            mod_id_raw = item.get("id")
                            try:
                                mod_id = int(mod_id_raw)
                            except (TypeError, ValueError):
                                continue

                            if has_query and sort_by == "relevance":
                                score = _score_curseforge_item(
                                    item,
                                    query=variant or query,
                                    query_tokens=variant_tokens or query_tokens,
                                    base_query=query,
                                    base_query_tokens=query_tokens,
                                    variant_index=variant_index,
                                )
                                existing = scored_by_id.get(mod_id)
                                if existing is None or score > existing[0]:
                                    scored_by_id[mod_id] = (score, item)
                                continue

                            if mod_id in seen_ordered_ids:
                                continue
                            seen_ordered_ids.add(mod_id)
                            ordered_items.append(item)
                        if len(data) < page_size and not (has_query and sort_by == "relevance"):
                            break

    results: list[dict] = []

    def _build_project_url(mod_id: int, item: dict) -> str:
        links = item.get("links") or {}
        if isinstance(links, dict):
            website = str(links.get("websiteUrl") or "").strip()
            if website:
                return website
        slug = str(item.get("slug") or "").strip()
        if content_type == "plugin":
            base = "https://www.curseforge.com/minecraft/bukkit-plugins"
        elif content_type == "modpack":
            base = "https://www.curseforge.com/minecraft/modpacks"
        else:
            base = "https://www.curseforge.com/minecraft/mc-mods"
        if slug:
            return f"{base}/{slug}"
        return f"{base}/{mod_id}"

    if has_query and sort_by == "relevance":
        candidates = [item for _, (_, item) in sorted(scored_by_id.items(), key=lambda entry: entry[1][0], reverse=True)]
    else:
        candidates = ordered_items

    release_check_mc = mc_versions[0] if len(mc_versions) == 1 else None
    release_check_loader = loaders[0] if len(loaders) == 1 else None

    for item in candidates:
        try:
            mod_id = int(item.get("id") or 0)
        except (TypeError, ValueError):
            continue
        if mod_id <= 0:
            continue
        if release_channel != "all":
            try:
                if not _curseforge_project_has_channel_match(
                    mod_id,
                    release_check_mc,
                    release_check_loader,
                    content_type,
                    release_channel,
                ):
                    continue
            except Exception:
                continue
        authors: list[str] = []
        for author in item.get("authors") or []:
            if not isinstance(author, dict):
                continue
            name = str(author.get("name") or "").strip()
            if name:
                authors.append(name)
        category_labels: list[str] = []
        for category in item.get("categories") or []:
            if not isinstance(category, dict):
                continue
            label = str(category.get("name") or "").strip()
            if label:
                category_labels.append(label)
        results.append(
            {
                "id": mod_id,
                "title": item.get("name"),
                "description": item.get("summary"),
                "downloads": item.get("downloadCount"),
                "followers": item.get("thumbsUpCount"),
                "icon_url": (item.get("logo") or {}).get("thumbnailUrl") or (item.get("logo") or {}).get("url"),
                "updated_at": item.get("dateModified"),
                "author": ", ".join(authors),
                "categories": category_labels,
                "project_url": _build_project_url(mod_id, item),
                "provider": "curseforge",
            }
        )
        if len(results) >= _SEARCH_RESULT_CAP:
            break
    return results


def list_curseforge_versions(
    mod_id: int,
    mc_version: str | None,
    loader: str | None,
    content_type: str,
    release_channel: str = "all",
) -> list[dict]:
    release_channel = _normalize_release_channel(release_channel)
    mc_versions = _normalize_mc_version_list(mc_version)
    loaders = _normalize_loader_list(loader)
    mc_candidates: list[str | None] = [None]
    if mc_versions:
        mc_candidates = mc_versions[:4]
    loader_candidates: list[str | None] = [None]
    if loaders:
        loader_candidates = loaders[:4]

    versions: list[dict] = []
    seen_ids: set[str] = set()
    for mc_candidate in mc_candidates:
        for loader_candidate in loader_candidates:
            params: dict[str, str] = {}
            if mc_candidate:
                params["gameVersion"] = mc_candidate
            loader_type = _curseforge_loader_type(loader_candidate, content_type)
            if loader_type is not None:
                params["modLoaderType"] = str(loader_type)
            url = f"{CURSEFORGE_BASE}/v1/mods/{mod_id}/files"
            if params:
                url = f"{url}?{urllib.parse.urlencode(params)}"
            payload = _request_json(url, headers=_curseforge_headers())
            data = payload.get("data", []) if isinstance(payload, dict) else []
            for item in data:
                channel = _curseforge_release_channel(item.get("releaseType"))
                if not _matches_release_channel(channel, release_channel):
                    continue
                version_id = str(item.get("id") or "")
                if version_id in seen_ids:
                    continue
                seen_ids.add(version_id)
                versions.append(
                    {
                        "id": item.get("id"),
                        "name": item.get("displayName") or item.get("fileName"),
                        "version_number": item.get("displayName") or item.get("fileName"),
                        "date": item.get("fileDate"),
                        "release_channel": channel,
                    }
                )
    versions.sort(key=lambda item: str(item.get("date") or ""), reverse=True)
    return versions


def install_curseforge(
    db: Session,
    server: Server,
    mod_id: int,
    file_id: int,
    content_type: str,
    user_id: int | None,
    resolve_dependencies: bool = True,
    enforce_compatibility: bool = True,
    keep_existing_dependency_version: bool = False,
    client_filter_fallback: bool = False,
    _processing: set[tuple[str, str]] | None = None,
    _dependency_depth: int = 0,
    _is_dependency: bool = False,
    _auto_installed: list[InstalledContent] | None = None,
) -> InstalledContent:
    try:
        normalized_mod_id = int(mod_id)
        normalized_file_id = int(file_id)
    except (TypeError, ValueError):
        raise ValueError("mod_id und file_id muessen numerisch sein.")
    if normalized_mod_id <= 0 or normalized_file_id <= 0:
        raise ValueError("mod_id und file_id muessen > 0 sein.")
    if _dependency_depth > _MAX_DEPENDENCY_DEPTH:
        raise ValueError("Abhaengigkeitskette zu tief. Bitte manuell pruefen.")

    processing = _processing if _processing is not None else set()
    stack_key = ("curseforge", str(normalized_mod_id))
    if stack_key in processing:
        existing_entry = _find_installed_entry(
            db,
            server,
            provider_name="curseforge",
            project_id=str(normalized_mod_id),
            content_type=content_type,
        )
        if existing_entry is not None:
            return existing_entry
        raise ValueError(f"Abhaengigkeitszyklus erkannt (CurseForge: {normalized_mod_id}).")

    processing.add(stack_key)
    try:
        mod_payload = _request_json(
            f"{CURSEFORGE_BASE}/v1/mods/{normalized_mod_id}",
            headers=_curseforge_headers(),
        )
        mod_data = mod_payload.get("data", {}) if isinstance(mod_payload, dict) else {}
        project_class_id = int(mod_data.get("classId") or 0) if isinstance(mod_data, dict) else 0
        if content_type == "mod" and project_class_id != _CURSEFORGE_CLASS_IDS["mod"]:
            raise ValueError(
                f"Nicht serverrelevanter CurseForge-Inhalt (classId={project_class_id})."
            )
        if (
            client_filter_fallback
            and content_type == "mod"
            and isinstance(mod_data, dict)
            and _is_known_client_only_curseforge_mod(mod_data)
        ):
            raise ValueError("Client-only CurseForge-Inhalt (bekanntes Client-Mod).")

        file_payload = _request_json(
            f"{CURSEFORGE_BASE}/v1/mods/{normalized_mod_id}/files/{normalized_file_id}",
            headers=_curseforge_headers(),
        )
        data = file_payload.get("data", {})
        if not isinstance(data, dict):
            raise ValueError("Ungueltige CurseForge Dateidaten.")

        if content_type == "mod" and resolve_dependencies:
            dependencies = _curseforge_required_dependencies(data)
            expected_loader = _expected_server_loader(server, content_type)
            expected_mc_version = _expected_server_mc_version(server)
            for dependency in dependencies:
                dep_mod_id_raw = dependency.get("mod_id")
                dep_file_id_raw = dependency.get("file_id")
                try:
                    dep_mod_id = int(dep_mod_id_raw or 0)
                except (TypeError, ValueError):
                    continue
                dep_file_id: int | None
                try:
                    dep_file_id = int(dep_file_id_raw) if dep_file_id_raw is not None else None
                except (TypeError, ValueError):
                    dep_file_id = None
                if dep_mod_id <= 0:
                    continue
                if dep_file_id is not None and dep_file_id <= 0:
                    dep_file_id = None

                existing_dep = _find_installed_entry(
                    db,
                    server,
                    provider_name="curseforge",
                    project_id=str(dep_mod_id),
                    content_type=content_type,
                )
                if existing_dep is not None and keep_existing_dependency_version:
                    continue
                if existing_dep is not None and (
                    dep_file_id is None or str(existing_dep.external_version_id) == str(dep_file_id)
                ):
                    continue

                if dep_file_id is None:
                    dep_versions = list_curseforge_versions(
                        dep_mod_id,
                        expected_mc_version,
                        expected_loader,
                        content_type,
                        release_channel="all",
                    )
                    if not dep_versions:
                        raise ValueError(
                            f"Abhaengigkeit konnte nicht aufgeloest werden (CurseForge: {dep_mod_id})."
                        )
                    try:
                        dep_file_id = int(dep_versions[0].get("id") or 0)
                    except (TypeError, ValueError):
                        dep_file_id = 0
                    if dep_file_id <= 0:
                        raise ValueError(
                            f"Abhaengigkeit ohne installierbare Version (CurseForge: {dep_mod_id})."
                        )

                install_curseforge(
                    db,
                    server,
                    dep_mod_id,
                    dep_file_id,
                    content_type,
                    user_id,
                    resolve_dependencies=True,
                    enforce_compatibility=enforce_compatibility,
                    keep_existing_dependency_version=keep_existing_dependency_version,
                    client_filter_fallback=client_filter_fallback,
                    _processing=processing,
                    _dependency_depth=_dependency_depth + 1,
                    _is_dependency=True,
                    _auto_installed=_auto_installed,
                )

        raw_game_versions = [
            str(entry).strip()
            for entry in (data.get("gameVersions") or [])
            if str(entry).strip()
        ]
        game_version_tokens = {entry.lower() for entry in raw_game_versions}
        if (
            content_type == "mod"
            and "client" in game_version_tokens
            and "server" not in game_version_tokens
        ):
            raise ValueError("Client-only CurseForge-Inhalt (nur Client-Distribution).")
        available_loaders = {
            normalized
            for normalized in (
                _normalize_loader(entry) for entry in raw_game_versions
            )
            if normalized in {"forge", "neoforge", "fabric", "quilt", "paper", "spigot", "bukkit"}
        }
        available_mc_versions = {
            entry
            for entry in raw_game_versions
            if re.match(r"^\d+\.\d+(\.\d+)?([a-zA-Z0-9._-]*)?$", entry)
        }
        if enforce_compatibility:
            _raise_if_incompatible_with_server(
                server,
                content_type,
                provider_name="CurseForge",
                available_loaders=available_loaders,
                available_mc_versions=available_mc_versions,
            )

        download_url = data.get("downloadUrl")
        file_name = _safe_file_name(str(data.get("fileName") or ""))
        if not download_url:
            try:
                url_payload = _request_json(
                    f"{CURSEFORGE_BASE}/v1/mods/{normalized_mod_id}/files/{normalized_file_id}/download-url",
                    headers=_curseforge_headers(),
                )
                download_url = (url_payload.get("data") or {}).get("url")
            except ValueError as exc:
                if "HTTP 403" in str(exc):
                    if isinstance(mod_data, dict) and mod_data.get("allowModDistribution") is False:
                        raise ValueError(
                            "Download per CurseForge API fuer dieses Projekt nicht erlaubt "
                            "(allowModDistribution=false)."
                        ) from exc
                    raise ValueError(
                        "Download-URL von CurseForge ist nicht per API verfuegbar (HTTP 403)."
                    ) from exc
                raise
        if not download_url or not file_name:
            raise ValueError("Download-URL fehlt.")

        _remove_existing_project_entries(
            db,
            server,
            provider_name="curseforge",
            project_id=str(normalized_mod_id),
            content_type=content_type,
        )

        target = _content_file_path(server, content_type, file_name)
        try:
            _download_file(download_url, target, headers=_curseforge_headers())
        except Exception as exc:
            raise ValueError(f"Download fehlgeschlagen: {exc}") from exc

        entry = InstalledContent(
            server_id=server.id,
            provider_name="curseforge",
            content_type=content_type,
            external_project_id=str(normalized_mod_id),
            external_version_id=str(normalized_file_id),
            name=mod_data.get("name") or str(normalized_mod_id),
            version_label=data.get("displayName") or data.get("fileName"),
            file_name=file_name,
            installed_by_user_id=user_id,
        )
        db.add(entry)
        db.commit()
        db.refresh(entry)

        audit_service.log_action(
            db,
            action="content.install",
            user_id=user_id,
            server_id=server.id,
            details=(
                f"provider=curseforge project={normalized_mod_id} file={normalized_file_id} "
                f"dependency={int(_is_dependency)}"
            ),
        )
        if _is_dependency and _auto_installed is not None:
            _auto_installed.append(entry)
        return entry
    finally:
        processing.discard(stack_key)


def install_bukkit(
    db: Session,
    server: Server,
    resource_id: int,
    version_id: int,
    content_type: str,
    user_id: int | None,
) -> InstalledContent:
    normalized_content_type = (content_type or "mod").strip().lower()
    if normalized_content_type != "plugin":
        raise ValueError("Bukkit-Provider unterstuetzt nur Plugins.")

    try:
        normalized_resource_id = int(resource_id)
        normalized_version_id = int(version_id)
    except (TypeError, ValueError):
        raise ValueError("resource_id und version_id muessen numerisch sein.")
    if normalized_resource_id <= 0 or normalized_version_id <= 0:
        raise ValueError("resource_id und version_id muessen > 0 sein.")

    resource_payload = _request_json(
        f"{SPIGET_BASE}/resources/{normalized_resource_id}",
        headers=_spiget_headers(),
    )
    if not isinstance(resource_payload, dict):
        raise ValueError("Ungueltige Bukkit Ressourcendaten.")
    if bool(resource_payload.get("premium")):
        raise ValueError("Premium-Plugins werden ueber Bukkit nicht unterstuetzt.")
    if bool(resource_payload.get("external")):
        raise ValueError("Extern gehostete Bukkit-Plugins werden nicht unterstuetzt.")

    tested_versions = {
        str(version).strip()
        for version in (resource_payload.get("testedVersions") or [])
        if str(version).strip()
    }
    _raise_if_incompatible_with_server(
        server,
        normalized_content_type,
        provider_name="Bukkit",
        available_loaders={"paper", "spigot", "bukkit"},
        available_mc_versions=tested_versions,
    )

    version_payload = _request_json(
        f"{SPIGET_BASE}/resources/{normalized_resource_id}/versions/{normalized_version_id}",
        headers=_spiget_headers(),
    )
    if not isinstance(version_payload, dict):
        raise ValueError("Ungueltige Bukkit Versionsdaten.")

    download_url = f"{SPIGET_BASE}/resources/{normalized_resource_id}/versions/{normalized_version_id}/download"
    resource_name = str(resource_payload.get("name") or normalized_resource_id).strip()
    version_name = str(version_payload.get("name") or normalized_version_id).strip()
    suggested_name = f"{resource_name}-{version_name}.jar"
    file_name = _safe_file_name(suggested_name)
    if not file_name.lower().endswith(".jar"):
        file_name = f"{file_name}.jar"

    _remove_existing_project_entries(
        db,
        server,
        provider_name="bukkit",
        project_id=str(normalized_resource_id),
        content_type=normalized_content_type,
    )

    target = _content_file_path(server, normalized_content_type, file_name)
    try:
        _download_file(download_url, target, headers=_spiget_headers())
    except Exception as exc:
        raise ValueError(f"Download fehlgeschlagen: {exc}") from exc

    entry = InstalledContent(
        server_id=server.id,
        provider_name="bukkit",
        content_type=normalized_content_type,
        external_project_id=str(normalized_resource_id),
        external_version_id=str(normalized_version_id),
        name=resource_name or str(normalized_resource_id),
        version_label=version_name or str(normalized_version_id),
        file_name=file_name,
        installed_by_user_id=user_id,
    )
    db.add(entry)
    db.commit()
    db.refresh(entry)

    audit_service.log_action(
        db,
        action="content.install",
        user_id=user_id,
        server_id=server.id,
        details=(
            f"provider=bukkit project={normalized_resource_id} "
            f"version={normalized_version_id}"
        ),
    )
    return entry


def list_installed_content(db: Session, server: Server) -> list[InstalledContent]:
    stmt = select(InstalledContent).where(InstalledContent.server_id == server.id)
    entries = list(db.scalars(stmt))
    valid_entries: list[InstalledContent] = []
    removed_any = False

    # Selbstheilung fuer Altbestaende:
    # Wenn ein Datei-Eintrag nicht mehr physisch existiert, wird der DB-Eintrag entfernt.
    for entry in entries:
        file_path = _content_file_path(server, entry.content_type, entry.file_name)
        if file_path.exists():
            valid_entries.append(entry)
            continue
        db.delete(entry)
        removed_any = True

    if removed_any:
        db.commit()

    return valid_entries


def delete_installed_content(db: Session, server: Server, content: InstalledContent, user_id: int | None) -> None:
    _delete_content_file(server, content.content_type, content.file_name)

    db.execute(delete(InstalledContent).where(InstalledContent.id == content.id))
    db.commit()

    audit_service.log_action(
        db,
        action="content.delete",
        user_id=user_id,
        server_id=server.id,
        details=f"provider={content.provider_name} id={content.external_project_id}",
    )


def auto_update_plugins_for_server_version(
    db: Session,
    server: Server,
    user_id: int | None,
    *,
    release_channel: str = "release",
) -> tuple[list[str], list[str]]:
    expected_mc_version = _expected_server_mc_version(server)
    expected_loader = _expected_server_loader(server, "plugin")
    if not expected_mc_version or not expected_loader:
        return [], []

    installed_items = list_installed_content(db, server)
    plugin_items = [item for item in installed_items if (item.content_type or "").strip().lower() == "plugin"]
    if not plugin_items:
        return [], []

    notes: list[str] = []
    warnings: list[str] = []
    for item in plugin_items:
        provider = (item.provider_name or "").strip().lower()
        project_id = str(item.external_project_id or "").strip()
        current_version_id = str(item.external_version_id or "").strip()
        display_name = item.name or project_id or "plugin"
        try:
            if provider == "modrinth":
                if not project_id:
                    warnings.append(f"Plugin uebersprungen ({display_name}): Modrinth Projekt-ID fehlt.")
                    continue
                versions = list_modrinth_versions(
                    project_id,
                    expected_mc_version,
                    expected_loader,
                    release_channel=release_channel,
                )
                if not versions and release_channel != "all":
                    versions = list_modrinth_versions(
                        project_id,
                        expected_mc_version,
                        expected_loader,
                        release_channel="all",
                    )
                latest = versions[0] if versions else None
                latest_version_id = str((latest or {}).get("id") or "").strip()
                latest_label = str(
                    (latest or {}).get("name")
                    or (latest or {}).get("version_number")
                    or latest_version_id
                ).strip() or latest_version_id
                if not latest_version_id or latest_version_id == current_version_id:
                    continue
                updated_entry = install_modrinth(
                    db,
                    server,
                    project_id,
                    latest_version_id,
                    "plugin",
                    user_id,
                )
                notes.append(
                    f"Plugin aktualisiert: {updated_entry.name} -> {latest_label}."
                )
                continue

            if provider == "curseforge":
                if not project_id.isdigit():
                    warnings.append(
                        f"Plugin uebersprungen ({display_name}): ungueltige CurseForge Projekt-ID."
                    )
                    continue
                versions = list_curseforge_versions(
                    int(project_id),
                    expected_mc_version,
                    expected_loader,
                    "plugin",
                    release_channel=release_channel,
                )
                if not versions and release_channel != "all":
                    versions = list_curseforge_versions(
                        int(project_id),
                        expected_mc_version,
                        expected_loader,
                        "plugin",
                        release_channel="all",
                    )
                latest = versions[0] if versions else None
                latest_version_id = str((latest or {}).get("id") or "").strip()
                latest_label = str(
                    (latest or {}).get("name")
                    or (latest or {}).get("version_number")
                    or latest_version_id
                ).strip() or latest_version_id
                if not latest_version_id or latest_version_id == current_version_id:
                    continue
                if not latest_version_id.isdigit():
                    warnings.append(
                        f"Plugin uebersprungen ({display_name}): ungueltige CurseForge Datei-ID."
                    )
                    continue
                updated_entry = install_curseforge(
                    db,
                    server,
                    int(project_id),
                    int(latest_version_id),
                    "plugin",
                    user_id,
                    resolve_dependencies=False,
                    enforce_compatibility=True,
                )
                notes.append(
                    f"Plugin aktualisiert: {updated_entry.name} -> {latest_label}."
                )
                continue

            if provider == "bukkit":
                if not project_id.isdigit():
                    warnings.append(
                        f"Plugin uebersprungen ({display_name}): ungueltige Bukkit Resource-ID."
                    )
                    continue
                versions = list_bukkit_versions(
                    int(project_id),
                    expected_mc_version,
                    expected_loader,
                    release_channel=release_channel,
                )
                if not versions and release_channel != "all":
                    versions = list_bukkit_versions(
                        int(project_id),
                        expected_mc_version,
                        expected_loader,
                        release_channel="all",
                    )
                latest = versions[0] if versions else None
                latest_version_id = str((latest or {}).get("id") or "").strip()
                latest_label = str(
                    (latest or {}).get("name")
                    or (latest or {}).get("version_number")
                    or latest_version_id
                ).strip() or latest_version_id
                if not latest_version_id or latest_version_id == current_version_id:
                    continue
                if not latest_version_id.isdigit():
                    warnings.append(
                        f"Plugin uebersprungen ({display_name}): ungueltige Bukkit Versions-ID."
                    )
                    continue
                updated_entry = install_bukkit(
                    db,
                    server,
                    int(project_id),
                    int(latest_version_id),
                    "plugin",
                    user_id,
                )
                notes.append(
                    f"Plugin aktualisiert: {updated_entry.name} -> {latest_label}."
                )
                continue

            warnings.append(
                f"Plugin uebersprungen ({display_name}): Provider '{item.provider_name}' wird nicht unterstuetzt."
            )
        except Exception as exc:
            warnings.append(f"Plugin-Update fehlgeschlagen ({display_name}): {exc}")

    return notes, warnings

