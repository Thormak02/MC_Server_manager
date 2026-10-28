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
# VanillaTweaks-Erkennung + Update
# --------------------------------------------------------------------------- #
def test_auto_adopt_vanillatweaks_datapack(client, monkeypatch, tmp_path):
    """Ein manuell hinzugefuegtes VT-Datapack (nicht auf Modrinth/CF) wird per VT-Katalog
    dem Provider 'vanillatweaks' zugeordnet - mit der aus dem Dateinamen gelesenen Version."""
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from app.services import vanillatweaks_service as vt
    from sqlalchemy import select

    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})
    monkeypatch.setattr(cs, "_curseforge_headers",
                        lambda: (_ for _ in ()).throw(ValueError("kein Key")))
    monkeypatch.setattr(cs, "_request_json_post",
                        lambda url, payload, headers=None, *, timeout=30: {})   # Modrinth-Miss

    lookup = {
        cs._normalized_lookup_key("armor statues"): {
            "pack_type": "datapacks", "category": "Decorative/Cosmetic",
            "name": "armor statues", "display": "Armor Statues", "version": "2.8.21",
        }
    }
    monkeypatch.setattr(vt, "build_datapack_lookup", lambda version: lookup)

    base = tmp_path / "srv"
    base.mkdir()
    dp = base / "world" / "datapacks" / "armor statues v2.8.20 (MC 1.21-1.21.10).zip"
    dp.parent.mkdir(parents=True)
    dp.write_bytes(b"zip")

    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="1.21.11")
        cs.auto_adopt_local_content(db, srv, None)
        row = db.scalar(select(InstalledContent).where(InstalledContent.server_id == srv.id))
        assert row.provider_name == "vanillatweaks"
        assert row.external_project_id == "vt:datapacks"
        assert row.external_version_id == "2.8.20"        # installierte Version aus dem Dateinamen
        assert row.version_label == "2.8.20"
        assert row.name == "Armor Statues"
        assert row.local_adopt_state is None


def _vt_container(inner_name: str) -> bytes:
    inner = io.BytesIO()
    with zipfile.ZipFile(inner, "w") as z:
        z.writestr("pack.mcmeta", '{"pack":{"pack_format":61}}')
    container = io.BytesIO()
    with zipfile.ZipFile(container, "w") as z:
        z.writestr(inner_name, inner.getvalue())
    return container.getvalue()


def test_update_installed_vt_regenerates_and_replaces_file(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(vt, "find_pack", lambda pt, ver, name: {
        "pack_type": "datapacks", "category": "Decorative/Cosmetic",
        "name": "armor statues", "display": "Armor Statues", "version": "2.8.21",
    })
    new_inner = "armor statues v2.8.21 (MC 1.21-1.21.11).zip"
    monkeypatch.setattr(vt, "generate_zip",
                        lambda pack_type, version, selection: _vt_container(new_inner))

    base = tmp_path / "srv"
    base.mkdir()
    old_file = "armor statues v2.8.20 (MC 1.21-1.21.10).zip"
    dp_dir = base / "world" / "datapacks"
    dp_dir.mkdir(parents=True)
    (dp_dir / old_file).write_bytes(b"old")

    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="1.21.11")
        entry = InstalledContent(
            server_id=srv.id, provider_name="vanillatweaks", content_type="datapack",
            external_project_id="vt:datapacks", external_version_id="2.8.20",
            name="Armor Statues", version_label="2.8.20", file_name=old_file,
        )
        db.add(entry)
        db.commit()

        updated, note = vt.update_installed_vt(db, srv, entry, None)
        assert updated is True
        assert entry.version_label == "2.8.21"
        assert entry.external_version_id == "2.8.21"
        assert entry.file_name == new_inner
        assert (dp_dir / new_inner).exists()
        assert not (dp_dir / old_file).exists()           # alte Datei entfernt


