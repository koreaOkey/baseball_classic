# Tasks

## iOS
- [x] `ios/mobile/BaseHaptic/Screens/ThemeStoreScreen.swift` — 파일 최상단에 `private let SHOW_STADIUM_CHEER_THEMES = false` 추가, `effectiveSection` computed property 도입. 헤더 응원 테마 라인 / `sectionToggle` / 목업 안내 텍스트 모두 플래그 게이트. `themeGrid` 의 `isCheerSection` 도 `effectiveSection` 기준으로 평가.
- [x] `ios/mobile/BaseHaptic/Screens/SettingsScreen.swift` — `SettingsItemWithToggle("경기장 응원", ...)` 블록 제거. 미사용 된 `@AppStorage("stadium_cheer_enabled") private var stadiumCheerEnabled = true` 선언 제거.
- [x] `ios/mobile/BaseHaptic/BaseHapticApp.swift` — `private let SHOW_MY_TEAM_TAB` 을 `true` → `false`. 하단 네비 "내 팀" 탭 미노출. Android 와 동일.

## 검증
- [ ] iOS 시뮬레이터: 상점 → Watch Themes 탭 → 베이직 그리드만 노출, "베이직 / 현장 응원" 칩 미노출, 헤더에 "응원 테마: ..." 라인 미노출.
- [ ] iOS 시뮬레이터: 설정 → "이벤트 영상 알림" 다음 항목이 바로 "정보" 섹션. "경기장 응원" 토글 없음.
- [ ] iOS 시뮬레이터: 하단 네비에 홈 / 상점 / 설정 3개 탭만 노출, "내 팀" 탭 없음.
- [ ] 빌드 경고 없음 (unused warning 없음).

## 후속
- [ ] 시각 확인 통과 후 archive 로 이동.
