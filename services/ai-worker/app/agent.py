from __future__ import annotations

"""经营诊断 Agent 主流程。

本模块只负责把证据、记忆和提示词组装成一次严格的诊断调用；会话落库、
用户记忆持久化和 SSE 输出由 Java API 层负责。诊断失败时显式返回错误状态，
避免在业务决策场景里悄悄退回到 mock 或本地模板答案。
"""

from app.context_compressor import CompressConfig, compress_context
from app.llm import generate_answer, LLMNotConfiguredError, LLMCallError
from app.memory import build_memory_context, extract_diagnosis_memories
from app.model_routing.router import ModelRouter
from app.prompt_library.assembler import PromptAssembler
from app.prompt_library.layers import AgentMode
from app.rag import KnowledgeItem, search_knowledge
from collections.abc import Iterator

from app.reasoning_checks.retry import run_with_retry, run_with_retry_stream

DISCLAIMER = "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。"
TIMELINESS = "基于V1静态基线知识和已入库情报生成。"

LLM_NOT_CONFIGURED = "LLM_NOT_CONFIGURED"
LLM_CALL_FAILED = "LLM_CALL_FAILED"

# 懒加载单例：FastAPI 进程内复用提示词装配器和模型路由器，避免每次请求重复构建。
_assembler: PromptAssembler | None = None
_router: ModelRouter | None = None


def _get_assembler() -> PromptAssembler:
    global _assembler
    if _assembler is None:
        _assembler = PromptAssembler()
    return _assembler


def _get_router() -> ModelRouter:
    global _router
    if _router is None:
        _router = ModelRouter()
    return _router


def diagnose(
    question: str,
    *,
    knowledge: list[KnowledgeItem],
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    conflict_labels: list[str] | None = None,
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
    compress_config: CompressConfig | None = None,
) -> dict:
    """基于知识库证据、会话记忆和用户问题生成经营诊断结果。"""
    # 冲突标签由上游治理链路给出；一旦存在冲突，先交给运营复核，避免输出不可靠结论。
    if conflict_labels:
        return {
            "mode": "DIAGNOSIS",
            "answer": "当前信息存在冲突或缺少权威支撑，需要运营复核后再给出结论。",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": "NEEDS_REVIEW",
            "disclaimer": DISCLAIMER,
        }

    # 先检索可追溯证据，再允许 LLM 生成；没有证据时直接返回“信息不足”。
    results = search_knowledge(
        question,
        knowledge,
        region_id=region_id,
        industry_id=industry_id,
        membership_level=membership_level,
        vector_store=vector_store,
        restrict_to_knowledge_ids=restrict_to_knowledge_ids,
    )
    if not results:
        return {
            "mode": "DIAGNOSIS",
            "answer": "信息不足：当前知识库没有检索到可支撑该问题的证据，请补充行业、地域或经营数据后重试。",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": "INSUFFICIENT_EVIDENCE",
            "disclaimer": DISCLAIMER,
        }

    # 将 RAG 命中的证据压缩到模型上下文预算内，同时保留消息摘要和长期记忆。
    result_dicts = [
        {
            "id": item.id,
            "title": item.title,
            "content": item.content,
            "source_url": item.source_url,
            "source_id": item.source_id,
            "score": item.score,
        }
        for item in results
    ]
    memory_context = build_memory_context(recent_messages, conversation_summary, long_term_memories)
    compress_cfg = compress_config or CompressConfig(
        total_token_budget=2100 if memory_context else 2400,
    )
    compressed_text, compressed_items = compress_context(result_dicts, config=compress_cfg)
    if memory_context:
        context = f"{memory_context}\n\n{compressed_text}"
    else:
        context = compressed_text

    # 使用分层提示词库生成系统提示词，保证诊断模式、地域和行业约束一致。
    assembler = _get_assembler()
    system_prompt = assembler.assemble(
        mode=AgentMode.DIAGNOSIS.value,
        industry_id=industry_id,
        region_id=region_id,
    )

    # 通过 ModelRouter 调用模型，并在自检失败时带反馈重试。
    router = _get_router()

    def llm_call(messages: list[dict]) -> str:
        """供自检重试器调用的薄包装：隐藏路由细节，只返回模型正文。"""
        result = router.call(
            messages=messages,
            task_hint="balanced",
            temperature=0.3,
            max_tokens=1024,
        )
        return result.response_text

    initial_messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"问题：{question}\n\n参考证据：\n{context}"},
    ]

    try:
        answer, check_status = run_with_retry(
            llm_call_fn=llm_call,
            messages=initial_messages,
            evidence=result_dicts,
            region_id=region_id,
            output_format=system_prompt,
        )
    except LLMNotConfiguredError:
        return {
            "mode": "DIAGNOSIS",
            "answer": "",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": LLM_NOT_CONFIGURED,
            "disclaimer": DISCLAIMER,
        }
    except LLMCallError:
        return {
            "mode": "DIAGNOSIS",
            "answer": "",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": LLM_CALL_FAILED,
            "disclaimer": DISCLAIMER,
        }

    return {
        "mode": "DIAGNOSIS",
        "answer": answer,
        "sources": [
            {
                "id": item.id,
                "title": item.title,
                "sourceUrl": item.source_url,
                "sourceId": item.source_id,
                "confidence": item.confidence,
                "score": item.score,
                "entitlement": item.entitlement,
            }
            for item in results
        ],
        "confidence": "MEDIUM",
        "timeliness": TIMELINESS,
        "selfCheckStatus": check_status,
        "disclaimer": DISCLAIMER,
        "memoryCandidates": extract_diagnosis_memories(question, answer, existing_memories=long_term_memories),
    }


