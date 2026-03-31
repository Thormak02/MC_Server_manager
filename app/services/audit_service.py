from sqlalchemy.orm import Session

from app.models.audit_log import AuditLog


def log_action(
    db: Session,
    *,
    action: str,
    user_id: int | None = None,
    server_id: int | None = None,
    details: str | None = None,
) -> AuditLog:
    entry = AuditLog(
        action=action,
        user_id=user_id,
        server_id=server_id,
        details=details,
    )
    db.add(entry)
    db.commit()
    db.refresh(entry)
    return entry
