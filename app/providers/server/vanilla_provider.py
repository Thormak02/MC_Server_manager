from pathlib import Path

from app.providers.base.server_provider_base import ServerProviderBase
from app.providers.server.common import (
    download_file,
    fetch_json,
    list_release_versions,
    offline_mode_enabled,
    write_placeholder_jar,
)
from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class VanillaProvider(ServerProviderBase):
    provider_name = "vanilla"
    default_mc_version = "1.20.6"
    _manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    def list_versions(self) -> list[VersionInfo]:
        try:
            versions = [
                VersionInfo(id=item, label=item, stable=True)
                for item in list_release_versions(minimum="1.7.10")
            ]
            if versions:
                return versions
        except Exception:
            pass
        return [VersionInfo(id=self.default_mc_version, label=self.default_mc_version)]

    def _resolve_server_jar_url(self, mc_version: str) -> str:
        data = fetch_json(self._manifest_url)
        match = next((v for v in data.get("versions", []) if v.get("id") == mc_version), None)
        if not match:
            raise ValueError(f"Vanilla Version '{mc_version}' nicht gefunden.")
        version_data = fetch_json(str(match["url"]))
        try:
            return str(version_data["downloads"]["server"]["url"])
        except Exception as exc:
            raise ValueError(f"Server-Download fuer Version {mc_version} nicht verfuegbar.") from exc

    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        jar_path = target_dir / "server.jar"
        if offline_mode_enabled():
            write_placeholder_jar(jar_path, f"vanilla-{request.mc_version}")
            return ProvisionResult(
                server_jar_path=str(jar_path),
                notes=["Offline-Modus: Platzhalterdatei erstellt."],
            )

        url = self._resolve_server_jar_url(request.mc_version)
        download_file(url, jar_path)
        return ProvisionResult(server_jar_path=str(jar_path))

    def generate_start_command(self, request: ProvisionServerRequest, jar_name: str) -> str:
        extra = ""
        if request.start_parameters:
            extra = f" {request.start_parameters.strip()}"
        return (
            f"java -Xms{request.memory_min_mb}M -Xmx{request.memory_max_mb}M "
            f"-jar {jar_name} nogui{extra}"
        )
