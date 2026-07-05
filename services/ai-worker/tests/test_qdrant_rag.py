from app.main import DiagnoseRequest, SearchRequest, diagnose_endpoint, search
from app.rag import KnowledgeItem, search_knowledge


def make_item(
    item_id: str,
    *,
    title: str,
    content: str,
    industry_id: str = "general",
    region_id: str = "cn-default",
    entitlement: str = "FREE",
    weight: float = 0.85,
    confidence: float = 0.85,
    review_confidence: float = 0.85,
    historical_quality: float = 0.85,
) -> KnowledgeItem:
    return KnowledgeItem(
        id=item_id,
        title=title,
        content=content,
        source_url=f"seed://{item_id}",
        source_id="seed-baseline",
        weight=weight,
        confidence=confidence,
        industry_id=industry_id,
        region_id=region_id,
        entitlement=entitlement,
        review_confidence=review_confidence,
        historical_quality=historical_quality,
    )


class FakeVectorStore:
    def __init__(self, candidates: list[dict] | None = None) -> None:
        self.candidates = candidates or []
        self.upsert_calls: list[list[KnowledgeItem]] = []
        self.search_calls: list[dict] = []
        self.collection_name = "fake-collection"
        self.client = self
        self.deleted_collections: list[str] = []
        self.fail_delete = False

    def upsert_knowledge(self, items: list[KnowledgeItem]) -> None:
        self.upsert_calls.append(items)

    def search(self, query_vector: list[float], limit: int = 5) -> list[dict]:
        self.search_calls.append({"query_vector": query_vector, "limit": limit})
        return self.candidates[:limit]

    def delete_collection(self, collection_name: str) -> None:
        if self.fail_delete:
            raise RuntimeError("delete failed")
        self.deleted_collections.append(collection_name)


def test_search_knowledge_prefers_vector_store_candidates_and_filters_in_python():
    local_knowledge = [
        make_item("local-only", title="本地命中", content="只有本地词法检索能命中这条。", weight=1.0),
    ]
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-paid-other-region",
                "score": 0.99,
                "payload": make_item(
                    "paid-other-region",
                    title="付费异地区域情报",
                    content="这条结果不应因为区域和会员限制而返回。",
                    region_id="cn-other",
                    entitlement="PAID",
                    weight=1.0,
                    confidence=0.95,
                ).__dict__,
            },
            {
                "id": "point-free-match",
                "score": 0.61,
                "payload": make_item(
                    "free-match",
                    title="餐饮门店平台佣金诊断",
                    content="平台佣金、翻台率和现金回款周期会共同影响门店现金流。",
                    weight=0.92,
                    confidence=0.9,
                    review_confidence=0.95,
                    historical_quality=0.9,
                ).__dict__,
            },
        ]
    )

    results = search_knowledge(
        "餐饮门店平台佣金怎么诊断",
        local_knowledge,
        region_id="cn-default",
        industry_id="general",
        membership_level="FREE",
        vector_store=vector_store,
    )

    assert [result.id for result in results] == ["free-match"]
    assert results[0].score > 0
    assert vector_store.search_calls[0]["limit"] == 15


def test_search_knowledge_falls_back_to_local_scoring_without_vector_store():
    knowledge = [
        make_item(
            "cashflow",
            title="餐饮门店现金流基础诊断",
            content="平台佣金、客单价和翻台率需要一起看。",
            weight=1.0,
            confidence=0.9,
        ),
        make_item(
            "inventory",
            title="库存风险",
            content="库存周转天数过高会压占现金流。",
            weight=0.8,
            confidence=0.8,
        ),
    ]

    results = search_knowledge("餐饮门店平台佣金怎么诊断", knowledge)

    assert [result.id for result in results][:1] == ["cashflow"]


def test_search_knowledge_restricts_request_scoped_candidates_to_current_knowledge_ids():
    knowledge = [
        make_item(
            "request-match",
            title="当前请求知识",
            content="平台佣金和回款周期是优先指标。",
            weight=0.95,
            confidence=0.9,
        )
    ]
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-stale",
                "score": 0.99,
                "payload": {
                    "id": "stale-from-older-request",
                    "title": "旧请求残留情报",
                    "content": "不应泄露到当前请求。",
                    "source_url": "seed://stale",
                    "source_id": "seed-baseline",
                    "weight": 1.0,
                    "confidence": 0.99,
                    "industry_id": "general",
                    "region_id": "cn-default",
                    "entitlement": "FREE",
                },
            },
            {
                "id": "point-request-match",
                "score": 0.74,
                "payload": knowledge[0].__dict__,
            },
        ]
    )

    results = search_knowledge(
        "门店现金流怎么诊断",
        knowledge,
        vector_store=vector_store,
        restrict_to_knowledge_ids=True,
    )

    assert [result.id for result in results] == ["request-match"]


