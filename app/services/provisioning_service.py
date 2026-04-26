from pathlib import Path

from sqlalchemy.orm import Session

from app.models.server import Server
from app.providers.base.server_provider_base import ServerProviderBase
from app.providers.server.bukkit_provider import BukkitProvider
from app.providers.server.fabric_provider import FabricProvider
from app.providers.server.forge_provider import ForgeProvider
from app.providers.server.neoforge_provider import NeoForgeProvider
from app.providers.server.paper_provider import PaperProvider
from app.providers.server.spigot_provider import SpigotProvider
from app.providers.server.vanilla_provider import VanillaProvider
from app.schemas.provider import ProvisionServerRequest, VersionInfo
from app.schemas.server import ServerCreate
from app.services.app_setting_service import get_server_storage_root
from app.services.java_runtime_service import choose_best_java_profile
from app.services.server_service import create_server, sync_server_settings_to_files
from app.services.server_service import slugify


class ProvisioningService:
    def __init__(self) -> None:
        self.providers: dict[str, ServerProviderBase] = {
            "vanilla": VanillaProvider(),
            "paper": PaperProvider(),
            "spigot": SpigotProvider(),
            "bukkit": BukkitProvider(),
            "fabric": FabricProvider(),
            "forge": ForgeProvider(),
            "neoforge": NeoForgeProvider(),
        }

    def list_available_server_types(self) -> list[str]:
        return list(self.providers.keys())

    def list_versions(self, server_type: str, channel: str = "release") -> list[VersionInfo]:
        provider = self.get_provider(server_type)
        return provider.list_versions(channel=channel)

    def list_loader_versions(self, server_type: str, mc_version: str, channel: str = "all") -> list[VersionInfo]:
        provider = self.get_provider(server_type)
        return provider.list_loader_versions(mc_version, channel=channel)

    def get_provider(self, server_type: str) -> ServerProviderBase:
        normalized = (server_type or "").strip().lower()
        provider = self.providers.get(normalized)
        if not provider:
            raise ValueError(f"Unbekannter Servertyp: {server_type}")
        return provider

    def create_server_instance(self, db: Session, data: ProvisionServerRequest):
        provider = self.get_provider(data.server_type)
        target_dir = self.resolve_target_directory(
            db,
            server_name=data.name,
            target_path=data.target_path,
        )
        target_dir.mkdir(parents=True, exist_ok=True)

        provision_result = provider.provision(data, target_dir)
        notes = list(provision_result.notes)

        java_profile_id = data.java_profile_id
        if java_profile_id is None:
            auto_profile = choose_best_java_profile(db, mc_version=data.mc_version)
            if auto_profile is not None:
                java_profile_id = auto_profile.id
                notes.append(f"Java-Profil automatisch zugewiesen: {auto_profile.name}")

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
            eula_file.write_text("eula=true\n", encoding="utf-8")

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
                java_profile_id=java_profile_id,
                memory_min_mb=data.memory_min_mb,
                memory_max_mb=data.memory_max_mb,
                port=data.port,
            ),
        )
        sync_server_settings_to_files(server)
        db.add(server)
        db.commit()
        db.refresh(server)
        return server, notes

    def resolve_target_directory(self, db: Session, *, server_name: str, target_path: str | None) -> Path:
        requested = (target_path or "").strip()
        if requested:
            return Path(requested).expanduser().resolve()

        root = get_server_storage_root(db)
        root.mkdir(parents=True, exist_ok=True)
        base_name = slugify(server_name)
        candidate = root / base_name
        index = 2
        while candidate.exists():
            candidate = root / f"{base_name}-{index}"
            index += 1
        return candidate.resolve()

    def reprovision_existing_server(
        self,
        server: Server,
        *,
        mc_version: str,
        loader_version: str | None,
    ) -> list[str]:
        provider = self.get_provider(server.server_type)
        target_dir = Path(server.base_path).expanduser().resolve()
        if not target_dir.exists() or not target_dir.is_dir():
            raise ValueError(f"Serverordner nicht gefunden: {target_dir}")

        request = ProvisionServerRequest(
            name=server.name,
            server_type=server.server_type,
            mc_version=mc_version,
            loader_version=loader_version,
            target_path=str(target_dir),
            java_profile_id=server.java_profile_id,
            memory_min_mb=server.memory_min_mb or 2048,
            memory_max_mb=server.memory_max_mb or 4096,
            port=server.port,
        )
        provision_result = provider.provision(request, target_dir)

        server_jar_name = None
        if provision_result.server_jar_path:
            server_jar_name = Path(provision_result.server_jar_path).name

        start_command = provision_result.start_command
        if not start_command:
            if server_jar_name:
                start_command = provider.generate_start_command(request, server_jar_name)
            else:
                start_command = server.start_command or "java -version"

        start_mode = provision_result.start_mode or server.start_mode
        start_bat_path = provision_result.start_bat_path
        if start_mode == "bat" and not start_bat_path:
            default_bat = target_dir / "start.bat"
            default_bat.write_text(f"@echo off\n{start_command}\n", encoding="utf-8")
            start_bat_path = str(default_bat.resolve())

        server.mc_version = mc_version
        server.loader_version = loader_version
        server.start_mode = start_mode
        server.start_command = start_command if start_mode == "command" else None
        server.start_bat_path = start_bat_path if start_mode == "bat" else None

        eula_file = target_dir / "eula.txt"
        if not eula_file.exists():
            eula_file.write_text("eula=true\n", encoding="utf-8")
        return provision_result.notes
