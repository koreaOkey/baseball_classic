import os
import sys
import asyncio
import requests
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from fastapi import BackgroundTasks
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.exc import IntegrityError, OperationalError

API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))


DB_FILE = Path(__file__).parent / "test_backend.db"
if DB_FILE.exists():
    DB_FILE.unlink()

os.environ["BASEHAPTIC_DATABASE_URL"] = f"sqlite+pysqlite:///{DB_FILE.as_posix()}"
os.environ["BASEHAPTIC_CRAWLER_API_KEY"] = "test-key"
os.environ["BASEHAPTIC_CORS_ALLOW_ORIGINS"] = "*"
os.environ["BASEHAPTIC_SUPABASE_JWT_SECRET"] = "test-jwt-secret-0123456789abcdef0123456789abcdef"

from app.main import app  # noqa: E402
from app import main as main_module  # noqa: E402
from app import db as db_module  # noqa: E402
from app.db import SessionLocal  # noqa: E402
from app.models import AppConfig, DeviceToken, Game, GameBatterStat, GameEvent, GameLineupSlot, GameNote, GamePitcherStat, LiveViewSession, TeamRecord, TeamSubscriptionToken  # noqa: E402
from app.services import _event_out_count, normalize_event_type, normalize_status  # noqa: E402
from app.weather import build_hourly_weather, build_weather_summary, clear_weather_cache, _fetch_vilage_forecast  # noqa: E402


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


def test_schema_init_backfills_legacy_game_summary_columns(tmp_path: Path) -> None:
    db_url = f"sqlite+pysqlite:///{(tmp_path / 'legacy.db').as_posix()}"
    engine = create_engine(db_url, future=True)

    with engine.begin() as conn:
        conn.execute(
            text(
                """
                CREATE TABLE games (
                    id VARCHAR(64) PRIMARY KEY,
                    home_team VARCHAR(64) NOT NULL,
                    away_team VARCHAR(64) NOT NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
                    inning VARCHAR(32) NOT NULL DEFAULT '-',
                    home_score INTEGER NOT NULL DEFAULT 0,
                    away_score INTEGER NOT NULL DEFAULT 0,
                    ball_count INTEGER NOT NULL DEFAULT 0,
                    strike_count INTEGER NOT NULL DEFAULT 0,
                    out_count INTEGER NOT NULL DEFAULT 0,
                    base_first BOOLEAN NOT NULL DEFAULT 0,
                    base_second BOOLEAN NOT NULL DEFAULT 0,
                    base_third BOOLEAN NOT NULL DEFAULT 0,
                    pitcher VARCHAR(128),
                    batter VARCHAR(128),
                    observed_at DATETIME,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL
                )
                """
            )
        )

    db_module._ensure_game_columns(engine)

    columns = {column["name"] for column in inspect(engine).get_columns("games")}
    assert {
        "game_date",
        "start_time",
        "live_started_at",
        "base_first_runner",
        "base_second_runner",
        "base_third_runner",
        "home_hits",
        "away_hits",
        "home_home_runs",
        "away_home_runs",
        "home_outs_total",
        "away_outs_total",
        "last_event_type",
        "last_event_desc",
        "last_event_at",
        "line_score_json",
        "home_errors",
        "away_errors",
    }.issubset(columns)


def test_app_config_returns_default_store_url_when_unconfigured() -> None:
    with TestClient(app) as client:
        response = client.get("/app-config?platform=ios&version=1.1.3")

    assert response.status_code == 200
    body = response.json()
    assert body["platform"] == "ios"
    assert body["forceUpdate"] is False
    assert body["storeUrl"] == "itms-apps://itunes.apple.com/app/id6761336752"
    assert body["notice"] == {"enabled": False, "title": "", "message": ""}


def test_app_config_returns_platform_specific_remote_update_message() -> None:
    with SessionLocal() as db:
        db.add(
            AppConfig(
                platform="android",
                min_supported_version="1.0.4",
                latest_version="1.0.5",
                force_update=True,
                update_title="업데이트 필수",
                update_message="운영비로 인해 광고가 추가되었습니다. 안정적인 운영에 사용하겠습니다.",
                store_url="market://details?id=com.basehaptic.mobile",
                notice_enabled=True,
                notice_title="운영 안내",
                notice_message="공지 내용",
            )
        )
        db.commit()

    with TestClient(app) as client:
        response = client.get("/app-config?platform=android&version=1.0.3")

    assert response.status_code == 200
    body = response.json()
    assert body["platform"] == "android"
    assert body["minSupportedVersion"] == "1.0.4"
    assert body["latestVersion"] == "1.0.5"
    assert body["forceUpdate"] is True
    assert body["updateTitle"] == "업데이트 필수"
    assert body["updateMessage"] == "운영비로 인해 광고가 추가되었습니다. 안정적인 운영에 사용하겠습니다."
    assert body["storeUrl"] == "market://details?id=com.basehaptic.mobile"
    assert body["notice"] == {
        "enabled": True,
        "title": "운영 안내",
        "message": "공지 내용",
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


def _make_lock_timeout_error() -> OperationalError:
    class _OrigError(Exception):
        def __init__(self, message: str):
            super().__init__(message)
            self.sqlstate = "57014"

    original = _OrigError(
        'canceling statement due to statement timeout CONTEXT: while locking tuple (2,19) in relation "games"'
    )
    return OperationalError("UPDATE games ...", {}, original)


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
        # 활성 투수("Kim Starter")의 pitchesThrown 이 game-state 에 노출된다
        assert state_body["pitcher"] == "Kim Starter"
        assert state_body["pitcherPitchCount"] == 88

        # 라인업이 응답에 포함되고 team_side 로 분리된다
        assert isinstance(state_body["homeLineup"], list)
        assert isinstance(state_body["awayLineup"], list)
        assert len(state_body["homeLineup"]) == 1
        assert state_body["homeLineup"][0]["playerName"] == "Home One"
        assert state_body["homeLineup"][0]["positionName"] == "Left Field"
        assert state_body["homeLineup"][0]["battingOrder"] == 1
        assert state_body["homeLineup"][0]["isActive"] is True
        assert len(state_body["awayLineup"]) == 1
        assert state_body["awayLineup"][0]["playerName"] == "Away One"

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


def test_duplicate_snapshot_skips_timestamp_only_update() -> None:
    game_id = "20250501SSSK02025_NOOP"
    with TestClient(app) as client:
        first = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=sample_snapshot(),
        )
        assert first.status_code == 200
        first_body = first.json()
        assert first_body["insertedEvents"] == 2

        duplicate_payload = sample_snapshot()
        duplicate_payload["observedAt"] = "2026-02-17T09:00:30Z"
        duplicate = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=duplicate_payload,
        )
        assert duplicate.status_code == 200
        duplicate_body = duplicate.json()
        assert duplicate_body["insertedEvents"] == 0
        assert duplicate_body["duplicateEvents"] == 2
        assert duplicate_body["updatedAt"].rstrip("Z") == first_body["updatedAt"].rstrip("Z")

        changed_payload = sample_snapshot()
        changed_payload["homeScore"] = 4
        changed_payload["observedAt"] = "2026-02-17T09:01:00Z"
        changed = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=changed_payload,
        )
        assert changed.status_code == 200
        assert changed.json()["updatedAt"].rstrip("Z") != first_body["updatedAt"].rstrip("Z")

        state = client.get(f"/games/{game_id}/state")
        assert state.status_code == 200
        assert state.json()["homeScore"] == 4


def test_health_stays_available_when_startup_db_init_fails() -> None:
    def fail_init_db() -> None:
        raise OperationalError("SELECT 1", {}, Exception("db unavailable"))

    with patch.object(main_module, "init_db", fail_init_db):
        with TestClient(app) as client:
            health = client.get("/health")

    assert health.status_code == 200
    assert health.json()["status"] == "ok"


def test_games_returns_503_during_db_backoff() -> None:
    with TestClient(app) as client:
        with patch.object(db_module, "is_sqlite", False):
            db_module.mark_db_unavailable()
            try:
                response = client.get("/games?date=1999-01-01&limit=1")
            finally:
                db_module.clear_db_unavailable()

    assert response.status_code == 503
    assert "database temporarily unavailable" in response.json()["detail"]


def test_ready_clears_db_backoff_after_successful_probe() -> None:
    with TestClient(app) as client:
        with patch.object(db_module, "is_sqlite", False):
            db_module.mark_db_unavailable()
            try:
                response = client.get("/ready")
                remaining = db_module.db_unavailable_remaining_sec()
            finally:
                db_module.clear_db_unavailable()

    assert response.json()["db"] == "connected"
    assert remaining == 0.0


def test_db_backoff_classifier_only_matches_database_failures() -> None:
    db_error = OperationalError("SELECT 1", {}, Exception("db unavailable"))
    duplicate_error = IntegrityError("INSERT", {}, Exception("duplicate token"))

    assert main_module._is_database_failure(db_error) is True
    assert main_module._is_database_failure(duplicate_error) is False
    assert main_module._is_database_failure(RuntimeError("non-db failure")) is False


def test_live_activity_token_duplicate_race_is_idempotent() -> None:
    class FakeResult:
        def __init__(self, value):
            self.value = value

        def scalar_one_or_none(self):
            return self.value

    class ExistingToken:
        my_team = "OLD"

    class FakeDb:
        def __init__(self) -> None:
            self.existing = ExistingToken()
            self.execute_calls = 0
            self.commit_calls = 0
            self.rollback_calls = 0
            self.added = []

        def execute(self, statement):
            self.execute_calls += 1
            if self.execute_calls == 1:
                return FakeResult(None)
            return FakeResult(self.existing)

        def add(self, item):
            self.added.append(item)

        def commit(self):
            self.commit_calls += 1
            if self.commit_calls == 1:
                raise IntegrityError("INSERT", {}, Exception("duplicate token"))

        def rollback(self):
            self.rollback_calls += 1

    fake_db = FakeDb()
    payload = main_module.LiveActivityTokenRequest(
        token="duplicate-token",
        game_id="20260628HHSK02026",
        my_team="HANWHA",
    )

    response = main_module.register_live_activity_token(payload, BackgroundTasks(), db=fake_db)

    assert response == {"status": "ok"}
    assert fake_db.rollback_calls == 1
    assert fake_db.commit_calls == 2
    assert fake_db.existing.my_team == "HANWHA"


def test_expired_redis_db_backoff_marker_is_cleared() -> None:
    class FakeRedisRelay:
        def __init__(self) -> None:
            self.store = {
                main_module.DB_UNAVAILABLE_CACHE_KEY: {
                    "until": main_module.time.time() - 10,
                },
            }

        async def get_cache(self, key):
            return self.store.get(key)

        async def delete_cache(self, key):
            self.store.pop(key, None)

    fake_redis = FakeRedisRelay()

    with patch.object(main_module, "redis_relay", fake_redis):
        remaining = asyncio.run(
            main_module._redis_db_unavailable_remaining_sec(hold_expired=True)
        )

    assert remaining == 0.0
    assert main_module.DB_UNAVAILABLE_CACHE_KEY not in fake_redis.store


