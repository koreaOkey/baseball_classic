# Ad Loading Notice Overlay ("곧 광고가 시작됩니다")

## Why

보상형 광고가 프리로드 없이 확인 시점에 네트워크 로드되는 구조라(iOS `RewardedAdManager.swift`, Android `RewardedAdManager.kt`), 토글 확인 → 광고 표시까지 1~4초의 무반응 구간이 생겨 지연으로 체감됐다. 프리로드 대신 대기 안내를 먼저 도입하기로 결정 (프리로드는 광고 요청 낭비 부작용이 있어 필요 확인 후 후속 검토).

## What Changes

- iOS: `mainContent`에 `rewardedAdManager.isLoading` 기반 풀스크린 오버레이(딤 + 스피너 + "곧 광고가 시작됩니다") 추가. 로드 완료 직전 isLoading 해제 시 자동 소멸.
- Android: `RewardedAdLoadingOverlay` 컴포저블 신설(`RewardedAdManager.isLoading` StateFlow 구독), `MainActivity` 루트 `BaseHapticTheme` 안 Box 오버레이로 장착.
- 매니저의 기존 isLoading 상태를 그대로 사용 — 워치 동기화·잠금화면·테마 상점 광고 유닛 모두 자동 적용.

## Capabilities

### rewarded-ad-ux

- 광고 로드 중에는 "곧 광고가 시작됩니다" 안내가 표시되어 무반응 구간이 없다.
- 로드 실패/즉시 로드 시에도 상태 해제와 함께 안내가 사라진다.

## Impact

- `ios/mobile/BaseHaptic/BaseHapticApp.swift`
- `apps/mobile/.../ui/components/RewardedAdLoadingOverlay.kt`(신규), `MainActivity.kt`
- 백엔드·워치 영향 없음. 양쪽 빌드 통과.
