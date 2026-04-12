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


class FabricProvider(ServerProviderBase):
    provider_name = "fabric"
    default_mc_version = "1.20.6"
    _meta_base = "https://meta.fabricmc.net/v2/versions"

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
        return [VersionInfo(id=self.default_mc_version, label=self.default_mc_version, stable=True)]

    def list_loader_versions(self, mc_version: str) -> list[VersionInfo]:
        try:
            data = fetch_json(f"{self._meta_base}/loader/{mc_version}")
            versions: list[VersionInfo] = []
            seen: set[str] = set()
            for item in data:
                loader = item.get("loader") or {}
                version_id = str(loader.get("version") or "")
                if not version_id or version_id in seen:
                    continue
                seen.add(version_id)
                versions.append(
                    VersionInfo(
                        id=version_id,
                        label=version_id,
                        stable=bool(loader.get("stable", True)),
                    )
                )
            return versions
        except Exception:
            return []

    def _latest_loader(self) -> str:
        loaders = fetch_json(f"{self._meta_base}/loader")
        if not loaders:
            raise ValueError("Keine Fabric Loader Version verfuegbar.")
        return str(loaders[0]["version"])

    def _latest_installer(self) -> str:
        installers = fetch_json(f"{self._meta_base}/installer")
        if not installers:
            raise ValueError("Keine Fabric Installer Version verfuegbar.")
        return str(installers[0]["version"])

    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        jar_path = target_dir / "fabric-server-launch.jar"
        if offline_mode_enabled():
            write_placeholder_jar(jar_path, f"fabric-{request.mc_version}")
            return ProvisionResult(
                server_jar_path=str(jar_path),
                notes=["Offline-Modus: Platzhalterdatei erstellt."],
            )

        loader_version = request.loader_version or self._latest_loader()
        installer_version = self._latest_installer()
        url = (
            f"{self._meta_base}/loader/"
            f"{request.mc_version}/{loader_version}/{installer_version}/server/jar"
        )
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
