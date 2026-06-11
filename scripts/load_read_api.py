#!/usr/bin/env python3
"""Read-only staging API load test.

This script simulates mobile/watch read traffic without mutating backend state.
Use it with a separate DB monitor process to correlate API latency with DB load.
"""

from __future__ import annotations

import argparse
import asyncio
import random
import statistics
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

import aiohttp


KST = ZoneInfo("Asia/Seoul")
DEFAULT_BASE_URL = "https://baseballclassic-production-4796.up.railway.app"
USER_AGENT = "BaseHapticLoadTest/1.0"


@dataclass(frozen=True)
class Route:
    name: str
    path: str
    weight: int


@dataclass
class Sample:
    route: str
    status: int
    elapsed_ms: float
    error: str | None = None


def _percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round((pct / 100) * (len(ordered) - 1)))))
    return ordered[index]


def _summarize(samples: list[Sample], elapsed_sec: float) -> dict[str, Any]:
    total = len(samples)
    ok = sum(1 for sample in samples if 200 <= sample.status < 400)
    errors = total - ok
    latencies = [sample.elapsed_ms for sample in samples if sample.elapsed_ms >= 0]

    by_route: dict[str, dict[str, Any]] = {}
    for route in sorted({sample.route for sample in samples}):
        route_samples = [sample for sample in samples if sample.route == route]
        route_latencies = [sample.elapsed_ms for sample in route_samples if sample.elapsed_ms >= 0]
        statuses = Counter(sample.status for sample in route_samples)
        by_route[route] = {
            "requests": len(route_samples),
            "status": dict(sorted(statuses.items())),
            "p50_ms": round(_percentile(route_latencies, 50), 1),
            "p95_ms": round(_percentile(route_latencies, 95), 1),
            "p99_ms": round(_percentile(route_latencies, 99), 1),
            "max_ms": round(max(route_latencies), 1) if route_latencies else 0.0,
        }

    return {
        "requests": total,
        "ok": ok,
        "errors": errors,
        "error_rate": round((errors / total) if total else 0.0, 4),
        "rps": round(total / elapsed_sec, 2) if elapsed_sec > 0 else 0.0,
        "p50_ms": round(_percentile(latencies, 50), 1),
        "p95_ms": round(_percentile(latencies, 95), 1),
        "p99_ms": round(_percentile(latencies, 99), 1),
        "max_ms": round(max(latencies), 1) if latencies else 0.0,
        "by_route": by_route,
    }


async def _request(session: aiohttp.ClientSession, base_url: str, route: Route) -> Sample:
    started = time.perf_counter()
    try:
        async with session.get(f"{base_url}{route.path}") as resp:
            await resp.read()
            elapsed_ms = (time.perf_counter() - started) * 1000
            return Sample(route=route.name, status=resp.status, elapsed_ms=elapsed_ms)
    except Exception as exc:
        elapsed_ms = (time.perf_counter() - started) * 1000
        return Sample(route=route.name, status=0, elapsed_ms=elapsed_ms, error=str(exc)[:160])


async def _fetch_seed_data(session: aiohttp.ClientSession, base_url: str) -> tuple[list[str], str]:
    today = datetime.now(KST).date().isoformat()
    game_ids: list[str] = []
    async with session.get(f"{base_url}/games?limit=20") as resp:
        resp.raise_for_status()
        games = await resp.json()
        game_ids = [str(game["id"]) for game in games if game.get("id")]
    if not game_ids:
        raise RuntimeError("staging has no games to target")
    return game_ids, today


def _build_routes(game_ids: list[str], today: str) -> list[Route]:
    game_id = random.choice(game_ids)
    return [
        Route("games_latest", "/games?limit=20", 20),
        Route("games_today", f"/games?date={today}&limit=20", 15),
        Route("game_summary", f"/games/{game_id}", 10),
        Route("game_state", f"/games/{game_id}/state", 25),
        Route("game_events", f"/games/{game_id}/events?limit=50", 25),
        Route("team_records", f"/team-records?categoryId=kbo&seasonCode={today[:4]}", 4),
        Route("stadiums", "/stadiums", 1),
    ]


