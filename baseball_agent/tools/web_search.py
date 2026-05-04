"""web_search Tool — OpenAI Responses API의 web_search_preview 사용.

우선순위:
  1) OPENAI_API_KEY 있으면 Responses API로 실제 웹 검색 시도
  2) Responses 웹검색이 불가하면 Chat Completions로 LLM 요약 (지식 기반)
  3) 둘 다 실패 또는 키 없음이면 mock stub 반환 (과제 채점 호환)

반환 스키마는 상위 Node가 어떤 경로를 탔는지 is_stub·source로 투명하게 전달한다.
"""
from __future__ import annotations

from agent.config import LLM_MODEL, get_client


def web_search(query: str, max_results: int = 5) -> dict:
    client = get_client()
    if client is None:
        return _stub_result(query, max_results, reason="no OPENAI_API_KEY")

    # 1) Responses API + web_search_preview
    try:
        resp = client.responses.create(
            model=LLM_MODEL,
            tools=[{"type": "web_search_preview"}],
            input=f"Search concisely: {query}. Return a 3-line summary of the top findings.",
        )
        text = (resp.output_text or "").strip()
        if text:
            lines = [ln for ln in text.splitlines() if ln.strip()]
            results = [
                {"title": f"web {i + 1}", "snippet": ln, "url": None}
                for i, ln in enumerate(lines[:max_results])
            ]
            return {
                "query": query,
                "results": results,
                "is_stub": False,
                "source": "openai_responses_web_search",
            }
    except Exception as e:
        last_err = f"responses failed: {type(e).__name__}: {e}"
    else:
        last_err = "responses empty"

    # 2) Chat fallback (LLM 지식 기반 요약)
    try:
        resp = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "You are a concise KBO/MLB news summarizer. Give 3 notable recent points as bullet sentences. Korean output.",
                },
                {"role": "user", "content": query},
            ],
            max_tokens=240,
        )
        text = (resp.choices[0].message.content or "").strip()
        lines = [ln.lstrip("-•* ").strip() for ln in text.splitlines() if ln.strip()]
        results = [
            {"title": f"llm {i + 1}", "snippet": ln, "url": None}
            for i, ln in enumerate(lines[:max_results])
        ]
        if results:
            return {
                "query": query,
                "results": results,
                "is_stub": False,
                "source": "openai_chat_knowledge",
            }
    except Exception as e:
        last_err = f"chat fallback failed: {type(e).__name__}: {e}"

    # 3) Stub
    return _stub_result(query, max_results, reason=last_err)


def _stub_result(query: str, max_results: int, reason: str = "") -> dict:
    return {
        "query": query,
        "results": [
            {
                "title": f"[STUB] {query} 결과 {i + 1}",
                "snippet": (
                    f"{query}에 대한 mock 결과. 실제 검색은 OPENAI_API_KEY 설정 후 활성화."
                ),
                "url": None,
            }
            for i in range(min(max_results, 3))
        ],
        "is_stub": True,
        "source": f"stub ({reason})" if reason else "stub",
    }
