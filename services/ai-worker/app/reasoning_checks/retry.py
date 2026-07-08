"""Retry loop that wraps LLM calls with the five-check self-test.

On check failure, the feedback is appended as (assistant, user) messages
and the LLM is re-invoked, up to *max_retries* times.
"""

from __future__ import annotations

import os
from typing import Callable

from app.reasoning_checks.checks import (
    aggregate_check_results,
    build_feedback,
    run_all_checks,
)

# ── V2 error code 4002: AI reasoning self-check exhausted ──
UNCERTAIN_RESPONSE = (
    "信息存疑：当前推理经多次检查后仍无法通过验证，已终止输出。"
    "建议您补充更具体的行业、地域或经营数据后重新提问。"
)


def run_with_retry(
    llm_call_fn: Callable[[list[dict]], str],
    messages: list[dict],
    evidence: list[dict],
    region_id: str | None = None,
    output_format: str | None = None,
    config: dict | None = None,
    max_retries: int | None = None,
) -> tuple[str, str]:
    """Call LLM, run five checks, retry with feedback up to *max_retries*.

    Args:
        llm_call_fn: callable that takes ``list[dict]`` messages and returns a str
                     answer. Typically a closure over ``ModelRouter.call()``.
        messages: initial message list (must include a system prompt as the first
                  message).
        evidence: list of knowledge/intelligence dicts used as RAG context.
        region_id: user's region for the region check.
        output_format: system prompt text (used by logic check for structural
                       completeness verification).
        config: optional dict to override per-check settings
                (``fact_check_enabled``, ``timeliness_check_enabled``, etc.).
        max_retries: max retry attempts (default 3, overridden by
                     ``MAX_SELF_CHECK_RETRIES`` env var).

    Returns:
        ``(final_answer, self_check_status)`` tuple.
    """
    if max_retries is None:
        max_retries = int(os.getenv("MAX_SELF_CHECK_RETRIES", "3"))

    working_messages: list[dict] = list(messages)

    for attempt in range(max_retries + 1):
        # 1. Call LLM
        answer = llm_call_fn(working_messages)

        # 2. Run all five checks
        check_results = run_all_checks(
            answer,
            evidence,
            region_id=region_id,
            output_format=output_format,
            config=config,
        )

        # 3. Aggregate
        status = aggregate_check_results(check_results)

        if status == "PASSED":
            return answer, "PASSED"

        # 4. If retries remain, inject feedback and retry
        if attempt < max_retries:
            feedback = build_feedback(check_results)
            if feedback:
                working_messages.append({"role": "assistant", "content": answer})
                working_messages.append({"role": "user", "content": feedback})

    # All retries exhausted
    return UNCERTAIN_RESPONSE, "SELF_CHECK_FAILED"
