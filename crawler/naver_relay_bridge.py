"""네이버 relay 브리지 (맥미니 상주, 127.0.0.1 전용).

2026-11-07: 네이버가 /schedule/games/{id}/relay 만 브라우저가 아닌 요청에 응답하지 않는다(경기 상세·preview 는
정상이고, 실제 브라우저에서는 문자중계가 보인다). 실제 Chromium 에 네이버 중계 페이지를 띄워 두고, 크롤러가
요청한 api-gw URL 을 그 페이지 안에서 fetch 해 응답을 그대로 돌려준다. 크롤러는 naver_http 에서 relay URL 만
이리로 보낸다(NAVER_RELAY_BRIDGE_URL).

GET /fetch?url=<https://api-gw.sports.naver.com/...>  → 네이버 응답 본문과 상태코드 그대로 (실패 시 502)
GET /health                                            → {"ok": true, "page": "<현재 페이지 URL>"}

실행: .venv/bin/python crawler/naver_relay_bridge.py [--port 8790] [--profile-dir browser-profile] [--headless]
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from logging.handlers import RotatingFileHandler
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ALLOWED_HOST = "api-gw.sports.naver.com"
DEFAULT_PAGE = "https://m.sports.naver.com/kbaseball/index"
GAME_RELAY_PAGE = "https://m.sports.naver.com/game/{game_id}/relay"
GAME_ID_PATTERN = re.compile(r"^/schedule/games/([0-9A-Za-z]+)/")
PAGE_TIMEOUT_MS = 20_000
FETCH_JS = """async (url) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(url, {credentials: 'include', signal: controller.signal});
    return {status: response.status, body: await response.text()};
  } catch (error) {
    return {status: 0, body: String(error)};
  } finally {
    clearTimeout(timer);
  }
}"""

LOGGER = logging.getLogger("relay_bridge")


def validate_target(url: str) -> str | None:
    """브리지가 대신 요청할 수 있는 URL 인지. 오픈 프록시가 되지 않도록 api-gw https 만 허용한다."""
    parsed = urlparse(url or "")
    if parsed.scheme != "https" or parsed.netloc != ALLOWED_HOST:
        return None
    return url


def game_id_of(url: str) -> str | None:
    match = GAME_ID_PATTERN.match(urlparse(url).path)
    return match.group(1) if match else None


class Bridge:
    def __init__(self, profile_dir: Path, headless: bool) -> None:
        from playwright.sync_api import sync_playwright

        self._playwright = sync_playwright().start()
        self._context = self._playwright.chromium.launch_persistent_context(
            str(profile_dir),
            headless=headless,
            locale="ko-KR",
            viewport={"width": 430, "height": 900},
        )
        self._page = self._context.pages[0] if self._context.pages else self._context.new_page()
        self._game_id: str | None = None
        self.broken = False
        self._open(DEFAULT_PAGE)

    @property
    def page_url(self) -> str:
        return self._page.url

    def _open(self, url: str) -> None:
        self._page.goto(url, wait_until="domcontentloaded", timeout=PAGE_TIMEOUT_MS)

    def fetch(self, url: str) -> tuple[int, str]:
        from playwright.sync_api import Error as PlaywrightError

        try:
            game_id = game_id_of(url)
            if game_id and game_id != self._game_id:
                # 그 경기 중계 페이지에서 요청해야 웹과 같은 조건이 된다
                self._open(GAME_RELAY_PAGE.format(game_id=game_id))
                self._game_id = game_id
            result = self._page.evaluate(FETCH_JS, url)
            if result.get("status") == 0:
                LOGGER.warning("fetch_failed url=%s error=%s; reloading page", url, str(result.get("body"))[:120])
                self._open(self._page.url)
                result = self._page.evaluate(FETCH_JS, url)
            return int(result.get("status") or 0), str(result.get("body") or "")
        except PlaywrightError as exc:
            # 브라우저가 죽었으면 프로세스를 끝내 launchd 가 다시 띄우게 한다
            LOGGER.error("playwright_error url=%s error=%s", url, str(exc)[:200])
            self.broken = True
            return 0, str(exc)


def make_handler(bridge: Bridge) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        def _send(self, status: int, body: str) -> None:
            data = body.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self) -> None:  # noqa: N802
            parsed = urlparse(self.path)
            if parsed.path == "/health":
                self._send(200, json.dumps({"ok": not bridge.broken, "page": bridge.page_url}))
                return
            if parsed.path != "/fetch":
                self._send(404, '{"error":"not found"}')
                return
            target = validate_target((parse_qs(parsed.query).get("url") or [""])[0])
            if target is None:
                self._send(400, '{"error":"only https://api-gw.sports.naver.com URLs are allowed"}')
                return
            status, body = bridge.fetch(target)
            if status == 0:
                self._send(502, json.dumps({"error": body[:300]}))
            else:
                if status != 200:
                    LOGGER.warning("upstream_status url=%s status=%s", target, status)
                self._send(status, body)

        def log_message(self, format: str, *args) -> None:  # noqa: A002
            return  # 요청마다 로그를 남기지 않는다 (실패만 LOGGER 로)

    return Handler


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=8790)
    parser.add_argument("--profile-dir", default="browser-profile")
    parser.add_argument("--log-file", default="log/relay-bridge.log")
    parser.add_argument("--headless", action="store_true", help="창 없이 실행 (기본은 실제 창을 띄움)")
    args = parser.parse_args()

    Path(args.log_file).parent.mkdir(parents=True, exist_ok=True)
    handler = RotatingFileHandler(args.log_file, maxBytes=1_000_000, backupCount=3, encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(message)s"))
    LOGGER.addHandler(handler)
    LOGGER.addHandler(logging.StreamHandler(sys.stderr))
    LOGGER.setLevel(logging.INFO)

    bridge = Bridge(Path(args.profile_dir).resolve(), headless=args.headless)
    server = HTTPServer(("127.0.0.1", args.port), make_handler(bridge))
    LOGGER.info("relay bridge listening on 127.0.0.1:%s page=%s", args.port, bridge.page_url)
    while not bridge.broken:
        server.handle_request()
    LOGGER.error("bridge broken; exiting for launchd restart")
    return 1


if __name__ == "__main__":
    sys.exit(main())
