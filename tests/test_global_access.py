"""Tests fuer die managerweiten Zugriffslisten (Whitelist, OPs, Banns).

Zwei bewusste Entscheidungen stehen hier im Mittelpunkt:
1. Durchgesetzt wird bei JEDEM Serverstart - eine lokale Entfernung haelt nicht.
2. Der fest verdrahtete Operator laesst sich nicht entfernen (Notnagel gegen
   Selbstaussperrung).
"""
import json
from pathlib import Path

import pytest


def _login_admin(client):
    antwort = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303


def _make_server(db, base: Path, *, name="Testserver", slug="testserver"):
    from app.models.server import Server

    base.mkdir(parents=True, exist_ok=True)
    (base / "server.properties").write_text("max-players=20\n", encoding="utf-8")
    srv = Server(name=name, slug=slug, server_type="paper", mc_version="1.21.11",
                 base_path=str(base), port=25599, status="stopped", start_mode="bat")
    db.add(srv)
    db.commit()
    db.refresh(srv)
    return srv


def _json_liste(base: Path, datei: str):
    pfad = base / datei
    if not pfad.is_file():
        return []
    return json.loads(pfad.read_text(encoding="utf-8"))


@pytest.fixture()
def _kein_mojang(monkeypatch):
    """Keine echten Namensabfragen im Test - die Offline-UUID reicht hier."""
    monkeypatch.setattr("app.services.file_service._lookup_mojang_uuid", lambda name: None)


def test_hardcoded_op_is_always_present(client):
    """Der fest verdrahtete Operator steht ohne Zutun in der Liste."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        ops = global_access_service.liste(db, "ops")

    fest = [e for e in ops if e["fest"]]
    assert len(fest) == 1
    assert fest[0]["identity"] == "Thormak2002"
    assert fest[0]["player_uuid"] == "69f230e1-af2c-4d0e-9885-93d66595a855"
    assert fest[0]["op_level"] == 4


def test_hardcoded_op_cannot_be_removed(client):
    """Entfernen muss scheitern - sonst koennte man sich selbst aussperren."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        with pytest.raises(ValueError, match="fest als Operator"):
            global_access_service.entfernen(db, "ops", "Thormak2002")
        # Gross-/Kleinschreibung darf die Sperre nicht umgehen.
        with pytest.raises(ValueError, match="fest als Operator"):
            global_access_service.entfernen(db, "ops", "thormak2002")

        assert any(e["fest"] for e in global_access_service.liste(db, "ops"))


def test_hardcoded_op_is_not_listed_twice(client, _kein_mojang):
    """Traegt jemand denselben Namen zusaetzlich ein, darf er nicht doppelt erscheinen."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        global_access_service.hinzufuegen(db, "ops", "Thormak2002", op_level=2)
        ops = global_access_service.liste(db, "ops")

    treffer = [e for e in ops if e["identity"].lower() == "thormak2002"]
    assert len(treffer) == 1
    assert treffer[0]["fest"] is True
    assert treffer[0]["op_level"] == 4, "der fest verdrahtete Level gewinnt"


def test_apply_writes_all_lists_to_a_server(client, tmp_path, _kein_mojang):
    from app.db.session import SessionLocal
    from app.services import global_access_service

    basis = tmp_path / "srv"
    with SessionLocal() as db:
        srv = _make_server(db, basis)
        global_access_service.hinzufuegen(db, "whitelist", "Freund")
        global_access_service.hinzufuegen(db, "banned_players", "Stoerer", reason="Griefing")
        global_access_service.hinzufuegen(db, "banned_ips", "203.0.113.7", reason="Bot")

        warnungen = global_access_service.auf_server_anwenden(db, srv)

    assert warnungen == []

    ops = _json_liste(basis, "ops.json")
    assert [e["name"] for e in ops] == ["Thormak2002"]
    assert ops[0]["uuid"] == "69f230e1-af2c-4d0e-9885-93d66595a855"
    assert ops[0]["level"] == 4

    assert [e["name"] for e in _json_liste(basis, "whitelist.json")] == ["Freund"]

    banns = _json_liste(basis, "banned-players.json")
    assert banns[0]["name"] == "Stoerer"
    assert banns[0]["reason"] == "Griefing"

    ips = _json_liste(basis, "banned-ips.json")
    assert ips[0]["ip"] == "203.0.113.7"
    assert ips[0]["reason"] == "Bot"


def test_apply_keeps_local_entries(client, tmp_path, _kein_mojang):
    """Durchsetzen heisst ERGAENZEN, nicht ersetzen: lokal eingetragene Spieler
    duerfen nicht verschwinden."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    basis = tmp_path / "srv"
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "ops.json").write_text(json.dumps([
        {"uuid": "11111111-2222-4333-8444-555555555555", "name": "LokalerOp",
         "level": 2, "bypassesPlayerLimit": False}
    ]), encoding="utf-8")

    with SessionLocal() as db:
        srv = _make_server(db, basis)
        global_access_service.auf_server_anwenden(db, srv)

    namen = {e["name"] for e in _json_liste(basis, "ops.json")}
    assert namen == {"LokalerOp", "Thormak2002"}


