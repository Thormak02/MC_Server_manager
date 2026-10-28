"""Tests zu den Fehlern der zweiten gegnerischen Pruefung.

Die beiden schweren hatten dieselbe Ursache wie schon einmal: ich hatte die Regel an
EINER Stelle eingebaut und nicht gefragt, welche anderen Wege zur selben Tat fuehren.

* Ein Zeitplan-Job vom Typ "command" lief mit ``initiated_by_user_id=None``, und das
  heisst in process_service "der Manager selbst" - die Admin-Sperre griff dort nie. Zwei
  normale Formular-Abschickungen genuegten, und jeder mit can_console konnte sich
  Operator-Rechte verschaffen, dauerhaft per Cron.
* Die Zugriffsseite schreibt ops.json, banned-*.json und server.properties DIREKT. Das
  ist der dauerhafte Weg - genau diese Dateien liest Minecraft beim Start. Die Sperre
  deckte nur den fluechtigen Konsolenbefehl ab und war mit can_edit_files umgehbar.
"""
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


def _server(db, tmp_path, *, slug, port, typ="paper"):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\nwhite-list=true\n", encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type=typ, mc_version="26.3",
                    base_path=str(basis), port=port, status="running",
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
                            can_edit_files=felder.get("can_edit_files", False),
                            can_manage=felder.get("can_manage", False)))
    db.commit()


# ----------------------------------------------------- Zeitplan als Hintertuer

def test_zeitplan_job_mit_op_wird_beim_anlegen_abgewiesen(client, tmp_path):
    """Die Absage soll beim ANLEGEN kommen, nicht erst nachts bei der Ausfuehrung."""
    from app.db.session import SessionLocal
    from app.services.schedule_service import create_job

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "zp-mod")
        server = _server(db, tmp_path, slug="zp-mod", port=25821)
        _recht(db, person, server, can_view=True, can_console=True)

        with pytest.raises(ValueError, match="Admin-Rechte"):
            create_job(db, server_id=server.id, job_type="command",
                       schedule_expression="0 4 * * *",
                       command_payload={"command": "op Thormak02"},
                       created_by_user_id=person.id)


def test_admin_darf_den_zeitplan_job_anlegen(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services.schedule_service import create_job

    with SessionLocal() as db:
        person = _nutzer(db, "admin", "zp-admin")
        server = _server(db, tmp_path, slug="zp-admin", port=25822)

        job = create_job(db, server_id=server.id, job_type="command",
                         schedule_expression="0 4 * * *",
                         command_payload={"command": "op Thormak02"},
                         created_by_user_id=person.id)

    assert job.created_by_user_id == person.id


def test_normaler_zeitplan_job_bleibt_moeglich(client, tmp_path):
    """Die Sperre darf den gewoehnlichen Gebrauch nicht behindern."""
    from app.db.session import SessionLocal
    from app.services.schedule_service import create_job

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "zp-normal")
        server = _server(db, tmp_path, slug="zp-normal", port=25823)
        _recht(db, person, server, can_view=True, can_console=True)

        job = create_job(db, server_id=server.id, job_type="command",
                         schedule_expression="0 4 * * *",
                         command_payload={"command": "save-all"},
                         created_by_user_id=person.id)

    assert job.id > 0


def test_job_ohne_bekannten_ersteller_fuehrt_op_nicht_aus(client, tmp_path):
    """Jobs aus der Zeit vor der Spalte haben created_by_user_id = NULL. Die duerfen
    keinen Befehl mit Admin-Vorbehalt ausfuehren - sonst waere der Altbestand das Loch."""
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="zp-alt", port=25824)

        # So laeuft ein Job ohne bekannten Ersteller.
        ok, meldung = send_console_command(db, server, "op Thormak02",
                                           initiated_by_user_id=None,
                                           system_fallback=False)

    assert ok is False
    assert "Admin-Rechte" in meldung
    assert "neu anlegen" in meldung


def test_manager_eigene_automatik_bleibt_erlaubt(client, tmp_path):
    """Ein Aufruf ohne Nutzerbezug (echte Automatik) darf weiterhin alles."""
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="zp-system", port=25825)

        ok, meldung = send_console_command(db, server, "whitelist off",
                                           initiated_by_user_id=None)

    assert ok is False
    assert "Admin-Rechte" not in meldung
    assert "nicht aktiv" in meldung


