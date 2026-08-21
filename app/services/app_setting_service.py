from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models.app_setting import AppSetting


SERVER_STORAGE_ROOT_KEY = "server_storage_root"
BACKUP_STORAGE_ROOT_KEY = "backup_storage_root"
# Zentrales NAS-Verzeichnis fuer Logs + Backups + DB-Snapshots (leer = alles lokal).
# UNC bevorzugen (ein SYSTEM-Dienst sieht Laufwerksbuchstaben wie Z: meist nicht).
CENTRAL_STORAGE_ROOT_KEY = "central_storage_root"
# Optionale NAS-Anmeldung: der Manager authentifiziert sich selbst an der Freigabe
# (WNetAddConnection2) - noetig, wenn er als SYSTEM laeuft und die Freigabe Anmeldung
# verlangt. Leer = Standard-Auth (Gast/Maschinenkonto).
NAS_USER_KEY = "nas_user"
NAS_PASSWORD_KEY = "nas_password"
PUBLIC_BASE_URL_KEY = "public_base_url"
NETWORK_MODE_KEY = "network_mode"
NETWORK_PORT_KEY = "network_port"
NETWORK_DOMAIN_KEY = "network_domain"
# Modpack-Dispatcher: blanke Domain erkennt den Client-Loader/-Mods und leitet weiter.
DISPATCHER_ENABLED_KEY = "dispatcher_enabled"
# Universal-Lobby: der Python-Hub (modded + vanilla, begehbar) als verwalteter
# Listener hinter dem Gateway. Default AUS -> aendert nichts am bestehenden Netzwerk.
HUB_LOBBY_ENABLED_KEY = "hub_lobby_enabled"
HUB_LOBBY_PORT_KEY = "hub_lobby_port"
HUB_LOBBY_REPLAY_KEY = "hub_lobby_replay"
HUB_LOBBY_VANILLA_REPLAY_KEY = "hub_lobby_vanilla_replay"  # optional; leer = nur modded
HUB_LOBBY_VANILLA_PORT_KEY = "hub_lobby_vanilla_port"     # 2. Hub-Port (vanilla, via ViaProxy)
_HUB_LOBBY_DEFAULT_PORT = 25599
_HUB_LOBBY_DEFAULT_VANILLA_PORT = 25600
_HUB_LOBBY_DEFAULT_REPLAY = "atm10_capture.replay"

# Hub-Praesentation (Serverlisten-Ping) + Zugang. Werden pro Verbindung gelesen
# (live editierbar ohne Hub-Neustart).
HUB_NAME_KEY = "hub_name"
HUB_MOTD_KEY = "hub_motd"
HUB_MAX_PLAYERS_KEY = "hub_max_players"
HUB_WHITELIST_ENABLED_KEY = "hub_whitelist_enabled"
HUB_WHITELIST_KEY = "hub_whitelist"  # Namen, per Zeile/Komma getrennt
_HUB_DEFAULT_NAME = "Universal-Lobby"
_HUB_DEFAULT_MOTD = "Universal-Lobby - alle Versionen und Modpacks"
_HUB_DEFAULT_MAX_PLAYERS = 100

# ViaProxy: standalone Java-Uebersetzer VOR dem Hub, damit vanilla Clients JEDER Version
# in die 1.21.1-Welt kommen (modded 1.21.1 umgeht ihn direkt). Default AUS.
VIAPROXY_ENABLED_KEY = "viaproxy_enabled"
VIAPROXY_PORT_KEY = "viaproxy_port"
VIAPROXY_JAR_KEY = "viaproxy_jar"
VIAPROXY_JAVA_KEY = "viaproxy_java"
_VIAPROXY_DEFAULT_PORT = 25601
_VIAPROXY_TARGET_VERSION = "1.21.1"

# Erlaubte Netzwerk-Modi:
#  - "gateway" = transparenter Hostname-Router (jeder Typ/jede Version, alle Server
#                laufen parallel, Direktverbindung bleibt).
#  - "off"     = kein gemeinsamer Eingang.
NETWORK_MODES = ("off", "gateway")


def _normalize_path(raw_value: str) -> Path:
    return Path(raw_value).expanduser().resolve()


