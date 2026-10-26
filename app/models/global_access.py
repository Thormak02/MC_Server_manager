from datetime import datetime

from sqlalchemy import DateTime, Integer, String, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class GlobalAccessEntry(Base):
    """Ein managerweiter Zugriffseintrag (Whitelist/OP/Bann), gueltig fuer ALLE Server.

    Wird bei jedem Serverstart in die Dateien des Servers geschrieben. Die UUID wird
    beim Anlegen EINMAL aufgeloest und hier festgehalten - sonst muesste jeder
    Serverstart erneut bei Mojang anfragen.
    """

    __tablename__ = "global_access_entries"
    __table_args__ = (
        UniqueConstraint("list_key", "identity_key", name="uq_global_access_list_identity"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    # whitelist | ops | banned_players | banned_ips
    list_key: Mapped[str] = mapped_column(String(32), index=True)
    # Anzeigeform (Spielername in Originalschreibweise bzw. IP-Adresse)
    identity: Mapped[str] = mapped_column(String(64))
    # Kleingeschrieben - traegt die Eindeutigkeit, damit "Notch" und "notch"
    # nicht zweimal in derselben Liste landen.
    identity_key: Mapped[str] = mapped_column(String(64), index=True)
    player_uuid: Mapped[str | None] = mapped_column(String(36), nullable=True)
    op_level: Mapped[int | None] = mapped_column(Integer, nullable=True)
    reason: Mapped[str | None] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
