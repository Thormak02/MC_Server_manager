"""Die Moderationsseite: was sie zeigt, was sie NICHT kann, und zwei Tafeln.

DER TRAGENDE RIEGEL IST, DASS HIER NICHTS GESCHRIEBEN WIRD
Das Plugin fuehrt das Schema seiner Datenbank selbst und versioniert es in
Schemaschritten. Der Manager ruft bei jedem Start ``init_db`` und damit
``Base.metadata.create_all`` - ein ORM-Modell fuer ``strafen``, ``meldungen`` oder
``team`` wuerde also dazu fuehren, dass der Manager in der FREMDEN Datenbank Tabellen
anlegt. Zwei Proben halten das am Quelltext fest, denn zur Laufzeit faellt es erst auf,
wenn der Schaden da ist.

ZWEI TAFELN GEGEN DEN PLUGIN-QUELLTEXT
Die Arten einer Strafe und die Staende einer Meldung sind ENUM-Namen im Java-Quelltext.
Kommt eine Art dazu, wird hier eine Probe rot - und zwar die, deren Name die Sache nennt.
Die ROLLEN haben bewusst keine Tafel: Sie stehen in der ``config.yml`` des Betreibers.

KEINE DATENBANK IM SPIEL
Die Proben setzen ``frage`` ab. Eine echte MariaDB waere eine Abhaengigkeit, die auf
einem fremden Rechner fehlt - und die Umwandlung, um die es geht (benennen, zusammenfassen,
Konsole erkennen), haengt nicht am Treiber.
"""
from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

JAVA = Path("freimarken")
DIENST = Path("app/services/freimarken_moderation_service.py")

CONFIG = """# Freimarken - Einstellungen
datenbank:
  url: ""
  benutzer: ""
  passwort: ""

wirtschaft:
  parzelle:
    grundpreis: 25000
"""


# --------------------------------------------------- Der Manager legt nichts an

def test_der_dienst_hat_kein_orm_modell():
    """``init_db`` ruft ``create_all`` - ein Modell hier legte Tabellen beim PLUGIN an.

    Geprueft wird der Quelltext und nicht das Verhalten: Zur Laufzeit faellt es erst auf,
    wenn der Manager gegen eine fremde Datenbank gelaufen ist. Dann steht dort eine
    Tabelle, die das Plugin nicht kennt - oder schlimmer: eine, die es kennt, mit
    anderen Spalten.
    """
    quelle = DIENST.read_text(encoding="utf-8")

    # Die AUFRUFFORMEN und nicht die Woerter: "Base.metadata.create_all" steht im
    # Dateikopf dieses Dienstes als Begruendung, und ein Muster, das sie trifft, prueft
    # seinen eigenen Kommentar. Geschaerft, nicht ausgenommen - eine ausgenommene Datei
    # waere fuer immer blind.
    for verboten in ("declarative_base(", "Column(", "__tablename__", "create_engine(",
                     "sessionmaker(", "relationship("):
        assert verboten not in quelle, (
            "Der Moderationsdienst enthaelt " + verboten + " - er darf NUR ueber "
            "freimarken_db_service.frage lesen.")

    # Und er holt sich die Werkzeuge dafuer nicht: kein sqlalchemy, nichts aus app.db.
    # Das ist die Zusage, die ein neues Modell nicht umgehen kann.
    for zeile in quelle.splitlines():
        gestrafft = zeile.strip()
        if not (gestrafft.startswith("import ") or gestrafft.startswith("from ")):
            continue
        assert "sqlalchemy" not in gestrafft, \
            "Der Dienst holt sqlalchemy herein: " + gestrafft
        assert not gestrafft.startswith("from app.db"), \
            "Der Dienst holt die Sitzung des Managers herein: " + gestrafft


