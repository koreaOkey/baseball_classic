## Why

Wear OS 워치에서 팀명이 "SSG", "LG" 등 영문 약어로 표시되어 Apple Watch의 한글 마스코트명("랜더스", "트윈스")과 불일치했고, 경기 중 화면이 시스템 타임아웃으로 꺼져 사용자가 매번 앱을 다시 열어야 했다.

## What Changes

- LiveGameScreen의 팀명 표시에 `displayTeamName()` 적용하여 한글 마스코트명으로 정규화
- 경기 라이브 중 `FLAG_KEEP_SCREEN_ON` 설정, 경기 종료 시 자동 해제
- 홈런 이벤트가 득점 등 하위 영상 재생 중 도착 시 기존 영상 중단하고 홈런 영상으로 교체 (iOS/Android)
- 내 팀이 아닌 중립 경기 관전 시 양 팀 모든 이벤트에 영상 표시 (iOS/Android)

## Capabilities

### New Capabilities

(없음 — 기존 기능의 버그 수정 및 UX 개선)

### Modified Capabilities

(스펙 수준 변경 없음)

## Impact

- `apps/watch/app/src/main/java/com/basehaptic/watch/ui/components/LiveGameScreen.kt` — 팀명 렌더링 변경
- `apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt` — 화면 유지 플래그, 홈런 우선순위, 중립 경기 로직 추가
- `ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift` — 홈런 우선순위, 중립 경기 로직 추가
