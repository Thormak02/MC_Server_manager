import json
import math
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.models.installed_content import InstalledContent
from app.models.server import Server
from app.services import audit_service
from app.services.platform_settings_service import (
    get_curseforge_api_key_runtime,
    get_modrinth_user_agent_runtime,
    is_provider_enabled_runtime,
)


MODRINTH_BASE = "https://api.modrinth.com/v2"
CURSEFORGE_BASE = "https://api.curseforge.com"
MC_GAME_ID = 432
_VALID_RELEASE_CHANNELS = {"all", "release", "beta", "alpha"}
_SEARCH_STOPWORDS = {"a", "an", "and", "for", "of", "the", "to", "with"}


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


def _target_dir(server: Server, content_type: str) -> Path:
    folder = "mods"
    if content_type == "plugin":
        folder = "plugins"
    return Path(server.base_path) / folder


def _default_content_type(server: Server) -> str:
    if server.server_type in {"paper", "spigot"}:
        return "plugin"
    return "mod"


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

    deduped: list[str] = []
    seen: set[str] = set()
    for variant in variants:
        normalized = variant.strip().lower()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        deduped.append(variant)
    return deduped[:3]


def _score_curseforge_item(
    item: dict,
    *,
    query: str,
    query_tokens: list[str],
    variant_index: int,
) -> float:
    title = str(item.get("name") or "")
    slug = str(item.get("slug") or "")
    summary = str(item.get("summary") or "")

    title_norm = _normalize_search_text(title)
    slug_norm = _normalize_search_text(slug)
    query_norm = _normalize_search_text(query)

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

    summary_norm = _normalize_search_text(summary)
    title_tokens = set(_tokenize_search_text(title))
    slug_tokens = set(_tokenize_search_text(slug))
    summary_tokens = set(_tokenize_search_text(summary))

    for token in query_tokens:
        if token in title_tokens:
            score += 850
        elif token in slug_tokens:
            score += 500
        elif token in summary_tokens:
            score += 90

    if query_tokens and all(token in (title_norm + " " + slug_norm) for token in query_tokens):
        score += 2500

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


def _modrinth_project_has_channel_match(
    project_id: str,
    mc_version: str | None,
    loader: str | None,
    release_channel: str,
) -> bool:
    params: dict[str, str] = {}
    if mc_version:
        params["game_versions"] = json.dumps([mc_version])
    if loader:
        params["loaders"] = json.dumps([loader])
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
) -> list[dict]:
    release_channel = _normalize_release_channel(release_channel)
    facets: list[list[str]] = [[f"project_type:{content_type}"]]
    if mc_version:
        facets.append([f"versions:{mc_version}"])
    if loader:
        facets.append([f"categories:{loader}"])
    params = {
        "query": query,
        "limit": 20,
        "facets": json.dumps(facets),
    }
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
                    mc_version,
                    loader,
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
                "icon_url": item.get("icon_url"),
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
    params: dict[str, str] = {}
    if mc_version:
        params["game_versions"] = json.dumps([mc_version])
    if loader:
        params["loaders"] = json.dumps([loader])
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
) -> InstalledContent:
    url = f"{MODRINTH_BASE}/version/{version_id}"
    payload = _request_json(url, headers=_modrinth_headers())
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
        project_id=str(project_id),
        content_type=content_type,
    )

    target = _content_file_path(server, content_type, file_name)
    try:
        _download_file(file_url, target, headers=_modrinth_headers())
    except Exception as exc:
        raise ValueError(f"Download fehlgeschlagen: {exc}") from exc

    project = _request_json(f"{MODRINTH_BASE}/project/{project_id}", headers=_modrinth_headers())

    entry = InstalledContent(
        server_id=server.id,
        provider_name="modrinth",
        content_type=content_type,
        external_project_id=str(project_id),
        external_version_id=str(version_id),
        name=project.get("title") or project_id,
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
        details=f"provider=modrinth project={project_id} version={version_id}",
    )
    return entry


def _curseforge_loader_type(loader: str | None, content_type: str) -> int | None:
    if content_type == "modpack":
        return None
    if content_type == "plugin":
        mapping = {"paper": 2, "spigot": 3, "bukkit": 2}
        if loader in mapping:
            return mapping[loader]
        return 2
    mapping = {"forge": 1, "fabric": 4, "quilt": 5, "neoforge": 6}
    if loader in mapping:
        return mapping[loader]
    return None


