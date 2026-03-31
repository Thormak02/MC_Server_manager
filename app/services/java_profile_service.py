from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.java_profile import JavaProfile
from app.schemas.java_profile import JavaProfileCreate


def list_java_profiles(db: Session) -> list[JavaProfile]:
    return list(db.scalars(select(JavaProfile).order_by(JavaProfile.name.asc())).all())


def get_java_profile(db: Session, profile_id: int) -> JavaProfile | None:
    return db.get(JavaProfile, profile_id)


def _set_default_if_needed(db: Session, profile: JavaProfile, is_default: bool) -> None:
    if not is_default:
        return
    db.query(JavaProfile).update({JavaProfile.is_default: False})
    profile.is_default = True


def create_java_profile(db: Session, data: JavaProfileCreate) -> JavaProfile:
    existing = db.scalar(select(JavaProfile).where(JavaProfile.name == data.name.strip()))
    if existing:
        raise ValueError("Java-Profilname bereits vorhanden.")

    profile = JavaProfile(
        name=data.name.strip(),
        java_path=data.java_path.strip(),
        version_label=(data.version_label or "").strip() or None,
        description=(data.description or "").strip() or None,
        is_default=False,
    )
    _set_default_if_needed(db, profile, data.is_default)
    db.add(profile)
    db.commit()
    db.refresh(profile)
    return profile


def set_default_java_profile(db: Session, profile_id: int) -> JavaProfile:
    profile = get_java_profile(db, profile_id)
    if profile is None:
        raise ValueError("Java-Profil nicht gefunden.")
    _set_default_if_needed(db, profile, True)
    db.add(profile)
    db.commit()
    db.refresh(profile)
    return profile


def delete_java_profile(db: Session, profile_id: int) -> None:
    profile = get_java_profile(db, profile_id)
    if profile is None:
        raise ValueError("Java-Profil nicht gefunden.")
    db.delete(profile)
    db.commit()
