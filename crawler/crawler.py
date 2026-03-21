import argparse
import re
import time
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

import pandas as pd
import requests

from backend_sender import build_snapshot_payload, post_snapshot_to_backend


BASE_URL = "https://api-gw.sports.naver.com"
DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; BaseballClassicCrawler/1.0)"
FINAL_STATUS = {"RESULT", "END", "FINAL"}


def fetch_json(url: str) -> Dict[str, Any]:
    response = requests.get(
        url,
        headers={"User-Agent": DEFAULT_USER_AGENT},
        timeout=20,
    )
    response.raise_for_status()
    return response.json()


def _pick_preview_player_value(player: Dict[str, Any], keys: List[str]) -> str:
    for key in keys:
        value = str(player.get(key) or "").strip()
        if value:
            return value
    return ""


def _preview_player_to_entry(player: Dict[str, Any], *, batting_order: Optional[int] = None) -> Dict[str, Any]:
    entry: Dict[str, Any] = {
        "pcode": _pick_preview_player_value(player, ["playerCode", "pCode", "playerId"]),
        "name": _pick_preview_player_value(player, ["playerName", "name"]),
        "pos": _pick_preview_player_value(player, ["position", "pos"]),
        "posName": _pick_preview_player_value(player, ["positionName", "posName"]),
    }
    if batting_order is not None:
        entry["batOrder"] = batting_order
    return entry


def _preview_to_relay_entry(preview_data: Dict[str, Any], team_side: str) -> Dict[str, Any]:
    team_key = "homeTeamLineUp" if team_side == "home" else "awayTeamLineUp"
    team_lineup = preview_data.get(team_key) or {}
    if not isinstance(team_lineup, dict):
        return {"batter": [], "pitcher": []}

    full_lineup = team_lineup.get("fullLineUp") or []
    batter: List[Dict[str, Any]] = []
    pitcher: List[Dict[str, Any]] = []
    for player in full_lineup:
        if not isinstance(player, dict):
            continue
        position_name = _pick_preview_player_value(player, ["positionName", "posName"])
        position_code = _pick_preview_player_value(player, ["position", "pos"])
        is_pitcher = "투수" in position_name or position_code == "1"
        if is_pitcher:
            pitcher.append(_preview_player_to_entry(player))
            continue
        batter.append(_preview_player_to_entry(player, batting_order=len(batter) + 1))

    return {"batter": batter, "pitcher": pitcher}


def _preview_to_relay_candidates(preview_data: Dict[str, Any], team_side: str) -> Dict[str, Any]:
    team_key = "homeTeamLineUp" if team_side == "home" else "awayTeamLineUp"
    team_lineup = preview_data.get(team_key) or {}
    if not isinstance(team_lineup, dict):
        return {"batter": [], "pitcher": []}

    batter_candidates = team_lineup.get("batterCandidate") or []
    pitcher_candidates = team_lineup.get("pitcherBullpen") or []

    batter: List[Dict[str, Any]] = []
    for player in batter_candidates:
        if isinstance(player, dict):
            batter.append(_preview_player_to_entry(player))

    pitcher: List[Dict[str, Any]] = []
    for player in pitcher_candidates:
        if isinstance(player, dict):
            pitcher.append(_preview_player_to_entry(player))

    return {"batter": batter, "pitcher": pitcher}


def _inject_preview_lineup_into_relays(relays_by_inning: Dict[int, Dict[str, Any]], preview_payload: Dict[str, Any]) -> None:
    result = preview_payload.get("result") or {}
    if not isinstance(result, dict):
        return
    preview_data = result.get("previewData") or {}
    if not isinstance(preview_data, dict):
        return

    home_lineup = _preview_to_relay_entry(preview_data, "home")
    away_lineup = _preview_to_relay_entry(preview_data, "away")
    home_entry = _preview_to_relay_candidates(preview_data, "home")
    away_entry = _preview_to_relay_candidates(preview_data, "away")
    if not (home_lineup["batter"] or home_lineup["pitcher"] or away_lineup["batter"] or away_lineup["pitcher"]):
        return

    base_relay = relays_by_inning.get(1) or {}
    merged = dict(base_relay)
    merged["homeLineup"] = home_lineup
    merged["awayLineup"] = away_lineup
    merged["homeEntry"] = home_entry
    merged["awayEntry"] = away_entry
    relays_by_inning[1] = merged

