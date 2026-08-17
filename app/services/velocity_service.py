"""Velocity-Proxy: Provisionierung, Konfiguration und Prozess-Lifecycle.

Phase 1 des Lobby-Netzwerks (siehe docs/velocity_network_plan.md):
- Velocity-Jar ueber die PaperMC-Fill-API (Projekt "velocity") laden.
- ``velocity.toml`` + ``forwarding.secret`` erzeugen.
- Velocity als verwalteten Prozess starten/stoppen (oeffentliche Eingangstuer,
  sobald Netzwerk-Modus = "velocity").

Die Anbindung der Backend-Server (Forwarding, [servers]-Tabelle, Sleep) folgt in
Phase 2/4. In Phase 1 startet Velocity mit leerer Server-Liste.
"""

from __future__ import annotations

import secrets
import subprocess
from pathlib import Path
from threading import RLock, Thread

from app.core.config import get_settings
from app.providers.server.common import download_file, fetch_json

_FILL_BASE = "https://fill.papermc.io/v3/projects/velocity"

_LOCK = RLock()
_process: subprocess.Popen[str] | None = None
_starting = False
_shutdown = False  # gesetzt beim App-Shutdown -> laufende Provisionierung startet nicht mehr
_last_status = ""  # letzte Start-/Fehlermeldung (fuer Status/Diagnose sichtbar)
_log_tail: list[str] = []
_LOG_TAIL_MAX = 400

# Standardmaessig die neueste STABLE-Version mit Major <= diesem Wert waehlen.
# Die generierte velocity.toml (config-version 2.7) passt zum 3.x-Format; 4.x
# nutzt ein anderes Config-Schema und muss bewusst gesetzt werden.
_DEFAULT_MAX_MAJOR = 3


# --------------------------------------------------------------------------- #
# Pfade
# --------------------------------------------------------------------------- #
def managed_velocity_dir() -> Path:
    return (get_settings().data_dir / "velocity").resolve()


def _jar_path() -> Path:
    return managed_velocity_dir() / "velocity.jar"


def _plugins_dir() -> Path:
    return managed_velocity_dir() / "plugins"


def _toml_path() -> Path:
    return managed_velocity_dir() / "velocity.toml"


def _secret_path() -> Path:
    return managed_velocity_dir() / "forwarding.secret"


def _log_path() -> Path:
    return managed_velocity_dir() / "velocity-manager.log"


# --------------------------------------------------------------------------- #
# Versionen / Download (Fill-API, gleiche Struktur wie Paper)
# --------------------------------------------------------------------------- #
def _fetch_version_entries() -> list[dict]:
    data = fetch_json(f"{_FILL_BASE}/versions")
    entries = data.get("versions") if isinstance(data, dict) else data
    return entries if isinstance(entries, list) else []


def _is_stable_id(version_id: str) -> bool:
    low = version_id.lower()
    return not any(tag in low for tag in ("-snapshot", "-rc", "-pre", "-exp"))


def list_velocity_versions(*, include_snapshots: bool = False) -> list[str]:
    result: list[str] = []
    try:
        for entry in _fetch_version_entries():  # neueste zuerst
            version = entry.get("version") if isinstance(entry, dict) else None
            if not isinstance(version, dict):
                continue
            vid = str(version.get("id") or "")
            if not vid:
                continue
            if not include_snapshots and not _is_stable_id(vid):
                continue
            result.append(vid)
    except Exception:  # noqa: BLE001
        return result
    return result


def latest_stable_version() -> str | None:
    versions = list_velocity_versions()
    return versions[0] if versions else None


def _major_of(version_id: str) -> int:
    try:
        return int(version_id.split(".", 1)[0])
    except (ValueError, IndexError):
        return 0


def default_velocity_version() -> str | None:
    """Auto-Default: neueste STABLE mit Major <= _DEFAULT_MAX_MAJOR (bekanntes
    Config-Format), sonst die neueste stabile insgesamt."""
    versions = list_velocity_versions()
    if not versions:
        return None
    known = [v for v in versions if _major_of(v) <= _DEFAULT_MAX_MAJOR]
    return known[0] if known else versions[0]


