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


def _pick_version_file(slug: str, mc_version: str) -> tuple[str, str] | None:
    """(Dateiname, Download-URL) der besten Version fuer ``mc_version`` oder None.

    Erst exakt fuer die MC-Version + Bukkit-Loader; findet Modrinth dazu nichts
    (z.B. brandneue Version), Fallback auf die neueste Version ueberhaupt.
    """
    from app.providers.server.common import fetch_json

    loaders = json.dumps(list(_LOADERS))
    attempts = (
        {"loaders": loaders, "game_versions": json.dumps([mc_version])},
        {"loaders": loaders},
        {},
    )
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
        for version in versions:
            files = version.get("files") or []
            primary = next((f for f in files if f.get("primary")), files[0] if files else None)
            if primary and primary.get("url") and primary.get("filename"):
                return str(primary["filename"]), str(primary["url"])
    return None


def install_multiversion(lobby_base_path: str, mc_version: str) -> tuple[bool, str]:
    """ViaVersion/ViaBackwards/ViaRewind in ``<lobby>/plugins/`` laden (idempotent).

    Ersetzt vorhandene alte Via*-Jars, damit nicht zwei Versionen parallel liegen.
    Best-effort: schlaegt ein Download fehl, werden die anderen trotzdem geladen.
    """
    from app.providers.server.common import download_file

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
        # Alte Version(en) desselben Plugins entfernen (case-insensitiv).
        for old in plugins.glob("*.jar"):
            if old.name.lower().startswith(slug):
                try:
                    old.unlink()
                except OSError:
                    pass
        try:
            download_file(url, plugins / filename)
            installed.append(filename)
        except Exception:  # noqa: BLE001
            failed.append(slug)

    if not installed:
        return False, f"Multi-Version-Download fehlgeschlagen ({', '.join(failed) or 'unbekannt'})."
    msg = f"Multi-Version installiert: {', '.join(installed)}."
    if failed:
        msg += f" Nicht geladen: {', '.join(failed)}."
    return True, msg
