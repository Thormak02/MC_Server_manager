from pydantic import BaseModel


class FileReadResponse(BaseModel):
    relative_path: str
    content: str
    is_editable: bool


class FileWriteRequest(BaseModel):
    relative_path: str
    content: str
