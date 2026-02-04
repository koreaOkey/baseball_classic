import argparse
from datetime import datetime
from typing import Any, Dict, List, Optional

import pandas as pd
import requests


BASE_URL = "https://api-gw.sports.naver.com"
DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; BaseHapticCrawler/1.0)"


def fetch_json(url: str) -> Dict[str, Any]:
    response = requests.get(
        url,
        headers={"User-Agent": DEFAULT_USER_AGENT},
        timeout=20,
    )
    response.raise_for_status()
    return response.json()


def build_output_name(game_id: str) -> str:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return f"tennis_{game_id}_{timestamp}.xlsx"


def parse_tennis(game: Dict[str, Any]) -> Dict[str, List[Dict[str, Any]]]:
    participants = game.get("participant") or {}
    position_a = participants.get("positionA") or []
    position_b = participants.get("positionB") or []
    player_a = position_a[0].get("playerName") if position_a else ""
    player_b = position_b[0].get("playerName") if position_b else ""

    score_detail = game.get("scoreDetail") or {}

    points: List[Dict[str, Any]] = []
    games: List[Dict[str, Any]] = []
    sets: List[Dict[str, Any]] = []

    for set_key, set_data in score_detail.items():
        if not set_key.startswith("set") or not isinstance(set_data, dict):
            continue

        set_number = int(set_key.replace("set", ""))
        sets.append(
            {
                "set": set_number,
                "games_a": set_data.get("a"),
                "games_b": set_data.get("b"),
                "tiebreak_a": set_data.get("tiebreakA"),
                "tiebreak_b": set_data.get("tiebreakB"),
                "has_tiebreak": set_data.get("hasTieBreak"),
            }
        )

        for game_data in set_data.get("game") or []:
            game_number = game_data.get("number")
            server = game_data.get("server")
            winner = game_data.get("winner")
            game_server_id = game_data.get("gameServerId")

            games.append(
                {
                    "set": set_number,
                    "game": game_number,
                    "server": server,
                    "winner": winner,
                    "game_server_id": game_server_id,
                }
            )

            for idx, point in enumerate(game_data.get("point") or [], start=1):
                points.append(
                    {
                        "set": set_number,
                        "game": game_number,
                        "point_no": idx,
                        "score_a": point.get("a"),
                        "score_b": point.get("b"),
                        "timestamp": point.get("timeStamp"),
                        "last_modified": point.get("lastModified"),
                        "server": server,
                        "winner": winner,
                        "game_server_id": game_server_id,
                    }
                )

    match_meta = [
        {
            "game_id": game.get("gameId"),
            "category": game.get("categoryId"),
            "category_name": game.get("categoryName"),
            "tournament": game.get("tournamentName"),
            "round": game.get("roundName"),
            "court_type": game.get("courtTypeName"),
            "match_type": game.get("matchTypeName"),
            "gender": game.get("genderTypeName"),
            "player_a": player_a,
            "player_b": player_b,
            "score_a": game.get("scoreA"),
            "score_b": game.get("scoreB"),
            "status": game.get("statusCode"),
            "status_info": game.get("statusInfo"),
            "current_set": game.get("currentSet"),
            "game_date": game.get("gameDateTime"),
        }
    ]

    return {
        "match": match_meta,
        "sets": sets,
        "games": games,
        "points": points,
    }


def save_excel(output_path: str, parsed: Dict[str, List[Dict[str, Any]]]) -> None:
    with pd.ExcelWriter(output_path, engine="openpyxl") as writer:
        pd.DataFrame(parsed["match"]).to_excel(writer, index=False, sheet_name="match")
        pd.DataFrame(parsed["sets"]).to_excel(writer, index=False, sheet_name="sets")
        pd.DataFrame(parsed["games"]).to_excel(writer, index=False, sheet_name="games")
        pd.DataFrame(parsed["points"]).to_excel(writer, index=False, sheet_name="points")


def run(game_id: str, output_path: Optional[str]) -> None:
    game_url = f"{BASE_URL}/schedule/games/{game_id}"
    game_data = fetch_json(game_url).get("result", {}).get("game", {})
    parsed = parse_tennis(game_data)
    target = output_path or build_output_name(game_id)
    save_excel(target, parsed)


def main() -> None:
    parser = argparse.ArgumentParser(description="Naver Sports tennis crawler")
    parser.add_argument("--game-id", required=True, help="예: eXzIlhIXM5IFA4n")
    parser.add_argument("--output", help="엑셀 저장 경로")
    args = parser.parse_args()
    run(args.game_id, args.output)


if __name__ == "__main__":
    main()
