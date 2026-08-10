## Context

분풀이 모드 클라이언트(iOS·Android·watchOS·Wear OS)는 완성됐으나, 후보 산정을 각자 목업/클라이언트 휴리스틱으로 하고 있고 백엔드는 전무하다. 기획 동결(2026-07-20)의 백엔드 3요소(TOP5 산정·감독 데이터·지표)를 붙인다.

제약:
- **운영 무중단이 최우선.** 별도 staging 없음 → `origin/staging` 푸시가 곧 프로덕션 배포(Railway가 staging tip 배포). 안전망은 코드 레벨 **다크 배포 플래그**.
- **DB 커넥션 풀이 민감.** 2026-07-28 QueuePool(size 1/overflow 0) 고갈 + 외부 SSL 장애로 32분 장애 이력. 외부 호출이 커넥션을 물면 재현된다.
- 설정은 pydantic `BaseSettings`(env_prefix `BASEHAPTIC_`), 마이그레이션은 `db/migrations/`.
- `wpaByPlate`(WPA)는 이미 수집·저장 중(`schemas.py`, `services.py`) → "결정적" 판단 근거로 즉시 사용 가능.
- `EventType` 15종에 개인 삼진/실책 전용 이벤트 없음(삼진=`OUT`, 실책=`OTHER`/description). 타격 찬스 실패·WPA 기반은 가능, 수비 실책은 description에 있을 때만.

## Goals / Non-Goals

**Goals:**
- 경기 종료 후 마이팀 패배 경기의 regret-top5를 서버가 권위 있게 산정·캐시하고, 클라가 캐시만 읽는다.
- 감독명을 팀별로 제공(TOP5 6번째 고정).
- 분풀이 지표를 응원팀 태그와 함께 수집해 향후 팀 랭킹의 기반을 만든다.
- 위 전부를 **기존 운영 경로에 0의 영향**으로 배포·롤백한다.

**Non-Goals:**
- 클라이언트 연동(홈카드 provider 교체·지표 전송) — 후속 change.
- 라이브(경기 중) 진입 후보 산정 — 클라이언트 규칙 유지.
- 수비 실책 정밀 판정을 위한 크롤러 보강 — 별도 change.
- 광고 게이트·무료 1회 서버 정산(로컬 pref 유지), Firebase 등 분석 SDK(영구 non-goal).
- 실명 랭킹/선수 줄세우기(영구 non-goal). 팀 랭킹만.

## Decisions

1. **LLM은 요청 경로 밖(out-of-band) 워커에서만 호출.** 경기 `FINISHED` 감지 → 백그라운드 작업 큐잉 → 산정 → `venting_regret_cache` 저장. 클라이언트 조회 endpoint는 **캐시 테이블만 읽는다.** 대안(요청 시 lazy LLM 호출)은 DB 커넥션을 외부 응답 대기 동안 물어 7/28 장애를 재현할 수 있어 기각. *DB 커넥션은 데이터 조회 시 잠깐 잡고 즉시 반납 → LLM 호출(커넥션 없음) → 결과 쓸 때 다시 짧게 잡는다.*

2. **하이브리드 산정(규칙/WPA 선별 → LLM 재정렬·문구).** 규칙 레이어가 마이팀 패배 경기의 후보 10~15개를 결정론적으로 선별(WPA 절댓값 상위 + 득점권 실패 상황). LLM은 **그 후보 안에서만** TOP5를 고르고 "상황" 사유문구를 쓴다 → 후보 밖 플레이 창작 불가(환각 차단). LLM 실패/비활성 시 규칙 레이어 상위 5개로 **폴백**(기능 degrade, 장애 전파 없음). 대안(LLM 단독 산정)은 환각·비용·검증 곤란으로 기각.

3. **실명 정책 = 선택 화면 표시 전용, 기계적 부착.** 표시 레이블은 "5번 타자 이선우"(역할 레이블 + 박스스코어에서 매칭한 실명). AI는 **상황만** 판단하고 사람을 평가하는 문장을 쓰지 않는다. **캐시에는 역할 레이블·타순·이벤트 참조만 저장하고 실명 문자열은 저장하지 않는다**(클라가 조회 시점에 박스스코어로 실명 결합). 분풀이 방·랭킹·저장 어디에도 실명이 영속하지 않는다. 대안(실명을 캐시에 저장)은 초상권 발자국을 남겨 기각.