def test_game_state_http_cache_uses_redis_payload() -> None:
    class FakeRedisRelay:
        def __init__(self) -> None:
            self.store = {}

        async def start(self, on_message):
            return None

        async def stop(self):
            return None

        async def get_cache(self, key):
            return self.store.get(key)

        async def set_cache(self, key, value, ttl_sec=300):
            self.store[key] = value

        async def delete_cache(self, key):
            self.store.pop(key, None)

        async def publish(self, game_id, message):
            return None

    fake_redis = FakeRedisRelay()
    game_id = "20250501SSSK02025_CACHE"
    with patch.object(main_module, "redis_relay", fake_redis):
        with TestClient(app) as client:
            ingest = client.post(
                f"/internal/crawler/games/{game_id}/snapshot",
                headers={"X-API-Key": "test-key"},
                json=sample_snapshot(),
            )
            assert ingest.status_code == 200

            first = client.get(f"/games/{game_id}/state")
            assert first.status_code == 200
            assert first.json()["homeScore"] == 3

            with SessionLocal() as db:
                game = db.get(Game, game_id)
                assert game is not None
                game.home_score = 99
                db.commit()

            second = client.get(f"/games/{game_id}/state")
            assert second.status_code == 200
            assert second.json()["homeScore"] == 3


def test_game_state_http_cache_serves_stale_payload_on_loader_error() -> None:
    class FakeRedisRelay:
        def __init__(self) -> None:
            self.store = {}

        async def start(self, on_message):
            return None

        async def stop(self):
            return None

        async def get_cache(self, key):
            return self.store.get(key)

        async def set_cache(self, key, value, ttl_sec=300):
            self.store[key] = value

        async def delete_cache(self, key):
            self.store.pop(key, None)

        async def publish(self, game_id, message):
            return None

    fake_redis = FakeRedisRelay()
    game_id = "20250501SSSK02025_STALE"
    cache_key = f"http:game_state:v1:{game_id}"
    main_module._http_cache_locks.clear()

    with patch.object(main_module, "redis_relay", fake_redis):
        with TestClient(app) as client:
            ingest = client.post(
                f"/internal/crawler/games/{game_id}/snapshot",
                headers={"X-API-Key": "test-key"},
                json=sample_snapshot(),
            )
            assert ingest.status_code == 200

            first = client.get(f"/games/{game_id}/state")
            assert first.status_code == 200
            assert first.json()["homeScore"] == 3
            assert main_module._stale_http_cache_key(cache_key) in fake_redis.store

            fake_redis.store.pop(cache_key, None)
            with patch.object(
                main_module,
                "_get_game_state_payload",
                side_effect=RuntimeError("db unavailable"),
            ):
                second = client.get(f"/games/{game_id}/state")

            assert second.status_code == 200
            assert second.json()["homeScore"] == 3


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


def test_status_mapping_includes_canceled_and_postponed() -> None:
    assert normalize_status("ENDED").value == "FINISHED"
    assert normalize_status("CANCELED").value == "CANCELED"
    assert normalize_status("cancelled").value == "CANCELED"
    assert normalize_status("rain_cancel").value == "CANCELED"
    assert normalize_status("POSTPONED").value == "POSTPONED"
    assert normalize_status("ppd").value == "POSTPONED"
    assert normalize_status("suspended").value == "POSTPONED"


def test_game_state_resets_ball_strike_when_three_outs() -> None:
    """파싱 불가 inning('7B') + out=3: BSO만 0으로 리셋, inning 텍스트 유지."""
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
        assert body["out"] == 0
        assert body["ball"] == 0
        assert body["strike"] == 0


def test_three_outs_advances_inning_in_regulation() -> None:
    """KBO 한국어 inning에서 out=3 들어오면 즉시 다음 회로 advance + BSO=0."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "3회초"
        payload["ball"] = 1
        payload["strike"] = 2
        payload["out"] = 3
        payload["homeScore"] = 1
        payload["awayScore"] = 1

        ingest = client.post(
            "/internal/crawler/games/20250501SSSK02025_ADV1/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        body = client.get("/games/20250501SSSK02025_ADV1/state").json()
        assert body["inning"] == "3회말"
        assert body["out"] == 0
        assert body["ball"] == 0
        assert body["strike"] == 0
        assert body["bases"] == {"first": False, "second": False, "third": False}


def test_three_outs_transition_advances_when_payload_resets_only_bso() -> None:
    """이전 out=2, payload out=0 + inning 동일 → transition으로 advance."""
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_TRANS"
        payload = sample_snapshot()
        payload["inning"] = "5회말"
        payload["out"] = 2
        payload["ball"] = 1
        payload["strike"] = 2

        client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        # 다음 폴링: 네이버가 BSO만 리셋, inning 텍스트는 그대로
        payload["out"] = 0
        payload["ball"] = 0
        payload["strike"] = 0
        payload["bases"] = {"first": False, "second": False, "third": False}
        client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get(f"/games/{game_id}/state").json()
        assert body["inning"] == "6회초"
        assert body["out"] == 0


def test_inning_regress_guard_keeps_advanced_inning() -> None:
    """백엔드가 advance한 후, 네이버가 옛 inning 보내도 후퇴 안 함 (BSO/score는 갱신)."""
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_GUARD"
        # 1차: out=3 → 백엔드 advance "3회초" → "3회말"
        payload = sample_snapshot()
        payload["inning"] = "3회초"
        payload["out"] = 3
        payload["homeScore"] = 0
        payload["awayScore"] = 0
        client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        # 2차: 네이버가 아직 "3회초" 텍스트로 옴 (BSO만 리셋된 새 타석)
        payload["inning"] = "3회초"
        payload["out"] = 0
        payload["ball"] = 1
        payload["strike"] = 0
        payload["homeScore"] = 1
        client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get(f"/games/{game_id}/state").json()
        assert body["inning"] == "3회말"  # 후퇴 방지
        assert body["ball"] == 1
        assert body["homeScore"] == 1


def test_ninth_top_three_outs_finishes_when_home_leads() -> None:
    """9회초 3아웃 + 홈팀 리드 → 9회말 생략, 경기 종료."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "9회초"
        payload["out"] = 3
        payload["homeScore"] = 5
        payload["awayScore"] = 2
        client.post(
            "/internal/crawler/games/20250501SSSK02025_9T/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_9T/state").json()
        assert body["status"] == "FINISHED"
        assert body["inning"] == "경기 종료"


def test_ninth_top_three_outs_advances_when_tied() -> None:
    """9회초 3아웃 + 동점 → 9회말 진행."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "9회초"
        payload["out"] = 3
        payload["homeScore"] = 2
        payload["awayScore"] = 2
        client.post(
            "/internal/crawler/games/20250501SSSK02025_9TT/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_9TT/state").json()
        assert body["status"] == "LIVE"
        assert body["inning"] == "9회말"


def test_ninth_bottom_three_outs_advances_to_extra_when_tied() -> None:
    """9회말 3아웃 + 동점 → 10회초 (연장)."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "9회말"
        payload["out"] = 3
        payload["homeScore"] = 4
        payload["awayScore"] = 4
        client.post(
            "/internal/crawler/games/20250501SSSK02025_9B/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_9B/state").json()
        assert body["status"] == "LIVE"
        assert body["inning"] == "10회초"


def test_ninth_bottom_three_outs_finishes_when_score_diff() -> None:
    """9회말 3아웃 + 점수차 → 경기 종료."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "9회말"
        payload["out"] = 3
        payload["homeScore"] = 3
        payload["awayScore"] = 5
        client.post(
            "/internal/crawler/games/20250501SSSK02025_9BF/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_9BF/state").json()
        assert body["status"] == "FINISHED"
        assert body["inning"] == "경기 종료"


def test_tenth_top_three_outs_finishes_when_home_leads() -> None:
    """10회초 3아웃 + 홈리드 → 종료 (연장 끝내기)."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "10회초"
        payload["out"] = 3
        payload["homeScore"] = 6
        payload["awayScore"] = 4
        client.post(
            "/internal/crawler/games/20250501SSSK02025_10T/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_10T/state").json()
        assert body["status"] == "FINISHED"
        assert body["inning"] == "경기 종료"


def test_tenth_bottom_three_outs_advances_when_tied() -> None:
    """10회말 3아웃 + 동점 → 11회초 (연장 계속)."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "10회말"
        payload["out"] = 3
        payload["homeScore"] = 5
        payload["awayScore"] = 5
        client.post(
            "/internal/crawler/games/20250501SSSK02025_10B/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_10B/state").json()
        assert body["status"] == "LIVE"
        assert body["inning"] == "11회초"


def test_tenth_bottom_three_outs_finishes_when_score_diff() -> None:
    """10회말 3아웃 + 점수차 → 종료."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "10회말"
        payload["out"] = 3
        payload["homeScore"] = 7
        payload["awayScore"] = 5
        client.post(
            "/internal/crawler/games/20250501SSSK02025_10BF/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_10BF/state").json()
        assert body["status"] == "FINISHED"
        assert body["inning"] == "경기 종료"


