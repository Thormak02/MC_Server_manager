from pathlib import Path

from app.providers.base.server_provider_base import ServerProviderBase
from app.providers.server.common import (
    download_file,
    fetch_json,
    normalize_version_channel,
    offline_mode_enabled,
    write_placeholder_jar,
)
from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class PaperProvider(ServerProviderBase):
    provider_name = "paper"
    default_mc_version = "1.21"
    # Die alte PaperMC-API (api.papermc.io/v2) wurde abgeschaltet (HTTP 410) und
    # durch die "Fill"-API v3 ersetzt. Struktur:
    #   /versions                     -> {"versions": [{"version": {"id","support","java"}, "builds": [...]}, ...]}
    #   /versions/<id>/builds         -> {"builds": [{"id","channel","downloads": {"server:default": {"name","url"}}}, ...]}
    # Beide Listen sind neueste-zuerst. Der Download-Link steht direkt im Build.
    _api_base = "https://fill.papermc.io/v3/projects/paper"

    def _fetch_version_entries(self) -> list[dict]:
        # /versions -> {"versions": [ {"version": {...}, "builds": [...]}, ... ]}
        data = fetch_json(f"{self._api_base}/versions")
        entries = data.get("versions") if isinstance(data, dict) else data
        return entries if isinstance(entries, list) else []

    def _fetch_builds(self, mc_version: str) -> list[dict]:
        # /versions/<id>/builds liefert je nach Endpoint eine blanke Liste ODER
        # {"builds": [...]}. Beides tolerieren.
        data = fetch_json(f"{self._api_base}/versions/{mc_version}/builds")
        if isinstance(data, list):
            return data
        builds = data.get("builds") if isinstance(data, dict) else None
        return builds if isinstance(builds, list) else []

    @staticmethod
    def _is_release_id(version_id: str) -> bool:
        low = version_id.lower()
        return not any(tag in low for tag in ("-rc", "-pre", "snapshot", "-exp"))

    def list_versions(self, channel: str = "release") -> list[VersionInfo]:
        normalized = normalize_version_channel(channel, default="release")
        try:
            result: list[VersionInfo] = []
            for entry in self._fetch_version_entries():  # neueste zuerst
                version = entry.get("version") if isinstance(entry, dict) else None
                if not isinstance(version, dict):
                    continue
                version_id = str(version.get("id") or "")
                if not version_id:
                    continue
                is_release = self._is_release_id(version_id)
                # "release" blendet nur RCs/Pre-Releases aus (alte, aber echte
                # Releases bleiben waehlbar); "all" zeigt alles.
                if normalized == "release" and not is_release:
                    continue
                result.append(
                    VersionInfo(
                        id=version_id,
                        label=version_id,
                        stable=is_release,
                        channel="release" if is_release else "beta",
                    )
                )
            if result:
                return result
        except Exception:
            pass
        return [
            VersionInfo(
                id=self.default_mc_version,
                label=self.default_mc_version,
                stable=True,
                channel="release",
            )
        ]

    @staticmethod
    def _build_id(build: dict) -> int:
        try:
            return int(build.get("id") or 0)
        except (TypeError, ValueError):
            return 0

    def list_loader_versions(self, mc_version: str, channel: str = "all") -> list[VersionInfo]:
        try:
            builds = sorted(self._fetch_builds(mc_version), key=self._build_id, reverse=True)
            result: list[VersionInfo] = []
            for build in builds:
                if build.get("id") is None:
                    continue
                stable = str(build.get("channel") or "").upper() == "STABLE"
                result.append(
                    VersionInfo(
                        id=str(build.get("id")),
                        label=str(build.get("id")),
                        stable=stable,
                        channel="release" if stable else "beta",
                    )
                )
            return result
        except Exception:
            return []

    def _resolve_download(
        self, mc_version: str, requested_build: str | None = None
    ) -> tuple[str, str]:
        builds = self._fetch_builds(mc_version)
        if not builds:
            raise ValueError(f"Keine Paper-Builds fuer {mc_version} gefunden.")

        if requested_build:
            build = next(
                (item for item in builds if str(item.get("id")) == str(requested_build)),
                None,
            )
            if build is None:
                raise ValueError(
                    f"Paper Build {requested_build} fuer {mc_version} nicht gefunden."
                )
        else:
            # Neuester STABLE-Build (nach Build-Nummer), sonst neuester ueberhaupt.
            stable_builds = [
                b for b in builds if str(b.get("channel") or "").upper() == "STABLE"
            ]
            build = max(stable_builds or builds, key=self._build_id)

        download = (build.get("downloads") or {}).get("server:default") or {}
        url = str(download.get("url") or "")
        file_name = str(download.get("name") or "")
        if not url or not file_name:
            raise ValueError("Paper Build ohne Server-Download gefunden.")
        return url, file_name

    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        if offline_mode_enabled():
            jar_path = target_dir / "paper.jar"
            write_placeholder_jar(jar_path, f"paper-{request.mc_version}")
            return ProvisionResult(
                server_jar_path=str(jar_path),
                notes=["Offline-Modus: Platzhalterdatei erstellt."],
            )

        url, file_name = self._resolve_download(request.mc_version, request.loader_version)
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
