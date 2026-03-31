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
        ),
    )


@router.get("/servers/create/versions")
def list_versions_endpoint(
    request: Request,
    server_type: str,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    versions = provisioning_service.list_versions(server_type)
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
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    payload = ProvisionServerRequest(
        name=name.strip(),
        server_type=server_type.strip().lower(),
        mc_version=mc_version.strip(),
        loader_version=(loader_version or "").strip() or None,
        target_path=target_path.strip(),
        java_profile_id=_to_optional_int(java_profile_id),
        memory_min_mb=int(memory_min_mb),
        memory_max_mb=int(memory_max_mb),
        port=_to_optional_int(port),
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
