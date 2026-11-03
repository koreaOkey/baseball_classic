import pytest
import requests
from curl_cffi import requests as curl_requests

import naver_http


class _FakeResponse:
    def __init__(self, status_code: int, text: str, url: str = "https://example.test/x") -> None:
        self.status_code = status_code
        self.text = text
        self.url = url


class _FakeSession:
    def __init__(self, *, response: _FakeResponse | None = None, exc: Exception | None = None) -> None:
        self._response = response
        self._exc = exc
        self.calls: list[tuple[str, dict | None, float]] = []

    def get(self, url, params=None, timeout=None):
        self.calls.append((url, params, timeout))
        if self._exc is not None:
            raise self._exc
        return self._response


def _use_session(monkeypatch, session: _FakeSession) -> None:
    monkeypatch.setattr(naver_http, "_get_session", lambda: session)


def test_returns_parsed_json_and_passes_params(monkeypatch) -> None:
    session = _FakeSession(response=_FakeResponse(200, '{"result": {"games": []}}'))
    _use_session(monkeypatch, session)

    payload = naver_http.get_json("https://example.test/x", params={"date": "2026-11-03"}, timeout=7)

    assert payload == {"result": {"games": []}}
    assert session.calls == [("https://example.test/x", {"date": "2026-11-03"}, 7)]


def test_timeout_becomes_requests_timeout(monkeypatch) -> None:
    _use_session(monkeypatch, _FakeSession(exc=curl_requests.exceptions.Timeout("timed out")))

    with pytest.raises(requests.Timeout):
        naver_http.get_json("https://example.test/x", timeout=1)


def test_connection_failure_becomes_requests_connection_error(monkeypatch) -> None:
    _use_session(monkeypatch, _FakeSession(exc=curl_requests.exceptions.ConnectionError("reset")))

    with pytest.raises(requests.ConnectionError):
        naver_http.get_json("https://example.test/x", timeout=1)


def test_http_error_keeps_status_code(monkeypatch) -> None:
    _use_session(monkeypatch, _FakeSession(response=_FakeResponse(403, "denied")))

    with pytest.raises(requests.HTTPError) as exc_info:
        naver_http.get_json("https://example.test/x", timeout=1)
    assert exc_info.value.response.status_code == 403


def test_invalid_json_is_a_requests_exception(monkeypatch) -> None:
    # crawler.crawl_once_detailed 의 preview 조회가 requests.RequestException 만 잡는다
    _use_session(monkeypatch, _FakeSession(response=_FakeResponse(200, "<html>")))

    with pytest.raises(requests.RequestException):
        naver_http.get_json("https://example.test/x", timeout=1)
