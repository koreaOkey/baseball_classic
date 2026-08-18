# Tasks

## 1. 토큰 추가

- [x] 1.1 iOS `TeamTheme` extension `controlAccent: Color { navIndicator }` (`Theme/TeamTheme.swift`)
- [x] 1.2 Android `val TeamTheme.controlAccent get() = navIndicator` (`ui/theme/TeamTheme.kt`)

## 2. 설정 화면 적용

- [x] 2.1 iOS SettingsScreen — `teamTheme.primary` 8곳 전부 `controlAccent` 로 교체 (이벤트 매트릭스 칩·아이콘, Toggle tint, SettingsItem 아이콘, 팀 선택)
- [x] 2.2 Android SettingsScreen — `teamTheme.primary`/`theme.primary` 11곳 전부 `controlAccent` 로 교체 (EventChannelToggleChip, Switch checkedTrackColor, 행 아이콘, 팀 선택, 잠금화면 스타일 라디오, promoted 배너) + import 추가

## 3. 검증

- [x] 3.1 iOS 시뮬레이터 빌드 통과 (BaseHaptic 스킴)
- [x] 3.2 Android `:mobile:compileDebugKotlin` 통과
- [ ] 3.3 실기기/시뮬레이터에서 KT 테마로 설정 > 알림 이벤트 ON/OFF 시각 구분 확인 (두산·롯데·키움도 스팟 체크)
