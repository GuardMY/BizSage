from __future__ import annotations

import os
import logging
import warnings

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
#
# V2: generate_answer() and generate_answer_learn() are now thin wrappers
#     that delegate to ModelRouter for backward compatibility.
# ---------------------------------------------------------------------------


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


# ── Backward-compatible wrappers (delegate to ModelRouter) ──────────

_router = None


def _get_router():
    global _router
    if _router is None:
        from app.model_routing.router import ModelRouter
        _router = ModelRouter()
    return _router


def generate_answer(question: str, context: str) -> str:
    """Backward-compatible wrapper. Delegates to ModelRouter (BALANCED tier).

    .. deprecated::
        Use ModelRouter directly with PromptAssembler. This wrapper bypasses
        the system-prompt layer and will be removed in a future version.
    """
    warnings.warn(
        "generate_answer() is deprecated — use ModelRouter + PromptAssembler directly",
        DeprecationWarning, stacklevel=2,
    )
    router = _get_router()
    messages = [
        {"role": "user", "content": f"问题：{question}\n\n参考证据：\n{context}"},
    ]
    result = router.call(
        messages=messages,
        task_hint="balanced",
        temperature=0.3,
        max_tokens=1024,
    )
    return result.response_text


def generate_answer_learning(question: str, context: str) -> str:
    """Backward-compatible wrapper for learning mode.

    .. deprecated::
        Use ModelRouter directly with the learning-mode prompt assembler.
        This wrapper will be removed in a future version.
    """
    warnings.warn(
        "generate_answer_learning() is deprecated — use ModelRouter + PromptAssembler directly",
        DeprecationWarning, stacklevel=2,
    )
    router = _get_router()
    messages = [
        {"role": "user", "content": context},
    ]
    result = router.call(
        messages=messages,
        task_hint="balanced",
        temperature=0.5,
        max_tokens=1024,
    )
    return result.response_text
