## Why

watchOS 앱은 라이브 game_data를 받을 때마다 WKExtendedRuntimeSession을 시작하고, 세션 만료 직전(`extendedRuntimeSessionWillExpire`)에 재시도 카운트를 0으로 리셋한 뒤 즉시 새 세션을 시작한다. 이로 인해 `maxExtendedSessionRetries(3)` 제한이 만료 사이클을 넘어 무력화되어, KBO 경기(평균 3시간+) 내내 프로세스와 무선을 계속 살려두는 구조가 됐다. 워치 배터리 소모의 최대 단일 요인이다.

한편 워치 직접 APNs push 수신은 이미 구현·실기기 검증 완료(2026-04-15, iPhone 잠금 상태 포함) 상태라, 백그라운드 햅틱이라는 세션의 원래 목적이 APNs 경로로 중복 충족되고 있다. 코드 주석에도 "재시도 횟수 초과 시 포기 (APNs push가 앱을 깨워줌)"라고 명시되어 있다.

단, 실시간 햅틱은 핵심 UX이므로 세션 제거는 "APNs 단독으로 한 경기 전체를 실기기 검증"한 뒤에만 적용한다.

## What Changes

- 1단계(검증): Extended Session 재시작을 임시 비활성화한 빌드로 실기기에서 한 경기 전체를 관전하며 APNs 단독 백그라운드 햅틱 수신율·지연을 확인한다.
- 2단계(적용): 검증 통과 시 `extendedRuntimeSessionWillExpire`의 무조건 재시작을 제거한다. 검증 실패(누락/지연 발생) 시 폴백으로 "총 연장 상한(예: 누적 1시간) + 사용자가 최근 N분 내 화면을 본 경우에만 재시작" 절충안을 적용한다.
- 백그라운드 햅틱·라이브 데이터의 실시간성은 두 경로 모두에서 회귀 없이 유지되어야 한다.

## Capabilities

### Modified Capabilities

- `watch-ios`: 라이브 중 백그라운드 유지 전략을 "세션 무한 연장"에서 "APNs 주도 + 세션 상한"으로 변경한다.

## Impact

- `ios/watch/BaseHapticWatch/WatchSync/WatchConnectivityManager.swift` — `extendedRuntimeSessionWillExpire` 재시작 로직, `startExtendedSession` 호출 조건
- 실기기 검증 필요(시뮬레이터로는 APNs·세션 수명 검증 불가)
