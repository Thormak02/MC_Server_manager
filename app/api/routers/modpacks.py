import shutil
from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.models.installed_content import InstalledContent
from app.models.scheduled_job import ScheduledJob
from app.models.server import Server
from app.models.server_permission import ServerPermission
from app.models.pending_modpack_install import PendingModpackInstall
from app.models.server_modpack_state import ServerModpackState
from app.schemas.modpack import ModpackExecuteResponse
from app.schemas.provider import ProvisionServerRequest
from app.services import audit_service, modpack_service
from app.services.auth_service import get_current_user_from_session
from app.services.java_profile_service import list_java_profiles
from app.services.provisioning_service import ProvisioningService
from app.services.app_setting_service import get_server_storage_root
from app.web.routes.pages import build_context, templates


router = APIRouter(include_in_schema=False)
provisioning_service = ProvisioningService()


def _to_optional_int(raw: str | None) -> int | None:
    if raw is None:
        return None
    stripped = raw.strip()
    if not stripped:
        return None
    try:
        return int(stripped)
    except ValueError as exc:
        raise ValueError(f"Ungueltige Ganzzahl: {raw}") from exc


def _require_user(request: Request, db: Session):
    user = get_current_user_from_session(request, db)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    return user


def _require_super_admin(user) -> None:
    if user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Nur Super Admin erlaubt.")


def _rollback_failed_modpack_server(
    db: Session,
    *,
    server: Server | None,
    user_id: int | None,
    reason: str,
) -> None:
    if server is None:
        return
    server_id = server.id
    server_path = str(server.base_path or "").strip()
    try:
        if server_path:
            base_path = Path(server_path).expanduser().resolve()
            if base_path.exists() and base_path.is_dir():
                shutil.rmtree(base_path, ignore_errors=True)
    except Exception:
        # Best-effort rollback: DB cleanup still proceeds.
        pass

    db.execute(delete(InstalledContent).where(InstalledContent.server_id == server_id))
    db.execute(delete(ServerModpackState).where(ServerModpackState.server_id == server_id))
    db.execute(delete(ServerPermission).where(ServerPermission.server_id == server_id))
    db.execute(delete(ScheduledJob).where(ScheduledJob.server_id == server_id))
    pending = db.query(PendingModpackInstall).filter(PendingModpackInstall.server_id == server_id).first()
    if pending is not None:
        modpack_service.discard_preview(pending.preview_token)
    db.execute(delete(PendingModpackInstall).where(PendingModpackInstall.server_id == server_id))
    db.delete(server)
    db.commit()

    audit_service.log_action(
        db,
        action="modpack.import_rollback",
        user_id=user_id,
        server_id=server_id,
        details=f"reason={reason}",
    )


@router.get("/modpacks/import", response_class=HTMLResponse)
def modpack_import_page(request: Request, db: Session = Depends(get_db)):
    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    return templates.TemplateResponse(
        request,
        "modpack_import.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Modpack importieren",
            java_profiles=list_java_profiles(db),
            default_server_storage_root=str(get_server_storage_root(db)),
        ),
    )