def test_apply_overwrites_a_downgraded_entry(client, tmp_path, _kein_mojang):
    """Der Kern der Entscheidung "bei jedem Start durchsetzen": wer lokal
    herabgestuft oder entfernt wurde, bekommt seinen managerweiten Stand zurueck."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    basis = tmp_path / "srv"
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "ops.json").write_text(json.dumps([
        {"uuid": "69f230e1-af2c-4d0e-9885-93d66595a855", "name": "Thormak2002",
         "level": 1, "bypassesPlayerLimit": False}
    ]), encoding="utf-8")

    with SessionLocal() as db:
        srv = _make_server(db, basis)
        global_access_service.auf_server_anwenden(db, srv)

    ops = _json_liste(basis, "ops.json")
    assert len(ops) == 1
    assert ops[0]["level"] == 4, "managerweit schlaegt lokal"


def test_apply_does_not_ask_mojang(client, tmp_path, monkeypatch, _kein_mojang):
    """Die UUID wird EINMAL beim Anlegen aufgeloest. Taete das jeder Serverstart
    erneut, haette man pro Eintrag und Server eine Netzanfrage im Startpfad."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        global_access_service.hinzufuegen(db, "whitelist", "Freund")

        def verboten(name):
            raise AssertionError("Serverstart darf nicht bei Mojang anfragen")

        monkeypatch.setattr("app.services.file_service._lookup_mojang_uuid", verboten)
        assert global_access_service.auf_server_anwenden(db, srv) == []


def test_running_servers_are_skipped_on_bulk_apply(client, tmp_path, monkeypatch, _kein_mojang):
    """Minecraft schreibt diese Dateien selbst. In einen laufenden Server zu
    schreiben waere nur scheinbar wirksam - also auslassen und sagen, warum."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    basis = tmp_path / "srv"
    with SessionLocal() as db:
        srv = _make_server(db, basis)
        sid = srv.id

    monkeypatch.setattr("app.services.process_service.is_running", lambda s: s == sid)

    with SessionLocal() as db:
        erledigt, warnungen = global_access_service.auf_alle_server_anwenden(db)

    assert erledigt == 0
    assert any("laeuft" in w for w in warnungen)
    assert _json_liste(basis, "ops.json") == []


def test_settings_page_shows_the_lists(client, _kein_mojang):
    _login_admin(client)
    seite = client.get("/settings")
    assert seite.status_code == 200
    assert 'data-settings-panel="zugriff"' in seite.text
    assert "Managerweite Zugriffslisten" in seite.text
    # Der fest verdrahtete Operator erscheint mit Kopf und ohne Entfernen-Knopf.
    assert "Thormak2002" in seite.text
    assert "/api/avatar/69f230e1af2c4d0e988593d66595a855" in seite.text
    assert "nicht entfernbar" in seite.text


def test_routes_add_and_remove(client, _kein_mojang):
    from app.db.session import SessionLocal
    from app.services import global_access_service

    _login_admin(client)

    antwort = client.post("/settings/global-access/add", follow_redirects=False,
                          data={"list_key": "whitelist", "identity": "Freund"})
    assert antwort.status_code == 303
    with SessionLocal() as db:
        assert [e["identity"] for e in global_access_service.liste(db, "whitelist")] == ["Freund"]

    antwort = client.post("/settings/global-access/remove", follow_redirects=False,
                          data={"list_key": "whitelist", "identity": "Freund"})
    assert antwort.status_code == 303
    with SessionLocal() as db:
        assert global_access_service.liste(db, "whitelist") == []


def test_route_refuses_to_remove_the_hardcoded_op(client, _kein_mojang):
    from app.db.session import SessionLocal
    from app.services import global_access_service

    _login_admin(client)
    antwort = client.post("/settings/global-access/remove", follow_redirects=False,
                          data={"list_key": "ops", "identity": "Thormak2002"})
    assert antwort.status_code == 303

    with SessionLocal() as db:
        assert any(e["fest"] for e in global_access_service.liste(db, "ops"))


def test_global_access_routes_are_behind_login(client):
    for pfad, daten in (
        ("/settings/global-access/add", {"list_key": "ops", "identity": "X"}),
        ("/settings/global-access/remove", {"list_key": "ops", "identity": "X"}),
        ("/settings/global-access/apply", {}),
    ):
        antwort = client.post(pfad, data=daten, follow_redirects=False)
        assert antwort.status_code == 303
        assert "/login" in antwort.headers.get("location", "")


def test_ip_bans_get_no_head(client, _kein_mojang):
    """Eine IP ist kein Spieler - auch in der managerweiten Liste kein Kopf."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        global_access_service.hinzufuegen(db, "banned_ips", "203.0.113.7", reason="Bot")

    _login_admin(client)
    seite = client.get("/settings")
    assert "203.0.113.7" in seite.text
    assert "/api/avatar/203" not in seite.text


