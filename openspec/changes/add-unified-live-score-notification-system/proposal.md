## Why

현재 워치 앱은 foreground일 때만 마스코트 펭귄 애니메이션 + 햅틱으로 이벤트를 적극 표시하고, 워치 홈/잠금 상태에서는 햅틱만 울리거나 정적인 "경기 관람 중" ongoing 노티만 표시된다. 폰도 잠금 상태에서 iOS Live Activity는 운영 중이지만 Android는 동등한 라이브 스코어 채널이 없어 사용자가 폰을 켜야만 점수를 확인할 수 있다. 또한 사용자가 보고 싶은 이벤트(SCORE/HOMERUN 등)를 선택적으로 받고 싶다는 요구가 있지만 현재 설정은 마스터 토글 수준에만 머문다. 이번 변경으로 폰/워치 양쪽에 통합된 라이브 스코어 ongoing 알림 + 사용자 지정 이벤트 long-look expand 패턴을 도입한다.

## What Changes

- 워치(iOS/Android) ongoing 노티를 정적 텍스트에서 **동적 스코어/이닝/BSO** 갱신 노티로 전환. 같은 notification id를 사용해 in-place replace.
- Android 폰에 **라이브 스코어 ongoing notification**(iOS Live Activity 대응) 신설. 잠금화면·알림 드로어에서 스코어 노출.
- 사용자별 이벤트 선택을 Watch와 잠금화면 채널로 분리 — 어떤 이벤트(SCORE/HOMERUN/HIT/WALK/STEAL/OUT/BALL/STRIKE 등)를 워치 알림 또는 잠금화면 강조로 받을지 선택. 백엔드 저장 + 모바일 설정 UI + 워치 sync.
- **워치 우선 햅틱 정책**: 워치 페어링 + 활성 상태면 폰 햅틱·푸시 suppress. 한 이벤트 = 한 채널만 발화.
- 이벤트 필터는 **채널별 강한 알림/강조 트리거에만 적용**. ongoing 스코어 갱신은 필터와 무관하게 항상 동작.
- 미수신 이벤트 큐잉 제거 — 양쪽 모두 off 상태에서 발생한 이벤트는 drop. 깨어났을 때 ongoing 노티가 최신 스코어 + 최근 이벤트 1개만 표시.
- 시나리오 3(P-fg + W-off): 워치 손목 미착용 가능성을 고려해 워치 햅틱·노티 발화 안 함. 폰 in-app UI만 갱신.
- Watch 보기/잠금화면 보기 자체는 경기 카드에서 제어하고, 설정에는 "알림 이벤트"의 Watch/잠금화면 채널별 이벤트 선택만 표시한다. 워치 이벤트 영상 토글은 별도 "워치 영상" 섹션으로 유지한다.
- **BREAKING**: 기존 Android 워치 ongoing notification 콘텐츠 모델 변경 (정적 → 동적). Static "경기 관람 중" 라벨 제거.
- **REMOVED**: Android Wear OS 타일 (`GameTileService`) 제거. ongoing 노티가 같은 정보를 더 풍부하게 제공하므로 deprecate.

## Capabilities

### New Capabilities
- `live-score-notification`: 폰/워치 양쪽의 라이브 스코어 ongoing 노티 + 채널별 선택 이벤트 알림 정책·payload·suppress 룰 정의

### Modified Capabilities
- `mobile-android`: 폰 라이브 스코어 ongoing notification 신설, 워치 우선 햅틱 suppress 가드 추가
- `mobile-ios`: 사용자 설정 UI에 Watch/잠금화면 채널별 이벤트 선택 추가, 워치 우선 햅틱 suppress 가드 추가
- `watch-android`: ongoing notification 동적 스코어 갱신, long-look expand 시 이벤트 필터 가드
- `watch-ios`: ongoing notification 패턴 신규 도입(같은 identifier replace), long-look expand 시 이벤트 필터 가드
- `realtime`: push payload에 이벤트 타입을 포함하고 채널별 사용자 이벤트 선택에 적용 (장기적으로는 클라이언트 가드 우선)

## Impact

- **백엔드**: 사용자별 Watch/잠금화면 이벤트 선택 컬럼 신설(Supabase user preferences), push payload에 이벤트 타입 포함(이미 있을 경우 재활용), 큐잉 로직 제거(있다면)
- **iOS 폰**: 설정 화면에 Watch/잠금화면 채널별 이벤트 선택 추가, 워치 페어링·활성 상태 감지해 햅틱·푸시 suppress 가드 추가
- **Android 폰**: 설정 화면에 Watch/잠금화면 채널별 이벤트 선택 추가, Live Activity 대응 ongoing notification 신설, 워치 우선 햅틱 suppress 가드 추가
- **iOS 워치**: ongoing 노티 패턴 도입(같은 identifier replace), long-look expand 가드, 기존 foreground suppress 정책 유지
- **Android 워치**: `MainActivity.kt` ongoing notification 콘텐츠 동적 갱신, `DataLayerListenerService.kt` long-look expand 가드, 기존 wakeScreenForEvent 정책 유지
- **공유**: 워치 페어링 상태를 폰이 판단할 수 있도록 watch connectivity 상태 확인 로직 보강 필요
- **테스트(열린 항목)**: iOS Live Activity + push 노티 동시 표시 시 시각적 노이즈 발생 여부 실기기 검증 — 발견 시 suppress 정책 추가
- **변경 없음**: 시나리오 1(P-fg+W-fg) 기존 in-app UI 그대로, 시나리오 7(P-off+W-fg) 워치 독립 모드(이미 구현됨)
- **제거**: Android Wear OS 타일 `GameTileService` 및 관련 manifest 항목 (apps/watch/app/src/main/java/com/basehaptic/watch/tile/)
