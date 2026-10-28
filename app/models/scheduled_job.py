from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class ScheduledJob(Base):
    __tablename__ = "scheduled_jobs"

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[int] = mapped_column(ForeignKey("servers.id", ondelete="CASCADE"))
    job_type: Mapped[str] = mapped_column(String(32))
    schedule_expression: Mapped[str] = mapped_column(String(128))
    command_payload: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    last_run_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    next_run_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )
    created_by_user_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    """Wer den Job angelegt hat.

    Noetig, weil ein Job SPAETER laeuft: ohne diese Angabe wurde er mit
    ``initiated_by_user_id=None`` ausgefuehrt, und das heisst in
    process_service._darf_befehl "der Manager selbst" - die Admin-Sperre fuer op/deop/
    pardon/whitelist off griff dort also nie. Ein Nutzer mit can_console konnte sich so
    per Zeitplan Operator-Rechte verschaffen. Jetzt laeuft der Job mit den Rechten
    dessen, der ihn angelegt hat."""
