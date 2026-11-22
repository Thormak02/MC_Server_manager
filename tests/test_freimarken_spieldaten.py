"""Der lesende Zugang zur Plugin-Datenbank und was er daraus macht.

DREI TAFELN GEGEN DEN PLUGIN-QUELLTEXT
Das Repo enthaelt beide Seiten, und darum muss keine Liste im Manager gegen sich selbst
geprueft werden: Die Arten einer Besitzzeile, die Zuschlagsnamen und die Rechtevorsilbe
stehen im Java-Quelltext, und die Proben hier lesen sie dort. Kommt im Plugin eine Art
oder ein Zuschlag dazu, wird eine Probe rot - und zwar diejenige, deren Name die Sache
nennt.

KEINE DATENBANK IM SPIEL
Die Proben fuer Besitz, Konten und Verlauf setzen ``frage`` ab. Eine echte MariaDB im
Probenlauf waere eine Abhaengigkeit, die auf einem fremden Rechner fehlt - und die
Umwandlung, um die es hier geht (Arten benennen, Stadtkassen erkennen, Zeitpunkte
kennzeichnen), haengt nicht am Treiber.
"""
from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

JAVA = Path("freimarken")


# -------------------------------------------------------- Tafeln gegen das Plugin

def test_die_arten_sind_die_des_plugins():
    """Die ENUM-Liste von ``parzellen.art`` steht in Datenbank.java - Schritt 29.

    Wer nur 'parzelle' und 'gruppe' kennt, zeigt eine Wolkeninsel als Parzelle an. Die
    Liste vollstaendig zu halten ist darum keine Formsache: Die Spalte unterscheidet
    FUENF verschiedene Dinge, die auf derselben Tabelle liegen.
    """
    from app.services.freimarken_spieldaten_service import ARTEN

    quelle = (JAVA / "daten/src/main/java/de/freimarken/daten/Datenbank.java").read_text(
        encoding="utf-8")
    # Der LETZTE MODIFY auf die Spalte art hat die vollstaendige Liste - jeder davor ist
    # ein frueherer Stand, und ein alter Schritt wird in diesem Haus nie geaendert.
    bloecke = re.findall(
        r"MODIFY COLUMN IF EXISTS art\s*\n\s*ENUM\(([^)]*)\)", quelle)
    assert bloecke, "In Datenbank.java steht kein MODIFY auf die Spalte art"
    im_plugin = set(re.findall(r"'([a-z]+)'", bloecke[-1]))

    assert im_plugin == set(ARTEN), (
        "Die Artenliste des Managers passt nicht zu der des Plugins. Im Plugin: "
        + str(sorted(im_plugin)) + " | im Manager: " + str(sorted(ARTEN)))


def test_die_zuschlaege_sind_die_des_plugins():
    """Die Namen stehen als RECHT_EXTRA_* im Java-Quelltext, nicht in einer Datei.

    Sie sind keine Einstellung des Betreibers - aber auch nichts, was im Programm des
    Managers stehen soll. Der Katalog fuehrt sie, und diese Probe haelt ihn gegen die
    Wirklichkeit.
    """
    from app.services.freimarken_mitglied_service import zuschlaege

    gefunden: set[str] = set()
    for datei in (JAVA / "paper/src/main/java/de/freimarken/paper").glob("*.java"):
        for name in re.findall(r'"freimarken\.extra\.([a-z]+)\."', datei.read_text(
                encoding="utf-8")):
            gefunden.add(name)

    assert gefunden, "Im Plugin steht kein freimarken.extra.<name>. als Konstante"
    assert gefunden == set(zuschlaege()), (
        "Die Zuschlagsliste im Katalog passt nicht zum Plugin. Im Plugin: "
        + str(sorted(gefunden)) + " | im Katalog: " + str(sorted(zuschlaege())))