# --- Schutz gegen /deop -----------------------------------------------------

@pytest.fixture(autouse=True)
def _zaehler_zuruecksetzen():
    """Die Versuchszaehlung ist prozessweit - sonst faerbt ein Test auf den naechsten ab."""
    from app.services import global_access_service

    global_access_service.versuche_zuruecksetzen()
    yield
    global_access_service.versuche_zuruecksetzen()


@pytest.mark.parametrize("zeile", [
    "[12:00:00] [Server thread/INFO]: Made Thormak2002 no longer a server operator",
    "[12:00:00] [Server thread/INFO]: De-opped Thormak2002",
    "[12:00:00] [Server thread/INFO]: Deopped thormak2002",
])
def test_erkenne_deop_findet_die_meldung(zeile):
    from app.services import global_access_service

    assert global_access_service.erkenne_deop(zeile) == "Thormak2002"


@pytest.mark.parametrize("zeile", [
    "[12:00:00] [Server thread/INFO]: Made Fremder no longer a server operator",
    "[12:00:00] [Server thread/INFO]: Thormak2002 joined the game",
    "[12:00:00] [Server thread/INFO]: Made Thormak2002 a server operator",
    # Chat-Echo: jemand TIPPT den Meldungstext. Ein Re-Op waere harmlos, wuerde aber
    # die Versuchszaehlung verbrauchen - also gar nicht erst ausloesen.
    "[12:00:00] [Server thread/INFO]: <Angreifer> Made Thormak2002 no longer a server operator",
    "",
])
def test_erkenne_deop_ignoriert_den_rest(zeile):
    from app.services import global_access_service

    assert global_access_service.erkenne_deop(zeile) is None


