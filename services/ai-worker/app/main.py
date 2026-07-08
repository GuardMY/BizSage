from __future__ import annotations

import hashlib
import os

from fastapi import FastAPI
from pydantic import BaseModel, Field
from qdrant_client import QdrantClient

from app.agent import diagnose
from app.agent_transition import AgentMode, execute_transition
from app.learning_agent import LearningMode, learn
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


class CompressConfigRequest(BaseModel):
    total_token_budget: int = 2400
    min_chars_per_item: int = 80
    max_chars_per_item: int = 1200
    merge_similarity_threshold: float = 0.70


class DiagnoseRequest(BaseModel):
    question: str
    knowledge: list[dict] = Field(default_factory=list)
    recent_messages: list[dict] = Field(default_factory=list)
    conversation_summary: str | None = None
    long_term_memories: list[dict] = Field(default_factory=list)
    region_id: str | None = None
    industry_id: str | None = None
    membership_level: str = "FREE"
    conflict_labels: list[str] = Field(default_factory=list)
    compress_config: CompressConfigRequest | None = None


class LearnRequest(BaseModel):
    question: str
    knowledge: list[dict] = Field(default_factory=list)
    chain_node_id: str | None = None
    learning_mode: str = LearningMode.FAST_START
    recent_messages: list[dict] = Field(default_factory=list)
    conversation_summary: str | None = None
    long_term_memories: list[dict] = Field(default_factory=list)
    region_id: str | None = None
    industry_id: str | None = None
    membership_level: str = "FREE"
    compress_config: CompressConfigRequest | None = None


class TransitionRequest(BaseModel):
    from_mode: str             # "LEARNING" | "DIAGNOSIS"
    to_mode: str               # "LEARNING" | "DIAGNOSIS"
    question: str
    chain_node_id: str | None = None
    knowledge: list[dict] = Field(default_factory=list)
    recent_messages: list[dict] = Field(default_factory=list)
    conversation_summary: str | None = None
    long_term_memories: list[dict] = Field(default_factory=list)
    region_id: str | None = None
    industry_id: str | None = None
    membership_level: str = "FREE"


class SyncRequest(BaseModel):
    """Request to sync knowledge into the persistent Qdrant collection."""
    knowledge: list[dict] = Field(default_factory=list)
    clear_before: bool = False  # When true, recreate the collection before upserting


class MemorySyncRequest(BaseModel):
    """Request to sync memory embeddings to Qdrant.

    Used by the API layer to push unstructured user memories from the
    user_memory_embeddings table into the vector store for semantic search.
    """
    memories: list[dict] = Field(default_factory=list)
    collection: str = "bizsage_memory"


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


@app.post("/knowledge/sync")
def sync_knowledge(request: SyncRequest) -> dict:
    """Upsert knowledge items into the persistent Qdrant collection.

    Used by the API layer to keep the vector store in sync with the
    authoritative knowledge base (MySQL `knowledge_items` table).
    When `clear_before` is true, the collection is recreated before
    upserting (used for full-resync on startup).
    """
    knowledge = parse_knowledge(request.knowledge)
    if not knowledge:
        return {"synced": 0, "message": "No knowledge items to sync"}

    vector_store = build_vector_store()

    if request.clear_before:
        try:
            vector_store.client.delete_collection(vector_store.collection_name)
        except Exception:  # noqa: BLE001
            pass
        vector_store._ensure_collection()

    vector_store.upsert_knowledge(knowledge)
    return {
        "synced": len(knowledge),
        "collection": vector_store.collection_name,
        "message": f"Synced {len(knowledge)} knowledge items to {vector_store.collection_name}",
    }


@app.post("/knowledge/delete")
def delete_knowledge(knowledge_ids: list[str]) -> dict:
    """Delete knowledge items from the persistent Qdrant collection by ID.

    Used when knowledge is unpublished or superseded.
    """
    if not knowledge_ids:
        return {"deleted": 0, "message": "No IDs provided"}

    import uuid as _uuid

    vector_store = build_vector_store()
    point_ids = [str(_uuid.uuid5(_uuid.NAMESPACE_URL, kid)) for kid in knowledge_ids]

    try:
        vector_store.client.delete(
            vector_store.collection_name,
            points_selector=point_ids,
        )
    except Exception as ex:  # noqa: BLE001
        return {"deleted": 0, "message": f"Delete failed: {ex}"}

    return {
        "deleted": len(knowledge_ids),
        "collection": vector_store.collection_name,
        "message": f"Deleted {len(knowledge_ids)} items from {vector_store.collection_name}",
    }


@app.post("/agent/diagnose")
def diagnose_endpoint(request: DiagnoseRequest) -> dict:
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))
    try:
        vector_store.upsert_knowledge(knowledge)
        from app.context_compressor import CompressConfig

        compress_cfg = None
        if request.compress_config is not None:
            compress_cfg = CompressConfig(
                total_token_budget=request.compress_config.total_token_budget,
                min_chars_per_item=request.compress_config.min_chars_per_item,
                max_chars_per_item=request.compress_config.max_chars_per_item,
                merge_similarity_threshold=request.compress_config.merge_similarity_threshold,
            )

        result = diagnose(
            request.question,
            knowledge=knowledge,
            recent_messages=request.recent_messages,
            conversation_summary=request.conversation_summary,
            long_term_memories=request.long_term_memories,
            region_id=request.region_id,
            industry_id=request.industry_id,
            membership_level=request.membership_level,
            conflict_labels=request.conflict_labels,
            vector_store=vector_store,
            restrict_to_knowledge_ids=True,
            compress_config=compress_cfg,
        )

        llm_err = _check_llm_error(result)
        if llm_err is not None:
            return llm_err
        return result
    finally:
        cleanup_request_collection(vector_store, request_knowledge)


