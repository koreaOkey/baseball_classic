## 1. 토큰 파일 생성 (iPhone)
- [x] 1.1 `ios/mobile/BaseHaptic/Theme/AppFont.swift` 생성 — display/h1~h5/bodyLg/label/body/caption/micro/tiny + liveActivity 전용
- [x] 1.2 `ios/mobile/BaseHaptic/Theme/AppSpacing.swift` 생성 — xxs(2)/xs(4)/sm(8)/md(12)/lg(16)/xl(20)/xxl(24)/xxxl(32) + buttonHeight/bottomSafeSpacer
- [x] 1.3 `ios/mobile/BaseHaptic/Theme/AppRadius.swift` 생성 — sm(8)/md(12)/lg(16)/xl(20)/pill(999)
- [x] 1.4 `ios/mobile/BaseHaptic/Theme/AppEventColors.swift` 생성 — color(for:) 헬퍼

## 2. 토큰 파일 생성 (Watch)
- [x] 2.1 `WatchAppSpacing.swift` 생성
- [x] 2.2 `WatchAppRadius.swift` 생성 (xxs/sm/md10/md/lg/pill)
- [x] 2.3 `WatchAppEventColors.swift` 생성 (color(for:) + overlayStyle(for:))
- [x] 2.4 `WatchColors.swift`에 `cyan500` 추가 (STEAL 오버레이 전용)

## 3. project.yml 업데이트
- [x] 3.1 BaseHapticLiveActivity 타겟에 `AppFont.swift`, `AppSpacing.swift`, `AppEventColors.swift` 명시적 포함

## 4. design-system/spec.md 업데이트
- [x] 4.1 컬러 팔레트 Requirement에 중립 스케일 10단계 + 시맨틱 스케일 표 추가
- [x] 4.2 팀 테마 Requirement 신규 추가 (10구단 × 주요 슬롯)
- [x] 4.3 타이포그래피 iPhone Requirement 신규 추가 (AppFont 스케일)
- [x] 4.4 타이포그래피 Watch Requirement (WatchUiProfile + 정적 텍스트 예외 명시)
- [x] 4.5 스페이싱 Requirement 신규 추가 (xxs~xxxl, 예외 허용, outlier 정리 규칙)
- [x] 4.6 반경 시스템 Requirement 실제 값 + 14pt 통일 규칙
- [x] 4.7 이벤트 색상 Requirement 신규 추가 (5개 그룹 매핑)
- [x] 4.8 토큰 준수 원칙 Requirement 신규 추가 (Reason 주석 규칙)

## 5. A급 — 하드코딩 hex → AppColors 치환
- [x] 5.1 `WatchTestScreen.swift` BSO 카운트 3곳 → green400/yellow400/red400
- [x] 5.2 `WatchTestScreen.swift` 시뮬레이션 중지 버튼 → red400
- [x] 5.3 `WatchTestScreen.swift` 수동 이벤트 배열 → `AppEventColors.color(for:)`
- [x] 5.4 `BaseHapticApp.swift:554` 탭바 비활성 → `AppColors.gray500`

## 6. iPhone 화면 — 토큰 적용
- [x] 6.1 `HomeScreen.swift` — font/padding/radius 전체 치환, `padding(.vertical, 6)` Reason 주석
- [x] 6.2 `LiveGameScreen.swift` — 전체 치환, `eventColor(_:)` → `AppEventColors.color(for:)`
- [x] 6.3 `OnboardingScreen.swift` — 전체 치환, `padding(.vertical, 14)` Reason 주석, 그라디언트 중간색 Reason 주석
- [x] 6.4 `SettingsScreen.swift` — 전체 치환
- [x] 6.5 `WatchTestScreen.swift` — 전체 치환
- [x] 6.6 `BaseHapticApp.swift` — 탭바 font/padding 치환, `padding(.vertical, 8)` → `AppSpacing.sm`
- [x] 6.7 `TeamLogo.swift` — 동적 size 비례 폰트에 Reason 주석

