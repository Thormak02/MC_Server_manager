"""Die Texte-Seite: Wortlaute aendern, ohne die Vorlage anzufassen.

DIE ZWEIDATEIFORM IST DER GANZE PUNKT
Das Plugin schreibt sprache-vorlage.yml bei JEDEM Start neu (saveResource(..., true)) und
legt sprache.yml LEER an. Die Vorlage ist die Referenz mit allen 770 Saetzen, die
Betreiberdatei enthaelt nur Abweichungen, und der Rueckfall gilt je Schluessel. Darum:

  * Angezeigt wird die VORLAGE (samt ihrer Erklaerungen).
  * Geschrieben wird die BETREIBERDATEI - und nur die. Eine Aenderung in der Vorlage
    waere beim naechsten Start weg, und der Betreiber haette sie fuer gueltig gehalten.
    test_die_vorlage_wird_nie_beschrieben haelt das fest.

FLACH ANHAENGEN - GEMESSEN, NICHT GERATEN
Am 22.11.2026 an zwei Wegwerfservern gemessen: Ein flacher Schluessel
"mark.verlauf.kopf: ..." auf oberster Ebene wirkt genau wie der dreifach eingerueckte
Block; der Start meldete beide Male "deine: mark.verlauf.kopf". Darum haengt der Manager
eine Zeile an, statt Bloecke aufzubauen.

Dieselbe Messung zeigte die Falle: Stehen BEIDE Formen fuer denselben Pfad in der Datei,
meldet der Start wieder nur einen Satz und KEINE Klage - eine Fassung ueberschreibt die
andere stumm, weil die Dublettenpruefung Schluesselnamen je Ebene vergleicht und nicht
Pfade. test_zweites_aendern_legt_keine_zweite_zeile_an haelt die Haelfte fest, die der
Manager verursachen koennte.
"""
from __future__ import annotations

from pathlib import Path

import pytest


VORLAGE = """farben:
  # Gewoehnlicher Text - der Grundton jeder Zeile.
  fliesstext: "#a5b4bf"
  # Eine Ueberschrift.
  kopf: "#c6a15b"

mark:
  # {betrag} - dein Stand.
  stand: "<fliesstext>Du hast <betont>{betrag}</betont>.</fliesstext>"
  verlauf:
    # Die Ueberschrift der Buchungsliste.
    kopf: "<kopf>Deine letzten Buchungen</kopf>"
    leer: "<fliesstext>Noch keine Buchungen.</fliesstext>"

parzelle:
  genommen: "<bestaetigung>Die Parzelle ist deine.</bestaetigung>"
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


def _aufbau(client, tmp_path, *, name, port, rolle="admin", eigene=None,
            vorlage=VORLAGE):
    """Server mit Sprachvorlage und (wahlweise) Betreiberdatei."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.models.server_permission import ServerPermission

    basis = tmp_path / name
    eigen_ordner = basis / "plugins" / "Freimarken"
    eigen_ordner.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    with open(eigen_ordner / "sprache-vorlage.yml", "w", encoding="utf-8",
              newline="") as offen:
        offen.write(vorlage)
    if eigene is not None:
        with open(eigen_ordner / "sprache.yml", "w", encoding="utf-8",
                  newline="") as offen:
            offen.write(eigene)

    with SessionLocal() as db:
        person = _nutzer(db, rolle, name)
        server = Server(name=name, slug=name, server_type="paper", mc_version="26.3",
                        base_path=str(basis), port=port, status="stopped",
                        start_mode="command", start_command="java -jar server.jar")
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
    return server_id, eigen_ordner


def _speichern(client, server_id, abschnitt="mark", grund="Probe", **felder):
    daten = {"abschnitt": abschnitt, "grund": grund}
    daten.update(felder)
    return client.post("/freimarken/" + str(server_id) + "/texte", data=daten,
                       follow_redirects=False)


# ------------------------------------------------------------------- Anzeigen

