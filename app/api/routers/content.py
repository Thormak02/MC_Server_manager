from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.installed_content import InstalledContent
from app.services import content_service
from app.services.auth_service import get_current_user_from_session
from app.services.server_service import can_control_server, can_view_server, get_server_by_id
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _ensure_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    return user


def _ensure_server_access(db: Session, user, server_id: int):
    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    return server


def _parse_categories(raw: str | None) -> list[str]:
    if not raw:
        return []
    tokens = [token.strip() for token in str(raw).split(",")]
    return [token for token in tokens if token]


def _normalize_paging(offset: int, limit: int) -> tuple[int, int]:
    normalized_offset = max(0, int(offset))
    normalized_limit = max(1, min(int(limit), 50))
    return normalized_offset, normalized_limit


def _empty_search_payload(offset: int, limit: int) -> dict:
    return {
        "results": [],
        "offset": offset,
        "limit": limit,
        "total": 0,
        "has_more": False,
    }


def _is_content_type_supported_for_server(server, content_type: str) -> bool:
    normalized_content_type = (content_type or "").strip().lower()
    if normalized_content_type not in {"mod", "plugin", "modpack"}:
        return True
    return content_service._expected_server_loader(server, normalized_content_type) is not None


@router.get("/servers/{server_id}/content", response_class=HTMLResponse)
def server_content_page(request: Request, server_id: int, db: Session = Depends(get_db)):
    try:
        current_user = _ensure_user(request, db)
        if current_user is None:
            return RedirectResponse(url="/login", status_code=303)
        server = _ensure_server_access(db, current_user, server_id)
        try:
            installed = content_service.list_installed_content(db, server)
        except SQLAlchemyError as exc:
            installed = []
            push_flash(request, f"Inhalte konnten nicht geladen werden: {exc}", "error")
        default_content_type = content_service._default_content_type(server)

        context = build_context(
            request,
            current_user=current_user,
            page_title=f"Mods & Inhalte: {server.name}",
            server=server,
            installed=installed,
            default_content_type=default_content_type,
            can_manage=can_control_server(db, current_user, server),
        )
        template = templates.get_template("server_content.html")
        rendered = template.render(context)
        return HTMLResponse(rendered)
    except Exception as exc:  # pragma: no cover
        return HTMLResponse(
            f"<h1>Fehler beim Laden der Inhalte</h1><pre>{exc}</pre>",
            status_code=500,
        )


@router.get("/api/content/search", response_class=JSONResponse)
def content_search(
    request: Request,
    provider: str,
    query: str = "",
    server_id: int | None = None,
    mc_version: str | None = None,
    loader: str | None = None,
    content_type: str = "mod",
    release_channel: str = "all",
    sort_by: str = "relevance",
    categories: str | None = None,
    offset: int = 0,
    limit: int = 20,
    db: Session = Depends(get_db),
):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    provider = provider.strip().lower()
    offset, limit = _normalize_paging(offset, limit)
    if server_id is not None:
        server = _ensure_server_access(db, current_user, server_id)
        if not _is_content_type_supported_for_server(server, content_type):
            return JSONResponse(_empty_search_payload(offset, limit))
        server_loader = content_service._expected_server_loader(server, content_type)
        if server_loader:
            loader = server_loader
        server_mc_version = content_service._expected_server_mc_version(server)
        if server_mc_version:
            mc_version = server_mc_version

    if provider == "modrinth":
        try:
            results = content_service.search_modrinth(
                query,
                mc_version,
                loader,
                content_type,
                release_channel,
                sort_by,
                _parse_categories(categories),
            )
        except Exception as exc:
            return JSONResponse(status_code=400, content={"detail": str(exc)})
    elif provider == "curseforge":
        try:
            results = content_service.search_curseforge(
                query,
                mc_version,
                loader,
                content_type,
                release_channel,
                sort_by,
                _parse_categories(categories),
            )
        except ValueError as exc:
            return JSONResponse(status_code=400, content={"detail": str(exc)})
        except Exception as exc:
            return JSONResponse(
                status_code=502,
                content={"detail": f"CurseForge Suche fehlgeschlagen: {exc}"},
            )
    elif provider == "bukkit":
        try:
            results = content_service.search_bukkit(
                query,
                mc_version,
                loader,
                content_type,
                release_channel,
                sort_by,
                _parse_categories(categories),
            )
        except ValueError as exc:
            return JSONResponse(status_code=400, content={"detail": str(exc)})
    else:
        raise HTTPException(status_code=400, detail="Unknown provider")

    total = len(results)
    page = results[offset : offset + limit]
    has_more = offset + len(page) < total
    return JSONResponse(
        {
            "results": page,
            "offset": offset,
            "limit": limit,
            "total": total,
            "has_more": has_more,
        }
    )


