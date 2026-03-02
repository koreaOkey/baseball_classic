import os
import sys
from pathlib import Path

from fastapi.testclient import TestClient

API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))


DB_FILE = Path(__file__).parent / "test_backend.db"
if DB_FILE.exists():
    DB_FILE.unlink()

os.environ["BASEHAPTIC_DATABASE_URL"] = f"sqlite+pysqlite:///{DB_FILE.as_posix()}"
os.environ["BASEHAPTIC_CRAWLER_API_KEY"] = "test-key"
os.environ["BASEHAPTIC_CORS_ALLOW_ORIGINS"] = "*"

from app.main import app  # noqa: E402
from app.db import SessionLocal  # noqa: E402
from app.models import Game, GameBatterStat, GameLineupSlot, GameNote, GamePitcherStat  # noqa: E402


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
        "homeHits": 7,
        "awayHits": 6,
        "homeHomeRuns": 1,
        "awayHomeRuns": 0,
        "homeOutsTotal": 18,
        "awayOutsTotal": 20,
        "observedAt": "2026-02-17T09:00:00Z",
        "events": [
            {
                "sourceEventId": "relay-001",
                "type": "HIT",
                "description": "문보경, 좌전 안타",
                "occurredAt": "2026-02-17T08:59:20Z",
                "hapticPattern": "○●○●",
                "metadata": {"inning": 7, "half": "bottom"},
            },
            {
                "sourceEventId": "relay-002",
                "type": "SCORE",
                "description": "3루 주자 홈인, 1점 추가",
                "occurredAt": "2026-02-17T08:59:44Z",
                "hapticPattern": "●○●○●",
                "metadata": {"inning": 7, "half": "bottom"},
            },
        ],
        "lineupSlots": [
            {
                "teamSide": "home",
                "battingOrder": 1,
                "playerId": "H001",
                "playerName": "김현수",
                "positionCode": "LF",
                "positionName": "좌익수",
                "isStarter": True,
                "isActive": True,
            },
            {
                "teamSide": "away",
                "battingOrder": 1,
                "playerId": "A001",
                "playerName": "문보경",
                "positionCode": "1B",
                "positionName": "1루수",
                "isStarter": True,
                "isActive": True,
            },
        ],
        "batterStats": [
            {
                "teamSide": "home",
                "playerId": "H001",
                "playerName": "김현수",
                "battingOrder": 1,
                "primaryPosition": "좌익수",
                "isStarter": True,
                "plateAppearances": 4,
                "atBats": 3,
                "runs": 1,
                "hits": 2,
                "rbi": 1,
                "homeRuns": 1,
            },
            {
                "teamSide": "away",
                "playerId": "A001",
                "playerName": "문보경",
                "battingOrder": 1,
                "primaryPosition": "1루수",
                "isStarter": True,
                "plateAppearances": 4,
                "atBats": 4,
                "runs": 0,
                "hits": 1,
                "rbi": 0,
            },
        ],
        "pitcherStats": [
            {
                "teamSide": "home",
                "appearanceOrder": 1,
                "playerId": "P001",
                "playerName": "김택연",
                "isStarter": True,
                "outsRecorded": 15,
                "hitsAllowed": 4,
                "runsAllowed": 2,
                "earnedRuns": 2,
                "walksAllowed": 1,
                "strikeouts": 6,
                "pitchesThrown": 88,
            },
            {
                "teamSide": "away",
                "appearanceOrder": 1,
                "playerId": "P101",
                "playerName": "임찬규",
                "isStarter": True,
                "outsRecorded": 12,
                "hitsAllowed": 6,
                "runsAllowed": 3,
                "earnedRuns": 3,
                "walksAllowed": 2,
                "strikeouts": 5,
                "pitchesThrown": 79,
            },
        ],
        "notes": [
            {
                "teamSide": "home",
                "noteType": "GAME_HIGHLIGHT",
                "noteTitle": "결승타",
                "noteBody": "김현수 7회 결승타",
                "inning": "7회말",
                "sourceEventId": "relay-002",
            }
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

        with SessionLocal() as db:
            game = db.get(Game, "20250501SSSK02025")
            assert game is not None
            assert game.home_hits == 7
            assert game.away_hits == 6
            assert game.home_home_runs == 1
            assert game.home_outs_total == 18
            assert game.last_event_type == "SCORE"

            assert db.query(GameLineupSlot).filter(GameLineupSlot.game_id == "20250501SSSK02025").count() == 2
            assert db.query(GameBatterStat).filter(GameBatterStat.game_id == "20250501SSSK02025").count() == 2
            assert db.query(GamePitcherStat).filter(GamePitcherStat.game_id == "20250501SSSK02025").count() == 2
            assert db.query(GameNote).filter(GameNote.game_id == "20250501SSSK02025").count() == 1


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


def test_empty_details_payload_does_not_wipe_existing_rows() -> None:
    with TestClient(app) as client:
        first = client.post(
            "/internal/crawler/games/20250501SSSK02025/snapshot",
            headers={"X-API-Key": "test-key"},
            json=sample_snapshot(),
        )
        assert first.status_code == 200

        second_payload = sample_snapshot()
        second_payload["events"] = []
        second_payload["lineupSlots"] = []
        second_payload["batterStats"] = []
        second_payload["pitcherStats"] = []
        second_payload["notes"] = []

        second = client.post(
            "/internal/crawler/games/20250501SSSK02025/snapshot",
            headers={"X-API-Key": "test-key"},
            json=second_payload,
        )
        assert second.status_code == 200

        with SessionLocal() as db:
            assert db.query(GameLineupSlot).filter(GameLineupSlot.game_id == "20250501SSSK02025").count() == 2
            assert db.query(GameBatterStat).filter(GameBatterStat.game_id == "20250501SSSK02025").count() == 2
            assert db.query(GamePitcherStat).filter(GamePitcherStat.game_id == "20250501SSSK02025").count() == 2
            assert db.query(GameNote).filter(GameNote.game_id == "20250501SSSK02025").count() == 1
