# Tasks

- [x] 1.1 `venting.py`에 `_DEFENSE_ERROR_WEIGHT` + `_fielder_order_label`(라인업 position_name→batting_order, cursor 활성 슬롯 우선, 미상 시 "수비수") 추가
- [x] 1.2 `_select_candidates`에 수비 실책 분기 추가 — payload `isError` && 수비팀==패배팀 → fielder 후보(실점 SCORE 우선 귀속), `kind="fielder"`/`event_type="ERROR"`/`batting_order` 해소
- [x] 1.3 `_fallback_reason` ERROR→"실책", LLM 시스템 프롬프트 허용 역할 레이블에 "유격수" 추가
- [x] 2.1 단위 테스트: 실책→fielder 귀속(role_label=포지션, batting_order 해소, 투수 중복 없음, 실명 미저장) + 포지션 미상 익명("수비수"/타순 없음)
- [x] 2.2 백엔드 전체 테스트 통과 (`pytest` 113 passed)
- [x] 3.1 클라이언트 무변경 검증 — iOS/Android `resolvePlayerName`이 kind!=pitcher를 batting_order로 조인, TargetRow가 `roleLabel(+playerName)` 렌더(코드 리뷰로 확인, 변경 불필요)
- [ ] 4.1 백엔드(baseball_classic) 재배포 후 실책 발생 경기에서 regret-top5에 fielder 항목 확인
- [ ] 4.2 실기기: 선택 화면에 "유격수 이OO" 실명 표기 + 룸·완파 익명("유격수") 확인
