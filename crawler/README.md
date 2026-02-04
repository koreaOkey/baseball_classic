# Crawler (Python)

네이버 스포츠(모바일) 중계 데이터를 주기적으로 수집/정제하는 크롤러 모듈입니다.

## 구성
- `crawler.py`: (야구) 릴레이 API 수집 → 엑셀 저장 (현재는 디버깅/검증용)
- `crawler_tennis.py`: (테니스) `scoreDetail` 기반 포인트/게임/세트 파싱 → 엑셀 저장 (검증용)
- `live_tennis_server.py`: (테니스) 10초 폴링 + 로컬 웹서버로 상태 확인 (검증용)
- `requirements.txt`: 파이썬 의존성

## 실행 (로컬)
의존성 설치:

```bash
pip install -r requirements.txt
```

야구(경기ID 예시):

```bash
python crawler.py --game-id 20250501SSSK02025 --output out.xlsx
```

테니스(경기ID 예시):

```bash
python crawler_tennis.py --game-id eXzIlhIXM5IFA4n --output tennis.xlsx
```

테니스 라이브 서버(10초 갱신):

```bash
python live_tennis_server.py --game-id eXzIlhIXM5IFA4n --interval 10 --port 8000 --output tennis_live.xlsx
```

브라우저 접속: `http://localhost:8000`

## 다음 단계(예정)
- 엑셀 출력 대신 **DB upsert**로 적재
- “새 이벤트만” 저장하도록 **idempotent 키** 설계 (gameId + seq/ids)
- 크롤러용 인증(서비스 키) 분리 및 레이트리밋/재시도 정책 추가

