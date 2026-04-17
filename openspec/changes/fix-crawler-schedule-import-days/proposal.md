# fix: 크롤러 경기 일정 7일치 미리 크롤링

## 문제
- 크롤러의 `--schedule-import-days` 기본값이 1(오늘만)이라, 미래 경기 일정이 DB에 없음
- iOS 앱 "다가오는 경기" 카드가 최대 3경기 표시인데, 내일 이후 경기가 DB에 없어서 2경기만 표시됨
- 기존 데이터는 이전에 한번에 수동으로 넣어둔 것이었음

## 원인
- `crawler/start.sh`에 `--schedule-import-days` 옵션 미지정 → 기본값 1 적용
- 매일 자정 00:05 daily import 시 오늘 하루치만 가져옴
- iOS `fetchUpcomingMyTeamGames()`는 내일(offset=1)부터 30일 앞까지 검색하므로, DB에 미래 경기가 없으면 카드가 비어버림

## 해결
- `crawler/start.sh`에 `--schedule-import-days "${SCHEDULE_IMPORT_DAYS:-7}"` 추가
- 매일 자정 00:05에 7일치 일정을 upsert (중복은 game_id PK 기반 upsert로 안전 처리)
- 환경변수 `SCHEDULE_IMPORT_DAYS`로 오버라이드 가능

## 변경 파일
- `crawler/start.sh` — `--schedule-import-days` 옵션 추가

## 커밋
- `fa0e88a` fix(crawler): 경기 일정 7일치 미리 크롤링 (--schedule-import-days 7)
