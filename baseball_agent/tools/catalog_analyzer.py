"""catalog_analyzer Tool — 기존 테마 12종의 축별 커버리지 분석.

IDENTIFY_GAP Node가 호출해 "어느 축이 부재한가"를 판단한다. 반환 결과는
PackState.catalog_gap (CatalogGap 모델)에 그대로 주입된다.
"""
from __future__ import annotations

import json
from collections import Counter
from functools import lru_cache
from pathlib import Path
from typing import Optional

DATA_FILE = Path(__file__).parent.parent / "data" / "catalog.json"


@lru_cache(maxsize=1)
def _load() -> dict:
    with DATA_FILE.open(encoding="utf-8") as f:
        return json.load(f)


def load_catalog() -> list[dict]:
    return _load()["themes"]


def get_axes_config() -> dict[str, list[str]]:
    """각 축의 가능한 값 목록."""
    return _load()["axes"]


def coverage_matrix() -> dict[str, dict[str, int]]:
    """축별·값별 카운트. 예: {"season": {"spring": 2, "summer": 2, ...}}"""
    axes = get_axes_config()
    themes = load_catalog()
    matrix: dict[str, dict[str, int]] = {}
    for axis_name in axes:
        counter: Counter[str] = Counter()
        for theme in themes:
            value = theme.get(axis_name)
            if value is not None:
                counter[value] += 1
        matrix[axis_name] = dict(counter)
    return matrix


def analyze_gaps(concept_hint: Optional[str] = None) -> dict:
    """공백 축 분석.

    각 축에서 카운트 0인 값(완전 부재)과 카운트 1 이하(희귀)를 모아 반환.
    concept_hint가 주어지면 힌트 단어가 포함된 축을 우선 가중.

    반환 스키마는 state.CatalogGap 모델과 호환.
    """
    axes = get_axes_config()
    matrix = coverage_matrix()

    missing: dict[str, list[str]] = {}
    for axis_name, possible_values in axes.items():
        missing_in_axis = [
            v for v in possible_values if matrix[axis_name].get(v, 0) == 0
        ]
        if missing_in_axis:
            missing[axis_name] = missing_in_axis

    # top gap: 가장 누락이 많은 축
    top_gap_axis: Optional[str] = None
    top_gap_value: Optional[str] = None
    if missing:
        top_gap_axis = max(missing.keys(), key=lambda k: len(missing[k]))
        top_gap_value = missing[top_gap_axis][0]

    # concept_hint 반영 (간단 키워드 매칭)
    recommended_concept = top_gap_value
    if concept_hint:
        hint_lower = concept_hint.lower()
        for axis_name, values in missing.items():
            for v in values:
                if v.lower() in hint_lower or hint_lower in v.lower():
                    top_gap_axis = axis_name
                    top_gap_value = v
                    recommended_concept = f"{concept_hint} ({v})"
                    break

    return {
        "missing_axes": missing,
        "top_gap_axis": top_gap_axis,
        "top_gap_value": top_gap_value,
        "recommended_concept": recommended_concept,
    }


def summarize_catalog() -> str:
    """LLM 프롬프트에 주입할 한 단락 요약."""
    themes = load_catalog()
    lines = [f"기존 테마 {len(themes)}종:"]
    for t in themes:
        affinity = f" / {t['team_affinity']}" if t.get("team_affinity") else ""
        lines.append(
            f"- {t['name_kr']} [{t['color_temp']}·{t['brightness']}·"
            f"{t['season']}·{t['mood']}{affinity}]"
        )
    return "\n".join(lines)
