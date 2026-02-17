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
