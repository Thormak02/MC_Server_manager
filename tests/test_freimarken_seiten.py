"""Die drei Spieldaten-Seiten: wer sie sieht, wer sie aendert, und was gesendet wird.

DREI STUFEN, NICHT ZWEI
Lesen ab Moderator, Stammdaten ab Admin, Geld- und Besitzkorrekturen nur Super-Admin
(Plan 12a.4). Die dritte Stufe wird HIER nur gefragt; der tragende Riegel liegt in
``process_service`` an der Sendestelle, durch die alle fuenf Wege gehen. Was diese Proben
halten, ist die Oberflaeche: ein Formular, das ein Moderator gar nicht sehen soll, und
eine Route, die ihn abweist.

DIE KORREKTUR MELDET KEINEN ERFOLG
``send_console_command`` kennt den Ausgang nicht - es schreibt auf stdin. Eine Meldung
"gebucht" waere eine Zusage, die der Manager nicht halten kann. Zwei Proben halten fest,
dass dort "gesendet" steht und dass die Meldung sagt, wo der Ausgang zu lesen ist.
"""
from __future__ import annotations

import pytest


CONFIG = """# Freimarken - Einstellungen
datenbank:
  url: ""
  benutzer: ""
  passwort: ""

wirtschaft:
  parzelle:
    grundpreis: 25000
  sprung-bestaetigt:
    grundpreis: ""
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


def _aufbau(client, tmp_path, *, name, port, rolle="super_admin", steuern=True):
    """Server mit Freimarken-Konfiguration und angemeldetem Nutzer dieser Rolle."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.models.server_permission import ServerPermission

    basis = tmp_path / name
    (basis / "plugins" / "Freimarken").mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    with open(basis / "plugins" / "Freimarken" / "config.yml", "w", encoding="utf-8",
              newline="") as offen:
        offen.write(CONFIG)

    with SessionLocal() as db:
        person = _nutzer(db, rolle, name)
        server = Server(name=name, slug=name, server_type="paper", mc_version="26.3",
                        base_path=str(basis), port=port, status="stopped",
                        start_mode="command", start_command="java -jar server.jar",
                        freimarken_enabled=True)
        db.add(server)
        db.commit()
        db.refresh(server)
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True,
                                can_console=steuern, can_restart=False,
                                can_edit_files=True, can_manage=False))
        db.commit()
        server_id = server.id

    assert client.post("/login", data={"username": name, "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303
    return server_id


# ------------------------------------------------------- Die Seiten sind erreichbar

@pytest.mark.parametrize("seite", ["besitz", "konten", "mitglieder"])
def test_die_seiten_oeffnen_sich_ab_moderator(client, tmp_path, seite):
    """Lesen ist Moderatorensache - auch bei Spieldaten."""
    server_id = _aufbau(client, tmp_path, name="lesen-" + seite, port=25660 + len(seite),
                        rolle="moderator")

    antwort = client.get("/freimarken/" + str(server_id) + "/" + seite)

    assert antwort.status_code == 200


@pytest.mark.parametrize("seite", ["besitz", "konten"])
def test_ohne_datenbank_sagt_die_seite_warum(client, tmp_path, seite):
    """Leer ist ein gueltiger Zustand des Plugins, kein Fehler.

    Die config.yml dieser Probe hat eine leere ``datenbank.url``. Die Seite muss das
    benennen, statt eine Ausnahme zu werfen oder eine leere Tabelle zu zeigen - eine
    leere Tabelle laese sich als "hier gehoert niemandem etwas" lesen.
    """
    server_id = _aufbau(client, tmp_path, name="ohnedb-" + seite,
                        port=25670 + len(seite))

    antwort = client.get("/freimarken/" + str(server_id) + "/" + seite)

    assert antwort.status_code == 200
    assert "speichert nichts" in antwort.text


def test_die_drei_seiten_stehen_im_menue(client, tmp_path):
    server_id = _aufbau(client, tmp_path, name="menue", port=25672)

    antwort = client.get("/freimarken/" + str(server_id) + "/konten")

    for name in ("Besitz", "Konten", "Mitgliedschaften"):
        assert name in antwort.text


# --------------------------------------------------------------- Die dritte Stufe

@pytest.mark.parametrize("pfad,daten", [
    ("konten", {"spieler": "Anna", "betrag": "500", "grund": "Probe"}),
    ("besitz", {"aktion": "freigeben", "x": "3", "z": "4", "grund": "Probe"}),
    ("mitglieder", {"kennung": "aaaaaaaa-1111-4111-8111-111111111111",
                    "zuschlag": "parzellen", "zahl": "1"}),
])
def test_ein_admin_darf_nicht_korrigieren(client, tmp_path, pfad, daten):
    """Plan 12a.4: Geld- und Besitzkorrekturen sind Sache des Super-Admins.

    Ein Admin darf Preise stellen - das ist Stammdaten. Was einem Spieler GEHOERT, darf
    er nicht aendern. Ohne diese Probe waere der Unterschied nur ein Kommentar.
    """
    server_id = _aufbau(client, tmp_path, name="admin-" + pfad, port=25680 + len(pfad),
                        rolle="admin")

    antwort = client.post("/freimarken/" + str(server_id) + "/" + pfad, data=daten,
                          follow_redirects=False)

    assert antwort.status_code == 403


def test_ein_super_admin_braucht_kein_serverrecht(client, tmp_path, monkeypatch):
    """GEMESSEN und nicht erwartet [22.11.2026].

    Hier stand eine Probe, die 403 verlangte, wenn dem Super-Admin das Steuerrecht fuer
    diesen Server fehlt. Sie wurde rot: ``can_control_server`` gibt fuer einen
    Super-Admin IMMER true zurueck ("SUPER_ADMIN immer"). Die Pruefung konnte also nie
    "nein" antworten und ist aus ``_fuer_korrektur`` entfernt - ein Riegel, der nicht
    greifen kann, macht den daneben unpruefbar.

    Was bleibt, ist diese Zusage: Die Rolle allein entscheidet, und das steht hier, damit
    niemand die Pruefung als Versehen wieder einbaut.
    """
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="ohnesteuer", port=25685,
                        steuern=False)

    antwort = client.post("/freimarken/" + str(server_id) + "/konten",
                          data={"spieler": "Anna", "betrag": "500"},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert gesendet == ["freimarken geld Anna 500"]


def test_das_formular_fehlt_einem_admin(client, tmp_path):
    """Sichtbarkeit und Erlaubnis muessen zusammenpassen.

    Ein Formular, das ein Admin sieht und nicht benutzen kann, ist eine Einladung zu
    einer Fehlermeldung. Das Verstecken ist Bequemlichkeit, der Riegel ist die Route
    darueber - beide werden geprueft.
    """
    server_id = _aufbau(client, tmp_path, name="formular-admin", port=25686,
                        rolle="admin")

    antwort = client.get("/freimarken/" + str(server_id) + "/konten")

    assert "Konto berichtigen" not in antwort.text

    # Und die Gegenrichtung: Der Super-Admin sieht es. Ohne diesen Satz waere ein
    # "zeige das Formular niemandem" gruen.
    server_id = _aufbau(client, tmp_path, name="formular-super", port=25687)
    antwort = client.get("/freimarken/" + str(server_id) + "/konten")
    assert "Konto berichtigen" in antwort.text


# --------------------------------------------------- Was gesendet wird, und was nicht

def _faelsche_konsole(monkeypatch):
    """``send_console_command`` abfangen - der Probenserver laeuft nicht."""
    from app.services import process_service as ps

    gesendet: list[str] = []

    def gefaelscht(db, server, command, initiated_by_user_id, **rest):
        gesendet.append(command)
        return True, "Befehl gesendet."

    monkeypatch.setattr(ps, "send_console_command", gefaelscht)
    return gesendet


def test_eine_geldkorrektur_sendet_den_befehl(client, tmp_path, monkeypatch):
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="geld-senden", port=25690)

    antwort = client.post("/freimarken/" + str(server_id) + "/konten",
                          data={"spieler": "Anna", "betrag": "-500",
                                "grund": "Fehlbuchung vom Dienstag"},
                          follow_redirects=False)

    assert antwort.status_code == 303
    # Das MINUS geht als Vorzeichen durch, nicht als eigener Zweig - so nimmt es der
    # Befehl, und ein eigener Abzugszweig gibt es nicht.
    assert gesendet == ["freimarken geld Anna -500"]


def test_eine_goldkorrektur_sendet_goldgeld(client, tmp_path, monkeypatch):
    """Die Waehrung entscheidet ueber das BEFEHLSWORT [23.11.2026].

    Bis dahin gab es im ganzen Haus keinen Weg, Goldmark auf ein Konto zu buchen: Wer 40
    Goldmark verlor, bekam auf dieses Formular vierzig MARK - zum Kurs des Plans ein
    Achthundertstel, und die Meldung war dabei richtig.
    """
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="gold-senden", port=25692)

    antwort = client.post("/freimarken/" + str(server_id) + "/konten",
                          data={"spieler": "Anna", "betrag": "40",
                                "waehrung": "goldmark", "grund": "Muenzhaus"},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert gesendet == ["freimarken goldgeld Anna 40"]


def test_ohne_waehrung_bleibt_es_die_mark(client, tmp_path, monkeypatch):
    """Ein Formular ohne das Feld (ein alter Reiter, ein Skript) darf nicht in Goldmark
    buchen - die Vorgabe ist die kleinere Waehrung."""
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="gold-vorgabe", port=25693)

    client.post("/freimarken/" + str(server_id) + "/konten",
                data={"spieler": "Anna", "betrag": "40"}, follow_redirects=False)

    assert gesendet == ["freimarken geld Anna 40"]


def test_eine_erfundene_waehrung_wird_abgewiesen(client, tmp_path, monkeypatch):
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="gold-erfunden", port=25694)

    client.post("/freimarken/" + str(server_id) + "/konten",
                data={"spieler": "Anna", "betrag": "40", "waehrung": "taler"},
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/konten").text

    assert gesendet == [], "es darf nichts gesendet worden sein"
    assert "mark oder goldmark" in seite


def test_die_goldschranke_ist_eine_million(client, tmp_path, monkeypatch):
    """Die Goldmark hat ihre EIGENE Schranke, und sie muss zu der des Plugins passen.

    Dieselbe Zahl wie Betreibergeld.HOECHSTENS_GOLD. Waere hier die Markschranke
    stehengeblieben, erreichte die Zahl den Zweig gar nicht: Brigadier weist sie am
    Argument ab und antwortet englisch an eine Konsole, die ihre Antwort nicht
    zurueckgibt - der Betreiber saehe "Gesendet" und nichts waere gebucht.
    """
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="gold-schranke", port=25695)

    client.post("/freimarken/" + str(server_id) + "/konten",
                data={"spieler": "Anna", "betrag": "1000001",
                      "waehrung": "goldmark"}, follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/konten").text

    assert gesendet == [], "ueber einer Million darf nichts gesendet werden"
    # "eine Million" ALLEIN ist keine Zusicherung [berichtigt 23.11.2026]: Der Satz steht
    # dauerhaft in der Auswahlzeile des Formulars ("Goldmark (hoechstens eine Million)") -
    # die Zeile war auch auf einer Seite OHNE jede Fehlermeldung erfuellt, und ebenso,
    # wenn dort "Hoechstens eine Milliarde" stuende. Geprueft wird darum die MELDUNG.
    assert "Hoechstens eine Million" in seite, (
        "die Meldung muss die Goldschranke nennen - sonst sucht der Betreiber sie um den"
        " Faktor tausend falsch")
    # Kein "eine Milliarde" nicht - der Satz gehoert der Markschranke und steht dauerhaft
    # im Hinweis des Formulars. Die Gegenrichtung steht unten: Dieselbe Zahl geht in Mark
    # durch, und DAS unterscheidet die zwei Schranken.

    # UND DIE GEGENRICHTUNG: In Mark geht dieselbe Zahl durch. Ohne sie waere die Probe
    # auch gruen, wenn die Schranke fuer BEIDE Waehrungen auf eine Million faellt.
    client.post("/freimarken/" + str(server_id) + "/konten",
                data={"spieler": "Anna", "betrag": "1000001"}, follow_redirects=False)

    assert gesendet == ["freimarken geld Anna 1000001"]


def test_die_meldung_sagt_gesendet_und_nicht_gebucht(client, tmp_path, monkeypatch):
    """``send_console_command`` kennt den Ausgang nicht.

    Es schreibt auf stdin und gibt "Befehl gesendet." zurueck - auch wenn das Plugin
    UNBEKANNTER_SPIELER oder NICHT_GEDECKT geantwortet hat. Eine Meldung "gebucht" waere
    eine Zusage, die der Manager nicht halten kann.
    """
    _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="geld-meldung", port=25691)

    client.post("/freimarken/" + str(server_id) + "/konten",
                data={"spieler": "Anna", "betrag": "500"}, follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/konten").text

    assert "Gesendet" in seite
    assert "Ausgang" in seite, "die Meldung muss sagen, wo der Ausgang zu lesen ist"
    assert "gebucht" not in seite
    # Das Waehrungsfeld muss im Formular stehen, sonst ist der Goldweg gebaut und
    # unerreichbar - und der alte Satz "Goldmark laesst sich so nicht berichtigen" waere
    # eine Luege, die sich nur im Quelltext widerlegt.
    assert 'name="waehrung"' in seite
    assert "goldmark" in seite
    assert "laesst sich so nicht berichtigen" not in seite
    # UND DER HINWEIS NENNT DAS RICHTIGE BUCH [23.11.2026]: Die Seite sagte "die Buchung
    # danach in /mark verlauf" fuer beide Waehrungen - dort kommen aber nur Markzeilen an.
    # Derselbe falsche Satz stand in der Hilfezeile des Plugins; eine der zwei Stellen zu
    # heilen haette die andere stehen gelassen, und wer die Seite benutzt, liest nur diese.
    assert "/mark muenzhaus verlauf" in seite, (
        "der Hinweis muss sagen, wo eine GOLDbuchung danach steht")


def test_der_grund_landet_im_protokoll(client, tmp_path, monkeypatch):
    """Das Warum steht im Manager-Audit - das Plugin nimmt keinen Grund.

    Geprueft wird mit dem gemeinsamen Trenner und nicht mit einem eigenen Literal: Eine
    Aenderung daran setzte sonst alle alten Zeilen stumm auf "kein Grund angegeben".
    """
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog
    from app.services import freimarken_service as fs

    _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="geld-grund", port=25692)

    client.post("/freimarken/" + str(server_id) + "/konten",
                data={"spieler": "Anna", "betrag": "500", "grund": "Ausgleich"},
                follow_redirects=False)

    with SessionLocal() as db:
        eintraege = list(db.scalars(select(AuditLog).where(
            AuditLog.action == fs.AKTION_KONTO, AuditLog.server_id == server_id)))

    assert len(eintraege) == 1
    assert fs.TRENNER_GRUND + "Ausgleich" in (eintraege[0].details or "")


def test_ein_fehlender_grund_wird_benannt(client, tmp_path, monkeypatch):
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog
    from app.services import freimarken_service as fs

    _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="geld-ohnegrund", port=25693)

    client.post("/freimarken/" + str(server_id) + "/konten",
                data={"spieler": "Anna", "betrag": "500", "grund": ""},
                follow_redirects=False)

    with SessionLocal() as db:
        eintrag = db.scalars(select(AuditLog).where(
            AuditLog.action == fs.AKTION_KONTO,
            AuditLog.server_id == server_id)).first()

    assert fs.OHNE_GRUND in (eintrag.details or "")


@pytest.mark.parametrize("daten,erwartet", [
    ({"spieler": "", "betrag": "500"}, "beide dastehen"),
    ({"spieler": "Anna Bert", "betrag": "500"}, "kein Leerzeichen"),
    ({"spieler": "Anna", "betrag": "viel"}, "ganze Zahl"),
    ({"spieler": "Anna", "betrag": "2000000000"}, "Milliarde"),
])
def test_eine_unbrauchbare_eingabe_wird_abgewiesen(client, tmp_path, monkeypatch,
                                                   daten, erwartet):
    """Abgewiesen, BEVOR etwas gesendet wird.

    Ein Spielername mit Leerzeichen und eine zu grosse Zahl erreichen den Zweig gar
    nicht: Das Argument ist ein Brigadier-Wort bzw. ein LongArgumentType mit Grenzen, und
    darueber antwortet Brigadier englisch. Eine englische Zeile in der Serverkonsole ist
    keine Antwort, die hier jemand liest.
    """
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="geld-kaputt" + str(len(erwartet)),
                        port=25700 + len(erwartet))

    client.post("/freimarken/" + str(server_id) + "/konten", data=daten,
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/konten").text

    assert gesendet == [], "es darf nichts gesendet worden sein"
    assert erwartet in seite


def test_freigeben_sendet_das_bestaetigungswort(client, tmp_path, monkeypatch):
    """Der Befehl verlangt es - es loescht Besitz ohne Rueckzahlung."""
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="besitz-frei", port=25710)

    client.post("/freimarken/" + str(server_id) + "/besitz",
                data={"aktion": "freigeben", "x": "3", "z": "-4", "grund": "Probe"},
                follow_redirects=False)

    assert gesendet == ["freimarken besitz freigeben 3 -4 bestaetigen"]


def test_uebertragen_braucht_einen_namen(client, tmp_path, monkeypatch):
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="besitz-ueber", port=25711)

    client.post("/freimarken/" + str(server_id) + "/besitz",
                data={"aktion": "uebertragen", "x": "3", "z": "4", "spieler": ""},
                follow_redirects=False)
    assert gesendet == []

    client.post("/freimarken/" + str(server_id) + "/besitz",
                data={"aktion": "uebertragen", "x": "3", "z": "4", "spieler": "Bert"},
                follow_redirects=False)
    assert gesendet == ["freimarken besitz uebertragen 3 4 Bert"]


def test_eine_fremde_aktion_wird_abgewiesen(client, tmp_path, monkeypatch):
    """Es gibt genau zwei. Eine dritte waere ein Befehl, den niemand geprueft hat."""
    gesendet = _faelsche_konsole(monkeypatch)
    server_id = _aufbau(client, tmp_path, name="besitz-fremd", port=25712)

    client.post("/freimarken/" + str(server_id) + "/besitz",
                data={"aktion": "loeschen", "x": "3", "z": "4"},
                follow_redirects=False)

    assert gesendet == []


# ------------------------------------------------------------- Mitgliedschaften

def test_eine_mitgliedschaft_nennt_ihr_recht_auf_der_seite(client, tmp_path):
    server_id = _aufbau(client, tmp_path, name="mitglied-seite", port=25720)

    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "AAAAAAAA-1111-4111-8111-111111111111",
                      "name": "Anna", "zuschlag": "parzellen", "zahl": "3",
                      "herkunft": "von Hand", "grund": "November"},
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/mitglieder").text

    assert "freimarken.extra.parzellen.3" in seite
    # Der Manager vergibt das Recht NICHT - er nennt es. Das muss dastehen, sonst haelt
    # der Betreiber die Mitgliedschaft fuer wirksam.
    assert "Rechteverwaltung" in seite
    assert "Anna" in seite


def test_eine_zahl_ohne_wirkung_wird_auf_der_seite_abgewiesen(client, tmp_path):
    """``mitZuschlag`` rechnet mit ``max(zahl, 0)`` - eine 0 bewirkt nichts."""
    server_id = _aufbau(client, tmp_path, name="mitglied-null", port=25721)

    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "aaaaaaaa-1111-4111-8111-111111111111",
                      "zuschlag": "parzellen", "zahl": "0"},
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/mitglieder").text

    assert "ueber 0 liegen" in seite
    assert "freimarken.extra.parzellen.0" not in seite


def test_beenden_laesst_die_zeile_stehen(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    server_id = _aufbau(client, tmp_path, name="mitglied-beenden", port=25722)
    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "aaaaaaaa-1111-4111-8111-111111111111",
                      "zuschlag": "wolken", "zahl": "1", "grund": "Dezember"},
                follow_redirects=False)
    with SessionLocal() as db:
        nummer = ms.liste(db, server_id)[0]["id"]

    client.post("/freimarken/" + str(server_id) + "/mitglieder/beenden",
                data={"eintrag": str(nummer)}, follow_redirects=False)

    with SessionLocal() as db:
        alle = ms.liste(db, server_id)
        laufende = ms.liste(db, server_id, nur_laufende=True)

    assert len(alle) == 1, "die Zeile ist der Beleg und bleibt stehen"
    assert alle[0]["grund"] == "Dezember"
    assert laufende == []


def test_eine_fremde_nummer_beendet_nichts(client, tmp_path):
    """Die Nummer allein genuegt nicht als Bedingung - der Server steht mit dabei."""
    server_id = _aufbau(client, tmp_path, name="mitglied-fremd", port=25723)

    antwort = client.post("/freimarken/" + str(server_id) + "/mitglieder/beenden",
                          data={"eintrag": "999999"}, follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/mitglieder").text

    assert antwort.status_code == 303
    assert "gibt es an diesem Server nicht" in seite


# ----------------------------------------------------------- Die Routenreihenfolge

@pytest.mark.parametrize("seite,erwartet", [
    ("konten", "freimarken_konto_korrigieren"),
    ("besitz", "freimarken_besitz_korrigieren"),
    ("mitglieder", "freimarken_mitglied_anlegen"),
])
def test_die_korrektur_landet_in_ihrer_eigenen_route(seite, erwartet):
    """Starlette nimmt die ERSTE Route, deren Muster passt - und "{seite}" passt auch.

    Geprueft wird die STELLUNG und nicht die Existenz [Fund 22.11.2026]: Hier stand nur,
    dass die Route mit diesem Pfad da ist und wie ihre Funktion heisst. Nach einem Tausch
    der Bloecke ist sie weiter da - die Probe blieb gruen, und die Einspritzung hat es
    gefangen. Die Reihenfolge ist keine Zeile, sondern die Stellung zweier Bloecke
    zueinander, also muss die Probe Stellungen vergleichen.

    Stuenden diese Routen hinter der allgemeinen Speicherroute, landete jede Korrektur
    dort und wuerde als unbekannter Pfad der config.yml gemeldet.
    """
    from app.main import create_app

    app = create_app()
    stellen = [getattr(r, "path", "") for r in app.routes
               if "POST" in (getattr(r, "methods", None) or set())]
    allgemein = "/freimarken/{server_id}/{seite}"
    eigen = "/freimarken/{server_id}/" + seite
    assert allgemein in stellen, "die allgemeine Speicherroute fehlt"
    assert eigen in stellen, "die Route " + eigen + " fehlt"
    assert stellen.index(eigen) < stellen.index(allgemein), (
        eigen + " steht HINTER der allgemeinen Route - jede Korrektur landete dort")

    for route in app.routes:
        if getattr(route, "path", None) == eigen and "POST" in (route.methods or set()):
            assert route.endpoint.__name__ == erwartet
            return
    raise AssertionError("Die Route POST " + eigen + " fehlt")


# ======================================================= DIE ABLAUFPRUEFUNG
#
# WAS BIS ZUM 26.11.2026 FEHLTE: Eine Mitgliedschaft lief nie ab. Das Formular hatte
# kein Feld fuer "bis", die Route schrieb bis=None, und die Rechtetafel liest
# nur_laufende=True - die Zeile verschwand also genau in dem Augenblick, in dem das
# Recht zu ENTZIEHEN war. Der Betreiber sah nie, was er wegnehmen muss.


def test_die_dauer_wird_nach_oben_gerundet():
    """Der bezahlte letzte Tag darf nicht verloren gehen.

    ``laeuft`` vergleicht mit ``>``: Wer bis zum 31. bezahlt hat, ist am 31. noch
    Mitglied. Eine abgerundete Dauer naehme ihm diesen Tag - und das ist der
    Fehler, den er bemerkt.
    """
    from datetime import datetime, timedelta, timezone

    from app.services import freimarken_mitglied_service as ms

    jetzt = datetime(2026, 11, 26, 12, 0, tzinfo=timezone.utc)
    assert ms.luckperms_dauer(jetzt + timedelta(days=30), stichtag=jetzt) == "30d"
    assert ms.luckperms_dauer(jetzt + timedelta(hours=12), stichtag=jetzt) == "12h"
    assert ms.luckperms_dauer(jetzt + timedelta(minutes=45), stichtag=jetzt) \
        == "45m"
    # Die Rundung nach oben: 30 Tage und eine Sekunde sind nicht 30 Tage.
    assert ms.luckperms_dauer(jetzt + timedelta(days=30, seconds=1),
                              stichtag=jetzt) != "30d"
    assert ms.luckperms_dauer(None) is None, "ohne bis laeuft es weiter"


def test_eine_abgelaufene_dauer_ist_null_und_nicht_unbefristet():
    """``0s`` und nicht ``None``.

    ``None`` heisst in dieser Funktion \"unbefristet\" - also das Gegenteil. Wer
    eine abgelaufene Mitgliedschaft nach ihrer Dauer fragt und ``None`` bekaeme,
    setzte das Recht DAUERHAFT.
    """
    from datetime import datetime, timedelta, timezone

    from app.services import freimarken_mitglied_service as ms

    jetzt = datetime(2026, 11, 26, 12, 0, tzinfo=timezone.utc)
    assert ms.luckperms_dauer(jetzt - timedelta(days=1), stichtag=jetzt) == "0s"


def test_das_datum_aus_dem_formular_ist_der_ganze_tag():
    """Wer \"bis 31.12.\" eintraegt, meint den ganzen 31.12."""
    from app.api.routers.freimarken import _bis_aus_formular

    wert = _bis_aus_formular("2026-12-31")

    assert wert is not None
    assert (wert.hour, wert.minute) == (23, 59), (
        "bis ist der LETZTE gueltige Augenblick - mit 00:00 waere der bezahlte"
        " letzte Tag verloren")
    assert _bis_aus_formular("") is None, "leer heisst: laeuft weiter"
    assert _bis_aus_formular("kaputt") is None, (
        "ein Tippfehler in einem FREIWILLIGEN Feld darf die Eingabe nicht"
        " abweisen - eine unbefristete Mitgliedschaft laesst sich beenden")


def test_die_rechtezeile_ist_settemp_wo_es_befristet_ist(client, tmp_path):
    """Ein dauerhaft gesetztes Recht neben einer befristeten Mitgliedschaft ist die
    halbe Einstellung, gegen die dieses Projekt sonst warnt: Die beiden
    widersprechen sich still, und keiner merkt es."""
    server_id = _aufbau(client, tmp_path, name="mitglied-settemp", port=25731)

    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "bbbbbbbb-1111-4111-8111-111111111111",
                      "name": "Anna", "zuschlag": "wolken", "zahl": "2",
                      "bis": "2099-12-31"},
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/mitglieder").text

    assert "permission settemp freimarken.extra.wolken.2 true" in seite, (
        "die Zeile muss BEFRISTET sein - sonst bleibt das Recht stehen, wenn die"
        " Mitgliedschaft ablaeuft")


def test_die_rechtezeile_ist_set_wo_es_unbefristet_ist(client, tmp_path):
    """Die Gegenrichtung: Ohne sie waere ein \"immer settemp\" ebenfalls gruen -
    und dann liefe eine unbefristete Mitgliedschaft nach einer Dauer ab, die
    niemand eingetragen hat."""
    server_id = _aufbau(client, tmp_path, name="mitglied-set", port=25732)

    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "cccccccc-1111-4111-8111-111111111111",
                      "name": "Bert", "zuschlag": "wolken", "zahl": "1"},
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/mitglieder").text

    assert "permission set freimarken.extra.wolken.1 true" in seite
    assert "settemp freimarken.extra.wolken.1" not in seite


def test_ein_abgelaufenes_recht_steht_zum_entzug_auf_der_seite(client, tmp_path):
    """DER ANSCHLUSS. Eine Liste, die nur der Dienst kennt, ist unsichtbar
    wirkungslos - und genau das war der Zustand: Die Rechtetafel liest nur die
    laufenden Zeilen."""
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    server_id = _aufbau(client, tmp_path, name="mitglied-entzug", port=25733)
    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "dddddddd-1111-4111-8111-111111111111",
                      "name": "Cara", "zuschlag": "wolken", "zahl": "3"},
                follow_redirects=False)
    with SessionLocal() as db:
        nummer = ms.liste(db, server_id)[0]["id"]
    client.post("/freimarken/" + str(server_id) + "/mitglieder/beenden",
                data={"eintrag": str(nummer)}, follow_redirects=False)

    seite = client.get("/freimarken/" + str(server_id) + "/mitglieder").text

    assert "zu ENTZIEHEN" in seite, (
        "die Tafel muss erscheinen, sobald ein Recht ungedeckt ist")
    assert "permission unset freimarken.extra.wolken.3" in seite, (
        "und die fertige Zeile zum Kopieren nennen")


def test_eine_verlaengerung_mit_kleinerer_zahl_nennt_den_ERSATZ(client, tmp_path):
    """DER FALL, DER SONST GELD KOSTET.

    Wer zwei Mitgliedschaften auf denselben Zuschlag hatte und nur eine mit
    KLEINERER Zahl verlaengert, behaelt ein Recht - nur ein schwaecheres. Eine
    Liste der abgelaufenen Zeilen naennte hier das alte Recht zum Entzug und
    verschwiege das neue: Der Betreiber entzieht und setzt nichts, und der Spieler
    verliert einen Zuschlag, fuer den er bezahlt hat.
    """
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    server_id = _aufbau(client, tmp_path, name="mitglied-ersatz", port=25734)
    kennung = "eeeeeeee-1111-4111-8111-111111111111"
    for zahl in ("3", "1"):
        client.post("/freimarken/" + str(server_id) + "/mitglieder",
                    data={"kennung": kennung, "name": "Dora",
                          "zuschlag": "wolken", "zahl": zahl},
                    follow_redirects=False)
    with SessionLocal() as db:
        # Die GROESSERE beenden - die kleinere laeuft weiter.
        gross = [z for z in ms.liste(db, server_id) if z["zahl"] == 3][0]["id"]
    client.post("/freimarken/" + str(server_id) + "/mitglieder/beenden",
                data={"eintrag": str(gross)}, follow_redirects=False)

    with SessionLocal() as db:
        offen = ms.zu_entziehen(db, server_id)

    assert len(offen) == 1, offen
    assert offen[0]["recht"] == "freimarken.extra.wolken.3"
    assert offen[0]["statt_recht"] == "freimarken.extra.wolken.1", (
        "was stattdessen gelten MUSS, gehoert daneben - sonst entzieht der"
        " Betreiber das alte und setzt das neue nicht")


def test_ein_noch_gedecktes_recht_steht_NICHT_zum_entzug(client, tmp_path):
    """Die Gegenrichtung: Eine abgelaufene Zeile, deren Recht eine laufende Zeile
    noch deckt, gehoert nicht in die Entzugsliste. Ohne diesen Satz waere ein
    \"nenne alles Abgelaufene\" gruen - und der Betreiber entzoege ein Recht, das
    gelten muss."""
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    server_id = _aufbau(client, tmp_path, name="mitglied-gedeckt", port=25735)
    kennung = "ffffffff-1111-4111-8111-111111111111"
    for _ in range(2):
        client.post("/freimarken/" + str(server_id) + "/mitglieder",
                    data={"kennung": kennung, "name": "Emil",
                          "zuschlag": "wolken", "zahl": "2"},
                    follow_redirects=False)
    with SessionLocal() as db:
        eine = ms.liste(db, server_id)[0]["id"]
    client.post("/freimarken/" + str(server_id) + "/mitglieder/beenden",
                data={"eintrag": str(eine)}, follow_redirects=False)

    with SessionLocal() as db:
        offen = ms.zu_entziehen(db, server_id)

    assert offen == [], (
        "dasselbe Recht laeuft noch ueber die zweite Zeile - es darf nicht zum"
        f" Entzug stehen: {offen}")


def test_was_bald_ablaeuft_steht_auf_der_seite(client, tmp_path):
    """Damit eine Verlaengerung angesprochen werden kann, BEVOR der Zuschlag weg
    ist. Ohne diese Liste erfaehrt man vom Ablauf erst, wenn sich der Spieler
    beschwert - und das ist die Enttaeuschung, gegen die Plan 8.5 geschrieben ist."""
    from datetime import datetime, timedelta, timezone

    server_id = _aufbau(client, tmp_path, name="mitglied-bald", port=25736)
    morgen = (datetime.now(timezone.utc) + timedelta(days=2)).strftime("%Y-%m-%d")

    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "99999999-1111-4111-8111-111111111111",
                      "name": "Finn", "zuschlag": "wolken", "zahl": "1",
                      "bis": morgen},
                follow_redirects=False)
    seite = client.get("/freimarken/" + str(server_id) + "/mitglieder").text

    assert "naechsten Tagen ab" in seite
    assert "Finn" in seite


def test_eine_unbefristete_laeuft_nicht_bald_ab(client, tmp_path):
    """Die Gegenrichtung: Ohne sie stuende jede unbefristete Mitgliedschaft in der
    Ablaufliste, und die Liste waere nach einer Woche unlesbar."""
    from app.db.session import SessionLocal
    from app.services import freimarken_mitglied_service as ms

    server_id = _aufbau(client, tmp_path, name="mitglied-ohne", port=25737)
    client.post("/freimarken/" + str(server_id) + "/mitglieder",
                data={"kennung": "88888888-1111-4111-8111-111111111111",
                      "name": "Gero", "zuschlag": "wolken", "zahl": "1"},
                follow_redirects=False)

    with SessionLocal() as db:
        assert ms.laeuft_bald_ab(db, server_id) == []
