from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.schemas.provider import ProvisionServerRequest
from app.services import audit_service
from app.services.auth_service import get_current_user_from_session
from app.services.provisioning_service import ProvisioningService
from app.services.java_profile_service import list_java_profiles
from app.services import template_service
from app.services.app_setting_service import get_server_storage_root
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


@router.get("/servers/create")
def create_server_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server_types = provisioning_service.list_available_server_types()
    selected_type = server_types[0] if server_types else "vanilla"
    versions = provisioning_service.list_versions(selected_type)
    templates_list = template_service.list_templates(db)
    default_template = template_service.get_default_template(db, selected_type)
    default_server_storage_root = str(get_server_storage_root(db))
    return templates.TemplateResponse(
        request,
        "server_create.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Server erstellen",
            server_types=server_types,
            versions=versions,
            selected_type=selected_type,
            java_profiles=list_java_profiles(db),
            templates=templates_list,
            default_template_id=default_template.id if default_template else None,
            default_server_storage_root=default_server_storage_root,
        ),
    )


@router.get("/servers/create/versions")
def list_versions_endpoint(
    request: Request,
    server_type: str,
    channel: str = "release",
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    versions = provisioning_service.list_versions(server_type, channel=channel)
    return JSONResponse({"versions": [version.model_dump() for version in versions]})


@router.get("/servers/create/loader-versions")
def list_loader_versions_endpoint(
    request: Request,
    server_type: str,
    mc_version: str,
    channel: str = "all",
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    versions = provisioning_service.list_loader_versions(server_type, mc_version, channel=channel)
    return JSONResponse({"versions": [version.model_dump() for version in versions]})


@router.post("/servers/create")
def create_server_action(
    request: Request,
    name: Annotated[str, Form()],
    server_type: Annotated[str, Form()],
    mc_version: Annotated[str, Form()],
    loader_version: Annotated[str | None, Form()] = None,
    target_path: Annotated[str, Form()] = "",
    java_profile_id: Annotated[str | None, Form()] = None,
    memory_min_mb: Annotated[str, Form()] = "2048",
    memory_max_mb: Annotated[str, Form()] = "4096",
    port: Annotated[str | None, Form()] = None,
    start_parameters: Annotated[str | None, Form()] = None,
    template_id: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    template = None
    if template_id:
        template = template_service.get_template(db, _to_optional_int(template_id) or 0)
        if template is None:
            push_flash(request, "Template nicht gefunden.", "error")
            return RedirectResponse(url="/servers/create", status_code=303)

    parsed_memory_min = _to_optional_int(memory_min_mb)
    parsed_memory_max = _to_optional_int(memory_max_mb)
    parsed_port = _to_optional_int(port)
    parsed_java_profile = _to_optional_int(java_profile_id)

    resolved_server_type = server_type.strip().lower() if server_type.strip() else (template.server_type if template else "")
    resolved_version = mc_version.strip() if mc_version.strip() else (template.mc_version if template else "")

    if not resolved_server_type or not resolved_version:
        push_flash(request, "Servertyp und Version sind erforderlich.", "error")
        return RedirectResponse(url="/servers/create", status_code=303)

    payload = ProvisionServerRequest(
        name=name.strip(),
        server_type=resolved_server_type,
        mc_version=resolved_version,
        loader_version=(loader_version or "").strip()
        or (template.loader_version if template else None),
        target_path=target_path.strip(),
        java_profile_id=parsed_java_profile
        if parsed_java_profile is not None
        else (template.java_profile_id if template else None),
        memory_min_mb=parsed_memory_min
        if parsed_memory_min is not None
        else (template.memory_min_mb if template else 2048),
        memory_max_mb=parsed_memory_max
        if parsed_memory_max is not None
        else (template.memory_max_mb if template else 4096),
        port=parsed_port if parsed_port is not None else (template.port_min if template else None),
        start_parameters=(start_parameters or "").strip()
        or (template.start_parameters if template else None),
    )

    try:
        server, notes = provisioning_service.create_server_instance(db, payload)
    except Exception as exc:
        push_flash(request, f"Server-Erstellung fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url="/servers/create", status_code=303)

    audit_service.log_action(
        db,
        action="server.create",
        user_id=current_user.id,
        server_id=server.id,
        details=f"type={server.server_type} version={server.mc_version}",
    )
    if notes:
        push_flash(request, " | ".join(notes), "info")
    push_flash(request, f"Server '{server.name}' wurde erstellt.", "success")
    return RedirectResponse(url=f"/servers/{server.id}", status_code=303)