def _repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _looks_like_service_profile(home_path: Path) -> bool:
    normalized = str(home_path).replace("/", "\\").lower()
    return (
        "\\windows\\system32\\config\\systemprofile" in normalized
        or "\\windows\\serviceprofiles\\" in normalized
    )


def _default_desktop_storage_path() -> Path:
    home_path = Path.home().expanduser().resolve()
    if _looks_like_service_profile(home_path):
        return (_repository_root() / "managed_servers").resolve()
    return (home_path / "Desktop" / "mc_servers").resolve()


def _default_backup_storage_path() -> Path:
    settings = get_settings()
    return (settings.data_dir / "backups").resolve()


def _get_setting_row(db: Session, key: str) -> AppSetting | None:
    return db.scalar(select(AppSetting).where(AppSetting.key == key))


def _get_root_from_sources(
    db: Session,
    *,
    key: str,
    env_value: str | None,
    default_path_factory,
) -> Path:
    row = _get_setting_row(db, key)
    if row and row.value.strip():
        return _normalize_path(row.value.strip())

    env_raw = (env_value or "").strip()
    if env_raw:
        return _normalize_path(env_raw)
    return default_path_factory()


def _get_source(
    db: Session,
    *,
    key: str,
    env_value: str | None,
) -> str:
    row = _get_setting_row(db, key)
    if row and row.value.strip():
        return "ui"
    if (env_value or "").strip():
        return "env"
    return "default"


def _ensure_initialized(
    db: Session,
    *,
    key: str,
    env_value: str | None,
    default_path_factory,
) -> Path:
    row = _get_setting_row(db, key)
    if row and row.value.strip():
        normalized = _normalize_path(row.value.strip())
        normalized.mkdir(parents=True, exist_ok=True)
        if row.value != str(normalized):
            row.value = str(normalized)
            db.add(row)
            db.commit()
        return normalized

    env_raw = (env_value or "").strip()
    if env_raw:
        normalized = _normalize_path(env_raw)
        normalized.mkdir(parents=True, exist_ok=True)
        return normalized

    default_path = default_path_factory()
    default_path.mkdir(parents=True, exist_ok=True)
    return default_path


def _set_root_override(db: Session, *, key: str, path_value: str) -> Path:
    normalized = _normalize_path(path_value.strip())
    normalized.mkdir(parents=True, exist_ok=True)
    row = _get_setting_row(db, key)
    if row is None:
        row = AppSetting(key=key, value=str(normalized))
    else:
        row.value = str(normalized)
    db.add(row)
    db.commit()
    return normalized


def _clear_root_override(
    db: Session,
    *,
    key: str,
    resolver,
) -> Path:
    row = _get_setting_row(db, key)
    if row is not None:
        db.delete(row)
        db.commit()
    path = resolver(db)
    path.mkdir(parents=True, exist_ok=True)
    return path


def get_server_storage_root(db: Session) -> Path:
    settings = get_settings()
    return _get_root_from_sources(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        env_value=settings.default_server_root,
        default_path_factory=_default_desktop_storage_path,
    )


def get_server_storage_source(db: Session) -> str:
    settings = get_settings()
    return _get_source(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        env_value=settings.default_server_root,
    )


def ensure_server_storage_initialized(db: Session) -> Path:
    settings = get_settings()
    return _ensure_initialized(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        env_value=settings.default_server_root,
        default_path_factory=_default_desktop_storage_path,
    )


def set_server_storage_root(db: Session, path_value: str) -> Path:
    return _set_root_override(db, key=SERVER_STORAGE_ROOT_KEY, path_value=path_value)


def clear_server_storage_override(db: Session) -> Path:
    return _clear_root_override(
        db,
        key=SERVER_STORAGE_ROOT_KEY,
        resolver=get_server_storage_root,
    )


def get_backup_storage_root(db: Session) -> Path:
    settings = get_settings()
    return _get_root_from_sources(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        env_value=settings.default_backup_root,
        default_path_factory=_default_backup_storage_path,
    )


def get_backup_storage_source(db: Session) -> str:
    settings = get_settings()
    return _get_source(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        env_value=settings.default_backup_root,
    )


def ensure_backup_storage_initialized(db: Session) -> Path:
    settings = get_settings()
    return _ensure_initialized(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        env_value=settings.default_backup_root,
        default_path_factory=_default_backup_storage_path,
    )


