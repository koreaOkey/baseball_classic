"""Event 서브그래프 — 이벤트별 히어로 스틸 + 메타 힌트 생성.

default_event_set을 순회하며 각 이벤트에 대해:
  BUILD_PROMPT (events.json 스펙 + 선수 모티프 결합)
    → GENERATE
    → VERIFY
  실패 시 추상화 레벨 상향으로 최대 3회 재시도, 실패 이벤트는 SKIP하고 다음 진행.
  최종 이벤트별 meta.json에 영상화 후속 작업용 힌트 포함.
"""
from __future__ import annotations

import json
from pathlib import Path

from agent.events import emit_progress
from agent.state import EventResult, PackState
from tools import face_logo_detector as fld
from tools import image_generate as ig

EVENTS_FILE = Path(__file__).parent.parent / "data" / "events.json"
MAX_RETRIES = 3

# gpt-image-1 세로 해상도 (이벤트 히어로 스틸 공통)
EVENT_SIZE = "1024x1536"


# 팀별 리치 마스코트 프로필. 공식 마스코트 IP 모사를 피하면서도 팀 정체성을 표현.
# 각 프로필은 종·비율·털결·눈 스타일·시그니처·성격·악센트 7요소로 구성.
TEAM_MASCOT_PROFILES: dict[str, dict] = {
    # ── KBO ───────────────────────────────────────────
    "LG": {
        "species": "a pair of identical twin baby lion cubs posing side by side",
        "body_proportion": "small chibi twins with 1:1.4 head-to-body ratio, stubby limbs, mirrored stance",
        "fur_texture": "soft warm cream-yellow plush fur with subtle velvet sheen",
        "eye_style": "large round black eyes with twin starry highlight sparks, identical synchronized expressions",
        "signature_item": "tiny matching crimson collar tags shaped like a 'twins' emblem",
        "personality_keywords": ["playful", "mischievous duo", "energetic"],
        "accent_color": "crisp white piping with crimson trim",
    },
    "DOOSAN": {
        "species": "a chubby baby brown bear cub",
        "body_proportion": "round dumpling-shaped body, 1:1.3 head-to-body ratio, very short stubby limbs",
        "fur_texture": "thick fluffy chocolate-brown plush with cream-colored belly and inner ears",
        "eye_style": "small dot eyes with single tiny highlight, gentle dreamy expression",
        "signature_item": "a small woven scarf draped around the neck",
        "personality_keywords": ["dignified", "gentle giant", "calm"],
        "accent_color": "cream belly fur and red inner-ear tint",
    },
    "KIWOOM": {
        "species": "a brave baby cub wearing a small superhero cape",
        "body_proportion": "compact chibi body with the cape flowing slightly behind, 1:1.5 head-to-body",
        "fur_texture": "soft rust-brown plush with a satin sheen on the cape",
        "eye_style": "determined large round eyes with a bold single highlight, focused gaze",
        "signature_item": "a small flowing burgundy hero cape clipped at the shoulders",
        "personality_keywords": ["heroic", "brave", "earnest"],
        "accent_color": "burgundy cape lining and navy collar",
    },
    "KT": {
        "species": "a curious baby cub wearing a tiny wizard hat",
        "body_proportion": "medium chibi body, 1:1.4 head-to-body, small paws",
        "fur_texture": "smooth jet-black fur with subtle indigo highlights, soft sheen",
        "eye_style": "wide curious eyes with a small star-shaped sparkle reflection",
        "signature_item": "a small black wizard hat tilted slightly to one side, decorated with a tiny crimson band",
        "personality_keywords": ["clever", "studious", "inquisitive"],
        "accent_color": "crimson hat band and white wand-tip accent",
    },
    "SSG": {
        "species": "an adventurous baby explorer cub (Korean tiger cub variant)",
        "body_proportion": "stocky chibi build, 1:1.5 head-to-body, short sturdy legs",
        "fur_texture": "soft orange-cream tabby with subtle dark stripes, velvet matte finish",
        "eye_style": "large amber-brown eyes with crisp twin highlights, alert curious gaze",
        "signature_item": "a small navy explorer bandana tied loosely around the neck with a gold trim edge",
        "personality_keywords": ["adventurous", "curious", "brave"],
        "accent_color": "gold trim on bandana, navy contrast",
    },
    "NC": {
        "species": "a tiny baby green dinosaur hatchling with a soft round body",
        "body_proportion": "egg-shaped torso, 1:1.6 head-to-body, stubby tail",
        "fur_texture": "smooth mint-green scaly skin with cream belly plates, soft matte finish",
        "eye_style": "large innocent dark eyes with single highlight, slightly drowsy lids",
        "signature_item": "a small gold crest of soft spikes along the head",
        "personality_keywords": ["ancient", "calm", "gentle"],
        "accent_color": "cream-gold belly plates and golden crest",
    },
    "SAMSUNG": {
        "species": "a noble baby lion cub with a soft fluffy mane",
        "body_proportion": "regal chibi posture, 1:1.4 head-to-body, fluffy mane filling the upper body",
        "fur_texture": "soft golden-cream plush with a darker honey-toned mane around the face",
        "eye_style": "calm proud eyes with single warm highlight, gentle confident smile",
        "signature_item": "a small royal blue collar with a tiny star charm",
        "personality_keywords": ["regal", "proud", "calm leader"],
        "accent_color": "royal blue trim and gold star accent",
    },
    "LOTTE": {
        "species": "a friendly baby cub with comically oversized paws and feet",
        "body_proportion": "round body with extra-large paws and feet, 1:1.4 head-to-body",
        "fur_texture": "soft cream-tan plush with darker brown patches on paw pads",
        "eye_style": "warm smiling eyes with twin highlights, easygoing expression",
        "signature_item": "a small white seagull feather tucked behind one ear",
        "personality_keywords": ["laid-back", "friendly", "warm-hearted"],
        "accent_color": "navy-blue trim with crimson seagull-feather highlights",
    },
    "KIA": {
        "species": "a fierce baby tiger cub",
        "body_proportion": "athletic chibi build, 1:1.4 head-to-body, slightly muscular legs",
        "fur_texture": "vivid orange tabby with bold black tiger stripes, matte finish, white chin and chest",
        "eye_style": "intense amber eyes with sharp single highlight, focused gaze",
        "signature_item": "a small red bandana tied around one wrist",
        "personality_keywords": ["fierce", "confident", "determined"],
        "accent_color": "deep red and black detailing, white chest fluff",
    },
    "HANWHA": {
        "species": "a sharp-eyed baby eagle chick",
        "body_proportion": "round body covered in fluffy down, 1:1.3 head-to-body, tiny clawed feet",
        "fur_texture": "soft dark-brown down with cream chest, subtle matte finish",
        "eye_style": "large piercing yellow-amber eyes with crisp highlight, alert gaze",
        "signature_item": "small wing feathers spread slightly outward, with a bright orange beak",
        "personality_keywords": ["sharp-eyed", "watchful", "swift"],
        "accent_color": "bright orange beak and cream chest fluff",
    },
    # ── MLB ───────────────────────────────────────────
    "SFG": {
        "species": "a friendly baby sea lion pup",
        "body_proportion": "smooth round body with small flippers, 1:1.5 head-to-body",
        "fur_texture": "smooth glossy gray seal-like skin with cream belly",
        "eye_style": "large dark dewy eyes with twin highlights, gentle smile",
        "signature_item": "a tiny set of whisker accents on the muzzle",
        "personality_keywords": ["friendly", "playful", "graceful"],
        "accent_color": "cream belly with warm orange highlights",
    },
    "LAD": {
        "species": "a soft baby cub with round chubby cheeks",
        "body_proportion": "well-rounded chibi body, 1:1.4 head-to-body",
        "fur_texture": "soft pale cream plush with subtle blue accent fur on ears",
        "eye_style": "wide bright eyes with twin highlights, cheerful smile",
        "signature_item": "a small star-shaped pin on the collar",
        "personality_keywords": ["cheerful", "friendly", "bright"],
        "accent_color": "Dodger-blue ear tips and trim",
    },
    "SDP": {
        "species": "a cozy baby cub with warm beige fur",
        "body_proportion": "soft rounded chibi body, 1:1.4 head-to-body",
        "fur_texture": "soft warm beige-tan plush with cream belly, matte finish",
        "eye_style": "calm gentle eyes with single highlight, contented expression",
        "signature_item": "a tiny brown leather satchel slung across the body",
        "personality_keywords": ["cozy", "calm", "thoughtful"],
        "accent_color": "warm brown leather accent",
    },
}


