from typing import Any

from sqlalchemy.orm import Session

from app.models.user import User
from app.services.process_service import (
    get_online_player_names,
    get_player_counts,
    get_process_resource_usage,
)
from app.services.server_service import list_servers_for_user

try:
    import psutil  # type: ignore[import-not-found]
except Exception:  # pragma: no cover
    psutil = None  # type: ignore[assignment]


def get_host_resources() -> dict[str, Any]:
    if psutil is None:
        return {
            "cpu_logical": None,
            "cpu_percent": None,
            "memory_total_mb": None,
            "memory_used_mb": None,
            "memory_percent": None,
        }

    vm = psutil.virtual_memory()
    return {
        "cpu_logical": psutil.cpu_count(logical=True),
        "cpu_percent": float(psutil.cpu_percent(interval=None)),
        "memory_total_mb": float(vm.total / (1024 * 1024)),
        "memory_used_mb": float(vm.used / (1024 * 1024)),
        "memory_percent": float(vm.percent),
    }


def get_server_resource_entries(db: Session, user: User) -> list[dict[str, Any]]:
    servers = list_servers_for_user(db, user)
    host = get_host_resources()
    total_mem = host.get("memory_total_mb") or 0.0

    entries: list[dict[str, Any]] = []
    for server in servers:
        usage = get_process_resource_usage(server.id)
        players_current, players_max = get_player_counts(server)
        mem_share = 0.0
        if total_mem and usage["memory_mb"]:
            mem_share = float(usage["memory_mb"]) / float(total_mem) * 100.0

        entries.append(
            {
                "server": server,
                "usage": usage,
                "players_current": players_current,
                "players_max": players_max,
                "online_players": get_online_player_names(server.id),
                "memory_share_percent": mem_share,
            }
        )
    return entries
