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
import urllib.request
from pathlib import Path
from threading import RLock
from typing import Optional

_LOCK = RLock()
_PROC: "Optional[subprocess.Popen]" = None
_LOG: "Optional[object]" = None
_STATE: dict = {}   # zuletzt gestartete Config -> Idempotenz beim Reconcile
_DOWNLOAD_TRIED = False   # Auto-Download nur einmal pro Prozesslauf versuchen

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
    - ``rewrite-transfer-packets: true`` -> ViaProxy faengt Transfer-Pakete ab und bleibt in
      der Uebersetzungsschleife (emuliert Transfer auch fuer Clients < 1.20.5)."""
    return (
        f"bind-address: 127.0.0.1:{int(cfg['port'])}\n"
        f"target-address: 127.0.0.1:{int(cfg['target_port'])}\n"
        f"target-version: {cfg['target_version']}\n"
        "proxy-online-mode: false\n"
        "auth-method: NONE\n"
        "rewrite-handshake-packet: false\n"
        "rewrite-transfer-packets: true\n"
    )


def _write_config(cfg: dict) -> Path:
    yml = _work_dir() / "viaproxy.yml"
    yml.write_text(render_config(cfg), encoding="utf-8")
    return yml


def _default_jar_path() -> Path:
    return _work_dir() / "ViaProxy.jar"


def download_viaproxy_jar(dest: Path | None = None) -> Path | None:
    """Neueste ViaProxy-Release von GitHub nach ``dest`` laden (None bei Fehler)."""
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
        # +java8-Build bevorzugen: laeuft auf JEDEM Java (auch dem 21 der 1.21.1-Server).
        pick = next((a for a in candidates if "java8" in str(a.get("name", ""))), None)
        pick = pick or (candidates[0] if candidates else None)
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
        got = download_viaproxy_jar(default)
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


def reconcile_viaproxy() -> None:
    """ViaProxy-Prozess an ``viaproxy_enabled`` + Config angleichen (selbstheilend)."""
    from app.services import app_setting_service as s

    cfg = s.get_viaproxy_config_runtime()
    if not cfg["enabled"]:
        if is_running():
            stop_viaproxy()
        return
    start_viaproxy(cfg)
