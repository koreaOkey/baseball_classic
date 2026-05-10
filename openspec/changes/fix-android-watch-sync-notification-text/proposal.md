## Why

2026-05-10 사용자 요청: 안드로이드에서 워치 관람 중일 때 상단에 뜨는 foreground service 알림이 다음 두 가지 문제를 가짐.

- 알림 타이틀이 코드명 `BaseHaptic` 으로 노출 — 사용자 노출 앱명은 `야구봄` (메모리 정책: `project_app_name.md`).
- 본문이 단순 `워치로 관람 중...` 으로, 어떤 경기를 관람 중인지 식별 불가.

원인:

- `apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt:323` `setContentTitle("BaseHaptic")` 하드코딩.
- 같은 파일 line 76 `buildNotification("워치로 관람 중...")` 초기 본문에 팀명 정보가 없음. 서비스 시작 시 인텐트 extras 는 `EXTRA_GAME_ID` + `EXTRA_SELECTED_TEAM` 만 받고 home/away 팀명은 없는 상태.

## What Changes

- `GameSyncForegroundService.setContentTitle` 을 `"야구봄"` 으로 변경 — 사용자 노출 정책과 일치.
- 서비스 내부에서 `pushStateToWatch` 첫 호출 시(백엔드 첫 응답 도착) `${away마스코트} vs ${home마스코트} 경기 워치로 관람 중...` 로 알림 본문 갱신. 호출자(`MainActivity`, `MobileDataLayerListenerService`) 시그니처는 변경하지 않음 — 서비스가 자체적으로 백엔드에서 팀 정보 받아 갱신.
- 표시는 팀 코드(SSG, 두산)가 아니라 마스코트명(랜더스, 베어스). `Team` enum 의 `teamName` 필드(`apps/mobile/app/src/main/java/com/basehaptic/mobile/data/model/Team.kt:5-16`) 를 사용하고, `Team.NONE` 또는 enum 매핑 실패 시에는 백엔드 raw 문자열로 폴백.
- 같은 텍스트 재호출 시 불필요한 `notify` 를 막기 위해 `lastNotificationText` 비교.
- 팀명 순서는 코드베이스 컨벤션(`MainActivity.kt:688`, `"$awayTeam vs $homeTeam 경기를 워치로 관람하시겠습니까?"`) 와 동일하게 `away vs home`.

## Capabilities

### Modified Capabilities

- `android-watch-sync-foreground-notification`: 워치 관람 중 표시되는 foreground service 알림이 사용자 노출 앱명(`야구봄`)과 현재 관람 중인 경기 팀명을 포함. 외부 행동: 알림 셰이드 진입만으로 어떤 경기를 워치로 관람 중인지 식별 가능.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 백엔드 첫 응답이 늦으면 초기 `워치로 관람 중...` 텍스트가 잠깐 보임 | 보통 1초 내 첫 state 도착. 첫 응답 즉시 팀명 포함 텍스트로 갱신. 사용자 체감 영향 미미 |
| 팀명이 빈 문자열로 내려오면 갱신 안 됨 | `state.homeTeam.isNotBlank() && state.awayTeam.isNotBlank()` 가드. 빈 값일 때는 초기 문구 유지 |
| 같은 서비스 인스턴스가 게임 전환 시 이전 텍스트 잔존 | `ACTION_START_STREAMING` 마다 `lastNotificationText` 를 초기 문구로 리셋 |

## Status

- [x] 구현 완료
  - `apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt:54` `lastNotificationText` 필드 추가
  - 동 파일 line 77-79 초기 본문 변수화 및 `lastNotificationText` 동기화
  - 동 파일 line 133-148 `pushStateToWatch` 진입부에서 `Team.teamName`(마스코트) 우선, raw 문자열 폴백으로 본문 갱신
  - 동 파일 line 333 `setContentTitle("야구봄")`
- [ ] 빌드 검증 (`./gradlew :mobile:assembleDebug`)
- [ ] 실기기 검증: 워치 관람 시작 → 알림 셰이드에 `야구봄` + `${away마스코트} vs ${home마스코트} 경기 워치로 관람 중...`(예: `랜더스 vs 베어스 경기 워치로 관람 중...`) 노출
- [ ] 다음 Android 릴리즈(versionCode bump) 포함
