"""분풀이(venting) 백엔드 Phase 2 단위 테스트.

- regret 산정: 패배팀 후보 선별 / 무승부·플래그OFF 스킵 / 재산정 방지 / 실명 미저장 / LLM 폴백
- 지표 수집 + 팀 랭킹 집계
"""

import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

from fastapi.testclient import TestClient

API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))

DB_FILE = Path(__file__).parent / "test_venting.db"
if DB_FILE.exists():
    DB_FILE.unlink()

os.environ.setdefault("BASEHAPTIC_DATABASE_URL", f"sqlite+pysqlite:///{DB_FILE.as_posix()}")
os.environ.setdefault("BASEHAPTIC_CRAWLER_API_KEY", "test-key")
os.environ.setdefault("BASEHAPTIC_SUPABASE_JWT_SECRET", "test-jwt-secret-0123456789abcdef0123456789abcdef")

from app import venting as venting_module  # noqa: E402
from app.config import get_settings  # noqa: E402
from app.db import Base, SessionLocal, engine  # noqa: E402
from app.main import app  # noqa: E402
from app.models import Game, GameBatterStat, GameEvent, GamePitcherStat, TeamManager, VentingRegretCache  # noqa: E402

Base.metadata.create_all(bind=engine)
client = TestClient(app)
_settings = get_settings()


def _enable(*, llm: bool = False) -> None:
    _settings.venting_backend_enabled = True
    _settings.venting_llm_enabled = llm


def _disable() -> None:
    _settings.venting_backend_enabled = False
    _settings.venting_llm_enabled = False


def _seed_finished_game(game_id: str, *, home_score: int, away_score: int) -> None:
    """away 팀이 지는(기본) FINISHED 경기 + 이벤트/박스스코어 시드."""
    with SessionLocal() as db:
        db.add(
            Game(
                id=game_id,
                home_team="Doosan",
                away_team="LG",
                status="FINISHED",
                inning="경기 종료",
                home_score=home_score,
                away_score=away_score,
            )
        )
        # away 공격(7회초) 병살, away 수비(7회말) 실점
        db.add_all(
            [
                GameEvent(
                    game_id=game_id, source_event_id="e1", event_type="DOUBLE_PLAY",
                    description="병살타", event_time=datetime.now(timezone.utc),
                    batter="홍길동", inning="7회초",
                    payload_json={"wpaByPlate": -0.12, "outAfter": 3},
                ),
                GameEvent(
                    game_id=game_id, source_event_id="e2", event_type="SCORE",
                    description="적시타 실점", event_time=datetime.now(timezone.utc),
                    pitcher="김투수", inning="7회말",
                    payload_json={"wpaByPlate": -0.20},
                ),
                GameEvent(
                    game_id=game_id, source_event_id="e3", event_type="OUT",
                    description="삼진", event_time=datetime.now(timezone.utc),
                    batter="이타자", inning="9회초",
                    payload_json={"wpaByPlate": -0.08},
                ),
            ]
        )
        db.add_all(
            [
                GameBatterStat(game_id=game_id, team_side="away", player_name="홍길동", batting_order=5),
                GameBatterStat(game_id=game_id, team_side="away", player_name="이타자", batting_order=3),
                GamePitcherStat(game_id=game_id, team_side="away", player_name="김투수", appearance_order=1, is_starter=True),
            ]
        )
        db.commit()


def _cache_for(game_id: str) -> VentingRegretCache | None:
    with SessionLocal() as db:
        return db.query(VentingRegretCache).filter_by(game_id=game_id).first()


def test_regret_endpoint_returns_empty_when_disabled():
    _disable()
    resp = client.get("/games/none/venting/regret-top5")
    assert resp.status_code == 200
    assert resp.json()["items"] == []


