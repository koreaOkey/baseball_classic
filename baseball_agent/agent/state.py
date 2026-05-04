"""PackState — LangGraph Agent의 전역 상태 정의.

모든 Node는 이 State의 부분 업데이트(dict)를 반환하며, LangGraph가 필드 단위로
머지한다. 누적형 필드(`errors`, `generated_events`, `total_cost_usd`)는 Annotated
reducer로 병합 방식을 명시한다.

필드는 크게 5개 블록으로 구성된다.
  1) 입력          — CLI로 들어오는 원시 파라미터
  2) Research 결과 — Supervisor/research.py가 채움
  3) Theme 블록   — theme_subgraph.py가 채움
  4) Event 블록   — event_subgraph.py가 채움
  5) 집계         — pack_writer.py가 마무리
"""
from __future__ import annotations

from operator import add
from typing import Annotated, Literal, Optional

from pydantic import BaseModel, Field


Mode = Literal["auto", "player_pack", "theme_pack", "event_pack"]
AssetStatus = Literal["pending", "ok", "failed_after_retry", "skipped"]


# ────────────────────────────────────────────────────────────────
# 서브 모델 (불변 데이터, reducer 없음)
# ────────────────────────────────────────────────────────────────


class PlayerProfile(BaseModel):
    """Research에서 선정된 선수의 팩트 필드. 저작권 안전 모티프만 포함.

    얼굴·외모·신체 특징 필드는 의도적으로 없다 (docs/safety.md §2).
    is_mascot=True 인 경우 실제 선수가 아닌 팀 마스코트 합성 식별자로 취급된다.
    """

    id: str
    name_kr: str
    team_code: str
    team_name_kr: str
    team_colors: list[str]
    jersey_num: int
    position: str
    nickname: Optional[str] = None
    visual_motifs: list[str] = Field(default_factory=list)
    signature_moves: list[str] = Field(default_factory=list)
    recent_issues: list[str] = Field(
        default_factory=list,
        description="web_search 보강 결과 (최근 기사·이슈 요약 문구).",
    )
    is_mascot: bool = Field(
        default=False,
        description="True면 실제 선수가 아닌 팀 마스코트 합성. RESEARCH_PLAYER에서 web_search 스킵.",
    )


class CatalogGap(BaseModel):
    """기존 12종 카탈로그의 공백 축 분석 결과."""

    missing_axes: dict[str, list[str]] = Field(
        default_factory=dict,
        description='예: {"season": ["spring"], "mood": ["pastel"]}',
    )
    top_gap_axis: Optional[str] = None
    top_gap_value: Optional[str] = None
    recommended_concept: Optional[str] = None


class VisualTraits(BaseModel):
    """EXTRACT_TRAITS Node 출력. 저작권 필터를 통과한 모티프만 남김."""

    dominant_colors: list[str] = Field(default_factory=list)
    motifs: list[str] = Field(default_factory=list)
    mood_tags: list[str] = Field(default_factory=list)
    composition_hints: list[str] = Field(default_factory=list)


