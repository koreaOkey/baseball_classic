"""분풀이(venting) 모드 백엔드 Phase 2.

경기 종료(FINISHED) 후 out-of-band 로 패배팀 관점 regret-top5 를 산정한다.
- 규칙/WPA 후보 선별 → (선택) GPT-5.6 Luna 재정렬·사유문구 → 캐시.
- LLM 호출 구간에는 DB 커넥션을 잡지 않는다(풀 고갈 방지). 동시 산정은 세마포어로 제한.
- 캐시에는 역할 레이블·타순·투수 등판순서·이벤트 참조·사유문구만 저장하고 선수 실명은
  저장하지 않는다(실명은 클라이언트가 조회 시점에 박스스코어와 결합해 표시).
- 전부 BASEHAPTIC_VENTING_BACKEND_ENABLED / _LLM_ENABLED 플래그로 게이트(기본 OFF).
"""

from __future__ import annotations

import json
import logging
import re
import threading
from datetime import datetime, timedelta, timezone
from typing import Any

import requests
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .db import SessionLocal
from .models import (
    Game,
    GameBatterStat,
    GameEvent,
    GameLineupSlot,
    GamePitcherStat,
    TeamManager,
    VentingEvent,
    VentingRegretCache,
    VentingTeamDaily,
    VentingTeamSeason,
    utcnow,
)

logger = logging.getLogger(__name__)
settings = get_settings()

KST = timezone(timedelta(hours=9))
FINISHED_STATUS = "FINISHED"

VALID_VENTING_EVENT_TYPES = {"room_enter", "destroy_complete", "retry_prompt_shown", "retry_ad_start"}
VALID_ENTRY_SOURCES = {"home_card", "live_button", "loss_prompt"}
VALID_PLATFORMS = {"ios", "android", "unknown"}

_MAX_CANDIDATES = 15
_TOP_N = 5

# 이벤트 타입별 기본 심각도 가중치 (WPA 부재 시 폴백 근거). WPA 가 있으면 함께 반영.
_OFFENSE_FAIL_WEIGHTS = {"TRIPLE_PLAY": 2.5, "DOUBLE_PLAY": 2.0, "OUT": 1.0}
_DEFENSE_CONCEDE_WEIGHTS = {"SCORE": 3.0, "SAC_FLY_SCORE": 3.0, "HOMERUN": 3.0}
# 개인 수비 실책 — 비자책 실점의 원흉이라 병살(2.0)보다 무겁고 실점(3.0)보다 살짝 낮게.
_DEFENSE_ERROR_WEIGHT = 2.5
_OUT_INCLUDE_WPA_THRESHOLD = 0.05  # 삼진이 아니어도 고레버리지(찬스 무산) 아웃이면 후보

_INNING_RE = re.compile(r"(\d+)\s*회\s*(초|말)")

# 동시 regret 산정 상한 — 버스트 종료(다수 경기 동시 FINISHED) 시 LLM/DB 폭주 방지.
_regret_semaphore = threading.Semaphore(max(1, settings.venting_llm_max_concurrency))

_LLM_SYSTEM_PROMPT = (
    "너는 KBO 경기 기록에서 패배팀 팬이 가장 아쉬워할 순간을 고르는 편집자다. "
    "반드시 주어진 candidates 목록 안에서만 최대 5개를 심각도(승패 영향) 순으로 고른다. "
    "목록에 없는 플레이를 지어내지 않는다. 각 항목에는 '상황'을 한 문장으로 쓴다. "
    "사람을 평가·비난하지 말고 상황만 담담히 묘사한다(예: '2사 만루에서 병살로 추격 흐름이 끊겼다'). "
    "선수 실명·등번호를 절대 쓰지 말고 주어진 역할 레이블(예: '5번 타자', '선발 투수', '유격수')만 쓴다. "
    '반드시 JSON 오브젝트로만 답한다: {"items":[{"index":<candidates의 index>,"reason":"<상황 한 문장>"}]}'
)


