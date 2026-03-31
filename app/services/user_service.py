from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.permissions import is_valid_role
from app.core.security import hash_password
from app.models.user import User


def list_users(db: Session) -> list[User]:
    return list(db.scalars(select(User).order_by(User.username.asc())).all())


def get_user_by_username(db: Session, username: str) -> User | None:
    return db.scalar(select(User).where(User.username == username))


def get_user_by_id(db: Session, user_id: int) -> User | None:
    return db.get(User, user_id)


def create_user(
    db: Session,
    *,
    username: str,
    password: str,
    role: str,
    is_active: bool = True,
) -> User:
    normalized_username = username.strip()
    if not normalized_username:
        raise ValueError("Username darf nicht leer sein.")
    if len(password) < 8:
        raise ValueError("Passwort muss mindestens 8 Zeichen haben.")
    if not is_valid_role(role):
        raise ValueError("Unbekannte Rolle.")
    if get_user_by_username(db, normalized_username):
        raise ValueError("Benutzername ist bereits vergeben.")

    user = User(
        username=normalized_username,
        password_hash=hash_password(password),
        role=role,
        is_active=is_active,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def deactivate_user(db: Session, user: User) -> User:
    user.is_active = False
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def reset_password(db: Session, user: User, new_password: str) -> User:
    if len(new_password) < 8:
        raise ValueError("Passwort muss mindestens 8 Zeichen haben.")

    user.password_hash = hash_password(new_password)
    db.add(user)
    db.commit()
    db.refresh(user)
    return user
