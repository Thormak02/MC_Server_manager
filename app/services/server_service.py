import re
from pathlib import Path

from sqlalchemy import delete, func, select, update
from sqlalchemy.orm import Session

from app.core.constants import DEFAULT_SERVER_STATUS, UserRole
from app.models.installed_content import InstalledContent
from app.models.scheduled_job import ScheduledJob
from app.models.server import Server
from app.models.server_permission import ServerPermission
from app.models.user import User
from app.services.java_runtime_service import choose_best_java_profile
from app.services.memory_settings_service import validate_memory_bounds
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
    from app.services import port_service

    base_path = str(Path(data.base_path).resolve())
    unique_name = _generate_unique_name(db, data.name)
    memory_min_mb, memory_max_mb = validate_memory_bounds(data.memory_min_mb, data.memory_max_mb)
    java_profile_id = data.java_profile_id
    if java_profile_id is None:
        auto_profile = choose_best_java_profile(db, mc_version=data.mc_version)
        if auto_profile is not None:
            java_profile_id = auto_profile.id

    # Kein Port angegeben -> host-weit freien Port aus dem Bereich vergeben.
    assigned_port = data.port
    if assigned_port is None:
        try:
            assigned_port = port_service.allocate_server_port(db)
        except ValueError:
            assigned_port = None

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
        memory_min_mb=memory_min_mb,
        memory_max_mb=memory_max_mb,
        port=assigned_port,
        status=DEFAULT_SERVER_STATUS,
        auto_restart=False,
        auto_start_with_manager=False,
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


def _sync_legacy_forge_run_bat_memory(server: Server) -> str | None:
    """Speicher-Flags in einem generierten Legacy-Forge ``run.bat`` aktualisieren.

    Modernes Forge (>=1.17) bezieht den Speicher aus ``user_jvm_args.txt`` und
    hat KEINE ``-Xmx`` im run.bat – solche run.bat werden nicht angefasst.
    """
    base_path = Path(server.base_path).expanduser().resolve()
    run_bat = base_path / "run.bat"
    if not run_bat.exists():
        return None
    try:
        content = run_bat.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return None
    if "-xmx" not in content.lower():
        return None

    lines = content.splitlines()
    changed = False
    for idx, line in enumerate(lines):
        if "java" not in line.lower():
            continue
        new_line = _apply_memory_flags_to_command(
            line, server.memory_min_mb, server.memory_max_mb
        )
        if new_line != line:
            lines[idx] = new_line
            changed = True
        break
    if changed:
        run_bat.write_text("\n".join(lines).strip() + "\n", encoding="utf-8")
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


_PENDING_STATUSES = {
    "starting",
    "restarting",
    "stopping",
    "backup_running",
    "provisioning",
}


def server_status_view(server: Server) -> dict[str, str]:
    """Anzeige-Status + Farb-Key fuer die UI.

    color: online (gruen), pending (orange), sleeping (lila), offline (rot).
    Ein Sleep-Server im Zustand 'stopped' wird als 'sleeping' angezeigt.
    """
    status = server.status or "stopped"
    if status == "running":
        return {"status": "running", "color": "online"}
    if status in _PENDING_STATUSES:
        return {"status": status, "color": "pending"}
    if getattr(server, "sleep_enabled", False) and status == "stopped":
        return {"status": "sleeping", "color": "sleeping"}
    if status in {"crashed", "error"}:
        return {"status": status, "color": "offline"}
    return {"status": status, "color": "offline"}


_SLEEP_DELAY_UNITS: list[tuple[str, int]] = [
    ("days", 86400),
    ("hours", 3600),
    ("minutes", 60),
    ("seconds", 1),
]


