"""Lesender Zugang zur Freimarken-Datenbank des Plugins.

WARUM EINE ZWEITE VERBINDUNG
Die Spieldaten - wem gehoert was, wie viel steht auf welchem Konto - schreibt
ausschliesslich das Plugin. Der Manager liest sie, und er aendert sie nie direkt: Plan
12a.2 Regel 2 sagt "der Manager liest direkt, aendert aber nur ueber das Plugin". Eine
Zeile, die hier entstuende, stuende in keinem Buch und braeche die tragende Zusage der
Kasse ("fuer ein eroeffnetes Konto ist die Summe aller Buchungen sein Stand").

KEINE MODELLE, NUR ABFRAGEN
Es gibt BEWUSST keine ORM-Modelle fuer die 21 Plugin-Tabellen. ``init_db`` ruft bei jedem
Start ``Base.metadata.create_all(bind=engine)`` (app/db/init_db.py), und alles, was an
``Base`` haengt, versucht der Manager damit anzulegen. Ein Modell fuer ``parzellen`` waere
der Weg, auf dem der Manager eines Tages in die fremde Datenbank schreibt - und zwar beim
Start, ohne dass es jemand verlangt hat. Darum nur ``text()``-Abfragen.

DAS KENNWORT BLEIBT, WO ES IST
Es wird nicht im Manager hinterlegt. Der Plan sagt den Grund: "Das Kennwort steht an
genau einer Stelle, in datenbank.properties beim Plugin, und muss nicht in einer zweiten
Anwendung liegen" (docs/citybuild_plan.md). Gelesen wird es aus
``plugins/Freimarken/config.yml`` des Servers - derselben Datei, die der Manager fuer das
Dashboard ohnehin liest. Es wird NIE protokolliert, NIE angezeigt und NIE in eine
Fehlermeldung gesetzt; ``ohne_geheimnis`` schneidet es aus jeder Adresse heraus, genau wie
``FreimarkenPlugin.ohneGeheimnis`` es auf der Plugin-Seite tut.

DIE ENGINE ENTSTEHT BEIM GEBRAUCH
Nicht auf Modulebene wie ``app/db/session.py``. Jene liest ihre Einstellungen EINMAL beim
Import, und genau deshalb muss ``tests/conftest.py`` fuenf Module neu laden. Hier haengt
die Verbindung an den Zugangsdaten des Servers: Aendert sie der Betreiber in der
config.yml, gilt die neue beim naechsten Aufruf - ohne Neustart des Managers.

DIE FRISTEN SIND NICHT GERATEN
Das Plugin spricht dieselbe Datenbank mit ``connectTimeout`` 3000 ms und
``socketTimeout`` 5000 ms an (Datenbank.java). Dieselben Zahlen stehen hier, denn ein
Zeitlimit ist eine Eigenschaft der Strecke und nicht des Programms. Ohne sie blockiert ein
tot gestellter Rechner den Arbeitsfaden von uvicorn, nicht nur die Anfrage: Die Routen
sind gewoehnliche ``def``-Funktionen.
"""
from __future__ import annotations

import re
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from app.models.server import Server

# Dieselben Fristen, mit denen das Plugin dieselbe Datenbank anspricht
# (Datenbank.java: AUFBAUFRIST_MS 3000, ANTWORTFRIST_MS 5000).
AUFBAUFRIST = 3
ANTWORTFRIST = 5

# Eine untaetige Verbindung schliesst MariaDB nach einigen Stunden - das sagt der
# Kommentar in Datenbank.verbindung() ausgeschrieben. Ohne pool_pre_ping liefert der erste
# Zugriff nach der Nacht einen Fehler statt Daten, und zwar genau dann, wenn ein Mensch
# die Seite oeffnet. Das Erneuern nach einer halben Stunde liegt deutlich unter der
# ueblichen wait_timeout von acht Stunden.
ERNEUERN_NACH = 1800

PFAD_CONFIG = "plugins/Freimarken/config.yml"

