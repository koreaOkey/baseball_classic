# Design

## 장애 메커니즘 (2026-09-05)

1. 19:27:55 Redis-GGli 새 deployment 생성, 19:27:59 구 컨테이너 SIGTERM → 기존 연결 종료.
2. 백엔드 구독 루프가 "Connection closed by server" 를 즉시 감지하고 재연결 시도. 발행/캐시
   클라이언트의 풀 연결도 끊겨 새 연결이 필요.
3. 새 연결은 교체 전 주소로 향했고 connect 타임아웃이 없어 커널 SYN 재시도(≈127s)까지
   블로킹. 두 번 반복돼 약 4.5분.
4. 모든 HTTP 핸들러가 `get_cache` 를 먼저 await 하므로 요청이 그 뒤에 줄을 섰다. 엣지는
   5s(클라이언트 타임아웃)~212s 뒤 499 로 기록. 크롤러 ingest 는 8s 타임아웃으로 전부 실패.
5. 19:31:41 두산:SSG 종료 → 크롤러 정상 종료(0) → post_stop_sync 실패(10s 타임아웃) →
   `window_closed reason=terminal` 로 영구 포기.

## 타임아웃 계층

| 계층 | 값 | 근거 |
|---|---|---|
| socket_connect_timeout | 2s | 같은 리전 사설망, 정상 연결은 수 ms |
| socket_timeout (명령) | 2s | 캐시 GET/SET 은 1ms 급, 2s 면 장애 |
| 캐시 작업 wait_for | 3s | 풀 대기 + 재시도(retry_on_timeout 1회) 포함 상한 |
| 구독 socket_timeout | 없음 | blocking listen 은 idle 이 정상 |
| 구독 keepalive | idle 30s / 10s × 3 | FIN 없이 사라진 피어를 ≈60s 안에 감지 |
| 구독 재연결 백오프 | 1s → 10s 상한 | 장애 중 로그·연결 폭주 방지 |

## 최종 동기화 재시도

- `RelayCheckWindow` 에 `final_sync_pending` / `final_sync_attempts` 를 두고 exhausted 와
  독립적으로 관리한다. 윈도우 병합은 exhausted 윈도우를 그대로 유지하므로 플래그가 살아남는다.
- 재시도는 스케줄 API(소스)의 단일 경기 조회 → 스냅샷 POST. 소스가 아직 LIVE 라면 그 상태를
  보내며, 이는 기존 forced sync 와 동일한 의미다.
- 백오프 30s × 2^n, 상한 300s, 최대 30회(≈2.4h). 상한 뒤엔 자정 수집(전날 포함)이 회수한다.

## APNs 관측성

- 발송 함수는 `settings → JWT → client → try(POST)` 순서라 `try` 앞의 예외가 조용히 사라졌다.
  JWT 생성과 클라이언트 초기화를 각각 감싸 원인을 남기고, gather 결과의 예외를 라벨별로
  요약한다(game-start-push / venting-loss-push / watch-push / live-activity-push / APNs-batch /
  APNs-visible-batch).
