from pathlib import Path
import shutil
from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.models.installed_content import InstalledContent
from app.models.scheduled_job import ScheduledJob
from app.models.server_permission import ServerPermission
from app.schemas.server import ServerImportConfirm
from app.services import audit_service
from app.services.auth_service import get_current_user_from_session
from app.services.java_profile_service import list_java_profiles
from app.services.process_service import (
    queue_restart,
    refresh_runtime_states,
    start_server,
    stop_server,
)
from app.services.server_import_service import analyze_directory, import_server
from app.services.server_service import (
    can_edit_server_files,
    can_control_server,
    can_view_server,
    get_server_by_id,
    sync_server_settings_to_files,
    update_server_settings,
)
from app.services.provisioning_service import ProvisioningService
from app.web.routes.pages import build_context, push_flash, templates


router = APIRouter(include_in_schema=False)
provisioning_service = ProvisioningService()


def _to_optional_int(raw: str | None) -> int | None:
    if raw is None:
        return None
    stripped = raw.strip()
    if not stripped:
        return None
    return int(stripped)


def _to_bool(raw: str | None) -> bool:
    if raw is None:
        return False
    normalized = raw.strip().lower()
    return normalized in {"1", "true", "on", "yes"}


def _require_logged_in(request: Request, db: Session):
    current_user = get_current_user_from_session(request, db)
    if current_user is None:
        return None
    return current_user


def _redirect_to_referer(request: Request, fallback: str = "/dashboard") -> RedirectResponse:
    referer = request.headers.get("referer")
    if referer and referer.startswith(("http://", "https://")):
        return RedirectResponse(url=referer, status_code=303)
    return RedirectResponse(url=fallback, status_code=303)


