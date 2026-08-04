from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.app_setting import AppSetting


SERVER_STORAGE_ROOT_KEY = "server_storage_root"
BACKUP_STORAGE_ROOT_KEY = "backup_storage_root"
PUBLIC_BASE_URL_KEY = "public_base_url"


def _normalize_path(raw_value: str) -> Path:
    return Path(raw_value).expanduser().resolve()


def _repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _looks_like_service_profile(home_path: Path) -> bool:
    normalized = str(home_path).replace("/", "\\").lower()
    return (
        "\\windows\\system32\\config\\systemprofile" in normalized
        or "\\windows\\serviceprofiles\\" in normalized
    )


def _default_desktop_storage_path() -> Path:
    home_path = Path.home().expanduser().resolve()
    if _looks_like_service_profile(home_path):
        return (_repository_root() / "managed_servers").resolve()
    return (home_path / "Desktop" / "mc_servers").resolve()


def _default_backup_storage_path() -> Path:
    settings = get_settings()
    return (settings.data_dir / "backups").resolve()


def _get_setting_row(db: Session, key: str) -> AppSetting | None:
    return db.scalar(select(AppSetting).where(AppSetting.key == key))


def _get_root_from_sources(
    db: Session,
    *,
    key: str,
    env_value: str | None,
    default_path_factory,
) -> Path:
    row = _get_setting_row(db, key)
    if row and row.value.strip():
        return _normalize_path(row.value.strip())

    env_raw = (env_value or "").strip()
    if env_raw:
        return _normalize_path(env_raw)
    return default_path_factory()


def _get_source(
    db: Session,
    *,
    key: str,
    env_value: str | None,
) -> str:
    row = _get_setting_row(db, key)
    if row and row.value.strip():
        return "ui"
    if (env_value or "").strip():
        return "env"
    return "default"


def _ensure_initialized(
    db: Session,
    *,
    key: str,
    env_value: str | None,
    default_path_factory,
) -> Path:
    row = _get_setting_row(db, key)
    if row and row.value.strip():
        normalized = _normalize_path(row.value.strip())
        normalized.mkdir(parents=True, exist_ok=True)
        if row.value != str(normalized):
            row.value = str(normalized)
            db.add(row)
            db.commit()
        return normalized

    env_raw = (env_value or "").strip()
    if env_raw:
        normalized = _normalize_path(env_raw)
        normalized.mkdir(parents=True, exist_ok=True)
        return normalized

    default_path = default_path_factory()
    default_path.mkdir(parents=True, exist_ok=True)
    return default_path


def _set_root_override(db: Session, *, key: str, path_value: str) -> Path:
    normalized = _normalize_path(path_value.strip())
    normalized.mkdir(parents=True, exist_ok=True)
    row = _get_setting_row(db, key)
    if row is None:
        row = AppSetting(key=key, value=str(normalized))
    else:
        row.value = str(normalized)
    db.add(row)
    db.commit()
    return normalized


def _clear_root_override(
    db: Session,
    *,
    key: str,
    resolver,
) -> Path:
    row = _get_setting_row(db, key)
    if row is not None:
        db.delete(row)
        db.commit()
    path = resolver(db)
    path.mkdir(parents=True, exist_ok=True)
    return path


def get_server_storage_root(db: Session) -> Path:
    settings = get_settings()
    return _get_root_from_sources(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        env_value=settings.default_server_root,
        default_path_factory=_default_desktop_storage_path,
    )


def get_server_storage_source(db: Session) -> str:
    settings = get_settings()
    return _get_source(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        env_value=settings.default_server_root,
    )


def ensure_server_storage_initialized(db: Session) -> Path:
    settings = get_settings()
    return _ensure_initialized(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        env_value=settings.default_server_root,
        default_path_factory=_default_desktop_storage_path,
    )


def set_server_storage_root(db: Session, path_value: str) -> Path:
    return _set_root_override(db, key=SERVER_STORAGE_ROOT_KEY, path_value=path_value)


def clear_server_storage_override(db: Session) -> Path:
    return _clear_root_override(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        resolver=get_server_storage_root,
    )


def get_backup_storage_root(db: Session) -> Path:
    settings = get_settings()
    return _get_root_from_sources(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        env_value=settings.default_backup_root,
        default_path_factory=_default_backup_storage_path,
    )


def get_backup_storage_source(db: Session) -> str:
    settings = get_settings()
    return _get_source(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        env_value=settings.default_backup_root,
    )


def ensure_backup_storage_initialized(db: Session) -> Path:
    settings = get_settings()
    return _ensure_initialized(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        env_value=settings.default_backup_root,
        default_path_factory=_default_backup_storage_path,
    )


def set_backup_storage_root(db: Session, path_value: str) -> Path:
    return _set_root_override(db, key=BACKUP_STORAGE_ROOT_KEY, path_value=path_value)


def clear_backup_storage_override(db: Session) -> Path:
    return _clear_root_override(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        resolver=get_backup_storage_root,
    )


# --- Oeffentliche Basis-URL / Zieldomain (fuer Resource-Pack-Hosting etc.) ---


def _normalize_public_base_url(raw_value: str) -> str:
    """Trimmen, abschliessende Slashes entfernen, Schema pruefen."""
    value = (raw_value or "").strip().rstrip("/")
    if not value:
        return ""
    lowered = value.lower()
    if not (lowered.startswith("http://") or lowered.startswith("https://")):
        raise ValueError("Die URL muss mit http:// oder https:// beginnen.")
    return value


def get_public_base_url(db: Session) -> str:
    """UI-Override > ENV (MCSM_PUBLIC_BASE_URL) > leer."""
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row and row.value.strip():
        return row.value.strip().rstrip("/")
    env_raw = (get_settings().public_base_url or "").strip()
    return env_raw.rstrip("/")


def get_public_base_url_source(db: Session) -> str:
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row and row.value.strip():
        return "ui"
    if (get_settings().public_base_url or "").strip():
        return "env"
    return "default"


def set_public_base_url(db: Session, url_value: str) -> str:
    normalized = _normalize_public_base_url(url_value)
    if not normalized:
        raise ValueError("Bitte eine gueltige URL angeben oder Zuruecksetzen nutzen.")
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row is None:
        row = AppSetting(key=PUBLIC_BASE_URL_KEY, value=normalized)
    else:
        row.value = normalized
    db.add(row)
    db.commit()
    return normalized


def clear_public_base_url_override(db: Session) -> str:
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row is not None:
        db.delete(row)
        db.commit()
    return get_public_base_url(db)


def get_public_base_url_runtime() -> str:
    """Laufzeit-Getter mit eigener Session (fuer Services ohne Request-Session).

    SessionLocal wird bewusst erst zur Laufzeit importiert, damit nach einem
    Reload von ``app.db.session`` (z.B. in Tests) die frische Factory genutzt wird.
    """
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_public_base_url(db)
