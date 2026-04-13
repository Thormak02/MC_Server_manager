from datetime import datetime, time
from typing import Any, Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Query, Request, status
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.models.audit_log import AuditLog
from app.models.server import Server
from app.models.user import User
from app.services.auth_service import get_current_user_from_session
from app.services.log_service import (
    filter_lines,
    get_console_lines,
    get_download_log_file,
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


def _parse_iso_date(raw: str | None, *, end_of_day: bool = False) -> datetime | None:
    value = (raw or "").strip()
    if not value:
        return None
    try:
        if "T" in value:
            return datetime.fromisoformat(value)
        parsed_date = datetime.fromisoformat(value).date()
        if end_of_day:
            return datetime.combine(parsed_date, time.max)
        return datetime.combine(parsed_date, time.min)
    except ValueError:
        raise ValueError("Datum/Uhrzeit muss ISO-Format haben (YYYY-MM-DD oder YYYY-MM-DDTHH:MM).")


def _serialize_audit_entry(entry: AuditLog, *, server_name: str | None = None) -> dict[str, Any]:
    user_name = entry.user.username if entry.user else None
    return {
        "id": entry.id,
        "created_at": entry.created_at.isoformat() if entry.created_at else None,
        "action": entry.action,
        "details": entry.details,
        "user_id": entry.user_id,
        "username": user_name,
        "server_id": entry.server_id,
        "server_name": server_name,
    }


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
    level: str = "all",
    lines_limit: int = 800,
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

    safe_limit = max(100, min(lines_limit, 10000))
    available_files = list_log_files(server)
    available_keys = {str(entry.get("key")) for entry in available_files}
    selected_file = (file or "").strip() or None
    lines: list[str]
    source_label: str

    if selected_file:
        if selected_file not in available_keys:
            push_flash(request, "Logdatei nicht vorhanden.", "error")
            lines = get_console_lines(server.id, limit_lines=safe_limit)
            source_label = "Aktuelle Sitzung"
            selected_file = None
        else:
            try:
                lines, source_label = read_log_file(server, selected_file, limit_lines=safe_limit)
            except ValueError as exc:
                push_flash(request, str(exc), "error")
                lines = get_console_lines(server.id, limit_lines=safe_limit)
                source_label = "Aktuelle Sitzung"
                selected_file = None
    else:
        lines = get_console_lines(server.id, limit_lines=safe_limit)
        source_label = "Aktuelle Sitzung"

    filtered_lines = filter_lines(lines, q, level=level)
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
            log_level=level,
            lines_limit=safe_limit,
            lines=filtered_lines,
        ),
    )


@router.get("/servers/{server_id}/logs/download")
def download_log_action(
    request: Request,
    server_id: int,
    file: str = Query(...),
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

    try:
        path, filename = get_download_log_file(server, file)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/logs", status_code=303)

    return FileResponse(path=str(path), filename=filename, media_type="text/plain")


@router.get("/audit-logs", response_class=HTMLResponse)
def audit_logs_page(
    request: Request,
    limit: int = 200,
    user_id: int | None = None,
    server_id: int | None = None,
    action: str | None = None,
    q: str | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    safe_limit = max(1, min(limit, 2000))
    stmt = select(AuditLog).order_by(desc(AuditLog.created_at))
    if user_id is not None:
        stmt = stmt.where(AuditLog.user_id == user_id)
    if server_id is not None:
        stmt = stmt.where(AuditLog.server_id == server_id)
    if action and action.strip():
        stmt = stmt.where(AuditLog.action.ilike(f"%{action.strip()}%"))
    if q and q.strip():
        text = q.strip()
        stmt = stmt.where(
            AuditLog.details.ilike(f"%{text}%")
            | AuditLog.action.ilike(f"%{text}%")
        )

    try:
        from_dt = _parse_iso_date(date_from, end_of_day=False)
        to_dt = _parse_iso_date(date_to, end_of_day=True)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        from_dt = None
        to_dt = None

    if from_dt:
        stmt = stmt.where(AuditLog.created_at >= from_dt)
    if to_dt:
        stmt = stmt.where(AuditLog.created_at <= to_dt)

    entries = list(db.scalars(stmt.limit(safe_limit)).all())

    users = list(db.scalars(select(User).order_by(User.username.asc())).all())
    servers = list(db.scalars(select(Server).order_by(Server.name.asc())).all())
    server_map = {server.id: server.name for server in servers}

    return templates.TemplateResponse(
        request,
        "audit_logs.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Audit Logs",
            entries=entries,
            users=users,
            servers=servers,
            server_map=server_map,
            filter_user_id=user_id,
            filter_server_id=server_id,
            filter_action=action or "",
            filter_query=q or "",
            filter_date_from=date_from or "",
            filter_date_to=date_to or "",
            limit=safe_limit,
        ),
    )


@router.get("/api/audit-logs", response_class=JSONResponse)
def audit_logs_api(
    request: Request,
    limit: int = 200,
    user_id: int | None = None,
    server_id: int | None = None,
    action: str | None = None,
    q: str | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})
    if current_user.role != UserRole.SUPER_ADMIN.value:
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    safe_limit = max(1, min(limit, 2000))
    stmt = select(AuditLog).order_by(desc(AuditLog.created_at))
    if user_id is not None:
        stmt = stmt.where(AuditLog.user_id == user_id)
    if server_id is not None:
        stmt = stmt.where(AuditLog.server_id == server_id)
    if action and action.strip():
        stmt = stmt.where(AuditLog.action.ilike(f"%{action.strip()}%"))
    if q and q.strip():
        text = q.strip()
        stmt = stmt.where(
            AuditLog.details.ilike(f"%{text}%")
            | AuditLog.action.ilike(f"%{text}%")
        )

    try:
        from_dt = _parse_iso_date(date_from, end_of_day=False)
        to_dt = _parse_iso_date(date_to, end_of_day=True)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    if from_dt:
        stmt = stmt.where(AuditLog.created_at >= from_dt)
    if to_dt:
        stmt = stmt.where(AuditLog.created_at <= to_dt)

    entries = list(db.scalars(stmt.limit(safe_limit)).all())
    server_ids = {entry.server_id for entry in entries if entry.server_id is not None}
    server_map: dict[int, str] = {}
    if server_ids:
        server_rows = list(db.scalars(select(Server).where(Server.id.in_(server_ids))).all())
        server_map = {server.id: server.name for server in server_rows}

    payload = [
        _serialize_audit_entry(
            entry,
            server_name=server_map.get(entry.server_id) if entry.server_id is not None else None,
        )
        for entry in entries
    ]
    return JSONResponse({"items": payload, "count": len(payload)})
