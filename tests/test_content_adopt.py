"""Tests fuer die Zuordnung manuell hinzugefuegter Dateien (provider='local') zu einem
Upstream-Projekt: Modrinth-Hash-Lookup, CurseForge-Fingerprint (murmur2), Metadaten-Auslesen,
Idempotenz und der Datapack-Ordner-Scan."""
import io
import zipfile

import pytest


# --------------------------------------------------------------------------- #
# murmur2 (CurseForge-Fingerprint-Basis) gegen eine unabhaengige Referenz
# --------------------------------------------------------------------------- #
def _ref_murmur2(data: bytes, seed: int = 1) -> int:
    m = 0x5BD1E995
    r = 24
    length = len(data)
    h = (seed ^ length) & 0xFFFFFFFF
    idx = 0
    while length >= 4:
        k = int.from_bytes(data[idx:idx + 4], "little")
        k = (k * m) & 0xFFFFFFFF
        k ^= k >> r
        k = (k * m) & 0xFFFFFFFF
        h = (h * m) & 0xFFFFFFFF
        h = (h ^ k) & 0xFFFFFFFF
        idx += 4
        length -= 4
    rem = data[idx:]
    if len(rem) == 3:
        h ^= rem[2] << 16
    if len(rem) >= 2:
        h ^= rem[1] << 8
    if len(rem) >= 1:
        h ^= rem[0]
        h = (h * m) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * m) & 0xFFFFFFFF
    h ^= h >> 15
    return h & 0xFFFFFFFF


def test_murmur2_matches_reference():
    from app.services import content_service as cs
    import os

    cases = [b"", b"a", b"ab", b"abc", b"abcd", b"abcde", b"hello world",
             os.urandom(1000), os.urandom(4096), os.urandom(4097)]
    for seed in (0, 1, 42):
        for data in cases:
            assert cs._murmur2(data, seed) == _ref_murmur2(data, seed)


def test_curseforge_fingerprint_strips_whitespace(tmp_path):
    from app.services import content_service as cs

    path = tmp_path / "x.jar"
    path.write_bytes(b"ab c\td\n\re")          # -> "abcde" nach Whitespace-Strip
    assert cs._curseforge_fingerprint(path) == cs._murmur2(b"abcde", 1)


# --------------------------------------------------------------------------- #
# Metadaten aus Artefakten
# --------------------------------------------------------------------------- #
def test_parse_plugin_yml_top_level_only():
    from app.services import content_service as cs

    text = (
        "name: AdvancedBackups\n"
        "version: 1.21-3.6\n"
        "main: com.example.Main\n"
        "api-version: 1.21\n"
        "commands:\n"
        "  backup:\n"
        "    description: nested wird ignoriert\n"
    )
    meta = cs._parse_plugin_yml(text)
    assert meta == {"name": "AdvancedBackups", "version": "1.21-3.6", "mc_version": "1.21"}


def test_read_artifact_metadata_plugin_yml(tmp_path):
    from app.services import content_service as cs

    path = tmp_path / "plug.jar"
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("plugin.yml", "name: GSit\nversion: 3.1.1\napi-version: 1.21\n")
    path.write_bytes(buf.getvalue())
    meta = cs._read_artifact_metadata(path)
    assert meta.get("name") == "GSit"
    assert meta.get("version") == "3.1.1"
    assert meta.get("mc_version") == "1.21"


def test_read_artifact_metadata_datapack_pack_format(tmp_path):
    from app.services import content_service as cs

    path = tmp_path / "dp.zip"
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("pack.mcmeta", '{"pack":{"pack_format":57,"description":"x"}}')
    path.write_bytes(buf.getvalue())
    meta = cs._read_artifact_metadata(path)
    assert meta.get("mc_version") == "1.21.4"        # 57 -> 1.21.4 (aus der Tabelle)


def test_read_artifact_metadata_bad_zip_returns_empty(tmp_path):
    from app.services import content_service as cs

    path = tmp_path / "broken.jar"
    path.write_bytes(b"not a zip at all")
    assert cs._read_artifact_metadata(path) == {}


