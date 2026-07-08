import pytest
from app.agent import diagnose
from app.memory import (
    MemoryCategory,
    build_memory_context,
    consolidate_memories,
    extract_diagnosis_memories,
    extract_learning_memories,
    extract_memory_candidates,
    extract_transition_memories,
    forget_expired,
    should_sync_to_vector_memory,
)
from app.rag import KnowledgeItem, search_knowledge


KNOWLEDGE = [
    KnowledgeItem(
        id="k1",
        title="餐饮现金流",
        content="餐饮门店应核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。",
        source_url="seed://restaurant-cashflow",
        source_id="seed-baseline",
        weight=1.0,
        confidence=0.9,
        industry_id="general",
        region_id="cn-default",
    ),
    KnowledgeItem(
        id="k2",
        title="库存风险",
        content="库存周转天数过高会压占现金流，并倒逼渠道低价清货。",
        source_url="seed://inventory-risk",
        source_id="seed-baseline",
        weight=0.85,
        confidence=0.85,
        industry_id="general",
        region_id="cn-default",
    ),
]


class FakeVectorStore:
    def __init__(self, candidates: list[dict]) -> None:
        self.candidates = candidates
        self.search_calls: list[dict] = []

    def search(self, query_vector: list[float], limit: int = 5) -> list[dict]:
        self.search_calls.append({"query_vector": query_vector, "limit": limit})
        return self.candidates[:limit]


@pytest.fixture(autouse=True)
def _mock_llm(monkeypatch):
    """Mock run_with_retry so tests don't need a real LLM API key or ModelRouter."""

    def fake_run_with_retry(
        llm_call_fn, messages, evidence=None, region_id=None,
        output_format=None, config=None, max_retries=None,
    ):
        # Extract the question from user messages for a realistic mock answer
        question = ""
        for m in messages:
            if m.get("role") == "user":
                question = m["content"][:60]
                break
        answer = (
            f"针对「{question}」的诊断分析报告。"
            "基于以下证据：建议先核对关键经营变量，再根据证据调整动作优先级。"
        )
        return answer, "PASSED"

    monkeypatch.setattr("app.agent.run_with_retry", fake_run_with_retry)


def test_search_knowledge_reranks_by_keyword_and_weight():
    results = search_knowledge("餐饮门店平台佣金怎么诊断", KNOWLEDGE)

    assert results[0].id == "k1"
    assert results[0].score > 0


def test_diagnosis_returns_information_insufficient_without_evidence():
    result = diagnose("新能源门店补贴政策", knowledge=[])

    assert result["selfCheckStatus"] == "INSUFFICIENT_EVIDENCE"
    assert result["sources"] == []
    assert result["confidence"] == "LOW"
    assert result["disclaimer"]


def test_diagnosis_includes_sources_timeliness_confidence_and_disclaimer():
    result = diagnose("餐饮门店现金流怎么诊断", knowledge=KNOWLEDGE)

    assert "餐饮门店现金流怎么诊断" in result["answer"]
    assert result["sources"][0]["id"] == "k1"
    assert result["confidence"] == "MEDIUM"
    assert "V1" in result["timeliness"]
    assert result["disclaimer"]


def test_diagnosis_uses_vector_store_and_preserves_filters():
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-paid-other-region",
                "score": 0.99,
                "payload": {
                    "id": "paid-other-region",
                    "title": "异地付费情报",
                    "content": "不应通过区域和会员限制。",
                    "source_url": "seed://paid-other-region",
                    "source_id": "seed-baseline",
                    "weight": 1.0,
                    "confidence": 0.95,
                    "industry_id": "general",
                    "region_id": "cn-other",
                    "entitlement": "PAID",
                },
            },
            {
                "id": "point-k1",
                "score": 0.62,
                "payload": KNOWLEDGE[0].__dict__,
            },
        ]
    )

    result = diagnose(
        "餐饮门店现金流怎么诊断",
        knowledge=KNOWLEDGE,
        region_id="cn-default",
        industry_id="general",
        membership_level="FREE",
        vector_store=vector_store,
    )

    assert len(vector_store.search_calls) == 1
    assert [source["id"] for source in result["sources"]] == ["k1"]


def test_diagnosis_uses_recent_messages_summary_and_long_term_memories():
    result = diagnose(
        "餐饮门店现金流怎么诊断",
        knowledge=KNOWLEDGE,
        recent_messages=[
            {"role": "user", "content": "focus on cashflow and inventory"},
            {"role": "assistant", "content": "review inventory and receivables first"},
        ],
        conversation_summary="User prefers conclusion-first answers and relies on delivery platforms.",
        long_term_memories=[
            {"category": "PREFERENCE", "key": "response_style", "value": "CONCLUSION_FIRST"},
            {"category": "BUSINESS_FACT", "key": "channel_mix", "value": "DELIVERY_PLATFORM_HEAVY"},
        ],
    )

    assert result["answer"]  # answer is non-empty
    assert result["selfCheckStatus"] == "PASSED"


