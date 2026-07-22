# Add Android 16 Promoted Live Update Lock Screen Score

## Why

iOS는 Live Activity로 잠금화면에 라이브 스코어가 고정 노출되지만, Android는 일반 ongoing 노티라 다른 알림에 섞이고 접힌 상태로만 첫 노출된다. Android 16(API 36)의 promoted Live Update를 도입해 잠금화면 최상단 고정(항상 확장) + 상태바 칩("3:2") + 삼성 One UI 8 Now Bar 노출을 확보한다.

promoted 노티는 커스텀 RemoteViews가 금지되므로(자격 상실), 유일한 자유 픽셀 영역인 largeIcon(~48dp)에 베이스 다이아몬드를 비트맵으로 그린다. 실기기(Z Fold6, One UI 8.0) 검증 결과: 합성(다이아몬드+BSO) 아이콘은 점이 너무 작아 탈락, **다이아몬드 전용 아이콘(투명 배경) + BSO는 본문 별도 줄 이모지**로 확정. 검증용 모드 스위치는 확정 후 제거했다.

## What Changes

- `compileSdk 35 → 36`, `androidx.core:core-ktx 1.13.1 → 1.17.0` (promoted API 지원 최소 버전). `targetSdk 35` 유지.
- 매니페스트에 `POST_PROMOTED_NOTIFICATIONS` 권한 추가(설치 시 자동 부여, 런타임 요청 불필요).
- `LiveScoreNotificationManager.post()` OS 분기:
  - **API 36+**: 커스텀 뷰 없이 BigTextStyle 2줄 — 타이틀 "LG 3 : 2 두산", subText "7회말", 본문 1줄 "타자 오지환 · 투수 곽빈" + 2줄 "B🟢🟢⚪ S🟡⚪ O🔴🔴", `setLargeIcon(다이아몬드 비트맵)`, `setShortCriticalText("3:2")`, `setRequestPromotedOngoing(true)`.
  - **API 35 이하**: 기존 커스텀 RemoteViews 카드 그대로 (동작 변화 없음).
- 신규 `LiveScorePromotedIconRenderer`: 384px 캔버스에 베이스 다이아몬드 렌더링. 투명 배경(배경판 없음), 회전 사각형 대각 확장을 포함해 캔버스 최대 크기로 계산(baseSize 0.33, spread 0.76). 설정 화면 "테스트 도구" 진입점(워치 미연결에서도 접근)과 테스트 화면의 promoted 지원 여부 안내 포함.

## Capabilities

### lock-screen-live-score

- Android 16+ 기기에서 라이브 스코어 노티는 promoted Live Update 자격 요건(ongoing, 비-커스텀뷰 스타일, 타이틀, 채널 importance)을 충족해 잠금화면 최상단에 항상 확장된 상태로 고정된다.
- 상태바 칩에 "원정:홈" 스코어(≤7자)가 표시되고, 삼성 One UI 8+에서는 Now Bar에 연동된다.
- 하이라이트 이벤트 시 채널 승격·진동 등 기존 동작은 promoted 여부와 무관하게 유지된다.
- Android 15 이하에서는 기존 커스텀 카드가 그대로 동작한다(회귀 없음).

## Impact

- `apps/mobile/app/build.gradle.kts` — compileSdk 36, core-ktx 1.17.0.
- `apps/mobile/app/src/main/AndroidManifest.xml` — POST_PROMOTED_NOTIFICATIONS.
- `apps/mobile/app/src/main/java/com/basehaptic/mobile/push/LiveScoreNotificationManager.kt` — 빌더 OS 분기 + promoted 스타일 적용.
- `apps/mobile/app/src/main/java/com/basehaptic/mobile/push/LiveScorePromotedIconRenderer.kt` — 신규.
- 백엔드·iOS·Watch 영향 없음. 데이터 경로(`GameSyncForegroundService` 스트림 → `post()`) 변경 없음.

## Non-Goals (후속 change)

- Android 17 MetricStyle(3열 큰 숫자 스코어) — compat 라이브러리 지원 확인 후 별도 진행.
- 잠금화면 위젯(완전 커스텀 중앙 정렬 카드 + 팀 로고) — 별도 트랙.
- promoted 아이콘 모드의 사용자 노출 설정 UI — 실기기 검증으로 모드 확정 후 불필요 시 스위치 제거.
