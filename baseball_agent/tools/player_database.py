"""player_database Tool — data/players.json 로더.

RESEARCH_PLAYER Node가 호출해 선수 팩트를 가져온다. 얼굴·외모 필드는
데이터 자체에 없으므로(docs/safety.md §2) 반환값을 바로 downstream에 써도 안전.
"""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Optional

DATA_FILE = Path(__file__).parent.parent / "data" / "players.json"


@lru_cache(maxsize=1)
def _load() -> dict:
    """JSON은 프로세스 수명 동안 한 번만 읽는다."""
    with DATA_FILE.open(encoding="utf-8") as f:
        return json.load(f)


def load_all_players() -> list[dict]:
    """전체 선수 리스트."""
    return _load()["players"]


def load_teams() -> dict[str, dict]:
    """팀 코드 → 팀 메타 dict."""
    return _load()["teams"]


def find_player(name_or_id: str) -> Optional[dict]:
    """이름(name_kr) 또는 id로 매칭. CLI --player 인자 해석에 사용.

    먼저 id 완전 일치, 다음 name_kr 완전 일치, 마지막으로 부분 일치 순으로 찾는다.
    """
    needle = name_or_id.strip()
    players = load_all_players()

    # 1) id 완전 일치
    for p in players:
        if p["id"] == needle:
            return p

    # 2) name_kr 완전 일치
    for p in players:
        if p["name_kr"] == needle:
            return p

    # 3) name_kr 부분 일치 (예: "이정" → 이정후)
    matches = [p for p in players if needle in p["name_kr"]]
    if len(matches) == 1:
        return matches[0]

    return None


def get_team_info(team_code: str) -> Optional[dict]:
    """팀 코드(LG, DOOSAN, SFG 등)로 팀 메타 조회."""
    return load_teams().get(team_code)


def find_team(name_or_code: str) -> Optional[tuple[str, dict]]:
    """팀 코드 또는 한글명으로 매칭. (team_code, team_dict) 튜플 반환.

    예:
      find_team("SSG")     → ("SSG", {...})
      find_team("ssg")     → ("SSG", {...})  (대소문자 무시)
      find_team("두산")    → ("DOOSAN", {...})
      find_team("두산 베어스") → ("DOOSAN", {...})  (부분 매칭)
    """
    needle = name_or_code.strip()
    teams = load_teams()

    # 1) 코드 정확 일치 (대소문자 무시)
    upper = needle.upper()
    if upper in teams:
        return upper, teams[upper]

    # 2) 한글명 정확 일치
    for code, t in teams.items():
        if t.get("name_kr") == needle:
            return code, t

    # 3) 한글명 부분 매칭 (예: "두산" → "두산 베어스")
    for code, t in teams.items():
        if needle in (t.get("name_kr") or ""):
            return code, t

    return None


def top_players_by_popularity(n: int = 10) -> list[dict]:
    """CHOOSE_PLAYER Node가 auto 모드에서 후보를 추릴 때 사용.

    실제 CHOOSE_PLAYER는 popularity만 보지 않고 web_search 결과와 함께 판단하지만,
    DB 단독 쿼리 시 시드로 쓸 수 있게 정렬된 상위 N개를 반환한다.
    """
    players = load_all_players()
    return sorted(players, key=lambda p: p.get("popularity_score", 0), reverse=True)[:n]


def enrich_with_team(player: dict) -> dict:
    """player dict에 team_name_kr, team_colors를 주입해 downstream 편의 향상."""
    team = get_team_info(player["team_code"])
    if team:
        return {
            **player,
            "team_name_kr": team["name_kr"],
            "team_colors": team["colors"],
        }
    return player
