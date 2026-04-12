import json
import urllib.request
from pathlib import Path

from app.core.config import get_settings


USER_AGENT = "mc-server-manager/1.0"
_VANILLA_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def fetch_json(url: str, timeout_seconds: float = 20.0) -> dict | list:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        payload = response.read().decode("utf-8")
    return json.loads(payload)


def _parse_version_tuple(version: str) -> tuple[int, ...]:
    parts: list[int] = []
    for item in version.split("."):
        try:
            parts.append(int(item))
        except Exception:
            parts.append(0)
    return tuple(parts)


def is_version_at_least(version: str, minimum: str) -> bool:
    left = _parse_version_tuple(version)
    right = _parse_version_tuple(minimum)
    max_len = max(len(left), len(right))
    left += (0,) * (max_len - len(left))
    right += (0,) * (max_len - len(right))
    return left >= right


def list_release_versions(
    *,
    minimum: str = "1.7.10",
    limit: int | None = None,
) -> list[str]:
    data = fetch_json(_VANILLA_MANIFEST)
    results: list[str] = []
    for item in data.get("versions", []):
        if item.get("type") != "release":
            continue
        version_id = str(item.get("id") or "")
        if not version_id:
            continue
        if not is_version_at_least(version_id, minimum):
            break
        results.append(version_id)
        if limit and len(results) >= limit:
            break
    return results


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
