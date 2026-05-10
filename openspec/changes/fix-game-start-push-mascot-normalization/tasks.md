# Tasks

## 코드 변경
- [x] `backend/api/app/main.py:611-637` `_TEAM_ALIAS_TO_MASCOT` 별칭 테이블 추가 (한글 모기업 10 + 마스코트 셀프 10)
- [x] `backend/api/app/main.py:639-654` `_resolve_mascot()` 헬퍼 신설 (영문 코드 → 한글 별칭 → None)
- [x] `backend/api/app/main.py:657-670` `_team_display_name()` / `_normalize_my_team_for_watch()` 를 `_resolve_mascot()` 위에 재구현
- [x] `backend/api/app/main.py:673-688` `_team_codes_for_match()` 가 마스코트로 정규화 후 영문 코드 역추적

## 검증
- [x] `python3 -c "import ast; ast.parse(open('app/main.py').read())"` 통과
- [ ] 다음 응원팀 경기 시작 시 푸시 제목 양쪽 모두 마스코트 노출 확인
- [ ] 한글 home/away (두산 vs 롯데 등) 경기에서 구독자 토큰 매칭 정상 동작 (로그 `[game-start-push] teams=...` 의 keys 확인)

## 후속
- [ ] 백엔드 next deploy 에 포함
- [ ] (선택) 크롤러 측에서 네이버 라벨을 영문 코드로 표준화하는 정규화 도입 — 지금은 백엔드가 양쪽 알아서 처리하지만, 데이터 소스 단계에서 정리하면 더 robust
