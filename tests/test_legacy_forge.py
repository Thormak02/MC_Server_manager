"""Tests fuer Legacy-Forge (<1.17): --installServer erzeugt kein run.bat,
sondern eine universal-/Server-Jar. Der Manager generiert daraus ein run.bat."""

from types import SimpleNamespace


def test_generate_legacy_forge_run_bat_prefers_universal(tmp_path):
    from app.services import process_service as ps

    (tmp_path / "forge-1.10.2-12.18.3.2511-installer.jar").write_text("x")
    (tmp_path / "forge-1.10.2-12.18.3.2511-universal.jar").write_text("x")
    (tmp_path / "minecraft_server.1.10.2.jar").write_text("x")

    server = SimpleNamespace(id=1, memory_min_mb=2048, memory_max_mb=8192)
    run_bat = tmp_path / "run.bat"

    assert ps._generate_legacy_forge_run_bat(server, tmp_path, run_bat) is True
    content = run_bat.read_text(encoding="utf-8")
    assert "-Xms2048M" in content
    assert "-Xmx8192M" in content
    assert "forge-1.10.2-12.18.3.2511-universal.jar" in content
    assert "installer" not in content
    assert content.strip().endswith("nogui %*")


def test_generate_legacy_forge_run_bat_launcher_jar_when_no_universal(tmp_path):
    from app.services import process_service as ps

    # 1.13-1.16: Installer erzeugt eine forge-<mc>-<loader>.jar (kein -universal).
    (tmp_path / "forge-1.16.5-36.2.34-installer.jar").write_text("x")
    (tmp_path / "forge-1.16.5-36.2.34.jar").write_text("x")

    server = SimpleNamespace(id=2, memory_min_mb=0, memory_max_mb=4096)
    run_bat = tmp_path / "run.bat"

    assert ps._generate_legacy_forge_run_bat(server, tmp_path, run_bat) is True
    content = run_bat.read_text(encoding="utf-8")
    assert "forge-1.16.5-36.2.34.jar" in content
    assert "-Xms" not in content  # memory_min_mb=0 -> kein -Xms
    assert "-Xmx4096M" in content


def test_generate_legacy_forge_run_bat_no_jar_returns_false(tmp_path):
    from app.services import process_service as ps

    (tmp_path / "forge-1.10.2-12.18.3.2511-installer.jar").write_text("x")  # nur Installer

    server = SimpleNamespace(id=3, memory_min_mb=2048, memory_max_mb=8192)
    assert ps._generate_legacy_forge_run_bat(server, tmp_path, tmp_path / "run.bat") is False
    assert not (tmp_path / "run.bat").exists()


def test_sync_legacy_forge_run_bat_memory_updates(tmp_path):
    from app.services import server_service as ss

    (tmp_path / "run.bat").write_text(
        "@echo off\n"
        "java -Xms2048M -Xmx8192M -jar forge-1.10.2-12.18.3.2511-universal.jar nogui %*\n",
        encoding="utf-8",
    )
    server = SimpleNamespace(
        base_path=str(tmp_path), memory_min_mb=4096, memory_max_mb=16384
    )
    ss._sync_legacy_forge_run_bat_memory(server)
    content = (tmp_path / "run.bat").read_text(encoding="utf-8")
    assert "-Xms4096M" in content
    assert "-Xmx16384M" in content
    assert "8192M" not in content


def test_sync_legacy_forge_leaves_modern_run_bat_untouched(tmp_path):
    from app.services import server_service as ss

    modern = (
        "@echo off\n"
        "java @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.2.0/win_args.txt nogui %*\n"
    )
    (tmp_path / "run.bat").write_text(modern, encoding="utf-8")
    server = SimpleNamespace(
        base_path=str(tmp_path), memory_min_mb=4096, memory_max_mb=16384
    )
    ss._sync_legacy_forge_run_bat_memory(server)
    assert (tmp_path / "run.bat").read_text(encoding="utf-8") == modern
