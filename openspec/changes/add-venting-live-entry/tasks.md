# Tasks

## 1. Models & provider

- [x] 1.1 `VentingGameResult.IN_PROGRESS` + `VentingGameContext.inningLabel` 추가 (홈카드 오픈 조건 무영향)
- [x] 1.2 `VentingTarget.Custom(name)` 추가 — 표시 전용, 저장·전송 없음
- [x] 1.3 `LiveRegretProvider` 신설 — 중계 이벤트·박스스코어 기반 후보 산출 (병살·삼진 타자 / 실점 투수, 역할 레이블만)

## 2. Selection UI

- [x] 2.1 직접 입력 행(CustomTargetRow) — 입력 즉시 선택, 비우면 선택 해제
- [x] 2.2 라이브 진입 라벨 — 제목 "지금까지의 아쉬운 순간", 스코어 가운데 현재 이닝, 후보 0건 안내
- [x] 2.3 `backLabel` 파라미터 (홈카드 "홈" / 라이브 "경기")

## 3. Live entry overlay

- [x] 3.1 `VentingLiveEntryOverlay` — 왼쪽 하단 플로팅 💢 버튼 (마이팀 경기 + 플래그 ON, 경기 상태 무관 상시)
- [x] 3.2 패배 팝업 — FINISHED 전이 감지, 경기당 1회 pref 가드, "분풀이 하러 가기"/"다음에"
- [x] 3.3 `LiveGameScreen` 루트 Box 래핑 + 오버레이 통합
- [x] 3.4 풀스크린 플로우 하단 잘림 수정 — Dialog 창이 상태바만큼 밀려 하단 잘림(targetSdk 35 엣지-투-엣지 강제). Dialog 폐기 → `VentingFlowController` + MainActivity 루트 `VentingFlowHost` 오버레이로 전환(앱 본체와 동일 윈도우·인셋, `safeDrawingPadding` + 터치 전면 소비). 홈카드 진입도 동일 경로로 통합

## 4. Verification

- [x] 4.1 `:app:compileDebugKotlin` + `:app:assembleDebug` 통과
- [x] 4.1b Pixel 8 에뮬레이터(API 37, 엣지-투-엣지 강제): 홈카드 → 선택 화면 하단 버튼·룸 도구 트레이 잘림 없음 스크린샷 확인
- [x] 4.1c 인형 스프라이트 "검은 옷" 결함 복구 — normal·crack 투명 구멍 인페인트, 어두운 배경 합성으로 검증, Android·iOS·design-assets 3곳 배포 (burst·destroyed는 원본 유지)
- [ ] 4.2 실기기: LIVE 경기(또는 debug-watch-sync-test 더미)에서 플로팅 버튼 → 선택 → 룸 → 완파 플로우 확인
- [ ] 4.3 실기기: 직접 입력으로 이름 지목 → 룸 상단에 입력명 표시 확인
- [ ] 4.4 실기기: 패배 종료 시 팝업 1회 노출·재진입 시 미노출 확인
- [ ] 4.5 마이팀 미참가 경기·플래그 OFF에서 버튼 미노출 확인
