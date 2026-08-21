"""Ein-Klick-Einrichtung des Universal-Hubs als Standard-Eingang.

Analog zu ``lobby_service.create_auto_lobby``, aber fuer den Python-Hub: schaltet
Gateway-Modus + Dispatcher + Hub an, prueft Domain und Modpack-Replay und wendet
alles sofort an. Damit wird der Hub die blanke-Domain-Lobby - der Dispatcher leitet
modded UND vanilla auf ``modlobby``/``vanlobby`` (den Hub), statt auf die Paper-Lobby.
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy.orm import Session

from app.services import app_setting_service as A


def create_auto_hub(db: Session, *, initiated_by_user_id: int | None = None) -> tuple[bool, str]:
    """Hub als Standard-Eingang einrichten. Gibt (ok, Nachricht) zurueck."""
    warnings: list[str] = []

    domain = A.get_network_domain(db).strip()
    if not domain:
        warnings.append(
            "Keine Netzwerk-Domain gesetzt - der Hub ist ueber die blanke Domain erst "
            "erreichbar, sobald du unter 'Netzwerk (Lobby)' eine Domain eintraegst."
        )

    # Modpack-Replay pruefen (ohne gueltiges Replay startet der Hub-Listener nicht).
    replay = A.get_hub_lobby_replay(db)
    if not Path(replay).exists():
        warnings.append(
            f"Modpack-Replay '{replay}' nicht gefunden - der Hub startet erst, wenn ein "
            "gueltiges Config-Replay hinterlegt ist (Universal-Lobby-Karte)."
        )

    # Kernschalter: Gateway-Transport + Dispatcher (blanke Domain) + Hub.
    A.set_network_mode(db, "gateway")
    A.set_dispatcher_enabled(db, True)
    A.set_hub_lobby_enabled(db, True)

    # Sofort anwenden: Hub-Listener starten, ViaProxy angleichen, Gateway-Routen neu bauen.
    from app.services import gateway_service, hub_lobby_service, viaproxy_service

    try:
        hub_lobby_service.reconcile_hub_lobby()
        viaproxy_service.reconcile_viaproxy()
        gateway_service.reconcile_gateway()
    except Exception as exc:  # noqa: BLE001
        return False, f"Hub-Einrichtung fehlgeschlagen beim Anwenden: {exc}"

    running = hub_lobby_service.is_running()
    if not running:
        warnings.append(
            "Hub-Listener laeuft (noch) nicht - meist fehlt ein gueltiges Replay."
        )

    if initiated_by_user_id is not None:
        try:
            from app.services import audit_service

            audit_service.log_action(
                db,
                action="hub.auto_create",
                user_id=initiated_by_user_id,
                details=f"running={running} domain={'set' if domain else 'missing'}",
            )
        except Exception:  # noqa: BLE001
            pass

    msg = "Universal-Hub eingerichtet: Gateway-Modus + Dispatcher + Hub aktiv."
    if running:
        msg += " Hub laeuft."
    msg += " Cross-Version (ViaProxy) bleibt ein separater Schalter in der Universal-Lobby-Karte."
    if warnings:
        msg += " Hinweis: " + " ".join(warnings)
    return True, msg
