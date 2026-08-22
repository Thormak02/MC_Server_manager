"""ViaProxy: Cross-Version-Uebersetzer als manager-verwalteter Java-Prozess VOR dem Hub.

Vanilla-/Plugin-Clients **jeder** Version -> ViaProxy (uebersetzt auf 1.21.1) ->
Hub-Vanilla-Port. So kommen gemischte Versionen in die eine 767-Welt und begegnen sich
den modded Spielern. **Modded (1.21.1) umgeht ViaProxy komplett** (Gateway leitet
modlobby direkt an den Hub) -> der NeoForge-Handshake wird nie angefasst.

Gated hinter ``viaproxy_enabled`` (Default AUS). Reconcile ist idempotent (Lifespan +
15s-Idle-Monitor) -> ein Settings-Toggle greift ohne App-Neustart. ViaProxy laeuft
headless: ``java -jar ViaProxy.jar config <viaproxy.yml>`` (Config-Keys aus der Doku).
"""

from __future__ import annotations

import json
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
_STATE: dict = {}   # zuletzt gestartete Config -> Idempotenz beim Reconcile
_DOWNLOAD_TRIED = False   # Auto-Download nur einmal pro Prozesslauf versuchen
_last_start_mono = 0.0     # Zeitpunkt des letzten Start-Versuchs (Crash-Erkennung + Backoff)
_crash_reported = False    # Crash-Grund nur EINMAL pro Absturz-Episode loggen
_jar_refetch_done = False  # falschen Auto-Jar nach Crash nur EINMAL neu laden (kein Loop)
_RETRY_AFTER_CRASH = 60.0  # nach sofortigem Absturz nicht alle 15s neu starten (kein Flapping)

_GH_LATEST = "https://api.github.com/repos/ViaVersion/ViaProxy/releases/latest"


def _glog(event: str, detail: str = "") -> None:
    try:
        from app.services import gateway_service

        gateway_service._glog(f"viaproxy.{event}", detail)
    except Exception:  # noqa: BLE001
        pass


def _work_dir() -> Path:
    from app.core.config import get_settings

    d = (get_settings().data_dir / "viaproxy").resolve()
    d.mkdir(parents=True, exist_ok=True)
    return d


def render_config(cfg: dict) -> str:
    """viaproxy.yml-Inhalt aus der Laufzeit-Config bauen (offline, keine Auth).

    Platzierung (Option A): ViaProxy ist eine INTERNE Uebersetzungsstufe VOR Dispatcher/Hub.
    - ``bind-address 127.0.0.1`` -> nur intern erreichbar (das Gateway leitet nicht-767-Joins
      hierher), nicht oeffentlich.
    - ``target-address`` = GATEWAY-Port (Loopback): der auf 767 uebersetzte Strom geht ZURUECK
      ins Gateway und wird dort normal geroutet (Hub/Dispatcher/Server).
    - ``rewrite-handshake-packet: false`` -> ViaProxy reicht den ORIGINAL-Handshake-Host
      unveraendert durch -> das Gateway-Host-Routing (modlobby-<id>/vanlobby/alias) ueberlebt.
    - ``rewrite-transfer-packets: false`` -> ViaProxy sitzt INTERN hinter dem Gateway; die
      Transfer-EMULATION (true) wuerde den Client an ViaProxys internen Eingang (= Apex)
      zurueckschicken -> Dispatcher -> Transfer -> ENDLOSSCHLEIFE. Mit false reicht ViaProxy
      das Transfer-Paket uebersetzt DURCH -> Clients >=1.20.5 folgen ihm nativ (auf
      vanlobby/<alias>.<domain> -> wieder ueber ViaProxy -> Ziel). Sehr alte Clients <1.20.5
      koennen (noch) nicht transferieren -> spaeterer Ausbau (ViaProxy als oeffentl. Eingang)."""
    return (
        f"bind-address: 127.0.0.1:{int(cfg['port'])}\n"
        f"target-address: 127.0.0.1:{int(cfg['target_port'])}\n"
        f"target-version: {cfg['target_version']}\n"
        "proxy-online-mode: false\n"
        "auth-method: NONE\n"
        "rewrite-handshake-packet: false\n"
        "rewrite-transfer-packets: false\n"
    )


def _write_config(cfg: dict) -> Path:
    yml = _work_dir() / "viaproxy.yml"
    yml.write_text(render_config(cfg), encoding="utf-8")
    return yml


def _default_jar_path() -> Path:
    return _work_dir() / "ViaProxy.jar"