def _version_entry(version_id: str) -> dict | None:
    for entry in _fetch_version_entries():
        version = entry.get("version") if isinstance(entry, dict) else None
        if isinstance(version, dict) and str(version.get("id") or "") == version_id:
            return entry
    return None


def required_java_for_version(version_id: str) -> int:
    """Minimale Java-Major aus der Fill-API (Fallback 17)."""
    try:
        entry = _version_entry(version_id)
        version = (entry or {}).get("version") if isinstance(entry, dict) else None
        java = (version or {}).get("java") if isinstance(version, dict) else None
        minimum = ((java or {}).get("version") or {}).get("minimum")
        if minimum:
            return int(minimum)
    except Exception:  # noqa: BLE001
        pass
    return 17


def _fetch_builds(version_id: str) -> list[dict]:
    data = fetch_json(f"{_FILL_BASE}/versions/{version_id}/builds")
    if isinstance(data, list):
        return data
    builds = data.get("builds") if isinstance(data, dict) else None
    return builds if isinstance(builds, list) else []


def _build_id(build: dict) -> int:
    try:
        return int(build.get("id") or 0)
    except (TypeError, ValueError):
        return 0


def _resolve_download(version_id: str) -> tuple[str, str]:
    builds = _fetch_builds(version_id)
    if not builds:
        raise ValueError(f"Keine Velocity-Builds fuer {version_id} gefunden.")
    stable = [b for b in builds if str(b.get("channel") or "").upper() == "STABLE"]
    build = max(stable or builds, key=_build_id)
    download = (build.get("downloads") or {}).get("server:default") or {}
    url = str(download.get("url") or "")
    name = str(download.get("name") or "")
    if not url:
        raise ValueError(f"Velocity {version_id}: kein Download-Link gefunden.")
    return url, name or f"velocity-{version_id}.jar"


# --------------------------------------------------------------------------- #
# Konfiguration
# --------------------------------------------------------------------------- #
def ensure_forwarding_secret() -> str:
    """Erzeugt (einmalig) ein Forwarding-Secret und gibt es zurueck."""
    path = _secret_path()
    if path.exists():
        existing = path.read_text(encoding="utf-8").strip()
        if existing:
            return existing
    secret = secrets.token_urlsafe(24)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(secret, encoding="utf-8")
    return secret


# Server-Typen mit nativem "Modern Forwarding" (Paper-Familie).
PAPER_FORKS = {"paper", "purpur", "pufferfish", "folia"}


def _velocity_members(db) -> list[dict]:
    """Netzwerk-Backends aus der DB: name, port (intern, localhost), lobby-Flag."""
    from sqlalchemy import select

    from app.models.server import Server
    from app.services import server_service

    members: list[dict] = []
    seen: set[str] = set()
    for srv in db.scalars(select(Server).where(Server.velocity_enabled.is_(True))).all():
        name = (
            server_service.normalize_velocity_name(srv.velocity_name)
            or server_service.normalize_velocity_name(srv.slug)
            or f"server{srv.id}"
        )
        while name in seen:  # Namenskollision -> eindeutig machen
            name = f"{name}-{srv.id}"
        seen.add(name)
        # Ziel = lokaler Sleep-Proxy-Port (bei Sleep) bzw. direkt der Backend-Port.
        port = server_service.velocity_target_port(srv)
        if not port:
            continue
        members.append({"name": name, "port": int(port), "is_lobby": bool(srv.velocity_is_lobby)})
    return members


def _render_servers_block(members: list[dict], domain: str) -> tuple[str, str]:
    """Baut die [servers]- und [forced-hosts]-Bloecke aus den Mitgliedern."""
    lines = ["[servers]"]
    for member in members:
        lines.append(f'{member["name"]} = "127.0.0.1:{member["port"]}"')
    lobbies = [m["name"] for m in members if m["is_lobby"]]
    if not lobbies and members:
        lobbies = [members[0]["name"]]  # Fallback: erstes Backend als Lobby
    try_list = ", ".join(f'"{name}"' for name in lobbies)
    lines.append(f"try = [{try_list}]")

    forced = ["[forced-hosts]"]
    if domain:
        for member in members:
            forced.append(f'"{member["name"]}.{domain}" = ["{member["name"]}"]')
    return "\n".join(lines), "\n".join(forced)


