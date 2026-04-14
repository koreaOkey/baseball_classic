## Why

2026-04-13에 iOS(iPhone + Apple Watch) 쪽 디자인 토큰화를 완료했다 (`2026-04-13-design-tokens` change). 이제 Android(mobile + Wear OS)를 같은 기준으로 맞춰야 `openspec/specs/design-system/spec.md`가 진정한 Single Source of Truth가 된다.

현재 Android 상태 (스캔 결과):

| 항목 | Mobile | Wear OS |
|---|---|---|
| 컬러 팔레트 | ✅ `Color.kt`에 토큰화 | ✅ `Color.kt`에 토큰화 (cyan500만 빠짐) |
| 팀 테마 | ✅ `TeamTheme.kt` 완료 | ✅ `TeamTheme.kt` 완료 |
| 타이포그래피 | ⚠️ Material3 `Typography` 3개만 (bodyLarge/titleLarge/labelSmall) | ⚠️ Wear `Typography` 3개만 (body1/title1/caption1) |
| 스페이싱 | ❌ 화면마다 raw `.dp` 직접 호출 | ⚠️ `WatchUiProfile`이 대부분 커버하지만 일부 raw `.dp` |
| 모서리 반경 | ❌ `RoundedCornerShape(N.dp)` 직접 호출 | ❌ 일부 raw |
| 이벤트 색상 | ❌ 각 화면에 하드코딩 | ❌ 하드코딩 |

Mobile 화면 스캔 결과: 8개 화면 파일에서 **raw dp/sp/Color가 347개** (TeamLogo 포함). iOS와 동일한 패턴(`fontSize = 14.sp`, `padding(16.dp)`, `RoundedCornerShape(12.dp)`).

## What Changes

### Scope
- **Android(mobile app + Wear OS app) 한정**. iOS는 별도 change로 이미 완료.
- 컬러·팀 테마는 이미 토큰화돼 있어 추가 정의 불필요. 타이포·스페이싱·라디우스·이벤트 색상을 신규 토큰화.

### 1. 신규 토큰 파일 (Mobile)
- `ui/theme/AppFont.kt` — SF-equivalent scale (display, h1~h5, bodyLg, label, body, caption, micro, tiny) — iOS `AppFont`와 1:1 매칭
- `ui/theme/Spacing.kt` — `object AppSpacing { val xxs = 2.dp; val xs = 4.dp; ... }`
- `ui/theme/Shapes.kt` — `object AppShapes { val sm = RoundedCornerShape(8.dp); ... }` + pill
- `ui/theme/EventColors.kt` — `fun eventColor(type: String): Color` (iOS `AppEventColors`와 동일 매핑)

### 2. 신규 토큰 파일 (Watch)
- `ui/theme/WatchAppSpacing.kt` — Mobile과 동일 스케일 (WatchUiProfile과 별도, 정적 상수용)
- `ui/theme/WatchAppShapes.kt` — 동일 스케일
- `ui/theme/WatchEventColors.kt` — iOS `WatchAppEventColors`와 동일 (`eventColor` + `overlayStyle` 함수)
- `ui/theme/Color.kt`에 `Cyan500` 추가 (STEAL 오버레이용)
- 타이포는 기존 `WatchUiProfile` 유지 (디바이스 크기별 반응형)

### 3. Mobile 화면 토큰 적용
대상 8개:
- `HomeScreen.kt` (87개)
- `SettingsScreen.kt` (71개)
- `OnboardingScreen.kt` (62개)
- `WatchTestScreen.kt` (51개)
- `LiveGameScreen.kt` (41개)
- `ThemeStoreScreen.kt` (34개)
- `CommunityScreen.kt` (10개)
- `TeamLogo.kt` (4개)

모든 `padding(N.dp)` → `padding(AppSpacing.xxx)`, `fontSize = N.sp` → `style = AppFont.xxx`, `RoundedCornerShape(N.dp)` → `AppShapes.xxx` 치환.

### 4. Watch 화면 토큰 적용
- `LiveGameScreen.kt`, `NoGameScreen.kt` — WatchUiProfile과 무관한 정적 값만 치환 (대부분은 이미 프로파일 기반)

### 5. Outlier 정규화 (시각 영향 최소)
iOS와 동일 규칙 적용:
- `padding(14.dp)` → `lg(16)`, `padding(6.dp)` → `sm(8)`, `padding(5.dp)` → `xs(4)`
- `padding(10.dp)` → `sm(8)` 또는 `md(12)` (문맥별)
- `RoundedCornerShape(14.dp)` → `md(12)`
- 1~2dp 시각 변화 발생 가능

### 6. 이벤트 색상 중앙화
현재 여러 화면에서 이벤트 타입별 색상이 개별 하드코딩됨. iOS처럼 `EventColors.eventColor(type)` 하나로 통합. iPhone과 완전히 같은 그룹 매핑 적용:
- HOMERUN/SCORE/SAC_FLY_SCORE/VICTORY → yellow500
- HIT/WALK/STEAL → green500
- DOUBLE_PLAY/TRIPLE_PLAY/STRIKE → orange500
- OUT/TAG_UP_ADVANCE → red500
- BALL → gray400

### 7. design-system/spec.md 업데이트
- iOS 작업에서 spec.md가 이미 플랫폼-중립 토큰 값으로 정의돼 있음
- Android 구현 참조를 명시적으로 추가:
  - "토큰 준수 원칙" Requirement에 Android Compose 예시 (`padding(AppSpacing.lg)` 대신 `.padding(16.dp)` 금지 등)
  - "타이포그래피" Requirement에 Mobile Android 케이스 추가 (Material3 Typography 대신 `AppFont` 사용 명시)

## Non-Goals (이번 change 범위 밖)
- iOS 토큰과의 실제 Kotlin Multiplatform/공유 모듈화 (순전히 값 동기화만, 코드 공유는 아님)
- 웹 프로토타입 토큰 동기화
- Android Material3 `Typography`/`Shapes` 슬롯 완전 활용 — iOS와 구조 맞추기 위해 정적 `object` 접근 채택
- `TeamTheme.kt`와 `Team` 모델의 `color` 속성 중복 정리 (iOS와 동일한 이슈, 별도 change)

## Impact

### 영향 범위
| 구분 | 파일 수 | 변경 |
|---|---|---|
| 신규 토큰 파일 (Mobile) | 4 | 생성 |
| 신규 토큰 파일 (Watch) | 3 | 생성 |
| 기존 토큰 수정 | 1 | `Color.kt`에 `Cyan500` 추가 |
| Mobile 화면 | 8 | 치환 |
| Watch 화면 | 2 | 치환 |
| openspec spec | 1 | Android 참조 추가 |

### 위험도
- **낮음**: 컬러·팀 테마는 이미 토큰화돼 있어 변경 없음
- **중간**: spacing/radius outlier 정리(14→16 등)는 1~2dp 시각 변화 유발 가능

### 롤백
- 파일 단위 분리, `git revert` 가능
- 토큰 파일 유지하면서 치환만 되돌리는 부분 롤백도 가능

## 왜 지금?
- iOS 작업이 방금 완료돼 spec이 최신 상태. 기억이 생생한 이번 주 안에 Android까지 맞춰두면 플랫폼 drift 방지
- Android는 현재 운영 중이고 iOS는 확장 예정 상태이므로, Android 코드가 spec을 실제로 따르는 상태가 되는 게 더 중요
- 이후 Figma → Android 코드 자동 생성 시 대응할 토큰 테이블 확보
