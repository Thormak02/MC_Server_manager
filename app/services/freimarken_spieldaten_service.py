"""Die Spieldaten des Plugins lesen: Besitz und Konten.

NUR LESEN. Der Weg hinein steht in ``freimarken_db_service`` und weist jede Abfrage ab,
die nicht mit SELECT beginnt. Geaendert wird ueber das Plugin (Plan 12a.2 Regel 2) -
``/freimarken geld`` und ``/freimarken besitz``, gesendet ueber die Konsole.

DIE UUID IST DER SCHLUESSEL, DER NAME NUR DIE BESCHRIFTUNG
Die Plugin-Tabellen fuehren Spieler ausschliesslich als ``CHAR(36)``. Einen Namen dazu
gibt es nur in der ``usercache.json`` des Servers, und der ist der Name vom letzten
Login - nach einer Umbenennung veraltet. Darum bleibt die UUID ueberall der Schluessel:
Gruppiert, sortiert und verglichen wird nach ihr, nie nach dem Namen. Derselbe Mensch kann
sonst unter zwei Kennungen stehen (eine echte und eine selbst erzeugte), und eine Liste,
die nach Namen zusammenzaehlt, verschmilzt zwei Konten zu einem.

DER RUECKFALL IST NIE LEER
Kennt der Server den Namen nicht - weil der Besitz aus einem Umzug stammt oder der Spieler
nie auf DIESEM Server war -, zeigt die Seite die ersten acht Zeichen der Kennung. Das ist
das Hausmass des Plugins (``Spielername.ZEICHEN_DER_KENNUNG``). Eine leere Zelle saehe wie
ein Fehler aus.

KEIN NETZ IM ANFRAGEPFAD
Es wird NICHT bei Mojang gefragt. ``avatar_service._uuid_zu_name`` fragt ohnehin in die
andere Richtung (Name -> Kennung), und ein Namensaufruf blockiert fuenf Sekunden ohne
Sammelabfrage: Eine Uebersicht mit fuenfzig Zeilen loeste fuenfzig Einzelanfragen
hintereinander aus. Was die Datei nicht weiss, bleibt eine Kennung.
"""
from __future__ import annotations

from pathlib import Path
from typing import Any

from app.models.server import Server
from app.services import freimarken_db_service as fdb

# So viele Zeichen der Kennung zeigt der Rueckfall - dieselbe Zahl wie
# Spielername.ZEICHEN_DER_KENNUNG im Plugin.
ZEICHEN_DER_KENNUNG = 8

# Hoechstens so viele Zeilen je Seite. Die Plugin-Tabellen haben keinen Index auf jeder
# Spalte, nach der man gern sortiert (der Quelltext nennt mehrere mit dem Zusatz "hat
# heute keinen Leser"), und eine Seite mit zehntausend Zeilen liest ohnehin niemand.
GRENZE = 200

# Die Arten einer Besitzzeile - die vollstaendige ENUM-Liste aus Datenbank.java Schritt 29.
#
# VOLLSTAENDIG aufgezaehlt, und das ist der Punkt: Wer nur 'parzelle' und 'gruppe' kennt,
# zeigt eine Wolkeninsel als Parzelle an. Eine neue Art des Plugins faellt hier nicht
# stumm durch, sondern erscheint mit ihrem Rohwert in Klammern - dann sieht man, dass hier
# etwas nachzutragen ist.
ARTEN = {
    "parzelle": "Parzelle",
    "gruppe": "Grundstueck",
    "werder": "Hafenwerder-Insel",
    "freistadt": "Freistadt",
    "ladeneinheit": "Ladeneinheit",
    "wolke": "Wolkeninsel",
}

ZUSTAENDE = {
    "aktiv": "aktiv",
    "gekuendigt": "gekuendigt",
    "verfallen": "verfallen",
}


def art_wort(roh: Any) -> str:
    """Die Art als Wort. Eine unbekannte Art wird BENANNT und nicht geraten."""
    schluessel = str(roh or "").strip().lower()
    bekannt = ARTEN.get(schluessel)
    if bekannt:
        return bekannt
    return "unbekannte Art (" + (schluessel or "leer") + ")"


