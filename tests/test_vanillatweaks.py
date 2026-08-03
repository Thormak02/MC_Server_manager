import io
import zipfile


def test_map_vt_version():
    from app.services import vanillatweaks_service as vt

    assert vt.map_vt_version("1.21.11") == "1.21"
    assert vt.map_vt_version("1.20.1") == "1.20"
    assert vt.map_vt_version("1.21") == "1.21"
    assert vt.map_vt_version("garbage") == "1.21"


def test_list_categories_parses(monkeypatch):
    from app.services import content_service, vanillatweaks_service as vt

    monkeypatch.setattr(
        content_service,
        "_request_json",
        lambda url, headers=None: {
            "versionName": "1.21",
            "categories": [
                {
                    "category": "Deco",
                    "packs": [{"name": "armor statues", "display": "Armor Statues"}],
                }
            ],
        },
    )
    cats = vt.list_categories("datapacks", "1.21")
    assert cats and cats[0]["category"] == "Deco"
    assert cats[0]["packs"][0]["name"] == "armor statues"


def _make_container() -> bytes:
    inner = io.BytesIO()
    with zipfile.ZipFile(inner, "w") as z:
        z.writestr("data/foo/x.json", "{}")
        z.writestr("pack.mcmeta", "{}")
    container = io.BytesIO()
    with zipfile.ZipFile(container, "w") as z:
        z.writestr("armor statues.zip", inner.getvalue())
    return container.getvalue()


def test_install_datapacks_places_files(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import content_service, vanillatweaks_service as vt

    monkeypatch.setattr(
        vt, "generate_zip", lambda pack_type, version, selection: _make_container()
    )

    base = tmp_path / "vtsrv"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(
            name="vt-server",
            slug="vt-server",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()

        notes, warnings = vt.install_datapacks(
            db, srv, "datapacks", {"Deco": ["armor statues"]}, None
        )
        assert len(notes) == 1
        assert not warnings

        # Inneres Datapack-.zip liegt im Welt-Datapacks-Ordner.
        placed = list((base / "world" / "datapacks").glob("*.zip"))
        assert len(placed) == 1

        # Als installiertes Datapack gefuehrt (provider vanillatweaks).
        installed = content_service.list_installed_content(db, srv)
        vt_entries = [
            i
            for i in installed
            if i.content_type == "datapack" and i.provider_name == "vanillatweaks"
        ]
        assert len(vt_entries) == 1


def test_vt_resourcepack_hosts_and_sets_properties(client, monkeypatch, tmp_path):
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import vanillatweaks_service as vt

    settings = get_settings()
    monkeypatch.setattr(settings, "public_base_url", "http://thormakmc.ddns.net:8000")
    monkeypatch.setattr(settings, "data_dir", tmp_path)
    monkeypatch.setattr(
        vt, "generate_zip", lambda pack_type, version, selection: b"PACKDATA"
    )

    base = tmp_path / "srv"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(
            name="vt-rp",
            slug="vt-rp",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()

        entry = vt.install_resourcepack(db, srv, {"Aesthetic": ["x"]}, None)
        assert entry.content_type == "resourcepack"

        hosted = list((tmp_path / "resourcepacks").glob("vt_*.zip"))
        assert len(hosted) == 1

        props = (base / "server.properties").read_text(encoding="utf-8")
        assert (
            "resource-pack=http://thormakmc.ddns.net:8000/resourcepacks/vt_" in props
        )
        assert "resource-pack-sha1=" in props


def test_vt_resourcepack_requires_public_url(client, monkeypatch, tmp_path):
    import pytest

    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import vanillatweaks_service as vt

    settings = get_settings()
    monkeypatch.setattr(settings, "public_base_url", None)
    monkeypatch.setattr(vt, "generate_zip", lambda *a, **k: b"X")

    base = tmp_path / "srv2"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(
            name="vt-rp2",
            slug="vt-rp2",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()
        with pytest.raises(ValueError):
            vt.install_resourcepack(db, srv, {"A": ["x"]}, None)
