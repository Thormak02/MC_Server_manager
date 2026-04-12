from pathlib import Path

from app.providers.base.server_provider_base import ServerProviderBase
from app.providers.server.common import (
    download_file,
    list_release_versions,
    offline_mode_enabled,
    write_placeholder_jar,
)
from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class SpigotProvider(ServerProviderBase):
    provider_name = "spigot"
    default_mc_version = "1.20.6"
    _buildtools_url = (
        "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
    )

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
        common = ["1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.7.10"]
        return [VersionInfo(id=item, label=item, stable=True) for item in common]

    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        notes: list[str] = []
        if offline_mode_enabled():
            jar_path = target_dir / "spigot.jar"
            write_placeholder_jar(jar_path, f"spigot-{request.mc_version}")
            return ProvisionResult(
                server_jar_path=str(jar_path),
                notes=["Offline-Modus: Platzhalterdatei erstellt."],
            )

        buildtools_dir = target_dir / "buildtools"
        buildtools_jar = buildtools_dir / "BuildTools.jar"
        download_file(self._buildtools_url, buildtools_jar)

        helper_script = buildtools_dir / "build_spigot.bat"
        helper_script.write_text(
            "@echo off\n"
            f"java -jar BuildTools.jar --rev {request.mc_version}\n",
            encoding="utf-8",
        )
        notes.append(
            "BuildTools heruntergeladen. Fuehre buildtools\\build_spigot.bat aus, um spigot.jar zu erzeugen."
        )

        # Target name expected after BuildTools run.
        jar_path = target_dir / f"spigot-{request.mc_version}.jar"
        return ProvisionResult(server_jar_path=str(jar_path), notes=notes)

    def generate_start_command(self, request: ProvisionServerRequest, jar_name: str) -> str:
        extra = ""
        if request.start_parameters:
            extra = f" {request.start_parameters.strip()}"
        return (
            f"java -Xms{request.memory_min_mb}M -Xmx{request.memory_max_mb}M "
            f"-jar {jar_name} nogui{extra}"
        )