# jdbc:mariadb://host:port/schema - mit optionalem Anhang hinter einem Fragezeichen.
MUSTER_URL = re.compile(
    r"^jdbc:(?:mariadb|mysql)://(?P<host>[^:/?]+)(?::(?P<port>\d+))?/(?P<schema>[^?]+)")


class FreimarkenDbFehler(Exception):
    """Der Text geht an den Nutzer - also deutsch, ohne Innereien und ohne Geheimnis."""


@dataclass(frozen=True)
class Zugang:
    """Was gebraucht wird, um sich zu verbinden. Das Kennwort steht hier nur im Speicher.

    ``__repr__`` wird mit abgeschaltet: Ein dataclass-repr nennt jedes Feld, und dieses
    Objekt landet sonst ueber eine Ausnahme, eine Protokollzeile oder einen Debugger im
    Klartext irgendwo, wo es nicht hingehoert.
    """

    host: str
    port: int
    schema: str
    benutzer: str
    passwort: str

    def __repr__(self) -> str:
        return f"Zugang({self.benutzer}@{self.host}:{self.port}/{self.schema})"

    def __str__(self) -> str:
        return self.__repr__()

    @property
    def schluessel(self) -> tuple[str, int, str, str]:
        """Woran die Engine haengt - OHNE das Kennwort.

        Es steht mit im Vergleich, aber nicht im Schluessel: Sonst liegt es als
        Teil eines dict-Schluessels im Speicher und in jedem Speicherabbild.
        """
        return self.host, self.port, self.schema, self.benutzer


def ohne_geheimnis(wert: str) -> str:
    """Eine Adresse ohne alles hinter dem Fragezeichen - und ohne Kennwort.

    Dasselbe Verfahren wie ``FreimarkenPlugin.ohneGeheimnis``, und mit derselben
    Begruendung: "denn das Protokoll liest spaeter jemand, der es nicht lesen soll".
    """
    text_wert = str(wert or "")
    return text_wert.split("?", 1)[0]


def lies_zugang(server: Server) -> Zugang:
    """Die Zugangsdaten aus ``plugins/Freimarken/config.yml`` des Servers.

    Gelesen wird der Schluessel ``datenbank.url`` und NICHT der erste, der passt: In
    ``data/freimarken/datenbank.properties`` stehen daneben ``url.test`` und
    ``root.passwort``, und das Testschema leeren die Java-Proben vor jedem Lauf. Wer dort
    hineinliest, zeigt eine leere Anlage - oder leert sie.
    """
    from app.services import freimarken_service as fs

    try:
        roh = fs.lies_datei(server, PFAD_CONFIG)
    except Exception as fehler:  # noqa: BLE001 - jeder Grund fuehrt zur gleichen Antwort
        raise FreimarkenDbFehler(
            "Die Datei plugins/Freimarken/config.yml dieses Servers ist nicht lesbar - "
            "ohne sie gibt es keine Zugangsdaten.") from fehler

    import yaml

    try:
        geladen = yaml.safe_load(roh) or {}
    except yaml.YAMLError as fehler:
        raise FreimarkenDbFehler(
            "Die config.yml dieses Servers ist zurzeit kein gueltiges YAML. Solange das "
            "so ist, lassen sich die Spieldaten nicht lesen.") from fehler

    abschnitt = (geladen or {}).get("datenbank") or {}
    if not isinstance(abschnitt, dict):
        raise FreimarkenDbFehler(
            "In der config.yml dieses Servers fehlt der Abschnitt 'datenbank'.")

    url = str(abschnitt.get("url") or "").strip()
    benutzer = str(abschnitt.get("benutzer") or "").strip()
    passwort = str(abschnitt.get("passwort") or "")
    if not url:
        raise FreimarkenDbFehler(
            "Dieser Server speichert nichts: datenbank.url in der config.yml ist leer. "
            "Parzellen und Guthaben gelten dann nur bis zum naechsten Neustart, und es "
            "gibt keine Spieldaten zu zeigen.")

    treffer = MUSTER_URL.match(url)
    if treffer is None:
        raise FreimarkenDbFehler(
            "datenbank.url ist keine Adresse der Form "
            "jdbc:mariadb://rechner:3306/schema (gelesen: " + ohne_geheimnis(url) + ").")
    if not benutzer:
        raise FreimarkenDbFehler(
            "In der config.yml dieses Servers fehlt datenbank.benutzer.")

    return Zugang(host=treffer.group("host"),
                  port=int(treffer.group("port") or 3306),
                  schema=treffer.group("schema"),
                  benutzer=benutzer,
                  passwort=passwort)


