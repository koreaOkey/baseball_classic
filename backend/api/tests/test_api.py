import os
import sys
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.exc import OperationalError

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
from app import main as main_module  # noqa: E402
from app import db as db_module  # noqa: E402
from app.db import SessionLocal  # noqa: E402
from app.models import AppConfig, Game, GameBatterStat, GameEvent, GameLineupSlot, GameNote, GamePitcherStat, LiveViewSession, TeamRecord  # noqa: E402
from app.services import _event_out_count, normalize_event_type, normalize_status  # noqa: E402


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
