# Railway 백엔드 단일 워커 + glibc 아레나 제한 (비용 절감)

## Why

Railway usage가 월 약 $9.2로 Hobby 플랜 포함분($5)을 넘고 있었다. 7일 메트릭 기준 비용의 85%가 백엔드(baseball_classic) RAM이었다.

- uvicorn `--workers 2` 구성에서 배포 직후 0.21 GB → 경기 밤을 한 번 지나면 0.4 GB → 두 번째 경기 밤(9/8) 뒤 1.0 GB로 계단식 상승하고 내려오지 않는다(시간 누수가 아니라 경기 부하 후 미반환). 이후 며칠간 1.0 → 1.13 GB 완만 상승.
- 동시 접속 100여 명은 단일 워커로 충분하다(2026-03 부하테스트: 3워커 500명 무손실). ingest 브로드캐스트는 자기 프로세스 WS에 직접 전달하고 Redis 릴레이는 자기 메시지를 걸러내므로 워커 수와 무관하게 정합.
- 스레드 사용이 많은 구조(to_thread 최대 32 + weather 6 + FCM/APNs/venting)라 glibc malloc 아레나 팽창이 RSS 미반환의 1순위 가설. `MALLOC_ARENA_MAX=2`는 코드 무수정 환경변수.
- Hobby $5 안에 들어가려면 백엔드 평균 RAM ≤ 0.35 GB 필요(고정분 크롤러·Redis·CPU·egress ≈ $1.5).

## What Changes

Railway 서비스 설정만 변경(코드 무수정, 2026-09-09 16:42 KST 배포 c0674924).

- 시작 명령: `uvicorn app.main:app --host 0.0.0.0 --port $PORT --workers 2` → `--workers 1`
- 환경변수 추가: `MALLOC_ARENA_MAX=2`
- 환경변수 변경: `BASEHAPTIC_DB_MAX_OVERFLOW` 6 → 14 (워커당 풀이므로 단일 워커에서도 DB 커넥션 총량 16 유지, 2026-07-28 풀 고갈 장애 재발 방지)
- `infra/staging/backend.env.example`에 실제 값 반영

## Capabilities

### backend-runtime

- 백엔드는 단일 uvicorn 워커로 동작하며 WebSocket 브로드캐스트·Redis 릴레이·Live Activity 발송 경로는 변경 전과 동일하게 유지된다.
- 워커 프로세스가 종료되면 uvicorn 감독 프로세스 대신 Railway 재시작 정책이 컨테이너를 재기동한다.

### backend-cost

- 백엔드 평균 RAM을 0.35 GB 이하로 유지해 Railway Hobby 포함 사용량 안에서 운영하는 것을 목표로 한다.

## Impact

- Railway baseball_classic 서비스 설정(시작 명령·변수). 저장소 코드 변경 없음.
- 배포 검증: 서버 프로세스 1개 기동, Redis 구독 1회, `/health`·`/health/verbose`(redis connected)·`/games` 200, WS 접속·초기 state 프레임·pong 확인.
- 롤백: 시작 명령 `--workers 2` 복원 + 변수 제거 후 재배포(약 2분).
- 모바일/워치 영향 없음(API·프로토콜 무변경).
