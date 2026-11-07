from naver_relay_bridge import game_id_of, validate_target


def test_validate_target_only_allows_naver_api_gw_https() -> None:
    ok = "https://api-gw.sports.naver.com/schedule/games/77771108KTSS02026/relay?inning=1"
    assert validate_target(ok) == ok
    assert validate_target("http://api-gw.sports.naver.com/schedule/games/x/relay") is None
    assert validate_target("https://evil.example.com/?https://api-gw.sports.naver.com") is None
    assert validate_target("https://api-gw.sports.naver.com.evil.com/x") is None
    assert validate_target("") is None


def test_game_id_of_extracts_game_from_api_path() -> None:
    assert game_id_of("https://api-gw.sports.naver.com/schedule/games/77771108KTSS02026/relay?inning=1") == "77771108KTSS02026"
    assert game_id_of("https://api-gw.sports.naver.com/statistics/categories/kbo/seasons/2026/teams") is None
