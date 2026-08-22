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
    from app.services.app_setting_service import (
        get_central_storage_root, get_nas_password, get_nas_user,
    )

    central_storage_root = get_central_storage_root(db)
    nas_user = get_nas_user(db)
    nas_password_set = bool(get_nas_password(db))
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
    from app.services import hub_lobby_service, viaproxy_service
    from app.services.app_setting_service import (
        get_hub_lobby_enabled, get_hub_lobby_port, get_hub_lobby_vanilla_port,
        get_hub_lobby_replay, get_hub_lobby_vanilla_replay,
        get_viaproxy_enabled, get_viaproxy_port,
        get_presence_bridge_enabled,
        get_hub_name, get_hub_motd, get_hub_max_players,
        get_hub_whitelist_enabled, get_hub_whitelist,
    )

    universal_lobby = {
        "hub_enabled": get_hub_lobby_enabled(db),
        "hub_port": get_hub_lobby_port(db),
        "hub_vanilla_port": get_hub_lobby_vanilla_port(db),
        "hub_replay": get_hub_lobby_replay(db),
        "hub_vanilla_replay": get_hub_lobby_vanilla_replay(db),
        "viaproxy_enabled": get_viaproxy_enabled(db),
        "viaproxy_port": get_viaproxy_port(db),
        "presence_bridge_enabled": get_presence_bridge_enabled(db),
        "hub_running": hub_lobby_service.is_running(),
        "viaproxy_running": viaproxy_service.is_running(),
        "hub_name": get_hub_name(db),
        "hub_motd": get_hub_motd(db),
        "hub_max_players": get_hub_max_players(db),
        "hub_whitelist_enabled": get_hub_whitelist_enabled(db),
        "hub_whitelist": get_hub_whitelist(db),
    }
    from app.services import plugin_build_service, proxy_service
    from app.services.app_setting_service import get_velocity_version

    velocity_running = proxy_service.is_running()
    velocity_version = get_velocity_version(db)
    velocity_installed_version = proxy_service.installed_velocity_version()
    velocity_via_plugins = proxy_service.installed_via_plugins()
    velocity_log_tail = proxy_service.log_tail(80)
    from app.services import presence_bridge_service
    bridge_status = presence_bridge_service.bridge_status()
    plugin_build_status = plugin_build_service.last_build_status()
    plugin_building = plugin_build_service.is_building()
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
            central_storage_root=central_storage_root,
            nas_user=nas_user,
            nas_password_set=nas_password_set,
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
            universal_lobby=universal_lobby,
            velocity_running=velocity_running,
            velocity_version=velocity_version,
            velocity_installed_version=velocity_installed_version,
            velocity_via_plugins=velocity_via_plugins,
            velocity_log_tail=velocity_log_tail,
            bridge_status=bridge_status,
            plugin_build_status=plugin_build_status,
            plugin_building=plugin_building,
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

    # Front-Door sofort an den neuen Modus angleichen. Gateway und Velocity teilen sich
    # den network_port -> den VERLIERER zuerst stoppen, dann den Gewinner binden.
    try:
        from app.services import gateway_service, proxy_service

        if mode == "velocity":
            gateway_service.reconcile_gateway()       # Modus!=gateway -> Gateway stoppt, Port frei
            proxy_service.reconcile_velocity_async()  # Velocity im Hintergrund hochziehen
        else:
            proxy_service.stop_velocity()             # schnell: Velocity aus, Port frei
            gateway_service.reconcile_gateway()       # Gateway bindet (falls mode==gateway)
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


