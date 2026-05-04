"""Research 체인 — CHOOSE_PLAYER · RESEARCH_PLAYER · EXTRACT_TRAITS.

Step 6 에서 LLM 경로 추가. OPENAI_API_KEY가 있으면 자율 선정·저작권 필터가
LLM으로 동작하고, 없으면 Step 5와 동일한 규칙 기반 fallback으로 끝까지 완주.
"""
from __future__ import annotations

import json
from typing import Any, Optional

from agent.config import LLM_MODEL, get_client, has_llm
from agent.events import emit_progress
from agent.state import PackState, PlayerProfile, VisualTraits
from tools import player_database as pdb
from tools import web_search as ws


# ────────────────────────────────────────────────────────────────
# CHOOSE_PLAYER
# ────────────────────────────────────────────────────────────────


def choose_player(state: PackState) -> dict:
    """선수를 State에 주입.

    우선순위:
      1) state.player 이미 있음 → skip
      2) state.team_input 있음 → 팀 마스코트 합성 PlayerProfile (선수 없이)
      3) state.player_name_input 있음 → DB 매칭
      4) mode=auto + LLM → 자율 선정
      5) fallback → popularity top 1
    """
    emit_progress("CHOOSE_PLAYER", "start")

    if state.player is not None:
        emit_progress("CHOOSE_PLAYER", "skip", reason="already set")
        return {"current_node": "CHOOSE_PLAYER"}

    # 1) 팀 마스코트 모드 (--team)
    if state.team_input and not state.player_name_input:
        team_match = pdb.find_team(state.team_input)
        if team_match is None:
            emit_progress(
                "CHOOSE_PLAYER",
                "fail",
                reason=f"team not found: {state.team_input}",
            )
            return {
                "errors": [f"team not found: {state.team_input}"],
                "current_node": "CHOOSE_PLAYER",
            }
        team_code, team = team_match
        profile = PlayerProfile(
            id=f"{team_code.lower()}_mascot",
            name_kr=f"{team['name_kr']} 마스코트",
            team_code=team_code,
            team_name_kr=team["name_kr"],
            team_colors=team.get("colors", []),
            jersey_num=1,  # generic
            position="MASCOT",
            visual_motifs=[],
            signature_moves=[],
            is_mascot=True,
        )
        emit_progress(
            "CHOOSE_PLAYER",
            "done",
            player=profile.name_kr,
            team=profile.team_name_kr,
            source="team_mascot",
        )
        return {"player": profile, "current_node": "CHOOSE_PLAYER"}

    chosen: Optional[dict] = None
    decision_source = "unknown"

    # 2) 명시적 --player
    if state.player_name_input:
        chosen = pdb.find_player(state.player_name_input)
        decision_source = "explicit_input"
        if chosen is None:
            emit_progress(
                "CHOOSE_PLAYER",
                "fail",
                reason=f"player not found: {state.player_name_input}",
            )
            return {
                "errors": [f"player not found: {state.player_name_input}"],
                "current_node": "CHOOSE_PLAYER",
            }

    # 3) auto 모드
    if chosen is None:
        candidates = pdb.top_players_by_popularity(10)
        if not candidates:
            emit_progress("CHOOSE_PLAYER", "fail", reason="empty db")
            return {
                "errors": ["CHOOSE_PLAYER: empty database"],
                "current_node": "CHOOSE_PLAYER",
            }

        if has_llm():
            chosen = _llm_pick_candidate(candidates)
            decision_source = "llm_autonomous"
        else:
            chosen = candidates[0]
            decision_source = "popularity_top1_fallback"

    enriched = pdb.enrich_with_team(chosen)
    profile = PlayerProfile(
        id=enriched["id"],
        name_kr=enriched["name_kr"],
        team_code=enriched["team_code"],
        team_name_kr=enriched.get("team_name_kr", enriched["team_code"]),
        team_colors=enriched.get("team_colors", []),
        jersey_num=enriched["jersey_num"],
        position=enriched["position"],
        nickname=enriched.get("nickname"),
        visual_motifs=enriched.get("visual_motifs", []),
        signature_moves=enriched.get("signature_moves", []),
    )

    emit_progress(
        "CHOOSE_PLAYER",
        "done",
        player=profile.name_kr,
        team=profile.team_name_kr,
        jersey=profile.jersey_num,
        source=decision_source,
    )
    return {"player": profile, "current_node": "CHOOSE_PLAYER"}


def _llm_pick_candidate(candidates: list[dict]) -> dict:
    """LLM이 10 후보 중 '최근 이슈로 콘텐츠화하기 가장 매력적인' 한 명을 선정.

    JSON 모드로 {"id": ..., "reason": ...}을 받고, 매칭 실패 시 첫 후보로 폴백.
    """
    client = get_client()
    if client is None:
        return candidates[0]

    summaries = "\n".join(
        [
            f"- {p['id']} | {p['name_kr']} ({p.get('team_name_kr', p['team_code'])}, "
            f"{p['position']}, #{p['jersey_num']}, 인기 {p.get('popularity_score', 0)}, "
            f"이슈 키워드: {p.get('recent_issue_keywords', [])})"
            for p in candidates
        ]
    )
    prompt = (
        "당신은 야구 콘텐츠 큐레이터다. 아래 후보 중 최근 이슈가 있고 "
        "이미지 팩(앱 테마·이벤트 스틸)으로 풀기 가장 매력적인 선수 1명을 고른다. "
        "결과는 JSON으로만 답한다.\n\n"
        f"후보:\n{summaries}\n\n"
        '응답 형식: {"id": "후보의 id", "reason": "한 문장 이유"}'
    )

    try:
        resp = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            max_tokens=200,
            temperature=0.4,
        )
        content = resp.choices[0].message.content or "{}"
        decision = json.loads(content)
        chosen_id = decision.get("id")
        for p in candidates:
            if p["id"] == chosen_id:
                return p
    except Exception:
        pass

    return candidates[0]


