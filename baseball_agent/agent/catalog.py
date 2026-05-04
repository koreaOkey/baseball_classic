"""Catalog 체인 — ANALYZE_CATALOG · FETCH_TREND · IDENTIFY_GAP.

FETCH_TREND는 concept_hint가 없을 때 LLM이 매번 다양한 검색 쿼리를 자율 생성한다.
"KBO 시즌 트렌드" 같은 고정 쿼리에 갇히지 않고 K-pop, 영화, 여행, 패션 등
다양한 분야에서 영감을 가져온다.

IDENTIFY_GAP은 카탈로그 공백 + 트렌드를 종합해 LLM이 추천 컨셉을 확정.
"""
from __future__ import annotations

import json
import random
from datetime import datetime
from pathlib import Path

from agent.config import LLM_MODEL, get_client, has_llm
from agent.events import emit_progress
from agent.state import CatalogGap, PackState
from tools import catalog_analyzer as cat
from tools import web_search as ws

PACKS_DIR = Path(__file__).parent.parent / "packs"

# LLM 없을 때의 시드 쿼리 풀. random.choice로 매번 다른 영감.
_SEED_QUERIES = [
    "2026 봄 K-pop 패션 트렌드",
    "한국 디저트 카페 비주얼 트렌드",
    "현대 그래픽 디자인 컬러 트렌드 2026",
    "한국 영화 포스터 미장센",
    "도시 야경 사진 무드",
    "한국 전통 색채 현대 해석",
    "K-pop 앨범 커버 아트워크",
    "한국 봄 여행지 풍경",
    "음악 페스티벌 비주얼 아이덴티티",
    "한국 길거리 패션 무드",
    "한국 화훼 전통 모티프",
    "도시 일러스트 트렌드",
    "야구장 응원 문화",
    "한국 절기 풍경 미감",
    "현대 일러스트레이션 무드보드",
]


def analyze_catalog(state: PackState) -> dict:
    """ANALYZE_CATALOG: gap 매트릭스 계산."""
    emit_progress("ANALYZE_CATALOG", "start")

    result = cat.analyze_gaps(concept_hint=state.concept_hint)
    gap = CatalogGap(
        missing_axes=result["missing_axes"],
        top_gap_axis=result["top_gap_axis"],
        top_gap_value=result["top_gap_value"],
        recommended_concept=result["recommended_concept"],
    )

    emit_progress(
        "ANALYZE_CATALOG",
        "done",
        top_gap=gap.top_gap_value,
        axis=gap.top_gap_axis,
        missing_n=sum(len(v) for v in gap.missing_axes.values()),
    )
    return {"catalog_gap": gap, "current_node": "ANALYZE_CATALOG"}


def fetch_trend(state: PackState) -> dict:
    """FETCH_TREND: 최근 트렌드를 웹에서 가져온다.

    concept_hint가 있으면 그걸 쿼리로. 없으면 LLM이 다양한 분야 (K-pop, 영화,
    여행, 패션, 디자인 등)에서 매번 다른 검색 쿼리 자율 생성.
    LLM 없으면 _SEED_QUERIES에서 랜덤 선택.
    """
    emit_progress("FETCH_TREND", "start")

    if state.concept_hint:
        query = state.concept_hint
        query_source = "user_hint"
    else:
        query = _generate_diverse_query()
        query_source = "llm" if has_llm() else "seed_random"

    result = ws.web_search(query, max_results=3)
    snippets = [r["snippet"] for r in result.get("results", [])]
    context = " / ".join(snippets[:3])

    emit_progress(
        "FETCH_TREND",
        "done",
        query=query,
        query_source=query_source,
        is_stub=result.get("is_stub", False),
    )
    return {"trend_context": context, "current_node": "FETCH_TREND"}


def _generate_diverse_query() -> str:
    """LLM이 최근 팩 트렌드와 안 겹치는 새 검색 쿼리를 한 줄 생성.

    오늘 날짜를 컨텍스트로 주입하되 계절 강제 X.
    """
    client = get_client()
    if client is None:
        return random.choice(_SEED_QUERIES)

    today = datetime.now()
    season = _korean_season(today.month)
    date_str = today.strftime("%Y년 %m월 %d일")

    recent_topics = _recent_trend_topics(n=5)
    recent_block = (
        "\n".join(f"- {t}" for t in recent_topics) if recent_topics else "(없음)"
    )

    prompt = (
        f"오늘은 {date_str} (한국 기준 {season}). 모바일·워치 배경 이미지를 위한 "
        "비주얼 무드보드 영감 검색 쿼리를 1줄 생성한다. "
        "이 배경은 야구 앱에 쓰이지만 야구와 직접 관련 없는 순수 비주얼 컨셉이어도 된다.\n\n"
        "탐색 각도 (자유 조합):\n"
        " • 한국 문화: 전통 화훼·궁궐 색채·한국 영화 미장센·K-pop 비주얼·길거리 패션\n"
        " • 글로벌 디자인: 그래픽 디자인 트렌드·일러스트 작가·앨범 커버 아트·포스터 디자인\n"
        " • 자연/풍경: 도시 야경·바다·산·여행지·천문\n"
        " • 미감 사조: 빈티지·복고·미니멀리즘·브루탈리즘·우키요에·아르데코·바우하우스\n"
        " • 서브컬처: 인디 게임 아트·동인지 일러스트·페스티벌 굿즈·LP 커버\n"
        " • 컬러/텍스처: 파스텔·네온·어반 콘크리트·자수·종이 질감\n\n"
        f"최근 검색했던 주제 (이번엔 겹치지 말 것):\n{recent_block}\n\n"
        "규칙:\n"
        " - 야구·KBO·시즌 키워드는 쓰지 말 것 (배경은 야구 무관).\n"
        " - 매번 전혀 다른 분야로 점프할 것.\n"
        " - 계절에 얽매이지 말 것.\n"
        "응답은 검색 쿼리 한 줄만 (한국어). 예: "
        "'2026 K-pop 앨범 커버 그래픽 트렌드', '우키요에 현대 재해석 일러스트', "
        "'서울 명동 골목 야경 사진', '한국 전통 자수 패턴'."
    )

    try:
        resp = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
            max_tokens=60,
            temperature=1.0,  # 다양성 최대
        )
        q = (resp.choices[0].message.content or "").strip()
        # 따옴표·줄바꿈 정리
        q = q.strip('"\'').splitlines()[0].strip()
        return q if len(q) >= 4 else random.choice(_SEED_QUERIES)
    except Exception:
        return random.choice(_SEED_QUERIES)


