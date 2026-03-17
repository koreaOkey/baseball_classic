from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="BASEHAPTIC_",
        env_file=".env",
        extra="ignore",
    )

    app_name: str = "BaseHaptic Backend API"
    environment: str = "development"
    database_url: str = "sqlite+pysqlite:///./basehaptic.db"
    db_pool_size: int = 1
    db_max_overflow: int = 0
    db_pool_timeout_sec: int = 30
    db_connect_timeout_sec: int = 10
    db_pool_recycle_sec: int = 1800
    redis_url: str | None = None
    redis_pubsub_channel: str = "basehaptic:live_events"
    instance_id: str | None = None
    crawler_api_key: str = "dev-crawler-key"
    cors_allow_origins: str = "*"

    @property
    def cors_origins(self) -> list[str]:
        raw = self.cors_allow_origins.strip()
        if raw == "*":
            return ["*"]
        return [origin.strip() for origin in raw.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