def _java_major_version(java_bin: str) -> int | None:
    """Major-Java-Version des Binaries (z.B. 21, 8); None wenn nicht ermittelbar. Wichtig
    fuer die Build-Wahl: der +java8-Build bricht auf Java 17+ an Log4js Caller-Class-
    Erkennung (ExceptionInInitializerError). Java 17+ braucht den regulaeren Build."""
    import re

    try:
        out = subprocess.run([java_bin, "-version"], capture_output=True, text=True, timeout=10)
    except Exception:  # noqa: BLE001
        return None
    text = (out.stderr or "") + (out.stdout or "")   # 'java -version' schreibt auf stderr
    m = re.search(r'version "(\d+)(?:\.(\d+))?', text)
    if not m:
        return None
    major = int(m.group(1))
    if major == 1 and m.group(2):   # "1.8.0" -> 8
        major = int(m.group(2))
    return major


def _pick_jar_asset(candidates: list, java_major: int | None):
    """Passendes Release-Asset waehlen: Java 17+ (oder unbekannt) -> REGULAERER Build;
    Java <17 -> +java8-Build. Der +java8-Build crasht auf Java 17+ (Log4j)."""
    regular = [a for a in candidates if "java8" not in str(a.get("name", ""))]
    java8 = [a for a in candidates if "java8" in str(a.get("name", ""))]
    use_java8 = java_major is not None and java_major < 17
    pool = (java8 or regular) if use_java8 else (regular or java8)
    return pool[0] if pool else (candidates[0] if candidates else None)


def download_viaproxy_jar(dest: Path | None = None, java_bin: str = "java") -> Path | None:
    """Neueste ViaProxy-Release von GitHub nach ``dest`` laden (None bei Fehler). Waehlt den
    zur Java-Version passenden Build (Java 17+ -> regulaer, Java <17 -> +java8)."""
    dest = dest or _default_jar_path()
    try:
        req = urllib.request.Request(
            _GH_LATEST,
            headers={"User-Agent": "mc-server-manager", "Accept": "application/vnd.github+json"},
        )
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = json.load(resp)
        candidates = [
            a for a in data.get("assets", [])
            if str(a.get("name", "")).endswith(".jar")
            and "sources" not in str(a.get("name", ""))
            and "javadoc" not in str(a.get("name", ""))
        ]
        major = _java_major_version(java_bin)
        pick = _pick_jar_asset(candidates, major)
        _glog("pick", f"java={major} -> {pick.get('name') if pick else 'none'}")
        url = pick.get("browser_download_url") if pick else None
        if not url:
            _glog("download_no_asset", f"release={data.get('tag_name')}")
            return None
        tmp = dest.with_name(dest.name + ".part")
        req2 = urllib.request.Request(url, headers={"User-Agent": "mc-server-manager"})
        with urllib.request.urlopen(req2, timeout=180) as resp, open(tmp, "wb") as fh:
            shutil.copyfileobj(resp, fh)
        tmp.replace(dest)
        _glog("downloaded", f"{data.get('tag_name')} -> {dest.name}")
        return dest
    except Exception as exc:  # noqa: BLE001 - Netzwerk/GitHub-Fehler
        _glog("download_failed", repr(exc))
        return None


def _resolve_jar(cfg: dict) -> str | None:
    """Jar-Pfad ermitteln: Setting -> Standardordner -> einmalig Auto-Download."""
    global _DOWNLOAD_TRIED
    jar = (cfg.get("jar") or "").strip()
    if jar and Path(jar).is_file():
        return jar
    default = _default_jar_path()
    if default.is_file():
        return str(default)
    if not _DOWNLOAD_TRIED:
        _DOWNLOAD_TRIED = True
        got = download_viaproxy_jar(default, (cfg.get("java") or "java"))
        if got:
            return str(got)
    return None


def is_running() -> bool:
    with _LOCK:
        return _PROC is not None and _PROC.poll() is None


