"""Der eigene Freimarken-Bereich: Markierung, Dashboard-Filter, Reiter, Erstellen.

DREI ZUSAGEN HATTEN BISHER KEINE PROBE, und die dritte ist die gefaehrlichste:

1. Ein markierter Server fehlt im Dashboard. Keine Probe im Haus prueft, dass ein Server
   dort AUFTAUCHT - der Filter koennte also jeden verschlucken, und niemand saehe es.
2. Ein nicht markierter Server steht weiter da. Ohne diese Richtung waere ein
   "blende alles aus" gruen.
3. /api/resources liefert dieselbe Liste. KEINE einzige Probe im Haus ruft diese Route
   auf - und app/web/static/js/app.js setzt daraus die Kacheln "Server gesamt" und
   "Server laufend", schon beim Laden der Seite. Ein Filter nur in get_dashboard_summary
   waere vollstaendig gruen und im Browser falsch.

DAZU EINE MIGRATIONSPROBE: In Proben legt immer create_all die Spalten an, der
ALTER-Zweig laeuft nie. Ein vergessener Eintrag in der Migrationsliste bliebe darum
unsichtbar - bis die vorhandene Datenbank nach einem Neustart bei jeder Abfrage auf
servers mit "no such column" abbricht.
"""
from __future__ import annotations

import pytest


