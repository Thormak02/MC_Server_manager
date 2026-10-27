"""Einstellungen der KI-Funktionen - inklusive der verschluesselten Schluessel-Ablage.

Liegt in der vorhandenen ``app_settings``-Tabelle (Schluessel-Wert). Der API-Schluessel ist
der einzige Sonderfall: er wird vor dem Ablegen ueber ``ai_secret_service`` verschluesselt
und nie im Klartext zurueckgegeben - weder an die Oberflaeche noch ins Protokoll. Wer
pruefen will, ob der richtige hinterlegt ist, bekommt die maskierte Form.
"""

from __future__ import annotations

import json

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.app_setting import AppSetting
from app.services import ai_secret_service
from app.services.ai_budget_service import (
    STANDARD_LIMIT_STUNDE,
    STANDARD_LIMIT_TAG,
    STANDARD_MONATSBUDGET_CENT,
    STANDARD_WARNSCHWELLE,
)
from app.services.ai_provider_service import MODELLE, STANDARD_MODELLE

PRAEFIX = "ai."
SCHLUESSEL_KEY = f"{PRAEFIX}api_key"

# Die Funktionen, die es gibt - Reihenfolge wie in der Oberflaeche.
FUNKTIONEN: dict[str, str] = {
    "crash_analyse": "Log- und Crash-Analyse",
    "config_assistent": "Konfigurationsassistent",
    "changelog": "Changelog-Zusammenfassung",
    "agent": "Freier Agent",
}


def _zeile(db: Session, key: str) -> AppSetting | None:
    return db.scalar(select(AppSetting).where(AppSetting.key == key))


def _lies(db: Session, key: str, standard: str = "") -> str:
    zeile = _zeile(db, key)
    return zeile.value if zeile and zeile.value is not None else standard


def _schreib(db: Session, key: str, wert: str | None) -> None:
    zeile = _zeile(db, key)
    text = (wert or "").strip()
    if not text:
        if zeile is not None:
            db.delete(zeile)
            db.commit()
        return
    if zeile is None:
        zeile = AppSetting(key=key, value=text)
    else:
        zeile.value = text
    db.add(zeile)
    db.commit()


def _bool(wert: str, standard: bool) -> bool:
    text = (wert or "").strip().lower()
    if not text:
        return standard
    return text in {"1", "true", "on", "yes"}


def hole_einstellungen(db: Session) -> dict:
    """Alle Einstellungen in der Form, die ai_budget_service und ai_service erwarten.

    Enthaelt NIE den Schluessel im Klartext - nur ob einer hinterlegt ist und seine
    maskierte Form.
    """
    abgelegt = _lies(db, SCHLUESSEL_KEY)
    try:
        modelle = json.loads(_lies(db, f"{PRAEFIX}modelle", "{}")) or {}
    except Exception:  # noqa: BLE001
        modelle = {}
    try:
        preise = json.loads(_lies(db, f"{PRAEFIX}preise", "{}")) or {}
    except Exception:  # noqa: BLE001
        preise = {}

    funktionen = {}
    for name in FUNKTIONEN:
        funktionen[name] = _bool(_lies(db, f"{PRAEFIX}funktion.{name}"), True)

    return {
        "aktiv": _bool(_lies(db, f"{PRAEFIX}aktiv"), False),
        "anbieter": _lies(db, f"{PRAEFIX}anbieter", "anthropic"),
        "schluessel_hinterlegt": bool(abgelegt),
        "schluessel_maskiert": _lies(db, f"{PRAEFIX}api_key_maske"),
        "schluessel_verschluesselt": ai_secret_service.ist_verschluesselt(abgelegt),
        "monatsbudget_cent": int(_lies(db, f"{PRAEFIX}monatsbudget_cent",
                                       str(STANDARD_MONATSBUDGET_CENT)) or 0),
        "warnschwelle": float(_lies(db, f"{PRAEFIX}warnschwelle",
                                    str(STANDARD_WARNSCHWELLE)) or 0.8),
        "limit_stunde": int(_lies(db, f"{PRAEFIX}limit_stunde",
                                  str(STANDARD_LIMIT_STUNDE)) or 0),
        "limit_tag": int(_lies(db, f"{PRAEFIX}limit_tag", str(STANDARD_LIMIT_TAG)) or 0),
        "namen_ersetzen": _bool(_lies(db, f"{PRAEFIX}namen_ersetzen"), False),
        "themensperre": _bool(_lies(db, f"{PRAEFIX}themensperre"), True),
        "funktionen": funktionen,
        "modelle": {**STANDARD_MODELLE, **modelle},
        "preise": {**MODELLE, **preise},
    }


