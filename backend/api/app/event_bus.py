import asyncio
import logging
from collections import defaultdict

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class GameEventBus:
    def __init__(self) -> None:
        self._connections: dict[str, set[WebSocket]] = defaultdict(set)
        self._send_locks: dict[int, asyncio.Lock] = {}
        self._lock = asyncio.Lock()
        self.stats: dict[str, int] = {
            "register": 0,
            "disconnect": 0,
            "broadcast_calls": 0,
            "broadcast_targets": 0,
            "send_ok": 0,
            "send_fail": 0,
            "broadcast_no_targets": 0,
        }

    async def connect(self, websocket: WebSocket) -> bool:
        try:
            await websocket.accept()
            return True
        except Exception as exc:
            logger.info("ws accept aborted (client disconnected during handshake): %s", exc)
            return False

    async def register(self, game_id: str, websocket: WebSocket) -> None:
        async with self._lock:
            self._connections[game_id].add(websocket)
            self._send_locks.setdefault(id(websocket), asyncio.Lock())
            self.stats["register"] += 1

    async def disconnect(self, game_id: str, websocket: WebSocket) -> None:
        async with self._lock:
            connections = self._connections.get(game_id)
            if connections:
                connections.discard(websocket)
                if not connections:
                    self._connections.pop(game_id, None)
            self._send_locks.pop(id(websocket), None)
            self.stats["disconnect"] += 1

    async def safe_send(self, websocket: WebSocket, message: dict) -> bool:
        lock = self._send_locks.get(id(websocket))
        if lock is None:
            lock = asyncio.Lock()
            self._send_locks[id(websocket)] = lock
        async with lock:
            try:
                await websocket.send_json(message)
                self.stats["send_ok"] += 1
                return True
            except Exception as exc:
                self.stats["send_fail"] += 1
                logger.debug("ws send_json failed (client gone): %s", exc)
                return False

    async def broadcast(self, game_id: str, message: dict) -> None:
        # Snapshot without holding _lock (accept theoretical read-during-write; Python dict/set iteration copies a view)
        targets = list(self._connections.get(game_id, ()))

        self.stats["broadcast_calls"] += 1
        self.stats["broadcast_targets"] += len(targets)

        if not targets:
            self.stats["broadcast_no_targets"] += 1
            return

        async def _send(ws: WebSocket) -> WebSocket | None:
            ok = await self.safe_send(ws, message)
            return None if ok else ws

        results = await asyncio.gather(*[_send(ws) for ws in targets])
        stale = [ws for ws in results if ws is not None]

        if stale:
            async with self._lock:
                connections = self._connections.get(game_id, set())
                for ws in stale:
                    connections.discard(ws)
                    self._send_locks.pop(id(ws), None)
                if not connections:
                    self._connections.pop(game_id, None)

    def snapshot_stats(self) -> dict[str, int | dict[str, int]]:
        return {
            **self.stats,
            "connection_games": len(self._connections),
            "connections_total": sum(len(s) for s in self._connections.values()),
            "send_locks": len(self._send_locks),
        }


event_bus = GameEventBus()
