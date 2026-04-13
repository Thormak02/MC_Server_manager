from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.app_setting import AppSetting


SERVER_STORAGE_ROOT_KEY = "server_storage_root"


def _normalize_path(raw_value: str) -> Path:
    return Path(raw_value).expanduser().resolve()


def _default_desktop_storage_path() -> Path:
    return (Path.home() / "Desktop" / "mc_servers").resolve()


def _get_setting_row(db: Session, key: str) -> AppSetting | None:
    return db.scalar(select(AppSetting).where(AppSetting.key == key))


def get_server_storage_root(db: Session) -> Path:
    row = _get_setting_row(db, SERVER_STORAGE_ROOT_KEY)
    if row and row.value.strip():
        return _normalize_path(row.value.strip())

    settings = get_settings()
    env_value = (settings.default_server_root or "").strip()
    if env_value:
        return _normalize_path(env_value)
    return _default_desktop_storage_path()


def get_server_storage_source(db: Session) -> str:
    row = _get_setting_row(db, SERVER_STORAGE_ROOT_KEY)
    if row and row.value.strip():
        return "ui"
    settings = get_settings()
    if (settings.default_server_root or "").strip():
        return "env"
    return "default"


def ensure_server_storage_initialized(db: Session) -> Path:
    row = _get_setting_row(db, SERVER_STORAGE_ROOT_KEY)
    if row and row.value.strip():
        normalized = _normalize_path(row.value.strip())
        normalized.mkdir(parents=True, exist_ok=True)
        if row.value != str(normalized):
            row.value = str(normalized)
            db.add(row)
            db.commit()
        return normalized

    settings = get_settings()
    env_value = (settings.default_server_root or "").strip()
    if env_value:
        normalized = _normalize_path(env_value)
        normalized.mkdir(parents=True, exist_ok=True)
        return normalized

    default_path = _default_desktop_storage_path()
    default_path.mkdir(parents=True, exist_ok=True)
    return default_path


def set_server_storage_root(db: Session, path_value: str) -> Path:
    normalized = _normalize_path(path_value.strip())
    normalized.mkdir(parents=True, exist_ok=True)
    row = _get_setting_row(db, SERVER_STORAGE_ROOT_KEY)
    if row is None:
        row = AppSetting(key=SERVER_STORAGE_ROOT_KEY, value=str(normalized))
    else:
        row.value = str(normalized)
    db.add(row)
    db.commit()
    return normalized


def clear_server_storage_override(db: Session) -> Path:
    row = _get_setting_row(db, SERVER_STORAGE_ROOT_KEY)
    if row is not None:
        db.delete(row)
        db.commit()
    path = get_server_storage_root(db)
    path.mkdir(parents=True, exist_ok=True)
    return path
