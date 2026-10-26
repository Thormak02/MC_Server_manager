"""Managerweite Zugriffslisten: Whitelist, OPs, Banns - gueltig fuer ALLE Server.

Durchgesetzt wird bei JEDEM Serverstart: die Eintraege werden dann in die
ops.json/whitelist.json/banned-players.json/banned-ips.json des Servers
geschrieben. Wer lokal entfernt wird, ist nach dem naechsten Start wieder drin -
das ist der bewusst gewaehlte Sinn einer managerweiten Liste.

Warum beim START und nicht laufend: Minecraft liest diese Dateien beim Hochfahren
und schreibt sie selbst, sobald jemand /op oder /ban benutzt. Wuerde der Manager
sie waehrend des Betriebs anfassen, ueberschriebe der Server die Aenderung beim
naechsten eigenen Schreibvorgang wieder.

Die UUID wird EINMAL beim Anlegen aufgeloest und in der Datenbank festgehalten.
Sonst muesste jeder Serverstart erneut bei Mojang anfragen - pro Eintrag, pro Server.
"""
from __future__ import annotations

from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.global_access import GlobalAccessEntry
from app.models.server import Server

LISTEN = ("whitelist", "ops", "banned_players", "banned_ips")

# Fest verdrahtet und NICHT entfernbar: Notnagel gegen Selbstaussperrung. Steht
# bewusst im Code und nicht in der Datenbank - ein versehentliches Loeschen in der
# Oberflaeche soll den Zugang nicht kosten koennen.
UNVERAENDERLICHE_OPS: tuple[dict[str, Any], ...] = (
    {
        "name": "Thormak2002",
        "uuid": "69f230e1-af2c-4d0e-9885-93d66595a855",
        "level": 4,
    },
)

_UNVERAENDERLICH_KEYS = {
    ("ops", eintrag["name"].strip().lower()) for eintrag in UNVERAENDERLICHE_OPS
}


def ist_unveraenderlich(list_key: str, identity: str) -> bool:
    return ((list_key or "").strip().lower(), (identity or "").strip().lower()) \
        in _UNVERAENDERLICH_KEYS


def _pruefe_liste(list_key: str) -> str:
    normalisiert = (list_key or "").strip().lower()
    if normalisiert not in LISTEN:
        raise ValueError("Unbekannte Zugriffsliste.")
    return normalisiert


def liste(db: Session, list_key: str) -> list[dict[str, Any]]:
    """Eintraege einer Liste - die fest verdrahteten zuerst, als solche markiert."""
    normalisiert = _pruefe_liste(list_key)
    ergebnis: list[dict[str, Any]] = []
    if normalisiert == "ops":
        for fest in UNVERAENDERLICHE_OPS:
            ergebnis.append({
                "identity": fest["name"], "player_uuid": fest["uuid"],
                "op_level": fest["level"], "reason": None, "fest": True,
            })
    zeilen = db.scalars(
        select(GlobalAccessEntry)
        .where(GlobalAccessEntry.list_key == normalisiert)
        .order_by(GlobalAccessEntry.identity.asc())
    ).all()
    for zeile in zeilen:
        if ist_unveraenderlich(normalisiert, zeile.identity):
            continue    # waere ein Doppeleintrag zum fest verdrahteten
        ergebnis.append({
            "identity": zeile.identity, "player_uuid": zeile.player_uuid,
            "op_level": zeile.op_level, "reason": zeile.reason, "fest": False,
        })
    return ergebnis


def alle_listen(db: Session) -> dict[str, list[dict[str, Any]]]:
    return {schluessel: liste(db, schluessel) for schluessel in LISTEN}


