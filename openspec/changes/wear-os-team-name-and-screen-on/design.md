## Context

Wear OS 워치앱에서 두 가지 UX 문제가 발견됨:
1. `LiveGameScreen`에서 팀명을 `team.uppercase()`로 렌더링하여 영문 약어(SSG, LG)가 그대로 노출. `TeamNameNormalizer.kt`에 `displayTeamName()` 함수가 이미 존재하나 UI 렌더링에 미적용.
2. 경기 중 시스템 타임아웃으로 화면이 꺼지고 앱이 백그라운드로 밀림. Ambient mode와 OngoingActivity는 구현되어 있으나 화면 유지 플래그 미설정.

Apple Watch(iOS)는 `displayTeamName()`을 데이터 수신 시점에 적용하여 한글 마스코트명 표시, 화면 유지도 watchOS가 자동 처리.

## Goals / Non-Goals

**Goals:**
- Wear OS 팀명을 Apple Watch와 동일하게 한글 마스코트명으로 표시
- 경기 라이브 중 화면 꺼짐 방지

**Non-Goals:**
- 팀명 매핑 로직 변경 (기존 `TeamNameNormalizer.kt` 그대로 사용)
- 경기 외 시간의 화면 유지 (배터리 소모 방지)

## Decisions

1. **팀명 정규화 적용 위치: UI 렌더링 시점**
   - `LiveGameScreen.kt`의 `ScoreSide` 컴포저블에서 `displayTeamName(team)` 호출
   - 대안: 데이터 저장 시점(DataLayerListenerService)에서 정규화 → iOS 방식이나, 기존 데이터 흐름 변경 범위가 커서 UI 시점 적용

2. **화면 유지: `FLAG_KEEP_SCREEN_ON` + `gameData.isLive` 조건부**
   - `LaunchedEffect(gameData?.isLive)`로 라이브 시 플래그 ON, 종료 시 OFF
   - 대안: WakeLock 직접 관리 → 해제 누락 위험, Compose 생명주기와 맞지 않음

## Risks / Trade-offs

- [배터리 소모] `FLAG_KEEP_SCREEN_ON`은 경기 중 화면을 계속 켜두므로 배터리 소모 증가 → 경기 라이브 중에만 활성화하여 완화. Ambient mode와 공존하여 실제로는 ambient 전환 시 저전력 화면으로 전환됨.
