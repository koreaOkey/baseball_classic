## Why

경기 상세 화면에 야구 앱 표준인 이닝별 라인스코어가 없고, 크롤러가 매 경기 수집해 DB에 쌓는 타자/투수 기록(game_batter_stats·game_pitcher_stats)이 UI에 전혀 노출되지 않았다. 네이버 relay 에 `textRelayData.inningScore` 와 `currentGameState.homeError/awayError` 가 이미 들어오는 것을 데이터 정찰로 확인했다 (2026-07-16 시안: design/live_detail_improvements_mockup.png).

## What Changes

### 크롤러
- relay 최신 데이터에서 이닝별 득점(`inningScore`)·실책을 추출해 스냅샷에 옵셔널 필드(`lineScore`/`homeErrors`/`awayErrors`)로 전송. 추출 불가 시 필드 생략(하위호환). 상태 시그니처에 포함해 라인스코어 변경만으로도 전송 트리거.

### 백엔드
- `games` 에 nullable `line_score_json`/`home_errors`/`away_errors` (기존 `_ensure_game_columns` 패턴으로 시작 시 자동 ADD COLUMN — 운영 무중단).
- `GameStateOut` 옵셔널 확장: `lineScore`, `homeHits`, `awayHits`, `homeErrors`, `awayErrors` (default None — 구클라이언트 무영향). 스냅샷이 필드를 생략해도 저장값 유지.
- 신규 `GET /games/{game_id}/boxscore`: 타자(타순 정렬)·투수(등판 순 정렬) 기록, LIVE 30초 / 그 외 600초 캐시, 404/빈 배열 처리.

### iOS·Android (동일 UX)
- 스코어 헤더 아래 **라인스코어 카드**: 1~N회(연장 가로 스크롤) + R·H·E, LIVE 시 진행 이닝 오렌지 강조, `lineScore` 부재 시 카드 숨김.
- **중계 | 박스스코어 탭**: 중계 탭은 기존 화면 그대로, 박스스코어 탭은 홈/어웨이 타자 기록표 + 투수 기록표(아웃카운트 → "6⅓" 표기). 탭 진입 시 조회, LIVE 중 30초 갱신, 실패 시 기존 데이터 유지.

## Capabilities

### Modified Capabilities
- `backend-api`: 경기 상태는 이닝별 라인스코어·안타·실책을 옵셔널로 노출하고, 박스스코어 조회 엔드포인트를 제공해야 한다.
- `crawler`: relay 의 이닝별 득점·실책을 스냅샷에 포함해야 한다(가용 시).
- `mobile-ios` / `mobile-android`: 경기 상세는 라인스코어 카드와 박스스코어 탭을 제공해야 한다.

## Impact

- 테스트: 백엔드 96 + 크롤러 61 = 157 passed. Android compileDebugKotlin·testDebugUnitTest 통과, iOS xcodebuild BUILD SUCCEEDED.
- 배포: staging `0e5c4eb4` 푸시 → Railway 자동 배포. DB 마이그레이션 파일 불필요(자동 컬럼 추가).
- 클라이언트 노출은 다음 앱 릴리즈부터. 구버전 앱·구백엔드 조합 모두 안전(옵셔널 필드/카드 숨김).
- 검증 잔여: 오늘 18:30 KST 라이브 경기에서 라인스코어 실데이터 확인.
