from __future__ import annotations

import asyncio
import json
import logging
import os
import socket
from collections.abc import Awaitable, Callable
from contextlib import suppress
from typing import Any

try:
    from redis.asyncio import Redis

    _REDIS_AVAILABLE = True
except ModuleNotFoundError:
    Redis = Any  # type: ignore[assignment,misc]
    _REDIS_AVAILABLE = False


logger = logging.getLogger(__name__)

RedisMessageHandler = Callable[[str, dict[str, Any]], Awaitable[None]]


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

        self._publisher = Redis.from_url(self._redis_url, decode_responses=True)
        self._subscriber = Redis.from_url(self._redis_url, decode_responses=True)
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
            client = Redis.from_url(self._redis_url, decode_responses=True)
            close_after_check = True

        try:
            await client.ping()
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
            await self._publisher.publish(self._channel, payload)
        except Exception:
            logger.exception("redis relay publish failed: game_id=%s", game_id)

    async def _run_subscribe_loop(self, on_message: RedisMessageHandler) -> None:
        while not self._stop_event.is_set():
            pubsub = None
            try:
                if self._subscriber is None:
                    return
                pubsub = self._subscriber.pubsub()
                await pubsub.subscribe(self._channel)

                async for raw in pubsub.listen():
                    if self._stop_event.is_set():
                        break
                    if raw.get("type") != "message":
                        continue

                    envelope = self._decode_envelope(raw.get("data"))
                    if envelope is None:
                        continue
                    if envelope["source"] == self._source_instance_id:
                        continue

                    await on_message(envelope["gameId"], envelope["message"])
            except asyncio.CancelledError:
                break
            except Exception:
                logger.exception("redis relay subscribe loop failed; reconnecting shortly")
                await asyncio.sleep(self._reconnect_delay_sec)
            finally:
                if pubsub is not None:
                    with suppress(Exception):
                        await pubsub.close()

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
