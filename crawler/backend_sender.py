from datetime import UTC, datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple

import requests


LIVE_STATUS = {"LIVE", "ING", "PLAYING", "IN_PROGRESS", "STARTED"}
FINISHED_STATUS = {"FINISHED", "FINAL", "END", "ENDED", "RESULT"}
CANCELED_STATUS = {"CANCELED", "CANCELLED", "CANCEL", "RAIN_CANCEL", "NO_GAME"}
POSTPONED_STATUS = {"POSTPONED", "PPD", "SUSPENDED", "DELAYED"}


def _contains_any(value: str, terms: List[str]) -> bool:
    normalized = (value or "").lower()
    return any(term.lower() in normalized for term in terms)


def _video_review_result_is_out(text: str) -> bool:
    compact = "".join((text or "").lower().split())

    for marker in ("→", "->", "⇒", "=>"):
        if marker not in compact:
            continue
        verdict = compact.rsplit(marker, 1)[1]
        if "아웃" in verdict or "out" in verdict:
            return True

    return _contains_any(
        compact,
        [
            "아웃으로번복",
            "아웃으로정정",
            "아웃판정유지",
            "판정아웃",
            "callstandsasout",
            "callreversedtoout",
            "reviewconfirmedout",
        ],
    )


def _is_pitcher_change(option: Dict[str, Any], option_type: int, text: str) -> bool:
    if option_type != 2:
        return False

    player_change = option.get("playerChange") or {}
    if player_change:
        live_text = (player_change.get("liveText") or text or "").strip()
        in_player = player_change.get("inPlayer") or {}
        out_player = player_change.get("outPlayer") or {}
        in_pos = (in_player.get("playerPos") or "").strip()
        out_pos = (out_player.get("playerPos") or "").strip()

        if (
            _contains_any(live_text, ["투수", "pitcher"])
            or _contains_any(in_pos, ["투수", "pitcher"])
            or _contains_any(out_pos, ["투수", "pitcher"])
        ):
            return True

    return _contains_any(text, ["투수", "pitcher"]) and _contains_any(text, ["교체", "change"])


def _is_half_inning_change(text: str) -> bool:
    normalized = (text or "").strip().lower()
    if not normalized:
        return False

    has_offense_token = _contains_any(
        normalized,
        [
            "\uacf5\uaca9",
            "attack",
            "offense",
            "batting",
        ],
    )
    has_half_token = _contains_any(
        normalized,
        [
            "\ud68c\ucd08",
            "\ud68c\ub9d0",
            "top",
            "bottom",
            "inning",
            "\ucd08 \uacf5\uaca9",
            "\ub9d0 \uacf5\uaca9",
        ],
    )
    if has_offense_token and has_half_token:
        return True

    return _contains_any(
        normalized,
        [
            "inning change",
            "switch sides",
            "change offense",
        ],
    )


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


def _base_runner_name(value: Any, player_map: Dict[str, str]) -> Optional[str]:
    if not _is_base_occupied(value):
        return None
    if isinstance(value, dict):
        for key in ("name", "playerName", "player_name"):
            name = str(value.get(key) or "").strip()
            if name:
                return name
        for key in ("pcode", "playerId", "player_id", "id"):
            player_id = str(value.get(key) or "").strip()
            if player_id and player_map.get(player_id):
                return player_map[player_id]
        return None

    raw = str(value).strip()
    if raw in {"1", "true", "True"}:
        return None
    return player_map.get(raw)


def _normalize_status(raw: Any, status_info: str | None = None) -> str:
    value = str(raw or "").strip().upper()
    if value in LIVE_STATUS:
        return "LIVE"
    if value in FINISHED_STATUS:
        return "FINISHED"
    if value in CANCELED_STATUS:
        return "CANCELED"
    if value in POSTPONED_STATUS:
        return "POSTPONED"

    # statusCode가 매핑되지 않는 경우, statusInfo 텍스트로 취소/연기 판별
    info = str(status_info or "").strip()
    if info:
        info_upper = info.upper()
        if any(kw in info for kw in ("우천취소", "우천 취소", "경기취소", "경기 취소", "노게임")) or \
           any(kw in info_upper for kw in ("CANCEL", "RAIN", "NO_GAME", "NO GAME")):
            return "CANCELED"
        if any(kw in info for kw in ("경기연기", "경기 연기")) or \
           any(kw in info_upper for kw in ("POSTPONE", "DELAY", "SUSPEND")):
            return "POSTPONED"

    return "SCHEDULED"


