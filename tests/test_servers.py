from types import SimpleNamespace


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _import_server(client, server_dir, *, name="Imported Server"):
    response = client.post(
        "/servers/import/confirm",
        data={
            "name": name,
            "base_path": str(server_dir),
            "server_type": "paper",
            "mc_version": "1.20.1",
            "start_mode": "bat",
            "start_bat_path": str(server_dir / "start.bat"),
            "start_command": "",
        },
        follow_redirects=False,
    )
    assert response.status_code == 303
    assert response.headers["location"].startswith("/servers/")
    return response.headers["location"]


def test_import_analysis_detects_basic_files(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "paper_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    (server_dir / "paper-1.20.1.jar").write_text("", encoding="utf-8")

    response = client.post(
        "/servers/import/analyze",
        data={"base_path": str(server_dir)},
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert "paper" in response.text
    assert "start.bat" in response.text


def test_start_and_stop_imported_server(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "runtime_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text(
        "@echo off\ntimeout /t 60 >nul\n",
        encoding="utf-8",
    )

    server_location = _import_server(client, server_dir, name="Runtime Server")

    start_response = client.post(
        f"{server_location}/start",
        follow_redirects=False,
    )
    assert start_response.status_code == 303

    detail_running = client.get(server_location)
    assert detail_running.status_code == 200
    assert "running" in detail_running.text

    stop_response = client.post(
        f"{server_location}/stop",
        data={"force": "true"},
        follow_redirects=False,
    )
    assert stop_response.status_code == 303

    detail_stopped = client.get(server_location)
    assert detail_stopped.status_code == 200
    assert "stopped" in detail_stopped.text


def test_start_runs_pending_modpack_install_before_launch(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_dir = tmp_path / "runtime_srv_pending"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text(
        "@echo off\ntimeout /t 60 >nul\n",
        encoding="utf-8",
    )
    server_location = _import_server(client, server_dir, name="Runtime Pending")
    server_id = int(server_location.rsplit("/", 1)[-1])

    from app.schemas.modpack import ModpackExecuteResponse
    from app.services import modpack_service

    called: list[int] = []

    monkeypatch.setattr(
        modpack_service,
        "get_pending_install",
        lambda db, sid: SimpleNamespace(pack_name="Test Pack") if int(sid) == server_id else None,
    )

    def fake_run_pending_install_for_server(db, *, server, initiated_by_user_id):
        called.append(int(server.id))
        return ModpackExecuteResponse(
            server_id=server.id,
            server_name=server.name,
            created_server=False,
            installed_count=2,
            overrides_copied=1,
            warnings=[],
            notes=["ok"],
        )

    monkeypatch.setattr(modpack_service, "run_pending_install_for_server", fake_run_pending_install_for_server)

    start_response = client.post(
        f"{server_location}/start",
        follow_redirects=False,
    )
    assert start_response.status_code == 303
    assert called == [server_id]

    stop_response = client.post(
        f"{server_location}/stop",
        data={"force": "true"},
        follow_redirects=False,
    )
    assert stop_response.status_code == 303


def test_modpack_update_api_endpoints(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_dir = tmp_path / "modpack_update_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    server_location = _import_server(client, server_dir, name="Modpack Update API")
    server_id = int(server_location.rsplit("/", 1)[-1])

    from app.api.routers import servers as servers_router
    from app.schemas.modpack import ModpackExecuteResponse, ModpackPreviewResponse

    queued_calls: list[tuple[str | None, str | None]] = []

    def fake_build_modpack_state_payload(db, *, server, include_latest=False, release_channel="all"):
        payload = {
            "has_modpack": True,
            "source": "modrinth",
            "pack_name": "API Pack",
            "current_version_id": "v1",
            "pending_version_id": None,
            "can_check_updates": True,
            "pending_install": False,
        }
        if include_latest:
            payload["latest_version_id"] = "v3"
            payload["latest_version_label"] = "Version 3"
            payload["update_available"] = True
        return payload

    def fake_queue_modpack_update_for_server(
        db,
        *,
        server,
        requested_by_user_id,
        target_version_id=None,
        reference_override=None,
    ):
        queued_calls.append((target_version_id, reference_override))
        return ModpackPreviewResponse(
            token="preview-token",
            source="modrinth",
            source_ref="project-abc",
            pack_name="API Pack",
        )

    monkeypatch.setattr(
        servers_router.modpack_service,
        "build_modpack_state_payload",
        fake_build_modpack_state_payload,
    )
    monkeypatch.setattr(
        servers_router.modpack_service,
        "get_server_modpack_state",
        lambda db, sid: SimpleNamespace(source="modrinth", upstream_project_id="project-abc")
        if int(sid) == server_id
        else None,
    )
    monkeypatch.setattr(
        servers_router.modpack_service,
        "list_modpack_update_versions",
        lambda **kwargs: [
            {"id": "v3", "name": "Version 3", "release_channel": "release"},
            {"id": "v2", "name": "Version 2", "release_channel": "beta"},
        ],
    )
    monkeypatch.setattr(
        servers_router.modpack_service,
        "queue_modpack_update_for_server",
        fake_queue_modpack_update_for_server,
    )
    monkeypatch.setattr(
        servers_router.modpack_service,
        "run_pending_install_for_server",
        lambda db, *, server, initiated_by_user_id: ModpackExecuteResponse(
            server_id=server.id,
            server_name=server.name,
            created_server=False,
            installed_count=3,
            overrides_copied=1,
            warnings=[],
            notes=[],
        ),
    )

    state_response = client.get(f"/api/servers/{server_id}/modpack/state?include_latest=true")
    assert state_response.status_code == 200
    state_payload = state_response.json()
    assert state_payload["has_modpack"] is True
    assert state_payload["latest_version_id"] == "v3"

    versions_response = client.get(f"/api/servers/{server_id}/modpack/versions")
    assert versions_response.status_code == 200
    versions_payload = versions_response.json()
    assert len(versions_payload["versions"]) == 2
    assert versions_payload["versions"][0]["id"] == "v3"

    queued_response = client.post(
        f"/api/servers/{server_id}/modpack/update",
        data={"target_version_id": "v3"},
    )
    assert queued_response.status_code == 200
    queued_payload = queued_response.json()
    assert queued_payload["queued"] is True
    assert queued_payload["applied"] is False
    assert queued_calls[-1] == ("v3", None)

    apply_response = client.post(
        f"/api/servers/{server_id}/modpack/update",
        data={"target_version_id": "v3", "apply_now": "true"},
    )
    assert apply_response.status_code == 200
    apply_payload = apply_response.json()
    assert apply_payload["queued"] is True
    assert apply_payload["applied"] is True
    assert apply_payload["install_result"]["installed_count"] == 3


def test_delete_server_accepts_server_prefix_in_confirm_name(client, tmp_path):
    _login_admin(client)
    server_name = "All the Mods 10: To the Sky ATM10SKY"
    server_dir = tmp_path / "delete_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")

    server_location = _import_server(client, server_dir, name=server_name)
    delete_response = client.post(
        f"{server_location}/delete",
        data={
            "confirm_name": f"Server: {server_name}",
            "confirm_delete": "true",
            "keep_folder": "true",
        },
        follow_redirects=False,
    )
    assert delete_response.status_code == 303
    assert delete_response.headers["location"] == "/dashboard"

    detail_after_delete = client.get(server_location, follow_redirects=False)
    assert detail_after_delete.status_code == 404
