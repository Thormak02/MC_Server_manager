from datetime import datetime

from sqlalchemy import DateTime, String, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class PlatformSetting(Base):
    __tablename__ = "platform_settings"
    __table_args__ = (
        UniqueConstraint("provider_name", "setting_key", name="uq_platform_settings_provider_key"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    provider_name: Mapped[str] = mapped_column(String(32), index=True)
    setting_key: Mapped[str] = mapped_column(String(64), index=True)
    setting_value_encrypted: Mapped[str] = mapped_column(String(2048))
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
    )