# --------------------------------------------------------------------------- #
# Bulk-Lookup-Helfer
# --------------------------------------------------------------------------- #
def test_find_modrinth_versions_by_hashes_posts_and_returns(monkeypatch):
    from app.services import content_service as cs

    captured = {}

    def fake_post(url, payload, headers=None, *, timeout=30):
        captured["url"] = url
        captured["payload"] = payload
        return {"abc": {"project_id": "p", "id": "v"}}

    monkeypatch.setattr(cs, "_request_json_post", fake_post)
    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})
    out = cs.find_modrinth_versions_by_hashes(["ABC", "abc", ""], "sha1")
    assert out == {"abc": {"project_id": "p", "id": "v"}}
    assert captured["url"].endswith("/version_files")
    assert captured["payload"]["algorithm"] == "sha1"
    assert captured["payload"]["hashes"] == ["abc"]          # kleingeschrieben + dedupliziert


def test_find_curseforge_fingerprints_none_without_key(monkeypatch):
    from app.services import content_service as cs

    def raise_no_key():
        raise ValueError("CurseForge API Key fehlt")

    monkeypatch.setattr(cs, "_curseforge_headers", raise_no_key)
    # Kein Key -> None (damit der Aufrufer nichts faelschlich als 'unmatched' markiert).
    assert cs.find_curseforge_fingerprint_matches([123, 456]) is None


# --------------------------------------------------------------------------- #
# auto_adopt_local_content: Modrinth-Treffer / Miss / CurseForge / Idempotenz
# --------------------------------------------------------------------------- #
def _make_server(db, base, server_type="spigot", mc="1.21.1"):
    from app.models.server import Server

    srv = Server(
        name="adopt", slug="adopt", server_type=server_type,
        mc_version=mc, base_path=str(base),
    )
    db.add(srv)
    db.commit()
    return srv


def _write_local_plugin(cs, srv, file_name, body=b"jarbytes"):
    path = cs._content_file_path(srv, "plugin", file_name)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(body)
    return path


def test_auto_adopt_modrinth_hash_match(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from sqlalchemy import select

    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})

    def fake_post(url, payload, headers=None, *, timeout=30):
        assert url.endswith("/version_files")
        # jeden angefragten Hash auf dasselbe Version-Objekt abbilden -> garantierter Treffer
        return {h: {"project_id": "gsit", "id": "ver123", "version_number": "3.1.1"}
                for h in payload["hashes"]}

    def fake_get(url, headers=None):
        raise AssertionError(f"Kein GET erwartet (Name kommt aus plugin.yml): {url}")

    monkeypatch.setattr(cs, "_request_json_post", fake_post)
    monkeypatch.setattr(cs, "_request_json", fake_get)

    base = tmp_path / "srv"
    base.mkdir()
    # Echtes Plugin-Jar mit plugin.yml -> Name kommt aus den Metadaten, kein Netzwerk-Titel noetig.
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("plugin.yml", "name: GSit\nversion: 3.1.1\napi-version: 1.21\n")
    with SessionLocal() as db:
        srv = _make_server(db, base)
        _write_local_plugin(cs, srv, "GSit-3.1.1.jar", body=buf.getvalue())

        result = cs.auto_adopt_local_content(db, srv, None)
        assert result["adopted"] == 1 and result["checked"] == 1

        row = db.scalar(select(InstalledContent).where(InstalledContent.server_id == srv.id))
        assert row.provider_name == "modrinth"
        assert row.external_project_id == "gsit"
        assert row.external_version_id == "ver123"
        assert row.version_label == "3.1.1"
        assert row.name == "GSit"                          # aus plugin.yml
        assert row.declared_mc_version == "1.21"           # api-version -> Badge
        assert row.file_name == "GSit-3.1.1.jar"           # Datei bleibt
        assert row.content_type == "plugin"
        assert row.file_sha1 and len(row.file_sha1) == 40


