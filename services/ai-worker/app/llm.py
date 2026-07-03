from __future__ import annotations

import os


def generate_answer(question: str, context: str) -> str:
    provider = os.getenv("LLM_PROVIDER", "mock").lower()
    if provider == "mock" or not os.getenv("OPENAI_COMPATIBLE_API_KEY"):
        return mock_answer(question, context)
    return mock_answer(question, context)


def mock_answer(question: str, context: str) -> str:
    return (
        f"针对「{question}」，V1 诊断建议先围绕已检索依据核对关键经营变量。"
        f"依据显示：{context[:180]}。"
        "建议补充近期营收、成本、库存、渠道和回款数据后再做更细判断。"
    )
