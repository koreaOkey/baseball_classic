## 1. 토큰 파일 생성 (Mobile)
- [x] 1.1 `AppFont.kt` 생성 — display/h1~h5/bodyLg/label/body/caption/micro/tiny (iOS 1:1 매칭)
- [x] 1.2 `Spacing.kt` 생성 — `object AppSpacing` (xxs=2.dp ~ xxxl=32.dp + buttonHeight + bottomSafeSpacer)
- [x] 1.3 `Shapes.kt` 생성 — `object AppShapes` (sm/md/lg/xl/pill)
- [x] 1.4 `EventColors.kt` 생성 — `AppEventColors.eventColor()`

## 2. 토큰 파일 생성 (Watch)
- [x] 2.1 `WatchAppSpacing.kt` 생성
- [x] 2.2 `WatchAppShapes.kt` 생성 (xxs/sm/md10/md/lg/pill)
- [x] 2.3 `WatchEventColors.kt` 생성 (`eventColor` + `overlayStyle`)
- [x] 2.4 `Color.kt`에 `Cyan500` 추가 (STEAL 오버레이 전용)

## 3. Mobile 화면 토큰 적용
- [x] 3.1 `HomeScreen.kt` 전면 치환
- [x] 3.2 `SettingsScreen.kt` 전면 치환
- [x] 3.3 `OnboardingScreen.kt` 전면 치환 (그라디언트 중간색·로그인 완료 톤·카카오 브랜드 색은 Reason 주석)
- [x] 3.4 `WatchTestScreen.kt` 전면 치환 + 이벤트 버튼을 `AppEventColors.eventColor()`로 통합
- [x] 3.5 `LiveGameScreen.kt` 전면 치환 + `eventColor(_)` 헬퍼 제거, `AppEventColors.eventColor()` 사용
- [x] 3.6 `ThemeStoreScreen.kt` 전면 치환 (목업 테마 데이터는 Reason 주석)
- [x] 3.7 `CommunityScreen.kt` 전면 치환 (48sp 이모지는 Reason)
- [x] 3.8 `TeamLogo.kt` 치환 (동적 size 비례 폰트에 Reason 주석)

## 4. Watch 화면 토큰 적용
- [x] 4.1 `LiveGameScreen.kt` — 정적 값 치환 (margin 4dp→xs, RoundedCornerShape 10→md10, 2→xxs). 18dp 스코어 카드 코너는 Reason.
- [x] 4.2 `NoGameScreen.kt` — WatchAppSpacing으로 치환, 반응형 폰트는 Reason 주석

## 5. 이벤트 색상 중앙화
- [x] 5.1 Mobile LiveGameScreen의 `eventColor(_)` 헬퍼 제거 → `AppEventColors.eventColor()`
- [x] 5.2 Mobile WatchTestScreen 이벤트 버튼 배열 → `AppEventColors.eventColor()`

## 6. spec.md 업데이트
- [x] 6.1 "타이포그래피 (iPhone / Android Mobile)" Requirement 제목 및 시나리오 추가 — Android Compose용 `AppFont` object 사용 명시
- [x] 6.2 "토큰 준수 원칙"에 "신규 코드 작성 (Android Compose)" Scenario 추가

## 7. 검증
- [x] 7.1 `grep fontSize.*\.sp apps/mobile/.../screens/` — 의도된 예외 3건(64sp 온보딩 이모지, 48sp 커뮤니티 이모지) + 토큰 정의 파일 외 0
- [x] 7.2 `grep RoundedCornerShape\(\d apps/mobile/.../screens/` — 0개
- [x] 7.3 `grep Color\(0x apps/mobile/.../screens/` — Reason 주석 있는 곳(브랜드 색, 그라디언트 중간, 목업 데이터) 외 0
- [x] 7.4 `grep fontSize.*\.sp apps/watch/.../ui/` — WatchUiProfile 반응형 참조 외 0 (토큰 정의 파일 포함)
- [x] 7.5 `grep RoundedCornerShape\(\d apps/watch/.../ui/` — Reason 있는 18dp 스코어 카드 외 0
- [ ] 7.6 `./gradlew :mobile:assembleDebug` — 사용자 빌드 검증 필요
- [ ] 7.7 `./gradlew :watch:assembleDebug` — 사용자 빌드 검증 필요
- [ ] 7.8 시각 회귀 확인 — 실사용 중 확인

## 8. Non-Goals 재확인
- [ ] iOS ↔ Android 코드 공유 (Kotlin Multiplatform) — 별도 작업
- [ ] 웹 프로토타입 토큰 동기화 — 별도 change
- [ ] `TeamTheme.kt` ↔ `Team` 모델 `color` 속성 중복 정리 — 별도 change
- [ ] Style Dictionary 도입 (레벨 1 자동화) — 사용자 결정: 레벨 0 유지

## 9. 아카이브
- [x] 9.1 코드 작업 + grep 검증 완료
- [x] 9.2 `openspec/changes/2026-04-13-android-design-tokens/` → `openspec/changes/archive/`로 이동