# --------------------------------------------------------------------------- #
# regret-top5 산정 (out-of-band)
# --------------------------------------------------------------------------- #


def _losing_side_and_team(game: Game) -> tuple[str | None, str | None]:
    """패배 side/team 반환. 무승부는 (None, None)."""
    if game.home_score > game.away_score:
        return "away", game.away_team
    if game.away_score > game.home_score:
        return "home", game.home_team
    return None, None


def _parse_inning(inning: str | None) -> tuple[int | None, str | None]:
    if not inning:
        return None, None
    m = _INNING_RE.search(inning)
    if not m:
        return None, None
    return int(m.group(1)), m.group(2)


def _event_wpa(event: GameEvent) -> float:
    payload = event.payload_json if isinstance(event.payload_json, dict) else {}
    try:
        return abs(float(payload.get("wpaByPlate"))) if payload.get("wpaByPlate") is not None else 0.0
    except (TypeError, ValueError):
        return 0.0


def _event_situation(event: GameEvent) -> dict[str, Any]:
    """LLM 컨텍스트용 상황 요약(실명 없음)."""
    payload = event.payload_json if isinstance(event.payload_json, dict) else {}
    return {
        "outs": payload.get("outAfter"),
        "bases": {
            "1": bool(payload.get("base1After")) if "base1After" in payload else None,
            "2": bool(payload.get("base2After")) if "base2After" in payload else None,
            "3": bool(payload.get("base3After")) if "base3After" in payload else None,
        },
    }


def _batting_order_label(db: Session, game_id: str, team_side: str, batter_name: str | None) -> tuple[int | None, str]:
    if batter_name:
        order = db.execute(
            select(GameBatterStat.batting_order)
            .where(
                GameBatterStat.game_id == game_id,
                GameBatterStat.team_side == team_side,
                GameBatterStat.player_name == batter_name,
            )
            .limit(1)
        ).scalar_one_or_none()
        if order:
            return int(order), f"{int(order)}번 타자"
    return None, "타자"


def _pitcher_role_label(db: Session, game_id: str, team_side: str, pitcher_name: str | None) -> tuple[int | None, str]:
    if pitcher_name:
        row = db.execute(
            select(GamePitcherStat.appearance_order, GamePitcherStat.is_starter)
            .where(
                GamePitcherStat.game_id == game_id,
                GamePitcherStat.team_side == team_side,
                GamePitcherStat.player_name == pitcher_name,
            )
            .limit(1)
        ).first()
        if row is not None:
            appearance_order, is_starter = row
            if is_starter or appearance_order == 1:
                return (appearance_order or 1), "선발 투수"
            return appearance_order, "구원 투수"
    return None, "투수"


def _fielder_order_label(
    db: Session,
    game_id: str,
    team_side: str,
    position_name: str | None,
    event_cursor: int | None,
) -> tuple[int | None, str]:
    """수비 실책 후보의 역할 레이블(포지션명)과 타순을 해소한다(실명 미저장).

    라인업(game_lineup_slots)에서 그 팀·그 포지션 슬롯의 타순을 찾아 반환한다. 타순을
    실으면 클라이언트가 기존 batter 경로(박스스코어 타순 조인)로 실명을 표시한다. 교체로
    같은 포지션에 복수 슬롯이 있으면 실책 이벤트 cursor 시점에 활성인 슬롯을 우선한다.
    포지션 미상이면 ('수비수', 타순 없음)으로 익명 유지.
    """
    if not position_name:
        return None, "수비수"
    rows = db.execute(
        select(
            GameLineupSlot.batting_order,
            GameLineupSlot.entered_at_event_cursor,
            GameLineupSlot.exited_at_event_cursor,
        ).where(
            GameLineupSlot.game_id == game_id,
            GameLineupSlot.team_side == team_side,
            GameLineupSlot.position_name == position_name,
        )
    ).all()
    if not rows:
        return None, position_name

    chosen: int | None = None
    if event_cursor is not None:
        for order, entered, exited in rows:
            if (entered is None or entered <= event_cursor) and (exited is None or event_cursor < exited):
                chosen = order
                break
    if chosen is None:
        chosen = rows[0][0]
    return (int(chosen) if chosen is not None else None), position_name


