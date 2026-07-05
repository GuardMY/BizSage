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
#
# STRICT MODE: 严格模式 — 不配置真实 LLM 或调用失败时必须显式失败，
# 绝不回退到 mock 回答。
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = (
    "你是一个企业经营诊断助手（BizSage）。"
    "请基于提供的知识库证据，给出结构化的经营分析建议。"
    "如果证据不足以支撑确定结论，请明确指出信息缺口。"
    "回答应包含：1) 关键发现 2) 风险提示 3) 可行动建议。"
    "请用简洁专业的商务中文回答，控制在 500 字以内。"
)


class LLMNotConfiguredError(Exception):
    """Raised when OPENAI_COMPATIBLE_API_KEY is not set."""


class LLMCallError(Exception):
    """Raised when the upstream LLM call fails or returns empty."""

    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


def _get_config() -> tuple[str, str, str]:
    """Return (base_url, api_key, model).

    Raises LLMNotConfiguredError if OPENAI_COMPATIBLE_API_KEY is not set.
    """
    api_key = os.getenv("OPENAI_COMPATIBLE_API_KEY", "").strip()
    if not api_key:
        raise LLMNotConfiguredError("OPENAI_COMPATIBLE_API_KEY not set – LLM is not configured")

    base_url = os.getenv("OPENAI_COMPATIBLE_BASE_URL", "https://api.deepseek.com/v1").strip()
    model = os.getenv("OPENAI_COMPATIBLE_MODEL", "deepseek-chat").strip()
    return base_url, api_key, model


def generate_answer(question: str, context: str) -> str:
    """Generate a diagnostic answer using the configured LLM provider.

    Calls the OpenAI‑compatible /chat/completions endpoint via httpx.
    Raises LLMNotConfiguredError or LLMCallError on failure —
    no mock fallback in strict mode.
    """
    provider = os.getenv("LLM_PROVIDER", "deepseek").lower()

    base_url, api_key, model = _get_config()
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
        if not content:
            raise LLMCallError(
                f"LLM returned empty response (provider={provider}, model={model})"
            )
        return content.strip()

    except (LLMNotConfiguredError, LLMCallError):
        raise

    except httpx.HTTPStatusError as exc:
        raise LLMCallError(
            f"LLM HTTP {exc.response.status_code} (provider={provider}, model={model}): "
            f"{exc.response.text[:500]}",
            status_code=exc.response.status_code,
        ) from exc

    except Exception as exc:
        raise LLMCallError(
            f"LLM call failed (provider={provider}, model={model}): {exc}"
        ) from exc
