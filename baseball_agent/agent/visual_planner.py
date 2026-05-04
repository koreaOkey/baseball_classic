"""PLAN_VISUAL_ASSETS Node — 컨셉 기반 시각 asset 자율 선정.

Research 체인과 카탈로그 분석이 끝난 뒤, Theme/Event 서브그래프 dispatch 전에 실행.
LLM이 선수·컨셉·트렌드를 종합해 '이 테마엔 어떤 요소가 들어가야 하는가'를 결정한다.

설계 원칙: 모든 모티프는 **동등한 후보**. 야구공·하트·벚꽃·눈송이 중 뭘 넣을지는
오직 컨셉이 결정. 코드에서 특정 모티프를 "기본 후보"로 프라이밍하지 않는다.

LLM 없을 때는 키워드 매칭 폴백으로 파이프라인 완주.
"""
from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

from agent.config import LLM_MODEL, get_client, has_llm
from agent.events import emit_progress
from agent.preferences import format_approved_as_examples, load_recent_approvals
from agent.state import PackState, VisualAssets

PACKS_DIR = Path(__file__).parent.parent / "packs"

# visual_planner가 뽑으면 안 되는 모티프 키워드 (저작권 필터).
# LLM이 실수로 "team logos", "player names" 같은 걸 primary에 넣는 경우 대비.
_FORBIDDEN_MOTIF_KEYWORDS = {
    "team logo", "team logos", "logo", "logos",
    "player name", "player face", "player portrait",
    "jersey number", "mascot costume",  # 구단 공식 마스코트 모사 차단
    "real person", "celebrity",
    "trademark",
}


def plan_visual_assets(state: PackState) -> dict:
    """asset 리스트를 state.visual_assets에 기록."""
    emit_progress("PLAN_VISUAL_ASSETS", "start")

    n_few_shot = 0
    if has_llm():
        n_few_shot = len(load_recent_approvals(n=3, min_rating=4))
        assets = _llm_plan(state)
        source = "llm"
    else:
        assets = _rule_based_fallback(state)
        source = "rule"

    # 저작권 위반 모티프 후처리 필터
    assets = _sanitize_motifs(assets)

    emit_progress(
        "PLAN_VISUAL_ASSETS",
        "done",
        primary=assets.primary_motifs[:4],
        secondary=assets.secondary_motifs[:3],
        n_palette=len(assets.palette_hints),
        style=assets.style_direction,
        n_few_shot=n_few_shot,
        source=source,
    )
    return {"visual_assets": assets, "current_node": "PLAN_VISUAL_ASSETS"}


def _sanitize_motifs(assets: VisualAssets) -> VisualAssets:
    """primary/secondary에서 저작권 위반 의심 키워드 제거."""
    def clean(items: list[str]) -> list[str]:
        out = []
        for m in items:
            low = m.lower()
            if any(k in low for k in _FORBIDDEN_MOTIF_KEYWORDS):
                continue
            out.append(m)
        return out

    return assets.model_copy(update={
        "primary_motifs": clean(assets.primary_motifs),
        "secondary_motifs": clean(assets.secondary_motifs),
    })


def _recent_style_mood_stats(n: int = 6) -> tuple[Counter, Counter]:
    """최근 n개 팩의 manifest를 읽어 style_direction·mood_keywords 빈도 카운트."""
    styles: Counter = Counter()
    moods: Counter = Counter()
    if not PACKS_DIR.exists():
        return styles, moods
    packs = sorted(PACKS_DIR.glob("pack_*"))[-n:]
    for p in packs:
        mf = p / "manifest.json"
        if not mf.exists():
            continue
        try:
            m = json.loads(mf.read_text(encoding="utf-8"))
        except Exception:
            continue
        va = m.get("visual_assets") or {}
        if va.get("style_direction"):
            styles[va["style_direction"]] += 1
        for mk in va.get("mood_keywords") or []:
            moods[mk] += 1
    return styles, moods


# ────────────────────────────────────────────────────────────────
# LLM 경로
# ────────────────────────────────────────────────────────────────