def test_eleventh_bottom_three_outs_draw_label() -> None:
    """11회말 3아웃 + 동점 → 경기 종료 (무승부) 라벨."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "11회말"
        payload["out"] = 3
        payload["homeScore"] = 3
        payload["awayScore"] = 3
        client.post(
            "/internal/crawler/games/20250501SSSK02025_DRAW/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_DRAW/state").json()
        assert body["status"] == "FINISHED"
        assert body["inning"] == "경기 종료 (무승부)"


def test_eleventh_bottom_three_outs_finishes_with_score_diff() -> None:
    """11회말 3아웃 + 점수차 → 경기 종료."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["inning"] = "11회말"
        payload["out"] = 3
        payload["homeScore"] = 5
        payload["awayScore"] = 3
        client.post(
            "/internal/crawler/games/20250501SSSK02025_11W/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )

        body = client.get("/games/20250501SSSK02025_11W/state").json()
        assert body["status"] == "FINISHED"
        assert body["inning"] == "경기 종료"


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


def test_game_state_includes_base_runner_names() -> None:
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_RUNNERS"
        payload = sample_snapshot()
        payload["bases"] = {"first": True, "second": False, "third": True}
        payload["baseRunners"] = {"first": "First Runner", "third": "Third Runner"}

        ingest = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        body = client.get(f"/games/{game_id}/state").json()
        assert body["bases"] == {"first": True, "second": False, "third": True}
        assert body["baseRunners"] == {
            "first": "First Runner",
            "second": None,
            "third": "Third Runner",
        }


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


def test_list_games_filters_by_date_range_and_sorts_schedule() -> None:
    with TestClient(app) as client:
        payload_a = sample_snapshot()
        payload_a["gameDate"] = "2026-06-12"
        payload_a["startTime"] = "18:30"
        payload_b = sample_snapshot()
        payload_b["gameDate"] = "2026-06-10"
        payload_b["startTime"] = "19:00"
        payload_c = sample_snapshot()
        payload_c["gameDate"] = "2026-06-10"
        payload_c["startTime"] = "14:00"
        payload_outside = sample_snapshot()
        payload_outside["gameDate"] = "2026-06-20"

        for game_id, payload in [
            ("RANGEGAME003", payload_a),
            ("RANGEGAME002", payload_b),
            ("RANGEGAME001", payload_c),
            ("RANGEGAME999", payload_outside),
        ]:
            response = client.post(
                f"/internal/crawler/games/{game_id}/snapshot",
                headers={"X-API-Key": "test-key"},
                json=payload,
            )
            assert response.status_code == 200

        games = client.get("/games?from=2026-06-10&to=2026-06-12&limit=10")
        assert games.status_code == 200
        ids = [item["id"] for item in games.json()]
        assert ids[:3] == ["RANGEGAME001", "RANGEGAME002", "RANGEGAME003"]
        assert "RANGEGAME999" not in ids


def test_list_games_rejects_invalid_date_range() -> None:
    with TestClient(app) as client:
        missing_to = client.get("/games?from=2026-06-10")
        assert missing_to.status_code == 400

        reversed_range = client.get("/games?from=2026-06-12&to=2026-06-10")
        assert reversed_range.status_code == 400


def test_list_games_filters_by_new_status_values() -> None:
    with TestClient(app) as client:
        canceled_payload = sample_snapshot()
        canceled_payload["status"] = "RAIN_CANCEL"
        postponed_payload = sample_snapshot()
        postponed_payload["status"] = "POSTPONED"

        canceled_ingest = client.post(
            "/internal/crawler/games/20260220STAT0001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=canceled_payload,
        )
        assert canceled_ingest.status_code == 200
        assert canceled_ingest.json()["status"] == "CANCELED"

        postponed_ingest = client.post(
            "/internal/crawler/games/20260220STAT0002/snapshot",
            headers={"X-API-Key": "test-key"},
            json=postponed_payload,
        )
        assert postponed_ingest.status_code == 200
        assert postponed_ingest.json()["status"] == "POSTPONED"

        canceled_games = client.get("/games?status=CANCELED")
        assert canceled_games.status_code == 200
        canceled_ids = [item["id"] for item in canceled_games.json()]
        assert "20260220STAT0001" in canceled_ids
        assert "20260220STAT0002" not in canceled_ids

        postponed_games = client.get("/games?status=POSTPONED")
        assert postponed_games.status_code == 200
        postponed_ids = [item["id"] for item in postponed_games.json()]
        assert "20260220STAT0002" in postponed_ids
        assert "20260220STAT0001" not in postponed_ids


def test_list_games_includes_optional_weather_summary() -> None:
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["gameDate"] = "2026-06-16"
        payload["status"] = "SCHEDULED"
        payload["inning"] = "18:30"
        payload["startTime"] = "18:30"

        ingest = client.post(
            "/internal/crawler/games/WEATHERGAME001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        weather = {
            "stadiumCode": "JAMSIL",
            "stadiumName": "잠실야구장",
            "stadiumShortName": "잠실",
            "forecastDate": "2026-06-16",
            "forecastTime": "1800",
            "forecastTimeLabel": "18시 기준",
            "condition": "흐림",
            "temperatureC": 23,
            "precipitationProbability": 30,
            "precipitationType": None,
            "windSpeedMps": 2.0,
            "isIndoor": False,
            "displayText": "경기 시작 예보 · 잠실 · 18시 기준 · 흐림 23° · 강수 30%",
        }
        with patch.object(main_module, "build_weather_summary", return_value=weather):
            games = client.get("/games?date=2026-06-16&limit=100")

        assert games.status_code == 200
        item = next(game for game in games.json() if game["id"] == "WEATHERGAME001")
        assert item["weather"]["displayText"] == "경기 시작 예보 · 잠실 · 18시 기준 · 흐림 23° · 강수 30%"


def test_list_games_uses_cached_weather_only() -> None:
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["gameDate"] = "2026-06-16"
        payload["status"] = "SCHEDULED"
        payload["inning"] = "18:30"
        payload["startTime"] = "18:30"

        ingest = client.post(
            "/internal/crawler/games/WEATHERCACHED001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        allow_network_values: list[bool | None] = []

        def fake_weather_summary(*args, **kwargs):
            allow_network_values.append(kwargs.get("allow_network"))
            return None

        with patch.object(main_module, "build_weather_summary", side_effect=fake_weather_summary):
            games = client.get("/games?date=2026-06-16&limit=100")

    assert games.status_code == 200
    assert False in allow_network_values


def test_weather_prewarm_warms_forecast_range_games_with_network() -> None:
    """프리워밍은 예보 지원 범위의 경기들에 대해 allow_network=True 로 요약을 계산한다."""
    with TestClient(app) as client:
        payload = sample_snapshot()
        today = datetime.now(main_module.KST).date()
        payload["gameDate"] = today.isoformat()
        payload["status"] = "SCHEDULED"
        payload["inning"] = "18:30"
        payload["startTime"] = "18:30"

        game_id = f"{today.strftime('%Y%m%d')}PREWARM01"
        ingest = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        calls: list[tuple[str, bool | None]] = []

        def fake_weather_summary(game, **kwargs):
            calls.append((game.id, kwargs.get("allow_network")))
            return {"stadiumCode": "JAMSIL"}

        with patch.object(main_module, "build_weather_summary", side_effect=fake_weather_summary):
            with patch.object(main_module.settings, "weather_service_key", "test-weather-key"):
                warmed = main_module._prewarm_weather_forecasts()

    assert warmed >= 1
    assert any(gid == game_id and allow is True for gid, allow in calls)


def test_weather_summary_dome_fallback_without_external_api_key() -> None:
    game = Game(
        id="20260616DOME001",
        game_date="2026-06-16",
        home_team="키움",
        away_team="LG",
        status="SCHEDULED",
        inning="18:30",
        start_time="18:30",
    )

    weather = build_weather_summary(game, service_key="", api_base_url="")

    assert weather is not None
    assert weather["isIndoor"] is True
    assert weather["displayText"] == "경기 시작 예보 · 고척돔 · 날씨 영향 적음"


def test_weather_summary_missing_key_returns_none_for_outdoor_game() -> None:
    game = Game(
        id="20260616OPEN001",
        game_date="2026-06-16",
        home_team="두산",
        away_team="LG",
        status="SCHEDULED",
        inning="18:30",
        start_time="18:30",
    )

    assert build_weather_summary(game, service_key="", api_base_url="") is None


def test_weather_forecast_failure_is_cached() -> None:
    clear_weather_cache()
    with patch("app.weather.requests.get", side_effect=requests.Timeout("weather timeout")) as get:
        first = _fetch_vilage_forecast(
            service_key="test-weather-key",
            api_base_url="https://weather.example.test/forecast",
            base_date="20260616",
            base_time="0200",
            nx=60,
            ny=127,
        )
        second = _fetch_vilage_forecast(
            service_key="test-weather-key",
            api_base_url="https://weather.example.test/forecast",
            base_date="20260616",
            base_time="0200",
            nx=60,
            ny=127,
        )

    clear_weather_cache()
    assert first == []
    assert second == []
    assert get.call_count == 1


def test_weather_forecast_decodes_encoded_service_key_before_request() -> None:
    class FakeResponse:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict:
            return {
                "response": {
                    "header": {"resultCode": "00"},
                    "body": {"items": {"item": []}},
                }
            }

    captured_params: dict[str, str] = {}

    def fake_get(*args, **kwargs):
        captured_params.update(kwargs["params"])
        return FakeResponse()

    clear_weather_cache()
    with patch("app.weather.requests.get", side_effect=fake_get):
        _fetch_vilage_forecast(
            service_key="abc%2Bdef%2Fghi%3D%3D",
            api_base_url="https://weather.example.test/forecast",
            base_date="20260616",
            base_time="0200",
            nx=61,
            ny=127,
        )

    clear_weather_cache()
    assert captured_params["serviceKey"] == "abc+def/ghi=="


def test_weather_summary_can_skip_network_on_cache_miss() -> None:
    game = Game(
        id="20260616OPEN002",
        game_date="2026-06-16",
        home_team="두산",
        away_team="LG",
        status="SCHEDULED",
        inning="18:30",
        start_time="18:30",
    )
    now = datetime(2026, 6, 16, 12, tzinfo=timezone(timedelta(hours=9)))

    clear_weather_cache()
    with patch("app.weather.requests.get", side_effect=AssertionError("network should not be used")):
        weather = build_weather_summary(
            game,
            service_key="test-weather-key",
            api_base_url="https://weather.example.test/forecast",
            now=now,
            allow_network=False,
        )

    clear_weather_cache()
    assert weather is None


def test_hourly_weather_unavailable_returns_empty_payload() -> None:
    game = Game(
        id="20260616OPEN001",
        game_date="2026-06-16",
        home_team="두산",
        away_team="LG",
        status="SCHEDULED",
        inning="18:30",
        start_time="18:30",
    )

    weather = build_hourly_weather(
        game,
        service_key="",
        api_base_url="",
        target_date=date(2026, 6, 16),
    )

    assert weather is not None
    assert weather["stadiumShortName"] == "잠실"
    assert weather["items"] == []


def test_game_weather_hourly_endpoint() -> None:
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["gameDate"] = "2026-06-16"
        payload["status"] = "SCHEDULED"
        payload["inning"] = "18:30"
        payload["startTime"] = "18:30"

        ingest = client.post(
            "/internal/crawler/games/WEATHERHOURLY001/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        hourly = {
            "gameId": "WEATHERHOURLY001",
            "stadiumCode": "JAMSIL",
            "stadiumName": "잠실야구장",
            "stadiumShortName": "잠실",
            "gameStartTime": "18:30",
            "items": [
                {
                    "forecastDate": "2026-06-16",
                    "forecastTime": "1800",
                    "timeLabel": "18시",
                    "condition": "흐림",
                    "temperatureC": 23,
                    "precipitationProbability": 30,
                    "precipitationType": None,
                    "windSpeedMps": 2.0,
                    "isGameStartForecast": True,
                }
            ],
        }
        with patch.object(main_module, "build_hourly_weather", return_value=hourly):
            response = client.get("/games/WEATHERHOURLY001/weather?date=2026-06-16")

        assert response.status_code == 200
        body = response.json()
        assert body["stadiumShortName"] == "잠실"
        assert body["items"][0]["isGameStartForecast"] is True


def test_ingest_snapshot_does_not_regress_status_to_scheduled() -> None:
    with TestClient(app) as client:
        game_live = "20260221STAT0001"
        game_finished = "20260221STAT0002"

        live_payload = sample_snapshot()
        live_payload["status"] = "LIVE"
        live_payload["inning"] = "7회말"
        live_payload["events"] = []
        live_first = client.post(
            f"/internal/crawler/games/{game_live}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=live_payload,
        )
        assert live_first.status_code == 200
        assert live_first.json()["status"] == "LIVE"

        stale_scheduled_payload = sample_snapshot()
        stale_scheduled_payload["status"] = "SCHEDULED"
        stale_scheduled_payload["inning"] = "13:00"
        stale_scheduled_payload["events"] = []
        live_second = client.post(
            f"/internal/crawler/games/{game_live}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=stale_scheduled_payload,
        )
        assert live_second.status_code == 200
        assert live_second.json()["status"] == "LIVE"

        finished_payload = sample_snapshot()
        finished_payload["status"] = "RESULT"
        finished_payload["inning"] = "9회말"
        finished_payload["events"] = []
        finished_first = client.post(
            f"/internal/crawler/games/{game_finished}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=finished_payload,
        )
        assert finished_first.status_code == 200
        assert finished_first.json()["status"] == "FINISHED"

        finished_second = client.post(
            f"/internal/crawler/games/{game_finished}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=stale_scheduled_payload,
        )
        assert finished_second.status_code == 200
        assert finished_second.json()["status"] == "FINISHED"

        live_game = client.get(f"/games/{game_live}")
        assert live_game.status_code == 200
        assert live_game.json()["status"] == "LIVE"

        finished_game = client.get(f"/games/{game_finished}")
        assert finished_game.status_code == 200
        assert finished_game.json()["status"] == "FINISHED"


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
        assert second_body["upsertedRecords"] == 1

        third = client.post(
            "/internal/crawler/team-records",
            headers={"X-API-Key": "test-key"},
            json=second_payload,
        )
        assert third.status_code == 200
        third_body = third.json()
        assert third_body["receivedRecords"] == 2
        assert third_body["upsertedRecords"] == 0

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


def test_team_record_websocket_receives_initial_and_changed_push() -> None:
    with TestClient(app) as client:
        ingest = client.post(
            "/internal/crawler/team-records",
            headers={"X-API-Key": "test-key"},
            json=sample_team_records_payload(),
        )
        assert ingest.status_code == 200

        with client.websocket_connect("/ws/team-records/LG?categoryId=kbo&seasonCode=2026") as ws:
            initial = ws.receive_json()
            assert initial["type"] == "team_record"
            assert initial["payload"]["teamId"] == "LG"
            assert initial["payload"]["ranking"] == 1

            changed_payload = sample_team_records_payload()
            changed_payload["records"][0]["ranking"] = 2
            changed_payload["records"][0]["wra"] = 0.667
            changed_payload["records"][0]["raw"] = {"teamId": "LG", "ranking": 2}
            changed = client.post(
                "/internal/crawler/team-records",
                headers={"X-API-Key": "test-key"},
                json=changed_payload,
            )
            assert changed.status_code == 200
            assert changed.json()["upsertedRecords"] == 1

            pushed = ws.receive_json()
            assert pushed["type"] == "team_record"
            assert pushed["payload"]["teamId"] == "LG"
            assert pushed["payload"]["ranking"] == 2
            assert pushed["payload"]["wra"] == 0.667


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


def test_get_team_record_standings_returns_ranked_list() -> None:
    with TestClient(app) as client:
        ingest = client.post(
            "/internal/crawler/team-records",
            headers={"X-API-Key": "test-key"},
            json=sample_team_records_payload(),
        )
        assert ingest.status_code == 200

        response = client.get("/team-records?categoryId=kbo&seasonCode=2026")
        assert response.status_code == 200
        body = response.json()
        assert [item["teamId"] for item in body] == ["LG", "OB"]
        assert body[0]["ranking"] == 1
        assert body[0]["gameCount"] == 2
        assert body[0]["winGameCount"] == 1
        assert body[0]["drawnGameCount"] == 1
        assert body[0]["loseGameCount"] == 0
        assert body[0]["continuousGameResult"] == "1승"


def test_get_team_record_standings_returns_empty_list_when_not_found() -> None:
    with TestClient(app) as client:
        response = client.get("/team-records?categoryId=kbo&seasonCode=2099")
        assert response.status_code == 200
        assert response.json() == []


def test_get_team_record_returns_404_when_not_found() -> None:
    with TestClient(app) as client:
        response = client.get("/team-records/NOPE?categoryId=kbo&seasonCode=2026")
        assert response.status_code == 404


def test_snapshot_ingest_retries_on_lock_timeout_and_succeeds() -> None:
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_RETRY"
        payload = sample_snapshot()
        original_upsert = main_module.upsert_game_from_snapshot
        calls = {"count": 0}

        def flaky_upsert(db, game_id: str, payload):
            calls["count"] += 1
            if calls["count"] == 1:
                raise _make_lock_timeout_error()
            return original_upsert(db, game_id=game_id, payload=payload)

        with (
            patch.object(main_module, "upsert_game_from_snapshot", side_effect=flaky_upsert),
            patch.object(main_module.time, "sleep", return_value=None),
        ):
            response = client.post(
                f"/internal/crawler/games/{game_id}/snapshot",
                headers={"X-API-Key": "test-key"},
                json=payload,
            )

        assert response.status_code == 200
        assert calls["count"] == 2


def test_snapshot_ingest_returns_503_when_lock_timeout_persists() -> None:
    with TestClient(app) as client:
        game_id = "20250501SSSK02025_BUSY"
        payload = sample_snapshot()

        def always_lock(*args, **kwargs):
            raise _make_lock_timeout_error()

        with (
            patch.object(main_module, "upsert_game_from_snapshot", side_effect=always_lock),
            patch.object(main_module.time, "sleep", return_value=None),
        ):
            response = client.post(
                f"/internal/crawler/games/{game_id}/snapshot",
                headers={"X-API-Key": "test-key"},
                json=payload,
            )

        assert response.status_code == 503
        assert response.json()["detail"] == "snapshot ingest busy; retry shortly"


def test_rollback_session_safely_success() -> None:
    class DummySession:
        def __init__(self) -> None:
            self.rolled_back = False
            self.closed = False

        def rollback(self) -> None:
            self.rolled_back = True

        def close(self) -> None:
            self.closed = True

    session = DummySession()
    ok = main_module._rollback_session_safely(session, game_id="G1", attempt=1)
    assert ok is True
    assert session.rolled_back is True
    assert session.closed is False


def test_rollback_session_safely_handles_rollback_error() -> None:
    class DummySession:
        def __init__(self) -> None:
            self.closed = False

        def rollback(self) -> None:
            raise RuntimeError("rollback failed")

        def close(self) -> None:
            self.closed = True

    session = DummySession()
    ok = main_module._rollback_session_safely(session, game_id="G2", attempt=2)
    assert ok is False
    assert session.closed is True


def test_game_state_excludes_inactive_lineup_slots() -> None:
    with TestClient(app) as client:
        snapshot = sample_snapshot()
        snapshot["lineupSlots"] = [
            {
                "teamSide": "home",
                "battingOrder": 1,
                "playerId": "H001",
                "playerName": "Home Active",
                "positionCode": "LF",
                "positionName": "Left Field",
                "isStarter": True,
                "isActive": True,
            },
            {
                "teamSide": "home",
                "battingOrder": 2,
                "playerId": "H002",
                "playerName": "Home Inactive",
                "positionCode": "CF",
                "positionName": "Center Field",
                "isStarter": True,
                "isActive": False,
            },
        ]
        ingest = client.post(
            "/internal/crawler/games/20260601LINEUP01/snapshot",
            headers={"X-API-Key": "test-key"},
            json=snapshot,
        )
        assert ingest.status_code == 200

        state = client.get("/games/20260601LINEUP01/state")
        assert state.status_code == 200
        body = state.json()
        names = [s["playerName"] for s in body["homeLineup"]]
        assert "Home Active" in names
        assert "Home Inactive" not in names


def test_game_state_lineup_empty_when_no_slots() -> None:
    with TestClient(app) as client:
        snapshot = sample_snapshot()
        snapshot["lineupSlots"] = []
        snapshot["batterStats"] = []
        ingest = client.post(
            "/internal/crawler/games/20260601LINEUP02/snapshot",
            headers={"X-API-Key": "test-key"},
            json=snapshot,
        )
        assert ingest.status_code == 200

        state = client.get("/games/20260601LINEUP02/state")
        assert state.status_code == 200
        body = state.json()
        assert body["homeLineup"] == []
        assert body["awayLineup"] == []


def test_event_inning_populated_from_payload_and_fallback() -> None:
    with TestClient(app) as client:
        snapshot = sample_snapshot()
        snapshot["inning"] = "7B"
        snapshot["events"] = [
            {
                "sourceEventId": "inning-explicit-001",
                "type": "HIT",
                "description": "explicit inning",
                "occurredAt": "2026-02-17T08:59:20Z",
                "hapticPattern": "HIT-HIT",
                "inning": "6회말",
                "metadata": {},
            },
            {
                "sourceEventId": "inning-fallback-001",
                "type": "SCORE",
                "description": "fallback to game.inning",
                "occurredAt": "2026-02-17T08:59:44Z",
                "hapticPattern": "SCORE-SCORE",
                "metadata": {},
            },
        ]
        ingest = client.post(
            "/internal/crawler/games/20260601INNING01/snapshot",
            headers={"X-API-Key": "test-key"},
            json=snapshot,
        )
        assert ingest.status_code == 200

        events = client.get("/games/20260601INNING01/events")
        assert events.status_code == 200
        items = events.json()["items"]
        by_source = {item["id"]: item for item in items}
        assert by_source["inning-explicit-001"]["inning"] == "6회말"
        # game.inning "7B" 는 정규화돼 저장되므로 응답은 정규화된 값(예: "7회말")
        assert by_source["inning-fallback-001"]["inning"] is not None
        assert by_source["inning-fallback-001"]["inning"] != ""


def test_live_view_session_upsert_tracks_active_surface() -> None:
    from app.db import init_db

    init_db()
    with TestClient(app) as client:
        created = client.post(
            "/live-view-sessions",
            json={
                "game_id": "20260611LIVEVIEW01",
                "user_key": "install-1",
                "surface": "android",
                "token_key": "fcm-token-1",
                "my_team": "DOOSAN",
                "active": True,
            },
        )
        assert created.status_code == 200
        assert created.json()["status"] == "ok"

        disabled = client.post(
            "/live-view-sessions",
            json={
                "game_id": "20260611LIVEVIEW01",
                "user_key": "install-1",
                "surface": "android",
                "token_key": "fcm-token-2",
                "my_team": "LG",
                "active": False,
            },
        )
        assert disabled.status_code == 200

    with SessionLocal() as db:
        rows = db.query(LiveViewSession).filter_by(
            game_id="20260611LIVEVIEW01",
            user_key="install-1",
            surface="android",
        ).all()
        assert len(rows) == 1
        assert rows[0].active is False
        assert rows[0].token_key == "fcm-token-2"
        assert rows[0].my_team == "LG"


def test_team_display_name_style_resolves_team_mascot_and_default() -> None:
    assert main_module._team_display_name("DOOSAN", "TEAM") == "두산"
    assert main_module._team_display_name("DOOSAN", "MASCOT") == "베어스"
    assert main_module._team_display_name("두산 베어스", None) == "두산"
    assert main_module._team_display_name("UNKNOWN", "MASCOT") == "UNKNOWN"


def test_game_start_team_match_codes_include_korean_aliases() -> None:
    assert {"DOOSAN", "베어스"}.issubset(main_module._team_codes_for_match("두산"))
    assert {"LOTTE", "자이언츠"}.issubset(main_module._team_codes_for_match("롯데"))
    assert {"SAMSUNG", "라이온즈"}.issubset(main_module._team_codes_for_match("삼성 라이온즈"))


def test_game_start_push_groups_korean_home_away_against_code_subscriptions() -> None:
    captured: list[dict[str, object]] = []

    def fake_load_team_subscriptions(my_teams: set[str]) -> list[tuple[str, str, str, bool, str]]:
        assert {"DOOSAN", "LOTTE"}.issubset(my_teams)
        return [
            ("android-doosan-token", "DOOSAN", "android", False, "MASCOT"),
            ("android-lotte-token", "LOTTE", "android", False, "MASCOT"),
        ]

    async def fake_fcm(
        tokens: list[str], *, title: str, body: str, data: dict[str, str] | None = None,
    ) -> tuple[list[str], list[str]]:
        captured.append({"tokens": tokens, "title": title, "body": body, "data": data})
        return [], []

    with patch.object(main_module, "_load_team_subscriptions", side_effect=fake_load_team_subscriptions), \
        patch.object(main_module, "send_fcm_visible_push_to_tokens_detailed", side_effect=fake_fcm):
        asyncio.run(main_module._send_game_start_notification("20260616LTOB02026", "두산", "롯데"))

    assert len(captured) == 2
    assert {tuple(item["tokens"]) for item in captured} == {
        ("android-doosan-token",),
        ("android-lotte-token",),
    }
    assert {item["title"] for item in captured} == {"[자이언츠] vs [베어스]"}
    assert {item["body"] for item in captured} == {
        "베어스 경기가 시작되었습니다!",
        "자이언츠 경기가 시작되었습니다!",
    }


def test_loss_push_targets_only_losing_team() -> None:
    captured: list[dict[str, object]] = []
    requested_codes: list[set[str]] = []

    def fake_load(my_teams: set[str]) -> list[tuple[str, str, str, bool, str, str | None]]:
        requested_codes.append(set(my_teams))
        return [("android-lotte-token", "LOTTE", "android", False, "MASCOT", None)]

    async def fake_fcm(
        tokens: list[str], *, title: str, body: str, data: dict[str, str] | None = None,
    ) -> tuple[list[str], list[str]]:
        captured.append({"tokens": tokens, "title": title, "body": body, "data": data})
        return [], []

    with patch.object(main_module, "_load_team_subscriptions_with_version", side_effect=fake_load), \
        patch.object(main_module, "send_fcm_visible_push_to_tokens_detailed", side_effect=fake_fcm):
        # 홈 두산 5 : 3 롯데(원정) → 패배팀 = 롯데
        asyncio.run(main_module._send_loss_notification("20260616LTOB02026", "두산", "롯데", 5, 3))

    # 패배팀(롯데) 코드만 조회, 승리팀(두산) 코드는 미포함
    assert requested_codes, "구독 조회가 호출되어야 한다"
    codes = requested_codes[0]
    assert {"LOTTE", "자이언츠"}.issubset(codes)
    assert "DOOSAN" not in codes and "베어스" not in codes
    assert len(captured) == 1
    assert captured[0]["tokens"] == ["android-lotte-token"]
    assert isinstance(captured[0]["data"], dict) and captured[0]["data"]["kind"] == "venting_loss"


def test_loss_push_version_gate_filters_old_versions() -> None:
    """min_version 설정 시 그 버전 이상 구독자에게만 발송(구버전·미상 제외)."""
    captured: list[list[str]] = []

    def fake_load(my_teams: set[str]) -> list[tuple[str, str, str, bool, str, str | None]]:
        return [
            ("tok-new", "LOTTE", "android", False, "MASCOT", "8.6.0"),   # 지원 버전 → 받음
            ("tok-old", "LOTTE", "android", False, "MASCOT", "8.5.9"),   # 구버전 → 제외
            ("tok-none", "LOTTE", "android", False, "MASCOT", None),      # 미상 → 제외
        ]

    async def fake_fcm(tokens, *, title, body, data=None):
        captured.append(list(tokens))
        return [], []

    settings = main_module.settings
    prev = settings.venting_loss_push_min_version
    settings.venting_loss_push_min_version = "8.6.0"
    try:
        with patch.object(main_module, "_load_team_subscriptions_with_version", side_effect=fake_load), \
            patch.object(main_module, "send_fcm_visible_push_to_tokens_detailed", side_effect=fake_fcm):
            asyncio.run(main_module._send_loss_notification("g", "두산", "롯데", 5, 3))
    finally:
        settings.venting_loss_push_min_version = prev

    sent = [t for group in captured for t in group]
    assert sent == ["tok-new"]  # 지원 버전만


def test_version_gte_helper() -> None:
    assert main_module._version_gte("8.6.0", "8.6.0") is True
    assert main_module._version_gte("8.10.0", "8.9.0") is True   # 숫자 비교(문자열 아님)
    assert main_module._version_gte("8.5.9", "8.6.0") is False
    assert main_module._version_gte(None, "8.6.0") is False
    assert main_module._version_gte("8.5.0", "") is True         # 게이트 없음


def test_loss_push_skips_draw() -> None:
    called = {"load": False}

    def fake_load(my_teams: set[str]) -> list[tuple[str, str, str, bool, str]]:
        called["load"] = True
        return []

    with patch.object(main_module, "_load_team_subscriptions", side_effect=fake_load):
        asyncio.run(main_module._send_loss_notification("g", "두산", "롯데", 4, 4))

    assert called["load"] is False  # 무승부는 토큰 조회조차 하지 않는다


def test_first_live_snapshot_schedules_game_start_push() -> None:
    from app.db import init_db

    init_db()
    calls: list[tuple[str, str, str]] = []

    async def fake_game_start_push(game_id: str, home_team: str, away_team: str) -> None:
        calls.append((game_id, home_team, away_team))

    payload = sample_snapshot()
    payload["homeTeam"] = "두산"
    payload["awayTeam"] = "롯데"
    payload["status"] = "LIVE"
    payload["events"] = []

    with patch.object(main_module, "_send_game_start_notification", side_effect=fake_game_start_push):
        with TestClient(app) as client:
            response = client.post(
                "/internal/crawler/games/20260616START01/snapshot",
                headers={"X-API-Key": "test-key"},
                json=payload,
            )

    assert response.status_code == 200
    assert calls == [("20260616START01", "두산", "롯데")]

    with SessionLocal() as db:
        game = db.query(Game).filter_by(id="20260616START01").one()
        assert game.status == "LIVE"
        assert game.live_started_at is not None


def test_push_token_registration_stores_display_name_style_with_default() -> None:
    from app.db import init_db

    init_db()
    with TestClient(app) as client:
        device_created = client.post(
            "/device-tokens",
            json={
                "token": "apns-display-style-1",
                "game_id": "20260616STYLE01",
                "my_team": "DOOSAN",
                "platform": "ios",
                "display_name_style": "MASCOT",
            },
        )
        assert device_created.status_code == 200

        subscription_created = client.post(
            "/team-subscriptions",
            json={
                "token": "team-display-style-default-1",
                "my_team": "DOOSAN",
                "platform": "android",
            },
        )
        assert subscription_created.status_code == 200

    with SessionLocal() as db:
        device = db.query(DeviceToken).filter_by(token="apns-display-style-1").one()
        subscription = db.query(TeamSubscriptionToken).filter_by(token="team-display-style-default-1").one()
        assert device.display_name_style == "MASCOT"
        assert subscription.display_name_style == "TEAM"


def test_event_at_bat_id_and_seqno_populated_from_source_event_id() -> None:
    """크롤러가 보내는 'NN-NNN-NNNN' 형식의 sourceEventId 에서 atBatId/seqno 가
    분리되어 응답에 노출되어야 한다 (라이브 상세 화면 타석 단위 그룹화 키)."""
    with TestClient(app) as client:
        snapshot = sample_snapshot()
        snapshot["events"] = [
            {
                "sourceEventId": "07-045-0001",
                "type": "BALL",
                "description": "ball outside",
                "occurredAt": "2026-02-17T08:59:10Z",
                "metadata": {},
            },
            {
                "sourceEventId": "07-045-0002",
                "type": "STRIKE",
                "description": "swinging strike",
                "occurredAt": "2026-02-17T08:59:20Z",
                "metadata": {},
            },
            {
                "sourceEventId": "07-046-0001",
                "type": "HIT",
                "description": "single to left",
                "occurredAt": "2026-02-17T08:59:40Z",
                "metadata": {},
            },
            {
                # 폴백: 시뮬레이션/테스트에서 들어올 수 있는 비정형 형식 → None 으로 둔다
                "sourceEventId": "relay-fallback-001",
                "type": "OUT",
                "description": "fly out",
                "occurredAt": "2026-02-17T08:59:50Z",
                "metadata": {},
            },
        ]
        ingest = client.post(
            "/internal/crawler/games/20260601ATBAT01/snapshot",
            headers={"X-API-Key": "test-key"},
            json=snapshot,
        )
        assert ingest.status_code == 200

        events = client.get("/games/20260601ATBAT01/events")
        assert events.status_code == 200
        by_source = {item["id"]: item for item in events.json()["items"]}

        assert by_source["07-045-0001"]["atBatId"] == "07-045"
        assert by_source["07-045-0001"]["seqno"] == 1
        assert by_source["07-045-0002"]["atBatId"] == "07-045"
        assert by_source["07-045-0002"]["seqno"] == 2
        # 같은 이닝의 다른 타석은 다른 atBatId 를 가져야 한다
        assert by_source["07-046-0001"]["atBatId"] == "07-046"
        assert by_source["07-046-0001"]["seqno"] == 1
        # 비정형 sourceEventId 는 폴백
        assert by_source["relay-fallback-001"]["atBatId"] is None
        assert by_source["relay-fallback-001"]["seqno"] is None


def test_event_score_after_populated_from_payload_metadata() -> None:
    """SCORE 이벤트는 payload_json metadata 에 homeScoreAfter/awayScoreAfter 가
    있으면 응답에 그대로 노출. 라이브 상세 "득점" 탭에서 정확 점수 표기에 사용.

    크롤러가 metadata 에 안 채운 이벤트는 응답 응답 필드가 null 로 폴백돼야 한다."""
    with TestClient(app) as client:
        snapshot = sample_snapshot()
        snapshot["events"] = [
            {
                "sourceEventId": "01-005-0001",
                "type": "SCORE",
                "description": "박해민 적시타로 1점",
                "occurredAt": "2026-02-17T09:00:00Z",
                "metadata": {"homeScoreAfter": 0, "awayScoreAfter": 1},
            },
            {
                "sourceEventId": "01-005-0002",
                "type": "HIT",
                "description": "다음 타자 안타",
                "occurredAt": "2026-02-17T09:00:10Z",
                "metadata": {"homeScoreAfter": 0, "awayScoreAfter": 1},
            },
            {
                "sourceEventId": "02-001-0001",
                "type": "SCORE",
                "description": "박찬호 솔로 홈런",
                "occurredAt": "2026-02-17T09:05:00Z",
                "metadata": {"homeScoreAfter": 1, "awayScoreAfter": 1},
            },
            {
                # 메타데이터에 스코어 없는 케이스 — 응답에서 null 폴백
                "sourceEventId": "02-002-0001",
                "type": "OUT",
                "description": "땅볼 아웃",
                "occurredAt": "2026-02-17T09:06:00Z",
                "metadata": {},
            },
        ]
        ingest = client.post(
            "/internal/crawler/games/20260601SCORE01/snapshot",
            headers={"X-API-Key": "test-key"},
            json=snapshot,
        )
        assert ingest.status_code == 200

        events = client.get("/games/20260601SCORE01/events")
        assert events.status_code == 200
        by_source = {item["id"]: item for item in events.json()["items"]}

        assert by_source["01-005-0001"]["homeScoreAfter"] == 0
        assert by_source["01-005-0001"]["awayScoreAfter"] == 1
        assert by_source["02-001-0001"]["homeScoreAfter"] == 1
        assert by_source["02-001-0001"]["awayScoreAfter"] == 1
        # 메타데이터 누락 — null 폴백
        assert by_source["02-002-0001"]["homeScoreAfter"] is None
        assert by_source["02-002-0001"]["awayScoreAfter"] is None
        # 0 도 명시적으로 유지(falsy 가 아닌 valid 값)
        assert by_source["01-005-0002"]["homeScoreAfter"] == 0


def test_event_pitch_detail_metadata_exposed_from_payload() -> None:
    """네이버 relay 원본의 투구 상세/BSO/확률 metadata 는 이벤트 응답에 optional 로 노출한다."""
    with TestClient(app) as client:
        snapshot = sample_snapshot()
        snapshot["events"] = [
            {
                "sourceEventId": "05-040-0210",
                "type": "STRIKE",
                "description": "5구 헛스윙",
                "occurredAt": "2026-06-07T09:05:06Z",
                "inning": "5회초",
                "metadata": {
                    "pitchNum": 5,
                    "pitchSpeed": 148,
                    "pitchStuff": "투심",
                    "ballAfter": 2,
                    "strikeAfter": 3,
                    "outAfter": 0,
                    "batterRecord": {
                        "name": "오윤석",
                        "batOrder": 8,
                        "seasonHra": 0.278,
                        "pa": 2,
                        "ab": 2,
                        "hit": 0,
                        "rbi": 0,
                        "hr": 0,
                        "bb": 0,
                        "so": 1,
                    },
                    "homeWinProbability": 76.0,
                    "awayWinProbability": 24.0,
                    "wpaByPlate": -3.2,
                },
            },
            {
                "sourceEventId": "05-040-0211",
                "type": "OUT",
                "description": "오윤석 : 삼진 아웃",
                "occurredAt": "2026-06-07T09:05:07Z",
                "inning": "5회초",
                "metadata": {},
            },
        ]
        ingest = client.post(
            "/internal/crawler/games/20260607PITCHMETA01/snapshot",
            headers={"X-API-Key": "test-key"},
            json=snapshot,
        )
        assert ingest.status_code == 200

        events = client.get("/games/20260607PITCHMETA01/events")
        assert events.status_code == 200
        by_source = {item["id"]: item for item in events.json()["items"]}

        pitch = by_source["05-040-0210"]
        assert pitch["atBatId"] == "05-040"
        assert pitch["seqno"] == 210
        assert pitch["pitchNum"] == 5
        assert pitch["pitchSpeed"] == 148
        assert pitch["pitchStuff"] == "투심"
        assert pitch["ballAfter"] == 2
        assert pitch["strikeAfter"] == 3
        assert pitch["outAfter"] == 0
        assert pitch["batterRecord"]["name"] == "오윤석"
        assert pitch["batterRecord"]["batOrder"] == 8
        assert pitch["awayWinProbability"] == 24.0
        assert pitch["wpaByPlate"] == -3.2

        outcome = by_source["05-040-0211"]
        assert outcome["atBatId"] == "05-040"
        assert outcome["pitchSpeed"] is None
        assert outcome["batterRecord"] is None


def test_events_endpoint_filters_by_inning_number_and_scoring_only() -> None:
    """라이브 상세 이닝 탭 lazy-load 를 위해 이벤트 API 는 회차/득점 필터를 지원한다.

    기존 cursor/limit 조회는 그대로 유지하면서, inningNumber 는 N회초+N회말을 모두
    포함하고 scoringOnly 는 득점성 이벤트만 반환해야 한다.
    """
    with TestClient(app) as client:
        snapshot = sample_snapshot()
        snapshot["events"] = [
            {
                "sourceEventId": "01-001-0001",
                "type": "BALL",
                "description": "1회초 볼",
                "occurredAt": "2026-02-17T09:00:00Z",
                "inning": "1회초",
                "metadata": {},
            },
            {
                "sourceEventId": "01-002-0001",
                "type": "SCORE",
                "description": "1회말 득점",
                "occurredAt": "2026-02-17T09:01:00Z",
                "inning": "1회말",
                "metadata": {},
            },
            {
                "sourceEventId": "02-001-0001",
                "type": "HIT",
                "description": "2회초 안타",
                "occurredAt": "2026-02-17T09:02:00Z",
                "inning": "2회초",
                "metadata": {},
            },
            {
                "sourceEventId": "02-002-0001",
                "type": "SAC_FLY_SCORE",
                "description": "2회말 희생플라이 득점",
                "occurredAt": "2026-02-17T09:03:00Z",
                "inning": "2회말",
                "metadata": {},
            },
        ]
        ingest = client.post(
            "/internal/crawler/games/20260601EVENTFILTER01/snapshot",
            headers={"X-API-Key": "test-key"},
            json=snapshot,
        )
        assert ingest.status_code == 200

        inning_one = client.get("/games/20260601EVENTFILTER01/events?inningNumber=1&limit=200")
        assert inning_one.status_code == 200
        assert [item["id"] for item in inning_one.json()["items"]] == [
            "01-001-0001",
            "01-002-0001",
        ]

        scoring = client.get("/games/20260601EVENTFILTER01/events?scoringOnly=true&limit=200")
        assert scoring.status_code == 200
        assert [item["id"] for item in scoring.json()["items"]] == [
            "01-002-0001",
            "02-002-0001",
        ]

        scoring_inning_two = client.get(
            "/games/20260601EVENTFILTER01/events?inningNumber=2&scoringOnly=true&limit=200"
        )
        assert scoring_inning_two.status_code == 200
        assert [item["id"] for item in scoring_inning_two.json()["items"]] == ["02-002-0001"]


def _insert_cheer_event(
    db,
    *,
    user_id: str,
    client_ts,
    status: str = "pending",
) -> int:
    from app.models import CheerEvent

    event = CheerEvent(
        user_id=user_id,
        team_code="DOOSAN",
        stadium_code="JAMSIL",
        client_ts=client_ts,
        lat=37.5121,
        lng=127.0719,
        accuracy_m=20.0,
        mock_location=False,
        platform="ios",
        validity_status=status,
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event.id


def test_user_checkin_aggregates_increment_on_valid():
    from datetime import datetime, timezone

    from app.db import init_db
    from app.models import UserCheckinDaily, UserCheckinSeason
    from app.workers.cheer_validator import validate_pending_cheer_events

    init_db()
    user_id = "user-checkin-incr-1"
    ts = datetime(2026, 6, 4, 10, 0, 0, tzinfo=timezone.utc)  # KST 2026-06-04 19:00

    with SessionLocal() as db:
        _insert_cheer_event(db, user_id=user_id, client_ts=ts)
        validate_pending_cheer_events(db)

        daily = db.get(UserCheckinDaily, {"user_id": user_id, "date": "2026-06-04"})
        season = db.get(UserCheckinSeason, {"user_id": user_id, "season": "2026"})
        assert daily is not None and daily.count == 1
        assert season is not None and season.count == 1

        _insert_cheer_event(db, user_id=user_id, client_ts=ts)
        validate_pending_cheer_events(db)

        db.refresh(daily)
        db.refresh(season)
        assert daily.count == 2
        assert season.count == 2


def test_user_checkin_aggregates_not_incremented_on_invalid():
    from datetime import datetime, timezone

    from app.db import init_db
    from app.models import CheerEvent, UserCheckinDaily, UserCheckinSeason
    from app.workers.cheer_validator import validate_pending_cheer_events

    init_db()
    user_id = "user-checkin-invalid-1"
    ts = datetime(2026, 6, 4, 10, 0, 0, tzinfo=timezone.utc)

    with SessionLocal() as db:
        bad = CheerEvent(
            user_id=user_id,
            team_code="DOOSAN",
            stadium_code="JAMSIL",
            client_ts=ts,
            lat=37.5121,
            lng=127.0719,
            accuracy_m=20.0,
            mock_location=True,  # invalid 트리거
            platform="ios",
            validity_status="pending",
        )
        db.add(bad)
        db.commit()
        validate_pending_cheer_events(db)

        assert db.get(UserCheckinDaily, {"user_id": user_id, "date": "2026-06-04"}) is None
        assert db.get(UserCheckinSeason, {"user_id": user_id, "season": "2026"}) is None


def test_user_checkin_backfill_idempotent():
    from datetime import datetime, timezone

    from sqlalchemy import delete

    from app.db import _ensure_user_checkin_backfill, init_db
    from app.models import UserCheckinDaily, UserCheckinSeason

    init_db()
    user_id = "user-checkin-backfill-1"
    ts = datetime(2026, 5, 1, 10, 0, 0, tzinfo=timezone.utc)  # KST 2026-05-01 19:00

    with SessionLocal() as db:
        # 백필 대상으로 valid raw 이벤트 2건을 직접 심는다(이 user 한정).
        _insert_cheer_event(db, user_id=user_id, client_ts=ts, status="valid")
        _insert_cheer_event(db, user_id=user_id, client_ts=ts, status="valid")

        # guard 가 발동하지 않도록 user_checkin_* 테이블을 비운 뒤 백필 호출.
        db.execute(delete(UserCheckinDaily))
        db.execute(delete(UserCheckinSeason))
        db.commit()

        _ensure_user_checkin_backfill()

        daily = db.get(UserCheckinDaily, {"user_id": user_id, "date": "2026-05-01"})
        season = db.get(UserCheckinSeason, {"user_id": user_id, "season": "2026"})
        assert daily is not None and daily.count == 2
        assert season is not None and season.count == 2

        # 멱등성: 두 번째 호출은 guard("user_checkin_season 행 존재")로 skip 되어야 한다.
        _ensure_user_checkin_backfill()
        db.refresh(daily)
        db.refresh(season)
        assert daily.count == 2
        assert season.count == 2


def test_cheer_events_user_id_index_exists():
    from sqlalchemy import inspect

    from app.db import engine, init_db

    init_db()
    inspector = inspect(engine)
    index_names = {idx["name"] for idx in inspector.get_indexes("cheer_events")}
    assert "idx_cheer_events_user_id" in index_names


def _make_supabase_jwt(
    sub: str = "user-jwt-1",
    *,
    secret: str = "test-jwt-secret-0123456789abcdef0123456789abcdef",
    aud: str = "authenticated",
) -> str:
    import jwt

    return jwt.encode({"sub": sub, "aud": aud}, secret, algorithm="HS256")


def test_jwt_valid_signature_is_accepted():
    from app.db import init_db

    init_db()
    token = _make_supabase_jwt(sub="user-jwt-valid-1")
    with TestClient(app) as client:
        response = client.get(
            "/cheer-events/me",
            headers={"Authorization": f"Bearer {token}"},
        )
    assert response.status_code == 200
    assert response.json()["user_id"] == "user-jwt-valid-1"


def test_jwt_invalid_signature_is_rejected():
    from app.db import init_db

    init_db()
    forged = _make_supabase_jwt(
        sub="user-jwt-forged-1",
        secret="attacker-secret-0123456789abcdef0123456789abcdef",
    )
    with TestClient(app) as client:
        response = client.get(
            "/cheer-events/me",
            headers={"Authorization": f"Bearer {forged}"},
        )
    assert response.status_code == 401


def test_jwt_unsigned_token_is_rejected():
    import base64
    import json as jsonlib

    from app.db import init_db

    init_db()

    def b64url(data: dict) -> str:
        raw = jsonlib.dumps(data, separators=(",", ":")).encode()
        return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()

    # alg=none 무서명 토큰은 반드시 거부되어야 한다 (기존 unverified decode 회귀 방지).
    unsigned = f"{b64url({'alg': 'none', 'typ': 'JWT'})}.{b64url({'sub': 'user-x', 'aud': 'authenticated'})}."
    with TestClient(app) as client:
        response = client.get(
            "/cheer-events/me",
            headers={"Authorization": f"Bearer {unsigned}"},
        )
    assert response.status_code == 401


def test_jwt_missing_secret_fails_closed():
    from app.db import init_db

    init_db()
    token = _make_supabase_jwt(sub="user-jwt-noconf-1")
    with patch.object(main_module.settings, "supabase_jwt_secret", ""):
        with TestClient(app) as client:
            response = client.get(
                "/cheer-events/me",
                headers={"Authorization": f"Bearer {token}"},
            )
    assert response.status_code == 401


def test_cheer_events_me_applies_limit_and_offset():
    from datetime import datetime, timezone

    from app.db import init_db

    init_db()
    user_id = "user-limit-1"
    with SessionLocal() as db:
        for hour in range(5):
            _insert_cheer_event(
                db,
                user_id=user_id,
                client_ts=datetime(2026, 6, 10, 9 + hour, 0, 0, tzinfo=timezone.utc),
                status="valid",
            )

    token = _make_supabase_jwt(sub=user_id)
    headers = {"Authorization": f"Bearer {token}"}
    with TestClient(app) as client:
        limited = client.get("/cheer-events/me?limit=2", headers=headers)
        assert limited.status_code == 200
        body = limited.json()
        assert body["count"] == 2
        assert len(body["items"]) == 2

        paged = client.get("/cheer-events/me?limit=2&offset=4", headers=headers)
        assert paged.status_code == 200
        assert paged.json()["count"] == 1

        default = client.get("/cheer-events/me", headers=headers)
        assert default.status_code == 200
        assert default.json()["count"] == 5


def test_silent_push_prunes_permanently_failed_tokens():
    from app.db import init_db

    init_db()
    game_id = "20260715PRUNE01"
    with SessionLocal() as db:
        db.add(DeviceToken(token="dead-token-1", game_id=game_id, my_team="DOOSAN", platform="ios", is_sandbox=False))
        db.add(DeviceToken(token="alive-token-1", game_id=game_id, my_team="LG", platform="ios", is_sandbox=False))
        db.commit()

    async def fake_cached_push_tokens(gid: str):
        return [
            ("dead-token-1", "DOOSAN", False, "ios", "TEAM"),
            ("alive-token-1", "LG", False, "ios", "TEAM"),
        ]

    async def fake_send_push_with_result(token, payload, *, use_sandbox=None, platform="ios"):
        # dead-token-1 은 APNs 410 Unregistered 상황을 흉내낸다.
        return (False, True) if token == "dead-token-1" else (True, False)

    state_payload = {"gameId": game_id, "bases": {}}
    with patch.object(main_module, "_cached_push_tokens", side_effect=fake_cached_push_tokens), \
        patch.object(main_module, "send_push_with_result", side_effect=fake_send_push_with_result):
        asyncio.run(main_module._send_push_for_game_events(game_id, state_payload, []))

    with SessionLocal() as db:
        remaining = {row.token for row in db.query(DeviceToken).filter_by(game_id=game_id).all()}
    assert remaining == {"alive-token-1"}


def test_fcm_batch_send_reports_all_failed_when_unconfigured():
    from app import fcm as fcm_module

    failed, unregistered = asyncio.run(
        fcm_module.send_visible_push_to_tokens_detailed(["t1", "t2"], title="t", body="b")
    )
    assert failed == ["t1", "t2"]
    assert unregistered == []


def test_debug_relay_stats_requires_api_key():
    with TestClient(app) as client:
        unauthorized = client.get("/debug/relay-stats")
        assert unauthorized.status_code == 401

        authorized = client.get("/debug/relay-stats", headers={"X-API-Key": "test-key"})
        assert authorized.status_code == 200


def test_weekly_team_rankings_aggregate_in_sql():
    from datetime import timedelta

    from app.db import init_db
    from app.models import TeamCheckinDaily
    from app.weather import KST as WEATHER_KST
    from datetime import datetime as dt

    init_db()
    today = dt.now(WEATHER_KST).date()
    with SessionLocal() as db:
        db.execute(text("DELETE FROM team_checkin_daily"))
        for offset, count in ((1, 3), (2, 4)):
            db.add(TeamCheckinDaily(
                team_code="DOOSAN",
                date=(today - timedelta(days=offset)).isoformat(),
                count=count,
            ))
        db.add(TeamCheckinDaily(team_code="LG", date=(today - timedelta(days=1)).isoformat(), count=5))
        # 7일 윈도 밖의 데이터는 집계에서 제외되어야 한다.
        db.add(TeamCheckinDaily(team_code="LG", date=(today - timedelta(days=30)).isoformat(), count=99))
        db.commit()

    with TestClient(app) as client:
        response = client.get("/rankings/teams?period=weekly")
    assert response.status_code == 200
    items = response.json()["items"]
    assert items[0] == {"team_code": "DOOSAN", "count": 7, "rank": 1}
    assert items[1] == {"team_code": "LG", "count": 5, "rank": 2}


def test_purge_expired_game_rows_deletes_old_details_keeps_games():
    from datetime import datetime as dt

    from app.db import init_db
    from app.weather import KST as WEATHER_KST

    init_db()
    today = dt.now(WEATHER_KST).date()
    old_id = "20260601TESTPURGE1"
    recent_id = f"{today.strftime('%Y%m%d')}TESTPURGE2"
    event_time = dt(2026, 6, 1, 10, 0, 0, tzinfo=timezone.utc)

    with SessionLocal() as db:
        db.add(Game(id=old_id, home_team="Doosan", away_team="LG", status="FINISHED",
                    game_date=(today - timedelta(days=10)).isoformat()))
        db.add(Game(id=recent_id, home_team="SSG", away_team="KT", status="LIVE",
                    game_date=today.isoformat()))
        db.flush()
        for idx in range(3):
            db.add(GameEvent(game_id=old_id, source_event_id=f"purge-old-{idx}",
                             event_type="HIT", description="old", event_time=event_time))
        db.add(GameEvent(game_id=recent_id, source_event_id="purge-recent-0",
                         event_type="HIT", description="recent", event_time=event_time))
        db.add(GameLineupSlot(game_id=old_id, team_side="home", batting_order=1, player_name="타자"))
        db.add(GameBatterStat(game_id=old_id, team_side="home", player_name="타자"))
        db.add(GamePitcherStat(game_id=old_id, team_side="home", player_name="투수"))
        db.add(GameNote(game_id=old_id, note_type="INFO", note_title="t", note_body="b"))
        db.commit()

    # 배치 상한을 작게 잡아 배치 루프 경로까지 검증한다.
    with patch.object(main_module, "GAME_DATA_PURGE_BATCH_ROWS", 2), \
         patch.object(main_module, "GAME_DATA_PURGE_BATCH_PAUSE_SEC", 0):
        deleted = main_module._purge_expired_game_rows()

    # 다른 테스트가 남긴 과거 경기도 함께 정리될 수 있으므로 하한으로 검증한다.
    assert deleted["game_events"] >= 3
    assert deleted["game_lineup_slots"] >= 1
    assert deleted["game_batter_stats"] >= 1
    assert deleted["game_pitcher_stats"] >= 1
    assert deleted["game_notes"] >= 1

    with SessionLocal() as db:
        # games 행은 보존, 최근 경기 상세는 유지
        assert db.get(Game, old_id) is not None
        assert db.get(Game, recent_id) is not None
        remaining = db.query(GameEvent).filter(GameEvent.game_id == old_id).count()
        assert remaining == 0
        assert db.query(GameEvent).filter(GameEvent.game_id == recent_id).count() == 1
        db.query(Game).filter(Game.id.in_((old_id, recent_id))).delete(synchronize_session=False)
        db.query(GameEvent).filter(GameEvent.game_id == recent_id).delete(synchronize_session=False)
        db.commit()


def test_purge_expired_game_rows_noop_when_nothing_old():
    from app.db import init_db

    init_db()
    deleted = main_module._purge_expired_game_rows()
    assert deleted == {}


def test_list_games_team_filter():
    from datetime import date as d, datetime as dt

    from app.db import init_db

    init_db()
    with SessionLocal() as db:
        db.add(Game(id="20260720LGOB02026", home_team="두산", away_team="LG", status="SCHEDULED",
                    game_date="2026-07-20"))
        db.add(Game(id="20260720SSKT02026", home_team="KT", away_team="SSG", status="SCHEDULED",
                    game_date="2026-07-20"))
        db.commit()

    with TestClient(app) as client:
        res = client.get("/games?from=2026-07-20&to=2026-07-20&team=DOOSAN&limit=500")
        assert res.status_code == 200
        ids = [g["id"] for g in res.json()]
        assert "20260720LGOB02026" in ids
        assert "20260720SSKT02026" not in ids

        # 한글 모기업 라벨 저장값과 무관하게 영문 코드로 조회 가능해야 한다
        res2 = client.get("/games?from=2026-07-20&to=2026-07-20&team=SSG&limit=500")
        assert res2.status_code == 200
        ids2 = [g["id"] for g in res2.json()]
        assert "20260720SSKT02026" in ids2
        assert "20260720LGOB02026" not in ids2

        res3 = client.get("/games?date=2026-07-20&team=NOPE")
        assert res3.status_code == 400

    with SessionLocal() as db:
        db.query(Game).filter(Game.id.in_(("20260720LGOB02026", "20260720SSKT02026"))).delete(synchronize_session=False)
        db.commit()


def test_games_list_cache_ttl_selection():
    from datetime import timedelta as td, datetime as dt

    from app.weather import KST as WEATHER_KST

    today = dt.now(WEATHER_KST).date()
    past = today - td(days=3)
    future = today + td(days=3)

    # 오늘 포함 → 라이브 TTL
    assert main_module._games_list_cache_ttl(game_date=today, from_date=None, to_date=None) == main_module.HTTP_LIVE_CACHE_TTL_SEC
    assert main_module._games_list_cache_ttl(game_date=None, from_date=past, to_date=future) == main_module.HTTP_LIVE_CACHE_TTL_SEC
    # 전부 과거/미래 → 일정 TTL
    assert main_module._games_list_cache_ttl(game_date=past, from_date=None, to_date=None) == main_module.HTTP_SCHEDULE_RANGE_CACHE_TTL_SEC
    assert main_module._games_list_cache_ttl(game_date=None, from_date=future, to_date=future + td(days=10)) == main_module.HTTP_SCHEDULE_RANGE_CACHE_TTL_SEC
    assert main_module._games_list_cache_ttl(game_date=None, from_date=past - td(days=10), to_date=past) == main_module.HTTP_SCHEDULE_RANGE_CACHE_TTL_SEC


# MARK: - 이닝별 라인스코어 / 실책 (lineScore, homeErrors, awayErrors)

def test_snapshot_line_score_and_errors_persist_and_survive_omission() -> None:
    game_id = "20260401WOSK02026LS"
    with TestClient(app) as client:
        payload = sample_snapshot()
        payload["lineScore"] = {"home": {"1": 0, "2": 1}, "away": {"1": 3}}
        payload["homeErrors"] = 2
        payload["awayErrors"] = 0
        first = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert first.status_code == 200

        state = client.get(f"/games/{game_id}/state")
        assert state.status_code == 200
        body = state.json()
        assert body["lineScore"] == {"home": {"1": 0, "2": 1}, "away": {"1": 3}}
        assert body["homeErrors"] == 2
        assert body["awayErrors"] == 0

        # lineScore/errors 를 생략한 스냅샷이 기존 저장값을 지우지 않는다
        omitted = sample_snapshot()
        omitted["homeScore"] = 4
        omitted["observedAt"] = "2026-02-17T09:05:00Z"
        second = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=omitted,
        )
        assert second.status_code == 200
        second_updated_at = second.json()["updatedAt"]

        body = client.get(f"/games/{game_id}/state").json()
        assert body["homeScore"] == 4
        assert body["lineScore"] == {"home": {"1": 0, "2": 1}, "away": {"1": 3}}
        assert body["homeErrors"] == 2
        assert body["awayErrors"] == 0

        # lineScore 변경만으로도 meaningful change 로 잡혀 state 가 갱신된다
        line_score_only = sample_snapshot()
        line_score_only["homeScore"] = 4
        line_score_only["lineScore"] = {"home": {"1": 0, "2": 1, "3": 2}, "away": {"1": 3}}
        line_score_only["observedAt"] = "2026-02-17T09:06:00Z"
        third = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=line_score_only,
        )
        assert third.status_code == 200
        assert third.json()["updatedAt"] != second_updated_at

        body = client.get(f"/games/{game_id}/state").json()
        assert body["lineScore"] == {"home": {"1": 0, "2": 1, "3": 2}, "away": {"1": 3}}


def test_game_state_line_score_defaults_to_none_when_never_provided() -> None:
    game_id = "20260401WOSK02026LSN"
    with TestClient(app) as client:
        ingest = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=sample_snapshot(),
        )
        assert ingest.status_code == 200

        body = client.get(f"/games/{game_id}/state").json()
        assert body["lineScore"] is None
        assert body["homeErrors"] is None
        assert body["awayErrors"] is None


