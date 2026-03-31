from pathlib import Path

from sqlalchemy.orm import Session

from app.providers.base.server_provider_base import ServerProviderBase
from app.providers.server.fabric_provider import FabricProvider
from app.providers.server.forge_provider import ForgeProvider
from app.providers.server.paper_provider import PaperProvider
from app.providers.server.spigot_provider import SpigotProvider
from app.providers.server.vanilla_provider import VanillaProvider
from app.schemas.provider import ProvisionServerRequest, VersionInfo
from app.schemas.server import ServerCreate
from app.services.server_service import create_server


class ProvisioningService:
    def __init__(self) -> None:
        self.providers: dict[str, ServerProviderBase] = {
            "vanilla": VanillaProvider(),
            "paper": PaperProvider(),
            "spigot": SpigotProvider(),
            "fabric": FabricProvider(),
            "forge": ForgeProvider(),
        }

    def list_available_server_types(self) -> list[str]:
        return list(self.providers.keys())

    def list_versions(self, server_type: str) -> list[VersionInfo]:
        provider = self.get_provider(server_type)
        return provider.list_versions()

    def get_provider(self, server_type: str) -> ServerProviderBase:
        normalized = (server_type or "").strip().lower()
        provider = self.providers.get(normalized)
        if not provider:
            raise ValueError(f"Unbekannter Servertyp: {server_type}")
        return provider

    def create_server_instance(self, db: Session, data: ProvisionServerRequest):
        provider = self.get_provider(data.server_type)
        target_dir = Path(data.target_path).expanduser().resolve()
        target_dir.mkdir(parents=True, exist_ok=True)

        provision_result = provider.provision(data, target_dir)

        server_jar_name = None
        if provision_result.server_jar_path:
            server_jar_name = Path(provision_result.server_jar_path).name

        start_command = provision_result.start_command
        if not start_command:
            if server_jar_name:
                start_command = provider.generate_start_command(data, server_jar_name)
            else:
                start_command = "java -version"

        start_bat_path = provision_result.start_bat_path
        if provision_result.start_mode == "bat" and not start_bat_path:
            start_bat = target_dir / "start.bat"
            start_bat.write_text(f"@echo off\n{start_command}\n", encoding="utf-8")
            start_bat_path = str(start_bat.resolve())

        eula_file = target_dir / "eula.txt"
        if not eula_file.exists():
            eula_file.write_text("eula=false\n", encoding="utf-8")

        server = create_server(
            db,
            ServerCreate(
                name=data.name,
                server_type=data.server_type,
                mc_version=data.mc_version,
                loader_version=data.loader_version,
                base_path=str(target_dir),
                start_mode=provision_result.start_mode,
                start_command=start_command if provision_result.start_mode == "command" else None,
                start_bat_path=start_bat_path if provision_result.start_mode == "bat" else None,
                java_profile_id=data.java_profile_id,
                memory_min_mb=data.memory_min_mb,
                memory_max_mb=data.memory_max_mb,
                port=data.port,
            ),
        )
        return server, provision_result.notes
