"""image_generate Tool — OpenAI 이미지 생성 래퍼 (gpt-image-1 계열 기본).

Dual-path:
  - OPENAI_API_KEY 있으면 실제 API 호출
      * gpt-image-1-mini / gpt-image-1 / gpt-image-2 / gpt-image-1.5 지원
      * DALL-E 3 도 하위 호환
  - 키 없으면 프롬프트 해시 기반 단색 더미 PNG 생성

모델별 주의:
  - gpt-image-1 계열: response_format 파라미터 없음 (항상 b64_json 반환).
    quality=low|medium|high|auto, moderation=low|auto, output_format=png|jpeg|webp 지원.
    해상도: 1024x1024, 1024x1536, 1536x1024
  - dall-e-3: response_format=b64_json 필요. quality=standard|hd, style=natural|vivid.
    해상도: 1024x1024, 1024x1792, 1792x1024

모든 프롬프트 끝에는 docs/safety.md §3의 안전 템플릿이 자동 append된다.
"""
from __future__ import annotations

import base64
import hashlib
import tempfile
from pathlib import Path
from typing import Optional

from agent.config import (
    IMAGE_MODEL,
    IMAGE_QUALITY,
    estimate_image_cost,
    get_client,
)

# 모든 프롬프트 끝에 자동 삽입되는 안전 구문 (docs/safety.md §3)
# 스타일은 asset별로 프롬프트에서 지시하고, 여기서는 권리 침해 요소만 차단한다.
SAFETY_TEMPLATE = (
    "Absolutely no real human faces or photorealistic portraits of real people. "
    "No real-world team logos or trademarked mascot likenesses. "
    "No uniform emblems, sponsor logos, or brand names. "
    "No text, letters, or numbers overlaid. "
    "Cute cartoon animal mascots and abstract graphic elements are allowed."
)

# 모델별 유효 해상도
_VALID_SIZES_GPT_IMAGE = {"1024x1024", "1024x1536", "1536x1024", "auto"}
_VALID_SIZES_DALLE3 = {"1024x1024", "1024x1792", "1792x1024"}


def generate_image(
    prompt: str,
    size: str = "1024x1536",
    negative_prompt: Optional[list[str]] = None,
) -> dict:
    """이미지 생성.

    반환 스키마:
      {
        "path": str,         # 로컬 파일 경로 (임시 디렉토리)
        "prompt": str,       # 모델에 넘긴 최종 프롬프트 (안전 템플릿 포함)
        "size": str,
        "model": str,
        "quality": str,
        "cost_usd": float,
        "is_stub": bool,
        "source": str,
      }
    """
    size = _normalize_size(size, IMAGE_MODEL)
    final_prompt = _compose_prompt(prompt, negative_prompt)
    cost_estimate = estimate_image_cost(IMAGE_MODEL, IMAGE_QUALITY)

    client = get_client()
    if client is not None:
        try:
            kwargs = _build_request_kwargs(final_prompt, size)
            resp = client.images.generate(**kwargs)
            b64 = resp.data[0].b64_json
            path = _write_b64_png(b64, final_prompt)
            return {
                "path": str(path),
                "prompt": final_prompt,
                "size": size,
                "model": IMAGE_MODEL,
                "quality": IMAGE_QUALITY if _is_gpt_image(IMAGE_MODEL) else "n/a",
                "cost_usd": cost_estimate,
                "is_stub": False,
                "source": f"openai_{IMAGE_MODEL}",
            }
        except Exception as e:
            last_err = f"{type(e).__name__}: {e}"
    else:
        last_err = "no OPENAI_API_KEY"

    # Stub 폴백
    return {
        "path": str(_write_dummy_png(final_prompt, size)),
        "prompt": final_prompt,
        "size": size,
        "model": IMAGE_MODEL,
        "quality": IMAGE_QUALITY,
        "cost_usd": 0.0,
        "is_stub": True,
        "source": f"stub ({last_err})",
    }


# ── 내부 유틸 ─────────────────────────────────────────────────


def _is_gpt_image(model: str) -> bool:
    return model.startswith("gpt-image-")


def _normalize_size(size: str, model: str) -> str:
    """모델별 유효 해상도로 보정."""
    if _is_gpt_image(model):
        return size if size in _VALID_SIZES_GPT_IMAGE else "1024x1024"
    if model.startswith("dall-e-3"):
        return size if size in _VALID_SIZES_DALLE3 else "1024x1024"
    return "1024x1024"


def _build_request_kwargs(prompt: str, size: str) -> dict:
    """모델 계열별 API 파라미터 조립."""
    kwargs: dict = {
        "model": IMAGE_MODEL,
        "prompt": prompt,
        "size": size,
        "n": 1,
    }

    if _is_gpt_image(IMAGE_MODEL):
        # gpt-image-1 / 1.5 / mini / 2 공통
        kwargs["quality"] = IMAGE_QUALITY     # low | medium | high | auto
        kwargs["moderation"] = "low"          # 추상 픽토그램엔 관대한 모더레이션
        # response_format 없음 (항상 b64_json 반환)
    elif IMAGE_MODEL.startswith("dall-e-3"):
        kwargs["response_format"] = "b64_json"
        # quality/style은 지정하지 않아 기본값 사용 (standard / vivid)

    return kwargs


def _compose_prompt(prompt: str, negative: Optional[list[str]]) -> str:
    parts = [prompt.strip()]
    if negative:
        parts.append("Negative: " + ", ".join(negative))
    parts.append(SAFETY_TEMPLATE)
    return "\n".join(parts)


def _tmp_dir() -> Path:
    d = Path(tempfile.gettempdir()) / "baseball_agent_assets"
    d.mkdir(exist_ok=True)
    return d


def _write_b64_png(b64: str, prompt: str) -> Path:
    digest = hashlib.sha256(prompt.encode("utf-8")).hexdigest()[:10]
    out = _tmp_dir() / f"real_{digest}.png"
    out.write_bytes(base64.b64decode(b64))
    return out


def _write_dummy_png(prompt: str, size: str) -> Path:
    digest = hashlib.sha256(prompt.encode("utf-8")).hexdigest()[:6]
    out = _tmp_dir() / f"stub_{digest}.png"
    if out.exists():
        return out

    try:
        from PIL import Image

        try:
            w, h = [int(x) for x in size.split("x")]
        except ValueError:
            w, h = 1024, 1024

        rgb = (
            int(digest[0:2], 16),
            int(digest[2:4], 16),
            int(digest[4:6], 16),
        )
        Image.new("RGB", (w, h), rgb).save(out)
    except ImportError:
        out.write_bytes(b"")

    return out