_DEFAULT_MASCOT_PROFILE: dict = {
    "species": "a generic cute baby animal cub",
    "body_proportion": "round chibi body, 1:1.4 head-to-body, stubby limbs",
    "fur_texture": "soft neutral plush with matte finish",
    "eye_style": "large round dark eyes with twin highlights",
    "signature_item": "no distinctive accessory beyond uniform",
    "personality_keywords": ["friendly", "cheerful"],
    "accent_color": "neutral white trim",
}


def _get_mascot_profile(team_code: str | None) -> dict:
    if not team_code:
        return _DEFAULT_MASCOT_PROFILE
    return TEAM_MASCOT_PROFILES.get(team_code, _DEFAULT_MASCOT_PROFILE)


def build_events(state: PackState) -> dict:
    emit_progress("EVENT_SUBGRAPH", "start")

    if not state.should_build_events():
        emit_progress("EVENT_SUBGRAPH", "skip", reason="mode excludes events")
        return {"current_node": "EVENT_SUBGRAPH"}

    spec = _load_events_spec()
    event_codes: list[str] = spec.get("default_event_set", [])
    event_definitions: dict[str, dict] = {e["code"]: e for e in spec.get("events", [])}

    results: dict[str, EventResult] = {}
    cost_total = 0.0

    for code in event_codes:
        ev_spec = event_definitions.get(code)
        if ev_spec is None:
            results[code] = EventResult(
                event_code=code,
                status="failed_after_retry",
                fail_reason=f"no spec in events.json",
            )
            continue

        result = _generate_event(state, ev_spec)
        results[code] = result
        cost_total += result.cost_usd

    ok = sum(1 for r in results.values() if r.status == "ok")
    emit_progress(
        "EVENT_SUBGRAPH",
        "done",
        ok=f"{ok}/{len(event_codes)}",
        cost=round(cost_total, 4),
    )

    return {
        "events_to_generate": event_codes,
        "generated_events": results,
        "total_cost_usd": cost_total,
        "current_node": "EVENT_SUBGRAPH",
    }


