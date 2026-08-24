# rename-test-tools-add-phone-venting-entry — 테스트 도구 명칭 정리 + 휴대폰 빠따존 진입 버튼

## Why

설정 > 테스트 도구 화면 제목이 "워치 테스트"였지만 실제로는 잠금화면 알림·푸시
시뮬레이션 등 워치 외 기능 테스트도 포함한다. 빠따존 테스트 섹션도 "워치 빠따존
테스트"라는 이름으로 워치 진입만 제공해, 휴대폰 빠따존 플로우(대상 선택 → 룸 → 완파)는
홈카드·라이브 진입 조건(마이팀 패배 경기 등)을 만들어야만 테스트할 수 있었다.

## What Changes

- **화면 제목 (iOS·Android)**: "워치 테스트" → "기능 테스트".
- **섹션 명칭 (iOS·Android)**: "워치 빠따존 테스트" → "빠따존 분풀이 테스트",
  설명 문구도 "워치 또는 휴대폰에…"로 일반화.
- **휴대폰 빠따존 열기 버튼 추가 (iOS·Android)**: "워치 빠따존 열기" 아래 배치.
  탭 시 mock 컨텍스트(패배 1:7, 후보 5명 + 감독 — iOS `VentingDebugNavigator`와 동일
  데이터)로 폰 빠따존 플로우에 즉시 진입. 팀은 응원팀 사용(미설정 시 한화 폴백),
  지표 `entry_source`는 `test_tool`로 기록해 실사용 지표와 구분.
- **릴리즈 미노출**: iOS는 `VentingFlowCoordinator`가 DEBUG 전용이므로 `#if DEBUG`,
  Android는 `BuildConfig.DEBUG` 조건 — 릴리즈 빌드에는 휴대폰 버튼이 보이지 않는다
  (기존 VentingFeatureFlag 게이트와 동일 기준).

## Impact

- **클라이언트 전용 2파일**:
  - iOS `Screens/WatchTestScreen.swift` (제목·섹션 명칭, `phoneVentingButton` + mock 컨텍스트)
  - Android `ui/screens/WatchTestScreen.kt` (제목·섹션 명칭, 버튼 + `debugPhoneVentingContext`,
    `VentingFlowController.open` 재사용)
- **백엔드 변경 없음** — `/venting/events`는 entry_source를 자유 문자열로 저장하므로
  `test_tool` 그대로 기록됨.
- 홈 `WatchInstallCard`의 "워치 테스트" 라벨은 워치 체험 맥락이라 유지(범위 외).
- 빌드 검증: Android `:app:compileDebugKotlin` 통과, iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과.

## Non-Goals

- 설정 메뉴 행 라벨("테스트 도구") 변경.
- 워치 빠따존 트리거 경로 변경 (기존 `sendVentingTrigger` 그대로).
- 릴리즈 빌드 휴대폰 빠따존 노출 (venting 피처 게이트 해제와 함께 후속 검토).