def _build_velocity_toml_text(db, *, bind_port: int) -> str:
    ensure_forwarding_secret()
    members = _velocity_members(db) if db is not None else []
    domain = ""
    if db is not None:
        try:
            from app.services import app_setting_service

            domain = app_setting_service.get_network_domain(db)
        except Exception:  # noqa: BLE001
            domain = ""
    servers_block, forced_block = _render_servers_block(members, domain)

    toml = f"""# Automatisch vom MC-Server-Manager erzeugt. NICHT von Hand editieren.
config-version = "2.7"
bind = "0.0.0.0:{int(bind_port)}"
motd = "<#09b287>MC Server Netzwerk"
show-max-players = 100
online-mode = true
force-key-authentication = true
prevent-client-proxy-connections = false
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "DISABLED"
enable-player-address-logging = true

{servers_block}

{forced_block}

[advanced]
compression-threshold = 256
compression-level = -1
login-ratelimit = 3000
connection-timeout = 5000
# Hoeher als Standard und mindestens so hoch wie das Wake-Ready-Timeout des
# Sleep-Proxys (180s), damit ein schlafendes Backend beim /server-Wechsel noch
# rechtzeitig hochfaehrt, bevor Velocity die Verbindung aufgibt.
read-timeout = 185000
haproxy-protocol = false
tcp-fast-open = false
bungee-plugin-message-channel = true
show-ping-requests = false
failover-on-unexpected-server-disconnect = true
announce-proxy-commands = true
log-command-executions = false
log-player-connections = true

[query]
enabled = false
port = {int(bind_port)}
map = "Velocity"
show-plugins = false
"""
    return toml


def generate_velocity_toml(db, *, bind_port: int) -> Path:
    """Schreibt eine gueltige velocity.toml inklusive Backend-Servern.

    Die [servers]-Tabelle + "try" (Lobby) werden aus den Netzwerk-Mitgliedern
    erzeugt; [forced-hosts] bietet Direktverbindung ueber <name>.<domain>.
    """
    path = _toml_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_build_velocity_toml_text(db, bind_port=bind_port), encoding="utf-8")
    return path


def _write_paper_velocity_config(base_path: Path, secret: str) -> None:
    """proxies.velocity in config/paper-global.yml setzen (Modern Forwarding).

    Bestehende Datei wird gemerged (Paper ergaenzt Defaults beim naechsten Start).
    """
    import yaml

    config_dir = base_path / "config"
    config_dir.mkdir(parents=True, exist_ok=True)
    path = config_dir / "paper-global.yml"

    data: dict = {}
    if path.exists():
        try:
            loaded = yaml.safe_load(path.read_text(encoding="utf-8", errors="ignore"))
            if isinstance(loaded, dict):
                data = loaded
        except Exception:  # noqa: BLE001 - kaputte Datei -> neu schreiben
            data = {}

    proxies = data.get("proxies")
    if not isinstance(proxies, dict):
        proxies = {}
    velocity = proxies.get("velocity")
    if not isinstance(velocity, dict):
        velocity = {}
    velocity.update({"enabled": True, "online-mode": True, "secret": secret})
    proxies["velocity"] = velocity
    data["proxies"] = proxies

    path.write_text(yaml.safe_dump(data, sort_keys=False, allow_unicode=True), encoding="utf-8")


def apply_backend_forwarding(db, server) -> list[str]:
    """Ein Netzwerk-Backend fuer Velocity vorbereiten (beim Start).

    - server.properties: online-mode=false + server-ip=127.0.0.1 (nur lokal!).
    - Paper-Familie: config/paper-global.yml Velocity-Sektion mit Secret.
    - Andere Server-Typen: Warnung (Modern Forwarding nicht nativ verfuegbar).
    """
    from app.services import server_service

    warnings: list[str] = []
    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists():
        return [f"Serverordner nicht gefunden: {base_path}"]

    server_type = (server.server_type or "").strip().lower()
    if server_type not in PAPER_FORKS:
        # WICHTIG: online-mode NICHT abschalten fuer Typen ohne Modern Forwarding –
        # sonst falsche Offline-UUIDs (Whitelist/OP greifen nicht) OHNE dass die
        # echte Identitaet weitergereicht wird. Solche Server gehoeren nicht ins
        # Velocity-Netz (siehe Sperre in update_server_settings).
        return [
            f"Servertyp '{server_type}' unterstuetzt kein Velocity-Forwarding – "
            "Backend-Modus nicht angewendet. Empfohlen: Paper/Purpur."
        ]

    # Sicherheit: hinter Velocity nur lokal + Offline-Mode (Velocity authentifiziert
    # ueber Modern Forwarding und reicht die echte Spieler-UUID durch).
    for key, value in (("online-mode", "false"), ("server-ip", "127.0.0.1")):
        warning = server_service._upsert_server_property(server, key, value)
        if warning:
            warnings.append(warning)
    try:
        _write_paper_velocity_config(base_path, ensure_forwarding_secret())
    except Exception as exc:  # noqa: BLE001
        warnings.append(f"paper-global.yml (Velocity) konnte nicht geschrieben werden: {exc}")
    return warnings


