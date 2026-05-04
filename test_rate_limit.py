"""
네이버 스포츠 API rate limit 테스트 스크립트.

단계별(30s→20s→15s→10s→7s) 10분씩 실제 크롤러(crawl_once_detailed)를
호출하며 차단 임계값을 탐색한다.
"""

import argparse
import sys
import time
from datetime import datetime
from typing import Any, Dict, List

sys.path.insert(0, "crawler")

import requests
from crawler import crawl_once_detailed, BASE_URL

STAGES = [30, 20, 15, 10, 7]
STAGE_DURATION_SEC = 10 * 60  # 10분
CONSECUTIVE_FAIL_LIMIT = 5


def run_stage(game_id: str, interval: int, base_url: str) -> Dict[str, Any]:
    results: List[Dict[str, Any]] = []
    consecutive_fails = 0
    blocked = False
    start = time.monotonic()

    print(f"\n{'='*60}", flush=True)
    print(f"[stage] interval={interval}s  duration={STAGE_DURATION_SEC}s  start={datetime.now().isoformat(timespec='seconds')}", flush=True)
    print(f"{'='*60}", flush=True)

    while time.monotonic() - start < STAGE_DURATION_SEC:
        req_start = time.monotonic()
        try:
            game_data, relays_by_inning, combined = crawl_once_detailed(
                game_id=game_id, base_url=base_url,
            )
            elapsed_ms = round((time.monotonic() - req_start) * 1000)
            inning_count = len(relays_by_inning)
            status = (game_data.get("statusCode") or "").upper()
            entry = {
                "ok": True,
                "elapsed_ms": elapsed_ms,
                "innings_fetched": inning_count,
                "error": None,
            }
            results.append(entry)
            consecutive_fails = 0
            print(
                f"  [{len(results):>4}] OK  {elapsed_ms:>5}ms  "
                f"innings={inning_count}  status={status}",
                flush=True,
            )

        except requests.HTTPError as exc:
            elapsed_ms = round((time.monotonic() - req_start) * 1000)
            status_code = exc.response.status_code if exc.response is not None else None
            consecutive_fails += 1
            entry = {
                "ok": False,
                "elapsed_ms": elapsed_ms,
                "innings_fetched": 0,
                "error": f"HTTP {status_code}",
            }
            results.append(entry)
            label = "*** BLOCKED ***" if status_code in {429, 403} else "error"
            print(
                f"  [{len(results):>4}] HTTP {status_code}  {elapsed_ms:>5}ms  {label}",
                flush=True,
            )
            if status_code in {429, 403}:
                blocked = True
                break

        except requests.RequestException as exc:
            elapsed_ms = round((time.monotonic() - req_start) * 1000)
            consecutive_fails += 1
            entry = {
                "ok": False,
                "elapsed_ms": elapsed_ms,
                "innings_fetched": 0,
                "error": str(exc),
            }
            results.append(entry)
            print(f"  [{len(results):>4}] FAIL  {elapsed_ms:>5}ms  {exc}", flush=True)

        if consecutive_fails >= CONSECUTIVE_FAIL_LIMIT:
            print(
                f"\n  *** 연속 {CONSECUTIVE_FAIL_LIMIT}회 실패 — 차단 감지, 단계 중단 ***",
                flush=True,
            )
            blocked = True
            break

        time.sleep(interval)

    success = sum(1 for r in results if r["ok"])
    errors = len(results) - success
    elapsed_list = [r["elapsed_ms"] for r in results]
    avg_ms = round(sum(elapsed_list) / len(elapsed_list)) if elapsed_list else 0
    max_ms = max(elapsed_list) if elapsed_list else 0

    summary = {
        "interval": interval,
        "total": len(results),
        "success": success,
        "errors": errors,
        "success_rate": f"{success / len(results) * 100:.1f}%" if results else "N/A",
        "avg_ms": avg_ms,
        "max_ms": max_ms,
        "blocked": blocked,
    }

    print(
        f"\n  총 {summary['total']}회 | 성공 {summary['success']} | 에러 {summary['errors']} | "
        f"성공률 {summary['success_rate']} | 평균 {avg_ms}ms | 최대 {max_ms}ms",
        flush=True,
    )

    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description="네이버 스포츠 API rate limit 테스트 (실제 크롤러 사용)")
    parser.add_argument("--game-id", required=True, help="테스트할 경기 ID")
    parser.add_argument("--base-url", default=BASE_URL)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    summaries: List[Dict[str, Any]] = []

    print(f"Rate Limit 테스트 시작 (실제 크롤러): game_id={args.game_id}", flush=True)
    print(f"단계: {STAGES} (각 {STAGE_DURATION_SEC // 60}분)", flush=True)
    print(f"차단 감지 조건: HTTP 429/403 또는 연속 {CONSECUTIVE_FAIL_LIMIT}회 실패", flush=True)

    for interval in STAGES:
        summary = run_stage(args.game_id, interval, base_url)
        summaries.append(summary)

        if summary["blocked"]:
            print(f"\n*** {interval}초 간격에서 차단 감지 — 테스트 종료 ***", flush=True)
            break

    print(f"\n{'='*60}", flush=True)
    print("최종 결과 요약", flush=True)
    print(f"{'='*60}", flush=True)
    print(
        f"{'간격':>6} {'폴링':>6} {'성공':>6} {'에러':>6} {'성공률':>8} {'평균ms':>8} {'최대ms':>8} {'차단':>6}",
        flush=True,
    )
    print("-" * 68, flush=True)
    for s in summaries:
        print(
            f"{s['interval']:>4}초 {s['total']:>6} {s['success']:>6} {s['errors']:>6} "
            f"{s['success_rate']:>8} {s['avg_ms']:>7} {s['max_ms']:>7} "
            f"{'YES' if s['blocked'] else 'no':>6}",
            flush=True,
        )


if __name__ == "__main__":
    main()