# Je Zugang EINE Engine, und ein Schloss darum: Zwei Anfragen zugleich wuerden sonst zwei
# Pools fuer dasselbe Ziel aufbauen, und der eine schliesst nie.
_ENGINES: dict[tuple[str, int, str, str], tuple[str, Engine]] = {}
_SCHLOSS = threading.Lock()


def _engine(zugang: Zugang) -> Engine:
    """Die Verbindung zu diesem Zugang - einmal gebaut und dann gehalten."""
    import hashlib

    # Das Kennwort geht als PRUEFSUMME in den Vergleich, nicht als Wert: So faellt eine
    # Aenderung auf, ohne dass es in einem Zwischenspeicher liegt.
    abdruck = hashlib.sha256(zugang.passwort.encode("utf-8")).hexdigest()
    with _SCHLOSS:
        vorhanden = _ENGINES.get(zugang.schluessel)
        if vorhanden is not None:
            alter_abdruck, engine = vorhanden
            if alter_abdruck == abdruck:
                return engine
            # Das Kennwort hat sich geaendert - die alte Verbindung weg, sonst arbeitet
            # der Pool mit Zugangsdaten weiter, die es nicht mehr gibt.
            engine.dispose()

        from urllib.parse import quote

        adresse = ("mysql+pymysql://" + quote(zugang.benutzer, safe="") + ":"
                   + quote(zugang.passwort, safe="") + "@" + zugang.host + ":"
                   + str(zugang.port) + "/" + zugang.schema + "?charset=utf8mb4")
        try:
            engine = create_engine(
                adresse,
                pool_pre_ping=True,
                pool_recycle=ERNEUERN_NACH,
                pool_size=2,
                max_overflow=2,
                connect_args={"connect_timeout": AUFBAUFRIST,
                              "read_timeout": ANTWORTFRIST,
                              "write_timeout": ANTWORTFRIST},
                future=True,
            )
        except ModuleNotFoundError as fehler:
            raise FreimarkenDbFehler(
                "Der Treiber pymysql fehlt. Er steht in requirements.txt; nach einem "
                "'pip install -r requirements.txt' im .venv des Managers geht es.",
            ) from fehler
        _ENGINES[zugang.schluessel] = (abdruck, engine)
        return engine


def verwirf_alles() -> None:
    """Alle Verbindungen schliessen - fuer den Abbau und fuer die Proben."""
    with _SCHLOSS:
        for _, engine in _ENGINES.values():
            engine.dispose()
        _ENGINES.clear()


def frage(server: Server, sql: str, werte: dict[str, Any] | None = None,
          *, grenze: int = 500) -> list[dict[str, Any]]:
    """Eine lesende Abfrage gegen die Plugin-Datenbank dieses Servers.

    NUR LESEN, und das wird geprueft: Eine Abfrage, die nicht mit SELECT beginnt, wird
    abgewiesen, bevor sie die Verbindung erreicht. Der Grund ist nicht Misstrauen gegen
    den naechsten Leser, sondern die Bauart dieses Moduls: Es gibt hier keinen
    Transaktionsrahmen und keinen Platz fuer eine Gegenbuchung. Wer schreiben will, nimmt
    den Weg ueber das Plugin (Plan 12a.2 Regel 2).

    Die ZEITSTEMPEL kommen als naive ``datetime`` zurueck - pymysql kennzeichnet sie
    nicht. Das Plugin schreibt sie in UTC und begruendet das bei
    ``Datenbank.utcKalender()``; hier werden sie darum als UTC gekennzeichnet. Ohne das
    zeigt der Manager im Winter eine, im Sommer zwei Stunden falsch, denn er rechnet
    sonst durchgaengig mit ``datetime.now(timezone.utc)``.
    """
    gesaeubert = sql.strip()
    if not gesaeubert.lower().startswith("select"):
        raise FreimarkenDbFehler(
            "Dieser Zugang liest nur. Eine Aenderung an den Spieldaten laeuft ueber das "
            "Plugin und nicht ueber die Datenbank.")

    zugang = lies_zugang(server)
    engine = _engine(zugang)
    try:
        with engine.connect() as verbindung:
            ergebnis = verbindung.execute(text(gesaeubert), werte or {})
            spalten = list(ergebnis.keys())
            zeilen = ergebnis.fetchmany(grenze)
    except FreimarkenDbFehler:
        raise
    except Exception as fehler:  # noqa: BLE001 - jede Stoerung wird ein deutscher Satz
        raise FreimarkenDbFehler(_satz_zum_fehler(fehler, zugang)) from fehler

    return [{name: _als_utc(wert) for name, wert in zip(spalten, zeile)}
            for zeile in zeilen]


