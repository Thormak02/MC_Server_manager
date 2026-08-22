"""Velocity: echter Proxy als oeffentlicher Eingang (Cross-Version-Lobby-Netzwerk).

Der Manager verwaltet Velocity als externen Java-Prozess VOR allen Servern - analog zu
``viaproxy_service`` (Popen + idempotenter Reconcile + Crash-Backoff), nur maechtiger:

- Velocity ist ein **INTERNES Backend hinter dem Gateway**: es bindet nur
  ``127.0.0.1:velocity_internal_port`` (loopback, NICHT oeffentlich). Der oeffentliche
  Eingang bleibt das Gateway (network_port); es routet Vanilla-Clients hierher.
- Mit ViaVersion + ViaBackwards + ViaRewind als **Velocity-Plugins** landet **jede**
  Vanilla-Version (1.7.10 .. 26.2) in der EINEN neuesten Lobby: der Proxy uebersetzt
  abwaerts (die reife Via-Richtung). 26.2-Clients nativ, aeltere per Backwards/Rewind.
- Backends (Lobby + Vanilla-Paper-Server) laufen hinter Velocity mit **Modern
  Forwarding** (Loopback, online-mode=false + Secret). **Modded-Server sind KEINE
  Velocity-Backends** - modded Clients laufen ueber den Dispatcher in den Python-Hub.

Gated hinter ``network_mode == "velocity"`` (UNIVERSAL-Modus). Das Gateway laeuft dabei
WEITER (Eingang); Velocity ist nur das interne Vanilla-Backend. Reconcile laeuft
idempotent im Lifespan + 15s-Idle-Monitor -> ein Moduswechsel greift ohne App-Neustart.
"""

from __future__ import annotations

import shutil
import subprocess
import time
import urllib.request
from pathlib import Path
from threading import RLock
from typing import Optional

_LOCK = RLock()
_PROC: "Optional[subprocess.Popen]" = None
_LOG: "Optional[object]" = None
_STATE: dict = {}          # zuletzt gestartete Signatur -> Idempotenz beim Reconcile
_DOWNLOAD_TRIED = False     # Auto-Download der Velocity-Jar nur einmal pro Prozesslauf
_last_start_mono = 0.0      # Zeitpunkt des letzten Start-Versuchs (Crash-Erkennung + Backoff)
_crash_reported = False     # Crash-Grund nur EINMAL pro Absturz-Episode loggen
_RETRY_AFTER_CRASH = 60.0   # nach sofortigem Absturz nicht alle 15s neu starten (kein Flapping)

_VELOCITY_API = "https://fill.papermc.io/v3/projects/velocity"
# Servertypen, die als Velocity-Backend taugen (Modern Forwarding = nur Paper-basiert).
# Alles andere (vanilla/forge/neoforge/fabric/quilt) ist ein nativer Transfer-Ziel-Server.
_BACKEND_TYPES = {"paper", "purpur", "spigot", "bukkit", "folia"}
_READ_TIMEOUT_MS = 185000   # >= Sleep-Wake-Timeout (180 s), damit kalt startende Backends hochkommen


def _glog(event: str, detail: str = "") -> None:
    try:
        from app.services import gateway_service

        gateway_service._glog(f"velocity.{event}", detail)
    except Exception:  # noqa: BLE001
        pass


def _work_dir() -> Path:
    from app.core.config import get_settings

    d = (get_settings().data_dir / "velocity").resolve()
    (d / "plugins").mkdir(parents=True, exist_ok=True)
    return d


def _default_jar_path() -> Path:
    return _work_dir() / "velocity.jar"


