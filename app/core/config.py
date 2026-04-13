from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "MC Server Manager"
    debug: bool = False

    secret_key: str = Field(
        default="CHANGE_ME_IN_ENV",
        description="Secret used to sign web sessions.",
    )
    session_cookie_name: str = "mcsm_session"
    session_max_age_seconds: int = 60 * 60 * 12
    session_idle_timeout_seconds: int = 60 * 30
    csrf_protection_enabled: bool = True
    security_log_ip: bool = True
    login_rate_limit_window_seconds: int = 300
    login_rate_limit_max_attempts: int = 8
    login_lockout_seconds: int = 900
    password_min_length: int = 10
    password_require_uppercase: bool = True
    password_require_lowercase: bool = True
    password_require_digit: bool = True

    data_dir: Path = Path("data")
    database_url: str | None = None

    initial_superadmin_username: str = "admin"
    initial_superadmin_password: str = "admin123!"

    scheduler_timezone: str = "Europe/Berlin"
    ingame_restart_delay_seconds: int = 30
    ingame_restart_warning_message: str = (
        "Server restartet in {seconds} Sekunden aufgrund /restart."
    )
    provisioning_offline_mode: bool = False
    default_server_root: str | None = None
    default_backup_root: str | None = None
    modrinth_enabled: bool = True
    curseforge_enabled: bool = True
    curseforge_api_key: str | None = None
    modrinth_user_agent: str = "mc-server-manager/1.0"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="MCSM_",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def resolved_database_url(self) -> str:
        if self.database_url:
            return self.database_url
        db_path = (self.data_dir / "mc_server_manager.db").resolve()
        return f"sqlite:///{db_path}"

    def ensure_data_dir(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    settings = Settings()
    settings.ensure_data_dir()
    return settings
