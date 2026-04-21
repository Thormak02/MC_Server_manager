import re
from pathlib import Path

from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from app.core.constants import DEFAULT_SERVER_STATUS, UserRole
from app.models.installed_content import InstalledContent
from app.models.scheduled_job import ScheduledJob
from app.models.server import Server
from app.models.server_permission import ServerPermission
from app.models.user import User
from app.services.java_runtime_service import choose_best_java_profile
from app.schemas.server import ServerCreate, ServerImportConfirm


_XMS_PATTERN = re.compile(r"(?i)-Xms\S+")
_XMX_PATTERN = re.compile(r"(?i)-Xmx\S+")
_JAVA_TOKEN_PATTERN = re.compile(r'(?i)("[^"]*java(?:\.exe)?"|java(?:\.exe)?)')


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
    java_profile_id = data.java_profile_id
    if java_profile_id is None:
        auto_profile = choose_best_java_profile(db, mc_version=data.mc_version)
        if auto_profile is not None:
            java_profile_id = auto_profile.id
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
        java_profile_id=java_profile_id,
        memory_min_mb=data.memory_min_mb,
        memory_max_mb=data.memory_max_mb,
        port=data.port,
        status=DEFAULT_SERVER_STATUS,
        auto_restart=False,
    )
    db.add(server)
    db.commit()
    db.refresh(server)

    # Safety cleanup for environments where old orphan rows existed and IDs get reused.
    cleanup_changed = False
    for table in (InstalledContent, ServerPermission, ScheduledJob):
        result = db.execute(delete(table).where(table.server_id == server.id))
        if (result.rowcount or 0) > 0:
            cleanup_changed = True
    if cleanup_changed:
        db.commit()
        db.refresh(server)

    return server


def _memory_token(value_mb: int | None, kind: str) -> str | None:
    if value_mb is None:
        return None
    if value_mb <= 0:
        return None
    return f"-X{kind}{value_mb}M"


def _apply_memory_flags_to_command(
    command: str,
    memory_min_mb: int | None,
    memory_max_mb: int | None,
) -> str:
    updated = _XMS_PATTERN.sub("", command)
    updated = _XMX_PATTERN.sub("", updated)
    updated = " ".join(updated.split())

    tokens: list[str] = []
    xms = _memory_token(memory_min_mb, "ms")
    xmx = _memory_token(memory_max_mb, "mx")
    if xms:
        tokens.append(xms)
    if xmx:
        tokens.append(xmx)
    if not tokens:
        return updated

    java_match = _JAVA_TOKEN_PATTERN.search(updated)
    if not java_match:
        return " ".join(tokens + [updated]).strip()

    insert_pos = java_match.end()
    before = updated[:insert_pos]
    after = updated[insert_pos:].strip()
    merged = f"{before} {' '.join(tokens)}"
    if after:
        merged = f"{merged} {after}"
    return merged.strip()


def _upsert_server_property(server: Server, key: str, value: str) -> str | None:
    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists() or not base_path.is_dir():
        return f"Serverordner nicht gefunden: {base_path}"

    properties_path = base_path / "server.properties"
    lines: list[str] = []
    if properties_path.exists():
        lines = properties_path.read_text(encoding="utf-8", errors="ignore").splitlines()

    prefix = f"{key}="
    replaced = False
    output: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith(prefix):
            output.append(f"{key}={value}")
            replaced = True
        else:
            output.append(line)

    if not replaced:
        output.append(f"{key}={value}")

    properties_path.write_text("\n".join(output).strip() + "\n", encoding="utf-8")
    return None


def _sync_forge_jvm_args(server: Server) -> str | None:
    base_path = Path(server.base_path).expanduser().resolve()
    args_path = base_path / "user_jvm_args.txt"
    existing_lines: list[str] = []
    if args_path.exists():
        existing_lines = args_path.read_text(encoding="utf-8", errors="ignore").splitlines()

    output: list[str] = []
    for line in existing_lines:
        stripped = line.strip()
        if stripped.lower().startswith("-xms") or stripped.lower().startswith("-xmx"):
            continue
        output.append(line)

    xms = _memory_token(server.memory_min_mb, "ms")
    xmx = _memory_token(server.memory_max_mb, "mx")
    if xms:
        output.append(xms)
    if xmx:
        output.append(xmx)

    args_path.write_text("\n".join(output).strip() + "\n", encoding="utf-8")
    return None


def _sync_bat_start_memory(server: Server) -> str | None:
    base_path = Path(server.base_path).expanduser().resolve()
    bat_path = Path(server.start_bat_path or str(base_path / "start.bat")).expanduser()
    if not bat_path.is_absolute():
        bat_path = (base_path / bat_path).resolve()
    else:
        bat_path = bat_path.resolve()
    if not bat_path.exists():
        return f"Startdatei nicht gefunden: {bat_path}"

    content = bat_path.read_text(encoding="utf-8", errors="ignore")
    lines = content.splitlines()
    changed = False
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.upper().startswith("REM ") or stripped.startswith("::"):
            continue
        if "java" not in stripped.lower():
            continue
        new_line = _apply_memory_flags_to_command(line, server.memory_min_mb, server.memory_max_mb)
        if new_line != line:
            lines[idx] = new_line
            changed = True
        break

    if changed:
        bat_path.write_text("\n".join(lines).strip() + "\n", encoding="utf-8")
    return None


def sync_server_settings_to_files(server: Server) -> list[str]:
    warnings: list[str] = []

    if server.port is not None:
        warning = _upsert_server_property(server, "server-port", str(server.port))
        if warning:
            warnings.append(warning)

    if server.server_type in {"forge", "neoforge"}:
        warning = _sync_forge_jvm_args(server)
        if warning:
            warnings.append(warning)
    elif server.start_mode == "bat":
        warning = _sync_bat_start_memory(server)
        if warning:
            warnings.append(warning)
    elif server.start_mode == "command" and server.start_command:
        server.start_command = _apply_memory_flags_to_command(
            server.start_command,
            server.memory_min_mb,
            server.memory_max_mb,
        )

    return warnings


def update_server_settings(
    db: Session,
    server: Server,
    *,
    mc_version: str | None,
    loader_version: str | None,
    java_profile_id: int | None,
    memory_min_mb: int | None,
    memory_max_mb: int | None,
    port: int | None,
    auto_restart: bool,
    start_mode: str | None,
    start_command: str | None,
    start_bat_path: str | None,
) -> tuple[Server, list[str]]:
    if mc_version is not None:
        stripped_version = mc_version.strip()
        if stripped_version:
            server.mc_version = stripped_version
    server.loader_version = (loader_version or "").strip() or None
    server.java_profile_id = java_profile_id
    if server.java_profile_id is None:
        auto_profile = choose_best_java_profile(db, mc_version=server.mc_version)
        if auto_profile is not None:
            server.java_profile_id = auto_profile.id
            warnings = [f"Java-Profil automatisch gesetzt: {auto_profile.name}"]
        else:
            warnings = []
    else:
        warnings = []
    server.memory_min_mb = memory_min_mb
    server.memory_max_mb = memory_max_mb
    server.port = port
    server.auto_restart = auto_restart
    if start_mode:
        server.start_mode = start_mode
    server.start_command = start_command
    server.start_bat_path = start_bat_path

    warnings.extend(sync_server_settings_to_files(server))

    db.add(server)
    db.commit()
    db.refresh(server)
    return server, warnings


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
