"""Five individual checker functions plus the orchestrator for the V2 reasoning self-test.

Checks (in execution order):
1. check_fact      — verify output aligns with retrieved evidence
2. check_timeliness — verify output uses the latest available knowledge
3. check_region    — verify output respects user's regional context
4. check_logic     — verify internal reasoning consistency
5. check_compliance — enforce legal/regulatory boundaries
"""

from __future__ import annotations

import time as _time
from dataclasses import dataclass
from enum import Enum


class CheckSeverity(str, Enum):
    ERROR = "ERROR"      # blocks delivery, triggers retry
    WARNING = "WARNING"  # flags NEEDS_REVIEW but does not block delivery
    INFO = "INFO"        # informational only


@dataclass
class CheckResult:
    check_name: str
    passed: bool
    severity: CheckSeverity = CheckSeverity.ERROR
    message: str = ""
    details: dict | None = None


# ══════════════════════════════════════════════════════════════════════════════
# Check 1: Fact Check
# ══════════════════════════════════════════════════════════════════════════════

def check_fact(
    answer: str,
    evidence: list[dict],
    enabled: bool = True,
) -> CheckResult:
    """Verify output aligns with retrieved evidence.

    Heuristic approach:
    - Detect explicit contradiction markers.
    - Flag answers that substantially exceed evidence scope.
    """
    if not enabled:
        return CheckResult("fact", True, CheckSeverity.INFO, "Fact check disabled")

    if not answer.strip():
        return CheckResult("fact", False, CheckSeverity.ERROR,
                           "Answer is empty")

    if not evidence:
        return CheckResult("fact", True, CheckSeverity.WARNING,
                           "No evidence available for fact verification")

    # Detect explicit contradiction markers (LLM acknowledging it contradicts itself)
    contradiction_markers = [
        "这不是事实", "实际上并非如此", "与事实不符",
        "我之前的陈述有误", "纠正一下",
    ]
    for marker in contradiction_markers:
        if marker in answer:
            return CheckResult("fact", False, CheckSeverity.ERROR,
                               f"Answer contains contradiction marker: '{marker}'",
                               {"marker": marker})

    # Rough scope check: flag if answer is disproportionately long vs evidence
    evidence_text = " ".join(e.get("content", "") for e in evidence)
    if len(answer) > max(len(evidence_text) * 3, 500):
        return CheckResult("fact", False, CheckSeverity.WARNING,
                           "Answer substantially exceeds evidence scope — "
                           "possible hallucination",
                           {"answer_len": len(answer),
                            "evidence_len": len(evidence_text)})

    return CheckResult("fact", True, CheckSeverity.INFO, "Fact check passed")


# ══════════════════════════════════════════════════════════════════════════════
# Check 2: Timeliness Check
# ══════════════════════════════════════════════════════════════════════════════

def check_timeliness(
    answer: str,
    evidence: list[dict],
    enabled: bool = True,
    short_term_days: int = 90,
    long_term_days: int = 365,
) -> CheckResult:
    """Verify output uses the latest available knowledge.

    Flags stale evidence sources based on timestamp metadata.
    """
    if not enabled:
        return CheckResult("timeliness", True, CheckSeverity.INFO,
                           "Timeliness check disabled")

    now = _time.time()
    stale_found: list[str] = []
    very_stale_found: list[str] = []

    for item in evidence:
        timestamp = item.get("timestamp") or item.get("created_at") or item.get("create_time")
        if timestamp:
            try:
                # Accept both epoch seconds and ISO-format strings
                if isinstance(timestamp, str):
                    import datetime as _dt
                    ts = _dt.datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
                    age_days = (now - ts.timestamp()) / 86400
                else:
                    age_days = (now - float(timestamp)) / 86400

                if age_days > long_term_days:
                    very_stale_found.append(str(item.get("id", "unknown")))
                elif age_days > short_term_days:
                    stale_found.append(str(item.get("id", "unknown")))
            except (ValueError, TypeError, OSError):
                pass  # unparseable timestamps are silently skipped

    if very_stale_found:
        return CheckResult("timeliness", False, CheckSeverity.WARNING,
                           f"Evidence exceeds long-term freshness threshold: "
                           f"{len(very_stale_found)} item(s) > {long_term_days} days",
                           {"very_stale": very_stale_found})

    if stale_found:
        return CheckResult("timeliness", True, CheckSeverity.WARNING,
                           f"Evidence exceeds short-term freshness threshold: "
                           f"{len(stale_found)} item(s) > {short_term_days} days",
                           {"stale": stale_found})

    return CheckResult("timeliness", True, CheckSeverity.INFO,
                       "Timeliness check passed")


