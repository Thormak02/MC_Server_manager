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
            "password": "Securepass123",
            "role": "moderator",
        },
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert "mod_user" in response.text


def test_super_admin_can_change_role(client):
    _login_admin(client)
    create_response = client.post(
        "/users",
        data={
            "username": "david",
            "password": "Securepass123",
            "role": "moderator",
        },
        follow_redirects=True,
    )
    assert create_response.status_code == 200

    from app.db.session import SessionLocal
    from app.models.user import User

    with SessionLocal() as db:
        david = db.query(User).filter(User.username == "david").one()
        david_id = david.id

    update_response = client.post(
        f"/users/{david_id}/role",
        data={"role": "admin"},
        follow_redirects=True,
    )
    assert update_response.status_code == 200

    from app.db.session import SessionLocal
    from app.models.user import User

    with SessionLocal() as db:
        david = db.query(User).filter(User.username == "david").one()
        assert david.role == "admin"


def test_super_admin_can_delete_user_and_recreate_same_username(client):
    _login_admin(client)
    create_response = client.post(
        "/users",
        data={
            "username": "david",
            "password": "Securepass123",
            "role": "moderator",
        },
        follow_redirects=True,
    )
    assert create_response.status_code == 200

    from app.db.session import SessionLocal
    from app.models.user import User

    with SessionLocal() as db:
        david = db.query(User).filter(User.username == "david").one()
        david_id = david.id

    delete_response = client.post(
        f"/users/{david_id}/delete",
        follow_redirects=True,
    )
    assert delete_response.status_code == 200

    recreate_response = client.post(
        "/users",
        data={
            "username": "david",
            "password": "AnotherSecure123",
            "role": "view_only",
        },
        follow_redirects=True,
    )
    assert recreate_response.status_code == 200

    with SessionLocal() as db:
        users = db.query(User).filter(User.username == "david").all()
        assert len(users) == 1
        assert users[0].is_active is True
