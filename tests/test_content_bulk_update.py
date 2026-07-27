from types import SimpleNamespace


def test_bulk_update_updates_outdated_skips_current(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.models.server import Server
    from app.services import content_service

    monkeypatch.setattr(
        content_service,
        "list_modrinth_versions",
        lambda pid, mc, loader, release_channel="all": [
            {"id": "v_new", "name": "1.2.0"}
        ],
    )

    calls: list[tuple[str, str, str]] = []

    def fake_install_modrinth(db, server, project_id, version_id, content_type, user_id, **kw):
        calls.append((project_id, version_id, content_type))
        return SimpleNamespace(name=f"mod-{project_id}")

    monkeypatch.setattr(content_service, "install_modrinth", fake_install_modrinth)

    base = tmp_path / "srv"
    base.mkdir()

    with SessionLocal() as db:
        srv = Server(
            name="bulk-update",
            slug="bulk-update",
            server_type="fabric",
            mc_version="1.20.1",
            loader_version="0.15.0",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()

        # Physische Dateien anlegen, sonst raeumt list_installed_content die
        # Eintraege als "verwaist" wieder ab (Selbstheilung).
        for file_name in ("old.jar", "cur.jar"):
            path = content_service._content_file_path(srv, "mod", file_name)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"")

        db.add(
            InstalledContent(
                server_id=srv.id,
                provider_name="modrinth",
                content_type="mod",
                external_project_id="proj_old",
                external_version_id="v_old",
                name="Veraltet",
                file_name="old.jar",
            )
        )
        db.add(
            InstalledContent(
                server_id=srv.id,
                provider_name="modrinth",
                content_type="mod",
                external_project_id="proj_current",
                external_version_id="v_new",
                name="Aktuell",
                file_name="cur.jar",
            )
        )
        db.commit()

        notes, warnings = content_service.bulk_update_installed_content(
            db,
            srv,
            None,
            release_channel="release",
            content_types={"mod", "plugin"},
        )

    # Veralteter Mod wird auf v_new aktualisiert ...
    assert ("proj_old", "v_new", "mod") in calls
    # ... der bereits aktuelle Mod nicht.
    assert all(project_id != "proj_current" for project_id, _v, _t in calls)
    assert len(notes) == 1
