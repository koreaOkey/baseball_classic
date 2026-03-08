from __future__ import annotations

import argparse
import logging
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import date, datetime, time as dt_time, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import requests


KST = ZoneInfo("Asia/Seoul")
LIVE_STATUS_CODES = {"LIVE", "ING", "PLAYING", "IN_PROGRESS", "STARTED"}
FINAL_STATUS_CODES = {"RESULT", "FINAL", "END", "FINISHED"}
LOGGER = logging.getLogger("wbc_dispatcher")


@dataclass
class RelayCheckWindow:
    game_id: str
    start_at: datetime
    checks_done: int = 0
    launched: bool = False
    exhausted: bool = False
    next_check_at: datetime | None = None

    def __post_init__(self) -> None:
        if self.next_check_at is None:
            self.next_check_at = self.start_at


@dataclass
class RunningCrawler:
    game_id: str
    process: subprocess.Popen[str]
    log_path: Path
    log_handle: Any
    started_at: datetime


def _setup_logging(log_dir: Path) -> Path:
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"dispatcher_{datetime.now(KST):%Y%m%d}.log"

    LOGGER.setLevel(logging.INFO)
    LOGGER.handlers.clear()
    LOGGER.propagate = False

    formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")

    file_handler = logging.FileHandler(log_path, encoding="utf-8")
    file_handler.setFormatter(formatter)
    LOGGER.addHandler(file_handler)

    stream_handler = logging.StreamHandler(sys.stdout)
    stream_handler.setFormatter(formatter)
    LOGGER.addHandler(stream_handler)

    return log_path


def _safe_json_get(url: str, timeout: float) -> Any | None:
    try:
        response = requests.get(
            url,
            headers={"User-Agent": "Mozilla/5.0 (compatible; WBCDispatcher/1.0)"},
            timeout=timeout,
        )
        response.raise_for_status()
        payload = response.json()
        if isinstance(payload, (dict, list)):
            return payload
    except Exception:
        return None
    return None


def _parse_hhmm(raw: str) -> dt_time | None:
    text = (raw or "").strip()
    if len(text) != 5 or text[2] != ":":
        return None
    hh_raw = text[:2]
    mm_raw = text[3:]
    if not (hh_raw.isdigit() and mm_raw.isdigit()):
        return None
    hh = int(hh_raw)
    mm = int(mm_raw)
    if not (0 <= hh <= 23 and 0 <= mm <= 59):
        return None
    return dt_time(hour=hh, minute=mm)


def _load_today_windows_from_backend(
    backend_base_url: str,
    target_date: date,
    timeout: float,
) -> dict[str, RelayCheckWindow]:
    endpoint = f"{backend_base_url.rstrip('/')}/games?date={target_date.isoformat()}&limit=100"
    payload = _safe_json_get(endpoint, timeout=timeout)
    if payload is None or not isinstance(payload, list):
        return {}

    windows: dict[str, RelayCheckWindow] = {}
    for item in payload:
        if not isinstance(item, dict):
            continue
        game_id = str(item.get("id") or "").strip()
        start_time_raw = str(item.get("startTime") or "").strip()
        if not game_id or not start_time_raw:
            continue
        parsed_time = _parse_hhmm(start_time_raw)
        if parsed_time is None:
            continue

        start_at = datetime.combine(target_date, parsed_time, tzinfo=KST)
        windows[game_id] = RelayCheckWindow(game_id=game_id, start_at=start_at)
    return windows


def _relay_is_available(source_base_url: str, game_id: str, timeout: float) -> bool:
    game_url = f"{source_base_url.rstrip('/')}/schedule/games/{game_id}"
    relay_url = f"{source_base_url.rstrip('/')}/schedule/games/{game_id}/relay?inning=1"

    game_payload = _safe_json_get(game_url, timeout=timeout)
    relay_payload = _safe_json_get(relay_url, timeout=timeout)
    if not isinstance(game_payload, dict) or not isinstance(relay_payload, dict):
        return False

    game = (game_payload.get("result") or {}).get("game") or {}
    relay_data = (relay_payload.get("result") or {}).get("textRelayData") or {}
    status_code = str(game.get("statusCode") or "").strip().upper()
    if status_code in FINAL_STATUS_CODES:
        return False

    text_relays = relay_data.get("textRelays") or []
    if isinstance(text_relays, list) and len(text_relays) > 0:
        return True

    # Some games expose relay/live metadata before first relay text appears.
    if status_code in LIVE_STATUS_CODES:
        game_center = game.get("gameCenterUrl") or {}
        if isinstance(game_center, dict) and game_center.get("relayTabUrl"):
            return True
        live_list = game.get("liveList") or []
        if isinstance(live_list, list) and len(live_list) > 0:
            return True
        if game.get("manualRelayUrl"):
            return True

    return False


