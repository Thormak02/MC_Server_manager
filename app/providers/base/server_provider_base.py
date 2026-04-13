from abc import ABC, abstractmethod
from pathlib import Path

from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class ServerProviderBase(ABC):
    provider_name: str
    default_mc_version: str

    @abstractmethod
    def list_versions(self, channel: str = "release") -> list[VersionInfo]:
        raise NotImplementedError

    def list_loader_versions(self, mc_version: str, channel: str = "all") -> list[VersionInfo]:
        return []

    @abstractmethod
    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        raise NotImplementedError

    @abstractmethod
    def generate_start_command(self, request: ProvisionServerRequest, jar_name: str) -> str:
        raise NotImplementedError
