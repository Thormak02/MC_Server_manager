def test_login_page_available(client):
    response = client.get("/login")
    assert response.status_code == 200
    assert "Login" in response.text


def test_dashboard_redirects_to_login_without_session(client):
    response = client.get("/dashboard", follow_redirects=False)
    assert response.status_code == 303
    assert response.headers["location"] == "/login"


def test_login_success_and_dashboard_access(client):
    login_response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert login_response.status_code == 303
    assert login_response.headers["location"] == "/dashboard"

    dashboard_response = client.get("/dashboard")
    assert dashboard_response.status_code == 200
    assert "Dashboard" in dashboard_response.text
