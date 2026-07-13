from __future__ import annotations

"""AI Worker HTTP 入口。

该服务对 API 层暴露诊断、学习、模式切换、知识同步和记忆向量同步接口。
请求级知识会写入临时 Qdrant 集合并在请求结束后清理；持久知识同步则写入
默认集合，作为线上 RAG 的长期语义索引。
"""

import hashlib
import json
import os
from collections.abc import Iterator

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field
from qdrant_client import QdrantClient

from app.agent import diagnose, diagnose_stream
from app.agent_transition import AgentMode, execute_transition, execute_transition_stream
from app.learning_agent import LearningMode, learn, learn_stream
from app.llm import LLMCallError, LLMNotConfiguredError
from app.rag import KnowledgeItem, search_knowledge
from app.vector_store import QdrantVectorStore

app = FastAPI(title="BizSage AI Worker", version="0.1.0")

# 开发和空知识库场景的兜底基线知识，保证本地环境仍可验证 RAG 流程。
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


class IndustrySearchRequest(BaseModel):
    query: str
    industries: list[dict] = Field(default_factory=list)


class CompressConfigRequest(BaseModel):
    total_token_budget: int = 2400
    min_chars_per_item: int = 80
    max_chars_per_item: int = 1200
    merge_similarity_threshold: float = 0.70


class DiagnoseRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=lambda value: value.split("_")[0] + "".join(part.title() for part in value.split("_")[1:]),
        populate_by_name=True,
    )
    question: str
    knowledge: list[dict] = Field(default_factory=list)
    recent_messages: list[dict] = Field(default_factory=list)
    conversation_summary: str | None = None
    long_term_memories: list[dict] = Field(default_factory=list)
    diagnosis_memories: list[dict] = Field(default_factory=list)
    diagnosis_completeness: float | None = None
    diagnosis_missing_fields: list[str] = Field(default_factory=list)
    profile_missing_fields_for_report: list[str] = Field(default_factory=list)
    additional_information_questions: list[dict] = Field(default_factory=list)
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
    """同步知识到持久 Qdrant 集合的请求。"""
    knowledge: list[dict] = Field(default_factory=list)
    clear_before: bool = False  # 为 true 时先重建集合，通常用于启动时全量重刷。


class MemorySyncRequest(BaseModel):
    """同步用户记忆向量到 Qdrant 的请求。

    API 层会把 user_memory_embeddings 表里待同步的非结构化记忆推送过来，
    Worker 负责计算向量并写入记忆集合，供后续语义检索使用。
    """
    memories: list[dict] = Field(default_factory=list)
    collection: str = "bizsage_memory"


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


def build_vector_store(collection_name: str | None = None) -> QdrantVectorStore:
    """创建 Qdrant 存储句柄；不传集合名时使用持久知识集合。"""
    base_collection = os.getenv("QDRANT_COLLECTION", "bizsage_knowledge")
    return QdrantVectorStore(
        QdrantClient(url=os.getenv("QDRANT_URL", "http://localhost:16333")),
        collection_name=collection_name or base_collection,
    )


@app.post("/rag/search")
def search(request: SearchRequest) -> dict:
    """调试/验证用 RAG 搜索接口，使用请求级临时集合隔离传入知识。"""
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


@app.post("/industry/search")
def search_industries(request: IndustrySearchRequest) -> dict:
    """Use the persistent Qdrant industry collection for semantic industry lookup."""
    if not request.query.strip() or not request.industries:
        return {"results": []}

    industry_items = [
        KnowledgeItem(
            id=f"industry-{item.get('id')}",
            title=str(item.get("name") or item.get("id") or ""),
            content=str(item.get("content") or item.get("name") or ""),
            source_url="",
            source_id="industry-catalog",
            weight=1.0,
            confidence=1.0,
            industry_id=str(item.get("id") or ""),
            region_id="cn-default",
        )
        for item in request.industries
        if item.get("id") and item.get("name")
    ]
    vector_store = build_vector_store("bizsage_industries")
    vector_store.upsert_knowledge(industry_items)
    results = search_knowledge(
        request.query,
        industry_items,
        limit=20,
        vector_store=vector_store,
        restrict_to_knowledge_ids=True,
    )
    return {
        "results": [
            {"industry_id": item.id.removeprefix("industry-"),
             "industry_name": item.title, "score": item.score}
            for item in results
        ]
    }


@app.post("/knowledge/sync")
def sync_knowledge(request: SyncRequest) -> dict:
    """将权威知识库条目写入持久 Qdrant 集合。

    API 层用它同步 MySQL knowledge_items 表；clear_before 为 true 时先删后建，
    适用于服务启动或人工触发的全量重建。
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
    """按业务知识 ID 删除持久 Qdrant 集合中的点。

    当知识被下架或被新版本替代时调用；点 ID 与写入时保持同一 uuid5 规则。
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
    """诊断接口：同步请求知识、调用诊断 Agent，并统一转换 LLM 配置错误。"""
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
            diagnosis_memories=request.diagnosis_memories,
            diagnosis_completeness=request.diagnosis_completeness,
            diagnosis_missing_fields=request.diagnosis_missing_fields,
            profile_missing_fields_for_report=request.profile_missing_fields_for_report,
            additional_information_questions=request.additional_information_questions,
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