def test_abschnittsliste_nennt_alle_abschnitte(client, tmp_path):
    """770 Saetze auf einer Seite waeren keine Oberflaeche - erst die Abschnitte."""
    server_id, _ = _aufbau(client, tmp_path, name="liste", port=25801)
    antwort = client.get("/freimarken/" + str(server_id) + "/texte")
    assert antwort.status_code == 200
    for abschnitt in ("farben", "mark", "parzelle"):
        assert abschnitt in antwort.text


def test_ein_abschnitt_zeigt_saetze_und_erklaerung(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="einabschnitt", port=25802)
    antwort = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert antwort.status_code == 200
    assert "Deine letzten Buchungen" in antwort.text
    assert "Die Ueberschrift der Buchungsliste" in antwort.text, \
        "die Erklaerung aus der Vorlage gehoert dazu"


def test_eigener_satz_wird_als_solcher_gezeigt(client, tmp_path):
    """Die tragende Unterscheidung: kommt der Satz aus deiner Datei oder aus dem Plugin?"""
    server_id, _ = _aufbau(client, tmp_path, name="eigen", port=25803,
                           eigene='mark.verlauf.kopf: "<kopf>MEINE FASSUNG</kopf>"\n')
    antwort = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert "MEINE FASSUNG" in antwort.text
    assert "von dir" in antwort.text
    assert "Deine letzten Buchungen" in antwort.text, \
        "der ausgelieferte Wortlaut muss daneben sichtbar bleiben"


def test_ohne_betreiberdatei_gilt_die_vorlage(client, tmp_path):
    """Das Plugin legt sie erst beim ersten Start an - bis dahin ist das kein Fehler."""
    server_id, ordner = _aufbau(client, tmp_path, name="ohnedatei", port=25804)
    assert not (ordner / "sprache.yml").exists()
    antwort = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert antwort.status_code == 200
    assert "Deine letzten Buchungen" in antwort.text


# ------------------------------------------------------------------ Schreiben

def test_neuer_satz_wird_flach_angehaengt(client, tmp_path):
    server_id, ordner = _aufbau(client, tmp_path, name="anhaengen", port=25805,
                                eigene="")
    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>NEUE FASSUNG</kopf>"})

    inhalt = (ordner / "sprache.yml").read_text(encoding="utf-8")
    assert "mark.verlauf.kopf:" in inhalt, "flach, nicht als Block"
    assert "NEUE FASSUNG" in inhalt
    # Kein Block aufgebaut - genau eine Zeile.
    assert inhalt.count("\n") == 1, "eine Zeile, nicht drei: " + repr(inhalt)


def test_die_vorlage_wird_nie_beschrieben(client, tmp_path):
    """Eine Aenderung in der Vorlage waere beim naechsten Start weg.

    Das Plugin schreibt sie bei jedem Start neu. Wer dort schreibt, verliert seine Arbeit
    - und merkt es erst, wenn der Satz im Spiel wieder der alte ist.
    """
    server_id, ordner = _aufbau(client, tmp_path, name="vorlageschutz", port=25806,
                                eigene="")
    vorher = (ordner / "sprache-vorlage.yml").read_bytes()

    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>GEAENDERT</kopf>",
                  "feld:parzelle.genommen": "<bestaetigung>Dein.</bestaetigung>"})

    assert (ordner / "sprache-vorlage.yml").read_bytes() == vorher, \
        "die Vorlage muss byteweise unberuehrt bleiben"
    assert "GEAENDERT" in (ordner / "sprache.yml").read_text(encoding="utf-8")


