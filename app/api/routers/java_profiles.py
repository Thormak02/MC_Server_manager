from typing import Annotated

from fastapi import APIRouter, Depends, Form, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.db.session import get_db
from app.schemas.java_profile import JavaProfileCreate
from app.services import audit_service
from app.services.auth_service import get_current_user_from_session
from app.services.app_setting_service import (
    clear_backup_storage_override,
    clear_public_base_url_override,
    clear_server_storage_override,
    get_backup_storage_root,
    get_backup_storage_source,
    get_network_domain,
    get_network_domain_source,
    get_network_mode,
    get_network_mode_source,
    get_network_port,
    get_network_port_source,
    get_public_base_url,
    get_public_base_url_source,
    get_server_storage_root,
    get_server_storage_source,
    set_backup_storage_root,
    set_network_domain,
    set_network_mode,
    set_network_port,
    set_public_base_url,
    set_server_storage_root,
)
from app.services.java_profile_service import (
    create_java_profile,
    delete_java_profile,
    list_java_profiles,
    set_default_java_profile,
)
from app.services.java_runtime_service import (
    install_java_from_adoptium,
    install_java_with_winget,
    sync_detected_java_profiles,
)
from app.services.platform_settings_service import (
    get_provider_settings,
    list_platform_settings,
    update_provider_settings,
)
from app.services.update_service import (
    get_manager_update_status,
    trigger_manager_restart,
    trigger_manager_update,
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


def _to_bool(raw: str | bool | None) -> bool:
    if isinstance(raw, bool):
        return raw
    if raw is None:
        return False
    return raw.strip().lower() in {"1", "true", "on", "yes"}


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
    backup_storage_root = str(get_backup_storage_root(db))
    backup_storage_source = get_backup_storage_source(db)
    public_base_url = get_public_base_url(db)
    public_base_url_source = get_public_base_url_source(db)
    network_port = get_network_port(db)
    network_port_source = get_network_port_source(db)
    network_domain = get_network_domain(db)
    network_domain_source = get_network_domain_source(db)
    network_mode = get_network_mode(db)
    network_mode_source = get_network_mode_source(db)
    from app.services import gateway_service
    from app.services.app_setting_service import get_dispatcher_enabled

    dispatcher_enabled = get_dispatcher_enabled(db)
    gateway_status = gateway_service.gateway_status_runtime()
    platform_settings = list_platform_settings(db, include_secrets=False)
    manager_update_status = get_manager_update_status(fetch_remote=False)
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
            backup_storage_root=backup_storage_root,
            backup_storage_source=backup_storage_source,
            public_base_url=public_base_url,
            public_base_url_source=public_base_url_source,
            network_port=network_port,
            network_port_source=network_port_source,
            network_domain=network_domain,
            network_domain_source=network_domain_source,
            network_mode=network_mode,
            network_mode_source=network_mode_source,
            dispatcher_enabled=dispatcher_enabled,
            gateway_status=gateway_status,
            platform_settings=platform_settings,
            manager_update_status=manager_update_status,
        ),
    )