def _read_server_property(base_path: Path, key: str) -> str | None:
    path = base_path / "server.properties"
    if not path.exists():
        return None
    prefix = f"{key}="
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if line.strip().startswith(prefix):
            return line.split("=", 1)[1].strip()
    return None


def revert_backend_forwarding(db, server) -> list[str]:
    """Velocity-Backend-Einstellungen zuruecknehmen, wenn ein Server NICHT (mehr)
    Teil des Netzwerks ist.

    Der Manager erkennt seine eigene Signatur am loopback-Bind (server-ip=127.0.0.1)
    und stellt dann online-mode=true wieder her und gibt den Bind frei. Ein bewusst
    'cracked' Standalone-Server (online-mode=false OHNE loopback) bleibt unangetastet.
    """
    from app.services import server_service

    base_path = Path(server.base_path).expanduser().resolve()
    if not base_path.exists():
        return []
    if (_read_server_property(base_path, "server-ip") or "").strip() != "127.0.0.1":
        return []  # keine Velocity-Signatur -> nichts anfassen

    notes: list[str] = []
    for key, value in (("online-mode", "true"), ("server-ip", "")):
        warning = server_service._upsert_server_property(server, key, value)
        if warning:
            notes.append(warning)
    if not notes:
        notes.append("Velocity-Backend-Modus zurueckgesetzt (online-mode=true, oeffentlicher Bind).")
    return notes


# --------------------------------------------------------------------------- #
# Cross-Version: ViaVersion-Plugins (Phase 3)
# --------------------------------------------------------------------------- #
# Modrinth-Slug -> Jar-Dateiname-Praefix (fuer "schon vorhanden?"-Pruefung).
VIA_PLUGINS = (
    ("viaversion", "ViaVersion"),
    ("viabackwards", "ViaBackwards"),
    ("viarewind", "ViaRewind"),
)


def _resolve_via_download(slug: str) -> tuple[str, str] | None:
    """Neueste Velocity-taugliche Version eines Via-Plugins von Modrinth (Release
    bevorzugt, sonst neueste beliebige)."""
    versions = fetch_json(f"https://api.modrinth.com/v2/project/{slug}/version")
    if not isinstance(versions, list):
        return None

    def velocity_ok(entry: dict) -> bool:
        return isinstance(entry, dict) and "velocity" in (entry.get("loaders") or [])

    releases = [v for v in versions if velocity_ok(v) and v.get("version_type") == "release"]
    pick = releases[0] if releases else next((v for v in versions if velocity_ok(v)), None)
    if not pick:
        return None
    files = pick.get("files") or []
    chosen = next((f for f in files if f.get("primary")), files[0] if files else None)
    if not chosen or not chosen.get("url"):
        return None
    return str(chosen["url"]), str(chosen.get("filename") or f"{slug}.jar")


def _via_jar_set() -> set[str]:
    plugins = _plugins_dir()
    if not plugins.exists():
        return set()
    names: set[str] = set()
    for _slug, prefix in VIA_PLUGINS:
        for jar in plugins.glob(f"{prefix}*.jar"):
            names.add(jar.name)
    return names


