# Default Promoted Live Score Style On All Devices (Including Samsung)

## Why

`add-live-score-style-user-choice`에서 promoted 잠금화면 스코어의 기본값을 **비삼성 ON / 삼성 OFF**로 두었다. 그 결과 삼성 기기(Z Fold6 등, 프로젝트 주 테스트 기기)에서는 기본이 이전 ongoing 리치 카드로 떨어져, "ongoing이 default"로 보인다. promoted 스타일을 전 기기 공통 기본값으로 통일한다.

삼성 Now Bar 미노출 우려는 런타임에서 자연 해소된다 — One UI 8.0은 `canPostPromotedNotifications()=false`라 켜져 있어도 자동으로 이전 카드로 폴백하며, One UI가 승격을 개방하면 그때부터 promoted가 적용된다. 별도 하드 블록/조건부 기본값이 불필요하다.

## What Changes

- `LiveScoreNotificationManager.isPromotedStyleEnabled()`:
  - 기본값을 `!Build.MANUFACTURER.equals("samsung")` (제조사 조건부) → **무조건 `true`**로 변경.
  - 삼성 기본 OFF 근거 주석을 "런타임 폴백에 위임" 설명으로 갱신.
- `canPromote` 분기(`post()`)의 삼성 관련 주석 갱신(로직 변경 없음 — `canPostPromotedNotifications()` 런타임 확인이 승격 미지원 기기를 그대로 폴백 처리).

사용자 설정 토글·`isPromotedStyleSupportedOnDevice()`·`forceStyle` 테스트 경로는 그대로다. `SettingsScreen`은 동일 헬퍼를 통해 새 기본값을 자동 상속한다.

## Capabilities

### lock-screen-live-score

- API 36+ **전 기기**(삼성 포함)에서 promoted 스타일이 기본값이다.
- 승격 미지원 기기(예: One UI 8.0)는 기본이 promoted여도 런타임에서 이전 리치 카드로 폴백한다(회귀 없음, 사용자 조작 불필요).
- 이미 토글을 조작한 기존 사용자는 저장된 값이 유지되고, 미조작 사용자만 새 기본값(ON)을 적용받는다.

## Impact

- `apps/mobile/app/src/main/java/com/basehaptic/mobile/push/LiveScoreNotificationManager.kt` — 기본값 1줄 + 주석.
- 백엔드·iOS·Watch 영향 없음. 설정 UI·테스트 도구 코드 변경 없음(기본값만 상속).

## Non-Goals

- 삼성 One UI 승격 개방 여부와 무관하게 강제 승격(불가) — 런타임 폴백 유지.
- 기존 사용자 저장값 강제 리셋 — 미조작 사용자만 기본값 반영.
