# Split Install vs Update Onboarding (설치=피처 가이드, 업데이트=안내 모달) + 분풀이 스텝

## Why

홈 스포트라이트 피처 가이드의 게이트가 `last_seen_feature_guide_version != 앱 버전`이라
**버전이 오를 때마다** 전체 스텝이 재생됐다. 그 결과 업데이트 사용자는 업데이트 안내 모달
(What's New) 확인 직후 신규 사용자용 코치마크(잠금화면 토글 등 5스텝)를 또 보게 됐다.
정책을 분리한다: 신규 설치 = 최초 온보딩 + 홈 피처 가이드(1회), 업데이트 = 안내 모달만.
또한 1.1.8 대표 기능인 분풀이 모드를 피처 가이드 스텝으로 추가한다.

## What Changes

### 노출 정책 분리 (iOS `BaseHapticApp` / Android `MainActivity`)

- 홈 피처 가이드 게이트를 버전 비교 → **1회성**(`last_seen_feature_guide_version`이
  비어 있을 때만)으로 변경. 같은 버전 재실행 경로는 신규 설치 직후 가이드를 끝내지
  못한 경우의 재개용으로만 동작한다.
- 업데이트 경로(및 키 도입 이전 기존 사용자 경로)는 진입 시 가이드 키를 **미노출 처리**
  (버전 기록)하고 안내 모달만 띄운다. 릴리즈 노트가 없는 버전이면 아무것도 안 띄운다
  (Android는 기존 알림 설정 프롬프트 체인 유지).
- What's New 확인 버튼에서 가이드로 이어지던 체인 제거
  (iOS `onConfirm`의 `queueFeatureGuideIfNeeded()`, Android `onConfirm`의
  `showFeatureGuideIfNeeded()` → 알림 설정 프롬프트 직행).
- **DEBUG 전용**: 같은 버전 재실행 시 가이드 재개가 없으면 업데이트 안내 모달을
  매 실행 노출(현재 버전 노트 없으면 최신 노트 폴백) — 카피·이미지 확인용.
  신규 설치·업데이트 경로와 릴리즈 빌드 동작은 그대로.

### 피처 가이드에 분풀이 스텝 추가 (iOS·Android HomeScreen)

- `UpdateHighlightStep`에 `venting`/`VENTING` 추가 (마지막 스텝):
  "빠따존 — 마이팀이 아쉽게 진 날, 홈에 이 카드가 나타나요…".
- 스텝 목록을 `activeSteps`(iOS) / `updateHighlightSteps`(Android)로 동적 구성 —
  분풀이 스텝은 `VentingFeatureFlag` 게이트가 열려 있을 때만 포함(릴리즈 빌드에선
  자동 제외, 추후 게이트 해제 시 자동 포함). 오버레이 인덱스/총계도 이 목록 기준.
- 가이드 진행 중에는 분풀이 홈카드 자리에 **쇼케이스 샘플 카드**를 렌더링해 하이라이트
  앵커를 보장한다(오버레이가 탭을 가로채므로 플로우 진입 없음). 실데이터 홈카드
  (`VentingHomeCardContainer`)는 가이드 종료 후 기존 조건대로 복귀.
- 스크롤: iOS는 분풀이 카드 전용 scroll target(center 앵커), Android는
  분풀이 스텝 → 아이템 4, 경기 카드 스텝(잠금화면·Watch·점수) → 아이템 5로 보정
  (가이드 중 샘플 카드가 아이템 4 높이를 차지하게 되어 기존 4 → 5).

## Impact

- iOS: `BaseHapticApp.swift`(트리거·게이트), `Screens/HomeScreen.swift`(스텝·앵커·스크롤).
- Android: `MainActivity.kt`(트리거·게이트), `ui/screens/HomeScreen.kt`(스텝·앵커·스크롤).
- 백엔드·워치 변경 없음. `add-update-onboarding-slides`(1.1.8 What's New 슬라이드)와 독립 —
  모달 내용은 그대로, 모달 이후 체인만 변경.
- 마이그레이션: 이 코드가 포함된 버전으로 업데이트하는 기존 사용자는 업데이트 경로에서
  가이드 키가 기록되므로 가이드를 다시 보지 않는다.

## Non-Goals

- 분풀이 DEBUG 게이트 해제(릴리즈 노출)는 별도 change — 해제 시 분풀이 스텝은 자동 포함.
- 설정에서 피처 가이드 다시 보기 진입점 추가.
