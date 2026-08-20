"""Auto-Lobby: mit einem Klick einen fertig konfigurierten Gateway-Lobby-Server
anlegen (Paper, stabiles 1.21.x), als Gateway-Default markieren (die blanke Domain
landet dort) und eine ruhige Lobby-Welt setzen.
"""

from __future__ import annotations

import shutil
from pathlib import Path

from sqlalchemy.orm import Session

# Fertig kompiliertes Transfer-Plugin (siehe app/assets/lobby_plugin/BUILD.md).
_PLUGIN_ASSET_DIR = Path(__file__).resolve().parents[1] / "assets" / "lobby_plugin"
_PLUGIN_JAR = _PLUGIN_ASSET_DIR / "MCSMLobby.jar"

# Item-Palette fuer die GUI (nur Optik) - je nach Servertyp.
_TYPE_MATERIAL = {
    "paper": "PAPER",
    "purpur": "PURPUR_BLOCK",
    "spigot": "GRASS_BLOCK",
    "bukkit": "GRASS_BLOCK",
    "vanilla": "GRASS_BLOCK",
    "forge": "ANVIL",
    "neoforge": "ANVIL",
    "fabric": "LOOM",
    "quilt": "LOOM",
}

# Servertypen, die Bukkit-Plugins laden koennen -> hier laesst sich MCSMLobby (fuer
# /lobby, /server, Kompass) installieren. Vanilla/Forge/Fabric koennen das nicht.
_BUKKIT_TYPES = {"paper", "purpur", "spigot", "bukkit", "folia"}

# Ruhige Lobby-Welt (Flat, kein Kampf/Monster) via server.properties.
_LOBBY_PROPERTIES = {
    "level-type": "minecraft:flat",
    "gamemode": "adventure",
    "force-gamemode": "true",
    "difficulty": "peaceful",
    "spawn-monsters": "false",
    "spawn-animals": "false",
    "pvp": "false",
    "allow-nether": "false",
    "generate-structures": "false",
    "spawn-protection": "16",
    "max-players": "100",
    "motd": "Willkommen in der Lobby",
}


def _latest_stable_lobby_version() -> str:
    """Neueste stabile Paper-1.21.x-Version."""
    try:
        from app.providers.server.paper_provider import PaperProvider

        for entry in PaperProvider().list_versions("release"):
            if str(entry.id).startswith("1.21."):
                return str(entry.id)
    except Exception:  # noqa: BLE001
        pass
    return "1.21.1"


def _free_gateway_alias(db: Session, preferred: str = "lobby") -> str:
    from app.services import server_service

    if not server_service.gateway_hostname_taken(db, preferred):
        return preferred
    index = 2
    while server_service.gateway_hostname_taken(db, f"{preferred}{index}"):
        index += 1
    return f"{preferred}{index}"


def _write_setup_readme(base_path: Path, member_aliases: list[str]) -> None:
    servers = ", ".join(member_aliases) if member_aliases else "(noch keine weiteren)"
    text = (
        "LOBBY - Gateway-Netzwerk\n"
        "========================\n\n"
        "Diese Lobby ist als Gateway-Default markiert: Verbindungen mit der blanken\n"
        "Domain landen hier. Alle Server laufen parallel und bleiben direkt erreichbar.\n\n"
        "Server erreichen:\n"
        "  - <alias>.<deine-domain>   (ueber das Gateway zum passenden Server)\n"
        "  - <server-ip>:<port>       (Direktverbindung, parallel)\n\n"
        f"Aktuelle Gateway-Aliase: {servers}\n\n"
        "Begehbare Transfer-Lobby (Plugin MCSMLobby, ab Client 1.20.5):\n"
        "  - Kompass (Rechtsklick) oeffnet das Server-Menue\n"
        "  - /server <alias>, /hub, /servers\n"
        "  - Schild bauen: Zeile 1 [server], Zeile 2 = Alias -> Rechtsklick\n"
        "  - Begehbare Portale: plugins/MCSMLobby/config.yml unter 'regions' Quader\n"
        "    (world, min:[x,y,z], max:[x,y,z], target: <alias>) eintragen -> reinlaufen\n"
        "Die Server-Liste im Menue erzeugt der Manager automatisch aus den Aliassen.\n"
    )
    try:
        (base_path / "LOBBY-SETUP.txt").write_text(text, encoding="utf-8")
    except Exception:  # noqa: BLE001
        pass


