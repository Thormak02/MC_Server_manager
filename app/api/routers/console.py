from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.models.audit_log import AuditLog
from app.services.auth_service import get_current_user_from_session
from app.services.log_service import (
    filter_lines,
    get_console_lines,
    list_log_files,
    read_log_file,
)
from app.services.process_service import send_console_command
from app.services.server_service import can_control_server, can_view_server, get_server_by_id
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    return user


@router.get("/servers/{server_id}/console", response_class=HTMLResponse)
def console_page(
    request: Request,
    server_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    return templates.TemplateResponse(
        request,
        "console.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Konsole: {server.name}",
            server=server,
            recent_lines=get_console_lines(server.id, limit_lines=400),
            can_send_commands=can_control_server(db, current_user, server),
        ),
    )


@router.post("/servers/{server_id}/console/command")
def send_console_command_action(
    request: Request,
    server_id: int,
    command: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    ok, message = send_console_command(db, server, command, current_user.id)
    push_flash(request, message, "success" if ok else "error")
    return RedirectResponse(url=f"/servers/{server_id}/console", status_code=303)


@router.get("/servers/{server_id}/logs", response_class=HTMLResponse)
def logs_page(
    request: Request,
    server_id: int,
    file: str | None = None,
    q: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    available_files = list_log_files(server.id)
    selected_file = (file or "").strip() or None
    lines: list[str]
    source_label: str

    if selected_file:
        try:
            lines = read_log_file(server.id, selected_file, limit_lines=2000)
            source_label = selected_file
        except ValueError as exc:
            push_flash(request, str(exc), "error")
            lines = get_console_lines(server.id, limit_lines=800)
            source_label = "Aktuelle Sitzung"
            selected_file = None
    else:
        lines = get_console_lines(server.id, limit_lines=800)
        source_label = "Aktuelle Sitzung"

    filtered_lines = filter_lines(lines, q)
    return templates.TemplateResponse(
        request,
        "logs.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Logs: {server.name}",
            server=server,
            source_label=source_label,
            selected_file=selected_file,
            available_files=available_files,
            query=q or "",
            lines=filtered_lines,
        ),
    )


@router.get("/audit-logs", response_class=HTMLResponse)
def audit_logs_page(
    request: Request,
    limit: int = 200,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    safe_limit = max(1, min(limit, 1000))
    entries = list(
        db.scalars(select(AuditLog).order_by(desc(AuditLog.created_at)).limit(safe_limit)).all()
    )
    return templates.TemplateResponse(
        request,
        "audit_logs.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Audit Logs",
            entries=entries,
            limit=safe_limit,
        ),
    )
