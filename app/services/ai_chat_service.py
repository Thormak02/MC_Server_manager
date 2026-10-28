"""Gespraeche des KI-Assistenten aufbewahren und wieder aufrufbar machen.

Der Verlauf lag vorher nur in der geoeffneten Seite. Ein Neuladen genuegte, und das
Gespraech war weg - mitsamt dem Zusammenhang, auf den sich jede Rueckfrage bezieht
("und jetzt der andere Server?").

Zwei Dinge sind dabei mehr als Bequemlichkeit:

* **Der Zusammenhang kommt jetzt vom Server.** Vorher schickte der Browser den Verlauf
  mit; er konnte ihn also frei erfinden. Ausfuehren liess sich damit nichts (jede Aktion
  wird serverseitig neu geprueft), aber das Modell liess sich mit erfundenen Werkzeug-
  ergebnissen fuettern. Jetzt wird nur eine ``chat_id`` geschickt, und der Verlauf kommt
  aus der Datenbank.
* **Ein Gespraech gehoert einem Nutzer.** Darin stehen Configauszuege, Logzeilen und
  Spielernamen. Andere lesen es nicht, auch kein Admin. Was der Assistent tatsaechlich
  ausgefuehrt hat, steht im Audit-Log - das ist der Ort fuer Nachvollziehbarkeit.

Grenzen, damit die Tabelle nicht unbegrenzt waechst: je Gespraech werden hoechstens
``MAX_NACHRICHTEN`` Zuege aufbewahrt (die aeltesten fallen weg), und je Nutzer
``MAX_CHATS`` Gespraeche (das aelteste, nicht angeheftete wird geloescht).
"""

from __future__ import annotations

import json
from datetime import datetime, timezone

from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from app.models.ai_chat import AiChat, AiChatMessage
from app.models.user import User

# Wie viele Zuege ein Gespraech behaelt. Der Verlauf geht bei jedem Auftrag mit und kostet
# damit Token; mehr als das traegt zum Zusammenhang kaum noch bei.
MAX_NACHRICHTEN = 60

# Wie viele Gespraeche je Nutzer aufbewahrt werden.
MAX_CHATS = 100

# Wie viele Zuege als Zusammenhang an die KI gehen. Weniger als aufbewahrt wird: die
# Anzeige darf laenger sein als das, was bei jeder Anfrage mitbezahlt wird.
VERLAUF_FUER_KI = 20

MAX_INHALT = 20_000


class ChatFehler(Exception):
    """Das Gespraech gibt es nicht oder es gehoert jemand anderem."""


def _titel_aus(nachricht: str) -> str:
    """Einen Titel aus der ersten Frage machen - wie im Web-Chat.

    Erste Zeile, auf eine lesbare Laenge gekuerzt. Kein KI-Aufruf dafuer: ein Titel ist
    kein Grund, Guthaben auszugeben.
    """
    roh = " ".join((nachricht or "").split())
    if not roh:
        return "Neues Gespraech"
    if len(roh) <= 60:
        return roh
    # An einer Wortgrenze abschneiden, nicht mitten im Wort.
    gekuerzt = roh[:60].rsplit(" ", 1)[0]
    return (gekuerzt or roh[:60]) + " ..."


def _chat_holen(db: Session, user: User, chat_id: int) -> AiChat:
    chat = db.get(AiChat, int(chat_id))
    if chat is None or chat.user_id != getattr(user, "id", None):
        # Dieselbe Meldung fuer "gibt es nicht" und "gehoert jemand anderem" - sonst
        # verraet die Antwort, dass es dieses Gespraech gibt.
        raise ChatFehler("Dieses Gespraech gibt es nicht.")
    return chat


def erstelle(db: Session, *, user: User, erste_nachricht: str = "") -> AiChat:
    chat = AiChat(user_id=user.id, titel=_titel_aus(erste_nachricht))
    db.add(chat)
    db.commit()
    db.refresh(chat)
    # Das GERADE angelegte Gespraech ausnehmen. Ohne diese Angabe war es bei einem
    # Nutzer mit MAX_CHATS angehefteten Gespraechen der einzige nicht angeheftete
    # Kandidat - und wurde sofort wieder geloescht. Der Aufrufer arbeitete danach mit
    # einem Objekt, das es nicht mehr gibt: jede neue Frage endete im 500er, und die
    # Frage selbst war verloren.
    _alte_chats_aufraeumen(db, user, ausser_id=chat.id)
    return chat


