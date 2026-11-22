"""Was beim Speichern mit der Datei geschieht - und was nicht.

DIE DATEI IST DAS HANDBUCH
Die config.yml des Plugins besteht zu drei Vierteln aus Kommentaren: 2327 von 3071 Zeilen
erklaeren, was ein Wert bedeutet, welcher Bereich gilt und was ein Neuladen erreicht. Ein
Dashboard, das beim Speichern yaml.safe_dump darueberschreibt, nimmt dem Betreiber genau
das weg, was ihn die Anlage bedienen laesst - und zwar unbemerkt, denn die Werte stimmen
ja noch. Darum pruefen die Proben hier BYTEWEISE und nicht dem Sinn nach.

DER SPRUNGSCHUTZ GEHOERT DEM PLUGIN
Das Plugin verwirft einen Geldwert, der um mehr als Faktor 2 von der ausgelieferten
Vorgabe abweicht, solange wirtschaft.sprung-bestaetigt.<name> nicht "alt -> neu" traegt.
Der Manager baut diesen Riegel nicht nach - er stellt die Rueckfrage und schreibt danach
beide Zeilen. Eine Probe haelt fest, dass ohne Bestaetigung NICHTS geschrieben wird, und
eine zweite, dass mit Bestaetigung BEIDE Zeilen ankommen: Ein Preis ohne seine
Bestaetigung waere eine Zahl, die das Plugin beim naechsten Start wegwirft - das
Dashboard haette "gespeichert" gemeldet und nichts erreicht.
"""
from __future__ import annotations

from pathlib import Path

import pytest


CONFIG = """# Freimarken - Einstellungen
# Diese Zeile muss das Speichern ueberleben.
wirtschaft:
  parzelle:
    # Was eine Parzelle kostet. Ueber Faktor 2 braucht es eine zweite Bestaetigung.
    grundpreis: 25000
    zusammenlegung-je-feld: 10000      # mit nachgestelltem Kommentar
  sprung-bestaetigt:
    grundpreis: ""
    zusammenlegung-je-feld: ""

parzellen:
  # NUR BEIM START GELESEN - die Rangleiter fuehrt den geltenden Wert.
  hoechstzahl: 1
  gruppe-hoechstzahl: 1

handel:
  marktgebuehr-spielershop-kauf-prozent: 0
  bundshop-wort: Bund
  zeilen-je-seite: 10
"""


