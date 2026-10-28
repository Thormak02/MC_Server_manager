"""Der Dateieditor als dritte Tuer zu op/deop/pardon/whitelist off.

Dieselbe Regel, dritter Anlauf. Erst galt die Admin-Sperre nur fuer den fluechtigen
Konsolenbefehl; dann kamen Zeitplan und Zugriffsseite dazu. Offen blieb der Weg, der am
naechsten liegt: ops.json im Dateieditor oeffnen und sich mit Level 4 eintragen. Das
Ergebnis ist dasselbe wie ``op`` - nur dauerhaft, denn genau diese Datei liest Minecraft
beim Start.

Mitgeprueft wird deshalb jeder Weg, der eine dieser Dateien schreibt oder loescht:
Freitext, Assistent, Upload (Formular und API), "Textdatei erstellen", Loeschen
(Formular und API) - und das Werkzeug ``einstellung_setzen`` des KI-Agenten.
"""
import json
from pathlib import Path

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


def _server(db, tmp_path, *, slug, port):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\nwhite-list=true\nmotd=Hallo\n",
        encoding="utf-8")
    (basis / "ops.json").write_text("[]\n", encoding="utf-8")
    (basis / "banned-players.json").write_text(
        '[{"name": "Stoerer", "uuid": "0-0-0-0-1"}]\n', encoding="utf-8")
    (basis / "banned-ips.json").write_text(
        '[{"ip": "203.0.113.7"}]\n', encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _recht(db, person, server, **felder):
    from app.models.server_permission import ServerPermission

    db.add(ServerPermission(user_id=person.id, server_id=server.id,
                            can_view=felder.get("can_view", True),
                            can_console=felder.get("can_console", False),
                            can_restart=felder.get("can_restart", False),
                            can_edit_files=felder.get("can_edit_files", True),
                            can_manage=felder.get("can_manage", False)))
    db.commit()


def _aufbau(client, tmp_path, *, name, port, rolle="moderator"):
    """Nutzer mit Dateirecht anlegen, anmelden - und Server-Id samt Ordner liefern."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        person = _nutzer(db, rolle, name)
        server = _server(db, tmp_path, slug=name, port=port)
        _recht(db, person, server, can_edit_files=True)
        server_id, basis = server.id, Path(server.base_path)

    antwort = client.post("/login", data={"username": name, "password": "Egal12345!"},
                          follow_redirects=False)
    assert antwort.status_code == 303
    return server_id, basis


OP_EINTRAG = json.dumps([{"uuid": "11111111-2222-3333-4444-555555555555",
                          "name": "Thormak02", "level": 4,
                          "bypassesPlayerLimit": True}])


# ------------------------------------------------------------- Die Regel selbst

@pytest.mark.parametrize("pfad", ["ops.json", "./ops.json", "OPS.JSON",
                                  "banned-players.json", "banned-ips.json"])
def test_diese_dateien_sind_admin_sache(pfad):
    from app.services.process_service import datei_braucht_admin

    assert datei_braucht_admin(pfad), f"{pfad!r} muesste gesperrt sein"


@pytest.mark.parametrize("pfad", [
    "whitelist.json",                 # Spieler zulassen bleibt Moderatorenarbeit
    "server.properties",              # nur die eine Zeile zaehlt, nicht die Datei
    "backups/ops.json",               # liest Minecraft nicht
    "plugins/Irgendwas/ops.json",
    "spigot.yml",
    "",
])
def test_diese_dateien_darf_ein_moderator_aendern(pfad):
    """Ueberzusperren waere auch ein Fehler - ein Moderator muss arbeiten koennen."""
    from app.services.process_service import datei_braucht_admin

    assert not datei_braucht_admin(pfad), f"{pfad!r} wurde faelschlich gesperrt"


@pytest.mark.parametrize("alt,neu,gesperrt", [
    ("white-list=true\n", "white-list=false\n", True),
    ("white-list=true\n", "motd=x\n", True),            # Zeile geloescht = aus
    ("white-list=true\n", "white-list=TRUE\n", False),
    ("white-list=false\n", "white-list=true\n", False),
    ("white-list=false\n", "white-list=false\n", False),
    ("motd=x\n", "white-list=false\n", False),          # war nie an
    ("white-list=true\n", "# white-list=false\nwhite-list=true\n", False),
])
def test_whitelist_nur_das_abschalten_ist_admin_sache(alt, neu, gesperrt):
    from app.services.process_service import whitelist_wird_abgeschaltet

    ergebnis = bool(whitelist_wird_abgeschaltet("server.properties", alt, neu))

    assert ergebnis is gesperrt


# ------------------------------------------------------------- Ueber die Oberflaeche

def test_freitext_speichern_traegt_keinen_operator_ein(client, tmp_path):
    server_id, basis = _aufbau(client, tmp_path, name="de-freitext", port=25901)

    antwort = client.post(f"/servers/{server_id}/files/save",
                          data={"relative_path": "ops.json", "content": OP_EINTRAG},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert "Thormak02" not in (basis / "ops.json").read_text(encoding="utf-8")


def test_assistent_speichern_traegt_keinen_operator_ein(client, tmp_path):
    """Der JSON-Assistent schreibt dieselbe Datei - also dieselbe Schranke."""
    server_id, basis = _aufbau(client, tmp_path, name="de-assistent", port=25902)
    (basis / "ops.json").write_text(
        json.dumps([{"uuid": "1-2-3-4-5", "name": "Thormak02", "level": 1}]),
        encoding="utf-8")

    seite = client.get(f"/servers/{server_id}/files?file=ops.json&mode=assistant")
    assert seite.status_code == 200

    antwort = client.post(f"/servers/{server_id}/files/assistant-save", data={
        "relative_path": "ops.json", "extras_text": OP_EINTRAG,
    }, follow_redirects=False)

    assert antwort.status_code == 303
    inhalt = json.loads((basis / "ops.json").read_text(encoding="utf-8"))
    assert inhalt[0]["level"] == 1, "Das Level wurde angehoben"


def test_upload_ueberschreibt_die_ops_json_nicht(client, tmp_path):
    server_id, basis = _aufbau(client, tmp_path, name="de-upload", port=25903)

    antwort = client.post(f"/servers/{server_id}/files/upload",
                          files={"upload": ("ops.json", OP_EINTRAG.encode("utf-8"),
                                            "application/json")},
                          data={"target_dir": "."}, follow_redirects=False)

    assert antwort.status_code == 303
    assert "Thormak02" not in (basis / "ops.json").read_text(encoding="utf-8")


def test_upload_ueber_die_api_ebenfalls_nicht(client, tmp_path):
    server_id, basis = _aufbau(client, tmp_path, name="de-api-upload", port=25904)

    antwort = client.post(f"/api/servers/{server_id}/files/upload",
                          files={"upload": ("ops.json", OP_EINTRAG.encode("utf-8"),
                                            "application/json")},
                          data={"target_dir": "."})

    assert antwort.status_code == 403
    assert "Thormak02" not in (basis / "ops.json").read_text(encoding="utf-8")


def test_neue_datei_anlegen_ist_kein_schlupfloch(client, tmp_path):
    """Gibt es die ops.json noch nicht, waere 'Textdatei erstellen' der Weg."""
    server_id, basis = _aufbau(client, tmp_path, name="de-neu", port=25905)
    (basis / "ops.json").unlink()

    antwort = client.post(f"/servers/{server_id}/files/create-text",
                          data={"relative_path": "ops.json",
                                "initial_content": OP_EINTRAG},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert not (basis / "ops.json").exists()


def test_banns_lassen_sich_nicht_wegloeschen(client, tmp_path):
    """Eine geloeschte banned-players.json ist ein Massen-pardon."""
    server_id, basis = _aufbau(client, tmp_path, name="de-loeschen", port=25906)

    antwort = client.post(f"/servers/{server_id}/files/delete",
                          data={"relative_path": "banned-players.json"},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert (basis / "banned-players.json").exists()
    assert "Stoerer" in (basis / "banned-players.json").read_text(encoding="utf-8")


def test_banns_lassen_sich_auch_ueber_die_api_nicht_wegloeschen(client, tmp_path):
    server_id, basis = _aufbau(client, tmp_path, name="de-api-loeschen", port=25907)

    antwort = client.delete(
        f"/api/servers/{server_id}/files?path=banned-ips.json")

    assert antwort.status_code == 403
    assert (basis / "banned-ips.json").exists()


def test_whitelist_bleibt_an(client, tmp_path):
    server_id, basis = _aufbau(client, tmp_path, name="de-wl", port=25908)
    neu = (basis / "server.properties").read_text(encoding="utf-8").replace(
        "white-list=true", "white-list=false")

    antwort = client.post(f"/servers/{server_id}/files/save",
                          data={"relative_path": "server.properties", "content": neu},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert "white-list=true" in (basis / "server.properties").read_text(encoding="utf-8")


def test_der_rest_von_server_properties_bleibt_aenderbar(client, tmp_path):
    """Nur das Abschalten ist gesperrt - die MOTD darf ein Moderator umschreiben."""
    server_id, basis = _aufbau(client, tmp_path, name="de-motd", port=25909)
    neu = (basis / "server.properties").read_text(encoding="utf-8").replace(
        "motd=Hallo", "motd=Guten Tag")

    antwort = client.post(f"/servers/{server_id}/files/save",
                          data={"relative_path": "server.properties", "content": neu},
                          follow_redirects=False)

    assert antwort.status_code == 303
    inhalt = (basis / "server.properties").read_text(encoding="utf-8")
    assert "motd=Guten Tag" in inhalt
    assert "white-list=true" in inhalt


def test_andere_dateien_bleiben_bearbeitbar(client, tmp_path):
    server_id, basis = _aufbau(client, tmp_path, name="de-normal", port=25910)
    (basis / "spigot.yml").write_text("settings:\n  bungeecord: false\n", encoding="utf-8")

    antwort = client.post(f"/servers/{server_id}/files/save",
                          data={"relative_path": "spigot.yml",
                                "content": "settings:\n  bungeecord: true\n"},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert "bungeecord: true" in (basis / "spigot.yml").read_text(encoding="utf-8")


def test_die_abweisung_steht_im_protokoll(client, tmp_path):
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog

    server_id, _ = _aufbau(client, tmp_path, name="de-protokoll", port=25911)
    client.post(f"/servers/{server_id}/files/save",
                data={"relative_path": "ops.json", "content": OP_EINTRAG},
                follow_redirects=False)

    with SessionLocal() as db:
        eintraege = [e for e in db.scalars(select(AuditLog).where(
            AuditLog.action == "server.access_change_denied"))
            if e.server_id == server_id]

    assert len(eintraege) == 1
    assert "ops.json" in (eintraege[0].details or "")


@pytest.mark.parametrize("rolle,port", [("admin", 25912), ("super_admin", 25913)])
def test_ein_admin_darf_weiterhin(client, tmp_path, rolle, port):
    server_id, basis = _aufbau(client, tmp_path, name=f"de-{rolle}", port=port,
                               rolle=rolle)

    antwort = client.post(f"/servers/{server_id}/files/save",
                          data={"relative_path": "ops.json", "content": OP_EINTRAG},
                          follow_redirects=False)

    assert antwort.status_code == 303
    assert "Thormak02" in (basis / "ops.json").read_text(encoding="utf-8")


# ------------------------------------------------------------- KI-Agent

def test_agent_schaltet_die_whitelist_fuer_moderatoren_nicht_ab(client, tmp_path):
    """Vierter Weg zur selben Tat: das Werkzeug einstellung_setzen."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "de-agent")
        server = _server(db, tmp_path, slug="de-agent", port=25914)
        _recht(db, person, server, can_edit_files=True)
        basis = Path(server.base_path)

        with pytest.raises(ai_agent_service.AgentFehler) as fehler:
            ai_agent_service.fuehre_aus(
                db, user=person, werkzeug_name="einstellung_setzen",
                parameter={"server_id": server.id, "schluessel": "white-list",
                           "wert": "false"})

    assert "Admin-Rechte" in str(fehler.value)
    assert "white-list=true" in (basis / "server.properties").read_text(encoding="utf-8")


def test_agent_darf_andere_einstellungen_weiterhin_setzen(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "de-agent-ok")
        server = _server(db, tmp_path, slug="de-agent-ok", port=25915)
        _recht(db, person, server, can_edit_files=True)
        basis = Path(server.base_path)

        ergebnis = ai_agent_service.fuehre_aus(
            db, user=person, werkzeug_name="einstellung_setzen",
            parameter={"server_id": server.id, "schluessel": "motd",
                       "wert": "Neuer Tag"})

    assert ergebnis
    assert "motd=Neuer Tag" in (basis / "server.properties").read_text(encoding="utf-8")