def hinzufuegen(
    db: Session,
    list_key: str,
    identity: str,
    *,
    op_level: int | str | None = None,
    reason: str | None = None,
) -> GlobalAccessEntry:
    """Eintrag anlegen oder aktualisieren. Loest die UUID EINMAL auf."""
    from app.services import file_service

    normalisiert = _pruefe_liste(list_key)
    roh = (identity or "").strip()
    if not roh:
        raise ValueError("Name bzw. IP fehlt.")

    # Ueber den vorhandenen Erbauer gehen: der prueft die Schreibweise, loest die
    # UUID auf (Mojang, sonst Offline-UUID) und kennt die Form jeder Liste.
    vorlage = file_service._build_access_entry(normalisiert, roh, op_level=op_level)
    anzeige = str(vorlage.get("ip") if normalisiert == "banned_ips" else vorlage.get("name"))
    schluessel = anzeige.strip().lower()

    vorhanden = db.scalar(
        select(GlobalAccessEntry).where(
            GlobalAccessEntry.list_key == normalisiert,
            GlobalAccessEntry.identity_key == schluessel,
        )
    )
    if vorhanden is None:
        vorhanden = GlobalAccessEntry(list_key=normalisiert, identity=anzeige,
                                      identity_key=schluessel)
        db.add(vorhanden)
    vorhanden.identity = anzeige
    vorhanden.player_uuid = str(vorlage.get("uuid") or "") or None
    vorhanden.op_level = int(vorlage["level"]) if normalisiert == "ops" else None
    if reason is not None:
        vorhanden.reason = reason.strip() or None
    db.commit()
    db.refresh(vorhanden)
    return vorhanden


def entfernen(db: Session, list_key: str, identity: str) -> bool:
    """Eintrag loeschen. Fest verdrahtete Eintraege lassen sich NICHT entfernen."""
    normalisiert = _pruefe_liste(list_key)
    roh = (identity or "").strip()
    if ist_unveraenderlich(normalisiert, roh):
        raise ValueError(
            f"'{roh}' ist fest als Operator hinterlegt und kann nicht entfernt werden."
        )
    zeile = db.scalar(
        select(GlobalAccessEntry).where(
            GlobalAccessEntry.list_key == normalisiert,
            GlobalAccessEntry.identity_key == roh.lower(),
        )
    )
    if zeile is None:
        return False
    db.delete(zeile)
    db.commit()
    return True


def _als_dateieintrag(list_key: str, eintrag: dict[str, Any]) -> dict[str, Any] | None:
    """Datenbankzeile in die JSON-Form bringen, die Minecraft erwartet.

    Ohne Netzaufruf - die UUID steht schon fest. Ein Eintrag ohne UUID (sollte es
    nicht geben) wird uebersprungen, statt eine kaputte Datei zu schreiben.
    """
    from app.services.file_service import _utc_now_label

    if list_key == "banned_ips":
        return {
            "ip": eintrag["identity"],
            "created": _utc_now_label(), "source": "MC Server Manager",
            "expires": "forever",
            "reason": eintrag.get("reason") or "Managerweiter Bann.",
        }

    uuid = str(eintrag.get("player_uuid") or "").strip()
    if not uuid:
        return None
    name = eintrag["identity"]
    if list_key == "whitelist":
        return {"uuid": uuid, "name": name}
    if list_key == "ops":
        return {"uuid": uuid, "name": name,
                "level": int(eintrag.get("op_level") or 4),
                "bypassesPlayerLimit": True}
    if list_key == "banned_players":
        return {"uuid": uuid, "name": name,
                "created": _utc_now_label(), "source": "MC Server Manager",
                "expires": "forever",
                "reason": eintrag.get("reason") or "Managerweiter Bann."}
    return None


def auf_server_anwenden(db: Session, server: Server) -> list[str]:
    """Alle managerweiten Eintraege in die Dateien EINES Servers schreiben.

    Gedacht fuer den Moment VOR dem Start - dann ist der Server aus und die Dateien
    gehoeren uns. Gibt Warnungen zurueck und wirft nie: eine stockende Liste darf
    keinen Serverstart verhindern.
    """
    from app.services import file_service

    warnungen: list[str] = []
    for schluessel in LISTEN:
        for eintrag in liste(db, schluessel):
            datei_eintrag = _als_dateieintrag(schluessel, eintrag)
            if datei_eintrag is None:
                warnungen.append(
                    f"Managerweit: '{eintrag['identity']}' ohne UUID uebersprungen."
                )
                continue
            try:
                file_service.upsert_access_entry(server, schluessel, datei_eintrag)
            except Exception as exc:  # noqa: BLE001 - darf den Start nie kippen
                warnungen.append(
                    f"Managerweit: '{eintrag['identity']}' ({schluessel}) "
                    f"nicht geschrieben: {exc}"
                )
    return warnungen


