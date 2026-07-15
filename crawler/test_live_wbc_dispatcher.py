import argparse
from datetime import date, datetime, timedelta

from live_wbc_dispatcher import (
    KST,
    MAX_CRAWLER_RESTARTS_PER_GAME,
    RelayCheckWindow,
    _build_schedule_import_dates,
    _build_schedule_import_dates_for_mode,
    _build_schedule_import_dates_until,
    _build_team_record_payload,
    _crawler_restart_backoff_sec,
    _handle_crawler_exit,
    _import_marks_daily_complete,
    _map_schedule_status,
    _merge_relay_windows,
    _parse_schedule_url,
    _preview_has_lineup,
    _run_schedule_import,
    _resolve_schedule_filters,
    _resolve_schedule_targets,
    _backend_game_matches_schedule_snapshot,
    _schedule_snapshot_signature,
    _schedule_game_matches_target_date,
    _should_skip_schedule_snapshot,
    build_parser,
)
import live_wbc_dispatcher


def test_parse_schedule_url_for_kbo() -> None:
    section_id, category_id = _parse_schedule_url(
        "https://m.sports.naver.com/kbaseball/schedule/index?category=kbo&date=2026-03-12"
    )

    assert section_id == "kbaseball"
    assert category_id == "kbo"


def test_resolve_schedule_filters_uses_league_preset() -> None:
    args = argparse.Namespace(
        schedule_target=[],
        league="kbo",
        schedule_url=None,
        section_id=None,
        category_id=None,
    )

    section_id, category_id = _resolve_schedule_filters(args)

    assert section_id == "kbaseball"
    assert category_id == "kbo"


def test_resolve_schedule_filters_honors_explicit_overrides() -> None:
    args = argparse.Namespace(
        schedule_target=[],
        league="wbc",
        schedule_url="https://m.sports.naver.com/kbaseball/schedule/index?category=kbo&date=2026-03-12",
        section_id="baseball",
        category_id="kbo",
    )

    section_id, category_id = _resolve_schedule_filters(args)

    assert section_id == "baseball"
    assert category_id == "kbo"


def test_resolve_schedule_targets_supports_multiple_entries() -> None:
    args = argparse.Namespace(
        schedule_target=["wbc", "kbo"],
        league=None,
        schedule_url=None,
        section_id=None,
        category_id=None,
    )

    targets = _resolve_schedule_targets(args)

    assert len(targets) == 2
    assert (targets[0].section_id, targets[0].category_id) == ("wbaseball", "wbc")
    assert (targets[1].section_id, targets[1].category_id) == ("kbaseball", "kbo")


def test_preview_has_lineup_detects_full_lineup() -> None:
    payload = {
        "result": {
            "previewData": {
                "homeTeamLineUp": {"fullLineUp": [{"name": "A"}]},
                "awayTeamLineUp": {"fullLineUp": []},
            }
        }
    }
    assert _preview_has_lineup(payload) is True


def test_preview_has_lineup_detects_starters() -> None:
    payload = {
        "result": {
            "previewData": {
                "homeStarter": {"name": "StarterA"},
                "awayStarter": {},
            }
        }
    }
    assert _preview_has_lineup(payload) is True


def test_preview_has_lineup_returns_false_for_empty_preview() -> None:
    payload = {"result": {"previewData": {"homeTeamLineUp": {}, "awayTeamLineUp": {}}}}
    assert _preview_has_lineup(payload) is False


def test_parser_preview_lineup_precheck_flag_default_and_enable() -> None:
    parser = build_parser()

    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.enable_preview_lineup_precheck is False

    args_enabled = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--enable-preview-lineup-precheck",
        ]
    )
    assert args_enabled.enable_preview_lineup_precheck is True


def test_build_team_record_payload_maps_rows() -> None:
    payload = _build_team_record_payload(
        section_id="kbaseball",
        category_id="kbo",
        season_code="2026",
        rows=[
            {
                "upperCategoryId": "kbaseball",
                "categoryId": "kbo",
                "seasonId": "2026",
                "teamId": "LG",
                "teamName": "LG",
                "teamShortName": "LG",
                "ranking": 1,
                "orderNo": 1,
                "gameType": "PRESEASON",
                "wra": 1.0,
                "gameCount": 2,
                "winGameCount": 1,
                "drawnGameCount": 1,
                "loseGameCount": 0,
                "gameBehind": 0.5,
                "continuousGameResult": "1승",
                "lastFiveGames": "-----",
                "offenseHra": 0.3,
                "defenseEra": 4.0,
            }
        ],
    )

    assert payload["categoryId"] == "kbo"
    assert payload["seasonCode"] == "2026"
    assert len(payload["records"]) == 1
    assert payload["records"][0]["teamId"] == "LG"
    assert payload["records"][0]["ranking"] == 1
    assert payload["records"][0]["raw"]["teamId"] == "LG"


