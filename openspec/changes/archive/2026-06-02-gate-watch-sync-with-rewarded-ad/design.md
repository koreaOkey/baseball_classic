## Context

`add-unified-live-score-notification-system`(2026-05-13)·`redesign-live-game-detail-screen`(2026-05-22) 작업으로 라이브 경기 상세 화면이 야구장형 정보 구조로 재정비됐고, 워치 동기화 상태는 상단바의 캡슐 뱃지(시계 아이콘 + 상태 점)로 표시되고 있다. 그러나 동기화 자체를 켜고 끄는 사용자 액션 진입점은 자동 프롬프트(관심 팀 LIVE 전환 시) 외에는 없다.

AdMob Rewarded SDK 는 이미 iOS·Android 양쪽에 통합되어 테마 스토어에서 운영 중이다.

- iOS: `ios/mobile/BaseHaptic/Components/RewardedAdManager.swift`
  - 테마 스토어 ad unit (프로덕션): `ca-app-pub-7935544989894266/6775093261`
  - 앱 ID(`GADApplicationIdentifier` in Info.plist): `ca-app-pub-7935544989894266~4763404952`
- Android: `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/components/RewardedAdManager.kt`
  - 테마 스토어 ad unit (프로덕션): `ca-app-pub-7935544989894266/3246911798`
  - 앱 ID(`com.google.android.gms.ads.APPLICATION_ID` in AndroidManifest): `ca-app-pub-7935544989894266~1737773050`

두 매니저 모두 현재는 단일 ad unit ID 가 하드코드돼 있고, 광고 로드 실패 시 콜백을 호출하지 않는 구조라서 새 위치 추가 시 시그니처 확장이 필요하다.

이번 변경에서 신규 도입되는 광고 단위는 다음과 같다(AdMob 콘솔에서 발급 완료, 앱 ID 등록 불필요).

- iOS Watch Sync Rewarded: `ca-app-pub-7935544989894266/6602098213`
- Android Watch Sync Rewarded: `ca-app-pub-7935544989894266/8231864339`

이번 변경은 라이브 경기 상세 화면에 한정되며, 워치 측 코드(`BaseHapticWatch`, `apps/watch`)는 건드리지 않는다.

## Goals / Non-Goals

**Goals**

- 라이브 경기 상세에서 사용자가 직접 워치 동기화를 켜고 끌 수 있는 토글 진입점 제공.
- 동기화 ON 1회당 AdMob Rewarded Video 1편 시청을 의무화하되, 같은 경기에 대한 반복 토글에서는 광고를 생략한다.
- iOS 와 Android 가 동일한 시각·동작·카피를 사용한다.
- 광고 로드/표시 실패가 동기화 사용성을 막지 않도록 폴백을 둔다.

**Non-Goals**

- 워치 OS 화면의 동기화 토글 UI(워치 앱 자체 토글은 별도 작업).
- 광고 entitlement 의 폰 푸시 적용(현재는 워치 동기화만 게이트).
- 광고 시청 이력의 서버 저장(전부 클라이언트 로컬 저장).
- 보상형 광고 외 다른 포맷(전면/배너) 도입.
- 백엔드 변경 또는 DB 마이그레이션.

## Decisions

1. **토글 UI 위치는 라이브 경기 상세 상단바 — LIVE 뱃지 옆 캡슐 뱃지 옆**.
   - 이유: `redesign-live-game-detail-screen` 6.6 에서 워치 동기화 상태가 이미 상단바 캡슐 뱃지에 통합됐다. 같은 줄에 액션(토글)과 상태(뱃지)를 모으면 사용자가 한 곳에서 완결된다.
   - 대안: 풀폭 CTA 카드(스코어보드 아래)도 검토했으나, ON 상태에서도 자리를 차지하거나 사라지는 시각 변동이 발생한다. 토글 + 뱃지 조합은 ON/OFF 양 상태 모두에서 시각 안정적이다.

2. **토글 인터랙션은 낙관적(optimistic) 슬라이드 → 확인 팝업 → 취소 시 슬라이드 백**.
   - 이유: iOS Settings 의 다수 토글이 이 패턴을 사용하며, 시각 만족도가 높다.
   - 대안: 비관적(팝업 먼저)은 안정적이지만 둔하게 느껴진다. 토글이 절대 destructive 액션이 아니므로 낙관적 패턴이 적합하다.

3. **광고 빈도는 경기당 1회 — `gameId` 기준 시청 완료 플래그를 영구 저장**.
   - 이유: 사용자가 같은 경기를 보다가 한두 번 끄고 켜는 케이스(전화 등)에 매번 광고를 강제하면 거부감이 크다. 경기 하나당 한 번이면 entry point 1개당 광고 1회로 일정하다.
   - 대안: 24h 쿨다운, 매번 강제, 첫 ON 영구. "경기당 1회"는 직관적이고 사용자가 예측 가능하다.
   - 저장: iOS `UserDefaults` (`watchSyncAdViewed.<gameId>` Bool 키), Android `SharedPreferences` (`watch_sync_ad_viewed_<gameId>` Bool 키). 별도 정리 작업 없이 키 누적은 허용(평균 일일 ~10개 미만).

