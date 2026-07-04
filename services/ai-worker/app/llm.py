from __future__ import annotations

import os
import logging

import httpx

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# DeepSeek / OpenAI‑compatible LLM wrapper (httpx‑based, no SDK needed)
# ---------------------------------------------------------------------------
# Supports any OpenAI‑compatible provider by configuring:
#   OPENAI_COMPATIBLE_BASE_URL   – e.g. https://api.deepseek.com/v1
#   OPENAI_COMPATIBLE_API_KEY    – your API key
#   OPENAI_COMPATIBLE_MODEL      – e.g. deepseek-chat
#   LLM_PROVIDER                 – logical provider name (deepseek / openai / …)
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = (
    "你是一个企业经营诊断助手（BizSage）。"
    "请基于提供的知识库证据，给出结构化的经营分析建议。"
    "如果证据不足以支撑确定结论，请明确指出信息缺口。"
    "回答应包含：1) 关键发现 2) 风险提示 3) 可行动建议。"
    "请用简洁专业的商务中文回答，控制在 500 字以内。"
)


def _get_config() -> tuple[str, str, str] | None:
    """Return (base_url, api_key, model) or None if not configured."""
    api_key = os.getenv("OPENAI_COMPATIBLE_API_KEY", "").strip()
    if not api_key:
        logger.warning("OPENAI_COMPATIBLE_API_KEY not set – falling back to mock")
        return None

    base_url = os.getenv("OPENAI_COMPATIBLE_BASE_URL", "https://api.deepseek.com/v1").strip()
    model = os.getenv("OPENAI_COMPATIBLE_MODEL", "deepseek-chat").strip()
    return base_url, api_key, model


def generate_answer(question: str, context: str) -> str:
    """Generate a diagnostic answer using the configured LLM provider.

    Calls the OpenAI‑compatible /chat/completions endpoint via httpx.
    Falls back to the mock implementation when no API key is configured
    or the remote call fails.
    """
    provider = os.getenv("LLM_PROVIDER", "deepseek").lower()

    config = _get_config()
    if config is None:
        return _mock_answer(question, context)

    base_url, api_key, model = config
    url = f"{base_url.rstrip('/')}/chat/completions"

    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": f"问题：{question}\n\n参考证据：\n{context}"},
        ],
        "temperature": 0.3,
        "max_tokens": 1024,
    }

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    try:
        response = httpx.post(url, json=payload, headers=headers, timeout=30.0)
        response.raise_for_status()
        body = response.json()
        content = body["choices"][0]["message"]["content"]
        if content:
            return content.strip()
        logger.warning("LLM returned empty response – falling back to mock")
        return _mock_answer(question, context)

    except httpx.HTTPStatusError as exc:
        logger.error(
            "LLM HTTP %d (provider=%s, model=%s): %s",
            exc.response.status_code,
            provider,
            model,
            exc.response.text[:500],
        )
        return _mock_answer(question, context)
    except Exception as exc:
        logger.error("LLM call failed (provider=%s, model=%s): %s", provider, model, exc)
        return _mock_answer(question, context)


def _mock_answer(question: str, context: str) -> str:
    """Local mock answer used as fallback when no LLM is available."""
    return (
        f"针对「{question}」，V1 诊断建议先围绕已检索证据核对关键经营变量。"
        f"证据显示：{context[:180]}。"
        "建议补充近期营收、成本、库存、渠道和回款数据后再做更细判断。"
    )
