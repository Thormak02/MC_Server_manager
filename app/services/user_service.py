from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.core.permissions import is_valid_role
from app.core.security import hash_password
from app.models.user import User
from app.services.password_policy_service import validate_password


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
    validate_password(password)
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


def count_active_super_admins(db: Session) -> int:
    return int(
        db.scalar(
            select(func.count(User.id)).where(
                User.role == UserRole.SUPER_ADMIN.value,
                User.is_active.is_(True),
            )
        )
        or 0
    )


def update_role(db: Session, user: User, role: str) -> User:
    if not is_valid_role(role):
        raise ValueError("Unbekannte Rolle.")
    user.role = role
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def delete_user(db: Session, user: User) -> None:
    db.delete(user)
    db.commit()


def reset_password(db: Session, user: User, new_password: str) -> User:
    validate_password(new_password)
    user.password_hash = hash_password(new_password)
    db.add(user)
    db.commit()
    db.refresh(user)
    return user
