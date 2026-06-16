## MODIFIED Requirements

### Requirement: 환경 변수
서비스 실행에 필요한 환경 변수를 정의해야 한다(MUST).

#### Scenario: 필수 환경 변수
- GIVEN 백엔드를 실행할 때
- WHEN 환경이 구성되면
- THEN 아래 변수가 설정되어야 한다:
  - `BASEHAPTIC_ENVIRONMENT` — development / staging / production
  - `BASEHAPTIC_DATABASE_URL` — DB 연결 문자열 (postgresql+psycopg://)
  - `BASEHAPTIC_CRAWLER_API_KEY` — 크롤러 인증 키
  - `BASEHAPTIC_CORS_ALLOW_ORIGINS` — CORS 허용 도메인

#### Scenario: DB 풀 설정
- GIVEN Supabase Session Pooler를 사용할 때
- WHEN 연결 풀을 설정하면
- THEN `BASEHAPTIC_DB_POOL_SIZE`(기본 1), `BASEHAPTIC_DB_MAX_OVERFLOW`(기본 0), `BASEHAPTIC_DB_POOL_TIMEOUT_SEC`를 조정할 수 있다

#### Scenario: 날씨 API 설정
- GIVEN 백엔드가 경기 시작 예보를 제공할 때
- WHEN 환경이 구성되면
- THEN 공공데이터포털 기상청 단기예보 API 키를 설정할 수 있다
- AND API 키가 설정되지 않아도 경기 목록 API는 정상 동작한다
