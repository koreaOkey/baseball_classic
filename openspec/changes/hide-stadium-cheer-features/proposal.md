## Why

"현장 응원 / 경기장 응원" 관련 기능은 아직 운영에 안정적으로 올릴 수 있는 상태가 아니다(목업 표기 + 실 동작 미검증). 사용자 노출 화면에서는 잠시 가려두되, 코드 / 데이터 / 백엔드 흐름은 그대로 보존해서 추후 재오픈 시 플래그 토글만으로 복귀할 수 있게 한다.

이미 Android 는 `SHOW_STADIUM_CHEER_THEMES = false` 컴파일 상수로 가려져 있고 Android 설정 화면에는 "경기장 응원" 항목이 존재하지 않는다. iOS 만 양쪽 진입점이 노출되어 있어 플랫폼 간 비대칭이 있다. 본 변경은 iOS 를 Android 와 같은 상태로 맞춘다.

## What Changes

- iOS `ThemeStoreScreen.swift`: 파일 스코프 상수 `private let SHOW_STADIUM_CHEER_THEMES = false` 도입. Android 와 동일한 명칭. 헤더 "응원 테마: ..." 라인, "베이직/현장 응원" 섹션 토글 (`sectionToggle`), 토글 하단 "응원 시각에 워치 풀스크린에 적용됩니다 (목업)" 안내 문구를 모두 플래그로 가린다. 플래그 off 시 `effectiveSection` 은 항상 `.basic` 으로 강제. 데이터(`StadiumCheerThemes`, `WatchThemeSection.stadiumCheer`), 콜백(`onApplyCheerTheme`), 컴포넌트(`ThemeCard` / `MiniWatchPreview`) 는 그대로 유지.
- iOS `SettingsScreen.swift`: "경기장 응원" `SettingsItemWithToggle` 항목 제거. 미사용이 된 `@AppStorage("stadium_cheer_enabled") private var stadiumCheerEnabled` 도 같이 제거. UserDefaults 키 자체(`stadium_cheer_enabled`) 와 `BaseHapticApp.swift` 의 게이트 로직(`UserDefaults.standard.bool(forKey: "stadium_cheer_enabled")`)은 보존 → 디폴트 `true` 유지로 백엔드 동작은 변동 없음.
- iOS `BaseHapticApp.swift`: 기존 `private let SHOW_MY_TEAM_TAB` 플래그를 `true` → `false` 로 전환. 하단 네비 "내 팀" 탭이 미노출. `Screen.myTeam` enum, `MyTeamScreen` 라우팅, `myTeamGame` 헬퍼 등 코드는 모두 보존.
- Android: 이미 `SHOW_STADIUM_CHEER_THEMES = false`, `SHOW_MY_TEAM_TAB = false` 로 지정되어 있어 변경 없음.
- 백엔드 / 워치 / 데이터 모델 변경 없음.

## Capabilities

### Modified Capabilities

- `mobile-ios:theme-store`: 워치 테마 탭이 베이직 섹션 단독으로 표시된다. 섹션 토글, 응원 테마 상태 라인, 목업 안내 문구는 컴파일 타임 플래그 `SHOW_STADIUM_CHEER_THEMES` 로 게이트.
- `mobile-ios:settings`: "정보" 섹션 직전에 있던 "경기장 응원" 토글이 사라진다. 이벤트 영상 알림 토글이 정보 섹션 바로 위로 이동.

### Removed (UI only) / Hidden Capabilities

- `mobile-ios:stadium-cheer-toggle`: 사용자 노출 표면 일시 제거. UserDefaults 키와 백엔드 게이트는 유지하므로 차후 토글만 다시 노출하면 즉시 동작 가능.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 플래그 의도와 다르게 빌드에서 토글이 다시 노출됨 | `SHOW_STADIUM_CHEER_THEMES = false` 단일 지점 제어. iOS/Android 명칭 통일 |
| `stadium_cheer_enabled` 디폴트 `true` 라 백엔드가 응원 페이로드를 계속 처리 | 의도적 — 백엔드 로직은 유지. UI 만 가림. 운영 노출 차단은 별도 backend gate 필요 시 후속 change |
| 미사용 코드(`sectionToggle`, `currentSectionThemes`, `WatchThemeSection.stadiumCheer`) 가 검토 시 혼란 | 플래그 주석 + Android 와 동일 패턴이라 인지 가능. 추후 영구 제거 결정 시 별도 change |

## Non-Goals

- `StadiumCheerThemes`, `StadiumRegionMonitor`, `CheerSignalsLoader` 등 응원 도메인 모델/매니저 영구 제거.
- Android 측 변경 (이미 동일 상태).
- 백엔드 응원 페이로드 비활성화.
- 워치/Wear OS 응원 코드 정리.
- WatchTestScreen "현장 응원 테스트" 항목 정리 (개발자 전용 화면이라 별도).

## Rollout

1. 본 PR 머지 → 다음 iOS 빌드에 함께 포함.
2. 플래그 재오픈 시: `SHOW_STADIUM_CHEER_THEMES = true` 로 변경 + `SettingsScreen` 에 토글 한 줄 복원만 하면 됨.

### Rollback

- `git revert` 한 번. UserDefaults 키는 변동 없음.

## Status

- [x] iOS `ThemeStoreScreen.swift` 플래그 도입 + 가시성 게이트
- [x] iOS `SettingsScreen.swift` "경기장 응원" 토글 제거 + 미사용 AppStorage 정리
- [ ] 시뮬레이터에서 상점 → 워치 테마 탭 / 설정 화면 시각 확인
- [ ] archive