class VisualAssets(BaseModel):
    """PLAN_VISUAL_ASSETS Node 출력. 컨셉에 맞춰 어떤 시각 요소를 넣을지 LLM이 자율 결정.

    설계 원칙: 야구공·하트·벚꽃·눈송이 등 모든 모티프는 동등한 지위.
    "야구 앱이니까 야구공이 기본"같은 편향을 LLM에 프라이밍하지 않는다.
    LLM은 컨셉만 보고 primary/secondary 리스트를 채운다.
    """

    primary_motifs: list[str] = Field(
        default_factory=list,
        description=(
            "컨셉 핵심 시각 요소 3~5개. 모든 모티프는 동등 후보. "
            "선수 트리뷰트면 'stylized baseballs', 'team hearts'가 primary일 수 있고, "
            "봄 파스텔이면 'cherry blossoms', 'drifting petals'가 primary."
        ),
    )
    secondary_motifs: list[str] = Field(
        default_factory=list,
        description="보조 장식·텍스처 2~3개. 예: 'soft sparkles', 'tiny dots'.",
    )
    palette_hints: list[str] = Field(
        default_factory=list,
        description="팔레트 힌트 3~5개 (컬러 이름 또는 hex).",
    )
    mood_keywords: list[str] = Field(
        default_factory=list,
        description="무드 설명어 3~5개. 예: 'soft', 'dreamy', 'bold'.",
    )
    composition_note: Optional[str] = Field(
        default=None,
        description="구도 추가 힌트 (한 문장, 선택).",
    )
    style_direction: Optional[str] = Field(
        default=None,
        description=(
            "비주얼 스타일 방향. 예: 'flat vector poster', 'watercolor illustration', "
            "'isometric 3D', 'retro 1990s baseball card', 'cyberpunk neon glow', "
            "'minimalist line art', 'risograph print'. "
            "None이면 theme_subgraph 기본값(flat vector) 사용."
        ),
    )


class ThemeAssetResult(BaseModel):
    """Theme 서브그래프의 splash/home_bg/lock 각 요소 결과."""

    path: Optional[str] = None
    status: AssetStatus = "pending"
    retry_count: int = 0
    cost_usd: float = 0.0
    fail_reason: Optional[str] = None


class ThemeBundle(BaseModel):
    """Theme 서브그래프 최종 산출물 — 배경 1장 + 토큰."""

    background: ThemeAssetResult = Field(default_factory=ThemeAssetResult)
    tokens: Optional[dict[str, str]] = Field(
        default=None,
        description="야구봄 design token 스키마 호환 JSON.",
    )


class EventResult(BaseModel):
    """이벤트별 히어로 스틸 생성 결과."""

    event_code: str
    hero_path: Optional[str] = None
    meta: Optional[dict] = None
    status: AssetStatus = "pending"
    retry_count: int = 0
    abstraction_level: int = Field(
        default=0,
        description="face_logo_detector 실패 시 상향. 0=normal, 1=silhouette only, 2=pictogram only.",
    )
    cost_usd: float = 0.0
    fail_reason: Optional[str] = None


# ────────────────────────────────────────────────────────────────
# reducers (LangGraph가 Node 출력을 기존 상태에 병합할 때 호출)
# ────────────────────────────────────────────────────────────────


def _merge_events(
    current: dict[str, EventResult], update: dict[str, EventResult]
) -> dict[str, EventResult]:
    """이벤트 서브그래프가 fan-out으로 동시에 이벤트별 결과를 올릴 수 있으므로
    키 단위 병합이 필요하다. 같은 이벤트 키가 들어오면 나중 값으로 덮어쓴다.
    """
    return {**current, **update}


def _accumulate_cost(current: float, update: float) -> float:
    """비용은 Tool 호출마다 누적된다."""
    return round(current + update, 4)


def _take_latest(current: str | None, update: str | None) -> str | None:
    """current_node 등 '마지막 값 유지' 필드용 reducer.

    병렬 fan-out 시 theme/event 서브그래프가 동시에 current_node를 업데이트해도
    충돌 없이 둘 중 하나(LangGraph가 결정)의 값만 남도록 한다.
    """
    return update if update is not None else current


# ────────────────────────────────────────────────────────────────
# PackState — LangGraph StateGraph가 사용하는 최상위 모델
# ────────────────────────────────────────────────────────────────


