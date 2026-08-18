# Keep Lock-Screen Live Activity Realtime

## Why

iOS 잠금화면 라이브 스코어(Live Activity)가 관람 시작 직후에는 잘 갱신되다가 **시간이 지나면 멈추고, 폰 앱을 열어야 최신 데이터로 돌아오는** 현상 보고 (2026-08-18).

원인 추적 결과 iOS 의 **Live Activity 업데이트 budget 스로틀링**:

1. 잠금 상태에서 카드 갱신의 유일한 채널은 ActivityKit 토큰 대상 APNs `liveactivity` push (`main.py _send_live_activity_update`).
2. 백엔드는 크롤러 ingest 마다 — 볼/스트라이크 포함 사실상 매 투구마다 — **모든 push 를 `apns-priority: 10`** 으로 발송 (경기당 수백 건).
3. 위젯 확장에 `NSSupportsLiveActivitiesFrequentUpdates` 가 없어 기본 budget 적용. priority 10 push 는 budget 을 소모하며, 초과분은 **기기에서 조용히 드롭** (APNs 는 200 반환 → 백엔드 로그상 정상).
4. 앱을 열면 WebSocket → 로컬 업데이트 경로(budget 무제한)로 복구되므로 "앱 진입 시에만 최신화" 증상과 일치.

부차 요인: push 에 `stale-date` 가 없어 업데이트가 끊겨도 카드가 낡은 스코어를 살아있는 것처럼 계속 표시.

## What Changes

"계속 실시간" 기준으로 3축 개선:

1. **frequent updates 엔타이틀먼트** — 위젯 확장 Info.plist 에 `NSSupportsLiveActivitiesFrequentUpdates=YES` 추가. 스포츠 스코어 앱의 정식 고빈도 갱신 경로로, budget 이 대폭 상향된다. (앱 업데이트 배포 필요)
2. **백엔드 발송 정책** (`_send_live_activity_update`) — budget 을 아껴 구버전 앱·frequent-updates OFF 사용자도 주요 순간은 계속 받도록:
   - **중복 스킵**: 직전 발송 content-state 와 동일하면 발송 생략 (Redis `live_activity_last_state:{game_id}`, TTL 6h). 단 heartbeat 간격(60s) 경과 시 stale-date 갱신용 저우선 재전송.
   - **priority 차등**: 스코어·주자·아웃·이닝·투수·상태 변화 또는 주요 이벤트 타입(홈런·득점·안타 등) → priority 10 즉시. 볼카운트/타자만 변한 일상 갱신 → priority 5 (budget 미소모, 기회적 전달). `event=end` 는 항상 10.
3. **stale 가시화** — 모든 push 에 `stale-date`(+180s) 포함, iOS 로컬 업데이트 staleDate 도 180s 로 통일. 위젯은 `context.isStale` 이면 이벤트 라벨 대신 주황색 "동기화 지연" 표시 → 끊김을 낡은 스코어로 오인하지 않게.

수정 파일:

- `backend/api/app/apns.py` — `send_live_activity_push(_with_result)` 에 `priority`·`stale_seconds` 파라미터, `stale-date` payload
- `backend/api/app/main.py` — `_la_is_significant` 분류 + 중복 스킵/heartbeat/priority 차등, 발송 후 last-state 캐시 기록
- `ios/liveactivity/BaseHapticLiveActivity/Info.plist` — `NSSupportsLiveActivitiesFrequentUpdates`
- `ios/liveactivity/BaseHapticLiveActivity/BaseHapticLiveActivityWidget.swift` — `isStale` 전파 + "동기화 지연" 라벨
- `ios/mobile/BaseHaptic/LiveActivity/LiveActivityManager.swift` — staleDate 180s 통일

## Non-Goals

- **frequent-updates OFF 사용자 폴백 차등 발송**: 사용자가 설정에서 빈번한 업데이트를 끄면(`ActivityAuthorizationInfo().frequentPushesEnabled == false`) 기본 budget 으로 회귀한다. 토큰 등록 시 플래그를 백엔드에 전달해 해당 토큰만 주요 이벤트로 제한하는 개선은 DB 컬럼 추가가 필요해 후속 change 로 분리.
- Dynamic Island 의 stale 표시 (잠금화면 카드만 대상).
- Android 잠금화면(FCM ongoing 노티)은 budget 개념이 없어 무영향·무변경.

## Capabilities

### Modified Capabilities

- `realtime`: 잠금화면 Live Activity push 는 budget 을 고려해 발송한다 — 동일 상태 중복 발송 금지, 주요 변화만 priority 10, 일상 갱신은 priority 5, 모든 push 에 stale-date 포함. 클라이언트는 frequent updates 를 선언하고 stale 상태를 시각적으로 구분한다.