def test_search_knowledge_expands_candidate_window_before_post_filters():
    valid_item = make_item(
        "valid-hit",
        title="有效命中",
        content="平台佣金和现金回款周期会影响门店现金流。",
        weight=0.93,
        confidence=0.91,
    )
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-blocked-1",
                "score": 0.99,
                "payload": make_item(
                    "blocked-paid",
                    title="付费拦截",
                    content="免费会员不应看到这条。",
                    entitlement="PAID",
                    weight=1.0,
                    confidence=0.95,
                ).__dict__,
            },
            {
                "id": "point-blocked-2",
                "score": 0.98,
                "payload": make_item(
                    "blocked-region",
                    title="异地区域拦截",
                    content="区域不匹配，不应保留。",
                    region_id="cn-other",
                    weight=1.0,
                    confidence=0.95,
                ).__dict__,
            },
            {
                "id": "point-valid",
                "score": 0.7,
                "payload": valid_item.__dict__,
            },
        ]
    )

    results = search_knowledge(
        "门店现金流怎么诊断",
        [valid_item],
        limit=1,
        region_id="cn-default",
        industry_id="general",
        membership_level="FREE",
        vector_store=vector_store,
    )

    assert vector_store.search_calls[0]["limit"] == 10
    assert [result.id for result in results] == ["valid-hit"]


def test_rag_search_route_upserts_request_knowledge_before_vector_query(monkeypatch):
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-route-match",
                "score": 0.77,
                "payload": make_item(
                    "route-match",
                    title="区域门店现金流诊断",
                    content="平台佣金和回款周期是优先指标。",
                    weight=0.95,
                    confidence=0.9,
                ).__dict__,
            }
        ]
    )
    monkeypatch.setattr("app.main.build_vector_store", lambda collection_name=None: vector_store)

    response = search(
        SearchRequest(
            query="门店现金流怎么诊断",
            knowledge=[
                {
                    "id": "route-match",
                    "title": "区域门店现金流诊断",
                    "content": "平台佣金和回款周期是优先指标。",
                    "source_url": "seed://route-match",
                    "source_id": "seed-baseline",
                    "weight": 0.95,
                    "confidence": 0.9,
                    "industry_id": "general",
                    "region_id": "cn-default",
                }
            ],
        )
    )

    assert [item.id for item in vector_store.upsert_calls[0]] == ["route-match"]
    assert len(vector_store.search_calls) == 1
    assert response["results"][0]["id"] == "route-match"
    assert vector_store.deleted_collections == ["fake-collection"]


def test_rag_search_route_blocks_stale_cross_request_candidates(monkeypatch):
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-stale",
                "score": 0.99,
                "payload": {
                    "id": "older-request-item",
                    "title": "旧请求情报",
                    "content": "不应返回给当前请求。",
                    "source_url": "seed://older-request-item",
                    "source_id": "seed-baseline",
                    "weight": 1.0,
                    "confidence": 0.95,
                    "industry_id": "general",
                    "region_id": "cn-default",
                    "entitlement": "FREE",
                },
            },
            {
                "id": "point-current",
                "score": 0.72,
                "payload": make_item(
                    "current-request-item",
                    title="当前请求知识",
                    content="当前请求只应返回这条知识。",
                    weight=0.94,
                    confidence=0.9,
                ).__dict__,
            },
        ]
    )
    monkeypatch.setattr("app.main.build_vector_store", lambda collection_name=None: vector_store)

    response = search(
        SearchRequest(
            query="当前请求知识",
            knowledge=[
                {
                    "id": "current-request-item",
                    "title": "当前请求知识",
                    "content": "当前请求只应返回这条知识。",
                    "source_url": "seed://current-request-item",
                    "source_id": "seed-baseline",
                    "weight": 0.94,
                    "confidence": 0.9,
                    "industry_id": "general",
                    "region_id": "cn-default",
                }
            ],
        )
    )

    assert [item["id"] for item in response["results"]] == ["current-request-item"]


def test_rag_search_seed_fallback_ignores_stale_request_payloads(monkeypatch):
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-stale",
                "score": 0.99,
                "payload": {
                    "id": "older-request-item",
                    "title": "旧请求情报",
                    "content": "不应在种子回退请求中泄漏。",
                    "source_url": "seed://older-request-item",
                    "source_id": "seed-baseline",
                    "weight": 1.0,
                    "confidence": 0.95,
                    "industry_id": "general",
                    "region_id": "cn-default",
                    "entitlement": "FREE",
                },
            },
            {
                "id": "point-seed",
                "score": 0.72,
                "payload": {
                    "id": "seed-restaurant-cashflow",
                    "title": "餐饮门店现金流基础诊断",
                    "content": "餐饮门店诊断应优先核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。",
                    "source_url": "seed://v1/restaurant-cashflow",
                    "source_id": "seed-baseline",
                    "weight": 1.0,
                    "confidence": 0.9,
                    "industry_id": "general",
                    "region_id": "cn-default",
                    "entitlement": "FREE",
                },
            },
        ]
    )
    monkeypatch.setattr("app.main.build_vector_store", lambda collection_name=None: vector_store)

    response = search(
        SearchRequest(
            query="门店现金流怎么诊断",
            knowledge=[],
        )
    )

    assert [item["id"] for item in response["results"]] == ["seed-restaurant-cashflow"]


