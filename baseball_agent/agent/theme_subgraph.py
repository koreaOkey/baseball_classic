"""Theme 서브그래프 — 스플래시·홈 배경·잠금 화면 + 디자인 토큰 생성.

3개 asset 각각에 대해 순차적으로:
  BUILD_PROMPT → GENERATE → VERIFY
                             ↓
                         pass → 저장
                         refine → 프롬프트 보정 후 재시도 (최대 3회)
                         skip → 즉시 폐기

모든 asset 완료 후 color_extractor로 대표 팔레트를 뽑아 tokens.json 구조로 조립.
"""
from __future__ import annotations

from dataclasses import dataclass

from agent.config import has_llm
from agent.events import emit_progress
from agent.state import PackState, ThemeAssetResult, ThemeBundle
from tools import color_extractor as ce
from tools import face_logo_detector as fld
from tools import image_generate as ig

MAX_RETRIES = 3


# ────────────────────────────────────────────────────────────────
# Asset spec — 각 테마 요소의 사이즈·구도 규정
# ────────────────────────────────────────────────────────────────


@dataclass(frozen=True)
class AssetSpec:
    name: str              # "splash" | "home_bg" | "lock"
    size: str              # DALL-E 3 지원 크기
    composition: str       # 구도 힌트 (영문, 프롬프트 직주입)


SPECS: list[AssetSpec] = [
    AssetSpec(
        name="background",
        size="1024x1536",
        composition=(
            "single background for both watch and phone — "
            "Zone C (bottom 40%) is balanced and richly decorated with the selected motifs; "
            "Zone B left/right edges hold accent items framing the composition; "
            "Zone A (top 42%) is completely clean for UI text overlay"
        ),
    ),
]


# 워치 800x1000 기준 safe zone 규칙 (openspec/specs/themes/spec.md).
# 3개 가용 존: 좌측 코너 · 우측 코너 · 하단 메인. 상단은 텍스트 오버레이 전용.
SAFE_ZONE_DIRECTIVE = (
    "Layout zones (watch/phone UI overlay — these are structural, not stylistic):\n\n"
    "ZONE A — TOP 42% (0% to 42% from top): STRICTLY CLEAN.\n"
    "  Only the background color and optional soft gradient. "
    "No decorative elements of any kind. "
    "Reserved for score display and clock text overlay.\n\n"
    "ZONE B — MIDDLE BAND (42% to 60% from top):\n"
    "  - CENTER 60% width: MUST remain EMPTY (reserved for BSO indicators and player info UI).\n"
    "  - LEFT 20% width edge: small motifs welcome here.\n"
    "  - RIGHT 20% width edge: same as left, mirrored or varied.\n\n"
    "ZONE C — BOTTOM 40% (60% to 100% from top): main decoration zone. "
    "Cluster the primary motifs and secondary accents richly here — "
    "make this area visually packed and energetic."
)


# ────────────────────────────────────────────────────────────────
# 메인 진입점
# ────────────────────────────────────────────────────────────────


def build_theme(state: PackState) -> dict:
    emit_progress("THEME_SUBGRAPH", "start")

    if not state.should_build_theme():
        emit_progress("THEME_SUBGRAPH", "skip", reason="mode excludes theme")
        return {"current_node": "THEME_SUBGRAPH"}

    results: dict[str, ThemeAssetResult] = {}
    cost_total = 0.0

    for spec in SPECS:
        result = _generate_asset(state, spec)
        results[spec.name] = result
        cost_total += result.cost_usd

    # 성공 asset에서 팔레트 합산 → 대표 토큰 추출
    tokens = _compile_tokens(results)

    bundle = ThemeBundle(
        background=results["background"],
        tokens=tokens,
    )

    ok_count = sum(1 for r in results.values() if r.status == "ok")
    emit_progress(
        "THEME_SUBGRAPH",
        "done",
        ok=f"{ok_count}/{len(SPECS)}",
        cost=round(cost_total, 4),
        tokens_extracted=tokens is not None,
    )

    return {
        "theme_bundle": bundle,
        "total_cost_usd": cost_total,
        "current_node": "THEME_SUBGRAPH",
    }


# ────────────────────────────────────────────────────────────────
# 개별 asset 생성 — BUILD → GENERATE → VERIFY 루프 (최대 3회)
# ────────────────────────────────────────────────────────────────