class PackState(BaseModel):
    """Agent 전역 상태.

    LangGraph 0.2+의 Pydantic BaseModel 지원을 활용한다. Annotated reducer가
    붙은 필드는 Node가 부분 업데이트를 반환할 때 함수로 병합되고, 그 외는
    단순 필드 교체로 처리된다.
    """

    model_config = {"arbitrary_types_allowed": True}

    # ── 1) 입력 (CLI 또는 OpenClaw에서 주입) ────────────────────
    mode: Mode = Field(
        default="auto",
        description="Supervisor PLAN Node가 auto면 자율 결정, 나머지는 해당 분기로 직행.",
    )
    player_name_input: Optional[str] = Field(
        default=None, description="CLI --player 인자. 이름 또는 id로 매칭."
    )
    team_input: Optional[str] = Field(
        default=None,
        description="CLI --team 인자. 팀 코드(SSG/KIA) 또는 한글명(두산/롯데). 선수 없이 마스코트 캐릭터.",
    )
    concept_hint: Optional[str] = Field(
        default=None,
        description="CLI --concept_hint. theme_pack 모드에서 gap 분석 보정용.",
    )

    # ── 2) Research 결과 ────────────────────────────────────────
    player: Optional[PlayerProfile] = None
    catalog_gap: Optional[CatalogGap] = None
    trend_context: Optional[str] = Field(
        default=None,
        description="FETCH_TREND Node의 웹 검색 요약 (한 단락).",
    )
    visual_traits: Optional[VisualTraits] = None
    visual_assets: Optional[VisualAssets] = Field(
        default=None,
        description="PLAN_VISUAL_ASSETS Node가 결정한 컨셉별 시각 요소 리스트.",
    )

    # ── 3) Theme 서브그래프 ────────────────────────────────────
    theme_bundle: ThemeBundle = Field(default_factory=ThemeBundle)

    # ── 4) Event 서브그래프 ────────────────────────────────────
    events_to_generate: list[str] = Field(
        default_factory=list,
        description="이벤트 코드 목록 (예: ['HR', 'HIT', ...]).",
    )
    generated_events: Annotated[dict[str, EventResult], _merge_events] = Field(
        default_factory=dict,
        description="이벤트 코드 → 결과. fan-out 병렬 업데이트를 키 단위로 병합.",
    )

    # ── 5) 집계 · 메타 ──────────────────────────────────────────
    pack_id: Optional[str] = None
    pack_path: Optional[str] = None
    total_cost_usd: Annotated[float, _accumulate_cost] = 0.0
    started_at: Optional[str] = None
    completed_at: Optional[str] = None

    errors: Annotated[list[str], add] = Field(
        default_factory=list,
        description="Node에서 발생한 복구 불가 에러 메시지 누적.",
    )
    current_node: Annotated[Optional[str], _take_latest] = Field(
        default=None,
        description="stdout progress event에 노출할 현재 Node 이름. 병렬 분기에서도 충돌 없도록 reducer 적용.",
    )

    # ── 편의 메서드 ─────────────────────────────────────────────

    def needs_player(self) -> bool:
        """Supervisor 조건부 엣지에서 Research 체인 진입 여부 판단."""
        return self.mode in ("player_pack", "event_pack") or (
            self.mode == "auto" and self.player is None
        )

    def needs_catalog(self) -> bool:
        """카탈로그 gap 분석이 필요한 모드인지 판단."""
        return self.mode in ("theme_pack",) or (
            self.mode == "auto" and self.player is None
        )

    def should_build_theme(self) -> bool:
        return self.mode in ("auto", "player_pack", "theme_pack")

    def should_build_events(self) -> bool:
        return self.mode in ("auto", "player_pack", "event_pack")

    def asset_summary(self) -> dict[str, int]:
        """manifest·로그용 요약 카운트."""
        theme_ok = 1 if self.theme_bundle.background.status == "ok" else 0
        events_ok = sum(
            1 for ev in self.generated_events.values() if ev.status == "ok"
        )
        events_failed = sum(
            1
            for ev in self.generated_events.values()
            if ev.status in ("failed_after_retry", "skipped")
        )
        return {
            "theme_ok": theme_ok,
            "theme_total": 1 if self.should_build_theme() else 0,
            "events_ok": events_ok,
            "events_total": len(self.events_to_generate),
            "events_failed": events_failed,
        }
