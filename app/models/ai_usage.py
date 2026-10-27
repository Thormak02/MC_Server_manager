from datetime import datetime

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class AiUsage(Base):
    """Ein KI-Aufruf mit seinem Verbrauch.

    Jeder Aufruf wird protokolliert, auch ein abgelehnter oder gescheiterter - nur so laesst
    sich hinterher nachvollziehen, wofuer das Monatsbudget draufging, und nur so kann der
    Deckel ueberhaupt greifen. Die Tabelle ist gleichzeitig die Grundlage fuer das Rate
    Limit (Aufrufe je Nutzer und Zeitraum).
    """

    __tablename__ = "ai_usages"

    id: Mapped[int] = mapped_column(primary_key=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        index=True,
    )
    user_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    server_id: Mapped[int | None] = mapped_column(
        ForeignKey("servers.id", ondelete="SET NULL"),
        nullable=True,
    )
    funktion: Mapped[str] = mapped_column(String(64), index=True)
    """crash_analyse, config_assistent, changelog, agent - wofuer der Aufruf war."""

    modell: Mapped[str] = mapped_column(String(64))
    tokens_ein: Mapped[int] = mapped_column(Integer, default=0)
    tokens_aus: Mapped[int] = mapped_column(Integer, default=0)
    kosten_cent: Mapped[float] = mapped_column(Float, default=0.0)
    status: Mapped[str] = mapped_column(String(32), default="ok", index=True)
    """ok, abgelehnt (Themen-Sperre/Rate Limit/Budget), fehler."""

    hinweis: Mapped[str | None] = mapped_column(Text, nullable=True)
    """Bei abgelehnt/fehler: der Grund. Bei ok: leer."""
