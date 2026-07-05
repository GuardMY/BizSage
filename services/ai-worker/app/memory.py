from __future__ import annotations


def build_memory_context(
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
) -> str:
    sections: list[str] = []
    if conversation_summary:
        sections.append(f"会话摘要: {conversation_summary}")
    if long_term_memories:
        rendered = " | ".join(
            f"{item.get('category', 'UNKNOWN')}:{item.get('key', '')}={item.get('value', '')}"
            for item in long_term_memories
        )
        sections.append(f"长期记忆: {rendered}")
    if recent_messages:
        rendered = " | ".join(
            f"{item.get('role', 'unknown')}: {item.get('content', '')}" for item in recent_messages
        )
        sections.append(f"最近消息: {rendered}")
    return "\n".join(section for section in sections if section)


def extract_memory_candidates(question: str, answer: str) -> list[dict]:
    candidates: list[dict] = []
    if "先给结论再给证据" in question:
        candidates.append(
            {
                "category": "PREFERENCE",
                "key": "response_style",
                "value": "先给结论再给证据",
                "confidence": 0.95,
                "structured": True,
            }
        )
    if "两家门店" in question:
        candidates.append(
            {
                "category": "BUSINESS_FACT",
                "key": "store_count",
                "value": "两家门店",
                "confidence": 0.88,
                "structured": True,
            }
        )
    if "外卖" in question:
        candidates.append(
            {
                "category": "BUSINESS_FACT",
                "key": "channel_mix",
                "value": "主要依赖外卖平台",
                "confidence": 0.9,
                "structured": True,
            }
        )
    if "堂食波动" in question:
        candidates.append(
            {
                "category": "BUSINESS_FACT",
                "key": "narrative_constraint",
                "value": "堂食波动很大且受平台佣金影响",
                "confidence": 0.8,
                "structured": False,
            }
        )
    return candidates


def should_sync_to_vector_memory(memory: dict) -> bool:
    return not memory.get("structured", True) and memory.get("status", "ACTIVE") == "ACTIVE"