def split_sleep_delay_seconds(seconds: int | None) -> dict[str, object]:
    """Sekunden in die groesste glatt teilbare Einheit zerlegen.

    z.B. 300 -> {value: 5, unit: "minutes"}, 86400 -> {value: 1, unit: "days"}.
    Fuer die UI, damit die Verzoegerung in Sek/Min/Std/Tage angezeigt wird.
    """
    total = int(seconds) if seconds else 0
    if total <= 0:
        return {"value": 0, "unit": "seconds"}
    for unit, mult in _SLEEP_DELAY_UNITS:
        if total % mult == 0:
            return {"value": total // mult, "unit": unit}
    return {"value": total, "unit": "seconds"}


def sleep_delay_to_seconds(value: int | None, unit: str | None) -> int | None:
    """Wert + Einheit (seconds/minutes/hours/days) in Sekunden umrechnen."""
    if value is None:
        return None
    multipliers = {mult_unit: mult for mult_unit, mult in _SLEEP_DELAY_UNITS}
    mult = multipliers.get((unit or "seconds").strip().lower(), 1)
    return max(0, int(value)) * mult


_GATEWAY_HOSTNAME_RE = re.compile(r"^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$")


def normalize_gateway_hostname(raw: str | None) -> str:
    """Alias normalisieren: Kleinschreibung, ohne fuehrende/abschliessende Punkte."""
    return (raw or "").strip().strip(".").lower()


def is_valid_gateway_hostname(value: str) -> bool:
    return (
        bool(value)
        and len(value) <= 255
        and ".." not in value
        and _GATEWAY_HOSTNAME_RE.match(value) is not None
    )


def gateway_hostname_taken(
    db: Session, hostname: str, *, exclude_server_id: int | None = None
) -> bool:
    stmt = select(Server.id).where(func.lower(Server.gateway_hostname) == hostname.lower())
    if exclude_server_id is not None:
        stmt = stmt.where(Server.id != exclude_server_id)
    return db.scalar(stmt) is not None


def effective_server_port(server: Server) -> int | None:
    """Port, auf dem der echte MC-Server laeuft.

    Sleep- **oder** Gateway-Server laufen auf ``sleep_internal_port`` (der
    oeffentliche ``port`` wird vom Sleep-Proxy bzw. vom Gateway belegt). Sonst
    der normale Port.
    """
    behind_proxy = server.sleep_enabled or getattr(server, "gateway_enabled", False)
    if behind_proxy and server.sleep_internal_port:
        return int(server.sleep_internal_port)
    return server.port


def sync_server_settings_to_files(server: Server) -> list[str]:
    warnings: list[str] = []

    internal_port = effective_server_port(server)
    if internal_port is not None:
        warning = _upsert_server_property(server, "server-port", str(internal_port))
        if warning:
            warnings.append(warning)

    if server.server_type in {"forge", "neoforge"}:
        warning = _sync_forge_jvm_args(server)
        if warning:
            warnings.append(warning)
        if server.server_type == "forge":
            # Legacy-Forge (<1.17) startet ueber ein generiertes run.bat mit
            # literalen -Xms/-Xmx – dort die Speicher-Flags mitziehen.
            warning = _sync_legacy_forge_run_bat_memory(server)
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
    auto_start_with_manager: bool,
    start_mode: str | None,
    start_command: str | None,
    start_bat_path: str | None,
    sleep_enabled: bool = False,
    sleep_delay_seconds: int | None = None,
    gateway_enabled: bool = False,
    gateway_hostname: str | None = None,
    gateway_is_default: bool = False,
) -> tuple[Server, list[str]]:
    if mc_version is not None:
        stripped_version = mc_version.strip()
        if stripped_version:
            server.mc_version = stripped_version
    memory_min_mb, memory_max_mb = validate_memory_bounds(memory_min_mb, memory_max_mb)
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
    server.auto_start_with_manager = auto_start_with_manager
    if start_mode:
        server.start_mode = start_mode
    server.start_command = start_command
    server.start_bat_path = start_bat_path

    server.sleep_enabled = bool(sleep_enabled)
    if sleep_delay_seconds is not None:
        server.sleep_delay_seconds = max(0, int(sleep_delay_seconds))
    if server.sleep_enabled:
        # Interner Port fuer den echten Server hinter dem Proxy sicherstellen.
        from app.services import port_service

        if not server.port:
            warnings.append(
                "Sleep-Modus benoetigt einen festen Port - bitte Port setzen."
            )
            server.sleep_enabled = False
        elif (
            not server.sleep_internal_port
            or server.sleep_internal_port == server.port
        ):
            preferred = server.port + 1 if server.port < 65535 else None
            try:
                server.sleep_internal_port = port_service.allocate_server_port(
                    db,
                    preferred=preferred,
                    exclude={server.port},
                    exclude_server_id=server.id,
                )
            except ValueError as exc:
                warnings.append(str(exc))
                server.sleep_enabled = False
        if server.sleep_enabled and server.status in {
            "running",
            "starting",
            "restarting",
        }:
            warnings.append(
                "Sleep-Modus aktiviert: bitte den Server neu starten, damit er "
                "auf den internen Port wechselt und der Proxy den oeffentlichen "
                "Port uebernehmen kann."
            )

    # --- Lobby-/Gateway-Routing ---
    normalized_alias = normalize_gateway_hostname(gateway_hostname)
    server.gateway_enabled = bool(gateway_enabled)
    server.gateway_hostname = None
    server.gateway_is_default = False
    if server.gateway_enabled:
        if not normalized_alias:
            warnings.append("Gateway-Alias (Hostname) ist erforderlich.")
            server.gateway_enabled = False
        elif not is_valid_gateway_hostname(normalized_alias):
            warnings.append(
                f"Ungueltiger Alias '{normalized_alias}' - erlaubt sind nur "
                "Kleinbuchstaben, Ziffern, '-' und '.'."
            )
            server.gateway_enabled = False
        elif gateway_hostname_taken(db, normalized_alias, exclude_server_id=server.id):
            warnings.append(
                f"Der Alias '{normalized_alias}' ist bereits vergeben - bitte einen "
                "anderen waehlen."
            )
            server.gateway_enabled = False
        else:
            server.gateway_hostname = normalized_alias
            server.gateway_is_default = bool(gateway_is_default)
            # Gateway-Server laeuft auf seinem internen Port -> sicherstellen.
            # Der eigene Gateway-Port darf NIE als interner Port vergeben werden
            # (sonst wuerde das Gateway spaeter auf sich selbst routen).
            from app.services import app_setting_service, port_service

            gateway_port = app_setting_service.get_gateway_port(db)
            if not server.sleep_internal_port or server.sleep_internal_port == server.port:
                preferred = (
                    server.port + 1 if server.port and server.port < 65535 else None
                )
                exclude_ports = {p for p in (server.port, gateway_port) if p}
                try:
                    server.sleep_internal_port = port_service.allocate_server_port(
                        db,
                        preferred=preferred,
                        exclude=exclude_ports or None,
                        exclude_server_id=server.id,
                    )
                except ValueError as exc:
                    warnings.append(str(exc))
            if server.status in {"running", "starting", "restarting"}:
                warnings.append(
                    "Gateway aktiviert: bitte den Server neu starten, damit er auf "
                    "den internen Port wechselt (erreichbar dann ueber das Gateway)."
                )

    # Genau EIN Default-/Lobby-Server: beim Setzen die Markierung aller anderen
    # Server zuruecksetzen.
    if server.gateway_is_default:
        db.execute(
            update(Server)
            .where(Server.id != server.id)
            .values(gateway_is_default=False)
        )

    warnings.extend(sync_server_settings_to_files(server))

    db.add(server)
    db.commit()
    db.refresh(server)

    # Proxy-/Gateway-Zustand an die neue Einstellung angleichen (Start/Stop der
    # Listener, Routing-Tabelle aktualisieren).
    try:
        from app.services import sleep_proxy_service

        sleep_proxy_service.reconcile_proxies()
    except Exception:  # noqa: BLE001 - UI-Update darf daran nicht scheitern
        pass
    try:
        from app.services import gateway_service

        gateway_service.reconcile_gateway()
    except Exception:  # noqa: BLE001
        pass

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
