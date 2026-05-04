"""color_extractor Tool — 생성 이미지에서 대표 팔레트 추출 후 디자인 토큰 매핑.

EXTRACT_COLORS Node가 Theme 3장(splash·home·lock)에서 호출한다.
Pillow + sklearn K-means로 상위 K개 컬러를 뽑고, 명도·채도 기준으로
야구봄 design token 필드에 매핑한다.

의존성: Pillow, scikit-learn, numpy. requirements.txt에 포함.
"""
from __future__ import annotations

import colorsys
from pathlib import Path
from typing import Optional


def extract_palette(image_path: str, k: int = 5) -> list[str]:
    """K-means로 이미지에서 상위 k개 컬러를 hex 리스트로 반환.

    이미지를 리사이즈(128x128)해 계산 속도를 확보한다.
    의존성 미설치 시 빈 리스트 반환 (graceful fallback).
    """
    try:
        import warnings

        import numpy as np
        from PIL import Image
        from sklearn.cluster import KMeans
        from sklearn.exceptions import ConvergenceWarning
    except ImportError:
        return []

    path = Path(image_path)
    if not path.exists():
        return []

    img = Image.open(path).convert("RGB")
    img.thumbnail((128, 128))
    pixels = np.array(img).reshape(-1, 3)

    # 단색 이미지(stub)에서 k보다 적은 고유색이 나와 경고 뜨는 것 방지
    unique_count = len({tuple(p) for p in pixels})
    actual_k = min(k, max(1, unique_count))

    with warnings.catch_warnings():
        warnings.simplefilter("ignore", ConvergenceWarning)
        kmeans = KMeans(n_clusters=actual_k, n_init=3, random_state=42)
        kmeans.fit(pixels)

    # 클러스터 중심 + 크기 순 정렬
    centers = kmeans.cluster_centers_.astype(int)
    labels, counts = np.unique(kmeans.labels_, return_counts=True)
    order = np.argsort(-counts)

    return [_rgb_to_hex(tuple(centers[i])) for i in order]


def map_to_design_tokens(palette: list[str]) -> dict[str, str]:
    """팔레트 → 야구봄 design token 필드 매핑.

    규칙 (단순하지만 안정적인 휴리스틱):
      - primary   : 가장 큰 클러스터 (가장 많이 쓰인 색)
      - accent    : 가장 채도가 높은 색 (강조용)
      - surface   : 가장 밝은 색
      - onSurface : 가장 어두운 색

    팔레트가 비었거나 짧으면 합리적 기본값으로 채운다.
    """
    if not palette:
        return _default_tokens()

    ranked_saturation = sorted(
        palette, key=lambda h: _hsv(h)[1], reverse=True
    )
    ranked_brightness = sorted(
        palette, key=lambda h: _hsv(h)[2]
    )

    return {
        "color.primary": palette[0],
        "color.accent": ranked_saturation[0],
        "color.surface": ranked_brightness[-1],
        "color.onSurface": ranked_brightness[0],
    }


def extract_tokens_from_image(image_path: str, k: int = 5) -> dict[str, str]:
    """extract_palette + map_to_design_tokens 원스텝."""
    palette = extract_palette(image_path, k=k)
    return map_to_design_tokens(palette)


# ── 내부 유틸 ─────────────────────────────────────────────────


def _rgb_to_hex(rgb: tuple[int, int, int]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb)


def _hex_to_rgb(hex_str: str) -> tuple[int, int, int]:
    h = hex_str.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4))  # type: ignore


def _hsv(hex_str: str) -> tuple[float, float, float]:
    r, g, b = (c / 255.0 for c in _hex_to_rgb(hex_str))
    return colorsys.rgb_to_hsv(r, g, b)


def _default_tokens() -> dict[str, str]:
    """팔레트 추출 실패 시 야구봄 기본 토큰 (catalog의 '기본형')."""
    return {
        "color.primary": "#F26722",
        "color.accent": "#1A1A1A",
        "color.surface": "#FFFFFF",
        "color.onSurface": "#0D0D0D",
    }
