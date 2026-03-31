from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.constants import DEFAULT_SERVER_STATUS
from app.db.base import Base

if TYPE_CHECKING:
    from app.models.server_permission import ServerPermission


class Server(Base):
    __tablename__ = "servers"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(128), unique=True)
    slug: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    server_type: Mapped[str] = mapped_column(String(32))
    mc_version: Mapped[str] = mapped_column(String(32))
    loader_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    base_path: Mapped[str] = mapped_column(String(512))
    start_mode: Mapped[str] = mapped_column(String(32), default="command")
    start_command: Mapped[str | None] = mapped_column(String(1024), nullable=True)
    start_bat_path: Mapped[str | None] = mapped_column(String(512), nullable=True)
    java_profile_id: Mapped[int | None] = mapped_column(
        ForeignKey("java_profiles.id", ondelete="SET NULL"),
        nullable=True,
    )
    memory_min_mb: Mapped[int | None] = mapped_column(Integer, nullable=True)
    memory_max_mb: Mapped[int | None] = mapped_column(Integer, nullable=True)
    port: Mapped[int | None] = mapped_column(Integer, nullable=True)
    status: Mapped[str] = mapped_column(String(32), default=DEFAULT_SERVER_STATUS)
    auto_restart: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )

    permissions: Mapped[list["ServerPermission"]] = relationship(
        back_populates="server",
        cascade="all, delete-orphan",
    )
