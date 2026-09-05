"""APNs 발송이 HTTP 호출 전에 실패해도 원인이 로그에 남는지 검증 (2026-09-05 Live Activity sent=0 조사)."""
import base64
import logging
import sys
from pathlib import Path

API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))

from app import apns  # noqa: E402


class _BadKeySettings:
    apns_key_base64 = base64.b64encode(b"this is not a p8 key").decode()
    apns_key_id = "KEYID12345"
    apns_team_id = "TEAMID1234"
    apns_bundle_id = "com.example.app"
    apns_use_sandbox = False


def _reset_jwt_state(monkeypatch) -> None:
    monkeypatch.setattr(apns, "_cached_jwt", None)
    monkeypatch.setattr(apns, "_cached_jwt_expires", 0)
    monkeypatch.setattr(apns, "_jwt_failure_logged_at", 0.0)


def test_invalid_key_returns_none_and_logs_reason(monkeypatch, caplog) -> None:
    _reset_jwt_state(monkeypatch)
    monkeypatch.setattr(apns, "get_settings", lambda: _BadKeySettings())

    with caplog.at_level(logging.WARNING, logger="app.apns"):
        assert apns._create_jwt_token() is None

    messages = [record.getMessage() for record in caplog.records]
    assert any("JWT creation failed" in message for message in messages), messages


def test_jwt_failure_log_is_throttled(monkeypatch, caplog) -> None:
    _reset_jwt_state(monkeypatch)
    monkeypatch.setattr(apns, "get_settings", lambda: _BadKeySettings())

    with caplog.at_level(logging.WARNING, logger="app.apns"):
        apns._create_jwt_token()
        apns._create_jwt_token()
        apns._create_jwt_token()

    failures = [r for r in caplog.records if "JWT creation failed" in r.getMessage()]
    assert len(failures) == 1


def test_log_send_exceptions_reports_count_and_first_error(caplog) -> None:
    with caplog.at_level(logging.WARNING, logger="app.apns"):
        apns.log_send_exceptions("unit-push", [(True, False), RuntimeError("boom"), ValueError("x")])

    messages = [record.getMessage() for record in caplog.records]
    assert any("[unit-push] 2/3 sends raised RuntimeError: boom" == message for message in messages), messages


def test_log_send_exceptions_is_silent_without_errors(caplog) -> None:
    with caplog.at_level(logging.WARNING, logger="app.apns"):
        apns.log_send_exceptions("unit-push", [(True, False), (False, True)])

    assert not caplog.records


_FAKE_P8 = "-----BEGIN PRIVATE KEY-----\nMIGTAgEAMBMGByqGSM49AgEG\n-----END PRIVATE KEY-----\n"


class _KeySettings:
    def __init__(self, value: str) -> None:
        self.apns_key_base64 = value


def _key_with(monkeypatch, value: str) -> str | None:
    monkeypatch.setattr(apns, "get_settings", lambda: _KeySettings(value))
    return apns._get_apns_key()


def test_key_decodes_when_trailing_padding_was_stripped(monkeypatch) -> None:
    encoded = base64.b64encode(_FAKE_P8.encode()).decode()
    assert encoded.endswith("=")  # 패딩이 있는 입력이어야 의미 있는 검증
    stripped = encoded.rstrip("=")

    assert _key_with(monkeypatch, stripped) == _FAKE_P8


def test_key_decodes_with_line_wraps_quotes_and_whitespace(monkeypatch) -> None:
    encoded = base64.b64encode(_FAKE_P8.encode()).decode()
    wrapped = "\n".join(encoded[i:i + 20] for i in range(0, len(encoded), 20))

    assert _key_with(monkeypatch, f'  "{wrapped}"  \n') == _FAKE_P8


def test_raw_pem_is_accepted_as_is(monkeypatch) -> None:
    assert _key_with(monkeypatch, _FAKE_P8) == _FAKE_P8
    assert _key_with(monkeypatch, _FAKE_P8.replace("\n", "\\n")) == _FAKE_P8


def test_empty_key_returns_none(monkeypatch) -> None:
    assert _key_with(monkeypatch, "") is None
    assert _key_with(monkeypatch, "   ") is None
