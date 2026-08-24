# Tasks

## 1. 토큰 추가

- [x] 1.1 iOS `TeamTheme` extension `controlAccent: Color { navIndicator }` (`Theme/TeamTheme.swift`)
- [x] 1.2 Android `val TeamTheme.controlAccent get() = navIndicator` (`ui/theme/TeamTheme.kt`)

## 2. 설정 화면 적용

- [x] 2.1 iOS SettingsScreen — `teamTheme.primary` 8곳 전부 `controlAccent` 로 교체 (이벤트 매트릭스 칩·아이콘, Toggle tint, SettingsItem 아이콘, 팀 선택)
- [x] 2.2 Android SettingsScreen — `teamTheme.primary`/`theme.primary` 11곳 전부 `controlAccent` 로 교체 (EventChannelToggleChip, Switch checkedTrackColor, 행 아이콘, 팀 선택, 잠금화면 스타일 라디오, promoted 배너) + import 추가

## 3. 설정 플로우 잔존 적용 (2026-08-19 확장)

- [x] 3.1 iOS WhatsNewSheet — `teamTheme.primary` 4곳(페이지 dot, 다음/확인 버튼, 불릿 체크 원) `controlAccent` 로 교체
- [x] 3.2 Android WhatsNewDialog — accentColor 2곳 `controlAccent` 로 교체 + import 추가
- [x] 3.3 Android SettingsScreen 계정 삭제 다이얼로그 "취소" TextButton — 기본 colorScheme.primary(KT=검정) 대신 Gray400 명시

## 4. 검증

- [x] 4.1 iOS 시뮬레이터 빌드 통과 (BaseHaptic 스킴)
- [x] 4.2 Android `:mobile:compileDebugKotlin` 통과
- [ ] 4.3 실기기/시뮬레이터에서 KT 테마로 설정 > 알림 이벤트 ON/OFF 시각 구분 확인 (두산·롯데·키움도 스팟 체크) — 두산·롯데·KT는 새 빌드 설치 후 확인 필요
- [ ] 4.4 WhatsNew 다이얼로그(설정 > 릴리즈 노트)에서 KT·두산·롯데 테마 액센트 가시성 확인
