import argparse

from live_wbc_dispatcher import (
    _parse_schedule_url,
    _preview_has_lineup,
    _resolve_schedule_filters,
    _resolve_schedule_targets,
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
