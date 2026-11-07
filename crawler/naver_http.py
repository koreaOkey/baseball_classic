"""네이버 스포츠 API(api-gw.sports.naver.com) JSON GET 클라이언트.

2026-10-26부터 네이버가 브라우저가 아닌 TLS 지문의 요청에 응답하지 않아(Read timeout /
RemoteDisconnected) curl_cffi로 Chrome TLS를 흉내 낸다. 호출부가 기존처럼 requests 예외를
잡을 수 있도록 실패는 requests 예외로 바꿔 올린다.

2026-11-07부터 relay(`/schedule/games/{id}/relay`)는 위장 요청도 막혀, NAVER_RELAY_BRIDGE_URL 이
설정돼 있으면 relay URL 만 실제 브라우저를 띄운 로컬 브리지(naver_relay_bridge.py)로 보낸다.
"""

from __future__ import annotations

import json
import os
from typing import Any
from urllib.parse import urlparse

import requests
from curl_cffi import requests as curl_requests

IMPERSONATE = "chrome"
RELAY_BRIDGE_URL = os.environ.get("NAVER_RELAY_BRIDGE_URL", "").strip().rstrip("/")

_session: curl_requests.Session | None = None


def _get_session() -> curl_requests.Session:
    global _session
    if _session is None:
        _session = curl_requests.Session(impersonate=IMPERSONATE)
    return _session


def _is_relay_url(url: str) -> bool:
    return urlparse(url).path.endswith("/relay")


def _get_json_via_bridge(url: str, timeout: float) -> Any:
    response = requests.get(f"{RELAY_BRIDGE_URL}/fetch", params={"url": url}, timeout=timeout)
    response.raise_for_status()
    return response.json()


def get_json(url: str, *, params: dict[str, Any] | None = None, timeout: float) -> Any:
    if RELAY_BRIDGE_URL and params is None and _is_relay_url(url):
        return _get_json_via_bridge(url, timeout)
    try:
        response = _get_session().get(url, params=params, timeout=timeout)
    except curl_requests.exceptions.Timeout as exc:
        raise requests.Timeout(str(exc)) from exc
    except curl_requests.exceptions.RequestException as exc:
        raise requests.ConnectionError(str(exc)) from exc

    if response.status_code >= 400:
        raise requests.HTTPError(
            f"{response.status_code} Error for url: {response.url}",
            response=response,
        )
    try:
        return json.loads(response.text)
    except json.JSONDecodeError as exc:
        raise requests.exceptions.JSONDecodeError(exc.msg, exc.doc, exc.pos) from exc
