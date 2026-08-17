# Tasks

## 1. 대상 선택 화면 — 워치 버튼

- [x] 1.1 iOS `VentingTargetSelectionScreen` — `showWatchOption`/`onSelectTargetOnWatch` 파라미터 + "⌚ 워치로 분풀이 시작하기" 버튼(연동 시)
- [x] 1.2 Android `VentingTargetSelectionScreen` — 동일 파라미터 + 버튼(`Icons.Default.Watch`, 아웃라인 스타일)

## 2. 조율자 — 연동 감지 · 트리거 · 핸드오프

- [x] 2.1 iOS `VentingFlowCoordinator` — `isWatchAvailable`(WCSession isPaired && isWatchAppInstalled), `sendVentingTrigger` 발송, `watchHandoff` 상태 + `VentingWatchHandoffScreen`
- [x] 2.2 Android `VentingFlowCoordinator` — `WatchCompanionStatusRepository`(캐시+조회)로 연동 감지, `WearGameSyncManager.sendVentingTrigger` 발송, `WatchHandoff` 상태
- [x] 2.3 Android `VentingWatchHandoffScreen.kt` 신규 안내 화면 (iOS 미러)
- [x] 2.4 대상→페이로드 매핑: `roleLabel`/`eventDescription` 재사용 (선수=역할, custom=입력값 그대로)
- [x] 2.5 지표: `watch_room_enter` 이벤트 리포트

## 3. Verification

- [x] 3.1 Android `:app:compileDebugKotlin` 클린 통과
- [ ] 3.2 iOS 빌드(mobile 스킴) 통과 — 실기기/시뮬레이터
- [ ] 3.3 실기기(iPhone+Apple Watch): 대상 선택 → "워치로 분풀이 시작" → 워치 룸 오픈 + 폰 안내 화면
- [ ] 3.4 실기기(Android+Galaxy Watch): 동일 플로우 (앱 꺼져 있을 때 자동 기동 포함)
- [ ] 3.5 연동 해제(워치 미설치) 시 버튼 미노출 확인
- [ ] 3.6 custom 입력값이 워치 룸 대상 라벨로 그대로 표시되는지 확인
