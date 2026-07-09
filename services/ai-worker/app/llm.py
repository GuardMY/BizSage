from __future__ import annotations

import os
import logging
import warnings

logger = logging.getLogger(__name__)

# OpenAI 兼容 LLM 的旧接口包装层。
#
# 严格模式：没有真实 LLM 配置或上游调用失败时必须显式失败，绝不生成 mock 回答。
# 新代码应直接使用 ModelRouter + PromptAssembler；这里保留薄包装是为了兼容旧调用。


class LLMNotConfiguredError(Exception):
    """OPENAI_COMPATIBLE_API_KEY 未配置时抛出。"""


class LLMCallError(Exception):
    """上游 LLM 调用失败或返回空内容时抛出。"""

    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


def _get_config() -> tuple[str, str, str]:
    """读取旧版 OPENAI_COMPATIBLE_* 配置。

    未配置 API Key 时抛出 LLMNotConfiguredError。
    """
    api_key = os.getenv("OPENAI_COMPATIBLE_API_KEY", "").strip()
    if not api_key:
        raise LLMNotConfiguredError("OPENAI_COMPATIBLE_API_KEY not set – LLM is not configured")

    base_url = os.getenv("OPENAI_COMPATIBLE_BASE_URL", "https://api.deepseek.com/v1").strip()
    model = os.getenv("OPENAI_COMPATIBLE_MODEL", "deepseek-chat").strip()
    return base_url, api_key, model


# 向后兼容包装：内部委托给 ModelRouter。

_router = None


def _get_router():
    """懒加载模型路由器，避免导入阶段产生配置依赖。"""
    global _router
    if _router is None:
        from app.model_routing.router import ModelRouter
        _router = ModelRouter()
    return _router


def generate_answer(question: str, context: str) -> str:
    """旧版诊断回答入口，内部委托给 ModelRouter 的 BALANCED 档。

    .. deprecated::
        新代码请直接使用 ModelRouter + PromptAssembler。此包装绕过系统提示词层，
        后续版本会移除。
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
    """旧版学习回答入口。

    .. deprecated::
        新代码请直接使用学习模式的 PromptAssembler + ModelRouter。
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