def test_die_vorsilbe_ist_die_des_plugins():
    """``Rangzahl.ausRecht`` sucht unter genau dieser Vorsilbe.

    Rangzahl.java begruendet es selbst: Ein Schreiber und ein Leser derselben Gestalt an
    zwei Stellen laufen eines Tages auseinander - geschrieben wird
    ``freimarken.parzelle.2``, gelesen ``freimarken.parzellen.``, und der Rang wirkt
    einfach nicht. Rot wird dabei nichts. Hier ist der Schreiber, und diese Probe ist das
    Gegenstueck.
    """
    from app.services.freimarken_mitglied_service import VORSILBE, rechtename

    quelle = (JAVA / "paper/src/main/java/de/freimarken/paper"
              / "ParzellenBefehle.java").read_text(encoding="utf-8")
    assert '"freimarken.extra.parzellen."' in quelle, \
        "Die Konstante im Plugin heisst anders als erwartet"
    assert VORSILBE == "freimarken.extra."
    assert rechtename("parzellen", 3) == "freimarken.extra.parzellen.3"


# ------------------------------------------------------------ Das Kennwort bleibt

def test_der_zugang_nennt_sein_kennwort_nie():
    """Ein dataclass-repr nennt jedes Feld - und landet dann in jeder Ausnahme."""
    from app.services.freimarken_db_service import Zugang

    zugang = Zugang(host="h", port=3306, schema="s", benutzer="u", passwort="geheim123")

    assert "geheim123" not in repr(zugang)
    assert "geheim123" not in str(zugang)
    assert "geheim123" not in str(zugang.schluessel), \
        "das Kennwort darf nicht Teil des Engine-Schluessels sein"
    assert "u" in repr(zugang) and "h" in repr(zugang), \
        "ohne Benutzer und Rechner ist die Darstellung nutzlos"


@pytest.mark.parametrize("adresse,erwartet", [
    ("jdbc:mariadb://h:3306/s?user=u&password=geheim", "jdbc:mariadb://h:3306/s"),
    ("jdbc:mariadb://h:3306/s", "jdbc:mariadb://h:3306/s"),
    ("", ""),
])
def test_ohne_geheimnis_schneidet_hinter_dem_fragezeichen(adresse, erwartet):
    """Dasselbe Verfahren wie ``FreimarkenPlugin.ohneGeheimnis``."""
    from app.services.freimarken_db_service import ohne_geheimnis

    assert ohne_geheimnis(adresse) == erwartet


# ----------------------------------------------------------- Nur lesen, nie schreiben

@pytest.mark.parametrize("sql", [
    "UPDATE kassen SET mark = 0",
    "DELETE FROM parzellen",
    "INSERT INTO kassen (spieler, mark) VALUES ('x', 1)",
    "DROP TABLE parzellen",
    "  update kassen set mark = 0",
    "TRUNCATE kassen",
])
def test_nur_select_kommt_durch(sql, tmp_path):
    """Eine Aenderung an den Spieldaten laeuft ueber das Plugin, nicht ueber die DB.

    Die Probe braucht KEINE Datenbank: Der Riegel greift, bevor die Verbindung gebaut
    wird. Genau das ist die Zusage - sonst stuende er hinter dem Verbindungsaufbau und
    waere auf einem Rechner ohne MariaDB nie geprueft.
    """
    from app.models.server import Server
    from app.services.freimarken_db_service import FreimarkenDbFehler, frage

    server = Server(name="x", slug="x", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path), port=25600, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    with pytest.raises(FreimarkenDbFehler) as fehler:
        frage(server, sql)
    assert "liest nur" in str(fehler.value)


def test_select_erreicht_den_zugang(tmp_path):
    """Die Gegenrichtung: Ein SELECT laeuft weiter und scheitert erst an den Zugangsdaten.

    Ohne diesen Satz waere ein "immer verweigern" gruen - und dann koennte die Seite
    nichts lesen, obwohl sie darf.
    """
    from app.models.server import Server
    from app.services.freimarken_db_service import FreimarkenDbFehler, frage

    server = Server(name="x", slug="x2", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path), port=25601, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    with pytest.raises(FreimarkenDbFehler) as fehler:
        frage(server, "SELECT 1")
    # Es gibt keine config.yml - also kommt die Meldung ueber die Datei und NICHT die
    # ueber den Lesezugang.
    assert "liest nur" not in str(fehler.value)
    assert "config.yml" in str(fehler.value)


# ------------------------------------------------------------ Die Zugangsdaten lesen

