from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from math import cos, floor, log, pi, pow, sin, tan
from threading import Lock
from typing import Any
from urllib.parse import unquote

import requests

from .cheer_signals import StadiumInfo, stadium_for_home_team, team_code_from_name
from .models import Game


KST = timezone(timedelta(hours=9))
FORECAST_TTL_SECONDS = 30 * 60
FORECAST_FAILURE_TTL_SECONDS = 120
# 온디맨드(앱 요청) 경로 타임아웃 (connect, read). 앱은 5초에 포기하므로 그 안에 실패를 확정하고
# 마지막 성공 예보(stale)로 대신 응답한다. 2026-09-06 기상청 API 지연(11~25s) 동안 앱의 날씨
# 요청이 전부 5초 타임아웃(499)으로 끝나고 요청 스레드가 12초씩 묶이던 사례.
FORECAST_REQUEST_TIMEOUT_SECONDS: float | tuple[float, float] = (2.0, 3.0)
# 백그라운드 프리워밍 경로: 느린 응답도 기다려 캐시를 채운다.
FORECAST_PREWARM_TIMEOUT_SECONDS: float | tuple[float, float] = (3.0, 12.0)
# 새 base_time 조회가 실패/진행 중일 때 대신 쓰는 마지막 성공 예보의 최대 나이
FORECAST_STALE_MAX_AGE_SECONDS = 6 * 3600
SUPPORTED_FORECAST_DAYS = 3
BASE_TIMES = ("0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300")
# 단기예보는 시간당 12개 카테고리 × 최대 +3일(~70여 시간) ≈ 900행을 넘을 수 있어
# 1000행이면 +3일차 저녁 슬롯이 잘릴 수 있다. 여유를 두고 요청한다.
FORECAST_ROWS = 1500
logger = logging.getLogger(__name__)

SKY_LABELS = {
    "1": "맑음",
    "3": "구름많음",
    "4": "흐림",
}
PTY_LABELS = {
    "0": None,
    "1": "비",
    "2": "비/눈",
    "3": "눈",
    "4": "소나기",
    "5": "빗방울",
    "6": "빗방울/눈날림",
    "7": "눈날림",
}
STADIUM_SHORT_NAMES = {
    "JAMSIL": "잠실",
    "GOCHEOK": "고척돔",
    "INCHEON": "문학",
    "SUWON": "수원",
    "DAEJEON": "대전",
    "DAEGU": "대구",
    "SAJIK": "사직",
    "GWANGJU": "광주",
    "CHANGWON": "창원",
}


@dataclass(frozen=True)
class ForecastSlot:
    forecast_date: str
    forecast_time: str
    condition: str
    temperature_c: int | None
    precipitation_probability: int | None
    precipitation_type: str | None
    wind_speed_mps: float | None


_forecast_cache: dict[tuple[int, int, str, str, str], tuple[datetime, list[dict[str, Any]]]] = {}
_forecast_cache_lock = Lock()
# (nx, ny, key_suffix) → (fetched_at, items). base_time 이 바뀌어 새 조회가 실패하거나 진행 중일 때
# 대체 응답으로 쓴다. 단기예보는 +3일치를 담고 있어 몇 시간 전 발표분도 유효하다.
_last_good_forecast: dict[tuple[int, int, str], tuple[datetime, list[dict[str, Any]]]] = {}
# 같은 키 조회가 진행 중이면 뒤따르는 요청은 기다리지 않고 stale/빈 응답으로 즉시 돌아간다
# (느린 상류에 요청이 쌓여 스레드 풀을 점유하는 것을 막는다).
_inflight_forecast_keys: set[tuple[int, int, str, str, str]] = set()


