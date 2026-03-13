from __future__ import annotations

import argparse
import logging
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import UTC, date, datetime, time as dt_time, timedelta
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse
from zoneinfo import ZoneInfo

import requests


KST = ZoneInfo("Asia/Seoul")
LIVE_STATUS_CODES = {"LIVE", "ING", "PLAYING", "IN_PROGRESS", "STARTED"}
FINAL_STATUS_CODES = {"RESULT", "FINAL", "END", "FINISHED"}
LOGGER = logging.getLogger("baseball_dispatcher")
LEAGUE_PRESETS: dict[str, tuple[str, str]] = {
    "wbc": ("wbaseball", "wbc"),
    "kbo": ("kbaseball", "kbo"),
}
KBO_GAME_ID_PATTERN = re.compile(r"^\d{8}[A-Z]{4}\d{5}$")


@dataclass(frozen=True)
class ScheduleTarget:
    section_id: str
    category_id: str
    source: str


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
            headers={"User-Agent": "Mozilla/5.0 (compatible; BaseballDispatcher/1.0)"},
            timeout=timeout,
        )
        response.raise_for_status()
        payload = response.json()
        if isinstance(payload, (dict, list)):
            return payload
    except Exception:
        return None
    return None


def _parse_schedule_url(schedule_url: str) -> tuple[str | None, str | None]:
    parsed = urlparse((schedule_url or "").strip())
    if not parsed.netloc:
        return None, None

    path_parts = [part.strip() for part in parsed.path.split("/") if part.strip()]
    section_id = path_parts[0] if path_parts else None

    query = parse_qs(parsed.query)
    category_candidates = query.get("category") or query.get("categoryId") or []
    category_id = category_candidates[0].strip() if category_candidates else None
    if not category_id:
        category_id = None

    return section_id, category_id


def _resolve_schedule_filters(args: argparse.Namespace) -> tuple[str, str]:
    section_id: str | None = None
    category_id: str | None = None

    league = str(args.league or "").strip().lower()
    if league:
        preset = LEAGUE_PRESETS.get(league)
        if preset is not None:
            section_id, category_id = preset

    if args.schedule_url:
        parsed_section, parsed_category = _parse_schedule_url(args.schedule_url)
        if parsed_section:
            section_id = parsed_section
        if parsed_category:
            category_id = parsed_category

    if args.section_id:
        section_id = str(args.section_id).strip()
    if args.category_id:
        category_id = str(args.category_id).strip()

    return section_id or "wbaseball", category_id or "wbc"


def _resolve_schedule_targets(args: argparse.Namespace) -> list[ScheduleTarget]:
    targets: list[ScheduleTarget] = []
    seen: set[tuple[str, str]] = set()

    for raw in getattr(args, "schedule_target", []) or []:
        token = str(raw or "").strip()
        if not token:
            continue

        section_id: str | None = None
        category_id: str | None = None
        source = token

        preset = LEAGUE_PRESETS.get(token.lower())
        if preset is not None:
            section_id, category_id = preset
        elif token.startswith("http://") or token.startswith("https://"):
            section_id, category_id = _parse_schedule_url(token)
        elif ":" in token:
            parts = [part.strip() for part in token.split(":", 1)]
            if len(parts) == 2 and parts[0] and parts[1]:
                section_id, category_id = parts[0], parts[1]

        if not section_id or not category_id:
            LOGGER.warning("[dispatcher] invalid schedule target ignored: %s", token)
            continue

        key = (section_id, category_id)
        if key in seen:
            continue
        seen.add(key)
        targets.append(ScheduleTarget(section_id=section_id, category_id=category_id, source=source))

    if targets:
        return targets

    section_id, category_id = _resolve_schedule_filters(args)
    return [ScheduleTarget(section_id=section_id, category_id=category_id, source="legacy-options")]


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