def _server_mit_config(tmp_path, inhalt, *, slug="zugang", port=25610):
    from app.models.server import Server

    basis = tmp_path / slug
    (basis / "plugins" / "Freimarken").mkdir(parents=True, exist_ok=True)
    (basis / "plugins" / "Freimarken" / "config.yml").write_text(
        inhalt, encoding="utf-8", newline="")
    return Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                  base_path=str(basis), port=port, status="stopped",
                  start_mode="command", start_command="java -jar server.jar")


def test_zugang_kommt_aus_der_config(tmp_path):
    from app.services.freimarken_db_service import lies_zugang

    server = _server_mit_config(tmp_path, """datenbank:
  url: "jdbc:mariadb://FRIEDRICHS-SERV:3306/freimarken"
  benutzer: freimarken
  passwort: geheim123
""")
    zugang = lies_zugang(server)

    assert zugang.host == "FRIEDRICHS-SERV"
    assert zugang.port == 3306
    assert zugang.schema == "freimarken"
    assert zugang.benutzer == "freimarken"
    assert zugang.passwort == "geheim123"


def test_ohne_port_gilt_3306(tmp_path):
    from app.services.freimarken_db_service import lies_zugang

    server = _server_mit_config(tmp_path, """datenbank:
  url: "jdbc:mariadb://127.0.0.1/freimarken"
  benutzer: fm
  passwort: x
""", slug="ohneport", port=25611)
    assert lies_zugang(server).port == 3306


def test_leere_url_heisst_dieser_server_speichert_nichts(tmp_path):
    """Leer ist ein gueltiger Zustand im Plugin, kein Fehler - die Meldung sagt das.

    Die config.yml sagt es selbst: "Leer heisst: Es wird nichts gespeichert. Parzellen
    und Guthaben gelten dann nur bis zum naechsten Neustart."
    """
    from app.services.freimarken_db_service import FreimarkenDbFehler, lies_zugang

    server = _server_mit_config(tmp_path, """datenbank:
  url: ""
  benutzer: ""
  passwort: ""
""", slug="leer", port=25612)

    with pytest.raises(FreimarkenDbFehler) as fehler:
        lies_zugang(server)
    assert "speichert nichts" in str(fehler.value)


def test_kaputte_adresse_wird_benannt_und_nicht_geraten(tmp_path):
    from app.services.freimarken_db_service import FreimarkenDbFehler, lies_zugang

    server = _server_mit_config(tmp_path, """datenbank:
  url: "FRIEDRICHS-SERV:3306"
  benutzer: fm
  passwort: geheim123
""", slug="kaputt", port=25613)

    with pytest.raises(FreimarkenDbFehler) as fehler:
        lies_zugang(server)
    assert "jdbc:mariadb://" in str(fehler.value)
    assert "geheim123" not in str(fehler.value), \
        "die Meldung darf die Adresse nennen, aber nie ein Kennwort"


# ----------------------------------------------------------------- Zeit ist UTC

def test_naive_zeitpunkte_werden_als_utc_gekennzeichnet():
    """pymysql gibt DATETIME ohne Zone zurueck; das Plugin schreibt in UTC.

    Ohne diese Kennzeichnung zeigt der Manager im Winter eine, im Sommer zwei Stunden
    falsch - er rechnet sonst durchgaengig mit ``datetime.now(timezone.utc)``.
    """
    from datetime import date

    from app.services.freimarken_db_service import _als_utc

    naiv = datetime(2026, 11, 22, 12, 0, 0)
    assert _als_utc(naiv).tzinfo == timezone.utc

    # Ein Wert MIT Zone wird nicht verschoben.
    mit = datetime(2026, 11, 22, 12, 0, 0, tzinfo=timezone.utc)
    assert _als_utc(mit) == mit

    # Eine Tagesspalte bleibt unberuehrt: Die sechs DATE-Spalten des Plugins stehen in
    # ORTSZEIT und sind keine Zeitpunkte.
    tag = date(2026, 11, 22)
    assert _als_utc(tag) is tag


# ------------------------------------------------------------ Namen und Arten