# MARK: - 박스스코어 endpoint (/games/{game_id}/boxscore)

def _boxscore_snapshot() -> dict:
    return {
        "homeTeam": "SSG",
        "awayTeam": "키움",
        "status": "LIVE",
        "inning": "3회초",
        "homeScore": 0,
        "awayScore": 1,
        "lineupSlots": [
            {"teamSide": "home", "battingOrder": 1, "playerName": "홈1번", "isStarter": True},
            {"teamSide": "home", "battingOrder": 2, "playerName": "홈2번", "isStarter": True},
            {"teamSide": "away", "battingOrder": 1, "playerName": "원정1번", "isStarter": True},
        ],
        "batterStats": [
            # 타순 없는 엔트리 선수(교체 대기)는 정렬 시 맨 뒤로 가야 한다
            {
                "teamSide": "home",
                "playerName": "홈교체대기",
                "battingOrder": None,
                "primaryPosition": "포수",
                "isStarter": False,
            },
            {
                "teamSide": "home",
                "playerName": "홈2번",
                "battingOrder": 2,
                "primaryPosition": "우익수",
                "isStarter": True,
                "atBats": 2,
                "hits": 1,
                "rbi": 1,
                "runs": 1,
                "homeRuns": 1,
                "walks": 0,
                "strikeouts": 1,
            },
            {
                "teamSide": "home",
                "playerName": "홈1번",
                "battingOrder": 1,
                "primaryPosition": "중견수",
                "isStarter": True,
                "atBats": 3,
                "hits": 2,
                "walks": 1,
            },
            {
                "teamSide": "away",
                "playerName": "원정1번",
                "battingOrder": 1,
                "primaryPosition": "유격수",
                "isStarter": True,
                "atBats": 2,
            },
        ],
        "pitcherStats": [
            # 등판 순서 역순으로 넣어도 appearance_order 로 정렬되어야 한다
            {
                "teamSide": "home",
                "appearanceOrder": 2,
                "playerName": "홈불펜",
                "isStarter": False,
                "outsRecorded": 3,
                "pitchesThrown": 15,
                "hitsAllowed": 1,
                "runsAllowed": 0,
                "earnedRuns": 0,
                "walksAllowed": 1,
                "strikeouts": 2,
            },
            {
                "teamSide": "home",
                "appearanceOrder": 1,
                "playerName": "홈선발",
                "isStarter": True,
                "outsRecorded": 6,
                "pitchesThrown": 40,
                "hitsAllowed": 3,
                "runsAllowed": 1,
                "earnedRuns": 1,
                "walksAllowed": 0,
                "strikeouts": 4,
            },
            {
                "teamSide": "away",
                "appearanceOrder": 1,
                "playerName": "원정선발",
                "isStarter": True,
                "outsRecorded": 6,
                "pitchesThrown": 35,
            },
        ],
    }


