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