def setze_schluessel(db: Session, klartext: str) -> str:
    """Schluessel verschluesselt ablegen. Rueckgabe: die maskierte Form fuer die Anzeige.

    Der Klartext verlaesst diese Funktion nicht und wird nirgends protokolliert.
    """
    wert = (klartext or "").strip()
    if not wert:
        _schreib(db, SCHLUESSEL_KEY, None)
        _schreib(db, f"{PRAEFIX}api_key_maske", None)
        return ""
    verschluesselt = ai_secret_service.verschluessele(wert)
    maske = ai_secret_service.maskiere(wert)
    _schreib(db, SCHLUESSEL_KEY, verschluesselt)
    _schreib(db, f"{PRAEFIX}api_key_maske", maske)
    return maske


def hole_schluessel(db: Session) -> str:
    """Klartext-Schluessel fuer den Aufruf beim Anbieter. Nur hier entschluesseln."""
    return ai_secret_service.entschluessele(_lies(db, SCHLUESSEL_KEY))


def loesche_schluessel(db: Session) -> None:
    _schreib(db, SCHLUESSEL_KEY, None)
    _schreib(db, f"{PRAEFIX}api_key_maske", None)


def speichere(db: Session, werte: dict) -> list[str]:
    """Einstellungen aus dem Formular uebernehmen. Rueckgabe: Meldungen fuer den Nutzer."""
    meldungen: list[str] = []

    if "aktiv" in werte:
        _schreib(db, f"{PRAEFIX}aktiv", "true" if werte["aktiv"] else "false")
    if "namen_ersetzen" in werte:
        _schreib(db, f"{PRAEFIX}namen_ersetzen",
                 "true" if werte["namen_ersetzen"] else "false")
    if "themensperre" in werte:
        _schreib(db, f"{PRAEFIX}themensperre",
                 "true" if werte["themensperre"] else "false")

    for name in FUNKTIONEN:
        feld = f"funktion_{name}"
        if feld in werte:
            _schreib(db, f"{PRAEFIX}funktion.{name}",
                     "true" if werte[feld] else "false")

    if werte.get("monatsbudget_cent") is not None:
        cent = max(0, int(werte["monatsbudget_cent"]))
        _schreib(db, f"{PRAEFIX}monatsbudget_cent", str(cent))
        if cent == 0:
            meldungen.append("Monatsbudget 0 bedeutet: KEIN Deckel. Damit kann eine "
                             "Dauerschleife das Guthaben beim Anbieter aufbrauchen.")
    if werte.get("warnschwelle") is not None:
        schwelle = min(1.0, max(0.0, float(werte["warnschwelle"])))
        _schreib(db, f"{PRAEFIX}warnschwelle", f"{schwelle:.2f}")
    for feld, key in (("limit_stunde", "limit_stunde"), ("limit_tag", "limit_tag")):
        if werte.get(feld) is not None:
            _schreib(db, f"{PRAEFIX}{key}", str(max(1, int(werte[feld]))))

    modelle = {}
    for name in FUNKTIONEN:
        gewaehlt = (werte.get(f"modell_{name}") or "").strip()
        if gewaehlt and gewaehlt in MODELLE:
            modelle[name] = gewaehlt
    if modelle:
        _schreib(db, f"{PRAEFIX}modelle", json.dumps(modelle))

    if werte.get("preise"):
        _schreib(db, f"{PRAEFIX}preise", json.dumps(werte["preise"]))

    return meldungen


def modell_fuer(einstellungen: dict, funktion: str) -> str:
    """Welches Modell diese Funktion benutzt."""
    return ((einstellungen.get("modelle") or {}).get(funktion)
            or STANDARD_MODELLE.get(funktion)
            or "claude-sonnet-5-5")