def build_weather_summary(
    game: Game,
    *,
    service_key: str,
    api_base_url: str,
    now: datetime | None = None,
    allow_network: bool = True,
    request_timeout: float | tuple[float, float] | None = None,
) -> dict[str, Any] | None:
    if (game.status or "").upper() != "SCHEDULED":
        return None

    target_date = _game_date(game)
    start_time = _start_time(game.start_time)
    stadium = _stadium_for_game(game)
    if target_date is None or start_time is None or stadium is None:
        return None

    if stadium.indoor:
        return _indoor_summary(stadium)

    slots = _forecast_slots_for_game(
        stadium=stadium,
        target_date=target_date,
        service_key=service_key,
        api_base_url=api_base_url,
        now=now,
        allow_network=allow_network,
        request_timeout=request_timeout,
    )
    if not slots:
        return None

    selected = _nearest_slot(slots, start_time)
    if selected is None:
        return None
    return _summary_payload(stadium, selected)


def build_hourly_weather(
    game: Game,
    *,
    service_key: str,
    api_base_url: str,
    target_date: date,
    now: datetime | None = None,
    request_timeout: float | tuple[float, float] | None = None,
) -> dict[str, Any] | None:
    stadium = _stadium_for_game(game)
    if stadium is None:
        return None

    start_time = _start_time(game.start_time)
    if stadium.indoor:
        return {
            "gameId": game.id,
            "stadiumCode": stadium.code,
            "stadiumName": stadium.name,
            "stadiumShortName": _stadium_short_name(stadium),
            "gameStartTime": game.start_time,
            "items": [
                {
                    "forecastDate": target_date.isoformat(),
                    "forecastTime": _hhmm(start_time) if start_time else "0000",
                    "timeLabel": _hour_label(start_time.hour if start_time else 0),
                    "condition": "날씨 영향 적음",
                    "temperatureC": None,
                    "precipitationProbability": None,
                    "precipitationType": None,
                    "windSpeedMps": None,
                    "isGameStartForecast": True,
                }
            ],
        }

    slots = _forecast_slots_for_game(
        stadium=stadium,
        target_date=target_date,
        service_key=service_key,
        api_base_url=api_base_url,
        now=now,
        request_timeout=request_timeout,
    )
    if not slots:
        return _empty_hourly_payload(game, stadium)

    selected = _nearest_slot(slots, start_time) if start_time else None
    items = []
    for slot in slots:
        items.append({
            "forecastDate": _iso_date(slot.forecast_date),
            "forecastTime": slot.forecast_time,
            "timeLabel": _hour_label(int(slot.forecast_time[:2])),
            "condition": slot.condition,
            "temperatureC": slot.temperature_c,
            "precipitationProbability": slot.precipitation_probability,
            "precipitationType": slot.precipitation_type,
            "windSpeedMps": slot.wind_speed_mps,
            "isGameStartForecast": selected == slot,
        })

    return {
        "gameId": game.id,
        "stadiumCode": stadium.code,
        "stadiumName": stadium.name,
        "stadiumShortName": _stadium_short_name(stadium),
        "gameStartTime": game.start_time,
        "items": items,
    }


def _empty_hourly_payload(game: Game, stadium: StadiumInfo) -> dict[str, Any]:
    return {
        "gameId": game.id,
        "stadiumCode": stadium.code,
        "stadiumName": stadium.name,
        "stadiumShortName": _stadium_short_name(stadium),
        "gameStartTime": game.start_time,
        "items": [],
    }


def clear_weather_cache() -> None:
    with _forecast_cache_lock:
        _forecast_cache.clear()
        _last_good_forecast.clear()
        _inflight_forecast_keys.clear()


def _stale_forecast(nx: int, ny: int, key_suffix: str, now: datetime) -> list[dict[str, Any]]:
    """호출자가 _forecast_cache_lock 을 잡지 않은 상태에서 부른다."""
    with _forecast_cache_lock:
        entry = _last_good_forecast.get((nx, ny, key_suffix))
    if entry is None:
        return []
    fetched_at, items = entry
    if now - fetched_at > timedelta(seconds=FORECAST_STALE_MAX_AGE_SECONDS):
        return []
    return items


