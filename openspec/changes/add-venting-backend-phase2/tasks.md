## 1. 설정·플래그·LLM 클라이언트 (다크, 무영향)

- [x] 1.1 `config.py`에 venting 설정군 추가: `venting_backend_enabled: bool = False`, `venting_llm_enabled: bool = False`, `venting_llm_api_key`(AliasChoices로 `OPENAI_API_KEY`도 허용), `venting_llm_model`, `venting_llm_base_url`, `venting_llm_timeout_sec: int = 20`, `venting_llm_max_concurrency: int = 2` — 전부 `BASEHAPTIC_` 프리픽스, 기본값이 기능 OFF
- [x] 1.2 OpenAI 호환 LLM 클라이언트 래퍼(`venting._call_llm`, 타임아웃·키 미로깅). 키/모델 미설정 또는 `venting_llm_enabled=false`면 호출 스킵하고 규칙 폴백
- [x] 1.3 배포 후 플래그 OFF 다크 검증 완료(커밋 995e8dd6, origin/staging). 프로덕션 health 200, 신규 엔드포인트 200 빈 응답(team-ranking `ranking:[]`, regret-top5 `items:[]`, events `disabled`) — 기존 경로 무영향 확인

## 2. DB 마이그레이션 (전부 CREATE TABLE, 기존 테이블 무변경)

- [x] 2.1 `venting_regret_cache` — game_id, team_code(패배팀), items(JSON: 역할 레이블·타순·event 참조·사유문구, **실명 없음**), manager_name, computed_at, source(llm/rule_fallback). game_id+team_code 유니크(재산정 방지)
- [x] 2.2 `team_manager` — team_code, manager_name, season, effective_from/to. (team_code, season) 인덱스 + 현재 감독 부분 유니크
- [x] 2.3 `venting_event` — event_type(4종), entry_source, team(응원팀), game_id, user_id(옵션), created_at. team+created_at 인덱스
- [x] 2.4 `venting_team_daily` / `venting_team_season` — team, date/season, count 롤업. 체크인 집계 테이블 패턴 재사용
- [x] 2.5 캐노니컬 SQL(`20260810_001`, RLS+트리거, 활성화 시 적용) 작성. 모델은 startup `create_all`이 기본 테이블 자동 생성(기존 테이블 무변경). 코드는 플래그 OFF에서 안전 no-op(테스트 확인)

## 3. regret-top5 산정 (out-of-band 워커)

- [x] 3.1 FINISHED 전이 훅: `services.upsert_game_from_snapshot`에 `_just_became_finished` transient 플래그 + `main.ingest`에서 플래그 ON일 때만 `background_tasks.add_task(compute_regret_for_game_background)`. 무승부·재산정 스킵
- [x] 3.2 규칙/WPA 후보 선별기(`venting._select_candidates`): `wpaByPlate` + 이벤트 타입 가중치, 공격 실패(병살/삼중살/삼진·고레버리지 OUT) / 수비 실점(SCORE/SAC_FLY_SCORE/HOMERUN), 역할 레이블(타순·선발/구원)·이벤트 참조 산출(실명 미포함), 역할 중복 최고 심각도 1건·최대 15
- [x] 3.3 LLM 산정(`venting._call_llm`): 후보 집합 프롬프트 → TOP5 재정렬 + "상황" 사유(사람 평가 금지·실명 금지·후보 밖 index 무시=창작 차단). off/실패 시 규칙 상위 5개 폴백
- [x] 3.4 커넥션 규율: Phase1 조회 세션 닫음 → Phase2 LLM 호출(세션 비점유) → Phase3 저장 세션. 동시 산정 `Semaphore(max_concurrency)` 제한
- [x] 3.5 결과를 `venting_regret_cache`에 upsert(감독명 부착, 실명 미저장, source 기록)

## 4. 조회·감독·지표 endpoint (캐시/DB만, LLM 없음)

- [x] 4.1 `GET /games/{game_id}/venting/regret-top5` — 캐시만 읽어 반환(패배팀, items+감독 6번째). 캐시 없음/플래그 OFF 시 빈 응답
- [x] 4.2 감독 조회(`venting._current_manager`) — `team_manager` effective_to IS NULL 최신, 부재 시 null(응답 정상)
- [x] 4.3 `POST /venting/events` — 4종 이벤트 수집(event_type·team 필수, entry_source·platform 정규화), best-effort(실패해도 비차단)
- [x] 4.4 팀 집계 롤업 — `record_venting_event`가 room_enter를 `venting_team_daily`/`_season` 원자적 upsert. `GET /venting/team-ranking`(집계 기준, 선수 미포함)

## 5. 검증·배포·게이트

- [x] 5.1 단위 테스트(`tests/test_venting.py`, 9건): 패배팀 산정/무승부·플래그OFF 스킵/재산정 방지/캐시 실명 미포함/감독 6번째/LLM 실패 폴백/지표+팀 랭킹/잘못된 타입 400. 전체 106 통과
- [x] 5.2 커넥션 비점유 — LLM 호출은 `with SessionLocal()` 블록 밖(Phase2)에서 실행되도록 구조화(7/28 풀 고갈 패턴 회피)
- [~] 5.3 코드 배포(플래그 OFF)·다크 무영향 관찰 완료. **잔여**: env(`VENTING_LLM_API_KEY/_MODEL`) 등록 확인 → 캐노니컬 RLS 마이그레이션 적용 → `VENTING_BACKEND_ENABLED=true`(감독·지표·조회) 관찰 → `VENTING_LLM_ENABLED=true` 순차 활성. 이상 시 플래그 OFF 롤백
- [ ] 5.4 **BLOCKING(출시 전 게이트)**: 실명 표시는 KBOP 라이선스/법률 확인 완료 후에만 활성. 미승인 시 역할 레이블만으로 운영(클라이언트 표시 정책과 연동)

## 6. 후속(범위 밖, 참조용)

- [ ] 6.1 클라이언트 연동: 홈카드(패배 후) provider를 서버 TOP5로 교체 + 지표 4종 전송(entry_source·team) — 별도 change
- [ ] 6.2 수비 실책 정밀 판정 위한 크롤러 보강 — 별도 change
- [ ] 6.3 감독 admin 갱신 API/UI(1차는 seed/SQL) — 별도 change
