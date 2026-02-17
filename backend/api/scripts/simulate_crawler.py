import argparse
import random
import time
from datetime import UTC, datetime

import requests


EVENT_POOL = [
    ("BALL", "볼"),
    ("STRIKE", "스트라이크"),
    ("OUT", "아웃"),
    ("HIT", "안타"),
    ("HOMERUN", "홈런"),
    ("SCORE", "득점"),
]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="BaseHaptic crawler simulator")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--api-key", default="dev-crawler-key")
    parser.add_argument("--game-id", default="DEMO-GAME-001")
    parser.add_argument("--interval", type=int, default=3)
    parser.add_argument("--home-team", default="두산")
    parser.add_argument("--away-team", default="LG")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    base_url = args.base_url.rstrip("/")
    endpoint = f"{base_url}/internal/crawler/games/{args.game_id}/snapshot"
    headers = {"X-API-Key": args.api_key}

    home_score = 0
    away_score = 0
    event_seq = 1
    inning = 1
    is_bottom = False

    while True:
        events = []
        if random.random() < 0.65:
            event_type, label = random.choice(EVENT_POOL)
            if event_type in {"SCORE", "HOMERUN"}:
                if random.random() < 0.5:
                    home_score += 1
                else:
                    away_score += 1
            events.append(
                {
                    "sourceEventId": f"relay-{event_seq:06d}",
                    "type": event_type,
                    "description": f"{label} 이벤트 발생",
                    "occurredAt": datetime.now(UTC).isoformat(),
                }
            )
            event_seq += 1

        payload = {
            "homeTeam": args.home_team,
            "awayTeam": args.away_team,
            "status": "LIVE",
            "inning": f"{inning}회{'말' if is_bottom else '초'}",
            "homeScore": home_score,
            "awayScore": away_score,
            "ball": random.randint(0, 3),
            "strike": random.randint(0, 2),
            "out": random.randint(0, 2),
            "bases": {
                "first": random.random() < 0.4,
                "second": random.random() < 0.3,
                "third": random.random() < 0.2,
            },
            "pitcher": "임시 투수",
            "batter": "임시 타자",
            "observedAt": datetime.now(UTC).isoformat(),
            "events": events,
        }

        response = requests.post(endpoint, headers=headers, json=payload, timeout=10)
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {response.status_code} {response.text}")

        if is_bottom:
            inning += 1
        is_bottom = not is_bottom
        time.sleep(args.interval)


if __name__ == "__main__":
    main()