def _korean_season(month: int) -> str:
    """한국 기준 월 → 계절 (3~5 봄, 6~8 여름, 9~11 가을, 12~2 겨울)."""
    if 3 <= month <= 5:
        return "봄"
    if 6 <= month <= 8:
        return "여름"
    if 9 <= month <= 11:
        return "가을"
    return "겨울"


def _recent_trend_topics(n: int = 5) -> list[str]:
    """최근 N팩의 manifest에서 trend_context·recommended_concept 회수."""
    if not PACKS_DIR.exists():
        return []
    out: list[str] = []
    for p in sorted(PACKS_DIR.glob("pack_*"))[-n:]:
        mf = p / "manifest.json"
        if not mf.exists():
            continue
        try:
            m = json.loads(mf.read_text(encoding="utf-8"))
        except Exception:
            continue
        cg = m.get("catalog_gap") or {}
        if cg.get("recommended_concept"):
            out.append(cg["recommended_concept"])
        ctx = m.get("trend_context")
        if ctx:
            out.append(ctx[:120])
    return out


def identify_gap(state: PackState) -> dict:
    """IDENTIFY_GAP: 카탈로그 커버리지 + 트렌드를 종합해 추천 컨셉 확정.

    LLM 사용 가능: 기존 카탈로그 12종 요약과 trend_context를 보고 "어떤 무드·계절이
    부족한가" 자율 판단해 recommended_concept를 재작성.
    LLM 없음: analyze_catalog이 이미 채운 값 그대로 유지.
    """
    emit_progress("IDENTIFY_GAP", "start")

    if state.catalog_gap is None:
        emit_progress("IDENTIFY_GAP", "skip", reason="no catalog_gap in state")
        return {"current_node": "IDENTIFY_GAP"}

    if not has_llm():
        emit_progress(
            "IDENTIFY_GAP",
            "done",
            recommended=state.catalog_gap.recommended_concept,
            source="rule",
        )
        return {"current_node": "IDENTIFY_GAP"}

    updated = _llm_sharpen_gap(state)
    emit_progress(
        "IDENTIFY_GAP",
        "done",
        recommended=updated.recommended_concept,
        source="llm",
    )
    return {"catalog_gap": updated, "current_node": "IDENTIFY_GAP"}


def _llm_sharpen_gap(state: PackState) -> CatalogGap:
    """LLM이 카탈로그 요약 + 트렌드 컨텍스트를 보고 추천 컨셉을 다듬는다."""
    client = get_client()
    if client is None:
        return state.catalog_gap  # type: ignore[return-value]

    prompt = (
        "야구봄 앱의 테마 카탈로그를 확장하려 한다. 아래 기존 테마 12종과 "
        "최근 트렌드 컨텍스트를 보고, 지금 '시장 공백'에 해당하는 컨셉 1개를 "
        "짧은 문구(한글)로 제안하라. JSON으로만 답한다.\n\n"
        f"기존 테마:\n{cat.summarize_catalog()}\n\n"
        f"트렌드 컨텍스트: {state.trend_context or '없음'}\n"
        f"사용자 힌트: {state.concept_hint or '없음'}\n\n"
        '응답 형식: {"top_gap_axis": "season|mood|color_temp", "top_gap_value": "예: spring", '
        '"recommended_concept": "짧은 한글 컨셉"}'
    )

    try:
        resp = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            max_tokens=200,
            temperature=0.5,
        )
        raw = json.loads(resp.choices[0].message.content or "{}")
        return state.catalog_gap.model_copy(
            update={
                "top_gap_axis": raw.get("top_gap_axis") or state.catalog_gap.top_gap_axis,
                "top_gap_value": raw.get("top_gap_value") or state.catalog_gap.top_gap_value,
                "recommended_concept": (
                    raw.get("recommended_concept")
                    or state.catalog_gap.recommended_concept
                ),
            }
        )
    except Exception:
        return state.catalog_gap