def test_auto_adopt_miss_marks_unmatched_and_is_idempotent(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from sqlalchemy import select

    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})

    def no_key():
        raise ValueError("kein CF Key")

    monkeypatch.setattr(cs, "_curseforge_headers", no_key)

    post_calls = {"n": 0}

    def fake_post(url, payload, headers=None, *, timeout=30):
        post_calls["n"] += 1
        assert url.endswith("/version_files")          # CF wird mangels Key nie gepostet
        return {}                                       # kein Modrinth-Treffer

    monkeypatch.setattr(cs, "_request_json_post", fake_post)

    base = tmp_path / "srv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base)
        _write_local_plugin(cs, srv, "MyPlugin-1.0.jar")

        first = cs.auto_adopt_local_content(db, srv, None)
        assert first["adopted"] == 0 and first["checked"] == 1
        assert post_calls["n"] == 1

        row = db.scalar(select(InstalledContent).where(InstalledContent.server_id == srv.id))
        assert row.provider_name == "local"
        assert row.local_adopt_state == "unmatched"
        assert row.file_sha1                              # Hash persistiert -> kein Rehash noetig

        # Zweiter Durchlauf (recheck=False): Zeile ist 'unmatched' -> KEIN erneuter Netzwerk-Lookup.
        second = cs.auto_adopt_local_content(db, srv, None)
        assert second["checked"] == 0
        assert post_calls["n"] == 1                       # unveraendert


def test_auto_adopt_curseforge_fingerprint_match(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from sqlalchemy import select

    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})
    monkeypatch.setattr(cs, "_curseforge_headers", lambda: {"x-api-key": "key"})

    base = tmp_path / "srv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="fabric", mc="1.20.1")
        path = cs._content_file_path(srv, "mod", "SomeMod.jar")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"modbytes-curseforge")
        fingerprint = cs._curseforge_fingerprint(path)

        def fake_post(url, payload, headers=None, *, timeout=30):
            if url.endswith("/version_files"):
                return {}                                 # Modrinth verfehlt -> CF-Fallback
            assert url.endswith("/v1/fingerprints")
            assert payload["fingerprints"] == [fingerprint]
            return {"data": {"exactMatches": [
                {"id": 42, "file": {"id": 999, "modId": 42,
                                    "fileFingerprint": fingerprint,
                                    "displayName": "1.4.2", "fileName": "SomeMod.jar"}}
            ]}}

        def fake_get(url, headers=None):
            assert "/v1/mods/42" in url
            return {"data": {"name": "Some Mod"}}

        monkeypatch.setattr(cs, "_request_json_post", fake_post)
        monkeypatch.setattr(cs, "_request_json", fake_get)

        result = cs.auto_adopt_local_content(db, srv, None)
        assert result["adopted"] == 1

        row = db.scalar(select(InstalledContent).where(InstalledContent.server_id == srv.id))
        assert row.provider_name == "curseforge"
        assert row.external_project_id == "42"
        assert row.external_version_id == "999"
        assert row.version_label == "1.4.2"
        assert row.name == "Some Mod"


def test_auto_adopt_transient_cf_failure_does_not_mark_unmatched(client, monkeypatch, tmp_path):
    """Modrinth verfehlt, CF-Key vorhanden, aber CF-Request scheitert transient (5xx) ->
    Zeile bleibt local + state NULL (kein 'unmatched'), damit spaeter erneut versucht wird."""
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from sqlalchemy import select

    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})
    monkeypatch.setattr(cs, "_curseforge_headers", lambda: {"x-api-key": "key"})

    hash_calls = {"n": 0}
    orig_sha1 = cs._file_sha1

    def counting_sha1(path):
        hash_calls["n"] += 1
        return orig_sha1(path)

    monkeypatch.setattr(cs, "_file_sha1", counting_sha1)

    def fake_post(url, payload, headers=None, *, timeout=30):
        if url.endswith("/version_files"):
            return {}                                  # Modrinth verfehlt
        raise ValueError("HTTP 503: Service Unavailable")   # CF transient down

    monkeypatch.setattr(cs, "_request_json_post", fake_post)

    base = tmp_path / "srv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="fabric", mc="1.20.1")
        path = cs._content_file_path(srv, "mod", "Mod.jar")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"modbytes")

        cs.auto_adopt_local_content(db, srv, None)
        row = db.scalar(select(InstalledContent).where(InstalledContent.server_id == srv.id))
        assert row.provider_name == "local"
        assert row.local_adopt_state is None           # NICHT 'unmatched' -> spaeter Retry moeglich
        assert row.file_sha1 and hash_calls["n"] == 1

        # Zweiter Durchlauf: Zeile ist weiterhin NULL -> Netzwerk-Retry, aber SHA1 wird
        # wiederverwendet (kein erneutes Datei-Lesen).
        cs.auto_adopt_local_content(db, srv, None)
        assert hash_calls["n"] == 1                     # kein Rehash