def _nutzer(db, rolle, name):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _server(db, tmp_path, *, slug, port, freimarken=False):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar",
                    freimarken_enabled=freimarken)
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _anmelden(client, db, rolle, name):
    _nutzer(db, rolle, name)
    assert client.post("/login", data={"username": name, "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303


# ------------------------------------------------------- Die Spalte und ihr Nachtrag

def test_die_spalte_ist_da_und_der_nachtrag_ist_eingetragen(client):
    """ZWEI Dinge, und das zweite ist das eigentliche.

    Die Spalte selbst legt create_all an - das prueft der erste Satz. Der zweite prueft,
    dass sie AUCH in der Migrationsliste steht: Ohne diesen Eintrag bekommt eine
    vorhandene Datenbank sie nie, und danach bricht jede Abfrage auf servers ab. In
    Proben laeuft der ALTER-Zweig nie, also ist die Liste selbst der Pruefgegenstand.
    """
    from sqlalchemy import inspect

    from app.db import init_db
    from app.db.session import engine

    spalten = {c["name"] for c in inspect(engine).get_columns("servers")}
    assert "freimarken_enabled" in spalten

    # Der Nachtrag muss die Spalte NENNEN - und zwar zeichengleich. Passt der Name nicht,
    # steht der Eintrag bei jedem Start erneut in pending, und das zweite ALTER wirft
    # "duplicate column name" aus einem Block ohne try: Die Anwendung startet nicht mehr.
    quelle = init_db._ensure_server_schema.__code__.co_consts
    alle = " ".join(str(k) for k in quelle)
    assert "freimarken_enabled" in alle, (
        "Die Spalte fehlt in der Migrationsliste von _ensure_server_schema - eine "
        "bestehende Datenbank bekommt sie dann nie.")


def test_der_nachtrag_ueberlebt_einen_zweiten_lauf(client):
    """migriere-Nachtraege muessen folgenlos wiederholbar sein."""
    from app.db import init_db

    init_db._ensure_server_schema()
    init_db._ensure_server_schema()


def test_ohne_angabe_ist_ein_server_kein_freimarken_server(client, tmp_path):
    """Die Vorgabe ist der Faenger: Alle vorhandenen Server bleiben im Dashboard."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        basis = tmp_path / "vorgabe"
        basis.mkdir()
        server = Server(name="vorgabe", slug="vorgabe", server_type="paper",
                        mc_version="26.3", base_path=str(basis), port=25760,
                        status="stopped", start_mode="command",
                        start_command="java -jar server.jar")
        db.add(server)
        db.commit()
        db.refresh(server)

        assert server.freimarken_enabled is False


# --------------------------------------------------------- Der Dashboard-Filter

def test_ein_freimarken_server_fehlt_im_dashboard(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _server(db, tmp_path, slug="imbereich", port=25761, freimarken=True)
        _server(db, tmp_path, slug="normal", port=25762)
        _anmelden(client, db, "super_admin", "dash-filter")

    seite = client.get("/dashboard").text

    assert "normal" in seite, "ein gewoehnlicher Server muss weiter dastehen"
    assert "imbereich" not in seite


def test_die_zahl_im_dashboard_zaehlt_ihn_nicht_mit(client, tmp_path):
    """Liste und Zahl kommen aus derselben Quelle - sonst widersprechen sie sich."""
    from app.db.session import SessionLocal
    from app.models.user import User
    from app.services.server_service import get_dashboard_summary

    with SessionLocal() as db:
        _server(db, tmp_path, slug="zahl-a", port=25763, freimarken=True)
        _server(db, tmp_path, slug="zahl-b", port=25764)
        person = _nutzer(db, "super_admin", "dash-zahl")

        zusammen = get_dashboard_summary(db, db.get(User, person.id))

    assert zusammen["total_servers"] == 1
    namen = {s.slug for s in zusammen["servers"]}
    assert namen == {"zahl-b"}


def test_die_ressourcenliste_zeigt_ihn_auch_nicht(client, tmp_path):
    """DIE WICHTIGSTE DIESER PROBEN.

    app/web/static/js/app.js setzt die Kacheln "Server gesamt" und "Server laufend" aus
    DIESER Liste, und refresh() laeuft schon beim Laden der Seite. Ein Filter nur in
    get_dashboard_summary wirkt darum fuer den Bruchteil einer Sekunde - und vor dem
    22.11.2026 rief KEINE Probe im Haus diese Route auf. Der Filter waere gruen gewesen
    und im Browser falsch.
    """
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _server(db, tmp_path, slug="res-imbereich", port=25765, freimarken=True)
        _server(db, tmp_path, slug="res-normal", port=25766)
        _anmelden(client, db, "super_admin", "dash-res")

    antwort = client.get("/api/resources")
    assert antwort.status_code == 200
    # Die Antwort heisst "entries", und je Eintrag steht der Server unter "server".
    # Genau diese Laenge liest app.js fuer die Kachel "Server gesamt".
    eintraege = antwort.json()["entries"]
    namen = {e["server"]["name"] for e in eintraege}

    assert "res-normal" in namen
    assert "res-imbereich" not in namen
    assert len(eintraege) == 1, (
        "die Laenge dieser Liste IST die Kachel 'Server gesamt' im Browser")


def test_die_uebrigen_listen_behalten_ihn(client, tmp_path):
    """Nur das Dashboard - Backupplan, Zeitplan und Serverauswahl sollen ihn weiter sehen.

    Geprueft am Tor list_servers_for_user, denn genau daraus speisen sich jene Listen.
    Haette der Filter dort gestanden, waere der Server aus allen verschwunden.
    """
    from app.db.session import SessionLocal
    from app.models.user import User
    from app.services.server_service import (list_dashboard_servers,
                                             list_servers_for_user)

    with SessionLocal() as db:
        _server(db, tmp_path, slug="tor-imbereich", port=25767, freimarken=True)
        person = _nutzer(db, "super_admin", "tor-alle")
        user = db.get(User, person.id)

        alle = {s.slug for s in list_servers_for_user(db, user)}
        nur_dashboard = {s.slug for s in list_dashboard_servers(db, user)}

    assert "tor-imbereich" in alle, "das Tor darf ihn nicht verlieren"
    assert "tor-imbereich" not in nur_dashboard


def test_das_leere_dashboard_nennt_den_bereich(client, tmp_path):
    """Gibt es NUR Freimarken-Server, behauptete das Dashboard, es gebe keinen."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _server(db, tmp_path, slug="nurbereich", port=25768, freimarken=True)
        _anmelden(client, db, "super_admin", "dash-leer")

    seite = client.get("/dashboard").text

    assert "Noch keine sichtbaren Server" in seite
    assert "/freimarken" in seite, "der Hinweis macht die Aussage erst wahr"


# ------------------------------------------------------ Die gemeinsame Reiterleiste

def test_eine_serverseite_zeigt_die_freimarken_reiter(client, tmp_path):
    """Das ist "alles im Freimarken-Tab": dieselben Seiten, eine Leiste.

    Geprueft auf der KONSOLENSEITE, also einer der acht Manager-Seiten - dort muessen die
    Katalogreiter stehen, damit ein Freimarken-Server vollstaendig an einem Ort bedient
    wird.
    """
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="leiste", port=25770, freimarken=True)
        person = _nutzer(db, "admin", "leiste-admin")
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True,
                                can_console=True, can_restart=False,
                                can_edit_files=True, can_manage=False))
        db.commit()
        server_id = server.id
    assert client.post("/login", data={"username": "leiste-admin",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    seite = client.get("/servers/" + str(server_id) + "/console").text

    assert "Konsole" in seite, "die Manager-Reiter bleiben"
    assert "/freimarken/" + str(server_id) + "/wirtschaft" in seite
    assert "/freimarken/" + str(server_id) + "/konten" in seite


def test_ein_gewoehnlicher_server_zeigt_sie_nicht(client, tmp_path):
    """Die Gegenrichtung. Ohne sie waere ein "zeige sie immer" gruen - und jeder Server
    haette zwoelf Reiter in einen Bereich, in den er nicht gehoert."""
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="leiste-normal", port=25771)
        person = _nutzer(db, "admin", "leiste-normal-admin")
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True,
                                can_console=True, can_restart=False,
                                can_edit_files=True, can_manage=False))
        db.commit()
        server_id = server.id
    assert client.post("/login", data={"username": "leiste-normal-admin",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    seite = client.get("/servers/" + str(server_id) + "/console").text

    assert "Konsole" in seite
    assert "/freimarken/" + str(server_id) + "/wirtschaft" not in seite


def test_view_only_bekommt_keine_reiter_ins_403(client, tmp_path):
    """Die acht Serverseiten verlangen nur can_view, der Freimarken-Bereich Moderator.

    Ohne die Rollenfrage in der Leiste bekaeme ein view_only-Nutzer zwoelf Reiter, die
    alle 403 liefern - ein Menue, das nur aus Absagen besteht.
    """
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="leiste-view", port=25772, freimarken=True)
        person = _nutzer(db, "view_only", "leiste-view-nutzer")
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True,
                                can_console=False, can_restart=False,
                                can_edit_files=False, can_manage=False))
        db.commit()
        server_id = server.id
    assert client.post("/login", data={"username": "leiste-view-nutzer",
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303

    seite = client.get("/servers/" + str(server_id)).text

    assert "/freimarken/" + str(server_id) + "/wirtschaft" not in seite
    # Und die Absage gilt wirklich - sonst waere die Leiste zu streng und nicht zu lasch.
    assert client.get("/freimarken/" + str(server_id) + "/wirtschaft",
                      follow_redirects=False).status_code == 403


# ----------------------------------------------------------- Der Erstellen-Knopf

def test_der_knopf_steht_in_der_uebersicht(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _anmelden(client, db, "super_admin", "knopf-super")

    seite = client.get("/freimarken").text

    assert "/servers/create?freimarken=1" in seite


def test_der_knopf_fehlt_einem_moderator(client, tmp_path):
    """Die Zielroute laesst nur den Super-Admin herein - ein Knopf fuer Moderatoren
    fuehrte stumm auf die Anmeldeseite."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _anmelden(client, db, "moderator", "knopf-mod")

    seite = client.get("/freimarken").text

    assert "/servers/create" not in seite
    assert "nur der Super-Admin" in seite, "stattdessen soll dastehen, warum"


def test_der_knopf_bleibt_auch_bei_einem_server_erreichbar(client, tmp_path):
    """Hier leitete die Uebersicht bei genau EINEM Server sofort weiter.

    Der Knopf waere damit ab dem ersten Server unerreichbar gewesen und beim zweiten
    wieder da - was wie ein Fehler aussieht.
    """
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _server(db, tmp_path, slug="einziger", port=25773, freimarken=True)
        _anmelden(client, db, "super_admin", "knopf-einer")

    antwort = client.get("/freimarken", follow_redirects=False)

    assert antwort.status_code == 200, "keine Umleitung mehr"
    assert "/servers/create?freimarken=1" in antwort.text
    assert "einziger" in antwort.text


def test_die_adresse_belegt_das_haekchen_vor(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        _anmelden(client, db, "super_admin", "haekchen")

    mit = client.get("/servers/create?freimarken=1").text
    ohne = client.get("/servers/create").text

    assert 'name="freimarken_enabled"' in mit
    assert "checked" in mit.split('name="freimarken_enabled"')[1][:120]
    assert "checked" not in ohne.split('name="freimarken_enabled"')[1][:120]


# ------------------------------------------------- Die Markierung durch die Kette

def test_das_haekchen_kommt_bis_an_die_zeile(client, tmp_path, monkeypatch):
    """VIER Schichten schreiben jedes Feld einzeln ab.

    Eine vergessene verliert die Markierung OHNE Fehler - der Server entsteht einfach als
    normaler Server. Darum wird hier der ganze Weg gegangen, vom Formular bis zur Zeile.
    """
    from app.db.session import SessionLocal
    with SessionLocal() as db:
        _anmelden(client, db, "super_admin", "kette")

    antwort = client.post("/servers/create", data={
        "name": "kettenserver",
        "server_type": "vanilla",
        # Feste Version wie in test_provisioning.py - der Erstellungsweg laeuft
        # offline und braucht keine Abfrage beim Anbieter.
        "mc_version": "1.20.1",
        "target_path": str(tmp_path / "kettenserver"),
        "freimarken_enabled": "1",
    }, follow_redirects=False)

    assert antwort.status_code == 303
    from sqlalchemy import select

    from app.models.server import Server
    with SessionLocal() as db:
        server = db.scalars(select(Server).where(Server.name == "kettenserver")).first()
        assert server is not None, "der Server muss angelegt sein"
        assert server.freimarken_enabled is True
        ziel = antwort.headers["location"]

    # UND die Umleitung fuehrt in den eigenen Bereich, nicht auf die Serverseite, aus der
    # er heraussoll.
    assert ziel.startswith("/freimarken/")


def test_ohne_haekchen_bleibt_es_ein_gewoehnlicher_server(client, tmp_path):
    """Die Gegenrichtung, und sie haelt zugleich die alte Umleitung.

    Zwei Proben im Haus rechnen die Server-Id aus der Adresse /servers/<id> - ein
    unbedingter Wechsel des Ziels haette sie rot gemacht.
    """
    from app.db.session import SessionLocal
    with SessionLocal() as db:
        _anmelden(client, db, "super_admin", "kette-ohne")

    antwort = client.post("/servers/create", data={
        "name": "gewoehnlich",
        "server_type": "vanilla",
        # Feste Version wie in test_provisioning.py - der Erstellungsweg laeuft
        # offline und braucht keine Abfrage beim Anbieter.
        "mc_version": "1.20.1",
        "target_path": str(tmp_path / "gewoehnlich"),
    }, follow_redirects=False)

    assert antwort.status_code == 303
    assert antwort.headers["location"].startswith("/servers/")

    from sqlalchemy import select

    from app.models.server import Server
    with SessionLocal() as db:
        server = db.scalars(select(Server).where(Server.name == "gewoehnlich")).first()
        assert server.freimarken_enabled is False


@pytest.mark.parametrize("wert,erwartet", [
    ("1", True), ("true", True), ("on", True), ("ja", True),
    ("", False), ("0", False), ("nein", False), (None, False),
])
def test_was_als_ja_gilt_steht_an_einer_stelle(wert, erwartet):
    """Zwei Abschriften derselben Vorstellung von "ja" liefen auseinander.

    Dann belegte die Adresse das Haekchen vor, und das Senden machte daraus trotzdem einen
    normalen Server - ohne Fehler und ohne Meldung.
    """
    from app.api.routers.provisioning import _ist_ja

    assert _ist_ja(wert) is erwartet
