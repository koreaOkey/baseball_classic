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
from app.models import Game, GameBatterStat, GameEvent, GameLineupSlot, GameNote, GamePitcherStat, TeamRecord  # noqa: E402
from app.services import _event_out_count, normalize_event_type  # noqa: E402


def sample_snapshot() -> dict:
    return {
        "homeTeam": "Doosan",
        "awayTeam": "LG",
        "status": "LIVE",
        "inning": "7B",
        "homeScore": 3,
        "awayScore": 2,
        "ball": 2,
        "strike": 1,
        "out": 1,
        "bases": {"first": True, "second": False, "third": True},
        "pitcher": "Kim Starter",
        "batter": "Moon Batter",
        "startTime": "18:30",
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
                "description": "single to left",
                "occurredAt": "2026-02-17T08:59:20Z",
                "hapticPattern": "HIT-HIT",
                "metadata": {"inning": 7, "half": "bottom"},
            },
            {
                "sourceEventId": "relay-002",
                "type": "SCORE",
                "description": "runner scored from third",
                "occurredAt": "2026-02-17T08:59:44Z",
                "hapticPattern": "SCORE-SCORE",
                "metadata": {"inning": 7, "half": "bottom"},
            },
        ],
        "lineupSlots": [
            {
                "teamSide": "home",
                "battingOrder": 1,
                "playerId": "H001",
                "playerName": "Home One",
                "positionCode": "LF",
                "positionName": "Left Field",
                "isStarter": True,
                "isActive": True,
            },
            {
                "teamSide": "away",
                "battingOrder": 1,
                "playerId": "A001",
                "playerName": "Away One",
                "positionCode": "1B",
                "positionName": "First Base",
                "isStarter": True,
                "isActive": True,
            },
        ],
        "batterStats": [
            {
                "teamSide": "home",
                "playerId": "H001",
                "playerName": "Home One",
                "battingOrder": 1,
                "primaryPosition": "Left Field",
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
                "playerName": "Away One",
                "battingOrder": 1,
                "primaryPosition": "First Base",
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
                "playerName": "Kim Starter",
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
                "playerName": "Park Starter",
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
                "noteTitle": "highlight",
                "noteBody": "home team scored",
                "inning": "7B",
                "sourceEventId": "relay-002",
            }
        ],
    }