# --- Velocity-Download (fill-API, wie PaperProvider) ---------------------------
def _resolve_velocity_download(version: str = "") -> tuple[str, str]:
    """(URL, Dateiname) des besten Velocity-Builds. ``version`` leer = neueste stabile."""
    from app.providers.server.common import fetch_json

    data = fetch_json(f"{_VELOCITY_API}/versions")
    entries = data.get("versions") if isinstance(data, dict) else data
    entries = entries if isinstance(entries, list) else []

    ver = (version or "").strip()
    if not ver:
        for entry in entries:  # neueste zuerst
            vid = str(((entry or {}).get("version") or {}).get("id") or "") if isinstance(entry, dict) else ""
            if vid and not any(t in vid.lower() for t in ("-rc", "-pre", "snapshot", "-exp")):
                ver = vid
                break
        if not ver and entries:
            ver = str(((entries[0] or {}).get("version") or {}).get("id") or "")
    if not ver:
        raise ValueError("Keine Velocity-Version ueber die fill-API gefunden.")

    builds = fetch_json(f"{_VELOCITY_API}/versions/{ver}/builds")
    blist = builds if isinstance(builds, list) else ((builds.get("builds") if isinstance(builds, dict) else None) or [])
    if not blist:
        raise ValueError(f"Keine Velocity-Builds fuer {ver} gefunden.")
    stable = [b for b in blist if str(b.get("channel") or "").upper() == "STABLE"]

    def _bid(b: dict) -> int:
        try:
            return int(b.get("id") or 0)
        except (TypeError, ValueError):
            return 0

    build = max(stable or blist, key=_bid)
    downloads = build.get("downloads") or {}
    dl = downloads.get("server:default") or (next(iter(downloads.values())) if downloads else {})
    url = str((dl or {}).get("url") or "")
    name = str((dl or {}).get("name") or "")
    if not url or not name:
        raise ValueError(f"Velocity-Build {ver} ohne Download-Link.")
    return url, name


def download_velocity_jar(dest: Path | None = None, version: str = "") -> Path | None:
    dest = dest or _default_jar_path()
    try:
        url, _name = _resolve_velocity_download(version)
        from app.providers.server.common import USER_AGENT

        tmp = dest.with_name(dest.name + ".part")
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=300) as resp, open(tmp, "wb") as fh:
            shutil.copyfileobj(resp, fh, length=1024 * 256)
        tmp.replace(dest)
        _glog("downloaded", f"{url.rsplit('/', 1)[-1]} -> {dest.name}")
        return dest
    except Exception as exc:  # noqa: BLE001
        _glog("download_failed", repr(exc))
        return None


def _resolve_jar(cfg: dict) -> str | None:
    """Jar-Pfad: Setting -> Standardordner -> einmalig Auto-Download."""
    global _DOWNLOAD_TRIED
    jar = (cfg.get("jar") or "").strip()
    if jar and Path(jar).is_file():
        return jar
    default = _default_jar_path()
    if default.is_file():
        return str(default)
    if not _DOWNLOAD_TRIED:
        _DOWNLOAD_TRIED = True
        got = download_velocity_jar(default, cfg.get("version") or "")
        if got:
            return str(got)
    return None


# --- Konfiguration (velocity.toml + forwarding.secret + Via-Plugins) -----------
def _lobby_mc_version(db) -> str:
    """MC-Version der Lobby (fuer die passenden Via-Plugin-Builds)."""
    from sqlalchemy import select

    from app.models.server import Server as _Server

    lobby = db.scalar(select(_Server).where(_Server.gateway_is_default.is_(True)))
    return str(getattr(lobby, "mc_version", "") or "") if lobby else ""


def velocity_backends(db) -> tuple[list[dict], str | None]:
    """Velocity-Backends (Lobby + Vanilla-Paper-Server) + Name der Default-Lobby.

    Backend = gateway_enabled Server eines Bukkit-Typs (Modern Forwarding faehig).
    Adresse = ``127.0.0.1:<port>`` (Backend bindet loopback, nur ueber Velocity
    erreichbar). Modded-/Vanilla-Server sind KEINE Backends (nativer Transfer).
    """
    from sqlalchemy import select

    from app.models.server import Server as _Server
    from app.services import gateway_service

    backends: list[dict] = []
    lobby_name: str | None = None
    rows = db.scalars(select(_Server).where(_Server.gateway_enabled.is_(True))).all()
    used: set[str] = set()
    for srv in rows:
        if str(srv.server_type or "").lower() not in _BACKEND_TYPES:
            continue
        if not srv.port:
            continue
        alias = gateway_service.clean_hostname(srv.gateway_hostname) or f"srv{srv.id}"
        name = alias
        suffix = 2
        while name in used:   # eindeutige Backend-Namen erzwingen
            name = f"{alias}{suffix}"
            suffix += 1
        used.add(name)
        backends.append({
            "name": name,
            "address": f"127.0.0.1:{int(srv.port)}",
            "alias": alias,
            "is_lobby": bool(srv.gateway_is_default),
        })
        if srv.gateway_is_default:
            lobby_name = name
    if lobby_name is None and backends:
        lobby_name = backends[0]["name"]
    return backends, lobby_name