def ensure_via_plugins(*, on_progress=None) -> list[str]:
    """ViaVersion(+Backwards+Rewind) nach velocity/plugins/ laden, falls noch nicht
    vorhanden. Best-effort: darf den Velocity-Start NIE hart abbrechen (jeder
    Fehler wird nur als Notiz zurueckgegeben)."""
    notes: list[str] = []
    plugins = _plugins_dir()
    try:
        plugins.mkdir(parents=True, exist_ok=True)
    except Exception as exc:  # noqa: BLE001
        return [f"ViaVersion uebersprungen (plugins-Ordner nicht anlegbar): {exc}"]

    for slug, prefix in VIA_PLUGINS:
        try:
            if any(plugins.glob(f"{prefix}*.jar")):
                continue  # bereits installiert
            resolved = _resolve_via_download(slug)
            if not resolved:
                notes.append(f"{prefix}: keine Velocity-Version auf Modrinth gefunden.")
                continue
            url, filename = resolved
            download_file(url, plugins / filename, timeout_seconds=120.0)
            if on_progress:
                on_progress(f"{prefix} installiert ({filename}).")
        except Exception as exc:  # noqa: BLE001
            notes.append(f"{prefix} konnte nicht geladen werden: {exc}")
    return notes


def remove_via_plugins() -> bool:
    """Via-Plugin-Jars entfernen (wenn Cross-Version deaktiviert wird)."""
    plugins = _plugins_dir()
    if not plugins.exists():
        return False
    removed = False
    for _slug, prefix in VIA_PLUGINS:
        for jar in plugins.glob(f"{prefix}*.jar"):
            try:
                jar.unlink()
                removed = True
            except OSError:
                pass
    return removed


def sync_via_plugins(db, *, on_progress=None) -> None:
    """Via-Plugins an die Einstellung angleichen; bei Aenderung Velocity neu starten
    (Plugins werden nur beim Start geladen). Best-effort."""
    from app.services import app_setting_service

    if app_setting_service.get_network_mode(db) != "velocity":
        return
    before = _via_jar_set()
    try:
        if app_setting_service.get_velocity_via_enabled(db):
            for note in ensure_via_plugins(on_progress=on_progress):
                _vlog(note)
        else:
            remove_via_plugins()
    except Exception as exc:  # noqa: BLE001 - optional, nie den Proxy blockieren
        _vlog(f"Via-Plugins uebersprungen: {exc}")
        return
    if _via_jar_set() != before and is_velocity_running():
        _vlog("Via-Plugins geaendert -> Velocity wird neu gestartet.")
        stop_velocity()
        _ensure_velocity_started()


def sync_velocity_config(db) -> None:
    """velocity.toml an die aktuellen Backends angleichen.

    Nur wenn sich die Datei tatsaechlich AENDERT und Velocity laeuft, wird der
    Proxy neu gestartet (velocity.toml wird nur beim Start gelesen). So kicken
    normale Server-Starts nicht bei jedem Mal alle Online-Spieler.
    """
    from app.services import app_setting_service

    if app_setting_service.get_network_mode(db) != "velocity":
        return
    bind_port = app_setting_service.get_network_port(db)
    new_text = _build_velocity_toml_text(db, bind_port=bind_port)

    path = _toml_path()
    old_text = path.read_text(encoding="utf-8") if path.exists() else ""
    changed = new_text != old_text
    if changed:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(new_text, encoding="utf-8")

    if not is_velocity_running():
        _ensure_velocity_started()
    elif changed:
        _vlog("Velocity-Backends geaendert -> Proxy wird neu gestartet.")
        stop_velocity()
        _ensure_velocity_started()


# --------------------------------------------------------------------------- #
# Provisionierung
# --------------------------------------------------------------------------- #
def ensure_velocity_jar(version_id: str) -> tuple[bool, str]:
    """Laedt die Velocity-Jar, falls noch nicht vorhanden."""
    jar = _jar_path()
    marker = managed_velocity_dir() / ".velocity-version"
    have_version = marker.read_text(encoding="utf-8").strip() if marker.exists() else ""
    if jar.exists() and have_version == version_id:
        return True, ""
    try:
        url, _name = _resolve_download(version_id)
        download_file(url, jar, timeout_seconds=120.0)
    except Exception as exc:  # noqa: BLE001
        return False, f"Velocity {version_id} konnte nicht geladen werden: {exc}"
    marker.write_text(version_id, encoding="utf-8")
    return True, ""