@router.post("/settings/velocity/auto-create")
def auto_create_velocity_action(request: Request, db: Session = Depends(get_db)):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import lobby_service

    try:
        ok, message, server_id = lobby_service.create_velocity_lobby(
            db, initiated_by_user_id=current_user.id
        )
    except Exception as exc:  # noqa: BLE001
        push_flash(request, f"Velocity-Einrichtung fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url="/settings", status_code=303)

    push_flash(request, message, "success" if ok else "error")
    if ok and server_id:
        return RedirectResponse(url=f"/servers/{server_id}", status_code=303)
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/velocity/restart")
def restart_velocity_action(request: Request, db: Session = Depends(get_db)):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import app_setting_service, proxy_service

    if app_setting_service.get_network_mode(db) != "velocity":
        push_flash(request, "Velocity laeuft nur im UNIVERSAL-Modus (velocity). Erst Modus setzen.", "error")
        return RedirectResponse(url="/settings", status_code=303)

    proxy_service.restart_velocity_async()
    push_flash(
        request,
        "Velocity wird neu gestartet und laedt dabei die NEUESTEN Via-Plugins "
        "(ViaVersion/ViaBackwards/ViaRewind). Das dauert ~15-30 s. Danach Seite neu laden - "
        "unten unter 'Velocity-Diagnose' siehst du die geladenen Via-Versionen und das Log "
        "(inkl. bis zu welcher MC-Version der Proxy Clients akzeptiert).",
        "success",
    )
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/lobby/build-plugin")
def build_lobby_plugin_action(request: Request, db: Session = Depends(get_db)):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import plugin_build_service

    started = plugin_build_service.run_build_async(user_id=current_user.id)
    if started:
        push_flash(
            request,
            "Plugin-Build laeuft im Hintergrund (~1 Min: Bibliotheken laden + kompilieren). "
            "Das Ergebnis erscheint gleich unter 'Letzter Plugin-Build' (Seite neu laden) und "
            "im Audit-Log. Bei Erfolg wird es automatisch verteilt - Lobby danach neu starten.",
            "success",
        )
    else:
        push_flash(request, "Es laeuft bereits ein Build. Bitte auf das Ergebnis warten.", "error")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/settings/hub/auto-create")
def auto_create_hub_action(request: Request, db: Session = Depends(get_db)):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import hub_setup_service

    try:
        ok, message = hub_setup_service.create_auto_hub(
            db, initiated_by_user_id=current_user.id
        )
    except Exception as exc:  # noqa: BLE001
        push_flash(request, f"Hub-Einrichtung fehlgeschlagen: {exc}", "error")
        return RedirectResponse(url="/settings", status_code=303)

    push_flash(request, message, "success" if ok else "error")
    return RedirectResponse(url="/settings", status_code=303)


@router.post("/hub/start")
def hub_start_action(request: Request, db: Session = Depends(get_db)):
    return _set_hub_running(request, db, enabled=True)


@router.post("/hub/stop")
def hub_stop_action(request: Request, db: Session = Depends(get_db)):
    return _set_hub_running(request, db, enabled=False)


def _set_hub_running(request: Request, db: Session, *, enabled: bool):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import app_setting_service as A

    A.set_hub_lobby_enabled(db, enabled)
    running = enabled
    try:
        from app.services import gateway_service, hub_lobby_service

        hub_lobby_service.reconcile_hub_lobby()
        gateway_service.reconcile_gateway()
        running = hub_lobby_service.is_running()
    except Exception:  # noqa: BLE001
        pass

    audit_service.log_action(
        db,
        action="hub.start" if enabled else "hub.stop",
        user_id=current_user.id,
        details=f"running={running}",
    )
    if enabled:
        msg = "Universal-Hub gestartet." if running else (
            "Universal-Hub aktiviert, Listener laeuft aber (noch) nicht - meist fehlt ein Replay.")
    else:
        msg = "Universal-Hub gestoppt."
    push_flash(request, msg, "success" if (running or not enabled) else "error")
    return RedirectResponse(url="/dashboard", status_code=303)


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


@router.post("/settings/universal-lobby")
def update_universal_lobby_action(
    request: Request,
    hub_lobby_enabled: Annotated[bool, Form()] = False,
    hub_lobby_port: Annotated[str | None, Form()] = None,
    hub_lobby_vanilla_port: Annotated[str | None, Form()] = None,
    hub_lobby_replay: Annotated[str | None, Form()] = None,
    hub_lobby_vanilla_replay: Annotated[str | None, Form()] = None,
    viaproxy_enabled: Annotated[bool, Form()] = False,
    viaproxy_port: Annotated[str | None, Form()] = None,
    presence_bridge_enabled: Annotated[bool, Form()] = False,
    hub_name: Annotated[str | None, Form()] = None,
    hub_motd: Annotated[str | None, Form()] = None,
    hub_max_players: Annotated[str | None, Form()] = None,
    hub_whitelist_enabled: Annotated[bool, Form()] = False,
    hub_whitelist: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from app.services import app_setting_service as A

    try:
        if (hub_lobby_port or "").strip():
            A.set_hub_lobby_port(db, int(hub_lobby_port))
        if (hub_lobby_vanilla_port or "").strip():
            A.set_hub_lobby_vanilla_port(db, int(hub_lobby_vanilla_port))
        if (viaproxy_port or "").strip():
            A.set_viaproxy_port(db, int(viaproxy_port))
        if (hub_max_players or "").strip():
            A.set_hub_max_players(db, int(hub_max_players))
        A.set_hub_lobby_replay(db, hub_lobby_replay)
        A.set_hub_lobby_vanilla_replay(db, hub_lobby_vanilla_replay)
        A.set_hub_name(db, hub_name)
        A.set_hub_motd(db, hub_motd)
        A.set_hub_whitelist(db, hub_whitelist)
        A.set_hub_whitelist_enabled(db, hub_whitelist_enabled)
        A.set_hub_lobby_enabled(db, hub_lobby_enabled)
        A.set_viaproxy_enabled(db, viaproxy_enabled)
        A.set_presence_bridge_enabled(db, presence_bridge_enabled)
    except (ValueError, TypeError) as exc:
        push_flash(request, str(exc), "error")
        return RedirectResponse(url="/settings", status_code=303)

    # Sofort anwenden: Hub-Listener, ViaProxy-Prozess und Gateway-Routen angleichen.
    try:
        from app.services import gateway_service, hub_lobby_service, viaproxy_service

        hub_lobby_service.reconcile_hub_lobby()
        viaproxy_service.reconcile_viaproxy()
        gateway_service.reconcile_gateway()
    except Exception:  # noqa: BLE001
        pass

    audit_service.log_action(
        db,
        action="settings.universal_lobby_update",
        user_id=current_user.id,
        details=f"hub={hub_lobby_enabled} viaproxy={viaproxy_enabled}",
    )
    push_flash(request, "Universal-Lobby-Einstellungen gespeichert.", "success")
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


@router.post("/settings/central-storage")
def update_central_storage_action(
    request: Request,
    central_storage_root: Annotated[str | None, Form()] = None,
    action: Annotated[str | None, Form()] = None,  # save | test | clear
    nas_user: Annotated[str | None, Form()] = None,
    nas_password: Annotated[str | None, Form()] = None,
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    from pathlib import Path

    from app.services import app_setting_service as A
    from app.services import central_storage_service

    raw = (central_storage_root or "").strip()

    if action == "clear":
        A.set_central_storage_root(db, None)
        A.set_nas_user(db, None)
        A.set_nas_password(db, None)
        try:
            A.clear_backup_storage_override(db)  # Backups zurueck auf Standard/lokal
        except Exception:  # noqa: BLE001
            pass
        audit_service.log_action(db, action="settings.central_storage_clear",
                                 user_id=current_user.id, details="cleared")
        push_flash(request, "Zentrales NAS-Verzeichnis geleert - Logs + Backups wieder lokal, NAS-Anmeldung entfernt.", "success")
        return RedirectResponse(url="/settings", status_code=303)

    # NAS-Anmeldung fuer test + save speichern (Passwort leer = beibehalten), damit die
    # Schreibprobe/Verbindung sie sofort nutzt.
    A.set_nas_user(db, (nas_user or "").strip() or None)
    if (nas_password or "").strip():
        A.set_nas_password(db, nas_password)

    if action == "test":
        ok, msg = central_storage_service.probe_now(raw)
        push_flash(request, msg, "success" if ok else "error")
        return RedirectResponse(url="/settings", status_code=303)

    if not raw:
        A.set_central_storage_root(db, None)
        try:
            A.clear_backup_storage_override(db)
        except Exception:  # noqa: BLE001
            pass
        push_flash(request, "Kein Pfad angegeben - alles bleibt lokal (data/).", "success")
        return RedirectResponse(url="/settings", status_code=303)

    ok, msg = central_storage_service.probe_now(raw)
    # Pfad merken: Logs faellt bei Nicht-Erreichbarkeit automatisch auf lokal zurueck.
    A.set_central_storage_root(db, raw)
    backups_path = "(lokal - kein NAS-Schreibzugriff)"
    if ok:
        # Backups NUR auf die NAS zeigen, wenn wirklich beschreibbar (fuer Backups gibt es
        # keinen Auto-Fallback - sonst wuerden Backups auf ein totes Ziel schreiben).
        backups_path = str(Path(raw) / "backups")
        try:
            A.set_backup_storage_root(db, backups_path)
        except Exception:  # noqa: BLE001
            backups_path = "(Backup-Pfad manuell setzen)"
    audit_service.log_action(db, action="settings.central_storage_update",
                             user_id=current_user.id, details=f"root={raw} probe_ok={ok}")
    if ok:
        push_flash(request, (f"NAS-Verzeichnis aktiv. Logs -> {raw}\\logs, "
                             f"Backups -> {backups_path}, DB-Snapshots -> {raw}\\db-snapshots."), "success")
    else:
        push_flash(request, (f"Pfad gespeichert, aber KEIN Schreibzugriff aus Manager-Sicht: {msg}. "
                             "Logs bleiben lokal (automatischer Fallback), Backups NICHT umgestellt. "
                             "Ursache meist: Dienst laeuft als SYSTEM ohne NAS-Zugang - siehe Anleitung."), "error")
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
    force: str = Form(default=""),
    db: Session = Depends(get_db),
):
    current_user = _require_super_admin(request, db)
    if current_user is None:
        return RedirectResponse(url="/login", status_code=303)

    force_flag = force.strip().lower() in {"1", "true", "on", "yes"}
    ok, message = trigger_manager_update(force=force_flag)
    push_flash(request, message, "success" if ok else "error")
    audit_service.log_action(
        db,
        action="settings.manager_update_apply",
        user_id=current_user.id,
        details=f"ok={ok} force={force_flag} message={message}",
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
