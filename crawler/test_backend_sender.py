import sys
from pathlib import Path

CRAWLER_ROOT = Path(__file__).resolve().parent
if str(CRAWLER_ROOT) not in sys.path:
    sys.path.insert(0, str(CRAWLER_ROOT))

from backend_sender import _classify_event_type, _normalize_status, build_snapshot_payload


def test_video_review_out_to_out_is_out() -> None:
    option = {
        "type": 2,
        "text": "4회말 9번타순 5구 후 20:37 ~ 20:38 (1분간) 일본요청 비디오 판독: S.겐다 2루 도루 관련 아웃→아웃",
    }

    assert _classify_event_type(option) == "OUT"


def test_video_review_safe_to_safe_is_other() -> None:
    option = {
        "type": 2,
        "text": "일본요청 비디오 판독: 2루 도루 관련 세이프→세이프",
    }

    assert _classify_event_type(option) == "OTHER"


def test_pitcher_change_with_player_change_block() -> None:
    option = {
        "type": 2,
        "text": "투수 메르세데스 : 투수 오석주 (으)로 교체",
        "playerChange": {
            "liveText": "투수 메르세데스 : 투수 오석주 (으)로 교체",
            "inPlayer": {"playerName": "오석주", "playerPos": "투수"},
            "outPlayer": {"playerName": "메르세데스", "playerPos": "투수"},
        },
    }

    assert _classify_event_type(option) == "PITCHER_CHANGE"


def test_pitcher_change_with_text_only() -> None:
    option = {
        "type": 2,
        "text": "투수 교체: 김민 -> 이로운",
    }

    assert _classify_event_type(option) == "PITCHER_CHANGE"


def test_half_inning_change_with_offense_text() -> None:
    option = {
        "type": 2,
        "text": "6\ud68c\ucd08 \uc77c\ubcf8 \uacf5\uaca9",
    }

    assert _classify_event_type(option) == "HALF_INNING_CHANGE"


def test_snapshot_payload_includes_offense_and_defense_team_metadata() -> None:
    game_data = {
        "homeTeamName": "Korea",
        "awayTeamName": "Japan",
        "statusCode": "STARTED",
        "currentInning": "6\ud68c\ucd08",
        "homeTeamScore": 0,
        "awayTeamScore": 1,
        "gameDateTime": "2026-03-08T12:00:00+09:00",
    }
    relays_by_inning = {
        6: {
            "textRelays": [
                {
                    "no": 1,
                    "homeOrAway": "0",
                    "textOptions": [
                        {
                            "seqno": 1,
                            "type": 2,
                            "text": "6\ud68c\ucd08 \uc77c\ubcf8 \uacf5\uaca9",
                            "currentGameState": {
                                "homeScore": 0,
                                "awayScore": 1,
                                "ball": 0,
                                "strike": 0,
                                "out": 0,
                            },
                        }
                    ],
                }
            ]
        }
    }

    payload = build_snapshot_payload(game_data=game_data, relays_by_inning=relays_by_inning)
    event = payload["events"][0]
    metadata = event["metadata"]

    assert event["type"] == "HALF_INNING_CHANGE"
    assert metadata["inning"] == 6
    assert metadata["half"] == "top"
    assert metadata["offenseTeam"] == "Japan"
    assert metadata["defenseTeam"] == "Korea"


def test_snapshot_payload_preserves_naver_pitch_detail_metadata() -> None:
    game_data = {
        "homeTeamName": "SSG",
        "awayTeamName": "KT",
        "statusCode": "STARTED",
        "currentInning": "5회초",
        "homeTeamScore": 2,
        "awayTeamScore": 0,
        "gameDateTime": "2026-06-07T17:00:00+09:00",
    }
    batter_record = {
        "name": "오윤석",
        "pcode": "64504",
        "batOrder": 8,
        "seasonHra": 0.278,
        "pa": 2,
        "ab": 2,
        "hit": 0,
        "rbi": 0,
        "hr": 0,
        "bb": 0,
        "so": 1,
    }
    relays_by_inning = {
        5: {
            "homeLineup": {
                "pitcher": [{"name": "베니지아노", "pcode": "56841", "seqno": 1, "ballCount": 75}],
                "batter": [],
            },
            "awayLineup": {
                "pitcher": [],
                "batter": [batter_record],
            },
            "textRelays": [
                {
                    "no": 40,
                    "homeOrAway": "0",
                    "metricOption": {
                        "homeTeamWinRate": 76.0,
                        "awayTeamWinRate": 24.0,
                        "wpaByPlate": -3.2,
                    },
                    "textOptions": [
                        {
                            "seqno": 210,
                            "text": "5구 헛스윙",
                            "type": 1,
                            "pitchNum": 5,
                            "pitchResult": "S",
                            "ptsPitchId": "260607_180506",
                            "speed": "148",
                            "stuff": "투심",
                            "batterRecord": batter_record,
                            "currentGameState": {
                                "homeScore": "2",
                                "awayScore": "0",
                                "pitcher": "56841",
                                "batter": "64504",
                                "strike": "3",
                                "ball": "2",
                                "out": "0",
                                "base1": "64504",
                                "base2": "0",
                                "base3": "0",
                            },
                        }
                    ],
                }
            ],
        }
    }

    payload = build_snapshot_payload(game_data=game_data, relays_by_inning=relays_by_inning)
    event = payload["events"][0]
    metadata = event["metadata"]

    assert event["sourceEventId"] == "05-040-0210"
    assert event["type"] == "STRIKE"
    assert event["inning"] == "5회초"
    assert metadata["batter"] == "오윤석"
    assert metadata["pitcher"] == "베니지아노"
    assert metadata["pitchNum"] == 5
    assert metadata["pitchResult"] == "S"
    assert metadata["pitchSpeed"] == 148
    assert metadata["pitchStuff"] == "투심"
    assert metadata["ptsPitchId"] == "260607_180506"
    assert metadata["ballAfter"] == 2
    assert metadata["strikeAfter"] == 3
    assert metadata["outAfter"] == 0
    assert metadata["batterRecord"]["batOrder"] == 8
    assert metadata["awayWinProbability"] == 24.0
    assert metadata["wpaByPlate"] == -3.2
    assert payload["bases"]["first"] is True
    assert payload["baseRunners"]["first"] == "오윤석"


