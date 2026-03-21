import argparse
from datetime import date

from live_wbc_dispatcher import (
    _build_schedule_import_dates,
    _build_team_record_payload,
    _map_schedule_status,
    _parse_schedule_url,
    _preview_has_lineup,
    _resolve_schedule_filters,
    _resolve_schedule_targets,
    _should_skip_schedule_snapshot,
    build_parser,
)


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


def test_parser_schedule_import_days_default_and_override() -> None:
    parser = build_parser()
    args_default = parser.parse_args(["--backend-base-url", "http://localhost:8080", "--backend-api-key", "x"])
    assert args_default.schedule_import_days == 1

    args_custom = parser.parse_args(
        [
            "--backend-base-url",
            "http://localhost:8080",
            "--backend-api-key",
            "x",
            "--schedule-import-days",
            "45",
        ]
    )
    assert args_custom.schedule_import_days == 45


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


def test_map_schedule_status_supports_canceled_and_postponed() -> None:
    assert _map_schedule_status("CANCELED") == "CANCELED"
    assert _map_schedule_status("cancelled") == "CANCELED"
    assert _map_schedule_status("RAIN_CANCEL") == "CANCELED"
    assert _map_schedule_status("POSTPONED") == "POSTPONED"
    assert _map_schedule_status("ppd") == "POSTPONED"
    assert _map_schedule_status("suspended") == "POSTPONED"
