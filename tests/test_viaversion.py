"""ViaVersion-Installer (ohne echten Netzwerkzugriff, fetch/download gemockt)."""


def _fake_fetch(url, timeout_seconds=20.0):
    slug = url.split("/project/")[1].split("/")[0]
    # Zwei Versionen, aeltere zuerst -> Installer muss die neuere waehlen.
    return [
        {
            "date_published": "2025-01-01",
            "files": [{"primary": True, "filename": f"{slug}-old.jar", "url": f"https://x/{slug}-old.jar"}],
        },
        {
            "date_published": "2026-06-01",
            "files": [{"primary": True, "filename": f"{slug}-new.jar", "url": f"https://x/{slug}-new.jar"}],
        },
    ]


def test_install_multiversion_downloads_latest(tmp_path, monkeypatch):
    import app.providers.server.common as common
    from app.services import viaversion_service as vv

    downloaded: list[str] = []

    def fake_download(url, target, timeout_seconds=60.0):
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"jar")
        downloaded.append(target.name)

    monkeypatch.setattr(common, "fetch_json", _fake_fetch)
    monkeypatch.setattr(common, "download_file", fake_download)

    ok, msg = vv.install_multiversion(str(tmp_path), "1.21.11")
    assert ok, msg
    plugins = tmp_path / "plugins"
    # Alle drei Via-Plugins, jeweils die NEUERE Version (nach date_published).
    for slug in ("viaversion", "viabackwards", "viarewind"):
        assert (plugins / f"{slug}-new.jar").exists()
        assert not (plugins / f"{slug}-old.jar").exists()
    assert len(downloaded) == 3


def test_install_multiversion_replaces_existing(tmp_path, monkeypatch):
    import app.providers.server.common as common
    from app.services import viaversion_service as vv

    plugins = tmp_path / "plugins"
    plugins.mkdir()
    (plugins / "ViaVersion-0.0.1.jar").write_bytes(b"old")  # veraltete Version

    def fake_download(url, target, timeout_seconds=60.0):
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"jar")

    monkeypatch.setattr(common, "fetch_json", _fake_fetch)
    monkeypatch.setattr(common, "download_file", fake_download)

    ok, _ = vv.install_multiversion(str(tmp_path), "1.21.11")
    assert ok
    # Die alte ViaVersion-Jar wurde entfernt (keine zwei Versionen parallel).
    assert not (plugins / "ViaVersion-0.0.1.jar").exists()
    assert (plugins / "viaversion-new.jar").exists()


def test_install_multiversion_reports_failure(tmp_path, monkeypatch):
    import app.providers.server.common as common
    from app.services import viaversion_service as vv

    def boom(url, timeout_seconds=20.0):
        raise OSError("kein Netz")

    monkeypatch.setattr(common, "fetch_json", boom)
    ok, msg = vv.install_multiversion(str(tmp_path), "1.21.11")
    assert not ok
    assert "fehlgeschlagen" in msg.lower()