def auf_alle_server_anwenden(db: Session) -> tuple[int, list[str]]:
    """Fuer den Knopf in den Einstellungen. LAUFENDE Server werden ausgelassen:
    Minecraft wuerde die Datei beim naechsten eigenen Schreibvorgang ueberschreiben,
    der Eintrag waere also nur scheinbar da. Sie bekommen ihn beim naechsten Start.
    """
    from app.services.process_service import is_running

    warnungen: list[str] = []
    erledigt = 0
    for server in db.scalars(select(Server).order_by(Server.name.asc())).all():
        if is_running(server.id):
            warnungen.append(
                f"'{server.name}' laeuft - wird beim naechsten Start uebernommen."
            )
            continue
        warnungen.extend(auf_server_anwenden(db, server))
        erledigt += 1
    return erledigt, warnungen


# --- Schutz gegen /deop der fest verdrahteten Operatoren ---------------------
#
# Ein erzwungener Neustart waere der teuerste Weg: alle Spieler fliegen raus, es
# dauert Minuten, und waehrenddessen bliebe der Angreifer selbst Operator -
# wiederholtes Deop waere damit ein Hebel fuer eine Dauer-Neustartschleife.
# Stattdessen liest der Manager die Konsolenzeile mit und schickt sofort ein
# `op <name>` zurueck. Minecraft schreibt die ops.json dabei selbst, der Zustand
# ist also auch auf der Platte wieder richtig.

import threading as _threading   # noqa: E402 - gehoert zu diesem Abschnitt
import time as _time             # noqa: E402

_DEOP_MARKER = (
    "no longer a server operator",   # Vanilla/Paper
    "de-opped",                      # aeltere Bukkit-Fassungen
    "deopped",
)

_MAX_VERSUCHE = 5
_VERSUCHSFENSTER = 60.0
_WAECHTER_INTERVALL = 60.0

_VERSUCHE: dict[tuple[int, str], list[float]] = {}
_VERSUCHE_LOCK = _threading.Lock()
_WAECHTER: "_threading.Thread | None" = None


def geschuetzte_ops() -> tuple[dict[str, Any], ...]:
    return UNVERAENDERLICHE_OPS


def erkenne_deop(zeile: str) -> str | None:
    """Namen des geschuetzten Operators zurueckgeben, wenn die Zeile ein Deop meldet.

    Chat wird ausgelassen: schreibt jemand den Meldungstext einfach in den Chat,
    steht er als "<Name> Made X no longer a server operator" in der Konsole. Ein
    Re-Op waere zwar harmlos (er ist ja schon OP), wuerde aber die Versuchszaehlung
    verbrauchen - und genau dieser Chat-Echo-Fall hat die Online-Liste schon
    einmal mit Unfug gefuellt.
    """
    text = (zeile or "").lower()
    if "<" in text and ">" in text:
        return None
    if not any(marker in text for marker in _DEOP_MARKER):
        return None
    for fest in UNVERAENDERLICHE_OPS:
        if fest["name"].lower() in text:
            return str(fest["name"])
    return None


def _darf_versuchen(server_id: int, name: str) -> bool:
    """Ratenbegrenzung gegen ein Duell mit einem gegenlaeufigen Rechte-Plugin."""
    jetzt = _time.monotonic()
    schluessel = (int(server_id), name.lower())
    with _VERSUCHE_LOCK:
        zeiten = [t for t in _VERSUCHE.get(schluessel, []) if jetzt - t < _VERSUCHSFENSTER]
        if len(zeiten) >= _MAX_VERSUCHE:
            _VERSUCHE[schluessel] = zeiten
            return False
        zeiten.append(jetzt)
        _VERSUCHE[schluessel] = zeiten
        return True


