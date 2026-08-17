from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from starlette.middleware.sessions import SessionMiddleware

from app.api.routers.auth import router as auth_router
from app.api.routers.backups import router as backups_router
from app.api.routers.console import router as console_router
from app.api.routers.content import router as content_router
from app.api.routers.dashboard import router as dashboard_router
from app.api.routers.files import router as files_router
from app.api.routers.java_profiles import router as java_profiles_router
from app.api.routers.modpacks import router as modpacks_router
from app.api.routers.provisioning import router as provisioning_router
from app.api.routers.schedules import router as schedules_router
from app.api.routers.security_events import router as security_events_router
from app.api.routers.servers import router as servers_router
from app.api.routers.server_templates import router as server_templates_router
from app.api.routers.system_status import router as system_status_router
from app.api.routers.users import router as users_router
from app.core.config import get_settings
from app.db.init_db import init_db
from app.middleware.csrf import CSRFSameOriginMiddleware
from app.services.schedule_service import sync_all_jobs
from app.services import gateway_service, sleep_proxy_service, velocity_service
from app.services.process_service import (
    reconcile_runtime_states_on_manager_startup,
    shutdown_all_managed_processes,
    start_servers_marked_for_manager_startup,
)
from app.tasks.scheduler import shutdown_scheduler, start_scheduler
from app.web.routes.pages import router as page_router
from app.websocket.console_ws import router as console_ws_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    init_db()
    reconcile_runtime_states_on_manager_startup()
    start_scheduler()
    sync_all_jobs()
    # Gateway VOR dem Autostart aufsetzen: Gateway-Server bekommen so ihren internen
    # Port + server.properties, bevor der MC-Prozess einen Port bindet.
    # Gemeinsame Eingangstuer (Gateway ODER Velocity) passend zum Netzwerk-Modus
    # aufsetzen. Muss VOR dem Autostart laufen, damit Gateway-Server ihren internen
    # Port + server.properties bekommen, bevor der MC-Prozess einen Port bindet.
    velocity_service.mark_startup()
    velocity_service.reconcile_network()
    start_servers_marked_for_manager_startup()
    sleep_proxy_service.reconcile_proxies()
    sleep_proxy_service.start_idle_monitor()
    try:
        yield
    finally:
        # Shutdown
        velocity_service.stop_velocity(shutting_down=True)
        gateway_service.stop_gateway()
        sleep_proxy_service.shutdown_all()
        shutdown_all_managed_processes(preserve_for_restart=True)
        shutdown_scheduler()


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title=settings.app_name, debug=settings.debug, lifespan=lifespan)

    app.add_middleware(
        CSRFSameOriginMiddleware,
        enabled=settings.csrf_protection_enabled,
    )
    app.add_middleware(
        SessionMiddleware,
        secret_key=settings.secret_key,
        session_cookie=settings.session_cookie_name,
        max_age=settings.session_max_age_seconds,
        same_site="lax",
        https_only=False,
    )

    static_dir = Path(__file__).resolve().parent / "web" / "static"
    app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")

    # Oeffentlich (ohne Login) ausgelieferte, selbst-gehostete Resource Packs.
    # Clients laden hierueber den ueber server.properties gesetzten Pack.
    resourcepacks_dir = settings.data_dir / "resourcepacks"
    resourcepacks_dir.mkdir(parents=True, exist_ok=True)
    app.mount(
        "/resourcepacks",
        StaticFiles(directory=str(resourcepacks_dir)),
        name="resourcepacks",
    )

    app.include_router(page_router)
    app.include_router(auth_router)
    app.include_router(backups_router)
    app.include_router(console_router)
    app.include_router(content_router)
    app.include_router(dashboard_router)
    app.include_router(files_router)
    app.include_router(java_profiles_router)
    app.include_router(modpacks_router)
    app.include_router(provisioning_router)
    app.include_router(schedules_router)
    app.include_router(security_events_router)
    app.include_router(servers_router)
    app.include_router(server_templates_router)
    app.include_router(system_status_router)
    app.include_router(users_router)
    app.include_router(console_ws_router)

    return app


app = create_app()
