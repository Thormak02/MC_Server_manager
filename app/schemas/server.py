from pydantic import BaseModel, Field


class ServerCreate(BaseModel):
    name: str
    server_type: str
    mc_version: str
    loader_version: str | None = None
    base_path: str
    start_mode: str = "bat"
    start_command: str | None = None
    start_bat_path: str | None = None
    java_profile_id: int | None = None
    memory_min_mb: int | None = None
    memory_max_mb: int | None = None
    port: int | None = None


class ServerUpdate(BaseModel):
    name: str | None = None
    java_profile_id: int | None = None
    memory_min_mb: int | None = None
    memory_max_mb: int | None = None
    port: int | None = None
    auto_restart: bool | None = None


class ServerImportPreview(BaseModel):
    name: str
    slug: str
    base_path: str
    server_type: str
    mc_version: str = "unknown"
    start_mode: str = "bat"
    start_command: str | None = None
    start_bat_path: str | None = None
    notes: list[str] = Field(default_factory=list)


class ServerImportConfirm(BaseModel):
    name: str
    base_path: str
    server_type: str
    mc_version: str = "unknown"
    start_mode: str = "bat"
    start_command: str | None = None
    start_bat_path: str | None = None
    loader_version: str | None = None
    java_profile_id: int | None = None
    memory_min_mb: int | None = None
    memory_max_mb: int | None = None
    port: int | None = None