def _extract_start_time(game_data: Dict[str, Any]) -> str | None:
    raw = str(game_data.get("gameDateTime") or "").strip()
    if not raw:
        return None

    try:
        return datetime.fromisoformat(raw).strftime("%H:%M")
    except ValueError:
        if "T" in raw:
            after_t = raw.split("T", 1)[1]
            if len(after_t) >= 5 and after_t[2] == ":":
                return after_t[:5]
        if len(raw) >= 5 and raw[2] == ":":
            return raw[:5]
        return None


def _extract_game_date(game_data: Dict[str, Any]) -> str | None:
    raw = str(game_data.get("gameDateTime") or "").strip()
    if raw:
        try:
            return datetime.fromisoformat(raw).date().isoformat()
        except ValueError:
            if len(raw) >= 10 and raw[4] == "-" and raw[7] == "-":
                return raw[:10]

    game_date_raw = str(game_data.get("gameDate") or "").strip()
    if len(game_date_raw) == 10 and game_date_raw[4] == "-" and game_date_raw[7] == "-":
        return game_date_raw
    if len(game_date_raw) == 8 and game_date_raw.isdigit():
        return f"{game_date_raw[:4]}-{game_date_raw[4:6]}-{game_date_raw[6:8]}"
    return None


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


def _collect_options(relays_by_inning: Dict[int, Dict[str, Any]]) -> List[Tuple[int, str, int, Dict[str, Any], Dict[str, Any]]]:
    options: List[Tuple[int, str, int, Dict[str, Any], Dict[str, Any]]] = []
    for inning in sorted(relays_by_inning):
        relay_data = relays_by_inning.get(inning) or {}
        for relay in sorted(relay_data.get("textRelays") or [], key=lambda item: item.get("no", 0)):
            relay_no = _safe_int(relay.get("no"), default=0)
            half = "top" if str(relay.get("homeOrAway")) == "0" else "bottom"
            metric_option = relay.get("metricOption") if isinstance(relay.get("metricOption"), dict) else {}
            for option in sorted(relay.get("textOptions") or [], key=lambda item: item.get("seqno", 0)):
                options.append((inning, half, relay_no, option, metric_option))
    return options


def _event_inning_label(inning: int, half: str) -> str:
    suffix = "말" if half == "bottom" else "초"
    return f"{inning}회{suffix}"


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
    has_mound_visit = _contains_any(
        text,
        [
            "마운드 방문",
            "코칭스태프 마운드",
            "mound visit",
        ],
    )
    has_pitcher_change = _is_pitcher_change(option, option_type, text)
    has_half_inning_change = _is_half_inning_change(text)
    review_result_is_out = has_video_review and _video_review_result_is_out(text)
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
    if has_pitcher_change:
        return "PITCHER_CHANGE"
    if has_mound_visit:
        return "MOUND_VISIT"
    if has_half_inning_change:
        return "HALF_INNING_CHANGE"
    if review_result_is_out:
        return "OUT"
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
    """homeLineup/homeEntry 등 '게임 전체 누적' lineup 객체를 가장 신선한 relay 에서 취한다.

    homeLineup 은 이닝별이 아니라 게임 전체 누적(타수/안타/타점…)이며 Naver 는 어느
    이닝을 조회해도 현재까지의 누적을 돌려준다. 하지만 이닝별 relay 캐시(C5) 때문에
    아직 진행되지 않은 높은 이닝의 relay 는 크롤러가 게임 초반(또는 프로세스 재시작)
    시점에 캐시한 stale lineup 을 들고 있을 수 있다 — 그 시점 스탯은 대부분 0 이다.

    단순히 '가장 높은 이닝'을 고르면(과거 구현) 그 stale 한 상위 이닝 lineup 이 매 폴
    재조회되는 현재 이닝의 신선한 lineup 을 가려서, 라이브 내내 박스스코어가 0(초반 값)
    으로 굳고 경기 종료(상위 이닝이 다시 조회되는 시점)에야 정상화되는 버그가 있었다.
    textRelays 가 있는(=실제 진행된, 매 폴 재조회되는) relay 를 우선하는
    _relays_latest_first 순서로 골라 최신 누적 스탯을 취한다.
    """
    for relay_data in _relays_latest_first(relays_by_inning):
        entry = relay_data.get(key)
        if isinstance(entry, dict) and entry:
            return entry
    return {}