def _current_inning_number(game_data: Dict[str, Any]) -> int:
    text = str(game_data.get("statusInfo") or game_data.get("currentInning") or "").strip()
    match = re.search(r"(\d+)", text)
    if match:
        try:
            return max(1, int(match.group(1)))
        except ValueError:
            pass
    return 9


def build_player_map(relay_data: Dict[str, Any]) -> Dict[str, str]:
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
        name = (player.get("name") or player.get("playerName") or "").strip()
        if player_id and name:
            player_map[player_id] = name

    def add_list(players: Optional[List[Dict[str, Any]]]) -> None:
        for player in players or []:
            add_player(player)

    for entry_key in ("homeEntry", "awayEntry", "homeLineup", "awayLineup"):
        entry = relay_data.get(entry_key, {})
        add_list(entry.get("batter"))
        add_list(entry.get("pitcher"))

    return player_map


def update_player_map_from_option(option: Dict[str, Any], player_map: Dict[str, str]) -> None:
    batter_record = option.get("batterRecord")
    if batter_record:
        player_id = str(batter_record.get("pcode") or "").strip()
        name = (batter_record.get("name") or "").strip()
        if player_id and name:
            player_map[player_id] = name

    player_change = option.get("playerChange") or {}
    for key in ("inPlayer", "outPlayer"):
        player = player_change.get(key) or {}
        player_id = str(player.get("playerId") or "").strip()
        name = (player.get("playerName") or "").strip()
        if player_id and name:
            player_map[player_id] = name


def normalize_half(home_or_away: Any) -> str:
    return "top" if str(home_or_away) == "0" else "bottom"


def get_team_names(game: Dict[str, Any]) -> Dict[str, str]:
    home_team = (game.get("homeTeamName") or game.get("homeTeamShortName") or "").strip()
    away_team = (game.get("awayTeamName") or game.get("awayTeamShortName") or "").strip()
    return {"home": home_team, "away": away_team}


def resolve_player_name(player_id: Optional[str], player_map: Dict[str, str]) -> str:
    if not player_id:
        return ""
    return player_map.get(str(player_id), "")


def should_count_ball(_: str, pitch_result: Optional[str]) -> bool:
    return pitch_result in {"B"}


def should_count_strike(_: str, pitch_result: Optional[str]) -> bool:
    return pitch_result in {"T", "S", "F"}


def _contains_any(value: str, terms: List[str]) -> bool:
    normalized = (value or "").lower()
    return any(term.lower() in normalized for term in terms)


def _is_pitcher_change(live_text: str, in_pos: str, out_pos: str) -> bool:
    return (
        _contains_any(live_text, ["투수", "pitcher"])
        or _contains_any(in_pos, ["투수", "pitcher"])
        or _contains_any(out_pos, ["투수", "pitcher"])
    )


def _is_pinch_hitter_change(live_text: str, in_pos: str, out_pos: str) -> bool:
    return (
        _contains_any(live_text, ["대타", "pinch"])
        or _contains_any(in_pos, ["대타", "pinch"])
        or _contains_any(out_pos, ["대타", "pinch"])
    )


