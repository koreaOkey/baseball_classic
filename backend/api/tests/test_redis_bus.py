"""Redis 캐시/릴레이 클라이언트의 바운디드 타임아웃 검증 (2026-09-05 Redis 재배포 장애 회귀 방지)."""
import asyncio
import sys
from pathlib import Path

API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))

from app import redis_bus  # noqa: E402
from app.redis_bus import RedisBroadcastRelay  # noqa: E402


class _HangingPublisher:
    async def get(self, key):
        await asyncio.sleep(60)

    async def set(self, key, value, ex=None):
        await asyncio.sleep(60)

    async def delete(self, key):
        await asyncio.sleep(60)

    async def publish(self, channel, payload):
        await asyncio.sleep(60)


class _BrokenPublisher:
    async def get(self, key):
        raise redis_bus.RedisConnectionError("Connection closed by server.")


class _HealthyPublisher:
    def __init__(self) -> None:
        self.store: dict[str, str] = {}

    async def get(self, key):
        return self.store.get(key)

    async def set(self, key, value, ex=None):
        self.store[key] = value

    async def delete(self, key):
        self.store.pop(key, None)


def _relay_with(publisher) -> RedisBroadcastRelay:
    relay = RedisBroadcastRelay(redis_url="redis://localhost:6379/0", channel="test-channel")
    relay._publisher = publisher
    return relay


def test_create_client_applies_connect_timeout_and_socket_timeout(monkeypatch) -> None:
    captured: dict = {}

    def fake_from_url(url, **kwargs):
        captured.clear()
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(redis_bus.Redis, "from_url", fake_from_url)
    relay = RedisBroadcastRelay(redis_url="redis://localhost:6379/0", channel="test-channel")

    relay._create_client()
    assert captured["socket_connect_timeout"] == redis_bus.REDIS_CONNECT_TIMEOUT_SEC
    assert captured["socket_timeout"] == redis_bus.REDIS_SOCKET_TIMEOUT_SEC

    # 구독 클라이언트는 blocking listen 을 쓰므로 socket_timeout 없이 connect 타임아웃만 적용
    relay._create_client(socket_timeout=None)
    assert captured["socket_connect_timeout"] == redis_bus.REDIS_CONNECT_TIMEOUT_SEC
    assert captured["socket_timeout"] is None


def test_get_cache_returns_none_quickly_when_redis_hangs(monkeypatch) -> None:
    monkeypatch.setattr(redis_bus, "REDIS_CACHE_OP_TIMEOUT_SEC", 0.05)
    relay = _relay_with(_HangingPublisher())

    async def scenario():
        loop = asyncio.get_running_loop()
        started = loop.time()
        result = await relay.get_cache("http:games:v1")
        return result, loop.time() - started

    result, elapsed = asyncio.run(scenario())

    assert result is None
    assert elapsed < 1.0
    assert relay.stats["cache_timeout"] == 1


def test_set_delete_publish_do_not_block_when_redis_hangs(monkeypatch) -> None:
    monkeypatch.setattr(redis_bus, "REDIS_CACHE_OP_TIMEOUT_SEC", 0.05)
    relay = _relay_with(_HangingPublisher())

    async def scenario():
        loop = asyncio.get_running_loop()
        started = loop.time()
        await relay.set_cache("k", {"a": 1}, ttl_sec=10)
        await relay.delete_cache("k")
        await relay.publish("20260905OBSK02026", {"type": "state"})
        return loop.time() - started

    elapsed = asyncio.run(scenario())

    assert elapsed < 1.0
    assert relay.stats["cache_timeout"] == 2
    assert relay.stats["publish_fail"] == 1


def test_get_cache_swallows_connection_error_and_counts_it() -> None:
    relay = _relay_with(_BrokenPublisher())

    assert asyncio.run(relay.get_cache("k")) is None
    assert relay.stats["cache_fail"] == 1


def test_cache_roundtrip_still_works() -> None:
    relay = _relay_with(_HealthyPublisher())

    async def scenario():
        await relay.set_cache("k", {"items": [1, 2]}, ttl_sec=10)
        hit = await relay.get_cache("k")
        await relay.delete_cache("k")
        miss = await relay.get_cache("k")
        return hit, miss

    hit, miss = asyncio.run(scenario())

    assert hit == {"items": [1, 2]}
    assert miss is None
    assert relay.stats["cache_fail"] == 0


def test_reconnect_delay_grows_and_caps() -> None:
    relay = RedisBroadcastRelay(redis_url="redis://x", channel="t", reconnect_delay_sec=1.0)

    assert relay._next_reconnect_delay() == 1.0
    assert relay._next_reconnect_delay() == 2.0
    assert relay._next_reconnect_delay() == 4.0
    for _ in range(5):
        relay._next_reconnect_delay()
    assert relay._next_reconnect_delay() == redis_bus.SUBSCRIBE_RECONNECT_MAX_SEC
