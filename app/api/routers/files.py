from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile, status
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import audit_service
from app.services.auth_service import get_current_user_from_session
from app.services.file_service import (
    add_access_entry,
    build_content_from_assistant,
    create_directory,
    create_text_file,
    delete_path,
    get_access_schema_key_label,
    get_assistant_payload,
    get_download_file,
    get_whitelist_enabled,
    list_files,
    list_access_entries,
    read_text_file,
    remove_access_entry,
    set_whitelist_enabled,
    update_op_level,
    upload_file as upload_server_file,
    write_text_file,
)
from app.services.server_service import can_edit_server_files, can_view_server, get_server_by_id
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    return user


def _normalize_access_tab(value: str | None) -> str:
    allowed = {"whitelist", "ops", "banned_players", "banned_ips"}
    normalized = (value or "").strip().lower()
    if normalized in allowed:
        return normalized
    return "whitelist"


@router.get("/servers/{server_id}/files", response_class=HTMLResponse)
def files_page(
    request: Request,
    server_id: int,
    file: str | None = None,
    mode: str | None = None,
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

    available_files = list_files(server)
    selected_file = (file or "").strip() or None
    file_content = None
    assistant_payload = None
    selected_mode = (mode or "assistant").strip().lower()
    if selected_mode not in {"assistant", "raw"}:
        selected_mode = "assistant"

    if selected_file:
        try:
            file_content = read_text_file(server, selected_file)
            assistant_payload = get_assistant_payload(selected_file, file_content.content)
            if assistant_payload is None and selected_mode == "assistant":
                selected_mode = "raw"
        except ValueError as exc:
            push_flash(request, str(exc), "error")
            selected_file = None

    return templates.TemplateResponse(
        request,
        "files.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Dateien: {server.name}",
            server=server,
            available_files=available_files,
            selected_file=selected_file,
            file_content=file_content,
            assistant_payload=assistant_payload,
            selected_mode=selected_mode,
            can_edit=can_edit_server_files(db, current_user, server),
        ),
    )


@router.get("/servers/{server_id}/access", response_class=HTMLResponse)
def access_page(
    request: Request,
    server_id: int,
    tab: str | None = None,
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

    selected_tab = _normalize_access_tab(tab)
    whitelist_entries: list[dict] = []
    ops_entries: list[dict] = []
    banned_players_entries: list[dict] = []
    banned_ips_entries: list[dict] = []
    try:
        whitelist_entries = list_access_entries(server, "whitelist")
        ops_entries = list_access_entries(server, "ops")
        banned_players_entries = list_access_entries(server, "banned_players")
        banned_ips_entries = list_access_entries(server, "banned_ips")
    except ValueError as exc:
        push_flash(request, f"Zugriffsdaten konnten nicht gelesen werden: {exc}", "error")

    return templates.TemplateResponse(
        request,
        "server_access.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"OP/Bans/Whitelist: {server.name}",
            server=server,
            can_edit=can_edit_server_files(db, current_user, server),
            selected_tab=selected_tab,
            tab_options=get_access_schema_key_label(),
            whitelist_enabled=get_whitelist_enabled(server),
            whitelist_entries=whitelist_entries,
            ops_entries=ops_entries,
            banned_players_entries=banned_players_entries,
            banned_ips_entries=banned_ips_entries,
        ),
    )