def set_backup_storage_root(db: Session, path_value: str) -> Path:
    return _set_root_override(db, key=BACKUP_STORAGE_ROOT_KEY, path_value=path_value)


def clear_backup_storage_override(db: Session) -> Path:
    return _clear_root_override(
        db,
        key=BACKUP_STORAGE_ROOT_KEY,
        resolver=get_backup_storage_root,
    )


# --- Zentrales NAS-Verzeichnis (Logs + Backups + DB-Snapshots) -----------------
def get_central_storage_root(db: Session) -> str:
    """Roh-Pfad des zentralen NAS-Verzeichnisses (leer = lokal). UNC empfohlen."""
    row = _get_setting_row(db, CENTRAL_STORAGE_ROOT_KEY)
    return row.value.strip() if row and row.value.strip() else ""


def set_central_storage_root(db: Session, value: str | None) -> None:
    _set_or_clear(db, CENTRAL_STORAGE_ROOT_KEY, (value or "").strip() or None)


def get_central_storage_root_runtime() -> str:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_central_storage_root(db)


def get_nas_user(db: Session) -> str:
    row = _get_setting_row(db, NAS_USER_KEY)
    return row.value.strip() if row and row.value.strip() else ""


def set_nas_user(db: Session, value: str | None) -> None:
    _set_or_clear(db, NAS_USER_KEY, (value or "").strip() or None)


def get_nas_password(db: Session) -> str:
    row = _get_setting_row(db, NAS_PASSWORD_KEY)
    return row.value if row and row.value else ""


def set_nas_password(db: Session, value: str | None) -> None:
    _set_or_clear(db, NAS_PASSWORD_KEY, (value or "") or None)


def get_nas_user_runtime() -> str:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_nas_user(db)


def get_nas_password_runtime() -> str:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_nas_password(db)


# --- Oeffentliche Basis-URL / Zieldomain (fuer Resource-Pack-Hosting etc.) ---


def _normalize_public_base_url(raw_value: str) -> str:
    """Trimmen, abschliessende Slashes entfernen, Schema pruefen."""
    value = (raw_value or "").strip().rstrip("/")
    if not value:
        return ""
    lowered = value.lower()
    if not (lowered.startswith("http://") or lowered.startswith("https://")):
        raise ValueError("Die URL muss mit http:// oder https:// beginnen.")
    return value


def get_public_base_url(db: Session) -> str:
    """UI-Override > ENV (MCSM_PUBLIC_BASE_URL) > leer."""
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row and row.value.strip():
        return row.value.strip().rstrip("/")
    env_raw = (get_settings().public_base_url or "").strip()
    return env_raw.rstrip("/")


def get_public_base_url_source(db: Session) -> str:
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row and row.value.strip():
        return "ui"
    if (get_settings().public_base_url or "").strip():
        return "env"
    return "default"


def set_public_base_url(db: Session, url_value: str) -> str:
    normalized = _normalize_public_base_url(url_value)
    if not normalized:
        raise ValueError("Bitte eine gueltige URL angeben oder Zuruecksetzen nutzen.")
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row is None:
        row = AppSetting(key=PUBLIC_BASE_URL_KEY, value=normalized)
    else:
        row.value = normalized
    db.add(row)
    db.commit()
    return normalized


def clear_public_base_url_override(db: Session) -> str:
    row = _get_setting_row(db, PUBLIC_BASE_URL_KEY)
    if row is not None:
        db.delete(row)
        db.commit()
    return get_public_base_url(db)


def get_public_base_url_runtime() -> str:
    """Laufzeit-Getter mit eigener Session (fuer Services ohne Request-Session).

    SessionLocal wird bewusst erst zur Laufzeit importiert, damit nach einem
    Reload von ``app.db.session`` (z.B. in Tests) die frische Factory genutzt wird.
    """
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_public_base_url(db)


# --- Netzwerk (Velocity-Lobby): Modus + oeffentlicher Port + Domain ---


def _normalize_bool(raw: object) -> bool:
    if isinstance(raw, bool):
        return raw
    return str(raw or "").strip().lower() in {"1", "true", "on", "yes"}


