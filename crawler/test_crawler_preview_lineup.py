from crawler import _inject_preview_lineup_into_relays


def test_inject_preview_lineup_into_relays_populates_lineup_and_entry() -> None:
    relays_by_inning = {1: {}}
    preview_payload = {
        "result": {
            "previewData": {
                "homeTeamLineUp": {
                    "fullLineUp": [
                        {"playerCode": "p1", "playerName": "HomeStarter", "positionName": "선발투수", "position": "1"},
                        {"playerCode": "b1", "playerName": "HomeB1", "positionName": "유격수", "position": "6"},
                    ],
                    "batterCandidate": [{"playerCode": "hb2", "playerName": "HomeBench"}],
                    "pitcherBullpen": [{"playerCode": "hp2", "playerName": "HomeBullpen"}],
                },
                "awayTeamLineUp": {
                    "fullLineUp": [
                        {"playerCode": "p2", "playerName": "AwayStarter", "positionName": "선발투수", "position": "1"},
                        {"playerCode": "b2", "playerName": "AwayB1", "positionName": "중견수", "position": "8"},
                    ],
                    "batterCandidate": [{"playerCode": "ab2", "playerName": "AwayBench"}],
                    "pitcherBullpen": [{"playerCode": "ap2", "playerName": "AwayBullpen"}],
                },
            }
        }
    }

    _inject_preview_lineup_into_relays(relays_by_inning, preview_payload)

    relay = relays_by_inning[1]
    assert relay["homeLineup"]["batter"][0]["name"] == "HomeB1"
    assert relay["homeLineup"]["batter"][0]["batOrder"] == 1
    assert relay["homeLineup"]["pitcher"][0]["name"] == "HomeStarter"
    assert relay["awayLineup"]["batter"][0]["name"] == "AwayB1"
    assert relay["awayEntry"]["pitcher"][0]["name"] == "AwayBullpen"


def test_inject_preview_lineup_into_relays_noop_when_empty() -> None:
    relays_by_inning = {1: {}}
    preview_payload = {"result": {"previewData": {"homeTeamLineUp": {}, "awayTeamLineUp": {}}}}

    _inject_preview_lineup_into_relays(relays_by_inning, preview_payload)

    assert relays_by_inning[1] == {}