def versuche_zuruecksetzen() -> None:
    """Nur fuer Tests - die Zaehlung ist prozessweit."""
    with _VERSUCHE_LOCK:
        _VERSUCHE.clear()


def erzwinge_op(server_id: int, name: str, *, grund: str = "deop") -> bool:
    """`op <name>` an den laufenden Server schicken. Gibt False zurueck, wenn die
    Ratenbegrenzung greift oder der Server nicht erreichbar ist. Wirft nie."""
    from app.db.session import SessionLocal
    from app.services import process_service
    from app.services.console_service import console_service

    if not _darf_versuchen(server_id, name):
        console_service.append_output(
            server_id,
            f"[MCSM] '{name}' wird wiederholt deopt ({_MAX_VERSUCHE}x in "
            f"{int(_VERSUCHSFENSTER)}s) - vermutlich ein Rechte-Plugin. "
            "Automatisches Zurueck-Oppen fuer diesen Server pausiert.",
        )
        return False

    try:
        with SessionLocal() as db:
            server = db.get(Server, int(server_id))
            if server is None or not process_service.is_running(server_id):
                return False
            ok, meldung = process_service.send_console_command(db, server, f"op {name}", None)
        if ok:
            console_service.append_output(
                server_id, f"[MCSM] '{name}' ist fest als Operator hinterlegt "
                           f"und wurde wieder geoppt ({grund})."
            )
        else:
            console_service.append_output(
                server_id, f"[MCSM] Zurueck-Oppen von '{name}' fehlgeschlagen: {meldung}"
            )
        return ok
    except Exception as exc:  # noqa: BLE001 - darf die Konsolenauswertung nie kippen
        try:
            console_service.append_output(
                server_id, f"[MCSM] Zurueck-Oppen von '{name}' fehlgeschlagen: {exc}"
            )
        except Exception:  # noqa: BLE001
            pass
        return False


def pruefe_server(server_id: int) -> list[str]:
    """Nachkontrolle: fehlt ein geschuetzter Operator in der ops.json, nachtragen.

    Faengt Rechte-Plugins ab, die ohne die uebliche Konsolenmeldung deopen - deren
    Deop saehe der Zeilenleser nie. Gibt die Namen zurueck, die nachgezogen wurden.
    """
    from app.db.session import SessionLocal
    from app.services import file_service, process_service

    nachgezogen: list[str] = []
    try:
        if not process_service.is_running(server_id):
            return nachgezogen
        with SessionLocal() as db:
            server = db.get(Server, int(server_id))
            if server is None:
                return nachgezogen
            vorhanden = file_service.list_access_entries(server, "ops")
        nach_uuid = {
            str(e.get("uuid") or "").replace("-", "").lower(): e for e in vorhanden
        }
        for fest in UNVERAENDERLICHE_OPS:
            eintrag = nach_uuid.get(fest["uuid"].replace("-", "").lower())
            if eintrag is not None and int(eintrag.get("level") or 0) >= int(fest["level"]):
                continue
            if erzwinge_op(server_id, str(fest["name"]), grund="Nachkontrolle"):
                nachgezogen.append(str(fest["name"]))
    except Exception:  # noqa: BLE001 - eine Wache darf nie etwas umbringen
        pass
    return nachgezogen


def _waechter_schleife() -> None:
    from app.db.session import SessionLocal
    from app.services import process_service

    while True:
        _time.sleep(_WAECHTER_INTERVALL)
        try:
            with SessionLocal() as db:
                ids = [int(i) for i in db.scalars(select(Server.id)).all()]
            for server_id in ids:
                if process_service.is_running(server_id):
                    pruefe_server(server_id)
        except Exception:  # noqa: BLE001
            pass


def starte_waechter() -> None:
    """Hintergrundwache starten (idempotent)."""
    global _WAECHTER
    if _WAECHTER is not None and _WAECHTER.is_alive():
        return
    _WAECHTER = _threading.Thread(
        target=_waechter_schleife, daemon=True, name="mcsm-op-waechter"
    )
    _WAECHTER.start()
