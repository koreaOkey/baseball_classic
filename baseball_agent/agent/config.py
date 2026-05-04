"""환경 설정·OpenAI 클라이언트 팩토리.

`.env` 파일에 OPENAI_API_KEY가 있으면 실제 LLM 호출. 없으면 각 Tool·Node가
자동으로 stub/rule 기반 fallback으로 동작해 여전히 `python main.py`가 끝까지 완주한다.

과제 채점용:
  - API 키 없이도 `python main.py`가 에러 없이 완주해야 함 (spec.md 요구).
  - 키가 있으면 Research·Extract·검증 품질이 올라감.
"""
from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import Any, Optional

# .env 로드 (선택 의존성)
try:
    from dotenv import load_dotenv

    _ENV_FILE = Path(__file__).parent.parent / ".env"
    if _ENV_FILE.exists():
        load_dotenv(_ENV_FILE)
except ImportError:  # pragma: no cover
    pass


# ── 모델 설정 ──────────────────────────────────────────────────

LLM_MODEL: str = os.getenv("OPENAI_LLM_MODEL", "gpt-4o-mini")
# 기본 이미지 모델: gpt-image-1-mini (최신 저가형, medium 품질 권장)
# 지원: gpt-image-1-mini, gpt-image-1, gpt-image-1.5, gpt-image-2, dall-e-3
IMAGE_MODEL: str = os.getenv("OPENAI_IMAGE_MODEL", "gpt-image-1-mini")
# gpt-image-* 계열에만 유효. low/medium/high/auto
IMAGE_QUALITY: str = os.getenv("OPENAI_IMAGE_QUALITY", "medium")
VISION_MODEL: str = os.getenv("OPENAI_VISION_MODEL", "gpt-4o-mini")

# ── 비용 상수 (OpenAI 공식 가격 페이지 2026-04 기준) ──
# 토큰 단가 $/1M tokens
COST_CHAT_PER_1K_INPUT = 0.00015       # gpt-4o-mini $0.15/1M
COST_CHAT_PER_1K_OUTPUT = 0.0006       # gpt-4o-mini $0.60/1M

# 이미지 출력 토큰 단가 ($/1M tokens)
_IMG_OUT_PER_1M = {
    "gpt-image-2": 30.0,
    "gpt-image-1.5": 8.0,     # 공식 미공개, mini와 같다고 가정
    "gpt-image-1": 8.0,       # 공식 미공개, mini와 같다고 가정
    "gpt-image-1-mini": 8.0,  # 공식 $8.00/1M
}

# quality별 이미지 출력 토큰 수 (1024x1024 기준)
_IMG_TOKENS = {
    "gpt-image-2": {"low": 196, "medium": 780, "high": 3120, "auto": 780},
    "gpt-image-1.5": {"low": 320, "medium": 1300, "high": 5200, "auto": 1300},
    "gpt-image-1": {"low": 320, "medium": 1300, "high": 5200, "auto": 1300},
    "gpt-image-1-mini": {"low": 272, "medium": 1056, "high": 4160, "auto": 1056},
}

COST_IMAGE_DALLE3 = 0.04               # DALL-E 3 standard 1024x1024 flat
COST_VISION_PER_IMAGE = 0.008          # gpt-4o-mini vision 추정


def estimate_image_cost(
    model: str = IMAGE_MODEL, quality: str = IMAGE_QUALITY
) -> float:
    """선택된 모델·품질에 대한 장당 추정 비용(USD). 1024x1024 기준."""
    if model.startswith("dall-e-3"):
        return COST_IMAGE_DALLE3
    if model.startswith("gpt-image-"):
        tokens = _IMG_TOKENS.get(model, _IMG_TOKENS["gpt-image-1-mini"]).get(
            quality, 1056
        )
        rate_per_1m = _IMG_OUT_PER_1M.get(model, 8.0)
        return round(tokens * rate_per_1m / 1_000_000, 5)
    return 0.04  # unknown model fallback


# ── 클라이언트 팩토리 ─────────────────────────────────────────


@lru_cache(maxsize=1)
def get_client() -> Optional[Any]:
    """OpenAI 클라이언트 또는 None(키 없음) 반환. 프로세스 수명 동안 캐싱."""
    if not os.getenv("OPENAI_API_KEY"):
        return None
    try:
        from openai import OpenAI

        return OpenAI()
    except ImportError:
        return None


def has_llm() -> bool:
    """Research·Extract·Verify Node가 실제 LLM 경로를 탈 수 있는지 여부."""
    return get_client() is not None
