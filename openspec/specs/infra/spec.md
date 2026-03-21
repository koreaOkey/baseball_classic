# Infrastructure Specification

## Purpose
BaseHaptic 서비스의 인프라 구성, 배포, 로컬 개발 환경, 운영 도구를 정의한다.

## Requirements

### Requirement: 배포 아키텍처
프로덕션 환경은 아래 구성을 따라야 한다(MUST).

#### Scenario: 프로덕션 배포 구성
- GIVEN 서비스를 프로덕션에 배포할 때
- WHEN 인프라를 구성하면
- THEN Backend는 Railway, DB는 Supabase(Session Pooler), Redis는 Railway Redis 서비스를 사용한다

#### Scenario: DB 접근 제한
- GIVEN DB에 접근이 필요할 때
- WHEN 클라이언트 앱에서 직접 접근을 시도하면
- THEN 차단하고, 반드시 백엔드 API를 프록시로 경유한다

### Requirement: Cloudflare 터널 (외부 디바이스 테스트)
로컬 백엔드를 외부 디바이스(폰/워치)에서 접근할 수 있도록 HTTPS 터널을 제공해야 한다(MUST).

#### Scenario: 퀵 터널 실행
- GIVEN 로컬 백엔드가 localhost:8080에서 실행 중일 때
- WHEN `scripts/dev/start_cloudflare_quick_tunnel.ps1 -Port 8080`을 실행하면
- THEN `https://<random>.trycloudflare.com` URL이 생성되어 외부 디바이스에서 접근 가능하다

#### Scenario: 네임드 터널 (고정 도메인)
- GIVEN Cloudflare 계정에 터널이 등록되어 있을 때
- WHEN `scripts/dev/start_cloudflare_named_tunnel.ps1`을 실행하면
- THEN 고정 도메인(예: api.your-domain.com)으로 로컬 백엔드에 접근 가능하다

#### Scenario: 터널 URL로 모바일 빌드
- GIVEN 터널 URL이 생성되었을 때
- WHEN `gradlew :apps:mobile:app:installDebug -PbackendBaseUrl=<터널URL>`을 실행하면
- THEN 해당 URL을 백엔드 주소로 사용하는 모바일 앱이 빌드된다

### Requirement: 로컬 Watchdog
로컬 개발 시 백엔드와 디스패처를 자동 복구하는 감시 스크립트를 제공해야 한다(MUST).

#### Scenario: 헬스 체크 및 자동 복구
- GIVEN watchdog 스크립트가 실행 중일 때
- WHEN `http://localhost:8080/health` 헬스 체크가 실패하면
- THEN 기존 uvicorn 프로세스를 종료하고 백엔드를 재시작한다

#### Scenario: 디스패처 자동 재시작
- GIVEN watchdog 스크립트가 디스패처를 관리 중일 때
- WHEN 디스패처 프로세스가 종료되면
- THEN 자동으로 재시작한다

#### Scenario: Watchdog 옵션
- GIVEN watchdog 스크립트를 실행할 때
- WHEN 옵션을 지정하면
- THEN `-RunOnce`(1회 체크), `-NoDispatcher`(디스패처 제외), `-CheckIntervalSec`(체크 간격), `-BackendApiKey`(API 키 지정)를 지원한다

### Requirement: 인코딩 설정
프로젝트는 UTF-8 인코딩을 기본으로 사용해야 한다(MUST).

#### Scenario: 에디터 인코딩
- GIVEN 프로젝트 파일을 편집할 때
- WHEN `.editorconfig`가 적용되면
- THEN `charset = utf-8`이 강제된다

#### Scenario: Windows PowerShell 한글 깨짐
- GIVEN Windows 환경에서 한글이 깨질 때
- WHEN `scripts/dev/enable_utf8.ps1`을 실행하면
- THEN 셸 세션의 인코딩이 UTF-8로 설정된다

#### Scenario: Git 줄바꿈 설정
- GIVEN Git으로 파일을 관리할 때
- WHEN `.gitattributes`가 적용되면
- THEN 텍스트 파일 줄바꿈을 정규화하고 바이너리 파일을 표시한다

### Requirement: 환경 변수
서비스 실행에 필요한 환경 변수를 정의해야 한다(MUST).

#### Scenario: 필수 환경 변수
- GIVEN 백엔드를 실행할 때
- WHEN 환경이 구성되면
- THEN 아래 변수가 설정되어야 한다:
  - `BASEHAPTIC_ENVIRONMENT` — development / production
  - `BASEHAPTIC_DATABASE_URL` — DB 연결 문자열 (postgresql+psycopg://)
  - `BASEHAPTIC_CRAWLER_API_KEY` — 크롤러 인증 키
  - `BASEHAPTIC_CORS_ALLOW_ORIGINS` — CORS 허용 도메인

#### Scenario: DB 풀 설정
- GIVEN Supabase Session Pooler를 사용할 때
- WHEN 연결 풀을 설정하면
- THEN `BASEHAPTIC_DB_POOL_SIZE`(기본 1), `BASEHAPTIC_DB_MAX_OVERFLOW`(기본 0), `BASEHAPTIC_DB_POOL_TIMEOUT_SEC`(기본 30)를 조정할 수 있다

### Requirement: 로컬 개발 실행 방법

#### Scenario: 백엔드 실행
- GIVEN 로컬 개발 환경에서
- WHEN 백엔드를 실행하면
- THEN `cd backend/api && uvicorn app.main:app --host 0.0.0.0 --port 8080`으로 시작한다

#### Scenario: 크롤러 디스패처 실행
- GIVEN 로컬 백엔드가 실행 중일 때
- WHEN 크롤러를 시작하면
- THEN `python crawler/live_baseball_dispatcher.py --backend-base-url http://localhost:8080 --backend-api-key dev-crawler-key`로 실행한다

#### Scenario: 모바일/워치 빌드
- GIVEN Android Studio 또는 Gradle이 설정되어 있을 때
- WHEN 앱을 빌드하면
- THEN `./gradlew :mobile:compileDebugKotlin` (모바일), `./gradlew :watch:compileDebugKotlin` (워치)로 빌드한다