def test_pitcher_stats_pick_up_ballcount_as_pitches_thrown() -> None:
    game_data = {
        "homeTeamName": "Hanwha",
        "awayTeamName": "LG",
        "statusCode": "STARTED",
        "currentInning": "1회초",
        "homeTeamScore": 0,
        "awayTeamScore": 0,
        "gameDateTime": "2026-05-08T18:30:00+09:00",
    }
    relays_by_inning = {
        1: {
            "homeLineup": {
                "pitcher": [
                    {"name": "박준영", "pcode": "52731", "seqno": 1, "ballCount": 16, "inn": "1.0"}
                ],
                "batter": [],
            },
            "awayLineup": {
                "pitcher": [
                    {"name": "송승기", "pcode": "51111", "seqno": 1, "ballCount": 0, "inn": "0.0"}
                ],
                "batter": [],
            },
            "textRelays": [],
        }
    }

    payload = build_snapshot_payload(game_data=game_data, relays_by_inning=relays_by_inning)
    by_name = {p["playerName"]: p for p in payload["pitcherStats"]}
    assert by_name["박준영"]["pitchesThrown"] == 16
    assert by_name["송승기"]["pitchesThrown"] == 0


def test_normalize_status_supports_canceled_and_postponed() -> None:
    assert _normalize_status("ENDED") == "FINISHED"
    assert _normalize_status("CANCELED") == "CANCELED"
    assert _normalize_status("cancelled") == "CANCELED"
    assert _normalize_status("RAIN_CANCEL") == "CANCELED"
    assert _normalize_status("POSTPONED") == "POSTPONED"
    assert _normalize_status("ppd") == "POSTPONED"


# MARK: - 이닝별 라인스코어 / 실책 추출

MOCK_RELAY_INNING_9 = (
    CRAWLER_ROOT.parent / "data" / "mock_baseball" / "20260401WOSK02026" / "relay_inning_9.json"
)


def _mock_game_data() -> dict:
    return {
        "homeTeamName": "SSG",
        "awayTeamName": "키움",
        "statusCode": "RESULT",
        "statusInfo": "경기종료",
        "homeTeamScore": 2,
        "awayTeamScore": 11,
        "gameDateTime": "2026-04-01T18:30:00+09:00",
    }


def test_snapshot_payload_includes_line_score_and_errors_from_mock_relay() -> None:
    import json

    relay_data = json.loads(MOCK_RELAY_INNING_9.read_text(encoding="utf-8"))["result"]["textRelayData"]

    payload = build_snapshot_payload(game_data=_mock_game_data(), relays_by_inning={9: relay_data})

    # inningScore 문자열 값이 int 로 변환되어 lineScore 로 노출된다
    assert payload["lineScore"]["home"] == {
        "1": 0, "2": 0, "3": 0, "4": 0, "5": 0, "6": 1, "7": 0, "8": 0, "9": 1,
    }
    assert payload["lineScore"]["away"] == {
        "1": 3, "2": 0, "3": 0, "4": 0, "5": 2, "6": 2, "7": 0, "8": 0, "9": 4,
    }
    # currentGameState.homeError/awayError → homeErrors/awayErrors
    assert payload["homeErrors"] == 2
    assert payload["awayErrors"] == 0


def test_snapshot_payload_omits_line_score_when_relay_has_no_inning_score() -> None:
    # relay 데이터가 없는 경로(경기 전 등)에서는 필드 자체가 생략되어야 한다 (하위 호환)
    payload = build_snapshot_payload(
        game_data=_mock_game_data(), relays_by_inning={1: {"textRelays": []}}
    )

    assert "lineScore" not in payload
    assert "homeErrors" not in payload
    assert "awayErrors" not in payload


def test_extract_line_score_skips_malformed_entries() -> None:
    from backend_sender import _extract_line_score

    relays = {
        1: {
            "textRelays": [{"no": 1, "homeOrAway": "0", "textOptions": []}],
            "inningScore": {
                "home": {"1": "0", "2": "-", "x": "3", "3": None},
                "away": "broken",
            },
        }
    }

    # 정수 변환 불가("-", None)·비숫자 키("x")·비딕셔너리 side 는 방어적으로 건너뛴다
    assert _extract_line_score(relays) == {"home": {"1": 0}}


def test_extract_line_score_prefers_freshest_played_inning() -> None:
    from backend_sender import _extract_line_score

    # 캐시된 미래 이닝(9회, textRelays 없음)의 stale inningScore 대신
    # 실제 진행된 최신 이닝(5회) relay 의 값을 사용해야 한다
    relays = {
        5: {
            "textRelays": [{"no": 1, "homeOrAway": "0", "textOptions": []}],
            "inningScore": {"home": {"1": "1"}, "away": {"1": "2"}},
        },
        9: {
            "textRelays": [],
            "inningScore": {"home": {"1": "0"}, "away": {"1": "0"}},
        },
    }

    assert _extract_line_score(relays) == {"home": {"1": 1}, "away": {"1": 2}}
