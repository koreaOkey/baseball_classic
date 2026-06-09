# Design

## Backend and Crawling

현재 `crawler/live_wbc_dispatcher.py`는 `--schedule-import-days`로 오늘부터 N일간의 스케줄을 import할 수 있다. 이 기능을 유지하면서 종료일 기반 옵션을 추가한다.

- `--schedule-import-until YYYY-MM-DD`: daily import 범위를 `today`부터 지정 날짜까지로 만든다.
- `--schedule-refresh-until YYYY-MM-DD`: refresh import도 오늘 하루가 아니라 지정 날짜까지 반복 갱신할 수 있게 한다.
- `--schedule-refresh-start-date YYYY-MM-DD`: 해당 날짜 이후에만 장기 refresh를 활성화한다. 기본값은 비활성이다.
- `--schedule-import-days`는 기존 호환을 위해 유지하고, `--schedule-import-until`이 있으면 종료일 옵션이 우선한다.

정책 예시는 다음과 같다.

- 상시: `--schedule-import-until 2026-09-07`
- 8월 중순 이후: `--schedule-refresh-start-date 2026-08-15 --schedule-refresh-until 2026-09-30`

백엔드는 기존 `/games?date=YYYY-MM-DD`를 유지하고, 날짜 범위 조회를 추가한다.

- `/games?from=YYYY-MM-DD&to=YYYY-MM-DD&limit=500`
- `date`가 있으면 기존 단일 날짜 동작을 유지한다.
- `from/to`는 `game_date` 범위로 조회하며 날짜, 시작 시간, game id 순으로 정렬한다.
- 상태 회귀 방지는 기존 ingest 로직을 사용한다.

DB 마이그레이션은 필요하지 않다. 기존 game row의 `game_date`, 팀, 시작 시간, 상태가 달력 조회에 충분하다.

## Mobile Data

Android/iOS repository는 일정 달력용 범위 조회 함수를 추가한다.

- 입력: 선택 팀, 시작일, 종료일
- 출력: 날짜별 경기 schedule item
- 서버 응답 실패 시 같은 범위 캐시가 있으면 캐시를 반환한다.
- 캐시 키는 선택 팀과 조회 범위를 포함한다.

캐시 정책:

- 날짜 범위 결과는 JSON 원문 또는 schedule item 배열로 저장한다.
- 오늘 포함 최근 구간은 앱 실행 또는 팝업 오픈 시 새로고침한다.
- 미래 일정은 하루 단위 캐시로도 충분하지만, MVP에서는 팝업 오픈 시 fresh 요청 후 실패하면 캐시 fallback으로 처리한다.

## Calendar UI

기존 일정 리스트 sheet를 달력 sheet로 교체한다.

상단:

- 월 제목
- 이전/다음 월 버튼
- 응원팀 이름과 조회 범위 안내

본문:

- 7열 달력 grid
- 경기 있는 날짜에 dot 또는 작은 count 표시
- 응원팀 경기일은 팀 컬러 강조
- 오늘 날짜는 outline 또는 fill로 표시
- 선택 날짜는 별도 강조

하단:

- 선택 날짜 경기 목록
- 경기 row에는 시간, 상대팀, 홈/원정, 상태를 표시한다.
- row tap은 기존 `onSelectGame` 콜백을 재사용한다.

상태:

- 응원팀 미선택
- 로딩
- 오류 및 다시 시도
- 해당 월/날짜 경기 없음

## Platform Impact

- Android mobile: 홈 일정 bottom sheet를 달력 UI로 변경한다.
- iOS mobile: 홈 일정 sheet를 달력 UI로 변경한다.
- Android watch: 직접 달력 UI 영향 없음.
- iOS watch: 직접 달력 UI 영향 없음. 단 워치 독립 폴링의 백엔드 URL 폴백은 기존 수정과 호환된다.
