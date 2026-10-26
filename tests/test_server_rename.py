"""Tests fuer das Umbenennen eines Servers (server_service.rename_server).

Kern der Sache: der ANZEIGENAME wandert ueberall mit, die daraus einmal abgeleiteten
DAUERHAFTEN Bezeichner bleiben stehen. Genau das pruefen die Tests hier nach, denn ein
mitwandernder Slug waere der Fehler, der bestehende Backups und das Hub-Replay
unauffindbar macht - und der faellt in der Oberflaeche nicht auf.
"""
from pathlib import Path

import pytest
from sqlalchemy import select


def _login_admin(client):
    antwort = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303


def _make_server(db, base: Path, *, name="David Survival", slug="david-survival"):
    from app.models.server import Server

    base.mkdir(parents=True, exist_ok=True)
    (base / "server.properties").write_text(
        "level-name=world\nserver-port=25599\nmotd=Hallo\n", encoding="utf-8"
    )

    srv = Server(
        name=name, slug=slug, server_type="paper", mc_version="1.21.11",
        base_path=str(base), port=25599, status="stopped",
        start_mode="bat", start_bat_path=str(base / "start.bat"),
        sleep_enabled=False, gateway_enabled=True, gateway_hostname="david",
        gateway_is_default=False,
    )
    db.add(srv)
    db.commit()
    db.refresh(srv)
    return srv


@pytest.fixture()
def _no_lobby_sync(monkeypatch):
    """Plugin-Sync abklemmen - der schreibt sonst in echte Serverordner.

    Die Aufrufe werden mitgezaehlt, damit ein Test belegen kann, dass das Umbenennen
    die Lobby-Menues ueberhaupt anfasst.
    """
    calls = []
    monkeypatch.setattr(
        "app.services.lobby_service.sync_lobby_plugin",
        lambda db: (calls.append(1), (True, "2 Server im Menue"))[1],
    )
    return calls


def test_rename_changes_name_but_keeps_derived_identifiers(tmp_path, client, _no_lobby_sync):
    """Der eigentliche Vertrag: Name neu, Slug/Ordner/Alias unveraendert."""
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        alter_slug, alter_pfad, alter_alias = srv.slug, srv.base_path, srv.gateway_hostname
        alte_bat = srv.start_bat_path

        changed, message = server_service.rename_server(db, srv, "Davids Welt")

        assert changed is True
        assert "Davids Welt" in message
        assert srv.name == "Davids Welt"
        # Das Entscheidende: nichts Abgeleitetes ist mitgewandert.
        assert srv.slug == alter_slug
        assert srv.base_path == alter_pfad
        assert srv.gateway_hostname == alter_alias
        assert srv.start_bat_path == alte_bat


def test_rename_keeps_backup_folder_and_replay_path_findable(tmp_path, client, _no_lobby_sync):
    """Backup-Ordner und Hub-Replay haengen am Slug - beide muessen nach dem
    Umbenennen noch auf denselben Ort zeigen. Sonst sind bestehende Sicherungen und
    das Modpack-Profil des Hubs stillschweigend verwaist."""
    from app.db.session import SessionLocal
    from app.services import backup_service, hub_replay_service, server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        ordner_vorher = backup_service._backup_folder_for_server(db, srv)
        replay_vorher = hub_replay_service.replay_path_for(srv.slug)

        server_service.rename_server(db, srv, "Komplett anderer Name")

        assert backup_service._backup_folder_for_server(db, srv) == ordner_vorher
        assert hub_replay_service.replay_path_for(srv.slug) == replay_vorher


def test_rename_updates_lobby_menu_label(tmp_path, client, _no_lobby_sync):
    """Der Name steht im Menue-Eintrag JEDES ANDEREN Gateway-Servers. Nach dem
    Umbenennen muss dort der neue Name stehen - und der Transfer-Schluessel (Alias)
    unveraendert, sonst zeigen /server <name> und Lesezeichen ins Leere."""
    from app.db.session import SessionLocal
    from app.services import app_setting_service, lobby_service, server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        app_setting_service.set_network_domain(db, "example.test")

        vorher = lobby_service.get_menu_servers(db)
        eintrag_vorher = next(e for e in vorher if e["id"] == srv.id)
        assert "David Survival" in eintrag_vorher["display"]

        server_service.rename_server(db, srv, "Davids Welt")

        nachher = lobby_service.get_menu_servers(db)
        eintrag = next(e for e in nachher if e["id"] == srv.id)
        assert "Davids Welt" in eintrag["display"]
        assert "David Survival" not in eintrag["display"]
        # Transfer-Ziel unberuehrt.
        assert eintrag["key"] == eintrag_vorher["key"]
        assert eintrag["host"] == eintrag_vorher["host"]
        assert eintrag["port"] == eintrag_vorher["port"]

    # Und die Bukkit-Lobbys bekommen ihre config.yml neu geschrieben.
    assert _no_lobby_sync, "Umbenennen muss den Lobby-Plugin-Sync anstossen"