def test_zeitplan_job_laeuft_mit_den_rechten_des_erstellers(client, tmp_path):
    """Der entscheidende Test: der Job ist angelegt (etwa von einem damaligen Admin),
    der Ersteller ist inzwischen Moderator - dann darf der Job das nicht mehr."""
    from app.db.session import SessionLocal
    from app.models.scheduled_job import ScheduledJob
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "zp-degradiert")
        server = _server(db, tmp_path, slug="zp-degr", port=25826)
        job = ScheduledJob(server_id=server.id, job_type="command",
                           schedule_expression="0 4 * * *",
                           command_payload='{"command": "op Thormak02"}',
                           created_by_user_id=person.id)
        db.add(job)
        db.commit()

        ok, meldung = send_console_command(db, server, "op Thormak02",
                                           initiated_by_user_id=job.created_by_user_id,
                                           system_fallback=False)

    assert ok is False
    assert "Admin-Rechte" in meldung


# ------------------------------------------- Zugriffsseite als Hintertuer

@pytest.mark.parametrize("daten,pfad", [
    ({"list_key": "ops", "identity": "Thormak02", "op_level": "4"}, "entry-add"),
    ({"list_key": "ops", "identity": "Thormak02"}, "entry-remove"),
    ({"list_key": "banned_players", "identity": "Stoerer"}, "entry-remove"),
    ({"list_key": "banned_ips", "identity": "203.0.113.7"}, "entry-remove"),
])
def test_moderator_kann_ueber_die_zugriffsseite_keine_rechte_vergeben(
        client, tmp_path, daten, pfad):
    """Vorher schrieb die Route ZUERST die Datei und versuchte danach den Live-Befehl -
    die Datei war also schon geaendert, bevor die Sperre ueberhaupt gefragt wurde."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", f"za-{pfad}-{daten['list_key']}")
        server = _server(db, tmp_path,
                         slug=f"za-{pfad}-{daten['list_key']}", port=25830 + len(pfad))
        _recht(db, person, server, can_view=True, can_edit_files=True)
        server_id = server.id
        basis = server.base_path

    antwort = client.post("/login", data={"username": person.username,
                                          "password": "Egal12345!"},
                          follow_redirects=False)
    assert antwort.status_code == 303

    ergebnis = client.post(f"/servers/{server_id}/access/{pfad}", data=daten,
                           follow_redirects=False)
    assert ergebnis.status_code == 303

    from pathlib import Path

    for name in ("ops.json", "banned-players.json", "banned-ips.json"):
        datei = Path(basis) / name
        if datei.exists():
            assert "Thormak02" not in datei.read_text(encoding="utf-8")
            assert "Stoerer" not in datei.read_text(encoding="utf-8")


def test_moderator_kann_die_whitelist_nicht_abschalten(client, tmp_path):
    from pathlib import Path

    from app.db.session import SessionLocal

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "za-wl")
        server = _server(db, tmp_path, slug="za-wl", port=25841)
        _recht(db, person, server, can_view=True, can_edit_files=True)
        server_id, basis = server.id, server.base_path

    client.post("/login", data={"username": "za-wl", "password": "Egal12345!"},
                follow_redirects=False)
    client.post(f"/servers/{server_id}/access/whitelist-toggle", data={},
                follow_redirects=False)

    inhalt = (Path(basis) / "server.properties").read_text(encoding="utf-8")
    assert "white-list=true" in inhalt, "Die Whitelist wurde abgeschaltet"


def test_moderator_kann_das_op_level_nicht_aendern(client, tmp_path):
    """Ein hoeheres Level ist mehr Macht - dieselbe Tat wie 'op'."""
    import json
    from pathlib import Path

    from app.db.session import SessionLocal

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "za-lvl")
        server = _server(db, tmp_path, slug="za-lvl", port=25842)
        _recht(db, person, server, can_view=True, can_edit_files=True)
        server_id, basis = server.id, server.base_path
        (Path(basis) / "ops.json").write_text(json.dumps(
            [{"uuid": "x", "name": "Alt", "level": 1, "bypassesPlayerLimit": False}]),
            encoding="utf-8")

    client.post("/login", data={"username": "za-lvl", "password": "Egal12345!"},
                follow_redirects=False)
    client.post(f"/servers/{server_id}/access/op-level-update",
                data={"identity": "Alt", "op_level": "4"}, follow_redirects=False)

    eintraege = json.loads((Path(basis) / "ops.json").read_text(encoding="utf-8"))
    assert eintraege[0]["level"] == 1, "Das Level wurde erhoeht"


def test_moderator_darf_die_whitelist_weiter_pflegen(client, tmp_path):
    """Nur das Abschalten ist gesperrt - Eintragen bleibt Moderatorenarbeit."""
    import json
    from pathlib import Path

    from app.db.session import SessionLocal

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "za-wladd")
        server = _server(db, tmp_path, slug="za-wladd", port=25843)
        _recht(db, person, server, can_view=True, can_edit_files=True)
        server_id, basis = server.id, server.base_path

    client.post("/login", data={"username": "za-wladd", "password": "Egal12345!"},
                follow_redirects=False)
    client.post(f"/servers/{server_id}/access/entry-add",
                data={"list_key": "whitelist", "identity": "Neuling"},
                follow_redirects=False)

    datei = Path(basis) / "whitelist.json"
    assert datei.exists(), "Der Eintrag wurde nicht geschrieben"
    assert "Neuling" in datei.read_text(encoding="utf-8")


def test_admin_darf_ueber_die_zugriffsseite_op_vergeben(client, tmp_path):
    import json
    from pathlib import Path

    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="za-admin", port=25844)
        server_id, basis = server.id, server.base_path

    client.post("/login", data={"username": "admin", "password": "admin123!"},
                follow_redirects=False)
    client.post(f"/servers/{server_id}/access/entry-add",
                data={"list_key": "ops", "identity": "Thormak02", "op_level": "4"},
                follow_redirects=False)

    eintraege = json.loads((Path(basis) / "ops.json").read_text(encoding="utf-8"))
    assert any(e.get("name") == "Thormak02" for e in eintraege)


def test_abgewiesene_zugangsaenderung_wird_protokolliert(client, tmp_path):
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "za-prot")
        server = _server(db, tmp_path, slug="za-prot", port=25845)
        _recht(db, person, server, can_view=True, can_edit_files=True)
        server_id = server.id

    client.post("/login", data={"username": "za-prot", "password": "Egal12345!"},
                follow_redirects=False)
    client.post(f"/servers/{server_id}/access/entry-add",
                data={"list_key": "ops", "identity": "Thormak02"},
                follow_redirects=False)

    with SessionLocal() as db:
        eintraege = list(db.scalars(select(AuditLog).where(
            AuditLog.action == "server.access_change_denied")))
    assert len(eintraege) == 1


# ------------------------------------------------------- Chat-Gespraeche

def test_neues_gespraech_ueberlebt_das_aufraeumen(client, tmp_path):
    """Bei MAX_CHATS angehefteten Gespraechen loeschte das Aufraeumen genau das gerade
    angelegte - danach endete jede Frage im 500er und war verloren."""
    from app.db.session import SessionLocal
    from app.services import ai_chat_service as acs

    with SessionLocal() as db:
        person = _nutzer(db, "admin", "chat-voll")
        for i in range(acs.MAX_CHATS):
            chat = acs.erstelle(db, user=person, erste_nachricht=f"Server-Frage {i}")
            acs.hefte_an(db, user=person, chat_id=chat.id, an=True)

        neu = acs.erstelle(db, user=person, erste_nachricht="Noch eine Server-Frage")
        geladen = acs.lade(db, user=person, chat_id=neu.id)

    assert geladen["id"] == neu.id, "Das neue Gespraech wurde sofort geloescht"


def test_aufraeumen_greift_bei_nicht_angehefteten(client, tmp_path):
    """Die Grenze muss trotzdem wirken."""
    from app.db.session import SessionLocal
    from app.services import ai_chat_service as acs

    with SessionLocal() as db:
        person = _nutzer(db, "admin", "chat-grenze")
        for i in range(acs.MAX_CHATS + 15):
            acs.erstelle(db, user=person, erste_nachricht=f"Server-Frage {i}")
        chats = acs.liste(db, user=person, grenze=500)

    assert len(chats) <= acs.MAX_CHATS


def test_view_only_schreibt_keine_gespraechszeilen(client, tmp_path):
    """Die Rollenpruefung lag hinter dem Anlegen des Gespraechs - ein Nutzer ohne jedes
    KI-Recht beschrieb damit bei jedem Versuch drei Tabellen."""
    from sqlalchemy import func, select

    from app.db.session import SessionLocal
    from app.models.ai_chat import AiChat, AiChatMessage

    with SessionLocal() as db:
        _nutzer(db, "view_only", "ki-nurlesen")

    client.post("/login", data={"username": "ki-nurlesen", "password": "Egal12345!"},
                follow_redirects=False)

    assert client.post("/api/ki/auftrag",
                       json={"nachricht": "Laufen alle Server?"}).status_code == 403
    assert client.get("/api/ki/chats").status_code == 403
    assert client.get("/api/ki/chats/1").status_code == 403
    assert client.post("/api/ki/chats/1/loeschen", json={}).status_code == 403

    with SessionLocal() as db:
        assert db.scalar(select(func.count(AiChat.id))) == 0
        assert db.scalar(select(func.count(AiChatMessage.id))) == 0


# ------------------------------------------------------- Agenten-Werkzeuge

def test_plugin_auf_fabric_wird_abgewiesen(client, tmp_path):
    """Der Agent meldete "installiert, wirksam beim naechsten Start", obwohl die Datei in
    einem Ordner lag, den ein Fabric-Server nie liest."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "wz-fabric")
        server = _server(db, tmp_path, slug="wz-fabric", port=25851, typ="fabric")

        with pytest.raises(aas.AgentFehler, match="laedt keine"):
            aas.fuehre_aus(db, user=person, werkzeug_name="inhalt_installieren",
                           parameter={"server_id": server.id, "anbieter": "modrinth",
                                      "projekt_id": "x", "version_id": "v1",
                                      "typ": "plugin"})