# ══════════════════════════════════════════════════════════════════════════════
# Check 3: Region Check
# ══════════════════════════════════════════════════════════════════════════════

# Known region identifier mappings for cross-region detection
_REGION_MAP: dict[str, list[str]] = {
    "cn-default":  ["中国", "中国大陆", "国内", "全国"],
    "cn-hongkong": ["香港", "香港特别行政区", "港资"],
    "cn-shanghai": ["上海", "上海市"],
    "cn-shenzhen": ["深圳", "深圳市"],
    "us-default":  ["美国", "北美"],
}

# Regions whose identifiers trigger warnings when mentioned in other contexts
_OTHER_REGION_IDS: dict[str, list[str]] = {
    rid: [kw for oid, kws in _REGION_MAP.items() if oid != rid for kw in kws]
    for rid in _REGION_MAP
}


def check_region(
    answer: str,
    region_id: str | None,
    enabled: bool = True,
) -> CheckResult:
    """Verify output respects user's regional context.

    Flags mentions of other regions that could cause cross-region confusion.
    Uses WARNING severity only — legitimate cross-region comparisons are allowed.
    """
    if not enabled or not region_id:
        return CheckResult("region", True, CheckSeverity.INFO,
                           "Region check skipped (disabled or no region_id)")

    if region_id not in _REGION_MAP:
        return CheckResult("region", True, CheckSeverity.INFO,
                           f"Region check skipped (unknown region: {region_id})")

    other_identifiers = _OTHER_REGION_IDS.get(region_id, [])
    if not other_identifiers:
        return CheckResult("region", True, CheckSeverity.INFO,
                           "Region check passed (no conflicting regions defined)")

    for other in other_identifiers:
        if other in answer:
            return CheckResult("region", False, CheckSeverity.WARNING,
                               f"Answer references '{other}' which differs from "
                               f"user region '{region_id}' — verify context",
                               {"mentioned": other, "user_region": region_id})

    return CheckResult("region", True, CheckSeverity.INFO, "Region check passed")


# ══════════════════════════════════════════════════════════════════════════════
# Check 4: Logic Check
# ══════════════════════════════════════════════════════════════════════════════

# Contradiction pairs — flag when both appear close together in the answer
_CONTRADICTION_PAIRS: list[tuple[str, str]] = [
    ("上升", "下降"),
    ("增加", "减少"),
    ("改善", "恶化"),
    ("增长", "萎缩"),
    ("盈利", "亏损"),
    ("高", "低"),
]

# Expected section markers based on output format instructions
_REQUIRED_SECTIONS_DIAGNOSIS = ["关键发现", "风险提示", "可行动建议"]
_REQUIRED_SECTIONS_LEARNING = ["核心概念", "行业实践", "关键指标"]


def check_logic(
    answer: str,
    output_format: str | None = None,
    enabled: bool = True,
) -> CheckResult:
    """Verify internal reasoning consistency.

    Checks for:
    - Contradictory terms used in close proximity.
    - Structural completeness (expected sections present).
    """
    if not enabled:
        return CheckResult("logic", True, CheckSeverity.INFO, "Logic check disabled")

    if not answer.strip():
        return CheckResult("logic", True, CheckSeverity.INFO,
                           "Logic check skipped (empty answer)")

    # ── Contradiction detection ──
    for pos_term, neg_term in _CONTRADICTION_PAIRS:
        if pos_term in answer and neg_term in answer:
            pos_idx = answer.find(pos_term)
            neg_idx = answer.find(neg_term)
            if abs(pos_idx - neg_idx) < 200:  # within 200 chars → likely same topic
                return CheckResult("logic", False, CheckSeverity.WARNING,
                                   f"Potential contradiction: both '{pos_term}' and "
                                   f"'{neg_term}' used in close proximity "
                                   f"({abs(pos_idx - neg_idx)} chars)",
                                   {"positive": pos_term, "negative": neg_term,
                                    "distance": abs(pos_idx - neg_idx)})

    # ── Structural completeness ──
    if output_format:
        required = []
        if "关键发现" in output_format or "DIAGNOSIS" in output_format.upper():
            required = _REQUIRED_SECTIONS_DIAGNOSIS
        elif "核心概念" in output_format or "LEARNING" in output_format.upper():
            required = _REQUIRED_SECTIONS_LEARNING

        for section in required:
            if section not in answer:
                return CheckResult("logic", False, CheckSeverity.WARNING,
                                   f"Answer missing expected section: '{section}'",
                                   {"missing_section": section})

    return CheckResult("logic", True, CheckSeverity.INFO, "Logic check passed")


# ══════════════════════════════════════════════════════════════════════════════
# Check 5: Compliance Check
# ══════════════════════════════════════════════════════════════════════════════

