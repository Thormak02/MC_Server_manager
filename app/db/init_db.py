from sqlalchemy import select, update

from app.core.config import get_settings
from app.core.constants import UserRole
from app.core.security import hash_password
from app.db.base import Base
from app.db.session import SessionLocal, engine
from app.models.audit_log import AuditLog  # noqa: F401
from app.models.installed_content import InstalledContent  # noqa: F401
from app.models.java_profile import JavaProfile  # noqa: F401
from app.models.scheduled_job import ScheduledJob  # noqa: F401
from app.models.server import Server  # noqa: F401
from app.models.server_permission import ServerPermission  # noqa: F401
from app.models.server_template import ServerTemplate  # noqa: F401
from app.models.user import User


def init_db() -> None:
    settings = get_settings()
    settings.ensure_data_dir()
    Base.metadata.create_all(bind=engine)
    _normalize_runtime_states()
    _seed_super_admin()


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
