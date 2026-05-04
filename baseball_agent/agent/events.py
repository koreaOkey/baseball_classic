"""stdout JSON progress event 방출 유틸.

OpenClaw 또는 기타 외부 래퍼가 subprocess stdout을 구독해 진행 상황을 파싱한다.
각 Node 시작/종료 시점에 한 줄 JSON을 방출한다 (docs/architecture · spec.md).

Event 스키마:
    {"ts": "HH:MM:SS", "node": "NODE_NAME", "status": "start|done|skip|fail", "...": ...}
"""
from __future__ import annotations

import json
import sys
from datetime import datetime
from typing import Any


def _emit(line: str) -> None:
    """stdout에 JSON 한 줄 직접 쓰기. rich는 ANSI로 감싸서 JSON 파싱이 깨지므로 사용하지 않는다."""
    print(line, file=sys.stdout, flush=True)


def emit_progress(
    node: str,
    status: str = "done",
    **extra: Any,
) -> None:
    """stdout에 한 줄 JSON을 방출.

    status 값:
        - start : Node 진입
        - done  : 정상 종료
        - skip  : 조건부로 건너뜀
        - fail  : 복구 불가 실패
    """
    payload = {
        "ts": datetime.now().strftime("%H:%M:%S"),
        "node": node,
        "status": status,
        **extra,
    }
    _emit(json.dumps(payload, ensure_ascii=False))
