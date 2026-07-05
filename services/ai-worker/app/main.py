from __future__ import annotations

import hashlib
import os

from fastapi import FastAPI
from pydantic import BaseModel, Field
from qdrant_client import QdrantClient

from app.agent import diagnose
from app.rag import KnowledgeItem, search_knowledge
from app.vector_store import QdrantVectorStore

app = FastAPI(title="BizSage AI Worker", version="0.1.0")

SEED_KNOWLEDGE = [
    KnowledgeItem(
        id="seed-restaurant-cashflow",
        title="餐饮门店现金流基础诊断",
        content="餐饮门店诊断应优先核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。",
        source_url="seed://v1/restaurant-cashflow",
        source_id="seed-baseline",
        weight=1.0,
        confidence=0.9,
        industry_id="general",
        region_id="cn-default",
    ),
    KnowledgeItem(
        id="seed-inventory-risk",
        title="实体供应链库存风险",
        content="库存周转天数、滞销库存占比和上游账期会共同影响现金流风险。",
        source_url="seed://v1/inventory-risk",
        source_id="seed-baseline",
        weight=0.85,
        confidence=0.85,
        industry_id="general",
        region_id="cn-default",
    ),
]


class SearchRequest(BaseModel):
    query: str
    knowledge: list[dict] = Field(default_factory=list)
    region_id: str | None = None
    industry_id: str | None = None
    membership_level: str = "FREE"


class DiagnoseRequest(BaseModel):
    question: str
    knowledge: list[dict] = Field(default_factory=list)
    region_id: str | None = None
    industry_id: str | None = None
    membership_level: str = "FREE"
    conflict_labels: list[str] = Field(default_factory=list)


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


def build_vector_store(collection_name: str | None = None) -> QdrantVectorStore:
    base_collection = os.getenv("QDRANT_COLLECTION", "bizsage_knowledge")
    return QdrantVectorStore(
        QdrantClient(url=os.getenv("QDRANT_URL", "http://localhost:16333")),
        collection_name=collection_name or base_collection,
    )


@app.post("/rag/search")
def search(request: SearchRequest) -> dict:
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))
    try:
        vector_store.upsert_knowledge(knowledge)
        return {
            "results": [
                result.__dict__
                for result in search_knowledge(
                    request.query,
                    knowledge,
                    region_id=request.region_id,
                    industry_id=request.industry_id,
                    membership_level=request.membership_level,
                    vector_store=vector_store,
                    restrict_to_knowledge_ids=True,
                )
            ]
        }
    finally:
        cleanup_request_collection(vector_store, request_knowledge)


@app.post("/agent/diagnose")
def diagnose_endpoint(request: DiagnoseRequest) -> dict:
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))
    try:
        vector_store.upsert_knowledge(knowledge)
        return diagnose(
            request.question,
            knowledge=knowledge,
            region_id=request.region_id,
            industry_id=request.industry_id,
            membership_level=request.membership_level,
            conflict_labels=request.conflict_labels,
            vector_store=vector_store,
            restrict_to_knowledge_ids=True,
        )
    finally:
        cleanup_request_collection(vector_store, request_knowledge)


def parse_knowledge(items: list[dict]) -> list[KnowledgeItem]:
    return [
        KnowledgeItem(
            id=resolve_knowledge_id(item),
            title=item["title"],
            content=item["content"],
            source_url=item.get("source_url") or item.get("sourceUrl") or "",
            source_id=item.get("source_id") or item.get("sourceId") or "unknown",
            weight=float(item.get("weight", 0.85)),
            confidence=float(item.get("confidence", 0.85)),
            industry_id=item.get("industry_id") or item.get("industryId") or "general",
            region_id=item.get("region_id") or item.get("regionId") or "cn-default",
            entitlement=item.get("entitlement", "FREE"),
            review_confidence=float(item.get("review_confidence", item.get("reviewConfidence", 0.85))),
            historical_quality=float(item.get("historical_quality", item.get("historicalQuality", 0.85))),
        )
        for item in items
    ]


def resolve_knowledge_id(item: dict) -> str:
    raw_id = item.get("id")
    if raw_id not in {None, ""}:
        return str(raw_id)

    fingerprint_source = "|".join(
        [
            str(item.get("source_url") or item.get("sourceUrl") or ""),
            str(item.get("source_id") or item.get("sourceId") or ""),
            str(item.get("title", "")),
            str(item.get("content", "")),
        ]
    )
    return hashlib.sha256(fingerprint_source.encode("utf-8")).hexdigest()


def build_request_collection_name(knowledge: list[KnowledgeItem]) -> str | None:
    if not knowledge:
        return None

    base_collection = os.getenv("QDRANT_COLLECTION", "bizsage_knowledge")
    fingerprint = hashlib.sha256(
        "|".join(
            f"{item.id}:{item.source_id}:{item.source_url}:{item.title}:{item.content}"
            for item in knowledge
        ).encode("utf-8")
    ).hexdigest()[:16]
    return f"{base_collection}_{fingerprint}"


def cleanup_request_collection(vector_store: QdrantVectorStore, request_knowledge: list[KnowledgeItem]) -> None:
    if not request_knowledge:
        return
    try:
        vector_store.client.delete_collection(vector_store.collection_name)
    except Exception:  # noqa: BLE001 - Cleanup should not break the request path.
        pass
