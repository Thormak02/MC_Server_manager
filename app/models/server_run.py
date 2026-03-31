class ServerRun(Base):
    id: int
    server_id: int
    started_at: datetime
    stopped_at: datetime | None
    exit_code: int | None
    termination_type: str | None