def test_jede_abfrage_des_dienstes_ist_ein_select():
    """Der Riegel in ``frage`` prueft das auch - hier steht die zweite Richtung.

    ``frage`` weist eine Abfrage ab, die nicht mit SELECT beginnt; diese Probe haelt,
    dass es im Dienst gar keine andere gibt. Der Unterschied ist der Zeitpunkt: Dort
    wird es zur Laufzeit zu einem Fehlersatz, hier bleibt es gar nicht erst stehen.
    """
    quelle = DIENST.read_text(encoding="utf-8")

    # An JEDEM Aufruf von frage() steht das erste Stueck der Abfrage - und das
    # entscheidet, denn der Riegel dort prueft genau den Anfang. Ueber die Aufrufstellen
    # und nicht ueber alle Zeichenketten der Datei: Die Abfragen sind auf mehrere
    # Literale verteilt ("... stand IN " + stueck), und ein Muster ueber alle Literale
    # pruefte Wortstuecke aus der Mitte.
    aufrufe = re.findall(r"fdb\.frage\(\s*server,\s*\"([^\"]*)", quelle)
    assert len(aufrufe) >= 4, \
        "So wenige Abfragen? Dann trifft dieses Muster sie nicht: " + str(aufrufe)
    for anfang in aufrufe:
        assert anfang.upper().startswith("SELECT "), \
            "Diese Abfrage beginnt nicht mit SELECT: " + anfang[:60]

    for verboten in ("INSERT ", "UPDATE ", "DELETE ", "DROP ", "TRUNCATE ", "ALTER "):
        assert verboten not in quelle.upper(), \
            "Der Moderationsdienst enthaelt " + verboten.strip()


def test_die_seite_hat_kein_formular():
    """Sichtbar, nicht aenderbar (Plan 12c.3).

    Eine Strafe aufzuheben hat Ansage, Rechteriegel und Protokollzeile. Ein Knopf hier
    waere ein zweiter Weg zur selben Tat - und der eine, der an allen drei vorbeigeht.
    Geprueft wird der Block der Vorlage und nicht die ganze Datei: Der Einrichtungsblock
    darueber hat sein Formular zu Recht.
    """
    vorlage = Path("app/web/templates/freimarken_seite.html").read_text(
        encoding="utf-8")
    anfang = vorlage.index('{% elif inhalt.seite.quelle == "moderation" %}')
    ende = vorlage.index('{% elif inhalt.seite.quelle == "mitglieder" %}', anfang)
    block = vorlage[anfang:ende]

    assert "<form" not in block, "Die Moderationsseite darf nichts senden"
    assert "<button" not in block
    # Und sie SAGT, wo es geht: Eine Seite, die nur nichts kann, laesst den Betreiber
    # suchen.
    assert "/strafe aufheben" in block


# -------------------------------------------------- Tafeln gegen das Plugin

def test_die_strafarten_sind_die_des_plugins():
    """Gespeichert wird ``Strafart.name()`` - also GROSSSCHRIFT (MariaStrafen.java)."""
    from app.services.freimarken_moderation_service import STRAFARTEN

    quelle = (JAVA / "core/src/main/java/de/freimarken/kern/Strafart.java").read_text(
        encoding="utf-8")
    im_plugin = set(re.findall(r"^    ([A-Z_]+)\(", quelle, re.M))

    assert im_plugin, "In Strafart.java steht keine Konstante"
    assert im_plugin == set(STRAFARTEN), (
        "Die Artenliste des Managers passt nicht zum Plugin. Im Plugin: "
        + str(sorted(im_plugin)) + " | im Manager: " + str(sorted(STRAFARTEN)))
    # Und die Schreibweise stimmt mit dem, was in der Spalte steht.
    assert 'anfrage.setString(2, geprueft.art().name())' in (
        JAVA / "daten/src/main/java/de/freimarken/daten/MariaStrafen.java").read_text(
            encoding="utf-8"), \
        "Das Plugin speichert die Art anders als mit name() - die Tafel ist dann falsch"


def test_die_meldestaende_sind_die_des_plugins():
    from app.services.freimarken_moderation_service import MELDESTAENDE, OFFENE_STAENDE

    quelle = (JAVA / "core/src/main/java/de/freimarken/kern/Meldestand.java").read_text(
        encoding="utf-8")
    im_plugin = set(re.findall(r"^    ([A-Z_]+)[,;]", quelle, re.M))

    assert im_plugin, "In Meldestand.java steht keine Konstante"
    assert im_plugin == set(MELDESTAENDE), (
        "Die Staendeliste passt nicht zum Plugin. Im Plugin: " + str(sorted(im_plugin))
        + " | im Manager: " + str(sorted(MELDESTAENDE)))
    # Die zwei Staende, die ARBEIT bedeuten, sind eine Teilmenge davon - sonst sucht die
    # Seite nach einem Stand, den es nicht gibt, und findet fuer immer nichts.
    assert set(OFFENE_STAENDE) <= im_plugin