# Blocked topics — must never appear in any answer
_BLOCKED_TOPICS: list[str] = [
    "炒股", "股票推荐", "投资建议", "内幕消息",
    "避税方案", "逃税", "洗钱",
    "赌博", "博彩",
]

# Mandatory disclaimer markers — at least one must be present
_DISCLAIMER_MARKERS: list[str] = ["免责声明", "不构成", "仅供参考"]

# Deterministic profit-claim markers
_PROFIT_CLAIM_MARKERS: list[str] = [
    "保证盈利", "稳赚", "包赚", "绝对赚钱", "一定赚钱",
    "保证收益", "确定收益", "确保回报",
]


def check_compliance(
    answer: str,
    enabled: bool = True,
) -> CheckResult:
    """Enforce legal and regulatory boundaries.

    Checks:
    - Blocked topics (ERROR severity — must retry).
    - Deterministic profit claims (ERROR severity).
    - Mandatory disclaimer presence (WARNING severity).
    """
    if not enabled:
        return CheckResult("compliance", True, CheckSeverity.INFO,
                           "Compliance check disabled")

    if not answer.strip():
        return CheckResult("compliance", True, CheckSeverity.INFO,
                           "Compliance check skipped (empty answer)")

    # ── Blocked topics ──
    for topic in _BLOCKED_TOPICS:
        if topic in answer:
            return CheckResult("compliance", False, CheckSeverity.ERROR,
                               f"Answer contains blocked topic: '{topic}'",
                               {"blocked_topic": topic})

    # ── Deterministic profit claims ──
    for claim in _PROFIT_CLAIM_MARKERS:
        if claim in answer:
            return CheckResult("compliance", False, CheckSeverity.ERROR,
                               f"Answer contains deterministic profit claim: '{claim}'",
                               {"profit_claim": claim})

    # ── Mandatory disclaimer ──
    if not any(marker in answer for marker in _DISCLAIMER_MARKERS):
        return CheckResult("compliance", False, CheckSeverity.WARNING,
                           "Answer missing mandatory disclaimer",
                           {"missing_markers": _DISCLAIMER_MARKERS})

    return CheckResult("compliance", True, CheckSeverity.INFO,
                       "Compliance check passed")


# ══════════════════════════════════════════════════════════════════════════════
# Orchestrator
# ══════════════════════════════════════════════════════════════════════════════

def run_all_checks(
    answer: str,
    evidence: list[dict],
    region_id: str | None = None,
    output_format: str | None = None,
    config: dict | None = None,
) -> list[CheckResult]:
    """Run all five checks and return results list.

    Args:
        answer: the LLM-generated answer text.
        evidence: list of knowledge items used as context.
        region_id: user's region for cross-region validation.
        output_format: the system prompt used (for structural completeness check).
        config: optional dict of check-specific configuration overrides.

    Returns:
        List of 5 ``CheckResult`` objects, one per check.
    """
    cfg = config or {}
    return [
        check_fact(answer, evidence,
                   enabled=cfg.get("fact_check_enabled", True)),
        check_timeliness(answer, evidence,
                         enabled=cfg.get("timeliness_check_enabled", True),
                         short_term_days=int(cfg.get("timeliness_short_term_days", 90)),
                         long_term_days=int(cfg.get("timeliness_long_term_days", 365))),
        check_region(answer, region_id,
                     enabled=cfg.get("region_check_enabled", True)),
        check_logic(answer, output_format,
                    enabled=cfg.get("logic_check_enabled", True)),
        check_compliance(answer,
                         enabled=cfg.get("compliance_check_enabled", True)),
    ]


def aggregate_check_results(results: list[CheckResult]) -> str:
    """Aggregate individual check results into an overall selfCheckStatus.

    Returns one of: ``"PASSED"``, ``"NEEDS_REVIEW"``, ``"SELF_CHECK_FAILED"``.
    """
    has_error = any(not r.passed and r.severity == CheckSeverity.ERROR for r in results)
    has_warning = any(not r.passed and r.severity == CheckSeverity.WARNING for r in results)

    if has_error:
        return "SELF_CHECK_FAILED"
    if has_warning:
        return "NEEDS_REVIEW"
    return "PASSED"


def build_feedback(results: list[CheckResult]) -> str:
    """Build a feedback message for retry, listing all failed checks."""
    failed = [r for r in results if not r.passed]
    if not failed:
        return ""

    parts = ["【自检反馈】以下检查项未通过，请修正你的回答后重新输出："]
    for r in failed:
        sev_label = "错误" if r.severity == CheckSeverity.ERROR else "警告"
        parts.append(f"- [{r.check_name}] [{sev_label}] {r.message}")
    return "\n".join(parts)