def _nutzer(db, rolle, name):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _aufbau(client, tmp_path, *, name, port, inhalt=CONFIG, zeilenende="\n"):
    """Server mit Freimarken-Konfiguration, angemeldeter Admin, Pfad zur Datei."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.models.server_permission import ServerPermission

    basis = tmp_path / name
    (basis / "plugins" / "Freimarken").mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    datei = basis / "plugins" / "Freimarken" / "config.yml"
    # newline="" heisst: der Text geht so auf die Platte, wie er hier steht. Nur damit
    # laesst sich spaeter pruefen, dass CRLF CRLF bleibt.
    with open(datei, "w", encoding="utf-8", newline="") as offen:
        offen.write(inhalt.replace("\n", zeilenende) if zeilenende != "\n" else inhalt)

    with SessionLocal() as db:
        person = _nutzer(db, "admin", name)
        server = Server(name=name, slug=name, server_type="paper", mc_version="26.3",
                        base_path=str(basis), port=port, status="stopped",
                        start_mode="command", start_command="java -jar server.jar",
                        freimarken_enabled=True)
        db.add(server)
        db.commit()
        db.refresh(server)
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True,
                               can_console=False, can_restart=False,
                               can_edit_files=True, can_manage=False))
        db.commit()
        server_id = server.id

    assert client.post("/login", data={"username": name, "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303
    return server_id, datei


def _speichern(client, server_id, seite="wirtschaft", **felder):
    daten = {"grund": "Probe"}
    daten.update(felder)
    return client.post("/freimarken/" + str(server_id) + "/" + seite, data=daten,
                       follow_redirects=False)


# ------------------------------------------------- Die Datei bleibt, was sie war

def test_nur_der_geaenderte_wert_aendert_sich(client, tmp_path):
    """Zeile fuer Zeile verglichen: genau EINE darf anders sein."""
    server_id, datei = _aufbau(client, tmp_path, name="einezeile", port=25601)
    vorher = datei.read_text(encoding="utf-8").splitlines()

    _speichern(client, server_id, **{"feld:wirtschaft.parzelle.grundpreis": "30000"})

    nachher = datei.read_text(encoding="utf-8").splitlines()
    assert len(vorher) == len(nachher), "die Zeilenzahl hat sich geaendert"
    anders = [(a, b) for a, b in zip(vorher, nachher) if a != b]
    assert len(anders) == 1, "genau eine Zeile darf anders sein, nicht " + str(len(anders))
    assert anders[0][0].strip() == "grundpreis: 25000"
    assert anders[0][1].strip() == "grundpreis: 30000"


def test_kommentare_bleiben_stehen(client, tmp_path):
    server_id, datei = _aufbau(client, tmp_path, name="kommentare", port=25602)
    _speichern(client, server_id, **{"feld:wirtschaft.parzelle.grundpreis": "30000"})
    inhalt = datei.read_text(encoding="utf-8")
    assert "# Diese Zeile muss das Speichern ueberleben." in inhalt
    assert "# NUR BEIM START GELESEN" in inhalt
    assert "# mit nachgestelltem Kommentar" in inhalt, \
        "auch ein Kommentar HINTER dem Wert gehoert zur Datei"


def test_crlf_bleibt_crlf(client, tmp_path):
    """Sonst macht eine Aenderung an einem Wert aus allen Zeilen eine Aenderung.

    Python wandelt beim Schreiben ohne newline-Angabe jedes \\n in \\r\\n. Das faellt im
    Dashboard nicht auf - wohl aber im naechsten Vergleich der Datei, der dann 3071
    geaenderte Zeilen zeigt statt einer.
    """
    server_id, datei = _aufbau(client, tmp_path, name="crlf", port=25603,
                               zeilenende="\r\n")
    roh_vorher = datei.read_bytes()
    assert roh_vorher.count(b"\r\n") > 10, "die Probe braucht eine CRLF-Datei"

    _speichern(client, server_id, **{"feld:wirtschaft.parzelle.grundpreis": "30000"})

    roh = datei.read_bytes()
    assert roh.count(b"\r\n") == roh_vorher.count(b"\r\n"), \
        "die Zahl der CRLF-Enden hat sich geaendert"
    assert b"\n\n" not in roh.replace(b"\r\n", b"\r\n"), "kein nacktes LF dazugekommen"
    assert b"grundpreis: 30000" in roh


def test_lf_bleibt_lf(client, tmp_path):
    server_id, datei = _aufbau(client, tmp_path, name="lf", port=25604)
    _speichern(client, server_id, **{"feld:wirtschaft.parzelle.grundpreis": "30000"})
    assert b"\r\n" not in datei.read_bytes(), "aus LF ist CRLF geworden"


def test_ohne_aenderung_bleibt_die_datei_byteweise_gleich(client, tmp_path):
    """Wer nichts aendert und speichert, bekommt dieselbe Datei zurueck."""
    server_id, datei = _aufbau(client, tmp_path, name="unberuehrt", port=25605)
    vorher = datei.read_bytes()
    _speichern(client, server_id, **{"feld:wirtschaft.parzelle.grundpreis": "25000"})
    assert datei.read_bytes() == vorher


# ----------------------------------------------------- Der Sprungschutz im Dienst

def test_grosser_sprung_schreibt_zunaechst_nichts(client, tmp_path):
    server_id, datei = _aufbau(client, tmp_path, name="sprung1", port=25606)
    vorher = datei.read_bytes()

    antwort = _speichern(client, server_id,
                         **{"feld:wirtschaft.parzelle.grundpreis": "100000"})

    assert antwort.status_code == 200, "die Rueckfrage ist eine Seite, keine Umleitung"
    assert datei.read_bytes() == vorher, "vor der Bestaetigung darf nichts geschrieben sein"
    assert "25000" in antwort.text and "100000" in antwort.text, \
        "alter und neuer Wert muessen im Klartext in der Rueckfrage stehen"


def test_mit_bestaetigung_werden_beide_zeilen_geschrieben(client, tmp_path):
    """Der Preis UND seine Bestaetigung - sonst verwirft das Plugin den Preis."""
    server_id, datei = _aufbau(client, tmp_path, name="sprung2", port=25607)

    antwort = _speichern(client, server_id, sprung_bestaetigt="1",
                         **{"feld:wirtschaft.parzelle.grundpreis": "100000"})

    assert antwort.status_code == 303
    inhalt = datei.read_text(encoding="utf-8")
    assert "grundpreis: 100000" in inhalt
    assert '"25000 -> 100000"' in inhalt or "'25000 -> 100000'" in inhalt, \
        "die Bestaetigungszeile fehlt: " + inhalt


def test_kleine_aenderung_fragt_nicht_nach(client, tmp_path):
    """Genau das Doppelte ist noch erlaubt - die Schranke beginnt darueber.

    Dieselbe Grenze wie Sprungregel.grosserSprungNachOben im Plugin. Lieferte der Manager
    hier eine andere Antwort als das Plugin, kaeme entweder eine Rueckfrage, die nichts
    bewirkt, oder keine, wo das Plugin eine braucht.
    """
    server_id, datei = _aufbau(client, tmp_path, name="sprung3", port=25608)
    antwort = _speichern(client, server_id,
                         **{"feld:wirtschaft.parzelle.grundpreis": "50000"})
    assert antwort.status_code == 303, "genau das Doppelte braucht keine Rueckfrage"
    assert "grundpreis: 50000" in datei.read_text(encoding="utf-8")


def test_knapp_ueber_dem_doppelten_fragt_nach(client, tmp_path):
    """Die Gegenprobe zur Grenze: eins mehr, und die Rueckfrage kommt.

    Ohne dieses Paar waere nicht gemessen, dass die Grenze ueberhaupt an dieser Stelle
    liegt - eine Schranke, die nie getroffen wird, ist von keiner unterscheidbar.
    """
    server_id, _ = _aufbau(client, tmp_path, name="sprung4", port=25609)
    antwort = _speichern(client, server_id,
                         **{"feld:wirtschaft.parzelle.grundpreis": "50001"})
    assert antwort.status_code == 200, "eins ueber dem Doppelten muss nachfragen"


def test_auch_nach_unten_wird_gefragt(client, tmp_path):
    """Bei Geld ist die gefaehrliche Richtung die nach unten: Ein zu billiger Preis macht
    Mark, und die liegen binnen Minuten auf vielen Konten."""
    server_id, _ = _aufbau(client, tmp_path, name="sprung5", port=25610)
    antwort = _speichern(client, server_id,
                         **{"feld:wirtschaft.parzelle.grundpreis": "1000"})
    assert antwort.status_code == 200
    assert "1000" in antwort.text


def test_genau_die_haelfte_fragt_nicht_nach(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="sprung6", port=25611)
    antwort = _speichern(client, server_id,
                         **{"feld:wirtschaft.parzelle.grundpreis": "12500"})
    assert antwort.status_code == 303, "genau die Haelfte ist noch erlaubt"


# ------------------------------------------------------------ Gesperrte Felder

def test_gesperrtes_feld_wird_uebergangen_und_gemeldet(client, tmp_path):
    """parzellen.hoechstzahl ist Klasse 3 nach Plan 12a.6 - sichtbar, aber nicht aenderbar.

    Im Formular steht es als disabled und wird vom Browser nicht mitgesendet. Diese Probe
    geht am Formular vorbei und schickt es trotzdem: Das Verstecken ist Bequemlichkeit,
    der Riegel liegt im Dienst.
    """
    server_id, datei = _aufbau(client, tmp_path, name="gesperrt", port=25612)
    vorher = datei.read_bytes()

    antwort = _speichern(client, server_id, seite="wirtschaft",
                         **{"feld:parzellen.hoechstzahl": "99"})

    assert antwort.status_code == 303
    assert datei.read_bytes() == vorher, "ein gesperrtes Feld darf nie geschrieben werden"


def test_gesperrtes_feld_steht_mit_begruendung_auf_der_seite(client, tmp_path):
    """Eine gesperrte Einstellung mit Begruendung ist freundlicher als eine fehlende,
    weil sie die Frage beantwortet, bevor sie gestellt wird."""
    server_id, _ = _aufbau(client, tmp_path, name="gesperrt2", port=25613)
    antwort = client.get("/freimarken/" + str(server_id) + "/wirtschaft")
    assert antwort.status_code == 200
    assert "hoechstzahl" in antwort.text
    assert "Nicht aenderbar" in antwort.text
    assert "Rangleiter" in antwort.text, "der Grund muss dastehen"


def test_nur_beim_start_wird_angezeigt(client, tmp_path):
    """Eine Einstellung, die ein Neuladen nicht erreicht, muss das sagen - sonst aendert
    jemand einen Wert, laedt neu und sucht den Fehler woanders."""
    server_id, _ = _aufbau(client, tmp_path, name="startmarke", port=25614)
    antwort = client.get("/freimarken/" + str(server_id) + "/wirtschaft")
    assert "Nur beim Start gelesen" in antwort.text


# --------------------------------------------------------------- Kaputte Datei

def test_kaputte_datei_wird_gemeldet_und_nicht_angefasst(client, tmp_path):
    """Ein YAML-Fehler darf keine 500 geben und nichts ueberschreiben.

    Wer die Datei im Dateieditor halb bearbeitet hat, soll hier einen Satz lesen und
    nicht einen Serverfehler - und die Datei soll reparierbar bleiben.
    """
    kaputt = "wirtschaft:\n  parzelle:\n   grundpreis: [offen\n"
    server_id, datei = _aufbau(client, tmp_path, name="kaputt", port=25615,
                               inhalt=kaputt)
    vorher = datei.read_bytes()

    antwort = client.get("/freimarken/" + str(server_id) + "/wirtschaft")
    assert antwort.status_code == 200
    assert "kein gueltiges YAML" in antwort.text
    assert datei.read_bytes() == vorher


def test_json_zwilling_liefert_dieselben_felder(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="jsonweg", port=25616)
    antwort = client.get("/api/freimarken/" + str(server_id) + "/wirtschaft")
    assert antwort.status_code == 200
    daten = antwort.json()
    pfade = {feld["pfad"] for gruppe in daten["gruppen"] for feld in gruppe["felder"]}
    assert "wirtschaft.parzelle.grundpreis" in pfade
    assert "parzellen.hoechstzahl" in pfade
    # Der Sprungschluessel reist mit, damit ein Werkzeug weiss, was eine Aenderung braucht.
    for gruppe in daten["gruppen"]:
        for feld in gruppe["felder"]:
            if feld["pfad"] == "wirtschaft.parzelle.grundpreis":
                assert feld["sprung"] == "grundpreis"
                assert feld["erklaerung"], "die Kommentarzeilen muessen mitkommen"


# ------------------------------------------ Was NICHT in die Datei gehoert

def test_feld_ohne_vorsatz_wird_nicht_geschrieben(client, tmp_path):
    """Nur Formularfelder mit dem Vorsatz "feld:" sind Einstellungen.

    GEMESSEN AM 22.11.2026: Die erste Fassung dieser Probe schickte "grund" und
    "irgendwas" und blieb gruen, auch nachdem der Vorsatz-Filter im Router entfernt war -
    denn der Dienst schreibt ohnehin nur Pfade, die in der Datei schon stehen, und
    "grund" steht dort nicht. Zwei Riegel fuer denselben Fall, und die Probe sah keinen
    von beiden.

    Diesen Zustand stellt die Probe jetzt her: ein Feldname, der OHNE den Vorsatz ein
    gueltiger Pfad ist. Mit dem Filter wird er verworfen; ohne ihn wuerde er geschrieben.
    Damit trennt die Probe die beiden Riegel - und haelt zugleich fest, dass ein fremd
    gebautes Formular keine Werte setzen kann, die diese Oberflaeche nie anbietet.
    """
    server_id, datei = _aufbau(client, tmp_path, name="ohnevorsatz", port=25617)
    vorher = datei.read_bytes()

    # 30000 und nicht 99999: Gegen die 25000 der Datei waere 99999 ein grosser Sprung,
    # die Rueckfrage kaeme, und geschrieben wuerde ohnehin nichts - die Probe waere
    # gruen, ohne den Riegel beruehrt zu haben. Derselbe Fehler wie beim ersten Versuch.
    client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                data={"wirtschaft.parzelle.grundpreis": "30000", "grund": "Probe"},
                follow_redirects=False)

    assert datei.read_bytes() == vorher, (
        "ein Formularfeld ohne den Vorsatz feld: darf keine Einstellung aendern")


def test_unbekannter_pfad_wird_nicht_angelegt(client, tmp_path):
    """Der zweite Riegel, fuer sich geprueft: ein Pfad, den die Datei nicht hat.

    Der Dienst erfindet keine Schluessel. Taete er es, stuende nach dem Speichern eine
    Einstellung in der config.yml, die das Plugin nicht kennt - und es klagte beim
    naechsten Start ueber sie.
    """
    server_id, datei = _aufbau(client, tmp_path, name="erfunden", port=25618)
    vorher = datei.read_bytes()

    antwort = client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                          data={"feld:wirtschaft.gibtsnicht": "1", "grund": "Probe"},
                          follow_redirects=True)

    assert datei.read_bytes() == vorher
    assert b"gibtsnicht" not in datei.read_bytes()
    # Geprueft wird der TEXT dieser Meldung und nicht blosses Vorkommen des Pfades: Faellt
    # der Riegel im Dienst, wirft der YAML-Assistent weiter unten und meldet "steht nicht
    # mehr dort, wo es beim Oeffnen stand" - darin steht der Pfad ebenfalls. Eine Probe
    # auf den Pfad allein waere also mit UND ohne Riegel gruen.
    assert "gibt es in der Datei nicht" in antwort.text, (
        "der unbekannte Pfad muss als solcher gemeldet werden, nicht als Schreibfehler")
    assert "gibtsnicht" in antwort.text, "und mit Namen"


# ------------------------------------------------------- Die Rechnung fuer sich

@pytest.mark.parametrize("alt,neu,erwartet", [
    ("25000", "50000", False),    # genau das Doppelte - erlaubt
    ("25000", "50001", True),
    ("25000", "12500", False),    # genau die Haelfte - erlaubt
    ("25000", "12499", True),
    ("25000", "25000", False),
    ("0", "100", False),          # ohne Vorgabe gibt es kein Verhaeltnis
    ("abc", "100", False),        # kein Zahlenwert - das Plugin entscheidet
    ("1", "2", False),
    ("1", "3", True),
])
def test_sprungrechnung(alt, neu, erwartet):
    """Dieselben Grenzen wie Sprungregel im Plugin - einzeln nachgerechnet.

    Gerechnet wird 2 * neu < alt und nicht neu < alt / 2: Eine Ganzzahldivision rundet ab
    und verschoebe die Grenze um eins.
    """
    from app.services import freimarken_service as fs

    assert fs.grosser_sprung(alt, neu) is erwartet


# ------------------------------------------- Zugangsdaten gehoeren nicht hierher

# Eine config.yml MIT Zugangsdaten - so sieht die echte aus (config.yml:7-22 des Plugins).
CONFIG_MIT_ZUGANG = CONFIG + """
datenbank:
  # NUR BEIM START GELESEN. Nach Plan 12a.6 gehoeren diese drei Zeilen nicht ins Dashboard.
  url: "jdbc:mariadb://127.0.0.1:3306/freimarken"
  benutzer: freimarken
  passwort: altesKennwort