def _preview_has_lineup(preview_payload: Any | None) -> bool:
    if not isinstance(preview_payload, dict):
        return False

    result = preview_payload.get("result") or {}
    if not isinstance(result, dict):
        return False
    preview_data = result.get("previewData") or {}
    if not isinstance(preview_data, dict):
        return False

    for team_key in ("homeTeamLineUp", "awayTeamLineUp"):
        lineup = preview_data.get(team_key)
        if not isinstance(lineup, dict):
            continue

        for list_key in ("fullLineUp", "batterCandidate", "pitcherBullpen"):
            players = lineup.get(list_key) or []
            if isinstance(players, list) and len(players) > 0:
                return True

    for starter_key in ("homeStarter", "awayStarter"):
        starter = preview_data.get(starter_key)
        if isinstance(starter, dict) and any(starter.get(key) for key in ("name", "playerName", "pcode", "playerId")):
            return True

    return False


def _relay_is_available(
    source_base_url: str,
    game_id: str,
    timeout: float,
    *,
    enable_preview_lineup_precheck: bool = False,
) -> tuple[bool, bool]:
    game_url = f"{source_base_url.rstrip('/')}/schedule/games/{game_id}"
    relay_url = f"{source_base_url.rstrip('/')}/schedule/games/{game_id}/relay?inning=1"
    preview_url = f"{source_base_url.rstrip('/')}/schedule/games/{game_id}/preview"

    game_payload = _safe_json_get(game_url, timeout=timeout)
    relay_payload = _safe_json_get(relay_url, timeout=timeout)
    if not isinstance(game_payload, dict) or not isinstance(relay_payload, dict):
        return False, False

    game = (game_payload.get("result") or {}).get("game") or {}
    relay_data = (relay_payload.get("result") or {}).get("textRelayData") or {}
    status_code = str(game.get("statusCode") or "").strip().upper()
    if status_code in FINAL_STATUS_CODES:
        return False, True

    text_relays = relay_data.get("textRelays") or []
    if isinstance(text_relays, list) and len(text_relays) > 0:
        return True, False

    # Before first pitch, some games expose lineup/entry rows without relay text.
    for lineup_key in ("homeLineup", "awayLineup", "homeEntry", "awayEntry"):
        lineup_data = relay_data.get(lineup_key)
        if not isinstance(lineup_data, dict):
            continue
        batters = lineup_data.get("batter") or []
        pitchers = lineup_data.get("pitcher") or []
        if (isinstance(batters, list) and len(batters) > 0) or (
            isinstance(pitchers, list) and len(pitchers) > 0
        ):
            return True, False

    game_center = game.get("gameCenterUrl") or {}
    if isinstance(game_center, dict):
        if game_center.get("lineupTabUrl") or game_center.get("relayTabUrl"):
            return True, False

    # Some games expose relay/live metadata before first relay text appears.
    if status_code in LIVE_STATUS_CODES:
        live_list = game.get("liveList") or []
        if isinstance(live_list, list) and len(live_list) > 0:
            return True, False
        if game.get("manualRelayUrl"):
            return True, False

    if enable_preview_lineup_precheck:
        preview_payload = _safe_json_get(preview_url, timeout=timeout)
        if _preview_has_lineup(preview_payload):
            return True, False

    return False, False


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def _safe_float(value: Any, default: float | None = None) -> float | None:
    if value is None:
        return default
    raw = str(value).strip()
    if not raw:
        return default
    try:
        return float(raw)
    except (TypeError, ValueError):
        return default


def _map_schedule_status(status_code: Any) -> str:
    raw = str(status_code or "").strip().upper()
    if raw in LIVE_STATUS_CODES:
        return "LIVE"
    if raw in FINAL_STATUS_CODES:
        return "FINISHED"
    return "SCHEDULED"


def _extract_schedule_inning(game: dict[str, Any]) -> str:
    status_info = str(game.get("statusInfo") or "").strip()
    if status_info:
        return status_info

    game_date_time = str(game.get("gameDateTime") or "").strip()
    if game_date_time:
        try:
            return datetime.fromisoformat(game_date_time).strftime("%H:%M")
        except ValueError:
            if len(game_date_time) >= 5 and game_date_time[2] == ":":
                return game_date_time[:5]
    return "-"