def test_parser_team_record_sync_options() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.disable_team_record_sync is False
    assert args_default.team_record_season_code is None

    args_disabled = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--disable-team-record-sync",
            "--team-record-season-code",
            "2026",
        ]
    )
    assert args_disabled.disable_team_record_sync is True
    assert args_disabled.team_record_season_code == "2026"


def test_parser_schedule_import_start_date_option() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.schedule_import_start_date is None

    args = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--schedule-import-start-date",
            "2026-03-01",
        ]
    )
    assert args.schedule_import_start_date == date(2026, 3, 1)


def test_build_schedule_import_dates_defaults_to_at_least_one_day() -> None:
    dates = _build_schedule_import_dates(date(2026, 3, 14), 0)
    assert len(dates) == 1
    assert dates[0] == date(2026, 3, 14)


def test_build_schedule_import_dates_builds_range() -> None:
    dates = _build_schedule_import_dates(date(2026, 3, 14), 3)
    assert dates == [
        date(2026, 3, 14),
        date(2026, 3, 15),
        date(2026, 3, 16),
    ]


def test_build_schedule_import_dates_until_overrides_days() -> None:
    dates = _build_schedule_import_dates_until(date(2026, 6, 9), 1, date(2026, 6, 12))
    assert dates == [
        date(2026, 6, 9),
        date(2026, 6, 10),
        date(2026, 6, 11),
        date(2026, 6, 12),
    ]


def test_schedule_game_matches_target_date_filters_mismatched_game_date() -> None:
    game = {"gameId": "20260627HHSK02026", "gameDate": "2026-06-27"}

    assert _schedule_game_matches_target_date(game, date(2026, 6, 27)) is True
    assert _schedule_game_matches_target_date(game, date(2026, 6, 29)) is False


def test_schedule_game_matches_target_date_allows_missing_game_date() -> None:
    assert _schedule_game_matches_target_date({"gameId": "unknown"}, date(2026, 6, 29)) is True


def test_build_schedule_import_dates_for_refresh_respects_start_date() -> None:
    args = argparse.Namespace(
        schedule_import_start_date=None,
        schedule_import_days=1,
        schedule_import_until=None,
        schedule_refresh_start_date=date(2026, 8, 15),
        schedule_refresh_until=date(2026, 9, 30),
    )

    before = _build_schedule_import_dates_for_mode(args, today=date(2026, 8, 14), mode="refresh")
    assert before == [date(2026, 8, 14)]

    after = _build_schedule_import_dates_for_mode(args, today=date(2026, 8, 15), mode="refresh")
    assert after[0] == date(2026, 8, 15)
    assert after[-1] == date(2026, 9, 30)


def test_build_schedule_import_dates_for_daily_respects_start_date() -> None:
    args = argparse.Namespace(
        schedule_import_start_date=date(2026, 3, 1),
        schedule_import_days=1,
        schedule_import_until=date(2026, 3, 3),
        schedule_refresh_start_date=None,
        schedule_refresh_until=None,
    )

    dates = _build_schedule_import_dates_for_mode(args, today=date(2026, 6, 9), mode="daily")

    assert dates == [
        date(2026, 3, 1),
        date(2026, 3, 2),
        date(2026, 3, 3),
    ]


def test_parser_schedule_import_days_default_and_override() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.schedule_import_days == 1
    assert args_default.schedule_import_until is None
    assert args_default.schedule_refresh_start_date is None
    assert args_default.schedule_refresh_until is None

    args_custom = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--schedule-import-days",
            "45",
            "--schedule-import-until",
            "2026-09-07",
            "--schedule-refresh-start-date",
            "2026-08-15",
            "--schedule-refresh-until",
            "2026-09-30",
        ]
    )
    assert args_custom.schedule_import_days == 45
    assert args_custom.schedule_import_until == date(2026, 9, 7)
    assert args_custom.schedule_refresh_start_date == date(2026, 8, 15)
    assert args_custom.schedule_refresh_until == date(2026, 9, 30)


def test_parser_crawler_backend_retry_options_default_and_override() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.crawler_backend_timeout_sec == 8.0
    assert args_default.crawler_backend_retries == 2

    args_custom = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--crawler-backend-timeout-sec",
            "15",
            "--crawler-backend-retries",
            "9",
        ]
    )
    assert args_custom.crawler_backend_timeout_sec == 15.0
    assert args_custom.crawler_backend_retries == 9