"""


def test_zugangsdaten_werden_nie_geschrieben(client, tmp_path):
    """Der Katalog nennt datenbank unter nicht-im-dashboard - das muss WIRKEN.

    Es stand dort mit Begruendung, und kein Programmpfad las die Abbildung: Der
    Schreibweg lehnte nur Pfade ab, die auf ein Muster unter ``gesperrt`` passen. Ein
    untergeschobenes Feld kam also durch. Diese Probe geht am Formular vorbei, so wie es
    auch ein Mensch mit einer eigenen Anfrage tun koennte.
    """
    server_id, datei = _aufbau(client, tmp_path, name="zugang", port=25631,
                               inhalt=CONFIG_MIT_ZUGANG)
    vorher = datei.read_bytes()

    antwort = _speichern(client, server_id, seite="wirtschaft",
                         **{"feld:datenbank.passwort": "neuesKennwort",
                            "feld:datenbank.benutzer": "wer-anders"})

    assert antwort.status_code == 303
    assert datei.read_bytes() == vorher, \
        "Zugangsdaten darf das Dashboard nie schreiben"


def test_zugangsdaten_stehen_nicht_im_protokoll(client, tmp_path):
    """Der zweite Ausgang: protokoll_text baut '<pfad>: <alt> -> <neu>'.

    Bei einem Kennwort stuende dort das alte UND das neue im Klartext - und das Protokoll
    ist ab Moderator zu lesen. Ein Riegel, der nur die Datei schuetzt, reicht also nicht.
    """
    from app.db.session import SessionLocal
    from app.services import freimarken_service as fs

    server_id, _ = _aufbau(client, tmp_path, name="zugangbuch", port=25632,
                           inhalt=CONFIG_MIT_ZUGANG)
    _speichern(client, server_id, seite="wirtschaft",
               **{"feld:datenbank.passwort": "neuesKennwort"})

    with SessionLocal() as db:
        zeilen = fs.protokollzeilen(db, server_id, grenze=200)
    alles = str(zeilen)
    assert "neuesKennwort" not in alles, "das neue Kennwort steht im Protokoll"
    assert "altesKennwort" not in alles, "das alte Kennwort steht im Protokoll"


def test_gueltige_aenderung_neben_dem_gesperrten_kommt_an(client, tmp_path):
    """Das Gegenbeispiel: Der Riegel darf nicht die ganze Anfrage verwerfen.

    Sonst waere die Probe oben auch gruen, wenn gar nichts mehr geschrieben wuerde - und
    ein Tippfehler in einem Pfad wuerde die daneben stehende Preisaenderung mitnehmen.
    """
    server_id, datei = _aufbau(client, tmp_path, name="zugangdaneben", port=25633,
                               inhalt=CONFIG_MIT_ZUGANG)

    _speichern(client, server_id, seite="wirtschaft",
               **{"feld:datenbank.passwort": "neuesKennwort",
                  "feld:wirtschaft.parzelle.grundpreis": "30000"})

    inhalt = datei.read_text(encoding="utf-8")
    assert "grundpreis: 30000" in inhalt, "die gueltige Aenderung muss ankommen"
    assert "passwort: altesKennwort" in inhalt, "das Kennwort bleibt unberuehrt"


def test_sperrgrund_fragt_beide_quellen():
    """Die Regel steht an einer Stelle. Gefragt wird hier, ob sie beide Quellen kennt.

    Ein Abschnitt unter nicht-im-dashboard sperrt seinen ganzen Unterbaum, ein Muster
    unter gesperrt genau eine Ebene. Und ein gewoehnlicher Pfad bleibt frei - ohne diesen
    dritten Satz waere ein 'immer sperren' auch gruen.
    """
    from app.services import freimarken_service as fs

    grund = fs.sperrgrund("datenbank.passwort")
    assert grund is not None, "der Unterbaum von datenbank muss gesperrt sein"
    assert "Browserverlauf" in grund, "der Grund des Katalogs muss durchkommen"
    assert fs.sperrgrund("datenbank") is not None, "auch der Abschnitt selbst"
    assert fs.sperrgrund("parzellen.hoechstzahl") is not None, "Muster unter gesperrt"
    assert fs.sperrgrund("wirtschaft.parzelle.grundpreis") is None, \
        "ein gewoehnlicher Preis muss aenderbar bleiben"
    assert fs.sperrgrund("datenbankfoo.passwort") is None, \
        "der Abschnittsname muss ganz passen, nicht als Vorsilbe"


# ------------------------------------------------- Listen sind auch Felder

# Dieselbe Datei wie oben, aber mit den Gestalten, die die ECHTE config.yml hat: eine
# Flussliste und eine Blockliste. 145 der 549 ausgelieferten Felder tragen einen
# Listenindex, und keine der Proben darueber hatte eine Liste - darum war der Fehler
# gruen.
CONFIG_MIT_LISTEN = CONFIG + """
welten:
  marktstadt:
    # Die Torkoordinate als Flussliste - so steht sie in der ausgelieferten Datei.
    tor:
      von: [58, -60, 0]
      bis: [62, -56, 0]
    kante: 3000