def test_reop_sends_the_console_command(client, tmp_path, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        sid = _make_server(db, tmp_path / "srv").id

    befehle = []
    monkeypatch.setattr("app.services.process_service.is_running", lambda s: True)
    monkeypatch.setattr(
        "app.services.process_service.send_console_command",
        lambda db, server, command, user: (befehle.append(command), (True, "ok"))[1],
    )

    assert global_access_service.erzwinge_op(sid, "Thormak2002") is True
    assert befehle == ["op Thormak2002"]


def test_reop_is_rate_limited(client, tmp_path, monkeypatch):
    """Gegen ein Duell mit einem gegenlaeufigen Rechte-Plugin: nach fuenf Versuchen
    in einer Minute wird abgebrochen und gewarnt, statt endlos weiterzumachen."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        sid = _make_server(db, tmp_path / "srv").id

    befehle = []
    monkeypatch.setattr("app.services.process_service.is_running", lambda s: True)
    monkeypatch.setattr(
        "app.services.process_service.send_console_command",
        lambda db, server, command, user: (befehle.append(command), (True, "ok"))[1],
    )

    ergebnisse = [global_access_service.erzwinge_op(sid, "Thormak2002") for _ in range(8)]
    assert ergebnisse[:5] == [True] * 5
    assert ergebnisse[5:] == [False] * 3
    assert len(befehle) == 5


def test_reop_does_nothing_when_server_is_stopped(client, tmp_path, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import global_access_service

    with SessionLocal() as db:
        sid = _make_server(db, tmp_path / "srv").id

    monkeypatch.setattr("app.services.process_service.is_running", lambda s: False)

    def verboten(*args, **kwargs):
        raise AssertionError("gestoppter Server darf keinen Befehl bekommen")

    monkeypatch.setattr("app.services.process_service.send_console_command", verboten)
    assert global_access_service.erzwinge_op(sid, "Thormak2002") is False


def test_periodic_check_restores_a_silently_removed_op(client, tmp_path, monkeypatch):
    """Ein Rechte-Plugin kann deopen, OHNE die uebliche Konsolenmeldung auszugeben -
    davon saehe der Zeilenleser nichts. Dafuer gibt es die Nachkontrolle."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    basis = tmp_path / "srv"
    with SessionLocal() as db:
        sid = _make_server(db, basis).id
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "ops.json").write_text("[]", encoding="utf-8")   # lautlos entfernt

    befehle = []
    monkeypatch.setattr("app.services.process_service.is_running", lambda s: True)
    monkeypatch.setattr(
        "app.services.process_service.send_console_command",
        lambda db, server, command, user: (befehle.append(command), (True, "ok"))[1],
    )

    assert global_access_service.pruefe_server(sid) == ["Thormak2002"]
    assert befehle == ["op Thormak2002"]


def test_periodic_check_is_quiet_when_everything_is_fine(client, tmp_path, monkeypatch):
    """Keine Befehlsflut, solange alles stimmt - die Wache laeuft im Minutentakt."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    basis = tmp_path / "srv"
    with SessionLocal() as db:
        sid = _make_server(db, basis).id
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "ops.json").write_text(json.dumps([
        {"uuid": "69f230e1-af2c-4d0e-9885-93d66595a855", "name": "Thormak2002",
         "level": 4, "bypassesPlayerLimit": True}
    ]), encoding="utf-8")

    monkeypatch.setattr("app.services.process_service.is_running", lambda s: True)

    def verboten(*args, **kwargs):
        raise AssertionError("ohne Not darf kein Befehl geschickt werden")

    monkeypatch.setattr("app.services.process_service.send_console_command", verboten)
    assert global_access_service.pruefe_server(sid) == []


def test_periodic_check_also_fixes_a_downgraded_level(client, tmp_path, monkeypatch):
    """Level 1 statt 4 ist genauso eine Entmachtung wie ein vollstaendiges Deop."""
    from app.db.session import SessionLocal
    from app.services import global_access_service

    basis = tmp_path / "srv"
    with SessionLocal() as db:
        sid = _make_server(db, basis).id
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "ops.json").write_text(json.dumps([
        {"uuid": "69f230e1-af2c-4d0e-9885-93d66595a855", "name": "Thormak2002",
         "level": 1, "bypassesPlayerLimit": False}
    ]), encoding="utf-8")

    befehle = []
    monkeypatch.setattr("app.services.process_service.is_running", lambda s: True)
    monkeypatch.setattr(
        "app.services.process_service.send_console_command",
        lambda db, server, command, user: (befehle.append(command), (True, "ok"))[1],
    )

    assert global_access_service.pruefe_server(sid) == ["Thormak2002"]
    assert befehle == ["op Thormak2002"]


def test_console_reader_is_wired_to_the_protection():
    """Die Erkennung muss wirklich an der Konsolenauswertung haengen - sonst
    greift sie im Betrieb nie, ohne dass ein Test es merkt."""
    from pathlib import Path

    quelle = Path("app/services/process_service.py").read_text(encoding="utf-8")
    assert "_schuetze_feste_ops(server_id, line)" in quelle
    assert "global_access_service.erkenne_deop(line)" in quelle
    # In einem eigenen Thread, sonst haelt das Zurueck-Oppen den Konsolenleser an.
    assert 'name=f"mcsm-reop-{server_id}"' in quelle
