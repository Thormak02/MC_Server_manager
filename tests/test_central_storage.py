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


def test_maybe_snapshot_single_file_change_detection(monkeypatch, tmp_path):
    """EINE Datei (current.db), alte timestamped werden entfernt, nur bei DB-Aenderung neu."""
    import os as _os

    dbfile = tmp_path / "src.db"
    con = sqlite3.connect(str(dbfile))
    con.execute("create table t(x)")
    con.execute("insert into t values (1)")
    con.commit()
    con.close()

    local = tmp_path / "data"

    class _S:
        data_dir = local

    monkeypatch.setattr(C, "_central_root", lambda: "")
    monkeypatch.setattr(C, "_db_file", lambda: dbfile)
    monkeypatch.setattr(C, "get_settings", lambda: _S())
    C._last_snapshot = 0.0
    C._last_db_sig = None

    snapdir = local / "db-snapshots"
    snapdir.mkdir(parents=True, exist_ok=True)
    (snapdir / "mc_server_manager-20260101-000000.db").write_bytes(b"alt")  # alter timestamped

    C.maybe_snapshot_db()
    assert (snapdir / "mc_server_manager-current.db").exists()
    assert list(snapdir.glob("mc_server_manager-2*.db")) == []            # timestamped entfernt
    assert len(list(snapdir.glob("mc_server_manager-*.db"))) == 1          # nur EINE Datei

    # 2. Aufruf ohne DB-Aenderung (Drossel zuruecksetzen) -> Skip: Signatur bleibt gleich.
    sig_before = C._last_db_sig
    C._last_snapshot = 0.0
    C.maybe_snapshot_db()
    assert C._last_db_sig == sig_before

    # DB aendern -> current.db spiegelt die Aenderung, weiterhin nur EINE Datei.
    con = sqlite3.connect(str(dbfile))
    con.execute("insert into t values (2)")
    con.commit()
    con.close()
    C._last_snapshot = 0.0
    C.maybe_snapshot_db()
    cur = sqlite3.connect(str(snapdir / "mc_server_manager-current.db"))
    assert cur.execute("select count(*) from t").fetchone()[0] == 2
    cur.close()
    assert len(list(snapdir.glob("mc_server_manager-*.db"))) == 1
    _os.utime(dbfile, None)  # touch (kein Assert, nur Aufraeumen)


def test_prune_logs_keeps_newest(monkeypatch, tmp_path):
    import os as _os

    local = tmp_path / "data"

    class _S:
        data_dir = local

    monkeypatch.setattr(C, "get_settings", lambda: _S())
    monkeypatch.setattr(C, "_LOG_KEEP_PER_SERVER", 2)
    srv = local / "logs" / "server_2"
    srv.mkdir(parents=True)
    for i in range(5):
        f = srv / f"session-2026010{i}-000000.log"
        f.write_text("x")
        _os.utime(f, (1000 + i, 1000 + i))  # klare mtime-Reihenfolge

    C.prune_logs()
    remaining = sorted(p.name for p in srv.glob("session-*.log"))
    assert remaining == ["session-20260103-000000.log", "session-20260104-000000.log"]


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