def test_zweites_aendern_legt_keine_zweite_zeile_an(client, tmp_path):
    """Sonst stehen zwei Zeilen fuer denselben Pfad - und das Plugin merkt es NICHT.

    Gemessen am 22.11.2026: Zwei Formen fuer denselben Pfad ergeben "1 von 770 Saetzen"
    ohne jede Klage; eine ueberschreibt die andere stumm. Zwei flache Zeilen waeren
    dasselbe, und der Betreiber saehe im Dashboard den einen Wert und im Spiel den
    anderen.
    """
    server_id, ordner = _aufbau(client, tmp_path, name="zweimal", port=25807,
                                eigene="")
    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>ERSTE</kopf>"})
    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>ZWEITE</kopf>"})

    inhalt = (ordner / "sprache.yml").read_text(encoding="utf-8")
    assert inhalt.count("mark.verlauf.kopf") == 1, \
        "der Pfad darf nur EINMAL in der Datei stehen: " + repr(inhalt)
    assert "ZWEITE" in inhalt
    assert "ERSTE" not in inhalt


def test_geschachtelt_vorhandener_satz_wird_ersetzt_nicht_angehaengt(client, tmp_path):
    """Ein von Hand geschriebener Block wird an seiner Stelle geaendert.

    Haengte der Manager stattdessen eine flache Zeile an, stuenden beide Formen in der
    Datei - genau die Lage, die das Plugin nicht bemerkt.
    """
    server_id, ordner = _aufbau(
        client, tmp_path, name="geschachtelt", port=25808,
        eigene='mark:\n  verlauf:\n    kopf: "<kopf>VON HAND</kopf>"\n')

    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>GEAENDERT</kopf>"})

    inhalt = (ordner / "sprache.yml").read_text(encoding="utf-8")
    assert "GEAENDERT" in inhalt
    assert "mark.verlauf.kopf:" not in inhalt, \
        "es darf keine zweite, flache Zeile dazukommen"
    assert inhalt.count("kopf:") == 1


def test_anfuehrungszeichen_im_wortlaut(client, tmp_path):
    """Ein Satz mit " darin muss die Datei lesbar lassen."""
    server_id, ordner = _aufbau(client, tmp_path, name="zitat", port=25809, eigene="")
    _speichern(client, server_id,
               **{"feld:parzelle.genommen": '<bestaetigung>Sag "danke".</bestaetigung>'},
               abschnitt="parzelle")

    import yaml
    geladen = yaml.safe_load((ordner / "sprache.yml").read_text(encoding="utf-8"))
    assert geladen["parzelle.genommen"] == '<bestaetigung>Sag "danke".</bestaetigung>'


def test_unveraenderter_satz_wird_nicht_eingetragen(client, tmp_path):
    """Wer nichts aendert, bekommt keine Zeile in seiner Datei.

    Sonst stuende nach einem Besuch der Seite jeder Satz darin, und das Plugin koennte
    seine Saetze nie mehr verbessern - der Rueckfall gilt je Schluessel, und ein
    eingetragener Schluessel faellt nicht zurueck.
    """
    server_id, ordner = _aufbau(client, tmp_path, name="unberuehrt", port=25810,
                                eigene="")
    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>Deine letzten Buchungen</kopf>",
                  "feld:mark.verlauf.leer": "<fliesstext>Noch keine Buchungen.</fliesstext>"})
    assert (ordner / "sprache.yml").read_text(encoding="utf-8") == ""


def test_unbekannter_satz_wird_nicht_erfunden(client, tmp_path):
    server_id, ordner = _aufbau(client, tmp_path, name="erfunden", port=25811,
                                eigene="")
    antwort = _speichern(client, server_id,
                         **{"feld:mark.gibtsnicht": "<kopf>X</kopf>"})
    assert antwort.status_code == 303
    assert (ordner / "sprache.yml").read_text(encoding="utf-8") == ""
    seite = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert "gibt es in der Vorlage nicht" in seite.text


# ------------------------------------------------------------------ Hinweise

def test_verlorener_platzhalter_wird_gemeldet(client, tmp_path):
    """Das Plugin lehnt einen Satz ohne seine Platzhalter beim Laden ab.

    Der Manager kennt die erwarteten Namen nicht und braucht sie nicht zu kennen: Die
    Differenz zum bisherigen Wortlaut sagt dasselbe und veraltet nie.
    """
    server_id, _ = _aufbau(client, tmp_path, name="platzhalter", port=25812, eigene="")
    _speichern(client, server_id,
               **{"feld:mark.stand": "<fliesstext>Du hast Geld.</fliesstext>"})
    seite = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert "{betrag}" in seite.text
    assert "lehnt es den neuen Wortlaut beim Laden ab" in seite.text


