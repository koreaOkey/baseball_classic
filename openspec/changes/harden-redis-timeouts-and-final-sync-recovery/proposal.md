# harden-redis-timeouts-and-final-sync-recovery

## Why

2026-09-05 19:27 KST Railway Redis 서비스가 재배포되자(구 컨테이너 SIGTERM) 백엔드 API 전체가
19:28~19:32 약 4.5분간 응답하지 않았다. Redis 클라이언트에 연결·명령 타임아웃이 없어 교체된
컨테이너로의 연결이 커널 SYN 재시도가 끝날 때까지 블로킹됐고, 모든 HTTP 경로가 캐시 조회를
먼저 하기 때문에 경기 목록·상태·크롤러 ingest 가 모두 멈췄다(엣지 로그 499 5s~212s).

그 시간에 종료된 두산:SSG(20260905OBSK02026)는 크롤러의 최종 동기화가 실패한 채 윈도우가
닫혀 백엔드에 "LIVE 9회초 3:2" 로 고착됐다. 자정 스케줄 수집은 당일부터만 다루므로 자동
회수 경로가 없었다.

같은 조사에서 두 가지를 추가로 확인했다.
- Live Activity 발송이 9/1 배포 이후 전부 `sent=0/N` 인데 APNs 모듈 로그가 한 줄도 없다.
  발송 코루틴이 HTTP 호출 전에 예외를 내면 gather 가 삼켜 원인이 보이지 않는 구조다.
- 9/3 취소 경기(statusCode=BEFORE, statusInfo="경기취소")를 크롤러가 이틀째 폴링 중이다.

## What Changes

- **Redis 바운디드 타임아웃**: 연결 2s, 명령 2s, 캐시 작업 총 3s. 초과·연결 실패는 캐시
  미스로 흡수해 DB 로더로 진행한다. 구독 연결은 connect 타임아웃 + 커널 keepalive(≈60s 감지)만
  적용하고, 재연결은 지수 백오프(최대 10s). 실패 카운터(cache_fail/cache_timeout)와 10s
  스로틀 경고 로그.
- **크롤러 최종 동기화 재시도**: 크롤러 종료·중계 종료 판정 시 강제 동기화가 실패하면 윈도우는
  닫되 30s→60s→…→300s 백오프로 최대 30회 재시도한다. 성공 시 회수 로그.
- **자정 스케줄 수집 전날 포함**: 일일 수집 기본 범위를 전날부터 시작해 백엔드 상태와 다른
  종료 경기를 회수한다(같으면 skipped_unchanged).
- **APNs 발송 예외 가시화**: JWT 생성(키 디코드/서명) 실패를 60s 스로틀 traceback 으로 기록,
  HTTP/2 클라이언트 초기화 실패 기록, 모든 fan-out 결과의 예외를 "N/M sends raised …" 로 요약.
- **APNs 키 디코드 복원**: 배포 직후 로그로 원인이 `APNS_KEY_BASE64` 의 `Incorrect padding` 으로
  확정됐다(모든 APNs 발송이 HTTP 호출 전 실패). 끝의 `=` 누락·줄바꿈·따옴표·.p8 원문 등
  붙여넣기 변형을 복원해 디코드한다. 값 자체가 손상된 경우는 환경변수 재설정이 필요하며
  `ES256 signing failed` 로그로 구분된다.
- **취소 표기 경기 폴링 종료**: crawler.py 가 statusInfo 텍스트(경기취소·우천취소·노게임·
  경기연기 등)로도 종료를 판정하고, 중계 없는 경기 전 상태가 6h 이상 이어지면 정상 종료해
  dispatcher 재판정에 맡긴다.

## Non-Goals

- 장애 중 유실된 두산:SSG 9회 이벤트 11건 복원(상태·점수만 회수).
- Railway 고아 서비스(backend-staging, crawler-staging, Redis) 정리.
- Android/iOS/워치 클라이언트 변경(없음).

## Decisions

- 구독 클라이언트에는 socket_timeout 을 두지 않는다. blocking listen 이 idle 마다 끊겨
  재구독이 폭주한다. 대신 connect 타임아웃 + keepalive 로 죽은 피어를 감지한다.
- 캐시 실패는 예외가 아니라 미스로 취급한다. Redis 는 가속기이지 정합성의 원천이 아니다.
- 최종 동기화 재시도는 스케줄 스냅샷(상태·점수) 수준으로 한정한다. 크롤러 재기동으로
  이벤트까지 복원하는 경로는 복잡도 대비 이득이 작다.
- staging 브랜치 푸시 = 프로덕션 배포(Railway). 배포가 크롤러를 재기동하므로 고착된
  두산:SSG 경기는 배포 직후 relay 종료 판정 → 강제 동기화로 회수된다.