def test_der_rueckfall_ist_nie_leer():
    from app.services.freimarken_spieldaten_service import (ZEICHEN_DER_KENNUNG,
                                                            beschriftung)

    kennung = "ABCDEF12-3456-4789-abcd-000000000000"
    assert beschriftung(kennung, {}) == kennung[:ZEICHEN_DER_KENNUNG]
    assert beschriftung(None, {}) == "(ohne Kennung)"
    # Die Suche geht ueber die KLEINGESCHRIEBENE Kennung: usercache.json schreibt sie
    # klein, die Datenbank CHAR(36) nicht zwingend.
    assert beschriftung(kennung, {kennung.lower(): "Anna"}) == "Anna"


def test_eine_unbekannte_art_wird_benannt_und_nicht_geraten():
    """Eine neue Art des Plugins faellt nicht stumm in 'Parzelle'."""
    from app.services.freimarken_spieldaten_service import art_wort

    assert art_wort("wolke") == "Wolkeninsel"
    assert "seeblase" in art_wort("seeblase")
    assert "leer" in art_wort("")


def test_namen_kommen_aus_usercache_und_den_zugangslisten(tmp_path):
    """Zwei Quellen, und der Zwischenspeicher gewinnt.

    Er ist aktueller als eine Zugangsliste, in der ein Name seit dem Eintrag stehen
    geblieben ist - genau die Lage nach einer Umbenennung.
    """
    import json

    from app.models.server import Server
    from app.services.freimarken_spieldaten_service import namen

    basis = tmp_path / "namen"
    basis.mkdir()
    (basis / "usercache.json").write_text(json.dumps(
        [{"uuid": "aaaaaaaa-1111-4111-8111-111111111111", "name": "AnnaNeu"}]),
        encoding="utf-8")
    (basis / "whitelist.json").write_text(json.dumps([
        {"uuid": "aaaaaaaa-1111-4111-8111-111111111111", "name": "AnnaAlt"},
        {"uuid": "bbbbbbbb-2222-4222-8222-222222222222", "name": "Bert"},
    ]), encoding="utf-8")

    server = Server(name="n", slug="n", server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=25620, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    gefunden = namen(server)
    assert gefunden["aaaaaaaa-1111-4111-8111-111111111111"] == "AnnaNeu"
    assert gefunden["bbbbbbbb-2222-4222-8222-222222222222"] == "Bert"


def test_kaputte_dateien_sind_kein_fehlerfall(tmp_path):
    """Eine Namensliste darf an einer kaputten Datei nicht zerbrechen."""
    from app.models.server import Server
    from app.services.freimarken_spieldaten_service import namen

    basis = tmp_path / "kaputt"
    basis.mkdir()
    (basis / "usercache.json").write_text("{kein json", encoding="utf-8")
    (basis / "whitelist.json").write_text("[1, 2, 3]", encoding="utf-8")

    server = Server(name="k", slug="k", server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=25621, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    assert namen(server) == {}


# ------------------------------------------------------------ Konten und Besitz

def _setze_frage_ab(monkeypatch, antworten):
    """``frage`` durch eine Tafel ersetzen: Anfang der Abfrage -> Zeilen."""
    from app.services import freimarken_db_service as fdb

    def gefaelscht(server, sql, werte=None, *, grenze=500):
        for anfang, zeilen in antworten.items():
            if sql.strip().startswith(anfang):
                return list(zeilen)
        raise AssertionError("Unerwartete Abfrage: " + sql.strip()[:90])

    monkeypatch.setattr(fdb, "frage", gefaelscht)
    from app.services import freimarken_spieldaten_service as sp
    monkeypatch.setattr(sp.fdb, "frage", gefaelscht)


def test_eine_stadtkasse_wird_als_solche_benannt(monkeypatch, tmp_path):
    """``parzellen.kasse`` steht in ``kassen.spieler`` - sie sieht wie ein Spieler aus.

    Ohne diese Unterscheidung stuende jede Freistadt mit einem achtstelligen Kuerzel in
    der Kontenliste, und eine Namensauflesung findet fuer sie nie jemanden.
    """
    from app.models.server import Server
    from app.services.freimarken_spieldaten_service import konten

    stadt = "cccccccc-3333-4333-8333-333333333333"
    mensch = "aaaaaaaa-1111-4111-8111-111111111111"
    _setze_frage_ab(monkeypatch, {
        "SELECT spieler, mark": [{"spieler": mensch, "mark": 500},
                                 {"spieler": stadt, "mark": 9000}],
        "SELECT spieler, goldmark": [],
        "SELECT DISTINCT kasse": [{"kasse": stadt}],
    })

    server = Server(name="s", slug="s", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path), port=25630, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    zeilen = {z["kennung"]: z for z in konten(server)}
    assert zeilen[stadt]["name"] == "Stadtkasse"
    assert zeilen[stadt]["ist_stadtkasse"] is True
    assert zeilen[mensch]["ist_stadtkasse"] is False
    assert zeilen[mensch]["name"] == mensch[:8]


def test_kein_goldkonto_ist_nicht_null_goldmark(monkeypatch, tmp_path):
    """Ein eroeffnetes Konto mit 0 und gar kein Konto sind zwei verschiedene Dinge."""
    from app.models.server import Server
    from app.services.freimarken_spieldaten_service import konten

    ohne = "aaaaaaaa-1111-4111-8111-111111111111"
    mit = "bbbbbbbb-2222-4222-8222-222222222222"
    _setze_frage_ab(monkeypatch, {
        "SELECT spieler, mark": [{"spieler": ohne, "mark": 100},
                                 {"spieler": mit, "mark": 100}],
        "SELECT spieler, goldmark": [{"spieler": mit, "goldmark": 0}],
        "SELECT DISTINCT kasse": [],
    })

    server = Server(name="g", slug="g", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path), port=25631, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    zeilen = {z["kennung"]: z for z in konten(server)}
    assert zeilen[ohne]["goldmark"] is None, "kein Goldkonto"
    assert zeilen[mit]["goldmark"] == 0, "eroeffnet, aber leer"


def test_eine_gruppe_hat_keine_stelle(monkeypatch, tmp_path):
    """``x`` und ``z`` sind NULL-faehig: Eine Gruppe hat keine Rasterzelle.

    Eine Anzeige, die 0/0 schreibt, nennt eine Stelle, an der etwas anderes liegt.
    """
    from app.models.server import Server
    from app.services.freimarken_spieldaten_service import besitz

    _setze_frage_ab(monkeypatch, {
        "SELECT id, welt, x, z": [
            {"id": 1, "welt": "w", "x": 3, "z": 4,
             "eigentuemer": "aaaaaaaa-1111-4111-8111-111111111111", "preis": 25000,
             "erworben_am": None, "art": "parzelle", "eltern_id": 9,
             "zustand": "aktiv", "name": None, "kasse": None},
            {"id": 9, "welt": "w", "x": None, "z": None,
             "eigentuemer": "aaaaaaaa-1111-4111-8111-111111111111", "preis": 0,
             "erworben_am": None, "art": "gruppe", "eltern_id": None,
             "zustand": "aktiv", "name": None, "kasse": None},
        ],
    })

    server = Server(name="b", slug="b", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path), port=25632, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    zeilen = {z["id"]: z for z in besitz(server)}
    assert zeilen[1]["stelle"] == "3/4"
    assert zeilen[9]["stelle"] == "", "eine Gruppe hat keine Stelle"
    assert zeilen[9]["x"] is None
    assert zeilen[1]["gehoert_zu_gruppe"] is True
    assert zeilen[9]["gehoert_zu_gruppe"] is False
    assert zeilen[9]["art"] == "Grundstueck"


def test_eine_leere_kassentabelle_gibt_null_und_nicht_none(monkeypatch, tmp_path):
    """``SUM`` ueber eine leere Tabelle gibt NULL - und None schreibt "None" in die Seite."""
    from app.models.server import Server
    from app.services.freimarken_spieldaten_service import zusammenfassung

    _setze_frage_ab(monkeypatch, {
        "SELECT art, COUNT(*)": [],
        "SELECT COUNT(*) AS zahl FROM kassen": [{"zahl": 0}],
        "SELECT COUNT(*) AS zahl FROM goldkassen": [{"zahl": 0}],
        "SELECT SUM(mark)": [{"summe": None}],
    })

    server = Server(name="z", slug="z", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path), port=25633, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    assert zusammenfassung(server)["mark_gesamt"] == 0


def test_der_verlauf_sortiert_mit_der_nummer(monkeypatch, tmp_path):
    """Der Schluessel heisst (spieler, zeitpunkt, id) - die Nummer muss mitsortieren.

    Zwei Buchungen im selben Millisekundenschritt kaemen sonst in beliebiger Reihenfolge,
    und bei einem Kauf samt Gebuehr saehe es aus, als waere die Gebuehr vorher gebucht.
    """
    from app.models.server import Server
    from app.services import freimarken_spieldaten_service as sp

    gesehen: list[str] = []

    def gefaelscht(server, sql, werte=None, *, grenze=500):
        gesehen.append(" ".join(sql.split()))
        return []

    monkeypatch.setattr(sp.fdb, "frage", gefaelscht)

    server = Server(name="v", slug="v", server_type="paper", mc_version="26.3",
                    base_path=str(tmp_path), port=25634, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")

    sp.verlauf(server, "aaaaaaaa-1111-4111-8111-111111111111")
    assert "ORDER BY zeitpunkt DESC, id DESC" in gesehen[0]
    assert "FROM buchungen" in gesehen[0]

    gesehen.clear()
    sp.verlauf(server, "aaaaaaaa-1111-4111-8111-111111111111", gold=True)
    assert "FROM goldbuchungen" in gesehen[0], "Goldmark hat ihr EIGENES Paar"


# ------------------------------------------------------------- Mitgliedschaften

def _nutzer(db, rolle, name):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _server(db, tmp_path, *, slug, port):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


KENNUNG = "aaaaaaaa-1111-4111-8111-111111111111"


def test_eine_mitgliedschaft_nennt_ihr_recht(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="mitglied", port=25640)
        ms.lege_an(db, server_id=server.id, spieler_kennung=KENNUNG,
                   spieler_name="Anna", zuschlag_name="parzellen", zuschlag_zahl=3,
                   grund="Probe")

        zeilen = ms.liste(db, server.id)

    assert len(zeilen) == 1
    assert zeilen[0]["recht"] == "freimarken.extra.parzellen.3"
    assert zeilen[0]["laeuft"] is True
    assert zeilen[0]["herkunft"] == "von Hand"
    assert zeilen[0]["grund"] == "Probe"


def test_ohne_kennung_geht_es_nicht(client, tmp_path):
    """Der Name allein genuegt nicht - er aendert sich bei einer Umbenennung."""
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="ohnekennung", port=25641)
        with pytest.raises(ms.MitgliedFehler) as fehler:
            ms.lege_an(db, server_id=server.id, spieler_kennung="  ",
                       spieler_name="Anna", zuschlag_name="parzellen",
                       zuschlag_zahl=1)
    assert "Kennung" in str(fehler.value)


@pytest.mark.parametrize("zahl", [0, -1, -3, "abc", ""])
def test_eine_zahl_ohne_wirkung_wird_abgewiesen(zahl):
    """``mitZuschlag`` rechnet mit ``Math.max(zahl, 0)``.

    Eine 0 oder eine negative Zahl wuerde gespeichert, das Recht wuerde vergeben, und es
    taete nichts. RangzahlTest nennt es "ein Tippfehler, kein Entzug".
    """
    from app.services import freimarken_mitglied_service as ms

    with pytest.raises(ms.MitgliedFehler):
        ms.pruefe("parzellen", zahl, seit=None, bis=None)


def test_bis_ist_der_letzte_gueltige_augenblick(client, tmp_path):
    """Wer bis zum 31. bezahlt hat, ist am 31. noch Mitglied."""
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    jetzt = datetime(2026, 11, 22, 12, 0, 0, tzinfo=timezone.utc)
    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="bis", port=25642)
        eintrag = ms.lege_an(db, server_id=server.id, spieler_kennung=KENNUNG,
                             spieler_name="Anna", zuschlag_name="gruppe",
                             zuschlag_zahl=2,
                             seit=jetzt - timedelta(days=30), bis=jetzt)

        assert ms.laeuft(eintrag, stichtag=jetzt) is True, "genau am Ende noch gueltig"
        assert ms.laeuft(eintrag, stichtag=jetzt + timedelta(seconds=1)) is False
        assert ms.laeuft(eintrag, stichtag=jetzt - timedelta(days=31)) is False, \
            "vor dem Beginn gilt sie nicht"


def test_bis_vor_seit_wird_abgewiesen():
    from app.services import freimarken_mitglied_service as ms

    jetzt = datetime(2026, 11, 22, 12, 0, 0, tzinfo=timezone.utc)
    with pytest.raises(ms.MitgliedFehler) as fehler:
        ms.pruefe("parzellen", 1, seit=jetzt, bis=jetzt - timedelta(days=1))
    assert "vor" in str(fehler.value)


def test_beenden_braucht_den_richtigen_server(client, tmp_path):
    """Die Nummer allein genuegt nicht als Bedingung.

    Eine AUTO_INCREMENT-Nummer wird nach einem Neustart wieder vergeben, und ein Admin,
    der nur einen Server sehen darf, soll ueber eine geratene Nummer nicht die Zeile
    eines anderen Servers beenden.
    """
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    with SessionLocal() as db:
        einer = _server(db, tmp_path, slug="beend-a", port=25643)
        anderer = _server(db, tmp_path, slug="beend-b", port=25644)
        eintrag = ms.lege_an(db, server_id=einer.id, spieler_kennung=KENNUNG,
                             spieler_name="Anna", zuschlag_name="inseln",
                             zuschlag_zahl=1)

        with pytest.raises(ms.MitgliedFehler):
            ms.beende(db, server_id=anderer.id, eintrag_id=eintrag.id)

        # Und die Gegenrichtung: am richtigen Server geht es. Ohne diesen Satz waere ein
        # "immer verweigern" gruen.
        beendet = ms.beende(db, server_id=einer.id, eintrag_id=eintrag.id)
        assert beendet.bis is not None
        assert ms.laeuft(beendet) is False


def test_beenden_loescht_nicht(client, tmp_path):
    """Die Zeile ist der Beleg dafuer, dass jemand bezahlt hat."""
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="beleg", port=25645)
        eintrag = ms.lege_an(db, server_id=server.id, spieler_kennung=KENNUNG,
                             spieler_name="Anna", zuschlag_name="wolken",
                             zuschlag_zahl=1, grund="Mitgliedschaft Nov")
        ms.beende(db, server_id=server.id, eintrag_id=eintrag.id)

        alle = ms.liste(db, server.id)
        laufende = ms.liste(db, server.id, nur_laufende=True)

    assert len(alle) == 1, "die beendete Zeile bleibt stehen"
    assert alle[0]["grund"] == "Mitgliedschaft Nov"
    assert laufende == [], "aber sie laeuft nicht mehr"


