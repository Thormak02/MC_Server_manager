from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from starlette.middleware.sessions import SessionMiddleware

from app.api.routers.auth import router as auth_router
from app.api.routers.console import router as console_router
from app.api.routers.content import router as content_router
from app.api.routers.dashboard import router as dashboard_router
from app.api.routers.files import router as files_router
from app.api.routers.java_profiles import router as java_profiles_router
from app.api.routers.provisioning import router as provisioning_router
from app.api.routers.schedules import router as schedules_router
from app.api.routers.servers import router as servers_router
from app.api.routers.server_templates import router as server_templates_router
from app.api.routers.users import router as users_router
from app.core.config import get_settings
from app.db.init_db import init_db
from app.services.schedule_service import sync_all_jobs
from app.services.process_service import shutdown_all_managed_processes
from app.tasks.scheduler import shutdown_scheduler, start_scheduler
from app.web.routes.pages import router as page_router
from app.websocket.console_ws import router as console_ws_router


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title=settings.app_name, debug=settings.debug)

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

    app.include_router(page_router)
    app.include_router(auth_router)
    app.include_router(console_router)
    app.include_router(content_router)
    app.include_router(dashboard_router)
    app.include_router(files_router)
    app.include_router(java_profiles_router)
    app.include_router(provisioning_router)
    app.include_router(schedules_router)
    app.include_router(servers_router)
    app.include_router(server_templates_router)
    app.include_router(users_router)
    app.include_router(console_ws_router)

    @app.on_event("startup")
    def on_startup() -> None:
        init_db()
        start_scheduler()
        sync_all_jobs()

    @app.on_event("shutdown")
    def on_shutdown() -> None:
        shutdown_all_managed_processes()
        shutdown_scheduler()

    return app


app = create_app()
