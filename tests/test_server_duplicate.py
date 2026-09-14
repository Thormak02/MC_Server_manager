"""Tests fuer das 1:1-Duplizieren eines Servers (server_service.duplicate_server).

Die (langsame) Datei-Kopie laeuft in Produktion im Hintergrund; hier wird der synchrone
Pfad (run_async=False) getestet, damit das Ergebnis deterministisch pruefbar ist."""
from pathlib import Path

import pytest
from sqlalchemy import select


def _make_source(db, base: Path):
    from app.models.server import Server

    base.mkdir(parents=True, exist_ok=True)
    (base / "server.properties").write_text(
        "level-name=world\nserver-port=25599\nmotd=Hallo\n", encoding="utf-8"
    )
    (base / "mods").mkdir()
    (base / "mods" / "some-mod.jar").write_bytes(b"jar")
    (base / "world").mkdir()
    (base / "world" / "level.dat").write_bytes(b"leveldat")

    srv = Server(
        name="David Survival", slug="david-survival", server_type="paper",
        mc_version="1.21.11", base_path=str(base), port=25599, status="stopped",
        start_mode="bat", start_bat_path=str(base / "start.bat"),
        sleep_enabled=False, gateway_enabled=True, gateway_hostname="david",
        gateway_is_default=True, auto_start_with_manager=True,
    )
    db.add(srv)
    db.commit()
    db.refresh(srv)
    return srv


@pytest.fixture()
def _det_ports(monkeypatch):
    ports = iter([25701, 25702, 25703, 25704])
    monkeypatch.setattr("app.services.port_service.allocate_server_port",
                        lambda db, **kw: next(ports))
    monkeypatch.setattr("app.services.process_service.is_running", lambda sid: False)


def test_duplicate_copies_everything_and_changes_port(client, monkeypatch, tmp_path, _det_ports):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.models.scheduled_job import ScheduledJob
    from app.models.server_permission import ServerPermission
    from app.models.server_modpack_state import ServerModpackState
    from app.models.user import User
    from app.services import server_service

    with SessionLocal() as db:
        src = _make_source(db, tmp_path / "source")
        user_id = db.scalar(select(User.id))

        db.add(InstalledContent(
            server_id=src.id, provider_name="modrinth", content_type="plugin",
            external_project_id="gsit", external_version_id="v1", name="GSit",
            version_label="3.1.1", file_name="GSit.jar",
        ))
        db.add(ServerPermission(server_id=src.id, user_id=user_id, can_view=True, can_manage=True))
        db.add(ScheduledJob(
            server_id=src.id, job_type="backup", schedule_expression="0 3 * * *",
            command_payload=None, is_enabled=True,
        ))
        db.add(ServerModpackState(server_id=src.id, source="modrinth", pack_name="Cool Pack"))
        db.commit()

        clone = server_service.duplicate_server(db, src, user_id, run_async=False)

        # Basisfelder: neuer Name/Slug/Port, gestoppt, Netzwerk aus, kein Auto-Start
        assert clone.name == "David Survival (Kopie)"
        assert clone.slug != src.slug
        assert clone.port == 25701 and clone.port != src.port
        assert clone.status == "stopped"
        assert clone.gateway_enabled is False
        assert clone.gateway_hostname is None
        assert clone.gateway_is_default is False
        assert clone.auto_start_with_manager is False
        assert clone.server_type == "paper" and clone.mc_version == "1.21.11"

        # start_bat_path auf den Klon-Ordner umgebogen
        assert clone.start_bat_path.startswith(clone.base_path)

        # Dateien 1:1 kopiert
        cbase = Path(clone.base_path)
        assert (cbase / "mods" / "some-mod.jar").read_bytes() == b"jar"
        assert (cbase / "world" / "level.dat").read_bytes() == b"leveldat"
        # server.properties auf den neuen Port angeglichen
        props = (cbase / "server.properties").read_text(encoding="utf-8")
        assert "server-port=25701" in props
        assert "level-name=world" in props

        # Relationen kopiert (neue server_id)
        ic = db.scalars(select(InstalledContent).where(InstalledContent.server_id == clone.id)).all()
        assert len(ic) == 1 and ic[0].name == "GSit"
        perms = db.scalars(select(ServerPermission).where(ServerPermission.server_id == clone.id)).all()
        assert len(perms) == 1 and perms[0].can_manage is True
        state = db.scalar(select(ServerModpackState).where(ServerModpackState.server_id == clone.id))
        assert state is not None and state.pack_name == "Cool Pack"

        # Geplante Tasks: kopiert, aber PAUSIERT + frischer Zeitplan
        jobs = db.scalars(select(ScheduledJob).where(ScheduledJob.server_id == clone.id)).all()
        assert len(jobs) == 1
        assert jobs[0].is_enabled is False
        assert jobs[0].next_run_at is None and jobs[0].last_run_at is None
        assert jobs[0].schedule_expression == "0 3 * * *"


