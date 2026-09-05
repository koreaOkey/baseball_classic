from crawler import FINAL_STATUS, is_terminal_status


def test_terminal_by_status_code() -> None:
    for code in sorted(FINAL_STATUS):
        assert is_terminal_status(code, None) is True
    assert is_terminal_status("result", "") is True


def test_live_and_before_are_not_terminal_without_info() -> None:
    assert is_terminal_status("STARTED", "9회말") is False
    assert is_terminal_status("BEFORE", "") is False
    assert is_terminal_status("BEFORE", None) is False
    assert is_terminal_status(None, None) is False


def test_before_with_cancel_text_is_terminal() -> None:
    # 2026-09-03 HTNC: statusCode=BEFORE, statusInfo="경기취소" 가 이틀간 폴링된 사례
    assert is_terminal_status("BEFORE", "경기취소") is True
    assert is_terminal_status("BEFORE", "우천 취소") is True
    assert is_terminal_status("BEFORE", "노게임") is True
    assert is_terminal_status("BEFORE", "경기 연기") is True
    assert is_terminal_status("BEFORE", "Rain cancel") is True


def test_rain_stop_text_is_not_terminal() -> None:
    # 우천 중단은 재개될 수 있으므로 종료로 보지 않는다
    assert is_terminal_status("STARTED", "우천 중단") is False
