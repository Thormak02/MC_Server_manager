import base64
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.session import SessionLocal
from app.models.platform_setting import PlatformSetting


_SUPPORTED_PROVIDERS: dict[str, set[str]] = {
    "modrinth": {"enabled", "user_agent"},
    "curseforge": {"enabled", "api_key"},
}
_SENSITIVE_KEYS = {"api_key"}


def _normalize_provider(provider_name: str) -> str:
    provider = (provider_name or "").strip().lower()
    if provider not in _SUPPORTED_PROVIDERS:
        raise ValueError(f"Unbekannter Provider: {provider_name}")
    return provider


def _normalize_bool(raw: Any) -> str:
    if isinstance(raw, bool):
        return "true" if raw else "false"
    text = str(raw or "").strip().lower()
    return "true" if text in {"1", "true", "on", "yes"} else "false"


def _encode_value(raw: str) -> str:
    return base64.urlsafe_b64encode(raw.encode("utf-8")).decode("ascii")


def _decode_value(encoded: str) -> str:
    try:
        return base64.urlsafe_b64decode(encoded.encode("ascii")).decode("utf-8")
    except Exception:
        return encoded


def _default_provider_values(provider_name: str) -> dict[str, str]:
    provider = _normalize_provider(provider_name)
    settings = get_settings()
    if provider == "modrinth":
        return {
            "enabled": "true" if settings.modrinth_enabled else "false",
            "user_agent": settings.modrinth_user_agent,
        }
    return {
        "enabled": "true" if settings.curseforge_enabled else "false",
        "api_key": (settings.curseforge_api_key or "").strip(),
    }


def _find_setting_row(db: Session, provider_name: str, setting_key: str) -> PlatformSetting | None:
    return db.scalar(
        select(PlatformSetting)
        .where(PlatformSetting.provider_name == provider_name)
        .where(PlatformSetting.setting_key == setting_key)
    )


def get_provider_settings(
    db: Session,
    *,
    provider_name: str,
    include_secrets: bool = False,
) -> dict[str, str]:
    provider = _normalize_provider(provider_name)
    defaults = _default_provider_values(provider)
    values = dict(defaults)
    rows = list(
        db.scalars(select(PlatformSetting).where(PlatformSetting.provider_name == provider))
    )
    for row in rows:
        if row.setting_key in _SUPPORTED_PROVIDERS[provider]:
            values[row.setting_key] = _decode_value(row.setting_value_encrypted)

    if not include_secrets:
        for key in list(values.keys()):
            if key in _SENSITIVE_KEYS:
                raw = values.get(key, "")
                values[key] = "********" if raw else ""
    return values


def list_platform_settings(db: Session, *, include_secrets: bool = False) -> dict[str, dict[str, str]]:
    payload: dict[str, dict[str, str]] = {}
    for provider in sorted(_SUPPORTED_PROVIDERS.keys()):
        payload[provider] = get_provider_settings(
            db,
            provider_name=provider,
            include_secrets=include_secrets,
        )
    return payload


def update_provider_settings(
    db: Session,
    *,
    provider_name: str,
    updates: dict[str, Any],
) -> dict[str, str]:
    provider = _normalize_provider(provider_name)
    supported_keys = _SUPPORTED_PROVIDERS[provider]
    defaults = _default_provider_values(provider)

    changed = False
    for key, value in updates.items():
        if key not in supported_keys:
            raise ValueError(f"Unbekannter Setting-Key fuer {provider}: {key}")

        normalized = str(value or "").strip()
        if key == "enabled":
            normalized = _normalize_bool(value)

        row = _find_setting_row(db, provider, key)
        if not normalized:
            if row is not None:
                db.delete(row)
                changed = True
            continue

        if normalized == defaults.get(key, ""):
            if row is not None:
                db.delete(row)
                changed = True
            continue

        encoded = _encode_value(normalized)
        if row is None:
            row = PlatformSetting(
                provider_name=provider,
                setting_key=key,
                setting_value_encrypted=encoded,
            )
            db.add(row)
            changed = True
            continue

        if row.setting_value_encrypted != encoded:
            row.setting_value_encrypted = encoded
            db.add(row)
            changed = True

    if changed:
        db.commit()

    return get_provider_settings(db, provider_name=provider, include_secrets=False)


def is_provider_enabled_runtime(provider_name: str) -> bool:
    provider = _normalize_provider(provider_name)
    with SessionLocal() as db:
        settings = get_provider_settings(db, provider_name=provider, include_secrets=True)
    return _normalize_bool(settings.get("enabled")) == "true"


def get_modrinth_user_agent_runtime() -> str:
    with SessionLocal() as db:
        settings = get_provider_settings(db, provider_name="modrinth", include_secrets=True)
    user_agent = (settings.get("user_agent") or "").strip()
    if user_agent:
        return user_agent
    return get_settings().modrinth_user_agent


def get_curseforge_api_key_runtime() -> str:
    with SessionLocal() as db:
        settings = get_provider_settings(db, provider_name="curseforge", include_secrets=True)
    return (settings.get("api_key") or "").strip()

