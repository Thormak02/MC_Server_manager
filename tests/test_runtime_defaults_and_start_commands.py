from pathlib import Path
from types import SimpleNamespace


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def test_delete_content_file_ignores_file_not_found_during_unlink(monkeypatch, tmp_path):
    from app.services import content_service

    class _RacePath:
        def exists(self) -> bool:
            return True

        def unlink(self) -> None:
            raise FileNotFoundError("already gone")

    monkeypatch.setattr(content_service, "_content_file_path", lambda *args, **kwargs: _RacePath())
    server = SimpleNamespace(base_path=str(tmp_path))

    # Must not raise: race between exists() and unlink() is benign.
    content_service._delete_content_file(server, "mod", "example.jar")


def test_service_profile_default_server_root_uses_repo_managed_servers(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import app_setting_service

    monkeypatch.setattr(
        app_setting_service.Path,
        "home",
        staticmethod(lambda: Path(r"C:\Windows\System32\config\systemprofile")),
    )
    expected = (Path(app_setting_service.__file__).resolve().parents[2] / "managed_servers").resolve()

    with SessionLocal() as db:
        resolved = app_setting_service.get_server_storage_root(db)

    assert resolved == expected


def test_command_for_bat_uses_call_without_appending_nogui(tmp_path):
    from app.services import process_service

    base_path = tmp_path / "srv"
    base_path.mkdir(parents=True, exist_ok=True)
    start_bat = base_path / "start.bat"
    start_bat.write_text("@echo off\necho ok\n", encoding="utf-8")
    server = SimpleNamespace(
        start_mode="bat",
        start_command=None,
        start_bat_path=str(start_bat),
    )

    command = process_service._command_for_server(server, base_path)
    assert command[:3] == ["cmd", "/d", "/c"]
    assert "call " in command[3].lower()
    assert "nogui" not in command[3].lower()


def test_create_all_server_types_have_resolvable_start_command(client, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import process_service

    _login_admin(client)
    server_types = ["vanilla", "paper", "spigot", "bukkit", "fabric", "forge", "neoforge"]
    locations: list[str] = []

    for index, server_type in enumerate(server_types, start=1):
        target = tmp_path / f"{server_type}_runtime"
        response = client.post(
            "/servers/create",
            data={
                "name": f"{server_type}-runtime",
                "server_type": server_type,
                "mc_version": "1.20.1",
                "loader_version": "",
                "target_path": str(target),
                "java_profile_id": "",
                "memory_min_mb": "1024",
                "memory_max_mb": "2048",
                "port": str(26000 + index),
            },
            follow_redirects=False,
        )
        assert response.status_code == 303
        locations.append(response.headers["location"])

    with SessionLocal() as db:
        for location in locations:
            server_id = int(location.rsplit("/", 1)[-1])
            server = db.get(Server, server_id)
            assert server is not None
            command = process_service._command_for_server(
                server,
                Path(server.base_path).expanduser().resolve(),
            )
            assert command[:3] == ["cmd", "/d", "/c"]
            assert isinstance(command[3], str)
            assert command[3].strip() != ""
