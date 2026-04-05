from fastapi import APIRouter, Depends, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.auth_service import get_current_user_from_session
from app.services.process_service import get_player_counts, get_process_resource_usage, refresh_runtime_states
from app.services.resource_service import get_host_resources, get_server_resource_entries
from app.services.server_service import get_dashboard_summary, list_servers_for_user
from app.web.routes.pages import build_context, templates


router = APIRouter(include_in_schema=False)


@router.get("/dashboard", response_class=HTMLResponse)
def dashboard_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    visible_servers = list_servers_for_user(db, current_user)
    refresh_runtime_states(db, visible_servers)
    summary = get_dashboard_summary(db, current_user)

    server_runtime: dict[int, dict[str, object]] = {}
    for server in summary["servers"]:
        players_current, players_max = get_player_counts(server)
        usage = get_process_resource_usage(server.id)
        server_runtime[server.id] = {
            "players_current": players_current,
            "players_max": players_max,
            "usage": usage,
        }

    return templates.TemplateResponse(
        request,
        "dashboard.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Dashboard",
            summary=summary,
            servers=summary["servers"],
            server_runtime=server_runtime,
            host_resources=get_host_resources(),
        ),
    )


@router.get("/resources", response_class=HTMLResponse)
def resources_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    entries = get_server_resource_entries(db, current_user)
    return templates.TemplateResponse(
        request,
        "resources.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Ressourcenmonitor",
            host_resources=get_host_resources(),
            entries=entries,
        ),
    )


@router.get("/api/resources", response_class=JSONResponse)
def resources_live(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    entries = get_server_resource_entries(db, current_user)
    payload_entries: list[dict[str, object]] = []
    for row in entries:
        server = row["server"]
        usage = row["usage"]
        payload_entries.append(
            {
                "server": {
                    "id": server.id,
                    "name": server.name,
                    "status": server.status,
                },
                "players_current": row.get("players_current"),
                "players_max": row.get("players_max"),
                "memory_share_percent": row.get("memory_share_percent", 0.0),
                "usage": {
                    "cpu_percent": usage.get("cpu_percent"),
                    "memory_mb": usage.get("memory_mb"),
                    "pid": usage.get("pid"),
                    "uptime_seconds": usage.get("uptime_seconds"),
                },
            }
        )

    return JSONResponse(
        {
            "host": get_host_resources(),
            "entries": payload_entries,
        }
    )
