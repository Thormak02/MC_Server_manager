import re


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _import_server(client, server_dir, *, name="Schedule Server"):
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
    match = re.search(r"/servers/(\d+)", response.headers["location"])
    assert match
    return int(match.group(1))


def test_create_schedule_job(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "schedule_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\ntimeout /t 10 >nul\n", encoding="utf-8")
    server_id = _import_server(client, server_dir)

    create_response = client.post(
        f"/servers/{server_id}/schedules",
        data={
            "job_type": "restart",
            "schedule_expression": "interval:60",
            "delay_seconds": "5",
            "warning_message": "Restart in {seconds}",
        },
        follow_redirects=True,
    )
    assert create_response.status_code == 200
    assert "interval:60" in create_response.text
    assert "restart" in create_response.text


def test_manual_restart_with_delay_and_warning(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "restart_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\ntimeout /t 30 >nul\n", encoding="utf-8")
    server_id = _import_server(client, server_dir)

    client.post(f"/servers/{server_id}/start", follow_redirects=False)
    restart_response = client.post(
        f"/servers/{server_id}/restart",
        data={"delay_seconds": "1", "warning_message": "Restart in {seconds} sec"},
        follow_redirects=True,
    )
    assert restart_response.status_code == 200
    assert "Neustart geplant in 1 Sekunden." in restart_response.text


def test_restart_via_console_command_is_supported(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "console_restart_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\ntimeout /t 10 >nul\n", encoding="utf-8")
    server_id = _import_server(client, server_dir)

    client.post(f"/servers/{server_id}/start", follow_redirects=False)
    response = client.post(
        f"/servers/{server_id}/console/command",
        data={"command": "/restart"},
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert "Neustart geplant in 1 Sekunden." in response.text
