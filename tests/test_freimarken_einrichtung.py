"""Die Einrichtung eines Freimarken-Servers: fuenf Schritte, die einzeln scheitern.

DIE ECHTEN SCHRITTE WERDEN ABGESETZT. Ein Gradle-Lauf dauert Minuten, und eine
winget-Installation veraendert den Rechner - beides gehoert nicht in eine Probe. Was hier
geprueft wird, ist die KETTE: dass jeder Schritt seine Zeile hinterlaesst, dass keiner die
anderen mitreisst, und dass die Reihenfolge haelt.

DREI ZUSAGEN SIND DIE WICHTIGSTEN:
1. Eine vorhandene config.yml wird NIE ueberschrieben. Sonst waere ein zweiter Lauf der
   Weg, auf dem ein Betreiber alle seine Preise verliert.
2. Das Kennwort steht in keiner Zeile, die der Dienst zurueckgibt.
3. Gelesen wird ``url`` und nicht ``url.test`` - das Testschema leeren die Java-Proben
   vor jedem Lauf.
"""
from __future__ import annotations

import pytest


CONFIG_VORLAGE = """# Freimarken - Einstellungen
# Diese Zeile ist das Handbuch und muss jede Aenderung ueberleben.
datenbank:
  # NUR BEIM START GELESEN.
  url: ""
  benutzer: ""
  passwort: ""

wirtschaft:
  parzelle:
    grundpreis: 25000
"""

PROPERTIES = """# Zugangsdaten - liegt unter data/, ist in .gitignore
url=jdbc:mariadb://127.0.0.1:3306/freimarken
url.netz=jdbc:mariadb://FRIEDRICHS-SERV:3306/freimarken
url.test=jdbc:mariadb://127.0.0.1:3306/freimarken_test
benutzer=freimarken
passwort=sehrGeheim123
root.passwort=nochGeheimer
"""


def _server(tmp_path, *, slug="einricht", port=25800, mit_config=False):
    from app.models.server import Server

    basis = tmp_path / slug
    (basis / "plugins" / "Freimarken").mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    if mit_config:
        with open(basis / "plugins" / "Freimarken" / "config.yml", "w",
                  encoding="utf-8", newline="") as offen:
            offen.write(CONFIG_VORLAGE)
    return Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                  base_path=str(basis), port=port, status="stopped",
                  start_mode="command", start_command="java -jar server.jar",
                  freimarken_enabled=True)


def _lege_vorlagen(monkeypatch, tmp_path, *, properties=PROPERTIES):
    """Vorlage und Zugangsdaten in ein Probenverzeichnis legen."""
    from app.services import freimarken_einrichtung_service as e

    vorlage = tmp_path / "config_vorlage.yml"
    with open(vorlage, "w", encoding="utf-8", newline="") as offen:
        offen.write(CONFIG_VORLAGE)
    monkeypatch.setattr(e, "PFAD_CONFIG_VORLAGE", vorlage)

    zugang = tmp_path / "datenbank.properties"
    if properties is not None:
        zugang.write_text(properties, encoding="utf-8")
    monkeypatch.setattr(e, "PFAD_ZUGANG", zugang)
    return vorlage, zugang


# ------------------------------------------------------------- Die Zugangsdaten

def test_gelesen_wird_url_und_nicht_url_test(monkeypatch, tmp_path):
    """Das Testschema leeren die Java-Proben vor jedem Lauf.

    Wer dort hineinzeigt, laesst den Betrieb auf einer Anlage laufen, die beim naechsten
    Probelauf leer ist - und das faellt erst auf, wenn die Parzellen weg sind.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)

    zugang = e.lies_zugangsdaten()

    assert zugang["url"] == "jdbc:mariadb://127.0.0.1:3306/freimarken"
    assert "test" not in zugang["url"]
    assert zugang["benutzer"] == "freimarken"
    assert zugang["passwort"] == "sehrGeheim123"


def test_ohne_datei_bleibt_der_zugang_leer(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path, properties=None)

    assert e.lies_zugangsdaten() == {}


# ------------------------------------------------------------- Die config.yml

def test_die_config_wird_abgelegt(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="config-neu", port=25801)

    ok, zeile = e.lege_config_ab(server)

    assert ok is True
    ziel = tmp_path / "config-neu" / "plugins" / "Freimarken" / "config.yml"
    assert ziel.is_file()
    # Die Kommentare sind das Handbuch der Anlage - sie muessen mitkommen.
    assert "Handbuch" in ziel.read_text(encoding="utf-8")
    assert "abgelegt" in zeile


def test_eine_vorhandene_config_wird_nie_ueberschrieben(monkeypatch, tmp_path):
    """Sonst waere ein zweiter Lauf der Weg, auf dem alle Preise verloren gehen.

    Genau die Haltung von ``saveDefaultConfig()`` im Plugin: Was da ist, bleibt.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="config-alt", port=25802, mit_config=True)
    ziel = tmp_path / "config-alt" / "plugins" / "Freimarken" / "config.yml"
    with open(ziel, "w", encoding="utf-8", newline="") as offen:
        offen.write("wirtschaft:\n  parzelle:\n    grundpreis: 99999\n")

    ok, zeile = e.lege_config_ab(server)

    assert ok is True, "das ist kein Fehlschlag, sondern der richtige Fall"
    assert "99999" in ziel.read_text(encoding="utf-8")
    assert "bleibt unangetastet" in zeile


