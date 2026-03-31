class UserCreate(BaseModel):
    username: str
    password: str
    role: str

class UserUpdate(BaseModel):
    role: str | None = None
    is_active: bool | None = None