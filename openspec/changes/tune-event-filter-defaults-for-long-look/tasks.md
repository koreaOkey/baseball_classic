# Tasks

## 1. iOS 폰 default 분기

- [x] 1.1 `EventFilterOption` struct 에 `defaultEnabled: Bool` 필드 추가, 8종 default 값 명시 (HR/SCORE/HIT=true, 그 외=false)
- [x] 1.2 `EventFilterOption.defaultEnabled(forKey:)` 정적 helper 추가
- [x] 1.3 `EventFilterGate.isAllowed` fallback 을 per-key default 로 교체
- [x] 1.4 `EventFilterToggleRow` `@AppStorage(wrappedValue:)` 를 `option.defaultEnabled` 로 교체
- [x] 1.5 `EventFilterOption.currentValues()` fallback 을 per-key default 로 교체

## 2. iOS 워치 default 분기

- [x] 2.1 `WatchConnectivityManager` 에 `eventFilterDefaults` static dict 추가 (폰과 1:1 매핑, 동기화 주석 포함)
- [x] 2.2 `isEventTypeAllowedByFilter` fallback 을 per-key default 로 교체

## 3. 차단 이벤트 시 long-look 노티 skip

- [x] 3.1 `handleGameData` 에서 `incomingEventType` 추출 + `incomingEventAllowed` 판정
- [x] 3.2 차단된 이벤트일 때 `latestEventType` 갱신 skip → 본문에 차단 이벤트 라벨 노출 방지
- [x] 3.3 차단된 이벤트일 때 `triggerHaptic` skip (기존 동작 유지)
- [x] 3.4 차단된 이벤트일 때 `postOngoingLiveScoreNotification` 호출 자체 skip → silent replace 가 다음 허용 이벤트의 wake 를 죽이는 부작용 차단
- [x] 3.5 이벤트 없는 state-only 푸시는 항상 post (점수 동기 유지)

## 4. 검증 (실기기)

- [ ] 4.1 신규 설치 폰: 설정 진입 시 도루/볼넷/아웃/병살/투수교체 5종 토글이 OFF 로 표시
- [ ] 4.2 신규 설치 폰: 설정 진입 시 홈런/득점/안타 3종 토글이 ON 으로 표시
- [ ] 4.3 잠금 상태 + 워치 OFF 에서 아웃·볼넷 이벤트 발생 → 폰 헤드업 알림 안 뜸 + 워치 long-look wake 안 일어남
- [ ] 4.4 잠금 상태 + 워치 OFF 에서 홈런/득점/안타 이벤트 발생 → 폰 헤드업 + 워치 long-look wake + 햅틱 발화
- [ ] 4.5 설정에서 아웃을 ON 으로 바꾸면 아웃 이벤트도 폰·워치 모두 발화
- [ ] 4.6 백엔드 silent push 흐름이 차단 이벤트에서도 게임 상태(점수/이닝/BSO) 캐시는 정상 갱신되는지 확인 (워치 앱 열어 즉시 최신 상태 보임)

## 5. Android 동등성 (별도 PR · 본 change 범위 외)

- [ ] 5.1 `EventFilterOption.kt` 에 `defaultEnabled` 필드 추가 + per-key default
- [ ] 5.2 `EventFilterGate.isAllowed` (Android 폰) fallback 변경
- [ ] 5.3 `DataLayerListenerService.isEventTypeAllowedByFilter` (Wear OS) fallback 변경
- [ ] 5.4 Wear OS ongoing 노티(`MainActivity.postOngoingActivity`) 에서 차단 이벤트 시 갱신 skip