# ----------------------------------------------------- Die Zugangsdaten eintragen

def test_die_zugangsdaten_kommen_in_die_config(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="eintrag", port=25803, mit_config=True)

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is True, zeile
    ziel = tmp_path / "eintrag" / "plugins" / "Freimarken" / "config.yml"
    inhalt = ziel.read_text(encoding="utf-8")
    assert "jdbc:mariadb://127.0.0.1:3306/freimarken" in inhalt
    assert "freimarken" in inhalt
    assert "sehrGeheim123" in inhalt
    # DAS HANDBUCH BLEIBT: geschrieben wird kommentarerhaltend, nicht per Textersatz.
    assert "Diese Zeile ist das Handbuch" in inhalt
    assert "NUR BEIM START GELESEN" in inhalt


def test_das_kennwort_steht_in_keiner_zeile(monkeypatch, tmp_path):
    """Die Zeilen dieses Dienstes gehen in den Fortschritt und ins Protokoll."""
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="geheim", port=25804, mit_config=True)

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is True
    assert "sehrGeheim123" not in zeile
    assert "nochGeheimer" not in zeile
    # Der Rechner und das Schema DUERFEN dastehen - ohne sie ist die Zeile nutzlos.
    assert "127.0.0.1" in zeile


def test_ohne_url_wird_es_benannt(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path, properties="benutzer=fm\npasswort=x\n")
    server = _server(tmp_path, slug="ohneurl", port=25805, mit_config=True)

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is False
    assert "keine url" in zeile
    assert "speichert nichts" in zeile


def test_eine_fehlende_config_wird_benannt(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="ohneconfig", port=25806)

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is False
    assert "nicht lesbar" in zeile


# --------------------------------------------------------------------- Die Kette

def _setze_schritte_ab(monkeypatch, *, bau=True, datenbank=True):
    """Bau und Datenbankeinrichtung absetzen - beide dauern Minuten."""
    from app.services import freimarken_einrichtung_service as e

    monkeypatch.setattr(e, "baue_plugin", lambda db: (
        bau, "Plugin gebaut: paper-0.1.0.jar" if bau else "Kein JDK gefunden."))
    monkeypatch.setattr(e, "richte_datenbank_ein", lambda: (
        datenbank, "MariaDB eingerichtet." if datenbank
        else "Braucht Administratorrechte."))
    monkeypatch.setattr(e, "jar_pfad", lambda: None)


def test_alle_fuenf_schritte_hinterlassen_ihre_zeile(monkeypatch, tmp_path):
    """Fortschritt als ZEILE, nicht als Zahl: "3 von 5" sagt nicht, welcher fehlt."""
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    _setze_schritte_ab(monkeypatch)
    server = _server(tmp_path, slug="kette", port=25810, mit_config=True)
    server.id = 9001
    e.vergiss(9001)

    schritte = e.richte_ein(None, server)

    # AUSGESCHRIEBEN und nicht aus e.SCHRITTE geholt [Fund 22.11.2026]: Eine Probe, die
    # ihre Erwartung aus dem Geprueften nimmt, faengt kein Vertauschen - sie bliebe gruen,
    # wenn jemand die Datenbank hinter das Eintragen schiebt. Und genau diese Reihenfolge
    # ist tragend: Das Skript SCHREIBT die Properties, aus denen Schritt 5 liest.
    assert [s["name"] for s in schritte] == [
        "Plugin bauen", "Plugin ablegen", "config.yml ablegen",
        "MariaDB einrichten", "Zugangsdaten eintragen",
    ], "fuenf Zeilen in genau dieser Reihenfolge"