def _toml_escape(value: str) -> str:
    """Fuer einen TOML-Basic-String escapen. Steuerzeichen (Newline/Tab/CR) sind in
    Basic-Strings VERBOTEN -> escapen, sonst ist die velocity.toml ungueltig und
    Velocity startet nicht (z.B. mehrzeilige MOTD)."""
    out = str(value).replace("\\", "\\\\").replace('"', '\\"')
    out = out.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    # Restliche Steuerzeichen (<0x20) als \uXXXX kodieren.
    return "".join(ch if ch >= " " or ch in "\\\"" else f"\\u{ord(ch):04X}" for ch in out)


def render_velocity_toml(cfg: dict, backends: list[dict], lobby_name: str | None) -> str:
    """velocity.toml aus Backends + Laufzeit-Config bauen (Modern Forwarding)."""
    bind_port = int(cfg["bind_port"])
    domain = (cfg.get("domain") or "").strip()
    motd = _toml_escape(cfg.get("motd") or "Willkommen im Netzwerk")

    lines: list[str] = []
    lines.append('config-version = "2.7"')
    # Loopback-Bind: Velocity ist ein INTERNES Backend hinter dem Gateway (oeffentlich ist
    # nur der Gateway-Port). Kein oeffentlicher Bind -> kein Umgehen des Routers.
    lines.append(f'bind = "127.0.0.1:{bind_port}"')
    lines.append(f'motd = "{motd}"')
    lines.append(f'show-max-players = {int(cfg.get("max_players") or 100)}')
    lines.append("online-mode = true")
    lines.append("force-key-authentication = true")
    lines.append("prevent-client-proxy-connections = false")
    lines.append('player-info-forwarding-mode = "modern"')
    lines.append('forwarding-secret-file = "forwarding.secret"')
    lines.append("announce-forge = false")
    lines.append("kick-existing-players = false")
    lines.append('ping-passthrough = "DISABLED"')
    lines.append("")
    lines.append("[servers]")
    for be in backends:
        # Namen als QUOTED key: ein Alias mit Punkt (z.B. "1.21.1") wuerde als bare key
        # zu verschachtelten TOML-Tabellen zerfallen und das Backend nie definieren.
        lines.append(f'"{_toml_escape(be["name"])}" = "{be["address"]}"')
    try_list = f'"{_toml_escape(lobby_name)}"' if lobby_name else ""
    lines.append(f"try = [{try_list}]")
    lines.append("")
    lines.append("[forced-hosts]")
    if domain:
        for be in backends:
            lines.append(f'"{_toml_escape(be["alias"])}.{_toml_escape(domain)}" = ["{be["name"]}"]')
    lines.append("")
    lines.append("[advanced]")
    lines.append(f"read-timeout = {_READ_TIMEOUT_MS}")
    lines.append("")
    lines.append("[query]")
    lines.append("enabled = false")
    lines.append("")
    return "\n".join(lines)


def _write_runtime_config(cfg: dict) -> tuple[str, list[str], str]:
    """velocity.toml + forwarding.secret schreiben (KEIN Netzwerk).

    Rueckgabe: (Signatur, Backend-Namen, Lobby-MC-Version). Die Signatur dient der
    Idempotenz - der Via-Plugin-Download (Netzwerk) passiert NUR beim tatsaechlichen
    (Neu-)Start, nicht bei jedem 15s-Reconcile."""
    from app.db.session import SessionLocal

    wd = _work_dir()
    # forwarding.secret schreiben (der Reconcile hat es via ensure_ schon erzeugt).
    secret = (cfg.get("secret") or "").strip()
    if secret:
        (wd / "forwarding.secret").write_text(secret, encoding="utf-8")

    with SessionLocal() as db:
        backends, lobby_name = velocity_backends(db)
        mc_version = _lobby_mc_version(db)
    toml_text = render_velocity_toml(cfg, backends, lobby_name)
    (wd / "velocity.toml").write_text(toml_text, encoding="utf-8")

    signature = f"{toml_text}||secret={secret}"
    return signature, [be["name"] for be in backends], mc_version


def _install_via_plugins(mc_version: str) -> None:
    """Via-Plugins (Velocity-Builds) passend zur Lobby-Version laden (best-effort,
    nur beim tatsaechlichen Start aufrufen - Netzwerk)."""
    try:
        from app.providers.server.common import offline_mode_enabled
        from app.services import viaversion_service

        if mc_version and not offline_mode_enabled():
            viaversion_service.install_velocity_plugins(_work_dir() / "plugins", mc_version)
    except Exception:  # noqa: BLE001
        pass