# ────────────────────────────────────────────────────────────────
# 개별 이벤트 생성 — 3회 재시도 + graceful degradation
# ────────────────────────────────────────────────────────────────


def _generate_event(state: PackState, ev_spec: dict) -> EventResult:
    code = ev_spec["code"]
    node_name = f"EVENT/{code}"
    emit_progress(node_name, "start", intensity=ev_spec.get("intensity"))

    prompt = _build_prompt(state, ev_spec)
    negative = ev_spec.get("negative_prompt", [])
    cost_accum = 0.0
    abstraction_level = 0

    for attempt in range(MAX_RETRIES):
        gen = ig.generate_image(
            prompt=prompt,
            size=EVENT_SIZE,
            negative_prompt=negative,
        )
        cost_accum += gen["cost_usd"]
        emit_progress(
            node_name,
            "done",
            step="generate",
            attempt=attempt + 1,
            is_stub=gen["is_stub"],
        )

        verify = fld.detect(gen["path"])
        cost_accum += verify["cost_usd"]
        emit_progress(
            node_name,
            "done",
            step="verify",
            attempt=attempt + 1,
            verdict=verify["verdict"],
        )

        if verify["verdict"] == "pass":
            emit_progress(node_name, "done", step="complete", attempt=attempt + 1)
            return EventResult(
                event_code=code,
                hero_path=gen["path"],
                meta=_build_meta(state, ev_spec),
                status="ok",
                retry_count=attempt,
                abstraction_level=abstraction_level,
                cost_usd=round(cost_accum, 4),
            )

        if verify["verdict"] == "skip":
            emit_progress(
                node_name,
                "fail",
                step="skip",
                reason=verify["reason"],
            )
            return EventResult(
                event_code=code,
                status="failed_after_retry",
                retry_count=attempt + 1,
                abstraction_level=abstraction_level,
                cost_usd=round(cost_accum, 4),
                fail_reason=f"skip: {verify['reason']}",
            )

        abstraction_level += 1
        prompt = _refine_prompt(prompt, verify["reason"], abstraction_level)

    emit_progress(node_name, "fail", step="max_retries")
    return EventResult(
        event_code=code,
        status="failed_after_retry",
        retry_count=MAX_RETRIES,
        abstraction_level=abstraction_level,
        cost_usd=round(cost_accum, 4),
        fail_reason="max retries exceeded",
    )


# ────────────────────────────────────────────────────────────────
# 프롬프트 + 메타 빌더
# ────────────────────────────────────────────────────────────────


