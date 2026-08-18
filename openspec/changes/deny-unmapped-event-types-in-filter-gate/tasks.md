# Tasks

## 1. 클라이언트 게이트 default-deny 전환 (4개 사본)

- [x] 1.1 Android 폰 `EventFilterGate.isAllowed` — 미매핑 타입 `return true` → `return type == "VICTORY"` (`apps/mobile/.../data/model/EventFilterOption.kt`)
- [x] 1.2 iOS 폰 `EventFilterGate.isAllowed` — 동일 변경 (`ios/mobile/BaseHaptic/Models/EventFilterOption.swift`)
- [x] 1.3 Wear OS `isEventTypeAllowedByFilter` — `else -> return true` → VICTORY 예외 (`apps/watch/.../DataLayerListenerService.kt`)
- [x] 1.4 watchOS `isEventTypeAllowedByFilter` — `default: return true` → VICTORY 예외 (`ios/watch/.../WatchSync/WatchConnectivityManager.swift`)
- [x] 1.5 4개 게이트 모두 "미매핑 타입 차단 + VICTORY 예외 사유" 주석 명시 (미래 신규 타입 추가 시 매핑 등록이 정식 경로임을 안내)

## 2. 회귀 방지 확인 (코드 조사)

- [x] 2.1 타입 null/blank 허용 분기 유지 — `game_start`·`venting_loss` visible 푸시는 `event_type` 키 없음 확인 (backend `main.py:1866`, `1963`)
- [x] 2.2 프로덕션 VICTORY 발화 경로는 게이트 미경유 확인 (Android `GameSyncForegroundService.kt:321`, iOS `BaseHapticApp.swift` finished 분기) — 워치 수신단·테스트 도구만 게이트 경유하므로 VICTORY 예외 필요
- [x] 2.3 테스트 도구(WatchTestScreen 양 플랫폼)가 사용하는 타입은 전부 매핑됨 + VICTORY 확인
- [x] 2.4 iOS Live Activity 는 기존 명시적 화이트리스트로 이미 방어됨 확인 (`LiveActivityManager.shouldHighlight`)

## 3. 빌드 검증

- [x] 3.1 Android 모바일 `:app:compileDebugKotlin` 통과
- [x] 3.2 Wear OS `:app:compileDebugKotlin` 통과
- [x] 3.3 iOS 폰 시뮬레이터 빌드 통과 (BaseHaptic 스킴)
- [x] 3.4 watchOS 시뮬레이터 빌드 통과 (BaseHapticWatch Watch App 타깃)

## 4. 실기기 검증 (잔존)

- [ ] 4.1 Android 잠금화면 라이브 스코어: 타자 교체(OTHER)·공수교대(HALF_INNING_CHANGE)·마운드 방문(MOUND_VISIT) 시 알림음·진동·강조 없음, 카드 본문 상태는 계속 갱신
- [ ] 4.2 득점·홈런·안타는 기존대로 강조 + 알림음 발화
- [ ] 4.3 승리 시 워치 VICTORY 햅틱 정상 발화 (Wear + watchOS)
- [ ] 4.4 iOS silent push 경로: OTHER 이벤트가 워치 햅틱을 더 이상 울리지 않음
