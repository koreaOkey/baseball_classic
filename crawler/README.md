# Crawler (Python)

이 폴더에는 야구/테니스 크롤러와 로컬 라이브 테스트 서버가 들어 있습니다.

## 파일 구성
- `crawler.py`: 야구 중계 크롤러 (`--base-url`로 목 API 지원)
- `backend_sender.py`: relay 데이터를 backend ingest payload로 변환/전송
- `crawler_tennis.py`: 테니스 `scoreDetail` 크롤러
- `live_tennis_server.py`: 테니스 라이브 모니터 서버
- `build_baseball_sample_data.py`: 종료된 야구 경기 데이터를 `data/mock_baseball/<game_id>`에 샘플로 저장
- `mock_baseball_relay_server.py`: 종료된 야구 경기를 라이브처럼 재생하는 목 API 서버
- `live_baseball_server.py`: `crawler.py`를 주기 실행하고 웹 화면으로 결과를 보여주는 서버

## 설치
```bash
pip install -r requirements.txt
```

## 현재 검증 기준
- 기준 경기: `20250902WOSK02025`
- 목 API 포트: `8011`
- 라이브 모니터 포트: `8010`
- 라이브 갱신 주기: `10초`

## 1) 야구 샘플 데이터 생성
```bash
python crawler/build_baseball_sample_data.py --game-id 20250902WOSK02025 --output-dir data/mock_baseball
```

생성 파일:
- `data/mock_baseball/20250902WOSK02025/game.json`
- `data/mock_baseball/20250902WOSK02025/relay_inning_1.json` ... `relay_inning_9.json`
- `data/mock_baseball/20250902WOSK02025/manifest.json`

## 2) 야구 목 라이브 API 서버 실행
```bash
python crawler/mock_baseball_relay_server.py --game-id 20250902WOSK02025 --data-dir data/mock_baseball --port 8011 --step-interval 10 --step-size 25
```

목 API 엔드포인트:
- `http://localhost:8011/schedule/games/20250902WOSK02025`
- `http://localhost:8011/schedule/games/20250902WOSK02025/relay?inning=1`

## 3) 야구 라이브 크롤링 모니터 서버 실행
```bash
python crawler/live_baseball_server.py --game-id 20250902WOSK02025 --source-base-url http://localhost:8011 --interval 10 --port 8010 --output-excel data/baseball_live_output.xlsx --output-json data/baseball_live_output.json
```

실행 예시(파일 잠금 회피용 출력 파일명):
```bash
python crawler/live_baseball_server.py --game-id 20250902WOSK02025 --source-base-url http://localhost:8011 --interval 10 --port 8010 --output-excel data/baseball_live_output_run2.xlsx --output-json data/baseball_live_output_run2.json
```

backend ingest까지 같이 수행:
```bash
python crawler/live_baseball_server.py --game-id 20250902WOSK02025 --source-base-url http://localhost:8011 --interval 10 --port 8010 --backend-base-url http://localhost:8080 --backend-api-key dev-crawler-key
```

접속:
- `http://localhost:8010`

출력 파일은 `data/` 폴더에 저장됩니다.

## 야구 크롤러 단독 실행
```bash
python crawler/crawler.py --game-id 20250902WOSK02025 --base-url http://localhost:8011 --watch --interval 10 --output data/baseball_from_mock.xlsx
```

crawler 단독 실행 + backend ingest 전송:
```bash
python crawler/crawler.py --game-id 20250902WOSK02025 --base-url http://localhost:8011 --watch --interval 10 --output data/baseball_from_mock.xlsx --backend-base-url http://localhost:8080 --backend-api-key dev-crawler-key
```

## Backend Event Type Mapping (Current)

When `backend_sender.py` builds ingest payloads, event types are emitted as:

- `BALL`, `STRIKE`, `WALK`, `OUT`, `HIT`, `HOMERUN`, `SCORE`, `SAC_FLY_SCORE`, `TAG_UP_ADVANCE`, `STEAL`, `OTHER`

Key rules:

- `N구 타격` is not final result -> `OTHER`
- hit result text (`1루타/2루타/3루타/안타/내야안타/번트안타`) -> `HIT`
- failed steal (`도루실패` + out) -> `OUT`
- successful steal -> `STEAL`
- `볼넷`/`고의사구` -> `WALK`

## 트러블슈팅
- `Permission denied: data/baseball_live_output.xlsx` 에러가 나면:
  - 해당 파일을 열고 있는 Excel/뷰어를 닫고 다시 실행
  - 또는 `--output-excel`, `--output-json` 파일명을 다른 이름으로 지정
- `http://localhost:8010` 접속이 안 되면:
  - 먼저 `mock_baseball_relay_server.py`(`8011`)가 실행 중인지 확인
  - 그다음 `live_baseball_server.py`(`8010`)를 실행
