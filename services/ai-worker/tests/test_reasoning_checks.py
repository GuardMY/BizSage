"""Tests for the five individual reasoning check functions."""

import pytest

from app.reasoning_checks.checks import (
    CheckSeverity,
    aggregate_check_results,
    check_fact,
    check_timeliness,
    check_region,
    check_logic,
    check_compliance,
    run_all_checks,
)


# ══════════════════════════════════════════════════════════════════════════════
# Fact check
# ══════════════════════════════════════════════════════════════════════════════

class TestFactCheck:
    def test_passes_with_aligned_answer(self):
        result = check_fact(
            "餐厅需要关注现金流和翻台率。",
            [{"id": "k1", "content": "餐饮门店应核对客单价和翻台率。"}],
        )
        assert result.passed

    def test_fails_on_empty_answer(self):
        result = check_fact("", [{"id": "k1", "content": "some content"}])
        assert not result.passed
        assert result.severity == CheckSeverity.ERROR

    def test_warns_when_answer_exceeds_evidence(self):
        long_answer = "经营分析报告：" + "详细分析内容。" * 200
        result = check_fact(
            long_answer,
            [{"id": "k1", "content": "short evidence"}],
        )
        assert not result.passed
        assert result.severity == CheckSeverity.WARNING

    def test_disabled_returns_info(self):
        result = check_fact("anything", [], enabled=False)
        assert result.passed
        assert result.severity == CheckSeverity.INFO

    def test_no_evidence_returns_warning(self):
        result = check_fact("some answer", [])
        assert result.passed  # passes but warns
        assert result.severity == CheckSeverity.WARNING


# ══════════════════════════════════════════════════════════════════════════════
# Timeliness check
# ══════════════════════════════════════════════════════════════════════════════

class TestTimelinessCheck:
    def test_passes_with_no_timestamps(self):
        result = check_timeliness(
            "answer",
            [{"id": "k1", "content": "no timestamp here"}],
        )
        assert result.passed

    def test_flags_very_stale_evidence(self):
        import time
        very_old = time.time() - 400 * 86400  # 400 days ago
        result = check_timeliness(
            "answer",
            [{"id": "old-k1", "content": "old", "timestamp": very_old}],
            long_term_days=365,
        )
        assert not result.passed
        assert "long-term" in result.message.lower() or "365" in result.message

    def test_disabled_returns_info(self):
        result = check_timeliness("answer", [], enabled=False)
        assert result.passed


# ══════════════════════════════════════════════════════════════════════════════
# Region check
# ══════════════════════════════════════════════════════════════════════════════

class TestRegionCheck:
    def test_passes_when_no_region(self):
        result = check_region("some answer", None)
        assert result.passed

    def test_passes_when_answer_matches_region(self):
        result = check_region("中国市场分析", "cn-default")
        assert result.passed

    def test_warns_on_cross_region_reference(self):
        result = check_region(
            "根据美国市场的经验，建议...", "cn-default"
        )
        assert not result.passed
        assert result.severity == CheckSeverity.WARNING
        assert "美国" in result.message

    def test_disabled_returns_info(self):
        result = check_region("美国市场", "cn-default", enabled=False)
        assert result.passed


# ══════════════════════════════════════════════════════════════════════════════
# Logic check
# ══════════════════════════════════════════════════════════════════════════════

class TestLogicCheck:
    def test_passes_with_consistent_answer(self):
        result = check_logic("市场行情整体向好，建议把握机会扩大生产规模。")
        assert result.passed

    def test_flags_contradiction_close_together(self):
        result = check_logic("销售额明显上升，但同时也显著下降。")
        assert not result.passed
        assert result.severity == CheckSeverity.WARNING

    def test_disabled_returns_info(self):
        result = check_logic("anything", enabled=False)
        assert result.passed

    def test_skips_empty_answer(self):
        result = check_logic("")
        assert result.passed


# ══════════════════════════════════════════════════════════════════════════════
# Compliance check
# ══════════════════════════════════════════════════════════════════════════════

class TestComplianceCheck:
    def test_passes_with_disclaimer(self):
        result = check_compliance(
            "经营建议：控制成本。免责声明：本分析仅供参考。"
        )
        assert result.passed

    def test_fails_on_blocked_topic(self):
        result = check_compliance("推荐你购买这些股票进行投资建议。")
        assert not result.passed
        assert result.severity == CheckSeverity.ERROR

    def test_fails_on_profit_claim(self):
        result = check_compliance(
            "这个策略保证盈利。免责声明：仅供参考。"
        )
        assert not result.passed
        assert result.severity == CheckSeverity.ERROR

    def test_warns_missing_disclaimer(self):
        result = check_compliance("这是一个好策略，应该立即采用。")
        assert not result.passed
        assert result.severity == CheckSeverity.WARNING

    def test_disabled_returns_info(self):
        result = check_compliance("投资建议", enabled=False)
        assert result.passed


# ══════════════════════════════════════════════════════════════════════════════
# Aggregation
# ══════════════════════════════════════════════════════════════════════════════

class TestAggregation:
    def test_all_passed_returns_passed(self):
        from app.reasoning_checks.checks import CheckResult
        results = [
            CheckResult("fact", True),
            CheckResult("timeliness", True),
            CheckResult("region", True),
            CheckResult("logic", True),
            CheckResult("compliance", True),
        ]
        assert aggregate_check_results(results) == "PASSED"

    def test_warning_returns_needs_review(self):
        from app.reasoning_checks.checks import CheckResult
        results = [
            CheckResult("fact", True),
            CheckResult("timeliness", True),
            CheckResult("region", False, CheckSeverity.WARNING, "cross-region"),
            CheckResult("logic", True),
            CheckResult("compliance", True),
        ]
        assert aggregate_check_results(results) == "NEEDS_REVIEW"

    def test_error_returns_self_check_failed(self):
        from app.reasoning_checks.checks import CheckResult
        results = [
            CheckResult("fact", True),
            CheckResult("timeliness", True),
            CheckResult("region", True),
            CheckResult("logic", True),
            CheckResult("compliance", False, CheckSeverity.ERROR, "blocked topic"),
        ]
        assert aggregate_check_results(results) == "SELF_CHECK_FAILED"


# ══════════════════════════════════════════════════════════════════════════════
# Orchestrator
# ══════════════════════════════════════════════════════════════════════════════

class TestRunAllChecks:
    def test_run_all_returns_five_results(self):
        results = run_all_checks(
            "经营建议：控制成本。免责声明：仅供参考。",
            [{"id": "k1", "content": "成本控制建议"}],
        )
        assert len(results) == 5
        check_names = [r.check_name for r in results]
        assert check_names == ["fact", "timeliness", "region", "logic", "compliance"]
