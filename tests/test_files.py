import json
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


def test_access_lists_support_empty_files_and_whitelist_toggle(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_dir = tmp_path / "access_srv"
    server_dir.mkdir(parents=True)
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    (server_dir / "whitelist.json").write_text("", encoding="utf-8")
    (server_dir / "ops.json").write_text("", encoding="utf-8")
    (server_dir / "banned-players.json").write_text("", encoding="utf-8")
    (server_dir / "banned-ips.json").write_text("", encoding="utf-8")

    from app.services import file_service

    monkeypatch.setattr(file_service, "_lookup_mojang_uuid", lambda _name: None)

    server_location = _import_server(client, server_dir, name="Access Server")
    match = re.search(r"/servers/(\d+)", server_location)
    assert match
    server_id = int(match.group(1))

    page_response = client.get(f"/servers/{server_id}/access?tab=whitelist")
    assert page_response.status_code == 200
    assert "Whitelist" in page_response.text

    add_whitelist = client.post(
        f"/servers/{server_id}/access/entry-add",
        data={"list_key": "whitelist", "identity": "PlayerOne"},
        follow_redirects=True,
    )
    assert add_whitelist.status_code == 200
    whitelist_data = json.loads((server_dir / "whitelist.json").read_text(encoding="utf-8"))
    assert len(whitelist_data) == 1
    assert whitelist_data[0]["name"] == "PlayerOne"
    assert "uuid" in whitelist_data[0]

    add_op = client.post(
        f"/servers/{server_id}/access/entry-add",
        data={"list_key": "ops", "identity": "AdminOne", "op_level": "2"},
        follow_redirects=True,
    )
    assert add_op.status_code == 200
    ops_data = json.loads((server_dir / "ops.json").read_text(encoding="utf-8"))
    assert len(ops_data) == 1
    assert ops_data[0]["name"] == "AdminOne"
    assert ops_data[0]["level"] == 2
    assert ops_data[0]["bypassesPlayerLimit"] is True

    update_op_level_response = client.post(
        f"/servers/{server_id}/access/op-level-update",
        data={"identity": "AdminOne", "op_level": "4"},
        follow_redirects=True,
    )
    assert update_op_level_response.status_code == 200
    ops_data_after_update = json.loads((server_dir / "ops.json").read_text(encoding="utf-8"))
    assert ops_data_after_update[0]["name"] == "AdminOne"
    assert ops_data_after_update[0]["level"] == 4

    add_ban = client.post(
        f"/servers/{server_id}/access/entry-add",
        data={"list_key": "banned_players", "identity": "BadOne"},
        follow_redirects=True,
    )
    assert add_ban.status_code == 200
    bans_data = json.loads((server_dir / "banned-players.json").read_text(encoding="utf-8"))
    assert len(bans_data) == 1
    assert bans_data[0]["name"] == "BadOne"
    assert bans_data[0]["reason"] == "Banned by an operator."

    toggle_response = client.post(
        f"/servers/{server_id}/access/whitelist-toggle",
        data={"enabled": "true"},
        follow_redirects=True,
    )
    assert toggle_response.status_code == 200
    props_content = (server_dir / "server.properties").read_text(encoding="utf-8")
    assert "white-list=true" in props_content


def test_assistant_detects_dynamic_server_properties_and_json_fields():
    from app.services.file_service import build_content_from_assistant, get_assistant_payload

    props_payload = get_assistant_payload(
        "server.properties",
        "motd=Hello\nallow-flight=true\ncustom-setting=abc\n",
    )
    assert props_payload is not None
    assert props_payload["mode"] == "server_properties"
    keys = [field["key"] for field in props_payload["fields"]]
    assert "allow-flight" in keys
    assert "custom-setting" in keys

    props_form = {
        "__assistant_field_keys": props_payload["field_keys_json"],
        "__assistant_existing_keys": props_payload["existing_keys_json"],
        "motd": "Updated",
        "allow-flight": "false",
        "custom-setting": "xyz",
        "extras_text": "",
    }
    props_content = build_content_from_assistant("server.properties", props_form)
    assert "motd=Updated" in props_content
    assert "allow-flight=false" in props_content
    assert "custom-setting=xyz" in props_content

    json_payload = get_assistant_payload(
        "config/settings.json",
        json.dumps(
            {
                "graphics": {"fancy": True, "distance": 12},
                "mods": [{"enabled": False}],
                "note": "hello",
            }
        ),
    )
    assert json_payload is not None
    assert json_payload["mode"] == "json_fields"
    labels = {field["label"]: field["key"] for field in json_payload["fields"]}
    assert "graphics.fancy" in labels
    assert "graphics.distance" in labels
    assert "mods[0].enabled" in labels
    assert "note" in labels

    json_form = {
        "__assistant_json_meta": json_payload["assistant_json_meta"],
        "__assistant_json_base": json_payload["assistant_json_base"],
    }
    json_form[labels["graphics.fancy"]] = "false"
    json_form[labels["graphics.distance"]] = "24"
    json_form[labels["mods[0].enabled"]] = "true"
    json_form[labels["note"]] = "updated"

    json_content = build_content_from_assistant("config/settings.json", json_form)
    parsed = json.loads(json_content)
    assert parsed["graphics"]["fancy"] is False
    assert parsed["graphics"]["distance"] == 24
    assert parsed["mods"][0]["enabled"] is True
    assert parsed["note"] == "updated"