@router.post("/settings/network")
def update_network_settings_action(
    request: Request,
    network_mode: Annotated[str | None, Form()] = None,
    network_port: Annotated[str | None, Form()] = None,
    network_domain: Annotated[str | None, Form()] = None,
    dispatcher_enabled: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        raw_port = (network_port or "").strip()
        if raw_port:
            set_network_port(db, int(raw_port))
        set_network_domain(db, network_domain)
        from app.services.app_setting_service import set_dispatcher_enabled

        set_dispatcher_enabled(db, dispatcher_enabled)
        mode = set_network_mode(db, network_mode or "off")
    except (ValueError, TypeError) as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    # Gateway sofort an den neuen Modus/Port angleichen (Listener start/stop/rebind).
    try:
        from app.services import gateway_service

        gateway_service.reconcile_gateway()
    except Exception:  # noqa: BLE001
        pass
    # Lobby-Plugin-config an die (evtl. geaenderte) Domain angleichen.
    try:
        from app.services import lobby_service

        lobby_service.sync_lobby_plugin(db)
    except Exception:  # noqa: BLE001
        pass

    audit_service.log_action(
        db,
        action="settings.network_update",
        user_id=current_user.id,
        details=f"mode={mode} port={raw_port or '(unveraendert)'}",
    )
    push_flash(request, f"Netzwerk-Einstellungen gespeichert (Modus: {mode}).", "success")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/lobby/auto-create")
def auto_create_lobby_action(request: Request, db: Session = Depends(get_db)):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import lobby_service

    try:
        ok, message, server_id = lobby_service.create_auto_lobby(
            db, initiated_by_user_id=current_user.id
        )
    except Exception as exc:  # noqa: BLE001
        push_flash(request, f"Auto-Lobby fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url="/settings", status_code=303)

    push_flash(request, message, "success" if ok else "error")
    if ok and server_id:
        return RedirectResponse(url=f"/servers/{server_id}", status_code=303)
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/lobby/sync-plugin")
def sync_lobby_plugin_action(request: Request, db: Session = Depends(get_db)):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import lobby_service

    try:
        ok, message = lobby_service.sync_lobby_plugin(db)
    except Exception as exc:  # noqa: BLE001
        push_flash(request, f"Transfer-Plugin-Sync fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url="/settings", status_code=303)

    push_flash(request, message, "success" if ok else "error")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/lobby/install-multiversion")
def install_multiversion_action(request: Request, db: Session = Depends(get_db)):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from sqlalchemy import select

    from app.models.server import Server
    from app.services import viaversion_service

    lobby = db.scalar(select(Server).where(Server.gateway_is_default.is_(True)))
    if lobby is None:
        push_flash(request, "Keine Gateway-Lobby vorhanden.", "error")
        return RedirectResponse(url="/settings", status_code=303)

    try:
        ok, message = viaversion_service.install_multiversion(lobby.base_path, lobby.mc_version)
    except Exception as exc:  # noqa: BLE001
        push_flash(request, f"Multi-Version-Installation fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url="/settings", status_code=303)

    if ok:
        message += " Lobby neu starten, damit die Plugins laden."
    push_flash(request, message, "success" if ok else "error")
    return RedirectResponse(url="/settings", status_code=303)


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


@router.post("/settings/backup-storage")
def update_backup_storage_action(
    request: Request,
    backup_storage_root: Annotated[str | None, Form()] = None,
    reset_to_default: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        if reset_to_default:
            new_path = clear_backup_storage_override(db)
            source = get_backup_storage_source(db)
            push_flash(
                request,
                f"Backup-Pfad zurueckgesetzt: {new_path} (Quelle: {source})",
                "success",
            )
            audit_service.log_action(
                db,
                action="settings.backup_storage_reset",
                user_id=current_user.id,
                details=f"path={new_path}",
            )
            return RedirectResponse(url="/settings", status_code=303)

        raw = (backup_storage_root or "").strip()
        if not raw:
            raise ValueError("Bitte einen gueltigen Pfad angeben oder Zuruecksetzen nutzen.")
        new_path = set_backup_storage_root(db, raw)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="settings.backup_storage_update",
        user_id=current_user.id,
        details=f"path={new_path}",
    )
    push_flash(request, f"Backup-Pfad gespeichert: {new_path}", "success")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/public-url")
def update_public_url_action(
    request: Request,
    public_base_url: Annotated[str | None, Form()] = None,
    reset_to_default: Annotated[bool, Form()] = False,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        if reset_to_default:
            new_url = clear_public_base_url_override(db)
            source = get_public_base_url_source(db)
            push_flash(
                request,
                f"Oeffentliche URL zurueckgesetzt: {new_url or '(leer)'} (Quelle: {source})",
                "success",
            )
            audit_service.log_action(
                db,
                action="settings.public_url_reset",
                user_id=current_user.id,
                details=f"url={new_url}",
            )
            return RedirectResponse(url="/settings", status_code=303)

        new_url = set_public_base_url(db, public_base_url or "")
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="settings.public_url_update",
        user_id=current_user.id,
        details=f"url={new_url}",
    )
    push_flash(request, f"Oeffentliche URL gespeichert: {new_url}", "success")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/platform/{provider_name}")
def update_platform_settings_action(
    request: Request,
    provider_name: str,
    enabled: Annotated[str | None, Form()] = None,
    api_key: Annotated[str | None, Form()] = None,
    clear_api_key: Annotated[str | None, Form()] = None,
    user_agent: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    provider = (provider_name or "").strip().lower()
    updates: dict[str, object] = {"enabled": _to_bool(enabled)}
    if provider == "curseforge":
        normalized_key = (api_key or "").strip()
        if _to_bool(clear_api_key):
            updates["api_key"] = ""
        elif normalized_key:
            updates["api_key"] = normalized_key
    elif provider == "modrinth":
        updates["user_agent"] = (user_agent or "").strip()
    else:
        push_flash(request, "Unbekannter Provider.", "error")
        return RedirectResponse(url="/settings", status_code=303)

    try:
        update_provider_settings(db, provider_name=provider, updates=updates)
    except ValueError as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="settings.platform_update",
        user_id=current_user.id,
        details=f"provider={provider}",
    )
    push_flash(request, f"Plattform-Einstellungen gespeichert ({provider}).", "success")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/manager-update/check")
def check_manager_update_action(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    status_info = get_manager_update_status(fetch_remote=True)
    if status_info.ok:
        kind = "success" if not status_info.has_update else "info"
    else:
        kind = "error"
    push_flash(request, status_info.message, kind)
    audit_service.log_action(
        db,
        action="settings.manager_update_check",
        user_id=current_user.id,
        details=(
            f"ok={status_info.ok} branch={status_info.branch} "
            f"ahead={status_info.ahead_count} behind={status_info.behind_count} dirty={status_info.dirty}"
        ),
    )
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/manager-update/apply")
def apply_manager_update_action(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    ok, message = trigger_manager_update()
    push_flash(request, message, "success" if ok else "error")
    audit_service.log_action(
        db,
        action="settings.manager_update_apply",
        user_id=current_user.id,
        details=f"ok={ok} message={message}",
    )
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/manager/restart")
def restart_manager_action(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    ok, message = trigger_manager_restart()
    push_flash(request, message, "success" if ok else "error")
    audit_service.log_action(
        db,
        action="settings.manager_restart",
        user_id=current_user.id,
        details=f"ok={ok} message={message}",
    )
    return RedirectResponse(url="/settings", status_code=303)


@router.get("/api/platform-settings", response_class=JSONResponse)
def api_platform_settings(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})
    return JSONResponse({"providers": list_platform_settings(db, include_secrets=False)})


@router.patch("/api/platform-settings/{provider_name}", response_class=JSONResponse)
async def api_update_platform_settings(
    request: Request,
    provider_name: str,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return JSONResponse(status_code=401, content={"detail": "Not authenticated"})

    try:
        payload = await request.json()
    except Exception:
        payload = {}
    if not isinstance(payload, dict):
        return JSONResponse(status_code=400, content={"detail": "JSON body muss ein Objekt sein."})

    if "enabled" in payload:
        payload["enabled"] = _to_bool(payload.get("enabled"))

    try:
        updated = update_provider_settings(
            db,
            provider_name=provider_name,
            updates=payload,
        )
    except ValueError as exc:
        return JSONResponse(status_code=400, content={"detail": str(exc)})

    audit_service.log_action(
        db,
        action="settings.platform_update",
        user_id=current_user.id,
        details=f"provider={provider_name}",
    )
    return JSONResponse({"provider": provider_name, "settings": updated})


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


@router.post("/settings/java-profiles/discover")
def discover_java_profiles_action(
    request: Request,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        detected, created, updated = sync_detected_java_profiles(db, force=True)
    except Exception as exc:
        push_flash(request, f"Java-Erkennung fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url="/settings", status_code=303)

    audit_service.log_action(
        db,
        action="java_profile.discover",
        user_id=current_user.id,
        details=f"detected={detected} created={created} updated={updated}",
    )
    push_flash(
        request,
        f"Java-Erkennung abgeschlossen: gefunden={detected}, neu={created}, aktualisiert={updated}",
        "success",
    )
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/java-profiles/install")
def install_java_action(
    request: Request,
    major_version: Annotated[str, Form()] = "21",
    distribution: Annotated[str, Form()] = "temurin",
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    try:
        major = int((major_version or "").strip())
    except ValueError:
        push_flash(request, "Ungueltige Java-Version.", "error")
        return RedirectResponse(url="/settings", status_code=303)

    distro = (distribution or "temurin").strip().lower()
    # Temurin direkt von Adoptium ziehen (kein winget/Adminrechte noetig, laeuft
    # auch als Dienst). winget bleibt Fallback fuer andere Distributionen.
    if distro == "temurin":
        ok, message, _java_exe = install_java_from_adoptium(major)
        if not ok:
            winget_ok, winget_msg = install_java_with_winget(
                major_version=major, distribution=distro
            )
            if winget_ok:
                ok, message = True, winget_msg
            else:
                message = f"{message} (winget-Fallback: {winget_msg})"
    else:
        ok, message = install_java_with_winget(major_version=major, distribution=distro)

    if not ok:
        push_flash(request, message, "error")
        return RedirectResponse(url="/settings", status_code=303)

    try:
        detected, created, updated = sync_detected_java_profiles(db, force=True)
    except Exception:
        detected, created, updated = 0, 0, 0

    audit_service.log_action(
        db,
        action="java_profile.install",
        user_id=current_user.id,
        details=f"major={major} distribution={distribution} detected={detected} created={created} updated={updated}",
    )
    push_flash(
        request,
        f"{message} Erkennung: gefunden={detected}, neu={created}, aktualisiert={updated}",
        "success",
    )
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