def test_parser_dispatcher_singleton_options() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.dispatcher_lock_file == "log/dispatcher.lock"
    assert args_default.leader_replica_id is None

    args_custom = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--dispatcher-lock-file",
            "log/custom.lock",
            "--leader-replica-id",
            "replica-1",
        ]
    )
    assert args_custom.dispatcher_lock_file == "log/custom.lock"
    assert args_custom.leader_replica_id == "replica-1"


def test_parser_schedule_backend_sync_options_default_and_override() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.backend_sync_timeout_sec is None
    assert args_default.backend_sync_retries == 3

    args_custom = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--backend-sync-timeout-sec",
            "45",
            "--backend-sync-retries",
            "5",
        ]
    )
    assert args_custom.backend_sync_timeout_sec == 45.0
    assert args_custom.backend_sync_retries == 5


def test_should_skip_schedule_snapshot_for_live_status() -> None:
    assert _should_skip_schedule_snapshot({"statusCode": "LIVE"}) is True
    assert _should_skip_schedule_snapshot({"statusCode": "ING"}) is True
    assert _should_skip_schedule_snapshot({"statusCode": "SCHEDULED"}) is False


def test_should_skip_schedule_snapshot_for_live_inning_text_even_if_status_is_not_live() -> None:
    assert _should_skip_schedule_snapshot({"statusCode": "SCHEDULED", "statusInfo": "9\uD68C\uCD08"}) is True
    assert _should_skip_schedule_snapshot({"statusCode": "SCHEDULED", "statusInfo": "9\uD68C\uB9D0"}) is True
    assert _should_skip_schedule_snapshot({"statusCode": "SCHEDULED", "statusInfo": "9T"}) is True


def test_should_not_skip_schedule_snapshot_for_terminal_status_even_with_live_like_inning() -> None:
    assert _should_skip_schedule_snapshot({"statusCode": "RESULT", "statusInfo": "9\uD68C\uB9D0"}) is False
    assert _should_skip_schedule_snapshot({"statusCode": "FINISHED", "statusInfo": "9T"}) is False
    assert _should_skip_schedule_snapshot({"statusCode": "CANCELED", "statusInfo": "5\uD68C\uCD08"}) is False


def test_schedule_snapshot_signature_ignores_observed_at() -> None:
    first = {
        "homeTeam": "KT",
        "awayTeam": "롯데",
        "gameDate": "2026-07-05",
        "status": "SCHEDULED",
        "inning": "18:00",
        "homeScore": 0,
        "awayScore": 0,
        "observedAt": "2026-07-05T07:00:00Z",
    }
    second = {**first, "observedAt": "2026-07-05T07:05:00Z"}

    assert _schedule_snapshot_signature(first) == _schedule_snapshot_signature(second)


def test_schedule_import_skips_unchanged_snapshot(monkeypatch) -> None:
    game = {
        "gameId": "20260705LTKT02026",
        "homeTeamName": "KT",
        "awayTeamName": "롯데",
        "statusCode": "SCHEDULED",
        "statusInfo": "18:00",
        "gameDate": "2026-07-05",
    }
    posted: list[str] = []

    monkeypatch.setattr(live_wbc_dispatcher, "_fetch_schedule_games", lambda **kwargs: [game])

    def fake_post(*, game_id: str, **kwargs):
        posted.append(game_id)
        return {"insertedEvents": 0, "duplicateEvents": 0}

    monkeypatch.setattr(live_wbc_dispatcher, "_post_schedule_snapshot_to_backend", fake_post)
    signatures: dict[str, str] = {}

    for _ in range(2):
        assert _run_schedule_import(
            source_base_url="https://example.test",
            backend_base_url="https://backend.example.test",
            backend_api_key="key",
            target_date=date(2026, 7, 5),
            section_id="kbaseball",
            category_id="kbo",
            fetch_timeout=1.0,
            backend_timeout=1.0,
            backend_retries=1,
            schedule_snapshot_signatures=signatures,
        ) is True

    assert posted == ["20260705LTKT02026"]


