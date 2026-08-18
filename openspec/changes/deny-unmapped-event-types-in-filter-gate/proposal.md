## Why

사용자가 알림 필터를 득점·홈런·안타만 ON 으로 설정했는데, Android 잠금화면 라이브 스코어 동기화 중 **타자 교체 시 알림음·진동과 함께 이벤트 강조가 발화**하는 현상 보고 (2026-08-18).

원인 추적 결과, 필터를 우회한 게 아니라 **필터 목록에 존재하지 않는 이벤트 타입이 게이트를 기본 통과**하는 구조적 구멍:

1. 크롤러 `_classify_event_type()` (`crawler/backend_sender.py:263`) 은 타자 교체·타석 등장 텍스트("대타 ○○○", "N번타자 ○○○")를 어떤 분류에도 매칭 못 해 `OTHER` 로 전송. 백엔드도 그대로 저장·스트림.
2. 4개 클라이언트 필터 게이트 전부 미매핑 타입을 **기본 허용**:
   - Android 폰 `EventFilterGate.isAllowed` — `optionForEventType(type) ?: return true`
   - iOS 폰 `EventFilterGate.isAllowed` — `guard let option ... else { return true }`
   - Wear OS `isEventTypeAllowedByFilter` — `else -> return true`
   - watchOS `isEventTypeAllowedByFilter` — `default: return true`
3. 그 결과 `OTHER`(타자 교체 등)·`HALF_INNING_CHANGE`(공수교대)·`MOUND_VISIT`(마운드 방문)이 잠금화면 강조(ALERTS 채널 + 진동)와 워치 햅틱을 필터 무관하게 발화.

경로별 노출:
- **Android 잠금화면** (사용자 보고 케이스): `GameSyncForegroundService.applyIncomingEvents` → LOCK_SCREEN 게이트 통과 → `LiveScoreNotificationManager.post(highlightEvent=true)` → `LIVE_SCORE_ALERTS` 채널 + `DEFAULT_ALL` + 진동으로 "⚡ <이벤트 문구>" 강조.
- **iOS 워치 햅틱 (silent push 경로)**: `AppDelegate.didReceiveRemoteNotification` 은 raw 타입을 매핑 없이 게이트에 넣어 `OTHER` 가 워치 햅틱으로 전달됨.
- **watchOS 직접 APNs**: `handleDirectPushHapticEvent`/`handleGameData` 인라인 이벤트도 같은 구멍.
- iOS Live Activity 는 게이트 뒤에 명시적 타입 화이트리스트가 이미 있어 (`LiveActivityManager.shouldHighlight`) 노출 안 됨.

## What Changes

4개 게이트 전부 **미매핑 타입 기본 차단(default-deny)** 으로 전환. 단 2가지 예외 유지:

- **타입 null/blank → 허용 유지**: `game_start`·`venting_loss` 등 visible 푸시는 `event_type` 키 자체가 없어 이 분기로 통과해야 함. 이벤트성 페이로드가 아니므로 필터 대상 아님.
- **`VICTORY` → 허용 유지**: 필터 설정 항목이 아닌 승리 순간 햅틱. 프로덕션 폰 코드는 게이트 없이 직접 전송하지만 (`GameSyncForegroundService.kt:321`, `BaseHapticApp.swift` finished 분기), 워치 수신단(`handleHapticEvent`)과 테스트 도구는 게이트를 거치므로 예외 없이는 승리 햅틱이 죽음.

수정 파일:
- `apps/mobile/.../data/model/EventFilterOption.kt` — `isAllowed` 미매핑 분기 `return true` → `return type == "VICTORY"`
- `ios/mobile/BaseHaptic/Models/EventFilterOption.swift` — 동일
- `apps/watch/.../DataLayerListenerService.kt` — `isEventTypeAllowedByFilter` `else -> return true` → `else -> return eventType.uppercase() == "VICTORY"`
- `ios/watch/.../WatchConnectivityManager.swift` — `default: return true` → `default: return eventType.uppercased() == "VICTORY"`

차단 후에도 잠금화면 ongoing 카드 **본문**의 상태(스코어·BSO·타자/투수)는 계속 갱신된다 — 게이트는 알림음·진동·강조·햅틱 여부만 결정하며 카드 게시 자체는 별도 경로.

## Capabilities

### Modified Capabilities

- `live-score-notification`: 이벤트 필터 게이트는 필터 옵션에 매핑된 타입만 발화 대상으로 삼는다. 미매핑 타입(OTHER·HALF_INNING_CHANGE·MOUND_VISIT 및 미래의 신규 타입)은 알림음·진동·강조·햅틱을 발화하지 않는다 (fail-closed). 예외: 타입 없는 페이로드(비이벤트 푸시), VICTORY(승리 햅틱).

## Out of Scope

- 크롤러의 타자 교체 전용 이벤트 타입 신설 (`BATTER_CHANGE` 분류) — 현재는 OTHER 로 뭉뚱그려지나, 차단이 목적이므로 분류 신설 불요. 향후 "타자 교체 알림" 옵션을 제품으로 추가할 때 함께.
- 백엔드 발송 단 필터 (옵션 A, `project_backend_push_filter_architecture`) — 별도 트랙.
- iOS Live Activity 화이트리스트와 게이트의 이중 방어 정리 — 동작 동일, 리팩터링 가치만 있음.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 향후 백엔드가 신규 이벤트 타입을 추가하면 클라이언트가 조용히 차단 | 의도된 fail-closed. 신규 타입은 4개 게이트의 매핑 + 필터 옵션 UI 에 추가하는 것이 정식 경로. 각 게이트에 주석으로 명시 |
| VICTORY 이외의 미매핑 타입을 정상 발화로 의존하던 경로 존재 가능성 | 전 호출부 조사 완료 — 프로덕션 발화 경로에서 미매핑 타입은 OTHER/HALF_INNING_CHANGE/MOUND_VISIT 뿐이며 전부 차단 대상. 테스트 도구는 매핑된 타입 + VICTORY 만 사용 |
| 잠금화면 카드 상태가 stale 해 보일 우려 | 카드 본문 갱신은 게이트와 무관하게 지속. 강조/알림음만 차단 |

## Status

- [x] Android 폰 게이트 default-deny (`EventFilterOption.kt`)
- [x] iOS 폰 게이트 default-deny (`EventFilterOption.swift`)
- [x] Wear OS 게이트 default-deny (`DataLayerListenerService.kt`)
- [x] watchOS 게이트 default-deny (`WatchConnectivityManager.swift`)
- [x] Android 폰·Wear 컴파일 통과, iOS 폰 시뮬레이터 빌드 통과
- [x] watchOS 시뮬레이터 빌드 통과
- [ ] 실기기 검증: 라이브 경기에서 타자 교체 시 잠금화면 무음 확인 + 득점/홈런/안타 강조 정상 + 승리 햅틱 정상
