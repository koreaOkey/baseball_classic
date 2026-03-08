import sys
from pathlib import Path

CRAWLER_ROOT = Path(__file__).resolve().parent
if str(CRAWLER_ROOT) not in sys.path:
    sys.path.insert(0, str(CRAWLER_ROOT))

from backend_sender import _classify_event_type, build_snapshot_payload


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
