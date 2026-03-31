from pydantic import BaseModel


class ScheduledJobCreate(BaseModel):
    server_id: int
    job_type: str
    schedule_expression: str
    command_payload: str | None = None
    delay_seconds: int | None = None
    warning_message: str | None = None
    is_enabled: bool = True
