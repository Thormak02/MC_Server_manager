"""Vertraeglichkeitspruefung vor der Installation: Loader und MC-Version.

Hintergrund des gemeldeten Fehlers: beim Installieren von WorldGuard (CurseForge) auf
einen Spigot-Server kam "Inkompatibel fuer diesen Server-Loader (spigot). Unterstuetzte
Loader laut CurseForge: unbekannt." CurseForge fuehrt bei Bukkit-Plugins naemlich oft gar
keinen Loader in ``gameVersions`` - Loader sind ein Forge/Fabric-Begriff. Die Pruefung
wertete diese LEERE Menge als Absage und sperrte damit systematisch Plugins aus, die
nachweislich laufen.

Die Regel lautet jetzt: keine Angabe heisst "nicht beurteilbar" und laesst durch;
abgelehnt wird nur gegen eine NICHT leere Liste, in der nichts passt.
"""
from types import SimpleNamespace

import pytest

from app.services import content_service as cs


# --------------------------------------------------------------------------- #
# Loader
# --------------------------------------------------------------------------- #

def test_missing_loader_info_does_not_block():
    """DER gemeldete Fall: CurseForge nennt keinen Loader -> trotzdem installierbar."""
    assert cs._is_loader_compatible("spigot", set()) is True
    assert cs._is_loader_compatible("paper", set()) is True
    assert cs._is_loader_compatible("forge", set()) is True


def test_named_but_mismatching_loaders_still_block():
    """Die Pruefung bleibt scharf, sobald der Provider sich AEUSSERT."""
    assert cs._is_loader_compatible("spigot", {"forge"}) is False
    assert cs._is_loader_compatible("forge", {"fabric"}) is False
    assert cs._is_loader_compatible("fabric", {"forge", "neoforge"}) is False


@pytest.mark.parametrize(
    "expected,available",
    [
        ("spigot", {"bukkit"}),                 # Bukkit-Plugin laeuft auf Spigot
        ("spigot", {"spigot"}),
        ("paper", {"spigot"}),                  # Paper frisst Spigot- und Bukkit-Plugins
        ("paper", {"bukkit"}),
        ("paper", {"paper"}),
        ("bukkit", {"bukkit"}),
        ("forge", {"forge"}),
        ("neoforge", {"neoforge"}),
        ("fabric", {"fabric", "forge"}),        # Mehrfach-Angabe, eine passt
    ],
)
def test_compatible_combinations_pass(expected, available):
    assert cs._is_loader_compatible(expected, available) is True


def test_bukkit_server_does_not_accept_a_spigot_only_plugin():
    """Die Richtung ist bewusst asymmetrisch: Spigot kann Bukkit, nicht umgekehrt."""
    assert cs._is_loader_compatible("bukkit", {"spigot"}) is False


def test_without_an_expected_loader_everything_passes():
    """Datapacks/Texturepacks haben keinen Loader - da gibt es nichts zu pruefen."""
    assert cs._is_loader_compatible("", {"forge"}) is True
    assert cs._is_loader_compatible("", set()) is True


# --------------------------------------------------------------------------- #
# MC-Version
# --------------------------------------------------------------------------- #

def test_missing_version_info_does_not_block():
    """Gleiche Regel wie beim Loader - keine Angabe ist keine Absage."""
    assert cs._is_mc_version_compatible("1.21.1", set()) is True


def test_named_but_mismatching_versions_still_block():
    assert cs._is_mc_version_compatible("1.21.1", {"1.20.1", "1.19.4"}) is False


@pytest.mark.parametrize(
    "expected,available",
    [
        ("1.21.1", {"1.21.1"}),
        ("1.21", {"1.21.1"}),       # Server auf 1.21, Datei fuer 1.21.1
        ("1.21.1", {"1.21"}),       # und andersherum
        ("26.2", {"26.2"}),
    ],
)
def test_compatible_versions_pass(expected, available):
    assert cs._is_mc_version_compatible(expected, available) is True


# --------------------------------------------------------------------------- #
# Das Installations-Tor als Ganzes
# --------------------------------------------------------------------------- #

def _server(server_type="spigot", mc_version="26.2"):
    return SimpleNamespace(id=8, name="26.2-spigot", server_type=server_type,
                           mc_version=mc_version, base_path="x")


def test_gate_lets_a_plugin_without_any_provider_info_through():
    """Der gemeldete Fall end-to-end: WorldGuard auf dem 26.2-Spigot-Server."""
    cs._raise_if_incompatible_with_server(
        _server(), "plugin",
        provider_name="CurseForge",
        available_loaders=set(),
        available_mc_versions={"26.2"},
    )   # darf NICHT werfen


def test_gate_still_rejects_a_forge_mod_on_a_spigot_server():
    with pytest.raises(ValueError, match="Server-Loader"):
        cs._raise_if_incompatible_with_server(
            _server(), "plugin",
            provider_name="CurseForge",
            available_loaders={"forge"},
            available_mc_versions={"26.2"},
        )


def test_gate_still_rejects_a_wrong_minecraft_version():
    with pytest.raises(ValueError, match="Minecraft"):
        cs._raise_if_incompatible_with_server(
            _server(), "plugin",
            provider_name="CurseForge",
            available_loaders={"spigot"},
            available_mc_versions={"1.20.1"},
        )


def test_gate_rejects_mods_on_a_plugin_server_regardless():
    """Unveraendert hart: ein Spigot-Server kann keine Mods laden, egal was dransteht."""
    with pytest.raises(ValueError, match="keine Mod-Installation"):
        cs._raise_if_incompatible_with_server(
            _server(), "mod",
            provider_name="CurseForge",
            available_loaders=set(),
            available_mc_versions=set(),
        )