def test_extract_memory_candidates_returns_preference_and_business_fact():
    candidates = extract_memory_candidates(
        "以后先给结论再给证据。我有两家门店，主要做外卖。",
        "建议先稳住现金流，再优化库存周转。",
    )

    categories = {item["category"] for item in candidates}
    assert "PREFERENCE" in categories
    assert "BUSINESS_FACT" in categories


def test_semantic_memory_sync_only_for_unstructured_long_term_memory():
    assert should_sync_to_vector_memory(
        {"category": "BUSINESS_FACT", "key": "store_count", "value": "2", "structured": True}
    ) is False
    assert should_sync_to_vector_memory(
        {
            "category": "BUSINESS_FACT",
            "key": "narrative_constraint",
            "value": "堂食波动很大且受平台佣金影响",
            "structured": False,
            "status": "ACTIVE",
            "confidence": 0.80,
        }
    ) is True


# ── Memory V2: LLM-based extraction tests ──────────────────────────

def test_extract_diagnosis_memories_falls_back_to_regex_when_llm_unavailable(monkeypatch):
    """When LLM extraction throws, regex fallback is used."""
    # Force LLM extraction to fail
    def _raise(*a, **kw):
        raise Exception("LLM unavailable")
    monkeypatch.setattr("app.memory._extract_memories_via_llm", _raise)
    candidates = extract_diagnosis_memories(
        "我有两家门店，主要做外卖，先给结论再给证据。",
        "建议先稳住现金流。",
    )
    categories = {c["category"] for c in candidates}
    assert "PREFERENCE" in categories
    assert "BUSINESS_FACT" in categories


def test_extract_diagnosis_memories_uses_llm_when_available(monkeypatch):
    """LLM results are preferred over regex fallback."""
    llm_result = [{
        "category": "BUSINESS_FACT",
        "key": "revenue_range",
        "value": "月营收约15万",
        "confidence": 0.92,
        "structured": True,
    }]
    monkeypatch.setattr(
        "app.memory._extract_memories_via_llm",
        lambda *a, **kw: llm_result,
    )
    candidates = extract_diagnosis_memories(
        "我每月营收大约15万，有两家门店。",
        "建议优化成本结构。",
    )
    assert len(candidates) == 1
    assert candidates[0]["key"] == "revenue_range"
    assert candidates[0]["confidence"] == 0.92


def test_extract_diagnosis_memories_with_existing_deduplication(monkeypatch):
    """LLM extraction receives existing memories for deduplication."""
    captured_existing = []

    def fake_llm(question, answer, chain_node_id=None, existing_memories=None):
        captured_existing.append(existing_memories)
        return []

    monkeypatch.setattr("app.memory._extract_memories_via_llm", fake_llm)
    existing = [{"category": "PREFERENCE", "key": "response_style", "value": "CONCISE"}]
    extract_diagnosis_memories("test", "answer", existing_memories=existing)
    assert captured_existing[0] == existing


def test_extract_learning_memories_falls_back_to_regex(monkeypatch):
    """Learning extraction falls back to regex when LLM unavailable."""
    def _raise(*a, **kw):
        raise Exception("LLM unavailable")
    monkeypatch.setattr("app.memory._extract_memories_via_llm", _raise)
    candidates = extract_learning_memories(
        "原材料采购成本怎么控制？详细给我讲讲",
        "原材料成本控制有三大要点...",
        chain_node_id="raw-materials",
    )
    categories = {c["category"] for c in candidates}
    assert "LEARNING_PROGRESS" in categories


def test_extract_learning_memories_uses_llm_when_available(monkeypatch):
    """LLM results are preferred for learning extraction."""
    llm_result = [{
        "category": "LEARNING_PROGRESS",
        "key": "interest_cost_management",
        "value": "成本管控",
        "confidence": 0.85,
        "structured": False,
    }]
    monkeypatch.setattr(
        "app.memory._extract_memories_via_llm",
        lambda *a, **kw: llm_result,
    )
    candidates = extract_learning_memories("test", "answer", chain_node_id="raw-materials")
    assert candidates == llm_result


# ── Memory V2: Transition memory extraction tests ──────────────────

def test_extract_transition_memories_learn_to_diagnose():
    candidates = extract_transition_memories(
        from_mode="LEARNING",
        to_mode="DIAGNOSIS",
        user_question="帮我诊断现金流",
        chain_node_id="sales-payment",
        previous_answer="销售回款周期一般在30-60天...",
    )
    # Should contain transition record + diagnosis_from_learning record
    keys = {c["key"] for c in candidates}
    assert "transition_learning_to_diagnosis" in keys
    assert "diagnosis_from_learning_sales-payment" in keys


