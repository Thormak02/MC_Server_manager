from types import SimpleNamespace
import subprocess


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


def _import_server_with_type(client, server_dir, *, name, server_type, mc_version="1.20.1"):
    response = client.post(
        "/servers/import/confirm",
        data={
            "name": name,
            "base_path": str(server_dir),
            "server_type": server_type,
            "mc_version": mc_version,
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


def test_import_analysis_detects_bukkit_type(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "bukkit_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    (server_dir / "craftbukkit-1.20.1.jar").write_text("", encoding="utf-8")

    response = client.post(
        "/servers/import/analyze",
        data={"base_path": str(server_dir)},
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert "bukkit" in response.text
    assert "start.bat" in response.text


def test_import_analysis_detects_mc_version_from_logs_when_jar_name_has_no_version(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "spigot_logs_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\njava -jar spigot.jar nogui\n", encoding="utf-8")
    (server_dir / "spigot.jar").write_text("", encoding="utf-8")
    (server_dir / "logs").mkdir()
    (server_dir / "logs" / "latest.log").write_text(
        "[12:00:00 INFO]: Starting minecraft server version 1.21.1\n",
        encoding="utf-8",
    )

    response = client.post(
        "/servers/import/analyze",
        data={"base_path": str(server_dir)},
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert 'name="mc_version"' in response.text
    assert 'value="1.21.1"' in response.text


def test_import_analysis_detects_port_and_ram_from_files(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "settings_detect_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text(
        "@echo off\njava -Xms2G -Xmx6G -jar paper.jar nogui\n",
        encoding="utf-8",
    )
    (server_dir / "server.properties").write_text(
        "motd=Test\nserver-port=25570\n",
        encoding="utf-8",
    )

    response = client.post(
        "/servers/import/analyze",
        data={"base_path": str(server_dir)},
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert 'id="port"' in response.text
    assert 'value="25570"' in response.text
    assert 'id="memory_min_mb"' in response.text
    assert 'value="2048"' in response.text
    assert 'id="memory_max_mb"' in response.text
    assert 'value="6144"' in response.text


def test_imported_mod_files_are_listed_as_local_content(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "import_mod_content_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    mods_dir = server_dir / "mods"
    mods_dir.mkdir()
    (mods_dir / "sodium-1.21.1.jar").write_text("", encoding="utf-8")
    (mods_dir / "jei-1.21.1.jar").write_text("", encoding="utf-8")

    server_location = _import_server_with_type(
        client,
        server_dir,
        name="Imported Local Mods",
        server_type="forge",
        mc_version="1.21.1",
    )
    server_id = int(server_location.rsplit("/", 1)[-1])

    first_response = client.get(f"/api/servers/{server_id}/content")
    assert first_response.status_code == 200
    first_items = first_response.json()["items"]
    assert len(first_items) == 2
    assert all(item["provider_name"] == "local" for item in first_items)
    assert all(item["content_type"] == "mod" for item in first_items)

    second_response = client.get(f"/api/servers/{server_id}/content")
    assert second_response.status_code == 200
    second_items = second_response.json()["items"]
    assert len(second_items) == 2


def test_imported_plugin_files_are_listed_as_local_content(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "import_plugin_content_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    plugins_dir = server_dir / "plugins"
    plugins_dir.mkdir()
    (plugins_dir / "EssentialsX-2.21.0.jar").write_text("", encoding="utf-8")

    server_location = _import_server_with_type(
        client,
        server_dir,
        name="Imported Local Plugins",
        server_type="spigot",
        mc_version="1.21.1",
    )
    server_id = int(server_location.rsplit("/", 1)[-1])

    response = client.get(f"/api/servers/{server_id}/content")
    assert response.status_code == 200
    items = response.json()["items"]
    assert len(items) == 1
    assert items[0]["provider_name"] == "local"
    assert items[0]["content_type"] == "plugin"


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


def test_version_change_blocked_for_modded_server(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "forge_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    server_location = _import_server_with_type(
        client,
        server_dir,
        name="Forge Server",
        server_type="forge",
        mc_version="1.20.1",
    )
    server_id = int(server_location.rsplit("/", 1)[-1])

    response = client.post(
        f"/servers/{server_id}/settings",
        data={"mc_version": "1.21.1"},
        follow_redirects=False,
    )
    assert response.status_code == 303
    assert response.headers["location"] == f"/servers/{server_id}"

    version_options = client.get(f"/servers/{server_id}/version-options")
    assert version_options.status_code == 200
    options_payload = version_options.json()
    assert options_payload["versions"] == []
    assert "locked_reason" in options_payload

    from app.db.session import SessionLocal
    from app.models.server import Server

    with SessionLocal() as db:
        server = db.get(Server, server_id)
        assert server is not None
        assert server.mc_version == "1.20.1"


def test_version_change_blocked_for_modpack_server(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "paper_modpack_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    server_location = _import_server_with_type(
        client,
        server_dir,
        name="Paper Modpack Server",
        server_type="paper",
        mc_version="1.20.1",
    )
    server_id = int(server_location.rsplit("/", 1)[-1])

    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.models.server_modpack_state import ServerModpackState

    with SessionLocal() as db:
        db.add(
            ServerModpackState(
                server_id=server_id,
                source="modrinth",
                pack_name="Example Pack",
                source_ref="example",
                upstream_project_id="example",
            )
        )
        db.commit()

    response = client.post(
        f"/servers/{server_id}/settings",
        data={"mc_version": "1.21.1"},
        follow_redirects=False,
    )
    assert response.status_code == 303
    assert response.headers["location"] == f"/servers/{server_id}"

    with SessionLocal() as db:
        server = db.get(Server, server_id)
        assert server is not None
        assert server.mc_version == "1.20.1"


def test_version_change_allowed_for_plugin_server(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_dir = tmp_path / "paper_update_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    server_location = _import_server_with_type(
        client,
        server_dir,
        name="Paper Update Server",
        server_type="paper",
        mc_version="1.20.1",
    )
    server_id = int(server_location.rsplit("/", 1)[-1])

    from app.api.routers import servers as servers_router
    from app.db.session import SessionLocal
    from app.models.server import Server

    called: list[tuple[str, str | None]] = []
    plugin_update_called: list[tuple[int, str]] = []

    def fake_reprovision(server, *, mc_version, loader_version):
        called.append((mc_version, loader_version))
        return ["ok"]

    monkeypatch.setattr(
        servers_router.provisioning_service,
        "reprovision_existing_server",
        fake_reprovision,
    )
    monkeypatch.setattr(
        servers_router.content_service,
        "auto_update_plugins_for_server_version",
        lambda db, server, user_id, release_channel="release": (
            plugin_update_called.append((server.id, release_channel)) or (["plugins updated"], [])
        ),
    )

    response = client.post(
        f"/servers/{server_id}/settings",
        data={"mc_version": "1.21.1"},
        follow_redirects=False,
    )
    assert response.status_code == 303
    assert response.headers["location"] == f"/servers/{server_id}"
    assert called == [("1.21.1", None)]
    assert plugin_update_called == [(server_id, "release")]

    with SessionLocal() as db:
        server = db.get(Server, server_id)
        assert server is not None
        assert server.mc_version == "1.21.1"


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


def test_prepare_neoforge_runtime_retries_after_failed_installer(monkeypatch, tmp_path):
    from app.services import process_service

    base_path = tmp_path / "neoforge_retry"
    base_path.mkdir()
    (base_path / "install_neoforge.bat").write_text("@echo off\necho installing\n", encoding="utf-8")
    start_bat = base_path / "run.bat"
    server = SimpleNamespace(
        id=99,
        server_type="neoforge",
        start_mode="bat",
        start_bat_path=str(start_bat),
    )

    calls = {"count": 0}

    def fake_run(*args, **kwargs):
        calls["count"] += 1
        if calls["count"] == 1:
            return subprocess.CompletedProcess(args=args[0], returncode=1, stdout="Read timed out")
        start_bat.write_text("@echo off\necho run\n", encoding="utf-8")
        return subprocess.CompletedProcess(args=args[0], returncode=0, stdout="ok")

    monkeypatch.setattr(process_service.subprocess, "run", fake_run)

    ok, message = process_service._prepare_loader_runtime_if_needed(server, base_path, runtime_env=None)
    assert ok is True
    assert message == ""
    assert calls["count"] == 2


def test_delete_server_retries_folder_removal_on_transient_error(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_name = "Retry Delete Server"
    server_dir = tmp_path / "delete_retry_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")

    server_location = _import_server(client, server_dir, name=server_name)

    from app.api.routers import servers as servers_router

    calls = {"rmtree": 0, "terminate": 0}

    monkeypatch.setattr(
        servers_router,
        "stop_server",
        lambda db, server, initiated_by_user_id, force=False: (True, "ok"),
    )
    monkeypatch.setattr(
        servers_router,
        "terminate_processes_for_server_path",
        lambda path: calls.__setitem__("terminate", calls["terminate"] + 1) or 0,
    )

    original_rmtree = servers_router.shutil.rmtree

    def flaky_rmtree(path):
        calls["rmtree"] += 1
        if calls["rmtree"] < 3:
            raise OSError("[WinError 32] locked")
        return original_rmtree(path)

    monkeypatch.setattr(servers_router.shutil, "rmtree", flaky_rmtree)

    delete_response = client.post(
        f"{server_location}/delete",
        data={
            "confirm_name": server_name,
            "confirm_delete": "true",
        },
        follow_redirects=False,
    )
    assert delete_response.status_code == 303
    assert delete_response.headers["location"] == "/dashboard"
    assert calls["rmtree"] == 3
    assert calls["terminate"] == 1


def test_start_progress_endpoint_returns_payload(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_dir = tmp_path / "start_progress_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\necho hello\n", encoding="utf-8")
    server_location = _import_server(client, server_dir, name="Start Progress Server")
    server_id = int(server_location.rsplit("/", 1)[-1])

    from app.api.routers import servers as servers_router

    monkeypatch.setattr(
        servers_router,
        "get_start_progress",
        lambda _server_id: {
            "active": True,
            "stage": "loader_install",
            "message": "Installer laeuft",
            "percent": 62,
            "installer_total": 100,
            "installer_done": 65,
            "updated_at": "2026-05-12T16:00:00+00:00",
        },
    )

    response = client.get(f"/api/servers/{server_id}/start-progress")
    assert response.status_code == 200
    payload = response.json()
    assert payload["active"] is True
    assert payload["stage"] == "loader_install"
    assert payload["percent"] == 62
    assert payload["server_status"] in {"stopped", "starting", "running", "error", "crashed", "restarting", "stopping"}