def test_unpaarige_farbrolle_wird_gemeldet(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="unpaarig", port=25813, eigene="")
    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>Ohne Schluss"})
    seite = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert "nicht paarig" in seite.text


def test_hinweis_verhindert_das_schreiben_nicht(client, tmp_path):
    """Hinweis und nicht Absage: Geprueft wird beim Laden, und zwar vom Plugin.

    Zwei Pruefungen fuer denselben Fall machen sich gegenseitig unpruefbar. Der Manager
    sagt, was ihm auffaellt - entscheiden tut das Plugin.
    """
    server_id, ordner = _aufbau(client, tmp_path, name="trotzdem", port=25814,
                               eigene="")
    _speichern(client, server_id,
               **{"feld:mark.verlauf.kopf": "<kopf>Ohne Schluss"})
    assert "Ohne Schluss" in (ordner / "sprache.yml").read_text(encoding="utf-8")


def test_fremdes_tag_ist_kein_fehler(client, tmp_path):
    """<bold> ist gewoehnliches MiniMessage, und \\<name> ein Angabeplatz.

    Gemessen an der Vorlage: 8 der 24 Tagnamen sind escapte Angabeplaetze in
    Befehlshinweisen. Daraus einen Fehler zu machen hiesse, dem Betreiber Dinge zu
    verbieten, die gehen.
    """
    from app.services import freimarken_service as fs

    assert fs.pruefe_satz("x", "<bold>fett</bold>", ["kopf"]) == []
    assert fs.pruefe_satz("x", "/laden mieten <name>", ["kopf"]) == []


# -------------------------------------------------------------- Zuruecknehmen

def test_zuruecknehmen_entfernt_die_zeile(client, tmp_path):
    server_id, ordner = _aufbau(
        client, tmp_path, name="zurueck", port=25815,
        eigene=('mark.verlauf.kopf: "<kopf>MEINE</kopf>"\n'
                'parzelle.genommen: "<bestaetigung>Dein.</bestaetigung>"\n'))

    antwort = client.post("/freimarken/" + str(server_id) + "/texte/zuruecknehmen",
                          data={"pfad": "mark.verlauf.kopf", "abschnitt": "mark"},
                          follow_redirects=False)
    assert antwort.status_code == 303

    inhalt = (ordner / "sprache.yml").read_text(encoding="utf-8")
    assert "mark.verlauf.kopf" not in inhalt
    assert "parzelle.genommen" in inhalt, "die andere Zeile bleibt"


def test_zuruecknehmen_verweigert_bei_geschachtelt(client, tmp_path):
    """Die eine Zeile aus einem Block zu nehmen liesse "verlauf:" ohne Kind zurueck - und
    ein Schluessel ohne Wert ist fuer das Plugin ein Satz, der kein Text ist."""
    server_id, ordner = _aufbau(
        client, tmp_path, name="zurueckblock", port=25816,
        eigene='mark:\n  verlauf:\n    kopf: "<kopf>VON HAND</kopf>"\n')
    vorher = (ordner / "sprache.yml").read_bytes()

    client.post("/freimarken/" + str(server_id) + "/texte/zuruecknehmen",
                data={"pfad": "mark.verlauf.kopf", "abschnitt": "mark"},
                follow_redirects=False)

    assert (ordner / "sprache.yml").read_bytes() == vorher
    seite = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert "eingerueckter Block" in seite.text
    assert "Dateieditor" in seite.text, "der Hinweis muss sagen, wo es geht"


