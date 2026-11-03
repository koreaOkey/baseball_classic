"""네이버 스포츠 API(api-gw.sports.naver.com) JSON GET 클라이언트.

2026-10-26부터 네이버가 브라우저가 아닌 TLS 지문의 요청에 응답하지 않아(Read timeout /
RemoteDisconnected) curl_cffi로 Chrome TLS를 흉내 낸다. 호출부가 기존처럼 requests 예외를
잡을 수 있도록 실패는 requests 예외로 바꿔 올린다.
"""

from __future__ import annotations

import json
from typing import Any

import requests
from curl_cffi import requests as curl_requests

IMPERSONATE = "chrome"

_session: curl_requests.Session | None = None


def _get_session() -> curl_requests.Session:
    global _session
    if _session is None:
        _session = curl_requests.Session(impersonate=IMPERSONATE)
    return _session


def get_json(url: str, *, params: dict[str, Any] | None = None, timeout: float) -> Any:
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
