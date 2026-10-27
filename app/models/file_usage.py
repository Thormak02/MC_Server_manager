from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class FileUsage(Base):
    """Wie oft und wann eine Datei im Dateimanager geoeffnet wurde.

    Grundlage fuer die Sortierung nach Nutzungshaeufigkeit und nach "zuletzt genutzt".
    Bewusst getrennt von der Aenderungszeit im Dateisystem: die sagt, wann der SERVER
    die Datei geschrieben hat (latest.log aendert sich dauernd), nicht, welche Datei der
    Nutzer tatsaechlich bearbeitet.
    """

    __tablename__ = "file_usages"
    __table_args__ = (UniqueConstraint("server_id", "relative_path",
                                       name="uq_file_usage_server_path"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[int] = mapped_column(
        ForeignKey("servers.id", ondelete="CASCADE"),
        index=True,
    )
    relative_path: Mapped[str] = mapped_column(String(512))
    open_count: Mapped[int] = mapped_column(Integer, default=0)
    last_opened_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )
