"""Wer im Freimarken-Bereich was darf - und wer nichts.

ZWEI FRAGEN, NICHT EINE
Plan 12a.4 stuft nach ROLLE ab: lesen ab Moderator, Stammdaten aendern nur Admin. Der
Manager stuft zusaetzlich nach SERVER ab (ServerPermission.can_edit_files). Dieser Bereich
schreibt eine Datei im Serverordner und ist damit ein zweiter Weg zu einer Tat, die der
Dateieditor schon kennt. Fragte er nur die Rolle, waere er der Weg um das Dateirecht
herum - ein Admin ohne Dateirecht fuer diesen Server koennte hier dessen config.yml
aendern. Die Probe test_admin_ohne_dateirecht_darf_nicht_schreiben haelt genau das fest.

ZWEIERLEI ABSAGE
Die Seiten geben 303 auf /login, wenn niemand angemeldet ist, und 403 bei zu niedriger
Rolle. Der JSON-Zwilling gibt 401 statt der Umleitung. Darum prueft jede Probe den Status,
der zu ihrem Weg gehoert - und bei einer Absage ausserdem, dass auf der PLATTE nichts
passiert ist. Ein 403, nach dem die Datei trotzdem anders aussieht, ist kein Schutz.
"""
from __future__ import annotations

from pathlib import Path

import pytest


# Eine kleine, aber ECHTE Konfiguration: alle Pfade darin kommen in der ausgelieferten
# config.yml des Plugins vor, sonst kennt der Feldkatalog sie nicht.
CONFIG = """# Freimarken - Einstellungen
wirtschaft:
  parzelle:
    # Was eine Parzelle kostet.
    grundpreis: 25000
    zusammenlegung-je-feld: 10000
  sprung-bestaetigt:
    grundpreis: ""
    zusammenlegung-je-feld: ""

parzellen:
  # NUR BEIM START GELESEN.
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


def _server(db, tmp_path, *, slug, port, mit_freimarken=True):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    if mit_freimarken:
        eigen = basis / "plugins" / "Freimarken"
        eigen.mkdir(parents=True, exist_ok=True)
        with open(eigen / "config.yml", "w", encoding="utf-8", newline="") as offen:
            offen.write(CONFIG)
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _recht(db, person, server, *, can_view=True, can_edit_files=True):
    from app.models.server_permission import ServerPermission

    db.add(ServerPermission(user_id=person.id, server_id=server.id,
                            can_view=can_view, can_console=False, can_restart=False,
                            can_edit_files=can_edit_files, can_manage=False))
    db.commit()


def _aufbau(client, tmp_path, *, name, port, rolle, can_view=True, can_edit_files=True,
            mit_freimarken=True):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        person = _nutzer(db, rolle, name)
        server = _server(db, tmp_path, slug=name, port=port,
                         mit_freimarken=mit_freimarken)
        _recht(db, person, server, can_view=can_view, can_edit_files=can_edit_files)
        server_id = server.id
        datei = Path(server.base_path) / "plugins" / "Freimarken" / "config.yml"

    antwort = client.post("/login", data={"username": name, "password": "Egal12345!"},
                          follow_redirects=False)
    assert antwort.status_code == 303
    return server_id, datei


# ----------------------------------------------------------------------- Lesen

def test_ohne_anmeldung_fuehrt_die_seite_auf_login(client):
    antwort = client.get("/freimarken", follow_redirects=False)
    assert antwort.status_code == 303
    assert antwort.headers["location"] == "/login"


def test_ohne_anmeldung_gibt_der_json_zwilling_401(client):
    """Nicht 303: Ein Programm, das eine Umleitung auf eine HTML-Seite bekommt, haelt sie
    fuer eine Antwort."""
    antwort = client.get("/api/freimarken/1/wirtschaft", follow_redirects=False)
    assert antwort.status_code == 401


def test_view_only_wird_abgewiesen(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="nurlesen", port=25570,
                           rolle="view_only")
    assert client.get("/freimarken", follow_redirects=False).status_code == 403
    assert client.get("/freimarken/" + str(server_id) + "/wirtschaft",
                      follow_redirects=False).status_code == 403
    assert client.get("/api/freimarken/" + str(server_id) + "/wirtschaft",
                      follow_redirects=False).status_code == 403


def test_moderator_darf_lesen(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="moder", port=25571,
                           rolle="moderator")
    antwort = client.get("/freimarken/" + str(server_id) + "/wirtschaft")
    assert antwort.status_code == 200
    assert "grundpreis" in antwort.text
    assert "25000" in antwort.text


def test_moderator_sieht_keinen_speichern_knopf(client, tmp_path):
    """Das Verstecken ist Bequemlichkeit, nicht der Riegel - der steckt in der Route.
    Geprueft wird beides; diese Probe ist die Haelfte fuers Auge."""
    server_id, _ = _aufbau(client, tmp_path, name="moder2", port=25572,
                           rolle="moderator")
    antwort = client.get("/freimarken/" + str(server_id) + "/wirtschaft")
    assert "Speichern</button>" not in antwort.text
    assert "kannst sie aber nicht aendern" in antwort.text


def test_server_ohne_freimarken_erscheint_nicht(client, tmp_path):
    _aufbau(client, tmp_path, name="leerserver", port=25573, rolle="moderator",
            mit_freimarken=False)
    antwort = client.get("/freimarken")
    assert antwort.status_code == 200
    assert "Kein Server mit Freimarken" in antwort.text


def test_ohne_sichtrecht_kein_zugriff_auf_den_server(client, tmp_path):
    """can_view=False: Der Server gehoert nicht in diese Hand, auch nicht lesend."""
    server_id, _ = _aufbau(client, tmp_path, name="fremd", port=25574, rolle="admin",
                           can_view=False, can_edit_files=False)
    assert client.get("/freimarken/" + str(server_id) + "/wirtschaft",
                      follow_redirects=False).status_code == 403


# ------------------------------------------------------------------- Schreiben

def test_moderator_darf_nicht_schreiben(client, tmp_path):
    server_id, datei = _aufbau(client, tmp_path, name="moder3", port=25575,
                               rolle="moderator")
    vorher = datei.read_bytes()
    antwort = client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                          data={"feld:wirtschaft.parzelle.grundpreis": "30000",
                                "grund": "Versuch"},
                          follow_redirects=False)
    assert antwort.status_code == 403
    # Und die Datei ist unberuehrt - byteweise, nicht nur dem Sinn nach.
    assert datei.read_bytes() == vorher


def test_admin_ohne_dateirecht_darf_nicht_schreiben(client, tmp_path):
    """Der Kern dieser Datei: Rolle Admin genuegt nicht, wenn das Dateirecht fehlt.

    Sonst waere dieser Bereich ein Weg um den Dateieditor herum.
    """
    server_id, datei = _aufbau(client, tmp_path, name="adminohne", port=25576,
                               rolle="admin", can_view=True, can_edit_files=False)
    vorher = datei.read_bytes()
    antwort = client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                          data={"feld:wirtschaft.parzelle.grundpreis": "30000",
                                "grund": "Versuch"},
                          follow_redirects=False)
    assert antwort.status_code == 403
    assert datei.read_bytes() == vorher


def test_admin_mit_dateirecht_darf_schreiben(client, tmp_path):
    server_id, datei = _aufbau(client, tmp_path, name="adminmit", port=25577,
                               rolle="admin")
    antwort = client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                          data={"feld:wirtschaft.parzelle.grundpreis": "30000",
                                "grund": "Mehr Spieler"},
                          follow_redirects=False)
    assert antwort.status_code == 303
    assert "grundpreis: 30000" in datei.read_text(encoding="utf-8")


def test_super_admin_braucht_kein_eigenes_serverrecht(client, tmp_path):
    """Ein Super-Admin kommt ueberall hin - so ist es im ganzen Manager, und eine
    Ausnahme nur hier waere eine Ueberraschung."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _nutzer(db, "super_admin", "chef")
        server = _server(db, tmp_path, slug="chefserver", port=25578)
        server_id = server.id
        datei = Path(server.base_path) / "plugins" / "Freimarken" / "config.yml"

    assert client.post("/login", data={"username": "chef", "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303
    antwort = client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                          data={"feld:wirtschaft.parzelle.grundpreis": "26000",
                                "grund": "Feinschliff"},
                          follow_redirects=False)
    assert antwort.status_code == 303
    assert "grundpreis: 26000" in datei.read_text(encoding="utf-8")


def test_jede_aenderung_steht_im_protokoll(client, tmp_path):
    """Plan 12a.2 Regel 3: wer, wann, was, warum. Der Grund ist der Teil, der sonst fehlt."""
    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog

    server_id, _ = _aufbau(client, tmp_path, name="buchfuehrung", port=25579,
                           rolle="admin")
    client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                data={"feld:wirtschaft.parzelle.grundpreis": "27000",
                      "grund": "Testgrund 4711"},
                follow_redirects=False)

    with SessionLocal() as db:
        eintraege = [e for e in db.query(AuditLog).all()
                     if e.action == "freimarken.stammdaten"]
    assert len(eintraege) == 1, "genau eine Protokollzeile erwartet"
    text = eintraege[0].details or ""
    assert "wirtschaft.parzelle.grundpreis" in text
    assert "25000 -> 27000" in text, "alter UND neuer Wert muessen dastehen"
    assert "Testgrund 4711" in text, "der Grund muss dastehen"


