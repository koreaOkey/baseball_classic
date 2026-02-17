# Backend

크롤러가 수집한 경기 데이터를 받아 저장하고, 모바일/워치 앱이 소비할 API와 실시간 스트림을 제공하는 영역입니다.

## 이번에 구현된 내용
- FastAPI 기반 API 서버 (`backend/api`)
- SQLite 기반 로컬 저장소 (개발용)
- crawler ingest API (서비스 키 인증)
- 게임/상태/이벤트 조회 API
- WebSocket 실시간 스트림 (`/ws/games/{gameId}`)
- 중복 이벤트 방지(idempotent) 처리
- crawler 데이터가 없어도 테스트 가능한 시뮬레이터/샘플 payload/pytest 추가

## 폴더 구조
```text
backend/
├─ README.md
└─ api/
   ├─ app/
   │  ├─ main.py            # FastAPI 엔트리
   │  ├─ config.py          # 환경변수 로딩
   │  ├─ db.py              # DB 세션/초기화
   │  ├─ models.py          # SQLAlchemy 모델
   │  ├─ schemas.py         # 요청/응답 스키마
   │  ├─ services.py        # 상태 정규화/업서트/조회 변환
   │  └─ event_bus.py       # WebSocket 브로드캐스트
   ├─ examples/
   │  └─ crawler_snapshot.json.example
   ├─ scripts/
   │  └─ simulate_crawler.py
   ├─ tests/
   │  └─ test_api.py
   ├─ .env.example
   ├─ requirements.txt
   └─ README.md
```

## 실행 방법 (로컬)
```bash
cd <repo-root>
python -m venv .venv
. .venv/Scripts/Activate.ps1
cd backend/api
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8080
```

- Swagger: `http://localhost:8080/docs`
- Health check: `GET http://localhost:8080/health`

## 환경변수
`.env` 예시:
```env
BASEHAPTIC_ENVIRONMENT=development
BASEHAPTIC_DATABASE_URL=sqlite+pysqlite:///./basehaptic.db
BASEHAPTIC_CRAWLER_API_KEY=dev-crawler-key
BASEHAPTIC_CORS_ALLOW_ORIGINS=*
```

## crawler → backend 데이터 계약
endpoint:
- `POST /internal/crawler/games/{gameId}/snapshot`
- Header: `X-API-Key: <BASEHAPTIC_CRAWLER_API_KEY>`

payload 핵심 필드:
- `homeTeam`, `awayTeam`, `status`, `inning`
- `homeScore`, `awayScore`, `ball`, `strike`, `out`
- `bases.first/second/third`
- `pitcher`, `batter`, `observedAt`
- `events[]` (각 이벤트의 `sourceEventId`, `type`, `description`, `occurredAt`)

샘플 파일:
- `backend/api/examples/crawler_snapshot.json.example`

## 제공 API
- `GET /health`
- `GET /games?status=LIVE&limit=20`
- `GET /games/{gameId}`
- `GET /games/{gameId}/state`
- `GET /games/{gameId}/events?after=<cursor>&limit=50`
- `POST /internal/crawler/games/{gameId}/snapshot`
- `WS /ws/games/{gameId}`

## 중복 이벤트 방지 방식
- DB unique key: `(game_id, source_event_id)`
- crawler가 같은 이벤트를 다시 보내도 `insertedEvents=0`, `duplicateEvents>0`로 처리
- 앱은 `events?after=<lastCursor>` 방식으로 안전하게 증분 조회 가능

## crawler 데이터가 아직 없을 때 테스트
### 1) 샘플 1회 적재
```bash
cd backend/api
curl -X POST "http://localhost:8080/internal/crawler/games/20250501SSSK02025/snapshot" ^
  -H "Content-Type: application/json" ^
  -H "X-API-Key: dev-crawler-key" ^
  --data-binary "@examples/crawler_snapshot.json.example"
```

### 2) 가짜 라이브 이벤트 스트림
```bash
cd backend/api
python scripts/simulate_crawler.py --base-url http://localhost:8080 --api-key dev-crawler-key --game-id DEMO-GAME-001
```

### 3) API 자동 테스트
```bash
cd backend/api
pytest -q
```

## 앱 연동 테스트 방법
현재 앱은 목업 데이터 기반이므로, 백엔드와 연결할 때 아래 순서로 검증하면 됩니다.

### A. 모바일 앱(조회)
1. 모바일 앱에서 경기 선택 시 `GET /games/{gameId}/state` 호출
2. 최초 진입 시 `GET /games/{gameId}/events?after=0` 호출
3. 이후 2~5초 폴링 또는 `WS /ws/games/{gameId}` 구독
4. 새 이벤트가 오면 `after` 커서를 최신값으로 업데이트

### B. 모바일 → 워치(Data Layer)
모바일이 백엔드에서 받은 상태/이벤트를 기존 Data Layer 코드로 전달:
- 상태 동기화: `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WearGameSyncManager.kt`
- 워치 수신/햅틱: `apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt`

권장 매핑:
- `GameStateOut` → `sendGameData(...)`
- `GameEventOut.type` → `eventType` 필드
- 주요 이벤트(`HOMERUN`, `SCORE`, `HIT`, `OUT`, `STRIKE`, `BALL`) 발생 시 `sendHapticEvent(...)`

## 통신 구조와 앱 노출 흐름
1. crawler가 경기 snapshot/events를 백엔드 ingest API로 전송
2. 백엔드는 game state + events 저장, 중복 이벤트 제거
3. 모바일 앱은 state/events API 또는 WebSocket으로 최신 데이터 수신
4. 모바일 앱이 워치로 Data Layer 전송
5. 워치 앱은 점수판/BSO/주자상황 UI 갱신 + 이벤트 타입별 햅틱 재생

즉, 앱 화면 노출은 `backend -> mobile -> watch` 순서로 이어지고, 워치는 모바일 전달 데이터를 기준으로 동작합니다.

