import argparse
import threading
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any, Dict, Optional

import pandas as pd

from crawler_tennis import BASE_URL, fetch_json, parse_tennis, save_excel


CACHE_LOCK = threading.Lock()
CACHE: Dict[str, Any] = {
    "last_updated": None,
    "parsed": None,
    "excel_path": None,
}


def update_loop(game_id: str, interval: int, output_path: Optional[str]) -> None:
    game_url = f"{BASE_URL}/schedule/games/{game_id}"
    while True:
        game_data = fetch_json(game_url).get("result", {}).get("game", {})
        parsed = parse_tennis(game_data)

        with CACHE_LOCK:
            CACHE["parsed"] = parsed
            CACHE["last_updated"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        if output_path:
            save_excel(output_path, parsed)
            with CACHE_LOCK:
                CACHE["excel_path"] = output_path

        time.sleep(interval)


def render_html() -> str:
    with CACHE_LOCK:
        parsed = CACHE.get("parsed")
        last_updated = CACHE.get("last_updated")
        excel_path = CACHE.get("excel_path")

    if not parsed:
        return "<html><body><h2>대기 중...</h2></body></html>"

    match_df = pd.DataFrame(parsed["match"])
    sets_df = pd.DataFrame(parsed["sets"])
    games_df = pd.DataFrame(parsed["games"])
    points_df = pd.DataFrame(parsed["points"]).head(200)

    excel_link = ""
    if excel_path:
        excel_link = f"<p>엑셀 저장 경로: {excel_path}</p>"

    return f"""
    <html>
      <head>
        <meta http-equiv="refresh" content="10">
        <title>Live Tennis Relay</title>
        <style>
          body {{ font-family: Arial, sans-serif; margin: 20px; }}
          table {{ border-collapse: collapse; margin-bottom: 20px; }}
          th, td {{ border: 1px solid #ddd; padding: 6px 10px; }}
          th {{ background: #f3f3f3; }}
        </style>
      </head>
      <body>
        <h2>테니스 라이브 중계</h2>
        <p>마지막 갱신: {last_updated}</p>
        {excel_link}
        <h3>Match</h3>
        {match_df.to_html(index=False)}
        <h3>Sets</h3>
        {sets_df.to_html(index=False)}
        <h3>Games</h3>
        {games_df.to_html(index=False)}
        <h3>Points (최근 200개)</h3>
        {points_df.to_html(index=False)}
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
    parser = argparse.ArgumentParser(description="Live tennis relay server")
    parser.add_argument("--game-id", required=True, help="예: eXzIlhIXM5IFA4n")
    parser.add_argument("--interval", type=int, default=10)
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--output", help="엑셀 저장 경로(갱신 시 덮어씀)")
    args = parser.parse_args()

    thread = threading.Thread(
        target=update_loop,
        args=(args.game_id, args.interval, args.output),
        daemon=True,
    )
    thread.start()

    server = HTTPServer(("0.0.0.0", args.port), LiveHandler)
    print(f"Live server running on http://localhost:{args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
