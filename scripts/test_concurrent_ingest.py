"""
5경기 동시 ingest 부하 테스트

오늘 KBO 경기 데이터를 Naver API에서 가져와서
5개 크롤러를 동시에 실행하여 Railway backend에 동시 ingest합니다.

Usage:
    python scripts/test_concurrent_ingest.py \
        --backend-base-url https://your-app.up.railway.app \
        --backend-api-key your-api-key

    # 특정 날짜 테스트 (기본: 오늘)
    python scripts/test_concurrent_ingest.py \
        --backend-base-url https://your-app.up.railway.app \
        --backend-api-key your-api-key \
        --date 2026-03-21

    # 동시 실행 없이 순차 실행 비교
    python scripts/test_concurrent_ingest.py \
        --backend-base-url https://your-app.up.railway.app \
        --backend-api-key your-api-key \
        --sequential
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import requests

KST = ZoneInfo("Asia/Seoul")
NAVER_API = "https://api-gw.sports.naver.com"
USER_AGENT = "Mozilla/5.0 (compatible; BaseballClassicCrawler/1.0)"


def fetch_today_game_ids(target_date: date) -> list[str]:
    """Naver Sports API에서 해당 날짜의 KBO 경기 ID 목록을 가져옵니다."""
    formatted = target_date.strftime("%Y-%m-%d")
    url = f"{NAVER_API}/schedule/games?date={formatted}&sectionId=kbaseball&categoryId=kbo"
    resp = requests.get(url, headers={"User-Agent": USER_AGENT}, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    games = data.get("result", {}).get("games", [])
    game_ids = []
    for game in games:
        game_id = game.get("gameId") or game.get("id") or ""
        if game_id:
            game_ids.append(game_id)

    return game_ids


def run_single_crawler(
    *,
    game_id: str,
    backend_base_url: str,
    backend_api_key: str,
    backend_timeout: float,
    repo_root: Path,
) -> dict[str, Any]:
    """단일 크롤러 프로세스를 실행하고 결과를 반환합니다."""
    start = time.monotonic()
    cmd = [
        sys.executable,
        str(repo_root / "crawler" / "crawler.py"),
        "--game-id", game_id,
        "--backend-base-url", backend_base_url,
        "--backend-api-key", backend_api_key,
        "--backend-timeout", str(backend_timeout),
        "--backend-retries", "2",
        "--output", str(repo_root / "data" / f"test_relay_{game_id}.xlsx"),
    ]
    result = subprocess.run(
        cmd,
        cwd=str(repo_root),
        capture_output=True,
        text=True,
        timeout=120,
    )
    elapsed = time.monotonic() - start

    # stdout에서 backend 결과 파싱
    backend_lines = [line for line in result.stdout.splitlines() if "[backend]" in line]
    snapshot_lines = [line for line in result.stdout.splitlines() if "[snapshot]" in line]

    return {
        "game_id": game_id,
        "exit_code": result.returncode,
        "elapsed_sec": round(elapsed, 2),
        "backend_log": backend_lines,
        "snapshot_log": snapshot_lines,
        "error": result.stderr.strip() if result.returncode != 0 else None,
    }


def print_header(title: str) -> None:
    print(f"\n{'=' * 60}")
    print(f"  {title}")
    print(f"{'=' * 60}")


def print_result(result: dict[str, Any], index: int) -> None:
    status = "OK" if result["exit_code"] == 0 else "FAIL"
    print(f"\n  [{index+1}] {result['game_id']}  {status}  ({result['elapsed_sec']}s)")

    for line in result.get("snapshot_log", []):
        print(f"      {line}")
    for line in result.get("backend_log", []):
        print(f"      {line}")

    if result.get("error"):
        error_lines = result["error"].splitlines()[:3]
        for line in error_lines:
            print(f"      ERROR: {line}")


def main() -> None:
    parser = argparse.ArgumentParser(description="5경기 동시 ingest 부하 테스트")
    parser.add_argument("--backend-base-url", required=True, help="Railway backend URL")
    parser.add_argument("--backend-api-key", required=True, help="Backend API key")
    parser.add_argument("--backend-timeout", type=float, default=15.0, help="Backend request timeout")
    parser.add_argument("--date", help="테스트할 날짜 (YYYY-MM-DD, 기본: 오늘)")
    parser.add_argument("--sequential", action="store_true", help="순차 실행 (동시성 비교용)")
    parser.add_argument("--max-games", type=int, default=5, help="최대 경기 수")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    target_date = date.fromisoformat(args.date) if args.date else datetime.now(KST).date()

    # 1. 경기 ID 조회
    print_header(f"경기 목록 조회 ({target_date.isoformat()})")
    game_ids = fetch_today_game_ids(target_date)
    if not game_ids:
        print("  경기가 없습니다.")
        return

    game_ids = game_ids[:args.max_games]
    print(f"  {len(game_ids)}경기 발견:")
    for gid in game_ids:
        print(f"    - {gid}")

    # 2. Backend health check
    print_header("Backend Health Check")
    try:
        health_resp = requests.get(
            f"{args.backend_base_url.rstrip('/')}/health",
            timeout=10,
        )
        health_resp.raise_for_status()
        health_data = health_resp.json()
        print(f"  status={health_data.get('status')}  env={health_data.get('environment')}")
    except Exception as exc:
        print(f"  FAIL: {exc}")
        print("  Backend에 접속할 수 없습니다. URL을 확인하세요.")
        return

    # 3. 테스트 실행
    mode = "순차" if args.sequential else "동시"
    print_header(f"{len(game_ids)}경기 {mode} Ingest 테스트")

    crawler_kwargs = {
        "backend_base_url": args.backend_base_url.rstrip("/"),
        "backend_api_key": args.backend_api_key,
        "backend_timeout": args.backend_timeout,
        "repo_root": repo_root,
    }

    total_start = time.monotonic()
    results: list[dict[str, Any]] = []

    if args.sequential:
        for gid in game_ids:
            print(f"  실행 중: {gid} ...", flush=True)
            result = run_single_crawler(game_id=gid, **crawler_kwargs)
            results.append(result)
    else:
        with ThreadPoolExecutor(max_workers=len(game_ids)) as pool:
            futures = {
                pool.submit(run_single_crawler, game_id=gid, **crawler_kwargs): gid
                for gid in game_ids
            }
            print(f"  {len(game_ids)}개 크롤러 동시 실행 중 ...", flush=True)
            for future in as_completed(futures):
                results.append(future.result())

    total_elapsed = time.monotonic() - total_start

    # 4. 결과 출력
    # game_ids 순서로 정렬
    id_order = {gid: i for i, gid in enumerate(game_ids)}
    results.sort(key=lambda r: id_order.get(r["game_id"], 999))

    print_header(f"결과 ({mode} 실행)")
    for i, result in enumerate(results):
        print_result(result, i)

    # 5. 요약
    success_count = sum(1 for r in results if r["exit_code"] == 0)
    fail_count = len(results) - success_count
    elapsed_list = [r["elapsed_sec"] for r in results]

    print_header("요약")
    print(f"  모드:         {mode}")
    print(f"  총 소요 시간: {round(total_elapsed, 2)}s")
    print(f"  성공/실패:    {success_count}/{fail_count}")
    if elapsed_list:
        print(f"  개별 소요:    min={min(elapsed_list)}s  max={max(elapsed_list)}s  avg={round(sum(elapsed_list)/len(elapsed_list), 2)}s")

    if fail_count > 0:
        print("\n  [!] 실패한 경기가 있습니다. 위 로그를 확인하세요.")
    else:
        print("\n  모든 경기 ingest 성공!")


if __name__ == "__main__":
    main()
