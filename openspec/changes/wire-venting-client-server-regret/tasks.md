## 1. Android (DEBUG 게이트 뒤, 릴리즈 무영향)

- [x] 1.1 `RegretCandidate` 확장(kind·teamSide·battingOrder·appearanceOrder·playerName, 선택-전용)
- [x] 1.2 `BackendGamesRepository.fetchVentingRegretTop5` + `VentingRegretTop5/Item` 파싱(items snake_case, top-level camelCase)
- [x] 1.3 `ServerRegretProvider.buildContext` — 서버 items + 로컬 state 병합, 박스스코어 실명 조인, items 비면 null(폴백)
- [x] 1.4 선택 화면 TargetRow 실명 표기("{roleLabel} {playerName}"), 룸·완파는 roleLabel만
- [x] 1.5 `VentingEventReporter` POST + 지표 4종 호출(room_enter·destroy_complete·retry_prompt_shown·retry_ad_start), entry_source 배선(live/home_card/loss_push)
- [x] 1.6 `compileDebugKotlin` 통과

## 2. iOS (동등 구현)

- [x] 2.1 `RegretCandidate`(VentingModels.swift) 확장 + `BackendVentingProvider: RegretCandidateProviding`(fetchVentingContext = regret-top5) 신설, 화면 코드 무수정(프로토콜 시맨)
- [x] 2.2 스코어/결과/이닝은 `LiveGameState`와 병합, 박스스코어 실명 조인(선택 화면만)
- [x] 2.3 `VentingEventsReporter`(POST, best-effort detached Task) + 지표 4종 호출, entrySource 배선
- [x] 2.4 시뮬 빌드 검증(BaseHaptic Debug, xcodegen로 신규 2파일 pbxproj 편입 후)

## 2b. 크로스플랫폼 정합 (백엔드)

- [x] 2b.1 **팀 표기 불일치 수정**: iOS는 `myTeamId`에 kboTeamId("HH"), Android는 enum명("HANWHA")을 보냄 → 백엔드 `record_venting_event`에 `_canonical_team` 정규화 추가(코드/한글/enum명 → 표준 enum명)해 팀 랭킹이 쪼개지지 않게 함. 테스트 추가(HH+HANWHA→HANWHA 합산). iOS 표시 로직 무수정.

## 3. 검증·게이트

- [x] 3.1 프로덕션 캐시 item에 kind/team_side/타순·등판순서 존재 확인(실명 조인 실동작 근거)
- [ ] 3.2 DEBUG 빌드 실기기 확인(선택 화면 실명·룸 익명·지표 수신·팀 랭킹 반영)
- [x] 3.3 **법률 확인 완료(2026-08-14): 실명 표시 승인됨.** 남은 것은 클라 DEBUG 게이트 해제(=venting 릴리즈)뿐 — 실명 로직 자체는 구현·승인 완료
