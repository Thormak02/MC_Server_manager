from datetime import datetime, timezone
from typing import Any

from sqlalchemy.orm import Session

from app.models.user import User
from app.services.process_service import get_process_resource_usage
from app.services.resource_service import get_host_resources
from app.services.server_service import list_servers_for_user

try:
    import psutil  # type: ignore[import-not-found]
except Exception:  # pragma: no cover
    psutil = None  # type: ignore[assignment]


def _collect_disks() -> list[dict[str, Any]]:
    if psutil is None:
        return []
    disks: list[dict[str, Any]] = []
    seen_mounts: set[str] = set()
    for partition in psutil.disk_partitions(all=False):
        mountpoint = partition.mountpoint
        if mountpoint in seen_mounts:
            continue
        seen_mounts.add(mountpoint)
        try:
            usage = psutil.disk_usage(mountpoint)
        except Exception:
            continue
        disks.append(
            {
                "device": partition.device,
                "mountpoint": mountpoint,
                "fstype": partition.fstype,
                "total_gb": round(usage.total / (1024 ** 3), 2),
                "used_gb": round(usage.used / (1024 ** 3), 2),
                "free_gb": round(usage.free / (1024 ** 3), 2),
                "percent": float(usage.percent),
            }
        )
    return disks


def get_managed_processes(db: Session, user: User) -> list[dict[str, Any]]:
    servers = list_servers_for_user(db, user)
    payload: list[dict[str, Any]] = []
    for server in servers:
        usage = get_process_resource_usage(server.id)
        payload.append(
            {
                "server_id": server.id,
                "server_name": server.name,
                "status": server.status,
                "pid": usage.get("pid"),
                "cpu_percent": usage.get("cpu_percent"),
                "memory_mb": usage.get("memory_mb"),
                "uptime_seconds": usage.get("uptime_seconds"),
                "running": bool(usage.get("running")),
            }
        )
    payload.sort(key=lambda row: ((row.get("running") is False), -(row.get("cpu_percent") or 0.0)))
    return payload


def get_system_summary(db: Session, user: User) -> dict[str, Any]:
    host = get_host_resources()
    managed = get_managed_processes(db, user)
    running_managed = sum(1 for row in managed if row.get("running"))
    return {
        "host": host,
        "disks": _collect_disks(),
        "managed_total": len(managed),
        "managed_running": running_managed,
        "generated_at": datetime.now(timezone.utc).isoformat(),
    }


def get_host_processes(*, limit: int = 50) -> list[dict[str, Any]]:
    if psutil is None:
        return []
    safe_limit = max(1, min(limit, 500))
    rows: list[dict[str, Any]] = []
    for proc in psutil.process_iter(["pid", "name", "username", "cpu_percent", "memory_info", "create_time"]):
        try:
            info = proc.info
            mem = info.get("memory_info")
            mem_mb = float(mem.rss / (1024 * 1024)) if mem else 0.0
            rows.append(
                {
                    "pid": int(info.get("pid") or 0),
                    "name": str(info.get("name") or ""),
                    "username": str(info.get("username") or ""),
                    "cpu_percent": float(info.get("cpu_percent") or 0.0),
                    "memory_mb": mem_mb,
                    "create_time": float(info.get("create_time") or 0.0),
                }
            )
        except Exception:
            continue
    rows.sort(key=lambda row: (-(row.get("cpu_percent") or 0.0), -(row.get("memory_mb") or 0.0)))
    return rows[:safe_limit]

