import io
import json
import zipfile


def _csrf_headers(path: str) -> dict[str, str]:
    return {"referer": f"http://testserver{path}"}


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _build_local_mrpack(mc_version: str = "1.21", loader_version: str = "51.0.33") -> bytes:
    payload = io.BytesIO()
    with zipfile.ZipFile(payload, mode="w", compression=zipfile.ZIP_DEFLATED) as zipped:
        index = {
            "formatVersion": 1,
            "game": "minecraft",
            "versionId": "1.0.0",
            "name": "Phase6 Test Pack",
            "dependencies": {"minecraft": mc_version, "forge": loader_version},
            "files": [],
        }
        zipped.writestr("modrinth.index.json", json.dumps(index))
        zipped.writestr("overrides/config/phase6-test.txt", "hello-phase6")
    return payload.getvalue()


def _build_local_neoforge_mrpack(mc_version: str = "1.21.1", loader_version: str = "21.1.200") -> bytes:
    payload = io.BytesIO()
    with zipfile.ZipFile(payload, mode="w", compression=zipfile.ZIP_DEFLATED) as zipped:
        index = {
            "formatVersion": 1,
            "game": "minecraft",
            "versionId": "1.0.0",
            "name": "NeoForge Test Pack",
            "dependencies": {"minecraft": mc_version, "neoforge": loader_version},
            "files": [],
        }
        zipped.writestr("modrinth.index.json", json.dumps(index))
    return payload.getvalue()


def _build_local_curseforge_pack(files: list[dict] | None = None) -> bytes:
    payload = io.BytesIO()
    manifest = {
        "minecraft": {
            "version": "1.21.1",
            "modLoaders": [{"id": "neoforge-21.1.221", "primary": True}],
        },
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": "Curse Pack Test",
        "version": "1.0.0",
        "author": "tests",
        "files": files
        or [
            {"projectID": 111, "fileID": 1001, "required": False},
            {"projectID": 222, "fileID": 2002, "required": True},
        ],
        "overrides": "overrides",
    }
    with zipfile.ZipFile(payload, mode="w", compression=zipfile.ZIP_DEFLATED) as zipped:
        zipped.writestr("manifest.json", json.dumps(manifest))
        zipped.writestr("overrides/config/test.txt", "hello")
    return payload.getvalue()


