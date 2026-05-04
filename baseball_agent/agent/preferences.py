"""Preferences — 사람이 승인한 팩/에셋 이력 관리.

`data/approvals.jsonl`에 JSON 한 줄씩 append. visual_planner가 읽어 few-shot 예시로 주입.

승인은 두 레벨:
  - asset_key="all"    : 팩 전체 승인 (splash·home_bg·lock 동일 visual_assets)
  - asset_key="splash" : 특정 에셋만 승인 (나머지는 별개)

few-shot 로드 시 최신순 + rating 필터 (기본 >=4).
"""
from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

DATA_DIR = Path(__file__).parent.parent / "data"
PACKS_DIR = Path(__file__).parent.parent / "packs"
APPROVALS_FILE = DATA_DIR / "approvals.jsonl"

VALID_ASSET_KEYS = {"all", "splash", "home_bg", "lock"}


# ────────────────────────────────────────────────────────────────
# 기록 (write)
# ────────────────────────────────────────────────────────────────


def record_approval(
    pack_id: str,
    asset_key: str = "all",
    rating: int = 5,
    notes: str = "",
) -> dict[str, Any]:
    """pack_id를 읽어 manifest에서 visual_assets를 추출해 approvals.jsonl에 append."""
    if asset_key not in VALID_ASSET_KEYS:
        raise ValueError(f"asset_key must be one of {VALID_ASSET_KEYS}, got {asset_key!r}")

    manifest_path = PACKS_DIR / pack_id / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"manifest not found: {manifest_path}")

    with manifest_path.open(encoding="utf-8") as f:
        manifest = json.load(f)

    record = {
        "approved_at": datetime.now().isoformat(),
        "pack_id": pack_id,
        "asset_key": asset_key,
        "rating": int(rating),
        "notes": notes,
        "concept_hint": manifest.get("concept_hint"),
        "catalog_gap": manifest.get("catalog_gap"),
        "visual_assets": manifest.get("visual_assets"),
        "tokens": (manifest.get("theme_bundle") or {}).get("tokens"),
    }

    APPROVALS_FILE.parent.mkdir(parents=True, exist_ok=True)
    with APPROVALS_FILE.open("a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")

    return record


# ────────────────────────────────────────────────────────────────
# 조회 (read)
# ────────────────────────────────────────────────────────────────


def load_all_approvals() -> list[dict[str, Any]]:
    """approvals.jsonl 전체 로드."""
    if not APPROVALS_FILE.exists():
        return []
    out: list[dict[str, Any]] = []
    with APPROVALS_FILE.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                out.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return out


def load_recent_approvals(
    n: int = 5,
    min_rating: int = 4,
    concept_filter: Optional[str] = None,
) -> list[dict[str, Any]]:
    """최근 승인 n개 로드.

    - min_rating 이상만
    - concept_filter가 주어지면 concept_hint에 해당 문자열 포함된 것만
    - 최신순 (append 순서 역순)
    """
    all_records = load_all_approvals()

    # rating 필터
    filtered = [r for r in all_records if r.get("rating", 0) >= min_rating]

    # 컨셉 필터
    if concept_filter:
        cf = concept_filter.lower()
        filtered = [
            r
            for r in filtered
            if cf in (r.get("concept_hint") or "").lower()
        ]

    # 최신순 (뒤에서부터)
    return list(reversed(filtered))[:n]


# ────────────────────────────────────────────────────────────────
# Few-shot 예시 포매팅 (visual_planner에서 사용)
# ────────────────────────────────────────────────────────────────


def format_approved_as_examples(records: list[dict[str, Any]]) -> str:
    """승인 레코드 리스트를 LLM 프롬프트용 예시 블록 문자열로."""
    if not records:
        return ""

    lines = ["최근 사용자가 만족한 선택 예시 (스타일·팔레트·모티프 경향 참고):"]
    for i, r in enumerate(records, 1):
        va = r.get("visual_assets") or {}
        primary = va.get("primary_motifs") or []
        secondary = va.get("secondary_motifs") or []
        palette = va.get("palette_hints") or []
        mood = va.get("mood_keywords") or []
        style = va.get("style_direction") or "(unspecified)"
        concept = r.get("concept_hint") or "(no hint)"
        rating = r.get("rating", 0)
        notes = r.get("notes") or ""
        lines.append(
            f"  예시 {i} (⭐{rating}, concept: '{concept}'"
            + (f", note: {notes}" if notes else "")
            + "):"
        )
        lines.append(f"    - primary: {primary}")
        if secondary:
            lines.append(f"    - secondary: {secondary}")
        if palette:
            lines.append(f"    - palette: {palette}")
        if mood:
            lines.append(f"    - mood: {mood}")
        lines.append(f"    - style: {style}")
    lines.append(
        "\n위 경향성을 참고하되 이번 컨셉에 맞춰 새롭게 설계. 복사하지 말고 '느낌'만 반영."
    )
    return "\n".join(lines)
