from functools import lru_cache

from pydantic import AliasChoices, Field
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
    db_pool_size: int = 4
    db_max_overflow: int = 4
    db_force_null_pool: bool = False
    db_pool_timeout_sec: int = 30
    db_connect_timeout_sec: int = 5
    db_init_lock_timeout_sec: float = 3.0
    db_pool_recycle_sec: int = 1800
    db_unavailable_backoff_sec: int = 300
    redis_url: str | None = None
    redis_pubsub_channel: str = "basehaptic:live_events"
    instance_id: str | None = None
    crawler_api_key: str = "dev-crawler-key"
    cors_allow_origins: str = "*"

    # Supabase Admin
    supabase_url: str = ""
    supabase_service_role_key: str = ""
    # Supabase Auth JWT 서명 검증용 시크릿 (HS256).
    # SUPABASE_JWT_SECRET 또는 BASEHAPTIC_SUPABASE_JWT_SECRET 둘 다 허용.
    supabase_jwt_secret: str = Field(
        default="",
        validation_alias=AliasChoices("SUPABASE_JWT_SECRET", "BASEHAPTIC_SUPABASE_JWT_SECRET"),
    )

    # APNs
    apns_key_base64: str | None = None  # .p8 파일 내용을 base64 인코딩한 값
    apns_key_id: str = ""
    apns_team_id: str = ""
    apns_bundle_id: str = "com.basehaptic.app"
    apns_use_sandbox: bool = False

    # FCM (Firebase Cloud Messaging)
    fcm_service_account_json: str | None = None  # Service Account JSON 전체를 문자열로
    # 시도당 HTTP 타임아웃. SDK 기본(120초)은 구글 방면 네트워크 장애 시
    # 내부 재시도까지 겹쳐 스레드를 분 단위로 점유하므로 짧게 제한한다.
    fcm_http_timeout_sec: int = 10
    # FCM 대량 발송 시 연결 재사용을 위한 requests 세션 풀 크기.
    # firebase-admin 기본 풀(pool_maxsize=10)로는 send_each 가 토큰마다 스레드를 띄워
    # 동시 발송할 때 남는 연결이 없어 매번 새 TLS 핸드셰이크를 치른다 — 경기 시작 푸시 폭주 시
    # CPU 가 튀고 "Connection pool is full, discarding connection" 경고가 쏟아진다.
    # 발송 규모만큼 풀을 키우면 연결을 재사용해 폭주 비용이 사라진다(병렬·실시간 방식은 유지).
    # 0 이하면 튜닝을 건너뛰고 SDK 기본값을 쓴다.
    fcm_http_pool_size: int = 50

    # Public Data Portal / KMA short-term forecast
    weather_service_key: str = ""
    weather_api_base_url: str = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"

    # 분풀이(venting) 모드 백엔드 — Phase 2. 전부 기본 OFF (다크 배포: 코드는 올라가되
    # 플래그를 켜기 전까지 워커·엔드포인트가 동작하지 않아 기존 운영 경로에 영향 0).
    venting_backend_enabled: bool = False  # 마스터: regret 워커 + 조회/지표/감독 엔드포인트
    venting_llm_enabled: bool = False  # LLM 산정만 별도 게이트 (off 시 규칙/WPA 폴백)
    # OpenAI 호환 LLM. 관례상 OPENAI_API_KEY 도 허용(BASEHAPTIC_ 접두 alias와 병행).
    venting_llm_api_key: str = Field(
        default="",
        validation_alias=AliasChoices("BASEHAPTIC_VENTING_LLM_API_KEY", "OPENAI_API_KEY"),
    )
    venting_llm_model: str = ""  # 예: gpt-5.6-luna (정확한 ID는 env로 주입, 코드 무수정)
    venting_llm_base_url: str = "https://api.openai.com/v1"
    venting_llm_timeout_sec: int = 20  # LLM 호출 타임아웃 (초)
    venting_llm_max_concurrency: int = 2  # 동시 regret 산정 상한 (버스트 종료 시 폭주 방지)
    # 경기 종료 시 패배팀 팬에게 분풀이 유도 푸시 (다크, 기본 OFF). 켜기 전까지 종료 훅에서
    # 태스크가 스케줄되지 않아 기존 운영 경로 무영향.
    venting_loss_push_enabled: bool = False
    # 패배 푸시 최소 앱 버전 게이트. 빈 값이면 게이트 없음(전 구독자). 값이 있으면(예 "8.6.0")
    # 그 버전 이상으로 등록한 구독자에게만 발송 — venting 미지원 구버전엔 배너를 안 보낸다.
    # 실테스트 시 이 값을 테스트 빌드 버전으로 두면 그 빌드(=본인)만 받는다.
    venting_loss_push_min_version: str = ""

    @property
    def cors_origins(self) -> list[str]:
        raw = self.cors_allow_origins.strip()
        if raw == "*":
            return ["*"]
        return [origin.strip() for origin in raw.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