def _forecast_slots_for_game(
    *,
    stadium: StadiumInfo,
    target_date: date,
    service_key: str,
    api_base_url: str,
    now: datetime | None,
    allow_network: bool = True,
    request_timeout: float | tuple[float, float] | None = None,
) -> list[ForecastSlot]:
    normalized_service_key = _normalize_service_key(service_key)
    if not normalized_service_key:
        return []

    now_kst = (now or datetime.now(KST)).astimezone(KST)
    if target_date < now_kst.date() or target_date > now_kst.date() + timedelta(days=SUPPORTED_FORECAST_DAYS):
        return []

    base_date, base_time = _latest_base_datetime(now_kst)
    nx, ny = _to_kma_grid(stadium.latitude, stadium.longitude)
    raw_items = _fetch_vilage_forecast(
        service_key=normalized_service_key,
        api_base_url=api_base_url,
        base_date=base_date,
        base_time=base_time,
        nx=nx,
        ny=ny,
        allow_network=allow_network,
        request_timeout=request_timeout,
    )
    return _parse_slots(raw_items, target_date)


def _fetch_vilage_forecast(
    *,
    service_key: str,
    api_base_url: str,
    base_date: str,
    base_time: str,
    nx: int,
    ny: int,
    allow_network: bool = True,
    request_timeout: float | tuple[float, float] | None = None,
) -> list[dict[str, Any]]:
    normalized_service_key = _normalize_service_key(service_key)
    key_suffix = normalized_service_key[-8:]
    cache_key = (nx, ny, base_date, base_time, key_suffix)
    now = datetime.now(timezone.utc)
    with _forecast_cache_lock:
        cached = _forecast_cache.get(cache_key)
        if cached is not None:
            expires_at, cached_items = cached
            if now < expires_at:
                if cached_items:
                    return cached_items
                # 실패 캐시(빈 목록) 구간에는 마지막 성공 예보로 대체한다
                cached = None
                stale_needed = True
            else:
                stale_needed = False
        else:
            stale_needed = False
        if stale_needed:
            pass
        elif not allow_network:
            pass
        elif cache_key in _inflight_forecast_keys:
            stale_needed = True
        else:
            _inflight_forecast_keys.add(cache_key)
            stale_needed = None  # 이 스레드가 조회 담당

    if stale_needed is not None:
        # 실패 캐시 구간 / 네트워크 금지 / 다른 스레드가 조회 중 → 기다리지 않고 stale 또는 빈 응답
        return _stale_forecast(nx, ny, key_suffix, now)

    timeout = request_timeout if request_timeout is not None else FORECAST_REQUEST_TIMEOUT_SECONDS
    try:
        try:
            response = requests.get(
                api_base_url,
                params={
                    "serviceKey": normalized_service_key,
                    "pageNo": "1",
                    "numOfRows": str(FORECAST_ROWS),
                    "dataType": "JSON",
                    "base_date": base_date,
                    "base_time": base_time,
                    "nx": str(nx),
                    "ny": str(ny),
                },
                timeout=timeout,
            )
            response.raise_for_status()
            payload = response.json()
        except requests.RequestException as exc:
            logger.warning(
                "weather forecast request failed: base_date=%s base_time=%s nx=%s ny=%s timeout_sec=%s error=%s",
                base_date,
                base_time,
                nx,
                ny,
                timeout,
                exc.__class__.__name__,
            )
            with _forecast_cache_lock:
                _forecast_cache[cache_key] = (now + timedelta(seconds=FORECAST_FAILURE_TTL_SECONDS), [])
            return _stale_forecast(nx, ny, key_suffix, now)

        result = payload.get("response", {}).get("header", {}).get("resultCode")
        if result and result != "00":
            logger.warning(
                "weather forecast api returned non-success: base_date=%s base_time=%s nx=%s ny=%s result_code=%s result_msg=%s",
                base_date,
                base_time,
                nx,
                ny,
                result,
                payload.get("response", {}).get("header", {}).get("resultMsg"),
            )
            with _forecast_cache_lock:
                _forecast_cache[cache_key] = (now + timedelta(seconds=FORECAST_FAILURE_TTL_SECONDS), [])
            return _stale_forecast(nx, ny, key_suffix, now)
        item = payload.get("response", {}).get("body", {}).get("items", {}).get("item") or []
        if isinstance(item, dict):
            items = [item]
        elif isinstance(item, list):
            items = item
        else:
            items = []

        with _forecast_cache_lock:
            _forecast_cache[cache_key] = (now + timedelta(seconds=FORECAST_TTL_SECONDS), items)
            if items:
                _last_good_forecast[(nx, ny, key_suffix)] = (now, items)
        return items
    finally:
        with _forecast_cache_lock:
            _inflight_forecast_keys.discard(cache_key)


