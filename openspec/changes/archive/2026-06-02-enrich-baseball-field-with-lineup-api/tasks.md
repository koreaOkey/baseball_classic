# Tasks

## 1. 백엔드 스키마 확장

- [x] 1.1 `backend/api/app/schemas.py` 에 `LineupSlotOut` 모델 추가 (`battingOrder`, `playerName`, `positionCode`, `positionName`, `isStarter`, `isActive`)
- [x] 1.2 `GameStateOut` 에 `homeLineup: list[LineupSlotOut]` 와 `awayLineup: list[LineupSlotOut]` 필드 추가 (default empty list)

## 2. 백엔드 서비스 보강

- [x] 2.1 `backend/api/app/services.py` `build_game_state` 에 `GameLineupSlot` 쿼리 추가 — `game_id`, `is_active = true` 필터
- [x] 2.2 슬롯을 `team_side` 로 분리해 `homeLineup` / `awayLineup` 채움
- [x] 2.3 `GameStateOut(...)` 호출에 두 필드 전달
- [x] 2.4 라인업 데이터 없는 경기에서도 응답이 깨지지 않게 빈 list 처리 확인

## 3. 백엔드 테스트

- [x] 3.1 `backend/api/tests/test_api.py` 에 라인업 시드 후 `/games/<id>/state` 응답에 `homeLineup`/`awayLineup` 포함 확인 테스트 추가
- [x] 3.2 라인업 미보유 게임에 대해서는 빈 list 반환 확인
- [x] 3.3 `is_active = false` 슬롯은 응답에서 제외 확인

## 4. iOS 모델·파싱

- [x] 4.1 `BackendGamesRepository.swift` 에 `LineupSlot` struct 추가 (백엔드와 1:1)
- [x] 4.2 `LiveGameState` 에 `homeLineup: [LineupSlot]`, `awayLineup: [LineupSlot]` 필드 추가
- [x] 4.3 `toLiveGameState()` JSON 파서 확장
- [x] 4.4 DEBUG 더미(`DebugDummyLiveGame.state`) 초기화 시 빈 lineup 으로 컴파일 호환

## 5. iOS 매핑 헬퍼

- [x] 5.1 `FieldLineup.from(state:)` static 헬퍼 추가 — 수비팀 판정 + 한글 positionName → 8 슬롯 매핑
- [x] 5.2 `isHomeDefending(inning:)` 헬퍼 — "초" → home, "말" → away, 미파싱 → nil
- [x] 5.3 매핑 미스(알 수 없는 positionName) 시 슬롯 nil → 라벨 자동 숨김 동작 확인

## 6. iOS UI 통합

- [x] 6.1 `LiveGameScreen.currentLineup` 계산을 production 흐름으로 교체 — `FieldLineup.from(state: gameState)` 우선, DEBUG 더미는 fallback
- [x] 6.2 production 빌드에서 실 라인업이 흐르는 시점에 자동 표시되는지 확인

## 7. Android 모델·파싱

- [x] 7.1 `BackendGamesRepository.kt` 에 `LineupSlot` data class 추가
- [x] 7.2 `LiveGameState` 에 `homeLineup`, `awayLineup` 필드 추가
- [x] 7.3 `toLiveGameState()` JSON 파서 확장 (`org.json` 또는 기존 패턴)
- [x] 7.4 DEBUG 더미(`DebugDummyLiveGame.state`) 빈 lineup 으로 컴파일 호환

## 8. Android 매핑 + UI 통합

- [x] 8.1 `FieldLineup.from(state)` companion 헬퍼 추가 — iOS 와 동일 매핑
- [x] 8.2 `isHomeDefending(inning)` 헬퍼
- [x] 8.3 `LiveGameScreen.currentLineup` production 흐름 도입
- [x] 8.4 매핑 미스 시 라벨 자동 숨김 확인

## 9. Cross-Platform Consistency

- [x] 9.1 한글 → 슬롯 매핑 테이블이 양 플랫폼 동일한지 cross-check
- [x] 9.2 이닝 초/말 판정 로직 동일성 cross-check
- [x] 9.3 production lineup 흐름 + DEBUG 더미 fallback 패턴 동일성

## 10. Validation

- [x] 10.1 백엔드 pytest 통과
- [x] 10.2 iOS BaseHaptic 앱 빌드 (`xcodebuild build` BUILD SUCCEEDED)
- [x] 10.3 Android 모바일 Kotlin 컴파일 BUILD SUCCESSFUL
- [ ] 10.4 실제 LIVE 경기 또는 백엔드 mock 으로 양 플랫폼에서 9명 라벨 자동 노출 확인
- [ ] 10.5 라인업 미보유 게임에서 P/B 2명만 보이고 8개 슬롯 깔끔히 숨김 확인
- [ ] 10.6 이닝 초/말 전환 시 수비팀이 바뀌어 lineup 도 교체되는지 확인
