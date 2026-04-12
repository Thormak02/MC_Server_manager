from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base

if TYPE_CHECKING:
    from app.models.java_profile import JavaProfile


class ServerTemplate(Base):
    __tablename__ = "server_templates"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(128), unique=True)
    server_type: Mapped[str] = mapped_column(String(32))
    mc_version: Mapped[str] = mapped_column(String(32))
    loader_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    java_profile_id: Mapped[int | None] = mapped_column(
        ForeignKey("java_profiles.id", ondelete="SET NULL"),
        nullable=True,
    )
    memory_min_mb: Mapped[int | None] = mapped_column(Integer, nullable=True)
    memory_max_mb: Mapped[int | None] = mapped_column(Integer, nullable=True)
    port_min: Mapped[int | None] = mapped_column(Integer, nullable=True)
    port_max: Mapped[int | None] = mapped_column(Integer, nullable=True)
    start_parameters: Mapped[str | None] = mapped_column(String(512), nullable=True)
    default_properties_json: Mapped[str | None] = mapped_column(String(4096), nullable=True)
    is_default: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )

    java_profile: Mapped["JavaProfile | None"] = relationship("JavaProfile")