def _build_plugin_servers(db: Session, exclude_id: int) -> tuple[list[dict], list[str]]:
    """Server-Eintraege fuer die Plugin-config aus den Gateway-Routen bauen.

    Ziel jeder Verbindung ist die Gateway-Subdomain ``<alias>.<domain>`` auf dem
    Netzwerk-Port - so laeuft der Transfer ueber dasselbe Gateway (jeder Servertyp,
    Direktverbindung + Sleep-Wake bleiben erhalten). ``exclude_id`` ist der Server, auf
    dem das Plugin laeuft (er darf sich nicht selbst im Menue anbieten). Gibt zusaetzlich
    die Namen der Server zurueck, die mangels Domain/Alias uebersprungen wurden.

    WICHTIG: eine **Liste** (jeder Eintrag mit ``key``-Feld), KEINE Map mit Alias als
    Schluessel. Bukkit-YAML behandelt '.' im Schluessel als Pfad-Trenner - ein Alias
    wie ``1.21.11-spigot`` als Map-Key wuerde im Plugin in ``1 -> 21 -> 11-spigot``
    zerfallen und nie geladen. Als Listen-Wert bleibt der Alias intakt.
    """
    from sqlalchemy import select

    from app.models.server import Server as _Server
    from app.services import app_setting_service, gateway_service

    domain = gateway_service.clean_hostname(app_setting_service.get_network_domain(db))
    network_port = app_setting_service.get_network_port(db)

    servers: list[dict] = []
    skipped: list[str] = []
    rows = db.scalars(
        select(_Server).where(_Server.gateway_enabled.is_(True))
    ).all()
    for srv in rows:
        if srv.id == exclude_id:
            continue
        alias = gateway_service.clean_hostname(srv.gateway_hostname)
        # Ohne Port hat das Gateway keine Route (build_gateway_routes ueberspringt
        # portlose Server) -> ein Menue-Eintrag wuerde nur zur Lobby zurueckwerfen.
        if not alias or not domain or not srv.port:
            skipped.append(srv.name)
            continue
        material = _TYPE_MATERIAL.get(str(srv.server_type or "").lower(), "GRASS_BLOCK")
        label = f"&a{srv.name}"
        if srv.mc_version:
            label += f" &7({srv.server_type} {srv.mc_version})"
        servers.append({
            "key": alias,
            "display": label,
            "host": f"{alias}.{domain}",
            "port": int(network_port),
            "material": material,
            "sleep": bool(srv.sleep_enabled),
        })
    return servers, skipped


def _lobby_transfer_target(db: Session) -> dict | None:
    """Adresse der Default-Lobby fuer ``/lobby`` (``<alias>.<domain>:<network_port>``).

    None, wenn keine Lobby / kein Alias / keine Domain gesetzt ist.
    """
    from sqlalchemy import select

    from app.models.server import Server as _Server
    from app.services import app_setting_service, gateway_service

    lobby = db.scalar(select(_Server).where(_Server.gateway_is_default.is_(True)))
    if lobby is None:
        return None
    alias = gateway_service.clean_hostname(lobby.gateway_hostname)
    domain = gateway_service.clean_hostname(app_setting_service.get_network_domain(db))
    if not alias or not domain:
        return None
    return {
        "id": lobby.id,
        "host": f"{alias}.{domain}",
        "port": int(app_setting_service.get_network_port(db)),
    }