def test_zuruecknehmen_eines_nicht_eigenen_satzes(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="zurueckleer", port=25817, eigene="")
    client.post("/freimarken/" + str(server_id) + "/texte/zuruecknehmen",
                data={"pfad": "mark.verlauf.kopf", "abschnitt": "mark"},
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert "gilt also schon der ausgelieferte" in seite.text


# ---------------------------------------------------------------------- Rechte

def test_moderator_darf_texte_nicht_aendern(client, tmp_path):
    server_id, ordner = _aufbau(client, tmp_path, name="textmoder", port=25818,
                                rolle="moderator", eigene="")
    antwort = _speichern(client, server_id,
                         **{"feld:mark.verlauf.kopf": "<kopf>X</kopf>"})
    assert antwort.status_code == 403
    assert (ordner / "sprache.yml").read_text(encoding="utf-8") == ""


def test_moderator_darf_nicht_zuruecknehmen(client, tmp_path):
    server_id, ordner = _aufbau(client, tmp_path, name="textmoder2", port=25819,
                                rolle="moderator",
                                eigene='mark.verlauf.kopf: "<kopf>MEINE</kopf>"\n')
    vorher = (ordner / "sprache.yml").read_bytes()
    antwort = client.post("/freimarken/" + str(server_id) + "/texte/zuruecknehmen",
                          data={"pfad": "mark.verlauf.kopf"},
                          follow_redirects=False)
    assert antwort.status_code == 403
    assert (ordner / "sprache.yml").read_bytes() == vorher


def test_moderator_darf_texte_lesen(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="textlesen", port=25820,
                           rolle="moderator")
    antwort = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert antwort.status_code == 200
    assert "Deine letzten Buchungen" in antwort.text


# --------------------------------------------------------- Die Routenreihenfolge

def test_texte_landet_in_der_texteroute(client, tmp_path):
    """Starlette nimmt die erste passende Route, und "{seite}" passt auch auf "texte".

    Stuenden die Texterouten hinter der allgemeinen Speicherroute, landete jedes
    Speichern von Saetzen dort - und wuerde als unbekannter Pfad der config.yml gemeldet.
    Eine Reihenfolge im Quelltext ist kein Riegel, den man sieht; diese Probe macht ihn
    sichtbar.
    """
    server_id, ordner = _aufbau(client, tmp_path, name="reihenfolge", port=25821,
                                eigene="")
    antwort = _speichern(client, server_id,
                         **{"feld:mark.verlauf.kopf": "<kopf>GEHT</kopf>"})
    assert antwort.status_code == 303
    # Der Beweis: Die Texteroute schreibt in die sprache.yml. Die allgemeine Route haette
    # die config.yml gesucht - die es hier gar nicht gibt - und etwas anderes gemeldet.
    assert "GEHT" in (ordner / "sprache.yml").read_text(encoding="utf-8")


def test_protokoll_nennt_die_textaenderung(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import freimarken_service as fs

    server_id, _ = _aufbau(client, tmp_path, name="textbuch", port=25822, eigene="")
    _speichern(client, server_id, grund="Freundlicher",
               **{"feld:mark.verlauf.kopf": "<kopf>NEU</kopf>"})

    with SessionLocal() as db:
        zeilen = fs.protokollzeilen(db, server_id)
    assert len(zeilen) == 1
    assert zeilen[0]["aktion"] == fs.AKTION_TEXTE
    assert zeilen[0]["grund"] == "Freundlicher"
    assert zeilen[0]["aenderungen"][0]["pfad"] == "mark.verlauf.kopf"


def test_kaputte_betreiberdatei_wird_gemeldet(client, tmp_path):
    server_id, ordner = _aufbau(client, tmp_path, name="kaputtsprache", port=25823,
                                eigene='mark.verlauf.kopf: "offen\n  - [kaputt\n')
    vorher = (ordner / "sprache.yml").read_bytes()
    antwort = client.get("/freimarken/" + str(server_id) + "/texte?abschnitt=mark")
    assert antwort.status_code == 200
    assert "kein gueltiges YAML" in antwort.text
    assert (ordner / "sprache.yml").read_bytes() == vorher