## 7. LiveActivity
- [x] 7.1 `BaseHapticLiveActivityWidget.swift` — LockScreen 전체 치환 (liveActivity9/10/11/15/24 토큰)
- [x] 7.2 LiveActivity `eventColor(_:)` 헬퍼 제거, `AppEventColors.color(for:)`로 교체
- [x] 7.3 BSOCountView와 BaseDiamondView의 semantic Color 호출을 `AppColors.*`로 교체

## 8. Watch 화면 — 토큰 적용
- [x] 8.1 `WatchLiveGameScreen.swift` — padding/cornerRadius 치환 (md10/xxs 등)
- [x] 8.2 `WatchNoGameScreen.swift` — padding 치환, 고정 폰트에 Reason 주석
- [x] 8.3 `BaseHapticWatchApp.swift` — WatchEventOverlay RGB 하드코딩 제거 → `WatchAppEventColors.overlayStyle(for:)`
- [x] 8.4 `BaseHapticWatchApp.swift` — WatchSyncPromptView padding 치환, 배경 `(26,26,26)` → `WatchColors.gray900`, 고정 폰트 Reason 주석
- [x] 8.5 Transition Screens (5개) — Color.black + image frame만 사용, 치환 대상 없음

## 9. 검증
- [x] 9.1 `grep -n "\\.system(size: [0-9]"` — 의도된 예외(WatchUiProfile, 동적 size, Reason 주석 있는 워치 고정) 외 0개 확인
- [x] 9.2 `grep -n "Color(hex:"` — 토큰 정의 파일(Colors/TeamTheme/Team) + Reason 주석 있는 곳(OnboardingScreen 그라디언트) 외 0개 확인
- [x] 9.3 `grep -n "cornerRadius([0-9]"` — 0개 확인
- [x] 9.4 `grep -n "\\.padding(\\.\\w\\+, [0-9]"` — Reason 주석 있는 곳 외 0개 확인
- [x] 9.5 Xcode 빌드 성공 확인 (BaseHaptic + BaseHapticWatch Watch App 2-타겟) — 사용자 확인 완료
- [ ] 9.6 시각 회귀 확인 — 주요 화면 스크린샷 전/후 비교 — 추후 실사용 중 확인
- [ ] 9.7 팀 변경 시 색상 반영 정상 동작 — 추후 실사용 중 확인

## 10. Non-Goals 재확인 (이번 change 범위 밖)
- [ ] Android/Wear OS 쪽 토큰화 — 별도 change로 분리
- [ ] `AppColors` ↔ `WatchColors` BaseHapticShared 통합 — 별도 change (Xcode 설정 변경 필요)
- [ ] `Team.swift`의 `color` 속성 hex 하드코딩 — `TeamTheme.swift`와 중복. 정리 시 Team 모델 공유 영향 신중 검토 필요. 별도 change.
- [ ] Figma Variables 실제 생성 — 이 change 완료 후 별도 작업
- [ ] **LiveActivity 타겟 활성화** — 원본 pbxproj에 네이티브 타겟으로 등록된 적 없음이 이번 작업 중 발견됨. `liveactivity/BaseHapticLiveActivity/BaseHapticLiveActivityWidget.swift`는 dead code 상태이며 실제 위젯 렌더링은 동작한 적 없음. `LiveActivityManager.swift`(메인 앱 포함)는 ActivityKit API를 호출하지만 렌더링 대상 extension이 없어 시스템이 활성화하지 못함. 사용자 확인: "언젠가 구현" 상태로 확정. 별도 change에서 다룬다. 위젯 코드에 적용한 토큰 치환(AppFont/AppSpacing/AppEventColors)은 활성화 시 즉시 사용 가능하도록 유지한다.

## 11. 아카이브
- [x] 11.1 사용자 빌드 검증 완료 (2026-04-13)
- [x] 11.2 `openspec/changes/2026-04-13-design-tokens/` → `openspec/changes/archive/`로 이동
