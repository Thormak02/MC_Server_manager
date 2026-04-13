from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import backup_service
from app.services.auth_service import get_current_user_from_session
from app.services.server_service import can_control_server, can_view_server, get_server_by_id
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    return user


def _to_bool(raw: str | None) -> bool:
    if raw is None:
        return False
    return raw.strip().lower() in {"1", "true", "on", "yes"}


async def _safe_json_body(request: Request) -> dict[str, object]:
    try:
        payload = await request.json()
    except Exception:
        return {}
    if isinstance(payload, dict):
        return payload
    return {}


@router.get("/servers/{server_id}/backups", response_class=HTMLResponse)
def backups_page(
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

    backups = backup_service.list_backups_for_server(db, server.id)
    restore_history = backup_service.list_restore_history_for_server(db, server.id)

    return templates.TemplateResponse(
        request,
        "backups.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Backups: {server.name}",
            server=server,
            backups=backups,
            restore_history=restore_history,
            can_manage=can_control_server(db, current_user, server),
        ),
    )


@router.post("/servers/{server_id}/backups/create")
def create_backup_action(
    request: Request,
    server_id: int,
    backup_scope: Annotated[str, Form()] = "full",
    pre_action: Annotated[str, Form()] = "none",
    backup_name: Annotated[str | None, Form()] = None,
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

    try:
        backup = backup_service.create_backup(
            db,
            server=server,
            initiated_by_user_id=current_user.id,
            backup_scope=backup_scope,
            pre_action=pre_action,
            backup_type="manual",
            custom_name=(backup_name or "").strip() or None,
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/backups", status_code=303)

    push_flash(request, f"Backup erstellt: {backup.backup_name}", "success")
    return RedirectResponse(url=f"/servers/{server_id}/backups", status_code=303)


@router.get("/servers/{server_id}/backups/{backup_id}/download")
def download_backup_action(
    request: Request,
    server_id: int,
    backup_id: int,
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

    backup = backup_service.get_backup(db, backup_id)
    if backup is None or backup.server_id != server.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Backup not found")

    path = Path(backup.storage_path).expanduser().resolve()
    if not path.exists() or not path.is_file():
        push_flash(request, "Backup-Datei nicht gefunden.", "error")
        return RedirectResponse(url=f"/servers/{server_id}/backups", status_code=303)
    return FileResponse(path=str(path), filename=path.name, media_type="application/zip")


@router.post("/servers/{server_id}/backups/{backup_id}/restore")
def restore_backup_action(
    request: Request,
    server_id: int,
    backup_id: int,
    confirm_restore: Annotated[str | None, Form()] = None,
    stop_if_running: Annotated[str | None, Form()] = None,
    start_after_restore: Annotated[str | None, Form()] = None,
    notes: Annotated[str | None, Form()] = None,
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

    if not _to_bool(confirm_restore):
        push_flash(request, "Restore muss explizit bestaetigt werden.", "error")
        return RedirectResponse(url=f"/servers/{server_id}/backups", status_code=303)

    backup = backup_service.get_backup(db, backup_id)
    if backup is None or backup.server_id != server.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Backup not found")

    try:
        backup_service.restore_backup(
            db,
            server=server,
            backup=backup,
            initiated_by_user_id=current_user.id,
            stop_if_running=_to_bool(stop_if_running),
            start_after_restore=_to_bool(start_after_restore),
            notes=(notes or "").strip() or None,
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/backups", status_code=303)

    push_flash(request, "Restore erfolgreich ausgefuehrt.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/backups", status_code=303)


@router.post("/servers/{server_id}/backups/{backup_id}/delete")
def delete_backup_action(
    request: Request,
    server_id: int,
    backup_id: int,
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

    backup = backup_service.get_backup(db, backup_id)
    if backup is None or backup.server_id != server.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Backup not found")

    backup_service.delete_backup(db, backup=backup, initiated_by_user_id=current_user.id)
    push_flash(request, "Backup geloescht.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/backups", status_code=303)


@router.get("/api/servers/{server_id}/backups", response_class=JSONResponse)
def api_list_backups(
    request: Request,
    server_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_view_server(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    items = backup_service.list_backups_for_server(db, server.id)
    payload = [
        {
            "id": item.id,
            "backup_name": item.backup_name,
            "backup_type": item.backup_type,
            "storage_path": item.storage_path,
            "created_at": item.created_at.isoformat() if item.created_at else None,
            "created_by_user_id": item.created_by_user_id,
            "size_bytes": item.size_bytes,
            "status": item.status,
        }
        for item in items
    ]
    return JSONResponse({"items": payload})


@router.post("/api/servers/{server_id}/backups", response_class=JSONResponse)
async def api_create_backup(
    request: Request,
    server_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_control_server(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    payload = await _safe_json_body(request)
    scope = str(payload.get("backup_scope", "full") or "full")
    pre_action = str(payload.get("pre_action", "none") or "none")
    backup_name = str(payload.get("backup_name", "") or "").strip() or None

    try:
        backup = backup_service.create_backup(
            db,
            server=server,
            initiated_by_user_id=current_user.id,
            backup_scope=scope,
            pre_action=pre_action,
            backup_type="manual",
            custom_name=backup_name,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    return JSONResponse(
        {
            "id": backup.id,
            "backup_name": backup.backup_name,
            "status": backup.status,
            "size_bytes": backup.size_bytes,
        }
    )


@router.delete("/api/backups/{backup_id}", response_class=JSONResponse)
def api_delete_backup(
    request: Request,
    backup_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    backup = backup_service.get_backup(db, backup_id)
    if backup is None:
        return JSONResponse(status_code=404, content={"detail": "Backup not found"})

    server = get_server_by_id(db, backup.server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_control_server(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    backup_service.delete_backup(db, backup=backup, initiated_by_user_id=current_user.id)
    return JSONResponse({"status": "ok"})


@router.post("/api/backups/{backup_id}/restore", response_class=JSONResponse)
async def api_restore_backup(
    request: Request,
    backup_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    backup = backup_service.get_backup(db, backup_id)
    if backup is None:
        return JSONResponse(status_code=404, content={"detail": "Backup not found"})

    server = get_server_by_id(db, backup.server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_control_server(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    payload = await _safe_json_body(request)
    stop_if_running = bool(payload.get("stop_if_running", True))
    start_after_restore = bool(payload.get("start_after_restore", False))
    notes = str(payload.get("notes", "") or "").strip() or None

    try:
        restore = backup_service.restore_backup(
            db,
            server=server,
            backup=backup,
            initiated_by_user_id=current_user.id,
            stop_if_running=stop_if_running,
            start_after_restore=start_after_restore,
            notes=notes,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    return JSONResponse(
        {
            "id": restore.id,
            "status": restore.status,
            "restored_at": restore.restored_at.isoformat() if restore.restored_at else None,
            "notes": restore.notes,
        }
    )
