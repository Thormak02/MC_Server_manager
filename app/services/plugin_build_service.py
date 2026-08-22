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
import threading
import time
import urllib.request
from pathlib import Path

# Letztes Build-Ergebnis (fuers UI + Audit). Der Build laeuft asynchron -> der Klick
# blockiert nicht, das Ergebnis erscheint hier + im Audit-Log.
_LAST_BUILD: dict = {"ok": None, "msg": "", "at": 0.0}
_BUILD_RUNNING = threading.Lock()

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
    "https://repo.codemc.io/repository/maven-releases/com/github/retrooper/packetevents-api/2.13.0/packetevents-api-2.13.0.jar",
)


_MODRINTH = "https://api.modrinth.com/v2"


def download_packetevents_runtime(plugins_dir: str | Path) -> tuple[bool, str]:
    """packetevents (eigenstaendiges Spigot/Paper-Plugin, Runtime fuer die Avatar-Bridge) in
    den plugins-Ordner laden. Neueste Spigot-Version von Modrinth. Idempotent."""
    import json
    import urllib.parse

    plugins = Path(plugins_dir).expanduser().resolve()
    plugins.mkdir(parents=True, exist_ok=True)
    if any(p.name.lower().startswith("packetevents") for p in plugins.glob("*.jar")):
        return True, "packetevents bereits vorhanden."
    try:
        from app.providers.server.common import download_file, fetch_json

        params = urllib.parse.urlencode(
            {"loaders": json.dumps(["spigot", "paper", "bukkit", "purpur", "folia"])})
        versions = fetch_json(f"{_MODRINTH}/project/packetevents/version?{params}")
        if not isinstance(versions, list) or not versions:
            return False, "Kein packetevents-Spigot-Build auf Modrinth gefunden."
        versions.sort(key=lambda v: str(v.get("date_published", "")), reverse=True)
        for v in versions:
            files = v.get("files") or []
            primary = next((f for f in files if f.get("primary")), files[0] if files else None)
            if primary and "sources" not in str(primary.get("filename", "")):
                download_file(primary["url"], plugins / str(primary["filename"]))
                return True, f"packetevents geladen: {primary['filename']}"
        return False, "packetevents-Build ohne primaeres Jar."
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


def last_build_status() -> dict:
    """Letztes Build-Ergebnis {ok, msg, at} (fuer die Settings-Anzeige)."""
    return dict(_LAST_BUILD)


def is_building() -> bool:
    return _BUILD_RUNNING.locked()


def _record_build(ok: bool, msg: str) -> None:
    global _LAST_BUILD
    _LAST_BUILD = {"ok": ok, "msg": msg, "at": time.time()}
    try:
        (_build_dir() / "build.log").write_text(msg, encoding="utf-8")
    except OSError:
        pass


def run_build_async(user_id: int | None = None) -> bool:
    """Build im Hintergrund starten (blockiert den HTTP-Request nicht). Bei Erfolg wird das
    Jar gleich auf die Lobby-Server verteilt. Ergebnis -> last_build_status() + Audit-Log.
    Gibt False zurueck, wenn schon ein Build laeuft."""
    if not _BUILD_RUNNING.acquire(blocking=False):
        return False

    def _run() -> None:
        from app.db.session import SessionLocal

        try:
            with SessionLocal() as db:
                ok, msg = build_lobby_plugin(db)
                _record_build(ok, msg)
                if ok:
                    try:
                        from app.services import lobby_service

                        lobby_service.sync_lobby_plugin(db)
                    except Exception:  # noqa: BLE001
                        pass
                try:
                    from app.services import audit_service

                    audit_service.log_action(
                        db, action="plugin.build", user_id=user_id,
                        details=("ok: " if ok else "FEHLER: ") + msg[:600])
                except Exception:  # noqa: BLE001
                    pass
        except Exception as exc:  # noqa: BLE001
            _record_build(False, f"Build-Thread-Fehler: {exc}")
        finally:
            _BUILD_RUNNING.release()

    threading.Thread(target=_run, daemon=True, name="plugin-build").start()
    return True