def _select_candidates(db: Session, game: Game, losing_side: str) -> list[dict[str, Any]]:
    """패배팀의 아쉬운 순간 후보를 규칙/WPA 로 선별(역할 레이블만, 실명 미저장).

    - 공격 실패(타자): 병살/삼중살, 삼진 또는 고레버리지 아웃 → 타순 레이블
    - 수비 실점(투수): 실점/희플/피홈런 → 선발/구원 투수 레이블
    """
    events = db.execute(
        select(GameEvent).where(GameEvent.game_id == game.id).order_by(GameEvent.cursor.asc())
    ).scalars().all()

    by_role: dict[str, dict[str, Any]] = {}

    for event in events:
        _, half = _parse_inning(event.inning)
        if half is None:
            continue
        batting_side = "away" if half == "초" else "home"
        pitching_side = "home" if half == "초" else "away"
        etype = (event.event_type or "").upper()
        desc = event.description or ""
        wpa = _event_wpa(event)
        payload = event.payload_json if isinstance(event.payload_json, dict) else {}
        is_error = bool(payload.get("isError"))

        candidate: dict[str, Any] | None = None

        # 수비 실책 (수비 팀 == 패배팀) — 실점 여부와 무관하게 실책은 실책 담당(포지션)에게 귀속.
        # SCORE 로 분류된 실책성 실점도 여기서 fielder 로 먼저 잡아 투수 귀속보다 우선한다.
        if pitching_side == losing_side and is_error:
            order, label = _fielder_order_label(
                db, game.id, pitching_side, payload.get("errorPosition"), event.cursor
            )
            candidate = {
                "kind": "fielder",
                "team_side": pitching_side,
                "batting_order": order,
                "appearance_order": None,
                "role_label": label,
                "inning": event.inning,
                "event_type": "ERROR",
                "situation": _event_situation(event),
                "severity": round(_DEFENSE_ERROR_WEIGHT + wpa * 10.0, 4),
            }

        # 공격 실패 (배팅 팀 == 패배팀)
        elif batting_side == losing_side and etype in _OFFENSE_FAIL_WEIGHTS:
            include = etype in ("DOUBLE_PLAY", "TRIPLE_PLAY") or "삼진" in desc or wpa >= _OUT_INCLUDE_WPA_THRESHOLD
            if include:
                order, label = _batting_order_label(db, game.id, batting_side, event.batter)
                base_weight = _OFFENSE_FAIL_WEIGHTS[etype]
                candidate = {
                    "kind": "batter",
                    "team_side": batting_side,
                    "batting_order": order,
                    "appearance_order": None,
                    "role_label": label,
                    "inning": event.inning,
                    "event_type": etype,
                    "situation": _event_situation(event),
                    "severity": round(base_weight + wpa * 10.0, 4),
                }

        # 수비 실점 (피칭 팀 == 패배팀)
        elif pitching_side == losing_side and etype in _DEFENSE_CONCEDE_WEIGHTS:
            appearance_order, label = _pitcher_role_label(db, game.id, pitching_side, event.pitcher)
            base_weight = _DEFENSE_CONCEDE_WEIGHTS[etype]
            candidate = {
                "kind": "pitcher",
                "team_side": pitching_side,
                "batting_order": None,
                "appearance_order": appearance_order,
                "role_label": label,
                "inning": event.inning,
                "event_type": etype,
                "situation": _event_situation(event),
                "severity": round(base_weight + wpa * 10.0, 4),
            }

        if candidate is None:
            continue

        # 역할 레이블 중복은 최고 심각도 1건만 유지(경기 전체 '패배 지분' 근사)
        key = f"{candidate['kind']}:{candidate['team_side']}:{candidate['role_label']}"
        prev = by_role.get(key)
        if prev is None or candidate["severity"] > prev["severity"]:
            by_role[key] = candidate

    ranked = sorted(by_role.values(), key=lambda c: c["severity"], reverse=True)
    return ranked[:_MAX_CANDIDATES]


