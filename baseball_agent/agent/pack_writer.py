"""PACK_WRITER Node — 최종 팩 디렉토리 생성·에셋 이동·manifest 직렬화.

Theme/Event 서브그래프가 /tmp에 저장한 이미지를 packs/<pack_id>/ 아래로 이동하고,
토큰·이벤트 메타·manifest JSON을 쓴다.

일부 asset이 failed_after_retry 상태라도 팩 저장은 계속 진행 (graceful degradation).
"""
from __future__ import annotations

from datetime import datetime
from pathlib import Path

from agent.events import emit_progress
from agent.state import PackState, ThemeAssetResult
from tools import pack_writer_tool as pwt


def write_pack(state: PackState) -> dict:
    emit_progress("PACK_WRITER", "start")

    pack_id = pwt.build_pack_id(
        mode=state.mode,
        player_id=state.player.id if state.player else None,
        concept_hint=state.concept_hint,
    )
    pack_root = pwt.create_pack_directory(pack_id)

    moved_paths: dict[str, str] = {}

    # ── Theme assets 이동 ──
    if state.should_build_theme():
        moved_paths.update(_move_theme_assets(state, pack_root))
        if state.theme_bundle.tokens:
            pwt.save_json(state.theme_bundle.tokens, pack_root, "theme/tokens.json")

    # ── Event assets 이동 ──
    if state.should_build_events():
        moved_paths.update(_move_event_assets(state, pack_root))

    # ── manifest.json 저장 ──
    completed_at = datetime.now().isoformat()
    # 상태 업데이트 먼저 반영 (manifest에 최종 상태 기록)
    state_for_manifest = state.model_copy(
        update={
            "pack_id": pack_id,
            "pack_path": str(pack_root),
            "completed_at": completed_at,
            "theme_bundle": _apply_paths(state.theme_bundle, moved_paths)
            if state.should_build_theme()
            else state.theme_bundle,
            "generated_events": {
                code: _apply_event_path(ev, moved_paths.get(f"events/{code}/hero.png"))
                for code, ev in state.generated_events.items()
            },
        }
    )
    pwt.write_manifest(state_for_manifest, pack_root)

    summary = state_for_manifest.asset_summary()
    emit_progress(
        "PACK_WRITER",
        "done",
        pack_id=pack_id,
        pack_path=str(pack_root),
        summary=summary,
        total_cost=round(state.total_cost_usd, 4),
    )

    return {
        "pack_id": pack_id,
        "pack_path": str(pack_root),
        "completed_at": completed_at,
        "theme_bundle": state_for_manifest.theme_bundle,
        "generated_events": state_for_manifest.generated_events,
        "current_node": "PACK_WRITER",
    }


# ────────────────────────────────────────────────────────────────
# 에셋 이동 헬퍼
# ────────────────────────────────────────────────────────────────


def _move_theme_assets(state: PackState, pack_root: Path) -> dict[str, str]:
    """배경 1장 + 워치 변형(800×1000) 1장 저장."""
    moved: dict[str, str] = {}
    asset: ThemeAssetResult = state.theme_bundle.background
    if asset.status != "ok" or not asset.path:
        return moved

    mobile_rel = "theme/background.png"
    try:
        pwt.move_asset(asset.path, pack_root, mobile_rel)
        moved[mobile_rel] = mobile_rel
    except FileNotFoundError:
        return moved

    watch_rel = "theme/background_watch.png"
    src_abs = str(pack_root / mobile_rel)
    if pwt.create_watch_variant(src_abs, pack_root, watch_rel) is not None:
        moved[watch_rel] = watch_rel

    return moved


def _move_event_assets(state: PackState, pack_root: Path) -> dict[str, str]:
    moved: dict[str, str] = {}
    for code, ev in state.generated_events.items():
        if ev.status == "ok" and ev.hero_path:
            rel = f"events/{code}/hero.png"
            try:
                pwt.move_asset(ev.hero_path, pack_root, rel)
                moved[rel] = rel
            except FileNotFoundError:
                continue
        if ev.meta:
            pwt.save_json(ev.meta, pack_root, f"events/{code}/meta.json")
    return moved


def _apply_paths(bundle, moved: dict[str, str]):
    """Theme bundle의 background path를 팩 루트 기준 상대 경로로 교체."""
    asset: ThemeAssetResult = bundle.background
    rel = "theme/background.png"
    if rel in moved and asset.status == "ok":
        return bundle.model_copy(
            update={"background": asset.model_copy(update={"path": rel})}
        )
    return bundle


def _apply_event_path(ev, rel: str | None):
    if rel and ev.status == "ok":
        return ev.model_copy(update={"hero_path": rel})
    return ev
