from pathlib import Path

from app.providers.base.server_provider_base import ServerProviderBase
from xml.etree import ElementTree

from app.providers.server.common import (
    download_file,
    fetch_json,
    fetch_text,
    list_release_versions,
    offline_mode_enabled,
    write_placeholder_jar,
)
from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class ForgeProvider(ServerProviderBase):
    provider_name = "forge"
    default_mc_version = "1.20.1"
    _promotions_url = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
    _maven_base = "https://maven.minecraftforge.net/net/minecraftforge/forge"
    _maven_metadata = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"

    def list_versions(self) -> list[VersionInfo]:
        try:
            data = fetch_json(self._promotions_url)
            promos = data.get("promos", {})
            supported: set[str] = set()
            for key in promos:
                if key.endswith("-latest"):
                    supported.add(key.replace("-latest", ""))

            ordered_versions = [
                VersionInfo(id=item, label=item, stable=True)
                for item in list_release_versions(minimum="1.7.10")
                if item in supported
            ]
            if ordered_versions:
                return ordered_versions
        except Exception:
            pass
        try:
            fallback = list_release_versions(minimum="1.7.10")
            if fallback:
                return [VersionInfo(id=item, label=item, stable=True) for item in fallback]
        except Exception:
            pass
        return [VersionInfo(id=self.default_mc_version, label=self.default_mc_version, stable=True)]

    def list_loader_versions(self, mc_version: str) -> list[VersionInfo]:
        try:
            raw = fetch_text(self._maven_metadata)
            root = ElementTree.fromstring(raw)
            versions: list[str] = []
            for elem in root.findall(".//version"):
                value = (elem.text or "").strip()
                if not value.startswith(f"{mc_version}-"):
                    continue
                loader_version = value.replace(f"{mc_version}-", "", 1)
                if loader_version and loader_version not in versions:
                    versions.append(loader_version)
            if not versions:
                return []

            def parse_loader(value: str) -> tuple[int, ...]:
                parts: list[int] = []
                for part in value.replace("-", ".").split("."):
                    try:
                        parts.append(int(part))
                    except Exception:
                        parts.append(0)
                return tuple(parts)

            versions.sort(key=parse_loader, reverse=True)
            return [
                VersionInfo(id=version, label=version, stable=True)
                for version in versions
            ]
        except Exception:
            return []

    def _resolve_loader_version(self, mc_version: str, requested: str | None) -> str:
        if requested:
            return requested
        data = fetch_json(self._promotions_url)
        promos = data.get("promos", {})
        latest = promos.get(f"{mc_version}-latest")
        if latest:
            return str(latest)
        raise ValueError(f"Keine Forge Loader-Version fuer {mc_version} gefunden.")

    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        jar_path = target_dir / "forge-server.jar"
        if offline_mode_enabled():
            write_placeholder_jar(jar_path, f"forge-{request.mc_version}-{request.loader_version or 'latest'}")
            return ProvisionResult(
                server_jar_path=str(jar_path),
                notes=["Offline-Modus: Platzhalterdatei erstellt."],
            )

        loader_version = self._resolve_loader_version(request.mc_version, request.loader_version)
        installer_name = f"forge-{request.mc_version}-{loader_version}-installer.jar"
        installer_path = target_dir / installer_name
        installer_url = (
            f"{self._maven_base}/{request.mc_version}-{loader_version}/{installer_name}"
        )
        download_file(installer_url, installer_path)

        install_script = target_dir / "install_forge.bat"
        install_script.write_text(
            "@echo off\n"
            f"java -jar {installer_name} --installServer\n",
            encoding="utf-8",
        )
        return ProvisionResult(
            server_jar_path=str(jar_path),
            start_mode="bat",
            start_bat_path=str((target_dir / "run.bat").resolve()),
            notes=["Forge Installer heruntergeladen. Fuehre install_forge.bat einmalig aus."],
        )

    def generate_start_command(self, request: ProvisionServerRequest, jar_name: str) -> str:
        extra = ""
        if request.start_parameters:
            extra = f" {request.start_parameters.strip()}"
        return (
            f"java -Xms{request.memory_min_mb}M -Xmx{request.memory_max_mb}M "
            f"-jar {jar_name} nogui{extra}"
        )
