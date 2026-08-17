# Fix Watch Venting Tap Haptic (탭마다 진동)

## Why

워치 분풀이 룸에서 인형을 탭할 때 진동이 잘 안 느껴진다는 사용자 피드백(2026-08-17, iOS 워치).
원인: 단계 전환(균열/터짐/완파) 순간이 아니면 경햅틱을 **히트 3회당 1회**만 재생하도록
throttle(`TAP_HAPTIC_EVERY_HITS = 3` / iOS `hitsSinceLightHaptic >= 3`)했다. 결과적으로 탭 3번 중
2번은 진동이 없어 "안 느껴진다"고 체감된다. iOS는 그중에서도 가장 약한 `.click` 햅틱이라 더 그렇다.

이 throttle의 본래 목적은 **크라운/베젤을 빠르게 감을 때** 초당 수십 히트로 Taptic 모터가
과부하되는 것을 막는 것이었다. 단발 탭에는 불필요하다.

## What Changes

- 단발 탭(`hits == 1`)은 **매 탭마다** 경햅틱을 재생한다.
- 크라운/로터리 연속 스크롤(`hits > 1`)만 기존대로 3틱당 1회 throttle을 유지(모터 과부하 방지).
- 단계 전환 햅틱(균열/터짐/완파)은 변경 없음.

## Impact

- iOS: `ios/watch/BaseHapticWatch/Screens/WatchVentingScreen.swift` — `WatchVentingState.recordHits` else 분기.
- Wear OS: `apps/watch/app/src/main/java/com/basehaptic/watch/venting/WatchVenting.kt` — `WatchVentingState.recordHits` else 분기.
- 폰·백엔드 영향 없음. 게이지 밸런스(40히트 완파)·단계 전환 로직 불변.

## Non-Goals (후속)

- 햅틱 세기 상향(iOS `.click` → 더 강한 타입, Wear amplitude 상향) — 실기기 체감 후 필요 시 별도 조정.
- 실기기 진동 강도 설정(Prominent Haptic)·시뮬레이터 미진동 안내는 코드 밖 이슈.