def _generate_asset(state: PackState, spec: AssetSpec) -> ThemeAssetResult:
    """한 asset에 대해 생성·검증·재시도 루프."""
    node_name = f"THEME/{spec.name.upper()}"
    emit_progress(node_name, "start")

    prompt = _build_prompt(state, spec)
    cost_accum = 0.0
    abstraction_level = 0

    for attempt in range(MAX_RETRIES):
        # GENERATE
        gen = ig.generate_image(
            prompt=prompt,
            size=spec.size,
            negative_prompt=["human face", "team logo", "player portrait", "text"],
        )
        cost_accum += gen["cost_usd"]
        emit_progress(
            node_name,
            "done" if attempt == 0 else "retry",
            step="generate",
            attempt=attempt + 1,
            is_stub=gen["is_stub"],
            source=gen["source"],
        )

        # VERIFY
        verify = fld.detect(gen["path"])
        cost_accum += verify["cost_usd"]
        emit_progress(
            node_name,
            "done",
            step="verify",
            attempt=attempt + 1,
            verdict=verify["verdict"],
            has_face=verify["has_face"],
            source=verify["source"],
        )

        if verify["verdict"] == "pass":
            emit_progress(node_name, "done", step="complete", attempt=attempt + 1)
            return ThemeAssetResult(
                path=gen["path"],
                status="ok",
                retry_count=attempt,
                cost_usd=round(cost_accum, 4),
            )

        if verify["verdict"] == "skip":
            # 다른 유명인·부적절 — 즉시 종료
            emit_progress(
                node_name,
                "fail",
                step="skip",
                reason=verify["reason"],
            )
            return ThemeAssetResult(
                status="failed_after_retry",
                retry_count=attempt + 1,
                cost_usd=round(cost_accum, 4),
                fail_reason=f"skip: {verify['reason']}",
            )

        # refine — 프롬프트 추상화 레벨 상향
        abstraction_level += 1
        prompt = _refine_prompt(prompt, verify["reason"], abstraction_level)

    # 3회 모두 실패
    emit_progress(node_name, "fail", step="max_retries", attempts=MAX_RETRIES)
    return ThemeAssetResult(
        status="failed_after_retry",
        retry_count=MAX_RETRIES,
        cost_usd=round(cost_accum, 4),
        fail_reason="max retries exceeded",
    )


# ────────────────────────────────────────────────────────────────
# 프롬프트 빌더
# ────────────────────────────────────────────────────────────────


def _build_prompt(state: PackState, spec: AssetSpec) -> str:
    """visual_assets 기반 프롬프트 조립 — 야구공·하트·기타 모티프 모두 LLM 자율 결정.

    이미지는 워치(800×1000)·모바일(1024×1536) 양쪽에 쓰이므로 safe zone 규칙을 지킨다.
    visual_assets가 없으면 최소 폴백(일반 야구 포스터).
    """
    # 컨셉 라벨
    concept_label = _resolve_concept_label(state)

    # 팔레트
    palette = _resolve_palette(state)
    palette_line = (
        f"Palette: {', '.join(palette)}." if palette else "Palette: derive from concept."
    )

    # 시각 asset 리스트 (LLM이 visual_planner에서 결정)
    assets = state.visual_assets
    element_block = _build_element_block(assets)

    # 무드
    mood = []
    if assets and assets.mood_keywords:
        mood = assets.mood_keywords[:4]
    elif state.visual_traits and state.visual_traits.mood_tags:
        mood = state.visual_traits.mood_tags[:3]
    mood_line = f"Mood: {', '.join(mood)}." if mood else ""

    # 구도 추가 힌트
    composition_note = ""
    if assets and assets.composition_note:
        composition_note = f"Composition note: {assets.composition_note}\n\n"

    # 비주얼 스타일 — visual_assets.style_direction 우선, 없으면 기본 flat vector
    style_line = _resolve_style_line(assets)

    # 첫 줄: 스타일 문구를 자연스럽게 녹이기
    opening_style = (
        assets.style_direction
        if assets and assets.style_direction
        else "flat vector illustration"
    )

    prompt = (
        f"A {opening_style} for a mobile/watch background wallpaper, themed around {concept_label}. "
        f"This is a pure visual concept — NOT a baseball image. No baseball motifs (no balls, "
        f"no bats, no stadium, no jerseys, no caps) unless the concept explicitly calls for them.\n\n"
        f"{palette_line}\n"
        f"{mood_line}\n\n"
        f"{element_block}\n\n"
        f"{composition_note}"
        f"{SAFE_ZONE_DIRECTIVE}\n\n"
        f"Asset intent: {spec.composition}\n\n"
        f"{style_line}\n\n"
        f"Forbidden: real human faces/portraits, real-world team logos or emblems, "
        f"uniform sponsor logos, text/letters/numbers overlaid on the image."
    )
    return prompt