verdienst:
  # Und als Blockliste.
  spawngruende:
    - BERGMANN
    - HOLZFAELLER
"""


def test_eine_listenstelle_laesst_sich_aendern(client, tmp_path):
    """Der Pfad einer Listenstelle ist KEIN Schluesselname.

    ``_pfad_label`` haengt die Stelle ohne Punkt an ("tor.von[0]"). Wer den Knotenpfad
    daraus mit ``split(".")`` zurueckrechnet, sucht im Mapping ``tor`` einen Schluessel
    namens ``von[0]`` - findet keinen und meldet dem Nutzer, die Datei habe sich
    zwischenzeitlich geaendert. Sie hat sich nicht geaendert, und Neuladen hilft nie.
    """
    server_id, datei = _aufbau(client, tmp_path, name="liste", port=25651,
                               inhalt=CONFIG_MIT_LISTEN)
    vorher = datei.read_text(encoding="utf-8").splitlines()

    antwort = _speichern(client, server_id, seite="welten",
                         **{"feld:welten.marktstadt.tor.von[0]": "62"})

    assert antwort.status_code == 303
    nachher = datei.read_text(encoding="utf-8").splitlines()
    anders = [(a, b) for a, b in zip(vorher, nachher) if a != b]
    assert len(anders) == 1, ("genau eine Zeile darf anders sein, nicht "
                              + str(len(anders)) + ": " + str(anders))
    assert "62" in anders[0][1] and "-60" in anders[0][1], \
        "nur die erste Zahl der Liste darf sich geaendert haben: " + anders[0][1]


def test_eine_blockliste_laesst_sich_aendern(client, tmp_path):
    """Dieselbe Falle in der anderen Schreibform einer Liste."""
    server_id, datei = _aufbau(client, tmp_path, name="blockliste", port=25652,
                               inhalt=CONFIG_MIT_LISTEN)

    _speichern(client, server_id, seite="berufe",
               **{"feld:verdienst.spawngruende[1]": "FISCHER"})

    inhalt = datei.read_text(encoding="utf-8")
    assert "- FISCHER" in inhalt
    assert "- BERGMANN" in inhalt, "die erste Stelle bleibt unberuehrt"
    assert "HOLZFAELLER" not in inhalt


def test_eine_gueltige_aenderung_neben_einer_liste_kommt_an(client, tmp_path):
    """Das eigentliche Uebel: ``schreibe`` wirft beim ERSTEN Eintrag.

    Ging die Listenstelle nicht, fiel die Preisaenderung desselben Formulars mit weg -
    und der Nutzer las eine Meldung ueber eine Dateiaenderung, die es nicht gab.
    """
    server_id, datei = _aufbau(client, tmp_path, name="listedaneben", port=25653,
                               inhalt=CONFIG_MIT_LISTEN)

    _speichern(client, server_id, seite="welten",
               **{"feld:welten.marktstadt.tor.von[0]": "62",
                  "feld:welten.marktstadt.kante": "4000"})

    inhalt = datei.read_text(encoding="utf-8")
    assert "kante: 4000" in inhalt
    assert "[62, -60, 0]" in inhalt


def test_jedes_feld_der_ausgelieferten_datei_ist_schreibbar():
    """DIE TAFEL: Was die Seite anzeigt, muss sie auch schreiben koennen.

    Gelesen wird die AUSGELIEFERTE config.yml des Plugins, nicht eine Probenfassung -
    genau daran ist der Fehler vorbeigegangen. Fuer jedes der 549 Felder wird gefragt, ob
    ``_knoten_an_pfad`` den Knoten unter dem Pfad findet, den ``felder_nach_pfad``
    mitliefert. Findet es ihn nicht, meldet das Dashboard eine Dateiaenderung, die nicht
    stattgefunden hat.

    Die Zahl wird MITGEPRUEFT: Ohne sie waere die Tafel auch gruen, wenn
    ``felder_nach_pfad`` eines Tages gar nichts mehr liefert.
    """
    from pathlib import Path

    import yaml

    from app.services import yaml_assistant_service as ya

    text = Path("freimarken/paper/src/main/resources/config.yml").read_text(
        encoding="utf-8")
    felder = ya.felder_nach_pfad(text)
    assert len(felder) > 500, ("die ausgelieferte Datei hat ueber 500 Felder, gefunden: "
                               + str(len(felder)))
    mit_liste = [label for label in felder if "[" in label]
    assert len(mit_liste) > 100, ("mehr als hundert davon tragen einen Listenindex, "
                                  "gefunden: " + str(len(mit_liste)))

    wurzel = yaml.compose(text)
    unerreichbar = [label for label, feld in felder.items()
                    if ya._knoten_an_pfad(wurzel, feld["pfad"]) is None]
    assert unerreichbar == [], (
        str(len(unerreichbar)) + " Felder sind nicht schreibbar, z. B.: "
        + str(unerreichbar[:5]))