def test_update_installed_vt_already_current_is_noop(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(vt, "find_pack", lambda pt, ver, name: {
        "pack_type": "datapacks", "category": "Convenience",
        "name": "elevators", "display": "Elevators", "version": "1.0.16",
    })

    def fail_generate(*a, **k):
        raise AssertionError("generate_zip darf bei aktueller Version nicht laufen")

    monkeypatch.setattr(vt, "generate_zip", fail_generate)

    base = tmp_path / "srv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="1.21.11")
        entry = InstalledContent(
            server_id=srv.id, provider_name="vanillatweaks", content_type="datapack",
            external_project_id="vt:datapacks", external_version_id="1.0.16",
            name="Elevators", version_label="1.0.16",
            file_name="elevators v1.0.16 (MC 1.21-1.21.11).zip",
            # Die Gruppe gehoert dazu: ohne sie gilt der Eintrag als "Herkunft unbekannt"
            # und wird bewusst einmal neu erzeugt (siehe Test darunter).
            declared_mc_version="1.21",
        )
        db.add(entry)
        db.commit()

        updated, note = vt.update_installed_vt(db, srv, entry, None)
        assert updated is False and note == ""            # bereits aktuell -> No-op


# --------------------------------------------------------------------------- #
# VanillaTweaks Crafting Tweaks (EIN kombiniertes Datapack + 'Selected Packs.txt')
# --------------------------------------------------------------------------- #
def _fake_ct_archive(pack_names: list[str]) -> bytes:
    """Simuliert ein kombiniertes VT-Crafting-Tweaks-Datapack: pack.mcmeta + data/ + Manifest."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("pack.mcmeta", '{"pack":{"pack_format":48}}')
        z.writestr("data/example/recipe.json", "{}")
        manifest = ("Vanilla Tweaks Crafting Tweaks\nVersion: 1.21\nPacks:\n"
                    + "".join(f"\t{n}\n" for n in pack_names))
        z.writestr("Selected Packs.txt", manifest)
    return buf.getvalue()


def test_read_selected_packs_parses_manifest(tmp_path):
    from app.services import vanillatweaks_service as vt

    path = tmp_path / "ct.zip"
    path.write_bytes(_fake_ct_archive(["back to blocks", "double doors"]))
    m = vt.read_selected_packs(path)
    assert m == {"kind": "craftingtweaks", "version": "1.21",
                 "packs": ["back to blocks", "double doors"]}
    # normales Datapack ohne Manifest -> None
    plain = tmp_path / "plain.zip"
    with zipfile.ZipFile(plain, "w") as z:
        z.writestr("pack.mcmeta", "{}")
    assert vt.read_selected_packs(plain) is None


def test_install_craftingtweaks_writes_one_combined_datapack(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import vanillatweaks_service as vt
    from sqlalchemy import select as _select

    monkeypatch.setattr(vt, "generate_zip",
                        lambda pt, ver, sel: _fake_ct_archive(["back to blocks"]))
    base = tmp_path / "srv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="paper", mc="1.21.1")
        notes, warnings = vt.install_datapacks(
            db, srv, "craftingtweaks", {"Quality of Life": ["back to blocks"]}, None
        )
        assert warnings == []
        rows = db.scalars(_select(InstalledContent).where(InstalledContent.server_id == srv.id)).all()
        assert len(rows) == 1                                   # EIN Eintrag, kein Datei-Wildwuchs
        assert rows[0].file_name == "VanillaTweaks Crafting Tweaks.zip"
        assert rows[0].provider_name == "vanillatweaks"
        assert rows[0].external_project_id == "vt:craftingtweaks"
        dp = base / "world" / "datapacks" / "VanillaTweaks Crafting Tweaks.zip"
        assert dp.exists()
        with zipfile.ZipFile(dp) as z:
            assert "pack.mcmeta" in z.namelist()               # ein gueltiges Datapack


def test_auto_adopt_recognizes_combined_crafting_tweaks_offline(client, monkeypatch, tmp_path):
    """Ein manuell reingelegtes (kombiniertes) Crafting-Tweaks-Datapack wird per Manifest erkannt -
    OHNE VT-Katalog/Netzwerk (Dateiname ist der VT-Hash-Name)."""
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from app.services import vanillatweaks_service as vt
    from sqlalchemy import select as _select

    monkeypatch.setattr(cs, "_modrinth_headers", lambda: {"User-Agent": "x"})
    monkeypatch.setattr(cs, "_curseforge_headers",
                        lambda: (_ for _ in ()).throw(ValueError("kein Key")))
    monkeypatch.setattr(cs, "_request_json_post",
                        lambda url, payload, headers=None, *, timeout=30: {})
    # Katalog absichtlich kaputt -> Offline-Manifest-Erkennung muss trotzdem greifen.
    monkeypatch.setattr(vt, "build_datapack_lookup",
                        lambda version: (_ for _ in ()).throw(ValueError("Katalog down")))

    base = tmp_path / "srv"
    base.mkdir()
    dp_dir = base / "world" / "datapacks"
    dp_dir.mkdir(parents=True)
    (dp_dir / "VanillaTweaks_abc123_MC1.21-1.21.11.zip").write_bytes(
        _fake_ct_archive(["back to blocks", "double doors"])
    )
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="1.21.11")
        cs.auto_adopt_local_content(db, srv, None)
        row = db.scalar(_select(InstalledContent).where(InstalledContent.server_id == srv.id))
        assert row.provider_name == "vanillatweaks"
        assert row.external_project_id == "vt:craftingtweaks"
        assert row.name == "VanillaTweaks Crafting Tweaks"
        assert row.version_label == "1.21"


def test_update_crafting_tweaks_regenerates_from_manifest(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(vt, "_catalog_packs", lambda pt, ver: (
        [{"pack_type": "craftingtweaks", "category": "Quality of Life",
          "name": "back to blocks", "display": "Back to Blocks", "version": "1.2.14"}]
        if pt == "craftingtweaks" else []
    ))
    gen_calls = []

    def fake_gen(pt, ver, sel):
        gen_calls.append((pt, ver, sel))
        return _fake_ct_archive(["back to blocks"])

    monkeypatch.setattr(vt, "generate_zip", fake_gen)

    base = tmp_path / "srv"
    base.mkdir()
    dp_dir = base / "world" / "datapacks"
    dp_dir.mkdir(parents=True)
    (dp_dir / "VanillaTweaks Crafting Tweaks.zip").write_bytes(_fake_ct_archive(["back to blocks"]))

    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="1.21.11")
        entry = InstalledContent(
            server_id=srv.id, provider_name="vanillatweaks", content_type="datapack",
            external_project_id="vt:craftingtweaks", external_version_id="1.21",
            name="VanillaTweaks Crafting Tweaks", version_label="1.21",
            file_name="VanillaTweaks Crafting Tweaks.zip",
        )
        db.add(entry)
        db.commit()

        updated, note = vt.update_installed_vt(db, srv, entry, None)
        assert updated is True
        assert gen_calls and gen_calls[0][0] == "craftingtweaks"
        # Auswahl aus dem Manifest -> Kategorie aus dem Katalog aufgefuellt
        assert gen_calls[0][2] == {"Quality of Life": ["back to blocks"]}


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


def test_update_installed_vt_regenerates_when_the_version_group_changed(client, monkeypatch,
                                                                       tmp_path):
    """Gleiche Pack-Nummer, andere MC-Gruppe -> trotzdem neu erzeugen.

    Der gemeldete Fehler: auf dem auf 26.2 angehobenen Server blieben alle
    VanillaTweaks-Datapacks die fuer 1.21 erzeugten Dateien. VT vergibt dieselbe
    Pack-Versionsnummer in mehreren Gruppen, und verglichen wurde nur sie - also galt
    das 1.21-Pack als "aktuell". Laden kann ein 26.2-Server es aber nicht: 1.21-Packs
    decken die Formate 48-94 ab, ein 26.2-Server verlangt 101+.
    """
    import io as _io
    import zipfile as _zipfile

    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(vt, "find_pack", lambda pt, ver, name: {
        "pack_type": "datapacks", "category": "Informative",
        "name": "durability ping", "display": "Durability Ping", "version": "1.1.18",
    })

    gerufen = []

    def fake_generate(pack_type, version, selection):
        gerufen.append(version)
        puffer = _io.BytesIO()
        with _zipfile.ZipFile(puffer, "w") as z:
            z.writestr("durability ping v1.1.18 (MC 26.2).zip", b"PK-Platzhalter")
        return puffer.getvalue()

    monkeypatch.setattr(vt, "generate_zip", fake_generate)

    base = tmp_path / "srv262"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="26.2")
        entry = InstalledContent(
            server_id=srv.id, provider_name="vanillatweaks", content_type="datapack",
            external_project_id="vt:datapacks", external_version_id="1.1.18",
            name="Durability Ping", version_label="1.1.18",
            file_name="durability ping v1.1.18 (MC 1.21-1.21.11).zip",
            declared_mc_version="1.21",          # fuer die ALTE Gruppe erzeugt
        )
        db.add(entry)
        db.commit()
        aktualisiert, notiz = vt.update_installed_vt(db, srv, entry, None)

        assert aktualisiert is True, f"haette neu erzeugt werden muessen (Notiz: {notiz!r})"
        assert gerufen == ["26.2"], f"fuer die falsche Gruppe erzeugt: {gerufen}"
        db.refresh(entry)
        assert entry.declared_mc_version == "26.2"


def test_update_installed_vt_without_a_known_group_heals_without_rebuilding(
        client, monkeypatch, tmp_path):
    """Altbestand ohne vermerkte Gruppe: Angabe nachziehen, aber NICHT neu erzeugen.

    Vorher wurde in diesem Fall vorsichtshalber neu erzeugt, weil die Gruppe nur im Feld
    ``declared_mc_version`` stand und dort fehlte. Inzwischen wird sie aus dem DATEINAMEN
    gelesen, den VanillaTweaks selbst schreibt ("... (MC 26.2).zip") - die Datei ist also
    nachweislich die richtige, und ein Neuerzeugen waere sinnlose Arbeit. Mehr noch: jedes
    Neuerzeugen ist die Gelegenheit, bei der die alte Datei liegenbleibt, wenn der Server
    laeuft und sie offen haelt (siehe tests/test_vanillatweaks_dubletten.py).

    Selbstheilend bleibt es trotzdem: die fehlende Angabe wird nachgetragen.
    """
    import io as _io
    import zipfile as _zipfile

    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(vt, "find_pack", lambda pt, ver, name: {
        "pack_type": "datapacks", "category": "Convenience",
        "name": "elevators", "display": "Elevators", "version": "1.0.18",
    })

    def fake_generate(pack_type, version, selection):
        puffer = _io.BytesIO()
        with _zipfile.ZipFile(puffer, "w") as z:
            z.writestr("elevators v1.0.18 (MC 26.2).zip", b"PK-Platzhalter")
        return puffer.getvalue()

    monkeypatch.setattr(vt, "generate_zip", fake_generate)

    base = tmp_path / "srvalt"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="26.2")
        entry = InstalledContent(
            server_id=srv.id, provider_name="vanillatweaks", content_type="datapack",
            external_project_id="vt:datapacks", external_version_id="1.0.18",
            name="Elevators", version_label="1.0.18",
            file_name="elevators v1.0.18 (MC 26.2).zip",
            declared_mc_version=None,            # Altbestand
        )
        db.add(entry)
        db.commit()
        erzeugt: list = []
        echtes_generate = vt.generate_zip
        monkeypatch.setattr(vt, "generate_zip", lambda *a, **k: (
            erzeugt.append(1), echtes_generate(*a, **k))[1])

        aktualisiert, notiz = vt.update_installed_vt(db, srv, entry, None)

        assert aktualisiert is False and notiz == "", "Das Pack ist bereits das richtige"
        assert erzeugt == [], "Es haette nichts neu erzeugt werden duerfen"
        db.refresh(entry)
        # Die fehlende Angabe wird trotzdem nachgetragen.
        assert entry.declared_mc_version == "26.2"


def _vt_datapack(server_id, name, datei, server=None):
    """VT-Datapack-Eintrag. external_version_id ist NOT NULL - immer mitgeben.

    Mit ``server`` wird auch die DATEI angelegt: list_installed_content gleicht mit dem
    Ordner ab und wirft Eintraege ohne Datei hinaus - der Test pruefte sonst nichts.
    """
    from app.models.installed_content import InstalledContent

    if server is not None:
        from app.services import content_service as _cs

        ziel = _cs._target_dir(server, "datapack")
        ziel.mkdir(parents=True, exist_ok=True)
        (ziel / datei).write_bytes(b"PKPlatzhalter")

    return InstalledContent(
        server_id=server_id, provider_name="vanillatweaks", content_type="datapack",
        external_project_id="vt:datapacks", external_version_id="1",
        name=name, file_name=datei,
    )


def test_version_change_also_updates_datapacks(client, monkeypatch, tmp_path):
    """Beim Versionswechsel wurden nur Plugins nachgezogen, Datapacks nicht.

    Genau so blieben beim Sprung 1.21.11 -> 26.2 alle VanillaTweaks-Packs auf der alten
    Gruppe stehen: ein 1.21-Pack deckt die Pack-Formate 48-94 ab, ein 26.x-Server
    verlangt 101+ - es laedt dort nicht, und nichts hat es gemeldet.
    """
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs
    from app.services import vanillatweaks_service as vt

    gerufen = []
    monkeypatch.setattr(vt, "update_installed_vt",
                        lambda db, srv, e, uid: (gerufen.append(e.name), (True, ""))[1])

    base = tmp_path / "vswitch"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="26.3")
        db.add_all([
            _vt_datapack(srv.id, "Durability Ping", "dp.zip", srv),
            _vt_datapack(srv.id, "Elevators", "el.zip", srv),
            # Fremdes Datapack: dafuer gibt es keine Neuerzeugung -> unberuehrt lassen.
            InstalledContent(server_id=srv.id, provider_name="local", content_type="datapack",
                             external_project_id="local", external_version_id="1",
                             name="Eigenbau", file_name="x.zip"),
            # Plugin: gehoert in den anderen Pfad.
            InstalledContent(server_id=srv.id, provider_name="modrinth", content_type="plugin",
                             external_project_id="abc", external_version_id="1",
                             name="Irgendein Plugin", file_name="p.jar"),
        ])
        db.commit()
        notizen, warnungen = cs.auto_update_datapacks_for_server_version(db, srv, None)

    assert sorted(gerufen) == ["Durability Ping", "Elevators"]
    assert len(notizen) == 2 and warnungen == []


def test_datapack_update_survives_a_single_broken_pack(client, monkeypatch, tmp_path):
    """Ein kaputtes Pack darf den Versionswechsel nicht kippen."""
    from app.db.session import SessionLocal
    from app.services import content_service as cs
    from app.services import vanillatweaks_service as vt

    def mal_so_mal_so(db, srv, e, uid):
        if e.name == "Kaputt":
            raise ValueError("bei VanillaTweaks nicht gefunden")
        return True, ""

    monkeypatch.setattr(vt, "update_installed_vt", mal_so_mal_so)

    base = tmp_path / "vswitch2"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="vanilla", mc="26.3")
        db.add_all([_vt_datapack(srv.id, "Kaputt", "k.zip", srv),
                    _vt_datapack(srv.id, "Heil", "h.zip", srv)])
        db.commit()
        notizen, warnungen = cs.auto_update_datapacks_for_server_version(db, srv, None)

    assert any("Heil" in n for n in notizen)
    assert any("Kaputt" in w for w in warnungen)


def _eintrag(server_id, server, art, name, datei, provider="modrinth", projekt="abc"):
    """Inhaltseintrag MIT Datei - sonst wirft der Ordner-Abgleich ihn hinaus."""
    from app.models.installed_content import InstalledContent
    from app.services import content_service as _cs

    ziel = _cs._target_dir(server, art)
    ziel.mkdir(parents=True, exist_ok=True)
    (ziel / datei).write_bytes(b"PK\x03\x04Platzhalter")
    return InstalledContent(
        server_id=server_id, provider_name=provider, content_type=art,
        external_project_id=projekt, external_version_id="alt",
        name=name, file_name=datei,
    )


def test_version_change_updates_mods_too(client, monkeypatch, tmp_path):
    """Mods hingen genauso an der Minecraft-Version wie Plugins, wurden aber nie
    nachgezogen - auf einem Modpack-Server war das der groesste Teil des Inhalts."""
    from app.db.session import SessionLocal
    from app.services import content_service as cs

    gesucht = []
    monkeypatch.setattr(cs, "list_modrinth_versions",
                        lambda pid, mc, loader, release_channel="release": (
                            gesucht.append((pid, mc, loader)) or []))

    base = tmp_path / "modsrv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="fabric", mc="26.3")
        db.add(_eintrag(srv.id, srv, "mod", "Sodium", "sodium.jar"))
        db.commit()
        _notizen, warnungen = cs.auto_update_content_for_server_version(db, srv, None)

    assert gesucht, "fuer die Mod wurde gar nicht nach einem neuen Build gesucht"
    assert gesucht[0][1] == "26.3" and gesucht[0][2] == "fabric"
    assert any("26.3" in w for w in warnungen), "fehlender Build muss gemeldet werden"


def test_loader_is_derived_per_content_type():
    """Der Loader haengt am INHALTSTYP, nicht am Server allein.

    Ein Spigot-Server kann keine Mods laden, ein Fabric-Server keine Plugins -
    solche Eintraege duerfen nicht mit dem falschen Loader gesucht werden.
    Datapacks/Resourcepacks sind loader-unabhaengig und gehen deshalb einen
    eigenen Weg (VanillaTweaks-Neuerzeugung).
    """
    from types import SimpleNamespace

    from app.services.content_service import _expected_server_loader as loader

    spigot = SimpleNamespace(server_type="spigot", mc_version="26.3")
    fabric = SimpleNamespace(server_type="fabric", mc_version="26.3")

    assert loader(spigot, "plugin") == "spigot"
    assert loader(spigot, "mod") is None            # Spigot laedt keine Mods
    assert loader(fabric, "mod") == "fabric"
    assert loader(fabric, "plugin") is None         # Fabric laedt keine Plugins
    assert loader(spigot, "datapack") is None       # bewusst loader-unabhaengig
    assert loader(spigot, "resourcepack") is None


def test_entries_the_server_cannot_host_are_skipped(client, monkeypatch, tmp_path):
    """Ein Fabric-Server hat keine Plugins - fuer so einen Eintrag darf gar nicht
    erst beim Anbieter gesucht werden."""
    from app.db.session import SessionLocal
    from app.services import content_service as cs

    gesucht = []
    monkeypatch.setattr(cs, "list_modrinth_versions",
                        lambda pid, mc, loader, release_channel="release": (
                            gesucht.append((pid, loader)) or []))

    base = tmp_path / "fabsrv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="fabric", mc="26.3")
        db.add(_eintrag(srv.id, srv, "mod", "Sodium", "sodium.jar", projekt="sod"))
        db.commit()
        cs.auto_update_content_for_server_version(db, srv, None)

    # Zweimal gesucht ist normal: findet der Release-Kanal nichts, wird mit
    # release_channel="all" nachgefasst. Entscheidend ist WOMIT gesucht wurde.
    assert set(gesucht) == {("sod", "fabric")}, f"falsch gesucht: {gesucht}"


def test_plugin_wrapper_still_limits_itself_to_plugins(client, monkeypatch, tmp_path):
    """Der alte Einstiegspunkt darf sich nicht heimlich verbreitert haben:
    auf einem Fabric-Server hat er nichts zu tun, der allgemeine dagegen schon."""
    from app.db.session import SessionLocal
    from app.services import content_service as cs

    gesucht = []
    monkeypatch.setattr(cs, "list_modrinth_versions",
                        lambda pid, mc, loader, release_channel="release": (
                            gesucht.append(pid) or []))

    base = tmp_path / "wrapsrv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="fabric", mc="26.3")
        db.add(_eintrag(srv.id, srv, "mod", "Sodium", "sodium.jar", projekt="sod"))
        db.commit()

        cs.auto_update_plugins_for_server_version(db, srv, None)
        assert gesucht == [], "der Wrapper hat eine Mod angefasst"

        cs.auto_update_content_for_server_version(db, srv, None)
        assert set(gesucht) == {"sod"}, "der allgemeine Pfad haette die Mod nachziehen muessen"


def test_modpack_servers_keep_their_mods_untouched(client, monkeypatch, tmp_path):
    """Auf einem Modpack-Server gehoeren die Mods DEM PACK.

    Sie einzeln beim Anbieter nachzuziehen triebe den Server von seinem Manifest weg -
    andere Versionen als das Pack vorsieht, fehlende Abhaengigkeiten - und das naechste
    Pack-Update ueberschriebe ohnehin alles wieder. Auf ATM10SKY waeren das 296 Mods.
    """
    from app.db.session import SessionLocal
    from app.services import content_service as cs
    from app.services import modpack_service

    gesucht = []
    monkeypatch.setattr(cs, "list_modrinth_versions",
                        lambda pid, mc, loader, release_channel="release": (
                            gesucht.append(pid) or []))
    monkeypatch.setattr(modpack_service, "get_server_modpack_state",
                        lambda db, sid: object())      # Server haengt an einem Pack

    base = tmp_path / "packsrv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="fabric", mc="26.3")
        db.add(_eintrag(srv.id, srv, "mod", "Sodium", "sodium.jar", projekt="sod"))
        db.commit()
        notizen, _warnungen = cs.auto_update_content_for_server_version(db, srv, None)

    assert gesucht == [], "die Mods eines Modpack-Servers duerfen nicht einzeln angefasst werden"
    assert any("Modpack" in n for n in notizen), "der Grund gehoert in die Meldung"


def test_without_a_modpack_mods_are_still_updated(client, monkeypatch, tmp_path):
    """Gegenprobe - ein handgebauter Modded-Server soll weiter nachgezogen werden."""
    from app.db.session import SessionLocal
    from app.services import content_service as cs
    from app.services import modpack_service

    gesucht = []
    monkeypatch.setattr(cs, "list_modrinth_versions",
                        lambda pid, mc, loader, release_channel="release": (
                            gesucht.append(pid) or []))
    monkeypatch.setattr(modpack_service, "get_server_modpack_state", lambda db, sid: None)

    base = tmp_path / "handsrv"
    base.mkdir()
    with SessionLocal() as db:
        srv = _make_server(db, base, server_type="fabric", mc="26.3")
        db.add(_eintrag(srv.id, srv, "mod", "Sodium", "sodium.jar", projekt="sod"))
        db.commit()
        cs.auto_update_content_for_server_version(db, srv, None)

    assert set(gesucht) == {"sod"}
