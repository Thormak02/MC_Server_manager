from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.schemas.java_profile import JavaProfileCreate
from app.services import audit_service
from app.services.auth_service import get_current_user_from_session
from app.services.app_setting_service import (
    clear_server_storage_override,
    get_server_storage_root,
    get_server_storage_source,
    set_server_storage_root,
)
from app.services.java_profile_service import (
    create_java_profile,
    delete_java_profile,
    list_java_profiles,
    set_default_java_profile,
)
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)


def _require_super_admin(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    if user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    return user


@router.get("/settings", response_class=HTMLResponse)
def settings_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    profiles = list_java_profiles(db)
    server_storage_root = str(get_server_storage_root(db))
    server_storage_source = get_server_storage_source(db)
    return templates.TemplateResponse(
        request,
        "settings.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Einstellungen",
            profiles=profiles,
            server_storage_root=server_storage_root,
            server_storage_source=server_storage_source,
        ),
    )


@router.post("/settings/server-storage")
def update_server_storage_action(
    request: Request,
    server_storage_root: Annotated[str | None, Form()] = None,
    reset_to_default: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        if reset_to_default:
            new_path = clear_server_storage_override(db)
            source = get_server_storage_source(db)
            push_flash(
                request,
                f"Server-Standardpfad zurueckgesetzt: {new_path} (Quelle: {source})",
                "success",
            )
            audit_service.log_action(
                db,
                action="settings.server_storage_reset",
                user_id=current_user.id,
                details=f"path={new_path}",
            )
            return RedirectResponse(url="/settings", status_code=303)

        raw = (server_storage_root or "").strip()
        if not raw:
            raise ValueError("Bitte einen gueltigen Pfad angeben oder Zuruecksetzen nutzen.")
        new_path = set_server_storage_root(db, raw)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="settings.server_storage_update",
        user_id=current_user.id,
        details=f"path={new_path}",
    )
    push_flash(request, f"Server-Standardpfad gespeichert: {new_path}", "success")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/java-profiles")
def create_java_profile_action(
    request: Request,
    name: Annotated[str, Form()],
    java_path: Annotated[str, Form()],
    version_label: Annotated[str | None, Form()] = None,
    description: Annotated[str | None, Form()] = None,
    is_default: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        profile = create_java_profile(
            db,
            JavaProfileCreate(
                name=name,
                java_path=java_path,
                version_label=version_label,
                description=description,
                is_default=is_default,
            ),
        )
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="java_profile.create",
        user_id=current_user.id,
        details=f"profile={profile.name}",
    )
    push_flash(request, f"Java-Profil '{profile.name}' angelegt.", "success")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/java-profiles/{profile_id}/default")
def set_default_java_profile_action(
    request: Request,
    profile_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        profile = set_default_java_profile(db, profile_id)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="java_profile.set_default",
        user_id=current_user.id,
        details=f"profile={profile.name}",
    )
    push_flash(request, f"'{profile.name}' ist jetzt Standardprofil.", "success")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/java-profiles/{profile_id}/delete")
def delete_java_profile_action(
    request: Request,
    profile_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        delete_java_profile(db, profile_id)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="java_profile.delete",
        user_id=current_user.id,
        details=f"profile_id={profile_id}",
    )
    push_flash(request, "Java-Profil geloescht.", "success")
    return RedirectResponse(url="/settings", status_code=303)
