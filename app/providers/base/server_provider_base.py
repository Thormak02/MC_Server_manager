from abc import ABC, abstractmethod
from pathlib import Path

from app.schemas.provider import ProvisionResult, ProvisionServerRequest, VersionInfo


class ServerProviderBase(ABC):
    provider_name: str
    default_mc_version: str

    @abstractmethod
    def list_versions(self) -> list[VersionInfo]:
        raise NotImplementedError

    @abstractmethod
    def provision(self, request: ProvisionServerRequest, target_dir: Path) -> ProvisionResult:
        raise NotImplementedError

    @abstractmethod
    def generate_start_command(self, request: ProvisionServerRequest, jar_name: str) -> str:
        raise NotImplementedError
