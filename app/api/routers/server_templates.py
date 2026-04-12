from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.services import template_service
from app.services.auth_service import get_current_user_from_session
from app.services.java_profile_service import list_java_profiles
from app.services.provisioning_service import ProvisioningService
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)
provisioning_service = ProvisioningService()


def _require_super_admin(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        return None
    if user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")
    return user


def _to_optional_int(value: str | None) -> int | None:
    if value is None:
        return None
    stripped = value.strip()
    if not stripped:
        return None
    return int(stripped)


def _form_payload(
    name: str,
    server_type: str,
    mc_version: str,
    loader_version: str | None,
    java_profile_id: str | None,
    memory_min_mb: str | None,
    memory_max_mb: str | None,
    port_min: str | None,
    port_max: str | None,
    start_parameters: str | None,
    default_properties_json: str | None,
    is_default: str | None,
) -> dict[str, object]:
    return {
        "name": name.strip(),
        "server_type": server_type.strip().lower(),
        "mc_version": mc_version.strip(),
        "loader_version": (loader_version or "").strip() or None,
        "java_profile_id": _to_optional_int(java_profile_id),
        "memory_min_mb": _to_optional_int(memory_min_mb),
        "memory_max_mb": _to_optional_int(memory_max_mb),
        "port_min": _to_optional_int(port_min),
        "port_max": _to_optional_int(port_max),
        "start_parameters": (start_parameters or "").strip() or None,
        "default_properties_json": (default_properties_json or "").strip() or None,
        "is_default": bool(is_default),
    }


@router.get("/templates")
def list_templates_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server_types = provisioning_service.list_available_server_types()
    selected_type = server_types[0] if server_types else "vanilla"
    versions = provisioning_service.list_versions(selected_type)

    return templates.TemplateResponse(
        request,
        "templates.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Templates",
            templates=template_service.list_templates(db),
            server_types=server_types,
            versions=versions,
            selected_type=selected_type,
            java_profiles=list_java_profiles(db),
        ),
    )


@router.post("/templates")
def create_template_action(
    request: Request,
    name: Annotated[str, Form()],
    server_type: Annotated[str, Form()],
    mc_version: Annotated[str, Form()],
    loader_version: Annotated[str | None, Form()] = None,
    java_profile_id: Annotated[str | None, Form()] = None,
    memory_min_mb: Annotated[str | None, Form()] = None,
    memory_max_mb: Annotated[str | None, Form()] = None,
    port_min: Annotated[str | None, Form()] = None,
    port_max: Annotated[str | None, Form()] = None,
    start_parameters: Annotated[str | None, Form()] = None,
    default_properties_json: Annotated[str | None, Form()] = None,
    is_default: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        template_service.create_template(
            db,
            _form_payload(
                name,
                server_type,
                mc_version,
                loader_version,
                java_profile_id,
                memory_min_mb,
                memory_max_mb,
                port_min,
                port_max,
                start_parameters,
                default_properties_json,
                is_default,
            ),
        )
    except Exception as exc:
        push_flash(request, f"Template konnte nicht erstellt werden: {exc}", "error")
        return RedirectResponse(url="/templates", status_code=303)

    push_flash(request, "Template gespeichert.", "success")
    return RedirectResponse(url="/templates", status_code=303)


@router.get("/templates/{template_id}")
def edit_template_page(
    request: Request,
    template_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    template = template_service.get_template(db, template_id)
    if template is None:
        push_flash(request, "Template nicht gefunden.", "error")
        return RedirectResponse(url="/templates", status_code=303)

    server_types = provisioning_service.list_available_server_types()
    selected_type = template.server_type
    versions = provisioning_service.list_versions(selected_type)

    return templates.TemplateResponse(
        request,
        "template_edit.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Template bearbeiten",
            template=template,
            server_types=server_types,
            versions=versions,
            selected_type=selected_type,
            java_profiles=list_java_profiles(db),
        ),
    )


@router.post("/templates/{template_id}")
def update_template_action(
    request: Request,
    template_id: int,
    name: Annotated[str, Form()],
    server_type: Annotated[str, Form()],
    mc_version: Annotated[str, Form()],
    loader_version: Annotated[str | None, Form()] = None,
    java_profile_id: Annotated[str | None, Form()] = None,
    memory_min_mb: Annotated[str | None, Form()] = None,
    memory_max_mb: Annotated[str | None, Form()] = None,
    port_min: Annotated[str | None, Form()] = None,
    port_max: Annotated[str | None, Form()] = None,
    start_parameters: Annotated[str | None, Form()] = None,
    default_properties_json: Annotated[str | None, Form()] = None,
    is_default: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    template = template_service.get_template(db, template_id)
    if template is None:
        push_flash(request, "Template nicht gefunden.", "error")
        return RedirectResponse(url="/templates", status_code=303)

    try:
        template_service.update_template(
            db,
            template,
            _form_payload(
                name,
                server_type,
                mc_version,
                loader_version,
                java_profile_id,
                memory_min_mb,
                memory_max_mb,
                port_min,
                port_max,
                start_parameters,
                default_properties_json,
                is_default,
            ),
        )
    except Exception as exc:
        push_flash(request, f"Template konnte nicht gespeichert werden: {exc}", "error")
        return RedirectResponse(url=f"/templates/{template_id}", status_code=303)

    push_flash(request, "Template aktualisiert.", "success")
    return RedirectResponse(url="/templates", status_code=303)


@router.post("/templates/{template_id}/delete")
def delete_template_action(
    request: Request,
    template_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    template = template_service.get_template(db, template_id)
    if template is None:
        push_flash(request, "Template nicht gefunden.", "error")
        return RedirectResponse(url="/templates", status_code=303)

    template_service.delete_template(db, template)
    push_flash(request, "Template geloescht.", "success")
    return RedirectResponse(url="/templates", status_code=303)
