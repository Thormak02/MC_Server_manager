from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.services import audit_service, user_service
from app.services.auth_service import get_current_user_from_session
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_super_admin(
    request: Request,
    db: Session,
):
    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return None
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Nur Super Admin darf Benutzer verwalten.",
        )
    return current_user


@router.get("/users", response_class=HTMLResponse)
def users_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    users = user_service.list_users(db)
    return templates.TemplateResponse(
        request,
        "users.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Benutzerverwaltung",
            users=users,
            available_roles=[role.value for role in UserRole],
        ),
    )


@router.post("/users")
def create_user_action(
    request: Request,
    username: Annotated[str, Form()],
    password: Annotated[str, Form()],
    role: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        created_user = user_service.create_user(
            db,
            username=username,
            password=password,
            role=role,
            is_active=True,
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/users", status_code=303)

    audit_service.log_action(
        db,
        action="users.create",
        user_id=current_user.id,
        details=f"created_user={created_user.username} role={created_user.role}",
    )
    push_flash(request, f"Benutzer '{created_user.username}' angelegt.", "success")
    return RedirectResponse(url="/users", status_code=303)


@router.post("/users/{user_id}/deactivate")
def deactivate_user_action(
    request: Request,
    user_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    target_user = user_service.get_user_by_id(db, user_id)
    if target_user is None:
        push_flash(request, "Benutzer nicht gefunden.", "error")
        return RedirectResponse(url="/users", status_code=303)
    if target_user.id == current_user.id:
        push_flash(request, "Der eigene Benutzer kann nicht deaktiviert werden.", "error")
        return RedirectResponse(url="/users", status_code=303)

    user_service.deactivate_user(db, target_user)
    audit_service.log_action(
        db,
        action="users.deactivate",
        user_id=current_user.id,
        details=f"target_user={target_user.username}",
    )
    push_flash(request, f"Benutzer '{target_user.username}' deaktiviert.", "success")
    return RedirectResponse(url="/users", status_code=303)


@router.post("/users/{user_id}/reset-password")
def reset_password_action(
    request: Request,
    user_id: int,
    new_password: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    target_user = user_service.get_user_by_id(db, user_id)
    if target_user is None:
        push_flash(request, "Benutzer nicht gefunden.", "error")
        return RedirectResponse(url="/users", status_code=303)

    try:
        user_service.reset_password(db, target_user, new_password)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/users", status_code=303)

    audit_service.log_action(
        db,
        action="users.reset_password",
        user_id=current_user.id,
        details=f"target_user={target_user.username}",
    )
    push_flash(
        request,
        f"Passwort fuer '{target_user.username}' wurde zurueckgesetzt.",
        "success",
    )
    return RedirectResponse(url="/users", status_code=303)