4. **다크 배포 플래그 2단.** `BASEHAPTIC_VENTING_BACKEND_ENABLED`(마스터: endpoint·워커 전체) + `BASEHAPTIC_VENTING_LLM_ENABLED`(LLM만; off면 규칙/WPA 폴백). 둘 다 기본 `false`. staging이 없으니 이 플래그가 staging 역할 — OFF로 배포해 기존 트래픽 무영향 확인 후 단계적으로 ON. 롤백 = 플래그 OFF.

5. **전부 additive.** 신규 endpoint·신규 테이블만. 기존 hot 테이블(events/games) ALTER·인덱스 추가·락 없음. 신규 `CREATE TABLE`은 기존 테이블을 잠그지 않는다. 마이그레이션을 코드보다 먼저 적용(expand)하고, 코드는 테이블 부재/플래그 OFF에서 안전 no-op.

6. **감독은 수동 관리 테이블.** `team_manager`(team_code, manager_name, season, effective dates). 시즌당 수동 입력, 경질 시 수동 갱신. admin 갱신은 1차로 직접 SQL/seed(전용 admin API는 후속). TOP5 응답이 해당 팀 현재 감독을 6번째로 붙인다.

7. **지표는 응원팀 태그 필수.** `venting_event`에 `event_type`(4종)·`entry_source`·`team`(유저 응원팀)·`game_id`·`created_at`. 팀 랭킹은 원시 이벤트를 `venting_team_daily`/`venting_team_season`으로 롤업(기존 체크인 집계 `user_checkin_daily/season` 패턴 재사용) — 원시 테이블 무한 스캔 회피. 쓰기는 작고 인덱스됨, 실패해도 사용자 플로우 비차단(best-effort).

8. **LLM 클라이언트는 OpenAI 호환.** 모델 ID는 env(`BASEHAPTIC_VENTING_LLM_MODEL`)로 분리 — `gpt-5.6-luna` 문자열이 바뀌어도 코드 무수정. `BASE_URL` env로 커스텀 게이트웨이 허용. 타임아웃(`_TIMEOUT_SEC`, 기본 20)·동시 산정 제한(`_MAX_CONCURRENCY`, 기본 2)으로 버스트(하루 다수 경기 동시 종료) 시 폭주 방지.

## Risks / Trade-offs

- [LLM 호출이 커넥션 풀을 물어 장애 재현] → out-of-band 워커 + LLM 호출 구간 DB 커넥션 비점유 + 동시성 제한. (Decision 1·8)
- [LLM 환각으로 없는 플레이·부적절 문구] → 규칙 후보 안에서만 선택, 사람 평가 금지 프롬프트, 실패 시 규칙 폴백. (Decision 2)
- [실명 초상권/퍼블리시티권] → 표시 전용·기계적 부착·미저장 + 출시 전 KBOP/법률 확인 게이트(tasks BLOCKING). (Decision 3, proposal)
- [staging 부재로 프로덕션 직접 배포] → 다크 플래그로 무트래픽 대기 → 단계 ON, 이상 시 즉시 OFF. (Decision 4)
- [버스트 종료 시 다수 경기 동시 산정] → 동시성 제한 + 경기당 1회 캐시(재계산 방지). (Decision 8)
- [지표 쓰기 폭주가 운영 쓰기와 경합] → 이벤트 저지연·소형·best-effort, 실패 시 사용자 비차단.

## Migration Plan

1. 마이그레이션 적용(신규 5개 테이블, `CREATE TABLE`만) — 기존 테이블 무영향.
2. 백엔드 코드 배포(플래그 둘 다 OFF) → 프로덕션에서 다크 대기, 기존 트래픽 정상 확인.
3. env 등록: `BASEHAPTIC_VENTING_LLM_API_KEY`/`_MODEL`/(`_BASE_URL`)/(`_TIMEOUT_SEC`)/(`_MAX_CONCURRENCY`).
4. `BASEHAPTIC_VENTING_BACKEND_ENABLED=true` → 감독·지표·캐시 조회 endpoint만 관찰(LLM 아직 off, 규칙 폴백).
5. 이상 없으면 `BASEHAPTIC_VENTING_LLM_ENABLED=true` → LLM 산정 가동.
6. 롤백: 문제 플래그만 `false`. 신규 테이블은 남아도 무해.

## Open Questions

- KBOP 라이선스/법률 확인 결과에 따라 실명 표시 최종 승인 여부(미승인 시 역할 레이블만으로 출시). — 출시 전 해소.
- regret 워커 트리거를 기존 경기 종료 처리(3아웃 auto-advance/FINISHED 전이) 지점에 태울지, 별도 폴링으로 뺄지 — 구현 시 기존 종료 판정 코드 위치 확인 후 결정(전자 선호).
- 감독 admin 갱신 UI/API 필요 시점(1차는 seed/SQL). — 후속 change.