def _write_plugin_for_server(db: Session, server, *, is_lobby: bool, lobby_target: dict | None) -> tuple[bool, str]:
    """Jar + config.yml fuer EINEN Gateway-Bukkit-Server schreiben (idempotent).

    - Kompass nur auf der Lobby (auf Gameplay-Servern nervt ein Auto-Kompass).
    - ``/lobby`` ueberall wo ein Ziel bekannt ist und der Server nicht selbst die
      Lobby ist.
    - Bestehende ``regions``/``cooldown_ms`` bleiben erhalten; bei kaputter config
      wird abgebrochen statt ueberschrieben (schuetzt Nutzer-Portale).
    """
    import yaml

    base = Path(server.base_path).expanduser().resolve()
    plugins_dir = base / "plugins"
    data_dir = plugins_dir / "MCSMLobby"
    try:
        data_dir.mkdir(parents=True, exist_ok=True)
    except Exception as exc:  # noqa: BLE001
        return False, f"{server.name}: Plugin-Ordner nicht erstellbar: {exc}"

    notes: list[str] = []
    if _PLUGIN_JAR.exists():
        try:
            shutil.copy2(_PLUGIN_JAR, plugins_dir / "MCSMLobby.jar")
        except (PermissionError, OSError):
            notes.append("Jar gesperrt (laeuft) - Update beim Neustart")
    else:
        notes.append("MCSMLobby.jar fehlt in Assets")

    cfg_path = data_dir / "config.yml"
    existing: dict = {}
    if cfg_path.exists():
        try:
            raw = cfg_path.read_text(encoding="utf-8")
        except OSError as exc:
            return False, f"{server.name}: config.yml nicht lesbar ({exc}) - nicht ueberschrieben."
        try:
            parsed = yaml.safe_load(raw)
        except yaml.YAMLError as exc:
            return False, f"{server.name}: config.yml YAML-Fehler ({exc}) - nicht ueberschrieben."
        existing = parsed if isinstance(parsed, dict) else {}

    servers, _skipped = _build_plugin_servers(db, server.id)
    # /lobby-Ziel: nur wenn dieser Server NICHT selbst die Lobby ist.
    lobby_cfg = {"host": "", "port": 25565}
    if lobby_target and not is_lobby:
        lobby_cfg = {"host": lobby_target["host"], "port": lobby_target["port"]}

    status_cfg = existing.get("status")
    if not isinstance(status_cfg, dict):
        status_cfg = {"enabled": True, "interval_seconds": 8}
    config = {
        "cooldown_ms": existing.get("cooldown_ms", 3000),
        "messages": {"transfer": "&aVerbinde zu &e%server%&a..."},
        "compass": {"enabled": bool(is_lobby), "slot": 4, "name": "&bServer-Auswahl &7(Rechtsklick)"},
        "gui": {"title": "Server auswaehlen", "rows": 3},
        "lobby": lobby_cfg,
        "status": status_cfg,
        "servers": servers,
        "regions": existing.get("regions", []) or [],
    }
    try:
        cfg_path.write_text(
            yaml.safe_dump(config, sort_keys=False, allow_unicode=True), encoding="utf-8"
        )
    except Exception as exc:  # noqa: BLE001
        return False, f"{server.name}: config nicht schreibbar: {exc}"

    suffix = f" ({'; '.join(notes)})" if notes else ""
    return True, f"{server.name}: {len(servers)} Server im Menue{suffix}"


def sync_lobby_plugin(db: Session) -> tuple[bool, str]:
    """Transfer-Plugin (MCSMLobby) auf ALLEN Gateway-Bukkit-Servern angleichen.

    - Lobby: Kompass-Menue + /server/hub, alle anderen Server im Menue.
    - Andere Bukkit-Server (Paper/Spigot/Purpur/...): ``/lobby`` zurueck zur Lobby,
      plus /server/hub-Menue; Kompass aus. So kommt man von ueberall zurueck.
    - Vanilla/Forge/Fabric koennen keine Plugins laden -> werden uebersprungen.

    No-op, wenn keine Default-Lobby existiert. Idempotent.
    """
    from sqlalchemy import select

    from app.models.server import Server as _Server

    lobby = db.scalar(select(_Server).where(_Server.gateway_is_default.is_(True)))
    if lobby is None:
        return False, "Keine Gateway-Lobby vorhanden."

    lobby_target = _lobby_transfer_target(db)
    rows = db.scalars(select(_Server).where(_Server.gateway_enabled.is_(True))).all()

    done: list[str] = []
    failed: list[str] = []
    non_bukkit: list[str] = []
    for srv in rows:
        if str(srv.server_type or "").lower() not in _BUKKIT_TYPES:
            non_bukkit.append(srv.name)
            continue
        ok, detail = _write_plugin_for_server(
            db, srv, is_lobby=(srv.id == lobby.id), lobby_target=lobby_target
        )
        (done if ok else failed).append(detail)

    msg = f"Transfer-Plugin auf {len(done)} Bukkit-Server(n) aktualisiert."
    if non_bukkit:
        msg += f" Kein Plugin moeglich (Vanilla/Forge/Fabric): {', '.join(non_bukkit)}."
    if failed:
        msg += " Fehler: " + " | ".join(failed)
    ok_overall = bool(done) or not failed
    return ok_overall, msg


