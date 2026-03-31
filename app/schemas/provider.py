from pydantic import BaseModel, Field


class ProvisionServerRequest(BaseModel):
    name: str
    server_type: str
    mc_version: str
    loader_version: str | None = None
    target_path: str
    java_profile_id: int | None = None
    memory_min_mb: int = 2048
    memory_max_mb: int = 4096
    port: int | None = None


class VersionInfo(BaseModel):
    id: str
    label: str
    stable: bool = True


class ProvisionResult(BaseModel):
    server_jar_path: str | None = None
    start_mode: str = "bat"
    start_command: str | None = None
    start_bat_path: str | None = None
    notes: list[str] = Field(default_factory=list)