def _extract_schedule_start_time(game: dict[str, Any]) -> str | None:
    raw = str(game.get("gameDateTime") or "").strip()
    if not raw:
        return None
    try:
        return datetime.fromisoformat(raw).strftime("%H:%M")
    except ValueError:
        if len(raw) >= 5 and raw[2] == ":":
            return raw[:5]
        return None


def _extract_schedule_game_date(game: dict[str, Any]) -> str | None:
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


def _fetch_schedule_games(
    *,
    source_base_url: str,
    section_id: str,
    category_id: str,
    target_date: date,
    timeout: float,
) -> list[dict[str, Any]]:
    try:
        calendar_games = _fetch_schedule_games_via_calendar(
            source_base_url=source_base_url,
            section_id=section_id,
            category_id=category_id,
            target_date=target_date,
            timeout=timeout,
        )
        if calendar_games:
            return calendar_games
    except Exception as exc:
        LOGGER.warning(
            "[import] calendar_fetch_failed date=%s section=%s category=%s error=%s",
            target_date.isoformat(),
            section_id,
            category_id,
            exc,
        )

    endpoint = f"{source_base_url.rstrip('/')}/schedule/games"
    response = requests.get(
        endpoint,
        params={
            "sectionId": section_id,
            "categoryId": category_id,
            "date": target_date.isoformat(),
        },
        headers={"User-Agent": "Mozilla/5.0 (compatible; BaseballDispatcher/1.0)"},
        timeout=timeout,
    )
    response.raise_for_status()
    payload = response.json()
    result = (payload.get("result") or {}) if isinstance(payload, dict) else {}
    games = result.get("games") or []
    if isinstance(games, list):
        return [item for item in games if isinstance(item, dict)]
    return []


def _fetch_schedule_games_via_calendar(
    *,
    source_base_url: str,
    section_id: str,
    category_id: str,
    target_date: date,
    timeout: float,
) -> list[dict[str, Any]]:
    calendar_endpoint = f"{source_base_url.rstrip('/')}/schedule/calendar"
    response = requests.get(
        calendar_endpoint,
        params={
            "sectionId": section_id,
            "categoryId": category_id,
            "date": target_date.isoformat(),
        },
        headers={"User-Agent": "Mozilla/5.0 (compatible; BaseballDispatcher/1.0)"},
        timeout=timeout,
    )
    response.raise_for_status()
    payload = response.json()
    result = (payload.get("result") or {}) if isinstance(payload, dict) else {}
    dates = result.get("dates") or []
    if not isinstance(dates, list):
        return []

    target_iso = target_date.isoformat()
    target_prefix = target_date.strftime("%Y%m%d")
    target_row = next(
        (
            item
            for item in dates
            if isinstance(item, dict) and str(item.get("ymd") or "").strip() == target_iso
        ),
        None,
    )
    if target_row is None:
        return []

    game_infos = target_row.get("gameInfos") or []
    if not isinstance(game_infos, list):
        return []

    game_ids: list[str] = []
    for info in game_infos:
        if not isinstance(info, dict):
            continue
        game_id = str(info.get("gameId") or "").strip()
        if not game_id.startswith(target_prefix):
            continue
        if section_id == "kbaseball" and category_id == "kbo" and not KBO_GAME_ID_PATTERN.match(game_id):
            continue
        game_ids.append(game_id)

    if not game_ids:
        return []

    deduped_game_ids = list(dict.fromkeys(game_ids))
    games: list[dict[str, Any]] = []
    for game_id in deduped_game_ids:
        game_endpoint = f"{source_base_url.rstrip('/')}/schedule/games/{game_id}"
        game_response = requests.get(
            game_endpoint,
            headers={"User-Agent": "Mozilla/5.0 (compatible; BaseballDispatcher/1.0)"},
            timeout=timeout,
        )
        game_response.raise_for_status()
        game_payload = game_response.json()
        game_result = (game_payload.get("result") or {}) if isinstance(game_payload, dict) else {}
        game = game_result.get("game")
        if isinstance(game, dict):
            games.append(game)
    return games


