"""
5경기 동시 ingest + 다중 WebSocket 클라이언트 부하 테스트

시나리오:
  - 5경기 각각에 N개 WebSocket 클라이언트 연결 (폰 시뮬레이션)
  - 5경기 동시에 snapshot ingest (테스트 이벤트 포함)
  - WebSocket 클라이언트가 state/events broadcast를 정상 수신하는지 검증
  - 수신 지연시간, 누락 여부 측정

Usage:
    python scripts/test_ws_load.py \
        --backend-base-url https://your-app.up.railway.app \
        --backend-api-key your-api-key

    # 경기당 클라이언트 수 조정 (기본: 3)
    python scripts/test_ws_load.py \
        --backend-base-url https://your-app.up.railway.app \
        --backend-api-key your-api-key \
        --clients-per-game 5
"""

from __future__ import annotations

import argparse
import asyncio
import json
import time
import uuid
from dataclasses import dataclass, field
from datetime import UTC, date, datetime
from typing import Any
from zoneinfo import ZoneInfo

import aiohttp
import websockets

KST = ZoneInfo("Asia/Seoul")
NAVER_API = "https://api-gw.sports.naver.com"
USER_AGENT = "Mozilla/5.0 (compatible; BaseballClassicCrawler/1.0)"


@dataclass
class ClientResult:
    game_id: str
    client_index: int
    connected: bool = False
    initial_state: bool = False
    initial_events: bool = False
    broadcast_state: bool = False
    broadcast_events: bool = False
    broadcast_state_latency_ms: float | None = None
    broadcast_events_latency_ms: float | None = None
    messages_received: int = 0
    error: str | None = None


@dataclass
class GameIngestResult:
    game_id: str
    status_code: int = 0
    inserted_events: int = 0
    elapsed_ms: float = 0
    error: str | None = None


@dataclass
class TestRun:
    test_id: str = field(default_factory=lambda: uuid.uuid4().hex[:8])
    ingest_sent_at: float = 0.0


async def fetch_game_ids(session: aiohttp.ClientSession, target_date: date) -> list[str]:
    formatted = target_date.strftime("%Y-%m-%d")
    url = f"{NAVER_API}/schedule/games?date={formatted}&sectionId=kbaseball&categoryId=kbo"
    async with session.get(url) as resp:
        data = await resp.json()
    games = data.get("result", {}).get("games", [])
    return [g["gameId"] for g in games if g.get("gameId")]


def build_test_snapshot(game_id: str, test_id: str, num_events: int = 5) -> dict[str, Any]:
    """테스트용 snapshot: 고유 sourceEventId로 새 이벤트 생성."""
    now = datetime.now(UTC).isoformat().replace("+00:00", "Z")
    events = []
    for i in range(num_events):
        events.append({
            "sourceEventId": f"test_{test_id}_{game_id}_{i:04d}",
            "type": "OTHER",
            "description": f"[load-test] event {i+1}/{num_events}",
            "occurredAt": now,
            "metadata": {"inning": 1, "half": "top", "loadTest": True, "testId": test_id},
        })
    return {
        "homeTeam": "TEST_H",
        "awayTeam": "TEST_A",
        "status": "LIVE",
        "inning": "1T",
        "homeScore": 0,
        "awayScore": 0,
        "ball": 0,
        "strike": 0,
        "out": 0,
        "bases": {"first": False, "second": False, "third": False},
        "pitcher": "test_pitcher",
        "batter": "test_batter",
        "observedAt": now,
        "events": events,
        "lineupSlots": [],
        "batterStats": [],
        "pitcherStats": [],
        "notes": [],
    }


