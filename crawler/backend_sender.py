from datetime import UTC, datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple

import requests


LIVE_STATUS = {"LIVE", "ING", "PLAYING", "IN_PROGRESS"}
FINISHED_STATUS = {"FINISHED", "FINAL", "END", "RESULT"}


def _contains_any(value: str, terms: List[str]) -> bool:
    normalized = (value or "").lower()
    return any(term.lower() in normalized for term in terms)


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def _clamp(value: int, minimum: int, maximum: int) -> int:
    return max(minimum, min(maximum, value))


def _is_base_occupied(value: Any) -> bool:
    raw = str(value).strip().lower()
    if not raw:
        return False
    return raw not in {"0", "false", "none", "null"}


def _normalize_status(raw: Any) -> str:
    value = str(raw or "").strip().upper()
    if value in LIVE_STATUS:
        return "LIVE"
    if value in FINISHED_STATUS:
        return "FINISHED"
    return "SCHEDULED"


def _collect_player_map(relays_by_inning: Dict[int, Dict[str, Any]]) -> Dict[str, str]:
    player_map: Dict[str, str] = {}

    def add_player(player: Optional[Dict[str, Any]]) -> None:
        if not player:
            return
        player_id = str(
            player.get("pcode")
            or player.get("playerId")
            or player.get("player_id")
            or ""
        ).strip()
        player_name = (player.get("name") or player.get("playerName") or "").strip()
        if player_id and player_name:
            player_map[player_id] = player_name

    def add_players(players: Optional[List[Dict[str, Any]]]) -> None:
        for player in players or []:
            add_player(player)

    for inning in sorted(relays_by_inning):
        relay_data = relays_by_inning.get(inning) or {}

        for entry_key in ("homeEntry", "awayEntry", "homeLineup", "awayLineup"):
            entry = relay_data.get(entry_key) or {}
            add_players(entry.get("batter"))
            add_players(entry.get("pitcher"))

        for relay in sorted(relay_data.get("textRelays") or [], key=lambda item: item.get("no", 0)):
            for option in sorted(relay.get("textOptions") or [], key=lambda item: item.get("seqno", 0)):
                batter_record = option.get("batterRecord") or {}
                add_player(
                    {
                        "pcode": batter_record.get("pcode"),
                        "name": batter_record.get("name"),
                    }
                )

                player_change = option.get("playerChange") or {}
                add_player(player_change.get("inPlayer"))
                add_player(player_change.get("outPlayer"))

    return player_map


def _collect_options(relays_by_inning: Dict[int, Dict[str, Any]]) -> List[Tuple[int, str, int, Dict[str, Any]]]:
    options: List[Tuple[int, str, int, Dict[str, Any]]] = []
    for inning in sorted(relays_by_inning):
        relay_data = relays_by_inning.get(inning) or {}
        for relay in sorted(relay_data.get("textRelays") or [], key=lambda item: item.get("no", 0)):
            relay_no = _safe_int(relay.get("no"), default=0)
            half = "top" if str(relay.get("homeOrAway")) == "0" else "bottom"
            for option in sorted(relay.get("textOptions") or [], key=lambda item: item.get("seqno", 0)):
                options.append((inning, half, relay_no, option))
    return options


def _classify_event_type(option: Dict[str, Any]) -> str:
    option_type = _safe_int(option.get("type"), default=-1)
    pitch_result = str(option.get("pitchResult") or "").strip().upper()
    text = (option.get("text") or "").strip()
    has_score = _contains_any(
        text,
        [
            "\ub4dd\uc810",
            "\ud648\uc778",
            "\ud648\uc73c\ub85c",
            "\uc0dd\ud658",
            "\ucd94\uac00\uc810",
            "\ub3d9\uc810",
            "\uc5ed\uc804",
            "scores",
            "scored",
            "score",
        ],
    )
    has_out = _contains_any(text, ["\uc544\uc6c3", "\uc0bc\uc9c4", "\ubcd1\uc0b4", "out", "strikeout", "double play"])
    has_sac_fly = _contains_any(text, ["\ud76c\uc0dd\ud50c\ub77c\uc774", "\ud76c\uc0dd \ud50c\ub77c\uc774", "sacrifice fly", "sac fly"])
    has_tag_up = _contains_any(text, ["\ud0dc\uadf8\uc5c5", "tag up", "tag-up"])
    has_steal = _contains_any(text, ["\ub3c4\ub8e8", "stolen base", "steal"])
    has_steal_fail = _contains_any(
        text,
        [
            "\ub3c4\ub8e8\uc2e4\ud328",
            "caught stealing",
        ],
    )
    has_video_review = _contains_any(
        text,
        [
            "\ube44\ub514\uc624 \ud310\ub3c5",
            "video review",
        ],
    )
    has_walk = _contains_any(
        text,
        [
            "\ubcfc\ub137",
            "\uace0\uc758\uc0ac\uad6c",
            "\uace0\uc758 \uc0ac\uad6c",
            "walk",
            "intentional walk",
        ],
    )
    has_hit_result = _contains_any(
        text,
        [
            "\ub8e8\ud0c0",
            "\uc548\ud0c0",
            "\ub0b4\uc57c\uc548\ud0c0",
            "\ubc88\ud2b8\uc548\ud0c0",
            "\ub2e8\ud0c0",
            "\uc88c\uc548",
            "\uc6b0\uc548",
            "\uc911\uc548",
            "\uc88c\uc911\uc548",
            "\uc6b0\uc911\uc548",
            "single",
            "double",
            "triple",
        ],
    )

    if option_type == 1:
        if pitch_result in {"B"}:
            return "BALL"
        if pitch_result in {"T", "S", "F"}:
            return "STRIKE"
        if pitch_result in {"H"}:
            # "N구 타격"은 타구 발생 이벤트이며, 안타/진루 확정 이벤트가 아님.
            return "OTHER"

    if has_out and has_score and (has_sac_fly or has_tag_up):
        return "SAC_FLY_SCORE"
    if has_tag_up and has_out and not has_score:
        return "TAG_UP_ADVANCE"
    if has_video_review:
        return "OTHER"
    if has_steal and (has_steal_fail or has_out):
        return "OUT"
    if has_steal:
        return "STEAL"
    if has_walk:
        return "WALK"

    if _contains_any(text, ["\ud648\ub7f0", "homerun", "home run"]):
        return "HOMERUN"
    if has_score:
        return "SCORE"
    if has_out:
        return "OUT"
    if has_hit_result and not has_out:
        return "HIT"

    return "OTHER"


