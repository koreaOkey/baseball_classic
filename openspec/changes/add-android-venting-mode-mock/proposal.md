# add-android-venting-mode-mock — 분풀이 모드 Android 목업 (iOS Phase 1 동등성)

## Why

분풀이 모드 Phase 1 iOS 목업(add-venting-mode)이 완료·검증되었으나 Android 사용자는 같은 플로우를 테스트할 수 없다. 핵심 UX(지목 → 파괴 → 해소) 검증을 양 플랫폼에서 진행하기 위해 iOS 목업과 동일한 화면·데이터·게이트를 Android 홈에 추가한다.

## What Changes

- Android 앱에 분풀이 모드 4개 화면 추가: 홈카드 진입 → 아쉬운 순간 TOP5 선택 → 분풀이 룸(펭귄 인형 탭 연타 파괴) → 완파. iOS와 동일 레이아웃·문구·디자인 토큰 스케일.
- 파괴 메커니즘 동일 포팅: 게이지 1/40씩 증가, 3단계 파괴 연출(균열 0.33 → 터짐 0.66 → 완파 1.0), 도구 5종(맨손/뿅망치/야구방망이/슬리퍼/프라이팬) 내려치기 연출 + 히트 이펙트 + 스쿼시.
- 진동: 탭 경진동(50ms 스로틀) / 단계 전환 중진동 / 완파 성공 웨이브폼 — Vibrator 기반 `VentingHapticPlayer`.
- TOP5 데이터는 `MockRegretProvider`가 iOS mock JSON과 동일 값을 하드코딩 제공, 오늘(KST)+현재 마이팀 주입 — **백엔드는 수정하지 않는다**.
- 오픈 조건 판정(`VentingOpenConditionChecker`)은 실제 코드: 마이팀 패배 + 당일 KST + 무승부/취소 미오픈.
- 피처 플래그 `venting_mode_enabled`(SharedPreferences) + `BuildConfig.DEBUG` 이중 게이트. 설정 화면 DEBUG 섹션에 토글 추가 (iOS SettingsScreen 동일).
- 홈: 플래그 ON + 완료된 마이팀 경기 없음 → 목업 패배 경기(3:7) 주입 (iOS HomeScreen 동일). 분풀이 플로우는 풀스크린 Dialog로 표시.
- 스프라이트 10종(인형 4단계 + 도구 5 + 히트 이펙트)은 iOS Asset Catalog PNG를 drawable-nodpi로 복사.

### Non-Goals

- iOS Phase 1 Non-Goal 전부 동일 (백엔드 endpoint, 광고 게이트 실동작, 지표 수집, Wear OS 분풀이 룸, 실명 선수 인형).
- 릴리즈 소스셋 분리(res 분리) — 목업 단계에서는 main 소스셋 + 런타임 DEBUG 게이트로 유지.

## Capabilities

### Modified Capabilities

- `venting-mode`: 분풀이 모드 플로우가 Android에서도 동작 (iOS와 동일 요구사항).
- `mobile-android`: 홈 화면에 패배 당일 "오늘의 아쉬운 순간" 카드 노출 + 설정 DEBUG 토글.

## Impact

- **Android 전용**: `apps/mobile/app/src/main/java/com/basehaptic/mobile/venting/` 신규 패키지 (models, destruction 상태머신, mock provider, 조건 판정, 진동, 화면 5파일) + `HomeScreen.kt`·`SettingsScreen.kt` 통합 + drawable-nodpi 스프라이트 10종.
- **백엔드/DB/iOS/워치 영향 없음**.
- 에뮬레이터(Pixel 8) 검증 완료: 토글 → 홈카드 → 선택 → 룸(균열 38% 단계 전환) → 완파 화면 전 플로우 스크린샷 확인. 실기기 진동 검증 잔존.