def parse_relay(
    relay_data: Optional[Dict[str, Any]],
    teams: Dict[str, str],
) -> Dict[str, List[Dict[str, Any]]]:
    relay_data = relay_data or {}
    player_map = build_player_map(relay_data)
    text_relays = relay_data.get("textRelays") or []

    at_bats: List[Dict[str, Any]] = []
    pinch_hitters: List[Dict[str, Any]] = []
    pitcher_changes: List[Dict[str, Any]] = []

    current_atbat: Optional[Dict[str, Any]] = None

    def close_current_atbat(result_text: Optional[str], end_seqno: Optional[int]) -> None:
        nonlocal current_atbat
        if not current_atbat:
            return
        if result_text and not current_atbat.get("result_text"):
            current_atbat["result_text"] = result_text
        current_atbat["end_seqno"] = end_seqno
        at_bats.append(current_atbat)
        current_atbat = None

    def start_atbat(
        inn: int,
        half: str,
        batter_id: Optional[str],
        batter_name: str,
        pitcher_id: Optional[str],
        pitcher_name: str,
        is_pinch_hitter: bool,
        seqno: Optional[int],
        batting_team: str,
        fielding_team: str,
    ) -> None:
        nonlocal current_atbat

        if current_atbat:
            same_batter = (
                current_atbat.get("inning") == inn
                and current_atbat.get("half") == half
                and current_atbat.get("batter_id") == batter_id
            )
            if same_batter:
                if pitcher_name and not current_atbat.get("pitcher"):
                    current_atbat["pitcher"] = pitcher_name
                if pitcher_id and not current_atbat.get("pitcher_id"):
                    current_atbat["pitcher_id"] = pitcher_id
                return
            close_current_atbat(None, seqno)

        current_atbat = {
            "inning": inn,
            "half": half,
            "batting_team": batting_team,
            "fielding_team": fielding_team,
            "batter": batter_name,
            "batter_id": batter_id,
            "pitcher": pitcher_name,
            "pitcher_id": pitcher_id,
            "balls": 0,
            "strikes": 0,
            "result_text": "",
            "is_pinch_hitter": is_pinch_hitter,
            "start_seqno": seqno,
            "end_seqno": None,
        }

    for relay in sorted(text_relays, key=lambda item: item.get("no", 0)):
        inn = int(relay.get("inn") or 0)
        if not (1 <= inn <= 9):
            continue

        half = normalize_half(relay.get("homeOrAway"))
        is_top = half == "top"
        batting_team = teams["away"] if is_top else teams["home"]
        fielding_team = teams["home"] if is_top else teams["away"]

        text_options = relay.get("textOptions") or []
        for option in sorted(text_options, key=lambda item: item.get("seqno", 0)):
            update_player_map_from_option(option, player_map)

            option_type = option.get("type")
            text = (option.get("text") or "").strip()
            seqno = option.get("seqno")

            current_state = option.get("currentGameState") or {}
            pitcher_id = str(current_state.get("pitcher") or "").strip() or None
            batter_id = str(current_state.get("batter") or "").strip() or None

            if option_type == 2 and option.get("playerChange"):
                change = option.get("playerChange") or {}
                live_text = (change.get("liveText") or text).strip()
                in_player = change.get("inPlayer") or {}
                out_player = change.get("outPlayer") or {}

                in_name = (in_player.get("playerName") or "").strip()
                out_name = (out_player.get("playerName") or "").strip()
                in_pos = (in_player.get("playerPos") or "").strip()
                out_pos = (out_player.get("playerPos") or "").strip()

                if _is_pitcher_change(live_text, in_pos, out_pos):
                    pitcher_changes.append(
                        {
                            "inning": inn,
                            "half": half,
                            "team": fielding_team,
                            "in_pitcher": in_name,
                            "out_pitcher": out_name,
                            "text": live_text,
                            "seqno": seqno,
                        }
                    )

                if _is_pinch_hitter_change(live_text, in_pos, out_pos):
                    pinch_hitters.append(
                        {
                            "inning": inn,
                            "half": half,
                            "team": batting_team,
                            "in_batter": in_name,
                            "out_batter": out_name,
                            "text": live_text,
                            "seqno": seqno,
                        }
                    )

            if option_type == 8 or option.get("batterRecord"):
                batter_record = option.get("batterRecord") or {}
                batter_name = (batter_record.get("name") or "").strip()
                if not batter_name and batter_id:
                    batter_name = resolve_player_name(batter_id, player_map)
                pitcher_name = resolve_player_name(pitcher_id, player_map)
                is_pinch = _contains_any(text, ["대타", "pinch"]) or _contains_any(
                    batter_record.get("posName") or "", ["대타", "pinch"]
                )
                if batter_name:
                    start_atbat(
                        inn,
                        half,
                        batter_id,
                        batter_name,
                        pitcher_id,
                        pitcher_name,
                        is_pinch,
                        seqno,
                        batting_team,
                        fielding_team,
                    )

            if option_type == 1:
                if current_atbat is None:
                    batter_name = resolve_player_name(batter_id, player_map)
                    pitcher_name = resolve_player_name(pitcher_id, player_map)
                    start_atbat(
                        inn,
                        half,
                        batter_id,
                        batter_name,
                        pitcher_id,
                        pitcher_name,
                        False,
                        seqno,
                        batting_team,
                        fielding_team,
                    )

                pitch_result = option.get("pitchResult")
                if current_atbat:
                    if should_count_ball(text, pitch_result):
                        current_atbat["balls"] += 1
                    if should_count_strike(text, pitch_result):
                        current_atbat["strikes"] += 1

            if option_type == 13:
                close_current_atbat(text, seqno)

    if current_atbat:
        close_current_atbat(None, None)

    return {
        "at_bats": at_bats,
        "pinch_hitters": pinch_hitters,
        "pitcher_changes": pitcher_changes,
    }


