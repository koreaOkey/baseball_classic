import crawler
from crawler import _should_fetch_inning, crawl_once_detailed


def test_should_fetch_inning_fetches_current_and_previous_only() -> None:
    cached = {"textRelays": []}

    # 캐시가 없으면 항상 조회
    assert _should_fetch_inning(1, 5, None) is True
    # 현재/직전 이닝은 캐시가 있어도 매번 조회 (이닝 전환 직후 늦은 기록 반영)
    assert _should_fetch_inning(5, 5, cached) is True
    assert _should_fetch_inning(4, 5, cached) is True
    # 이미 종료된 이닝과 아직 시작 전 이닝은 캐시 사용
    assert _should_fetch_inning(3, 5, cached) is False
    assert _should_fetch_inning(6, 5, cached) is False


def test_crawl_once_detailed_uses_cache_for_completed_innings(monkeypatch) -> None:
    game_data = {
        "gameId": "20260715LTKT02026",
        "statusCode": "LIVE",
        "statusInfo": "5회말",
        "homeTeamName": "KT",
        "awayTeamName": "롯데",
    }
    fetched_urls: list[str] = []

    def fake_fetch_json(url: str):
        fetched_urls.append(url)
        if url.endswith("/schedule/games/20260715LTKT02026"):
            return {"result": {"game": game_data}}
        return {"result": {"textRelayData": {"textRelays": []}}}

    monkeypatch.setattr(crawler, "fetch_json", fake_fetch_json)

    relay_cache: dict = {}

    # 첫 폴: 캐시가 비어 있으므로 1~9회 전부 조회
    _, first_relays, _ = crawl_once_detailed(
        game_id="20260715LTKT02026", base_url="https://example.test", relay_cache=relay_cache
    )
    first_relay_fetches = [url for url in fetched_urls if "relay?inning=" in url]
    assert len(first_relay_fetches) == 9
    assert set(first_relays.keys()) == set(range(1, 10))

    # 두 번째 폴: 현재(5회)와 직전(4회) 이닝만 재조회, 나머지는 캐시 사용
    fetched_urls.clear()
    _, second_relays, _ = crawl_once_detailed(
        game_id="20260715LTKT02026", base_url="https://example.test", relay_cache=relay_cache
    )
    second_relay_fetches = [url for url in fetched_urls if "relay?inning=" in url]
    assert sorted(second_relay_fetches) == [
        "https://example.test/schedule/games/20260715LTKT02026/relay?inning=4",
        "https://example.test/schedule/games/20260715LTKT02026/relay?inning=5",
    ]
    # 병합 결과의 형태는 동일해야 한다
    assert set(second_relays.keys()) == set(range(1, 10))


def test_crawl_once_detailed_without_cache_fetches_everything(monkeypatch) -> None:
    game_data = {
        "gameId": "20260715LTKT02026",
        "statusCode": "LIVE",
        "statusInfo": "5회말",
        "homeTeamName": "KT",
        "awayTeamName": "롯데",
    }
    fetched_urls: list[str] = []

    def fake_fetch_json(url: str):
        fetched_urls.append(url)
        if url.endswith("/schedule/games/20260715LTKT02026"):
            return {"result": {"game": game_data}}
        return {"result": {"textRelayData": {"textRelays": []}}}

    monkeypatch.setattr(crawler, "fetch_json", fake_fetch_json)

    for _ in range(2):
        crawl_once_detailed(game_id="20260715LTKT02026", base_url="https://example.test")

    relay_fetches = [url for url in fetched_urls if "relay?inning=" in url]
    assert len(relay_fetches) == 18