@router.get("/api/content/categories", response_class=JSONResponse)
def content_categories(
    request: Request,
    provider: str,
    content_type: str = "mod",
    db: Session = Depends(get_db),
):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    normalized_provider = provider.strip().lower()
    try:
        if normalized_provider == "modrinth":
            categories = content_service.list_modrinth_categories(content_type)
        elif normalized_provider == "curseforge":
            categories = content_service.list_curseforge_categories(content_type)
        elif normalized_provider == "bukkit":
            categories = content_service.list_bukkit_categories(content_type)
        else:
            return JSONResponse(status_code=400, content={"detail": "Unknown provider"})
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    return JSONResponse({"categories": categories})


@router.get("/api/content/filter-options", response_class=JSONResponse)
def content_filter_options(
    request: Request,
    provider: str,
    content_type: str = "mod",
    server_id: int | None = None,
    db: Session = Depends(get_db),
):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    normalized_provider = provider.strip().lower()
    server = None
    if server_id is not None:
        server = _ensure_server_access(db, current_user, server_id)
    try:
        if normalized_provider == "modrinth":
            categories = content_service.list_modrinth_categories(content_type)
            mc_versions = content_service.list_modrinth_game_versions()
            loaders = content_service.list_modrinth_loader_types()
        elif normalized_provider == "curseforge":
            categories = content_service.list_curseforge_categories(content_type)
            mc_versions = content_service.list_curseforge_game_versions()
            loaders = content_service.list_curseforge_loader_types(content_type)
        elif normalized_provider == "bukkit":
            categories = content_service.list_bukkit_categories(content_type)
            mc_versions = content_service.list_bukkit_game_versions()
            loaders = content_service.list_bukkit_loader_types(content_type)
        else:
            return JSONResponse(status_code=400, content={"detail": "Unknown provider"})
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    except Exception as exc:
        return JSONResponse(
            status_code=502,
            content={"detail": f"Filter-Optionen konnten nicht geladen werden: {exc}"},
        )

    if server is not None:
        if not _is_content_type_supported_for_server(server, content_type):
            return JSONResponse(
                {
                    "categories": [],
                    "mc_versions": [],
                    "loaders": [],
                }
            )
        server_loader = content_service._expected_server_loader(server, content_type)
        if server_loader:
            loaders = [server_loader]
        server_mc_version = content_service._expected_server_mc_version(server)
        if server_mc_version:
            mc_versions = [server_mc_version]

    return JSONResponse(
        {
            "categories": categories,
            "mc_versions": mc_versions,
            "loaders": loaders,
        }
    )


@router.get("/api/content/modrinth/versions", response_class=JSONResponse)
def modrinth_versions(
    request: Request,
    project_id: str,
    server_id: int | None = None,
    mc_version: str | None = None,
    loader: str | None = None,
    release_channel: str = "all",
    db: Session = Depends(get_db),
):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    if server_id is not None:
        server = _ensure_server_access(db, current_user, server_id)
        if not _is_content_type_supported_for_server(server, "mod"):
            return JSONResponse({"versions": []})
        server_loader = content_service._expected_server_loader(server, "mod")
        if server_loader:
            loader = server_loader
        server_mc_version = content_service._expected_server_mc_version(server)
        if server_mc_version:
            mc_version = server_mc_version

    try:
        versions = content_service.list_modrinth_versions(
            project_id,
            mc_version,
            loader,
            release_channel,
        )
    except Exception as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    return JSONResponse({"versions": versions})