def test_rename_rejects_duplicate_name_case_insensitive(tmp_path, client, _no_lobby_sync):
    """SQLite wuerde 'david' neben 'David' erlauben - im Lobby-Menue und im
    Loesch-Dialog waere das aber nicht unterscheidbar."""
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        _make_server(db, tmp_path / "a", name="Erster", slug="erster")
        zweiter = _make_server(db, tmp_path / "b", name="Zweiter", slug="zweiter")

        with pytest.raises(ValueError, match="bereits einen Server"):
            server_service.rename_server(db, zweiter, "Erster")
        with pytest.raises(ValueError, match="bereits einen Server"):
            server_service.rename_server(db, zweiter, "erster")

        db.refresh(zweiter)
        assert zweiter.name == "Zweiter"


def test_rename_rejects_empty_and_overlong(tmp_path, client, _no_lobby_sync):
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")

        for leer in ("", "   ", "\t\n"):
            with pytest.raises(ValueError, match="nicht leer"):
                server_service.rename_server(db, srv, leer)

        # 128 ist die Spaltenbreite - genau 128 muss durchgehen, 129 nicht.
        with pytest.raises(ValueError, match="Zeichen lang"):
            server_service.rename_server(db, srv, "x" * 129)
        changed, _ = server_service.rename_server(db, srv, "y" * 128)
        assert changed is True
        assert srv.name == "y" * 128


def test_rename_trims_and_detects_no_change(tmp_path, client, _no_lobby_sync):
    """Fuehrende/folgende Leerzeichen werden abgeschnitten, und ein unveraenderter
    Name loest weder Audit-Eintrag noch Plugin-Sync aus (sonst rauscht jedes
    Speichern der Einstellungen durch alle Lobbys)."""
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")

        changed, message = server_service.rename_server(db, srv, "  David Survival  ")
        assert changed is False
        assert "unveraendert" in message
        assert not _no_lobby_sync, "unveraenderter Name darf keinen Lobby-Sync ausloesen"

        changed, _ = server_service.rename_server(db, srv, "  Davids Welt  ")
        assert changed is True
        assert srv.name == "Davids Welt"


def test_rename_writes_audit_entry_with_both_names(tmp_path, client, _no_lobby_sync):
    """Die Historie muss beide Namen festhalten - sonst ist nach dem Umbenennen
    nicht mehr nachvollziehbar, welcher Server gemeint war."""
    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog
    from app.services import server_service

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        server_service.rename_server(db, srv, "Davids Welt")

        eintrag = db.scalar(
            select(AuditLog)
            .where(AuditLog.action == "server.rename", AuditLog.server_id == srv.id)
        )
        assert eintrag is not None
        assert "David Survival" in (eintrag.details or "")
        assert "Davids Welt" in (eintrag.details or "")


def test_rename_survives_broken_lobby_sync(tmp_path, client, monkeypatch):
    """Ein kaputter Plugin-Sync (Server-Ordner weg, Datei gesperrt) darf das
    Umbenennen nicht zurueckdrehen - der Name ist dann schon committet. Der Nutzer
    muss aber erfahren, dass die Lobby den alten Namen noch zeigt."""
    from app.db.session import SessionLocal
    from app.services import server_service

    def kaputt(db):
        raise OSError("config.yml gesperrt")

    monkeypatch.setattr("app.services.lobby_service.sync_lobby_plugin", kaputt)

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        changed, message = server_service.rename_server(db, srv, "Davids Welt")

        assert changed is True
        assert srv.name == "Davids Welt"
        assert "Hinweis" in message and "gesperrt" in message


def test_rename_via_settings_route(tmp_path, client, _no_lobby_sync):
    """Durch die echte Route - inklusive Weiterleitung und Erfolgsmeldung."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        sid = srv.id

    _login_admin(client)
    antwort = client.post(
        f"/servers/{sid}/settings",
        data={"name": "Davids Welt", "mc_version": "1.21.11"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303

    with SessionLocal() as db:
        from app.models.server import Server

        assert db.get(Server, sid).name == "Davids Welt"


def test_settings_route_without_name_field_keeps_name(tmp_path, client, _no_lobby_sync):
    """Ein Aufruf ohne ``name``-Feld darf den Namen nicht leeren - sonst wuerde
    jedes aeltere Formular und jeder externe Aufruf den Server namenlos machen."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        sid = srv.id

    _login_admin(client)
    antwort = client.post(
        f"/servers/{sid}/settings",
        data={"mc_version": "1.21.11"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303

    with SessionLocal() as db:
        assert db.get(Server, sid).name == "David Survival"


def test_settings_route_rejects_empty_name_without_saving_rest(tmp_path, client, _no_lobby_sync):
    """Ein leeres Namensfeld bricht das Speichern ab - es soll nicht die Haelfte
    der Einstellungen uebernehmen und den Namen unkommentiert stehen lassen."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        srv = _make_server(db, tmp_path / "srv")
        sid, alter_ram = srv.id, srv.memory_max_mb

    _login_admin(client)
    antwort = client.post(
        f"/servers/{sid}/settings",
        data={"name": "   ", "mc_version": "1.21.11", "memory_max_mb": "8192"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303

    with SessionLocal() as db:
        nachher = db.get(Server, sid)
        assert nachher.name == "David Survival"
        assert nachher.memory_max_mb == alter_ram
