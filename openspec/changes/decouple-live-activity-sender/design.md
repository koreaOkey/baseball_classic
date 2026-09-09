# Design: Decouple Live Activity Sender

## Context

현재 잠금화면 Live Activity push 는 크롤러 스냅샷 ingest 요청의 Starlette BackgroundTasks 체인 맨 끝에서 발송된다:

```
ingest 커밋 → [응답] → WS 브로드캐스트(events, state) → Redis 캐시(state/events/http) → 사일런트 푸시 팬아웃(이벤트 × 토큰) → (경기시작 푸시) → Live Activity 발송
```

- 체인은 요청마다 하나씩 만들어져 **서로 독립적으로 동시에** 돈다. 앞단이 늦어지면 LA 발송이 밀리고, 늦게 끝난 체인이 더 오래된 상태를 나중에 보낼 수 있다.
- LA 발송 함수는 Redis `live_activity_last_state:{game_id}` 의 "마지막 발송 상태·시각" 과 비교해 동일 상태 스킵 / 볼카운트성 20s 코얼레싱 / 60s heartbeat 를 결정한다. 이 게이트는 "지금 도착한 상태가 최신" 이라는 전제를 깔고 있어, 순서가 뒤집히면 최신 상태를 버리거나 낡은 상태를 보낸다.
- APNs `timestamp` 는 발송 시각(`int(time.time())`)이라 iOS 쪽에서 역전을 거를 수 없다.
- 백엔드는 Railway 에서 `uvicorn --workers 2` 로 실행된다. 프로세스 2개가 같은 Redis 캐시를 두고 발송한다. 인메모리 락·lifespan 루프는 프로세스별로 따로 돈다.
- 9/8 로그 기준 한 경기 LA 토큰은 1~3개, 디바이스 토큰은 2~12개로 팬아웃 자체는 작다. 그럼에도 ingest→발송 20~35s 지연과 수 분 단위 누락이 관측됐다(원인 후보: 체인 직렬 대기, 동시 체인 간 게이트 충돌).

제약: 페이로드 형식·iOS 앱·Android·워치·크롤러·DB 스키마는 바꾸지 않는다. 배포 = staging 푸시 = 프로덕션.

## Goals / Non-Goals

**Goals:**
- ingest 커밋 직후 LA 발송이 체인 앞단의 처리 시간과 무관하게 시작된다.
- 경기별로 발송이 직렬화되고, 이미 발송한 것보다 오래된 상태는 절대 발송되지 않는다.
- 코얼레싱으로 미뤄진 마지막 상태가 다음 ingest 없이도 간격 경과 후 발송된다.
- heartbeat 가 ingest 와 무관하게 타이머로 유지되어, 파이프라인이 살아 있는 동안 카드가 stale 로 떨어지지 않는다.
- 프로세스 2개 환경에서 heartbeat·발송이 중복되지 않는다(Redis 가용 시).
- 로그만으로 발송/스킵 사유와 ingest→발송 지연을 검증할 수 있다.

**Non-Goals:**
- 토큰 단위 DELETE, workers 수 조정, iOS 앱 변경(proposal Non-Goals 참조).
- WS·Redis 캐시·사일런트 푸시 팬아웃의 구조 변경.
- Dynamic Island / Android 잠금화면.

## Decisions

### D1. 발송 트리거를 체인 "끝" 에서 "앞단의 fire-and-forget" 으로 옮긴다

ingest 엔드포인트는 동기 함수라 이벤트 루프에 직접 태스크를 만들 수 없다. 대신 BackgroundTasks 의 **첫 번째** 항목으로 아주 짧은 async 디스패처를 넣고, 디스패처는 발송 코루틴을 `create_task` 로 띄우고 즉시 반환한다. 체인의 나머지(WS·캐시·팬아웃)는 그 뒤에 그대로 실행되므로 기존 동작은 변하지 않고, LA 발송만 체인과 병렬로 진행된다. 띄운 태스크는 모듈 레벨 집합에 보관해 GC 로 사라지지 않게 한다.

