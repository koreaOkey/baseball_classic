import argparse
import copy
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any, Dict, List, Tuple
from urllib.parse import parse_qs, urlparse


DEFAULT_GAME_ID = "20250902WOSK02025"


class ReplayState:
    def __init__(
        self,
        game: Dict[str, Any],
        relays_by_inning: Dict[int, Dict[str, Any]],
        step_interval: int,
        step_size: int,
    ) -> None:
        self.game = game
        self.relays_by_inning = relays_by_inning
        self.step_interval = max(1, step_interval)
        self.step_size = max(1, step_size)
        self.started_at = time.time()
        self.lock = threading.Lock()
        self.timeline = self._build_timeline()

    def _build_timeline(self) -> List[Tuple[int, int, int]]:
        timeline: List[Tuple[int, int, int]] = []
        for inning in range(1, 10):
            relay_data = self.relays_by_inning.get(inning, {})
            text_relays = relay_data.get("textRelays") or []
            for relay in sorted(text_relays, key=lambda x: x.get("no", 0)):
                relay_no = int(relay.get("no") or 0)
                for option in sorted(relay.get("textOptions") or [], key=lambda x: x.get("seqno", 0)):
                    seqno = int(option.get("seqno") or 0)
                    timeline.append((inning, relay_no, seqno))
        return timeline

    def reset(self) -> None:
        with self.lock:
            self.started_at = time.time()

    def _visible_count(self) -> int:
        with self.lock:
            elapsed = time.time() - self.started_at
        steps = int(elapsed // self.step_interval) + 1
        return min(len(self.timeline), steps * self.step_size)

    def _visible_keys(self) -> set:
        count = self._visible_count()
        return set(self.timeline[:count])

    def get_game_payload(self) -> Dict[str, Any]:
        visible = self._visible_count()
        game_copy = copy.deepcopy(self.game)

        if visible >= len(self.timeline):
            game_copy["statusCode"] = "RESULT"
            game_copy["statusInfo"] = "Replay Finished"
        else:
            game_copy["statusCode"] = "ING"
            game_copy["statusInfo"] = "Replay Running"

        game_copy["mockLiveProgress"] = {
            "visible_events": visible,
            "total_events": len(self.timeline),
            "step_interval_sec": self.step_interval,
            "step_size": self.step_size,
        }

        return {"result": {"game": game_copy}}

    def get_relay_payload(self, inning: int) -> Dict[str, Any]:
        relay_data = copy.deepcopy(self.relays_by_inning.get(inning, {}))
        visible_keys = self._visible_keys()

        filtered_relays: List[Dict[str, Any]] = []
        for relay in relay_data.get("textRelays") or []:
            relay_no = int(relay.get("no") or 0)
            options = relay.get("textOptions") or []
            relay["textOptions"] = [
                opt
                for opt in options
                if (inning, relay_no, int(opt.get("seqno") or 0)) in visible_keys
            ]
            filtered_relays.append(relay)

        relay_data["textRelays"] = filtered_relays
        relay_data["mockLiveProgress"] = {
            "visible_events": len(visible_keys),
            "total_events": len(self.timeline),
        }
        return {"result": {"textRelayData": relay_data}}


def load_sample_data(data_root: Path, game_id: str) -> Tuple[Dict[str, Any], Dict[int, Dict[str, Any]]]:
    game_path = data_root / game_id / "game.json"
    if not game_path.exists():
        raise FileNotFoundError(f"sample game file not found: {game_path}")

    game_payload = json.loads(game_path.read_text(encoding="utf-8"))
    game = game_payload.get("result", {}).get("game", {})

    relays_by_inning: Dict[int, Dict[str, Any]] = {}
    for inning in range(1, 10):
        relay_path = data_root / game_id / f"relay_inning_{inning}.json"
        if not relay_path.exists():
            relays_by_inning[inning] = {}
            continue
        relay_payload = json.loads(relay_path.read_text(encoding="utf-8"))
        relay_data = relay_payload.get("result", {}).get("textRelayData", {})
        relays_by_inning[inning] = relay_data

    return game, relays_by_inning


def make_handler(game_id: str, state: ReplayState):
    class Handler(BaseHTTPRequestHandler):
        def _send_json(self, payload: Dict[str, Any], status: int = 200) -> None:
            raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def do_GET(self) -> None:  # noqa: N802
            parsed = urlparse(self.path)
            path = parsed.path

            if path == "/":
                payload = {
                    "service": "mock_baseball_relay_server",
                    "game_id": game_id,
                    "usage": [
                        f"/schedule/games/{game_id}",
                        f"/schedule/games/{game_id}/relay?inning=1",
                        "/control/reset",
                    ],
                }
                self._send_json(payload)
                return

            if path == "/control/reset":
                state.reset()
                self._send_json({"ok": True, "message": "replay reset"})
                return

            if path == f"/schedule/games/{game_id}":
                self._send_json(state.get_game_payload())
                return

            if path == f"/schedule/games/{game_id}/relay":
                query = parse_qs(parsed.query)
                inning_values = query.get("inning") or []
                inning = int(inning_values[0]) if inning_values else 1
                self._send_json(state.get_relay_payload(inning))
                return

            self._send_json({"error": "not found", "path": path}, status=404)

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description="Mock live baseball relay API server")
    parser.add_argument("--game-id", default=DEFAULT_GAME_ID)
    parser.add_argument("--data-dir", default="data/mock_baseball")
    parser.add_argument("--port", type=int, default=8011)
    parser.add_argument("--step-interval", type=int, default=10)
    parser.add_argument(
        "--step-size",
        type=int,
        default=25,
        help="number of text options to reveal per interval",
    )
    args = parser.parse_args()

    data_root = Path(args.data_dir)
    game, relays_by_inning = load_sample_data(data_root=data_root, game_id=args.game_id)
    state = ReplayState(
        game=game,
        relays_by_inning=relays_by_inning,
        step_interval=args.step_interval,
        step_size=args.step_size,
    )

    handler = make_handler(game_id=args.game_id, state=state)
    server = HTTPServer(("0.0.0.0", args.port), handler)

    total = len(state.timeline)
    print(f"Mock baseball relay server running on http://localhost:{args.port}")
    print(f"Game ID: {args.game_id}")
    print(f"Timeline events: {total}, interval={args.step_interval}s, step_size={args.step_size}")
    server.serve_forever()


if __name__ == "__main__":
    main()
