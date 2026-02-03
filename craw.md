# 크롤링 작업 기록 (Python 기반 실시간 경기 데이터 크롤러)

## 대상
- 네이버 스포츠 야구 중계 (모바일)
  - 예시: https://m.sports.naver.com/game/20250501SSSK02025/relay

## 사용 API
- 경기 메타: `https://api-gw.sports.naver.com/schedule/games/{gameId}`
- 텍스트 중계: `https://api-gw.sports.naver.com/schedule/games/{gameId}/relay?inning={1~9}`

## 수집 항목
- 1회 ~ 9회 각 타석(타자 등장)별:
  - 이닝/초·말
  - 공격/수비 팀
  - 타자
  - 투수
  - 볼/스트라이크 개수(해당 타석 내 누적)
  - 타석 결과(타구 처리 텍스트)
- 대타 투입 시점
- 투수 교체 시점 및 팀

## 구현 파일
- `crawler.py`: 크롤러 본체
- `requirements.txt`: 의존성

## 실행 방법
1) 의존성 설치
```
pip install -r requirements.txt
```

2) 단발 실행 (현재까지의 중계 데이터 엑셀 저장)
```
python crawler.py --game-id 20250501SSSK02025
```

3) 라이브 경기 대응 (주기적 갱신)
```
python crawler.py --game-id 20250501SSSK02025 --watch --interval 30
```

## 테니스 라이브 서버 (10초 갱신)
```
python live_tennis_server.py --game-id eXzIlhIXM5IFA4n --interval 10 --port 8000 --output tennis_live.xlsx
```
- 브라우저에서 `http://localhost:8000` 접속
- 10초마다 자동 새로고침됨
- `--output` 지정 시 엑셀을 주기적으로 덮어씀

## 출력 엑셀 구조
- `at_bats` 시트: 타석별 요약 (볼/스트라이크 포함)
- `pinch_hitters` 시트: 대타 교체 기록
- `pitcher_changes` 시트: 투수 교체 기록

## 참고
- 중계 텍스트 옵션의 `type` 값으로 이벤트 유형을 구분함.
  - `1`: 투구 이벤트(볼/스트라이크 등)
  - `2`: 선수 교체
  - `8`: 타자 소개
  - `13`: 타석 결과
- 라이브 경기에서는 `--watch` 모드로 주기적 갱신 가능.
- 이닝별 호출을 합쳐 1회~9회 데이터를 모두 저장함.