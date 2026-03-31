import re
from pathlib import Path

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.constants import DEFAULT_SERVER_STATUS, UserRole
from app.models.server import Server
from app.models.server_permission import ServerPermission
from app.models.user import User
from app.schemas.server import ServerCreate, ServerImportConfirm


def slugify(value: str) -> str:
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    value = value.strip("-")
    return value or "server"


def _generate_unique_slug(db: Session, name: str) -> str:
    base_slug = slugify(name)
    candidate = base_slug
    index = 2
    while db.scalar(select(Server).where(Server.slug == candidate)) is not None:
        candidate = f"{base_slug}-{index}"
        index += 1
    return candidate


def _generate_unique_name(db: Session, name: str) -> str:
    normalized = name.strip() or "Server"
    candidate = normalized
    index = 2
    while db.scalar(select(Server).where(Server.name == candidate)) is not None:
        candidate = f"{normalized} ({index})"
        index += 1
    return candidate


def list_servers_for_user(db: Session, user: User) -> list[Server]:
    if user.role == UserRole.SUPER_ADMIN.value:
        return list(db.scalars(select(Server).order_by(Server.name.asc())).all())

    query = (
        select(Server)
        .join(ServerPermission, ServerPermission.server_id == Server.id)
        .where(ServerPermission.user_id == user.id, ServerPermission.can_view.is_(True))
        .order_by(Server.name.asc())
    )
    return list(db.scalars(query).all())


def get_server_by_id(db: Session, server_id: int) -> Server | None:
    return db.get(Server, server_id)


def can_view_server(db: Session, user: User, server: Server) -> bool:
    if user.role == UserRole.SUPER_ADMIN.value:
        return True
    permission = db.scalar(
        select(ServerPermission).where(
            ServerPermission.user_id == user.id,
            ServerPermission.server_id == server.id,
        )
    )
    return bool(permission and permission.can_view)


def can_control_server(db: Session, user: User, server: Server) -> bool:
    if user.role == UserRole.SUPER_ADMIN.value:
        return True
    permission = db.scalar(
        select(ServerPermission).where(
            ServerPermission.user_id == user.id,
            ServerPermission.server_id == server.id,
        )
    )
    if not permission:
        return False
    return bool(permission.can_manage or permission.can_restart or permission.can_console)


def can_edit_server_files(db: Session, user: User, server: Server) -> bool:
    if user.role == UserRole.SUPER_ADMIN.value:
        return True
    permission = db.scalar(
        select(ServerPermission).where(
            ServerPermission.user_id == user.id,
            ServerPermission.server_id == server.id,
        )
    )
    if not permission:
        return False
    return bool(permission.can_manage or permission.can_edit_files)


def create_server(db: Session, data: ServerCreate) -> Server:
    base_path = str(Path(data.base_path).resolve())
    unique_name = _generate_unique_name(db, data.name)
    server = Server(
        name=unique_name,
        slug=_generate_unique_slug(db, unique_name),
        server_type=data.server_type,
        mc_version=data.mc_version,
        loader_version=data.loader_version,
        base_path=base_path,
        start_mode=data.start_mode,
        start_command=data.start_command,
        start_bat_path=data.start_bat_path,
        java_profile_id=data.java_profile_id,
        memory_min_mb=data.memory_min_mb,
        memory_max_mb=data.memory_max_mb,
        port=data.port,
        status=DEFAULT_SERVER_STATUS,
        auto_restart=False,
    )
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def update_server_settings(
    db: Session,
    server: Server,
    *,
    java_profile_id: int | None,
    memory_min_mb: int | None,
    memory_max_mb: int | None,
    port: int | None,
    auto_restart: bool,
    start_mode: str | None,
    start_command: str | None,
    start_bat_path: str | None,
) -> Server:
    server.java_profile_id = java_profile_id
    server.memory_min_mb = memory_min_mb
    server.memory_max_mb = memory_max_mb
    server.port = port
    server.auto_restart = auto_restart
    if start_mode:
        server.start_mode = start_mode
    server.start_command = start_command
    server.start_bat_path = start_bat_path
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def create_server_from_import(db: Session, data: ServerImportConfirm) -> Server:
    create_data = ServerCreate(
        name=data.name.strip(),
        server_type=data.server_type,
        mc_version=data.mc_version or "unknown",
        loader_version=data.loader_version,
        base_path=data.base_path,
        start_mode=data.start_mode,
        start_command=data.start_command,
        start_bat_path=data.start_bat_path,
        java_profile_id=data.java_profile_id,
        memory_min_mb=data.memory_min_mb,
        memory_max_mb=data.memory_max_mb,
        port=data.port,
    )
    return create_server(db, create_data)


def get_dashboard_summary(db: Session, user: User) -> dict[str, object]:
    visible_servers = list_servers_for_user(db, user)
    running_servers = sum(1 for server in visible_servers if server.status == "running")

    user_count: int | None = None
    if user.role == UserRole.SUPER_ADMIN.value:
        user_count = int(db.scalar(select(func.count(User.id))) or 0)

    return {
        "total_servers": len(visible_servers),
        "running_servers": running_servers,
        "user_count": user_count,
        "servers": visible_servers,
    }
