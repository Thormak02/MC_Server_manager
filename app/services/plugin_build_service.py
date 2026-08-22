"""MCSMLobby selbst bauen: der Manager kompiliert das Lobby-Plugin auf dem Server.

Der Manager laeuft auf dem Server-PC, wo die (auto-installierten) JDKs liegen -> er kann
das Plugin **selbst** aus dem Quelltext (app/assets/lobby_plugin/src) kompilieren und als
Jar ablegen. So muss niemand manuell mit javac/Maven hantieren; ein Knopfdruck (oder die
Velocity-Einrichtung) baut die aktuelle Version.

Ablauf: JDK (javac) finden -> Compile-Abhaengigkeiten (paper-api, adventure, bungeecord-chat,
optional packetevents-api) in einen Cache laden -> javac --release 17 -> Ressourcen (plugin.yml,
config.yml) beilegen -> jar. Das Ergebnis landet in ``data/plugin-build/MCSMLobby.jar`` und wird
von ``lobby_service`` bevorzugt vor dem mitgelieferten Asset ausgeliefert.

Fehler (fehlendes JDK, Compile-Fehler) werden mit der javac-Ausgabe zurueckgegeben, damit man
sie im UI sieht und gezielt fixen kann.
"""

from __future__ import annotations

import shutil
import subprocess
import urllib.request
from pathlib import Path

# Compile-Classpath (nur zum Kompilieren, NICHT ins Jar). Versionen wie in
# app/assets/lobby_plugin/BUILD.md; Bukkit-API ist abwaerts stabil -> gegen 1.20.6
# kompiliertes Plugin laeuft auch auf Paper 26.x.
_PAPER = "https://repo.papermc.io/repository/maven-public"
_CENTRAL = "https://repo1.maven.org/maven2"
_COMPILE_DEPS: tuple[tuple[str, str], ...] = (
    ("paper-api.jar",
     f"{_PAPER}/io/papermc/paper/paper-api/1.20.6-R0.1-SNAPSHOT/paper-api-1.20.6-R0.1-20241030.191541-127.jar"),
    ("adventure-api.jar", f"{_CENTRAL}/net/kyori/adventure-api/4.17.0/adventure-api-4.17.0.jar"),
    ("adventure-key.jar", f"{_CENTRAL}/net/kyori/adventure-key/4.17.0/adventure-key-4.17.0.jar"),
    ("examination-api.jar", f"{_CENTRAL}/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar"),
    ("bungeecord-chat.jar",
     f"{_PAPER}/net/md-5/bungeecord-chat/1.20-R0.2-deprecated+build.18/bungeecord-chat-1.20-R0.2-deprecated+build.18.jar"),
)

# packetevents-api (nur noetig, sobald der Avatar-Code es importiert). Separat gehalten,
# damit die Build-Pipeline zuerst OHNE das riskante Avatar-Modul validiert werden kann.
_PACKETEVENTS_API = (
    "packetevents-api.jar",
    "https://repo.codemc.io/repository/maven-releases/com/github/retrooper/packetevents-api/2.9.6/packetevents-api-2.9.6.jar",
)


_PACKETEVENTS_GH = "https://api.github.com/repos/retrooper/packetevents/releases/latest"


