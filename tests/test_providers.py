"""Provider-Tests: PaperMC v3-API-Parsing + BuildTools-Skip-Logik (netzwerkfrei)."""

from pathlib import Path
from types import SimpleNamespace


def test_paper_list_versions_filters_prereleases(monkeypatch):
    from app.providers.server import paper_provider as pp

    fake = {
        "versions": [
            {"version": {"id": "26.2", "support": {"status": "SUPPORTED"}}},
            {"version": {"id": "26.2-rc-2", "support": {"status": "UNSUPPORTED"}}},
            {"version": {"id": "1.21.11-pre5", "support": {"status": "UNSUPPORTED"}}},
            {"version": {"id": "1.20.1", "support": {"status": "UNSUPPORTED"}}},
        ]
    }
    monkeypatch.setattr(pp, "fetch_json", lambda url: fake)
    provider = pp.PaperProvider()

    release_ids = [v.id for v in provider.list_versions("release")]
    # RC/Pre-Releases raus, alte (aber echte) Releases bleiben waehlbar.
    assert release_ids == ["26.2", "1.20.1"]

    all_ids = [v.id for v in provider.list_versions("all")]
    assert "26.2-rc-2" in all_ids and "1.21.11-pre5" in all_ids


def test_paper_resolve_download_picks_latest_stable_build(monkeypatch):
    from app.providers.server import paper_provider as pp

    # /builds liefert eine BLANKE Liste (kein {"builds": ...}) -> muss toleriert werden.
    builds = [
        {"id": 110, "channel": "STABLE",
         "downloads": {"server:default": {"name": "paper-26.2-110.jar", "url": "https://x/110"}}},
        {"id": 113, "channel": "EXPERIMENTAL",
         "downloads": {"server:default": {"name": "paper-26.2-113.jar", "url": "https://x/113"}}},
        {"id": 112, "channel": "STABLE",
         "downloads": {"server:default": {"name": "paper-26.2-112.jar", "url": "https://x/112"}}},
    ]
    monkeypatch.setattr(pp, "fetch_json", lambda url: builds)
    provider = pp.PaperProvider()

    url, name = provider._resolve_download("26.2")
    assert name == "paper-26.2-112.jar"  # neuester STABLE (nicht der experimentelle 113)
    assert url == "https://x/112"


def test_buildtools_skips_non_bukkit_spigot():
    from app.services import process_service as ps

    srv = SimpleNamespace(id=1, server_type="paper", mc_version="1.21.4")
    ok, msg = ps._prepare_buildtools_if_needed(srv, Path("C:/does-not-matter"), None)
    assert ok is True and msg == ""


def test_buildtools_skips_when_jar_already_built(tmp_path):
    from app.services import process_service as ps

    (tmp_path / "spigot-1.21.4.jar").write_text("jar", encoding="utf-8")
    srv = SimpleNamespace(id=2, server_type="spigot", mc_version="1.21.4")
    ok, msg = ps._prepare_buildtools_if_needed(srv, tmp_path, None)
    assert ok is True and msg == ""  # bereits gebaut -> kein BuildTools-Lauf
