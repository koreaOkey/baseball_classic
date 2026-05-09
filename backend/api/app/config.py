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

    # Supabase Admin
    supabase_url: str = ""
    supabase_service_role_key: str = ""

    # APNs
    apns_key_base64: str | None = None  # .p8 파일 내용을 base64 인코딩한 값
    apns_key_id: str = ""
    apns_team_id: str = ""
    apns_bundle_id: str = "com.basehaptic.app"
    apns_use_sandbox: bool = False

    # FCM (Firebase Cloud Messaging)
    fcm_service_account_json: str | None = None  # Service Account JSON 전체를 문자열로

    @property
    def cors_origins(self) -> list[str]:
        raw = self.cors_allow_origins.strip()
        if raw == "*":
            return ["*"]
        return [origin.strip() for origin in raw.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
