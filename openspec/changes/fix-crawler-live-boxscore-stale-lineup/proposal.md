# fix-crawler-live-boxscore-stale-lineup

## Why

라이브(진행 중) 경기의 경기 상세 > 박스스코어에서 **출전 선수 기록이 전부 0**으로
표시되고, 경기가 끝나야 정상 값이 채워지는 버그. (Android에서 확인, iOS 동일 엔드포인트라 공통)

원인은 크롤러의 `_extract_latest_entry`가 lineup/entry(`homeLineup`/`awayLineup`/
`homeEntry`/`awayEntry`)를 **"가장 높은 이닝" relay에서** 뽑는 데 있다.

- `homeLineup`은 이닝별이 아니라 **게임 전체 누적**(타수/안타/타점…) 객체이고, Naver는
  어느 이닝을 조회해도 현재까지의 누적을 돌려준다.
- 하지만 이닝별 relay 캐시(C5)는 현재·직전 이닝만 매 폴 재조회하고, 아직 진행되지 않은
  높은 이닝 relay는 크롤러가 **게임 초반(또는 프로세스 재시작) 시점에 캐시한 stale
  lineup**(스탯이 대부분 0)을 계속 들고 있는다.
- `_extract_latest_entry`가 그 stale한 상위 이닝(예: 9회) lineup을 우선 선택해, 매 폴
  재조회되는 현재 이닝의 신선한 lineup을 가려버린다 → 라이브 내내 박스스코어 0.
- 경기 종료 시점엔 현재 이닝이 9회까지 올라가 상위 이닝이 다시 조회되므로, **완료 경기만
  정상**으로 보였다.

코드베이스에는 이미 이 staleness를 위한 `_relays_latest_first`(textRelays가 있는
=실제 진행된 신선한 relay 우선) 헬퍼가 존재하는데, inningScore/error 추출에만 쓰이고
lineup 추출(`_extract_latest_entry`)은 이를 사용하지 않고 있었다.

## What Changes

- `crawler/backend_sender.py`의 `_extract_latest_entry`가 단순 "가장 높은 이닝" 대신
  `_relays_latest_first`(진행된 relay 우선) 순서로 lineup/entry를 선택하도록 변경
- 회귀 테스트 추가: 상위 이닝(미진행)의 stale 0-스탯 lineup이 신선한 현재 이닝의 실제
  누적 스탯을 가리지 않는지 검증

## Impact

- **코드**: `crawler/backend_sender.py` 1개 함수(선택 순서만 변경)
- **테스트**: `crawler/test_backend_sender.py` 회귀 테스트 1건 추가
- **위험도**: 낮음 — 선택 소스만 신선한 relay로 바꿀 뿐, pregame(진행 relay 없음)
  동작은 기존과 동일하게 preview/상위 이닝 fallback 유지
- **범위**: 백엔드/앱 코드 변경 없음. 크롤러 재배포만으로 라이브·완료 모두 정상화

## Non-Goals

- 백엔드 박스스코어 엔드포인트/스키마 변경 없음
- 미출전 엔트리 선수 노출 정책 변경 없음 (기존대로 벤치 선수도 노출)
- 앱(Android/iOS) 클라이언트 변경 없음