def sample_team_records_payload() -> dict:
    return {
        "upperCategoryId": "kbaseball",
        "categoryId": "kbo",
        "seasonCode": "2026",
        "observedAt": "2026-03-14T00:01:00Z",
        "records": [
            {
                "upperCategoryId": "kbaseball",
                "categoryId": "kbo",
                "seasonCode": "2026",
                "teamId": "LG",
                "teamName": "LG",
                "teamShortName": "LG",
                "ranking": 1,
                "orderNo": 1,
                "gameType": "PRESEASON",
                "wra": 1.0,
                "gameCount": 2,
                "winGameCount": 1,
                "drawnGameCount": 1,
                "loseGameCount": 0,
                "gameBehind": 0.5,
                "continuousGameResult": "1승",
                "lastFiveGames": "-----",
                "offenseHra": 0.30556,
                "defenseEra": 4.0,
                "raw": {"teamId": "LG", "ranking": 1},
            },
            {
                "upperCategoryId": "kbaseball",
                "categoryId": "kbo",
                "seasonCode": "2026",
                "teamId": "OB",
                "teamName": "두산",
                "teamShortName": "두산",
                "ranking": 2,
                "orderNo": 2,
                "gameType": "PRESEASON",
                "wra": 0.5,
                "gameCount": 2,
                "winGameCount": 1,
                "drawnGameCount": 0,
                "loseGameCount": 1,
                "gameBehind": 1.0,
                "continuousGameResult": "1패",
                "lastFiveGames": "WLL--",
                "offenseHra": 0.276,
                "defenseEra": 3.2,
                "raw": {"teamId": "OB", "ranking": 2},
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
        games_body = games.json()
        assert len(games_body) == 1
        assert games_body[0]["startTime"] == "18:30"

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
        assert events_body[0]["pitcher"] == sample_snapshot()["pitcher"]
        assert events_body[0]["batter"] == sample_snapshot()["batter"]
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
            assert game.start_time == "18:30"
            first_event = (
                db.query(GameEvent)
                .filter(GameEvent.game_id == "20250501SSSK02025")
                .order_by(GameEvent.cursor.asc())
                .first()
            )
            assert first_event is not None
            assert first_event.pitcher == sample_snapshot()["pitcher"]
            assert first_event.batter == sample_snapshot()["batter"]

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


def test_event_type_double_triple_play_mapping() -> None:
    double_play = normalize_event_type("DOUBLE_PLAY")
    triple_play = normalize_event_type("TRIPLE_PLAY")

    assert double_play.value == "DOUBLE_PLAY"
    assert triple_play.value == "TRIPLE_PLAY"
    assert _event_out_count(double_play, "") == 2
    assert _event_out_count(triple_play, "") == 3


def test_event_type_pitcher_change_mapping() -> None:
    pitcher_change = normalize_event_type("PITCHER_CHANGE")

    assert pitcher_change.value == "PITCHER_CHANGE"
    assert _event_out_count(pitcher_change, "") == 0


def test_event_type_half_inning_change_mapping() -> None:
    half_inning_change = normalize_event_type("HALF_INNING_CHANGE")
    offense_change = normalize_event_type("OFFENSE_CHANGE")

    assert half_inning_change.value == "HALF_INNING_CHANGE"
    assert offense_change.value == "HALF_INNING_CHANGE"
    assert _event_out_count(half_inning_change, "") == 0


def test_game_state_resets_ball_strike_when_three_outs() -> None:
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["ball"] = 2
        payload["strike"] = 2
        payload["out"] = 3

        ingest = client.post(
            "/internal/crawler/games/20250501SSSK02025/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        state = client.get("/games/20250501SSSK02025/state")
        assert state.status_code == 200
        body = state.json()
        assert body["out"] == 3
        assert body["ball"] == 0
        assert body["strike"] == 0


def test_event_pitcher_batter_prefers_metadata_values() -> None:
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_META"
        payload = sample_snapshot()
        payload["pitcher"] = "fallback pitcher"
        payload["batter"] = "fallback batter"
        payload["events"][0]["metadata"] = {
            "inning": 7,
            "half": "bottom",
            "pitcher": "event pitcher",
            "batter": "event batter",
        }

        ingest = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        events = client.get(f"/games/{game_id}/events")
        assert events.status_code == 200
        first = events.json()["items"][0]
        assert first["pitcher"] == "event pitcher"
        assert first["batter"] == "event batter"


def test_duplicate_events_backfill_missing_pitcher_batter() -> None:
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_BACKFILL"
        payload = sample_snapshot()
        payload["pitcher"] = None
        payload["batter"] = None
        for event in payload["events"]:
            event["metadata"] = {"inning": 7, "half": "bottom"}

        first = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert first.status_code == 200

        backfill_payload = sample_snapshot()
        backfill_payload["pitcher"] = "backfill pitcher"
        backfill_payload["batter"] = "backfill batter"

        second = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=backfill_payload,
        )
        assert second.status_code == 200
        assert second.json()["insertedEvents"] == 0
        assert second.json()["duplicateEvents"] == 2

        events = client.get(f"/games/{game_id}/events")
        assert events.status_code == 200
        first_event = events.json()["items"][0]
        assert first_event["pitcher"] == "backfill pitcher"
        assert first_event["batter"] == "backfill batter"


def test_duplicate_events_upgrade_other_to_half_inning_change() -> None:
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_TYPEUP"
        first_payload = sample_snapshot()
        first_payload["events"] = [
            {
                "sourceEventId": "relay-half-001",
                "type": "OTHER",
                "description": "6th inning top, Japan offense",
                "occurredAt": "2026-02-17T08:59:20Z",
                "metadata": {"inning": 6, "half": "top"},
            }
        ]

        first = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=first_payload,
        )
        assert first.status_code == 200
        assert first.json()["insertedEvents"] == 1

        second_payload = sample_snapshot()
        second_payload["events"] = [
            {
                "sourceEventId": "relay-half-001",
                "type": "HALF_INNING_CHANGE",
                "description": "6th inning top, Japan offense",
                "occurredAt": "2026-02-17T08:59:20Z",
                "metadata": {
                    "inning": 6,
                    "half": "top",
                    "offenseTeam": "Japan",
                    "defenseTeam": "Korea",
                },
            }
        ]

        second = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=second_payload,
        )
        assert second.status_code == 200
        assert second.json()["insertedEvents"] == 0
        assert second.json()["duplicateEvents"] == 1

        events = client.get(f"/games/{game_id}/events")
        assert events.status_code == 200
        item = events.json()["items"][0]
        assert item["type"] == "HALF_INNING_CHANGE"

        with SessionLocal() as db:
            stored = (
                db.query(GameEvent)
                .filter(
                    GameEvent.game_id == game_id,
                    GameEvent.source_event_id == "relay-half-001",
                )
                .first()
            )
            assert stored is not None
            assert stored.event_type == "HALF_INNING_CHANGE"
            assert stored.payload_json is not None
            assert stored.payload_json.get("offenseTeam") == "Japan"
            assert stored.payload_json.get("defenseTeam") == "Korea"


def test_list_games_filters_by_date() -> None:
    with TestClient(app) as client:
        payload_a = sample_snapshot()
        payload_a["startTime"] = "12:10"
        payload_b = sample_snapshot()
        payload_b["startTime"] = "19:40"

        first = client.post(
            "/internal/crawler/games/20260217TEST0001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload_a,
        )
        assert first.status_code == 200

        second = client.post(
            "/internal/crawler/games/20260218TEST0001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload_b,
        )
        assert second.status_code == 200

        games = client.get("/games?date=2026-02-17")
        assert games.status_code == 200
        ids = [item["id"] for item in games.json()]
        assert "20260217TEST0001" in ids
        assert "20260218TEST0001" not in ids


def test_list_games_filters_by_game_date_column() -> None:
    with TestClient(app) as client:
        payload_a = sample_snapshot()
        payload_a["gameDate"] = "2026-03-08"
        payload_b = sample_snapshot()
        payload_b["gameDate"] = "2026-03-09"

        first = client.post(
            "/internal/crawler/games/WBCGAMEA001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload_a,
        )
        assert first.status_code == 200

        second = client.post(
            "/internal/crawler/games/WBCGAMEB001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload_b,
        )
        assert second.status_code == 200

        games = client.get("/games?date=2026-03-08")
        assert games.status_code == 200
        ids = [item["id"] for item in games.json()]
        assert "WBCGAMEA001" in ids
        assert "WBCGAMEB001" not in ids


def test_ingest_team_record_upsert_flow() -> None:
    with TestClient(app) as client:
        first_payload = sample_team_records_payload()
        first = client.post(
            "/internal/crawler/team-records",
            headers={"X-API-Key": "test-key"},
            json=first_payload,
        )
        assert first.status_code == 200
        first_body = first.json()
        assert first_body["receivedRecords"] == 2
        assert first_body["upsertedRecords"] == 2

        second_payload = sample_team_records_payload()
        second_payload["records"][0]["ranking"] = 3
        second_payload["records"][0]["wra"] = 0.333
        second_payload["records"][0]["raw"] = {"teamId": "LG", "ranking": 3}
        second = client.post(
            "/internal/crawler/team-records",
            headers={"X-API-Key": "test-key"},
            json=second_payload,
        )
        assert second.status_code == 200
        second_body = second.json()
        assert second_body["receivedRecords"] == 2
        assert second_body["upsertedRecords"] == 2

        with SessionLocal() as db:
            all_rows = db.query(TeamRecord).filter(TeamRecord.category_id == "kbo", TeamRecord.season_code == "2026").all()
            assert len(all_rows) == 2
            lg = (
                db.query(TeamRecord)
                .filter(
                    TeamRecord.category_id == "kbo",
                    TeamRecord.season_code == "2026",
                    TeamRecord.team_id == "LG",
                )
                .first()
            )
            assert lg is not None
            assert lg.ranking == 3
            assert lg.wra == 0.333


def test_ingest_team_record_requires_valid_api_key() -> None:
    with TestClient(app) as client:
        denied = client.post(
            "/internal/crawler/team-records",
            headers={"X-API-Key": "wrong-key"},
            json=sample_team_records_payload(),
        )
        assert denied.status_code == 401


def test_get_team_record_by_team_id() -> None:
    with TestClient(app) as client:
        ingest = client.post(
            "/internal/crawler/team-records",
            headers={"X-API-Key": "test-key"},
            json=sample_team_records_payload(),
        )
        assert ingest.status_code == 200

        response = client.get("/team-records/LG?categoryId=kbo&seasonCode=2026")
        assert response.status_code == 200
        body = response.json()
        assert body["teamId"] == "LG"
        assert body["ranking"] == 1
        assert body["wra"] == 1.0


def test_get_team_record_returns_404_when_not_found() -> None:
    with TestClient(app) as client:
        response = client.get("/team-records/NOPE?categoryId=kbo&seasonCode=2026")
        assert response.status_code == 404
