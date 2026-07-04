from app.agent import diagnose
from app.rag import KnowledgeItem, search_knowledge


KNOWLEDGE = [
    KnowledgeItem(
        id="free-cn",
        title="CN cashflow",
        content="Cashflow diagnosis for cn-default restaurants.",
        source_url="seed://free-cn",
        source_id="seed-baseline",
        weight=0.8,
        confidence=0.8,
        industry_id="general",
        region_id="cn-default",
        entitlement="FREE",
        review_confidence=0.9,
        historical_quality=0.8,
    ),
    KnowledgeItem(
        id="paid-cn",
        title="Paid margin benchmark",
        content="Paid margin benchmark for cn-default restaurants.",
        source_url="seed://paid-cn",
        source_id="paid-seed",
        weight=1.0,
        confidence=0.95,
        industry_id="general",
        region_id="cn-default",
        entitlement="PAID",
        review_confidence=0.95,
        historical_quality=0.9,
    ),
    KnowledgeItem(
        id="paid-other-region",
        title="Other region benchmark",
        content="Paid benchmark for another region.",
        source_url="seed://paid-other",
        source_id="paid-seed",
        weight=1.0,
        confidence=0.99,
        industry_id="general",
        region_id="cn-other",
        entitlement="PAID",
        review_confidence=0.95,
        historical_quality=0.9,
    ),
]


def test_free_member_retrieval_excludes_paid_evidence():
    results = search_knowledge(
        "cashflow benchmark",
        KNOWLEDGE,
        region_id="cn-default",
        industry_id="general",
        membership_level="FREE",
    )

    assert [result.id for result in results] == ["free-cn"]


def test_seed_paid_retrieval_includes_paid_same_region_evidence():
    results = search_knowledge(
        "cashflow benchmark",
        KNOWLEDGE,
        region_id="cn-default",
        industry_id="general",
        membership_level="SEED_PAID",
    )

    assert [result.id for result in results][:2] == ["paid-cn", "free-cn"]
    assert "paid-other-region" not in [result.id for result in results]


def test_diagnosis_self_check_blocks_conflicting_unsupported_answer():
    result = diagnose(
        "Should I ignore a conflicting paid benchmark?",
        knowledge=KNOWLEDGE,
        region_id="cn-default",
        industry_id="general",
        membership_level="FREE",
        conflict_labels=["SUSPICIOUS_CONFLICT"],
    )

    assert result["selfCheckStatus"] == "NEEDS_REVIEW"
    assert result["confidence"] == "LOW"
    assert "unsupported" in result["answer"].lower()