def search_curseforge(
    query: str,
    mc_version: str | None,
    loader: str | None,
    content_type: str,
    release_channel: str = "all",
) -> list[dict]:
    release_channel = _normalize_release_channel(release_channel)
    query_variants = _build_curseforge_query_variants(query)
    if not query_variants:
        return []

    page_size = 50
    max_pages_per_variant = 4

    base_params: dict[str, str] = {
        "gameId": str(MC_GAME_ID),
        "pageSize": str(page_size),
    }
    if mc_version:
        base_params["gameVersion"] = mc_version
    loader_type = _curseforge_loader_type(loader, content_type)
    if loader_type is not None:
        base_params["modLoaderType"] = str(loader_type)
    if content_type == "plugin":
        base_params["classId"] = "5"
    elif content_type == "modpack":
        base_params["classId"] = "4471"
    else:
        base_params["classId"] = "6"

    query_tokens = [
        token for token in _tokenize_search_text(query) if token not in _SEARCH_STOPWORDS
    ]
    scored_by_id: dict[int, tuple[float, dict]] = {}

    for variant_index, variant in enumerate(query_variants):
        for page in range(max_pages_per_variant):
            params = dict(base_params)
            params["searchFilter"] = variant
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
                score = _score_curseforge_item(
                    item,
                    query=query,
                    query_tokens=query_tokens,
                    variant_index=variant_index,
                )
                existing = scored_by_id.get(mod_id)
                if existing is None or score > existing[0]:
                    scored_by_id[mod_id] = (score, item)
            if len(data) < page_size:
                break

    sorted_items = sorted(scored_by_id.items(), key=lambda entry: entry[1][0], reverse=True)
    results: list[dict] = []

    for mod_id, (_, item) in sorted_items:
        if release_channel != "all":
            try:
                if not _curseforge_project_has_channel_match(
                    mod_id,
                    mc_version,
                    loader,
                    content_type,
                    release_channel,
                ):
                    continue
            except Exception:
                continue
        results.append(
            {
                "id": mod_id,
                "title": item.get("name"),
                "description": item.get("summary"),
                "downloads": item.get("downloadCount"),
                "icon_url": (item.get("logo") or {}).get("url"),
                "provider": "curseforge",
            }
        )
        if len(results) >= 40:
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
    params: dict[str, str] = {}
    if mc_version:
        params["gameVersion"] = mc_version
    loader_type = _curseforge_loader_type(loader, content_type)
    if loader_type is not None:
        params["modLoaderType"] = str(loader_type)
    url = f"{CURSEFORGE_BASE}/v1/mods/{mod_id}/files"
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    payload = _request_json(url, headers=_curseforge_headers())
    versions: list[dict] = []
    data = payload.get("data", []) if isinstance(payload, dict) else []
    for item in data:
        channel = _curseforge_release_channel(item.get("releaseType"))
        if not _matches_release_channel(channel, release_channel):
            continue
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
) -> InstalledContent:
    file_payload = _request_json(
        f"{CURSEFORGE_BASE}/v1/mods/{mod_id}/files/{file_id}",
        headers=_curseforge_headers(),
    )
    data = file_payload.get("data", {})
    download_url = data.get("downloadUrl")
    file_name = _safe_file_name(str(data.get("fileName") or ""))
    if not download_url:
        url_payload = _request_json(
            f"{CURSEFORGE_BASE}/v1/mods/{mod_id}/files/{file_id}/download-url",
            headers=_curseforge_headers(),
        )
        download_url = (url_payload.get("data") or {}).get("url")
    if not download_url or not file_name:
        raise ValueError("Download-URL fehlt.")

    _remove_existing_project_entries(
        db,
        server,
        provider_name="curseforge",
        project_id=str(mod_id),
        content_type=content_type,
    )

    target = _content_file_path(server, content_type, file_name)
    try:
        _download_file(download_url, target, headers=_curseforge_headers())
    except Exception as exc:
        raise ValueError(f"Download fehlgeschlagen: {exc}") from exc

    mod_payload = _request_json(
        f"{CURSEFORGE_BASE}/v1/mods/{mod_id}",
        headers=_curseforge_headers(),
    )
    mod_data = mod_payload.get("data", {})

    entry = InstalledContent(
        server_id=server.id,
        provider_name="curseforge",
        content_type=content_type,
        external_project_id=str(mod_id),
        external_version_id=str(file_id),
        name=mod_data.get("name") or str(mod_id),
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
        details=f"provider=curseforge project={mod_id} file={file_id}",
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