4. **광고 로드/표시 실패 시 동기화는 허용**.
   - 이유: 광고는 부가 수익이지 동기화의 본질 기능이 아니다. 네트워크 단절·SDK 실패가 사용자 핵심 기능을 막으면 안 된다.
   - 구현: `RewardedAdManager` 의 콜백 시그니처를 `onComplete: (rewardEarned: Bool) -> Void` 로 확장하여 실패도 콜백을 호출하도록 변경한다. UI 는 reward 여부와 무관하게 ON 처리한다(단, 경기당 1회 플래그는 reward earned 인 경우에만 저장한다 — 실패 ON 은 다음 ON 에 다시 광고 시도).
   - 대안: 실패 시 차단하면 어뷰즈 방지에는 강하지만 사용자 신뢰가 깨진다.

5. **워치 동기화용 ad unit ID 는 신규 발급해 분리한다**.
   - 이유: AdMob 콘솔에서 광고 위치별 eCPM·전환율·실패율을 분리 추적해야 향후 광고 위치 추가·제거 의사결정이 가능하다.
   - 구현: `RewardedAdManager` 호출 시 `adUnitId` 매개변수를 받도록 시그니처 확장. 양 플랫폼 동일.
   - 확정 ID(프로덕션): iOS `ca-app-pub-7935544989894266/6602098213`, Android `ca-app-pub-7935544989894266/8231864339`.
   - DEBUG 빌드는 기존 매니저 패턴 유지 — Google 표준 테스트 ID 사용(iOS `ca-app-pub-3940256099942544/1712485313`, Android `ca-app-pub-3940256099942544/5224354917`).

6. **`syncedGameId` 갱신 = 동기화 ON 의 단일 트리거**.
   - 이유: 메인 홈 화면(`HomeScreen.swift:157`, `HomeScreen.kt:346`)이 이미 `syncedGameId` 기반으로 "관람중" 표시를 한다. 광고 콜백에서 이 값만 갱신하면 홈 UI 가 자동 반응한다.
   - 워치 측 동작(WCSession / DataLayer 동기화 시작)은 기존 `syncedGameId` 변경 옵저버가 처리한다. 새 워치 코드 변경 불필요.

## Risks / Trade-offs

- **[Risk]** 광고 실패 시 자동 ON 정책이 어뷰즈될 수 있다(예: 항공 모드로 광고 우회).
  → 영향이 미미하며(광고 1편 가치 ≈ 수십 원), 사용자 신뢰가 더 중요하다. 모니터링은 AdMob 콘솔의 광고 표시율로 충분.
- **[Risk]** 경기당 1회 정책으로 사용자가 광고 한 번 보고 같은 경기를 종일 ON/OFF 반복할 수 있다.
  → 의도된 동작이다. 사용자 경험을 우선시한다.
- **[Risk]** `UserDefaults`/`SharedPreferences` 키 누적(시즌 누적 ~수백 게임).
  → 키 크기 미미하며, 향후 광고 1회 정책 변경 시 마이그레이션과 함께 정리 가능.
- **[Risk]** 낙관적 토글이 광고 로드 지연 시 어색해 보일 수 있다.
  → 팝업 확인 → 광고 로드 중 spinner 표시. 광고 SDK 평균 로드 시간 1초 미만으로 체감 무리 없다.
- **[Risk]** iOS·Android 토글 컴포넌트 시각 차이.
  → 양 플랫폼 네이티브 Switch(SwiftUI `Toggle` / Compose `Switch`)를 사용하되, 색상은 디자인 토큰(`AppColors.primary` / `MaterialTheme.colorScheme.primary`)으로 통일한다.

## Migration Plan

1. iOS·Android 양쪽 `RewardedAdManager` 콜백 시그니처를 `onComplete(rewardEarned: Bool)` 로 확장하고, ad unit ID 를 호출 시점에 받도록 변경한다. 기존 테마 스토어 호출처를 새 시그니처에 맞춰 업데이트한다.
2. 워치 동기화 광고 단위 ID 상수 추가 — iOS `ca-app-pub-7935544989894266/6602098213`, Android `ca-app-pub-7935544989894266/8231864339`. DEBUG 빌드는 Google 표준 테스트 ID.
3. 라이브 경기 상세 상단바에 토글 컴포넌트 추가 + 두 종류의 확인 팝업 구현.
4. 광고 시청 플래그 영구 저장 로직 추가(iOS UserDefaults, Android SharedPreferences).
5. 광고 시청 완료/실패 콜백에서 `syncedGameId` 갱신.
6. OFF 토글 흐름(팝업 → 즉시 OFF) 구현.
7. 빌드 검증(iOS/Android).
8. 실기기 검증 — DEBUG 빌드 테스트 광고 재생 확인 → 프로덕션 빌드에서 실광고 재검증.

Rollback 은 토글 컴포넌트 노출만 조건부로 끄거나(예: feature flag), 코드 revert. DB·백엔드 변경이 없으므로 데이터 마이그레이션은 없다.

## Open Questions

- 향후 경기 종료 후 광고 플래그를 자동 정리할지(예: `UserDefaults` 캐시 트리밍 잡)는 후속 과제로 분리.