@router.get("/servers/import", response_class=HTMLResponse)
def server_import_page(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    return templates.TemplateResponse(
        request,
        "server_import.html",
        build_context(request, current_user=current_user, page_title="Server importieren"),
    )


@router.post("/servers/import/analyze", response_class=HTMLResponse)
def server_import_analyze(
    request: Request,
    base_path: Annotated[str, Form()],
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    try:
        preview = analyze_directory(base_path)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return templates.TemplateResponse(
            request,
            "server_import.html",
            build_context(request, current_user=current_user, page_title="Server importieren"),
        )

    return templates.TemplateResponse(
        request,
        "server_import.html",
        build_context(
            request,
            current_user=current_user,
            page_title="Server importieren",
            preview=preview,
        ),
    )


@router.post("/servers/import/confirm")
def server_import_confirm(
    request: Request,
    name: Annotated[str, Form()],
    base_path: Annotated[str, Form()],
    server_type: Annotated[str, Form()],
    mc_version: Annotated[str, Form()],
    start_mode: Annotated[str, Form()],
    start_command: Annotated[str | None, Form()] = None,
    start_bat_path: Annotated[str | None, Form()] = None,
    loader_version: Annotated[str | None, Form()] = None,
    java_profile_id: Annotated[str | None, Form()] = None,
    memory_min_mb: Annotated[str | None, Form()] = None,
    memory_max_mb: Annotated[str | None, Form()] = None,
    port: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    payload = ServerImportConfirm(
        name=name.strip(),
        base_path=base_path.strip(),
        server_type=server_type.strip().lower(),
        mc_version=mc_version.strip() or "unknown",
        start_mode=start_mode.strip().lower(),
        start_command=(start_command or "").strip() or None,
        start_bat_path=(start_bat_path or "").strip() or None,
        loader_version=(loader_version or "").strip() or None,
        java_profile_id=_to_optional_int(java_profile_id),
        memory_min_mb=_to_optional_int(memory_min_mb),
        memory_max_mb=_to_optional_int(memory_max_mb),
        port=_to_optional_int(port),
    )

    try:
        server = import_server(db, payload)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/servers/import", status_code=303)

    audit_service.log_action(
        db,
        action="server.import",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={server.base_path} type={server.server_type}",
    )
    push_flash(request, f"Server '{server.name}' wurde importiert.", "success")
    return RedirectResponse(url=f"/servers/{server.id}", status_code=303)


@router.get("/servers/{server_id}", response_class=HTMLResponse)
def server_detail_page(
    request: Request,
    server_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_view_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    refresh_runtime_states(db, [server])
    db.refresh(server)

    return templates.TemplateResponse(
        request,
        "server_detail.html",
        build_context(
            request,
            current_user=current_user,
            page_title=f"Server: {server.name}",
            server=server,
            can_control=can_control_server(db, current_user, server),
            can_edit_files=can_edit_server_files(db, current_user, server),
            java_profiles=list_java_profiles(db),
        ),
    )


@router.get("/servers/{server_id}/version-options")
def server_version_options(
    request: Request,
    server_id: int,
    channel: str = "release",
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    versions = provisioning_service.list_versions(server.server_type, channel=channel)
    return JSONResponse({"versions": [version.model_dump() for version in versions]})


@router.get("/servers/{server_id}/loader-version-options")
def server_loader_version_options(
    request: Request,
    server_id: int,
    mc_version: str,
    channel: str = "all",
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    versions = provisioning_service.list_loader_versions(server.server_type, mc_version, channel=channel)
    return JSONResponse({"versions": [version.model_dump() for version in versions]})


@router.post("/servers/{server_id}/start")
def start_server_action(
    request: Request,
    server_id: int,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    ok, message = start_server(db, server, current_user.id)
    push_flash(request, message, "success" if ok else "error")
    return _redirect_to_referer(request, fallback=f"/servers/{server_id}")


@router.post("/servers/{server_id}/stop")
def stop_server_action(
    request: Request,
    server_id: int,
    force: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    ok, message = stop_server(db, server, current_user.id, force=force)
    push_flash(request, message, "success" if ok else "error")
    return _redirect_to_referer(request, fallback=f"/servers/{server_id}")


@router.post("/servers/{server_id}/restart")
def restart_server_action(
    request: Request,
    server_id: int,
    delay_seconds: Annotated[str | None, Form()] = None,
    warning_message: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    ok, message = queue_restart(
        db,
        server,
        current_user.id,
        delay_seconds=_to_optional_int(delay_seconds) or 0,
        warning_message=(warning_message or "").strip() or None,
        source="manual_action",
    )
    push_flash(request, message, "success" if ok else "error")
    return _redirect_to_referer(request, fallback=f"/servers/{server_id}")


@router.post("/servers/{server_id}/settings")
def update_server_settings_action(
    request: Request,
    server_id: int,
    mc_version: Annotated[str | None, Form()] = None,
    loader_version: Annotated[str | None, Form()] = None,
    java_profile_id: Annotated[str | None, Form()] = None,
    memory_min_mb: Annotated[str | None, Form()] = None,
    memory_max_mb: Annotated[str | None, Form()] = None,
    port: Annotated[str | None, Form()] = None,
    auto_restart: Annotated[str | None, Form()] = None,
    start_mode: Annotated[str | None, Form()] = None,
    start_command: Annotated[str | None, Form()] = None,
    start_bat_path: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")
    if not can_control_server(db, current_user, server):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    target_mc_version = (mc_version or "").strip() or server.mc_version
    raw_loader_version = (loader_version or "").strip()
    if raw_loader_version:
        target_loader_version = raw_loader_version
    elif target_mc_version != server.mc_version:
        target_loader_version = None
    else:
        target_loader_version = server.loader_version or None
    version_changed = (
        target_mc_version != server.mc_version
        or target_loader_version != (server.loader_version or None)
    )
    if version_changed and server.status in {"starting", "running", "stopping", "restarting"}:
        push_flash(
            request,
            "Version kann nur im gestoppten Zustand geaendert werden.",
            "error",
        )
        return RedirectResponse(url=f"/servers/{server_id}", status_code=303)

    _, sync_warnings = update_server_settings(
        db,
        server,
        mc_version=target_mc_version,
        loader_version=target_loader_version,
        java_profile_id=_to_optional_int(java_profile_id),
        memory_min_mb=_to_optional_int(memory_min_mb),
        memory_max_mb=_to_optional_int(memory_max_mb),
        port=_to_optional_int(port),
        auto_restart=_to_bool(auto_restart),
        start_mode=(start_mode or "").strip().lower() or None,
        start_command=(start_command or "").strip() or None,
        start_bat_path=(start_bat_path or "").strip() or None,
    )
    reprovision_notes: list[str] = []
    if version_changed:
        try:
            reprovision_notes = provisioning_service.reprovision_existing_server(
                server,
                mc_version=target_mc_version,
                loader_version=target_loader_version,
            )
            sync_warnings.extend(sync_server_settings_to_files(server))
            db.add(server)
            db.commit()
            db.refresh(server)
        except Exception as exc:
            db.rollback()
            push_flash(
                request,
                f"Version gespeichert, Runtime-Update fehlgeschlagen: {exc}",
                "error",
            )
            return RedirectResponse(url=f"/servers/{server_id}", status_code=303)

    audit_service.log_action(
        db,
        action="server.settings_update",
        user_id=current_user.id,
        server_id=server.id,
        details="Servereinstellungen aktualisiert",
    )
    if sync_warnings:
        push_flash(
            request,
            "Servereinstellungen gespeichert, aber nicht alles konnte in Dateien synchronisiert werden: "
            + " | ".join(sync_warnings),
            "info",
        )
    elif reprovision_notes:
        push_flash(
            request,
            "Servereinstellungen gespeichert. " + " | ".join(reprovision_notes),
            "info",
        )
    else:
        push_flash(request, "Servereinstellungen gespeichert.", "success")
    return RedirectResponse(url=f"/servers/{server_id}", status_code=303)


@router.post("/servers/{server_id}/delete")
def delete_server_action(
    request: Request,
    server_id: int,
    confirm_name: Annotated[str, Form()],
    confirm_delete: Annotated[str | None, Form()] = None,
    keep_folder: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_logged_in(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)
    if current_user.role != UserRole.SUPER_ADMIN.value:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    server = get_server_by_id(db, server_id)
    if server is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Server not found")

    if confirm_name.strip() != server.name:
        push_flash(request, "Servername stimmt nicht ueberein.", "error")
        return RedirectResponse(url=f"/servers/{server_id}", status_code=303)

    if not _to_bool(confirm_delete):
        push_flash(request, "Bitte die Bestaetigung aktivieren.", "error")
        return RedirectResponse(url=f"/servers/{server_id}", status_code=303)

    if can_control_server(db, current_user, server):
        stop_server(db, server, current_user.id, force=False)

    delete_folder = not _to_bool(keep_folder)
    if delete_folder:
        base_path = Path(server.base_path).expanduser().resolve()
        if base_path.exists():
            if not base_path.is_dir():
                push_flash(request, "Serverpfad ist kein Ordner. Abbruch.", "error")
                return RedirectResponse(url=f"/servers/{server_id}", status_code=303)
            if base_path.parent == base_path:
                push_flash(request, "Serverpfad ist ungueltig. Abbruch.", "error")
                return RedirectResponse(url=f"/servers/{server_id}", status_code=303)
            try:
                shutil.rmtree(base_path)
            except Exception as exc:
                push_flash(request, f"Ordner konnte nicht geloescht werden: {exc}", "error")
                return RedirectResponse(url=f"/servers/{server_id}", status_code=303)

    audit_service.log_action(
        db,
        action="server.delete",
        user_id=current_user.id,
        server_id=server.id,
        details=f"path={server.base_path} delete_folder={delete_folder}",
    )

    db.execute(delete(InstalledContent).where(InstalledContent.server_id == server.id))
    db.execute(delete(ServerPermission).where(ServerPermission.server_id == server.id))
    db.execute(delete(ScheduledJob).where(ScheduledJob.server_id == server.id))
    db.delete(server)
    db.commit()

    if delete_folder:
        push_flash(request, "Server und Ordner wurden geloescht.", "success")
    else:
        push_flash(request, "Server wurde geloescht. Ordner wurde behalten.", "success")
    return RedirectResponse(url="/dashboard", status_code=303)