def test_compute_regret_rule_fallback_no_real_names():
    _enable(llm=False)
    gid = "20260810DoosanLG0"
    _seed_finished_game(gid, home_score=7, away_score=2)
    venting_module.compute_regret_for_game(gid)

    cache = _cache_for(gid)
    assert cache is not None
    assert cache.team_code == "LG"  # 패배팀
    assert cache.source == "rule_fallback"
    labels = {item["role_label"] for item in cache.items}
    assert "5번 타자" in labels  # 병살 타자
    assert "선발 투수" in labels  # 실점 투수
    # 실명은 캐시에 절대 저장되지 않는다
    dumped = json.dumps(cache.items, ensure_ascii=False)
    for name in ("홍길동", "김투수", "이타자"):
        assert name not in dumped

    # 엔드포인트가 캐시를 그대로 반환 + 감독 6번째
    resp = client.get(f"/games/{gid}/venting/regret-top5")
    assert resp.status_code == 200
    body = resp.json()
    assert body["teamCode"] == "LG"
    assert len(body["items"]) >= 2


def test_compute_regret_skips_draw():
    _enable()
    gid = "20260810DrawGame0"
    _seed_finished_game(gid, home_score=3, away_score=3)
    venting_module.compute_regret_for_game(gid)
    assert _cache_for(gid) is None


def test_compute_regret_skips_when_disabled():
    _disable()
    gid = "20260810DisabledGame0"
    _seed_finished_game(gid, home_score=5, away_score=1)
    venting_module.compute_regret_for_game(gid)
    assert _cache_for(gid) is None


def test_compute_regret_not_recomputed():
    _enable()
    gid = "20260810RecomputeGame0"
    _seed_finished_game(gid, home_score=6, away_score=0)
    venting_module.compute_regret_for_game(gid)
    first = _cache_for(gid)
    assert first is not None
    first_computed = first.computed_at
    venting_module.compute_regret_for_game(gid)  # 2회차
    second = _cache_for(gid)
    assert second.computed_at == first_computed  # 재산정 안 됨


def test_manager_attached_as_sixth():
    _enable()
    gid = "20260810ManagerGame0"
    _seed_finished_game(gid, home_score=8, away_score=2)
    with SessionLocal() as db:
        db.add(TeamManager(team_code="LG", manager_name="염경엽", season="2026"))
        db.commit()
    venting_module.compute_regret_for_game(gid)
    resp = client.get(f"/games/{gid}/venting/regret-top5")
    assert resp.json()["manager"] == "염경엽"


def test_llm_ranking_falls_back_on_failure():
    _enable(llm=True)
    _settings.venting_llm_api_key = "sk-bad"
    _settings.venting_llm_model = "gpt-5.6-luna"
    _settings.venting_llm_base_url = "http://127.0.0.1:9/v1"  # 연결 실패 유도
    gid = "20260810LlmFailGame0"
    _seed_finished_game(gid, home_score=9, away_score=1)
    venting_module.compute_regret_for_game(gid)
    cache = _cache_for(gid)
    assert cache is not None
    assert cache.source == "rule_fallback"  # LLM 실패 → 규칙 폴백
    _settings.venting_llm_enabled = False


def test_venting_event_and_team_ranking():
    _enable()
    payload = {"event_type": "room_enter", "team": "LG", "entry_source": "home_card", "platform": "ios"}
    resp = client.post("/venting/events", json=payload)
    assert resp.status_code == 200
    assert resp.json()["ok"] is True
    client.post("/venting/events", json={"event_type": "room_enter", "team": "LG"})
    client.post("/venting/events", json={"event_type": "room_enter", "team": "Doosan"})

    season = str(datetime.now(venting_module.KST).year)
    rank = client.get(f"/venting/team-ranking?season={season}").json()["ranking"]
    counts = {row["team"]: row["count"] for row in rank}
    assert counts.get("LG") == 2
    assert counts.get("Doosan") == 1


def test_venting_event_rejects_invalid_type():
    _enable()
    resp = client.post("/venting/events", json={"event_type": "bogus", "team": "LG"})
    assert resp.status_code == 400
