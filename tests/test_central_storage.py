"""Tests fuer central_storage_service (Logs-Fallback, DB-Snapshot) + Settings-Route."""

from __future__ import annotations

import sqlite3

from app.services import central_storage_service as C


def test_probe_now(tmp_path):
    ok, _msg = C.probe_now(str(tmp_path / "nas"))
    assert ok is True
    ok2, _m2 = C.probe_now("")
    assert ok2 is False


def test_logs_dir_local_fallback(monkeypatch, tmp_path):
    local = tmp_path / "local-logs"
    monkeypatch.setattr(C, "_central_root", lambda: "")
    monkeypatch.setattr(C, "_local_logs_dir", lambda: local)
    C._usable_cache.clear()
    d = C.logs_dir()
    assert d == local and d.exists()


def test_logs_dir_uses_central_when_usable(monkeypatch, tmp_path):
    central = tmp_path / "nas"
    monkeypatch.setattr(C, "_central_root", lambda: str(central))
    C._usable_cache.clear()
    d = C.logs_dir()
    assert d == central / "logs" and d.exists()


def test_logs_dir_falls_back_when_central_unusable(monkeypatch, tmp_path):
    local = tmp_path / "local2"
    monkeypatch.setattr(C, "_central_root", lambda: str(tmp_path / "nas2"))
    monkeypatch.setattr(C, "is_usable", lambda p: False)   # NAS "nicht erreichbar"
    monkeypatch.setattr(C, "_local_logs_dir", lambda: local)
    C._usable_cache.clear()
    assert C.logs_dir() == local


def test_snapshot_db_consistent(monkeypatch, tmp_path):
    dbfile = tmp_path / "src.db"
    con = sqlite3.connect(str(dbfile))
    con.execute("create table t(x)")
    con.execute("insert into t values (42)")
    con.commit()
    con.close()

    central = tmp_path / "nas"
    monkeypatch.setattr(C, "_central_root", lambda: str(central))
    monkeypatch.setattr(C, "_db_file", lambda: dbfile)
    C._usable_cache.clear()

    ok, _msg = C.snapshot_db()
    assert ok is True
    snaps = list((central / "db-snapshots").glob("mc_server_manager-*.db"))
    assert len(snaps) == 1
    con = sqlite3.connect(str(snaps[0]))
    assert con.execute("select x from t").fetchone()[0] == 42
    con.close()


def test_snapshot_db_no_central(monkeypatch):
    monkeypatch.setattr(C, "_central_root", lambda: "")
    ok, _msg = C.snapshot_db()
    assert ok is False


def test_central_storage_route(client, tmp_path):
    client.post("/login", data={"username": "admin", "password": "admin123!"})
    nas = tmp_path / "nas"

    resp = client.post("/settings/central-storage",
                       data={"central_storage_root": str(nas), "action": "save"})
    assert resp.status_code == 200

    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        assert A.get_central_storage_root(db) == str(nas)

    # test-Aktion (Schreibprobe) laeuft ohne Fehler
    resp = client.post("/settings/central-storage",
                       data={"central_storage_root": str(nas), "action": "test"})
    assert resp.status_code == 200

    # leeren -> wieder lokal
    resp = client.post("/settings/central-storage", data={"action": "clear"})
    assert resp.status_code == 200
    with dbs.SessionLocal() as db:
        assert A.get_central_storage_root(db) == ""
