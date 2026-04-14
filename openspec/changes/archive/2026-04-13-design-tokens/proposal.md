## Why

현재 iOS(iPhone + Apple Watch) 앱은 컬러 외에는 디자인 토큰화가 되어 있지 않다. 화면마다 `.font(.system(size: 14))`, `.padding(16)`, `.cornerRadius(12)` 같은 매직 넘버가 흩어져 있고, 일부는 `AppColors`에 이미 정의된 색을 `Color(hex: 0x...)`로 중복 호출한다. 이 때문에:

1. **Figma 연동 불가** — Figma MCP로 디자인을 코드에 반영하려 해도 대응할 토큰 테이블이 없다.
2. **일괄 수정 불가** — "본문 글자 키워줘"라는 요청 하나에 40+ 곳을 수동으로 찾아야 한다.
3. **일관성 깨짐** — padding에 14/10/6/5 같은 8의 배수 아닌 값이 섞여 있고, radius도 12/14/16이 혼재한다.
4. **spec 공백** — `openspec/specs/design-system/spec.md`에 컬러·타이포·라디우스 스케일이 "sm/md/lg/xl을 사용한다"로만 언급돼 있고 실제 값이 정의돼 있지 않다. Android/Watch 쪽은 이미 `WatchUiProfile`로 구조화돼 있는데 iPhone 쪽만 뒤처진 상태.

이 change는 디자인 토큰을 코드에 도입하고, **모든 디자인 관련 기준을 `design-system` spec으로 집중**시킨다.

## What Changes

### Scope
- **iOS(iPhone + Apple Watch) 한정**. Android 쪽은 이미 Material Theme을 쓰고 있어 별도 change에서 다룸.
- 컬러 팔레트 자체는 변경 없음(이미 `AppColors`/`WatchColors`에 토큰화됨). 타이포그래피·스페이싱·라디우스·이벤트 색상을 신규 토큰화.

### 1. 신규 토큰 파일 생성 (iPhone)
- `AppFont.swift` — SF Pro system font 기반 타이포 스케일 (display/title/body/caption)
- `AppSpacing.swift` — 2/4/8/12/16/20/24/32 기반 8점 스케일 + outlier 명시
- `AppRadius.swift` — sm(8)/md(12)/lg(16)/xl(20)/pill(999) 스케일
- `AppEventColors.swift` — 이벤트 종류별 시맨틱 색상 (HIT/WALK/STEAL/SCORE/HOMERUN/DOUBLE_PLAY/TRIPLE_PLAY/OUT/STRIKE/BALL/VICTORY)

### 2. 신규 토큰 파일 생성 (Watch)
- `WatchAppSpacing.swift` — iPhone과 동일 스케일 (`WatchUiProfile`의 프로파일 값과 별도, 정적 상수용)
- `WatchAppRadius.swift` — iPhone과 동일 스케일
- `WatchAppEventColors.swift` — iPhone과 동일 시맨틱 색상
- 타이포는 기존 `WatchUiProfile` 유지 (디바이스 크기별 반응형이라 이게 맞음)

### 3. 이미 존재하는 토큰을 안 쓴 곳 치환 (A급 — 무위험)
- `WatchTestScreen.swift`: BSO 카운트 색(3곳), 시뮬레이션 중지 버튼(2곳), 이벤트 배열(8곳)
- `BaseHapticApp.swift:554`: 탭바 비활성 색
- 전부 `Color(hex: 0x...)` → `AppColors.xxx` 1:1 치환. 시각적 변화 0.

### 4. Typography 토큰 적용 (B-1)
- 모든 `.font(.system(size: N))` → `.font(AppFont.xxx)` 치환
- 대상: HomeScreen, LiveGameScreen, OnboardingScreen, SettingsScreen, WatchTestScreen, LiveActivity

