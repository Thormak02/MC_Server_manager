from datetime import datetime, timezone
from time import time

from fastapi import Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.security import verify_password
from app.models.user import User
from app.services import security_service

SESSION_USER_ID_KEY = "user_id"
SESSION_ROLE_KEY = "role"
SESSION_LAST_SEEN_KEY = "last_seen_unix"


def authenticate_credentials(db: Session, username: str, password: str) -> User | None:
    user = db.scalar(select(User).where(User.username == username))
    if not user:
        return None
    if not verify_password(password, user.password_hash):
        return None
    return user


def set_logged_in_session(request: Request, user: User) -> None:
    request.session.clear()
    request.session[SESSION_USER_ID_KEY] = user.id
    request.session[SESSION_ROLE_KEY] = user.role
    request.session[SESSION_LAST_SEEN_KEY] = int(time())


def clear_session(request: Request) -> None:
    request.session.clear()


def get_current_user_from_session(request: Request, db: Session) -> User | None:
    raw_user_id = request.session.get(SESSION_USER_ID_KEY)
    if raw_user_id is None:
        return None

    try:
        user_id = int(raw_user_id)
    except (TypeError, ValueError):
        request.session.clear()
        return None

    settings = get_settings()
    idle_timeout = max(0, int(settings.session_idle_timeout_seconds))
    if idle_timeout > 0:
        raw_last_seen = request.session.get(SESSION_LAST_SEEN_KEY)
        try:
            last_seen = int(raw_last_seen)
        except (TypeError, ValueError):
            last_seen = 0
        now_unix = int(time())
        if last_seen > 0 and now_unix - last_seen > idle_timeout:
            stale_user = db.get(User, user_id)
            ip_address = request.client.host if request.client else None
            security_service.record_session_timeout(
                db,
                user_id=user_id,
                username=stale_user.username if stale_user else None,
                ip_address=ip_address,
                idle_seconds=now_unix - last_seen,
            )
            request.session.clear()
            return None

    user = db.get(User, user_id)
    if user is None or not user.is_active:
        request.session.clear()
        return None

    request.session[SESSION_LAST_SEEN_KEY] = int(time())
    return user


def touch_last_login(db: Session, user: User) -> None:
    user.last_login_at = datetime.now(timezone.utc)
    db.add(user)
    db.commit()