def is_running() -> bool:
    # LOCK-FREI: nur das Prozess-Handle lesen + pollen (beides guenstig/atomar in CPython).
    # So blockiert weder der 15s-Idle-Monitor noch die /settings-Seite, waehrend ein
    # (Neu-)Start gerade Downloads erledigt.
    p = _PROC
    return p is not None and p.poll() is None


def start_velocity(cfg: dict) -> bool:
    """Velocity-Prozess starten. Idempotent: gleiche Config-Signatur -> No-op.

    WICHTIG: die potenziell langen NETZWERK-Schritte (Velocity-Jar-Download, Java-Resolve/
    -Autoinstall, Via-Plugin-Download) laufen BEWUSST OHNE ``_LOCK``. Der Lock wird nur fuer
    den schnellen Prozess-Tausch (terminate/Popen) gehalten -> der Idle-Monitor und
    HTTP-Anfragen (is_running) bleiben responsiv, auch waehrend Minuten dauernder Downloads.
    """
    global _PROC, _LOG, _STATE

    # --- 1) Alles Langsame ausserhalb des Locks (Download/Install/Config schreiben) ---
    jar = _resolve_jar(cfg)                          # ggf. Velocity-Jar-Download
    if not jar:
        _glog("jar_missing", "Velocity-Jar fehlt und Auto-Download fehlgeschlagen.")
        return False

    from app.db.session import SessionLocal
    from app.services import java_runtime_service

    with SessionLocal() as db:
        java = java_runtime_service.resolve_java_binary(db, 17)   # ggf. JDK-Autoinstall
    if not java:
        _glog("java_missing", "Kein Java 17+ fuer Velocity verfuegbar (Auto-Install fehlgeschlagen).")
        return False

    signature, backend_names, mc_version = _write_runtime_config(cfg)   # nur Dateien, kein Netz
    desired = {"jar": jar, "java": java, "sig": signature}
    if is_running() and _STATE == desired:
        return True   # unveraendert -> KEIN Neustart, KEIN Plugin-Download

    _install_via_plugins(mc_version)                 # Via-Plugin-Download (nur bei Start)

    # --- 2) Nur der schnelle Prozess-Tausch unter dem Lock ---
    with _LOCK:
        if is_running() and _STATE == desired:
            return True                              # ein Parallel-Reconcile war schneller
        _stop_locked()
        wd = _work_dir()
        try:
            _LOG = open(wd / "velocity.log", "ab")  # noqa: SIM115 - Handle lebt bis stop
            _PROC = subprocess.Popen(
                [java, "-jar", str(Path(jar).resolve())],
                cwd=str(wd), stdout=_LOG, stderr=subprocess.STDOUT,
                stdin=subprocess.PIPE,
            )
        except OSError as exc:
            _glog("spawn_failed", repr(exc))
            _stop_locked()
            return False
        _STATE = desired
    _glog("started", f"port={cfg['bind_port']} backends={','.join(backend_names) or '-'}")
    return True


def _stop_locked() -> None:
    global _PROC, _LOG, _STATE
    if _PROC is not None:
        try:
            _PROC.terminate()
            try:
                _PROC.wait(timeout=8)
            except Exception:  # noqa: BLE001
                _PROC.kill()
        except Exception:  # noqa: BLE001
            pass
        _glog("stopped")
    if _LOG is not None:
        try:
            _LOG.close()
        except Exception:  # noqa: BLE001
            pass
    _PROC = None
    _LOG = None
    _STATE = {}


def stop_velocity() -> None:
    with _LOCK:
        _stop_locked()


def reconcile_velocity_async() -> None:
    """reconcile_velocity in einem Hintergrund-Thread anstossen.

    Fuer Aufrufer im HTTP-Request-Thread (Setup-Button / Settings-Form): der erste
    (Neu-)Start laedt ggf. Velocity-Jar + Java + Via-Plugins (Minuten) - das darf die
    Antwort nicht blockieren. Der Idle-Monitor haelt es danach selbstheilend am Leben."""
    import threading

    threading.Thread(target=reconcile_velocity, name="velocity-reconcile", daemon=True).start()


