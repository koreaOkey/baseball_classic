# Add Live Score Style User Choice (Promoted vs Classic Card)

## Why

Android 16 promoted Live Update 잠금화면 스코어(`add-android-promoted-live-score`)가 도입되면서, 지원 기기에서는 기존 리치 커스텀 카드가 자동으로 promoted 스타일로 대체된다. 그러나 두 스타일은 정보 밀도·표현이 달라(리치 카드: 팀 로고 + 커스텀 레이아웃 / promoted: 시스템 템플릿 + 다이아몬드 아이콘) 사용자에 따라 이전 카드를 선호할 수 있다. 강제 전환 대신 설정 탭에서 직접 선택할 수 있게 한다.

## What Changes

- `LiveScoreNotificationManager`:
  - 신규 pref `live_score_promoted_style_enabled` (기본 true = 새 스타일).
  - `canPromote` 분기에 `isPromotedStyleEnabled()` 조건 추가 — 꺼져 있으면 API 36+ 기기에서도 기존 리치 커스텀 카드로 렌더링.
  - `isPromotedStyleSupportedOnDevice()` 신설 — 설정 UI 노출 판단용(API 36+ && 비삼성). `canPostPromotedNotifications()`는 시스템 설정에 따라 변할 수 있어 노출 조건에서 제외.
- `SettingsScreen`: "알림 이벤트" 매트릭스 아래 신규 섹션 "잠금화면 라이브 스코어" + 스위치 "새 잠금화면 스코어"(끄면 이전 버전 카드). promoted 미지원 기기(API<36·삼성)에서는 섹션 자체를 숨김(어차피 이전 카드만 동작).

## Capabilities

### lock-screen-live-score

- API 36+ 비삼성 기기 사용자는 설정 탭에서 promoted 스타일(기본)과 이전 리치 카드 스타일 중 선택할 수 있다.
- 토글 변경은 다음 라이브 스코어 갱신(post) 시점부터 적용된다(라이브 중 폴링/푸시 주기 내 자동 반영).
- 미지원 기기에서는 설정이 노출되지 않고 기존 리치 카드 동작이 유지된다(회귀 없음).

## Impact

- `apps/mobile/app/src/main/java/com/basehaptic/mobile/push/LiveScoreNotificationManager.kt` — pref 헬퍼 2개 + canPromote 조건.
- `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/SettingsScreen.kt` — 섹션 + 스위치.
- 백엔드·iOS·Watch 영향 없음.

## Non-Goals

- 토글 즉시 재게시(현재 표시 중인 노티 즉각 스타일 교체) — 라이브 갱신 주기가 짧아 불필요.
- iOS 측 대응 설정 — Live Activity는 단일 스타일이라 해당 없음.