def _relays_latest_first(relays_by_inning: Dict[int, Dict[str, Any]]) -> List[Dict[str, Any]]:
    """게임 전체 누적 필드(inningScore 등)를 읽을 relay 를 신선한 순서로 나열.

    이닝별 relay 캐시(C5) 때문에 아직 시작하지 않은 높은 이닝의 relay 는 과거
    폴 시점의 stale 데이터일 수 있다. textRelays 가 있는(=실제 진행된, 현재
    이닝은 매 폴 재조회되는) relay 를 높은 이닝부터 우선하고, 없으면 나머지를
    높은 이닝부터 폴백으로 사용한다.
    """
    innings = sorted(relays_by_inning.keys(), reverse=True)
    played = [inning for inning in innings if (relays_by_inning.get(inning) or {}).get("textRelays")]
    played_set = set(played)
    rest = [inning for inning in innings if inning not in played_set]
    return [relays_by_inning.get(inning) or {} for inning in played + rest]


def _extract_line_score(
    relays_by_inning: Dict[int, Dict[str, Any]],
) -> Optional[Dict[str, Dict[str, int]]]:
    """이닝별 라인스코어 추출.

    각 이닝 relay 의 textRelayData.inningScore 는 경기 전체 누적 딕셔너리
    ({"home": {"1": "0", ...}, "away": {...}}) 이므로 가장 신선한 relay 에서
    하나만 취한다. 값은 문자열로 오므로 정수 변환에 실패하는 엔트리("-" 등)는
    방어적으로 건너뛴다. 데이터가 없으면 None (필드 생략).
    """
    for relay_data in _relays_latest_first(relays_by_inning):
        raw = relay_data.get("inningScore")
        if not isinstance(raw, dict):
            continue

        line_score: Dict[str, Dict[str, int]] = {}
        for side in ("home", "away"):
            side_raw = raw.get(side)
            if not isinstance(side_raw, dict):
                continue
            side_scores: Dict[str, int] = {}
            for inning_key, score_value in side_raw.items():
                key = str(inning_key).strip()
                if not key.isdigit():
                    continue
                try:
                    score = int(str(score_value).strip())
                except (TypeError, ValueError):
                    continue
                if score < 0:
                    continue
                side_scores[key] = score
            if side_scores:
                line_score[side] = side_scores

        if line_score:
            return line_score
    return None