def _build_prompt(state: PackState, ev_spec: dict) -> str:
    """리치 마스코트 프로필 + 이벤트별 gear + 영화적 스튜디오 디테일 결합.

    7요소 프로필(species·body_proportion·fur_texture·eye_style·signature_item·
    personality·accent_color) + gear_props + character_action + studio setup.
    """
    # 1) 마스코트 프로필 + 팀 컬러
    if state.player:
        profile = _get_mascot_profile(state.player.team_code)
        colors = (
            state.visual_traits.dominant_colors[:2]
            if state.visual_traits and state.visual_traits.dominant_colors
            else state.player.team_colors[:2]
        )
    else:
        profile = _DEFAULT_MASCOT_PROFILE
        colors = ["navy blue", "white"]

    primary = colors[0] if colors else "navy blue"
    secondary = colors[1] if len(colors) > 1 else "white"

    # 2) 이벤트 정보
    action = ev_spec.get("character_action") or " ".join(ev_spec.get("prompt_hints", []))
    mood = ", ".join(ev_spec.get("mood_tags", [])[:3])
    gear_list = ev_spec.get("gear_props", [])
    gear_line = (
        "Action gear (visible in the scene): " + "; ".join(gear_list) + "."
        if gear_list
        else ""
    )

    # 3) 캐릭터 프로필 블록
    personality = ", ".join(profile["personality_keywords"])
    character_block = (
        f"Character: {profile['species']}.\n"
        f"  • Personality: {personality}.\n"
        f"  • Body: {profile['body_proportion']}.\n"
        f"  • Fur/skin: {profile['fur_texture']}.\n"
        f"  • Eyes: {profile['eye_style']}.\n"
        f"  • Distinctive feature: {profile['signature_item']}."
    )

    # 4) 의상 블록
    outfit_block = (
        f"Outfit: a plain {primary} baseball helmet with visible chinstrap and small earhole detail, "
        f"and a plain {primary} baseball jersey with {secondary} trim "
        f"({profile['accent_color']}). "
        f"Cream-white baseball pants with small white socks. "
        f"Completely plain — no numbers, no real team logos, no text, no sponsor patches."
    )

    # 5) 스튜디오 셋업 (영화 룩)
    studio_block = (
        "Studio setup: clean off-white studio backdrop with a subtle radial gradient. "
        "85mm-portrait-lens equivalent perspective, slight 3/4 angle on the character. "
        "Three-point lighting — warm key light from the upper-left, cool fill from the right, "
        "soft rim/hair light from behind for separation. Shallow soft floor shadow only. "
        "The character is the sole subject — no stadium, no crowd, no environmental clutter "
        "beyond the action gear listed above."
    )

    # 6) 미감 레퍼런스
    aesthetic_block = (
        "Aesthetic reference: high-end designer vinyl figure × kawaii chibi sensibility × "
        "polished 3D animation render. Glossy plastic-like surface with soft subsurface "
        "scattering on skin/fur, crisp specular highlights, pristine clean geometry. "
        "Friendly figurine-like appearance suitable for collectible toy product photography."
    )

    return (
        f"A 3D rendered chibi mascot character.\n\n"
        f"{character_block}\n\n"
        f"{outfit_block}\n\n"
        f"{gear_line}\n\n"
        f"Action pose: {action}.\n\n"
        f"{studio_block}\n\n"
        f"Mood: {mood}.\n\n"
        f"{aesthetic_block}"
    )


def _refine_prompt(prev: str, fail_reason: str, level: int) -> str:
    directives = {
        1: "Silhouettes only, abstract the figures.",
        2: "Pure pictogram style, geometric glyphs only.",
        3: "Color-field abstract, no figures or objects.",
    }
    d = directives.get(level, directives[3])
    return f"{prev} Refinement (reason: {fail_reason}): {d}"


def _build_meta(state: PackState, ev_spec: dict) -> dict:
    """이벤트별 meta.json — 영상화 후속 작업용 힌트."""
    dominant_colors: list[str] = []
    if state.visual_traits and state.visual_traits.dominant_colors:
        dominant_colors = state.visual_traits.dominant_colors[:3]
    elif state.player and state.player.team_colors:
        dominant_colors = state.player.team_colors[:3]

    return {
        "event": ev_spec["code"],
        "event_name_kr": ev_spec.get("name_kr"),
        "scene_description": " ".join(ev_spec.get("prompt_hints", [])),
        "focal_point": [0.5, 0.4],
        "dominant_colors": dominant_colors,
        "mood_tags": ev_spec.get("mood_tags", []),
        "motion_hint_for_future_video": ev_spec.get(
            "motion_hint_for_future_video"
        ),
    }


# ────────────────────────────────────────────────────────────────
# 유틸
# ────────────────────────────────────────────────────────────────


def _load_events_spec() -> dict:
    with EVENTS_FILE.open(encoding="utf-8") as f:
        return json.load(f)