def _set_or_clear(db: Session, key: str, value: str | None) -> None:
    row = _get_setting_row(db, key)
    normalized = (value or "").strip()
    if not normalized:
        if row is not None:
            db.delete(row)
            db.commit()
        return
    if row is None:
        row = AppSetting(key=key, value=normalized)
    else:
        row.value = normalized
    db.add(row)
    db.commit()


def get_dispatcher_enabled(db: Session) -> bool:
    """Ob der Modpack-Dispatcher an der blanken Domain aktiv ist (Default: aus)."""
    row = _get_setting_row(db, DISPATCHER_ENABLED_KEY)
    return _normalize_bool(row.value) if row and row.value else False


def set_dispatcher_enabled(db: Session, enabled: bool) -> None:
    _set_or_clear(db, DISPATCHER_ENABLED_KEY, "true" if enabled else None)


def get_network_port(db: Session) -> int:
    """Oeffentlicher Netzwerk-Port (Gateway-Eingang). UI > ENV > Default."""
    row = _get_setting_row(db, NETWORK_PORT_KEY)
    if row and row.value.strip():
        try:
            return int(row.value.strip())
        except ValueError:
            pass
    return int(get_settings().network_port)


def get_network_port_source(db: Session) -> str:
    row = _get_setting_row(db, NETWORK_PORT_KEY)
    return "ui" if (row and row.value.strip()) else "config"


def set_network_port(db: Session, port: int) -> int:
    port = int(port)
    if not (1 <= port <= 65535):
        raise ValueError("Port muss zwischen 1 und 65535 liegen.")
    _set_or_clear(db, NETWORK_PORT_KEY, str(port))
    return port


def get_network_port_runtime() -> int:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_network_port(db)


def _normalize_network_domain(raw: str | None) -> str:
    """Domain vereinheitlichen: ohne Schema, ohne Slashes/Punkt am Ende, klein."""
    value = (raw or "").strip().lower()
    if "://" in value:
        value = value.split("://", 1)[1]
    value = value.split("/", 1)[0].strip().strip(".")
    return value


def get_network_domain(db: Session) -> str:
    """UI-Override > ENV/Config (MCSM_NETWORK_DOMAIN) > leer."""
    row = _get_setting_row(db, NETWORK_DOMAIN_KEY)
    if row and row.value.strip():
        return _normalize_network_domain(row.value)
    return _normalize_network_domain(get_settings().network_domain)


def get_network_domain_source(db: Session) -> str:
    row = _get_setting_row(db, NETWORK_DOMAIN_KEY)
    if row and row.value.strip():
        return "ui"
    if (get_settings().network_domain or "").strip():
        return "env"
    return "default"


def set_network_domain(db: Session, domain: str | None) -> str:
    normalized = _normalize_network_domain(domain)
    _set_or_clear(db, NETWORK_DOMAIN_KEY, normalized)
    return normalized


def get_network_domain_runtime() -> str:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_network_domain(db)


def get_network_mode(db: Session) -> str:
    """Aktiver Netzwerk-Modus: 'off' | 'gateway'. Default: 'off'."""
    row = _get_setting_row(db, NETWORK_MODE_KEY)
    if row and row.value.strip():
        value = row.value.strip().lower()
        if value in NETWORK_MODES:
            return value
    return "off"


def get_network_mode_source(db: Session) -> str:
    row = _get_setting_row(db, NETWORK_MODE_KEY)
    return "ui" if (row and row.value.strip()) else "default"


def set_network_mode(db: Session, mode: str) -> str:
    normalized = (mode or "").strip().lower()
    if normalized not in NETWORK_MODES:
        raise ValueError(f"Ungueltiger Netzwerk-Modus. Erlaubt: {', '.join(NETWORK_MODES)}.")
    _set_or_clear(db, NETWORK_MODE_KEY, normalized)
    return normalized


def get_network_mode_runtime() -> str:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_network_mode(db)


# --- Universal-Lobby (Python-Hub fuer modded + vanilla) ------------------------
def get_hub_lobby_enabled(db: Session) -> bool:
    """Ob der begehbare Python-Hub als Lobby laeuft (Default: aus)."""
    row = _get_setting_row(db, HUB_LOBBY_ENABLED_KEY)
    return _normalize_bool(row.value) if row and row.value else False


def set_hub_lobby_enabled(db: Session, enabled: bool) -> None:
    _set_or_clear(db, HUB_LOBBY_ENABLED_KEY, "true" if enabled else None)


