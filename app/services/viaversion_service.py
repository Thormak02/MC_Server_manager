"""Multi-Version-Support fuer die Lobby: ViaVersion & Co. laden.

Damit sich Clients **jeder** Version mit der (einen) Paper-Lobby verbinden koennen,
ohne die klassische "Outdated client/server"-Meldung. Die Plugins uebersetzen das
Protokoll:

- **ViaVersion**   – neuere Clients auf einen aelteren Server
- **ViaBackwards** – aeltere Clients auf einen neueren Server (das braucht der Nutzer,
  z.B. ein 1.21.1-Client auf einer 1.21.11-Lobby)
- **ViaRewind**    – sehr alte Clients (1.8 / 1.7)

Nach dem Transfer landet der Spieler auf dem Zielserver; ideal ist, wenn auch dieser
die passende Version spricht. Fuer den reinen Lobby-Zugang genuegt Via auf der Lobby.
"""

from __future__ import annotations

import json
import urllib.parse
from pathlib import Path

_MODRINTH = "https://api.modrinth.com/v2"
# Reihenfolge = Installationsreihenfolge (Abhaengigkeit ViaVersion zuerst).
_PROJECTS = ("viaversion", "viabackwards", "viarewind")
_LOADERS = ("paper", "spigot", "bukkit", "folia")


def _pick_version_file(
    slug: str,
    mc_version: str,
    loaders_list: tuple[str, ...] = _LOADERS,
    *,
    allow_any_loader: bool = True,
) -> tuple[str, str] | None:
    """(Dateiname, Download-URL) der besten Version fuer ``mc_version`` oder None.

    Erst exakt fuer die MC-Version + Loader; findet Modrinth dazu nichts
    (z.B. brandneue Version), Fallback auf die neueste Version ueberhaupt.
    ``loaders_list`` = ("paper", ...) fuer die Bukkit-Lobby oder ("velocity",) fuer den Proxy.
    ``allow_any_loader=False`` UNTERDRUECKT den letzten loaderlosen Fallback (fuer Velocity:
    sonst landet z.B. ein Bukkit-Build im Proxy-plugins-Ordner, den Velocity nicht laedt).

    WICHTIG: bevorzugt STABILE Releases (version_type=="release") vor Snapshots/Betas. Die
    taeglichen Via-*-SNAPSHOTs sind fluechtig und koennen kaputt sein (broke schon mal alle
    Clients); ein Release wie 5.11.0 deckt 1.8..26.2 ab und ist reproduzierbar. Nur wenn KEIN
    Release die MC-Version unterstuetzt, faellt es auf den neuesten Snapshot zurueck.
    """
    from app.providers.server.common import fetch_json

    loaders = json.dumps(list(loaders_list))
    attempts = [
        {"loaders": loaders, "game_versions": json.dumps([mc_version])},
        {"loaders": loaders},
    ]
    if allow_any_loader:
        attempts.append({})

    def _first_file(version: dict) -> tuple[str, str] | None:
        files = version.get("files") or []
        primary = next((f for f in files if f.get("primary")), files[0] if files else None)
        if primary and primary.get("url") and primary.get("filename"):
            return str(primary["filename"]), str(primary["url"])
        return None

    for params in attempts:
        query = urllib.parse.urlencode(params)
        url = f"{_MODRINTH}/project/{slug}/version"
        if query:
            url += f"?{query}"
        try:
            versions = fetch_json(url)
        except Exception:  # noqa: BLE001 - Netzwerk/JSON -> naechster Versuch
            continue
        if not isinstance(versions, list) or not versions:
            continue
        # Neueste zuerst (Modrinth garantiert die Reihenfolge nicht).
        versions.sort(key=lambda v: str(v.get("date_published", "")), reverse=True)
        # 1) Bevorzugt: neuestes STABILES Release. 2) sonst: neueste Version ueberhaupt.
        releases = [v for v in versions if v.get("version_type") == "release"]
        for version in releases + versions:
            picked = _first_file(version)
            if picked:
                return picked
    return None


