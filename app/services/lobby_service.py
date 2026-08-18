"""Auto-Lobby: mit einem Klick einen fertig konfigurierten Gateway-Lobby-Server
anlegen (Paper, stabiles 1.21.x), als Gateway-Default markieren (die blanke Domain
landet dort) und eine ruhige Lobby-Welt setzen.
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy.orm import Session

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
        "Fuer eine begehbare Lobby mit Portal/Schild (Version 1.20.5+) kann spaeter ein\n"
        "Transfer-Plugin ergaenzt werden. Bis dahin verbinden sich Spieler ueber die\n"
        "Adresse ihres Servers.\n"
    )
    try:
        (base_path / "LOBBY-SETUP.txt").write_text(text, encoding="utf-8")
    except Exception:  # noqa: BLE001
        pass


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
        f"'{alias}.<domain>'.{warn_suffix}"
    )
    return True, message, server.id