def zustand_wort(roh: Any) -> str:
    schluessel = str(roh or "").strip().lower()
    return ZUSTAENDE.get(schluessel) or ("unbekannt (" + (schluessel or "leer") + ")")


def namen(server: Server) -> dict[str, str]:
    """Kennung -> Name, aus den Dateien dieses Servers. Die EINE Stelle, die ihn holt.

    Dieselbe Haltung wie ``Spielername`` im Plugin ("die EINE Stelle, die ihn holt"). Drei
    Quellen in dieser Reihenfolge, spaetere ergaenzen nur, was fehlt:

    1. ``usercache.json`` - die einzige Zuordnung, die ein Server von sich aus fuehrt.
    2. die drei Zugangslisten im Serverordner (``whitelist.json``, ``ops.json``,
       ``banned-players.json``); dort stehen Kennung und Name ohnehin nebeneinander.

    Was der Zwischenspeicher NICHT ist: ein Archiv. Das Format fuehrt ein
    ``expiresOn``, das ``lade_usercache`` nicht liest - abgelaufene und gueltige Eintraege
    sind damit nicht unterscheidbar, und ein lange abwesender Spieler kann aus der Datei
    verschwinden. Fuer eine Beschriftung genuegt das; als Grundlage einer Entscheidung
    nicht, und darum wird hier nichts entschieden.
    """
    from app.services.world_identity_service import lade_usercache

    basis = Path(str(server.base_path or ""))
    gefunden: dict[str, str] = {}
    try:
        gefunden.update(lade_usercache(basis))
    except Exception:  # noqa: BLE001 - eine kaputte Datei ist kein Fehlerfall
        pass

    import json

    for datei in ("whitelist.json", "ops.json", "banned-players.json"):
        pfad = basis / datei
        if not pfad.is_file():
            continue
        try:
            daten = json.loads(pfad.read_text(encoding="utf-8", errors="ignore"))
        except Exception:  # noqa: BLE001
            continue
        if not isinstance(daten, list):
            continue
        for eintrag in daten:
            if not isinstance(eintrag, dict):
                continue
            kennung = str(eintrag.get("uuid") or "").strip().lower()
            name = str(eintrag.get("name") or "").strip()
            # setdefault und nicht zuweisen: Der Zwischenspeicher ist aktueller als eine
            # Zugangsliste, in der ein Name seit dem Eintrag stehen geblieben ist.
            if kennung and name:
                gefunden.setdefault(kennung, name)
    return gefunden


def beschriftung(kennung: Any, bekannt: dict[str, str]) -> str:
    """Der Name zu einer Kennung - oder ihre ersten acht Zeichen."""
    roh = str(kennung or "").strip()
    if not roh:
        return "(ohne Kennung)"
    name = bekannt.get(roh.lower())
    if name:
        return name
    return roh[:ZEICHEN_DER_KENNUNG]


def _stadtkassen(server: Server) -> set[str]:
    """Die Kontonummern, die einer STADT gehoeren und keinem Menschen.

    ``parzellen.kasse`` ist eine Kennung, die in ``kassen.spieler`` und
    ``buchungen.spieler`` steht - eine Stadtkasse sieht in der Kontenliste also wie ein
    Spieler aus, und eine Namensauflesung findet fuer sie niemanden. Ohne diese Frage
    stuenden Staedte als Spieler mit einem achtstelligen Kuerzel in der Liste.
    """
    zeilen = fdb.frage(
        server,
        "SELECT DISTINCT kasse FROM parzellen WHERE kasse IS NOT NULL",
        grenze=10_000)
    return {str(zeile.get("kasse") or "").strip().lower()
            for zeile in zeilen if zeile.get("kasse")}


