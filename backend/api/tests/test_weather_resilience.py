"""기상청 API 지연 시 날씨 경로 격리 검증 (2026-09-06: 상류 11~25s 지연으로 앱 요청 전부 5s 타임아웃)."""
import os
import sys
import threading
import time
from pathlib import Path
from unittest.mock import patch

import requests

API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))

# 단독 실행 시에도 앱 설정이 운영 DB 를 가리키지 않도록 test_api.py 와 같은 환경을 먼저 준다.
os.environ.setdefault("BASEHAPTIC_DATABASE_URL", f"sqlite+pysqlite:///{(Path(__file__).parent / 'test_backend.db').as_posix()}")
os.environ.setdefault("BASEHAPTIC_CRAWLER_API_KEY", "test-key")
os.environ.setdefault("BASEHAPTIC_CORS_ALLOW_ORIGINS", "*")
os.environ.setdefault("BASEHAPTIC_SUPABASE_JWT_SECRET", "test-jwt-secret-0123456789abcdef0123456789abcdef")

from app import weather  # noqa: E402
from app.weather import _fetch_vilage_forecast, clear_weather_cache  # noqa: E402


class _OkResponse:
    def __init__(self, items):
        self._items = items

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return {"response": {"header": {"resultCode": "00"}, "body": {"items": {"item": self._items}}}}


def _fetch(base_time: str, **overrides):
    kwargs = dict(
        service_key="test-weather-key",
        api_base_url="https://weather.example.test/forecast",
        base_date="20260906",
        base_time=base_time,
        nx=61,
        ny=126,
    )
    kwargs.update(overrides)
    return _fetch_vilage_forecast(**kwargs)


def test_on_demand_uses_short_timeout_and_prewarm_uses_long() -> None:
    clear_weather_cache()
    seen: list = []

    def fake_get(*args, **kwargs):
        seen.append(kwargs["timeout"])
        return _OkResponse([{"category": "TMP"}])

    with patch("app.weather.requests.get", side_effect=fake_get):
        _fetch("0200")
        _fetch("0500", request_timeout=weather.FORECAST_PREWARM_TIMEOUT_SECONDS)

    clear_weather_cache()
    assert seen == [weather.FORECAST_REQUEST_TIMEOUT_SECONDS, weather.FORECAST_PREWARM_TIMEOUT_SECONDS]
    assert weather.FORECAST_REQUEST_TIMEOUT_SECONDS[1] < weather.FORECAST_PREWARM_TIMEOUT_SECONDS[1]


def test_stale_forecast_is_served_when_new_base_time_fails() -> None:
    clear_weather_cache()
    items_0200 = [{"category": "TMP", "fcstValue": "25"}]
    calls = {"n": 0}

    def fake_get(*args, **kwargs):
        calls["n"] += 1
        if kwargs["params"]["base_time"] == "0200":
            return _OkResponse(items_0200)
        raise requests.Timeout("upstream slow")

    with patch("app.weather.requests.get", side_effect=fake_get):
        assert _fetch("0200") == items_0200
        assert _fetch("0500") == items_0200  # 실패 → 마지막 성공분으로 대체
        assert _fetch("0500") == items_0200  # 실패 캐시 구간에도 stale 로 응답, 재요청 없음

    clear_weather_cache()
    assert calls["n"] == 2


def test_failure_without_stale_returns_empty_and_is_cached() -> None:
    clear_weather_cache()
    with patch("app.weather.requests.get", side_effect=requests.Timeout("upstream slow")) as get:
        assert _fetch("0200") == []
        assert _fetch("0200") == []
    clear_weather_cache()
    assert get.call_count == 1


def test_concurrent_request_does_not_pile_on_inflight_fetch() -> None:
    clear_weather_cache()
    release = threading.Event()
    started = threading.Event()
    calls = {"n": 0}

    def slow_get(*args, **kwargs):
        calls["n"] += 1
        started.set()
        release.wait(timeout=5)
        return _OkResponse([{"category": "TMP"}])

    results: dict = {}
    with patch("app.weather.requests.get", side_effect=slow_get):
        worker = threading.Thread(target=lambda: results.setdefault("first", _fetch("0200")))
        worker.start()
        assert started.wait(timeout=2)

        t0 = time.monotonic()
        second = _fetch("0200")
        elapsed = time.monotonic() - t0

        release.set()
        worker.join(timeout=5)

    clear_weather_cache()
    assert second == []  # 기다리지 않고 즉시 (stale 없음 → 빈 응답)
    assert elapsed < 0.5
    assert calls["n"] == 1
    assert results["first"] == [{"category": "TMP"}]


def test_no_network_path_prefers_stale_over_empty() -> None:
    clear_weather_cache()
    items = [{"category": "POP", "fcstValue": "30"}]
    with patch("app.weather.requests.get", return_value=_OkResponse(items)):
        assert _fetch("0200") == items
    with patch("app.weather.requests.get", side_effect=AssertionError("must not call network")):
        assert _fetch("0500", allow_network=False) == items
    clear_weather_cache()
