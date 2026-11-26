from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Backup(Base):
    __tablename__ = "backups"

    id: Mapped[int] = mapped_column(primary_key=True)
    server_id: Mapped[int] = mapped_column(ForeignKey("servers.id", ondelete="CASCADE"), index=True)
    backup_name: Mapped[str] = mapped_column(String(255), index=True)
    backup_type: Mapped[str] = mapped_column(String(32), default="manual")
    storage_path: Mapped[str] = mapped_column(String(1024))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        index=True,
    )
    created_by_user_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    size_bytes: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="running", index=True)

    # Was mit der PLUGIN-DATENBANK geschah [26.11.2026]. Sie liegt nicht im
    # Serverordner - sie laeuft als MariaDB-Dienst -, und bis heute lag sie
    # darum in keiner Sicherung: Parzellen, Guthaben, Laeden, Freistaedte,
    # Strafen, Meldungen, Teamrollen, Mitgliedschaften, Blockbuch.
    #
    # EIN SATZ UND KEIN JA/NEIN: Die Dateisicherung kann gelingen, waehrend
    # der Abzug scheitert. Beides als "success" zu melden waere die stille
    # Luege; beides als "error" liesse eine brauchbare Dateisicherung
    # aussehen wie keine. Wer "nein" liest, fragt "warum" - der Satz
    # beantwortet es.
    #
    # NULL heisst "die Frage stellte sich nicht": Sicherungen von vor heute,
    # und Weltsicherungen, die ausdruecklich nur Gelaende meinen.
    datenbank_hinweis: Mapped[str | None] = mapped_column(Text, nullable=True)