def besitz(server: Server, *, welt: str = "", eigentuemer: str = "",
           grenze: int = GRENZE) -> list[dict[str, Any]]:
    """Alle Besitzzeilen - Parzellen, Grundstuecke, Inseln, Freistaedte, Wolken.

    EINE Tabelle, unterschieden ueber die Spalte ``art``. Die Kinder einer Gruppe werden
    MITGEZAEHLT und nicht weggelassen: Sie tragen Eigentuemer und Mitbauer vollstaendig,
    und eine Liste, die sie verschweigt, zeigt ein Grundstueck aus acht Feldern als eine
    Zeile - der Betreiber sucht dann die sieben anderen.

    ``x`` und ``z`` sind NULL-faehig: Eine Gruppe hat keine Rasterzelle, ihr Bereich ergibt
    sich aus den Kindern. Darum kommen sie als ``None`` heraus und nicht als 0/0 - eine
    Null ist eine Stelle, ein Fehlen ist keine.
    """
    bedingungen = ["1 = 1"]
    werte: dict[str, Any] = {}
    if welt:
        bedingungen.append("welt = :welt")
        werte["welt"] = welt
    if eigentuemer:
        bedingungen.append("eigentuemer = :eigentuemer")
        werte["eigentuemer"] = eigentuemer

    zeilen = fdb.frage(
        server,
        "SELECT id, welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, "
        "zustand, name, kasse FROM parzellen WHERE " + " AND ".join(bedingungen)
        # Nach der Nummer und nicht nach welt/x/z: Die Nummer ist eindeutig, x und z sind
        # NULL-faehig, und MariaDB sortiert NULL je Richtung anders an das Ende.
        + " ORDER BY welt, art, id",
        werte, grenze=grenze)

    bekannt = namen(server)
    raus: list[dict[str, Any]] = []
    for zeile in zeilen:
        kennung = str(zeile.get("eigentuemer") or "")
        raus.append({
            "id": zeile.get("id"),
            "welt": zeile.get("welt"),
            "x": zeile.get("x"),
            "z": zeile.get("z"),
            "stelle": (str(zeile.get("x")) + "/" + str(zeile.get("z"))
                       if zeile.get("x") is not None and zeile.get("z") is not None
                       else ""),
            "eigentuemer": kennung,
            "eigentuemer_name": beschriftung(kennung, bekannt),
            "preis": zeile.get("preis"),
            "erworben_am": zeile.get("erworben_am"),
            "art": art_wort(zeile.get("art")),
            "art_roh": str(zeile.get("art") or ""),
            "eltern_id": zeile.get("eltern_id"),
            "gehoert_zu_gruppe": zeile.get("eltern_id") is not None,
            "zustand": zustand_wort(zeile.get("zustand")),
            "name": zeile.get("name") or "",
            "kasse": zeile.get("kasse") or "",
        })
    return raus


def konten(server: Server, *, grenze: int = GRENZE) -> list[dict[str, Any]]:
    """Alle Konten mit Stand - Mark und Goldmark, je Kennung eine Zeile.

    ZWEI PAARE UND KEINE WAEHRUNGSSPALTE: Das Plugin fuehrt Mark in ``kassen``/
    ``buchungen`` und Goldmark in ``goldkassen``/``goldbuchungen``. Die tragende Zusage
    "Summe aller Buchungen = Stand" gilt JE WAEHRUNG und nie ueber beide zusammen, darum
    werden sie hier zusammengestellt und nicht addiert.

    Ein Konto kann in nur einer der beiden Tabellen stehen; dann fehlt der andere Stand,
    und er wird als ``None`` gezeigt und nicht als 0. Ein Konto mit 0 Goldmark und gar
    kein Goldkonto sind zwei verschiedene Dinge: Das erste ist eroeffnet, das zweite nicht.
    """
    mark = fdb.frage(server, "SELECT spieler, mark FROM kassen ORDER BY mark DESC",
                     grenze=grenze)
    gold = fdb.frage(server,
                     "SELECT spieler, goldmark FROM goldkassen ORDER BY goldmark DESC",
                     grenze=grenze)

    staedte = _stadtkassen(server)
    bekannt = namen(server)

    nach_kennung: dict[str, dict[str, Any]] = {}
    for zeile in mark:
        kennung = str(zeile.get("spieler") or "")
        nach_kennung[kennung] = {"kennung": kennung, "mark": zeile.get("mark"),
                                 "goldmark": None}
    for zeile in gold:
        kennung = str(zeile.get("spieler") or "")
        eintrag = nach_kennung.setdefault(
            kennung, {"kennung": kennung, "mark": None, "goldmark": None})
        eintrag["goldmark"] = zeile.get("goldmark")

    raus = []
    for kennung, eintrag in nach_kennung.items():
        ist_stadt = kennung.strip().lower() in staedte
        raus.append({
            **eintrag,
            # Eine Stadtkasse wird BENANNT und nicht mit einem Kuerzel beschriftet: Sonst
            # steht sie als Spieler in der Liste, den niemand kennt.
            "name": ("Stadtkasse" if ist_stadt else beschriftung(kennung, bekannt)),
            "ist_stadtkasse": ist_stadt,
        })
    # Nach dem Markstand, absteigend - und bei Gleichstand nach der Kennung, damit die
    # Reihenfolge zwischen zwei Aufrufen dieselbe ist. None zaehlt als kleinster Wert.
    raus.sort(key=lambda e: (-(e["mark"] or 0), e["kennung"]))
    return raus[:grenze]