def test_extract_transition_memories_diagnose_to_learn():
    candidates = extract_transition_memories(
        from_mode="DIAGNOSIS",
        to_mode="LEARNING",
        user_question="教我库存管理",
        chain_node_id="warehouse-inventory",
        previous_answer="诊断发现库存周转天数偏高...",
    )
    keys = {c["key"] for c in candidates}
    assert "transition_diagnosis_to_learning" in keys
    assert "learning_from_diagnosis_warehouse-inventory" in keys


def test_extract_transition_memories_without_chain_node():
    candidates = extract_transition_memories(
        from_mode="LEARNING",
        to_mode="DIAGNOSIS",
        user_question="帮我诊断",
        chain_node_id=None,
    )
    keys = {c["key"] for c in candidates}
    assert "transition_learning_to_diagnosis" in keys
    # No chain-specific records without chain_node_id
    assert len(candidates) == 1  # Only the transition record itself


# ── Memory V2: forget_expired tests ────────────────────────────────

def test_forget_expired_filters_past_timestamps():
    import time
    now = time.time()
    memories = [
        {"category": "BUSINESS_FACT", "key": "recent", "value": "x",
         "expires_at": now + 86400},  # still valid
        {"category": "BUSINESS_FACT", "key": "old", "value": "y",
         "expires_at": now - 86400},  # expired
        {"category": "PREFERENCE", "key": "style", "value": "z"},  # no expiry → keep
    ]
    active = forget_expired(memories, current_time=now)
    keys = {m["key"] for m in active}
    assert "recent" in keys
    assert "old" not in keys
    assert "style" in keys


def test_forget_expired_keeps_non_numeric_expiry():
    memories = [
        {"category": "PREFERENCE", "key": "style", "value": "z",
         "expires_at": "2027-01-01T00:00:00Z"},  # string expiry → keep
    ]
    active = forget_expired(memories)
    assert len(active) == 1


# ── Memory V2: build_memory_context with confidence filter ─────────

def test_build_memory_context_filters_low_confidence():
    context = build_memory_context(
        long_term_memories=[
            {"category": "BUSINESS_FACT", "key": "good", "value": "reliable",
             "confidence": 0.90},
            {"category": "PREFERENCE", "key": "bad", "value": "unreliable",
             "confidence": 0.40},  # below MIN_CONFIDENCE_FOR_CONTEXT (0.60)
        ],
    )
    assert "good" in context
    assert "bad" not in context


def test_build_memory_context_all_tiers():
    context = build_memory_context(
        recent_messages=[
            {"role": "user", "content": "诊断现金流"},
            {"role": "assistant", "content": "建议优化库存"},
        ],
        conversation_summary="用户关注现金流管理",
        long_term_memories=[
            {"category": "PREFERENCE", "key": "response_style",
             "value": "CONCLUSION_FIRST", "confidence": 0.95},
            {"category": "BUSINESS_FACT", "key": "store_count",
             "value": "两家门店", "confidence": 0.88},
        ],
    )
    assert "会话摘要" in context
    assert "长期记忆" in context
    assert "最近消息" in context
    assert "CONCLUSION_FIRST" in context
    assert "两家门店" in context


# ── Memory V2: consolidation tests ─────────────────────────────────

def test_consolidate_memories_deduplicates_by_category_key():
    memories = [
        {"category": "BUSINESS_FACT", "key": "stores", "value": "2",
         "confidence": 0.80},
        {"category": "BUSINESS_FACT", "key": "stores", "value": "3",
         "confidence": 0.90},  # higher confidence — should win
        {"category": "PREFERENCE", "key": "style", "value": "concise",
         "confidence": 0.85},
    ]
    consolidated = consolidate_memories(memories)
    assert len(consolidated) == 2
    stores_mem = [m for m in consolidated if m["key"] == "stores"][0]
    assert stores_mem["value"] == "3"
    assert stores_mem["confidence"] == 0.90


def test_consolidate_memories_empty():
    assert consolidate_memories([]) == []


# ── Memory V2: backward-compatible alias ───────────────────────────

def test_extract_memory_candidates_is_diagnosis_alias():
    assert extract_memory_candidates is extract_diagnosis_memories


# ── Memory V2: vector sync with confidence threshold ───────────────

def test_should_sync_to_vector_memory_respects_confidence_threshold():
    # Below MIN_CONFIDENCE_FOR_VECTOR_SYNC (0.70)
    assert should_sync_to_vector_memory({
        "category": "PAIN_POINT", "key": "risk", "value": "some risk",
        "structured": False, "status": "ACTIVE", "confidence": 0.60,
    }) is False
    # Above threshold
    assert should_sync_to_vector_memory({
        "category": "PAIN_POINT", "key": "risk", "value": "some risk",
        "structured": False, "status": "ACTIVE", "confidence": 0.80,
    }) is True
    # Not ACTIVE
    assert should_sync_to_vector_memory({
        "category": "PAIN_POINT", "key": "risk", "value": "some risk",
        "structured": False, "status": "INACTIVE", "confidence": 0.80,
    }) is False