def _run_schedule_import(
    repo_root: Path,
    python_executable: str,
    target_date: date,
    section_id: str,
    category_id: str,
) -> bool:
    script_path = repo_root / "backend" / "api" / "scripts" / "import_wbc_schedule.py"
    cmd = [
        python_executable,
        str(script_path),
        "--date",
        target_date.isoformat(),
        "--section-id",
        section_id,
        "--category-id",
        category_id,
    ]
    LOGGER.info("[import] run date=%s", target_date.isoformat())
    completed = subprocess.run(
        cmd,
        cwd=str(repo_root),
        text=True,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
        env={**os.environ, "PYTHONIOENCODING": "utf-8"},
    )
    if completed.stdout:
        LOGGER.info("[import][stdout]\n%s", completed.stdout.rstrip())
    if completed.stderr:
        LOGGER.info("[import][stderr]\n%s", completed.stderr.rstrip())
    LOGGER.info("[import] exit=%s", completed.returncode)
    return completed.returncode == 0


def _start_crawler(
    repo_root: Path,
    python_executable: str,
    game_id: str,
    source_base_url: str,
    crawler_interval_sec: int,
    backend_base_url: str,
    backend_api_key: str,
    log_dir: Path,
) -> RunningCrawler:
    crawler_script = repo_root / "crawler" / "crawler.py"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"live_crawler_{game_id}.log"
    log_handle = log_path.open("a", encoding="utf-8")

    cmd = [
        python_executable,
        str(crawler_script),
        "--game-id",
        game_id,
        "--base-url",
        source_base_url.rstrip("/"),
        "--watch",
        "--interval",
        str(crawler_interval_sec),
        "--backend-base-url",
        backend_base_url.rstrip("/"),
        "--backend-api-key",
        backend_api_key,
        "--output",
        str(log_dir / f"relay_{game_id}.xlsx"),
    ]
    process = subprocess.Popen(
        cmd,
        cwd=str(repo_root),
        stdout=log_handle,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        env={**os.environ, "PYTHONIOENCODING": "utf-8"},
    )
    return RunningCrawler(
        game_id=game_id,
        process=process,
        log_path=log_path,
        log_handle=log_handle,
        started_at=datetime.now(KST),
    )


def _cleanup_finished_processes(running: dict[str, RunningCrawler]) -> None:
    for game_id, running_crawler in list(running.items()):
        code = running_crawler.process.poll()
        if code is None:
            continue
        running_crawler.log_handle.close()
        LOGGER.info("[crawler] stopped gameId=%s exit=%s log=%s", game_id, code, running_crawler.log_path)
        del running[game_id]