def _als_utc(wert: Any) -> Any:
    """Einen nicht gekennzeichneten Zeitpunkt als UTC kennzeichnen.

    Nur den NICHT gekennzeichneten: Ein Wert, der schon eine Zone traegt, wird nicht
    verschoben. ``date``-Werte bleiben unberuehrt - die sechs Tagesspalten des Plugins
    stehen in ORTSZEIT und sind keine Zeitpunkte.
    """
    if isinstance(wert, datetime) and wert.tzinfo is None:
        return wert.replace(tzinfo=timezone.utc)
    return wert


def _satz_zum_fehler(fehler: Exception, zugang: Zugang) -> str:
    """Aus einer Treiberausnahme ein deutscher Satz - ohne das Kennwort.

    Die Meldung des Treibers enthaelt die Adresse, und in einer Adresse kann ein Kennwort
    stecken. Darum wird sie NICHT durchgereicht, sondern nur ihre Art benannt. Dieselbe
    Haltung wie ``ai_provider_service._http_fehler``: Die Lage wird auf einen Satz
    abgebildet, den ein Mensch lesen kann.
    """
    art = type(fehler).__name__
    kurz = str(fehler).splitlines()[0] if str(fehler) else ""
    ziel = zugang.host + ":" + str(zugang.port) + "/" + zugang.schema

    if "Access denied" in kurz or "1045" in kurz:
        return ("Die Freimarken-Datenbank (" + ziel + ") weist die Zugangsdaten aus der "
                "config.yml ab. Benutzer oder Kennwort stimmen dort nicht mehr.")
    if "Unknown database" in kurz or "1049" in kurz:
        return ("Das Schema " + zugang.schema + " gibt es auf " + zugang.host + " nicht. "
                "Angelegt wird es von freimarken/skripte/mariadb_einrichten.ps1.")
    if "doesn't exist" in kurz or "1146" in kurz:
        return ("In " + ziel + " fehlt eine Tabelle, die diese Seite braucht. Das Plugin "
                "legt sie beim ersten Start an - hat dieser Server schon einmal mit "
                "Freimarken laufen?")
    if "timed out" in kurz.lower() or "timeout" in art.lower():
        return ("Die Freimarken-Datenbank (" + ziel + ") antwortet nicht innerhalb von "
                + str(ANTWORTFRIST) + " Sekunden.")
    return ("Die Freimarken-Datenbank (" + ziel + ") ist nicht erreichbar.")


def pruefe_verbindung(server: Server) -> tuple[bool, str]:
    """Die billigste moegliche Abfrage - fuer einen Knopf "Verbindung pruefen".

    Dasselbe Muster wie ``ai_service.pruefe_verbindung``: Rueckgabe statt Ausnahme, damit
    die Oberflaeche beide Faelle gleich behandelt und die Meldung anzeigen kann.
    """
    try:
        frage(server, "SELECT 1 AS eins", grenze=1)
    except FreimarkenDbFehler as fehler:
        return False, str(fehler)
    zugang = lies_zugang(server)
    return True, ("Verbunden mit " + zugang.schema + " auf " + zugang.host + ":"
                  + str(zugang.port) + ".")
