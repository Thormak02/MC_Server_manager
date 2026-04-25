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
from types import SimpleNamespace
