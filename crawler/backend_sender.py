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
            "득점",
            "홈인",
            "홈으로",
            "생환",
            "추가점",
            "동점",
            "역전",
            "scores",
            "scored",
            "score",
        ],
    )
    has_out = _contains_any(text, ["아웃", "삼진", "병살", "out", "strikeout", "double play"])
    has_double_play = _contains_any(text, ["병살", "double play"])
    has_triple_play = _contains_any(text, ["삼중살", "triple play"])
    has_sac_fly = _contains_any(text, ["희생플라이", "희생 플라이", "sacrifice fly", "sac fly"])
    has_tag_up = _contains_any(text, ["태그업", "tag up", "tag-up"])
    has_steal = _contains_any(text, ["도루", "stolen base", "steal"])
    has_steal_fail = _contains_any(
        text,
        [
            "도루실패",
            "caught stealing",
        ],
    )
    has_video_review = _contains_any(
        text,
        [
            "비디오 판독",
            "video review",
        ],
    )
    has_walk = _contains_any(
        text,
        [
            "볼넷",
            "고의사구",
            "고의 사구",
            "walk",
            "intentional walk",
        ],
    )
    has_hit_result = _contains_any(
        text,
        [
            "루타",
            "안타",
            "내야안타",
            "번트안타",
            "단타",
            "좌안",
            "우안",
            "중안",
            "좌중안",
            "우중안",
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
            return "OTHER"

    if has_triple_play:
        return "TRIPLE_PLAY"
    if has_double_play:
        return "DOUBLE_PLAY"

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

    if _contains_any(text, ["홈런", "homerun", "home run"]):
        return "HOMERUN"
    if has_score:
        return "SCORE"
    if has_out:
        return "OUT"
    if has_hit_result and not has_out:
        return "HIT"

    return "OTHER"


def _get_str(value: Any) -> str:
    return str(value or "").strip()


def _pick_str(data: Dict[str, Any], keys: List[str]) -> str:
    for key in keys:
        value = _get_str(data.get(key))
        if value:
            return value
    return ""


def _pick_int(data: Dict[str, Any], keys: List[str], default: int = 0) -> int:
    for key in keys:
        value = data.get(key)
        if value is None:
            continue
        parsed = _safe_int(value, default=-1)
        if parsed >= 0:
            return parsed
    return default


def _pick_bool(data: Dict[str, Any], keys: List[str], default: bool) -> bool:
    for key in keys:
        value = data.get(key)
        if isinstance(value, bool):
            return value
        raw = _get_str(value).lower()
        if raw in {"true", "1", "y", "yes"}:
            return True
        if raw in {"false", "0", "n", "no"}:
            return False
    return default


def _parse_ip_to_outs(raw_value: Any) -> int:
    raw = _get_str(raw_value)
    if not raw:
        return 0
    if raw.isdigit():
        return _safe_int(raw) * 3

    if "." in raw:
        whole, frac = raw.split(".", 1)
        outs = _safe_int(whole, 0) * 3
        if frac == "1":
            outs += 1
        elif frac == "2":
            outs += 2
        return outs

    return 0


def _extract_latest_entry(relays_by_inning: Dict[int, Dict[str, Any]], key: str) -> Dict[str, Any]:
    for inning in sorted(relays_by_inning.keys(), reverse=True):
        entry = (relays_by_inning.get(inning) or {}).get(key)
        if isinstance(entry, dict) and entry:
            return entry
    return {}


def _extract_lineup_and_boxscore(
    relays_by_inning: Dict[int, Dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    lineup_slots: list[dict[str, Any]] = []
    batter_stats: list[dict[str, Any]] = []
    pitcher_stats: list[dict[str, Any]] = []

    lineup_entries = {
        "home": _extract_latest_entry(relays_by_inning, "homeLineup"),
        "away": _extract_latest_entry(relays_by_inning, "awayLineup"),
    }
    entry_entries = {
        "home": _extract_latest_entry(relays_by_inning, "homeEntry"),
        "away": _extract_latest_entry(relays_by_inning, "awayEntry"),
    }

    for team_side in ("home", "away"):
        lineup_entry = lineup_entries.get(team_side) or {}
        entry = entry_entries.get(team_side) or {}
        # Prefer lineup because it typically carries full per-player boxscore.
        batters = lineup_entry.get("batter") or entry.get("batter") or []
        pitchers = lineup_entry.get("pitcher") or entry.get("pitcher") or []

        for index, batter in enumerate(batters):
            name = _pick_str(batter, ["name", "playerName"])
            if not name:
                continue

            player_id = _pick_str(batter, ["pcode", "playerId", "player_id"]) or None
            batting_order_raw = _pick_int(
                batter,
                ["batOrder", "battingOrder", "turn", "order", "bo", "seq"],
                default=index + 1,
            )
            batting_order = batting_order_raw if 1 <= batting_order_raw <= 9 else None
            position_code = _pick_str(batter, ["pos", "positionCode", "playerPos"]) or None
            position_name = _pick_str(batter, ["posName", "positionName", "playerPosName"]) or None
            is_starter = _pick_bool(batter, ["starter", "isStarter", "start"], default=index < 9)
            is_active = _pick_bool(batter, ["isActive", "active", "inLineup"], default=True)

            if batting_order is not None:
                lineup_slots.append(
                    {
                        "teamSide": team_side,
                        "battingOrder": batting_order,
                        "playerId": player_id,
                        "playerName": name,
                        "positionCode": position_code,
                        "positionName": position_name,
                        "isStarter": is_starter,
                        "isActive": is_active,
                    }
                )

            hits = _pick_int(batter, ["h", "hit", "hits"])
            home_runs = _pick_int(batter, ["hr", "homeRun", "homerun", "homeRuns"])
            at_bats = _pick_int(batter, ["ab", "atBat", "atBats"])
            walks = _pick_int(batter, ["bb", "walk", "walks", "baseOnBalls"])
            hit_by_pitch = _pick_int(batter, ["hbp", "hitByPitch"])
            sac_bunts = _pick_int(batter, ["sh", "sacBunt", "sacBunts"])
            sac_flies = _pick_int(batter, ["sf", "sacFly", "sacFlies"])
            plate_appearances = _pick_int(
                batter,
                ["pa", "plateAppearance", "plateAppearances"],
                default=at_bats + walks + hit_by_pitch + sac_bunts + sac_flies,
            )

            batter_stats.append(
                {
                    "teamSide": team_side,
                    "playerId": player_id,
                    "playerName": name,
                    "battingOrder": batting_order,
                    "primaryPosition": position_name,
                    "isStarter": is_starter,
                    "plateAppearances": plate_appearances,
                    "atBats": at_bats,
                    "runs": _pick_int(batter, ["r", "run", "runs"]),
                    "hits": hits,
                    "rbi": _pick_int(batter, ["rbi"]),
                    "doubles": _pick_int(batter, ["h2", "double", "doubles"]),
                    "triples": _pick_int(batter, ["h3", "triple", "triples"]),
                    "homeRuns": home_runs,
                    "walks": walks,
                    "strikeouts": _pick_int(batter, ["so", "kk", "strikeout", "strikeouts"]),
                    "stolenBases": _pick_int(batter, ["sb", "stolenBase", "stolenBases"]),
                    "caughtStealing": _pick_int(batter, ["cs", "caughtStealing"]),
                    "hitByPitch": hit_by_pitch,
                    "sacBunts": sac_bunts,
                    "sacFlies": sac_flies,
                    "leftOnBase": _pick_int(batter, ["lob", "leftOnBase"]),
                }
            )

        for index, pitcher in enumerate(pitchers):
            name = _pick_str(pitcher, ["name", "playerName"])
            if not name:
                continue

            player_id = _pick_str(pitcher, ["pcode", "playerId", "player_id"]) or None
            appearance_order = _pick_int(pitcher, ["appearanceOrder", "seq", "order"], default=index + 1)
            is_starter = _pick_bool(pitcher, ["starter", "isStarter", "start"], default=index == 0)
            outs_recorded = _pick_int(pitcher, ["outsRecorded", "outs"], default=0)
            if outs_recorded == 0:
                outs_recorded = _parse_ip_to_outs(_pick_str(pitcher, ["ip", "inn", "inning"]))

            pitcher_stats.append(
                {
                    "teamSide": team_side,
                    "appearanceOrder": appearance_order,
                    "playerId": player_id,
                    "playerName": name,
                    "isStarter": is_starter,
                    "outsRecorded": outs_recorded,
                    "hitsAllowed": _pick_int(pitcher, ["h", "hit", "hits", "hitsAllowed"]),
                    "runsAllowed": _pick_int(pitcher, ["r", "run", "runs", "runsAllowed"]),
                    "earnedRuns": _pick_int(pitcher, ["er", "earnedRun", "earnedRuns"]),
                    "walksAllowed": _pick_int(pitcher, ["bb", "walk", "walks", "walksAllowed"]),
                    "strikeouts": _pick_int(pitcher, ["so", "kk", "strikeout", "strikeouts"]),
                    "homeRunsAllowed": _pick_int(pitcher, ["hr", "homeRun", "homeRuns", "homeRunsAllowed"]),
                    "battersFaced": _pick_int(pitcher, ["bf", "tb", "battersFaced"]),
                    "atBatsAgainst": _pick_int(pitcher, ["ab", "atBatsAgainst"]),
                    "pitchesThrown": _pick_int(pitcher, ["np", "pc", "pitchCount", "pitchesThrown"]),
                }
            )

    return lineup_slots, batter_stats, pitcher_stats


def _summary_from_batter_stats(batter_stats: list[dict[str, Any]]) -> dict[str, int]:
    summary = {
        "homeHits": 0,
        "awayHits": 0,
        "homeHomeRuns": 0,
        "awayHomeRuns": 0,
    }
    for stat in batter_stats:
        team_side = stat.get("teamSide")
        if team_side not in {"home", "away"}:
            continue
        summary[f"{team_side}Hits"] += _safe_int(stat.get("hits"), 0)
        summary[f"{team_side}HomeRuns"] += _safe_int(stat.get("homeRuns"), 0)
    return summary


def _summary_from_events(events: list[dict[str, Any]]) -> dict[str, int]:
    summary = {
        "homeHits": 0,
        "awayHits": 0,
        "homeHomeRuns": 0,
        "awayHomeRuns": 0,
        "homeOutsTotal": 0,
        "awayOutsTotal": 0,
    }
    for event in events:
        metadata = event.get("metadata") or {}
        half = metadata.get("half")
        side = "away" if half == "top" else "home" if half == "bottom" else None
        if side is None:
            continue

        event_type = event.get("type")
        if event_type in {"HIT", "HOMERUN"}:
            summary[f"{side}Hits"] += 1
        if event_type == "HOMERUN":
            summary[f"{side}HomeRuns"] += 1
        desc = str(event.get("description") or "").lower()
        out_delta = 0
        if event_type == "TRIPLE_PLAY" or "삼중살" in desc or "triple play" in desc:
            out_delta = 3
        elif event_type == "DOUBLE_PLAY" or "병살" in desc or "double play" in desc:
            out_delta = 2
        elif event_type in {"OUT", "SAC_FLY_SCORE", "TAG_UP_ADVANCE"}:
            out_delta = 1
        summary[f"{side}OutsTotal"] += out_delta
    return summary


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

    lineup_slots, batter_stats, pitcher_stats = _extract_lineup_and_boxscore(relays_by_inning)

    summary = _summary_from_events(events)
    if batter_stats:
        batter_summary = _summary_from_batter_stats(batter_stats)
        summary["homeHits"] = batter_summary["homeHits"]
        summary["awayHits"] = batter_summary["awayHits"]
        summary["homeHomeRuns"] = batter_summary["homeHomeRuns"]
        summary["awayHomeRuns"] = batter_summary["awayHomeRuns"]

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
        "homeHits": summary["homeHits"],
        "awayHits": summary["awayHits"],
        "homeHomeRuns": summary["homeHomeRuns"],
        "awayHomeRuns": summary["awayHomeRuns"],
        "homeOutsTotal": summary["homeOutsTotal"],
        "awayOutsTotal": summary["awayOutsTotal"],
        "observedAt": now.isoformat().replace("+00:00", "Z"),
        "events": events,
        "lineupSlots": lineup_slots,
        "batterStats": batter_stats,
        "pitcherStats": pitcher_stats,
        "notes": [],
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