def save_excel(
    output_path: str,
    game: Dict[str, Any],
    parsed: Dict[str, List[Dict[str, Any]]],
) -> None:
    at_bats_df = pd.DataFrame(parsed["at_bats"])
    pinch_df = pd.DataFrame(parsed["pinch_hitters"])
    pitcher_df = pd.DataFrame(parsed["pitcher_changes"])
    summary_df = pd.DataFrame(
        [
            {
                "game_id": game.get("gameId"),
                "home_team": game.get("homeTeamName") or game.get("homeTeamShortName"),
                "away_team": game.get("awayTeamName") or game.get("awayTeamShortName"),
                "status": game.get("statusCode"),
                "score_home": game.get("homeTeamScore"),
                "score_away": game.get("awayTeamScore"),
            }
        ]
    )

    with pd.ExcelWriter(output_path, engine="openpyxl") as writer:
        summary_df.to_excel(writer, index=False, sheet_name="match")
        at_bats_df.to_excel(writer, index=False, sheet_name="at_bats")
        pinch_df.to_excel(writer, index=False, sheet_name="pinch_hitters")
        pitcher_df.to_excel(writer, index=False, sheet_name="pitcher_changes")


def build_output_name(game_id: str) -> str:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return f"relay_{game_id}_{timestamp}.xlsx"


def crawl_once_detailed(
    game_id: str,
    base_url: str = BASE_URL,
) -> Tuple[Dict[str, Any], Dict[int, Dict[str, Any]], Dict[str, List[Dict[str, Any]]]]:
    game_url = f"{base_url}/schedule/games/{game_id}"
    game_data = fetch_json(game_url).get("result", {}).get("game", {})
    if not game_data:
        raise ValueError(f"No game data found for game_id={game_id}")

    relays_by_inning: Dict[int, Dict[str, Any]] = {}
    teams = get_team_names(game_data)
    combined = {
        "at_bats": [],
        "pinch_hitters": [],
        "pitcher_changes": [],
    }

    inning_limit = max(9, _current_inning_number(game_data))
    for inning in range(1, inning_limit + 1):
        relay_url = f"{base_url}/schedule/games/{game_id}/relay?inning={inning}"
        relay_data = fetch_json(relay_url).get("result", {}).get("textRelayData") or {}
        relays_by_inning[inning] = relay_data
        parsed = parse_relay(relay_data, teams)
        combined["at_bats"].extend(parsed["at_bats"])
        combined["pinch_hitters"].extend(parsed["pinch_hitters"])
        combined["pitcher_changes"].extend(parsed["pitcher_changes"])

    # Pregame lineup can be exposed in preview endpoint before relay text is available.
    if not any(
        isinstance(relay, dict) and (
            relay.get("homeLineup") or relay.get("awayLineup") or relay.get("homeEntry") or relay.get("awayEntry")
        )
        for relay in relays_by_inning.values()
    ):
        preview_url = f"{base_url}/schedule/games/{game_id}/preview"
        try:
            preview_payload = fetch_json(preview_url)
        except requests.RequestException:
            preview_payload = {}
        if isinstance(preview_payload, dict):
            _inject_preview_lineup_into_relays(relays_by_inning, preview_payload)

    return game_data, relays_by_inning, combined


def crawl_once(game_id: str, base_url: str = BASE_URL) -> Tuple[Dict[str, Any], Dict[str, List[Dict[str, Any]]]]:
    game_data, _, combined = crawl_once_detailed(game_id=game_id, base_url=base_url)
    return game_data, combined


