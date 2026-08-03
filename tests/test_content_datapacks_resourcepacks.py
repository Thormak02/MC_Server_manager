from types import SimpleNamespace


def test_datapack_target_dir_uses_world(tmp_path):
    from app.services import content_service as cs

    base = tmp_path / "srv"
    base.mkdir()
    (base / "server.properties").write_text("level-name=myworld\n", encoding="utf-8")
    srv = SimpleNamespace(base_path=str(base), server_type="paper")

    target = cs._target_dir(srv, "datapack")
    assert str(target).replace("\\", "/").endswith("myworld/datapacks")

    # Ohne level-name -> Default "world".
    (base / "server.properties").write_text("", encoding="utf-8")
    assert str(cs._target_dir(srv, "datapack")).replace("\\", "/").endswith(
        "world/datapacks"
    )


def test_loader_none_for_datapack_and_resourcepack():
    from app.services import content_service as cs

    for ct in ("datapack", "resourcepack"):
        assert cs._curseforge_loader_type("spigot", ct) is None
        srv = SimpleNamespace(server_type="fabric")
        assert cs._expected_server_loader(srv, ct) is None
        assert cs._modrinth_project_types_for_content_type(ct) == [ct]
        assert ct in cs._CURSEFORGE_CLASS_IDS


def test_set_server_resource_pack_writes_properties(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import content_service

    def fake_request_json(url, headers=None, **kw):
        if "/version/" in url:
            return {
                "files": [
                    {
                        "primary": True,
                        "url": "http://cdn/pack.zip",
                        "hashes": {"sha1": "deadbeef"},
                    }
                ],
                "version_number": "2.1",
            }
        if "/project/" in url:
            return {"title": "Fancy Textures"}
        return {}

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)

    base = tmp_path / "rpsrv"
    base.mkdir()
    (base / "server.properties").write_text(
        "level-name=world\nresource-pack=\n", encoding="utf-8"
    )

    with SessionLocal() as db:
        srv = Server(
            name="rp-server",
            slug="rp-server",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()

        entry = content_service.set_server_resource_pack(
            db, srv, "modrinth", "proj1", "ver1", None
        )
        assert entry.content_type == "resourcepack"
        assert entry.name == "Fancy Textures"

        props = (base / "server.properties").read_text(encoding="utf-8")
        assert "resource-pack=http://cdn/pack.zip" in props
        assert "resource-pack-sha1=deadbeef" in props

        # Resource Pack bleibt gelistet (keine Datei-Selbstheilung).
        installed = content_service.list_installed_content(db, srv)
        assert any(i.content_type == "resourcepack" for i in installed)

        # Entfernen leert server.properties.
        content_service.delete_installed_content(db, srv, entry, None)
        props_after = (base / "server.properties").read_text(encoding="utf-8")
        assert "resource-pack=\n" in props_after or props_after.rstrip().endswith(
            "resource-pack="
        )