@app.post("/agent/learn")
def learn_endpoint(request: LearnRequest) -> dict:
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))
    try:
        vector_store.upsert_knowledge(knowledge)

        from app.context_compressor import CompressConfig

        compress_cfg = None
        if request.compress_config is not None:
            compress_cfg = CompressConfig(
                total_token_budget=request.compress_config.total_token_budget,
                min_chars_per_item=request.compress_config.min_chars_per_item,
                max_chars_per_item=request.compress_config.max_chars_per_item,
                merge_similarity_threshold=request.compress_config.merge_similarity_threshold,
            )

        result = learn(
            request.question,
            knowledge=knowledge,
            chain_node_id=request.chain_node_id,
            learning_mode=request.learning_mode,
            recent_messages=request.recent_messages,
            conversation_summary=request.conversation_summary,
            long_term_memories=request.long_term_memories,
            region_id=request.region_id,
            industry_id=request.industry_id,
            membership_level=request.membership_level,
            vector_store=vector_store,
            restrict_to_knowledge_ids=True,
            compress_config=compress_cfg,
        )

        llm_err = _check_llm_error(result)
        if llm_err is not None:
            return llm_err
        return result
    finally:
        cleanup_request_collection(vector_store, request_knowledge)


@app.post("/agent/transition")
def transition_endpoint(request: TransitionRequest) -> dict:
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))
    try:
        vector_store.upsert_knowledge(knowledge)

        result = execute_transition(
            from_mode=request.from_mode,
            to_mode=request.to_mode,
            user_question=request.question,
            knowledge=knowledge,
            chain_node_id=request.chain_node_id,
            recent_messages=request.recent_messages,
            conversation_summary=request.conversation_summary,
            long_term_memories=request.long_term_memories,
            region_id=request.region_id,
            industry_id=request.industry_id,
            membership_level=request.membership_level,
            vector_store=vector_store,
            restrict_to_knowledge_ids=True,
        )

        llm_err = _check_llm_error(result)
        if llm_err is not None:
            return llm_err
        return result
    finally:
        cleanup_request_collection(vector_store, request_knowledge)


@app.post("/memory/sync")
def sync_memory_embeddings(request: MemorySyncRequest) -> dict:
    """Sync user memory embeddings to Qdrant.

    Receives PENDING memory embedding records from the Java API layer,
    computes embeddings via embed_text(), and upserts them to the
    bizsage_memory Qdrant collection.

    Returns the qdrant_point_id for each synced memory so the API layer
    can update the embedding_status to SYNCED.
    """
    if not request.memories:
        return {"synced": 0, "results": [], "message": "No memories to sync"}

    import uuid as _uuid

    from app.embeddings import embed_text

    memory_vs = QdrantVectorStore(
        QdrantClient(url=os.getenv("QDRANT_URL", "http://localhost:16333")),
        collection_name=request.collection,
    )

    results: list[dict] = []
    points = []
    for mem in request.memories:
        memory_id = mem.get("id")
        memory_text = mem.get("memory_text", "").strip()
        if not memory_text:
            continue

        point_id = str(_uuid.uuid5(_uuid.NAMESPACE_URL, f"mem-{memory_id}"))
        vector = embed_text(memory_text, dimensions=memory_vs.dimensions)
        points.append({
            "id": point_id,
            "vector": vector,
            "payload": {
                "user_memory_embedding_id": memory_id,
                "user_id": mem.get("user_id"),
                "memory_text": memory_text,
                "source_conversation_id": mem.get("source_conversation_id"),
            },
        })
        results.append({
            "embedding_id": memory_id,
            "qdrant_point_id": point_id,
            "status": "SYNCED",
        })

    if points:
        from qdrant_client import models as qmodels
        memory_vs.client.upsert(
            memory_vs.collection_name,
            [
                qmodels.PointStruct(
                    id=p["id"],
                    vector=p["vector"],
                    payload=p["payload"],
                )
                for p in points
            ],
        )

    return {
        "synced": len(results),
        "results": results,
        "collection": request.collection,
        "message": f"Synced {len(results)} memory embeddings to {request.collection}",
    }


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
            link_id=item.get("link_id") or item.get("linkId") or "",
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


def _check_llm_error(result: dict):
    """Return a 503 JSONResponse if the LLM is not configured or the call failed, else None."""
    from app.agent import LLM_NOT_CONFIGURED, LLM_CALL_FAILED
    from fastapi.responses import JSONResponse

    status = result.get("selfCheckStatus", "")
    if status == LLM_NOT_CONFIGURED:
        return JSONResponse(status_code=503, content={
            "error": "LLM_NOT_CONFIGURED",
            "message": "OPENAI_COMPATIBLE_API_KEY is not set. The AI worker cannot generate answers without a configured LLM.",
        })
    if status == LLM_CALL_FAILED:
        return JSONResponse(status_code=503, content={
            "error": "LLM_CALL_FAILED",
            "message": "The upstream LLM provider returned an error or empty response.",
        })
    return None


def cleanup_request_collection(vector_store: QdrantVectorStore, request_knowledge: list[KnowledgeItem]) -> None:
    if not request_knowledge:
        return
    try:
        vector_store.client.delete_collection(vector_store.collection_name)
    except Exception:  # noqa: BLE001 - Cleanup should not break the request path.
        pass