def run_dispatcher(args: argparse.Namespace) -> None:
    repo_root = Path(__file__).resolve().parents[1]
    python_executable = sys.executable
    log_dir = repo_root / args.log_dir
    dispatcher_log_path = _setup_logging(log_dir)
    LOGGER.info("[dispatcher] started pid=%s log=%s", os.getpid(), dispatcher_log_path)

    windows: dict[str, RelayCheckWindow] = {}
    running: dict[str, RunningCrawler] = {}
    imported_date: date | None = None
    last_import_attempt_at: datetime | None = None

    def refresh_windows(target_date: date) -> None:
        nonlocal windows
        loaded = _load_today_windows_from_backend(
            backend_base_url=args.backend_base_url,
            target_date=target_date,
            timeout=args.http_timeout_sec,
        )
        merged: dict[str, RelayCheckWindow] = {}
        for game_id, loaded_window in loaded.items():
            existing = windows.get(game_id)
            if existing is None:
                merged[game_id] = loaded_window
                continue
            if existing.launched or existing.exhausted:
                merged[game_id] = existing
                continue
            existing.start_at = loaded_window.start_at
            if existing.next_check_at is None:
                existing.next_check_at = loaded_window.start_at
            merged[game_id] = existing
        windows = merged
        preview_ids = ",".join(sorted(windows.keys())[:8])
        LOGGER.info(
            "[dispatcher] windows_loaded=%s date=%s preview=%s",
            len(windows),
            target_date.isoformat(),
            preview_ids,
        )

    now = datetime.now(KST)
    refresh_windows(now.date())

    while True:
        now = datetime.now(KST)
        _cleanup_finished_processes(running)

        import_trigger = datetime.combine(
            now.date(),
            dt_time(hour=args.schedule_hour, minute=args.schedule_minute),
            tzinfo=KST,
        )
        if now >= import_trigger and imported_date != now.date():
            should_attempt = (
                last_import_attempt_at is None
                or now - last_import_attempt_at >= timedelta(seconds=args.import_retry_interval_sec)
            )
            if should_attempt:
                last_import_attempt_at = now
                if _run_schedule_import(
                    repo_root=repo_root,
                    python_executable=python_executable,
                    target_date=now.date(),
                    section_id=args.section_id,
                    category_id=args.category_id,
                ):
                    imported_date = now.date()
                    refresh_windows(now.date())

        check_window = timedelta(minutes=args.relay_check_minutes)
        for game_id, window in list(windows.items()):
            if window.launched or window.exhausted:
                continue
            if game_id in running:
                window.launched = True
                continue
            if window.next_check_at is None:
                window.next_check_at = window.start_at
            if now < window.start_at:
                continue
            if now >= window.start_at + check_window:
                window.exhausted = True
                LOGGER.info("[relay] expired gameId=%s checks=%s", game_id, window.checks_done)
                continue
            if now < window.next_check_at:
                continue

            window.checks_done += 1
            available = _relay_is_available(
                source_base_url=args.source_base_url,
                game_id=game_id,
                timeout=args.http_timeout_sec,
            )
            LOGGER.info(
                "[relay] gameId=%s check=%s/%s available=%s",
                game_id,
                window.checks_done,
                args.relay_check_minutes,
                available,
            )
            if available:
                running_crawler = _start_crawler(
                    repo_root=repo_root,
                    python_executable=python_executable,
                    game_id=game_id,
                    source_base_url=args.source_base_url,
                    crawler_interval_sec=args.crawler_interval_sec,
                    backend_base_url=args.backend_base_url,
                    backend_api_key=args.backend_api_key,
                    log_dir=log_dir,
                )
                running[game_id] = running_crawler
                window.launched = True
                LOGGER.info(
                    "[crawler] started gameId=%s pid=%s log=%s",
                    game_id,
                    running_crawler.process.pid,
                    running_crawler.log_path,
                )
            else:
                window.next_check_at = window.next_check_at + timedelta(minutes=1)

        time.sleep(args.dispatch_interval_sec)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Daily WBC dispatcher: imports schedule at 00:05 KST and checks relay "
            "availability every minute for 10 minutes from each game's start time."
        )
    )
    parser.add_argument("--backend-base-url", required=True, help="Backend API base URL, e.g. http://localhost:8080")
    parser.add_argument("--backend-api-key", required=True, help="Backend ingest API key")
    parser.add_argument("--source-base-url", default="https://api-gw.sports.naver.com")
    parser.add_argument("--section-id", default="wbaseball")
    parser.add_argument("--category-id", default="wbc")
    parser.add_argument("--schedule-hour", type=int, default=0)
    parser.add_argument("--schedule-minute", type=int, default=5)
    parser.add_argument("--relay-check-minutes", type=int, default=10)
    parser.add_argument("--dispatch-interval-sec", type=int, default=15)
    parser.add_argument("--crawler-interval-sec", type=int, default=10)
    parser.add_argument("--import-retry-interval-sec", type=int, default=300)
    parser.add_argument("--http-timeout-sec", type=float, default=10.0)
    parser.add_argument("--log-dir", default="log")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    run_dispatcher(args)


if __name__ == "__main__":
    main()