def _llm_plan(state: PackState) -> VisualAssets:
    client = get_client()
    if client is None:
        return _rule_based_fallback(state)

    # 컨텍스트 요약 조립
    context_lines = [f"Mode: {state.mode}"]
    if state.player:
        context_lines.append(
            f"Player: {state.player.name_kr} ({state.player.team_name_kr}, "
            f"#{state.player.jersey_num}, {state.player.position})"
        )
        if state.player.recent_issues:
            context_lines.append(
                f"Recent news: {state.player.recent_issues[0][:200]}"
            )
    if state.concept_hint:
        context_lines.append(f"User concept hint: {state.concept_hint}")
    if state.catalog_gap and state.catalog_gap.recommended_concept:
        context_lines.append(
            f"Recommended concept: {state.catalog_gap.recommended_concept}"
        )
    if state.trend_context:
        context_lines.append(f"Trend context: {state.trend_context[:300]}")
    if state.visual_traits:
        if state.visual_traits.dominant_colors:
            context_lines.append(
                f"Player team colors: {state.visual_traits.dominant_colors}"
            )
        if state.visual_traits.motifs:
            context_lines.append(f"Player motifs: {state.visual_traits.motifs}")

    context = "\n".join(context_lines)

    # 최근 사용자 승인 예시 (few-shot) — 있으면 주입
    approved_examples = load_recent_approvals(
        n=3,
        min_rating=4,
        concept_filter=None,
    )
    examples_block = format_approved_as_examples(approved_examples)
    few_shot_section = f"\n\n{examples_block}\n\n" if examples_block else "\n\n"

    # 최근 스타일·무드 쏠림 탐지 → 다양성 압력
    recent_styles, recent_moods = _recent_style_mood_stats(n=6)
    diversity_lines: list[str] = []
    if recent_styles:
        top_style, top_count = recent_styles.most_common(1)[0]
        if top_count >= 2:
            diversity_lines.append(
                f"- 최근 6개 팩 중 style_direction='{top_style}'이 {top_count}회 반복됨. "
                f"이번엔 이와 뚜렷이 다른 스타일을 우선 고려하라."
            )
    if recent_moods:
        hot_moods = [m for m, c in recent_moods.most_common(5) if c >= 2]
        if hot_moods:
            diversity_lines.append(
                f"- 최근 자주 등장한 mood: {hot_moods}. 가능하면 이 단어들은 피하고 다른 결로 표현하라."
            )
    diversity_block = ""
    if diversity_lines:
        diversity_block = (
            "\n\n다양성 압력 (반복 방지):\n" + "\n".join(diversity_lines) + "\n"
        )

    prompt = (
        "너는 비주얼 아트 디렉터다. 아래 컨텍스트의 컨셉에 맞는 **배경 이미지** "
        "시각 요소를 결정한다. 이 배경은 모바일·워치 wallpaper로 쓰이며 "
        "**야구와 직접 관련 없는 순수 비주얼 컨셉**이어도 된다 — 컨셉이 자연·예술·"
        "패션·문화 등 어디서 왔든 상관없이 그 자체로 완결된 디자인을 만든다.\n\n"
        f"컨텍스트:\n{context}"
        f"{diversity_block}"
        f"{few_shot_section}"
        "원칙:\n"
        "  • 모든 모티프(야구공·하트·벚꽃·눈송이·별 등)는 동등한 후보다.\n"
        "  • 기본값이나 '안 넣으면 어색한 요소' 같은 건 없다.\n"
        "  • 오직 컨셉이 어울리는 요소를 결정한다.\n\n"
        "판단 지침:\n"
        "1) primary_motifs (3~5개, 영문 짧게): 이 컨셉을 가장 잘 표현하는 핵심 시각 요소.\n"
        "   예 (선수 트리뷰트): ['stylized baseballs', 'team hearts', 'stadium spotlight beams']\n"
        "   예 (봄 파스텔):     ['cherry blossoms', 'drifting petals', 'soft pastel clouds']\n"
        "   예 (겨울 야경):     ['snowflakes', 'crescent moon', 'star sparkles']\n"
        "2) secondary_motifs (2~3개): 채움·텍스처·악센트용.\n"
        "   예: ['soft sparkles', 'tiny dots', 'subtle grain']\n"
        "3) palette_hints (3~5): 컨셉에 맞는 컬러 이름 또는 hex.\n"
        "4) mood_keywords (3~5): 'soft', 'dreamy', 'bold', 'triumphant' 등.\n"
        "5) composition_note (한 문장, 선택): 요소 배치·흐름 등 구도 힌트.\n"
        "6) style_direction (한 문장, 필수): 이 컨셉을 살리는 비주얼 스타일.\n"
        "   후보 9종 (각기 완전히 다른 결):\n"
        "     • flat vector poster — 볼드 그래픽·원색·깔끔한 실루엣\n"
        "     • watercolor illustration — 수채 번짐·종이 질감 (⚠️ 너무 기본값, 다른 것도 적극 고려)\n"
        "     • isometric 3D — 기하 입체·게임아트 포스터\n"
        "     • retro 1990s baseball card — 하프톤 점무늬·복고 인쇄\n"
        "     • risograph print — 2도 인쇄·거친 그레인·오프셋 미스레지\n"
        "     • minimalist line art — 얇은 선·여백·절제\n"
        "     • cyberpunk neon glow — 다크 배경·네온·홀로그램\n"
        "     • anime cel-shading — 플랫 컬러·굵은 아웃라인·드라마틱 조명\n"
        "     • oil painting — 유화 질감·풍부한 혼색·고전적 깊이\n"
        "   또는 위에 없는 스타일을 자유롭게 문장으로 기술해도 됨 (예: 'korean folk ink wash').\n"
        "   절대 기본값에 의존하지 말고, 이번 컨셉에 가장 덜 예상되는 조합도 시도해보라.\n\n"
        "응답은 JSON으로만:\n"
        "{\n"
        '  "primary_motifs": [...],\n'
        '  "secondary_motifs": [...],\n'
        '  "palette_hints": [...],\n'
        '  "mood_keywords": [...],\n'
        '  "composition_note": "...",\n'
        '  "style_direction": "..."\n'
        "}"
    )

    try:
        resp = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            max_tokens=500,
            temperature=0.9,  # 다양성 확보 (기존 0.6 → 0.9)
        )
        raw = json.loads(resp.choices[0].message.content or "{}")
        return VisualAssets(
            primary_motifs=list(raw.get("primary_motifs", []))[:6],
            secondary_motifs=list(raw.get("secondary_motifs", []))[:4],
            palette_hints=list(raw.get("palette_hints", []))[:5],
            mood_keywords=list(raw.get("mood_keywords", []))[:5],
            composition_note=raw.get("composition_note"),
            style_direction=raw.get("style_direction"),
        )
    except Exception:
        return _rule_based_fallback(state)


