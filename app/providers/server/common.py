import json
import urllib.request
from pathlib import Path

from app.core.config import get_settings


USER_AGENT = "mc-server-manager/1.0"


def fetch_json(url: str, timeout_seconds: float = 20.0) -> dict | list:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        payload = response.read().decode("utf-8")
    return json.loads(payload)


def download_file(url: str, target_file: Path, timeout_seconds: float = 60.0) -> None:
    target_file.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        data = response.read()
    target_file.write_bytes(data)


def offline_mode_enabled() -> bool:
    return get_settings().provisioning_offline_mode


def write_placeholder_jar(target_file: Path, marker: str) -> None:
    target_file.parent.mkdir(parents=True, exist_ok=True)
    # Test/offline marker file; not a runnable jar.
    target_file.write_text(
        f"PLACEHOLDER for {marker}\n",
        encoding="utf-8",
    )
