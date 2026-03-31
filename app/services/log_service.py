from pathlib import Path

from app.services.console_service import console_service
from app.services.process_service import get_log_directory_for_server


def list_log_files(server_id: int) -> list[str]:
    log_dir = get_log_directory_for_server(server_id)
    files = sorted(log_dir.glob("*.log"), key=lambda item: item.stat().st_mtime, reverse=True)
    return [file.name for file in files]


def read_log_file(server_id: int, filename: str, limit_lines: int = 800) -> list[str]:
    if not filename:
        return []

    log_dir = get_log_directory_for_server(server_id).resolve()
    file_path = (log_dir / filename).resolve()
    if log_dir not in file_path.parents and file_path != log_dir:
        raise ValueError("Ungueltiger Logdatei-Pfad.")
    if not file_path.exists() or not file_path.is_file():
        raise ValueError("Logdatei nicht gefunden.")

    with file_path.open("r", encoding="utf-8", errors="ignore") as handle:
        lines = [line.rstrip("\r\n") for line in handle.readlines()]
    if limit_lines > 0:
        return lines[-limit_lines:]
    return lines


def get_console_lines(server_id: int, limit_lines: int = 800) -> list[str]:
    return console_service.get_recent_lines(server_id, limit=limit_lines)


def filter_lines(lines: list[str], query: str | None) -> list[str]:
    if not query:
        return lines
    pattern = query.lower().strip()
    if not pattern:
        return lines
    return [line for line in lines if pattern in line.lower()]