@app.post("/agent/diagnose/stream")
def diagnose_stream_endpoint(request: DiagnoseRequest) -> StreamingResponse:
    """真实流式诊断端点：模型 delta、自检 reset 和最终结果按 SSE 输出。"""
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))

    def events() -> Iterator[str]:
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
            yield from _encode_agent_events(diagnose_stream(
                request.question,
                knowledge=knowledge,
                recent_messages=request.recent_messages,
                conversation_summary=request.conversation_summary,
                long_term_memories=request.long_term_memories,
                diagnosis_memories=request.diagnosis_memories,
                diagnosis_completeness=request.diagnosis_completeness,
                diagnosis_missing_fields=request.diagnosis_missing_fields,
                profile_missing_fields_for_report=request.profile_missing_fields_for_report,
                additional_information_questions=request.additional_information_questions,
                region_id=request.region_id,
                industry_id=request.industry_id,
                membership_level=request.membership_level,
                conflict_labels=request.conflict_labels,
                vector_store=vector_store,
                restrict_to_knowledge_ids=True,
                compress_config=compress_cfg,
            ))
        finally:
            cleanup_request_collection(vector_store, request_knowledge)

    return _agent_stream_response(events())


@app.post("/agent/learn")
def learn_endpoint(request: LearnRequest) -> dict:
    """学习接口：按学习模式和链条节点执行 RAG + 学习 Agent。"""
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


@app.post("/agent/learn/stream")
def learn_stream_endpoint(request: LearnRequest) -> StreamingResponse:
    """真实流式学习端点。"""
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))

    def events() -> Iterator[str]:
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
            yield from _encode_agent_events(learn_stream(
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
            ))
        finally:
            cleanup_request_collection(vector_store, request_knowledge)

    return _agent_stream_response(events())


@app.post("/agent/transition")
def transition_endpoint(request: TransitionRequest) -> dict:
    """双 Agent 模式切换接口，保留上下文后路由到目标 Agent。"""
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


@app.post("/agent/transition/stream")
def transition_stream_endpoint(request: TransitionRequest) -> StreamingResponse:
    """真实流式双 Agent 模式切换端点。"""
    request_knowledge = parse_knowledge(request.knowledge)
    knowledge = request_knowledge or SEED_KNOWLEDGE
    vector_store = build_vector_store(build_request_collection_name(request_knowledge))

    def events() -> Iterator[str]:
        try:
            vector_store.upsert_knowledge(knowledge)
            yield from _encode_agent_events(execute_transition_stream(
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
            ))
        finally:
            cleanup_request_collection(vector_store, request_knowledge)

    return _agent_stream_response(events())


@app.post("/memory/sync")
def sync_memory_embeddings(request: MemorySyncRequest) -> dict:
    """同步用户长期记忆向量到 Qdrant。

    接收 Java API 层发送的 PENDING 记忆记录，使用 embed_text() 计算向量后
    写入 bizsage_memory 集合。

    返回每条记忆对应的 qdrant_point_id，供 API 层把 embedding_status 更新为 SYNCED。
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
    """把 API/前端传入的字典规范化为 RAG 使用的 KnowledgeItem。"""
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
    """优先使用传入 ID；缺失时用来源、标题和正文生成稳定指纹。"""
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
    """为请求级知识生成临时集合名，避免不同请求的向量互相污染。"""
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
    """把 Agent 返回的 LLM 错误状态转换成 HTTP 503；正常结果返回 None。"""
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


def _encode_agent_events(events: Iterator[dict]) -> Iterator[str]:
    """Convert internal Agent events to the Worker-to-API SSE protocol."""
    try:
        for item in events:
            event_name = item["event"]
            data = item["result"] if event_name == "result" else {
                key: value for key, value in item.items() if key != "event"
            }
            yield _sse(event_name, data)
    except LLMNotConfiguredError:
        yield _sse("error", {
            "error": "LLM_NOT_CONFIGURED",
            "message": "The AI engine is not configured.",
        })
    except LLMCallError:
        yield _sse("error", {
            "error": "LLM_CALL_FAILED",
            "message": "The upstream model call failed.",
        })
    except Exception:
        yield _sse("error", {
            "error": "WORKER_ERROR",
            "message": "The AI worker could not complete the stream.",
        })


def _sse(event_name: str, data: dict) -> str:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event_name}\ndata: {payload}\n\n"


def _agent_stream_response(events: Iterator[str]) -> StreamingResponse:
    return StreamingResponse(
        events,
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


def cleanup_request_collection(vector_store: QdrantVectorStore, request_knowledge: list[KnowledgeItem]) -> None:
    """清理请求级临时集合；清理失败不影响本次业务响应。"""
    if not request_knowledge:
        return
    try:
        vector_store.client.delete_collection(vector_store.collection_name)
    except Exception:  # noqa: BLE001 - Cleanup should not break the request path.
        pass
