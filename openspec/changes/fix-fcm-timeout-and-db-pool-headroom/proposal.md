# Fix FCM Timeout and DB Pool Headroom (7/28 장애 재발 방지)

## Why

2026-07-28 18:32~19:04 KST 프로덕션 백엔드가 약 32분간 전면 503을 반환했다. 트리거는 저녁 시간대에 반복되는(최소 7/25부터) Railway→구글 방면 아웃바운드 SSL 단절(`SSL: UNEXPECTED_EOF_WHILE_READING`)이고, 증폭 요인은 두 가지였다:

1. FCM SDK 기본 httpTimeout 120초 + 내부 재시도(urllib3 Retry ~10회)가 겹쳐 발송 스레드가 분 단위로 블로킹.
2. DB 풀이 `pool_size=1, max_overflow=0`(Railway env 오버라이드)이라 커넥션 1개가 묶이면 전체 요청이 30초 타임아웃 → `QueuePool limit of size 1 overflow 0 reached` 전면 고갈.

같은 SSL 에피소드가 7/25·7/26에는 경고만 남기고 지나갔으나, 7/28은 1시간 이상 지속되며 5경기 동시 시작(18:30) 푸시 burst와 겹쳐 임계점을 넘었다.

## What Changes

- **DB 풀 여유 확보 (env only, 코드 무변경)**: Railway `baseball_classic` production 서비스의 `BASEHAPTIC_DB_POOL_SIZE` 1→2, `BASEHAPTIC_DB_MAX_OVERFLOW` 0→6. 워커 2개 기준 피크 16커넥션으로 Supavisor 클라이언트 한도(200)의 8%. prepared statement 충돌은 기존 `prepare_threshold=None`으로 이미 해결돼 있어 풀 확대와 무관.
- **FCM 시도당 타임아웃 제한**: `Settings.fcm_http_timeout_sec`(기본 10초, env `BASEHAPTIC_FCM_HTTP_TIMEOUT_SEC`) 신설, `firebase_admin.initialize_app(cred, options={"httpTimeout": ...})`로 전달. firebase-admin 6.9.0의 `_MessagingService`가 이 옵션을 읽어 HTTP 클라이언트에 적용함을 소스로 확인.

## Capabilities

### push-delivery-resilience

- 구글 방면 네트워크 장애 시 FCM 발송 시도가 시도당 최대 10초로 제한되어 스레드 풀 점유가 짧아진다.
- 전송(transport) 단계 실패는 `failed`로만 분류되고 토큰 삭제 근거인 `unregistered`에는 들어가지 않아, 네트워크 장애로 유저 토큰이 오삭제되지 않는다(기존 동작 유지).
- DB 풀은 burst 시 overflow 6개까지 즉시 확장되어, 커넥션 1개가 행에 걸려도 서비스 전체가 마비되지 않는다.

## Impact

- `backend/api/app/config.py` — `fcm_http_timeout_sec` 설정 추가.
- `backend/api/app/fcm.py` — `initialize_app`에 `httpTimeout` 옵션 전달.
- Railway env 2건 (배포 2977dfdd), 코드 배포 7fd724b7 (커밋 d6619912).
- 트레이드오프: 수십 초짜리 네트워크 blip 동안 늦게라도 배달됐을 푸시 일부가 실패로 처리될 수 있음(라이브 스코어 특성상 지연 배달 가치 낮음 — 수용).

## Non-Goals

- messaging 세션 재시도 횟수 축소(사설 API 패치) — 오늘 저녁 SSL 에피소드에서 httpTimeout 효과 확인 후 필요 시 후속.
- Railway egress SSL 단절 자체의 해결(플랫폼 문의 별도 진행), healthcheck 재시작 정책.
