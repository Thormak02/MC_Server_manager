from datetime import datetime, timezone

from pydantic import BaseModel, Field


class ModpackImportEntry(BaseModel):
    name: str
    path: str
    provider_name: str
    content_type: str = "mod"
    project_id: str | None = None
    version_id: str | None = None
    download_url: str | None = None


class ModpackPreviewSnapshot(BaseModel):
    token: str
    source: str
    source_ref: str | None = None
    pack_format: str
    pack_name: str
    pack_version: str | None = None
    mc_version: str | None = None
    loader: str | None = None
    loader_version: str | None = None
    recommended_server_type: str = "vanilla"
    entries: list[ModpackImportEntry] = Field(default_factory=list)
    override_roots: list[str] = Field(default_factory=list)
    override_file_count: int = 0
    warnings: list[str] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class ModpackPreviewResponse(BaseModel):
    token: str
    source: str
    source_ref: str | None = None
    pack_name: str
    pack_version: str | None = None
    mc_version: str | None = None
    loader: str | None = None
    loader_version: str | None = None
    recommended_server_type: str = "vanilla"
    entry_count: int = 0
    entries: list[ModpackImportEntry] = Field(default_factory=list)
    override_file_count: int = 0
    warnings: list[str] = Field(default_factory=list)


class ModpackExecuteResponse(BaseModel):
    server_id: int
    server_name: str
    created_server: bool = False
    installed_count: int = 0
    overrides_copied: int = 0
    warnings: list[str] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)