async def _virtual_user(
    *,
    user_id: int,
    session: aiohttp.ClientSession,
    base_url: str,
    game_ids: list[str],
    today: str,
    deadline: float,
    think_time_sec: float,
    samples: list[Sample],
) -> None:
    del user_id
    while time.perf_counter() < deadline:
        routes = _build_routes(game_ids, today)
        route = random.choices(routes, weights=[item.weight for item in routes], k=1)[0]
        samples.append(await _request(session, base_url, route))
        if think_time_sec > 0:
            await asyncio.sleep(random.uniform(0, think_time_sec * 2))


async def _run_step(
    *,
    base_url: str,
    users: int,
    duration_sec: float,
    think_time_sec: float,
    request_timeout_sec: float,
) -> dict[str, Any]:
    timeout = aiohttp.ClientTimeout(total=request_timeout_sec)
    connector = aiohttp.TCPConnector(limit=max(users * 2, 100), ttl_dns_cache=300)
    headers = {"User-Agent": USER_AGENT}
    async with aiohttp.ClientSession(timeout=timeout, connector=connector, headers=headers) as session:
        game_ids, today = await _fetch_seed_data(session, base_url)
        print(f"  targets: {len(game_ids)} games, date={today}", flush=True)

        samples: list[Sample] = []
        started = time.perf_counter()
        deadline = started + duration_sec
        tasks = [
            asyncio.create_task(
                _virtual_user(
                    user_id=user_id,
                    session=session,
                    base_url=base_url,
                    game_ids=game_ids,
                    today=today,
                    deadline=deadline,
                    think_time_sec=think_time_sec,
                    samples=samples,
                )
            )
            for user_id in range(users)
        ]
        await asyncio.gather(*tasks)
        elapsed_sec = time.perf_counter() - started

    summary = _summarize(samples, elapsed_sec)
    summary["users"] = users
    summary["duration_sec"] = round(elapsed_sec, 2)
    return summary


def _print_summary(summary: dict[str, Any]) -> None:
    print(
        "  total={requests} ok={ok} errors={errors} error_rate={error_rate:.2%} "
        "rps={rps} p95={p95_ms}ms p99={p99_ms}ms max={max_ms}ms".format(**summary)
    )
    route_rows = summary["by_route"]
    for route, row in route_rows.items():
        print(
            f"    {route:<13} n={row['requests']:<5} "
            f"p95={row['p95_ms']:<7} p99={row['p99_ms']:<7} status={row['status']}"
        )


async def main() -> int:
    parser = argparse.ArgumentParser(description="Read-only staging API load test")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--steps", default="10,25,50,100", help="comma-separated virtual users")
    parser.add_argument("--duration-sec", type=float, default=60.0)
    parser.add_argument("--think-time-sec", type=float, default=0.2)
    parser.add_argument("--timeout-sec", type=float, default=15.0)
    parser.add_argument("--max-error-rate", type=float, default=0.01)
    parser.add_argument("--max-p95-ms", type=float, default=1000.0)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    steps = [int(item.strip()) for item in args.steps.split(",") if item.strip()]
    if not steps:
        raise SystemExit("--steps must include at least one user count")

    print(f"Read-only load test: base_url={base_url}")
    print(
        f"criteria: error_rate <= {args.max_error_rate:.2%}, "
        f"p95 <= {args.max_p95_ms}ms"
    )

    failed = False
    for users in steps:
        print(f"\n== step: users={users}, duration={args.duration_sec}s ==")
        try:
            summary = await _run_step(
                base_url=base_url,
                users=users,
                duration_sec=args.duration_sec,
                think_time_sec=args.think_time_sec,
                request_timeout_sec=args.timeout_sec,
            )
        except Exception as exc:
            failed = True
            print(f"  step failed before requests completed: {exc.__class__.__name__}: {exc}")
            print("  verdict: threshold exceeded")
            continue
        _print_summary(summary)
        step_failed = (
            summary["error_rate"] > args.max_error_rate
            or summary["p95_ms"] > args.max_p95_ms
        )
        failed = failed or step_failed
        if step_failed:
            print("  verdict: threshold exceeded")
        else:
            print("  verdict: pass")

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
