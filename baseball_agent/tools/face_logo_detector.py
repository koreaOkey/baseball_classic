"""face_logo_detector Tool — 생성 이미지 저작권 가드 (GPT-4o Vision).

Step 7 dual-path:
  - OPENAI_API_KEY 있으면 GPT-4o-mini Vision이 실제 판정 (JSON 모드)
  - 키 없으면 항상 pass (stub). 단 `stub_fail_rate` 환경변수로 재시도 루프 연습용 실패 유도 가능.

반환 스키마는 docs/safety.md §4 참조.
"""
from __future__ import annotations

import base64
import json
import os
import random
from pathlib import Path

from agent.config import COST_VISION_PER_IMAGE, VISION_MODEL, get_client

# 테스트용: 실패 유도율 (0.0~1.0). 예: STUB_FAIL_RATE=0.3
STUB_FAIL_RATE: float = float(os.getenv("STUB_FAIL_RATE", "0.0"))

DETECT_PROMPT = (
    "이 이미지를 저작권 가드 관점에서 검사해줘.\n\n"
    "FAIL 요소:\n"
    " - 실존 인물의 사실적 얼굴/초상 (사진풍·극사실 일러스트 모두)\n"
    " - 구단 공식 로고·엠블럼 (KBO·MLB 구단)\n"
    " - 공식 팀 마스코트의 고유 디자인 모사 (블레오·호범이 등 실물 모사)\n"
    " - 다른 유명 IP 캐릭터 (미키·피카츄 등)\n"
    " - 폭력·성적 부적절 요소\n\n"
    "PASS 요소:\n"
    " - 일반 만화/픽사풍 동물 마스코트 (아기 사자·호랑이·곰·공룡 등) — 얼굴 있어도 OK\n"
    " - 인간 실루엣/픽토그램 (얼굴 디테일 없음)\n"
    " - 추상 그래픽·야구공·하트·선버스트 패턴\n"
    " - 플레인 유니폼 (로고 없는 단색)\n\n"
    "JSON으로만 답해 (반드시 valid JSON):\n"
    "{\n"
    '  "has_face": true/false,     # 실존 인간 얼굴만 true. 만화 동물은 false\n'
    '  "has_logo": true/false,     # 구단 공식 로고만 true\n'
    '  "logo_similarity": 0.0~1.0,\n'
    '  "has_other_ip": true/false, # 공식 마스코트 모사·타 IP 캐릭터\n'
    '  "has_inappropriate": true/false,\n'
    '  "reason": "한 문장 이유",\n'
    '  "verdict": "pass" | "refine" | "skip"\n'
    "}\n\n"
    "verdict 규칙:\n"
    " - pass   : 문제 없음, 저장 진행\n"
    " - refine : 인간 얼굴/공식 로고 감지, 프롬프트 보정 후 재생성\n"
    " - skip   : 다른 IP·부적절, 즉시 폐기 (재시도 없음)"
)


def detect(image_path: str) -> dict:
    client = get_client()
    if client is None:
        return _stub_detect()

    try:
        p = Path(image_path)
        if not p.exists() or p.stat().st_size == 0:
            return _stub_detect(reason="empty_image")

        b64 = base64.b64encode(p.read_bytes()).decode("ascii")
        image_url = f"data:image/png;base64,{b64}"

        resp = client.chat.completions.create(
            model=VISION_MODEL,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": DETECT_PROMPT},
                        {"type": "image_url", "image_url": {"url": image_url}},
                    ],
                }
            ],
            response_format={"type": "json_object"},
            max_tokens=300,
            temperature=0.0,
        )
        raw = json.loads(resp.choices[0].message.content or "{}")
        return {
            "has_face": bool(raw.get("has_face", False)),
            "has_logo": bool(raw.get("has_logo", False)),
            "logo_similarity": float(raw.get("logo_similarity", 0.0)),
            "has_other_ip": bool(raw.get("has_other_ip", False)),
            "has_inappropriate": bool(raw.get("has_inappropriate", False)),
            "reason": str(raw.get("reason", "")),
            "verdict": str(raw.get("verdict", "pass")),
            "cost_usd": COST_VISION_PER_IMAGE,
            "is_stub": False,
            "source": "openai_vision",
        }
    except Exception as e:
        return _stub_detect(reason=f"vision_error: {type(e).__name__}")


def _stub_detect(reason: str = "no_api_key") -> dict:
    """키 없을 때의 폴백. STUB_FAIL_RATE로 재시도 루프 연습용 실패 유도 가능."""
    # 확률적 실패 (루프 체크용)
    if STUB_FAIL_RATE > 0 and random.random() < STUB_FAIL_RATE:
        return {
            "has_face": True,
            "has_logo": False,
            "logo_similarity": 0.0,
            "has_other_ip": False,
            "has_inappropriate": False,
            "reason": "[STUB] 재시도 루프 검증용 실패 유도",
            "verdict": "refine",
            "cost_usd": 0.0,
            "is_stub": True,
            "source": f"stub_fail ({reason})",
        }

    return {
        "has_face": False,
        "has_logo": False,
        "logo_similarity": 0.0,
        "has_other_ip": False,
        "has_inappropriate": False,
        "reason": "[STUB] Vision 검증 생략",
        "verdict": "pass",
        "cost_usd": 0.0,
        "is_stub": True,
        "source": f"stub ({reason})",
    }