@pytest.mark.parametrize("roh,erwartet", [
    ("BANN", "Sperre"),
    ("bann", "Sperre"),
    (" STUMM ", "Stummschaltung"),
    ("VERWARNUNG", "unbekannte Art (VERWARNUNG)"),
    ("", "unbekannte Art (leer)"),
    (None, "unbekannte Art (leer)"),
])
def test_eine_unbekannte_art_wird_benannt(roh, erwartet):
    """Eine vierte Art des Plugins erscheint mit ihrem Rohwert und nicht als Sperre."""
    from app.services.freimarken_moderation_service import strafart_wort

    assert strafart_wort(roh) == erwartet


@pytest.mark.parametrize("roh,erwartet", [
    ("IN_ARBEIT", "in Arbeit"),
    ("ABGEWIESEN", "abgewiesen"),
    ("WARTET", "unbekannter Stand (WARTET)"),
])
def test_ein_unbekannter_stand_wird_benannt(roh, erwartet):
    from app.services.freimarken_moderation_service import meldestand_wort

    assert meldestand_wort(roh) == erwartet


# ------------------------------------------------------ Was der Dienst daraus macht

def _setze_frage_ab(monkeypatch, antworten):
    """``frage`` durch eine Tafel ersetzen: Anfang der Abfrage -> Zeilen."""
    from app.services import freimarken_db_service as fdb
    from app.services import freimarken_moderation_service as mod
    from app.services import freimarken_spieldaten_service as sp

    def gefaelscht(server, sql, werte=None, *, grenze=500):
        for anfang, zeilen in antworten.items():
            if " ".join(sql.split()).startswith(anfang):
                return list(zeilen)
        raise AssertionError("Unerwartete Abfrage: " + " ".join(sql.split())[:110])

    monkeypatch.setattr(fdb, "frage", gefaelscht)
    monkeypatch.setattr(mod.fdb, "frage", gefaelscht)
    monkeypatch.setattr(sp.fdb, "frage", gefaelscht)


def _server(tmp_path, *, slug="mod", port=25700):
    from app.models.server import Server

    basis = tmp_path / slug
    (basis / "plugins" / "Freimarken").mkdir(parents=True, exist_ok=True)
    return Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                  base_path=str(basis), port=port, status="stopped",
                  start_mode="command", start_command="java -jar server.jar",
                  freimarken_enabled=True)


def test_die_konsole_steht_als_konsole_und_nicht_als_kuerzel(monkeypatch, tmp_path):
    """``von_wem`` ist NULL, wenn die Konsole gestraft hat - sie hat keine Kennung.

    Ohne diese Unterscheidung stuende dort eine leere Zelle, und die sieht aus wie ein
    Fehler. Die Gegenrichtung steht in derselben Probe: Eine echte Kennung bekommt ihr
    achtstelliges Kuerzel.
    """
    from app.services.freimarken_moderation_service import dauerhafte_strafen

    moderator = "aaaaaaaa-1111-4111-8111-111111111111"
    _setze_frage_ab(monkeypatch, {
        "SELECT nummer, betroffener, art": [
            {"nummer": 7, "betroffener": "bbbbbbbb-2222-4222-8222-222222222222",
             "art": "BANN", "grund": "Grief in Altfurt", "von_wem": None,
             "verhaengt_am": datetime(2026, 11, 25, 12, 0, tzinfo=timezone.utc)},
            {"nummer": 8, "betroffener": "cccccccc-3333-4333-8333-333333333333",
             "art": "STUMM", "grund": "Beleidigung", "von_wem": moderator,
             "verhaengt_am": datetime(2026, 11, 25, 13, 0, tzinfo=timezone.utc)},
        ],
        "SELECT DISTINCT kasse": [],
    })

    zeilen = dauerhafte_strafen(_server(tmp_path), bekannt={})

    assert zeilen[0]["von_wem_name"] == "Konsole"
    assert zeilen[1]["von_wem_name"] == moderator[:8]
    assert zeilen[0]["art"] == "Sperre"
    assert zeilen[1]["art"] == "Stummschaltung"