def test_backend_game_matches_schedule_snapshot_ignores_observed_at() -> None:
    snapshot = {
        "homeTeam": "KT",
        "awayTeam": "롯데",
        "status": "SCHEDULED",
        "inning": "경기전",
        "startTime": "18:00",
        "homeScore": 0,
        "awayScore": 0,
        "observedAt": "2026-07-05T07:45:00Z",
    }
    backend_game = {
        "id": "20260705LTKT02026",
        "homeTeam": "KT",
        "awayTeam": "롯데",
        "status": "SCHEDULED",
        "inning": "경기전",
        "startTime": "18:00",
        "homeScore": 0,
        "awayScore": 0,
        "observedAt": "2026-07-05T07:42:00Z",
        "updatedAt": "2026-07-05T07:42:00Z",
    }

    assert _backend_game_matches_schedule_snapshot(backend_game, snapshot) is True


def test_schedule_import_skips_when_backend_state_matches(monkeypatch) -> None:
    game = {
        "gameId": "20260705LTKT02026",
        "homeTeamName": "KT",
        "awayTeamName": "롯데",
        "statusCode": "SCHEDULED",
        "statusInfo": "경기전",
        "gameDate": "2026-07-05",
        "gameDateTime": "2026-07-05T18:00:00",
    }
    posted: list[str] = []

    monkeypatch.setattr(live_wbc_dispatcher, "_fetch_schedule_games", lambda **kwargs: [game])
    monkeypatch.setattr(
        live_wbc_dispatcher,
        "_fetch_backend_games_by_id",
        lambda **kwargs: {
            "20260705LTKT02026": {
                "id": "20260705LTKT02026",
                "homeTeam": "KT",
                "awayTeam": "롯데",
                "status": "SCHEDULED",
                "inning": "경기전",
                "startTime": "18:00",
                "homeScore": 0,
                "awayScore": 0,
                "observedAt": "2026-07-05T07:42:00Z",
                "updatedAt": "2026-07-05T07:42:00Z",
            }
        },
    )

    def fake_post(*, game_id: str, **kwargs):
        posted.append(game_id)
        return {"insertedEvents": 0, "duplicateEvents": 0}

    monkeypatch.setattr(live_wbc_dispatcher, "_post_schedule_snapshot_to_backend", fake_post)

    assert _run_schedule_import(
        source_base_url="https://example.test",
        backend_base_url="https://backend.example.test",
        backend_api_key="key",
        target_date=date(2026, 7, 5),
        section_id="kbaseball",
        category_id="kbo",
        fetch_timeout=1.0,
        backend_timeout=1.0,
        backend_retries=1,
        schedule_snapshot_signatures={},
    ) is True

    assert posted == []


def test_map_schedule_status_supports_canceled_and_postponed() -> None:
    assert _map_schedule_status("ENDED") == "FINISHED"
    assert _map_schedule_status("CANCELED") == "CANCELED"
    assert _map_schedule_status("cancelled") == "CANCELED"
    assert _map_schedule_status("RAIN_CANCEL") == "CANCELED"
    assert _map_schedule_status("POSTPONED") == "POSTPONED"
    assert _map_schedule_status("ppd") == "POSTPONED"
    assert _map_schedule_status("suspended") == "POSTPONED"


# --- C1: 크롤러 비정상 종료 시 윈도우 재시작 ---


def _make_window(**overrides) -> RelayCheckWindow:
    defaults = dict(
        game_id="20260715LTKT02026",
        start_at=datetime(2026, 7, 15, 18, 0, tzinfo=KST),
    )
    defaults.update(overrides)
    return RelayCheckWindow(**defaults)


def test_handle_crawler_exit_nonzero_schedules_restart_with_backoff() -> None:
    now = datetime(2026, 7, 15, 19, 0, tzinfo=KST)
    window = _make_window(launched=True, checks_done=3)

    action = _handle_crawler_exit(window, 1, now, is_terminal=False)

    assert action == "restart-scheduled"
    assert window.launched is False
    assert window.exhausted is False
    assert window.restart_count == 1
    assert window.next_check_at == now + timedelta(seconds=60)


def test_handle_crawler_exit_backoff_grows_and_caps_at_900() -> None:
    assert _crawler_restart_backoff_sec(0) == 60.0
    assert _crawler_restart_backoff_sec(1) == 120.0
    assert _crawler_restart_backoff_sec(2) == 240.0
    assert _crawler_restart_backoff_sec(4) == 900.0
    assert _crawler_restart_backoff_sec(9) == 900.0


def test_handle_crawler_exit_zero_with_live_game_schedules_restart() -> None:
    now = datetime(2026, 7, 15, 19, 0, tzinfo=KST)
    window = _make_window(launched=True)

    action = _handle_crawler_exit(window, 0, now, is_terminal=False)

    assert action == "restart-scheduled"
    assert window.launched is False