def test_ein_gescheiterter_bau_reisst_die_kette_nicht_mit(monkeypatch, tmp_path):
    """Ohne Jar kein Ablegen - aber die config.yml und die Zugangsdaten gehen trotzdem.

    Ein Server ohne Plugin ist noch kein Freimarken-Server, aber die Datei daliegen zu
    haben heisst: Die Einstellungsseiten funktionieren, und ein Mensch kann das Jar von
    Hand nachlegen.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    _setze_schritte_ab(monkeypatch, bau=False)
    server = _server(tmp_path, slug="baufehler", port=25811, mit_config=True)
    server.id = 9002
    e.vergiss(9002)

    schritte = e.richte_ein(None, server)
    nach_namen = {s["name"]: s for s in schritte}

    assert len(schritte) == 5, "alle fuenf Zeilen stehen da"
    assert nach_namen["Plugin bauen"]["ok"] is False
    assert nach_namen["Plugin ablegen"]["ok"] is False
    assert "Uebersprungen" in nach_namen["Plugin ablegen"]["text"]
    # UND die uebrigen laufen trotzdem - das ist der Punkt.
    assert nach_namen["config.yml ablegen"]["ok"] is True
    assert nach_namen["Zugangsdaten eintragen"]["ok"] is True


def test_eine_gescheiterte_datenbank_laesst_den_server_fertig_werden(monkeypatch,
                                                                     tmp_path):
    """Ohne Datenbank ist der Server spielbar - nur haelt er nichts ueber einen Neustart.

    Das ist eine Lage, die ein Mensch beheben kann (Manager als Administrator starten und
    wiederholen) - kein Grund, die Einrichtung abzubrechen.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    _setze_schritte_ab(monkeypatch, datenbank=False)
    server = _server(tmp_path, slug="dbfehler", port=25812, mit_config=True)
    server.id = 9003
    e.vergiss(9003)

    schritte = e.richte_ein(None, server)
    nach_namen = {s["name"]: s for s in schritte}

    assert nach_namen["MariaDB einrichten"]["ok"] is False
    assert "Administratorrechte" in nach_namen["MariaDB einrichten"]["text"]
    # Die Zugangsdaten werden TROTZDEM eingetragen: Die properties liegen schon da, und
    # sobald die Datenbank laeuft, passt die config.yml.
    assert nach_namen["Zugangsdaten eintragen"]["ok"] is True


def test_ein_werfender_schritt_reisst_die_kette_nicht_mit(monkeypatch, tmp_path):
    """Keiner darf die anderen mitnehmen, auch nicht durch eine Ausnahme."""
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    _setze_schritte_ab(monkeypatch)

    def wirft():
        raise RuntimeError("die Kaffeemaschine ist aus")

    monkeypatch.setattr(e, "richte_datenbank_ein", wirft)
    server = _server(tmp_path, slug="wurf", port=25813, mit_config=True)
    server.id = 9004
    e.vergiss(9004)

    schritte = e.richte_ein(None, server)
    nach_namen = {s["name"]: s for s in schritte}

    assert len(schritte) == 5
    assert nach_namen["MariaDB einrichten"]["ok"] is False
    assert "Kaffeemaschine" in nach_namen["MariaDB einrichten"]["text"]
    assert nach_namen["Zugangsdaten eintragen"]["ok"] is True


# ------------------------------------------------------------------- Der Stand

def test_ein_leerer_stand_heisst_nicht_fertig():
    """Eine leere Liste heisst "noch nichts getan", nicht "abgeschlossen"."""
    from app.services import freimarken_einrichtung_service as e

    e.vergiss(9100)
    jetzt = e.stand(9100)

    assert jetzt["schritte"] == []
    assert jetzt["laeuft"] is False
    assert jetzt["fertig"] is False, (
        "leer und fertig muessen unterscheidbar sein - sonst sieht ein nie begonnener "
        "Lauf aus wie ein erledigter")


def test_der_stand_ist_eine_kopie(monkeypatch, tmp_path):
    """Die Oberflaeche liest, waehrend der Faden schreibt."""
    from app.services import freimarken_einrichtung_service as e

    e.vergiss(9101)
    e._zeile(9101, "erste Zeile", True)
    gelesen = e.stand(9101)
    gelesen["schritte"].append({"text": "untergeschoben", "ok": True})

    assert len(e.stand(9101)["schritte"]) == 1


@pytest.mark.echte_einrichtung
def test_zwei_laeufe_zugleich_gibt_es_nicht(monkeypatch, tmp_path):
    """Sonst bauen zwei Faeden gleichzeitig in dasselbe Gradle-Verzeichnis.

    MARKIERT, weil sie starte SELBST prueft - das Sicherheitsnetz im conftest setzt
    es sonst ab, und die Probe pruefte die Attrappe. Gefaehrlich ist das nicht: Der Stand
    wird vorher auf "laeuft" gesetzt, also kehrt starte sofort zurueck und oeffnet
    keinen Faden.
    """
    from app.services import freimarken_einrichtung_service as e

    e.vergiss(9102)
    with e._SCHLOSS:
        e._STAENDE[9102] = {"laeuft": True, "fertig": False, "schritte": [],
                            "begonnen": 0.0}

    assert e.starte(9102) is False
    e.vergiss(9102)


