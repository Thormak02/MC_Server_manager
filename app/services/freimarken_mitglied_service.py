"""Mitgliedschaften: wer, seit wann, bis wann, aus welcher Zahlung - und was sie gibt.

Die Datenhaltung liegt im Manager, nicht im Plugin (Plan 12a.5, Bauschritt 16: "Das
Plugin spricht nie mit dem Zahlungsanbieter"). Das Plugin kennt nur die WIRKUNG, und die
kommt ueber ein Recht der Form ``freimarken.extra.<name>.<zahl>``.

DER MANAGER VERGIBT DAS RECHT NICHT SELBST
Er NENNT es. Rechte vergibt die Rechteverwaltung des Servers (LuckPerms oder was immer
dort laeuft), und der Manager hat dafuer keinen Weg - ``/lp`` waere ein Konsolenbefehl an
ein fremdes Plugin, dessen Form sich mit jeder Fassung aendern kann. Die Seite zeigt
darum die Rechtezeile zum Kopieren. Das ist ehrlicher als ein Knopf, der bei einer
anderen Rechteverwaltung stumm nichts tut.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.freimarken_mitgliedschaft import FreimarkenMitgliedschaft

# Die Vorsilbe, unter der Rangzahl.ausRecht den Zuschlag sucht. Sie steht im Plugin an
# vier Stellen als RECHT_EXTRA_*, und die Gestalt ist dort ausdruecklich begruendet:
# Schreiber und Leser derselben Gestalt an zwei Stellen laufen eines Tages auseinander
# (Rangzahl.java). Hier ist der Schreiber, und eine Probe haelt ihn gegen den Quelltext.
VORSILBE = "freimarken.extra."

HERKUNFT_VON_HAND = "von Hand"


class MitgliedFehler(Exception):
    """Der Text geht an den Nutzer - also deutsch und ohne Innereien."""


def zuschlaege() -> dict[str, str]:
    """Die gueltigen Zuschlagsnamen samt Erklaerung, aus dem Feldkatalog."""
    from app.services.freimarken_service import katalog

    roh = katalog().get("zuschlaege") or {}
    if not isinstance(roh, dict):
        raise MitgliedFehler("Die Zuschlagsliste im Feldkatalog ist unlesbar.")
    return {str(name): str(satz) for name, satz in roh.items()}


def rechtename(zuschlag_name: str, zahl: int) -> str:
    """Die Rechtezeile, die diesen Zuschlag gibt.

    Zusammengesetzt und nicht abgeschrieben: Die Vorsilbe steht an EINER Stelle, damit
    eine Aenderung nicht an fuenf Stellen nachgezogen werden muss.
    """
    return VORSILBE + str(zuschlag_name) + "." + str(int(zahl))


def _jetzt() -> datetime:
    return datetime.now(timezone.utc)


def _utc(wert: datetime) -> datetime:
    """Einen nicht gekennzeichneten Zeitpunkt als UTC lesen.

    SQLite gibt gespeicherte Zeitpunkte ohne Zone zurueck, auch wenn die Spalte
    ``DateTime(timezone=True)`` heisst. Ein Vergleich mit ``datetime.now(timezone.utc)``
    wirft dann "can't compare offset-naive and offset-aware datetimes" - und zwar erst,
    wenn die erste Mitgliedschaft ablaeuft.
    """
    return wert.replace(tzinfo=timezone.utc) if wert.tzinfo is None else wert


def _als_utc(wert: datetime | None) -> datetime | None:
    """Wie {_utc}, aber ``None`` bleibt ``None``."""
    return None if wert is None else _utc(wert)


def laeuft(eintrag: FreimarkenMitgliedschaft, *, stichtag: datetime | None = None) -> bool:
    """Gilt diese Mitgliedschaft am Stichtag?

    ``bis`` ist der LETZTE gueltige Augenblick und nicht der erste ungueltige: Wer bis zum
    31. bezahlt hat, ist am 31. noch Mitglied. ``None`` heisst "laeuft weiter".
    """
    wann = stichtag or _jetzt()
    seit = _als_utc(eintrag.seit)
    bis = _als_utc(eintrag.bis)
    if seit is not None and wann < seit:
        return False
    # > und nicht >=: bis ist der LETZTE gueltige Augenblick. Mit >= waere eine
    # Mitgliedschaft, die genau jetzt endet, schon vorbei - und bei einer Eingabe auf
    # den Tag genau waere der bezahlte letzte Tag verloren.
    if bis is not None and wann > bis:
        return False
    return True


def pruefe(zuschlag_name: str, zahl: Any, *, seit: datetime | None,
           bis: datetime | None) -> tuple[str, int]:
    """Was eine neue Mitgliedschaft erfuellen muss. Wirft mit einem deutschen Satz.

    Die ZAHL muss ueber 0 liegen, und das ist kein Formalismus: ``Rangzahl.mitZuschlag``
    rechnet mit ``Math.max(zuschlag, 0)``. Eine 0 oder eine negative Zahl wuerde also
    gespeichert, das Recht wuerde vergeben, und es taete nichts - "ein Tippfehler, kein
    Entzug" steht dazu in RangzahlTest. Eine Mitgliedschaft, die stumm nichts bewirkt, ist
    schlimmer als eine abgewiesene Eingabe.
    """
    gueltig = zuschlaege()
    name = str(zuschlag_name or "").strip()
    if name not in gueltig:
        raise MitgliedFehler(
            "'" + (name or "(leer)") + "' ist kein Zuschlag, den das Plugin liest. Es "
            "gibt: " + ", ".join(sorted(gueltig)) + ".")
    try:
        zahl_wert = int(str(zahl).strip())
    except (TypeError, ValueError) as fehler:
        raise MitgliedFehler("Die Zahl des Zuschlags muss eine ganze Zahl sein.") from fehler
    if zahl_wert < 1:
        raise MitgliedFehler(
            "Die Zahl des Zuschlags muss ueber 0 liegen. Das Plugin rechnet mit "
            "Math.max(zahl, 0): Eine 0 oder eine negative Zahl wuerde das Recht "
            "vergeben und nichts bewirken.")
    if seit is not None and bis is not None and _utc(bis) < _utc(seit):
        raise MitgliedFehler("'Bis' liegt vor 'seit' - so herum gibt es keinen Zeitraum.")
    return name, zahl_wert


def lege_an(db: Session, *, server_id: int, spieler_kennung: str, spieler_name: str,
            zuschlag_name: str, zuschlag_zahl: Any, seit: datetime | None = None,
            bis: datetime | None = None, zahlung_herkunft: str = HERKUNFT_VON_HAND,
            zahlung_beleg: str = "", grund: str = "",
            angelegt_von_user_id: int | None = None) -> FreimarkenMitgliedschaft:
    """Eine Mitgliedschaft eintragen. Prueft zuerst, schreibt danach."""
    kennung = str(spieler_kennung or "").strip().lower()
    if not kennung:
        raise MitgliedFehler(
            "Ohne die Kennung des Spielers geht es nicht - sie ist der Schluessel, unter "
            "dem das Plugin ihn fuehrt. Der Name allein genuegt nicht: Er aendert sich "
            "bei einer Umbenennung.")

    name, zahl = pruefe(zuschlag_name, zuschlag_zahl, seit=seit, bis=bis)

    eintrag = FreimarkenMitgliedschaft(
        server_id=int(server_id),
        spieler_kennung=kennung,
        spieler_name=str(spieler_name or "")[:32],
        zuschlag_name=name,
        zuschlag_zahl=zahl,
        seit=(seit or _jetzt()),
        bis=bis,
        zahlung_herkunft=(str(zahlung_herkunft or "").strip() or HERKUNFT_VON_HAND)[:64],
        zahlung_beleg=(str(zahlung_beleg or "").strip()[:128] or None),
        grund=str(grund or "").strip()[:256],
        angelegt_von_user_id=angelegt_von_user_id,
    )
    db.add(eintrag)
    db.commit()
    db.refresh(eintrag)
    return eintrag


def beende(db: Session, *, server_id: int, eintrag_id: int,
           bis: datetime | None = None) -> FreimarkenMitgliedschaft:
    """Eine Mitgliedschaft beenden - mit Datum, nicht durch Loeschen.

    GELOESCHT WIRD NICHT: Die Zeile ist der Beleg dafuer, dass jemand bezahlt hat. Wer sie
    entfernt, kann in drei Monaten nicht mehr beantworten, warum dieser Spieler damals
    drei Parzellen mehr hatte.

    ``server_id`` steht MIT in der Bedingung und nicht nur die Nummer: Eine
    AUTO_INCREMENT-Nummer wird nach einem Neustart wieder vergeben, und ein Admin, der
    nur einen Server sehen darf, soll ueber eine geratene Nummer nicht die Zeile eines
    anderen Servers beenden.
    """
    eintrag = db.scalars(select(FreimarkenMitgliedschaft).where(
        FreimarkenMitgliedschaft.id == int(eintrag_id),
        FreimarkenMitgliedschaft.server_id == int(server_id))).first()
    if eintrag is None:
        raise MitgliedFehler("Diese Mitgliedschaft gibt es an diesem Server nicht.")
    eintrag.bis = bis or _jetzt()
    db.commit()
    db.refresh(eintrag)
    return eintrag


def liste(db: Session, server_id: int, *, nur_laufende: bool = False,
          grenze: int = 500) -> list[dict[str, Any]]:
    """Die Mitgliedschaften dieses Servers, neueste zuerst.

    ``order_by(id.desc())`` und nicht nach ``seit``: Zwei am selben Tag eingetragene
    Zeilen kaemen sonst in beliebiger Reihenfolge, und die Nummer ist das einzige, was
    innerhalb eines Servers eindeutig und aufsteigend ist.
    """
    eintraege = list(db.scalars(select(FreimarkenMitgliedschaft).where(
        FreimarkenMitgliedschaft.server_id == int(server_id)).order_by(
        FreimarkenMitgliedschaft.id.desc()).limit(grenze)))

    raus: list[dict[str, Any]] = []
    for eintrag in eintraege:
        gilt = laeuft(eintrag)
        if nur_laufende and not gilt:
            continue
        raus.append({
            "id": eintrag.id,
            "kennung": eintrag.spieler_kennung,
            "name": eintrag.spieler_name or eintrag.spieler_kennung[:8],
            "zuschlag": eintrag.zuschlag_name,
            "zahl": eintrag.zuschlag_zahl,
            "recht": rechtename(eintrag.zuschlag_name, eintrag.zuschlag_zahl),
            "seit": _als_utc(eintrag.seit),
            "bis": _als_utc(eintrag.bis),
            "laeuft": gilt,
            "herkunft": eintrag.zahlung_herkunft,
            "beleg": eintrag.zahlung_beleg or "",
            "grund": eintrag.grund or "",
        })
    return raus


def rechtezeilen(db: Session, server_id: int) -> list[dict[str, Any]]:
    """Welches Recht welcher Spieler haben muesste - zum Abgleich mit dem Server.

    NUR DIE HOECHSTE ZAHL je Spieler und Zuschlag: ``Rangzahl.hoechsteAus`` nimmt die
    groesste Zahl, die ein Recht dieser Vorsilbe hergibt, und addiert sie EINMAL. Zwei
    laufende Mitgliedschaften auf denselben Zuschlag geben also nicht die Summe, sondern
    die groessere - wer hier beide Zeilen auflistete, verspraeche einen Zuschlag, den das
    Plugin nicht gibt.
    """
    hoechste: dict[tuple[str, str], dict[str, Any]] = {}
    for eintrag in liste(db, server_id, nur_laufende=True, grenze=5000):
        schluessel = (eintrag["kennung"], eintrag["zuschlag"])
        vorhanden = hoechste.get(schluessel)
        if vorhanden is None or eintrag["zahl"] > vorhanden["zahl"]:
            hoechste[schluessel] = eintrag

    return [{
        "kennung": eintrag["kennung"],
        "name": eintrag["name"],
        "recht": eintrag["recht"],
        "zuschlag": eintrag["zuschlag"],
        "zahl": eintrag["zahl"],
    } for eintrag in sorted(hoechste.values(),
                            key=lambda e: (e["name"].lower(), e["zuschlag"]))]