def _fetch_team_rank_rows(
    *,
    source_base_url: str,
    category_id: str,
    season_code: str,
    timeout: float,
) -> list[dict[str, Any]]:
    endpoint = f"{source_base_url.rstrip('/')}/statistics/categories/{category_id}/seasons/{season_code}/teams"
    response = requests.get(
        endpoint,
        headers={"User-Agent": "Mozilla/5.0 (compatible; BaseballDispatcher/1.0)"},
        timeout=timeout,
    )
    response.raise_for_status()
    payload = response.json()
    result = (payload.get("result") or {}) if isinstance(payload, dict) else {}
    rows = result.get("seasonTeamStats") or []
    if isinstance(rows, list):
        return [item for item in rows if isinstance(item, dict)]
    return []


def _build_team_record_payload(
    *,
    section_id: str,
    category_id: str,
    season_code: str,
    rows: list[dict[str, Any]],
) -> dict[str, Any]:
    observed_at = datetime.now(UTC).isoformat().replace("+00:00", "Z")
    records: list[dict[str, Any]] = []
    for row in rows:
        team_id = str(row.get("teamId") or "").strip()
        team_name = str(row.get("teamName") or row.get("teamShortName") or "").strip()
        if not team_id or not team_name:
            continue

        record_season_code_raw = row.get("seasonId") or row.get("year") or season_code
        record_season_code = str(record_season_code_raw).strip() or season_code

        records.append(
            {
                "upperCategoryId": str(row.get("upperCategoryId") or section_id).strip() or section_id,
                "categoryId": str(row.get("categoryId") or category_id).strip() or category_id,
                "seasonCode": record_season_code,
                "teamId": team_id,
                "teamName": team_name,
                "teamShortName": str(row.get("teamShortName") or "").strip() or None,
                "ranking": _safe_int(row.get("ranking"), default=0) or None,
                "orderNo": _safe_int(row.get("orderNo"), default=0) or None,
                "gameType": str(row.get("gameType") or "").strip() or None,
                "wra": _safe_float(row.get("wra")),
                "gameCount": _safe_int(row.get("gameCount"), default=0),
                "winGameCount": _safe_int(row.get("winGameCount"), default=0),
                "drawnGameCount": _safe_int(row.get("drawnGameCount"), default=0),
                "loseGameCount": _safe_int(row.get("loseGameCount"), default=0),
                "gameBehind": _safe_float(row.get("gameBehind")),
                "continuousGameResult": str(row.get("continuousGameResult") or "").strip() or None,
                "lastFiveGames": str(row.get("lastFiveGames") or "").strip() or None,
                "offenseHra": _safe_float(row.get("offenseHra")),
                "defenseEra": _safe_float(row.get("defenseEra")),
                "raw": row,
            }
        )

    return {
        "upperCategoryId": section_id,
        "categoryId": category_id,
        "seasonCode": season_code,
        "observedAt": observed_at,
        "records": records,
    }


def _post_team_records_to_backend(
    *,
    backend_base_url: str,
    backend_api_key: str,
    payload: dict[str, Any],
    timeout: float,
) -> dict[str, Any]:
    endpoint = f"{backend_base_url.rstrip('/')}/internal/crawler/team-records"
    response = requests.post(
        endpoint,
        headers={"X-API-Key": backend_api_key},
        json=payload,
        timeout=timeout,
    )
    response.raise_for_status()
    data = response.json()
    return data if isinstance(data, dict) else {}


