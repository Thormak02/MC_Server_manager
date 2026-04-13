from pathlib import Path

from app.models.server import Server
from app.services.console_service import console_service
from app.services.process_service import get_log_directory_for_server


_LOG_LEVELS = {"all", "warning", "error"}


def _is_inside(base: Path, target: Path) -> bool:
    return target == base or base in target.parents


def _server_logs_dir(server: Server) -> Path:
    return (Path(server.base_path).expanduser().resolve() / "logs").resolve()


def _runtime_logs_dir(server: Server) -> Path:
    return get_log_directory_for_server(server.id).resolve()


def list_log_files(server: Server, *, max_files: int = 200) -> list[dict[str, object]]:
    entries: list[dict[str, object]] = []

    runtime_dir = _runtime_logs_dir(server)
    for file in runtime_dir.glob("*.log"):
        if not file.is_file():
            continue
        stats = file.stat()
        entries.append(
            {
                "key": f"runtime:{file.name}",
                "label": f"Runtime: {file.name}",
                "source": "runtime",
                "name": file.name,
                "size_bytes": stats.st_size,
                "mtime": stats.st_mtime,
            }
        )

    server_log_dir = _server_logs_dir(server)
    if server_log_dir.exists() and server_log_dir.is_dir():
        for file in server_log_dir.rglob("*"):
            if not file.is_file():
                continue
            relative = file.relative_to(server_log_dir).as_posix()
            stats = file.stat()
            entries.append(
                {
                    "key": f"server:{relative}",
                    "label": f"Server: logs/{relative}",
                    "source": "server",
                    "name": relative,
                    "size_bytes": stats.st_size,
                    "mtime": stats.st_mtime,
                }
            )
            if len(entries) >= max_files:
                break

    entries.sort(key=lambda item: float(item.get("mtime", 0.0)), reverse=True)
    return entries[:max_files]


def _resolve_log_file(server: Server, key: str) -> tuple[Path, str]:
    if not key or ":" not in key:
        raise ValueError("Ungueltiger Logdatei-Schluessel.")
    source, value = key.split(":", 1)
    if source not in {"runtime", "server"}:
        raise ValueError("Unbekannte Logquelle.")

    if source == "runtime":
        base = _runtime_logs_dir(server)
        path = (base / value).resolve()
        label = f"Runtime: {value}"
    else:
        base = _server_logs_dir(server)
        path = (base / value).resolve()
        label = f"Server: logs/{value}"

    if not _is_inside(base, path):
        raise ValueError("Ungueltiger Logdatei-Pfad.")
    if not path.exists() or not path.is_file():
        raise ValueError("Logdatei nicht gefunden.")
    return path, label


def read_log_file(server: Server, key: str, limit_lines: int = 800) -> tuple[list[str], str]:
    file_path, label = _resolve_log_file(server, key)
    with file_path.open("r", encoding="utf-8", errors="ignore") as handle:
        lines = [line.rstrip("\r\n") for line in handle.readlines()]
    if limit_lines > 0:
        return lines[-limit_lines:], label
    return lines, label


def get_download_log_file(server: Server, key: str) -> tuple[Path, str]:
    path, label = _resolve_log_file(server, key)
    return path, path.name if path.name else label.replace(":", "-")


def get_console_lines(server_id: int, limit_lines: int = 800) -> list[str]:
    return console_service.get_recent_lines(server_id, limit=limit_lines)


def _matches_log_level(line: str, level: str) -> bool:
    normalized = level if level in _LOG_LEVELS else "all"
    if normalized == "all":
        return True
    lower = line.lower()
    if normalized == "error":
        return any(token in lower for token in ["error", "exception", "fatal", "traceback"])
    if normalized == "warning":
        return any(token in lower for token in ["warn", "warning"])
    return True


def filter_lines(
    lines: list[str],
    query: str | None,
    *,
    level: str = "all",
) -> list[str]:
    pattern = (query or "").lower().strip()
    filtered = [
        line
        for line in lines
        if _matches_log_level(line, level)
        and (not pattern or pattern in line.lower())
    ]
    return filtered