def _read_log_tail(lines: int = 20) -> str:
    try:
        data = (_work_dir() / "velocity.log").read_text(
            encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return ""
    return " / ".join(ln.strip() for ln in data[-lines:] if ln.strip())[:900]


def log_tail(lines: int = 80) -> str:
    """Rohes Ende von velocity.log (mehrzeilig) fuer die Diagnose-Anzeige im UI.

    Zeigt u.a. welche ViaVersion wirklich laedt und bis zu welcher Protokoll-/MC-Version
    der Proxy Clients akzeptiert ("... range 1.7.2-26.2" bzw. ViaVersion-Ladezeile)."""
    try:
        data = (_work_dir() / "velocity.log").read_text(
            encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return ""
    tail = [ln.rstrip() for ln in data[-lines:]]
    return "\n".join(tail)[-6000:]


def installed_via_plugins() -> list[str]:
    """Jar-Namen der aktuell im Velocity-plugins-Ordner liegenden Via-Plugins (Diagnose).

    So sieht man im UI SCHWARZ AUF WEISS, welche ViaVersion/ViaBackwards/ViaRewind-Version
    der Proxy tatsaechlich geladen hat - statt aus dem Quelltext zu raten."""
    try:
        plugins = _work_dir() / "plugins"
        return sorted(p.name for p in plugins.glob("*.jar"))
    except OSError:
        return []


def installed_velocity_version() -> str:
    """Zuletzt heruntergeladene Velocity-Version (aus .velocity-version), leer wenn unbekannt."""
    try:
        return (_work_dir() / ".velocity-version").read_text(encoding="utf-8").strip()
    except OSError:
        return ""


def restart_velocity() -> None:
    """Velocity ZWINGEND neu starten und dabei die NEUESTEN Via-Plugins frisch ziehen.

    Wichtig, weil ein Neustart der Paper-Lobby (Server #7) den SEPARATEN Velocity-Proxy
    NICHT neu startet - der Proxy laeuft sonst mit den Via-Jars weiter, die er beim ersten
    Start geladen hat (u.U. veraltet). Diese Funktion stoppt den Proxy, setzt den Crash-
    Backoff zurueck und laesst reconcile ihn frisch (inkl. Via-Neuinstallation) hochziehen.
    """
    global _last_start_mono, _crash_reported
    stop_velocity()            # setzt _STATE zurueck -> naechster start ist ein echter Neustart
    _last_start_mono = 0.0     # Backoff zuruecksetzen -> sofortiger Neustart erlaubt
    _crash_reported = False
    reconcile_velocity()       # nicht laufend -> start_velocity() -> _install_via_plugins()


def restart_velocity_async() -> None:
    """restart_velocity im Hintergrund (Via-Download + Java/Jar-Resolve dauern)."""
    import threading

    threading.Thread(target=restart_velocity, name="velocity-restart", daemon=True).start()


def reconcile_velocity() -> None:
    """Velocity-Prozess an ``network_mode == velocity`` + Config angleichen (selbstheilend).

    Crash-Erkennung wie beim ViaProxy: stirbt der Prozess sofort, wird der Grund
    (Ende von velocity.log) EINMAL in den Audit-Log gelegt und danach nur gedrosselt
    neu gestartet (kein Flapping)."""
    global _last_start_mono, _crash_reported
    from app.services import app_setting_service as s

    cfg = s.get_velocity_config_runtime()
    if not cfg["enabled"]:
        if is_running():
            stop_velocity()
        _crash_reported = False
        _last_start_mono = 0.0
        return

    # Velocity ist jetzt ein INTERNES Backend (loopback:velocity_internal_port) HINTER dem
    # Gateway - kein Kampf mehr um den network_port, das Gateway bleibt der Eingang.
    if is_running():
        _crash_reported = False
        start_velocity(cfg)   # idempotent: gleiche Signatur -> No-op, sonst Neustart
        return

    now = time.monotonic()
    if _last_start_mono and (now - _last_start_mono) < _RETRY_AFTER_CRASH:
        if not _crash_reported:
            _crash_reported = True
            tail = _read_log_tail()
            _glog("crashed",
                  f"Velocity sofort beendet/nicht gestartet. Log-Ende: {tail}" if tail
                  else "Velocity nicht gestartet - Java/Jar/Config pruefen (keine Log-Ausgabe).")
        return
    # Start VERSUCHEN und den Zeitpunkt IMMER merken (auch bei Fehlschlag) -> der 60s-
    # Backoff greift auch, wenn Jar/Java fehlen (kein Download-Hammern alle 15s).
    ok = start_velocity(cfg)
    _last_start_mono = now
    _crash_reported = False if ok else _crash_reported
