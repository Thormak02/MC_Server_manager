from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import audit_service
from app.services.auth_service import get_current_user_from_session
from app.services.file_service import list_files, read_text_file, write_text_file
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

    if selected_file:
        try:
            file_content = read_text_file(server, selected_file)
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
        return RedirectResponse(url=f"/servers/{server_id}/files?file={relative_path}", status_code=303)

    audit_service.log_action(
        db,
        action="server.file_edit",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={relative_path}",
    )
    push_flash(request, f"Datei '{relative_path}' gespeichert.", "success")
    return RedirectResponse(url=f"/servers/{server_id}/files?file={relative_path}", status_code=303)