def test_boxscore_returns_ordered_batters_and_pitchers() -> None:
    game_id = "20260401WOSK02026BX"
    with TestClient(app) as client:
        ingest = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=_boxscore_snapshot(),
        )
        assert ingest.status_code == 200

        response = client.get(f"/games/{game_id}/boxscore")
        assert response.status_code == 200
        body = response.json()
        assert body["gameId"] == game_id

        # 타자: batting_order 오름차순, NULL 은 맨 뒤
        assert [b["playerName"] for b in body["homeBatters"]] == ["홈1번", "홈2번", "홈교체대기"]
        assert [b["battingOrder"] for b in body["homeBatters"]] == [1, 2, None]
        leadoff = body["homeBatters"][0]
        assert leadoff["position"] == "중견수"
        assert leadoff["atBats"] == 3
        assert leadoff["hits"] == 2
        assert leadoff["walks"] == 1
        assert leadoff["isStarter"] is True
        assert body["homeBatters"][1]["homeRuns"] == 1
        assert body["homeBatters"][1]["rbi"] == 1
        assert body["homeBatters"][2]["isStarter"] is False
        assert [b["playerName"] for b in body["awayBatters"]] == ["원정1번"]

        # 투수: appearance_order 오름차순
        assert [p["playerName"] for p in body["homePitchers"]] == ["홈선발", "홈불펜"]
        starter = body["homePitchers"][0]
        assert starter["appearanceOrder"] == 1
        assert starter["isStarter"] is True
        assert starter["outsRecorded"] == 6
        assert starter["pitchesThrown"] == 40
        assert starter["hitsAllowed"] == 3
        assert starter["runsAllowed"] == 1
        assert starter["earnedRuns"] == 1
        assert starter["walksAllowed"] == 0
        assert starter["strikeouts"] == 4
        assert [p["playerName"] for p in body["awayPitchers"]] == ["원정선발"]


def test_boxscore_404_when_game_missing_and_empty_when_no_stats() -> None:
    with TestClient(app) as client:
        missing = client.get("/games/UNKNOWN_GAME_ID/boxscore")
        assert missing.status_code == 404

        # 경기는 있지만 스탯이 없으면 빈 배열
        game_id = "20260401WOSK02026BXE"
        payload = sample_snapshot()
        payload.pop("lineupSlots")
        payload.pop("batterStats")
        payload.pop("pitcherStats")
        payload.pop("notes")
        payload["events"] = []
        ingest = client.post(
            f"/internal/crawler/games/{game_id}/snapshot",
            headers={"X-API-Key": "test-key"},
            json=payload,
        )
        assert ingest.status_code == 200

        response = client.get(f"/games/{game_id}/boxscore")
        assert response.status_code == 200
        assert response.json() == {
            "gameId": game_id,
            "homeBatters": [],
            "awayBatters": [],
            "homePitchers": [],
            "awayPitchers": [],
        }