- 대안 A: 요청 스레드에서 `run_coroutine_threadsafe` — 루프 참조 관리가 늘고 이득이 없어 기각.
- 대안 B: 체인 순서만 바꿔 LA 를 첫 태스크로 두고 `await` — 여전히 요청별 체인이 동시에 돌아 순서 역전을 못 막음. 기각.

### D2. 경기별 직렬화 + seq 기반 stale 차단

각 후보 상태에 `seq` 를 붙인다. seq 는 ingest 커밋 시점의 백엔드 벽시계(초, float)다. 크롤러는 경기당 순차 폴링(응답을 기다린 뒤 다음 POST)이라 같은 경기의 seq 는 단조 증가한다.

발송기는 후보를 처리하기 전에 **경기별 락**을 잡는다:

1. 프로세스 내: `asyncio.Lock` per game + "최신 pending 슬롯". 발송 중에 새 후보가 오면 슬롯만 갱신하고, 발송 루프가 슬롯이 빌 때까지 반복(drain)한다. 프로세스 안의 버스트는 자연히 코얼레싱된다.
2. 프로세스 간: Redis `SET NX PX` 단기 락(`live_activity_lock:{game_id}`, 5s). 획득 실패 시 200ms 간격으로 최대 3s 재시도, 그래도 실패하면 락 없이 진행하고 `lock_busy` 를 로그한다(현재 수준으로 저하).

락 안에서 `live_activity_last_state` 를 읽어 `candidate.seq <= last.seq` 이면 `skip_stale` 로 버린다. 그 외 기존 게이트(동일 상태 → `skip_unchanged`, routine & 20s 미만 → `skip_coalesce`, significant/end → 즉시)를 적용한다.

- 대안: iOS 의 `timestamp` 역전 거르기(observedAt 사용)에 의존 — 크롤러 observedAt 이 없거나 벽시계와 어긋나면 stale-date 계산까지 틀어진다. 서버 측 seq 차단이 더 확실하고 iOS 버전에 의존하지 않는다.

### D3. APNs timestamp 는 경기별 단조 증가

발송 시 `ts = max(int(now), last.ts + 1)` 로 정하고 last-state 에 `ts` 를 저장한다. `stale-date = ts + 180`. 순서는 D2 가 보장하고, timestamp 는 iOS 가 유효한 갱신을 "이전보다 오래됨" 으로 오판해 드롭하지 않도록만 단조성을 확보한다. 같은 초에 두 번 보내도 +1 로 구분된다.

### D4. 코얼레싱은 trailing flush 를 갖는다

routine 후보가 20s 게이트에 걸려 스킵되면 그 상태를 pending 슬롯에 남기고, 남은 간격만큼 기다렸다가 다시 제출하는 지연 태스크를 경기당 하나만 유지한다. 그 사이 더 새로운 후보가 오면 슬롯이 교체되고 지연 태스크는 최신 것을 보낸다. 이닝 마지막 투구가 다음 ingest 없이도 20s 안에 카드에 반영된다.

### D5. heartbeat 는 타이머 루프

lifespan 에서 프로세스마다 15s 주기 루프를 띄운다. 매 틱마다 Redis 에서 `live_activity_last_state:*` 키를 SCAN 해 후보 경기를 얻고, 각 경기에 대해:

- status 가 LIVE 가 아니거나 마지막 이벤트가 `end` 면 건너뜀.
- `now - last.ingestAt > 10분` 이면 건너뜀 — 크롤러/ingest 가 멈춘 경기는 heartbeat 로 "살아 있는 척" 하지 않고 카드가 정직하게 stale 로 떨어지게 한다.
- `now - last.sentAt >= 60s` 이면 D2 의 경기별 락을 잡고 재확인한 뒤, 같은 content-state 를 새 ts/stale-date 로 재발송하고 `sentAt`·`ts` 를 갱신한다(`heartbeat` 사유).

두 프로세스가 같은 틱에 같은 경기를 보더라도 락을 잡은 쪽만 보낸다. Redis 미가용 시 SCAN 이 비어 heartbeat 는 동작하지 않는다(현재와 동일한 저하).

- 대안: 프로세스 중 하나만 heartbeat 를 돌리는 리더 선출 — 리더 사망 처리가 필요해 과하다. 경기별 락으로 충분.

### D6. last-state 레코드 확장