def _resolve_style_line(assets) -> str:
    """visual_assets.style_direction → 구체적 스타일 지시문.

    LLM이 style_direction을 채웠으면 그걸 중심으로 풍부화. 비었으면 기본 flat vector.
    """
    if assets and assets.style_direction:
        direction = assets.style_direction.lower()
        if "watercolor" in direction:
            return (
                "Style: watercolor illustration with soft brush strokes, "
                "bleeding color edges, gentle paper texture, organic imperfect shapes."
            )
        if "isometric" in direction:
            return (
                "Style: isometric 3D composition, geometric shapes, "
                "clean edges, subtle depth shading, game-art poster look."
            )
        if "retro" in direction or "1990" in direction:
            return (
                "Style: retro 1990s baseball card aesthetic, "
                "halftone dot patterns, vintage color separation, "
                "slight print misregistration feel."
            )
        if "risograph" in direction:
            return (
                "Style: risograph print aesthetic, limited two-color palette, "
                "visible grain and registration offset, slightly rough textures."
            )
        if "minimalist" in direction or "line art" in direction:
            return (
                "Style: minimalist line art, thin consistent stroke weight, "
                "ample white space, elegant restraint."
            )
        if "cyberpunk" in direction or "neon" in direction:
            return (
                "Style: cyberpunk neon glow, dark background with saturated neon accents, "
                "subtle bloom and chromatic glow, futuristic feel."
            )
        if "anime" in direction or "cel" in direction:
            return (
                "Style: anime cel-shading, crisp flat color areas, "
                "bold outlines, high contrast lighting zones."
            )
        if "oil" in direction or "painting" in direction:
            return (
                "Style: oil painting texture, visible brush strokes, rich color blending, "
                "soft atmospheric depth."
            )
        # 지정됐지만 사전에 없는 경우: 그대로 전달
        return (
            f"Style: {assets.style_direction}. "
            "High visual impact, cohesive composition."
        )

    # 기본값
    return (
        "Style: flat vector illustration, poster-like, no 3D, no photorealistic shading. "
        "High visual impact, cohesive palette, unified visual language across all elements."
    )


def _resolve_concept_label(state: PackState) -> str:
    """배경 컨셉 라벨. 선수 정보가 있어도 'tribute' 프레이밍은 사용하지 않음 —
    배경은 야구와 무관한 순수 비주얼 컨셉이고, 선수 팀컬러는 팔레트 힌트로만 흘러간다."""
    if state.catalog_gap and state.catalog_gap.recommended_concept:
        return f"'{state.catalog_gap.recommended_concept}'"
    if state.concept_hint:
        return f"'{state.concept_hint}'"
    return "'editorial wallpaper'"


def _resolve_palette(state: PackState) -> list[str]:
    """우선순위: visual_assets.palette_hints > visual_traits.dominant_colors > player.team_colors."""
    if state.visual_assets and state.visual_assets.palette_hints:
        return state.visual_assets.palette_hints[:4]
    if state.visual_traits and state.visual_traits.dominant_colors:
        return state.visual_traits.dominant_colors[:3]
    if state.player and state.player.team_colors:
        return state.player.team_colors[:3]
    return []


def _build_element_block(assets) -> str:
    """Concept director가 결정한 모티프 리스트를 일러스트레이터에게 전달.

    리스트 자체가 "이걸 써서 그려"라는 지시이고, 구도·분위기·자연스러운 보완은
    이미지 모델의 창의적 재량에 맡긴다. 하드 차단(예: "no other elements")은 두지 않는다.
    """
    if assets is None or (not assets.primary_motifs and not assets.secondary_motifs):
        return (
            "Visual direction: no specific motifs pre-selected. "
            "Keep the composition minimal and strictly on-concept."
        )

    lines: list[str] = ["Visual direction (selected by the concept director):"]

    if assets.primary_motifs:
        primary_str = ", ".join(assets.primary_motifs)
        lines.append(
            f"  ・ PRIMARY — main visual stars: {primary_str}."
        )

    if assets.secondary_motifs:
        secondary_str = ", ".join(assets.secondary_motifs)
        lines.append(
            f"  ・ SECONDARY — accents / texture: {secondary_str}."
        )

    lines.append(
        "Compose these into a cohesive scene across Zone B corners and Zone C. "
        "You may enrich the composition with atmospheric touches "
        "(light, gradients, motion flow) that reinforce the mood."
    )

    return "\n".join(lines)


def _refine_prompt(prev: str, fail_reason: str, level: int) -> str:
    """검증 실패 원인에 따라 프롬프트를 추상화 방향으로 강화."""
    directive_by_level = {
        1: "Silhouettes only, no detailed figures.",
        2: "Pure pictogram style, geometric shapes only, no characters.",
        3: "Abstract color-field only, no representational elements.",
    }
    directive = directive_by_level.get(level, directive_by_level[3])
    return f"{prev} Refinement (reason: {fail_reason}): {directive}"


# ────────────────────────────────────────────────────────────────
# 컬러 추출 · 토큰 조립
# ────────────────────────────────────────────────────────────────


def _compile_tokens(results: dict[str, ThemeAssetResult]) -> dict[str, str] | None:
    """성공한 asset에서 팔레트를 추출해 design token 필드로 매핑."""
    palettes: list[str] = []
    for r in results.values():
        if r.status == "ok" and r.path:
            palettes.extend(ce.extract_palette(r.path, k=3))

    if not palettes:
        return None

    return ce.map_to_design_tokens(palettes)