def create_auto_lobby(db: Session, *, initiated_by_user_id: int | None) -> tuple[bool, str, int | None]:
    """Erstellt (oder findet) einen fertig konfigurierten Gateway-Lobby-Server.

    Idempotent: existiert bereits eine Lobby (gateway_is_default), wird sie
    wiederverwendet. Der Netzwerk-Modus wird ERST nach erfolgreichem Anlegen auf
    ``gateway`` gestellt.
    """
    from sqlalchemy import select

    from app.models.server import Server as _Server
    from app.schemas.provider import ProvisionServerRequest
    from app.services import app_setting_service, audit_service, server_service
    from app.services.provisioning_service import ProvisioningService

    existing = db.scalar(select(_Server).where(_Server.gateway_is_default.is_(True)))
    if existing is not None:
        app_setting_service.set_network_mode(db, "gateway")
        try:
            from app.services import gateway_service

            gateway_service.reconcile_gateway()
        except Exception:  # noqa: BLE001
            pass
        try:
            sync_lobby_plugin(db)
        except Exception:  # noqa: BLE001
            pass
        return (
            True,
            f"Lobby '{existing.name}' ist bereits eingerichtet (Netzwerk-Modus: gateway).",
            existing.id,
        )

    alias = _free_gateway_alias(db, "lobby")
    version = _latest_stable_lobby_version()

    # Lobby-Port darf nicht der Gateway-Port sein (den belegt das Gateway).
    from app.services import port_service

    network_port = app_setting_service.get_network_port(db)
    try:
        lobby_port = port_service.allocate_server_port(db, exclude={network_port})
    except ValueError:
        lobby_port = None

    try:
        server, _notes = ProvisioningService().create_server_instance(
            db,
            ProvisionServerRequest(
                name="Lobby",
                server_type="paper",
                mc_version=version,
                target_path="",
                memory_min_mb=1024,
                memory_max_mb=2048,
                port=lobby_port,
            ),
        )
    except Exception as exc:  # noqa: BLE001
        return False, f"Lobby-Server konnte nicht erstellt werden: {exc}", None

    try:
        _srv, warnings = server_service.update_server_settings(
            db,
            server,
            mc_version=version,
            loader_version=None,
            java_profile_id=server.java_profile_id,
            memory_min_mb=server.memory_min_mb,
            memory_max_mb=server.memory_max_mb,
            port=server.port,
            auto_restart=False,
            auto_start_with_manager=True,
            start_mode=server.start_mode,
            start_command=server.start_command,
            start_bat_path=server.start_bat_path,
            sleep_enabled=False,
            sleep_delay_seconds=None,
            gateway_enabled=True,
            gateway_hostname=alias,
            gateway_is_default=True,
        )
    except Exception as exc:  # noqa: BLE001
        return False, f"Lobby '{server.name}' angelegt, aber Konfiguration fehlgeschlagen: {exc}", server.id
    db.refresh(server)

    if not server.gateway_enabled or not server.gateway_is_default:
        detail = "; ".join(warnings) if warnings else "unbekannter Grund"
        return (
            False,
            f"Lobby '{server.name}' angelegt, aber Gateway-Konfiguration fehlgeschlagen: {detail}",
            server.id,
        )

    try:
        base_path = Path(server.base_path).expanduser().resolve()
        for key, value in _LOBBY_PROPERTIES.items():
            server_service._upsert_server_property(server, key, value)
        member_aliases = [
            s.gateway_hostname
            for s in db.scalars(select(_Server).where(_Server.gateway_enabled.is_(True))).all()
            if s.id != server.id and s.gateway_hostname
        ]
        _write_setup_readme(base_path, member_aliases)
    except Exception:  # noqa: BLE001
        pass

    app_setting_service.set_network_mode(db, "gateway")
    try:
        from app.services import gateway_service

        gateway_service.reconcile_gateway()
    except Exception:  # noqa: BLE001
        pass
    try:
        sync_lobby_plugin(db)
    except Exception:  # noqa: BLE001
        pass
    # Multi-Version (ViaVersion) best-effort mitliefern, damit sich JEDE Client-Version
    # mit der Lobby verbinden kann. Nur online (im Offline-/Test-Modus ueberspringen).
    via_note = ""
    try:
        from app.providers.server.common import offline_mode_enabled
        from app.services import viaversion_service

        if not offline_mode_enabled():
            ok_via, _ = viaversion_service.install_multiversion(server.base_path, version)
            via_note = " Multi-Version (ViaVersion) installiert." if ok_via else ""
    except Exception:  # noqa: BLE001
        pass

    audit_service.log_action(
        db,
        action="lobby.auto_create",
        user_id=initiated_by_user_id,
        server_id=server.id,
        details=f"version={version} alias={alias}",
    )

    warn_suffix = f" Hinweise: {'; '.join(warnings)}." if warnings else ""
    message = (
        f"Lobby '{server.name}' erstellt (Paper {version}), als Gateway-Default markiert "
        f"(Alias '{alias}'). Jetzt starten – erreichbar ueber die blanke Domain oder "
        f"'{alias}.<domain>'.{via_note}{warn_suffix}"
    )
    return True, message, server.id
