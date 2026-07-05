from types import SimpleNamespace

from app.embeddings import embed_text
from app.rag import KnowledgeItem
from app.vector_store import QdrantVectorStore


class FakeClient:
    def __init__(self) -> None:
        self.collection_exists_calls: list[str] = []
        self.create_collection_calls: list[tuple[str, object]] = []
        self.upsert_calls: list[tuple[str, list[object]]] = []
        self.query_points_calls: list[dict] = []
        self.exists = False
        self.next_points: list[object] = []

    def collection_exists(self, collection_name: str) -> bool:
        self.collection_exists_calls.append(collection_name)
        return self.exists

    def create_collection(self, collection_name: str, vectors_config: object) -> None:
        self.create_collection_calls.append((collection_name, vectors_config))
        self.exists = True

    def upsert(self, collection_name: str, points: list[object]) -> None:
        self.upsert_calls.append((collection_name, points))

    def query_points(
        self,
        *,
        collection_name: str,
        query: list[float],
        limit: int,
        with_payload: bool,
    ) -> SimpleNamespace:
        self.query_points_calls.append(
            {
                "collection_name": collection_name,
                "query": query,
                "limit": limit,
                "with_payload": with_payload,
            }
        )
        return SimpleNamespace(points=self.next_points)


def make_item(item_id: str) -> KnowledgeItem:
    return KnowledgeItem(
        id=item_id,
        title="库存周转风险",
        content="库存周转天数偏高会直接压占现金流并放大滞销风险。",
        source_url=f"seed://{item_id}",
        source_id="seed-baseline",
        weight=0.9,
        confidence=0.8,
        industry_id="general",
        region_id="cn-default",
        entitlement="FREE",
        review_confidence=0.88,
        historical_quality=0.87,
    )


def test_embed_text_is_deterministic_and_dimensioned():
    left = embed_text("库存周转风险", dimensions=8)
    right = embed_text("库存周转风险", dimensions=8)
    other = embed_text("门店客流分析", dimensions=8)

    assert len(left) == 8
    assert left == right
    assert left != other


def test_vector_store_upsert_serializes_knowledge_item_payloads():
    client = FakeClient()
    store = QdrantVectorStore(client, collection_name="knowledge-test", dimensions=8)
    item = make_item("inventory-risk")

    store.upsert_knowledge([item])

    assert client.collection_exists_calls == ["knowledge-test"]
    assert len(client.create_collection_calls) == 1
    assert len(client.upsert_calls) == 1
    collection_name, points = client.upsert_calls[0]
    assert collection_name == "knowledge-test"
    assert len(points) == 1
    assert points[0].payload == {
        "id": "inventory-risk",
        "title": "库存周转风险",
        "content": "库存周转天数偏高会直接压占现金流并放大滞销风险。",
        "source_url": "seed://inventory-risk",
        "source_id": "seed-baseline",
        "weight": 0.9,
        "confidence": 0.8,
        "industry_id": "general",
        "region_id": "cn-default",
        "entitlement": "FREE",
        "review_confidence": 0.88,
        "historical_quality": 0.87,
    }


def test_vector_store_search_returns_raw_candidate_hits():
    client = FakeClient()
    client.next_points = [
        SimpleNamespace(
            id="point-1",
            score=0.91,
            payload={"id": "inventory-risk", "title": "库存周转风险"},
        ),
        SimpleNamespace(
            id="point-2",
            score=0.55,
            payload={"id": "menu-design", "title": "菜单设计建议"},
        ),
    ]
    store = QdrantVectorStore(client, collection_name="knowledge-test", dimensions=8)
    query_vector = embed_text("库存现金流风险", dimensions=8)

    results = store.search(query_vector, limit=2)

    assert client.query_points_calls == [
        {
            "collection_name": "knowledge-test",
            "query": query_vector,
            "limit": 2,
            "with_payload": True,
        }
    ]
    assert results == [
        {
            "id": "point-1",
            "score": 0.91,
            "payload": {"id": "inventory-risk", "title": "库存周转风险"},
        },
        {
            "id": "point-2",
            "score": 0.55,
            "payload": {"id": "menu-design", "title": "菜单设计建议"},
        },
    ]
