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
FORECAST_FAILURE_TTL_SECONDS = 60
FORECAST_REQUEST_TIMEOUT_SECONDS = 12
SUPPORTED_FORECAST_DAYS = 3
BASE_TIMES = ("0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300")
FORECAST_ROWS = 1000
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


def build_weather_summary(
    game: Game,
    *,
    service_key: str,
    api_base_url: str,
    now: datetime | None = None,
    allow_network: bool = True,
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
    )
    if not slots:
        return None

    selected = _nearest_slot(slots, start_time)
    if selected is None:
        return None
    return _summary_payload(stadium, selected)


def build_hourly_weather(game: Game, *, service_key: str, api_base_url: str, target_date: date, now: datetime | None = None) -> dict[str, Any] | None:
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


def _forecast_slots_for_game(
    *,
    stadium: StadiumInfo,
    target_date: date,
    service_key: str,
    api_base_url: str,
    now: datetime | None,
    allow_network: bool = True,
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
) -> list[dict[str, Any]]:
    normalized_service_key = _normalize_service_key(service_key)
    cache_key = (nx, ny, base_date, base_time, normalized_service_key[-8:])
    now = datetime.now(timezone.utc)
    with _forecast_cache_lock:
        cached = _forecast_cache.get(cache_key)
        if cached is not None:
            expires_at, cached_items = cached
            if now < expires_at:
                return cached_items

    if not allow_network:
        return []

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
            timeout=FORECAST_REQUEST_TIMEOUT_SECONDS,
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
            FORECAST_REQUEST_TIMEOUT_SECONDS,
            exc.__class__.__name__,
        )
        with _forecast_cache_lock:
            _forecast_cache[cache_key] = (now + timedelta(seconds=FORECAST_FAILURE_TTL_SECONDS), [])
        return []

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
        return []
    item = payload.get("response", {}).get("body", {}).get("items", {}).get("item") or []
    if isinstance(item, dict):
        items = [item]
    elif isinstance(item, list):
        items = item
    else:
        items = []

    with _forecast_cache_lock:
        _forecast_cache[cache_key] = (now + timedelta(seconds=FORECAST_TTL_SECONDS), items)
    return items


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