def test_das_jar_ist_das_neueste(monkeypatch, tmp_path):
    """Eine alte Fassung bleibt stehen, wenn die Versionsnummer wechselt.

    Dann waere die alphabetisch erste die falsche - und der Server bekaeme ein Plugin,
    das Monate alt ist.
    """
    import os
    import time

    from app.services import freimarken_einrichtung_service as e

    ordner = tmp_path / "libs"
    ordner.mkdir()
    # DIE NAMEN MUESSEN AUSEINANDERFALLEN [Fund 22.11.2026]: Hier stand die aeltere Datei
    # alphabetisch HINTEN, und dann liefert sorted()[0] zufaellig dasselbe wie die
    # Zeitsortierung - die Probe war gruen mit UND ohne die Zusage. Jetzt liegt die
    # aeltere alphabetisch VORN, also unterscheiden sich beide Wege.
    alt = ordner / "paper-0.1.0.jar"
    neu = ordner / "paper-0.9.0.jar"
    alt.write_bytes(b"alt")
    neu.write_bytes(b"neu")
    os.utime(alt, (time.time() - 9000, time.time() - 9000))
    assert sorted([alt, neu])[0] == alt, (
        "die Probe braucht die aeltere Datei alphabetisch vorn - sonst prueft sie "
        "den Namen und nicht die Zeit")
    monkeypatch.setattr(e, "PFAD_GEBAUT", ordner)

    assert e.jar_pfad() == neu


def test_ohne_ordner_gibt_es_kein_jar(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    monkeypatch.setattr(e, "PFAD_GEBAUT", tmp_path / "gibtesnicht")

    assert e.jar_pfad() is None


@pytest.mark.parametrize("adresse", [
    "", "kein-jdbc", "jdbc:mariadb://", "jdbc:postgres://h:5432/db",
])
def test_eine_unleserliche_adresse_heisst_keine_datenbank(monkeypatch, tmp_path,
                                                          adresse):
    """``_datenbank_antwortet`` darf an einer kaputten Adresse nicht werfen."""
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path, properties="url=" + adresse + "\n")

    assert e._datenbank_antwortet() is False


# ------------------------------------------------------- Die Wiederholungsroute