def test_modpack_import_preview_and_execute_creates_new_server(client, tmp_path):
    _login_admin(client)

    archive_bytes = _build_local_mrpack()
    preview_response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("pack.mrpack", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert preview_response.status_code == 200
    preview_payload = preview_response.json()
    assert preview_payload["pack_name"] == "Phase6 Test Pack"
    assert preview_payload["recommended_server_type"] == "forge"
    token = preview_payload["token"]
    assert token

    new_server_dir = tmp_path / "created_by_modpack"
    execute_response = client.post(
        "/api/modpacks/import-execute",
        data={
            "preview_token": token,
            "new_server_name": "Created By Modpack",
            "new_server_path": str(new_server_dir),
            "port": "25570",
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert execute_response.status_code == 200
    execute_payload = execute_response.json()
    assert execute_payload["created_server"] is True
    assert execute_payload["server_id"] > 0
    assert execute_payload["server_name"] == "Created By Modpack"
    assert execute_payload["installed_count"] == 0
    assert execute_payload["overrides_copied"] == 0
    assert any("ersten serverstart" in note.lower() for note in execute_payload["notes"])
    assert (new_server_dir / "eula.txt").exists()
    assert not (new_server_dir / "config" / "phase6-test.txt").exists()

    from app.db.session import SessionLocal
    from app.models.pending_modpack_install import PendingModpackInstall

    with SessionLocal() as db:
        pending = (
            db.query(PendingModpackInstall)
            .filter(PendingModpackInstall.server_id == int(execute_payload["server_id"]))
            .first()
        )
        assert pending is not None
        assert pending.pack_name == "Phase6 Test Pack"


def test_modpack_preview_recommends_neoforge_for_neoforge_loader(client):
    _login_admin(client)

    archive_bytes = _build_local_neoforge_mrpack()
    response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("neo-pack.mrpack", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["pack_name"] == "NeoForge Test Pack"
    assert payload["recommended_server_type"] == "neoforge"
    assert payload["loader"] == "neoforge"


def test_modpack_import_page_requires_super_admin(client):
    response = client.get("/modpacks/import", follow_redirects=False)
    assert response.status_code == 303
    assert response.headers["location"] == "/login"


def test_modpack_import_page_renders_for_super_admin(client):
    _login_admin(client)
    response = client.get("/modpacks/import")
    assert response.status_code == 200
    assert "Modpack Suche" in response.text


def test_modpack_search_endpoints_accept_modpack_content_type(client, monkeypatch):
    _login_admin(client)

    from app.api.routers import content as content_router

    def fake_search_modrinth(
        query,
        mc_version,
        loader,
        content_type,
        release_channel,
        sort_by="relevance",
        categories=None,
    ):
        assert query == "better"
        assert content_type == "modpack"
        return [
            {
                "id": "better-pack",
                "title": "Better Pack",
                "description": "Test pack",
                "provider": "modrinth",
            }
        ]

    def fake_curseforge_versions(mod_id, mc_version, loader, content_type, release_channel):
        assert mod_id == 123
        assert content_type == "modpack"
        return [{"id": 999, "name": "Pack v1", "release_channel": "release"}]

    monkeypatch.setattr(content_router.content_service, "search_modrinth", fake_search_modrinth)
    monkeypatch.setattr(content_router.content_service, "list_curseforge_versions", fake_curseforge_versions)

    search_response = client.get(
        "/api/content/search",
        params={
            "provider": "modrinth",
            "query": "better",
            "content_type": "modpack",
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert search_response.status_code == 200
    search_payload = search_response.json()
    assert search_payload["results"][0]["id"] == "better-pack"

    versions_response = client.get(
        "/api/content/curseforge/versions",
        params={
            "project_id": 123,
            "content_type": "modpack",
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert versions_response.status_code == 200
    versions_payload = versions_response.json()
    assert versions_payload["versions"][0]["id"] == 999


def test_modrinth_download_archive_accepts_single_reference_version_id(monkeypatch, tmp_path):
    from app.services import modpack_service

    archive_target = tmp_path / "modrinth-pack.mrpack"
    captured: dict[str, str] = {}

    def fake_request_json(url, headers=None):
        if "/project/AbCdEfGh/version" in url:
            raise ValueError("HTTP 404: Not Found")
        if "/version/AbCdEfGh" in url:
            return {
                "id": "AbCdEfGh",
                "files": [{"url": "https://example.invalid/modrinth-pack.mrpack", "primary": True}],
            }
        raise AssertionError(f"Unexpected URL: {url}")

    def fake_download_file(url, target, headers=None):
        captured["url"] = url
        target.write_bytes(b"modrinth-pack")

    monkeypatch.setattr(modpack_service.content_service, "_modrinth_headers", lambda: {})
    monkeypatch.setattr(modpack_service.content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(modpack_service.content_service, "_download_file", fake_download_file)

    version_id = modpack_service._download_modrinth_archive(
        archive_target,
        reference="AbCdEfGh",
        explicit_version_id=None,
    )
    assert version_id == "AbCdEfGh"
    assert captured["url"] == "https://example.invalid/modrinth-pack.mrpack"
    assert archive_target.read_bytes() == b"modrinth-pack"


def test_modrinth_download_archive_prefers_server_pack_when_available(monkeypatch, tmp_path):
    from app.services import modpack_service

    archive_target = tmp_path / "modrinth-server-pack.mrpack"
    captured: dict[str, str] = {}

    def fake_request_json(url, headers=None):
        if "/project/atm/version" in url:
            return [
                {
                    "id": "client-new",
                    "date_published": "2026-03-01T00:00:00Z",
                    "name": "ATM10 Client",
                    "files": [
                        {
                            "url": "https://example.invalid/client-new.mrpack",
                            "filename": "ATM10-client.mrpack",
                            "primary": True,
                        }
                    ],
                },
                {
                    "id": "server-good",
                    "date_published": "2026-02-01T00:00:00Z",
                    "name": "ATM10 Server Files",
                    "files": [
                        {
                            "url": "https://example.invalid/server-good.mrpack",
                            "filename": "ATM10-server.mrpack",
                            "primary": True,
                        }
                    ],
                },
            ]
        raise AssertionError(f"Unexpected URL: {url}")

    def fake_download_file(url, target, headers=None):
        captured["url"] = url
        target.write_bytes(b"modrinth-server-pack")

    monkeypatch.setattr(modpack_service.content_service, "_modrinth_headers", lambda: {})
    monkeypatch.setattr(modpack_service.content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(modpack_service.content_service, "_download_file", fake_download_file)

    decision: dict[str, object] = {}
    version_id = modpack_service._download_modrinth_archive(
        archive_target,
        reference="atm",
        explicit_version_id=None,
        decision=decision,
    )
    assert version_id == "server-good"
    assert captured["url"] == "https://example.invalid/server-good.mrpack"
    assert decision.get("server_pack_selected") is True
    assert decision.get("client_filter_fallback") is False
    assert archive_target.read_bytes() == b"modrinth-server-pack"


def test_parse_curseforge_reference_does_not_treat_profile_code_as_file_id():
    from app.services import modpack_service

    project_id, file_id = modpack_service._parse_curseforge_reference("2VcBaQ-J")
    assert project_id is None
    assert file_id is None


def test_create_preview_accepts_curseforge_profile_code(monkeypatch):
    from app.services import modpack_service

    archive_bytes = _build_local_curseforge_pack()
    captured: dict[str, str] = {}

    def fake_download_file(url, target, headers=None):
        captured["url"] = url
        target.write_bytes(archive_bytes)

    monkeypatch.setattr(modpack_service.content_service, "_download_file", fake_download_file)

    preview = modpack_service.create_preview(
        source="curseforge",
        curseforge_reference="u0aAKxeg",
    )
    assert preview.source_ref == "shared:u0aAKxeg"
    assert "/v1/shared-profile/u0aAKxeg" in captured["url"]
    assert any("fallback" in warning.lower() for warning in preview.warnings)


def test_create_preview_accepts_curseforge_direct_link(monkeypatch):
    from app.services import modpack_service

    archive_bytes = _build_local_curseforge_pack()
    captured: dict[str, str] = {}

    def fake_download_file(url, target, headers=None):
        captured["url"] = url
        target.write_bytes(archive_bytes)

    monkeypatch.setattr(modpack_service.content_service, "_download_file", fake_download_file)

    preview = modpack_service.create_preview(
        source="curseforge",
        curseforge_reference="https://example.invalid/my-custom-pack.zip",
    )
    assert preview.source_ref == "url:https://example.invalid/my-custom-pack.zip"
    assert captured["url"] == "https://example.invalid/my-custom-pack.zip"
    assert any("fallback" in warning.lower() for warning in preview.warnings)


def test_curseforge_download_archive_accepts_file_id_only(monkeypatch, tmp_path):
    from app.services import modpack_service

    archive_target = tmp_path / "curseforge-pack.zip"
    captured: dict[str, str] = {}

    def fake_request_json(url, headers=None):
        if "/v1/mods/files/9876543" in url:
            return {
                "data": {
                    "id": 9876543,
                    "modId": 123456,
                    "downloadUrl": "https://example.invalid/curseforge-pack.zip",
                }
            }
        raise AssertionError(f"Unexpected URL: {url}")

    def fake_download_file(url, target, headers=None):
        captured["url"] = url
        target.write_bytes(b"curseforge-pack")

    monkeypatch.setattr(modpack_service.content_service, "_curseforge_headers", lambda: {})
    monkeypatch.setattr(modpack_service.content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(modpack_service.content_service, "_download_file", fake_download_file)

    project_id, file_id = modpack_service._download_curseforge_archive(
        archive_target,
        project_id=None,
        file_id=9876543,
        reference=None,
    )
    assert project_id == 123456
    assert file_id == 9876543
    assert captured["url"] == "https://example.invalid/curseforge-pack.zip"
    assert archive_target.read_bytes() == b"curseforge-pack"


def test_curseforge_download_archive_prefers_linked_server_pack(monkeypatch, tmp_path):
    from app.services import modpack_service

    archive_target = tmp_path / "curseforge-linked-server-pack.zip"
    captured: dict[str, str] = {}

    def fake_request_json(url, headers=None):
        if "/v1/mods/123456/files/9876543" in url:
            return {
                "data": {
                    "id": 9876543,
                    "modId": 123456,
                    "serverPackFileId": 9877777,
                    "downloadUrl": "https://example.invalid/client-pack.zip",
                }
            }
        if "/v1/mods/123456/files/9877777" in url:
            return {
                "data": {
                    "id": 9877777,
                    "modId": 123456,
                    "isServerPack": True,
                    "downloadUrl": "https://example.invalid/server-pack.zip",
                }
            }
        raise AssertionError(f"Unexpected URL: {url}")

    def fake_download_file(url, target, headers=None):
        captured["url"] = url
        target.write_bytes(b"curseforge-server-pack")

    monkeypatch.setattr(modpack_service.content_service, "_curseforge_headers", lambda: {})
    monkeypatch.setattr(modpack_service.content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(modpack_service.content_service, "_download_file", fake_download_file)

    decision: dict[str, object] = {}
    project_id, file_id = modpack_service._download_curseforge_archive(
        archive_target,
        project_id=123456,
        file_id=9876543,
        reference=None,
        decision=decision,
    )
    assert project_id == 123456
    assert file_id == 9877777
    assert captured["url"] == "https://example.invalid/server-pack.zip"
    assert decision.get("server_pack_selected") is True
    assert decision.get("client_filter_fallback") is False
    assert archive_target.read_bytes() == b"curseforge-server-pack"


def test_curseforge_download_archive_accepts_project_id_only(monkeypatch, tmp_path):
    from app.services import modpack_service

    archive_target = tmp_path / "curseforge-project-only.zip"
    captured: dict[str, str] = {}

    def fake_request_json(url, headers=None):
        if "/v1/mods/123456/files?pageSize=50&index=0" in url:
            return {
                "data": [
                    {"id": 101, "releaseType": 3, "fileDate": "2026-01-01T00:00:00Z"},
                    {"id": 102, "releaseType": 1, "fileDate": "2025-12-31T23:59:59Z"},
                    {
                        "id": 103,
                        "releaseType": 1,
                        "fileDate": "2026-02-01T12:00:00Z",
                        "downloadUrl": "https://example.invalid/curseforge-latest.zip",
                    },
                ]
            }
        raise AssertionError(f"Unexpected URL: {url}")

    def fake_download_file(url, target, headers=None):
        captured["url"] = url
        target.write_bytes(b"curseforge-project")

    monkeypatch.setattr(modpack_service.content_service, "_curseforge_headers", lambda: {})
    monkeypatch.setattr(modpack_service.content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(modpack_service.content_service, "_download_file", fake_download_file)

    project_id, file_id = modpack_service._download_curseforge_archive(
        archive_target,
        project_id=123456,
        file_id=None,
        reference=None,
    )
    assert project_id == 123456
    assert file_id == 103
    assert captured["url"] == "https://example.invalid/curseforge-latest.zip"
    assert archive_target.read_bytes() == b"curseforge-project"


def test_curseforge_preview_preserves_required_flags(client):
    _login_admin(client)

    archive_bytes = _build_local_curseforge_pack(
        files=[
            {"projectID": 100, "fileID": 1, "required": False},
            {"projectID": 200, "fileID": 2, "required": True},
        ]
    )
    response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("curse-pack.zip", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert response.status_code == 200
    payload = response.json()
    entries = payload["entries"]
    assert len(entries) == 2
    assert entries[0]["required"] is False
    assert entries[1]["required"] is True


def test_modpack_execute_processes_optional_curseforge_entries(client, monkeypatch, tmp_path):
    _login_admin(client)

    from app.services import modpack_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    called: list[tuple[int, int]] = []

    def fake_install_curseforge(db, server, mod_id, file_id, content_type, user_id, **kwargs):
        called.append((int(mod_id), int(file_id)))
        assert kwargs.get("resolve_dependencies") is True
        assert kwargs.get("enforce_compatibility") is False
        assert kwargs.get("keep_existing_dependency_version") is True
        assert kwargs.get("client_filter_fallback") is True
        return None

    monkeypatch.setattr(modpack_service.content_service, "install_curseforge", fake_install_curseforge)

    archive_bytes = _build_local_curseforge_pack(
        files=[
            {"projectID": 101, "fileID": 1001, "required": False},
            {"projectID": 202, "fileID": 2002, "required": True},
        ]
    )
    preview_response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("curse-pack.zip", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert preview_response.status_code == 200
    token = preview_response.json()["token"]

    new_server_dir = tmp_path / "curseforge_optional_skip"
    execute_response = client.post(
        "/api/modpacks/import-execute",
        data={
            "preview_token": token,
            "new_server_name": "Curseforge Optional Process",
            "new_server_path": str(new_server_dir),
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert execute_response.status_code == 200
    server_id = int(execute_response.json()["server_id"])

    with SessionLocal() as db:
        server = db.get(Server, server_id)
        assert server is not None
        result = modpack_service.run_pending_install_for_server(
            db,
            server=server,
            initiated_by_user_id=1,
        )
        assert result is not None
        assert result.installed_count == 2
        assert result.warnings == []

    assert called == [(101, 1001), (202, 2002)]


def test_modpack_execute_aborts_on_required_entry_failure(client, monkeypatch, tmp_path):
    _login_admin(client)

    from app.services import modpack_service
    from app.db.session import SessionLocal
    from app.models.pending_modpack_install import PendingModpackInstall
    from app.models.server import Server

    def fake_install_curseforge(db, server, mod_id, file_id, content_type, user_id, **kwargs):
        assert kwargs.get("resolve_dependencies") is True
        assert kwargs.get("enforce_compatibility") is False
        assert kwargs.get("keep_existing_dependency_version") is True
        raise ValueError("Dependency konnte nicht aufgeloest werden")

    monkeypatch.setattr(modpack_service.content_service, "install_curseforge", fake_install_curseforge)

    archive_bytes = _build_local_curseforge_pack(
        files=[{"projectID": 303, "fileID": 3003, "required": True}]
    )
    preview_response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("curse-pack.zip", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert preview_response.status_code == 200
    token = preview_response.json()["token"]

    new_server_dir = tmp_path / "curseforge_required_failure"
    execute_response = client.post(
        "/api/modpacks/import-execute",
        data={
            "preview_token": token,
            "new_server_name": "Curseforge Required Failure",
            "new_server_path": str(new_server_dir),
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert execute_response.status_code == 200
    server_id = int(execute_response.json()["server_id"])

    with SessionLocal() as db:
        server = db.get(Server, server_id)
        assert server is not None
        try:
            modpack_service.run_pending_install_for_server(
                db,
                server=server,
                initiated_by_user_id=1,
            )
            assert False, "Expected ValueError for required mod failure"
        except ValueError as exc:
            assert "Pflicht-Eintrag fehlgeschlagen" in str(exc)

        pending = (
            db.query(PendingModpackInstall)
            .filter(PendingModpackInstall.server_id == server_id)
            .first()
        )
        assert pending is not None
        assert pending.last_error is not None and pending.last_error != ""


def test_modpack_execute_skips_required_non_server_curseforge_entry(client, monkeypatch, tmp_path):
    _login_admin(client)

    from app.services import modpack_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    def fake_install_curseforge(db, server, mod_id, file_id, content_type, user_id, **kwargs):
        raise ValueError("Nicht serverrelevanter CurseForge-Inhalt (classId=12).")

    monkeypatch.setattr(modpack_service.content_service, "install_curseforge", fake_install_curseforge)

    archive_bytes = _build_local_curseforge_pack(
        files=[{"projectID": 616555, "fileID": 5623665, "required": True}]
    )
    preview_response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("curse-pack.zip", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert preview_response.status_code == 200
    token = preview_response.json()["token"]

    new_server_dir = tmp_path / "curseforge_non_server_required"
    execute_response = client.post(
        "/api/modpacks/import-execute",
        data={
            "preview_token": token,
            "new_server_name": "Curseforge Non-Server Required",
            "new_server_path": str(new_server_dir),
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert execute_response.status_code == 200
    server_id = int(execute_response.json()["server_id"])

    with SessionLocal() as db:
        server = db.get(Server, server_id)
        assert server is not None
        result = modpack_service.run_pending_install_for_server(
            db,
            server=server,
            initiated_by_user_id=1,
        )
        assert result is not None
        assert result.installed_count == 0
        assert any("nicht serverrelevant" in warning.lower() for warning in result.warnings)


def test_modpack_execute_skips_required_curseforge_distribution_blocked_entry(client, monkeypatch, tmp_path):
    _login_admin(client)

    from app.services import modpack_service
    from app.db.session import SessionLocal
    from app.models.server import Server

    def fake_install_curseforge(db, server, mod_id, file_id, content_type, user_id, **kwargs):
        raise ValueError(
            "Download per CurseForge API fuer dieses Projekt nicht erlaubt "
            "(allowModDistribution=false)."
        )

    monkeypatch.setattr(modpack_service.content_service, "install_curseforge", fake_install_curseforge)

    archive_bytes = _build_local_curseforge_pack(
        files=[{"projectID": 391382, "fileID": 1234567, "required": True}]
    )
    preview_response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("curse-pack.zip", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert preview_response.status_code == 200
    token = preview_response.json()["token"]

    new_server_dir = tmp_path / "curseforge_distribution_blocked_required"
    execute_response = client.post(
        "/api/modpacks/import-execute",
        data={
            "preview_token": token,
            "new_server_name": "Curseforge Distribution Blocked",
            "new_server_path": str(new_server_dir),
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert execute_response.status_code == 200
    server_id = int(execute_response.json()["server_id"])

    with SessionLocal() as db:
        server = db.get(Server, server_id)
        assert server is not None
        result = modpack_service.run_pending_install_for_server(
            db,
            server=server,
            initiated_by_user_id=1,
        )
        assert result is not None
        assert result.installed_count == 0
        assert any("allowmoddistribution=false" in warning.lower() for warning in result.warnings)


def test_modpack_execute_skips_required_curseforge_client_only_entry_in_fallback(client, monkeypatch, tmp_path):
    _login_admin(client)

    from app.services import modpack_service
    from app.db.session import SessionLocal
    from app.models.pending_modpack_install import PendingModpackInstall
    from app.models.server import Server

    def fake_install_curseforge(db, server, mod_id, file_id, content_type, user_id, **kwargs):
        raise ValueError("Client-only CurseForge-Inhalt (nur Client-Distribution).")

    monkeypatch.setattr(modpack_service.content_service, "install_curseforge", fake_install_curseforge)

    archive_bytes = _build_local_curseforge_pack(
        files=[{"projectID": 900001, "fileID": 900002, "required": True}]
    )
    preview_response = client.post(
        "/api/modpacks/import-preview",
        data={"source": "local_archive"},
        files={"archive_file": ("curse-pack.zip", archive_bytes, "application/zip")},
        headers=_csrf_headers("/modpacks/import"),
    )
    assert preview_response.status_code == 200
    token = preview_response.json()["token"]

    new_server_dir = tmp_path / "curseforge_client_only_fallback"
    execute_response = client.post(
        "/api/modpacks/import-execute",
        data={
            "preview_token": token,
            "new_server_name": "Curseforge ClientOnly Fallback",
            "new_server_path": str(new_server_dir),
        },
        headers=_csrf_headers("/modpacks/import"),
    )
    assert execute_response.status_code == 200
    server_id = int(execute_response.json()["server_id"])

    with SessionLocal() as db:
        pending = (
            db.query(PendingModpackInstall)
            .filter(PendingModpackInstall.server_id == server_id)
            .first()
        )
        assert pending is not None

        snapshot = modpack_service.load_preview(pending.preview_token)
        snapshot.client_filter_fallback = True
        modpack_service._write_snapshot(snapshot)

        server = db.get(Server, server_id)
        assert server is not None
        result = modpack_service.run_pending_install_for_server(
            db,
            server=server,
            initiated_by_user_id=1,
        )
        assert result is not None
        assert result.installed_count == 0
        assert any("client-only" in warning.lower() for warning in result.warnings)