def diagnose_stream(
    question: str,
    *,
    knowledge: list[KnowledgeItem],
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    conflict_labels: list[str] | None = None,
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
    compress_config: CompressConfig | None = None,
) -> Iterator[dict]:
    """Yield diagnosis deltas and one terminal result event."""
    if conflict_labels:
        result = {
            "mode": "DIAGNOSIS",
            "answer": "当前信息存在冲突或缺少权威支撑，需要运营复核后再给出结论。",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": "NEEDS_REVIEW",
            "disclaimer": DISCLAIMER,
        }
        yield {"event": "delta", "text": result["answer"], "attempt": 1}
        yield {"event": "result", "result": result, "attempt": 1}
        return

    results = search_knowledge(
        question,
        knowledge,
        region_id=region_id,
        industry_id=industry_id,
        membership_level=membership_level,
        vector_store=vector_store,
        restrict_to_knowledge_ids=restrict_to_knowledge_ids,
    )
    if not results:
        result = {
            "mode": "DIAGNOSIS",
            "answer": "信息不足：当前知识库没有检索到可支撑该问题的证据，请补充行业、地域或经营数据后重试。",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": "INSUFFICIENT_EVIDENCE",
            "disclaimer": DISCLAIMER,
        }
        yield {"event": "delta", "text": result["answer"], "attempt": 1}
        yield {"event": "result", "result": result, "attempt": 1}
        return

    result_dicts = [
        {
            "id": item.id,
            "title": item.title,
            "content": item.content,
            "source_url": item.source_url,
            "source_id": item.source_id,
            "score": item.score,
        }
        for item in results
    ]
    memory_context = build_memory_context(
        recent_messages, conversation_summary, long_term_memories
    )
    compress_cfg = compress_config or CompressConfig(
        total_token_budget=2100 if memory_context else 2400,
    )
    compressed_text, _ = compress_context(result_dicts, config=compress_cfg)
    context = f"{memory_context}\n\n{compressed_text}" if memory_context else compressed_text
    system_prompt = _get_assembler().assemble(
        mode=AgentMode.DIAGNOSIS.value,
        industry_id=industry_id,
        region_id=region_id,
    )
    initial_messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"问题：{question}\n\n参考证据：\n{context}"},
    ]
    router = _get_router()

    def llm_stream(messages: list[dict]) -> Iterator[dict]:
        yield from router.stream(
            messages=messages,
            task_hint="balanced",
            temperature=0.3,
            max_tokens=1024,
        )

    for event in run_with_retry_stream(
        llm_stream_fn=llm_stream,
        messages=initial_messages,
        evidence=result_dicts,
        region_id=region_id,
        output_format=system_prompt,
    ):
        if event["event"] != "result":
            yield event
            continue

        answer = event["answer"]
        result = {
            "mode": "DIAGNOSIS",
            "answer": answer,
            "sources": [
                {
                    "id": item.id,
                    "title": item.title,
                    "sourceUrl": item.source_url,
                    "sourceId": item.source_id,
                    "confidence": item.confidence,
                    "score": item.score,
                    "entitlement": item.entitlement,
                }
                for item in results
            ],
            "confidence": "MEDIUM",
            "timeliness": TIMELINESS,
            "selfCheckStatus": event["selfCheckStatus"],
            "disclaimer": DISCLAIMER,
            "memoryCandidates": extract_diagnosis_memories(
                question, answer, existing_memories=long_term_memories
            ),
        }
        yield {"event": "result", "result": result, "attempt": event["attempt"]}
        return