@router.post("/servers/{server_id}/access/entry-add")
def access_add_entry_action(
    request: Request,
    server_id: int,
    list_key: Annotated[str, Form()],
    identity: Annotated[str, Form()],
    op_level: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    tab = _normalize_access_tab(list_key)
    try:
        add_access_entry(server, tab, identity, op_level=op_level if tab == "ops" else None)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)

    audit_service.log_action(
        db,
        action="server.access_entry_add",
        user_id=current_user.id,
        server_id=server.id,
        details=(
            f"list={tab} identity={identity.strip()} level={str(op_level).strip()}"
            if tab == "ops" and op_level is not None
            else f"list={tab} identity={identity.strip()}"
        ),
    )
    push_flash(request, "Eintrag hinzugefuegt.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)


@router.post("/servers/{server_id}/access/op-level-update")
def access_update_op_level_action(
    request: Request,
    server_id: int,
    identity: Annotated[str, Form()],
    op_level: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    try:
        update_op_level(server, identity, op_level)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/access?tab=ops", status_code=303)

    audit_service.log_action(
        db,
        action="server.op_level_update",
        user_id=current_user.id,
        server_id=server.id,
        details=f"identity={identity.strip()} level={op_level.strip()}",
    )
    push_flash(request, "OP-Level aktualisiert.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/access?tab=ops", status_code=303)


@router.post("/servers/{server_id}/access/entry-remove")
def access_remove_entry_action(
    request: Request,
    server_id: int,
    list_key: Annotated[str, Form()],
    identity: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    tab = _normalize_access_tab(list_key)
    try:
        remove_access_entry(server, tab, identity)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)

    audit_service.log_action(
        db,
        action="server.access_entry_remove",
        user_id=current_user.id,
        server_id=server.id,
        details=f"list={tab} identity={identity.strip()}",
    )
    push_flash(request, "Eintrag entfernt.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/access?tab={tab}", status_code=303)


@router.post("/servers/{server_id}/access/whitelist-toggle")
def access_whitelist_toggle_action(
    request: Request,
    server_id: int,
    enabled: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    target_state = str(enabled or "").strip().lower() in {"1", "true", "on", "yes"}
    set_whitelist_enabled(server, target_state)
    audit_service.log_action(
        db,
        action="server.whitelist_toggle",
        user_id=current_user.id,
        server_id=server.id,
        details=f"enabled={target_state}",
    )
    push_flash(
        request,
        "Whitelist aktiviert." if target_state else "Whitelist deaktiviert.",
        "success",
    )
    return RedirectResponse(url=f"/servers/{server_id}/access?tab=whitelist", status_code=303)


@router.post("/servers/{server_id}/files/save")
def save_file_action(
    request: Request,
    server_id: int,
    relative_path: Annotated[str, Form()],
    content: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    try:
        write_text_file(server, relative_path, content)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/files?file={relative_path}&mode=raw", status_code=303)

    audit_service.log_action(
        db,
        action="server.file_edit",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} mode=raw",
    )
    push_flash(request, f"Datei '{relative_path}' gespeichert.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/files?file={relative_path}&mode=raw", status_code=303)


@router.post("/servers/{server_id}/files/assistant-save")
async def save_file_assistant_action(
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
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    form = await request.form()
    relative_path = str(form.get("relative_path", "")).strip()
    data = {key: str(value) for key, value in form.items() if key != "relative_path"}

    try:
        content = build_content_from_assistant(relative_path, data)
        write_text_file(server, relative_path, content)
    except (ValueError, TypeError, KeyError, Exception) as exc:
        push_flash(request, f"Assistent-Speichern fehlgeschlagen: {exc}", "error")
        return RedirectResponse(
            url=f"/servers/{server_id}/files?file={relative_path}&mode=assistant",
            status_code=303,
        )

    audit_service.log_action(
        db,
        action="server.file_edit",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} mode=assistant",
    )
    push_flash(request, f"Datei '{relative_path}' via Assistent gespeichert.", "success")
    return RedirectResponse(
        url=f"/servers/{server_id}/files?file={relative_path}&mode=assistant",
        status_code=303,
    )


@router.get("/servers/{server_id}/files/download")
def download_file_action(
    request: Request,
    server_id: int,
    path: str,
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
        target = get_download_file(server, path)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)

    audit_service.log_action(
        db,
        action="server.file_download",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={Path(path).as_posix()}",
    )
    return FileResponse(path=str(target), filename=target.name, media_type="application/octet-stream")


@router.post("/servers/{server_id}/files/upload")
async def upload_file_action(
    request: Request,
    server_id: int,
    upload: UploadFile = File(...),
    target_dir: Annotated[str, Form()] = ".",
    overwrite: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    filename = (upload.filename or "").strip()
    if not filename:
        push_flash(request, "Bitte eine Datei auswaehlen.", "error")
        return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)

    try:
        payload = await upload.read()
        relative_path = upload_server_file(
            server,
            target_dir=(target_dir or ".").strip() or ".",
            original_filename=filename,
            content_bytes=payload,
            overwrite=overwrite,
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)

    audit_service.log_action(
        db,
        action="server.file_upload",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} size={len(payload)}",
    )
    push_flash(request, f"Datei hochgeladen: {relative_path}", "success")
    return RedirectResponse(url=f"/servers/{server_id}/files?file={relative_path}&mode=raw", status_code=303)


@router.post("/servers/{server_id}/directories")
def create_directory_action(
    request: Request,
    server_id: int,
    relative_dir: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    try:
        created = create_directory(server, relative_dir)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)

    audit_service.log_action(
        db,
        action="server.directory_create",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={created}",
    )
    push_flash(request, f"Ordner erstellt: {created}", "success")
    return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)


@router.post("/servers/{server_id}/files/create-text")
def create_text_file_action(
    request: Request,
    server_id: int,
    relative_path: Annotated[str, Form()],
    initial_content: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    try:
        created = create_text_file(server, relative_path, initial_content or "")
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)

    audit_service.log_action(
        db,
        action="server.file_create",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={created}",
    )
    push_flash(request, f"Textdatei erstellt: {created}", "success")
    return RedirectResponse(url=f"/servers/{server_id}/files?file={created}&mode=raw", status_code=303)


@router.post("/servers/{server_id}/files/delete")
def delete_file_action(
    request: Request,
    server_id: int,
    relative_path: Annotated[str, Form()],
    recursive: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_edit_server_files(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    try:
        deleted = delete_path(server, relative_path, recursive=recursive)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)

    audit_service.log_action(
        db,
        action="server.file_delete",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={deleted} recursive={recursive}",
    )
    push_flash(request, f"Geloescht: {deleted}", "success")
    return RedirectResponse(url=f"/servers/{server_id}/files", status_code=303)


@router.post("/api/servers/{server_id}/files/upload", response_class=JSONResponse)
async def api_upload_file_action(
    request: Request,
    server_id: int,
    upload: UploadFile = File(...),
    target_dir: Annotated[str, Form()] = ".",
    overwrite: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_edit_server_files(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    try:
        payload = await upload.read()
        relative_path = upload_server_file(
            server,
            target_dir=(target_dir or ".").strip() or ".",
            original_filename=upload.filename or "",
            content_bytes=payload,
            overwrite=overwrite,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    audit_service.log_action(
        db,
        action="server.file_upload",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path} size={len(payload)}",
    )
    return JSONResponse({"status": "ok", "path": relative_path, "size": len(payload)})


@router.delete("/api/servers/{server_id}/files", response_class=JSONResponse)
def api_delete_file_action(
    request: Request,
    server_id: int,
    path: str = Query(...),
    recursive: bool = Query(False),
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_edit_server_files(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    try:
        deleted = delete_path(server, path, recursive=recursive)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    audit_service.log_action(
        db,
        action="server.file_delete",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={deleted} recursive={recursive}",
    )
    return JSONResponse({"status": "ok", "path": deleted})


@router.post("/api/servers/{server_id}/directories", response_class=JSONResponse)
def api_create_directory_action(
    request: Request,
    server_id: int,
    relative_dir: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        return JSONResponse(status_code=404, content={"detail": "Server not found"})
    if not can_edit_server_files(db, current_user, server):
        return JSONResponse(status_code=403, content={"detail": "Forbidden"})

    try:
        created = create_directory(server, relative_dir)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    audit_service.log_action(
        db,
        action="server.directory_create",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={created}",
    )
    return JSONResponse({"status": "ok", "path": created})
