"""Tests fuer den Import fertiger CurseForge/ServerPackCreator-Server-Packs
(Archive ohne manifest.json / modrinth.index.json)."""

import io
import zipfile
from types import SimpleNamespace

import pytest


def _make_server_pack(*, wrap: str | None = None, with_variables: bool = True) -> bytes:
    buf = io.BytesIO()
    prefix = f"{wrap}/" if wrap else ""
    with zipfile.ZipFile(buf, "w") as z:
        if with_variables:
            z.writestr(
                prefix + "variables.txt",
                "MINECRAFT_VERSION=1.10.2\nMODLOADER=Forge\nMODLOADER_VERSION=12.18.3.2511\n",
            )
        z.writestr(prefix + "mods/SomeMod-1.10.2-1.0.jar", "JAR")
        z.writestr(prefix + "config/some.cfg", "cfg")
        z.writestr(prefix + "world/level.dat", "world")
        z.writestr(prefix + "start.sh", "#!/bin/sh\n")
    return buf.getvalue()


def test_parse_server_pack_detects_metadata(tmp_path):
    from app.services import modpack_service as mps

    archive = tmp_path / "sp.zip"
    archive.write_bytes(_make_server_pack())

    snap = mps._parse_archive("tok", archive, "curseforge", "253026:5222157")
    assert snap.pack_format == "server_pack"
    assert snap.mc_version == "1.10.2"
    assert snap.loader == "forge"
    assert snap.loader_version == "12.18.3.2511"
    assert snap.recommended_server_type == "forge"
    assert snap.entries == []
    assert snap.override_roots == []  # Inhalt liegt im Archiv-Root
    assert snap.override_file_count == 5


def test_apply_server_pack_copies_all_files(tmp_path):
    from app.services import modpack_service as mps

    archive = tmp_path / "sp.zip"
    archive.write_bytes(_make_server_pack())
    snap = mps._parse_archive("tok", archive, "curseforge", "x")

    base = tmp_path / "srv"
    base.mkdir()
    server = SimpleNamespace(base_path=str(base))

    copied, warnings = mps._apply_overrides(
        snapshot=snap, archive_path=archive, server=server
    )
    assert copied == 5
    assert (base / "mods" / "SomeMod-1.10.2-1.0.jar").exists()
    assert (base / "config" / "some.cfg").exists()
    assert (base / "world" / "level.dat").exists()
    assert (base / "variables.txt").exists()


def test_server_pack_wrapper_folder_is_stripped(tmp_path):
    from app.services import modpack_service as mps

    archive = tmp_path / "sp2.zip"
    archive.write_bytes(_make_server_pack(wrap="Forever Stranded"))

    snap = mps._parse_archive("tok", archive, "curseforge", "x")
    assert snap.pack_format == "server_pack"
    assert snap.override_roots == ["Forever Stranded"]
    assert snap.override_file_count == 5

    base = tmp_path / "srv2"
    base.mkdir()
    copied, _ = mps._apply_overrides(
        snapshot=snap, archive_path=archive, server=SimpleNamespace(base_path=str(base))
    )
    assert copied == 5
    assert (base / "mods" / "SomeMod-1.10.2-1.0.jar").exists()
    assert not (base / "Forever Stranded").exists()


def test_server_pack_metadata_fallback_without_variables(tmp_path):
    from app.services import modpack_service as mps

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("mods/forge-1.10.2-12.18.3.2511.jar", "JAR")
        z.writestr("mods/OtherMod-1.10.2-2.0.jar", "JAR")
    archive = tmp_path / "sp3.zip"
    archive.write_bytes(buf.getvalue())

    snap = mps._parse_archive("tok", archive, "curseforge", "x")
    assert snap.pack_format == "server_pack"
    assert snap.mc_version == "1.10.2"  # aus Jar-Namen abgeleitet
    assert snap.loader == "forge"  # aus forge-*.jar abgeleitet


def test_server_pack_variables_with_bom_are_parsed(tmp_path):
    """variables.txt mit UTF-8-BOM (Windows-Editor) darf den ersten Schluessel
    (MINECRAFT_VERSION) nicht verlieren."""
    from app.services import modpack_service as mps

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(
            "variables.txt",
            "﻿MINECRAFT_VERSION=1.20.1\r\nMODLOADER=NeoForge\r\nMODLOADER_VERSION=21.1.66\r\n",
        )
        z.writestr("mods/SomeMod-1.20.1.jar", "JAR")
    archive = tmp_path / "bom.zip"
    archive.write_bytes(buf.getvalue())

    snap = mps._parse_archive("tok", archive, "curseforge", "x")
    assert snap.mc_version == "1.20.1"
    assert snap.loader == "neoforge"
    assert snap.loader_version == "21.1.66"


def test_server_pack_mc_version_ignores_loader_version_tokens(tmp_path):
    """Ohne variables.txt darf die MC-Version NICHT aus Loader-/Library-Jars
    (z.B. neoforge-21.1.66) geraten werden."""
    from app.services import modpack_service as mps

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(
            "libraries/net/neoforged/neoforge/21.1.66/neoforge-21.1.66-universal.jar",
            "JAR",
        )
        z.writestr("mods/Create-1.20.1-forge.jar", "JAR")
        z.writestr("mods/JEI-1.20.1-15.2.0.27.jar", "JAR")
        z.writestr("start.sh", "#!/bin/sh\n")
    archive = tmp_path / "neo.zip"
    archive.write_bytes(buf.getvalue())

    snap = mps._parse_archive("tok", archive, "curseforge", "x")
    assert snap.pack_format == "server_pack"
    assert snap.loader == "neoforge"
    assert snap.mc_version == "1.20.1"  # nicht 1.1.66 aus neoforge-21.1.66


def test_plain_zip_without_modpack_markers_still_raises(tmp_path):
    from app.services import modpack_service as mps

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("readme.txt", "hello")
        z.writestr("images/logo.png", "png")
    archive = tmp_path / "plain.zip"
    archive.write_bytes(buf.getvalue())

    with pytest.raises(ValueError) as excinfo:
        mps._parse_archive("tok", archive, "curseforge", "x")
    assert "weder" in str(excinfo.value)
