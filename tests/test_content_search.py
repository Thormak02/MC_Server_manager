import urllib.parse
from types import SimpleNamespace

from app.services import content_service


def test_curseforge_query_variants_include_atm_aliases():
    variants = [item.lower() for item in content_service._build_curseforge_query_variants("all the mods 10")]

    assert variants[0] == "all the mods 10"
    assert "atm10" in variants
    assert "allthemods10" in variants
    assert "all mods 10" in variants
    assert "allmods10" in variants


def test_curseforge_query_variants_split_compact_letter_digit_queries():
    variants = [item.lower() for item in content_service._build_curseforge_query_variants("atm10")]
    assert "atm10" in variants
    assert "atm 10" in variants


def test_search_curseforge_prefers_relevant_hits_from_later_pages(monkeypatch):
    def fake_request_json(url: str, headers=None):
        parsed = urllib.parse.urlparse(url)
        params = urllib.parse.parse_qs(parsed.query)
        query = params.get("searchFilter", [""])[0]
        index = int(params.get("index", ["0"])[0])

        if query == "all the mods 10" and index == 0:
            return {
                "data": [
                    {
                        "id": 100,
                        "name": "Random Sky Pack",
                        "summary": "generic",
                        "downloadCount": 2_000_000,
                        "slug": "random-sky-pack",
                    }
                ]
            }
        if query == "all the mods 10" and index == 50:
            return {
                "data": [
                    {
                        "id": 101,
                        "name": "All the Mods 10",
                        "summary": "official atm10 pack",
                        "downloadCount": 20_000,
                        "slug": "all-the-mods-10",
                    }
                ]
            }
        if query == "atm10" and index == 0:
            return {
                "data": [
                    {
                        "id": 102,
                        "name": "All the Mods 10 - ATM10",
                        "summary": "atm10",
                        "downloadCount": 100_000,
                        "slug": "atm10",
                    }
                ]
            }
        return {"data": []}

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(content_service, "_curseforge_headers", lambda: {"x-api-key": "test"})

    results = content_service.search_curseforge(
        query="all the mods 10",
        mc_version=None,
        loader=None,
        content_type="modpack",
        release_channel="all",
    )

    assert len(results) >= 2
    assert results[0]["id"] in {101, 102}
    assert any(item["id"] == 101 for item in results)
    assert any(item["id"] == 102 for item in results)


def test_search_curseforge_compact_query_matches_spaced_input(monkeypatch):
    def fake_request_json(url: str, headers=None):
        parsed = urllib.parse.urlparse(url)
        params = urllib.parse.parse_qs(parsed.query)
        query = params.get("searchFilter", [""])[0]
        index = int(params.get("index", ["0"])[0])

        if query == "sky factory" and index == 0:
            return {
                "data": [
                    {
                        "id": 200,
                        "name": "Create: Sky Factory",
                        "summary": "generic sky pack",
                        "downloadCount": 10_000_000,
                        "slug": "create-sky-factory",
                    }
                ]
            }
        if query == "skyfactory" and index == 0:
            return {
                "data": [
                    {
                        "id": 201,
                        "name": "SkyFactory 4",
                        "summary": "official skyfactory pack",
                        "downloadCount": 1_000_000,
                        "slug": "skyfactory-4",
                    }
                ]
            }
        return {"data": []}

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(content_service, "_curseforge_headers", lambda: {"x-api-key": "test"})

    results = content_service.search_curseforge(
        query="sky factory",
        mc_version=None,
        loader=None,
        content_type="modpack",
        release_channel="all",
    )

    assert len(results) >= 2
    assert results[0]["id"] == 201


def test_list_modrinth_categories_maps_plugin_to_minecraft_java_server(monkeypatch):
    def fake_request_json(url: str, headers=None):
        return [
            {"name": "adventure", "header": "Adventure", "project_type": "mod"},
            {"name": "bukkit", "header": "Bukkit", "project_type": "plugin"},
            {"name": "paper", "header": "Paper", "project_type": "minecraft_java_server"},
        ]

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(content_service, "_modrinth_headers", lambda: {"User-Agent": "test"})

    categories = content_service.list_modrinth_categories("plugin")
    ids = [item["id"] for item in categories]

    assert "bukkit" in ids
    assert "paper" in ids
    assert "adventure" not in ids


def test_list_curseforge_loader_types_normalizes_versioned_loader_names(monkeypatch):
    def fake_request_json(url: str, headers=None):
        return {
            "data": [
                {"name": "forge-1.3.2.5"},
                {"name": "Forge-47.2.0"},
                {"name": "NeoForge-20.4.237"},
                {"name": "fabric-loader-0.15.10"},
                {"name": "quilt-loader-0.24.0"},
            ]
        }

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(content_service, "_curseforge_headers", lambda: {"x-api-key": "test"})

    loaders = content_service.list_curseforge_loader_types("mod")

    assert "forge" in loaders
    assert "neoforge" in loaders
    assert "fabric" in loaders
    assert "quilt" in loaders
    assert all("-" not in item for item in loaders)