def test_duplicate_allocates_sleep_internal_port(client, monkeypatch, tmp_path, _det_ports):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import server_service

    with SessionLocal() as db:
        src = _make_source(db, tmp_path / "source")
        src.sleep_enabled = True
        db.commit()

        clone = server_service.duplicate_server(db, src, None, run_async=False)
        assert clone.sleep_enabled is True
        assert clone.sleep_internal_port == 25702        # zweiter vergebener Port
        assert clone.sleep_internal_port != clone.port


def test_duplicate_blocked_when_source_running(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.services import server_service

    monkeypatch.setattr("app.services.process_service.is_running", lambda sid: True)

    with SessionLocal() as db:
        src = _make_source(db, tmp_path / "source")
        with pytest.raises(ValueError, match="stoppen"):
            server_service.duplicate_server(db, src, None, run_async=False)


def test_duplicate_rejects_nested_target(client, monkeypatch, tmp_path, _det_ports):
    """Wenn der Quellordner ein Vorfahre des Zielordners ist (copytree wuerde sich endlos selbst
    kopieren), muss abgebrochen werden."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import app_setting_service, server_service

    with SessionLocal() as db:
        storage_root = app_setting_service.ensure_server_storage_initialized(db)
        # Quelle = Storage-Root selbst -> Ziel (storage_root/<slug>) liegt darin.
        src = Server(name="Nested", slug="nested", server_type="paper", mc_version="1.21.1",
                     base_path=str(storage_root), port=25599, status="stopped")
        db.add(src)
        db.commit()
        with pytest.raises(ValueError, match="Quellordner|Zielordner"):
            server_service.duplicate_server(db, src, None, run_async=False)


def test_duplicate_rejected_while_source_provisioning(client, monkeypatch, tmp_path, _det_ports):
    from app.db.session import SessionLocal
    from app.services import server_service

    with SessionLocal() as db:
        src = _make_source(db, tmp_path / "source")
        src.status = "provisioning"
        db.commit()
        with pytest.raises(ValueError, match="erstellt/kopiert"):
            server_service.duplicate_server(db, src, None, run_async=False)


def test_normalize_runtime_states_provisioning_becomes_error(client):
    from app.db.session import SessionLocal
    from app.db import init_db
    from app.models.server import Server

    with SessionLocal() as db:
        prov = Server(name="Prov", slug="prov", server_type="paper", mc_version="1.21.1",
                      base_path="x", port=25599, status="provisioning")
        run = Server(name="Run", slug="run", server_type="paper", mc_version="1.21.1",
                     base_path="y", port=25600, status="running")
        db.add_all([prov, run])
        db.commit()
        prov_id, run_id = prov.id, run.id

    init_db._normalize_runtime_states()

    with SessionLocal() as db:
        # Unterbrochenes Duplizieren -> 'error' (nicht faelschlich startbereit)
        assert db.get(Server, prov_id).status == "error"
        # Normaler Laufzustand -> 'stopped'
        assert db.get(Server, run_id).status == "stopped"


def test_start_server_refused_while_duplication_source(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.services import process_service, server_service

    monkeypatch.setattr("app.services.process_service.is_running", lambda sid: False)

    with SessionLocal() as db:
        src = _make_source(db, tmp_path / "source")
        server_service._mark_duplication_source(src.id)
        try:
            ok, msg = process_service.start_server(db, src, None)
        finally:
            server_service._unmark_duplication_source(src.id)
        assert ok is False
        assert "dupliziert" in msg.lower()


def test_duplicate_does_not_copy_backups(client, monkeypatch, tmp_path, _det_ports):
    from app.db.session import SessionLocal
    from app.models.backup import Backup
    from app.services import server_service
    from sqlalchemy import select as _select

    with SessionLocal() as db:
        src = _make_source(db, tmp_path / "source")
        db.add(Backup(server_id=src.id, backup_name="b1", backup_type="manual",
                      storage_path=str(tmp_path / "b1.zip"), status="success"))
        db.commit()

        clone = server_service.duplicate_server(db, src, None, run_async=False)
        clone_backups = db.scalars(_select(Backup).where(Backup.server_id == clone.id)).all()
        assert clone_backups == []                       # Backups gehoeren zum Original
