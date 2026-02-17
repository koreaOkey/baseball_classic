# Data 폴더

이 폴더에는 원본 API 샘플과 크롤러 결과 파일을 저장합니다.

## 야구 목 라이브 샘플
기준 경기:
- https://m.sports.naver.com/game/20250902WOSK02025/relay

생성 샘플 파일:
- `mock_baseball/20250902WOSK02025/game.json`
- `mock_baseball/20250902WOSK02025/relay_inning_1.json` ... `relay_inning_9.json`
- `mock_baseball/20250902WOSK02025/manifest.json`

## 라이브 크롤러 출력 파일
`crawler/live_baseball_server.py` 실행 시 아래 결과 파일이 저장됩니다.

기본 파일명:
- `baseball_live_output.xlsx`
- `baseball_live_output.json`

테스트에서 사용한 대체 파일명(잠금 회피):
- `baseball_live_output_run2.xlsx`
- `baseball_live_output_run2.json`

## 단건/테스트 출력 파일
- `baseball_from_mock_test.xlsx`: `crawler.py`를 목 API로 단건 실행한 결과
