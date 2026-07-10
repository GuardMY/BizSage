"""带五项自检的 LLM 重试循环。

模型每次回答后都会执行事实、时效、地域、逻辑和格式等检查；若未通过，
把检查反馈追加到上下文中让模型修正，直到达到最大重试次数。
"""

from __future__ import annotations

import os
from collections.abc import Iterator
from typing import Callable

from app.reasoning_checks.checks import (
    aggregate_check_results,
    build_feedback,
    run_all_checks,
)

# 自检重试耗尽时返回的用户可见提示，对应 AI 推理自检失败场景。
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
    """调用 LLM、执行五项自检，并在失败时带反馈重试。

    返回 ``(final_answer, self_check_status)``；调用方据此决定是否正常输出或提示失败。
    """
    if max_retries is None:
        max_retries = int(os.getenv("MAX_SELF_CHECK_RETRIES", "3"))

    working_messages: list[dict] = list(messages)

    for attempt in range(max_retries + 1):
        # 1. 生成候选回答。
        answer = llm_call_fn(working_messages)

        # 2. 对候选回答执行全部自检。
        check_results = run_all_checks(
            answer,
            evidence,
            region_id=region_id,
            output_format=output_format,
            config=config,
        )

        # 3. 聚合检查结果，只有全部通过才返回 PASSED。
        status = aggregate_check_results(check_results)

        if status == "PASSED":
            return answer, "PASSED"

        # 4. 仍有重试次数时，把失败原因作为用户反馈注入下一轮。
        if attempt < max_retries:
            feedback = build_feedback(check_results)
            if feedback:
                working_messages.append({"role": "assistant", "content": answer})
                working_messages.append({"role": "user", "content": feedback})

    # 所有重试耗尽后，不输出未经验证的原始回答。
    return UNCERTAIN_RESPONSE, "SELF_CHECK_FAILED"


def run_with_retry_stream(
    llm_stream_fn: Callable[[list[dict]], Iterator[dict]],
    messages: list[dict],
    evidence: list[dict],
    region_id: str | None = None,
    output_format: str | None = None,
    config: dict | None = None,
    max_retries: int | None = None,
) -> Iterator[dict]:
    """Stream each candidate, then validate and reset before any retry."""
    if max_retries is None:
        max_retries = int(os.getenv("MAX_SELF_CHECK_RETRIES", "3"))

    working_messages: list[dict] = list(messages)
    for attempt_index in range(max_retries + 1):
        attempt = attempt_index + 1
        answer_parts: list[str] = []

        for event in llm_stream_fn(working_messages):
            if event["event"] == "delta":
                answer_parts.append(event["text"])
                yield {**event, "attempt": attempt}
            elif event["event"] == "reset":
                answer_parts.clear()
                yield {**event, "attempt": attempt}

        answer = "".join(answer_parts)
        yield {"event": "status", "state": "validating", "attempt": attempt}
        check_results = run_all_checks(
            answer,
            evidence,
            region_id=region_id,
            output_format=output_format,
            config=config,
        )
        status = aggregate_check_results(check_results)
        if status == "PASSED":
            yield {
                "event": "result",
                "answer": answer,
                "selfCheckStatus": "PASSED",
                "attempt": attempt,
            }
            return

        if attempt_index < max_retries:
            feedback = build_feedback(check_results)
            if feedback:
                working_messages.append({"role": "assistant", "content": answer})
                working_messages.append({"role": "user", "content": feedback})
            yield {
                "event": "reset",
                "reason": "SELF_CHECK_RETRY",
                "attempt": attempt + 1,
            }
            yield {
                "event": "status",
                "state": "retrying",
                "attempt": attempt + 1,
                "message": "正在校验并优化回答…",
            }
            continue

        yield {
            "event": "reset",
            "reason": "SELF_CHECK_RETRY",
            "attempt": attempt,
        }
        yield {"event": "delta", "text": UNCERTAIN_RESPONSE, "attempt": attempt}
        yield {
            "event": "result",
            "answer": UNCERTAIN_RESPONSE,
            "selfCheckStatus": "SELF_CHECK_FAILED",
            "attempt": attempt,
        }
        return