def test_mehrere_rollen_stehen_in_EINER_zeile(monkeypatch, tmp_path):
    """Ein Mensch kann mehrere Rollen haben - seine Rechte sind die Vereinigung.

    Zusammengefasst wird nach KENNUNG und nicht nach Namen: Derselbe Mensch kann sonst
    unter zwei Kennungen stehen, und eine Liste nach Namen verschmolze sie zu einer
    Zeile, die es nicht gibt.
    """
    from app.services.freimarken_moderation_service import teamrollen

    anna = "aaaaaaaa-1111-4111-8111-111111111111"
    bert = "bbbbbbbb-2222-4222-8222-222222222222"
    _setze_frage_ab(monkeypatch, {
        "SELECT spieler, rolle, vergeben_am": [
            {"spieler": anna, "rolle": "moderator",
             "vergeben_am": datetime(2026, 11, 20, tzinfo=timezone.utc),
             "vergeber": None},
            {"spieler": anna, "rolle": "baumeister",
             "vergeben_am": datetime(2026, 11, 21, tzinfo=timezone.utc),
             "vergeber": bert},
            {"spieler": bert, "rolle": "verwalter",
             "vergeben_am": datetime(2026, 11, 1, tzinfo=timezone.utc),
             "vergeber": None},
        ],
    })

    zeilen = teamrollen(_server(tmp_path, slug="rollen", port=25701), bekannt={})

    assert len(zeilen) == 2, "zwei Menschen, drei Rollen: " + str(zeilen)
    nach_kennung = {eintrag["kennung"]: eintrag for eintrag in zeilen}
    assert [rolle["rolle"] for rolle in nach_kennung[anna]["rollen"]] \
        == ["moderator", "baumeister"]
    assert nach_kennung[anna]["rollen"][0]["vergeber_name"] == "Konsole"
    assert nach_kennung[anna]["rollen"][1]["vergeber_name"] == bert[:8]


def test_die_zahlen_fragen_mit_EINEM_zeitpunkt_in_utc(monkeypatch, tmp_path):
    """``NOW()`` waere die Zone des Datenbankservers - die Spalten stehen in UTC.

    Geprueft wird, WAS hereingegeben wird: ein naiver Zeitpunkt in UTC, genau so, wie
    die Spalten dort liegen. Ein Zeitpunkt mit Zone ginge je Treiber anders hinein, und
    eine Stunde Unterschied entscheidet darueber, ob eine gerade abgelaufene Sperre noch
    mitgezaehlt wird.
    """
    from app.services.freimarken_moderation_service import zahlen

    gesehen: dict = {}

    from app.services import freimarken_moderation_service as mod

    def gefaelscht(server, sql, werte=None, *, grenze=500):
        if "FROM strafen" in sql:
            gesehen.update(werte or {})
            return [{"dauerhaft": 2, "befristet": 3, "gesamt": 9}]
        return [{"offen": 1, "gesamt": 4}]

    monkeypatch.setattr(mod.fdb, "frage", gefaelscht)

    ergebnis = zahlen(_server(tmp_path, slug="zahlen", port=25702),
                      jetzt=datetime(2026, 11, 25, 14, 0,
                                     tzinfo=timezone(timedelta(hours=2))))

    assert gesehen["jetzt"] == datetime(2026, 11, 25, 12, 0), \
        "14:00 in UTC+2 ist 12:00 UTC - und ohne Zonenangabe"
    assert gesehen["jetzt"].tzinfo is None, "die Spalten sind naiv"
    assert ergebnis["strafen_dauerhaft"] == 2
    assert ergebnis["meldungen_offen"] == 1


def test_eine_leere_tabelle_gibt_null_und_nicht_none(monkeypatch, tmp_path):
    """``SUM`` ueber keine Zeile ist NULL - und ``int(None)`` wirft.

    Dieselbe Falle wie bei der Kassentabelle: Ein Server, auf dem noch nie etwas
    passiert ist, ist der gewoehnliche Zustand nach dem ersten Start.
    """
    from app.services.freimarken_moderation_service import zahlen

    from app.services import freimarken_moderation_service as mod
    monkeypatch.setattr(mod.fdb, "frage",
                        lambda server, sql, werte=None, *, grenze=500:
                        [{"dauerhaft": None, "befristet": None, "gesamt": 0}]
                        if "FROM strafen" in sql else [])

    ergebnis = zahlen(_server(tmp_path, slug="leer", port=25703))

    assert ergebnis["strafen_dauerhaft"] == 0
    assert ergebnis["meldungen_offen"] == 0


def test_die_staende_stehen_als_platzhalter_im_sql():
    """Ein Tupel an ``IN :staende`` findet NIE etwas - der Treiber setzt es als EINEN Wert.

    SQLAlchemy braucht dafuer ein ausdehnendes Bindeglied, und ``frage`` nimmt nur ein
    gewoehnliches Woerterbuch. Die Probe haelt beides: dass die Platzhalter aus der EINEN
    Liste gerechnet werden und dass ihre Zahl stimmt.
    """
    from app.services.freimarken_moderation_service import (
        OFFENE_STAENDE,
        _offene_staende,
    )

    stueck, werte = _offene_staende()

    assert len(werte) == len(OFFENE_STAENDE)
    assert set(werte.values()) == set(OFFENE_STAENDE)
    for name in werte:
        assert ":" + name in stueck
    assert stueck.startswith("(") and stueck.endswith(")")