# ────────────────────────────────────────────────────────────────
# 룰 기반 폴백 (LLM 없을 때)
# ────────────────────────────────────────────────────────────────


# 키워드 → primary/secondary/팔레트 사전 (LLM 없을 때 폴백).
# primary에 baseballs/hearts가 들어갈지는 키워드별로 결정. 특권 없음.
_CONCEPT_DEFAULTS: dict[str, tuple[list[str], list[str], list[str]]] = {
    "봄": (
        ["cherry blossoms", "drifting petals", "soft pastel clouds", "gentle hearts"],
        ["soft sparkles", "tiny dots"],
        ["soft pink", "pastel mint", "cream white"],
    ),
    "파스텔": (
        ["cherry blossoms", "soft pastel clouds", "gentle hearts", "pastel orbs"],
        ["sparkles", "thin dashes"],
        ["pastel pink", "soft mint", "cream"],
    ),
    "여름": (
        ["ocean waves", "sun disk", "stylized baseballs", "water droplets"],
        ["sand grains", "foam bubbles"],
        ["azure blue", "sunny yellow", "white foam"],
    ),
    "가을": (
        ["maple leaves", "falling leaves", "stylized baseballs", "warm gradient rays"],
        ["tiny dots", "grain texture"],
        ["burnt orange", "deep gold", "rust red"],
    ),
    "겨울": (
        ["snowflakes", "crescent moon", "star sparkles", "frost crystals"],
        ["bokeh dots", "fine lines"],
        ["deep navy", "silver white", "frost blue"],
    ),
    "야경": (
        ["stars", "moon", "city light bokeh", "neon glow"],
        ["thin rays", "small dots"],
        ["midnight blue", "warm amber", "soft gold"],
    ),
    "석양": (
        ["radiant sun rays", "warm light flare", "silhouette leaves", "glowing hearts"],
        ["heat shimmer", "dust particles"],
        ["sunset orange", "deep pink", "amber gold"],
    ),
}


def _rule_based_fallback(state: PackState) -> VisualAssets:
    """컨셉 문자열에서 키워드 매칭. 선수 팩은 별도 분기."""
    hint = " ".join(
        s
        for s in (
            state.concept_hint,
            state.catalog_gap.recommended_concept if state.catalog_gap else None,
            state.trend_context,
        )
        if s
    ).lower()

    # 선수 팩 기본값 (baseballs/hearts가 자연스러움)
    primary = [
        "stylized baseballs",
        "team-colored hearts",
        "stadium spotlight beams",
        "confetti bursts",
    ]
    secondary = ["motion lines", "sparkles"]
    palette: list[str] = []

    # 컨셉 키워드 매칭 (선수 팩보다 우선)
    for keyword, (p, s, c) in _CONCEPT_DEFAULTS.items():
        if keyword in hint:
            primary, secondary, palette = p, s, c
            break

    # 선수 팩 + 팀 컬러
    if state.player and state.visual_traits and state.visual_traits.dominant_colors:
        palette = state.visual_traits.dominant_colors[:3]

    return VisualAssets(
        primary_motifs=primary,
        secondary_motifs=secondary,
        palette_hints=palette,
        mood_keywords=["stylized", "flat vector", "poster"],
        composition_note=None,
    )
