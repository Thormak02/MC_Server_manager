from datetime import datetime

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class AiChat(Base):
    """Ein Gespraech mit dem KI-Assistenten.

    Vorher lebte der Verlauf nur in der geoeffneten Seite: ein Neuladen, ein
    Seitenwechsel oder ein Browser-Neustart - und das Gespraech war weg, mitsamt dem
    Zusammenhang, auf den sich Rueckfragen beziehen. Deshalb liegt er jetzt hier.

    Ein Gespraech gehoert EINEM Nutzer und ist fuer andere nicht lesbar, auch nicht fuer
    Admins: darin stehen Configauszuege, Logzeilen und Spielernamen. Was der Assistent
    tatsaechlich AUSGEFUEHRT hat, steht dagegen im Audit-Log und ist dort nachvollziehbar -
    das ist der Ort fuer Nachvollziehbarkeit, nicht die Unterhaltung.
    """

    __tablename__ = "ai_chats"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True)
    titel: Mapped[str] = mapped_column(String(200), default="Neues Gespraech")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), index=True)
    angeheftet: Mapped[bool] = mapped_column(Boolean, default=False)

    nachrichten: Mapped[list["AiChatMessage"]] = relationship(
        back_populates="chat", cascade="all, delete-orphan",
        order_by="AiChatMessage.id")


class AiChatMessage(Base):
    """Ein Zug in einem Gespraech - genau so, wie er in der Seite stand.

    Gespeichert wird der SICHTBARE Verlauf, nicht die internen Werkzeugbloecke. Die
    gehoeren zu einem einzelnen Auftrag, nicht zum Gespraech, und sie wuerden die Tabelle
    mit Dateiinhalten und Logauszuegen fuellen.
    """

    __tablename__ = "ai_chat_messages"

    id: Mapped[int] = mapped_column(primary_key=True)
    chat_id: Mapped[int] = mapped_column(
        ForeignKey("ai_chats.id", ondelete="CASCADE"), index=True)
    rolle: Mapped[str] = mapped_column(String(16))
    """nutzer oder assistent."""

    inhalt: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now())

    modell: Mapped[str | None] = mapped_column(String(64), nullable=True)
    kosten_cent: Mapped[float] = mapped_column(Float, default=0.0)
    schritte: Mapped[str | None] = mapped_column(Text, nullable=True)
    """Was nachgesehen wurde, als JSON - damit ein geladenes Gespraech auch zeigt, worauf
    die Antwort beruhte."""

    tokens_ein: Mapped[int] = mapped_column(Integer, default=0)
    tokens_aus: Mapped[int] = mapped_column(Integer, default=0)

    chat: Mapped["AiChat"] = relationship(back_populates="nachrichten")