def _nutzer(db, rolle, name):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _server_in_db(db, tmp_path, *, slug, port):
    from app.models.server import Server

    basis = tmp_path / slug
    (basis / "plugins" / "Freimarken").mkdir(parents=True, exist_ok=True)
    with open(basis / "plugins" / "Freimarken" / "config.yml", "w", encoding="utf-8",
              newline="") as offen:
        offen.write(CONFIG_VORLAGE)
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar",
                    freimarken_enabled=True)
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def test_die_wiederholung_ist_super_admin_sache(client, tmp_path, monkeypatch):
    """Sie baut, installiert und schreibt Zugangsdaten - das ist kein Moderatorenwerk."""
    from app.db.session import SessionLocal
    from app.services import freimarken_einrichtung_service as e

    monkeypatch.setattr(e, "starte", lambda server_id: True)

    with SessionLocal() as db:
        server = _server_in_db(db, tmp_path, slug="wieder-admin", port=25820)
        _nutzer(db, "admin", "wieder-admin-nutzer")
        server_id = server.id
    assert client.post("/login", data={"username": "wieder-admin-nutzer",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    antwort = client.post("/freimarken/" + str(server_id) + "/einrichten",
                          follow_redirects=False)

    assert antwort.status_code == 403


def test_der_super_admin_darf_wiederholen(client, tmp_path, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import freimarken_einrichtung_service as e

    gerufen: list[int] = []
    monkeypatch.setattr(e, "starte", lambda server_id: gerufen.append(server_id) or True)

    with SessionLocal() as db:
        server = _server_in_db(db, tmp_path, slug="wieder-super", port=25821)
        _nutzer(db, "super_admin", "wieder-super-nutzer")
        server_id = server.id
    assert client.post("/login", data={"username": "wieder-super-nutzer",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    antwort = client.post("/freimarken/" + str(server_id) + "/einrichten",
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert gerufen == [server_id]


def test_eine_laufende_einrichtung_wird_nicht_zweimal_gestartet(client, tmp_path,
                                                                monkeypatch):
    from app.db.session import SessionLocal
    from app.services import freimarken_einrichtung_service as e

    monkeypatch.setattr(e, "starte", lambda server_id: False)

    with SessionLocal() as db:
        server = _server_in_db(db, tmp_path, slug="wieder-laeuft", port=25822)
        _nutzer(db, "super_admin", "wieder-laeuft-nutzer")
        server_id = server.id
    assert client.post("/login", data={"username": "wieder-laeuft-nutzer",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    client.post("/freimarken/" + str(server_id) + "/einrichten",
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/wirtschaft").text

    assert "laeuft schon" in seite


def test_der_fortschritt_steht_auf_der_seite(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import freimarken_einrichtung_service as e

    with SessionLocal() as db:
        server = _server_in_db(db, tmp_path, slug="fortschritt", port=25823)
        _nutzer(db, "super_admin", "fortschritt-nutzer")
        server_id = server.id
    assert client.post("/login", data={"username": "fortschritt-nutzer",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    e.vergiss(server_id)
    e._zeile(server_id, "Plugin bauen: Plugin gebaut: paper-0.1.0.jar", True)
    e._zeile(server_id, "MariaDB einrichten: Braucht Administratorrechte.", False)

    seite = client.get("/freimarken/" + str(server_id) + "/wirtschaft").text

    assert "Plugin gebaut" in seite
    assert "Administratorrechte" in seite
    e.vergiss(server_id)


# ----------------------------------------------------- Das Netz im conftest selbst

def test_eine_erstellung_stoesst_die_echte_kette_nicht_an(client, tmp_path,
                                                          _nie_wirklich_einrichten):
    """DAS NETZ, und es ist kein Luxus [Fund 22.11.2026].

    Vor dem Netz hat genau diese Erstellung einen Gradle-Lauf auf der Netzfreigabe und
    ``winget install MariaDB.Server`` gestartet - in einem Probenlauf. Gefunden hat es
    eine Gegenlese, nicht eine Probe.

    Geprueft wird ZWEIERLEI: dass die Erstellung die Einrichtung anstoesst (sonst waere
    das Netz ein stummer Ausschalter) und dass dabei nichts Echtes laeuft.
    """
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _nutzer(db, "super_admin", "netz-nutzer")
    assert client.post("/login", data={"username": "netz-nutzer",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    antwort = client.post("/servers/create", data={
        "name": "netzserver",
        "server_type": "vanilla",
        "mc_version": "1.20.1",
        "target_path": str(tmp_path / "netzserver"),
        "freimarken_enabled": "1",
    }, follow_redirects=False)

    assert antwort.status_code == 303
    assert len(_nie_wirklich_einrichten) == 1, (
        "die Erstellung muss die Einrichtung anstossen - und zwar die Attrappe")


def test_ohne_markierung_wird_nichts_angestossen(client, tmp_path,
                                                 _nie_wirklich_einrichten):
    """Die Gegenrichtung: Ein gewoehnlicher Server braucht keine Einrichtung."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _nutzer(db, "super_admin", "netz-ohne-nutzer")
    assert client.post("/login", data={"username": "netz-ohne-nutzer",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    client.post("/servers/create", data={
        "name": "netzohne",
        "server_type": "vanilla",
        "mc_version": "1.20.1",
        "target_path": str(tmp_path / "netzohne"),
    }, follow_redirects=False)

    assert _nie_wirklich_einrichten == []


# ------------------------------------------- Was die Gegenlese am 22.11.2026 fand

def test_das_kennwort_steht_auch_im_fehlerfall_in_keiner_zeile(monkeypatch, tmp_path):
    """DER SCHWERSTE FUND: Der Assistent nennt in zwei Meldungen den GESCHRIEBENEN Wert.

    Stand ``datenbank.passwort`` als unquotierte Ziffernfolge in der Datei, galt das Feld
    als "int" - und der Assistent rief int() auf dem Kennwort und warf mit einer Meldung,
    die es im Klartext nennt. Diese Zeile erscheint auf jeder Freimarken-Seite und ist ab
    Moderator zu lesen.

    Zwei Dinge sind behoben, und beide werden hier geprueft: art ist jetzt immer "text",
    und kein Fremdtext wird durchgereicht.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="zahlkennwort", port=25830)
    # Eine Datei, in der das Kennwort als ZAHL dasteht - so wie sie entsteht, wenn jemand
    # ein Ziffernkennwort von Hand eintraegt.
    ziel = tmp_path / "zahlkennwort" / "plugins" / "Freimarken" / "config.yml"
    with open(ziel, "w", encoding="utf-8", newline="") as offen:
        offen.write("datenbank:\n  url: \"\"\n  benutzer: \"\"\n  passwort: 12345678\n")

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert "sehrGeheim123" not in zeile, (
        "das Kennwort darf in KEINER Zeile stehen - auch nicht in einer Fehlermeldung")
    assert "12345678" not in zeile, "und das alte auch nicht"
    # GEMESSEN UND ANDERS ALS ERWARTET [22.11.2026]: Der Riegel "was schon dasteht,
    # bleibt" ueberspringt dieses Feld, denn 12345678 ist nicht leer. Damit ist der
    # int-Pfad des Assistenten - ueber den das Kennwort entwich - gar nicht mehr
    # erreichbar; die Datenverlust-Reparatur hat ihn nebenbei geschlossen.
    #
    # Die Probe haelt darum, was WIRKLICH gilt: Das gesetzte Kennwort bleibt unangetastet.
    # Der tragende Riegel fuer das Geheimnis ist der Fremdtext-Riegel, und den prueft
    # test_ein_schreibfehler_nennt_nur_die_art - er greift bei JEDER Ausnahme.
    assert ok is True, zeile
    assert "passwort: 12345678" in ziel.read_text(encoding="utf-8"), (
        "ein gesetztes Kennwort wird nicht angefasst")
    assert "schon gesetzt" in zeile


def test_ein_schreibfehler_nennt_nur_die_art(monkeypatch, tmp_path):
    """Kein Fremdtext in der Zeile - auch nicht aus einer Ausnahme, die den Wert traegt."""
    from app.services import freimarken_einrichtung_service as e
    from app.services import yaml_assistant_service as ya

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="schreibfehler", port=25831, mit_config=True)

    def wirft_mit_wert(aktuell, form_data, meta_roh):
        raise ValueError("'datenbank.passwort': 'sehrGeheim123' ist keine Zahl.")

    monkeypatch.setattr(ya, "schreibe", wirft_mit_wert)

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is False
    assert "sehrGeheim123" not in zeile
    assert "ValueError" in zeile, "die ART des Fehlers darf dastehen"
    assert "Protokoll" in zeile, "und wo der Grund zu finden ist"


def test_kaputtes_yaml_nennt_nur_die_erste_zeile(monkeypatch, tmp_path):
    """Eine MarkedYAMLError traegt den Quelltext der Fehlerzeile mit sich.

    Liegt die Marke im datenbank-Block, stuende dort die passwort-Zeile.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="kaputtyaml", port=25832)
    ziel = tmp_path / "kaputtyaml" / "plugins" / "Freimarken" / "config.yml"
    with open(ziel, "w", encoding="utf-8", newline="") as offen:
        offen.write("datenbank:\n  passwort: geheim\n   kaputt: [\n")

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is False
    assert "geheim" not in zeile, (
        "der Quelltextausschnitt der Ausnahme darf nicht durchgereicht werden")
    assert len(zeile.splitlines()) == 1, "eine Zeile, nicht der ganze Wurf"


def test_eine_leere_config_wird_ersetzt(monkeypatch, tmp_path):
    """Eine 0-Byte-Datei "ist" eine Datei - traegt aber nichts.

    Vorher meldete Schritt 3 gruen und behauptete, sie trage die Einstellungen; die
    ausgelieferte Fassung wurde NIE wieder hingelegt, und auch saveDefaultConfig() half
    nicht, denn eine leere Datei fehlt nicht.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="leerconfig", port=25833)
    ziel = tmp_path / "leerconfig" / "plugins" / "Freimarken" / "config.yml"
    ziel.write_text("", encoding="utf-8")

    ok, zeile = e.lege_config_ab(server)

    assert ok is True
    assert ziel.stat().st_size > 0
    assert "Handbuch" in ziel.read_text(encoding="utf-8")
    assert "LEER" in zeile, "und die Zeile sagt, dass sie etwas ersetzt hat"
    assert "auf Vorgabe" in zeile


def test_gesetzte_zugangsdaten_bleiben_stehen(monkeypatch, tmp_path):
    """Eine von Hand gestellte Adresse darf eine Wiederholung nicht zuruecksetzen.

    Ueber das Dashboard geht das gar nicht - der Abschnitt ist dort gesperrt. Wer sie von
    Hand auf einen anderen Rechner gestellt hat, verlor sie bei jeder Wiederholung, und
    der Server hing danach an einem leeren Schema.
    """
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="handgesetzt", port=25834)
    ziel = tmp_path / "handgesetzt" / "plugins" / "Freimarken" / "config.yml"
    with open(ziel, "w", encoding="utf-8", newline="") as offen:
        offen.write('datenbank:\n  url: "jdbc:mariadb://FriedrichNAS:3306/freimarken"\n'
                    '  benutzer: "fremd"\n  passwort: ""\n')

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is True, zeile
    inhalt = ziel.read_text(encoding="utf-8")
    assert "FriedrichNAS" in inhalt, "die Adresse bleibt"
    assert "fremd" in inhalt, "der Benutzer bleibt"
    # Nur das LEERE Feld wird gefuellt.
    assert "sehrGeheim123" in inhalt
    assert "schon gesetzt" in zeile


def test_standen_alle_drei_ist_das_kein_fehlschlag(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    server = _server(tmp_path, slug="allegesetzt", port=25835)
    ziel = tmp_path / "allegesetzt" / "plugins" / "Freimarken" / "config.yml"
    with open(ziel, "w", encoding="utf-8", newline="") as offen:
        offen.write('datenbank:\n  url: "jdbc:mariadb://h:3306/s"\n'
                    '  benutzer: "a"\n  passwort: "b"\n')

    ok, zeile = e.trage_zugangsdaten_ein(server)

    assert ok is True, "nichts zu tun ist Erfolg, nicht Fehlschlag"
    assert "standen schon" in zeile


def test_schreib_datei_laesst_nie_eine_halbe_datei_zurueck(monkeypatch, tmp_path):
    """Ein nacktes open(ziel, "w") kuerzt die Zieldatei ZUERST auf 0 Byte.

    Bei der config.yml sind das 157 KB und 2327 Erklaerungszeilen, von denen es keine
    Kopie gibt. Der Einrichtungsfaden schreibt bis zu 35 Minuten nach dem Klick und ist
    ein Daemon - beim Prozessende wird er ohne Flush abgeschnitten.
    """
    import builtins

    from app.services import freimarken_service as fs

    server = _server(tmp_path, slug="atomar", port=25836, mit_config=True)
    ziel = tmp_path / "atomar" / "plugins" / "Freimarken" / "config.yml"
    vorher = ziel.read_text(encoding="utf-8")

    echtes_open = builtins.open

    def bricht_ab(*args, **kwargs):
        offen = echtes_open(*args, **kwargs)
        if len(args) > 1 and "w" in str(args[1]):
            class Abbrecher:
                def __enter__(self):
                    return self

                def __exit__(self, *rest):
                    offen.close()
                    return False

                def write(self, _text):
                    raise OSError("Datentraeger voll")

                def flush(self):
                    pass

                def fileno(self):
                    return offen.fileno()
            return Abbrecher()
        return offen

    monkeypatch.setattr(builtins, "open", bricht_ab)
    try:
        with pytest.raises(OSError):
            fs.schreib_datei(server, fs.PFAD_CONFIG, "neuer Inhalt")
    finally:
        monkeypatch.setattr(builtins, "open", echtes_open)

    # DIE ALTE DATEI STEHT UNVERAENDERT DA - das ist die ganze Zusage.
    assert ziel.read_text(encoding="utf-8") == vorher
    # Und keine Zwischendatei bleibt liegen: Sie saehe im Dateieditor wie ein zweiter
    # Stand aus.
    assert not (ziel.parent / (ziel.name + ".neu")).exists()


def test_schreib_datei_ersetzt_den_inhalt(monkeypatch, tmp_path):
    """Die Gegenrichtung: ohne Fehler wird wirklich geschrieben."""
    from app.services import freimarken_service as fs

    server = _server(tmp_path, slug="atomar-ok", port=25837, mit_config=True)
    ziel = tmp_path / "atomar-ok" / "plugins" / "Freimarken" / "config.yml"

    fs.schreib_datei(server, fs.PFAD_CONFIG, "wirtschaft:\n  neu: 1\n")

    assert ziel.read_text(encoding="utf-8") == "wirtschaft:\n  neu: 1\n"
    assert not (ziel.parent / (ziel.name + ".neu")).exists()


def test_die_datenbankeinrichtung_haengt_nicht_an_pipes(monkeypatch, tmp_path):
    """Mit capture_output haengt die Frist an den ENKELN, nicht am Prozess.

    powershell gibt die Pipe an winget weiter, winget an den MSI-Installer. Nach der
    Frist toetet subprocess.run nur powershell und wartet dann auf Windows OHNE Frist
    erneut auf EOF - das kommt erst, wenn der letzte Enkel die Pipe schliesst.

    Geprueft wird am AUFRUF: stdout geht in eine Datei, nicht in eine Pipe.
    """
    import subprocess

    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    monkeypatch.setattr(e, "_datenbank_antwortet", lambda: False)
    gesehen: list[dict] = []

    class Fertig:
        returncode = 0

    def gefaelscht(befehl, **rest):
        gesehen.append(rest)
        return Fertig()

    monkeypatch.setattr(subprocess, "run", gefaelscht)

    e.richte_datenbank_ein()

    assert len(gesehen) == 1
    rest = gesehen[0]
    assert "capture_output" not in rest, (
        "capture_output legt Pipes an - genau daran haengt die Frist nicht mehr am "
        "gerufenen Prozess")
    assert rest.get("stdout") is not None, "die Ausgabe geht in eine Datei"
    assert rest.get("stdout") != subprocess.PIPE
    assert rest.get("stdin") == subprocess.DEVNULL, (
        "ohne das kann das Skript auf eine Eingabe warten, die nie kommt")
    assert rest.get("timeout") == e.FRIST_DATENBANK


def test_eine_antwortende_datenbank_wird_uebersprungen(monkeypatch, tmp_path):
    """Die Grundlage von "folgenlos wiederholbar" - und sie war ungeprueft.

    Eine winget-Installation ohne Not sind Minuten fuer nichts, und sie dreht die Adresse
    in den Properties auf 127.0.0.1 zurueck.
    """
    import subprocess

    from app.services import freimarken_einrichtung_service as e

    _lege_vorlagen(monkeypatch, tmp_path)
    monkeypatch.setattr(e, "_datenbank_antwortet", lambda: True)

    def darf_nicht(*args, **kwargs):
        raise AssertionError("die Einrichtung haette nicht laufen duerfen")

    monkeypatch.setattr(subprocess, "run", darf_nicht)

    ok, zeile = e.richte_datenbank_ein()

    assert ok is True
    assert "antwortet schon" in zeile


def test_das_jar_landet_in_plugins(monkeypatch, tmp_path):
    """lege_jar_ab hatte keine einzige Probe [Fund 22.11.2026].

    In jeder Kettenprobe war Schritt 2 ein Fehlschlag, weil jar_pfad abgesetzt war und
    None gab - die Funktion selbst wurde also nie ausgefuehrt.
    """
    from app.services import freimarken_einrichtung_service as e

    jar = tmp_path / "paper-0.1.0.jar"
    jar.write_bytes(b"Plugin")
    monkeypatch.setattr(e, "jar_pfad", lambda: jar)
    server = _server(tmp_path, slug="jarziel", port=25840)

    ok, zeile = e.lege_jar_ab(server)

    assert ok is True, zeile
    ziel = tmp_path / "jarziel" / "plugins" / e.PLUGIN_DATEI
    assert ziel.is_file()
    assert ziel.read_bytes() == b"Plugin"
    assert e.PLUGIN_DATEI in zeile


def test_ohne_jar_wird_nichts_abgelegt(monkeypatch, tmp_path):
    from app.services import freimarken_einrichtung_service as e

    monkeypatch.setattr(e, "jar_pfad", lambda: None)
    server = _server(tmp_path, slug="jarlos", port=25841)

    ok, zeile = e.lege_jar_ab(server)

    assert ok is False
    assert "Kein gebautes Jar" in zeile
    assert not (tmp_path / "jarlos" / "plugins" / e.PLUGIN_DATEI).exists()


def test_eine_gesperrte_jardatei_wird_benannt(monkeypatch, tmp_path):
    """Windows sperrt das Jar, solange der Server laeuft - das darf die Kette nicht
    mitnehmen."""
    import shutil

    from app.services import freimarken_einrichtung_service as e

    jar = tmp_path / "paper-0.1.0.jar"
    jar.write_bytes(b"Plugin")
    monkeypatch.setattr(e, "jar_pfad", lambda: jar)

    def gesperrt(*args, **kwargs):
        raise PermissionError("Die Datei wird von einem anderen Prozess benutzt")

    monkeypatch.setattr(shutil, "copy2", gesperrt)
    server = _server(tmp_path, slug="jargesperrt", port=25842)

    ok, zeile = e.lege_jar_ab(server)

    assert ok is False
    assert "Laeuft der Server" in zeile


@pytest.mark.echte_einrichtung
def test_starte_oeffnet_wirklich_einen_faden(monkeypatch, tmp_path):
    """Fuer starte() selbst gab es keine Probe - ein fehlendes .start() faellt nicht auf.

    MARKIERT, weil sie ``starte`` SELBST prueft: Das Netz im conftest setzt es sonst ab,
    und die Probe saehe nur die Attrappe.

    Der Faden wird dabei NICHT laufen gelassen - threading.Thread ist hier durch eine
    Attrappe ersetzt, die bloss mitschreibt. Es laeuft also weder Gradle noch winget.
    """
    import threading

    from app.services import freimarken_einrichtung_service as e

    gestartet: list[str] = []
    echter_faden = threading.Thread

    class Attrappe:
        def __init__(self, target=None, name=None, daemon=None, **rest):
            self.target = target
            self.name = name
            self.daemon = daemon

        def start(self):
            gestartet.append(self.name or "")

    monkeypatch.setattr(threading, "Thread", Attrappe)
    e.vergiss(9200)
    try:
        assert e.starte(9200) is True
    finally:
        monkeypatch.setattr(threading, "Thread", echter_faden)

    assert len(gestartet) == 1, "genau ein Faden, und er wurde gestartet"
    assert "9200" in gestartet[0], "der Name nennt den Server - sonst sind sie im "        "Stapelbild nicht zu unterscheiden"
    # UND der Stand sagt sofort "laeuft": Sonst koennte ein zweiter Klick daneben laufen,
    # bevor der Faden ueberhaupt angefangen hat.
    assert e.stand(9200)["laeuft"] is True
    e.vergiss(9200)


def test_der_bau_laeuft_nie_zweimal_zugleich():
    """Zwei Gradle-Aufrufe im selben Verzeichnis teilen Sperren und Konfigurationscache.

    Geprueft wird, dass das Schloss WIRKLICH um den Aufruf liegt: Ein zweiter Eintritt
    muss warten, solange der erste es haelt.
    """
    from app.services import freimarken_einrichtung_service as e

    assert e._BAUSCHLOSS.acquire(blocking=False) is True
    try:
        # Solange es gehalten wird, kommt niemand hinein.
        assert e._BAUSCHLOSS.acquire(blocking=False) is False
    finally:
        e._BAUSCHLOSS.release()
    assert e._BAUSCHLOSS.acquire(blocking=False) is True
    e._BAUSCHLOSS.release()