async def ws_client(
    backend_ws_url: str,
    game_id: str,
    client_index: int,
    test_run: TestRun,
    listen_duration: float,
) -> ClientResult:
    """단일 WebSocket 클라이언트: 연결 → 초기 메시지 수신 → broadcast 대기."""
    result = ClientResult(game_id=game_id, client_index=client_index)
    ws_url = f"{backend_ws_url}/ws/games/{game_id}"

    try:
        async with websockets.connect(ws_url, open_timeout=10) as ws:
            result.connected = True

            # 초기 메시지 수신 (state + events)
            try:
                for _ in range(2):
                    raw = await asyncio.wait_for(ws.recv(), timeout=5)
                    msg = json.loads(raw)
                    result.messages_received += 1
                    if msg.get("type") == "state":
                        result.initial_state = True
                    elif msg.get("type") == "events":
                        result.initial_events = True
            except asyncio.TimeoutError:
                pass

            # broadcast 대기 (ingest 이후 메시지)
            deadline = time.monotonic() + listen_duration
            while time.monotonic() < deadline:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    break
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
                    recv_at = time.monotonic()
                    msg = json.loads(raw)
                    result.messages_received += 1

                    # ingest 이전 메시지는 무시
                    if test_run.ingest_sent_at == 0:
                        continue

                    latency_ms = (recv_at - test_run.ingest_sent_at) * 1000

                    if msg.get("type") == "state" and not result.broadcast_state:
                        result.broadcast_state = True
                        result.broadcast_state_latency_ms = round(latency_ms, 1)
                    elif msg.get("type") == "events" and not result.broadcast_events:
                        result.broadcast_events = True
                        result.broadcast_events_latency_ms = round(latency_ms, 1)

                except asyncio.TimeoutError:
                    break

    except Exception as exc:
        result.error = str(exc)[:100]

    return result


async def ingest_snapshot(
    session: aiohttp.ClientSession,
    backend_url: str,
    api_key: str,
    game_id: str,
    test_id: str,
    num_events: int,
) -> GameIngestResult:
    """단일 경기 snapshot ingest."""
    payload = build_test_snapshot(game_id, test_id, num_events)
    url = f"{backend_url}/internal/crawler/games/{game_id}/snapshot"
    start = time.monotonic()
    try:
        async with session.post(
            url,
            json=payload,
            headers={"X-API-Key": api_key},
            timeout=aiohttp.ClientTimeout(total=30),
        ) as resp:
            elapsed = (time.monotonic() - start) * 1000
            body = await resp.json()
            return GameIngestResult(
                game_id=game_id,
                status_code=resp.status,
                inserted_events=body.get("insertedEvents", 0),
                elapsed_ms=round(elapsed, 1),
            )
    except Exception as exc:
        elapsed = (time.monotonic() - start) * 1000
        return GameIngestResult(
            game_id=game_id,
            status_code=0,
            elapsed_ms=round(elapsed, 1),
            error=str(exc)[:100],
        )


def print_header(title: str) -> None:
    print(f"\n{'=' * 65}")
    print(f"  {title}")
    print(f"{'=' * 65}")