def _fallback_reason(candidate: dict[str, Any]) -> str:
    label_map = {
        "DOUBLE_PLAY": "병살",
        "TRIPLE_PLAY": "삼중살",
        "OUT": "삼진" if candidate.get("event_type") == "OUT" else "아웃",
        "SCORE": "실점",
        "SAC_FLY_SCORE": "희생플라이 실점",
        "HOMERUN": "피홈런",
        "ERROR": "실책",
    }
    label = label_map.get(candidate.get("event_type", ""), "아쉬운 장면")
    inning = candidate.get("inning") or ""
    return f"{inning} {label}".strip()


def _finalize_item(candidate: dict[str, Any], reason: str) -> dict[str, Any]:
    """캐시 저장용 item — 실명 없이 역할·참조·사유만."""
    return {
        "kind": candidate["kind"],
        "team_side": candidate["team_side"],
        "batting_order": candidate.get("batting_order"),
        "appearance_order": candidate.get("appearance_order"),
        "role_label": candidate["role_label"],
        "inning": candidate.get("inning"),
        "event_type": candidate.get("event_type"),
        "reason": reason,
    }


def _rule_fallback_items(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [_finalize_item(c, _fallback_reason(c)) for c in candidates[:_TOP_N]]


def _call_llm(candidates: list[dict[str, Any]]) -> list[dict[str, Any]] | None:
    """GPT-5.6 Luna(OpenAI 호환)로 후보 안에서 TOP5 재정렬 + 사유 생성.

    후보 밖 index 는 무시(창작 방지). 실패 시 None → 규칙 폴백.
    DB 커넥션을 잡지 않은 상태에서 호출된다.
    """
    llm_candidates = [
        {
            "index": i,
            "role": c["role_label"],
            "inning": c.get("inning"),
            "event": c.get("event_type"),
            "situation": c.get("situation"),
        }
        for i, c in enumerate(candidates)
    ]
    body = {
        "model": settings.venting_llm_model,
        "messages": [
            {"role": "system", "content": _LLM_SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps({"candidates": llm_candidates}, ensure_ascii=False)},
        ],
        # gpt-5 계열(gpt-5.6-luna 등)은 temperature 기본값(1)만 허용 — 0.4 지정 시 400.
        # 기본값 사용을 위해 temperature 미지정.
        "response_format": {"type": "json_object"},
    }
    url = settings.venting_llm_base_url.rstrip("/") + "/chat/completions"
    headers = {
        "Authorization": f"Bearer {settings.venting_llm_api_key}",
        "Content-Type": "application/json",
    }
    try:
        resp = requests.post(url, headers=headers, json=body, timeout=max(1, settings.venting_llm_timeout_sec))
        resp.raise_for_status()
        content = resp.json()["choices"][0]["message"]["content"]
        parsed = json.loads(content)
    except (requests.RequestException, KeyError, ValueError, TypeError) as exc:
        logger.warning("[venting] LLM regret ranking failed err=%s", exc)
        return None

    raw_items = parsed.get("items") if isinstance(parsed, dict) else None
    if not isinstance(raw_items, list):
        logger.warning("[venting] LLM response missing items[]")
        return None

    items: list[dict[str, Any]] = []
    seen: set[int] = set()
    for raw in raw_items:
        if not isinstance(raw, dict):
            continue
        idx = raw.get("index")
        if not isinstance(idx, int) or idx < 0 or idx >= len(candidates) or idx in seen:
            continue  # 후보 밖/중복 무시 → 창작 차단
        seen.add(idx)
        reason = str(raw.get("reason") or "").strip() or _fallback_reason(candidates[idx])
        items.append(_finalize_item(candidates[idx], reason))
        if len(items) >= _TOP_N:
            break

    return items or None


def _current_manager(db: Session, team_code: str | None) -> str | None:
    if not team_code:
        return None
    return db.execute(
        select(TeamManager.manager_name)
        .where(TeamManager.team_code == team_code, TeamManager.effective_to.is_(None))
        .order_by(TeamManager.created_at.desc())
        .limit(1)
    ).scalar_one_or_none()


def _upsert_regret_cache(
    db: Session,
    *,
    game_id: str,
    team_code: str,
    items: list[dict[str, Any]],
    manager_name: str | None,
    source: str,
) -> None:
    existing = db.execute(
        select(VentingRegretCache).where(
            VentingRegretCache.game_id == game_id,
            VentingRegretCache.team_code == team_code,
        )
    ).scalars().first()
    now = utcnow()
    if existing is None:
        db.add(
            VentingRegretCache(
                game_id=game_id,
                team_code=team_code,
                items=items,
                manager_name=manager_name,
                source=source,
                computed_at=now,
            )
        )
    else:
        existing.items = items
        existing.manager_name = manager_name
        existing.source = source
        existing.computed_at = now


def compute_regret_for_game(game_id: str) -> None:
    """경기 종료 후 regret-top5 산정 진입점(백그라운드 태스크에서 호출).

    실패해도 예외를 전파하지 않는다(다른 처리 비차단). 플래그 OFF 시 즉시 반환.
    """
    if not settings.venting_backend_enabled:
        return

    acquired = _regret_semaphore.acquire(timeout=max(1, settings.venting_llm_timeout_sec) + 5)
    if not acquired:
        logger.warning("[venting] regret compute skipped (concurrency saturated) game_id=%s", game_id)
        return
    try:
        # Phase 1: 데이터 조회 (짧은 세션) → 세션 닫음
        with SessionLocal() as db:
            game = db.get(Game, game_id)
            if game is None or (game.status or "").upper() != FINISHED_STATUS:
                return
            losing_side, losing_team = _losing_side_and_team(game)
            if losing_side is None or not losing_team:
                return  # 무승부
            already = db.execute(
                select(VentingRegretCache.id).where(
                    VentingRegretCache.game_id == game_id,
                    VentingRegretCache.team_code == losing_team,
                ).limit(1)
            ).first()
            if already is not None:
                return  # 재산정 방지
            candidates = _select_candidates(db, game, losing_side)
            manager_name = _current_manager(db, losing_team)

        # Phase 2: LLM 호출 (DB 커넥션 비점유)
        source = "rule_fallback"
        items: list[dict[str, Any]]
        if not candidates:
            items = []
        else:
            llm_items = None
            if settings.venting_llm_enabled and settings.venting_llm_api_key and settings.venting_llm_model:
                llm_items = _call_llm(candidates)
            if llm_items is not None:
                items, source = llm_items, "llm"
            else:
                items = _rule_fallback_items(candidates)

        # Phase 3: 결과 저장 (짧은 세션)
        with SessionLocal() as db:
            _upsert_regret_cache(
                db,
                game_id=game_id,
                team_code=losing_team,
                items=items,
                manager_name=manager_name,
                source=source,
            )
            db.commit()
        logger.info("[venting] regret computed game_id=%s team=%s items=%d source=%s", game_id, losing_team, len(items), source)
    except Exception:  # noqa: BLE001 — 백그라운드 태스크는 절대 예외를 전파하지 않는다
        logger.exception("[venting] regret compute failed game_id=%s", game_id)
    finally:
        _regret_semaphore.release()


def compute_regret_for_game_background(game_id: str) -> None:
    """BackgroundTasks 진입점 — 자체 세션/예외 격리."""
    compute_regret_for_game(game_id)


def get_regret_cache(db: Session, game_id: str) -> VentingRegretCache | None:
    return db.execute(
        select(VentingRegretCache).where(VentingRegretCache.game_id == game_id).limit(1)
    ).scalars().first()


# --------------------------------------------------------------------------- #
# 지표 수집 + 팀 랭킹 집계
# --------------------------------------------------------------------------- #


def _increment_team_count(db: Session, model: type, keys: dict[str, Any]) -> None:
    """팀 집계 카운트를 원자적 upsert 로 +1 (체크인 집계와 동일 패턴)."""
    dialect = db.get_bind().dialect.name
    if dialect == "postgresql":
        from sqlalchemy.dialects.postgresql import insert as dialect_insert
    else:
        from sqlalchemy.dialects.sqlite import insert as dialect_insert

    table = model.__table__
    stmt = dialect_insert(model).values(**keys, count=1, updated_at=utcnow())
    stmt = stmt.on_conflict_do_update(
        index_elements=list(keys.keys()),
        set_={"count": table.c.count + 1, "updated_at": utcnow()},
    )
    db.execute(stmt)


# 팬 응원팀 표기 정규화 — 클라이언트마다 다른 표기(iOS kboTeamId 코드 "HH" vs
# Android enum명 "HANWHA", 혹은 한글 라벨)를 하나의 표준(enum명)으로 모아 팀 랭킹이
# 같은 팬덤인데도 쪼개지지 않게 한다. 표준형은 이미 배포된 Android 표기(enum명).
_TEAM_CANONICAL = {
    # iOS kboTeamId 코드 → 표준(enum명)
    "OB": "DOOSAN", "WO": "KIWOOM", "SS": "SAMSUNG", "LT": "LOTTE",
    "SK": "SSG", "HH": "HANWHA", "HT": "KIA",
    # 한글 라벨 → 표준
    "두산": "DOOSAN", "키움": "KIWOOM", "삼성": "SAMSUNG", "롯데": "LOTTE", "한화": "HANWHA",
    # LG/KT/NC/SSG/KIA 등 공통 코드·enum명은 그대로(하단 passthrough).
}


def _canonical_team(team: str) -> str:
    """iOS/Android 팬팀 표기를 표준(enum명)으로 정규화. 미매핑은 대문자 그대로."""
    t = (team or "").strip()
    if t in _TEAM_CANONICAL:
        return _TEAM_CANONICAL[t]
    up = t.upper()
    return _TEAM_CANONICAL.get(up, up)


def record_venting_event(
    db: Session,
    *,
    event_type: str,
    team: str,
    entry_source: str | None = None,
    game_id: str | None = None,
    user_id: str | None = None,
    client_ts: datetime | None = None,
    platform: str = "unknown",
) -> VentingEvent:
    """지표 이벤트 저장 + room_enter 는 팀 랭킹 집계에 반영."""
    team = _canonical_team(team)
    normalized_platform = platform if platform in VALID_PLATFORMS else "unknown"
    normalized_source = entry_source if entry_source in VALID_ENTRY_SOURCES else None
    event = VentingEvent(
        event_type=event_type,
        entry_source=normalized_source,
        team=team,
        game_id=game_id,
        user_id=user_id,
        client_ts=client_ts,
        platform=normalized_platform,
    )
    db.add(event)
    db.flush()

    # "분풀이 방을 실행" = room_enter 기준으로 팀 랭킹 집계
    if event_type == "room_enter":
        ref = (client_ts or utcnow()).astimezone(KST)
        date_key = ref.date().isoformat()
        season = str(ref.year)
        _increment_team_count(db, VentingTeamDaily, {"team": team, "date": date_key})
        _increment_team_count(db, VentingTeamSeason, {"team": team, "season": season})
    return event


def get_team_season_ranking(db: Session, season: str) -> list[dict[str, Any]]:
    rows = db.execute(
        select(VentingTeamSeason.team, VentingTeamSeason.count)
        .where(VentingTeamSeason.season == season)
        .order_by(VentingTeamSeason.count.desc())
    ).all()
    return [{"team": team, "count": count} for team, count in rows]
