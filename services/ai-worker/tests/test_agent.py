from app.agent import diagnose
from app.memory import extract_memory_candidates, should_sync_to_vector_memory
from app.rag import KnowledgeItem, search_knowledge


KNOWLEDGE = [
    KnowledgeItem(
        id="k1",
        title="椁愰ギ鐜伴噾娴?",
        content="椁愰ギ闂ㄥ簵搴旀牳瀵瑰鍗曚环銆佺炕鍙扮巼銆侀鏉愭崯鑰楃巼銆佸钩鍙颁剑閲戙€佺閲戝崰钀ユ敹姣斾緥鍜岀幇閲戝洖娆惧懆鏈熴€?",
        source_url="seed://restaurant-cashflow",
        source_id="seed-baseline",
        weight=1.0,
        confidence=0.9,
        industry_id="general",
        region_id="cn-default",
    ),
    KnowledgeItem(
        id="k2",
        title="搴撳瓨椋庨櫓",
        content="搴撳瓨鍛ㄨ浆澶╂暟杩囬珮浼氬帇鍗犵幇閲戞祦锛屽苟鍊掗€兼笭閬撲綆浠锋竻璐с€?",
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


def test_search_knowledge_reranks_by_keyword_and_weight():
    results = search_knowledge("椁愰ギ闂ㄥ簵骞冲彴浣ｉ噾鎬庝箞璇婃柇", KNOWLEDGE)

    assert results[0].id == "k1"
    assert results[0].score > 0


def test_diagnosis_returns_information_insufficient_without_evidence():
    result = diagnose("鏂拌兘婧愰棬搴楄ˉ璐存斂绛?", knowledge=[])

    assert result["selfCheckStatus"] == "INSUFFICIENT_EVIDENCE"
    assert result["sources"] == []
    assert result["confidence"] == "LOW"
    assert result["disclaimer"]


def test_diagnosis_includes_sources_timeliness_confidence_and_disclaimer():
    result = diagnose("椁愰ギ闂ㄥ簵鐜伴噾娴佹€庝箞璇婃柇", knowledge=KNOWLEDGE)

    assert "椁愰ギ闂ㄥ簵鐜伴噾娴佹€庝箞璇婃柇" in result["answer"]
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
                    "title": "寮傚湴浠樿垂鎯呮姤",
                    "content": "涓嶅簲閫氳繃鍖哄煙鍜屼細鍛橀檺鍒躲€?",
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
        "椁愰ギ闂ㄥ簵鐜伴噾娴佹€庝箞璇婃柇",
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
        "椁愰ギ闂ㄥ簵鐜伴噾娴佹€庝箞璇婃柇",
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

    assert "CONCLUSION_FIRST" in result["answer"]
    assert "DELIVERY_PLATFORM_HEAVY" in result["answer"]


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
        }
    ) is True