def _run_team_record_import(
    *,
    source_base_url: str,
    backend_base_url: str,
    backend_api_key: str,
    section_id: str,
    category_id: str,
    season_code: str,
    timeout: float,
) -> bool:
    if category_id.strip().lower() != "kbo":
        LOGGER.info(
            "[team-record] skipped category=%s season=%s reason=unsupported-category",
            category_id,
            season_code,
        )
        return False

    try:
        rows = _fetch_team_rank_rows(
            source_base_url=source_base_url,
            category_id=category_id,
            season_code=season_code,
            timeout=timeout,
        )
    except Exception as exc:
        LOGGER.warning(
            "[team-record] fetch_failed category=%s season=%s error=%s",
            category_id,
            season_code,
            exc,
        )
        return False

    if not rows:
        LOGGER.warning("[team-record] empty category=%s season=%s", category_id, season_code)
        return False

    payload = _build_team_record_payload(
        section_id=section_id,
        category_id=category_id,
        season_code=season_code,
        rows=rows,
    )
    try:
        result = _post_team_records_to_backend(
            backend_base_url=backend_base_url,
            backend_api_key=backend_api_key,
            payload=payload,
            timeout=timeout,
        )
    except Exception as exc:
        LOGGER.warning(
            "[team-record] sync_failed category=%s season=%s error=%s",
            category_id,
            season_code,
            exc,
        )
        return False

    LOGGER.info(
        "[team-record] synced category=%s season=%s received=%s upserted=%s",
        category_id,
        season_code,
        result.get("receivedRecords"),
        result.get("upsertedRecords"),
    )
    return True


def _schedule_game_to_snapshot(game: dict[str, Any], target_date: date) -> dict[str, Any]:
    observed_at = datetime.now(UTC).isoformat().replace("+00:00", "Z")
    return {
        "homeTeam": str(game.get("homeTeamName") or "").strip() or "HOME",
        "awayTeam": str(game.get("awayTeamName") or "").strip() or "AWAY",
        "gameDate": _extract_schedule_game_date(game) or target_date.isoformat(),
        "status": _map_schedule_status(game.get("statusCode")),
        "inning": _extract_schedule_inning(game),
        "homeScore": _safe_int(game.get("homeTeamScore"), 0),
        "awayScore": _safe_int(game.get("awayTeamScore"), 0),
        "ball": 0,
        "strike": 0,
        "out": 0,
        "bases": {"first": False, "second": False, "third": False},
        "pitcher": None,
        "batter": None,
        "startTime": _extract_schedule_start_time(game),
        "observedAt": observed_at,
        "events": [],
        "lineupSlots": [],
        "batterStats": [],
        "pitcherStats": [],
        "notes": [],
    }


def _post_schedule_snapshot_to_backend(
    *,
    backend_base_url: str,
    backend_api_key: str,
    game_id: str,
    payload: dict[str, Any],
    timeout: float,
) -> dict[str, Any]:
    endpoint = f"{backend_base_url.rstrip('/')}/internal/crawler/games/{game_id}/snapshot"
    response = requests.post(
        endpoint,
        headers={"X-API-Key": backend_api_key},
        json=payload,
        timeout=timeout,
    )
    response.raise_for_status()
    data = response.json()
    return data if isinstance(data, dict) else {}


def _run_schedule_import(
    *,
    source_base_url: str,
    backend_base_url: str,
    backend_api_key: str,
    target_date: date,
    section_id: str,
    category_id: str,
    timeout: float,
) -> bool:
    LOGGER.info(
        "[import] fetch date=%s section=%s category=%s",
        target_date.isoformat(),
        section_id,
        category_id,
    )
    games = _fetch_schedule_games(
        source_base_url=source_base_url,
        section_id=section_id,
        category_id=category_id,
        target_date=target_date,
        timeout=timeout,
    )
    LOGGER.info("[import] fetched=%s date=%s", len(games), target_date.isoformat())

    success_count = 0
    failure_count = 0
    for game in games:
        game_id = str(game.get("gameId") or "").strip()
        if not game_id:
            continue

        payload = _schedule_game_to_snapshot(game, target_date)
        try:
            result = _post_schedule_snapshot_to_backend(
                backend_base_url=backend_base_url,
                backend_api_key=backend_api_key,
                game_id=game_id,
                payload=payload,
                timeout=timeout,
            )
            success_count += 1
            LOGGER.info(
                "[import] synced gameId=%s status=%s inning=%s score=%s:%s inserted=%s duplicates=%s",
                game_id,
                payload["status"],
                payload["inning"],
                payload["awayScore"],
                payload["homeScore"],
                result.get("insertedEvents"),
                result.get("duplicateEvents"),
            )
        except Exception as exc:
            failure_count += 1
            LOGGER.warning("[import] sync_failed gameId=%s error=%s", game_id, exc)

    LOGGER.info(
        "[import] done date=%s success=%s failed=%s",
        target_date.isoformat(),
        success_count,
        failure_count,
    )
    return failure_count == 0