기존 `{"state", "sentAt"}` 에 `seq`, `ts`, `ingestAt`, `lastEvent` 를 추가한다. 구 레코드(필드 없음)는 0 으로 간주해 첫 발송을 통과시킨다. TTL 6h 유지.

### D7. 로깅

`app.live_activity` 로거 한 줄 형식:

```
[APNs-LA] game=<id> result=<sent|heartbeat|skip_unchanged|skip_coalesce|skip_stale|no_tokens|lock_busy> sent=<n>/<m> significant=<bool> event=<update|end> lag_ms=<ingest→발송> ts=<apns ts>
```

skip 은 ingest 당 최대 1줄이라 볼륨은 지금과 같은 수준이다.

### D8. 모듈 분리

발송기·heartbeat·락 로직은 새 모듈로 분리하고, 토큰 로더와 APNs 발송 함수는 주입한다(main.py 순환 import 회피, 테스트에서 가짜로 교체 용이). main.py 는 디스패치 호출과 lifespan 등록만 바뀐다.

## 플랫폼별 차이

- iOS 잠금화면: 유일한 대상. 페이로드·priority(10)·stale-date(180s) 동일. 배포된 앱 그대로 동작.
- Android 잠금화면(FCM ongoing 노티)·Wear OS·watchOS: 이 경로를 쓰지 않아 무영향.
- 웹: 무관.

## 배터리 / 네트워크

- heartbeat 는 경기·디바이스당 최대 1회/분, 그것도 LIVE 이고 ingest 가 10분 내인 경우에만. 오늘 heartbeat 가 ingest 에 묻어 나가던 양과 같거나 적다.
- 순서 역전으로 인한 불필요한 재발송(낡은 상태 → 다시 최신 상태)이 사라져 총 push 수는 줄어든다.
- Redis 락·SCAN 은 경기당 수 바이트, 15s 주기라 부하는 무시할 수준.

## Risks / Trade-offs

- [Redis 미가용 시 프로세스 2개가 각자 발송] → 오늘과 같은 수준의 중복이며 데이터는 틀리지 않는다. 로그로 관찰.
- [락 획득 실패 시 락 없이 진행] → 극히 드문 순서 역전 가능. `lock_busy` 로그로 빈도 확인 후 필요하면 재시도 상한 조정.
- [seq 가 벽시계라 컨테이너 간 시계 차이] → 워커 2개는 같은 컨테이너다. 인스턴스가 늘면 NTP 오차(ms) 대비 ingest 간격(≥수 초)이 훨씬 커서 문제되지 않는다.
- [heartbeat 컷오프 10분 동안 낡은 카드가 "정상" 으로 보임] → 크롤러 정지 시 최대 10분 뒤 stale 표시. 오늘은 heartbeat 가 아예 없어 3분이면 stale 이었으므로, 정지 감지가 7분 늦어지는 대신 정상 운영 중 오탐이 사라진다. 값은 상수로 두어 조정 가능.
- [fire-and-forget 태스크 예외] → 태스크 내부에서 전부 잡아 로그로 남긴다. 체인의 나머지(WS·팬아웃)에는 영향 없음.
- [기존 테스트 2건] → 새 경로 기준으로 개정.

## Migration Plan

1. 백엔드 테스트 통과 확인 후 경기 없는 시간대(18:30 KST 이전)에 staging 푸시.
2. Railway 배포 후 `/health` 정상, 첫 경기에서 `[APNs-LA]` 로그로 `lag_ms` 가 수 초 이내이고 `skip_stale`/`lock_busy` 가 드물며 stale-date 초과 공백이 사라졌는지 확인.
3. 문제 시 커밋 revert 후 staging 재푸시(롤백). last-state 레코드 확장은 하위호환이라 롤백에 캐시 정리가 필요 없다.

## Open Questions

- 락 재시도 상한(3s)과 heartbeat 컷오프(10분)는 첫 경기 로그를 보고 조정한다.
- 폰 포그라운드 시 18s 마다 반복되는 토큰 재등록이 토큰 캐시를 계속 무효화해 발송마다 DB 조회가 생긴다. 이 change 범위 밖이지만 lag 에 기여하면 후속으로 다룬다.
