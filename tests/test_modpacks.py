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
    assert (new_server_dir / "eula.txt").exists()
    assert (new_server_dir / "config" / "phase6-test.txt").exists()


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
