def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _import_server(client, server_dir, *, name="Console Server"):
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
    return response.headers["location"]


def test_console_and_logs_pages_are_available(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "console_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text(
        "@echo off\necho Booting server\nping -n 5 127.0.0.1 >nul\n",
        encoding="utf-8",
    )
    server_location = _import_server(client, server_dir)

    console_response = client.get(f"{server_location}/console")
    assert console_response.status_code == 200
    assert "Konsole" in console_response.text

    logs_response = client.get(f"{server_location}/logs")
    assert logs_response.status_code == 200
    assert "Logs:" in logs_response.text

    command_response = client.post(
        f"{server_location}/console/command",
        data={"command": "say hello"},
        follow_redirects=False,
    )
    assert command_response.status_code == 303


def test_super_admin_can_open_audit_logs(client):
    _login_admin(client)
    response = client.get("/audit-logs")
    assert response.status_code == 200
    assert "Audit Logs" in response.text
