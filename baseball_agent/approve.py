"""승인 CLI — 사람이 마음에 든 팩/에셋을 기록해 다음 생성 시 few-shot으로 주입되게 한다.

사용 예:
    # 팩 전체 승인 (splash·home_bg·lock 동일 visual_assets)
    python approve.py pack_20260423_09_untitled

    # 특정 에셋만 승인 + 별점 + 메모
    python approve.py pack_20260423_09_untitled --asset splash --rating 5 --notes "색감 완벽"

    # 승인 목록 조회
    python approve.py --list
    python approve.py --list --concept "봄"

승인 기록 위치: data/approvals.jsonl (append only)
"""
from __future__ import annotations

import argparse
import json
import sys

from agent.preferences import (
    VALID_ASSET_KEYS,
    load_recent_approvals,
    record_approval,
)


def main() -> int:
    parser = argparse.ArgumentParser(description="팩/에셋 승인 및 조회")
    parser.add_argument(
        "pack_id",
        nargs="?",
        default=None,
        help="승인할 팩 ID (예: pack_20260423_09_untitled)",
    )
    parser.add_argument(
        "--asset",
        choices=sorted(VALID_ASSET_KEYS),
        default="all",
        help="승인 범위 (기본 all = 팩 전체)",
    )
    parser.add_argument(
        "--rating",
        type=int,
        default=5,
        help="별점 1~5 (기본 5)",
    )
    parser.add_argument("--notes", default="", help="메모 (선택)")
    parser.add_argument(
        "--list",
        dest="list_mode",
        action="store_true",
        help="최근 승인 목록 조회",
    )
    parser.add_argument(
        "--concept",
        default=None,
        help="--list 시 concept_hint 필터",
    )
    parser.add_argument(
        "--n",
        type=int,
        default=10,
        help="--list 시 표시 개수 (기본 10)",
    )

    args = parser.parse_args()

    if args.list_mode:
        records = load_recent_approvals(n=args.n, concept_filter=args.concept)
        if not records:
            print("no approvals yet.", file=sys.stderr)
            return 0
        for r in records:
            va = r.get("visual_assets") or {}
            print(
                f"⭐{r.get('rating', 0)} "
                f"{r.get('pack_id')} [{r.get('asset_key')}] "
                f"concept='{r.get('concept_hint') or '-'}' "
                f"style='{(va.get('style_direction') or '-')}'"
            )
            if r.get("notes"):
                print(f"    note: {r['notes']}")
        return 0

    if not args.pack_id:
        parser.print_help(sys.stderr)
        return 2

    try:
        rec = record_approval(
            pack_id=args.pack_id,
            asset_key=args.asset,
            rating=args.rating,
            notes=args.notes,
        )
    except FileNotFoundError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1
    except ValueError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 2

    print(
        f"✓ approved {rec['pack_id']} [{rec['asset_key']}] "
        f"⭐{rec['rating']}",
        file=sys.stderr,
    )
    if rec.get("visual_assets"):
        va = rec["visual_assets"]
        print(
            f"  recorded motifs: primary={len(va.get('primary_motifs') or [])}, "
            f"style={va.get('style_direction') or '(none)'}",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