def test_zwei_zuschlaege_auf_dasselbe_geben_die_groessere_zahl(client, tmp_path):
    """``Rangzahl.hoechsteAus`` nimmt die GROESSTE Zahl und addiert sie einmal.

    Wer beide Zeilen als Rechte auflistete, verspraeche einen Zuschlag von 5, den das
    Plugin nicht gibt - es gibt 3.
    """
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="zweimal", port=25646)
        ms.lege_an(db, server_id=server.id, spieler_kennung=KENNUNG,
                   spieler_name="Anna", zuschlag_name="parzellen", zuschlag_zahl=2)
        ms.lege_an(db, server_id=server.id, spieler_kennung=KENNUNG,
                   spieler_name="Anna", zuschlag_name="parzellen", zuschlag_zahl=3)
        ms.lege_an(db, server_id=server.id, spieler_kennung=KENNUNG,
                   spieler_name="Anna", zuschlag_name="gruppe", zuschlag_zahl=1)

        zeilen = ms.rechtezeilen(db, server.id)

    rechte = sorted(z["recht"] for z in zeilen)
    assert rechte == ["freimarken.extra.gruppe.1", "freimarken.extra.parzellen.3"], \
        "nur die groessere je Zuschlag, und die andere Art daneben"


def test_eine_beendete_mitgliedschaft_nennt_kein_recht_mehr(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="beendet", port=25647)
        eintrag = ms.lege_an(db, server_id=server.id, spieler_kennung=KENNUNG,
                             spieler_name="Anna", zuschlag_name="freistaedte",
                             zuschlag_zahl=1)
        ms.beende(db, server_id=server.id, eintrag_id=eintrag.id)

        assert ms.rechtezeilen(db, server.id) == []
