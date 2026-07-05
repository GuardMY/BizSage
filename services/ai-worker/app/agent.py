from __future__ import annotations

from app.llm import generate_answer
from app.memory import build_memory_context, extract_memory_candidates
from app.rag import KnowledgeItem, search_knowledge

DISCLAIMER = "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。"
TIMELINESS = "基于V1静态基线知识和已入库情报生成。"


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
) -> dict:
    if conflict_labels:
        return {
            "answer": "当前信息存在冲突或缺少权威支撑，需要运营复核后再给出结论。",
            "sources": [],
            "confidence": "LOW",
            "timeliness": TIMELINESS,
            "selfCheckStatus": "NEEDS_REVIEW",
            "disclaimer": DISCLAIMER,
        }

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

    context = "\n".join(f"{item.title}: {item.content}" for item in results)
    memory_context = build_memory_context(recent_messages, conversation_summary, long_term_memories)
    if memory_context:
        context = f"{memory_context}\n{context}"
    answer = generate_answer(question, context)
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
        "selfCheckStatus": "PASSED",
        "disclaimer": DISCLAIMER,
        "memoryCandidates": extract_memory_candidates(question, answer),
    }