def download_packetevents_runtime(plugins_dir: str | Path) -> tuple[bool, str]:
    """packetevents (Spigot-Plugin, Runtime-Abhaengigkeit fuer die Avatar-Bridge) in den
    plugins-Ordner der Lobby laden. Neueste GitHub-Release, Asset mit 'spigot' im Namen.
    Idempotent: ist schon eins da, nichts tun."""
    import json

    plugins = Path(plugins_dir).expanduser().resolve()
    plugins.mkdir(parents=True, exist_ok=True)
    if any(p.name.lower().startswith("packetevents") for p in plugins.glob("*.jar")):
        return True, "packetevents bereits vorhanden."
    try:
        from app.providers.server.common import USER_AGENT

        req = urllib.request.Request(_PACKETEVENTS_GH, headers={
            "User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        assets = [a for a in data.get("assets", []) if str(a.get("name", "")).endswith(".jar")]
        pick = next((a for a in assets if "spigot" in str(a.get("name", "")).lower()), None)
        pick = pick or (assets[0] if assets else None)
        url = pick.get("browser_download_url") if pick else None
        if not url:
            return False, "packetevents-Release ohne passendes Jar-Asset."
        _download(url, plugins / str(pick.get("name")))
        return True, f"packetevents geladen: {pick.get('name')}"
    except Exception as exc:  # noqa: BLE001
        return False, f"packetevents-Download fehlgeschlagen: {exc}"


def _plugin_src_dir() -> Path:
    return Path(__file__).resolve().parents[1] / "assets" / "lobby_plugin" / "src"


def _asset_jar() -> Path:
    return Path(__file__).resolve().parents[1] / "assets" / "lobby_plugin" / "MCSMLobby.jar"


def _build_dir() -> Path:
    from app.core.config import get_settings

    d = (get_settings().data_dir / "plugin-build").resolve()
    d.mkdir(parents=True, exist_ok=True)
    return d


def built_jar_path() -> Path:
    return _build_dir() / "MCSMLobby.jar"


def preferred_plugin_jar() -> Path:
    """Selbst gebautes Jar bevorzugen, sonst das mitgelieferte Asset."""
    built = built_jar_path()
    return built if built.is_file() else _asset_jar()


def _tool_from_java(java_bin: str, tool: str) -> str | None:
    """javac/jar neben der gefundenen java.exe ableiten."""
    p = Path(java_bin)
    cand = p.with_name(tool + p.suffix)   # java.exe -> javac.exe / jar.exe
    return str(cand) if cand.is_file() else None


def _download(url: str, dest: Path) -> None:
    from app.providers.server.common import USER_AGENT

    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    tmp = dest.with_name(dest.name + ".part")
    with urllib.request.urlopen(req, timeout=180) as resp, open(tmp, "wb") as fh:
        shutil.copyfileobj(resp, fh, length=1024 * 256)
    tmp.replace(dest)


def ensure_compile_libs(with_packetevents: bool = False) -> tuple[list[Path], list[str]]:
    """Compile-Abhaengigkeiten in den Cache laden (idempotent). (Pfade, Fehler)."""
    libs_dir = _build_dir() / "libs"
    libs_dir.mkdir(parents=True, exist_ok=True)
    deps = list(_COMPILE_DEPS)
    if with_packetevents:
        deps.append(_PACKETEVENTS_API)
    paths: list[Path] = []
    errors: list[str] = []
    for name, url in deps:
        dest = libs_dir / name
        if not dest.is_file():
            try:
                _download(url, dest)
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{name}: {exc}")
                continue
        paths.append(dest)
    return paths, errors


def build_lobby_plugin(db, *, with_packetevents: bool = True) -> tuple[bool, str]:
    """MCSMLobby aus dem Quelltext kompilieren und als Jar ablegen. (ok, Log/Meldung).

    Der Manager nutzt ein installiertes JDK (>=17). Kompiliert wird gegen die gecachten
    Bibliotheken; das fertige Jar landet in ``data/plugin-build/MCSMLobby.jar``.
    """
    from app.services import java_runtime_service

    java_bin = java_runtime_service.resolve_java_binary(db, 17)
    if not java_bin:
        return False, "Kein JDK (Java 17+) gefunden. Der Manager kann das Plugin nicht bauen."
    javac = _tool_from_java(java_bin, "javac")
    jar_tool = _tool_from_java(java_bin, "jar")
    if not javac or not jar_tool:
        return False, (
            "Gefundenes Java ist nur eine JRE (kein javac/jar). Bitte ein JDK installieren "
            "(Einstellungen -> Java) - der Manager kann dann das Plugin selbst bauen."
        )

    libs, dep_errors = ensure_compile_libs(with_packetevents=with_packetevents)
    if dep_errors and not libs:
        return False, "Compile-Abhaengigkeiten nicht ladbar: " + "; ".join(dep_errors)

    src_dir = _plugin_src_dir()
    java_files = sorted(str(p) for p in src_dir.glob("net/mcsm/lobby/*.java"))
    if not java_files:
        return False, f"Keine Java-Quellen unter {src_dir}."

    work = _build_dir() / "out"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True, exist_ok=True)

    classpath = ";".join(str(p) for p in libs)   # Windows-Classpath-Trenner
    cmd = [javac, "--release", "17", "-cp", classpath, "-d", str(work), *java_files]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    except Exception as exc:  # noqa: BLE001
        return False, f"javac-Aufruf fehlgeschlagen: {exc}"
    if proc.returncode != 0:
        tail = (proc.stderr or proc.stdout or "").strip()
        return False, "Compile-Fehler:\n" + tail[-4000:]

    # Ressourcen (plugin.yml, config.yml) beilegen.
    for res in ("plugin.yml", "config.yml"):
        srcf = src_dir / res
        if srcf.is_file():
            shutil.copy2(srcf, work / res)

    out_jar = built_jar_path()
    jar_cmd = [jar_tool, "cf", str(out_jar), "-C", str(work), "."]
    try:
        jproc = subprocess.run(jar_cmd, capture_output=True, text=True, timeout=120)
    except Exception as exc:  # noqa: BLE001
        return False, f"jar-Aufruf fehlgeschlagen: {exc}"
    if jproc.returncode != 0:
        return False, "jar-Fehler:\n" + (jproc.stderr or jproc.stdout or "").strip()[-2000:]

    warn = f" (Hinweis: {'; '.join(dep_errors)})" if dep_errors else ""
    return True, f"MCSMLobby gebaut: {out_jar.name} aus {len(java_files)} Quelldatei(en).{warn}"
