from sqlalchemy import delete, select, update

from app.core.config import get_settings
from app.core.constants import UserRole
from app.core.security import hash_password
from app.db.base import Base
from app.db.session import SessionLocal, engine
from app.models.audit_log import AuditLog  # noqa: F401
from app.models.app_setting import AppSetting  # noqa: F401
from app.models.installed_content import InstalledContent  # noqa: F401
from app.models.java_profile import JavaProfile  # noqa: F401
from app.models.scheduled_job import ScheduledJob  # noqa: F401
from app.models.server import Server  # noqa: F401
from app.models.server_permission import ServerPermission  # noqa: F401
from app.models.server_template import ServerTemplate  # noqa: F401
from app.models.user import User
from app.services.app_setting_service import ensure_server_storage_initialized


def init_db() -> None:
    settings = get_settings()
    settings.ensure_data_dir()
    Base.metadata.create_all(bind=engine)
    _normalize_runtime_states()
    _cleanup_orphaned_server_relations()
    _seed_super_admin()
    _ensure_server_storage_root()


def _seed_super_admin() -> None:
    settings = get_settings()
    with SessionLocal() as db:
        existing = db.scalar(
            select(User).where(User.username == settings.initial_superadmin_username)
        )
        if existing:
            return

        user = User(
            username=settings.initial_superadmin_username,
            password_hash=hash_password(settings.initial_superadmin_password),
            role=UserRole.SUPER_ADMIN.value,
            is_active=True,
        )
        db.add(user)
        db.commit()


def _normalize_runtime_states() -> None:
    with SessionLocal() as db:
        db.execute(
            update(Server)
            .where(
                Server.status.in_(
                    [
                        "running",
                        "starting",
                        "stopping",
                        "restarting",
                        "backup_running",
                        "provisioning",
                    ]
                )
            )
            .values(status="stopped")
        )
        db.commit()


def _cleanup_orphaned_server_relations() -> None:
    with SessionLocal() as db:
        server_ids = select(Server.id)
        db.execute(delete(InstalledContent).where(~InstalledContent.server_id.in_(server_ids)))
        db.execute(delete(ServerPermission).where(~ServerPermission.server_id.in_(server_ids)))
        db.execute(delete(ScheduledJob).where(~ScheduledJob.server_id.in_(server_ids)))
        db.commit()


def _ensure_server_storage_root() -> None:
    with SessionLocal() as db:
        ensure_server_storage_initialized(db)
