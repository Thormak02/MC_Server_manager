from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class InstalledContent(Base):
    __tablename__ = "installed_content"

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[int] = mapped_column(
        ForeignKey("servers.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    provider_name: Mapped[str] = mapped_column(String(32))
    content_type: Mapped[str] = mapped_column(String(32))
    external_project_id: Mapped[str] = mapped_column(String(128))
    external_version_id: Mapped[str] = mapped_column(String(128))
    name: Mapped[str] = mapped_column(String(256))
    version_label: Mapped[str | None] = mapped_column(String(128), nullable=True)
    file_name: Mapped[str] = mapped_column(String(256))
    installed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )
    installed_by_user_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