# ----------------------------------------------------------------- Die Seite selbst

def test_die_seite_oeffnet_sich_ab_moderator(client, tmp_path):
    """Lesen ist Moderatorensache - wie bei Besitz und Konten."""
    from tests.test_freimarken_seiten import _aufbau

    server_id = _aufbau(client, tmp_path, name="standseite", port=25710,
                        rolle="moderator")

    antwort = client.get("/freimarken/" + str(server_id) + "/strafen")

    assert antwort.status_code == 200


def test_ohne_datenbank_sagt_die_seite_warum(client, tmp_path):
    """Leer ist ein gueltiger Zustand, aber keine Antwort.

    Die ``config.yml`` dieser Probe hat eine leere ``datenbank.url``. Eine leere Tabelle
    liest sich hier als "niemand ist gesperrt, keine Meldung offen" - und das ist die
    eine Auskunft, die diese Seite nie geben darf, wenn sie gar nicht gelesen hat.

    GEHALTEN WIRD DAS VON DER VORLAGE und nicht vom Router [Fund durch Einspritzung]:
    Bei einem Lesefehler endet sie vor dem Block, und zwar fuer jede Quelle ausser
    ``spieldaten`` - dort muss das Korrekturformular stehenbleiben. Nimmt man diese
    Seite in jene Ausnahme hinein, wird diese Probe rot.
    """
    from tests.test_freimarken_seiten import _aufbau

    server_id = _aufbau(client, tmp_path, name="standohnedb", port=25711)

    antwort = client.get("/freimarken/" + str(server_id) + "/strafen")

    assert antwort.status_code == 200
    assert "speichert nichts" in antwort.text
    assert "Niemand ist dauerhaft gesperrt" not in antwort.text, \
        "ohne Datenbank darf die Seite keine Aussage ueber Strafen machen"


def test_die_seite_steht_im_menue(client, tmp_path):
    from tests.test_freimarken_seiten import _aufbau

    server_id = _aufbau(client, tmp_path, name="modmenue", port=25712)

    antwort = client.get("/freimarken/" + str(server_id) + "/protokoll")

    # BEIDE Seiten: die Einstellungen und der Stand. Sie heissen verschieden, weil sie
    # Verschiedenes tun - "Moderation" stellt die Grenzen, "Strafen und Meldungen" zeigt,
    # was damit geschehen ist.
    for name in ("Moderation", "Strafen und Meldungen"):
        assert name in antwort.text


def test_die_einstellungsseite_zeigt_die_grenzen(client, tmp_path):
    """Die zweite Seite: die GRENZEN aus der config.yml, mit Formular.

    Ohne diese Probe waere die Seite angemeldet und leer - der Feldkatalog verlangt nur,
    dass ein Abschnitt verortet IST, nicht dass er ankommt. Geprueft wird darum mit einer
    config.yml, die beide Abschnitte hat.
    """
    from tests.test_freimarken_seiten import _aufbau

    server_id = _aufbau(client, tmp_path, name="modwerte", port=25713)
    datei = (tmp_path / "modwerte" / "plugins" / "Freimarken" / "config.yml")
    with open(datei, "a", encoding="utf-8", newline="") as offen:
        offen.write("""
moderation:
  strafen:
    hoechste-stunden:
      stumm: 168
      bann: 720
  einsehen:
    dauer-sekunden: 300
team:
  rollen:
    moderator:
      rechte:
        - meldungen
        - einsehen
""")

    antwort = client.get("/freimarken/" + str(server_id) + "/moderation")

    assert antwort.status_code == 200
    # Die Zahl UND ihr Pfad: Ein Feld ohne Namen ist ein Eingabefeld, das niemand
    # zuordnen kann.
    assert "hoechste-stunden.bann" in antwort.text
    assert "720" in antwort.text
    assert "einsehen.dauer-sekunden" in antwort.text
    # Die Rollen sind eine LISTE - ihre Eintraege kommen einzeln, mit ihrem Index.
    assert "rollen.moderator.rechte" in antwort.text