def start_viaproxy(cfg: dict) -> bool:
    """ViaProxy-Prozess starten (ohne Flag-Check). Idempotent: gleiche Config -> No-op."""
    global _PROC, _LOG, _STATE
    with _LOCK:
        if is_running() and _STATE == cfg:
            return True
        _stop_locked()
        jar = _resolve_jar(cfg)   # Setting -> Standardordner -> Auto-Download
        if not jar:
            _glog("jar_missing", "ViaProxy-Jar fehlt und Auto-Download fehlgeschlagen -> Cross-Version inaktiv")
            return False
        yml = _write_config(cfg)
        wd = _work_dir()
        java = cfg.get("java") or "java"
        try:
            _LOG = open(wd / "viaproxy.log", "ab")  # noqa: SIM115 - Handle lebt bis stop
            _PROC = subprocess.Popen(
                [java, "-jar", str(Path(jar).resolve()), "config", str(yml)],
                cwd=str(wd), stdout=_LOG, stderr=subprocess.STDOUT,
            )
        except OSError as exc:
            _glog("spawn_failed", repr(exc))
            _stop_locked()
            return False
        _STATE = dict(cfg)
        _glog("started",
              f"port={cfg['port']} -> 127.0.0.1:{cfg['target_port']} v{cfg['target_version']}")
        return True


def _stop_locked() -> None:
    global _PROC, _LOG, _STATE
    if _PROC is not None:
        try:
            _PROC.terminate()
            try:
                _PROC.wait(timeout=5)
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


def stop_viaproxy() -> None:
    with _LOCK:
        _stop_locked()


def _read_log_tail(lines: int = 20) -> str:
    """Letzte Zeilen von viaproxy.log (stdout+stderr) fuer die Crash-Diagnose im Audit-Log."""
    try:
        data = (_work_dir() / "viaproxy.log").read_text(
            encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return ""
    return " / ".join(ln.strip() for ln in data[-lines:] if ln.strip())[:900]


def _maybe_refetch_wrong_jar(cfg: dict) -> None:
    """Selbstheilung nach Crash: auf Java 17+ crasht der +java8-Build (Log4j). Den AUTO-
    geladenen Jar EINMAL verwerfen -> der naechste Reconcile laedt den passenden (regulaeren)
    Build nach. Nur wenn kein manueller Jar-Pfad gesetzt ist (den nicht anfassen)."""
    global _jar_refetch_done, _DOWNLOAD_TRIED, _last_start_mono
    if _jar_refetch_done or (cfg.get("jar") or "").strip():
        return
    major = _java_major_version(cfg.get("java") or "java")
    if not major or major < 17:
        return
    _jar_refetch_done = True
    try:
        _default_jar_path().unlink(missing_ok=True)
    except OSError:
        pass
    with _LOCK:
        _DOWNLOAD_TRIED = False   # Neu-Download erlauben
    _last_start_mono = 0.0        # naechsten Reconcile SOFORT neu versuchen (kein 60s-Backoff)
    _glog("jar_refetch",
          f"Crash auf Java {major} -> vermutlich falscher (+java8) Jar verworfen, laedt passenden Build nach")


def reconcile_viaproxy() -> None:
    """ViaProxy-Prozess an ``viaproxy_enabled`` + Config angleichen (selbstheilend).

    Mit Crash-Erkennung: stirbt der Prozess SOFORT (z.B. falscher Jar-Build / Config), wird
    der Grund (Ende von viaproxy.log) EINMAL in den Audit-Log gelegt, der falsche Auto-Jar
    ggf. verworfen (passenden Build nachladen), und danach nur gedrosselt neu gestartet."""
    global _last_start_mono, _crash_reported, _jar_refetch_done
    from app.services import app_setting_service as s

    cfg = s.get_viaproxy_config_runtime()
    if not cfg["enabled"]:
        if is_running():
            stop_viaproxy()
        _crash_reported = False
        _last_start_mono = 0.0
        _jar_refetch_done = False
        return

    if is_running():
        _crash_reported = False        # laeuft stabil
        _jar_refetch_done = False       # neuer Crash spaeter darf wieder 1x nachladen
        start_viaproxy(cfg)            # idempotent: gleiche Config -> No-op, sonst Neustart
        return

    now = time.monotonic()
    # Kuerzlich gestartet und schon wieder tot -> Absturz: Grund einmal melden, ggf. Jar
    # verwerfen, dann Backoff.
    if _last_start_mono and (now - _last_start_mono) < _RETRY_AFTER_CRASH:
        if not _crash_reported:
            _crash_reported = True
            tail = _read_log_tail()
            _glog("crashed",
                  f"ViaProxy sofort beendet. Log-Ende: {tail}" if tail
                  else "ViaProxy sofort beendet - keine Log-Ausgabe (Java/Jar/Args pruefen).")
            _maybe_refetch_wrong_jar(cfg)
        return
    # Erststart oder Backoff abgelaufen -> versuchen.
    if start_viaproxy(cfg):
        _last_start_mono = now
        _crash_reported = False