def test_handle_crawler_exit_terminal_game_marks_exhausted() -> None:
    now = datetime(2026, 7, 15, 22, 0, tzinfo=KST)
    window = _make_window(launched=True)

    action = _handle_crawler_exit(window, 0, now, is_terminal=True)

    assert action == "terminal"
    assert window.exhausted is True
    assert window.restart_count == 0


def test_handle_crawler_exit_zero_with_unknown_status_assumes_terminal() -> None:
    now = datetime(2026, 7, 15, 22, 0, tzinfo=KST)
    window = _make_window(launched=True)

    action = _handle_crawler_exit(window, 0, now, is_terminal=None)

    assert action == "assumed-terminal"
    assert window.exhausted is True


def test_handle_crawler_exit_restart_cap_reached() -> None:
    now = datetime(2026, 7, 15, 20, 0, tzinfo=KST)
    window = _make_window(launched=True, restart_count=MAX_CRAWLER_RESTARTS_PER_GAME)

    action = _handle_crawler_exit(window, 1, now, is_terminal=False)

    assert action == "restart-cap"
    assert window.exhausted is True


# --- C2: import 실패 시 완료로 표시하지 않음 ---


def test_import_incomplete_when_today_failed_keeps_daily_import_due() -> None:
    today = date(2026, 7, 15)
    import_dates = [today, today + timedelta(days=1)]

    # 오늘 날짜 import 실패 → 완료로 표시하면 안 됨 (should_daily_import 유지)
    assert _import_marks_daily_complete(today, import_dates, {today}) is False
    # 미래 날짜만 실패한 경우는 오늘 기준으로 완료 처리
    assert _import_marks_daily_complete(today, import_dates, {today + timedelta(days=1)}) is True
    # 전부 성공
    assert _import_marks_daily_complete(today, import_dates, set()) is True


def test_import_incomplete_when_today_not_in_range_requires_all_success() -> None:
    today = date(2026, 7, 15)
    past_dates = [date(2026, 3, 1), date(2026, 3, 2)]

    assert _import_marks_daily_complete(today, past_dates, {date(2026, 3, 1)}) is False
    assert _import_marks_daily_complete(today, past_dates, set()) is True


# --- C4: 자정 윈도우 교체 시 전날 미종료 경기 유지 ---


def test_merge_relay_windows_retains_pending_previous_date_window() -> None:
    now = datetime(2026, 7, 16, 0, 10, tzinfo=KST)
    suspended_yesterday = _make_window(
        game_id="20260715SSLG02026",
        start_at=datetime(2026, 7, 15, 18, 0, tzinfo=KST),
        checks_done=12,
    )
    exhausted_yesterday = _make_window(
        game_id="20260715HHNC02026",
        start_at=datetime(2026, 7, 15, 18, 0, tzinfo=KST),
        exhausted=True,
    )
    existing = {
        suspended_yesterday.game_id: suspended_yesterday,
        exhausted_yesterday.game_id: exhausted_yesterday,
    }
    loaded_today = {
        "20260716LTKT02026": _make_window(
            game_id="20260716LTKT02026",
            start_at=datetime(2026, 7, 16, 18, 0, tzinfo=KST),
        ),
    }

    merged = _merge_relay_windows(existing, loaded_today, now)

    # 전날 미종료(우천 중단 등) 윈도우는 유지, 종료된 윈도우는 제거
    assert suspended_yesterday.game_id in merged
    assert merged[suspended_yesterday.game_id] is suspended_yesterday
    assert exhausted_yesterday.game_id not in merged
    assert "20260716LTKT02026" in merged


def test_merge_relay_windows_drops_stale_windows_beyond_max_age() -> None:
    now = datetime(2026, 7, 16, 0, 10, tzinfo=KST)
    stale = _make_window(
        game_id="20260710OBWO02026",
        start_at=now - timedelta(hours=72),
    )

    merged = _merge_relay_windows({stale.game_id: stale}, {}, now)

    assert merged == {}


def test_merge_relay_windows_keeps_existing_launched_window() -> None:
    now = datetime(2026, 7, 15, 12, 0, tzinfo=KST)
    launched = _make_window(launched=True, restart_count=2)
    loaded = {launched.game_id: _make_window()}

    merged = _merge_relay_windows({launched.game_id: launched}, loaded, now)

    assert merged[launched.game_id] is launched
    assert merged[launched.game_id].restart_count == 2


def test_parser_enable_file_log_default_off() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.enable_file_log is False

    args_enabled = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--enable-file-log",
        ]
    )
    assert args_enabled.enable_file_log is True
