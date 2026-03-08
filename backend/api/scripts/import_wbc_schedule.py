import argparse
import os
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import requests


API_ROOT = Path(__file__).resolve().parents[1]
os.chdir(API_ROOT)

import sys

if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))

from app.db import SessionLocal, init_db
from app.schemas import BaseStatus, CrawlerSnapshotRequest
from app.services import upsert_game_from_snapshot


SCHEDULE_API_URL = "https://api-gw.sports.naver.com/schedule/games"
LIVE_STATUS_CODES = {"LIVE", "ING", "PLAYING", "IN_PROGRESS", "STARTED"}
FINISHED_STATUS_CODES = {"RESULT", "FINAL", "END", "FINISHED"}


def _map_status(status_code: Any) -> str:
    raw = str(status_code or "").strip().upper()
    if raw in LIVE_STATUS_CODES:
        return "LIVE"
    if raw in FINISHED_STATUS_CODES:
        return "FINISHED"
    return "SCHEDULED"


def _extract_inning(game: dict[str, Any]) -> str:
    status_info = str(game.get("statusInfo") or "").strip()
    if status_info:
        return status_info

    game_date_time = str(game.get("gameDateTime") or "").strip()
    if game_date_time:
        try:
            return datetime.fromisoformat(game_date_time).strftime("%H:%M")
        except ValueError:
            return game_date_time

    return "-"


def _extract_start_time(game: dict[str, Any]) -> str | None:
    raw = str(game.get("gameDateTime") or "").strip()
    if not raw:
        return None
    try:
        return datetime.fromisoformat(raw).strftime("%H:%M")
    except ValueError:
        if len(raw) >= 5 and raw[2] == ":":
            return raw[:5]
        return None


def _extract_game_date(game: dict[str, Any]) -> str | None:
    game_date = str(game.get("gameDate") or "").strip()
    if len(game_date) == 10 and game_date[4] == "-" and game_date[7] == "-":
        return game_date

    raw = str(game.get("gameDateTime") or "").strip()
    if not raw:
        return None
    try:
        return datetime.fromisoformat(raw).date().isoformat()
    except ValueError:
        if len(raw) >= 10 and raw[4] == "-" and raw[7] == "-":
            return raw[:10]
        return None


def _build_payload(game: dict[str, Any], observed_at: datetime, target_date: str) -> CrawlerSnapshotRequest:
    game_date = _extract_game_date(game) or target_date
    return CrawlerSnapshotRequest(
        homeTeam=str(game.get("homeTeamName") or "").strip() or "HOME",
        awayTeam=str(game.get("awayTeamName") or "").strip() or "AWAY",
        gameDate=game_date,
        status=_map_status(game.get("statusCode")),
        inning=_extract_inning(game),
        homeScore=int(game.get("homeTeamScore") or 0),
        awayScore=int(game.get("awayTeamScore") or 0),
        ball=0,
        strike=0,
        out=0,
        bases=BaseStatus(first=False, second=False, third=False),
        pitcher=None,
        batter=None,
        startTime=_extract_start_time(game),
        observedAt=observed_at,
        events=[],
        lineupSlots=[],
        batterStats=[],
        pitcherStats=[],
        notes=[],
    )


def fetch_games(*, section_id: str, category_id: str, date: str) -> list[dict[str, Any]]:
    response = requests.get(
        SCHEDULE_API_URL,
        params={
            "sectionId": section_id,
            "categoryId": category_id,
            "date": date,
        },
        headers={"User-Agent": "Mozilla/5.0", "content-type": "application/json"},
        timeout=20,
    )
    response.raise_for_status()
    payload = response.json()
    return (payload.get("result") or {}).get("games") or []


def main() -> None:
    parser = argparse.ArgumentParser(description="Import WBC schedule games into backend DB")
    parser.add_argument("--date", required=True, help="Target date in YYYY-MM-DD")
    parser.add_argument("--section-id", default="wbaseball")
    parser.add_argument("--category-id", default="wbc")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    games = fetch_games(section_id=args.section_id, category_id=args.category_id, date=args.date)
    print(f"fetched={len(games)} date={args.date} section={args.section_id} category={args.category_id}")
    if not games:
        return

    now_utc = datetime.now(UTC)
    for game in games:
        game_id = str(game.get("gameId") or "").strip()
        if not game_id:
            continue
        payload = _build_payload(game, observed_at=now_utc, target_date=args.date)
        if args.dry_run:
            print(
                f"[dry-run] gameId={game_id} {payload.awayTeam} vs {payload.homeTeam} "
                f"status={payload.status} inning={payload.inning} score={payload.awayScore}:{payload.homeScore}"
            )

    if args.dry_run:
        return

    init_db()
    with SessionLocal() as db:
        upserted = 0
        for game in games:
            game_id = str(game.get("gameId") or "").strip()
            if not game_id:
                continue
            payload = _build_payload(game, observed_at=now_utc, target_date=args.date)
            row = upsert_game_from_snapshot(db, game_id=game_id, payload=payload)
            row.updated_at = now_utc
            upserted += 1
            print(
                f"upserted gameId={game_id} {payload.awayTeam} vs {payload.homeTeam} "
                f"status={payload.status} inning={payload.inning}"
            )
        db.commit()
    print(f"done upserted={upserted}")


if __name__ == "__main__":
    main()