def test_auto_adopt_no_curseforge_key_skips_fingerprint(client, monkeypatch, tmp_path):
    """Ohne CF-Key wird eine Datei fuer den Fingerprint gar nicht erst gelesen."""
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from sqlalchemy import select

    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})
    monkeypatch.setattr(cs, "_curseforge_headers",
                        lambda: (_ for _ in ()).throw(ValueError("kein Key")))

    def boom(path):
        raise AssertionError("Fingerprint darf ohne CF-Key nicht berechnet werden")

    monkeypatch.setattr(cs, "_curseforge_fingerprint", boom)
    monkeypatch.setattr(cs, "_request_json_post",
                        lambda url, payload, headers=None, *, timeout=30: {})   # Modrinth-Miss

    base = tmp_path / "srv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base)
        _write_local_plugin(cs, srv, "P.jar")
        cs.auto_adopt_local_content(db, srv, None)
        row = db.scalar(select(InstalledContent).where(InstalledContent.server_id == srv.id))
        # Modrinth definitiv (miss) + CF dauerhaft ohne Key -> 'unmatched'
        assert row.local_adopt_state == "unmatched"


def test_bulk_update_updates_datapack_despite_no_loader(client, monkeypatch, tmp_path):
    """Regression: Datapacks (loader-unabhaengig) duerfen von 'Alle aktualisieren' NICHT
    pauschal mit 'Loader unbekannt' uebersprungen werden."""
    from types import SimpleNamespace
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs

    monkeypatch.setattr(cs, "list_modrinth_versions",
                        lambda pid, mc, loader, release_channel="all": [{"id": "v_new", "name": "2.0"}])
    calls = []

    def fake_install(db, server, project_id, version_id, content_type, user_id, **kw):
        calls.append((project_id, version_id, content_type))
        return SimpleNamespace(name=f"dp-{project_id}")

    monkeypatch.setattr(cs, "install_modrinth", fake_install)

    base = tmp_path / "srv"
    base.mkdir()
    dp = base / "world" / "datapacks" / "dp.zip"
    dp.parent.mkdir(parents=True)
    dp.write_bytes(b"zip")

    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="1.21.1")
        db.add(InstalledContent(
            server_id=srv.id, provider_name="modrinth", content_type="datapack",
            external_project_id="dproj", external_version_id="v_old", name="Cool DP",
            file_name="dp.zip",
        ))
        db.commit()

        notes, warnings = cs.bulk_update_installed_content(
            db, srv, None, release_channel="release", content_types={"datapack"},
        )

    assert ("dproj", "v_new", "datapack") in calls
    assert len(notes) == 1


# --------------------------------------------------------------------------- #
# Datapack-Ordner-Scan (per-World-Name)
# --------------------------------------------------------------------------- #
def test_sync_discovers_datapacks_with_level_name(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from sqlalchemy import select

    base = tmp_path / "srv"
    base.mkdir()
    (base / "server.properties").write_text("level-name=meinewelt\n", encoding="utf-8")
    dp_dir = base / "meinewelt" / "datapacks"
    dp_dir.mkdir(parents=True)
    (dp_dir / "cooldatapack.zip").write_bytes(b"zip")

    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="1.21.1")
        cs._sync_local_content_entries(db, srv)
        rows = list(db.scalars(select(InstalledContent).where(InstalledContent.server_id == srv.id)))

    datapacks = [r for r in rows if r.content_type == "datapack"]
    assert len(datapacks) == 1
    assert datapacks[0].file_name == "cooldatapack.zip"
    assert datapacks[0].provider_name == "local"


# --------------------------------------------------------------------------- #
# Schema-Migration
# --------------------------------------------------------------------------- #
def test_installed_content_schema_migration_idempotent(client):
    from app.db import init_db

    # create_all (conftest) hat die Spalten bereits angelegt -> zweifacher Aufruf ist ein No-op
    # und darf nicht werfen.
    init_db._ensure_installed_content_schema()
    init_db._ensure_installed_content_schema()

    from sqlalchemy import inspect
    from app.db.session import engine

    cols = {c["name"] for c in inspect(engine).get_columns("installed_content")}
    assert {"file_sha1", "file_sha512", "local_adopt_state", "declared_mc_version"} <= cols
