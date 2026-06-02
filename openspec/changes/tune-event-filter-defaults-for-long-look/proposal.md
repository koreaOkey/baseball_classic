## Why

`add-unified-live-score-notification-system` (2026-05-13) 로 8종 이벤트 필터(`event_filter_*_enabled`)가 도입됐고 모든 키의 default 가 `true` 로 출고됐다. 사용자가 iOS 실기기에서 관찰한 결과, 잠금화면 long-look 노티가 **아웃·진루·득점 등 모든 이벤트마다 갱신되어 시각적으로 시끄럽고**, 어떤 이벤트가 와도 같은 identifier(`live_score_ongoing`) 로 silent replace 되어 다음 강조 이벤트(홈런/득점/안타) 의 wrist-raise wake 가 죽는 현상 발생.

또한 워치 ongoing 노티는 인입된 이벤트 타입을 필터 검사 없이 `latestEventType` 에 그대로 저장한 뒤 본문에 라벨로 표시하기 때문에 (`WatchConnectivityManager.handleGameData` line 304-321), 아웃/볼넷 같은 차단 의도의 이벤트가 본문에 끼어드는 현상도 동시 보고됨.

## What Changes

이벤트 필터의 출고 default 를 사용자가 원한 3종(홈런·득점·안타) 만 ON, 나머지 5종(도루·볼넷·아웃·병살·투수교체) OFF 로 좁힌다. 설정 UI 는 동일 — 사용자가 원하면 5종도 켤 수 있다.

추가로 워치 측에서 차단된 이벤트 푸시는 `postOngoingLiveScoreNotification` 호출 자체를 생략한다. 같은 identifier silent replace 가 다음 허용 이벤트의 lock-screen wake 를 죽이는 현상을 막기 위함.

iOS 폰·iOS 워치 두 타깃 모두 default 매핑을 동일하게 보유 (`EventFilterOption.defaultEnabled` ↔ `WatchConnectivityManager.eventFilterDefaults`).

- `ios/mobile/BaseHaptic/Models/EventFilterOption.swift`
  - `EventFilterOption` 에 `defaultEnabled: Bool` 필드 추가, 8종 default 명시
  - `EventFilterGate.isAllowed` fallback 을 `?? true` → `?? EventFilterOption.defaultEnabled(forKey:)`
  - `defaultEnabled(forKey:)` 정적 helper 추가
- `ios/mobile/BaseHaptic/Screens/SettingsScreen.swift`
  - `EventFilterToggleRow` `@AppStorage(wrappedValue: true, ...)` → `wrappedValue: option.defaultEnabled`
  - `EventFilterOption.currentValues()` fallback 도 동일하게 per-key default
- `ios/watch/BaseHapticWatch/WatchSync/WatchConnectivityManager.swift`
  - `eventFilterDefaults` static dict 추가 (폰과 1:1 동기화)
  - `isEventTypeAllowedByFilter` fallback 변경
  - `handleGameData`: 인입 이벤트가 차단된 경우 `latestEventType` 갱신·햅틱·`postOngoingLiveScoreNotification` 모두 생략

## Capabilities

### Modified Capabilities

- `live-score-notification`: "선택 이벤트 long-look expand" 요건의 default 정책 명시 — HR/SCORE/HIT default ON, 그 외 default OFF. "미선택 이벤트 조용한 갱신" 요건은 iOS 한정 동작 명시 추가 (silent replace 가 wake 를 죽이는 부작용 회피를 위해 post 자체 생략).
- `mobile-ios`: 설정 UI 의 8종 토글 default 가 더 이상 일률적 ON 이 아님을 명시.
- `watch-ios`: 차단 이벤트가 동반된 푸시에서 ongoing 노티 post 가 skip 되며 본문에 차단 이벤트 라벨이 노출되지 않음을 명시.

## Out of Scope

- Android(폰·워치) — 사용자가 iOS 만 테스트했음. 별도 PR 로 Android 동등성 확보 예정.
- 백엔드 push payload — 클라이언트 단 default 변경만으로 충분. 백엔드 Bulk SQL 필터(`add-unified-live-score-notification-system` tasks 1.6~1.9) 작업 시점에 default 정책 일치 확인.
- 이벤트별 unique identifier 도입(매번 fresh wake) — 알림 센터가 더러워지는 트레이드오프 있어 본 PR 범위 외.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 기존 사용자가 이미 5종 토글 ON 으로 받고 있던 알림이 사라짐 | UserDefaults 에 명시적 값이 있으면 그걸 사용. default 만 적용되는 건 미설정 사용자(=신규 + 설정 미진입자) |
| 차단 이벤트 푸시에서 ongoing 노티 post 가 skip 되어 잠금화면 점수가 stale 해질 수 있음 | 점수 변화는 SCORE/HOMERUN 이벤트로 발화되며 두 키는 default ON. 진정한 stale 케이스는 아웃 누적·BSO 변동인데, 워치 앱을 열면 즉시 동기화됨 |
| 폰 ↔ 워치 default 매핑 drift | `WatchConnectivityManager.eventFilterDefaults` 주석에 "폰과 1:1 동기 — 둘 중 하나만 바꾸지 말 것" 명시 |

## Status

- [x] iOS 폰 default 분기 적용 (`EventFilterOption.swift`, `SettingsScreen.swift`)
- [x] iOS 워치 default 분기 적용 (`WatchConnectivityManager.swift`)
- [x] 차단 이벤트 시 ongoing 노티 post skip 적용 (`WatchConnectivityManager.handleGameData`)
- [ ] 실기기 검증 (iOS): 신규 설치 → 홈런만 잠금화면 wake / 아웃·볼넷 wake 안 함 확인
- [ ] 설정 UI 토글 → 5종 OFF default 표기 확인
- [ ] Android 동등성 작업 (별도 PR)
