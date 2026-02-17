import argparse
import json
from pathlib import Path
from typing import Any, Dict, List

import requests


BASE_URL = "https://api-gw.sports.naver.com"
DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; BaseballClassicCrawler/1.0)"


def fetch_json(url: str) -> Dict[str, Any]:
    response = requests.get(url, headers={"User-Agent": DEFAULT_USER_AGENT}, timeout=20)
    response.raise_for_status()
    return response.json()


def build_manifest(relays: Dict[int, Dict[str, Any]]) -> Dict[str, Any]:
    events: List[Dict[str, Any]] = []
    inning_counts: Dict[str, int] = {}

    for inning in range(1, 10):
        relay_data = relays.get(inning, {})
        text_relays = relay_data.get("textRelays") or []
        count = 0

        for relay in sorted(text_relays, key=lambda x: x.get("no", 0)):
            relay_no = relay.get("no")
            for option in sorted(relay.get("textOptions") or [], key=lambda x: x.get("seqno", 0)):
                count += 1
                events.append(
                    {
                        "step": len(events) + 1,
                        "inning": inning,
                        "relay_no": relay_no,
                        "seqno": option.get("seqno"),
                        "type": option.get("type"),
                        "text": option.get("text"),
                    }
                )

        inning_counts[str(inning)] = count

    return {
        "total_events": len(events),
        "inning_event_counts": inning_counts,
        "events": events,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Download baseball sample relay data")
    parser.add_argument("--game-id", default="20250902WOSK02025")
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--output-dir", default="data/mock_baseball")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    target_dir = Path(args.output_dir) / args.game_id
    target_dir.mkdir(parents=True, exist_ok=True)

    game_payload = fetch_json(f"{base_url}/schedule/games/{args.game_id}")
    (target_dir / "game.json").write_text(
        json.dumps(game_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    relays: Dict[int, Dict[str, Any]] = {}
    for inning in range(1, 10):
        relay_payload = fetch_json(f"{base_url}/schedule/games/{args.game_id}/relay?inning={inning}")
        (target_dir / f"relay_inning_{inning}.json").write_text(
            json.dumps(relay_payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        relays[inning] = relay_payload.get("result", {}).get("textRelayData", {})

    manifest = build_manifest(relays)
    (target_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"saved sample data to {target_dir}")
    print(f"total events: {manifest['total_events']}")


if __name__ == "__main__":
    main()