# --------------------------------------------------------------------------- #
# Prozess-Lifecycle
# --------------------------------------------------------------------------- #
def is_velocity_running() -> bool:
    with _LOCK:
        return _process is not None and _process.poll() is None


def _pump_log(stream) -> None:
    try:
        with _log_path().open("a", encoding="utf-8", errors="replace") as handle:
            for raw in stream:
                line = raw.rstrip("\r\n")
                handle.write(line + "\n")
                handle.flush()
                with _LOCK:
                    _log_tail.append(line)
                    if len(_log_tail) > _LOG_TAIL_MAX:
                        del _log_tail[: len(_log_tail) - _LOG_TAIL_MAX]
    except Exception:  # noqa: BLE001
        pass


def log_tail() -> list[str]:
    with _LOCK:
        return list(_log_tail)


def last_status() -> str:
    with _LOCK:
        return _last_status


def _vlog(message: str) -> None:
    """Diagnose-Zeile: in die Velocity-Logdatei + Ring-Puffer + letzter Status."""
    global _last_status
    if not message:
        return
    with _LOCK:
        _last_status = message
        _log_tail.append(message)
        if len(_log_tail) > _LOG_TAIL_MAX:
            del _log_tail[: len(_log_tail) - _LOG_TAIL_MAX]
    try:
        path = _log_path()
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8", errors="replace") as handle:
            handle.write(f"[manager] {message}\n")
    except Exception:  # noqa: BLE001
        pass


def _current_network_mode() -> str:
    from app.db.session import SessionLocal
    from app.services import app_setting_service

    try:
        with SessionLocal() as db:
            return app_setting_service.get_network_mode(db)
    except Exception:  # noqa: BLE001
        return "off"


def start_velocity(db, *, on_progress=None) -> tuple[bool, str]:
    """Startet Velocity (idempotent). Laedt Jar + Java bei Bedarf.

    Die (potenziell minutenlange) Provisionierung (Jar-/Java-Download) laeuft
    BEWUSST ohne den Prozess-Lock, damit ``is_velocity_running``/``stop_velocity``/
    Status waehrenddessen nicht blockieren. Der Lock schuetzt nur die kurze
    Start-/Zustandsmutation. Ein ``_starting``-Flag verhindert Doppelstarts.

    Vor dem eigentlichen Launch wird erneut geprueft, dass der Netzwerk-Modus
    weiterhin "velocity" ist und kein Shutdown laeuft (sonst wuerde nach einem
    Moduswechsel waehrend des Downloads faelschlich Velocity gestartet).
    """
    from app.services import app_setting_service, process_service
    from app.services.java_runtime_service import (
        _best_profile_for_major,
        build_java_env_from_profile,
        ensure_java_available,
    )

    def _log(msg: str) -> None:
        _vlog(msg)
        if on_progress and msg:
            on_progress(msg)

    global _process, _starting
    with _LOCK:
        if is_velocity_running():
            return True, "Velocity laeuft bereits."
        if _starting:
            return True, "Velocity-Start laeuft bereits."
        _starting = True

    try:
        version = app_setting_service.get_velocity_version(db) or default_velocity_version()
        if not version:
            return False, "Keine Velocity-Version verfuegbar (Fill-API nicht erreichbar?)."

        _log(f"Velocity {version} wird vorbereitet ...")
        ok, message = ensure_velocity_jar(version)
        if not ok:
            return False, message

        # Cross-Version: ViaVersion-Plugins sicherstellen bzw. entfernen. Optional
        # -> darf den Proxy-Start nie blockieren (best-effort).
        try:
            if app_setting_service.get_velocity_via_enabled(db):
                for note in ensure_via_plugins(on_progress=_log):
                    _vlog(note)
            else:
                remove_via_plugins()
        except Exception as exc:  # noqa: BLE001
            _vlog(f"Via-Plugins uebersprungen: {exc}")

        required = required_java_for_version(version)
        installed, java_msg = ensure_java_available(db, required, on_progress=on_progress)
        if not installed:
            return False, f"Velocity braucht Java {required}+: {java_msg}"
        profile = _best_profile_for_major(db, required)
        if profile is None:
            return False, f"Kein passendes Java {required}+ fuer Velocity gefunden."
        env = build_java_env_from_profile(profile)

        bind_port = app_setting_service.get_network_port(db)
        generate_velocity_toml(db, bind_port=bind_port)

        base = managed_velocity_dir()
        use_pushd = process_service._is_unc_path(base)
        run = "java -Xms512M -Xmx1024M -jar velocity.jar"
        if use_pushd:
            step = (
                f"pushd {process_service._escape_cmd_token(process_service._normalize_windows_path(str(base)))}"
                f" && {run}"
            )
        else:
            step = run

        with _LOCK:
            # Nach der Provisionierung erneut absichern: Modus noch "velocity",
            # kein Shutdown, kein paralleler Lauf -> sonst NICHT starten.
            if _shutdown:
                return False, "Velocity-Start abgebrochen (Shutdown)."
            if is_velocity_running():
                return True, "Velocity laeuft bereits."
            if app_setting_service.get_network_mode(db) != "velocity":
                return False, "Velocity-Start abgebrochen (Netzwerk-Modus gewechselt)."
            try:
                proc = subprocess.Popen(
                    ["cmd", "/d", "/c", step],
                    cwd=None if use_pushd else str(base),
                    env=env,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    bufsize=1,
                    creationflags=process_service._build_creation_flags(),
                )
            except Exception as exc:  # noqa: BLE001
                return False, f"Velocity konnte nicht gestartet werden: {exc}"
            _process = proc
            Thread(target=_pump_log, args=(proc.stdout,), daemon=True).start()

        _log(f"Velocity {version} gestartet (Port {bind_port}).")
        return True, f"Velocity {version} gestartet (Port {bind_port})."
    finally:
        with _LOCK:
            _starting = False


