from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile, status
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import audit_service
from app.services.auth_service import get_current_user_from_session
from app.services.file_service import (
    build_content_from_assistant,
    create_directory,
    create_text_file,
    delete_path,
    get_assistant_payload,
    get_download_file,
    list_files,
    read_text_file,
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
