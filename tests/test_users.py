def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def test_super_admin_can_open_users_page(client):
    _login_admin(client)
    response = client.get("/users")
    assert response.status_code == 200
    assert "Benutzerverwaltung" in response.text


def test_super_admin_can_create_user(client):
    _login_admin(client)
    response = client.post(
        "/users",
        data={
            "username": "mod_user",
            "password": "securepass123",
            "role": "moderator",
        },
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert "mod_user" in response.text