### 5. Spacing 토큰 적용 + outlier 정리 (B-2)
- 모든 `.padding(N)` → `.padding(AppSpacing.xxx)` 치환
- Outlier 정규화 (시각적 영향 최소):
  - `padding(6)` → `padding(AppSpacing.sm)` (8)
  - `padding(14)` → `padding(AppSpacing.lg)` (16)
  - `padding(5)` → `padding(AppSpacing.xs)` (4)
  - `padding(10)` → `padding(AppSpacing.sm)` (8) 또는 `padding(AppSpacing.md)` (12) — 문맥별 판단
  - `padding(2)` → 유지 (미세 조정용으로 의도적)

### 6. Radius 토큰 적용 + outlier 정리 (B-3)
- 모든 `.cornerRadius(N)` → `.cornerRadius(AppRadius.xxx)` 치환
- `cornerRadius(14)` → `cornerRadius(AppRadius.md)` (12)로 통일
- `cornerRadius(999)` → `cornerRadius(AppRadius.pill)`

### 7. 이벤트 색상 중앙화
- `WatchTestScreen.swift:292-304`의 이벤트 색상 배열과 `LiveGameScreen.swift:322-331`의 `eventColor(_:)` 헬퍼가 **같은 정보를 두 번** 하드코딩하고 있음
- `AppEventColors.color(for:)` 하나로 통합 → 뷰에서 호출
- iPhone/Watch/LiveActivity 전역에서 동일한 매핑 보장

### 8. spec.md 업데이트
- `openspec/specs/design-system/spec.md`에 다음 Requirement 추가/보강:
  - **컬러 팔레트**: 실제 토큰 값(AppColors 전체) 명시
  - **팀 테마**: 10개 구단 × 7개 슬롯 표 추가
  - **타이포그래피 (iPhone)**: AppFont 스케일 정의
  - **스페이싱**: 8점 스케일 + 허용 예외 규칙
  - **반경 시스템**: 실제 값 정의 (기존은 "sm/md/lg/xl"만 언급)
  - **이벤트 색상**: HIT→green500 등 시맨틱 매핑 표
  - **토큰 준수 원칙**: 새 코드는 하드코딩 금지, 예외 시 주석으로 이유 명시

## Non-Goals (이번 change에서 하지 않는 것)
- Android/Wear OS 쪽 토큰화 (별도 change)
- 웹 프로토타입(`Real-time Baseball Broadcast`) 토큰 동기화
- `AppColors` ↔ `WatchColors` 통합 모듈화 (`BaseHapticShared/`로 이동) — Xcode 프로젝트 설정 변경이 필요해 위험도가 높음. 다음 change로 분리.
- Figma Variables 실제 생성 — 이 change 완료 후 별도 작업

## Impact

### 영향 범위
| 구분 | 파일 수 | 변경 종류 |
|---|---|---|
| 신규 토큰 파일 (iPhone) | 4 | 생성 |
| 신규 토큰 파일 (Watch) | 3 | 생성 |
| iPhone 화면 파일 | 5 | 수정 (HomeScreen, LiveGameScreen, OnboardingScreen, SettingsScreen, WatchTestScreen) |
| LiveActivity | 1 | 수정 |
| Watch 화면 파일 | 8 | 수정 |
| App 진입점 | 2 | 수정 (BaseHapticApp, BaseHapticWatchApp) |
| **openspec spec** | 1 | 업데이트 (design-system/spec.md) |

### 위험도
- **낮음**: 컬러·타이포는 값 보존 치환이라 시각 변화 0
- **중간**: Spacing/Radius outlier 정리(14→16 등)는 1~2px 시각 변화. 스크린샷 비교 필요.

### 롤백
- 모든 변경이 파일 단위로 분리되므로 `git revert` 가능. 토큰 파일만 남기고 치환만 되돌리는 부분 롤백도 가능.

## Why now
- Figma 디자인 워크플로 도입 준비 작업. 토큰 없이 Figma MCP를 붙이면 생성되는 SwiftUI 코드가 기존 앱과 따로 논다.
- 이후 모든 UI 개편이 이 토큰을 기준으로 진행되므로, 새 화면 작업 전에 완료해야 중복 작업을 막을 수 있다.
