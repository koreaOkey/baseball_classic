## Why

분풀이 모드는 iOS·Android·워치(watchOS·Wear OS) 4개 클라이언트가 모두 구현됐지만, 각 앱이 **목업·클라이언트 휴리스틱으로 따로** "아쉬운 순간" 후보를 만들고 있고 백엔드는 코드가 0줄이다(모든 client change가 "백엔드 무수정"). 기획 인터뷰(2026-07-20 동결)에서 정한 ① 백엔드 규칙 기반 TOP5 ② 감독 데이터 ③ 지표 수집이 전부 Phase 2 Non-Goal로 남아 있어, 이 change로 백엔드를 붙여 분풀이 모드를 프로덕션에서 실제 활성화한다. 별도 staging 환경이 없어 **다크 배포 플래그**가 안전망이다.

## What Changes

- **regret-top5 산정**: 경기 `FINISHED` 시점에만 out-of-band 워커가 산정한다. 규칙/WPA(`wpaByPlate` 이미 수집됨)로 후보를 사전 선별한 뒤 GPT-5.6 Luna(OpenAI 호환 LLM)가 TOP5 재정렬 + "상황" 사유문구를 생성하고, 결과는 캐시 테이블에 저장한다. 클라이언트는 **캐시만 읽는다**(LLM은 요청 경로에 없음).
  - 마이팀 **패배 경기만** 산정. 라이브(경기 중) 진입은 지금의 클라이언트 규칙(최신순) 유지 — 이 change 범위 밖.
  - 선택 화면 노출은 "5번 타자 이선우"처럼 **역할 레이블 + 실명**(실명은 박스스코어에서 기계적으로 부착, AI는 "상황"만 판단하고 사람을 평가하지 않는다). **분풀이 방 안에서는 실명 미노출**, 산정 결과 저장에도 실명 없음(역할 레이블만 영속).
  - **BLOCKING**: 실명 노출은 출시 전 KBOP 라이선스/법률 확인을 완료해야 활성화한다(tasks에 게이트로 명시).
- **감독 디렉터리**: 팀별 감독명 테이블 신설(시즌당 수동 관리, 경질 시 수동 갱신). TOP5 응답에 고정 6번째 선택지로 포함.
- **분풀이 지표 수집**: `room_enter`/`destroy_complete`/`retry_prompt_shown`/`retry_ad_start` 4종 + `entry_source`(home_card/live_button/loss_prompt) + **유저 응원팀(team) 필수 저장**. 향후 "어떤 응원팀이 분풀이를 가장 많이 실행했나" 팀 랭킹 집계(`venting_team_daily`/`venting_team_season`, 기존 체크인 집계 패턴 재사용)를 위한 기반. 특정 선수를 줄세우는 랭킹이 아니라 팬덤 인게이지먼트 지표.
- **안전 제약(전 범위 공통)**: 전부 additive — 신규 endpoint·`CREATE TABLE`만, 기존 hot 테이블(events/games) ALTER·락 금지. 다크 배포 플래그 `BASEHAPTIC_VENTING_BACKEND_ENABLED` / `BASEHAPTIC_VENTING_LLM_ENABLED`(둘 다 기본 `false`)로 게이트. 클라이언트가 아직 DEBUG 게이트라 배포 직후엔 트래픽 0으로 대기. Firebase 등 분석 SDK 도입은 영구 non-goal.

## Capabilities

### New Capabilities
- `venting-regret-selection`: 경기 종료 후 규칙/WPA 후보 선별 + LLM(GPT-5.6 Luna) TOP5 산정·사유문구 생성, 결과 캐시, 실명 표시 정책(선택 화면만·기계적 부착·방/저장 미노출), out-of-band 워커·타임아웃·동시성 제한·폴백, 다크 배포 플래그.
- `venting-manager-directory`: 팀별 감독명 수동 관리 테이블 + 조회, TOP5 응답 6번째 고정 항목 제공.
- `venting-analytics`: 분풀이 지표 4종 수집 endpoint(entry_source·team 포함) + 팀별 집계 롤업(일/시즌) 기반.

### Modified Capabilities
<!-- 없음: venting-mode 클라이언트 스펙은 아직 changes/에만 존재(specs/ 미archive)하고, 이 change는 신규 백엔드 표면만 추가한다. game-state/ingest 등 기존 스펙의 요구사항은 변경하지 않는다(additive only). -->

## Impact

- **백엔드 신규**: `backend/api/app/` — regret 산정 워커(out-of-band), 감독/지표 endpoint, `config.py`에 venting 설정군 추가(`BASEHAPTIC_VENTING_*`). 기존 크롤러 인제스트·게임 상태·푸시·cheer-events 경로 무수정.
- **DB 신규**: `db/migrations/` — `venting_regret_cache`, `team_manager`, `venting_event`, `venting_team_daily`, `venting_team_season`(전부 CREATE TABLE, 기존 테이블 ALTER 없음).
- **신규 의존성/설정**: OpenAI 호환 LLM 클라이언트, 환경변수 `BASEHAPTIC_VENTING_LLM_API_KEY`/`_MODEL`/`_BASE_URL`/`_TIMEOUT_SEC`/`_MAX_CONCURRENCY` + 플래그 2종.
- **클라이언트**: 이 change 범위 아님. 후속 change에서 홈카드(패배 후) provider를 서버 TOP5로 교체 + 지표 전송 연동. 라이브 중 진입은 클라이언트 규칙 유지.
- **운영**: LLM을 요청/DB 경로 밖으로 격리(2026-07-28 QueuePool 고갈 장애 패턴 회피). 롤백 = 플래그 OFF, 잔존 신규 테이블은 무해.