def _parse_slots(items: list[dict[str, Any]], target_date: date) -> list[ForecastSlot]:
    target = target_date.strftime("%Y%m%d")
    grouped: dict[tuple[str, str], dict[str, str]] = {}
    for item in items:
        if str(item.get("fcstDate", "")) != target:
            continue
        forecast_time = str(item.get("fcstTime", ""))
        category = str(item.get("category", ""))
        value = str(item.get("fcstValue", ""))
        if len(forecast_time) != 4 or not category:
            continue
        grouped.setdefault((target, forecast_time), {})[category] = value

    slots: list[ForecastSlot] = []
    for (forecast_date, forecast_time), values in sorted(grouped.items()):
        condition = _condition(values)
        slots.append(ForecastSlot(
            forecast_date=forecast_date,
            forecast_time=forecast_time,
            condition=condition,
            temperature_c=_safe_int(values.get("TMP")),
            precipitation_probability=_safe_int(values.get("POP")),
            precipitation_type=PTY_LABELS.get(str(values.get("PTY", "0"))),
            wind_speed_mps=_safe_float(values.get("WSD")),
        ))
    return slots


def _summary_payload(stadium: StadiumInfo, slot: ForecastSlot) -> dict[str, Any]:
    short_name = _stadium_short_name(stadium)
    temp = f"{slot.temperature_c}°" if slot.temperature_c is not None else "-°"
    pop = f"강수 {slot.precipitation_probability}%" if slot.precipitation_probability is not None else "강수 -%"
    label = _hour_label(int(slot.forecast_time[:2]))
    return {
        "stadiumCode": stadium.code,
        "stadiumName": stadium.name,
        "stadiumShortName": short_name,
        "forecastDate": _iso_date(slot.forecast_date),
        "forecastTime": slot.forecast_time,
        "forecastTimeLabel": f"{label} 기준",
        "condition": slot.condition,
        "temperatureC": slot.temperature_c,
        "precipitationProbability": slot.precipitation_probability,
        "precipitationType": slot.precipitation_type,
        "windSpeedMps": slot.wind_speed_mps,
        "isIndoor": False,
        "displayText": f"경기 시작 예보 · {short_name} · {label} 기준 · {slot.condition} {temp} · {pop}",
    }


def _indoor_summary(stadium: StadiumInfo) -> dict[str, Any]:
    short_name = _stadium_short_name(stadium)
    return {
        "stadiumCode": stadium.code,
        "stadiumName": stadium.name,
        "stadiumShortName": short_name,
        "forecastDate": None,
        "forecastTime": None,
        "forecastTimeLabel": None,
        "condition": "날씨 영향 적음",
        "temperatureC": None,
        "precipitationProbability": None,
        "precipitationType": None,
        "windSpeedMps": None,
        "isIndoor": True,
        "displayText": f"경기 시작 예보 · {short_name} · 날씨 영향 적음",
    }


def _normalize_service_key(service_key: str) -> str:
    return unquote(service_key.strip())


