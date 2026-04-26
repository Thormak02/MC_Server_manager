from pathlib import Path


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def test_server_create_page_available(client):
    _login_admin(client)
    response = client.get("/servers/create")
    assert response.status_code == 200
    assert "Server erstellen" in response.text
    assert "vanilla" in response.text


def test_create_all_provider_types_offline(client, tmp_path):
    _login_admin(client)
    server_types = ["vanilla", "paper", "spigot", "bukkit", "fabric", "forge", "neoforge"]

    for index, server_type in enumerate(server_types, start=1):
        target = tmp_path / f"{server_type}_srv"
        response = client.post(
            "/servers/create",
            data={
                "name": f"{server_type}-server",
                "server_type": server_type,
                "mc_version": "1.20.1",
                "loader_version": "",
                "target_path": str(target),
                "java_profile_id": "",
                "memory_min_mb": "1024",
                "memory_max_mb": "2048",
                "port": str(25560 + index),
            },
            follow_redirects=False,
        )
        assert response.status_code == 303
        location = response.headers["location"]
        assert location.startswith("/servers/")

        detail = client.get(location)
        assert detail.status_code == 200
        assert server_type in detail.text
        assert target.exists()
        assert (target / "eula.txt").exists()
        assert any(Path(target).glob("*.jar")) or (target / "start.bat").exists()


def test_list_versions_endpoint(client):
    _login_admin(client)
    response = client.get("/servers/create/versions", params={"server_type": "vanilla"})
    assert response.status_code == 200
    payload = response.json()
    assert "versions" in payload
    assert isinstance(payload["versions"], list)