def get_hub_lobby_port(db: Session) -> int:
    """Lokaler Port des Hub-Listeners (hinter dem Gateway). UI > Default."""
    row = _get_setting_row(db, HUB_LOBBY_PORT_KEY)
    if row and row.value.strip():
        try:
            return int(row.value.strip())
        except ValueError:
            pass
    return _HUB_LOBBY_DEFAULT_PORT


def set_hub_lobby_port(db: Session, port: int) -> int:
    port = int(port)
    if not (1 <= port <= 65535):
        raise ValueError("Port muss zwischen 1 und 65535 liegen.")
    _set_or_clear(db, HUB_LOBBY_PORT_KEY, str(port))
    return port


def get_hub_lobby_replay(db: Session) -> str:
    """Pfad zur Config-Replay-Datei, die der Hub abspielt (Default: ATM10-Capture)."""
    row = _get_setting_row(db, HUB_LOBBY_REPLAY_KEY)
    if row and row.value.strip():
        return row.value.strip()
    return _HUB_LOBBY_DEFAULT_REPLAY


def set_hub_lobby_replay(db: Session, path: str | None) -> None:
    _set_or_clear(db, HUB_LOBBY_REPLAY_KEY, (path or "").strip() or None)


def get_hub_lobby_enabled_runtime() -> bool:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_hub_lobby_enabled(db)


def get_hub_lobby_port_runtime() -> int:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_hub_lobby_port(db)


def get_hub_lobby_replay_runtime() -> str:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_hub_lobby_replay(db)


def get_hub_lobby_vanilla_replay(db: Session) -> str:
    """Pfad zum Vanilla-Config-Replay (leer = kein Vanilla-Pfad, nur modded)."""
    row = _get_setting_row(db, HUB_LOBBY_VANILLA_REPLAY_KEY)
    return row.value.strip() if row and row.value.strip() else ""


def set_hub_lobby_vanilla_replay(db: Session, path: str | None) -> None:
    _set_or_clear(db, HUB_LOBBY_VANILLA_REPLAY_KEY, (path or "").strip() or None)


def get_hub_lobby_vanilla_replay_runtime() -> str:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_hub_lobby_vanilla_replay(db)


def get_hub_lobby_vanilla_port(db: Session) -> int:
    row = _get_setting_row(db, HUB_LOBBY_VANILLA_PORT_KEY)
    if row and row.value.strip():
        try:
            return int(row.value.strip())
        except ValueError:
            pass
    return _HUB_LOBBY_DEFAULT_VANILLA_PORT


def set_hub_lobby_vanilla_port(db: Session, port: int) -> int:
    port = int(port)
    if not (1 <= port <= 65535):
        raise ValueError("Port muss zwischen 1 und 65535 liegen.")
    _set_or_clear(db, HUB_LOBBY_VANILLA_PORT_KEY, str(port))
    return port


def get_hub_lobby_vanilla_port_runtime() -> int:
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return get_hub_lobby_vanilla_port(db)


# --- Hub-Praesentation (Serverlisten-Ping) + Whitelist -------------------------
def get_hub_name(db: Session) -> str:
    """Anzeigename/Version-Text des Hubs im Serverlisten-Ping."""
    row = _get_setting_row(db, HUB_NAME_KEY)
    if row and row.value.strip():
        return row.value.strip()
    return _HUB_DEFAULT_NAME


def set_hub_name(db: Session, value: str | None) -> None:
    _set_or_clear(db, HUB_NAME_KEY, (value or "").strip() or None)


def get_hub_motd(db: Session) -> str:
    """MOTD-Text (Beschreibung) im Serverlisten-Ping."""
    row = _get_setting_row(db, HUB_MOTD_KEY)
    if row and row.value.strip():
        return row.value.strip()
    return _HUB_DEFAULT_MOTD


def set_hub_motd(db: Session, value: str | None) -> None:
    _set_or_clear(db, HUB_MOTD_KEY, (value or "").strip() or None)


def get_hub_max_players(db: Session) -> int:
    row = _get_setting_row(db, HUB_MAX_PLAYERS_KEY)
    if row and row.value.strip():
        try:
            return int(row.value.strip())
        except ValueError:
            pass
    return _HUB_DEFAULT_MAX_PLAYERS


