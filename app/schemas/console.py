from pydantic import BaseModel, Field


class ConsoleCommandRequest(BaseModel):
    command: str = Field(min_length=1, max_length=1024)