def test_list_curseforge_loader_types_keeps_core_loader_families_available(monkeypatch):
    def fake_request_json(url: str, headers=None):
        return {
            "data": [
                {"name": "forge-1.3.2.5"},
            ]
        }

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(content_service, "_curseforge_headers", lambda: {"x-api-key": "test"})

    loaders = content_service.list_curseforge_loader_types("modpack")

    assert "forge" in loaders
    assert "neoforge" in loaders
    assert "fabric" in loaders
    assert "quilt" in loaders


def test_search_bukkit_plugins_works_without_curseforge_key(monkeypatch):
    def fake_request_json(url: str, headers=None):
        if "/search/resources/" in url:
            return [
                {
                    "id": 42,
                    "name": "Example Bukkit Plugin",
                    "tag": "test plugin",
                    "downloads": 12345,
                    "likes": 99,
                    "icon": {"url": "data/resource_icons/42.jpg"},
                    "author": {"id": 7},
                    "contributors": "",
                    "category": {"id": 15},
                    "testedVersions": ["1.21", "1.21.1"],
                    "external": False,
                    "premium": False,
                    "file": {"url": "resources/example-bukkit-plugin.42/download?version=2"},
                    "updateDate": 1710000000,
                },
                {
                    "id": 99,
                    "name": "External Plugin",
                    "tag": "external",
                    "downloads": 10,
                    "likes": 1,
                    "author": {"id": 8},
                    "contributors": "",
                    "category": {"id": 15},
                    "testedVersions": ["1.21.1"],
                    "external": True,
                    "premium": False,
                    "file": {"url": "resources/external.99/download?version=1"},
                },
            ]
        return []

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)
    monkeypatch.setattr(content_service, "_curseforge_headers", lambda: (_ for _ in ()).throw(RuntimeError("must not be called")))

    results = content_service.search_bukkit(
        query="example",
        mc_version="1.21.1",
        loader="paper",
        content_type="plugin",
        release_channel="release",
    )

    assert len(results) == 1
    assert results[0]["id"] == "42"
    assert results[0]["provider"] == "bukkit"
    assert "spigotmc.org/resources/example-bukkit-plugin.42" in str(results[0]["project_url"])


def test_list_bukkit_versions_respects_mc_compatibility(monkeypatch):
    def fake_request_json(url: str, headers=None):
        if "/resources/42/versions" in url:
            return [
                {"id": 1001, "name": "v1", "releaseDate": 1710000000},
                {"id": 1000, "name": "v0", "releaseDate": 1700000000},
            ]
        if "/resources/42" in url:
            return {
                "id": 42,
                "testedVersions": ["1.20", "1.20.1"],
            }
        return {}

    monkeypatch.setattr(content_service, "_request_json", fake_request_json)

    incompatible = content_service.list_bukkit_versions(42, "1.21.1", "paper")
    compatible = content_service.list_bukkit_versions(42, "1.20.1", "paper")

    assert incompatible == []
    assert len(compatible) == 2
    assert compatible[0]["id"] == "1001"


def test_default_content_type_for_bukkit_servers_is_plugin():
    server = SimpleNamespace(server_type="bukkit")
    assert content_service._default_content_type(server) == "plugin"
    assert content_service._expected_server_loader(server, "plugin") == "bukkit"


def test_auto_update_plugins_for_server_version_updates_when_available(monkeypatch):
    server = SimpleNamespace(id=1, mc_version="1.21.1", server_type="paper")
    db = object()

    installed_entries = [
        SimpleNamespace(
            content_type="plugin",
            provider_name="modrinth",
            external_project_id="project-modrinth",
            external_version_id="old-modrinth",
            name="Plugin One",
        ),
        SimpleNamespace(
            content_type="plugin",
            provider_name="curseforge",
            external_project_id="12345",
            external_version_id="100",
            name="Plugin Two",
        ),
    ]

    monkeypatch.setattr(content_service, "list_installed_content", lambda *_args, **_kwargs: installed_entries)
    monkeypatch.setattr(
        content_service,
        "list_modrinth_versions",
        lambda *args, **kwargs: [{"id": "new-modrinth", "name": "Plugin One v2"}],
    )
    monkeypatch.setattr(
        content_service,
        "list_curseforge_versions",
        lambda *args, **kwargs: [{"id": 200, "name": "Plugin Two v2"}],
    )
    monkeypatch.setattr(
        content_service,
        "install_modrinth",
        lambda *args, **kwargs: SimpleNamespace(name="Plugin One"),
    )
    monkeypatch.setattr(
        content_service,
        "install_curseforge",
        lambda *args, **kwargs: SimpleNamespace(name="Plugin Two"),
    )

    notes, warnings = content_service.auto_update_plugins_for_server_version(db, server, user_id=1)

    assert len(notes) == 2
    assert any("Plugin One" in note for note in notes)
    assert any("Plugin Two" in note for note in notes)
    assert warnings == []
