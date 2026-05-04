## Why

기존 라이브 화면은 BSO·베이스 다이어그램·점수만 보여주고, 경기 흐름을 끊는 두 신호(코칭스태프 마운드 방문, 투수 교체)를 사용자에게 알리지 않았다. 사용자 요청: WALK/SCORE처럼 짧은 풀스크린 오버레이 팝업으로 노출해 응원 흐름을 끊지 않으면서도 인지 가능하게 한다.

조사 결과:
- 네이버 스포츠 라이브 API는 "비디오 판독 진행 중" 신호는 제공하지 않음(결과 통보만). 반면 **마운드 방문**은 `option.type=7` + 텍스트 `"마운드 방문"` / `"코칭스태프 마운드"` 패턴, **투수 교체**는 `option.type=2` + `playerChange` 블록 (`inPlayer/outPlayer.playerPos="투수"`)으로 즉시 식별 가능.
- 워치·iPhone에는 이미 WALK/SCORE/OUT 등을 띄우는 풀스크린 오버레이 인프라가 존재 — Android Watch [WatchEventOverlay](apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt#L716-L764), iOS Watch [WatchEventOverlay](ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift). 신규 컴포넌트 없이 이벤트 타입 두 종류만 추가하면 재활용 가능.

## What Changes

### 백엔드 분류
- [crawler/backend_sender.py](crawler/backend_sender.py) `_classify_event_type()`에 `MOUND_VISIT` 분류 추가 (`type=7` + "마운드 방문" / "코칭스태프 마운드" / "mound visit" 텍스트). 기존 `PITCHER_CHANGE` 분류는 유지.
- 이벤트 페이로드 빌드 시 `PITCHER_CHANGE`인 경우 `metadata.outName` / `metadata.inName`을 명시적으로 채워 클라이언트가 부가 정보를 활용할 수 있게 함.

### 모바일 → 워치 매핑 (화이트리스트)
- iOS [BaseHapticApp.swift](ios/mobile/BaseHaptic/BaseHapticApp.swift): `mapToWatchEventType`에 `PITCHER_CHANGE`, `MOUND_VISIT` 추가.
- Android [GameSyncForegroundService.kt](apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt): 동일.

### 색상 매핑 (모바일·워치 일관)
- `PITCHER_CHANGE` → green500 (HIT/WALK/STEAL 그룹) — 일상적 경기 진행.
- `MOUND_VISIT` → yellow500 (강조, 그러나 점수 그룹과는 톤 차별) — 잠재적 변경 신호.
- iOS Mobile [AppEventColors.swift](ios/mobile/BaseHaptic/Theme/AppEventColors.swift), Android [EventColors.kt](apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/theme/EventColors.kt), iOS Watch [WatchAppEventColors.swift](ios/watch/BaseHapticWatch/Theme/WatchAppEventColors.swift), Android Watch는 기존 그룹별 매핑에 합류.

### 워치 오버레이
- Android [MainActivity.kt](apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt) `eventUiFor`에 두 항목 추가 — `PITCHER_CHANGE`(SwapHoriz, blue400, "투수 교체"), `MOUND_VISIT`(Sports, yellow400, "마운드 방문").
- iOS [WatchAppEventColors.swift](ios/watch/BaseHapticWatch/Theme/WatchAppEventColors.swift) `overlayStyle(for:)`에 동일 항목 추가 — SF Symbol `arrow.left.arrow.right.circle.fill` / `figure.baseball`.

### 햅틱 매핑
- `PITCHER_CHANGE` → WALK 그룹 (가벼운 더블 클릭) — 경기 진행 정보로서 알림.
- `MOUND_VISIT` → 햅틱 없음 (오버레이만) — 잠재적 단계라 노이즈 회피.
- Android [DataLayerListenerService.kt](apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt) `triggerHapticFeedback`, iOS [WatchConnectivityManager.swift](ios/watch/BaseHapticWatch/WatchSync/WatchConnectivityManager.swift) `triggerHaptic`.

## Capabilities

### Added Capabilities
- `event-mound-visit-overlay`: `option.type=7` + 마운드 방문 텍스트를 `MOUND_VISIT` 이벤트로 분류해 모바일/워치에 풀스크린 오버레이로 노출 (햅틱 없음). **표시 정책 예외**: 다른 이벤트와 달리 자동 dismiss 타이머 없이 다음 이벤트 도달까지 계속 표시. 다음 LaunchedEffect/asyncAfter가 새 이벤트로 덮어쓰는 자연스러운 교체 흐름 활용.
- `event-pitcher-change-overlay`: `option.type=2` + `playerChange` 블록을 `PITCHER_CHANGE` 이벤트로 분류해 동일 오버레이 + WALK 그룹 햅틱.

### Modified Capabilities
- `event-haptic-mapping`: `PITCHER_CHANGE`를 WALK 그룹으로 정식 편입.
- `crawler-event-classification`: `_classify_event_type()` 우선순위에 `MOUND_VISIT` 분기 추가 (PITCHER_CHANGE → MOUND_VISIT → HALF_INNING_CHANGE → 기존 흐름).

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `type=7`이 비디오 판독에도 쓰여 마운드 방문 텍스트와 혼동 | 텍스트 키워드(`마운드 방문` / `코칭스태프 마운드` / `mound visit`)로 분기 — 비디오 판독 텍스트("비디오 판독")와 겹치지 않음 |
| 마운드 방문이 항상 교체로 이어지지 않아 false-positive 우려 | 햅틱 OFF + 짧은 오버레이만 — 노이즈 최소화. 후속 PITCHER_CHANGE 이벤트는 별도 오버레이로 명확화 |
| `playerChange.outPlayer/inPlayer` 메타데이터를 워치 표시에 사용하려면 폰→워치 페이로드 확장 필요 | 본 변경에서는 라벨/아이콘만 표시하고 부가 표시(예: "타케다 → 박시후")는 후속 작업으로 분리 |

## Status

- [x] 구현 완료 (백엔드 분류 + iOS Mobile/Watch + Android Mobile/Watch)
- [x] Android 모바일/워치 빌드 확인 (compileDebugKotlin 통과)
- [ ] iOS 빌드 확인
- [ ] 실기기 테스트: 마운드 방문/투수 교체 발생 경기에서 워치·폰 오버레이·햅틱 확인