def test_unbekannte_seite_gibt_404(client, tmp_path):
    server_id, _ = _aufbau(client, tmp_path, name="seitenlos", port=25580,
                           rolle="admin")
    assert client.get("/freimarken/" + str(server_id) + "/goldesel",
                      follow_redirects=False).status_code == 404
    assert client.post("/freimarken/" + str(server_id) + "/goldesel",
                       data={"feld:wirtschaft.parzelle.grundpreis": "1"},
                       follow_redirects=False).status_code == 404


def test_fremde_felder_landen_nicht_in_der_datei(client, tmp_path):
    """Nur Formularfelder mit dem Vorsatz "feld:" sind Einstellungen.

    Ohne diese Trennung schriebe ein Formularfeld wie "grund" einen Schluessel grund: in
    die config.yml - und das Plugin klagte beim naechsten Start ueber eine unbekannte
    Einstellung.
    """
    server_id, datei = _aufbau(client, tmp_path, name="sauber", port=25581,
                               rolle="admin")
    client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                data={"feld:wirtschaft.parzelle.grundpreis": "28000",
                      "grund": "Soll nicht in der Datei stehen",
                      "irgendwas": "auch nicht"},
                follow_redirects=False)
    inhalt = datei.read_text(encoding="utf-8")
    assert "grundpreis: 28000" in inhalt
    assert "Soll nicht in der Datei stehen" not in inhalt
    assert "irgendwas" not in inhalt
