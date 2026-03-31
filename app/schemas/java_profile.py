from pydantic import BaseModel, Field


class JavaProfileCreate(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    java_path: str = Field(min_length=1, max_length=512)
    version_label: str | None = Field(default=None, max_length=64)
    description: str | None = None
    is_default: bool = False


class JavaProfileUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=128)
    java_path: str | None = Field(default=None, min_length=1, max_length=512)
    version_label: str | None = Field(default=None, max_length=64)
    description: str | None = None
    is_default: bool | None = None
