import argparse
import json
import threading
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any, Dict, Optional

import pandas as pd

from backend_sender import build_snapshot_payload, post_snapshot_to_backend
from crawler import BASE_URL, crawl_once_detailed, save_excel


CACHE_LOCK = threading.Lock()
CACHE: Dict[str, Any] = {
    "last_updated": None,
    "game": None,
    "parsed": None,
    "error": None,
    "excel_path": None,
    "json_path": None,
    "source_base_url": None,
    "backend_base_url": None,
    "backend_result": None,
}


def ensure_parent(path: Optional[str]) -> None:
    if not path:
        return
    Path(path).parent.mkdir(parents=True, exist_ok=True)


def save_json(path: str, game: Dict[str, Any], parsed: Dict[str, Any]) -> None:
    payload = {
        "updated_at": datetime.now().isoformat(timespec="seconds"),
        "game": game,
        "parsed": parsed,
    }
    Path(path).write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def update_loop(
    game_id: str,
    interval: int,
    source_base_url: str,
    output_excel: Optional[str],
    output_json: Optional[str],
    backend_base_url: Optional[str],
    backend_api_key: Optional[str],
    backend_timeout: float,
) -> None:
    if backend_base_url and not backend_api_key:
        raise ValueError("--backend-api-key is required when --backend-base-url is set")

    ensure_parent(output_excel)
    ensure_parent(output_json)

    while True:
        try:
            game, relays_by_inning, parsed = crawl_once_detailed(game_id=game_id, base_url=source_base_url)
            if output_excel:
                save_excel(output_excel, game, parsed)
            if output_json:
                save_json(output_json, game, parsed)

            backend_result = None
            if backend_base_url and backend_api_key:
                snapshot = build_snapshot_payload(game_data=game, relays_by_inning=relays_by_inning)
                backend_result = post_snapshot_to_backend(
                    backend_base_url=backend_base_url,
                    api_key=backend_api_key,
                    game_id=game_id,
                    payload=snapshot,
                    timeout=backend_timeout,
                )

            with CACHE_LOCK:
                CACHE["game"] = game
                CACHE["parsed"] = parsed
                CACHE["last_updated"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                CACHE["error"] = None
                CACHE["excel_path"] = output_excel
                CACHE["json_path"] = output_json
                CACHE["source_base_url"] = source_base_url
                CACHE["backend_base_url"] = backend_base_url
                CACHE["backend_result"] = backend_result
        except Exception as exc:  # pylint: disable=broad-except
            with CACHE_LOCK:
                CACHE["error"] = str(exc)
                CACHE["last_updated"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        time.sleep(interval)


def render_html() -> str:
    with CACHE_LOCK:
        game = CACHE.get("game")
        parsed = CACHE.get("parsed")
        error = CACHE.get("error")
        last_updated = CACHE.get("last_updated")
        excel_path = CACHE.get("excel_path")
        json_path = CACHE.get("json_path")
        source_base_url = CACHE.get("source_base_url")
        backend_base_url = CACHE.get("backend_base_url")
        backend_result = CACHE.get("backend_result")

    if error and not parsed:
        return f"<html><body><h2>Error</h2><pre>{error}</pre></body></html>"

    if not parsed:
        return "<html><body><h2>Waiting for first crawl...</h2></body></html>"

    match_df = pd.DataFrame(
        [
            {
                "game_id": game.get("gameId"),
                "status": game.get("statusCode"),
                "home_team": game.get("homeTeamName") or game.get("homeTeamShortName"),
                "away_team": game.get("awayTeamName") or game.get("awayTeamShortName"),
                "home_score": game.get("homeTeamScore"),
                "away_score": game.get("awayTeamScore"),
            }
        ]
    )
    at_bats_df = pd.DataFrame(parsed.get("at_bats") or []).tail(200)
    pinch_df = pd.DataFrame(parsed.get("pinch_hitters") or []).tail(100)
    pitcher_df = pd.DataFrame(parsed.get("pitcher_changes") or []).tail(100)

    notes = []
    notes.append(f"<p>Last Updated: {last_updated}</p>")
    notes.append(f"<p>Source Base URL: {source_base_url}</p>")
    if excel_path:
        notes.append(f"<p>Excel Output: {excel_path}</p>")
    if json_path:
        notes.append(f"<p>JSON Output: {json_path}</p>")
    if backend_base_url:
        notes.append(f"<p>Backend Base URL: {backend_base_url}</p>")
    if backend_result:
        notes.append(
            "<p>Backend Ingest: "
            f"received={backend_result.get('receivedEvents')}, "
            f"inserted={backend_result.get('insertedEvents')}, "
            f"duplicates={backend_result.get('duplicateEvents')}"
            "</p>"
        )
    if error:
        notes.append(f"<p style='color:red;'>Last Error: {error}</p>")

    return f"""
    <html>
      <head>
        <meta http-equiv="refresh" content="10">
        <title>Live Baseball Relay Crawler</title>
        <style>
          body {{ font-family: Arial, sans-serif; margin: 20px; }}
          table {{ border-collapse: collapse; margin-bottom: 20px; width: 100%; }}
          th, td {{ border: 1px solid #ddd; padding: 6px 10px; font-size: 12px; }}
          th {{ background: #f3f3f3; }}
          pre {{ background: #fafafa; border: 1px solid #ddd; padding: 10px; }}
        </style>
      </head>
      <body>
        <h2>Live Baseball Relay Crawler</h2>
        {''.join(notes)}
        <h3>Match</h3>
        {match_df.to_html(index=False)}
        <h3>At Bats (last 200)</h3>
        {at_bats_df.to_html(index=False)}
        <h3>Pinch Hitters (last 100)</h3>
        {pinch_df.to_html(index=False)}
        <h3>Pitcher Changes (last 100)</h3>
        {pitcher_df.to_html(index=False)}
      </body>
    </html>
    """


class LiveHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        if self.path not in ("/", "/index.html"):
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")
            return

        html = render_html().encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(html)))
        self.end_headers()
        self.wfile.write(html)


def main() -> None:
    parser = argparse.ArgumentParser(description="Live baseball crawler monitor server")
    parser.add_argument("--game-id", required=True, help="example: 20250902WOSK02025")
    parser.add_argument("--interval", type=int, default=10)
    parser.add_argument("--port", type=int, default=8010)
    parser.add_argument("--source-base-url", default=BASE_URL)
    parser.add_argument("--output-excel", default="data/baseball_live_output.xlsx")
    parser.add_argument("--output-json", default="data/baseball_live_output.json")
    parser.add_argument("--backend-base-url", help="backend ingest base url, example: http://localhost:8080")
    parser.add_argument("--backend-api-key", help="backend ingest X-API-Key value")
    parser.add_argument("--backend-timeout", type=float, default=10.0)
    args = parser.parse_args()
    if args.backend_base_url and not args.backend_api_key:
        parser.error("--backend-api-key is required when --backend-base-url is set")

    thread = threading.Thread(
        target=update_loop,
        args=(
            args.game_id,
            args.interval,
            args.source_base_url.rstrip("/"),
            args.output_excel,
            args.output_json,
            args.backend_base_url.rstrip("/") if args.backend_base_url else None,
            args.backend_api_key,
            args.backend_timeout,
        ),
        daemon=True,
    )
    thread.start()

    server = HTTPServer(("0.0.0.0", args.port), LiveHandler)
    print(f"Live baseball crawler monitor running on http://localhost:{args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
