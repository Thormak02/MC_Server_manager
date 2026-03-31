from datetime import datetime, timezone

from fastapi import Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.security import verify_password
from app.models.user import User

SESSION_USER_ID_KEY = "user_id"
SESSION_ROLE_KEY = "role"


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

    user = db.get(User, user_id)
    if user is None or not user.is_active:
        request.session.clear()
        return None

    return user


def touch_last_login(db: Session, user: User) -> None:
    user.last_login_at = datetime.now(timezone.utc)
    db.add(user)
    db.commit()
