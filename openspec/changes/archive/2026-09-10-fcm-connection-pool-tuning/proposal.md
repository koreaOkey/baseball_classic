# FCM 연결 풀 확대 (경기 시작 푸시 폭주 CPU 완화)

## Why

경기 시작(SCHEDULED→LIVE) 시점에 응원팀 구독자에게 visible 푸시를 한꺼번에 발송하는 1~2분 동안 백엔드 CPU가 1 vCPU 이상으로 치솟았다(2026-09-09 18:32 KST=09:32 UTC, CPU 1.10 vCPU). 워커 2개였던 전날에도 같은 스파이크(1.76 vCPU)가 있어 워커 수와 무관하며, 원인은 안드로이드(FCM) 발송 경로다.

- 안드로이드 배치 발송은 firebase-admin `messaging.send_each`가 토큰마다 스레드를 띄워(`ThreadPoolExecutor(max_workers=len(messages))`) 동시 요청한다.
- 그 아래 `requests.Session`의 기본 연결 풀은 `pool_maxsize=10`이라, 토큰이 10개를 넘으면 남는 연결이 없어 매 발송마다 새 TLS 핸드셰이크를 친다.
- 로그에 `[FCM] Connection pool is full, discarding connection` 경고가 수십 건 쌓였고, 이 재연결 비용이 CPU 스파이크의 실체다.
- iOS/watch(APNs)는 공유 httpx HTTP/2 클라이언트(keepalive 50)로 이미 효율적이라 폭주 대상이 아니다.

워커 1개 구성(2026-09-09 비용 절감)에서는 이 스파이크를 하나뿐인 프로세스가 사용자 요청과 함께 처리하므로, 경기 시작 직후 접속 사용자가 응답 지연을 체감할 수 있다.

## What Changes

발송 방식(병렬·실시간)은 그대로 두고, 연결 재사용만 확보한다.

- firebase-admin이 쓰는 `requests` 세션에 큰 풀(`pool_maxsize`)을 가진 HTTPAdapter를 다시 mount한다. 기존 재시도 설정(500/503 최대 4회)은 보존한다.
- 초기화 1회 시점(`_ensure_initialized`)에만 적용하고, SDK 내부 구조(`_get_messaging_service`/`_client`/`session`)에 의존하므로 실패 시 삼키고 튜닝만 건너뛴다(발송 자체는 계속).
- 풀 크기는 `BASEHAPTIC_FCM_HTTP_POOL_SIZE`로 조정 가능(기본 50, 0 이하면 튜닝 스킵).

## Capabilities

### push-fanout

- 대량 FCM 푸시 발송 시 연결을 재사용하여, 발송 규모가 기본 풀을 넘어도 매 발송마다 새 TLS 연결을 맺지 않는다.
- 연결 풀 튜닝이 실패해도 푸시 발송 자체는 영향받지 않는다.

## Impact

- `backend/api/app/config.py` — `fcm_http_pool_size` 설정 추가(기본 50).
- `backend/api/app/fcm.py` — `_tune_connection_pool` 추가, 초기화 시 호출.
- 저장소 외: Railway 백엔드 재배포 필요(코드 변경). 선택적으로 `BASEHAPTIC_FCM_HTTP_POOL_SIZE` 환경변수로 크기 조정.
- 모바일/워치 영향 없음(발송 API·페이로드 무변경). iOS/APNs 경로 무변경.
- 검증: `pytest -k "fcm or push or notif or game_start"` 10건 통과, requests 어댑터 풀 10→50 확대·재시도 보존 스모크 통과.