def _extract_team_errors(
    relays_by_inning: Dict[int, Dict[str, Any]],
    fallback_state: Dict[str, Any],
) -> Tuple[Optional[int], Optional[int]]:
    """홈/원정 실책(homeError/awayError) 추출.

    마지막 텍스트 옵션의 currentGameState(경기 최신 상태)를 우선 사용하고,
    없으면 relay 최상위 currentGameState 를 신선한 relay 순서로 폴백한다.
    데이터가 없으면 (None, None) — payload 에서 필드 생략.
    """

    def _parse(state: Any) -> Tuple[Optional[int], Optional[int]]:
        if not isinstance(state, dict):
            return None, None
        home = _safe_int(state.get("homeError"), default=-1)
        away = _safe_int(state.get("awayError"), default=-1)
        return (home if home >= 0 else None), (away if away >= 0 else None)

    home_errors, away_errors = _parse(fallback_state)
    if home_errors is not None or away_errors is not None:
        return home_errors, away_errors

    for relay_data in _relays_latest_first(relays_by_inning):
        home_errors, away_errors = _parse(relay_data.get("currentGameState"))
        if home_errors is not None or away_errors is not None:
            return home_errors, away_errors

    return None, None


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

    def identity(player: dict[str, Any]) -> str:
        player_id = _pick_str(player, ["pcode", "playerId", "player_id"])
        if player_id:
            return f"id:{player_id}"
        return f"name:{_pick_str(player, ['name', 'playerName'])}"

    for team_side in ("home", "away"):
        lineup_entry = lineup_entries.get(team_side) or {}
        entry = entry_entries.get(team_side) or {}

        lineup_batters = lineup_entry.get("batter") or []
        entry_batters = entry.get("batter") or []
        lineup_pitchers = lineup_entry.get("pitcher") or []
        entry_pitchers = entry.get("pitcher") or []

        seen_batter_ids: set[str] = set()
        for index, batter in enumerate(lineup_batters):
            name = _pick_str(batter, ["name", "playerName"])
            if not name:
                continue

            seen_batter_ids.add(identity(batter))
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

        for batter in entry_batters:
            pid = identity(batter)
            if pid in seen_batter_ids:
                continue

            name = _pick_str(batter, ["name", "playerName"])
            if not name:
                continue

            seen_batter_ids.add(pid)
            player_id = _pick_str(batter, ["pcode", "playerId", "player_id"]) or None
            position_name = _pick_str(batter, ["posName", "positionName", "playerPosName", "pos"]) or None
            batter_stats.append(
                {
                    "teamSide": team_side,
                    "playerId": player_id,
                    "playerName": name,
                    "battingOrder": None,
                    "primaryPosition": position_name,
                    "isStarter": False,
                    "plateAppearances": _pick_int(batter, ["pa", "plateAppearance", "plateAppearances"]),
                    "atBats": _pick_int(batter, ["ab", "atBat", "atBats"]),
                    "runs": _pick_int(batter, ["r", "run", "runs"]),
                    "hits": _pick_int(batter, ["h", "hit", "hits"]),
                    "rbi": _pick_int(batter, ["rbi"]),
                    "doubles": _pick_int(batter, ["h2", "double", "doubles"]),
                    "triples": _pick_int(batter, ["h3", "triple", "triples"]),
                    "homeRuns": _pick_int(batter, ["hr", "homeRun", "homerun", "homeRuns"]),
                    "walks": _pick_int(batter, ["bb", "walk", "walks", "baseOnBalls"]),
                    "strikeouts": _pick_int(batter, ["so", "kk", "strikeout", "strikeouts"]),
                    "stolenBases": _pick_int(batter, ["sb", "stolenBase", "stolenBases"]),
                    "caughtStealing": _pick_int(batter, ["cs", "caughtStealing"]),
                    "hitByPitch": _pick_int(batter, ["hbp", "hitByPitch"]),
                    "sacBunts": _pick_int(batter, ["sh", "sacBunt", "sacBunts"]),
                    "sacFlies": _pick_int(batter, ["sf", "sacFly", "sacFlies"]),
                    "leftOnBase": _pick_int(batter, ["lob", "leftOnBase"]),
                }
            )

        merged_pitchers: list[dict[str, Any]] = []
        seen_pitcher_ids: set[str] = set()
        for pitcher in list(lineup_pitchers) + list(entry_pitchers):
            pid = identity(pitcher)
            if pid in seen_pitcher_ids:
                continue
            seen_pitcher_ids.add(pid)
            merged_pitchers.append(pitcher)

        for index, pitcher in enumerate(merged_pitchers):
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
                    # Naver relay 의 lineup.pitcher 레코드는 누적 투구수를 ballCount 로 노출. 다른 별칭은 fallback.
                    "pitchesThrown": _pick_int(pitcher, ["ballCount", "np", "pc", "pitchCount", "pitchesThrown"]),
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
    for inning, half, _, option, _ in options:
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

    inning_text = (game_data.get("statusInfo") or game_data.get("currentInning") or "").strip()
    if not inning_text:
        if latest_inning is not None and latest_half is not None:
            inning_text = f"{latest_inning}{'B' if latest_half == 'bottom' else 'T'}"
        else:
            inning_text = "0T"

    base_time = now - timedelta(milliseconds=len(options))
    events: List[Dict[str, Any]] = []
    last_pitcher_name = pitcher_name
    last_batter_name = batter_name
    for index, (inning, half, relay_no, option, metric_option) in enumerate(options):
        seqno = _safe_int(option.get("seqno"), default=index)
        option_type = _safe_int(option.get("type"), default=-1)
        pitch_result = str(option.get("pitchResult") or "").strip().upper() or None
        pitch_num = _safe_int(option.get("pitchNum"), default=-1)
        pitch_speed = _safe_int(option.get("speed"), default=-1)
        pitch_stuff = str(option.get("stuff") or "").strip()
        pts_pitch_id = str(option.get("ptsPitchId") or "").strip()
        event_time = base_time + timedelta(milliseconds=index)
        event_time_iso = event_time.isoformat().replace("+00:00", "Z")
        current_state = option.get("currentGameState") or {}
        option_pitcher_id = str(current_state.get("pitcher") or "").strip()
        option_batter_id = str(current_state.get("batter") or "").strip()
        option_pitcher_name = player_map.get(option_pitcher_id, "")
        option_batter_name = player_map.get(option_batter_id, "")

        if option_pitcher_name:
            last_pitcher_name = option_pitcher_name
        if option_batter_name:
            last_batter_name = option_batter_name

        # 이벤트 직후 시점의 누적 스코어. SCORE/SAC_FLY_SCORE 이벤트가 어느 회 누구 공격에
        # 어떤 결과로 몇 점이 났는지를 클라이언트가 정확히 재구성할 수 있게 한다.
        option_home_score = _safe_int(current_state.get("homeScore"), default=-1)
        option_away_score = _safe_int(current_state.get("awayScore"), default=-1)
        ball_after = _safe_int(current_state.get("ball"), default=-1)
        strike_after = _safe_int(current_state.get("strike"), default=-1)
        out_after = _safe_int(current_state.get("out"), default=-1)

        metadata: Dict[str, Any] = {
            "inning": inning,
            "half": half,
            "relayNo": relay_no,
            "seqno": seqno,
            "optionType": option_type,
        }
        if option_home_score >= 0:
            metadata["homeScoreAfter"] = option_home_score
        if option_away_score >= 0:
            metadata["awayScoreAfter"] = option_away_score
        if half == "top":
            metadata["offenseTeam"] = away_team
            metadata["defenseTeam"] = home_team
        elif half == "bottom":
            metadata["offenseTeam"] = home_team
            metadata["defenseTeam"] = away_team
        if pitch_result is not None:
            metadata["pitchResult"] = pitch_result
        if last_pitcher_name:
            metadata["pitcher"] = last_pitcher_name
        if last_batter_name:
            metadata["batter"] = last_batter_name
        if pitch_num >= 0:
            metadata["pitchNum"] = pitch_num
        if pitch_speed >= 0:
            metadata["pitchSpeed"] = pitch_speed
        if pitch_stuff:
            metadata["pitchStuff"] = pitch_stuff
        if pts_pitch_id:
            metadata["ptsPitchId"] = pts_pitch_id
        if ball_after >= 0:
            metadata["ballAfter"] = ball_after
        if strike_after >= 0:
            metadata["strikeAfter"] = strike_after
        if out_after >= 0:
            metadata["outAfter"] = out_after
        batter_record = option.get("batterRecord")
        if isinstance(batter_record, dict) and batter_record:
            metadata["batterRecord"] = batter_record
        if metric_option:
            if "homeTeamWinRate" in metric_option:
                metadata["homeWinProbability"] = metric_option["homeTeamWinRate"]
            if "awayTeamWinRate" in metric_option:
                metadata["awayWinProbability"] = metric_option["awayTeamWinRate"]
            if "wpaByPlate" in metric_option:
                metadata["wpaByPlate"] = metric_option["wpaByPlate"]

        event_type = _classify_event_type(option)
        if event_type == "PITCHER_CHANGE":
            player_change = option.get("playerChange") or {}
            in_player = player_change.get("inPlayer") or {}
            out_player = player_change.get("outPlayer") or {}
            in_name = (in_player.get("playerName") or "").strip()
            out_name = (out_player.get("playerName") or "").strip()
            if in_name:
                metadata["inName"] = in_name
            if out_name:
                metadata["outName"] = out_name

        events.append(
            {
                "sourceEventId": f"{inning:02d}-{relay_no:03d}-{seqno:04d}",
                "type": event_type,
                "description": (option.get("text") or "").strip(),
                "occurredAt": event_time_iso,
                "inning": _event_inning_label(inning, half),
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

    home_score = max(
        _safe_int(game_data.get("homeTeamScore"), default=0),
        _safe_int(latest_state.get("homeScore"), default=0),
    )
    away_score = max(
        _safe_int(game_data.get("awayTeamScore"), default=0),
        _safe_int(latest_state.get("awayScore"), default=0),
    )
    start_time = _extract_start_time(game_data)
    game_date = _extract_game_date(game_data)

    payload: Dict[str, Any] = {
        "homeTeam": home_team,
        "awayTeam": away_team,
        "status": _normalize_status(game_data.get("statusCode"), game_data.get("statusInfo")),
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
        "baseRunners": {
            "first": _base_runner_name(latest_state.get("base1"), player_map),
            "second": _base_runner_name(latest_state.get("base2"), player_map),
            "third": _base_runner_name(latest_state.get("base3"), player_map),
        },
        "pitcher": pitcher_name or None,
        "batter": batter_name or None,
        "gameDate": game_date,
        "startTime": start_time,
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

    # 이닝별 라인스코어/실책은 relay 데이터가 있을 때만 optional 로 포함한다.
    # (경기 전 스냅샷·구버전 백엔드와의 하위 호환을 위해 없으면 필드 자체를 생략)
    line_score = _extract_line_score(relays_by_inning)
    home_errors, away_errors = _extract_team_errors(relays_by_inning, latest_state)
    if line_score is not None:
        payload["lineScore"] = line_score
    if home_errors is not None:
        payload["homeErrors"] = home_errors
    if away_errors is not None:
        payload["awayErrors"] = away_errors

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
