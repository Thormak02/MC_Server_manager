import re


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _import_server(client, server_dir, *, name="Files Server"):
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


def test_java_profile_create_and_default(client):
    _login_admin(client)

    create_response = client.post(
        "/settings/java-profiles",
        data={
            "name": "Java17",
            "java_path": r"C:\Java\jdk-17\bin\java.exe",
            "version_label": "17",
            "description": "Standard Java 17",
            "is_default": "true",
        },
        follow_redirects=True,
    )
    assert create_response.status_code == 200
    assert "Java17" in create_response.text


def test_file_edit_roundtrip(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "files_srv"
    (server_dir / "config").mkdir(parents=True)
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    target_file = server_dir / "config" / "test.cfg"
    target_file.write_text("key=old\n", encoding="utf-8")

    server_location = _import_server(client, server_dir)
    match = re.search(r"/servers/(\d+)", server_location)
    assert match
    server_id = int(match.group(1))

    read_response = client.get(f"/servers/{server_id}/files", params={"file": "config/test.cfg"})
    assert read_response.status_code == 200
    assert "key=old" in read_response.text

    write_response = client.post(
        f"/servers/{server_id}/files/save",
        data={"relative_path": "config/test.cfg", "content": "key=new\n"},
        follow_redirects=True,
    )
    assert write_response.status_code == 200
    assert "key=new" in write_response.text
    assert target_file.read_text(encoding="utf-8") == "key=new\n"


def test_file_assistant_for_server_properties(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "assistant_srv"
    server_dir.mkdir(parents=True)
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    props = server_dir / "server.properties"
    props.write_text("motd=Hello\npvp=true\nmax-players=20\n", encoding="utf-8")

    server_location = _import_server(client, server_dir, name="Assistant Server")
    match = re.search(r"/servers/(\d+)", server_location)
    assert match
    server_id = int(match.group(1))

    read_response = client.get(
        f"/servers/{server_id}/files",
        params={"file": "server.properties", "mode": "assistant"},
    )
    assert read_response.status_code == 200
    assert "Server Properties Assistent" in read_response.text

    save_response = client.post(
        f"/servers/{server_id}/files/assistant-save",
        data={
            "relative_path": "server.properties",
            "motd": "New MOTD",
            "pvp": "false",
            "max-players": "42",
            "extras_text": "allow-flight=true",
        },
        follow_redirects=True,
    )
    assert save_response.status_code == 200
    content = props.read_text(encoding="utf-8")
    assert "motd=New MOTD" in content
    assert "pvp=false" in content
    assert "max-players=42" in content
    assert "allow-flight=true" in content