def test_diagnose_route_upserts_request_knowledge_before_vector_query(monkeypatch):
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-diagnose-match",
                "score": 0.81,
                "payload": make_item(
                    "diagnose-match",
                    title="门店现金流诊断",
                    content="平台佣金、回款周期和翻台率要一起判断。",
                    weight=0.96,
                    confidence=0.91,
                ).__dict__,
            }
        ]
    )
    monkeypatch.setattr("app.main.build_vector_store", lambda collection_name=None: vector_store)

    # Mock the LLM call so the strict-mode diagnose_endpoint doesn't return 503
    def fake_generate(question: str, context: str) -> str:
        return f"基于证据：{context[:80]}。建议：先核对关键经营变量。"

    monkeypatch.setattr("app.agent.generate_answer", fake_generate)

    response = diagnose_endpoint(
        DiagnoseRequest(
            question="门店现金流怎么诊断",
            region_id="cn-default",
            industry_id="general",
            membership_level="FREE",
            knowledge=[
                {
                    "id": "diagnose-match",
                    "title": "门店现金流诊断",
                    "content": "平台佣金、回款周期和翻台率要一起判断。",
                    "source_url": "seed://diagnose-match",
                    "source_id": "seed-baseline",
                    "weight": 0.96,
                    "confidence": 0.91,
                    "industry_id": "general",
                    "region_id": "cn-default",
                }
            ],
        )
    )

    assert [item.id for item in vector_store.upsert_calls[0]] == ["diagnose-match"]
    assert len(vector_store.search_calls) == 1
    assert [source["id"] for source in response["sources"]] == ["diagnose-match"]
    assert vector_store.deleted_collections == ["fake-collection"]


def test_parse_knowledge_generates_stable_fallback_id_for_missing_ids():
    from app.main import parse_knowledge

    parsed = parse_knowledge(
        [
            {
                "title": "No explicit id",
                "content": "Use content-derived id",
                "source_url": "seed://derived",
                "source_id": "seed-baseline",
            }
        ]
    )

    assert parsed[0].id != "None"
    assert parsed[0].id


def test_request_knowledge_uses_distinct_collection_names_for_same_explicit_id(monkeypatch):
    vector_store = FakeVectorStore([])
    collection_names: list[str | None] = []

    def fake_builder(collection_name=None):
        collection_names.append(collection_name)
        return vector_store

    monkeypatch.setattr("app.main.build_vector_store", fake_builder)

    search(
        SearchRequest(
            query="first",
            knowledge=[
                {
                    "id": "shared-id",
                    "title": "First payload",
                    "content": "First content",
                    "source_url": "seed://first",
                    "source_id": "seed-baseline",
                }
            ],
        )
    )
    search(
        SearchRequest(
            query="second",
            knowledge=[
                {
                    "id": "shared-id",
                    "title": "Second payload",
                    "content": "Second content",
                    "source_url": "seed://second",
                    "source_id": "seed-baseline",
                }
            ],
        )
    )

    assert len(collection_names) == 2
    assert collection_names[0] != collection_names[1]
    assert collection_names[0] is not None


def test_request_collection_cleanup_failure_does_not_break_search_route(monkeypatch):
    vector_store = FakeVectorStore(
        [
            {
                "id": "point-route-match",
                "score": 0.77,
                "payload": make_item(
                    "route-match",
                    title="鍖哄煙闂ㄥ簵鐜伴噾娴佽瘖鏂?",
                    content="骞冲彴浣ｉ噾鍜屽洖娆惧懆鏈熸槸浼樺厛鎸囨爣銆?",
                    weight=0.95,
                    confidence=0.9,
                ).__dict__,
            }
        ]
    )
    vector_store.fail_delete = True
    monkeypatch.setattr("app.main.build_vector_store", lambda collection_name=None: vector_store)

    response = search(
        SearchRequest(
            query="闂ㄥ簵鐜伴噾娴佹€庝箞璇婃柇",
            knowledge=[
                {
                    "id": "route-match",
                    "title": "鍖哄煙闂ㄥ簵鐜伴噾娴佽瘖鏂?",
                    "content": "骞冲彴浣ｉ噾鍜屽洖娆惧懆鏈熸槸浼樺厛鎸囨爣銆?",
                    "source_url": "seed://route-match",
                    "source_id": "seed-baseline",
                    "weight": 0.95,
                    "confidence": 0.9,
                    "industry_id": "general",
                    "region_id": "cn-default",
                }
            ],
        )
    )

    assert response["results"][0]["id"] == "route-match"
