import os
from pathlib import Path

from fastapi.testclient import TestClient


DB_FILE = Path(__file__).parent / "test_backend.db"
if DB_FILE.exists():
    DB_FILE.unlink()

os.environ["BASEHAPTIC_DATABASE_URL"] = f"sqlite+pysqlite:///{DB_FILE.as_posix()}"
os.environ["BASEHAPTIC_CRAWLER_API_KEY"] = "test-key"
os.environ["BASEHAPTIC_CORS_ALLOW_ORIGINS"] = "*"

from app.main import app  # noqa: E402


def sample_snapshot() -> dict:
    return {
        "homeTeam": "두산",
        "awayTeam": "LG",
        "status": "LIVE",
        "inning": "7회말",
        "homeScore": 3,
        "awayScore": 2,
        "ball": 2,
        "strike": 1,
        "out": 1,
        "bases": {"first": True, "second": False, "third": True},
        "pitcher": "김택연",
        "batter": "문보경",
        "observedAt": "2026-02-17T09:00:00Z",
        "events": [
            {
                "sourceEventId": "relay-001",
                "type": "HIT",
                "description": "문보경, 좌전 안타",
                "occurredAt": "2026-02-17T08:59:20Z",
                "hapticPattern": "○●○●",
            },
            {
                "sourceEventId": "relay-002",
                "type": "SCORE",
                "description": "3루 주자 홈인, 1점 추가",
                "occurredAt": "2026-02-17T08:59:44Z",
                "hapticPattern": "●○●○●",
            },
        ],
    }


def test_ingest_and_query_flow() -> None:
    with TestClient(app) as client:
        health = client.get("/health")
        assert health.status_code == 200
        assert health.json()["status"] == "ok"

        ingest = client.post(
            "/internal/crawler/games/20250501SSSK02025/snapshot",
            headers={"X-API-Key": "test-key"},
            json=sample_snapshot(),
        )
        assert ingest.status_code == 200
        body = ingest.json()
        assert body["insertedEvents"] == 2
        assert body["duplicateEvents"] == 0

        games = client.get("/games")
        assert games.status_code == 200
        assert len(games.json()) == 1

        state = client.get("/games/20250501SSSK02025/state")
        assert state.status_code == 200
        state_body = state.json()
        assert state_body["homeScore"] == 3
        assert state_body["awayScore"] == 2
        assert state_body["lastEventType"] == "SCORE"

        events = client.get("/games/20250501SSSK02025/events")
        assert events.status_code == 200
        events_body = events.json()["items"]
        assert len(events_body) == 2
        assert events_body[0]["type"] == "HIT"
        assert events_body[1]["type"] == "SCORE"

        last_cursor = events_body[-1]["cursor"]
        no_more = client.get(f"/games/20250501SSSK02025/events?after={last_cursor}")
        assert no_more.status_code == 200
        assert no_more.json()["items"] == []


def test_ingest_idempotent_and_auth() -> None:
    with TestClient(app) as client:
        unauthorized = client.post(
            "/internal/crawler/games/20250501SSSK02025/snapshot",
            headers={"X-API-Key": "wrong-key"},
            json=sample_snapshot(),
        )
        assert unauthorized.status_code == 401

        first = client.post(
            "/internal/crawler/games/20250501SSSK02025/snapshot",
            headers={"X-API-Key": "test-key"},
            json=sample_snapshot(),
        )
        assert first.status_code == 200

        second = client.post(
            "/internal/crawler/games/20250501SSSK02025/snapshot",
            headers={"X-API-Key": "test-key"},
            json=sample_snapshot(),
        )
        assert second.status_code == 200
        assert second.json()["insertedEvents"] == 0
        assert second.json()["duplicateEvents"] == 2
