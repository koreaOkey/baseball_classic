## 1. 토큰 파일 생성 (iPhone)
- [ ] 1.1 `ios/mobile/BaseHaptic/Theme/AppFont.swift` 생성 — display/title/body/caption 스케일
- [ ] 1.2 `ios/mobile/BaseHaptic/Theme/AppSpacing.swift` 생성 — xxs(2)/xs(4)/sm(8)/md(12)/lg(16)/xl(20)/xxl(24)/xxxl(32)
- [ ] 1.3 `ios/mobile/BaseHaptic/Theme/AppRadius.swift` 생성 — sm(8)/md(12)/lg(16)/xl(20)/pill(999)
- [ ] 1.4 `ios/mobile/BaseHaptic/Theme/AppEventColors.swift` 생성 — HIT/WALK/STEAL/SCORE/HOMERUN/DOUBLE_PLAY/TRIPLE_PLAY/OUT/STRIKE/BALL/VICTORY 매핑

## 2. 토큰 파일 생성 (Watch)
- [ ] 2.1 `ios/watch/BaseHapticWatch/Theme/WatchAppSpacing.swift` 생성 — iPhone과 동일 스케일
- [ ] 2.2 `ios/watch/BaseHapticWatch/Theme/WatchAppRadius.swift` 생성 — iPhone과 동일 스케일
- [ ] 2.3 `ios/watch/BaseHapticWatch/Theme/WatchAppEventColors.swift` 생성 — `WatchColors` 기반

## 3. A급 — 하드코딩 hex → AppColors 치환 (무위험)
- [ ] 3.1 `WatchTestScreen.swift:224-226` BSO 카운트 (green400/yellow400/red400)
- [ ] 3.2 `WatchTestScreen.swift:273-274` 시뮬레이션 중지 버튼 (red400)
- [ ] 3.3 `WatchTestScreen.swift:294-301` 이벤트 색상 배열 — `AppEventColors` 사용으로 전환
- [ ] 3.4 `BaseHapticApp.swift:554` 탭바 비활성 색 (gray500)

## 4. iPhone 화면 — 토큰 적용
- [ ] 4.1 `HomeScreen.swift` — font/padding/radius 전체 치환, spacing outlier 확인
- [ ] 4.2 `LiveGameScreen.swift` — font/padding/radius 치환, `eventColor(_:)` 헬퍼를 `AppEventColors`로 교체
- [ ] 4.3 `OnboardingScreen.swift` — font/padding/radius 치환, Color(hex: 0x0F172A) 배경 그라디언트는 유지(원본 디자인 색)
- [ ] 4.4 `SettingsScreen.swift` — font/padding/radius 치환
- [ ] 4.5 `WatchTestScreen.swift` — font/padding/radius 치환 (3번 작업 포함)

## 5. LiveActivity
- [ ] 5.1 `BaseHapticLiveActivityWidget.swift` — font/padding 치환 (size 9/10/11/15/24 전용 토큰 추가 필요 시 AppFont에 추가)

## 6. Watch 화면 — 토큰 적용
- [ ] 6.1 `WatchLiveGameScreen.swift` — padding/radius 치환
- [ ] 6.2 `WatchNoGameScreen.swift` — padding 치환
- [ ] 6.3 `HomeRunTransitionScreen.swift`
- [ ] 6.4 `VictoryTransitionScreen.swift`
- [ ] 6.5 `HitTransitionScreen.swift`
- [ ] 6.6 `DoublePlayTransitionScreen.swift`
- [ ] 6.7 `ScoreTransitionScreen.swift`
- [ ] 6.8 `BaseHapticWatchApp.swift` — padding/radius 치환

## 7. spec.md 업데이트
- [ ] 7.1 `openspec/specs/design-system/spec.md`에 컬러 팔레트 Requirement에 실제 토큰 값 표 추가
- [ ] 7.2 팀 테마 Requirement 신규 추가 (10구단 × 7슬롯)
- [ ] 7.3 타이포그래피 Requirement에 iPhone AppFont 스케일 추가
- [ ] 7.4 스페이싱 Requirement 신규 추가
- [ ] 7.5 반경 시스템 Requirement에 실제 값 명시
- [ ] 7.6 이벤트 색상 Requirement 신규 추가
- [ ] 7.7 토큰 준수 원칙 Requirement 추가 (새 코드는 하드코딩 금지)

## 8. 검증
- [ ] 8.1 `grep -rn "\.system(size:" ios/mobile/BaseHaptic/Screens/` — 결과 0개 확인 (LiveActivity 제외 시)
- [ ] 8.2 `grep -rn "Color(hex:" ios/mobile/BaseHaptic/Screens/` — 의도적 예외 외 0개
- [ ] 8.3 `grep -rn "cornerRadius([0-9]" ios/` — 토큰 이외 0개
- [ ] 8.4 Xcode 빌드 성공 (iPhone + Watch 타겟)
- [ ] 8.5 시각 회귀 확인 — 주요 화면 스크린샷 전/후 비교 (Home, LiveGame, Settings, Onboarding, Watch Live, Watch Transition)
- [ ] 8.6 팀 변경 시 색상 반영 정상 동작

## 9. 아카이브
- [ ] 9.1 모든 작업 완료 확인
- [ ] 9.2 `openspec/changes/2026-04-13-design-tokens/` → `openspec/changes/archive/2026-04-13-design-tokens/`로 이동
