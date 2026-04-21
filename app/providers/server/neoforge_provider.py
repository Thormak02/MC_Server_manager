from pathlib import Path
from xml.etree import ElementTree

from app.providers.base.server_provider_base import ServerProviderBase
from app.providers.server.common import (
    download_file,
    fetch_text,
    normalize_version_channel,
    offline_mode_enabled,
)
from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class NeoForgeProvider(ServerProviderBase):
    provider_name = "neoforge"
    default_mc_version = "1.21.1"
    _maven_base = "https://maven.neoforged.net/releases/net/neoforged/neoforge"
    _maven_metadata = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"

    @staticmethod
    def _loader_channel(value: str) -> str:
        lowered = value.lower()
        if "alpha" in lowered:
            return "alpha"
        if "beta" in lowered or "pre" in lowered or "rc" in lowered:
            return "beta"
        return "release"

    @staticmethod
    def _parse_core_parts(value: str) -> list[int]:
        core = value.split("-", 1)[0]
        parts: list[int] = []
        for token in core.split("."):
            if token.isdigit():
                parts.append(int(token))
            else:
                return []
        return parts

    @classmethod
    def _mc_version_from_loader(cls, loader_version: str) -> str | None:
        parts = cls._parse_core_parts(loader_version)
        if len(parts) < 2:
            return None
        mc = f"1.{parts[0]}.{parts[1]}"
        if mc.endswith(".0"):
            mc = mc[:-2]
        return mc

    @staticmethod
    def _mc_sort_key(mc_version: str) -> tuple[int, ...]:
        out: list[int] = []
        for token in mc_version.split("."):
            if token.isdigit():
                out.append(int(token))
            else:
                out.append(0)
        return tuple(out)

    @classmethod
    def _loader_sort_key(cls, value: str) -> tuple[list[int], int]:
        parts = cls._parse_core_parts(value)
        channel = cls._loader_channel(value)
        channel_rank = {"alpha": 0, "beta": 1, "release": 2}.get(channel, 0)
        return parts, channel_rank

    def _all_loader_versions(self) -> list[str]:
        raw = fetch_text(self._maven_metadata)
        root = ElementTree.fromstring(raw)
        out: list[str] = []
        seen: set[str] = set()
        for elem in root.findall(".//version"):
            value = (elem.text or "").strip()
            if not value or value in seen:
                continue
            seen.add(value)
            if not self._parse_core_parts(value):
                continue
            out.append(value)
        return out

    def list_versions(self, channel: str = "release") -> list[VersionInfo]:
        normalized_channel = normalize_version_channel(channel, default="release")
        try:
            loaders = self._all_loader_versions()
            per_mc: dict[str, set[str]] = {}
            for loader in loaders:
                mc = self._mc_version_from_loader(loader)
                if not mc:
                    continue
                per_mc.setdefault(mc, set()).add(self._loader_channel(loader))

            versions: list[VersionInfo] = []
            for mc, channels in per_mc.items():
                stable = "release" in channels
                if normalized_channel == "release" and not stable:
                    continue
                if normalized_channel in {"beta", "alpha"} and normalized_channel not in channels:
                    continue

                if stable:
                    entry_channel = "release"
                elif "beta" in channels:
                    entry_channel = "beta"
                else:
                    entry_channel = "alpha"

                versions.append(
                    VersionInfo(
                        id=mc,
                        label=mc,
                        stable=stable,
                        channel=entry_channel if normalized_channel == "all" else normalized_channel,
                    )
                )
            versions.sort(key=lambda item: self._mc_sort_key(item.id), reverse=True)
            if versions:
                return versions
        except Exception:
            pass
        return [VersionInfo(id=self.default_mc_version, label=self.default_mc_version, stable=True, channel="release")]

    def list_loader_versions(self, mc_version: str, channel: str = "all") -> list[VersionInfo]:
        normalized_channel = normalize_version_channel(channel, default="all")
        target_mc = mc_version.strip()
        if target_mc.endswith(".0"):
            target_mc = target_mc[:-2]
        try:
            versions: list[VersionInfo] = []
            for loader in self._all_loader_versions():
                mc = self._mc_version_from_loader(loader)
                if mc != target_mc:
                    continue
                entry_channel = self._loader_channel(loader)
                if normalized_channel != "all" and entry_channel != normalized_channel:
                    continue
                versions.append(
                    VersionInfo(
                        id=loader,
                        label=loader,
                        stable=entry_channel == "release",
                        channel=entry_channel,
                    )
                )
            versions.sort(key=lambda item: self._loader_sort_key(item.id), reverse=True)
            return versions
        except Exception:
            return []

    def _resolve_loader_version(self, mc_version: str, requested: str | None) -> str:
        if requested:
            return requested
        versions = self.list_loader_versions(mc_version, channel="release")
        if versions:
            return versions[0].id
        versions = self.list_loader_versions(mc_version, channel="all")
        if versions:
            return versions[0].id
        raise ValueError(f"Keine NeoForge Loader-Version fuer {mc_version} gefunden.")

    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        jar_path = target_dir / "neoforge-server.jar"
        if offline_mode_enabled():
            jar_path.write_text(
                f"PLACEHOLDER for neoforge-{request.mc_version}-{request.loader_version or 'latest'}\n",
                encoding="utf-8",
            )
            return ProvisionResult(
                server_jar_path=str(jar_path),
                notes=["Offline-Modus: Platzhalterdatei erstellt."],
            )

        loader_version = self._resolve_loader_version(request.mc_version, request.loader_version)
        installer_name = f"neoforge-{loader_version}-installer.jar"
        installer_path = target_dir / installer_name
        installer_url = f"{self._maven_base}/{loader_version}/{installer_name}"
        download_file(installer_url, installer_path)

        install_script = target_dir / "install_neoforge.bat"
        install_script.write_text(
            "@echo off\n"
            f"java -jar {installer_name} --installServer\n",
            encoding="utf-8",
        )

        return ProvisionResult(
            server_jar_path=str(jar_path),
            start_mode="bat",
            start_bat_path=str((target_dir / "run.bat").resolve()),
            notes=[
                "NeoForge Installer heruntergeladen. Die Erstinstallation wird beim ersten Start automatisch ausgefuehrt."
            ],
        )

    def generate_start_command(self, request: ProvisionServerRequest, jar_name: str) -> str:
        extra = ""
        if request.start_parameters:
            extra = f" {request.start_parameters.strip()}"
        return (
            f"java -Xms{request.memory_min_mb}M -Xmx{request.memory_max_mb}M "
            f"-jar {jar_name} nogui{extra}"
        )