def _alte_chats_aufraeumen(db: Session, user: User,
                           *, ausser_id: int | None = None) -> int:
    """Ueber MAX_CHATS hinausgehende Gespraeche loeschen.

    Angeheftete bleiben immer, und ``ausser_id`` bleibt auch - damit nicht das Gespraech
    geloescht wird, fuer das der Aufrufer gerade einen Platz braucht.

    Sind so viele Gespraeche angeheftet, dass die Grenze allein damit erreicht ist, wird
    NICHTS geloescht. Das ist Absicht: angeheftet heisst "behalten", und ein Aufraeumen,
    das dafuer das neue Gespraech wegnimmt, macht die Funktion unbenutzbar statt sie zu
    begrenzen.
    """
    try:
        kandidaten = [
            c for c in db.scalars(
                select(AiChat)
                .where(AiChat.user_id == user.id, AiChat.angeheftet.is_(False))
                .order_by(AiChat.updated_at.desc()))
            if ausser_id is None or c.id != ausser_id
        ]
        if not kandidaten:
            return 0
        gesamt = db.scalar(select(func.count(AiChat.id))
                           .where(AiChat.user_id == user.id)) or 0
        zuviel = min(int(gesamt) - MAX_CHATS, len(kandidaten))
        if zuviel <= 0:
            return 0
        for chat in kandidaten[len(kandidaten) - zuviel:]:
            db.delete(chat)
        db.commit()
        return zuviel
    except Exception:  # noqa: BLE001 - Aufraeumen darf nichts kaputt machen
        try:
            db.rollback()
        except Exception:  # noqa: BLE001
            pass
        return 0


def haenge_an(db: Session, *, chat: AiChat, rolle: str, inhalt: str,
              modell: str | None = None, kosten_cent: float = 0.0,
              schritte: list | None = None, tokens_ein: int = 0,
              tokens_aus: int = 0) -> AiChatMessage:
    """Einen Zug anhaengen und das Gespraech als zuletzt benutzt markieren."""
    nachricht = AiChatMessage(
        chat_id=chat.id,
        rolle="assistent" if rolle == "assistent" else "nutzer",
        inhalt=(inhalt or "")[:MAX_INHALT],
        modell=modell,
        kosten_cent=max(0.0, float(kosten_cent or 0.0)),
        # Auch die Schritte deckeln: die Grenze galt nur fuer ``inhalt``, und ein
        # Werkzeugergebnis kann laenger sein als die Antwort selbst.
        schritte=(json.dumps(schritte, ensure_ascii=False,
                             default=str)[:MAX_INHALT] if schritte else None),
        tokens_ein=max(0, int(tokens_ein or 0)),
        tokens_aus=max(0, int(tokens_aus or 0)))
    db.add(nachricht)
    chat.updated_at = datetime.now(timezone.utc)
    if chat.titel in ("", "Neues Gespraech") and nachricht.rolle == "nutzer":
        chat.titel = _titel_aus(inhalt)
    db.add(chat)
    db.commit()
    db.refresh(nachricht)
    _alte_nachrichten_aufraeumen(db, chat)
    return nachricht


def _alte_nachrichten_aufraeumen(db: Session, chat: AiChat) -> int:
    try:
        anzahl = db.scalar(select(func.count(AiChatMessage.id))
                           .where(AiChatMessage.chat_id == chat.id)) or 0
        zuviel = int(anzahl) - MAX_NACHRICHTEN
        if zuviel <= 0:
            return 0
        alte = list(db.scalars(
            select(AiChatMessage.id).where(AiChatMessage.chat_id == chat.id)
            .order_by(AiChatMessage.id.asc()).limit(zuviel)))
        if not alte:
            return 0
        db.execute(delete(AiChatMessage).where(AiChatMessage.id.in_(alte)))
        db.commit()
        return len(alte)
    except Exception:  # noqa: BLE001
        try:
            db.rollback()
        except Exception:  # noqa: BLE001
            pass
        return 0


