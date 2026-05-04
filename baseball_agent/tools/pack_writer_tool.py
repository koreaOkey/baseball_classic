"""pack_writer_tool — 팩 디렉토리 생성·이미지 저장·manifest 직렬화.

PACK_WRITER Node가 마지막에 호출한다. 규격:

    packs/<pack_id>/
      ├── manifest.json
      ├── theme/{splash.png, home_bg.png, lock.png, tokens.json}
      └── events/<EVENT>/{hero.png, meta.json}

Theme·Event 서브그래프는 이미지를 임시 경로에 저장하고 State에 path만 들고 있다가
본 Tool에서 최종 위치로 이동·직렬화한다.
"""
from __future__ import annotations

import json
import shutil
from datetime import datetime
from pathlib import Path
from typing import TYPE_CHECKING, Optional

# 워치 배경 규격 (openspec/specs/themes/spec.md §"워치 배경 이미지 규격")
WATCH_SIZE = (800, 1000)                # 4:5 비율
WATCH_SAFE_ZONE_TOP_RATIO = 0.42        # 상단 42% = 점수판·UI 영역

if TYPE_CHECKING:
    from agent.state import PackState

PACKS_ROOT = Path(__file__).parent.parent / "packs"


def build_pack_id(
    mode: str, player_id: Optional[str], concept_hint: Optional[str]
) -> str:
    """pack_YYYYMMDD_<seq>_<label> 형식.

    <seq>는 같은 날 생성된 팩 수 + 1.
    <label>은 player_id 또는 concept_hint 슬러그, 없으면 mode.
    """
    today = datetime.now().strftime("%Y%m%d")
    existing = (
        list(PACKS_ROOT.glob(f"pack_{today}_*")) if PACKS_ROOT.exists() else []
    )
    seq = f"{len(existing) + 1:02d}"

    label = player_id or _slugify(concept_hint) if concept_hint else mode
    if player_id:
        label = player_id
    elif concept_hint:
        label = _slugify(concept_hint)
    else:
        label = mode

    return f"pack_{today}_{seq}_{label}"


def create_pack_directory(pack_id: str) -> Path:
    """팩 루트·하위 폴더 생성 후 경로 반환."""
    root = PACKS_ROOT / pack_id
    (root / "theme").mkdir(parents=True, exist_ok=True)
    (root / "events").mkdir(parents=True, exist_ok=True)
    return root


def move_asset(src_path: str, pack_root: Path, rel_path: str) -> str:
    """임시 경로에 있던 이미지를 팩 디렉토리 내 최종 위치로 이동.

    반환: 팩 루트 기준 상대 경로 문자열 (manifest에 기록용).
    """
    src = Path(src_path)
    if not src.exists():
        raise FileNotFoundError(f"asset not found: {src_path}")

    dest = pack_root / rel_path
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src), str(dest))
    return rel_path


def save_json(data: dict, pack_root: Path, rel_path: str) -> str:
    dest = pack_root / rel_path
    dest.parent.mkdir(parents=True, exist_ok=True)
    with dest.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    return rel_path


def create_watch_variant(src_abs_path: str, pack_root: Path, rel_path: str) -> Optional[str]:
    """원본 이미지 → 워치 800×1000 변형 생성.

    1) 4:5 비율로 크롭. 크롭은 상단 clean 존을 더 보존하도록 편향 (top_crop = 25%).
       결과적으로 원본 prompt의 Zone A(42% clean)가 변형 이미지에서 약 41% 차지.
    2) 800×1000으로 리사이즈.
    3) 저장.

    반환: 성공 시 팩 루트 기준 상대 경로, 실패 시 None.
    """
    try:
        from PIL import Image
    except ImportError:
        return None

    src = Path(src_abs_path)
    if not src.exists():
        return None

    try:
        img = Image.open(src).convert("RGB")
    except Exception:
        return None

    w, h = img.size
    target_w, target_h = WATCH_SIZE
    target_ratio = target_w / target_h          # 0.8
    current_ratio = w / h

    if current_ratio < target_ratio:
        # 소스가 더 세로로 김 → 높이를 줄여 4:5 맞추기.
        # 상단(청결 존)을 더 보존하려 위에서 25%만 깎고 나머지는 아래에서.
        fit_h = int(w / target_ratio)
        excess = h - fit_h
        top_crop = max(0, excess // 4)
        img = img.crop((0, top_crop, w, top_crop + fit_h))
    elif current_ratio > target_ratio:
        # 소스가 더 가로로 김 → 너비 크롭 (중앙).
        fit_w = int(h * target_ratio)
        left_crop = (w - fit_w) // 2
        img = img.crop((left_crop, 0, left_crop + fit_w, h))

    img = img.resize(WATCH_SIZE, Image.LANCZOS)

    dest = pack_root / rel_path
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, "PNG")
    return rel_path


def write_manifest(state: "PackState", pack_root: Path) -> str:
    """PackState를 manifest.json으로 직렬화.

    저장되는 필드는 재현성·검수에 필요한 메타 위주. 내부 임시 경로 등은 제외.
    """
    payload = {
        "pack_id": state.pack_id,
        "mode": state.mode,
        "concept_hint": state.concept_hint,
        "generated_at": state.completed_at or datetime.now().isoformat(),
        "player": (
            state.player.model_dump(exclude={"visual_motifs"}) if state.player else None
        ),
        "catalog_gap": state.catalog_gap.model_dump() if state.catalog_gap else None,
        "visual_traits": state.visual_traits.model_dump() if state.visual_traits else None,
        "visual_assets": state.visual_assets.model_dump() if state.visual_assets else None,
        "trend_context": state.trend_context,
        "theme_bundle": {
            "background": state.theme_bundle.background.model_dump(),
            "tokens": state.theme_bundle.tokens,
        }
        if state.should_build_theme()
        else None,
        "events": {
            code: ev.model_dump() for code, ev in state.generated_events.items()
        }
        if state.should_build_events()
        else None,
        "summary": state.asset_summary(),
        "total_cost_usd": state.total_cost_usd,
        "errors": state.errors,
    }

    return save_json(payload, pack_root, "manifest.json")


# ── 내부 유틸 ─────────────────────────────────────────────────


def _slugify(s: str) -> str:
    """한글·공백 제거, 영숫자만 남기는 간단 슬러그."""
    import re

    s = s.lower().strip()
    s = re.sub(r"[^a-z0-9]+", "_", s)
    s = s.strip("_")
    return s or "untitled"