@router.post("/api/modpacks/import-preview", response_class=JSONResponse)
async def modpack_import_preview(
    request: Request,
    source: Annotated[str, Form()],
    modrinth_reference: Annotated[str | None, Form()] = None,
    modrinth_version_id: Annotated[str | None, Form()] = None,
    curseforge_reference: Annotated[str | None, Form()] = None,
    curseforge_project_id: Annotated[str | None, Form()] = None,
    curseforge_file_id: Annotated[str | None, Form()] = None,
    archive_file: Annotated[UploadFile | None, File()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    _require_super_admin(current_user)

    archive_bytes: bytes | None = None
    archive_name: str | None = None
    if archive_file is not None:
        archive_name = archive_file.filename
        archive_bytes = await archive_file.read()

    try:
        preview = modpack_service.create_preview(
            source=source,
            modrinth_reference=modrinth_reference,
            modrinth_version_id=modrinth_version_id,
            curseforge_reference=curseforge_reference,
            curseforge_project_id=_to_optional_int(curseforge_project_id),
            curseforge_file_id=_to_optional_int(curseforge_file_id),
            local_archive_name=archive_name,
            local_archive_bytes=archive_bytes,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    except Exception as exc:
        return JSONResponse(status_code=500, content={"detail": f"Import-Preview fehlgeschlagen: {exc}"})

    audit_service.log_action(
        db,
        action="modpack.import_preview",
        user_id=current_user.id,
        details=f"source={preview.source} token={preview.token} pack={preview.pack_name}",
    )
    return JSONResponse(preview.model_dump())


@router.post("/api/modpacks/import-execute", response_class=JSONResponse)
def modpack_import_execute(
    request: Request,
    preview_token: Annotated[str, Form()],
    new_server_name: Annotated[str | None, Form()] = None,
    new_server_path: Annotated[str | None, Form()] = None,
    server_type: Annotated[str | None, Form()] = None,
    mc_version: Annotated[str | None, Form()] = None,
    loader_version: Annotated[str | None, Form()] = None,
    java_profile_id: Annotated[str | None, Form()] = None,
    memory_min_mb: Annotated[str | None, Form()] = None,
    memory_max_mb: Annotated[str | None, Form()] = None,
    port: Annotated[str | None, Form()] = None,
    start_parameters: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_user(request, db)
    _require_super_admin(current_user)
    created_server: Server | None = None
    try:
        snapshot = modpack_service.load_preview(preview_token)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    try:
        resolved_java_profile_id = _to_optional_int(java_profile_id)
        resolved_memory_min_mb = _to_optional_int(memory_min_mb) or 2048
        resolved_memory_max_mb = _to_optional_int(memory_max_mb) or 4096
        resolved_port = _to_optional_int(port)
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    resolved_server_type = (server_type or "").strip().lower() or snapshot.recommended_server_type
    resolved_mc_version = (mc_version or "").strip() or (snapshot.mc_version or "").strip()
    resolved_loader_version = (loader_version or "").strip() or (snapshot.loader_version or None)
    if not resolved_server_type:
        return JSONResponse(status_code=400, content={"detail": "Servertyp fehlt."})
    if not resolved_mc_version:
        return JSONResponse(status_code=400, content={"detail": "Im Modpack fehlt eine Minecraft-Version."})

    server_name = (new_server_name or "").strip() or snapshot.pack_name
    if not server_name:
        return JSONResponse(status_code=400, content={"detail": "Servername fehlt."})

    provision_request = ProvisionServerRequest(
        name=server_name,
        server_type=resolved_server_type,
        mc_version=resolved_mc_version,
        loader_version=resolved_loader_version,
        target_path=(new_server_path or "").strip(),
        java_profile_id=resolved_java_profile_id,
        memory_min_mb=resolved_memory_min_mb,
        memory_max_mb=resolved_memory_max_mb,
        port=resolved_port,
        start_parameters=(start_parameters or "").strip() or None,
    )

    notes: list[str] = []
    try:
        server, provision_notes = provisioning_service.create_server_instance(db, provision_request)
        created_server = server
    except Exception as exc:
        return JSONResponse(status_code=400, content={"detail": f"Servererstellung fehlgeschlagen: {exc}"})
    notes.extend(provision_notes)

    try:
        modpack_service.queue_pending_install(
            db,
            server=server,
            snapshot=snapshot,
            requested_by_user_id=current_user.id,
        )
    except ValueError as exc:
        _rollback_failed_modpack_server(
            db,
            server=created_server,
            user_id=current_user.id,
            reason=str(exc),
        )
        return JSONResponse(status_code=400, content={"detail": str(exc)})
    except Exception as exc:
        _rollback_failed_modpack_server(
            db,
            server=created_server,
            user_id=current_user.id,
            reason=f"unexpected:{exc}",
        )
        return JSONResponse(status_code=500, content={"detail": f"Import-Vorbereitung fehlgeschlagen: {exc}"})

    notes.append("Modpack wird beim ersten Serverstart automatisch installiert.")
    result = ModpackExecuteResponse(
        server_id=server.id,
        server_name=server.name,
        created_server=True,
        installed_count=0,
        overrides_copied=0,
        warnings=list(snapshot.warnings),
        notes=notes,
    )
    return JSONResponse(result.model_dump())
