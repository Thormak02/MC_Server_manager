"""Die Moderation des Plugins sichtbar machen: dauerhafte Strafen, Meldungen, Rollen.

NUR LESEN, und zwar ausschliesslich ueber ``freimarken_db_service.frage`` - derselbe Weg
wie bei ``freimarken_spieldaten_service``. Was er mitbringt, ist hier wichtiger als
dort:

* Jede Abfrage, die nicht mit ``SELECT`` beginnt, wird abgewiesen, bevor sie die
  Verbindung erreicht.
* Es entsteht KEIN ORM-Modell fuer ``strafen``, ``meldungen`` oder ``team``. Das ist der
  Riegel dieses Moduls: ``init_db`` ruft bei jedem Start des Managers
  ``Base.metadata.create_all``, und ein Modell wuerde den Manager dazu bringen, in der
  FREMDEN Datenbank des Plugins Tabellen anzulegen - gegen ein Schema, das das Plugin
  selbst fuehrt und versioniert.
* Es gibt hier kein zweites ``create_engine``. Mit ihm fielen der SELECT-Riegel, die
  Zeitgrenzen, ``pool_pre_ping`` und die Maskierung des Kennworts in den Fehlersaetzen
  weg - vier Zusagen, die niemand einzeln wieder aufbaut.

WARUM DIESE SEITE NICHTS AENDERT
Plan 12c.3 verlangt, dass dauerhafte Banne "zusaetzlich im Manager sichtbar" sind -
sichtbar, nicht aenderbar. Eine Strafe aufzuheben ist eine Moderationshandlung mit
Ansage, Protokollzeile und Rechteriegel; sie laeuft ueber ``/strafe aufheben`` am
laufenden Server (Plan 12a.2 Regel 2). Ein Knopf hier daneben waere ein zweiter Weg zur
selben Tat - und der eine, der an der Ansage, am Recht und am Protokoll des Plugins
vorbeigeht.

DIE ZEIT KOMMT VON HIER UND NICHT VON DER DATENBANK
Ob eine befristete Strafe JETZT noch gilt, entscheidet ein Vergleich mit der Uhr. Dafuer
steht hier ``:jetzt`` als Parameter und nicht ``NOW()``: Jenes waere die Zeit des
Datenbankservers in SEINER Zone, und die Spalten des Plugins stehen in UTC
(``Datenbank.utcKalender()``). Uebergeben wird ein naiver UTC-Zeitpunkt, denn genau so
liegen die Spalten da.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from app.models.server import Server
from app.services import freimarken_db_service as fdb
from app.services import freimarken_spieldaten_service as spieldaten

# Hoechstens so viele Zeilen je Tabelle - dieselbe Zahl und derselbe Grund wie bei den
# Spieldaten: Eine Seite mit zehntausend Zeilen liest ohnehin niemand.
GRENZE = 200

# Die Arten einer Strafe. Gespeichert wird ``Strafart.name()``, also GROSSSCHRIFT -
# gemessen an MariaStrafen.java:142.
#
# Eine unbekannte Art wird BENANNT und nicht geraten: Eine vierte Art des Plugins
# erscheint dann mit ihrem Rohwert, und man sieht, dass hier etwas nachzutragen ist.
# Dieselbe Haltung wie bei ``freimarken_spieldaten_service.ARTEN``.
STRAFARTEN = {
    "STUMM": "Stummschaltung",
    "RAUSWURF": "Rauswurf",
    "BANN": "Sperre",
}

# Die Staende einer Meldung - ``Meldestand.name()``, gemessen an MariaMeldungen.java:125.
MELDESTAENDE = {
    "OFFEN": "offen",
    "IN_ARBEIT": "in Arbeit",
    "ABGESCHLOSSEN": "abgeschlossen",
    "ABGEWIESEN": "abgewiesen",
}

# Die Staende, die ARBEIT bedeuten. Sie stehen hier als Menge und nicht als Bedingung im
# SQL: Eine Liste, die man an zwei Stellen tippt, laeuft auseinander.
OFFENE_STAENDE = ("OFFEN", "IN_ARBEIT")

# KEINE Tafel fuer die ROLLEN, und das ist Absicht: Welche Rollen es gibt, steht in der
# config.yml des Servers (``team.rollen``) und kann morgen anders sein. Eine Tafel hier
# wuerde eine eigene Rolle als "unbekannt" ausweisen, obwohl sie richtig ist.


def strafart_wort(roh: Any) -> str:
    schluessel = str(roh or "").strip().upper()
    bekannt = STRAFARTEN.get(schluessel)
    if bekannt:
        return bekannt
    return "unbekannte Art (" + (schluessel or "leer") + ")"


def meldestand_wort(roh: Any) -> str:
    schluessel = str(roh or "").strip().upper()
    bekannt = MELDESTAENDE.get(schluessel)
    if bekannt:
        return bekannt
    return "unbekannter Stand (" + (schluessel or "leer") + ")"


def _offene_staende() -> tuple[str, dict[str, Any]]:
    """Das ``IN (...)``-Stueck und seine Werte - aus der EINEN Liste gerechnet.

    Nicht ``IN :staende`` mit einem Tupel: Das braucht in SQLAlchemy ein ausdehnendes
    Bindeglied (``bindparam(..., expanding=True)``), und ``fdb.frage`` nimmt nur ein
    gewoehnliches Woerterbuch - der Treiber setzte das Tupel sonst als EINEN Wert ein und
    fand nie etwas. Und nicht die Namen zweimal getippt: Eine Liste, die an zwei Stellen
    steht, laeuft auseinander, sobald das Plugin einen fuenften Stand bekommt.
    """
    werte = {"stand" + str(nummer): wert
             for nummer, wert in enumerate(OFFENE_STAENDE)}
    stueck = "(" + ", ".join(":" + name for name in werte) + ")"
    return stueck, werte


def _naives_jetzt(jetzt: datetime | None) -> datetime:
    """Der Zeitpunkt, mit dem die Datenbank vergleichen kann - naiv und in UTC."""
    gewaehlt = jetzt or datetime.now(timezone.utc)
    if gewaehlt.tzinfo is not None:
        gewaehlt = gewaehlt.astimezone(timezone.utc).replace(tzinfo=None)
    return gewaehlt


def dauerhafte_strafen(server: Server, *, grenze: int = GRENZE,
                       bekannt: dict[str, str] | None = None) -> list[dict[str, Any]]:
    """Die geltenden Strafen OHNE Ende - das, was Plan 12c.3 im Manager sehen will.

    ``laeuft_ab_am IS NULL`` heisst unbegrenzt (so steht es im Schemaschritt 41), und
    ``aufgehoben_am IS NULL`` heisst: sie gilt noch. Beide Bedingungen gehoeren
    zusammen - ohne die zweite stuenden aufgehobene Banne als laufende da, und das ist
    genau die Auskunft, nach der jemand hier sucht.
    """
    zeilen = fdb.frage(
        server,
        "SELECT nummer, betroffener, art, grund, von_wem, verhaengt_am "
        "FROM strafen "
        "WHERE laeuft_ab_am IS NULL AND aufgehoben_am IS NULL "
        "ORDER BY verhaengt_am DESC, nummer DESC",
        grenze=grenze)

    namen = bekannt if bekannt is not None else spieldaten.namen(server)
    raus: list[dict[str, Any]] = []
    for zeile in zeilen:
        betroffener = str(zeile.get("betroffener") or "")
        vonWem = str(zeile.get("von_wem") or "")
        raus.append({
            "nummer": zeile.get("nummer"),
            "betroffener": betroffener,
            "betroffener_name": spieldaten.beschriftung(betroffener, namen),
            "art": strafart_wort(zeile.get("art")),
            "grund": zeile.get("grund"),
            "von_wem": vonWem,
            # Ohne Kennung war es die KONSOLE - das Plugin schreibt dort NULL. Ein
            # achtstelliges Kuerzel von "" waere eine leere Zelle, und die saehe wie ein
            # Fehler aus.
            "von_wem_name": (spieldaten.beschriftung(vonWem, namen) if vonWem
                             else "Konsole"),
            "verhaengt_am": zeile.get("verhaengt_am"),
        })
    return raus


def meldungen(server: Server, *, grenze: int = GRENZE,
              bekannt: dict[str, str] | None = None) -> list[dict[str, Any]]:
    """Die Meldungen, an denen noch Arbeit ist - offen und in Arbeit.

    Mit dem BEARBEITER, denn das ist die Frage dieser Seite: Eine Meldung in Arbeit, an
    der seit Tagen niemand war, ist der Fund. Das Plugin nennt diese Luecke selbst
    (``Meldestand.IN_ARBEIT``): Ein Moderator, der aufhoert, laesst sie in Arbeit stehen,
    und einen Weg zurueck gibt es nicht.
    """
    stueck, werte = _offene_staende()
    zeilen = fdb.frage(
        server,
        "SELECT nummer, melder, betroffener, grund, welt, x, y, z, gemeldet_am, "
        "stand, bearbeiter FROM meldungen "
        "WHERE stand IN " + stueck + " "
        "ORDER BY gemeldet_am ASC, nummer ASC",
        werte,
        grenze=grenze)

    namen = bekannt if bekannt is not None else spieldaten.namen(server)
    raus: list[dict[str, Any]] = []
    for zeile in zeilen:
        melder = str(zeile.get("melder") or "")
        betroffener = str(zeile.get("betroffener") or "")
        bearbeiter = str(zeile.get("bearbeiter") or "")
        raus.append({
            "nummer": zeile.get("nummer"),
            "melder": melder,
            "melder_name": spieldaten.beschriftung(melder, namen),
            "betroffener": betroffener,
            "betroffener_name": spieldaten.beschriftung(betroffener, namen),
            "grund": zeile.get("grund"),
            "welt": zeile.get("welt"),
            # Der Tatort ist der Ort des MELDERS - was er gesehen hat, hat er von dort
            # gesehen. Darum steht er in dieser Zeile und nicht der Ort des Betroffenen.
            "stelle": (str(zeile.get("x")) + "/" + str(zeile.get("y")) + "/"
                       + str(zeile.get("z"))),
            "gemeldet_am": zeile.get("gemeldet_am"),
            "stand": meldestand_wort(zeile.get("stand")),
            "bearbeiter": bearbeiter,
            "bearbeiter_name": (spieldaten.beschriftung(bearbeiter, namen) if bearbeiter
                                else ""),
        })
    return raus


def teamrollen(server: Server, *, grenze: int = GRENZE,
               bekannt: dict[str, str] | None = None) -> list[dict[str, Any]]:
    """Wer welche Rolle hat, seit wann und von wem.

    Eine Zeile je Spieler UND Rolle - so liegt die Tabelle da, und ein Mensch kann
    mehrere Rollen haben (seine Rechte sind die Vereinigung). Zusammengefasst wird
    nach KENNUNG und nicht nach Namen: Derselbe Mensch kann sonst unter zwei Kennungen
    stehen, und eine Liste nach Namen verschmolze sie.
    """
    zeilen = fdb.frage(
        server,
        "SELECT spieler, rolle, vergeben_am, vergeber FROM team "
        "ORDER BY spieler, rolle",
        grenze=grenze)

    namen = bekannt if bekannt is not None else spieldaten.namen(server)
    nach_spieler: dict[str, dict[str, Any]] = {}
    for zeile in zeilen:
        kennung = str(zeile.get("spieler") or "")
        eintrag = nach_spieler.setdefault(kennung, {
            "kennung": kennung,
            "name": spieldaten.beschriftung(kennung, namen),
            "rollen": [],
        })
        vergeber = str(zeile.get("vergeber") or "")
        eintrag["rollen"].append({
            # Die Rolle ROH und ohne Tafel: Welche es gibt, steht in der config.yml.
            "rolle": str(zeile.get("rolle") or ""),
            "seit": zeile.get("vergeben_am"),
            "vergeber": vergeber,
            "vergeber_name": (spieldaten.beschriftung(vergeber, namen) if vergeber
                              else "Konsole"),
        })
    return sorted(nach_spieler.values(), key=lambda eintrag: eintrag["name"].lower())


def zahlen(server: Server, *, jetzt: datetime | None = None) -> dict[str, int]:
    """Die vier Zahlen oben auf der Seite - in zwei Abfragen.

    GETRENNT von den Listen, weil die Listen bei 200 Zeilen abbrechen: Eine Zahl, die
    aus einer abgeschnittenen Liste gerechnet waere, sagte "200 Strafen" und bliebe
    danach fuer immer bei 200. Darum zaehlt die Datenbank.
    """
    grenzzeit = _naives_jetzt(jetzt)
    straf = fdb.frage(
        server,
        "SELECT "
        "SUM(CASE WHEN aufgehoben_am IS NULL AND laeuft_ab_am IS NULL "
        "         THEN 1 ELSE 0 END) AS dauerhaft, "
        "SUM(CASE WHEN aufgehoben_am IS NULL AND laeuft_ab_am > :jetzt "
        "         THEN 1 ELSE 0 END) AS befristet, "
        "COUNT(*) AS gesamt "
        "FROM strafen",
        {"jetzt": grenzzeit},
        grenze=1)
    stueck, werte = _offene_staende()
    meld = fdb.frage(
        server,
        "SELECT "
        "SUM(CASE WHEN stand IN " + stueck + " THEN 1 ELSE 0 END) AS offen, "
        "COUNT(*) AS gesamt "
        "FROM meldungen",
        werte,
        grenze=1)

    erste_straf = straf[0] if straf else {}
    erste_meld = meld[0] if meld else {}
    return {
        "strafen_dauerhaft": int(erste_straf.get("dauerhaft") or 0),
        "strafen_befristet": int(erste_straf.get("befristet") or 0),
        "strafen_gesamt": int(erste_straf.get("gesamt") or 0),
        "meldungen_offen": int(erste_meld.get("offen") or 0),
        "meldungen_gesamt": int(erste_meld.get("gesamt") or 0),
    }


def stand(server: Server, *, jetzt: datetime | None = None,
          grenze: int = GRENZE) -> dict[str, Any]:
    """Alles, was die Seite braucht - in EINEM Aufruf und mit EINER Namensauflesung.

    Die Namen werden einmal geholt und an alle drei Listen weitergegeben: ``namen()``
    liest vier Dateien des Servers, und dreimal dasselbe zu lesen waere dreimal derselbe
    Plattenzugriff fuer dasselbe Ergebnis.

    Ein Lesefehler wird NICHT gefangen - er gehoert dem Router, der daraus eine Meldung
    auf der Seite macht. Eine halbe Antwort mit leeren Listen saehe aus wie "hier ist
    nichts passiert", und das ist die eine Auskunft, die diese Seite nie geben darf.
    """
    bekannt = spieldaten.namen(server)
    return {
        "zahlen": zahlen(server, jetzt=jetzt),
        "strafen": dauerhafte_strafen(server, grenze=grenze, bekannt=bekannt),
        "meldungen": meldungen(server, grenze=grenze, bekannt=bekannt),
        "team": teamrollen(server, grenze=grenze, bekannt=bekannt),
        "grenze": grenze,
    }
