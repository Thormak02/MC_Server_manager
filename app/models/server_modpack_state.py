from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class ServerModpackState(Base):
    __tablename__ = "server_modpack_states"

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[int] = mapped_column(
        ForeignKey("servers.id", ondelete="CASCADE"),
        unique=True,
        index=True,
    )
    source: Mapped[str] = mapped_column(String(32))
    pack_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    source_ref: Mapped[str | None] = mapped_column(String(512), nullable=True)
    upstream_project_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    current_version_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    pending_version_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    pack_version: Mapped[str | None] = mapped_column(String(128), nullable=True)
    last_known_version_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    last_known_version_label: Mapped[str | None] = mapped_column(String(255), nullable=True)
    last_check_error: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
    )