def _build_schedule_import_dates(start_date: date, days: int) -> list[date]:
    normalized_days = max(1, days)
    return [start_date + timedelta(days=offset) for offset in range(normalized_days)]


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
    schedule_targets = _resolve_schedule_targets(args)
    LOGGER.info(
        "[dispatcher] schedule_targets=%s",
        ", ".join(f"{target.section_id}:{target.category_id}({target.source})" for target in schedule_targets),
    )

    windows: dict[str, RelayCheckWindow] = {}
    running: dict[str, RunningCrawler] = {}
    imported_date: date | None = None
    last_import_attempt_at: datetime | None = None
    last_import_success_at: datetime | None = None

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
        should_daily_import = now >= import_trigger and imported_date != now.date()
        should_refresh_import = (
            args.schedule_refresh_interval_sec > 0
            and imported_date == now.date()
            and (
                last_import_success_at is None
                or now - last_import_success_at >= timedelta(seconds=args.schedule_refresh_interval_sec)
            )
        )
        if should_daily_import or should_refresh_import:
            should_attempt = (
                last_import_attempt_at is None
                or now - last_import_attempt_at >= timedelta(seconds=args.import_retry_interval_sec)
            )
            if should_attempt:
                mode = "daily" if should_daily_import else "refresh"
                last_import_attempt_at = now
                LOGGER.info("[import] due mode=%s date=%s", mode, now.date().isoformat())
                all_success = True
                import_dates = _build_schedule_import_dates(
                    start_date=now.date(),
                    days=args.schedule_import_days if should_daily_import else 1,
                )
                LOGGER.info(
                    "[import] date_range mode=%s days=%s from=%s to=%s",
                    mode,
                    len(import_dates),
                    import_dates[0].isoformat(),
                    import_dates[-1].isoformat(),
                )
                for target in schedule_targets:
                    for import_date in import_dates:
                        if not _run_schedule_import(
                            source_base_url=args.source_base_url,
                            backend_base_url=args.backend_base_url,
                            backend_api_key=args.backend_api_key,
                            target_date=import_date,
                            section_id=target.section_id,
                            category_id=target.category_id,
                            timeout=args.http_timeout_sec,
                        ):
                            all_success = False
                    if not args.disable_team_record_sync:
                        season_code = str(args.team_record_season_code or now.date().year).strip()
                        _run_team_record_import(
                            source_base_url=args.source_base_url,
                            backend_base_url=args.backend_base_url,
                            backend_api_key=args.backend_api_key,
                            section_id=target.section_id,
                            category_id=target.category_id,
                            season_code=season_code,
                            timeout=args.http_timeout_sec,
                        )

                if all_success:
                    imported_date = now.date()
                    last_import_success_at = now
                    refresh_windows(now.date())

        max_check_window = timedelta(minutes=args.relay_check_minutes) if args.relay_check_minutes > 0 else None
        precheck_window = timedelta(minutes=max(0, args.precheck_minutes))
        for game_id, window in list(windows.items()):
            if window.launched or window.exhausted:
                continue
            if game_id in running:
                window.launched = True
                continue
            check_start_at = window.start_at - precheck_window
            if window.checks_done == 0 and (
                window.next_check_at is None or window.next_check_at > check_start_at
            ):
                window.next_check_at = check_start_at
            if now < check_start_at:
                continue
            if window.next_check_at is None:
                window.next_check_at = now
            if now < window.next_check_at:
                continue

            window.checks_done += 1
            available, is_final = _relay_is_available(
                source_base_url=args.source_base_url,
                game_id=game_id,
                timeout=args.http_timeout_sec,
                enable_preview_lineup_precheck=args.enable_preview_lineup_precheck,
            )
            LOGGER.info(
                "[relay] gameId=%s check=%s maxMinutes=%s available=%s final=%s",
                game_id,
                window.checks_done,
                args.relay_check_minutes if args.relay_check_minutes > 0 else "until-final",
                available,
                is_final,
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
                if is_final:
                    window.exhausted = True
                    LOGGER.info("[relay] finalized gameId=%s checks=%s", game_id, window.checks_done)
                    continue

                if max_check_window is not None and now >= window.start_at + max_check_window:
                    window.exhausted = True
                    LOGGER.info("[relay] expired gameId=%s checks=%s", game_id, window.checks_done)
                    continue

                window.next_check_at = now + timedelta(minutes=1)

        time.sleep(args.dispatch_interval_sec)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Daily baseball dispatcher: imports schedule at 00:05 KST and checks relay "
            "availability every minute from before each game's start time, launches crawler "
            "when relay is available, and periodically refreshes schedule scores/status."
        )
    )
    parser.add_argument("--backend-base-url", required=True, help="Backend API base URL, e.g. http://localhost:8080")
    parser.add_argument("--backend-api-key", required=True, help="Backend ingest API key")
    parser.add_argument("--source-base-url", default="https://api-gw.sports.naver.com")
    parser.add_argument(
        "--schedule-target",
        action="append",
        default=[],
        help=(
            "Schedule target to import. Repeat this option to run multiple targets in one dispatcher. "
            "Supported values: preset league (wbc/kbo), URL, or sectionId:categoryId."
        ),
    )
    parser.add_argument(
        "--league",
        choices=sorted(LEAGUE_PRESETS.keys()),
        default=None,
        help="Legacy single-target preset. Example: kbo -> sectionId=kbaseball, categoryId=kbo",
    )
    parser.add_argument(
        "--schedule-url",
        default=None,
        help=(
            "Optional Naver schedule URL to infer section/category, e.g. "
            "https://m.sports.naver.com/kbaseball/schedule/index?category=kbo&date=2026-03-12"
        ),
    )
    parser.add_argument("--section-id", default=None, help="Legacy single-target override: sectionId")
    parser.add_argument("--category-id", default=None, help="Legacy single-target override: categoryId")
    parser.add_argument("--schedule-hour", type=int, default=0)
    parser.add_argument("--schedule-minute", type=int, default=5)
    parser.add_argument(
        "--relay-check-minutes",
        type=int,
        default=0,
        help="Maximum minutes to check relay after game start (0: until game final status).",
    )
    parser.add_argument(
        "--precheck-minutes",
        type=int,
        default=180,
        help="Minutes before scheduled first pitch to start relay/lineup checks.",
    )
    parser.add_argument(
        "--enable-preview-lineup-precheck",
        action="store_true",
        help=(
            "Treat schedule/games/{gameId}/preview lineup data as available before relay text appears, "
            "so crawler can start earlier for pregame lineup sync."
        ),
    )
    parser.add_argument("--dispatch-interval-sec", type=int, default=15)
    parser.add_argument("--crawler-interval-sec", type=int, default=10)
    parser.add_argument(
        "--schedule-refresh-interval-sec",
        type=int,
        default=300,
        help="Re-import today's schedule every N seconds after daily import (0 to disable).",
    )
    parser.add_argument(
        "--schedule-import-days",
        type=int,
        default=30,
        help=(
            "Number of days to import from the current date during daily import "
            "(default: 30). Refresh import keeps using only today's date."
        ),
    )
    parser.add_argument(
        "--team-record-season-code",
        default=None,
        help=(
            "Season code for team-rank sync (default: current year in KST). "
            "Example: 2026"
        ),
    )
    parser.add_argument(
        "--disable-team-record-sync",
        action="store_true",
        help="Disable KBO team-rank sync to backend during import cycles.",
    )
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
