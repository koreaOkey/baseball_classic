# Decouple Live Activity Sender

## Why

8/18 수정(`keep-lockscreen-live-activity-realtime`, 전부 priority 10 + 코얼레싱) 이후에도 iOS 잠금화면 Live Activity 가 **어느 순간 최신화를 멈추는** 현상이 계속 보고됨 (2026-09-09). 9/8 KIA:삼성·키움:LG 경기 Railway 로그와 Supabase 토큰을 조사한 결과 APNs 전달 실패는 0건인데도 다음이 확인됐다:

1. **발송 누락**: 크롤러가 이벤트를 정상 ingest 한 시점(11:18:12 이닝 교대+투수교체, 11:19:51, 11:28:36, 11:30:00 투수교체 등)에 `[APNs-LA]` 발송 로그가 아예 없다. 한 경기에 stale-date(180s)를 넘는 발송 공백이 약 10회, 최장 6.5분. 에러·Traceback 은 없어 조용히 사라지는 형태다.
2. **발송 지연·순서 역전**: ingest 로부터 발송까지 정상 2s 가 아니라 20~35s 걸린 케이스가 다수. 요청별 백그라운드 체인이 동시에 돌아 오래된 상태가 나중에 발송될 수 있고, APNs `timestamp` 가 발송 시각이라 iOS 가 역전을 걸러내지 못한다.
3. **heartbeat 부재 구간**: heartbeat 가 "변경된 스냅샷 ingest" 에만 붙어 있어, 크롤러가 no-delta 로 스킵하는 이닝 교대·투수교체 공백(2~3분)마다 stale-date 를 넘겨 "동기화 지연" 이 뜬다.

구조적 원인은 Live Activity 발송이 ingest 요청의 백그라운드 체인 **맨 끝**(WS 브로드캐스트 → Redis 캐시 → 사일런트 푸시 팬아웃 → LA)에 붙어 있고, 마지막 발송 상태를 공유 캐시와 비교하는 dedupe/코얼레싱/heartbeat 게이트가 "늦게 도착한 체인" 을 버리거나 낡은 상태를 보내는 것이다. 백엔드가 `uvicorn --workers 2` 로 돌아 프로세스 2개가 같은 캐시를 두고 경쟁하는 점도 고려해야 한다.

## What Changes

운영 구조(WS·Redis 캐시·워치/iOS/Android 사일런트 푸시·경기 시작 푸시·크롤러·DB 스키마)는 건드리지 않고 Live Activity 발송 경로만 분리한다.

1. **발송 분리 + 순서 보장** — ingest 가 커밋되면 Live Activity 상태를 체인 앞단에서 독립 태스크로 넘긴다. 경기당 발송은 Redis 단기 락으로 직렬화하고, 각 상태에 ingest 순번(seq)을 실어 **이미 발송한 것보다 오래된 상태는 절대 보내지 않는다**. APNs `timestamp` 는 경기별로 단조 증가시켜 iOS 가 유효한 갱신을 드롭하지 않게 한다. 코얼레싱으로 미뤄진 마지막 상태는 간격 경과 후 자동 발송한다(trailing flush).
2. **타이머 heartbeat** — ingest 와 무관하게 60s 주기로, 토큰이 있는 LIVE 경기의 마지막 발송이 60s 이상 지났으면 같은 상태를 새 stale-date 로 재발송한다. workers 2 환경에서 중복 발송을 막기 위해 경기별 Redis 락을 잡은 프로세스만 보낸다. Redis 미가용 시에는 락 없이 진행한다(중복 가능, 현재 수준).
3. **관측 가능성** — `[APNs-LA]` 로그에 결과 사유(sent / heartbeat / skip_unchanged / skip_coalesce / skip_stale / no_tokens / lock_busy)와 ingest→발송 지연(ms)을 남겨 다음 경기에서 누락·지연·순서를 로그만으로 검증할 수 있게 한다.

이 change 는 백엔드만 수정한다. 페이로드 형식은 그대로라 배포된 iOS 앱과 호환된다.

## Non-Goals

- `DELETE /live-activity-tokens` 의 경기 단위 전체 삭제(다른 사용자 토큰까지 지움) 수정 — 하위호환(iOS 앱 릴리즈) 결정이 필요해 별도 change.
- `uvicorn --workers` 수 조정, 프로세스별 ingest 락·스케줄러 중복 정리 — 별도 결정.
- iOS 앱 변경(재시작 후 중복 액티비티 방지, 18s 마다 토큰 재등록 정리).
- Android 잠금화면(FCM ongoing 노티)은 이 경로를 쓰지 않아 무영향·무변경.

## Capabilities

### New Capabilities

(없음)

### Modified Capabilities

- `realtime`: 잠금화면 Live Activity 발송은 ingest 처리 체인과 분리되어 지연·누락·순서 역전 없이 전달되어야 하고, heartbeat 는 ingest 가 없어도 타이머로 유지되어야 한다. (8/18 change 의 delta 에 있던 "heartbeat 는 동일 상태 ingest 반복 시" 조항을 타이머 기준으로 대체한다. 해당 delta 는 아직 main spec 에 동기화되지 않았다.)

## Impact

- `backend/api/app/main.py` — ingest 후처리에서 Live Activity 발송을 체인 끝 `add_task` 대신 앞단 독립 태스크로 위임, lifespan 에 heartbeat 루프 등록
- `backend/api/app/live_activity_sender.py` (신규) — 경기별 직렬화 발송·seq 기반 stale 차단·trailing flush·타이머 heartbeat·사유 로깅
- `backend/api/app/redis_bus.py` — 단기 락(SET NX PX) 헬퍼 추가
- `backend/api/app/apns.py` — 변경 없음(timestamp 파라미터 기존 그대로 사용)
- `backend/api/tests/test_api.py` — 기존 `_send_live_activity_update` 테스트를 새 발송 경로 기준으로 개정
- 배포: staging 푸시 = 프로덕션. 경기 없는 시간대에 배포, 문제 시 revert 로 롤백. Redis 의 마지막 상태 캐시(TTL 6h)는 그대로 재사용.
- 클라이언트: iOS 페이로드 동일(변경 없음), Android/워치 무영향.
