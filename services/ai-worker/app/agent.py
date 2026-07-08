from __future__ import annotations

from app.context_compressor import CompressConfig, compress_context
from app.llm import generate_answer, LLMNotConfiguredError, LLMCallError
from app.memory import build_memory_context, extract_diagnosis_memories
from app.model_routing.router import ModelRouter
from app.prompt_library.assembler import PromptAssembler
from app.prompt_library.layers import AgentMode
from app.rag import KnowledgeItem, search_knowledge
from app.reasoning_checks.retry import run_with_retry

DISCLAIMER = "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。"
TIMELINESS = "基于V1静态基线知识和已入库情报生成。"

LLM_NOT_CONFIGURED = "LLM_NOT_CONFIGURED"
LLM_CALL_FAILED = "LLM_CALL_FAILED"

# ── Lazy-initialized singletons ──
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
    # ── Conflict pre-check ──
    if conflict_labels:
        return {
            "answer": "当前信息存在冲突或缺少权威支撑，需要运营复核后再给出结论。",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": "NEEDS_REVIEW",
            "disclaimer": DISCLAIMER,
        }

    # ── RAG search ──
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
            "answer": "信息不足：当前知识库没有检索到可支撑该问题的证据，请补充行业、地域或经营数据后重试。",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": "INSUFFICIENT_EVIDENCE",
            "disclaimer": DISCLAIMER,
        }

    # ── Context compression ──
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

    # ── V2: Build system prompt from layered prompt library ──
    assembler = _get_assembler()
    system_prompt = assembler.assemble(
        mode=AgentMode.DIAGNOSIS.value,
        industry_id=industry_id,
        region_id=region_id,
    )

    # ── V2: Route LLM call through ModelRouter + Self-Check Retry ──
    router = _get_router()

    def llm_call(messages: list[dict]) -> str:
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
            "answer": "",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": LLM_NOT_CONFIGURED,
            "disclaimer": DISCLAIMER,
        }
    except LLMCallError:
        return {
            "answer": "",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": LLM_CALL_FAILED,
            "disclaimer": DISCLAIMER,
        }

    return {
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
