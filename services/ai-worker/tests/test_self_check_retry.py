"""Tests for the retry loop that wraps LLM calls with self-check."""

import pytest

from app.reasoning_checks.retry import UNCERTAIN_RESPONSE, run_with_retry


class TestRunWithRetry:
    """Tests for the retry loop."""

    def test_first_pass_returns_answer(self):
        """When checks pass on the first attempt, return the answer immediately."""
        def good_llm(messages):
            return "经营建议：控制成本。免责声明：本分析仅供参考。"

        answer, status = run_with_retry(
            llm_call_fn=good_llm,
            messages=[{"role": "user", "content": "test"}],
            evidence=[{"id": "k1", "content": "控制成本是有效的经营策略。"}],
            max_retries=2,
        )
        assert answer == good_llm([])
        assert status == "PASSED"

    def test_retry_fixes_warning(self):
        """A warning on first attempt is fixed on second attempt."""
        call_count = [0]

        def flaky_llm(messages):
            call_count[0] += 1
            if call_count[0] == 1:
                # Missing disclaimer → compliance WARNING
                return "这个策略很好，建议立即采用。"
            else:
                return "经营建议：控制成本。免责声明：仅供参考。"

        answer, status = run_with_retry(
            llm_call_fn=flaky_llm,
            messages=[{"role": "user", "content": "test"}],
            evidence=[{"id": "k1", "content": "成本控制建议"}],
            max_retries=2,
        )
        assert call_count[0] == 2  # called twice
        assert status == "PASSED"

    def test_max_retries_exhausted_returns_uncertain(self):
        """When all retries are exhausted, return UNCERTAIN_RESPONSE."""
        call_count = [0]

        def always_bad_llm(messages):
            call_count[0] += 1
            return "推荐购买股票，保证盈利。"  # blocked topic → compliance ERROR

        answer, status = run_with_retry(
            llm_call_fn=always_bad_llm,
            messages=[{"role": "user", "content": "test"}],
            evidence=[{"id": "k1", "content": "generic evidence"}],
            max_retries=2,
        )
        assert call_count[0] == 3  # 1 initial + 2 retries
        assert status == "SELF_CHECK_FAILED"
        assert "信息存疑" in answer

    def test_checks_disabled_returns_passed(self):
        """When all checks are disabled, always return PASSED."""
        def any_llm(messages):
            return "推荐购买股票投资建议。"  # would normally fail compliance

        answer, status = run_with_retry(
            llm_call_fn=any_llm,
            messages=[{"role": "user", "content": "test"}],
            evidence=[],
            config={
                "fact_check_enabled": False,
                "timeliness_check_enabled": False,
                "region_check_enabled": False,
                "logic_check_enabled": False,
                "compliance_check_enabled": False,
            },
            max_retries=2,
        )
        assert status == "PASSED"

    def test_messages_grow_with_retries(self):
        """Verify that retry feedback is appended to messages."""
        captured_messages = []

        def recording_llm(messages):
            captured_messages.append(list(messages))  # snapshot
            if len(captured_messages) == 1:
                return "推荐购买股票投资建议。"  # blocked topic
            else:
                return "经营建议。免责声明：仅供参考。"

        answer, status = run_with_retry(
            llm_call_fn=recording_llm,
            messages=[{"role": "system", "content": "system prompt"}],
            evidence=[],
            max_retries=2,
        )
        # Second call should have 2 extra messages (assistant + user feedback)
        assert len(captured_messages[1]) > len(captured_messages[0])

    def test_uncertain_response_is_non_empty(self):
        assert len(UNCERTAIN_RESPONSE) > 20
        assert "信息存疑" in UNCERTAIN_RESPONSE
