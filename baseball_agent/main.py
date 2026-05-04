"""야구봄 이미지 생성 Agent — 엔트리포인트.

사용:
    python main.py
    python main.py --mode auto
    python main.py --mode player_pack --player 이정후
    python main.py --mode theme_pack --concept_hint "봄 파스텔"
    python main.py --mode event_pack --team SSG       # 팀 마스코트 캐릭터 (선수 없이)
    python main.py --mode event_pack --team 두산

종료 코드:
    0 : 전체 성공
    1 : 부분 실패 (팩은 저장됨)
    2 : 치명적 실패
"""
from __future__ import annotations

import argparse
import sys
from datetime import datetime
from typing import Any

from agent.events import emit_progress
from agent.state import PackState
from agent.supervisor import build_graph


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="야구봄 이미지 생성 Agent",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--mode",
        choices=["auto", "player_pack", "theme_pack", "event_pack"],
        default="auto",
        help="팩 생성 모드 (기본: auto, Supervisor가 자율 결정)",
    )
    parser.add_argument(
        "--player",
        dest="player",
        default=None,
        help="대상 선수 이름 (한글) 또는 id. 미지정 시 auto 모드가 자율 선정.",
    )
    parser.add_argument(
        "--team",
        dest="team",
        default=None,
        help="팀 코드(SSG/KIA/...) 또는 한글명(두산/롯데/...). 선수 없이 팀 마스코트 캐릭터 생성.",
    )
    parser.add_argument(
        "--concept_hint",
        dest="concept_hint",
        default=None,
        help="테마 컨셉 힌트 (예: '봄 파스텔'). theme_pack 모드에서 특히 유용.",
    )
    return parser.parse_args(argv)


def run(args: argparse.Namespace) -> int:
    """Agent를 한 번 실행하고 종료 코드를 반환."""
    graph = build_graph()

    initial = PackState(
        mode=args.mode,
        player_name_input=args.player,
        team_input=args.team,
        concept_hint=args.concept_hint,
        started_at=datetime.now().isoformat(),
    )

    emit_progress(
        "MAIN",
        "start",
        mode=args.mode,
        player=args.player,
        team=args.team,
        concept_hint=args.concept_hint,
    )

    try:
        final: dict[str, Any] = graph.invoke(initial)
    except Exception as e:
        emit_progress("MAIN", "fail", reason=f"{type(e).__name__}: {e}")
        return 2

    final_state = PackState(**final)
    summary = final_state.asset_summary()

    emit_progress(
        "MAIN",
        "done",
        pack_id=final_state.pack_id,
        pack_path=final_state.pack_path,
        summary=summary,
        total_cost_usd=round(final_state.total_cost_usd, 4),
    )

    # 종료 코드 판정
    if final_state.pack_id is None:
        return 2

    theme_fail = (
        final_state.should_build_theme() and summary["theme_ok"] < summary["theme_total"]
    )
    events_fail = summary["events_failed"] > 0
    return 1 if (theme_fail or events_fail) else 0


def main() -> int:
    args = parse_args()
    return run(args)


if __name__ == "__main__":
    sys.exit(main())
