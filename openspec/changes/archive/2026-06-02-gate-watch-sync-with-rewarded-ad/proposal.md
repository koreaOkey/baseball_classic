## Why

진행 중인 경기 상세 화면에서 사용자가 워치 동기화 ON/OFF 를 직접 제어할 수 있는 토글이 없다. 현재는 관심 팀 LIVE 전환 시 자동 프롬프트만 발생하며, 다른 경기로 전환하거나 임시로 끄고 싶을 때 명시적 진입점이 없다.

또한 보상형 광고(AdMob Rewarded) SDK 가 이미 통합되어 테마 스토어에서 사용 중이고 AdMob 콘솔 앱 등록(iOS `ca-app-pub-7935544989894266~4763404952` / Android `ca-app-pub-7935544989894266~1737773050`)도 완료된 상태다. 라이브 경기 동기화라는 high-intent 액션에 광고 게이트를 붙이면 수익화 위치가 한 곳 더 확보된다.

## What Changes

- 라이브 경기 상세 상단바의 워치 동기화 캡슐 뱃지 옆에 **슬라이드 토글**을 추가한다.
- 토글 OFF→ON 탭 시 토글이 즉시 슬라이드(낙관적)된 뒤 "워치로 보시겠습니까? / 광고 관람 후 동기화됩니다" 팝업이 나타나며, 확인 시 **AdMob Rewarded Video** 가 재생된다.
- 광고 시청 완료 콜백에서 `syncedGameId` 를 해당 경기로 설정한다. 광고 로드 실패 시에도 동기화는 허용한다(폴백).
- 사용자가 팝업에서 취소하면 토글은 슬라이드 되돌리기 애니메이션으로 OFF 복귀한다.
- 토글 ON→OFF 탭 시 "워치 동기화를 끄시겠습니까?" 팝업이 뜨며, 확인 시 즉시 OFF(광고 없음).
- 광고 시청 빈도는 **경기당 1회** — 같은 `gameId` 에 대해 이미 광고를 본 적이 있으면 토글 ON 시 광고 생략하고 바로 동기화한다.
- 워치 동기화 위치 전용 AdMob Rewarded 광고 단위 ID 를 코드에 신규 추가한다(테마 스토어 ad unit 과 분리).
  - iOS: `ca-app-pub-7935544989894266/6602098213`
  - Android: `ca-app-pub-7935544989894266/8231864339`
- 광고 entitlement 범위는 워치 동기화만 — 폰 푸시 정책은 기존 그대로 유지한다.
- 메인 홈 화면의 "관람중" UI(`syncedGameId` 기반 카드 표시)는 기존 흐름을 그대로 사용하므로 별도 변경 없다.

## Capabilities

### New Capabilities

- 없음

### Modified Capabilities

- `mobile-ios`: iOS 모바일 앱은 라이브 경기 상세에서 사용자 토글로 워치 동기화를 켜고 끌 수 있어야 하며, ON 전환 시 보상형 광고 게이트를 통과해야 한다.
- `mobile-android`: Android 모바일 앱은 iOS 와 동일한 위치/동작의 토글과 광고 게이트를 제공해야 한다.

## Impact

- iOS Mobile: `LiveGameScreen.swift` 상단바 토글 추가, `RewardedAdManager.swift` 호출 위치 ID 매개변수화 및 실패 콜백 처리, 경기별 광고 시청 플래그 UserDefaults 저장.
- Android Mobile: `LiveGameScreen.kt` 상단바 토글 추가, `RewardedAdManager.kt` 호출 위치 ID 매개변수화 및 실패 콜백 처리, 경기별 광고 시청 플래그 SharedPreferences 저장.
- AdMob 콘솔: 워치 동기화용 Rewarded 광고 단위 2개(iOS `…/6602098213`, Android `…/8231864339`) 이미 발급 완료.
- Watch Android/iOS: 동기화 활성화 메커니즘 자체는 기존 그대로(`syncedGameId` 갱신만). 변경 대상 아님.
- Backend: 변경 없음.
- DB Migration: 필요 없음.