def _download_and_swap(url: str, filename: str, plugins: Path, slug: str) -> bool:
    """Neuen Jar SICHER installieren: erst in eine .part-Datei laden, dann - nur bei Erfolg -
    alte Slug-Jars entfernen und die neue Datei an ihren Platz schieben.

    So bleibt bei einem fehlgeschlagenen Download der alte (funktionierende) Jar erhalten,
    statt: alt geloescht + kein Ersatz -> Proxy startet ohne Via -> ALLE Clients scheitern.
    """
    import os

    from app.providers.server.common import download_file

    tmp = plugins / (filename + ".part")
    try:
        download_file(url, tmp)
    except Exception:  # noqa: BLE001 - Download-Fehler -> alten Jar behalten
        try:
            tmp.unlink()
        except OSError:
            pass
        return False
    # Erfolg -> alte Versionen DESSELBEN Plugins weg (kein Doppel-Jar), dann an den Zielnamen.
    for old in plugins.glob("*.jar"):
        if old.name.lower().startswith(slug) and old.name != filename:
            try:
                old.unlink()
            except OSError:
                pass
    try:
        os.replace(tmp, plugins / filename)
    except OSError:
        try:
            tmp.unlink()
        except OSError:
            pass
        return False
    return True


def install_multiversion(lobby_base_path: str, mc_version: str) -> tuple[bool, str]:
    """ViaVersion/ViaBackwards/ViaRewind in ``<lobby>/plugins/`` laden (idempotent).

    Ersetzt vorhandene alte Via*-Jars, damit nicht zwei Versionen parallel liegen.
    Best-effort: schlaegt ein Download fehl, werden die anderen trotzdem geladen (und der
    bisherige Jar bleibt erhalten - siehe _download_and_swap).
    """
    plugins = Path(lobby_base_path).expanduser().resolve() / "plugins"
    try:
        plugins.mkdir(parents=True, exist_ok=True)
    except Exception as exc:  # noqa: BLE001
        return False, f"plugins-Ordner nicht erstellbar: {exc}"

    installed: list[str] = []
    failed: list[str] = []
    for slug in _PROJECTS:
        picked = _pick_version_file(slug, mc_version)
        if picked is None:
            failed.append(slug)
            continue
        filename, url = picked
        if _download_and_swap(url, filename, plugins, slug):
            installed.append(filename)
        else:
            failed.append(slug)

    if not installed:
        return False, f"Multi-Version-Download fehlgeschlagen ({', '.join(failed) or 'unbekannt'})."
    msg = f"Multi-Version installiert: {', '.join(installed)}."
    if failed:
        msg += f" Nicht geladen: {', '.join(failed)}."
    return True, msg


def install_velocity_plugins(plugins_dir: str | Path, mc_version: str) -> tuple[bool, str]:
    """ViaVersion/ViaBackwards/ViaRewind als **Velocity**-Plugins nach ``plugins_dir`` laden.

    Wie ``install_multiversion``, aber die Velocity-Builds (Loader "velocity") in den
    Velocity-``plugins/``-Ordner. So uebersetzt EIN Proxy jede Client-Version (1.7.10 ..
    ``mc_version``) auf die neueste Lobby-Version (abwaerts = die reife Via-Richtung).
    Ersetzt vorhandene alte Via*-Jars nur bei erfolgreichem Download. Best-effort.
    """
    plugins = Path(plugins_dir).expanduser().resolve()
    try:
        plugins.mkdir(parents=True, exist_ok=True)
    except Exception as exc:  # noqa: BLE001
        return False, f"Velocity-plugins-Ordner nicht erstellbar: {exc}"

    installed: list[str] = []
    failed: list[str] = []
    for slug in _PROJECTS:
        # KEIN loaderloser Fallback: lieber sauber ueberspringen als einen Bukkit-Build
        # in den Velocity-plugins-Ordner legen (den Velocity nicht laedt).
        picked = _pick_version_file(slug, mc_version, ("velocity",), allow_any_loader=False)
        if picked is None:
            failed.append(slug)
            continue
        filename, url = picked
        if _download_and_swap(url, filename, plugins, slug):
            installed.append(filename)
        else:
            failed.append(slug)

    if not installed:
        return False, f"Velocity-Via-Download fehlgeschlagen ({', '.join(failed) or 'unbekannt'})."
    msg = f"Velocity-Via installiert: {', '.join(installed)}."
    if failed:
        msg += f" Nicht geladen: {', '.join(failed)}."
    return True, msg