def set_hub_max_players(db: Session, value: int) -> int:
    value = int(value)
    if not (1 <= value <= 100000):
        raise ValueError("Maximale Spielerzahl muss zwischen 1 und 100000 liegen.")
    _set_or_clear(db, HUB_MAX_PLAYERS_KEY, str(value))
    return value


def get_hub_whitelist_enabled(db: Session) -> bool:
    row = _get_setting_row(db, HUB_WHITELIST_ENABLED_KEY)
    return _normalize_bool(row.value) if row and row.value else False


def set_hub_whitelist_enabled(db: Session, enabled: bool) -> None:
    _set_or_clear(db, HUB_WHITELIST_ENABLED_KEY, "true" if enabled else None)


def get_hub_whitelist(db: Session) -> str:
    """Rohtext der Whitelist (Namen, per Zeile/Komma getrennt)."""
    row = _get_setting_row(db, HUB_WHITELIST_KEY)
    return row.value.strip() if row and row.value.strip() else ""


def set_hub_whitelist(db: Session, value: str | None) -> None:
    _set_or_clear(db, HUB_WHITELIST_KEY, (value or "").strip() or None)


def _parse_name_list(raw: str) -> set[str]:
    names: set[str] = set()
    for chunk in (raw or "").replace(",", "\n").splitlines():
        name = chunk.strip().lower()
        if name:
            names.add(name)
    return names


def get_hub_config_runtime() -> dict:
    """Alle Hub-Praesentations-/Zugangswerte in EINER Session.

    Wird vom Hub pro Verbindung gelesen -> Aenderungen greifen ohne Hub-Neustart.
    """
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return {
            "name": get_hub_name(db),
            "motd": get_hub_motd(db),
            "max_players": get_hub_max_players(db),
            "whitelist_enabled": get_hub_whitelist_enabled(db),
            "whitelist": _parse_name_list(get_hub_whitelist(db)),
        }


# --- ViaProxy (Cross-Version-Uebersetzer vor dem Hub) --------------------------
def get_viaproxy_enabled(db: Session) -> bool:
    row = _get_setting_row(db, VIAPROXY_ENABLED_KEY)
    return _normalize_bool(row.value) if row and row.value else False


def set_viaproxy_enabled(db: Session, enabled: bool) -> None:
    _set_or_clear(db, VIAPROXY_ENABLED_KEY, "true" if enabled else None)


def get_viaproxy_port(db: Session) -> int:
    row = _get_setting_row(db, VIAPROXY_PORT_KEY)
    if row and row.value.strip():
        try:
            return int(row.value.strip())
        except ValueError:
            pass
    return _VIAPROXY_DEFAULT_PORT


def set_viaproxy_port(db: Session, port: int) -> int:
    port = int(port)
    if not (1 <= port <= 65535):
        raise ValueError("Port muss zwischen 1 und 65535 liegen.")
    _set_or_clear(db, VIAPROXY_PORT_KEY, str(port))
    return port


def get_viaproxy_jar(db: Session) -> str:
    row = _get_setting_row(db, VIAPROXY_JAR_KEY)
    return row.value.strip() if row and row.value.strip() else ""


def set_viaproxy_jar(db: Session, path: str | None) -> None:
    _set_or_clear(db, VIAPROXY_JAR_KEY, (path or "").strip() or None)


def get_viaproxy_java(db: Session) -> str:
    """Java-Binary fuer ViaProxy (leer = 'java' aus dem PATH)."""
    row = _get_setting_row(db, VIAPROXY_JAVA_KEY)
    return row.value.strip() if row and row.value.strip() else "java"


def set_viaproxy_java(db: Session, path: str | None) -> None:
    _set_or_clear(db, VIAPROXY_JAVA_KEY, (path or "").strip() or None)


def get_viaproxy_config_runtime() -> dict:
    """Alle ViaProxy-Laufzeitwerte auf einmal (fuer den viaproxy_service-Reconcile)."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return {
            "enabled": get_viaproxy_enabled(db),
            "port": get_viaproxy_port(db),
            "jar": get_viaproxy_jar(db),
            "java": get_viaproxy_java(db),
            "target_port": get_hub_lobby_vanilla_port(db),
            "target_version": _VIAPROXY_TARGET_VERSION,
        }