def verlauf(server: Server, kennung: str, *, gold: bool = False,
            grenze: int = GRENZE) -> list[dict[str, Any]]:
    """Die Buchungen eines Kontos, neueste zuerst.

    ``ORDER BY zeitpunkt DESC, id DESC`` und NICHT nur nach dem Zeitpunkt: Der Schluessel
    heisst ``nach_spieler (spieler, zeitpunkt, id)``, und zwei Buchungen im selben
    Millisekundenschritt kaemen sonst in beliebiger Reihenfolge - bei einem Kauf samt
    Gebuehr sieht das aus, als waere die Gebuehr vor dem Kauf gebucht worden.

    ``betrag`` ist VORZEICHENBEHAFTET: Eine Ausgabe steht negativ im Buch. Hier wird
    darum nichts umgerechnet und nichts gefiltert - die Zahl geht so an die Seite, wie sie
    dasteht.
    """
    tabelle = "goldbuchungen" if gold else "buchungen"
    zeilen = fdb.frage(
        server,
        "SELECT id, zeitpunkt, grund, betrag, stand_nachher FROM " + tabelle
        + " WHERE spieler = :spieler ORDER BY zeitpunkt DESC, id DESC",
        {"spieler": kennung}, grenze=grenze)
    return zeilen


def zusammenfassung(server: Server) -> dict[str, Any]:
    """Was die Uebersicht zeigt - wenige Zahlen, eine Abfrage je Zahl.

    ``COUNT(*)`` je Art und nicht eine Liste, die der Manager zaehlt: Die Tabelle kann
    zehntausende Zeilen haben, und die Uebersicht braucht nur die Zahlen.
    """
    nach_art = fdb.frage(
        server,
        "SELECT art, COUNT(*) AS zahl FROM parzellen GROUP BY art",
        grenze=50)
    konten_zahl = fdb.frage(server, "SELECT COUNT(*) AS zahl FROM kassen", grenze=1)
    gold_zahl = fdb.frage(server, "SELECT COUNT(*) AS zahl FROM goldkassen", grenze=1)
    summe = fdb.frage(server, "SELECT SUM(mark) AS summe FROM kassen", grenze=1)

    return {
        "nach_art": [{"art": art_wort(z.get("art")), "zahl": z.get("zahl")}
                     for z in nach_art],
        "konten": (konten_zahl[0].get("zahl") if konten_zahl else 0),
        "goldkonten": (gold_zahl[0].get("zahl") if gold_zahl else 0),
        # SUM ueber eine leere Tabelle gibt NULL, nicht 0 - und None in einer Vorlage
        # schreibt "None" in die Seite.
        "mark_gesamt": ((summe[0].get("summe") if summe else None) or 0),
    }
