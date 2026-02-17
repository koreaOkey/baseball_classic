import asyncio
from collections import defaultdict

from fastapi import WebSocket


class GameEventBus:
    def __init__(self) -> None:
        self._connections: dict[str, set[WebSocket]] = defaultdict(set)
        self._lock = asyncio.Lock()

    async def connect(self, game_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        async with self._lock:
            self._connections[game_id].add(websocket)

    async def disconnect(self, game_id: str, websocket: WebSocket) -> None:
        async with self._lock:
            connections = self._connections.get(game_id)
            if not connections:
                return
            connections.discard(websocket)
            if not connections:
                self._connections.pop(game_id, None)

    async def broadcast(self, game_id: str, message: dict) -> None:
        async with self._lock:
            targets = list(self._connections.get(game_id, set()))

        stale: list[WebSocket] = []
        for ws in targets:
            try:
                await ws.send_json(message)
            except Exception:
                stale.append(ws)

        if stale:
            async with self._lock:
                connections = self._connections.get(game_id, set())
                for ws in stale:
                    connections.discard(ws)
                if not connections:
                    self._connections.pop(game_id, None)


event_bus = GameEventBus()
