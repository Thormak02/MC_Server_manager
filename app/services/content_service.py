import json
import urllib.parse
import urllib.request
from pathlib import Path

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.installed_content import InstalledContent
from app.models.server import Server
from app.services import audit_service


MODRINTH_BASE = "https://api.modrinth.com/v2"
CURSEFORGE_BASE = "https://api.curseforge.com"
MC_GAME_ID = 432


def _request_json(url: str, headers: dict[str, str] | None = None) -> dict:
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _download_file(url: str, target: Path, headers: dict[str, str] | None = None) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=60) as resp:
        target.write_bytes(resp.read())


def _modrinth_headers() -> dict[str, str]:
    settings = get_settings()
    return {"User-Agent": settings.modrinth_user_agent}


def _curseforge_headers() -> dict[str, str]:
    settings = get_settings()
    if not settings.curseforge_api_key:
        raise ValueError("CurseForge API Key fehlt (MCSM_CURSEFORGE_API_KEY).")
    return {"x-api-key": settings.curseforge_api_key}


def _target_dir(server: Server, content_type: str) -> Path:
    folder = "mods"
    if content_type == "plugin":
        folder = "plugins"
    return Path(server.base_path) / folder


def _default_content_type(server: Server) -> str:
    if server.server_type in {"paper", "spigot"}:
        return "plugin"
    return "mod"


def search_modrinth(query: str, mc_version: str | None, loader: str | None, content_type: str) -> list[dict]:
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
    for item in payload.get("hits", []):
        results.append(
            {
                "id": item.get("project_id"),
                "title": item.get("title"),
                "description": item.get("description"),
                "downloads": item.get("downloads"),
                "icon_url": item.get("icon_url"),
                "provider": "modrinth",
            }
        )
    return results


def list_modrinth_versions(project_id: str, mc_version: str | None, loader: str | None) -> list[dict]:
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
    for item in payload:
        versions.append(
            {
                "id": item.get("id"),
                "name": item.get("name"),
                "version_number": item.get("version_number"),
                "date": item.get("date_published"),
            }
        )
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
    file_name = primary.get("filename")
    if not file_url or not file_name:
        raise ValueError("Download-URL fehlt.")

    target = _target_dir(server, content_type) / file_name
    _download_file(file_url, target, headers=_modrinth_headers())

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
    if content_type == "plugin":
        mapping = {"paper": 2, "spigot": 3, "bukkit": 2}
        if loader in mapping:
            return mapping[loader]
        return 2
    mapping = {"forge": 1, "fabric": 4, "quilt": 5, "neoforge": 6}
    if loader in mapping:
        return mapping[loader]
    return None


def search_curseforge(query: str, mc_version: str | None, loader: str | None, content_type: str) -> list[dict]:
    params: dict[str, str] = {
        "gameId": str(MC_GAME_ID),
        "searchFilter": query,
        "pageSize": "20",
    }
    if mc_version:
        params["gameVersion"] = mc_version
    loader_type = _curseforge_loader_type(loader, content_type)
    if loader_type is not None:
        params["modLoaderType"] = str(loader_type)
    if content_type == "plugin":
        params["classId"] = "5"
    else:
        params["classId"] = "6"
    url = f"{CURSEFORGE_BASE}/v1/mods/search?{urllib.parse.urlencode(params)}"
    payload = _request_json(url, headers=_curseforge_headers())
    results: list[dict] = []
    for item in payload.get("data", []):
        results.append(
            {
                "id": item.get("id"),
                "title": item.get("name"),
                "description": item.get("summary"),
                "downloads": item.get("downloadCount"),
                "icon_url": (item.get("logo") or {}).get("url"),
                "provider": "curseforge",
            }
        )
    return results


def list_curseforge_versions(mod_id: int, mc_version: str | None, loader: str | None, content_type: str) -> list[dict]:
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
    for item in payload.get("data", []):
        versions.append(
            {
                "id": item.get("id"),
                "name": item.get("displayName") or item.get("fileName"),
                "version_number": item.get("displayName") or item.get("fileName"),
                "date": item.get("fileDate"),
            }
        )
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
    file_name = data.get("fileName")
    if not download_url:
        url_payload = _request_json(
            f"{CURSEFORGE_BASE}/v1/mods/{mod_id}/files/{file_id}/download-url",
            headers=_curseforge_headers(),
        )
        download_url = (url_payload.get("data") or {}).get("url")
    if not download_url or not file_name:
        raise ValueError("Download-URL fehlt.")

    target = _target_dir(server, content_type) / file_name
    _download_file(download_url, target, headers=_curseforge_headers())

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


def list_installed_content(db: Session, server_id: int) -> list[InstalledContent]:
    stmt = select(InstalledContent).where(InstalledContent.server_id == server_id)
    return list(db.scalars(stmt))


def delete_installed_content(db: Session, server: Server, content: InstalledContent, user_id: int | None) -> None:
    target = _target_dir(server, content.content_type) / content.file_name
    if target.exists():
        try:
            target.unlink()
        except Exception:
            pass

    db.execute(delete(InstalledContent).where(InstalledContent.id == content.id))
    db.commit()

    audit_service.log_action(
        db,
        action="content.delete",
        user_id=user_id,
        server_id=server.id,
        details=f"provider={content.provider_name} id={content.external_project_id}",
    )
