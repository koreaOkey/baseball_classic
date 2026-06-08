import argparse
import random
import time
from datetime import UTC, datetime, timedelta

import requests


EVENT_POOL = [
    ("BALL", "볼"),
    ("STRIKE", "스트라이크"),
    ("OUT", "아웃"),
    ("DOUBLE_PLAY", "병살"),
    ("TRIPLE_PLAY", "삼중살"),
    ("HIT", "안타"),
    ("HOMERUN", "홈런"),
    ("SCORE", "득점"),
]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="BaseHaptic crawler simulator")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--api-key", default="dev-crawler-key")
    parser.add_argument("--game-id", default="20260607KTSK02026")
    parser.add_argument("--game-date", default="2026-06-07")
    parser.add_argument("--interval", type=int, default=3)
    parser.add_argument("--home-team", default="SSG")
    parser.add_argument("--away-team", default="KT")
    parser.add_argument(
        "--naver-pitch-detail-once",
        action="store_true",
        help="오윤석 5회초 타석 형태의 네이버 relay 상세 fixture를 한 번만 주입",
    )
    return parser


def build_naver_pitch_detail_fixture(args: argparse.Namespace) -> dict:
    now = datetime.now(UTC).replace(microsecond=0)
    pitches = [
        ("0206", "BALL", "1구 볼", 1, 129, "스위퍼", 1, 0, "B"),
        ("0207", "STRIKE", "2구 스트라이크", 2, 136, "체인지업", 1, 1, "T"),
        ("0208", "STRIKE", "3구 파울", 3, 147, "직구", 1, 2, "F"),
        ("0209", "BALL", "4구 볼", 4, 137, "슬라이더", 2, 2, "B"),
        ("0210", "STRIKE", "5구 헛스윙", 5, 148, "투심", 2, 3, "S"),
    ]
    batter_record = {
        "name": "오윤석",
        "pcode": "64504",
        "batOrder": 8,
        "seasonHra": 0.278,
        "pa": 2,
        "ab": 2,
        "hit": 0,
        "rbi": 0,
        "hr": 0,
        "bb": 0,
        "so": 1,
    }
    events = [
        {
            "sourceEventId": "05-040-0205",
            "type": "OTHER",
            "description": "8번타자 오윤석",
            "occurredAt": now.isoformat().replace("+00:00", "Z"),
            "inning": "5회초",
            "metadata": {
                "inning": 5,
                "half": "top",
                "relayNo": 40,
                "seqno": 205,
                "optionType": 8,
                "pitcher": "베니지아노",
                "batter": "오윤석",
                "offenseTeam": args.away_team,
                "defenseTeam": args.home_team,
                "homeScoreAfter": 2,
                "awayScoreAfter": 0,
                "batterRecord": batter_record,
                "homeWinProbability": 76.0,
                "awayWinProbability": 24.0,
                "wpaByPlate": -3.2,
            },
        }
    ]
    for offset, (suffix, event_type, description, pitch_num, speed, stuff, ball, strike, pitch_result) in enumerate(pitches, start=1):
        events.append(
            {
                "sourceEventId": f"05-040-{suffix}",
                "type": event_type,
                "description": description,
                "occurredAt": (now + timedelta(seconds=offset)).isoformat().replace("+00:00", "Z"),
                "inning": "5회초",
                "metadata": {
                    "inning": 5,
                    "half": "top",
                    "relayNo": 40,
                    "seqno": int(suffix),
                    "optionType": 1,
                    "pitcher": "베니지아노",
                    "batter": "오윤석",
                    "offenseTeam": args.away_team,
                    "defenseTeam": args.home_team,
                    "homeScoreAfter": 2,
                    "awayScoreAfter": 0,
                    "pitchNum": pitch_num,
                    "pitchResult": pitch_result,
                    "pitchSpeed": speed,
                    "pitchStuff": stuff,
                    "ballAfter": ball,
                    "strikeAfter": strike,
                    "outAfter": 0,
                    "batterRecord": batter_record,
                    "homeWinProbability": 76.0,
                    "awayWinProbability": 24.0,
                    "wpaByPlate": -3.2,
                },
            }
        )
    events.append(
        {
            "sourceEventId": "05-040-0211",
            "type": "OUT",
            "description": "오윤석 : 삼진 아웃",
            "occurredAt": (now + timedelta(seconds=6)).isoformat().replace("+00:00", "Z"),
            "inning": "5회초",
            "metadata": {
                "inning": 5,
                "half": "top",
                "relayNo": 40,
                "seqno": 211,
                "optionType": 13,
                "pitcher": "베니지아노",
                "batter": "오윤석",
                "offenseTeam": args.away_team,
                "defenseTeam": args.home_team,
                "homeScoreAfter": 2,
                "awayScoreAfter": 0,
                "ballAfter": 2,
                "strikeAfter": 3,
                "outAfter": 1,
                "batterRecord": {**batter_record, "so": 1},
                "homeWinProbability": 76.0,
                "awayWinProbability": 24.0,
                "wpaByPlate": -3.2,
            },
        }
    )
    return {
        "homeTeam": args.home_team,
        "awayTeam": args.away_team,
        "gameDate": args.game_date,
        "status": "LIVE",
        "inning": "5회초",
        "homeScore": 2,
        "awayScore": 0,
        "ball": 2,
        "strike": 3,
        "out": 1,
        "bases": {"first": False, "second": False, "third": False},
        "pitcher": "베니지아노",
        "batter": "오윤석",
        "observedAt": (now + timedelta(seconds=6)).isoformat().replace("+00:00", "Z"),
        "events": events,
    }


def main() -> None:
    args = build_parser().parse_args()
    base_url = args.base_url.rstrip("/")
    endpoint = f"{base_url}/internal/crawler/games/{args.game_id}/snapshot"
    headers = {"X-API-Key": args.api_key}

    if args.naver_pitch_detail_once:
        payload = build_naver_pitch_detail_fixture(args)
        response = requests.post(endpoint, headers=headers, json=payload, timeout=10)
        print(f"[fixture] {response.status_code} {response.text}")
        return

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
            "gameDate": args.game_date,
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