async def main() -> None:
    parser = argparse.ArgumentParser(description="5경기 동시 ingest + 다중 WebSocket 부하 테스트")
    parser.add_argument("--backend-base-url", required=True)
    parser.add_argument("--backend-api-key", required=True)
    parser.add_argument("--clients-per-game", type=int, default=3, help="경기당 WebSocket 클라이언트 수 (기본: 3)")
    parser.add_argument("--events-per-game", type=int, default=5, help="경기당 테스트 이벤트 수 (기본: 5)")
    parser.add_argument("--date", help="테스트 날짜 (YYYY-MM-DD, 기본: 오늘)")
    parser.add_argument("--max-games", type=int, default=5)
    parser.add_argument("--listen-sec", type=float, default=10.0, help="broadcast 대기 시간 (기본: 10초)")
    args = parser.parse_args()

    backend_url = args.backend_base_url.rstrip("/")
    backend_ws_url = backend_url.replace("https://", "wss://").replace("http://", "ws://")
    target_date = date.fromisoformat(args.date) if args.date else datetime.now(KST).date()
    test_run = TestRun()

    async with aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session:
        # 1. 경기 목록
        print_header(f"경기 목록 조회 ({target_date.isoformat()})")
        game_ids = await fetch_game_ids(session, target_date)
        game_ids = game_ids[:args.max_games]
        if not game_ids:
            print("  경기가 없습니다.")
            return
        print(f"  {len(game_ids)}경기 발견")
        for gid in game_ids:
            print(f"    - {gid}")

        # 2. Health check
        print_header("Backend Health Check")
        async with session.get(f"{backend_url}/health") as resp:
            health = await resp.json()
            print(f"  status={health.get('status')}  env={health.get('environment')}")

        # 3. WebSocket 클라이언트 연결 + 대기
        total_clients = len(game_ids) * args.clients_per_game
        print_header(
            f"WebSocket 클라이언트 {total_clients}개 연결 "
            f"({len(game_ids)}경기 x {args.clients_per_game}클라이언트)"
        )

        ws_tasks = []
        for gid in game_ids:
            for ci in range(args.clients_per_game):
                ws_tasks.append(
                    ws_client(backend_ws_url, gid, ci, test_run, args.listen_sec)
                )

        # WebSocket 연결과 ingest를 동시에 실행
        # WebSocket은 먼저 연결 후 ingest 시작 → broadcast 수신 대기
        async def run_ws_clients() -> list[ClientResult]:
            return await asyncio.gather(*ws_tasks)

        async def run_ingest_after_delay() -> list[GameIngestResult]:
            # WebSocket 클라이언트가 연결+초기 메시지 수신할 시간 확보
            await asyncio.sleep(3)
            print(f"  {len(game_ids)}경기 동시 ingest 전송 중 ...", flush=True)
            test_run.ingest_sent_at = time.monotonic()
            ingest_tasks = [
                ingest_snapshot(session, backend_url, args.backend_api_key, gid, test_run.test_id, args.events_per_game)
                for gid in game_ids
            ]
            return await asyncio.gather(*ingest_tasks)

        ws_results_task = asyncio.create_task(run_ws_clients())
        ingest_results_task = asyncio.create_task(run_ingest_after_delay())

        ingest_results = await ingest_results_task
        ws_results = await ws_results_task

    # 4. 결과 출력 — Ingest
    print_header("Ingest 결과")
    for ir in sorted(ingest_results, key=lambda r: r.game_id):
        status = "OK" if ir.status_code == 200 else "FAIL"
        print(f"  {ir.game_id}  {status}  inserted={ir.inserted_events}  {ir.elapsed_ms}ms")
        if ir.error:
            print(f"    ERROR: {ir.error}")

    # 5. 결과 출력 — WebSocket
    print_header("WebSocket 클라이언트 결과")
    for gid in game_ids:
        game_clients = sorted(
            [r for r in ws_results if r.game_id == gid],
            key=lambda r: r.client_index,
        )
        print(f"\n  [{gid}]")
        for cr in game_clients:
            conn = "connected" if cr.connected else "FAIL"
            init = "state" if cr.initial_state else "     "
            init += "+events" if cr.initial_events else "       "
            bc_state = f"state({cr.broadcast_state_latency_ms}ms)" if cr.broadcast_state else "state(X)"
            bc_events = f"events({cr.broadcast_events_latency_ms}ms)" if cr.broadcast_events else "events(X)"
            print(f"    client[{cr.client_index}]  {conn}  init=[{init}]  broadcast=[{bc_state} {bc_events}]  total_msgs={cr.messages_received}")
            if cr.error:
                print(f"             ERROR: {cr.error}")

    # 6. 요약
    print_header("요약")
    connected = sum(1 for r in ws_results if r.connected)
    got_state = sum(1 for r in ws_results if r.broadcast_state)
    got_events = sum(1 for r in ws_results if r.broadcast_events)
    state_latencies = [r.broadcast_state_latency_ms for r in ws_results if r.broadcast_state_latency_ms is not None]
    event_latencies = [r.broadcast_events_latency_ms for r in ws_results if r.broadcast_events_latency_ms is not None]

    ingest_ok = sum(1 for r in ingest_results if r.status_code == 200)
    ingest_times = [r.elapsed_ms for r in ingest_results]

    print(f"  Ingest:     {ingest_ok}/{len(ingest_results)} 성공")
    if ingest_times:
        print(f"              min={min(ingest_times)}ms  max={max(ingest_times)}ms  avg={round(sum(ingest_times)/len(ingest_times), 1)}ms")

    print(f"  WebSocket:  {connected}/{total_clients} 연결")
    print(f"  Broadcast state:  {got_state}/{connected} 수신")
    print(f"  Broadcast events: {got_events}/{connected} 수신")

    if state_latencies:
        print(f"  State 지연:  min={min(state_latencies)}ms  max={max(state_latencies)}ms  avg={round(sum(state_latencies)/len(state_latencies), 1)}ms")
    if event_latencies:
        print(f"  Events 지연: min={min(event_latencies)}ms  max={max(event_latencies)}ms  avg={round(sum(event_latencies)/len(event_latencies), 1)}ms")

    if got_state < connected:
        print(f"\n  [!] {connected - got_state}개 클라이언트가 state broadcast를 못 받았습니다.")
    if got_events < connected:
        print(f"  [!] {connected - got_events}개 클라이언트가 events broadcast를 못 받았습니다.")
    if got_state == connected and got_events == connected:
        print(f"\n  모든 클라이언트가 broadcast를 정상 수신했습니다!")


if __name__ == "__main__":
    asyncio.run(main())
