# Fix Settings Control Accent For Dark Team Themes

## Why

KT 위즈 응원 팀 선택 시 설정 > 알림 이벤트 화면에서 **ON/OFF 상태가 구분되지 않는** 문제 보고 (2026-08-18).

원인: 설정 화면의 토글·ON 칩·행 아이콘이 전부 `teamTheme.primary` 를 쓰는데, KT 의 primary 는 순수 검정(`#000000`)이라 gray900 카드 배경 위에서 보이지 않는다. ON 칩은 검정 배경 + gray950(거의 검정) 텍스트가 되어 OFF 와 시각적으로 동일해진다. 두산(`#131230`)·롯데(`#041E42`)도 같은 문제(짙은 남색), 키움(`#820024`)도 대비가 약하다.

팀 테마에는 이미 이 문제를 위해 존재하는 색이 있다: `navIndicator` 는 어두운 primary 팀(KT·두산·롯데 → 팀 레드, 키움 → 골드)에 대비 보장 색을 지정하고, 밝은 primary 팀은 primary 와 같은 값이다.

## What Changes

1. **`controlAccent` 토큰 추가** — iOS/Android `TeamTheme` 에 "gray900 카드 위 아이콘·토글 ON 표시용 액센트" 시맨틱으로 `controlAccent`(= `navIndicator`) 추가.
2. **설정 화면 전면 교체** — 양 플랫폼 SettingsScreen 의 `teamTheme.primary` 사용처를 모두 `controlAccent` 로 교체:
   - 알림 이벤트 매트릭스 ON 칩 배경/테두리 + 행 아이콘
   - 토글 행(Switch/Toggle) tint·checkedTrackColor + 아이콘
   - 일반 설정 행 아이콘, 팀 선택 체크/선택 배경, 잠금화면 스타일 라디오, promoted 안내 배너

밝은 primary 7개 팀은 `navIndicator == primary` 라 시각 변화 없음. KT·두산·롯데는 팀 레드, 키움은 골드로 ON 상태가 보이게 된다.

수정 파일:

- `ios/mobile/BaseHaptic/Theme/TeamTheme.swift` — `controlAccent` extension
- `ios/mobile/BaseHaptic/Screens/SettingsScreen.swift` — primary → controlAccent (8곳)
- `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/theme/TeamTheme.kt` — `controlAccent` extension val
- `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/SettingsScreen.kt` — primary → controlAccent (11곳)

## Non-Goals

- 설정 화면 외의 primary 사용처(홈·라이브 등)는 배경/그라데이션 용도가 많아 별도 검토 대상.
- 워치(watchOS/Wear)는 이벤트 필터 설정 UI 가 없어(폰에서 관리·동기화) 무변경.
- WatchTestScreen 의 ProgressView tint 등 테스트 화면은 제외.

## Capabilities

### Modified Capabilities

- `themes`: 어두운 배경 위 컨트롤 상태 표시(토글 ON, 선택 아이콘)는 primary 가 아닌 대비 보장 액센트(`controlAccent` = `navIndicator`)를 사용한다.
