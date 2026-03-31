from typing import Annotated

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services import audit_service
from app.services.auth_service import (
    authenticate_credentials,
    clear_session,
    get_current_user_from_session,
    set_logged_in_session,
    touch_last_login,
)
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


@router.get("/login", response_class=HTMLResponse)
def login_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = get_current_user_from_session(request, db)
    if current_user:
        return RedirectResponse(url="/dashboard", status_code=303)
    return templates.TemplateResponse(
        request,
        "login.html",
        build_context(request, page_title="Login"),
    )


@router.post("/login")
def login_action(
    request: Request,
    username: Annotated[str, Form()],
    password: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    user = authenticate_credentials(db, username.strip(), password)

    client_host = request.client.host if request.client else "unknown"
    if user is None:
        audit_service.log_action(
            db,
            action="auth.login_failed",
            details=f"username={username.strip()} ip={client_host}",
        )
        push_flash(request, "Ungueltige Login-Daten.", "error")
        return RedirectResponse(url="/login", status_code=303)

    if not user.is_active:
        audit_service.log_action(
            db,
            action="auth.login_blocked_inactive",
            user_id=user.id,
            details=f"ip={client_host}",
        )
        push_flash(request, "Benutzer ist deaktiviert.", "error")
        return RedirectResponse(url="/login", status_code=303)

    set_logged_in_session(request, user)
    touch_last_login(db, user)
    audit_service.log_action(
        db,
        action="auth.login_success",
        user_id=user.id,
        details=f"ip={client_host}",
    )
    return RedirectResponse(url="/dashboard", status_code=303)


@router.post("/logout")
def logout_action(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = get_current_user_from_session(request, db)
    if current_user:
        audit_service.log_action(
            db,
            action="auth.logout",
            user_id=current_user.id,
        )
    clear_session(request)
    push_flash(request, "Erfolgreich abgemeldet.", "info")
    return RedirectResponse(url="/login", status_code=303)