def _nearest_slot(slots: list[ForecastSlot], start_time: time) -> ForecastSlot | None:
    start_minutes = start_time.hour * 60 + start_time.minute
    return min(
        slots,
        key=lambda slot: abs((int(slot.forecast_time[:2]) * 60 + int(slot.forecast_time[2:])) - start_minutes),
        default=None,
    )


def _latest_base_datetime(now_kst: datetime) -> tuple[str, str]:
    available_at = now_kst - timedelta(minutes=45)
    for base_time in reversed(BASE_TIMES):
        hour = int(base_time[:2])
        candidate = available_at.replace(hour=hour, minute=0, second=0, microsecond=0)
        if candidate <= available_at:
            return candidate.strftime("%Y%m%d"), base_time

    previous_day = available_at.date() - timedelta(days=1)
    return previous_day.strftime("%Y%m%d"), "2300"


def _stadium_for_game(game: Game) -> StadiumInfo | None:
    home_code = team_code_from_name(game.home_team)
    if home_code is None:
        return None
    return stadium_for_home_team(home_code)


def _game_date(game: Game) -> date | None:
    raw = game.game_date
    if raw:
        try:
            return date.fromisoformat(raw)
        except ValueError:
            pass
    prefix = (game.id or "")[:8]
    if len(prefix) == 8 and prefix.isdigit():
        try:
            return datetime.strptime(prefix, "%Y%m%d").date()
        except ValueError:
            return None
    return None


def _start_time(raw: str | None) -> time | None:
    if not raw:
        return None
    try:
        hour, minute = raw.split(":", maxsplit=1)
        return time(hour=int(hour), minute=int(minute))
    except (ValueError, TypeError):
        return None


def _condition(values: dict[str, str]) -> str:
    pty = str(values.get("PTY", "0"))
    precipitation = PTY_LABELS.get(pty)
    if precipitation:
        return precipitation
    return SKY_LABELS.get(str(values.get("SKY", "")), "예보")


def _safe_int(value: str | None) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(float(value))
    except ValueError:
        return None


def _safe_float(value: str | None) -> float | None:
    if value is None or value == "":
        return None
    try:
        return round(float(value), 1)
    except ValueError:
        return None


def _stadium_short_name(stadium: StadiumInfo) -> str:
    return STADIUM_SHORT_NAMES.get(stadium.code, stadium.name)


def _hour_label(hour: int) -> str:
    return f"{hour}시"


def _hhmm(value: time) -> str:
    return f"{value.hour:02d}{value.minute:02d}"


def _iso_date(raw: str) -> str:
    try:
        return datetime.strptime(raw, "%Y%m%d").date().isoformat()
    except ValueError:
        return raw


def _to_kma_grid(latitude: float, longitude: float) -> tuple[int, int]:
    # KMA DFS grid conversion.
    re = 6371.00877
    grid = 5.0
    slat1 = 30.0 * pi / 180.0
    slat2 = 60.0 * pi / 180.0
    olon = 126.0 * pi / 180.0
    olat = 38.0 * pi / 180.0
    xo = 43.0
    yo = 136.0

    sn = tan(pi * 0.25 + slat2 * 0.5) / tan(pi * 0.25 + slat1 * 0.5)
    sn = log(cos(slat1) / cos(slat2)) / log(sn)
    sf = tan(pi * 0.25 + slat1 * 0.5)
    sf = pow(sf, sn) * cos(slat1) / sn
    ro = tan(pi * 0.25 + olat * 0.5)
    ro = re / grid * sf / pow(ro, sn)

    ra = tan(pi * 0.25 + latitude * pi / 180.0 * 0.5)
    ra = re / grid * sf / pow(ra, sn)
    theta = longitude * pi / 180.0 - olon
    if theta > pi:
        theta -= 2.0 * pi
    if theta < -pi:
        theta += 2.0 * pi
    theta *= sn

    x = floor(ra * sin(theta) + xo + 0.5)
    y = floor(ro - ra * cos(theta) + yo + 0.5)
    return int(x), int(y)