def stop_velocity(*, shutting_down: bool = False) -> None:
    from app.services import process_service

    global _process, _shutdown
    with _LOCK:
        if shutting_down:
            _shutdown = True
        proc = _process
        _process = None
    if proc is None or proc.poll() is not None:
        return
    try:
        process_service._terminate_process_tree(proc, timeout_seconds=8.0)
    except Exception:  # noqa: BLE001
        try:
            proc.kill()
        except Exception:  # noqa: BLE001
            pass


def mark_startup() -> None:
    """Beim App-Start das Shutdown-Flag zuruecksetzen (rearm)."""
    global _shutdown
    with _LOCK:
        _shutdown = False


def _ensure_velocity_started() -> None:
    """Startet Velocity im Hintergrund, falls noetig (nicht laufend/nicht startend)."""
    with _LOCK:
        if _shutdown or is_velocity_running() or _starting:
            return
    Thread(target=_start_velocity_bg, daemon=True).start()


def _start_velocity_bg() -> None:
    from app.db.session import SessionLocal

    try:
        with SessionLocal() as db:
            ok, message = start_velocity(db)
        if not ok:
            _vlog(f"Velocity-Start fehlgeschlagen: {message}")
    except Exception as exc:  # noqa: BLE001
        _vlog(f"Velocity-Start abgebrochen (Ausnahme): {exc}")


def reconcile_network() -> None:
    """Gemeinsame Eingangstuer (Gateway ODER Velocity) passend zum Netzwerk-Modus.

    Beide teilen sich den Netzwerk-Port -> der jeweils falsche wird zuerst gestoppt
    (Port freigeben), bevor der richtige bindet. Idempotent (App-Start, Settings,
    Idle-Tick/Crash-Recovery).
    """
    from app.services import gateway_service

    mode = _current_network_mode()
    if mode == "velocity":
        gateway_service.stop_gateway()
        _ensure_velocity_started()
    elif mode == "gateway":
        stop_velocity()
        gateway_service.reconcile_gateway()
    else:  # off
        stop_velocity()
        gateway_service.stop_gateway()


# Rueckwaertskompatibler Alias (Lifespan/Settings/Idle rufen die Koordination).
def reconcile_velocity() -> None:
    reconcile_network()


def velocity_status_runtime() -> dict:
    from app.db.session import SessionLocal
    from app.services import app_setting_service

    with SessionLocal() as db:
        mode = app_setting_service.get_network_mode(db)
        version = app_setting_service.get_velocity_version(db) or "(neueste stabile)"
    return {
        "mode": mode,
        "running": is_velocity_running(),
        "version": version,
        "jar_present": _jar_path().exists(),
        "last_status": last_status(),
    }
