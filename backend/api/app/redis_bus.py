from __future__ import annotations

import asyncio
import json
import logging
import os
import socket
import time
from collections.abc import Awaitable, Callable
from contextlib import suppress
from typing import Any

try:
    from redis.asyncio import Redis
    from redis.exceptions import ConnectionError as RedisConnectionError
    from redis.exceptions import TimeoutError as RedisTimeoutError

    _REDIS_AVAILABLE = True
except ModuleNotFoundError:
    Redis = Any  # type: ignore[assignment,misc]
    RedisConnectionError = ConnectionError  # type: ignore[assignment,misc]
    RedisTimeoutError = TimeoutError  # type: ignore[assignment,misc]
    _REDIS_AVAILABLE = False


logger = logging.getLogger(__name__)

RedisMessageHandler = Callable[[str, dict[str, Any]], Awaitable[None]]

# 2026-09-05 19:28~19:32 KST 장애: Redis 컨테이너가 재배포되자 타임아웃 없는 연결 시도가
# 커널 SYN 재시도가 끝날 때까지 블로킹됐고, 모든 HTTP 경로가 캐시 조회를 먼저 하므로
# API 전체가 4.5분간 멈췄다. 연결·명령·캐시 작업 모두 바운디드 타임아웃을 둔다.
REDIS_CONNECT_TIMEOUT_SEC = 2.0
REDIS_SOCKET_TIMEOUT_SEC = 2.0
# 캐시 조회/저장 한 번에 허용하는 총 시간(풀 대기 포함). 초과 시 캐시 미스로 취급한다.
REDIS_CACHE_OP_TIMEOUT_SEC = 3.0
# 구독 재연결 백오프 상한. 실패가 이어져도 로그 폭주 없이 주기적으로 재시도한다.
SUBSCRIBE_RECONNECT_MAX_SEC = 10.0
_CACHE_FAIL_LOG_INTERVAL_SEC = 10.0


def _tcp_keepalive_options() -> dict[int, int]:
    """플랫폼이 지원하는 TCP keepalive 옵션만 모아 돌려준다 (Linux: idle 30s, interval 10s, 3회)."""
    options: dict[int, int] = {}
    for name, value in (("TCP_KEEPIDLE", 30), ("TCP_KEEPINTVL", 10), ("TCP_KEEPCNT", 3)):
        option = getattr(socket, name, None)
        if option is not None:
            options[option] = value
    return options


