from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class JobHistory(Base):
    __tablename__ = "job_history"

    id: Mapped[int] = mapped_column(primary_key=True)
    scheduled_job_id: Mapped[int] = mapped_column(
        ForeignKey("scheduled_jobs.id", ondelete="CASCADE"),
        index=True,
    )
    started_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        index=True,
    )
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="running", index=True)
    message: Mapped[str | None] = mapped_column(Text, nullable=True)