def run(
    game_id: str,
    output_path: Optional[str],
    watch: bool,
    interval: int,
    base_url: str,
    backend_base_url: Optional[str] = None,
    backend_api_key: Optional[str] = None,
    backend_timeout: float = 10.0,
    backend_retries: int = 3,
) -> None:
    posted_source_event_ids: set[str] = set()

    while True:
        try:
            game_data, relays_by_inning, combined = crawl_once_detailed(game_id=game_id, base_url=base_url)
        except requests.RequestException as exc:
            print(f"[crawl][warn] gameId={game_id} fetch_failed error={exc}", flush=True)
            if not watch:
                raise
            time.sleep(interval)
            continue

        target = output_path or build_output_name(game_id)
        save_excel(target, game_data, combined)
        status = (game_data.get("statusCode") or "").upper()
        inning = (game_data.get("statusInfo") or game_data.get("currentInning") or "-").strip()
        away_score = game_data.get("awayTeamScore")
        home_score = game_data.get("homeTeamScore")
        relay_count = sum(len((relay.get("textRelays") or [])) for relay in relays_by_inning.values())
        print(
            f"[crawl] at={datetime.now().isoformat(timespec='seconds')} "
            f"gameId={game_id} status={status or '-'} inning={inning or '-'} "
            f"score={away_score}:{home_score} relayFrames={relay_count} "
            f"atBats={len(combined['at_bats'])} pitcherChanges={len(combined['pitcher_changes'])}",
            flush=True,
        )
        print(f"[output] excel={target}", flush=True)

        if backend_base_url:
            if not backend_api_key:
                raise ValueError("--backend-api-key is required when --backend-base-url is set")
            snapshot = build_snapshot_payload(game_data=game_data, relays_by_inning=relays_by_inning)
            all_events = snapshot.get("events") or []
            delta_events = [
                event
                for event in all_events
                if str(event.get("sourceEventId") or "").strip()
                and str(event.get("sourceEventId") or "").strip() not in posted_source_event_ids
            ]
            snapshot["events"] = delta_events
            print(
                f"[snapshot] gameDate={snapshot.get('gameDate')} startTime={snapshot.get('startTime')} "
                f"events={len(delta_events)} totalEvents={len(all_events)}",
                flush=True,
            )
            try:
                last_error: requests.RequestException | None = None
                result: Dict[str, Any] | None = None
                attempts = max(1, backend_retries)
                for attempt in range(1, attempts + 1):
                    try:
                        result = post_snapshot_to_backend(
                            backend_base_url=backend_base_url,
                            api_key=backend_api_key,
                            game_id=game_id,
                            payload=snapshot,
                            timeout=backend_timeout,
                        )
                        break
                    except requests.RequestException as exc:
                        last_error = exc
                        print(
                            f"[backend][warn] gameId={game_id} ingest_retry={attempt}/{attempts} error={exc}",
                            flush=True,
                        )
                        if attempt < attempts:
                            time.sleep(min(5, attempt))
                if result is None:
                    raise last_error or requests.RequestException("backend ingest failed")

                print(
                    f"[backend] gameId={result.get('gameId')} "
                    f"received={result.get('receivedEvents')} "
                    f"inserted={result.get('insertedEvents')} "
                    f"duplicates={result.get('duplicateEvents')}",
                    flush=True,
                )
                posted_source_event_ids.update(
                    str(event.get("sourceEventId")).strip()
                    for event in delta_events
                    if str(event.get("sourceEventId") or "").strip()
                )
            except requests.RequestException as exc:
                print(f"[backend][error] gameId={game_id} ingest_failed error={exc}", flush=True)
                if not watch:
                    raise

        if not watch or status in FINAL_STATUS:
            break
        time.sleep(interval)


def main() -> None:
    parser = argparse.ArgumentParser(description="Naver Sports baseball relay crawler")
    parser.add_argument("--game-id", required=True, help="example: 20250902WOSK02025")
    parser.add_argument("--output", help="excel output path")
    parser.add_argument(
        "--watch",
        action="store_true",
        help="poll repeatedly until game status becomes final",
    )
    parser.add_argument("--interval", type=int, default=30, help="watch interval in seconds")
    parser.add_argument(
        "--base-url",
        default=BASE_URL,
        help="api base url (default: official naver api)",
    )
    parser.add_argument(
        "--backend-base-url",
        help="backend ingest base url, example: http://localhost:8080",
    )
    parser.add_argument("--backend-api-key", help="backend ingest X-API-Key value")
    parser.add_argument(
        "--backend-timeout",
        type=float,
        default=10.0,
        help="backend ingest request timeout in seconds",
    )
    parser.add_argument(
        "--backend-retries",
        type=int,
        default=3,
        help="number of backend ingest retries per poll",
    )

    args = parser.parse_args()
    if args.backend_base_url and not args.backend_api_key:
        parser.error("--backend-api-key is required when --backend-base-url is set")

    run(
        game_id=args.game_id,
        output_path=args.output,
        watch=args.watch,
        interval=args.interval,
        base_url=args.base_url.rstrip("/"),
        backend_base_url=args.backend_base_url.rstrip("/") if args.backend_base_url else None,
        backend_api_key=args.backend_api_key,
        backend_timeout=args.backend_timeout,
        backend_retries=args.backend_retries,
    )


if __name__ == "__main__":
    main()