def build_snapshot_payload(
    game_data: Dict[str, Any],
    relays_by_inning: Dict[int, Dict[str, Any]],
    observed_at: Optional[datetime] = None,
) -> Dict[str, Any]:
    now = observed_at or datetime.now(UTC)
    if now.tzinfo is None:
        now = now.replace(tzinfo=UTC)
    else:
        now = now.astimezone(UTC)

    options = _collect_options(relays_by_inning)
    player_map = _collect_player_map(relays_by_inning)

    latest_state: Dict[str, Any] = {}
    latest_inning: Optional[int] = None
    latest_half: Optional[str] = None
    for inning, half, _, option in options:
        state = option.get("currentGameState") or {}
        if state:
            latest_state = state
            latest_inning = inning
            latest_half = half

    home_team = (game_data.get("homeTeamName") or game_data.get("homeTeamShortName") or "").strip() or "HOME"
    away_team = (game_data.get("awayTeamName") or game_data.get("awayTeamShortName") or "").strip() or "AWAY"

    pitcher_id = str(latest_state.get("pitcher") or "").strip()
    batter_id = str(latest_state.get("batter") or "").strip()

    pitcher_name = player_map.get(pitcher_id, "")
    if not pitcher_name:
        if latest_half == "top":
            pitcher_name = (game_data.get("homeCurrentPitcherName") or "").strip()
        elif latest_half == "bottom":
            pitcher_name = (game_data.get("awayCurrentPitcherName") or "").strip()

    batter_name = player_map.get(batter_id, "")

    inning_text = (game_data.get("currentInning") or "").strip()
    if not inning_text:
        if latest_inning is not None and latest_half is not None:
            inning_text = f"{latest_inning}{'B' if latest_half == 'bottom' else 'T'}"
        else:
            inning_text = "0T"

    base_time = now - timedelta(milliseconds=len(options))
    events: List[Dict[str, Any]] = []
    for index, (inning, half, relay_no, option) in enumerate(options):
        seqno = _safe_int(option.get("seqno"), default=index)
        option_type = _safe_int(option.get("type"), default=-1)
        pitch_result = str(option.get("pitchResult") or "").strip().upper() or None
        event_time = base_time + timedelta(milliseconds=index)
        event_time_iso = event_time.isoformat().replace("+00:00", "Z")

        metadata: Dict[str, Any] = {
            "inning": inning,
            "half": half,
            "relayNo": relay_no,
            "seqno": seqno,
            "optionType": option_type,
        }
        if pitch_result is not None:
            metadata["pitchResult"] = pitch_result

        events.append(
            {
                "sourceEventId": f"{inning:02d}-{relay_no:03d}-{seqno:04d}",
                "type": _classify_event_type(option),
                "description": (option.get("text") or "").strip(),
                "occurredAt": event_time_iso,
                "metadata": metadata,
            }
        )

    home_score = _safe_int(latest_state.get("homeScore"), default=_safe_int(game_data.get("homeTeamScore")))
    away_score = _safe_int(latest_state.get("awayScore"), default=_safe_int(game_data.get("awayTeamScore")))

    payload: Dict[str, Any] = {
        "homeTeam": home_team,
        "awayTeam": away_team,
        "status": _normalize_status(game_data.get("statusCode")),
        "inning": inning_text,
        "homeScore": _clamp(home_score, 0, 99),
        "awayScore": _clamp(away_score, 0, 99),
        "ball": _clamp(_safe_int(latest_state.get("ball")), 0, 4),
        "strike": _clamp(_safe_int(latest_state.get("strike")), 0, 3),
        "out": _clamp(_safe_int(latest_state.get("out")), 0, 3),
        "bases": {
            "first": _is_base_occupied(latest_state.get("base1")),
            "second": _is_base_occupied(latest_state.get("base2")),
            "third": _is_base_occupied(latest_state.get("base3")),
        },
        "pitcher": pitcher_name or None,
        "batter": batter_name or None,
        "observedAt": now.isoformat().replace("+00:00", "Z"),
        "events": events,
    }
    return payload


def post_snapshot_to_backend(
    *,
    backend_base_url: str,
    api_key: str,
    game_id: str,
    payload: Dict[str, Any],
    timeout: float = 10.0,
) -> Dict[str, Any]:
    endpoint = f"{backend_base_url.rstrip('/')}/internal/crawler/games/{game_id}/snapshot"
    response = requests.post(
        endpoint,
        headers={"X-API-Key": api_key},
        json=payload,
        timeout=timeout,
    )
    response.raise_for_status()
    return response.json()