# ────────────────────────────────────────────────────────────────
# RESEARCH_PLAYER
# ────────────────────────────────────────────────────────────────


def research_player(state: PackState) -> dict:
    """web_search로 최근 이슈 보강.

    마스코트 합성 player(is_mascot=True)는 검색할 실제 인물이 없으므로 skip.
    """
    emit_progress("RESEARCH_PLAYER", "start")

    if state.player is None:
        emit_progress("RESEARCH_PLAYER", "skip", reason="no player")
        return {"current_node": "RESEARCH_PLAYER"}

    if state.player.is_mascot:
        emit_progress("RESEARCH_PLAYER", "skip", reason="mascot pseudo-player")
        return {"current_node": "RESEARCH_PLAYER"}

    query = f"{state.player.name_kr} {state.player.team_name_kr} 최근 이슈"
    result = ws.web_search(query, max_results=3)

    recent = [r["snippet"] for r in result.get("results", [])]
    updated_player = state.player.model_copy(update={"recent_issues": recent})

    emit_progress(
        "RESEARCH_PLAYER",
        "done",
        query=query,
        hits=len(recent),
        is_stub=result.get("is_stub", False),
        source=result.get("source", "unknown"),
    )
    return {"player": updated_player, "current_node": "RESEARCH_PLAYER"}


# ────────────────────────────────────────────────────────────────
# EXTRACT_TRAITS
# ────────────────────────────────────────────────────────────────


def extract_traits(state: PackState) -> dict:
    """저작권 안전 시각 모티프만 추출.

    마스코트 합성: 팀컬러만 사용 (LLM 호출 없이 규칙 기반).
    LLM 사용 가능: 얼굴·초상 관련 어휘 제거 + mood_tags·composition_hints 풍성화.
    LLM 없음: 규칙 기반.
    """
    emit_progress("EXTRACT_TRAITS", "start")

    if state.player is None:
        emit_progress("EXTRACT_TRAITS", "skip", reason="no player")
        return {"current_node": "EXTRACT_TRAITS"}

    if state.player.is_mascot:
        traits, source = _rule_based_extract(state.player), "mascot_rule"
    elif has_llm():
        traits, source = _llm_extract(state.player), "llm"
    else:
        traits, source = _rule_based_extract(state.player), "rule"

    emit_progress(
        "EXTRACT_TRAITS",
        "done",
        colors=traits.dominant_colors,
        n_motifs=len(traits.motifs),
        n_mood=len(traits.mood_tags),
        source=source,
    )
    return {"visual_traits": traits, "current_node": "EXTRACT_TRAITS"}


def _rule_based_extract(player: PlayerProfile) -> VisualTraits:
    """LLM 없을 때의 폴백 (Step 5와 동일)."""
    return VisualTraits(
        dominant_colors=player.team_colors[:3],
        motifs=list(player.visual_motifs),
        mood_tags=[],
        composition_hints=[],
    )


def _llm_extract(player: PlayerProfile) -> VisualTraits:
    """LLM이 저작권 금지어를 제거하고 visual 모티프·mood·composition을 풍성화."""
    client = get_client()
    if client is None:
        return _rule_based_extract(player)

    prompt = (
        "아래 선수를 주제로 추상 스타일의 이미지 프롬프트를 만들 예정이다. "
        "얼굴·초상·인물의 외모 묘사는 절대 금지(저작권). "
        "등번호·팀컬러·포지션 실루엣·플레이 모션·별명 모티프만 허용. "
        "JSON으로 답한다.\n\n"
        f"선수: {player.name_kr} ({player.team_name_kr} #{player.jersey_num}, {player.position})\n"
        f"팀 컬러: {player.team_colors}\n"
        f"시그니처 모션: {player.signature_moves}\n"
        f"기존 모티프: {player.visual_motifs}\n"
        f"최근 이슈: {player.recent_issues}\n\n"
        "응답 형식:\n"
        "{\n"
        '  "dominant_colors": ["#RRGGBB", ...],\n'
        '  "motifs": ["짧은 한국어/영어 문구", ...],\n'
        '  "mood_tags": ["impact", "calm", ...],\n'
        '  "composition_hints": ["wide shot", "low angle", ...]\n'
        "}"
    )

    try:
        resp = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            max_tokens=400,
            temperature=0.5,
        )
        raw: dict[str, Any] = json.loads(resp.choices[0].message.content or "{}")
        # 저작권 금지어 재필터 (LLM이 실수해도 2중 가드)
        forbidden = {"face", "portrait", "얼굴", "초상", "외모", "머리", "헤어"}
        motifs = [
            m for m in raw.get("motifs", []) if not any(f in m.lower() for f in forbidden)
        ]
        return VisualTraits(
            dominant_colors=raw.get("dominant_colors") or player.team_colors[:3],
            motifs=motifs or list(player.visual_motifs),
            mood_tags=raw.get("mood_tags", []),
            composition_hints=raw.get("composition_hints", []),
        )
    except Exception:
        return _rule_based_extract(player)