class RedisBroadcastRelay:
    def __init__(
        self,
        *,
        redis_url: str | None,
        channel: str,
        source_instance_id: str | None = None,
        reconnect_delay_sec: float = 1.0,
    ) -> None:
        self._redis_url = (redis_url or "").strip()
        self._channel = channel.strip() or "basehaptic:live_events"
        self._source_instance_id = source_instance_id or f"{socket.gethostname()}:{os.getpid()}"
        self._reconnect_delay_sec = max(0.2, reconnect_delay_sec)
        self._publisher: Redis | None = None
        self._subscriber: Redis | None = None
        self._subscriber_task: asyncio.Task | None = None
        self._stop_event = asyncio.Event()
        self.stats: dict[str, int] = {
            "publish_ok": 0,
            "publish_fail": 0,
            "sub_received": 0,
            "sub_skipped_own": 0,
            "sub_forwarded": 0,
            "sub_errors": 0,
            "sub_reconnects": 0,
            "cache_fail": 0,
            "cache_timeout": 0,
        }
        self.subscribed_at: float | None = None
        self._last_cache_failure_log_at = 0.0
        self._sub_consecutive_failures = 0

    @property
    def enabled(self) -> bool:
        return bool(self._redis_url) and _REDIS_AVAILABLE

    async def start(self, on_message: RedisMessageHandler) -> None:
        if not self._redis_url:
            logger.info("redis relay disabled: REDIS_URL is not configured")
            return
        if not _REDIS_AVAILABLE:
            logger.warning("redis relay disabled: redis package is not installed")
            return
        if self._subscriber_task is not None:
            return

        self._publisher = self._create_client()
        self._subscriber = self._create_client(socket_timeout=None)
        is_connected, detail = await self.ping()
        if not is_connected:
            logger.warning("redis relay connection check failed: %s", detail)
            await self.stop()
            return

        self._stop_event.clear()
        self._subscriber_task = asyncio.create_task(
            self._run_subscribe_loop(on_message),
            name="redis-broadcast-relay",
        )
        logger.info(
            "redis relay enabled: channel=%s source_instance_id=%s",
            self._channel,
            self._source_instance_id,
        )

    async def ping(self) -> tuple[bool, str]:
        if not self._redis_url:
            return False, "not_configured"
        if not _REDIS_AVAILABLE:
            return False, "redis_package_missing"

        client = self._publisher
        close_after_check = False
        if client is None:
            client = self._create_client()
            close_after_check = True

        try:
            await asyncio.wait_for(client.ping(), timeout=REDIS_CACHE_OP_TIMEOUT_SEC)
            return True, "connected"
        except Exception as exc:
            logger.warning("redis ping failed: %s", exc)
            return False, f"error:{type(exc).__name__}"
        finally:
            if close_after_check:
                with suppress(Exception):
                    await client.aclose()

    async def stop(self) -> None:
        self._stop_event.set()
        if self._subscriber_task is not None:
            self._subscriber_task.cancel()
            with suppress(asyncio.CancelledError):
                await self._subscriber_task
            self._subscriber_task = None

        if self._publisher is not None:
            with suppress(Exception):
                await self._publisher.aclose()
            self._publisher = None

        if self._subscriber is not None:
            with suppress(Exception):
                await self._subscriber.aclose()
            self._subscriber = None

    async def _run_cache_op(self, label: str, op: Awaitable[Any]) -> Any:
        """캐시 명령을 바운디드 타임아웃으로 실행한다. 실패는 None(캐시 미스)으로 흡수한다.

        Redis 가 죽거나 교체돼도 요청 처리(DB 로더)는 계속 진행돼야 하므로 예외를
        밖으로 내보내지 않는다. 연결·타임아웃 계열은 카운터와 스로틀된 경고로 남긴다.
        """
        try:
            return await asyncio.wait_for(op, timeout=REDIS_CACHE_OP_TIMEOUT_SEC)
        except asyncio.TimeoutError:
            self.stats["cache_timeout"] += 1
            self._log_cache_failure(label, "timeout")
        except (RedisConnectionError, RedisTimeoutError, OSError) as exc:
            self.stats["cache_fail"] += 1
            self._log_cache_failure(label, f"{type(exc).__name__}: {exc}")
        except Exception:
            self.stats["cache_fail"] += 1
            logger.debug("redis cache %s failed", label, exc_info=True)
        return None

    def _log_cache_failure(self, label: str, detail: str) -> None:
        now = time.monotonic()
        if now - self._last_cache_failure_log_at < _CACHE_FAIL_LOG_INTERVAL_SEC:
            return
        self._last_cache_failure_log_at = now
        logger.warning(
            "redis cache %s failed (%s); serving without cache. cache_fail=%s cache_timeout=%s",
            label,
            detail,
            self.stats["cache_fail"],
            self.stats["cache_timeout"],
        )

    async def set_cache(self, key: str, value: dict[str, Any], ttl_sec: int = 300) -> None:
        if not self.enabled or self._publisher is None:
            return
        try:
            payload = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
        except Exception:
            logger.debug("redis cache set failed: key=%s", key)
            return
        await self._run_cache_op("set", self._publisher.set(key, payload, ex=ttl_sec))

    async def get_cache(self, key: str) -> dict[str, Any] | None:
        if not self.enabled or self._publisher is None:
            return None
        raw = await self._run_cache_op("get", self._publisher.get(key))
        if raw is None:
            return None
        try:
            return json.loads(raw)
        except Exception:
            logger.debug("redis cache decode failed: key=%s", key)
            return None

    async def delete_cache(self, key: str) -> None:
        if not self.enabled or self._publisher is None:
            return
        await self._run_cache_op("delete", self._publisher.delete(key))

    async def publish(self, game_id: str, message: dict[str, Any]) -> None:
        if not self.enabled or self._publisher is None:
            return

        payload = json.dumps(
            {
                "source": self._source_instance_id,
                "gameId": game_id,
                "message": message,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        )
        try:
            await asyncio.wait_for(
                self._publisher.publish(self._channel, payload),
                timeout=REDIS_CACHE_OP_TIMEOUT_SEC,
            )
            self.stats["publish_ok"] += 1
        except (RedisConnectionError, RedisTimeoutError, asyncio.TimeoutError, OSError) as exc:
            self.stats["publish_fail"] += 1
            logger.warning(
                "redis relay publish connection issue: game_id=%s error=%s",
                game_id,
                exc,
            )
            await self._reset_publisher_client()
        except Exception:
            self.stats["publish_fail"] += 1
            logger.exception("redis relay publish failed: game_id=%s", game_id)

    async def _run_subscribe_loop(self, on_message: RedisMessageHandler) -> None:
        import time as _time
        while not self._stop_event.is_set():
            pubsub = None
            try:
                if self._subscriber is None:
                    return
                pubsub = self._subscriber.pubsub()
                await pubsub.subscribe(self._channel)
                self.subscribed_at = _time.time()
                logger.warning(
                    "redis relay SUBSCRIBED channel=%s source=%s",
                    self._channel,
                    self._source_instance_id,
                )
                self._sub_consecutive_failures = 0

                async for raw in pubsub.listen():
                    if self._stop_event.is_set():
                        break
                    if raw.get("type") != "message":
                        continue

                    self.stats["sub_received"] += 1
                    envelope = self._decode_envelope(raw.get("data"))
                    if envelope is None:
                        continue
                    if envelope["source"] == self._source_instance_id:
                        self.stats["sub_skipped_own"] += 1
                        continue

                    self.stats["sub_forwarded"] += 1
                    try:
                        await on_message(envelope["gameId"], envelope["message"])
                    except Exception:
                        self.stats["sub_errors"] += 1
                        logger.exception("redis relay on_message handler failed")
            except asyncio.CancelledError:
                break
            except (RedisConnectionError, RedisTimeoutError, OSError) as exc:
                self.stats["sub_reconnects"] += 1
                self.subscribed_at = None
                delay = self._next_reconnect_delay()
                logger.warning(
                    "redis relay subscribe connection dropped: %s; reconnecting in %.1fs",
                    exc,
                    delay,
                )
                await self._reset_subscriber_client()
                await asyncio.sleep(delay)
            except Exception:
                self.stats["sub_reconnects"] += 1
                self.subscribed_at = None
                delay = self._next_reconnect_delay()
                logger.exception("redis relay subscribe loop failed; reconnecting in %.1fs", delay)
                await self._reset_subscriber_client()
                await asyncio.sleep(delay)
            finally:
                if pubsub is not None:
                    with suppress(Exception):
                        await pubsub.aclose()

    def _create_client(self, *, socket_timeout: float | None = REDIS_SOCKET_TIMEOUT_SEC) -> Redis:
        # 구독 클라이언트는 blocking listen 을 쓰므로 socket_timeout 을 두면 idle 마다
        # 끊긴다 → 구독은 connect 타임아웃만, 명령(publisher)은 둘 다 적용한다.
        return Redis.from_url(
            self._redis_url,
            decode_responses=True,
            socket_keepalive=True,
            # 구독 연결은 health check 가 닿지 않으므로 커널 keepalive 로 죽은 피어를 ~60s 안에 감지한다.
            socket_keepalive_options=_tcp_keepalive_options() or None,
            socket_connect_timeout=REDIS_CONNECT_TIMEOUT_SEC,
            socket_timeout=socket_timeout,
            health_check_interval=30,
            retry_on_timeout=True,
        )

    def _next_reconnect_delay(self) -> float:
        delay = min(
            SUBSCRIBE_RECONNECT_MAX_SEC,
            self._reconnect_delay_sec * (2 ** self._sub_consecutive_failures),
        )
        self._sub_consecutive_failures += 1
        return delay

    async def _reset_publisher_client(self) -> None:
        if not self.enabled:
            return
        if self._publisher is not None:
            with suppress(Exception):
                await self._publisher.aclose()
        self._publisher = self._create_client()

    async def _reset_subscriber_client(self) -> None:
        if not self.enabled:
            return
        if self._subscriber is not None:
            with suppress(Exception):
                await self._subscriber.aclose()
        self._subscriber = self._create_client(socket_timeout=None)

    def _decode_envelope(self, raw: Any) -> dict[str, Any] | None:
        if raw is None:
            return None
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8", errors="ignore")
        if not isinstance(raw, str) or not raw.strip():
            return None

        try:
            payload = json.loads(raw)
        except Exception:
            logger.warning("redis relay received non-json payload")
            return None

        game_id = str(payload.get("gameId") or "").strip()
        source = str(payload.get("source") or "").strip()
        message = payload.get("message")
        if not game_id or not source or not isinstance(message, dict):
            return None
        return {"gameId": game_id, "source": source, "message": message}
