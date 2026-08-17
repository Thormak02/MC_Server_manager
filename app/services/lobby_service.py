"""Auto-Lobby: mit einem Klick einen fertig konfigurierten Velocity-Lobby-Server
anlegen (Paper, stabiles 1.21.x), als Netzwerk-Lobby markieren, ruhige Lobby-Welt
setzen und einen Server-Auswahl-Plugin (ServerSelector) mitinstallieren.
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy.orm import Session

from app.providers.server.common import download_file, fetch_json

# Modrinth-Plugin fuer die Server-Auswahl (Kompass-/GUI-Menu). Paper-kompatibel.
_SELECTOR_SLUG = "serverselector"

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
    """Neueste stabile Paper-1.21.x-Version (breit von ViaVersion unterstuetzt)."""
    try:
        from app.providers.server.paper_provider import PaperProvider

        for entry in PaperProvider().list_versions("release"):
            if str(entry.id).startswith("1.21."):
                return str(entry.id)
    except Exception:  # noqa: BLE001
        pass
    return "1.21.1"


def _resolve_selector_download() -> tuple[str, str] | None:
    """Neueste Paper-taugliche ServerSelector-Version von Modrinth (Release
    bevorzugt)."""
    try:
        versions = fetch_json(f"https://api.modrinth.com/v2/project/{_SELECTOR_SLUG}/version")
    except Exception:  # noqa: BLE001
        return None
    if not isinstance(versions, list):
        return None

    def paper_ok(entry: dict) -> bool:
        loaders = entry.get("loaders") or []
        return isinstance(entry, dict) and ("paper" in loaders or "bukkit" in loaders)

    releases = [v for v in versions if paper_ok(v) and v.get("version_type") == "release"]
    pick = releases[0] if releases else next((v for v in versions if paper_ok(v)), None)
    if not pick:
        return None
    files = pick.get("files") or []
    chosen = next((f for f in files if f.get("primary")), files[0] if files else None)
    if not chosen or not chosen.get("url"):
        return None
    return str(chosen["url"]), str(chosen.get("filename") or "ServerSelector.jar")


def _install_selector_plugin(base_path: Path) -> str:
    plugins = base_path / "plugins"
    try:
        plugins.mkdir(parents=True, exist_ok=True)
    except Exception as exc:  # noqa: BLE001
        return f"Plugin-Ordner nicht anlegbar: {exc}"
    if any(plugins.glob("ServerSelector*.jar")):
        return "ServerSelector-Plugin bereits vorhanden."
    resolved = _resolve_selector_download()
    if not resolved:
        return "ServerSelector-Plugin nicht gefunden (spaeter manuell installierbar)."
    url, filename = resolved
    try:
        download_file(url, plugins / filename, timeout_seconds=120.0)
    except Exception as exc:  # noqa: BLE001
        return f"ServerSelector-Plugin konnte nicht geladen werden: {exc}"
    return f"ServerSelector-Plugin installiert ({filename})."


def _write_setup_readme(base_path: Path, member_names: list[str]) -> None:
    servers = ", ".join(member_names) if member_names else "(noch keine weiteren Server)"
    text = (
        "LOBBY – Velocity-Netzwerk\n"
        "=========================\n\n"
        "Diese Lobby ist bereits als Netzwerk-Lobby markiert (Sleep aus).\n\n"
        "Im Spiel zwischen Servern wechseln:\n"
        "  - /server <name>        (bringt Velocity von Haus aus mit)\n"
        "  - <name>.<deine-domain> (Direktverbindung)\n\n"
        f"Aktuelle Netzwerk-Server: {servers}\n\n"
        "Fuer Klick-Wechsel (Kompass/GUI) ist das Plugin 'ServerSelector' in\n"
        "plugins/ installiert. Nach dem ersten Start erzeugt es seine Konfiguration\n"
        "unter plugins/ServerSelector/ – dort die Server (Namen wie oben) eintragen.\n"
        "Alternativ Schilder/Portale ueber ein passendes Plugin (Mods & Inhalte).\n"
    )
    try:
        (base_path / "LOBBY-SETUP.txt").write_text(text, encoding="utf-8")
    except Exception:  # noqa: BLE001
        pass


def _free_velocity_name(db: Session, preferred: str = "lobby") -> str:
    from app.services import server_service

    if not server_service.velocity_name_taken(db, preferred):
        return preferred
    index = 2
    while server_service.velocity_name_taken(db, f"{preferred}{index}"):
        index += 1
    return f"{preferred}{index}"


def create_auto_lobby(db: Session, *, initiated_by_user_id: int | None) -> tuple[bool, str, int | None]:
    """Erstellt (oder findet) eine fertig konfigurierte Velocity-Lobby.

    Idempotent: existiert bereits eine Lobby, wird sie wiederverwendet (kein
    zweiter Server). Der Netzwerk-Modus wird ERST nach erfolgreichem Anlegen auf
    ``velocity`` gestellt (kein Stranden im velocity-Modus ohne Lobby).
    """
    from sqlalchemy import select

    from app.models.server import Server as _Server
    from app.schemas.provider import ProvisionServerRequest
    from app.services import app_setting_service, audit_service, server_service
    from app.services.provisioning_service import ProvisioningService

    # 0. Bereits eine Lobby vorhanden? -> wiederverwenden (idempotent).
    existing = db.scalar(select(_Server).where(_Server.velocity_is_lobby.is_(True)))
    if existing is not None:
        app_setting_service.set_network_mode(db, "velocity")
        try:
            from app.services import velocity_service

            velocity_service.sync_velocity_config(db)
        except Exception:  # noqa: BLE001
            pass
        return (
            True,
            f"Lobby '{existing.name}' ist bereits eingerichtet (Netzwerk-Modus: velocity).",
            existing.id,
        )

    lobby_name = _free_velocity_name(db, "lobby")
    version = _latest_stable_lobby_version()

    # 1. Paper-Lobby provisionieren (Jar wird geladen). Modus NOCH NICHT umstellen,
    #    damit ein Fehlschlag den Manager nicht im velocity-Modus ohne Lobby laesst.
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
            ),
        )
    except Exception as exc:  # noqa: BLE001
        return False, f"Lobby-Server konnte nicht erstellt werden: {exc}", None

    # 2. Als Netzwerk-Lobby markieren (Sleep aus, Auto-Start an) + Warnungen pruefen.
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
            velocity_enabled=True,
            velocity_name=lobby_name,
            velocity_is_lobby=True,
        )
    except Exception as exc:  # noqa: BLE001
        return False, f"Lobby '{server.name}' angelegt, aber Konfiguration fehlgeschlagen: {exc}", server.id
    db.refresh(server)

    if not server.velocity_enabled or not server.velocity_is_lobby:
        detail = "; ".join(warnings) if warnings else "unbekannter Grund"
        return (
            False,
            f"Lobby '{server.name}' angelegt, aber Netzwerk-Konfiguration fehlgeschlagen: {detail}",
            server.id,
        )

    # 3. Ruhige Lobby-Welt + Server-Auswahl-Plugin + Setup-Hinweis (best-effort).
    plugin_note = ""
    try:
        base_path = Path(server.base_path).expanduser().resolve()
        for key, value in _LOBBY_PROPERTIES.items():
            server_service._upsert_server_property(server, key, value)
        plugin_note = _install_selector_plugin(base_path)
        member_names = [
            (s.velocity_name or s.slug)
            for s in db.scalars(select(_Server).where(_Server.velocity_enabled.is_(True))).all()
            if s.id != server.id
        ]
        _write_setup_readme(base_path, member_names)
    except Exception as exc:  # noqa: BLE001
        plugin_note = f"(Lobby-Welt/Plugin nur teilweise gesetzt: {exc})"

    # 4. Jetzt (nach Erfolg) den Netzwerk-Modus auf velocity stellen + Config bauen.
    app_setting_service.set_network_mode(db, "velocity")
    try:
        from app.services import velocity_service

        velocity_service.sync_velocity_config(db)
    except Exception:  # noqa: BLE001
        pass

    audit_service.log_action(
        db,
        action="lobby.auto_create",
        user_id=initiated_by_user_id,
        server_id=server.id,
        details=f"version={version} name={lobby_name}",
    )

    warn_suffix = f" Hinweise: {'; '.join(warnings)}." if warnings else ""
    message = (
        f"Lobby '{server.name}' erstellt (Paper {version}), als Netzwerk-Lobby markiert. "
        f"{plugin_note} Jetzt starten – Wechsel per /server oder Kompass (ServerSelector).{warn_suffix}"
    )
    return True, message, server.id
