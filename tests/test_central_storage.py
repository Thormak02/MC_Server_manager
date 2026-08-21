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


def test_snapshot_db_local_without_nas(monkeypatch, tmp_path):
    """Ohne NAS-Setting wird trotzdem LOKAL nach data/db-snapshots gesnapshottet
    (Option D: der Manager schreibt lokal, ein Sync-Task liefert es auf die NAS)."""
    dbfile = tmp_path / "src.db"
    con = sqlite3.connect(str(dbfile))
    con.execute("create table t(x)")
    con.execute("insert into t values (7)")
    con.commit()
    con.close()

    local = tmp_path / "localdata"

    class _S:
        data_dir = local

    monkeypatch.setattr(C, "_central_root", lambda: "")
    monkeypatch.setattr(C, "_db_file", lambda: dbfile)
    monkeypatch.setattr(C, "get_settings", lambda: _S())

    ok, _msg = C.snapshot_db()
    assert ok is True
    snaps = list((local / "db-snapshots").glob("mc_server_manager-*.db"))
    assert len(snaps) == 1


def test_share_root():
    assert C._share_root(r"\\FriedrichNAS\FriedrichNAS\MC-manager-Logs") == r"\\FriedrichNAS\FriedrichNAS"
    assert C._share_root(r"\\host\share") == r"\\host\share"
    assert C._share_root(r"\\host\share\a\b\c") == r"\\host\share"
    assert C._share_root("C:\\local\\path") is None
    assert C._share_root("") is None
    assert C._share_root(r"\\host") is None  # nur Host, keine Freigabe


def test_central_route_saves_nas_creds(client, tmp_path):
    client.post("/login", data={"username": "admin", "password": "admin123!"})
    nas = tmp_path / "nas"

    import app.db.session as dbs
    from app.services import app_setting_service as A

    resp = client.post("/settings/central-storage", data={
        "central_storage_root": str(nas), "action": "save",
        "nas_user": "Friedrich", "nas_password": "secret1",
    })
    assert resp.status_code == 200
    with dbs.SessionLocal() as db:
        assert A.get_nas_user(db) == "Friedrich"
        assert A.get_nas_password(db) == "secret1"

    # Passwort leer lassen -> beibehalten; Benutzer aendern
    resp = client.post("/settings/central-storage", data={
        "central_storage_root": str(nas), "action": "save",
        "nas_user": "Friedrich2", "nas_password": "",
    })
    assert resp.status_code == 200
    with dbs.SessionLocal() as db:
        assert A.get_nas_user(db) == "Friedrich2"
        assert A.get_nas_password(db) == "secret1"  # unveraendert

    # clear entfernt auch die Anmeldung
    resp = client.post("/settings/central-storage", data={"action": "clear"})
    assert resp.status_code == 200
    with dbs.SessionLocal() as db:
        assert A.get_nas_user(db) == ""
        assert A.get_nas_password(db) == ""


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