def _schritte_lesen(roh: str | None) -> list:
    if not roh:
        return []
    try:
        wert = json.loads(roh)
    except Exception:  # noqa: BLE001 - abgeschnitten oder beschaedigt
        return []
    return wert if isinstance(wert, list) else []


def liste(db: Session, *, user: User, grenze: int = 50) -> list[dict]:
    """Die Gespraeche eines Nutzers, zuletzt benutzte zuerst, angeheftete oben."""
    zeilen = list(db.scalars(
        select(AiChat).where(AiChat.user_id == user.id)
        .order_by(AiChat.angeheftet.desc(), AiChat.updated_at.desc())
        .limit(max(1, grenze))))
    ergebnis: list[dict] = []
    for chat in zeilen:
        anzahl = db.scalar(select(func.count(AiChatMessage.id))
                           .where(AiChatMessage.chat_id == chat.id)) or 0
        ergebnis.append({
            "id": chat.id, "titel": chat.titel, "angeheftet": bool(chat.angeheftet),
            "nachrichten": int(anzahl),
            "zuletzt": chat.updated_at.isoformat() if chat.updated_at else None,
        })
    return ergebnis


def lade(db: Session, *, user: User, chat_id: int) -> dict:
    """Ein Gespraech mit allen Zuegen - fuer die Anzeige beim Wiederaufrufen."""
    chat = _chat_holen(db, user, chat_id)
    nachrichten = list(db.scalars(
        select(AiChatMessage).where(AiChatMessage.chat_id == chat.id)
        .order_by(AiChatMessage.id.asc())))
    return {
        "id": chat.id, "titel": chat.titel, "angeheftet": bool(chat.angeheftet),
        "nachrichten": [{
            "rolle": n.rolle, "inhalt": n.inhalt, "modell": n.modell,
            "kosten_cent": n.kosten_cent,
            # Abgeschnittenes JSON (siehe MAX_INHALT) darf das Laden nicht
            # scheitern lassen - der Text der Antwort ist das Wichtige.
            "schritte": _schritte_lesen(n.schritte),
            "zeit": n.created_at.isoformat() if n.created_at else None,
        } for n in nachrichten],
    }


def verlauf_fuer_ki(db: Session, *, user: User, chat_id: int,
                    grenze: int = VERLAUF_FUER_KI) -> list[dict]:
    """Den Zusammenhang, der an die KI geht - aus der Datenbank, nicht aus dem Browser.

    Nur die letzten Zuege: der Verlauf wird bei JEDER Anfrage mitbezahlt.
    """
    chat = _chat_holen(db, user, chat_id)
    nachrichten = list(db.scalars(
        select(AiChatMessage).where(AiChatMessage.chat_id == chat.id)
        .order_by(AiChatMessage.id.desc()).limit(max(1, grenze))))
    nachrichten.reverse()
    return [{"rolle": n.rolle, "inhalt": n.inhalt} for n in nachrichten
            if (n.inhalt or "").strip()]


def loesche(db: Session, *, user: User, chat_id: int) -> None:
    chat = _chat_holen(db, user, chat_id)
    db.delete(chat)
    db.commit()


def benenne_um(db: Session, *, user: User, chat_id: int, titel: str) -> AiChat:
    chat = _chat_holen(db, user, chat_id)
    neu = " ".join((titel or "").split())[:200]
    if not neu:
        raise ChatFehler("Der Titel darf nicht leer sein.")
    chat.titel = neu
    db.add(chat)
    db.commit()
    db.refresh(chat)
    return chat


def hefte_an(db: Session, *, user: User, chat_id: int, an: bool) -> AiChat:
    """Angeheftete Gespraeche stehen oben und werden nicht automatisch aufgeraeumt."""
    chat = _chat_holen(db, user, chat_id)
    chat.angeheftet = bool(an)
    db.add(chat)
    db.commit()
    db.refresh(chat)
    return chat