def test_datapack_bleibt_auf_jedem_server_erlaubt(client, tmp_path):
    """Datapacks sind loaderunabhaengig - die Pruefung darf sie nicht aussperren."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="wz-dp", port=25852, typ="fabric")
        aas._pruefe_inhaltstyp(server, "datapack")      # darf nicht werfen
        aas._pruefe_inhaltstyp(server, "resourcepack")


def test_resourcepack_braucht_dateirechte(client, tmp_path):
    """Ein Resource Pack wird in server.properties gesetzt, nicht als Datei abgelegt -
    dafuer braucht es Dateirechte, nicht nur Steuerrechte."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "wz-rp")
        server = _server(db, tmp_path, slug="wz-rp", port=25853)
        _recht(db, person, server, can_view=True, can_restart=True)   # darf steuern

        with pytest.raises(aas.AgentFehler, match="Dateien"):
            aas.fuehre_aus(db, user=person, werkzeug_name="inhalt_installieren",
                           parameter={"server_id": server.id, "anbieter": "modrinth",
                                      "projekt_id": "x", "version_id": "v1",
                                      "typ": "resourcepack"})


def test_beschreibung_stuerzt_bei_unsinniger_id_nicht_ab(client, tmp_path):
    """Vorher riss eine nicht-numerische ID die ganze Anfrage mit - HTTP 500, obwohl die
    Antwort des Modells schon bezahlt war."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "wz-id")
        server = _server(db, tmp_path, slug="wz-id", port=25854)

        for wert in ("abc", None, "", "1; DROP TABLE", 999999):
            vorschlag = aas._baue_vorschlag(db, person, {
                "name": "inhalt_entfernen", "id": "tu_1",
                "eingabe": {"server_id": server.id, "inhalt_id": wert}})
            assert vorschlag["beschreibung"]


def test_beschreibung_nennt_keine_fremden_inhalte(client, tmp_path):
    """Der Bestaetigungstext nannte Name und Datei eines Inhalts von einem Server, fuer
    den der Nutzer kein Leserecht hat."""
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "wz-fremd")
        a = _server(db, tmp_path, slug="wz-fremd-a", port=25855)
        b = _server(db, tmp_path, slug="wz-fremd-b", port=25856)
        fremd = InstalledContent(server_id=b.id, provider_name="modrinth",
                                 content_type="plugin", external_project_id="p",
                                 external_version_id="v", name="GeheimesPlugin",
                                 file_name="geheim.jar")
        db.add(fremd)
        db.commit()
        db.refresh(fremd)

        vorschlag = aas._baue_vorschlag(db, person, {
            "name": "inhalt_entfernen", "id": "tu_1",
            "eingabe": {"server_id": a.id, "inhalt_id": fremd.id}})

    assert "GeheimesPlugin" not in vorschlag["beschreibung"]
    assert "geheim.jar" not in vorschlag["beschreibung"]


def test_grosse_datei_wird_auch_beim_wachsen_nicht_ganz_gelesen(client, tmp_path):
    """Unterhalb der Groessengrenze wurde ueber read_text_file gelesen, und das holt die
    GANZE Datei - eine Datei, die zwischen stat() und open() waechst, kam damit trotzdem
    vollstaendig in den Speicher."""
    import tracemalloc
    from pathlib import Path

    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "wz-wachs")
        server = _server(db, tmp_path, slug="wz-wachs", port=25857)
        datei = Path(server.base_path) / "wachsend.log"
        # Knapp UNTER der Grenze anlegen ...
        datei.write_text("x" * (aas.MAX_DATEI_BYTES - 1000), encoding="utf-8")
        groesse_vorher = datei.stat().st_size
        # ... und dann deutlich wachsen lassen, wie ein Log, in das geschrieben wird.
        with datei.open("a", encoding="utf-8") as f:
            f.write("y" * (aas.MAX_DATEI_BYTES * 6))

        tracemalloc.start()
        ergebnis = aas._fuehre_lesend_aus(db, person, "datei_lesen",
                                          {"server_id": server.id,
                                           "pfad": "wachsend.log"})
        _aktuell, spitze = tracemalloc.get_traced_memory()
        tracemalloc.stop()

    assert "fehler" not in ergebnis
    assert len(ergebnis["inhalt"]) <= aas.MAX_ERGEBNIS
    assert spitze < groesse_vorher, (
        f"Speicherspitze {spitze / 1024 / 1024:.1f} MB - die Datei wurde ganz gelesen")
