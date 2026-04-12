from pathlib import Path

from app.providers.base.server_provider_base import ServerProviderBase
from app.providers.server.common import download_file, fetch_json, offline_mode_enabled, write_placeholder_jar
from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class PaperProvider(ServerProviderBase):
    provider_name = "paper"
    default_mc_version = "1.20.6"
    _api_base = "https://api.papermc.io/v2/projects/paper"

    def list_versions(self) -> list[VersionInfo]:
        try:
            data = fetch_json(self._api_base)
            raw_versions = data.get("versions", [])
            versions = [
                VersionInfo(id=str(version), label=str(version), stable=True)
                for version in reversed(raw_versions)
            ]
            if versions:
                return versions
        except Exception:
            pass
        return [VersionInfo(id=self.default_mc_version, label=self.default_mc_version)]

    def _resolve_download(self, mc_version: str) -> tuple[str, str]:
        builds_data = fetch_json(f"{self._api_base}/versions/{mc_version}/builds")
        builds = builds_data.get("builds", [])
        if not builds:
            raise ValueError(f"Keine Paper-Builds fuer {mc_version} gefunden.")

        build = sorted(builds, key=lambda item: int(item.get("build", 0)))[-1]
        downloads = build.get("downloads", {})
        application = downloads.get("application")
        if not application:
            raise ValueError("Paper Build ohne application-Download gefunden.")

        file_name = str(application.get("name"))
        build_id = int(build.get("build"))
        url = (
            f"{self._api_base}/versions/{mc_version}/builds/{build_id}/downloads/{file_name}"
        )
        return url, file_name

    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        if offline_mode_enabled():
            jar_path = target_dir / "paper.jar"
            write_placeholder_jar(jar_path, f"paper-{request.mc_version}")
            return ProvisionResult(
                server_jar_path=str(jar_path),
                notes=["Offline-Modus: Platzhalterdatei erstellt."],
            )

        url, file_name = self._resolve_download(request.mc_version)
        jar_path = target_dir / file_name
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
