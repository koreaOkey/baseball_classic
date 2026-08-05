# Add Venting Live Entry (경기 중 분풀이 진입)

## Why

분풀이 모드는 현재 "마이팀 패배 확정 + 당일" 홈카드로만 진입할 수 있다. 그러나 분풀이 감정은 경기가 끝나기 전, 실점·병살을 지켜보는 순간에 정점이다. 경기 상세 화면에서 경기 진행 중 언제든지 진입할 수 있게 하고, 종료 시 패배면 진입 팝업으로 이어준다. (2026-08-05 사용자 결정: 플로팅 버튼 상시 노출 — 스코어 조건 없음.)

## What Changes

- `LiveGameScreen`: 루트를 Box로 감싸고 `VentingLiveEntryOverlay` 오버레이 추가.
  - **왼쪽 하단 플로팅 💢 버튼** — 마이팀 참가 경기 + 피처 플래그 ON이면 경기전·라이브·종료 상태 구분 없이 상시 표시(2026-08-05 사용자 결정: LIVE 한정 → 상시로 확대). 스코어와 무관하게 언제든지 진입.
  - **패배 팝업** — 경기 종료(FINISHED) 감지 시 마이팀 패배면 "분풀이 모드로 진입하시겠습니까?" AlertDialog. 경기당 1회만(`venting_loss_prompted_game_ids` pref, "다음에"도 1회로 카운트).
- `LiveRegretProvider` 신설: 경기 상세가 이미 보유한 중계 이벤트·박스스코어에서 "지금까지 이슈가 있던 마이팀 선수" 후보를 클라이언트 규칙으로 산출 — **백엔드 무수정**.
  - 마이팀 공격: 병살(DOUBLE_PLAY/TRIPLE_PLAY)·삼진(STRIKEOUT, OUT+"삼진") 타자 → 박스스코어 타순으로 "N번 타자" 레이블.
  - 마이팀 수비: 실점(SCORE/SAC_FLY_SCORE/HOMERUN) 시점 투수 → "선발/구원 투수" 레이블.
  - 역할 레이블 중복은 최신 1건, 최대 5건. 실명·등번호 미표기 원칙은 홈카드 TOP5와 동일하게 유지(중계 문구의 "선수명 : " 접두 제거).
- `VentingTarget.Custom` 신설: **선수명 직접 입력** 대상. 입력값은 화면 표시 전용 — 저장·전송 없음(초상권 리스크 차단, 실명 지목은 이 경로로만).
- `VentingTargetSelectionScreen`: 감독 아래 "직접 입력" 행(BasicTextField) 추가, 라이브 진입 시 제목 "지금까지의 아쉬운 순간" + 스코어 가운데 라벨 "최종" 대신 현재 이닝, 후보 0건 안내 문구, `backLabel` 파라미터("홈"/"경기").
- `VentingGameContext.inningLabel`(라이브 이닝 표기)·`VentingGameResult.IN_PROGRESS` 추가 — 홈카드 오픈 조건(`isOpen`)은 LOSS만 통과하므로 기존 동작 무영향.
- **플로우 표시 구조 교체**: 기존 풀스크린 `Dialog`는 targetSdk 35 엣지-투-엣지 강제 환경에서 창이 상태바만큼 밀려 하단이 잘리는 문제 → `VentingFlowController`(전역 요청 상태) + MainActivity 루트 `VentingFlowHost` 오버레이로 대체. 홈카드·라이브 진입이 동일 경로를 사용하며, 앱 본체와 같은 윈도우라 인셋 동작이 보장된다.

## Capabilities

### venting-mode (modified)

- 경기 상세 화면에서 경기 상태(경기전·진행·종료)와 무관하게 플로팅 버튼으로 분풀이 플로우에 진입할 수 있다.
- 진입 시 선택지는 ① 지금까지 이슈가 있던 마이팀 선수(역할 레이블) ② 감독 ③ 직접 입력한 이름.
- 마이팀 패배로 종료되는 순간을 경기 상세에서 보고 있으면 진입 팝업이 경기당 1회 노출된다.
- 기존 홈카드(패배 확정 후) 진입은 변경 없음.

## Impact

- `apps/mobile/.../venting/VentingModels.kt` — IN_PROGRESS, inningLabel, VentingTarget.Custom.
- `apps/mobile/.../venting/LiveRegretProvider.kt` — 신규.
- `apps/mobile/.../venting/ui/VentingLiveEntry.kt` — 신규 (플로팅 버튼 + 팝업 + 플로우 다이얼로그).
- `apps/mobile/.../venting/ui/VentingTargetSelectionScreen.kt`, `VentingFlowCoordinator.kt` — 직접 입력·라이브 라벨·backLabel.
- `apps/mobile/.../ui/screens/LiveGameScreen.kt` — 오버레이 통합.
- 백엔드·iOS·워치 영향 없음. DEBUG + `venting_mode_enabled` 이중 게이트 그대로 — 릴리즈 빌드 무영향.

## Non-Goals (후속)

- iOS 동등성 (동일 설계로 별도 change).
- 분풀이 룸 오픈 중 라이브 이벤트 햅틱 억제(탭 햅틱과 충돌) — Phase 2.
- 경기당 무료 1회와 라이브 진입의 통합 카운트 정산 — 광고 게이트 연결 change에서 처리.
- `room_enter` 지표의 `entry_source`(home_card/live_button/loss_prompt) — 지표 수집 change에서 처리.
- 실점 직후 플로팅 버튼 강조(펄스) 연출.
- 인형 스프라이트 "검은 옷" 결함 — hermes designer 산출물이 흰 유니폼 몸판을 배경으로 오인해 alpha=0으로 뚫어놓음(흰 배경 미리보기에선 안 보이고 어두운 룸에서 노출). 픽셀 인페인트 복구를 시도했으나 실기기에서 실루엣 밖 흰 블록으로 드러나 **롤백** — 최종 해법은 스프라이트 원본 유지 + 룸·완파 UI의 인형 뒤 **크림 스포트라이트 글로우**(radial gradient, `VentingDollSpotlight`). 구멍이 디자이너 승인 프리뷰(흰 배경)와 동일하게 흰 유니폼으로 읽힌다. 4개 플랫폼(폰 2·워치 2) 공통 적용은 add-venting-ios-parity-shake-watch-lite에서.
