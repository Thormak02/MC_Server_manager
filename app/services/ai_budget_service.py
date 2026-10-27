"""Monatsdeckel, Rate Limit und Protokoll fuer KI-Aufrufe.

Der Grund steht in der TODO-Notiz des Nutzers: "Budget pro Monat deckeln und den Verbrauch
sichtbar machen (sonst frisst eine Crash-Analyse-Schleife still das Guthaben)". Genau das
ist hier umgesetzt - und zwar so, dass der Deckel VOR dem Aufruf greift, nicht danach.

Drei Schranken, in dieser Reihenfolge geprueft:

1. **Abschaltung** - global oder je Funktion. Nichts laeuft, was nicht eingeschaltet ist.
2. **Rate Limit** je Nutzer (Stunde und Tag). Faengt die Schleife ab, bevor sie Geld
   kostet - auch die versehentliche, etwa einen Dauerlauf durch einen Fehler im Aufrufer.
3. **Monatsdeckel** in US-Cent. Bei 80 Prozent eine Warnung, ab 100 Prozent Schluss bis zum
   Monatswechsel.

Gezaehlt wird nach dem Aufruf mit den echten Token-Zahlen des Anbieters, nicht mit einer
Schaetzung. Abgelehnte und gescheiterte Aufrufe stehen ebenfalls im Protokoll (mit 0 Cent) -
sonst sieht man hinterher nicht, dass eine Schleife gegen die Wand gelaufen ist.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.models.ai_usage import AiUsage
from app.models.user import User

# Standardwerte. Alle in den Einstellungen aenderbar (ai_settings_service).
STANDARD_MONATSBUDGET_CENT = 20_000      # 200 US-Dollar
STANDARD_WARNSCHWELLE = 0.8              # bei 80 Prozent warnen
STANDARD_LIMIT_STUNDE = 20
STANDARD_LIMIT_TAG = 60
# Moderatoren bekommen die Haelfte: sie duerfen mitarbeiten, aber das Guthaben ist ein
# gemeinsamer Topf, und der Nutzer wollte sie ausdruecklich "mit eingeschraenkten Rechten".
MODERATOR_ANTEIL = 0.5


class BudgetGesperrt(Exception):
    """Der Aufruf darf nicht stattfinden. Die Meldung ist fuer den Nutzer gedacht."""


def _monatsbeginn(jetzt: datetime | None = None) -> datetime:
    bezug = jetzt or datetime.now(timezone.utc)
    return bezug.replace(day=1, hour=0, minute=0, second=0, microsecond=0)


def _anteil(rolle: str, wert: int) -> int:
    """Kontingent je Rolle. Moderatoren bekommen den halben Wert, mindestens aber 1."""
    if (rolle or "") == UserRole.MODERATOR.value:
        return max(1, int(wert * MODERATOR_ANTEIL))
    return wert


def verbrauch_monat_cent(db: Session, *, jetzt: datetime | None = None) -> float:
    """Was in diesem Kalendermonat ueber alle Nutzer zusammengekommen ist."""
    summe = db.scalar(
        select(func.coalesce(func.sum(AiUsage.kosten_cent), 0.0))
        .where(AiUsage.created_at >= _monatsbeginn(jetzt))
    )
    return float(summe or 0.0)


def verbrauch_je_funktion(db: Session, *, jetzt: datetime | None = None) -> list[dict]:
    """Verbrauch des Monats nach Funktion - damit sichtbar ist, WOFUER das Geld ging."""
    zeilen = db.execute(
        select(AiUsage.funktion,
               func.count(AiUsage.id),
               func.coalesce(func.sum(AiUsage.kosten_cent), 0.0),
               func.coalesce(func.sum(AiUsage.tokens_ein), 0),
               func.coalesce(func.sum(AiUsage.tokens_aus), 0))
        .where(AiUsage.created_at >= _monatsbeginn(jetzt))
        .group_by(AiUsage.funktion)
        .order_by(func.coalesce(func.sum(AiUsage.kosten_cent), 0.0).desc())
    ).all()
    return [{"funktion": z[0], "aufrufe": int(z[1]), "cent": float(z[2]),
             "tokens_ein": int(z[3]), "tokens_aus": int(z[4])} for z in zeilen]


def aufrufe_seit(db: Session, user_id: int | None, seit: datetime) -> int:
    """Aufrufe eines Nutzers seit einem Zeitpunkt - Grundlage des Rate Limits.

    Abgelehnte zaehlen MIT: sonst koennte man das Limit mit Anfragen umgehen, die an der
    Themen-Sperre haengen bleiben, und jede davon kostet Rechenzeit im Manager.
    """
    if user_id is None:
        return 0
    anzahl = db.scalar(
        select(func.count(AiUsage.id))
        .where(AiUsage.user_id == user_id, AiUsage.created_at >= seit)
    )
    return int(anzahl or 0)


def budget_stand(db: Session, einstellungen: dict | None = None) -> dict:
    """Alles, was die Oberflaeche ueber das Budget anzeigen soll."""
    werte = einstellungen or {}
    deckel = int(werte.get("monatsbudget_cent") or STANDARD_MONATSBUDGET_CENT)
    schwelle = float(werte.get("warnschwelle") or STANDARD_WARNSCHWELLE)
    verbraucht = verbrauch_monat_cent(db)
    anteil = (verbraucht / deckel) if deckel > 0 else 0.0
    return {
        "deckel_cent": deckel,
        "verbraucht_cent": verbraucht,
        "rest_cent": max(0.0, deckel - verbraucht),
        "anteil": anteil,
        "warnt": anteil >= schwelle and anteil < 1.0,
        "gesperrt": deckel > 0 and verbraucht >= deckel,
        "je_funktion": verbrauch_je_funktion(db),
    }


def pruefe_erlaubt(db: Session, *, user: User, funktion: str,
                   einstellungen: dict | None = None) -> None:
    """Darf dieser Nutzer diese Funktion jetzt aufrufen? Wirft BudgetGesperrt, wenn nicht.

    Wird VOR dem Aufruf beim Anbieter ausgefuehrt - das ist der ganze Sinn: ein Aufruf, der
    nicht stattfindet, kostet nichts.
    """
    werte = einstellungen or {}

    if not werte.get("aktiv", True):
        raise BudgetGesperrt("Die KI-Funktionen sind in den Einstellungen abgeschaltet.")
    if not (werte.get("funktionen") or {}).get(funktion, True):
        raise BudgetGesperrt(f"Die Funktion '{funktion}' ist in den Einstellungen "
                             f"abgeschaltet.")

    rolle = getattr(user, "role", "") or ""
    erlaubte_rollen = {UserRole.SUPER_ADMIN.value, UserRole.ADMIN.value,
                       UserRole.MODERATOR.value}
    if rolle not in erlaubte_rollen:
        raise BudgetGesperrt("Fuer KI-Funktionen braucht es mindestens Moderator-Rechte.")

    jetzt = datetime.now(timezone.utc)
    grenze_stunde = _anteil(rolle, int(werte.get("limit_stunde")
                                       or STANDARD_LIMIT_STUNDE))
    grenze_tag = _anteil(rolle, int(werte.get("limit_tag") or STANDARD_LIMIT_TAG))

    in_stunde = aufrufe_seit(db, user.id, jetzt - timedelta(hours=1))
    if in_stunde >= grenze_stunde:
        raise BudgetGesperrt(
            f"Dein Stundenkontingent ist aufgebraucht ({in_stunde} von {grenze_stunde} "
            f"Anfragen). Das schuetzt das Guthaben vor Dauerschleifen - in einer Stunde "
            f"geht es weiter.")
    in_tag = aufrufe_seit(db, user.id, jetzt - timedelta(days=1))
    if in_tag >= grenze_tag:
        raise BudgetGesperrt(
            f"Dein Tageskontingent ist aufgebraucht ({in_tag} von {grenze_tag} Anfragen).")

    stand = budget_stand(db, werte)
    if stand["gesperrt"]:
        raise BudgetGesperrt(
            f"Das Monatsbudget ist ausgeschoepft "
            f"({stand['verbraucht_cent'] / 100:.2f} von "
            f"{stand['deckel_cent'] / 100:.2f} US-Dollar). Bis zum Monatswechsel sind "
            f"keine KI-Aufrufe mehr moeglich. Der Deckel steht in den Einstellungen.")


def protokolliere(db: Session, *, user_id: int | None, server_id: int | None,
                  funktion: str, modell: str, tokens_ein: int = 0, tokens_aus: int = 0,
                  kosten_cent: float = 0.0, status: str = "ok",
                  hinweis: str | None = None) -> None:
    """Einen Aufruf festhalten. Scheitert still - ein fehlender Protokolleintrag darf eine
    fertige Antwort nicht entwerten."""
    try:
        db.add(AiUsage(user_id=user_id, server_id=server_id, funktion=funktion,
                       modell=modell, tokens_ein=max(0, int(tokens_ein)),
                       tokens_aus=max(0, int(tokens_aus)),
                       kosten_cent=max(0.0, float(kosten_cent)), status=status,
                       hinweis=(hinweis or None)))
        db.commit()
    except Exception:  # noqa: BLE001
        try:
            db.rollback()
        except Exception:  # noqa: BLE001
            pass


def letzte_aufrufe(db: Session, *, grenze: int = 50) -> list[AiUsage]:
    """Die neuesten Aufrufe fuer die Anzeige in den Einstellungen."""
    return list(db.scalars(
        select(AiUsage).order_by(AiUsage.created_at.desc()).limit(max(1, grenze))
    ).all())