@router.get("/api/content/curseforge/versions", response_class=JSONResponse)
def curseforge_versions(
    request: Request,
    project_id: int,
    server_id: int | None = None,
    mc_version: str | None = None,
    loader: str | None = None,
    content_type: str = "mod",
    release_channel: str = "all",
    db: Session = Depends(get_db),
):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    if server_id is not None:
        server = _ensure_server_access(db, current_user, server_id)
        if not _is_content_type_supported_for_server(server, content_type):
            return JSONResponse({"versions": []})
        server_loader = content_service._expected_server_loader(server, content_type)
        if server_loader:
            loader = server_loader
        server_mc_version = content_service._expected_server_mc_version(server)
        if server_mc_version:
            mc_version = server_mc_version

    try:
        versions = content_service.list_curseforge_versions(
            project_id,
            mc_version,
            loader,
            content_type,
            release_channel,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    except Exception as exc:
        return JSONResponse(
            status_code=502,
            content={"detail": f"CurseForge Versionsliste fehlgeschlagen: {exc}"},
        )
    return JSONResponse({"versions": versions})


@router.get("/api/content/bukkit/versions", response_class=JSONResponse)
def bukkit_versions(
    request: Request,
    project_id: int,
    server_id: int | None = None,
    mc_version: str | None = None,
    loader: str | None = None,
    content_type: str = "plugin",
    release_channel: str = "all",
    db: Session = Depends(get_db),
):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    if server_id is not None:
        server = _ensure_server_access(db, current_user, server_id)
        if not _is_content_type_supported_for_server(server, content_type):
            return JSONResponse({"versions": []})
        server_loader = content_service._expected_server_loader(server, content_type)
        if server_loader:
            loader = server_loader
        server_mc_version = content_service._expected_server_mc_version(server)
        if server_mc_version:
            mc_version = server_mc_version

    try:
        versions = content_service.list_bukkit_versions(
            project_id,
            mc_version,
            loader,
            release_channel,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    return JSONResponse({"versions": versions})


@router.get("/api/servers/{server_id}/content", response_class=JSONResponse)
def list_server_content(request: Request, server_id: int, db: Session = Depends(get_db)):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})
    server = _ensure_server_access(db, current_user, server_id)
    items = content_service.list_installed_content(db, server)

    payload = []
    for item in items:
        payload.append(
            {
                "id": item.id,
                "provider_name": item.provider_name,
                "content_type": item.content_type,
                "external_project_id": item.external_project_id,
                "external_version_id": item.external_version_id,
                "name": item.name,
                "version_label": item.version_label,
                "file_name": item.file_name,
                "installed_at": item.installed_at.isoformat() if item.installed_at else None,
            }
        )

    return JSONResponse({"items": payload})


@router.post("/api/servers/{server_id}/content/install", response_class=JSONResponse)
async def install_content(request: Request, server_id: int, db: Session = Depends(get_db)):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})
    server = _ensure_server_access(db, current_user, server_id)
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=403, detail="Forbidden")

    payload = await request.json()
    provider = str(payload.get("provider", "")).strip().lower()
    project_id = str(payload.get("project_id", "")).strip()
    version_id = str(payload.get("version_id", "")).strip()
    content_type = str(payload.get("content_type", "mod")).strip().lower()

    if not project_id or not version_id:
        raise HTTPException(status_code=400, detail="project_id und version_id erforderlich")

    auto_installed: list[InstalledContent] = []
    if provider == "modrinth":
        try:
            entry = content_service.install_modrinth(
                db,
                server,
                project_id,
                version_id,
                content_type,
                current_user.id,
                _auto_installed=auto_installed,
            )
        except ValueError as exc:
            db.rollback()
            return JSONResponse(status_code=400, content={"detail": str(exc)})
    elif provider == "curseforge":
        try:
            entry = content_service.install_curseforge(
                db,
                server,
                int(project_id),
                int(version_id),
                content_type,
                current_user.id,
                _auto_installed=auto_installed,
            )
        except ValueError as exc:
            db.rollback()
            return JSONResponse(status_code=400, content={"detail": str(exc)})
    elif provider == "bukkit":
        try:
            entry = content_service.install_bukkit(
                db,
                server,
                int(project_id),
                int(version_id),
                content_type,
                current_user.id,
            )
        except ValueError as exc:
            db.rollback()
            return JSONResponse(status_code=400, content={"detail": str(exc)})
    else:
        raise HTTPException(status_code=400, detail="Unknown provider")

    return JSONResponse(
        {
            "id": entry.id,
            "name": entry.name,
            "version_label": entry.version_label,
            "file_name": entry.file_name,
            "auto_installed": [
                {
                    "id": item.id,
                    "name": item.name,
                    "version_label": item.version_label,
                    "file_name": item.file_name,
                    "provider_name": item.provider_name,
                }
                for item in auto_installed
            ],
        }
    )


@router.delete("/api/servers/{server_id}/content/{content_id}", response_class=JSONResponse)
def delete_content(request: Request, server_id: int, content_id: int, db: Session = Depends(get_db)):
    current_user = _ensure_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})
    server = _ensure_server_access(db, current_user, server_id)
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=403, detail="Forbidden")

    content = db.get(InstalledContent, content_id)
    if content is None or content.server_id != server.id:
        raise HTTPException(status_code=404, detail="Content not found")

    try:
        content_service.delete_installed_content(db, server, content, current_user.id)
    except ValueError as exc:
        db.rollback()
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    return JSONResponse({"status": "ok"})
