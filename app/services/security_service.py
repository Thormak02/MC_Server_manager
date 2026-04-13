from datetime import datetime, timedelta, timezone

from sqlalchemy import desc, func, or_, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.security_event import SecurityEvent


def _effective_ip(ip_address: str | None) -> str | None:
    settings = get_settings()
    if not settings.security_log_ip:
        return None
    return (ip_address or "").strip() or None


def log_security_event(
    db: Session,
    *,
    event_type: str,
    user_id: int | None = None,
    username: str | None = None,
    ip_address: str | None = None,
    details: str | None = None,
) -> SecurityEvent:
    row = SecurityEvent(
        event_type=event_type.strip().lower(),
        user_id=user_id,
        username=(username or "").strip() or None,
        ip_address=_effective_ip(ip_address),
        details=details,
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def _is_locked(
    db: Session,
    *,
    username: str,
    ip_address: str | None,
    lockout_seconds: int,
) -> bool:
    if lockout_seconds <= 0:
        return False

    conditions = [SecurityEvent.username == username]
    effective_ip = _effective_ip(ip_address)
    if effective_ip:
        conditions.append(SecurityEvent.ip_address == effective_ip)
    stmt = (
        select(SecurityEvent)
        .where(SecurityEvent.event_type == "login_locked")
        .where(or_(*conditions))
        .order_by(desc(SecurityEvent.created_at))
        .limit(1)
    )
    latest = db.scalar(stmt)
    if latest is None or latest.created_at is None:
        return False
    return latest.created_at + timedelta(seconds=lockout_seconds) > datetime.now(timezone.utc)


def _count_recent_failures(
    db: Session,
    *,
    username: str,
    ip_address: str | None,
    window_seconds: int,
) -> int:
    if window_seconds <= 0:
        return 0
    since = datetime.now(timezone.utc) - timedelta(seconds=window_seconds)
    conditions = [SecurityEvent.username == username]
    effective_ip = _effective_ip(ip_address)
    if effective_ip:
        conditions.append(SecurityEvent.ip_address == effective_ip)
    stmt = (
        select(func.count(SecurityEvent.id))
        .where(SecurityEvent.event_type == "login_failed")
        .where(SecurityEvent.created_at >= since)
        .where(or_(*conditions))
    )
    return int(db.scalar(stmt) or 0)


def is_login_allowed(
    db: Session,
    *,
    username: str,
    ip_address: str | None,
) -> tuple[bool, str | None]:
    settings = get_settings()
    normalized_username = (username or "").strip()
    if not normalized_username:
        return True, None

    if _is_locked(
        db,
        username=normalized_username,
        ip_address=ip_address,
        lockout_seconds=max(0, settings.login_lockout_seconds),
    ):
        return False, "Zu viele fehlgeschlagene Login-Versuche. Bitte spaeter erneut versuchen."

    attempts = max(0, settings.login_rate_limit_max_attempts)
    if attempts == 0:
        return True, None

    failures = _count_recent_failures(
        db,
        username=normalized_username,
        ip_address=ip_address,
        window_seconds=max(0, settings.login_rate_limit_window_seconds),
    )
    if failures < attempts:
        return True, None

    log_security_event(
        db,
        event_type="login_locked",
        username=normalized_username,
        ip_address=ip_address,
        details=f"failures={failures} window={settings.login_rate_limit_window_seconds}s",
    )
    return False, "Zu viele fehlgeschlagene Login-Versuche. Bitte spaeter erneut versuchen."


def record_login_failed(
    db: Session,
    *,
    username: str,
    ip_address: str | None,
    details: str | None = None,
) -> SecurityEvent:
    return log_security_event(
        db,
        event_type="login_failed",
        username=username,
        ip_address=ip_address,
        details=details,
    )


def record_login_success(
    db: Session,
    *,
    user_id: int | None,
    username: str | None,
    ip_address: str | None,
) -> SecurityEvent:
    return log_security_event(
        db,
        event_type="login_success",
        user_id=user_id,
        username=username,
        ip_address=ip_address,
    )


def record_session_timeout(
    db: Session,
    *,
    user_id: int | None,
    username: str | None,
    ip_address: str | None,
    idle_seconds: int,
) -> SecurityEvent:
    return log_security_event(
        db,
        event_type="session_timeout",
        user_id=user_id,
        username=username,
        ip_address=ip_address,
        details=f"idle_seconds={idle_seconds}",
    )


def list_security_events(
    db: Session,
    *,
    limit: int = 200,
    user_id: int | None = None,
    username: str | None = None,
    event_type: str | None = None,
    ip_address: str | None = None,
) -> list[SecurityEvent]:
    safe_limit = max(1, min(limit, 2000))
    stmt = select(SecurityEvent).order_by(desc(SecurityEvent.created_at))
    if user_id is not None:
        stmt = stmt.where(SecurityEvent.user_id == user_id)
    if username and username.strip():
        stmt = stmt.where(SecurityEvent.username.ilike(f"%{username.strip()}%"))
    if event_type and event_type.strip():
        stmt = stmt.where(SecurityEvent.event_type.ilike(f"%{event_type.strip().lower()}%"))
    if ip_address and ip_address.strip():
        stmt = stmt.where(SecurityEvent.ip_address == ip_address.strip())
    return list(db.scalars(stmt.limit(safe_limit)).all())

