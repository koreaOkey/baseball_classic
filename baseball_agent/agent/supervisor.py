"""Supervisor — LangGraph StateGraph 조립 + PLAN Node.

그래프 구조 (Step 5 기준):

    START
      ↓
    PLAN                           (mode 자율 판단 자리)
      ↓ (cond: needs_player?)
    [yes] CHOOSE_PLAYER → RESEARCH_PLAYER → EXTRACT_TRAITS
    [no]  (skip to catalog check)
      ↓ (cond: needs_catalog?)
    [yes] ANALYZE_CATALOG → FETCH_TREND → IDENTIFY_GAP
    [no]  (skip to dispatch)
      ↓
    DISPATCH                       (fan-out)
      ├─→ THEME_SUBGRAPH  ─┐
      └─→ EVENT_SUBGRAPH  ─┤
                           ▼
                      PACK_WRITER
                           ↓
                          END
"""
from __future__ import annotations

from typing import Any

from langgraph.graph import END, START, StateGraph

from agent import (
    catalog,
    event_subgraph,
    pack_writer,
    research,
    theme_subgraph,
    visual_planner,
)
from agent.events import emit_progress
from agent.state import PackState


# ────────────────────────────────────────────────────────────────
# PLAN · DISPATCH Node
# ────────────────────────────────────────────────────────────────


def plan(state: PackState) -> dict:
    """PLAN: mode를 확정.

    Step 5: auto 모드일 때 단순 규칙 — concept_hint 있으면 theme_pack,
    아니면 player_pack. Step 6에서 LLM이 web_search 트렌드까지 반영해 자율 결정.
    """
    emit_progress("PLAN", "start", mode=state.mode)

    if state.mode == "auto":
        if state.concept_hint:
            decided = "theme_pack"
            reason = "concept_hint 존재 → 테마 중심"
        elif state.player_name_input:
            decided = "player_pack"
            reason = "player 지정 → 선수 팩"
        else:
            decided = "player_pack"
            reason = "기본값: 이슈 있는 선수 자동 선정 (Step 6에서 LLM 판단으로 교체)"

        emit_progress("PLAN", "done", decided_mode=decided, reason=reason)
        return {"mode": decided, "current_node": "PLAN"}

    emit_progress("PLAN", "done", decided_mode=state.mode, reason="explicit")
    return {"current_node": "PLAN"}


def dispatch(state: PackState) -> dict:
    """DISPATCH: Theme/Event 서브그래프 병렬 실행을 위한 더미 진입점.

    실제 fan-out은 conditional edge + 리턴 리스트로 수행 (build_graph 참조).
    이 Node는 State 무변경, progress event만 방출.
    """
    emit_progress(
        "DISPATCH",
        "done",
        theme=state.should_build_theme(),
        events=state.should_build_events(),
    )
    return {"current_node": "DISPATCH"}


# ────────────────────────────────────────────────────────────────
# 조건부 라우팅
# ────────────────────────────────────────────────────────────────


def _route_after_plan(state: PackState) -> str:
    """PLAN 이후 첫 분기: player chain 필요 여부."""
    if state.needs_player():
        return "choose_player"
    if state.needs_catalog():
        return "analyze_catalog"
    return "plan_visual_assets"


def _route_after_extract_traits(state: PackState) -> str:
    """Research 끝난 뒤: catalog chain 필요 여부. 모두 끝나면 asset planner."""
    if state.needs_catalog():
        return "analyze_catalog"
    return "plan_visual_assets"


def _route_after_dispatch(state: PackState) -> list[str]:
    """DISPATCH 이후 fan-out — 모드에 따라 theme/event 중 필요한 것만 실행."""
    nexts: list[str] = []
    if state.should_build_theme():
        nexts.append("theme_subgraph")
    if state.should_build_events():
        nexts.append("event_subgraph")
    # 두 서브그래프 모두 필요 없는 케이스 (방어적): pack_writer로 직행
    if not nexts:
        nexts.append("pack_writer")
    return nexts


# ────────────────────────────────────────────────────────────────
# Graph Builder
# ────────────────────────────────────────────────────────────────


def build_graph() -> Any:
    """LangGraph StateGraph를 조립해 compile된 runnable을 반환."""
    builder = StateGraph(PackState)

    # Nodes
    builder.add_node("plan", plan)
    builder.add_node("choose_player", research.choose_player)
    builder.add_node("research_player", research.research_player)
    builder.add_node("extract_traits", research.extract_traits)
    builder.add_node("analyze_catalog", catalog.analyze_catalog)
    builder.add_node("fetch_trend", catalog.fetch_trend)
    builder.add_node("identify_gap", catalog.identify_gap)
    builder.add_node("plan_visual_assets", visual_planner.plan_visual_assets)
    builder.add_node("dispatch", dispatch)
    builder.add_node("theme_subgraph", theme_subgraph.build_theme)
    builder.add_node("event_subgraph", event_subgraph.build_events)
    builder.add_node("pack_writer", pack_writer.write_pack)

    # Entry
    builder.add_edge(START, "plan")

    # PLAN 이후 분기 — player/catalog/asset_planner 중 하나
    builder.add_conditional_edges(
        "plan",
        _route_after_plan,
        {
            "choose_player": "choose_player",
            "analyze_catalog": "analyze_catalog",
            "plan_visual_assets": "plan_visual_assets",
        },
    )

    # Research chain
    builder.add_edge("choose_player", "research_player")
    builder.add_edge("research_player", "extract_traits")
    builder.add_conditional_edges(
        "extract_traits",
        _route_after_extract_traits,
        {
            "analyze_catalog": "analyze_catalog",
            "plan_visual_assets": "plan_visual_assets",
        },
    )

    # Catalog chain → asset planner
    builder.add_edge("analyze_catalog", "fetch_trend")
    builder.add_edge("fetch_trend", "identify_gap")
    builder.add_edge("identify_gap", "plan_visual_assets")

    # asset planner → dispatch
    builder.add_edge("plan_visual_assets", "dispatch")

    # Dispatch fan-out — conditional edge의 list 반환으로 병렬 실행
    builder.add_conditional_edges(
        "dispatch",
        _route_after_dispatch,
        {
            "theme_subgraph": "theme_subgraph",
            "event_subgraph": "event_subgraph",
            "pack_writer": "pack_writer",
        },
    )

    # Fan-in — 두 서브그래프 모두 pack_writer로 수렴 (LangGraph가 자동 동기화)
    builder.add_edge("theme_subgraph", "pack_writer")
    builder.add_edge("event_subgraph", "pack_writer")

    # Exit
    builder.add_edge("pack_writer", END)

    return builder.compile()
