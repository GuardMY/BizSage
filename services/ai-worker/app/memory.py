"""Three-tier Agent memory system.

Tier 1 — Conversation: recent message turns (temporary, current session only).
Tier 2 — Summary: compressed conversation history (per conversation).
Tier 3 — Profile: user preferences, business facts, pain points (90-365 day lifecycle).

Implements intelligent automatic forgetting: memories past their lifecycle
expiry are filtered out and no longer used in context construction.

V2: Memory extraction now uses LLM-based semantic analysis with regex fallback.
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Memory categories and lifecycle
# ---------------------------------------------------------------------------

class MemoryCategory:
    PREFERENCE = "PREFERENCE"              # response style, format preferences
    BUSINESS_FACT = "BUSINESS_FACT"        # store_count, channel_mix, revenue_range
    PAIN_POINT = "PAIN_POINT"             # recurring operating problems
    INDUSTRY_CONTEXT = "INDUSTRY_CONTEXT"  # chosen industry, region settings
    LEARNING_PROGRESS = "LEARNING_PROGRESS"  # nodes studied, topics of interest

    @classmethod
    def all_categories(cls) -> list[str]:
        return [cls.PREFERENCE, cls.BUSINESS_FACT, cls.PAIN_POINT,
                cls.INDUSTRY_CONTEXT, cls.LEARNING_PROGRESS]


# Lifecycle in days (matches Java UserMemoryStore expiry logic)
MEMORY_LIFECYCLE: dict[str, int] = {
    MemoryCategory.PREFERENCE: 180,
    MemoryCategory.BUSINESS_FACT: 90,
    MemoryCategory.PAIN_POINT: 90,
    MemoryCategory.INDUSTRY_CONTEXT: 365,
    MemoryCategory.LEARNING_PROGRESS: 180,
}

# Minimum confidence threshold for memories to be used in context
MIN_CONFIDENCE_FOR_CONTEXT: float = 0.60

# Minimum confidence for vector sync (unstructured narratives)
MIN_CONFIDENCE_FOR_VECTOR_SYNC: float = 0.70


# ---------------------------------------------------------------------------
# Context construction
# ---------------------------------------------------------------------------

def build_memory_context(
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    max_profile_items: int = 5,
) -> str:
    """Build a compact memory context string from all three tiers.

    Tier 1 (recent_messages) and Tier 2 (summary) are rendered as-is.
    Tier 3 (profile) items are treated as already filtered by the API layer's
    authoritative MySQL memory store, then sorted by confidence descending and
    capped at *max_profile_items* for prompt efficiency.
    """
    sections: list[str] = []

    if conversation_summary:
        sections.append(f"会话摘要: {conversation_summary}")

    if long_term_memories:
        # The API layer is the source of truth for lifecycle filtering.
        active = [m for m in long_term_memories
                  if m.get("confidence", 0.0) >= MIN_CONFIDENCE_FOR_CONTEXT]
        active.sort(key=lambda m: m.get("confidence", 0.0), reverse=True)
        top = active[:max_profile_items]

        parts: list[str] = []
        for item in top:
            category = item.get("category", "UNKNOWN")
            key = item.get("key", "")
            value = item.get("value", "")
            parts.append(f"{category}:{key}={value}")
        if parts:
            sections.append(f"长期记忆: {' | '.join(parts)}")

    if recent_messages:
        rendered = " | ".join(
            f"{item.get('role', 'unknown')}: {item.get('content', '')}"
            for item in recent_messages[-6:]  # last 6 messages only
        )
        sections.append(f"最近消息: {rendered}")

    return "\n".join(section for section in sections if section)


# ---------------------------------------------------------------------------
# LLM-based memory extraction (V2)
# ---------------------------------------------------------------------------

_memory_router = None

_MEMORY_EXTRACTION_SYSTEM_PROMPT = """你是一个用户记忆分析器。从对话中提取值得长期记忆的用户信息。

## 记忆类别
- PREFERENCE: 用户的回复风格偏好、格式偏好、学习偏好
- BUSINESS_FACT: 业务事实（门店数量、渠道组合、营收范围、经营约束等）
- PAIN_POINT: 重复性经营痛点（现金流、利润、客户流失、招工、租金等）
- INDUSTRY_CONTEXT: 行业选择、地区设置、市场环境
- LEARNING_PROGRESS: 学习过的知识节点、感兴趣的主题

## 规则
1. 仅提取对话中明确出现的新信息，不要编造
2. 每个记忆的 confidence 应为 0.5-1.0（非常确定=0.9+，推测=0.6-0.75）
3. structured=true 表示结构化键值对，structured=false 表示自由文本叙事
4. 没有值得记忆的内容时返回空数组
5. 以 JSON 数组格式返回，每个元素包含: category, key, value, confidence, structured

## 示例输出
[{"category":"BUSINESS_FACT","key":"store_count","value":"两家门店","confidence":0.9,"structured":true}]"""


def _extract_memories_via_llm(
    question: str,
    answer: str,
    chain_node_id: str | None = None,
    existing_memories: list[dict] | None = None,
) -> list[dict]:
    """Use LLM to semantically extract memory candidates from a conversation turn.

    Falls back to regex-based extraction if the LLM is unavailable or returns
    unparseable output.

    Args:
        question: The user's question.
        answer: The assistant's answer.
        chain_node_id: Current chain node (learning mode only).
        existing_memories: Previously extracted memories to avoid duplicates.

    Returns:
        A list of memory candidate dicts.
    """
    try:
        global _memory_router
        if _memory_router is None:
            from app.model_routing.router import ModelRouter
            _memory_router = ModelRouter()

        # Build context about what we already know
        known_context = ""
        if existing_memories:
            known_keys = {f"{m.get('category')}:{m.get('key')}" for m in existing_memories}
            known_context = f"\n已有记忆（请勿重复提取）: {', '.join(sorted(known_keys))}"

        chain_hint = ""
        if chain_node_id:
            chain_hint = f"\n当前学习节点: {chain_node_id}"

        user_prompt = (
            f"用户问题: {question}\n"
            f"助手回答: {answer[:2000]}"  # Truncate very long answers
            f"{chain_hint}"
            f"{known_context}"
            f"\n\n请提取值得长期记忆的用户信息（JSON数组格式）："
        )

        result = _memory_router.call(
            messages=[
                {"role": "system", "content": _MEMORY_EXTRACTION_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            task_hint="fast",  # Lightweight task — use fast tier
            temperature=0.1,   # Low temperature for consistent extraction
            max_tokens=512,
        )

        # Parse the JSON response
        raw = result.response_text.strip()
        # Handle markdown code fences if present
        if raw.startswith("```"):
            raw = raw.split("\n", 1)[-1]  # Remove ```json line
            if raw.endswith("```"):
                raw = raw[:-3]
            raw = raw.strip()

        candidates = json.loads(raw)
        if not isinstance(candidates, list):
            logger.warning("LLM memory extraction returned non-list: %s", type(candidates))
            return []

        # Validate and normalize each candidate
        validated: list[dict] = []
        valid_categories = set(MemoryCategory.all_categories())
        for cand in candidates:
            if not isinstance(cand, dict):
                continue
            category = cand.get("category", "")
            if category not in valid_categories:
                continue
            key = cand.get("key", "").strip()
            value = cand.get("value", "").strip()
            if not key or not value:
                continue
            validated.append({
                "category": category,
                "key": key,
                "value": value,
                "confidence": float(cand.get("confidence", 0.70)),
                "structured": bool(cand.get("structured", True)),
            })

        if validated:
            logger.info("LLM extracted %d memory candidates", len(validated))
        return validated

    except Exception as ex:
        logger.debug("LLM memory extraction failed, will fall back to regex: %s", ex)
        return []  # Return empty to trigger fallback


# ---------------------------------------------------------------------------
# Memory extraction — diagnosis mode
# ---------------------------------------------------------------------------

# Backward-compatible alias
extract_memory_candidates = None  # defined below


def extract_diagnosis_memories(
    question: str,
    answer: str,
    existing_memories: list[dict] | None = None,
) -> list[dict]:
    """Extract memory candidates from a diagnosis interaction.

    V2: Attempts LLM-based extraction first; falls back to regex keyword matching
    when the LLM is unavailable or returns no candidates.
    """
    # ── Try LLM-based extraction first ──
    try:
        llm_candidates = _extract_memories_via_llm(
            question, answer, existing_memories=existing_memories,
        )
        if llm_candidates:
            return llm_candidates
    except Exception:
        logger.debug("LLM extraction threw unexpectedly, falling back to regex", exc_info=True)

    # ── Fallback: regex-based keyword extraction ──
    logger.debug("Falling back to regex-based memory extraction for diagnosis")
    candidates: list[dict] = []
    import re as _re

    # ── PREFERENCE ──
    if _re.search(r"先给结论|结论先行|先说结论|直接说结果", question):
        candidates.append({
            "category": MemoryCategory.PREFERENCE,
            "key": "response_style", "value": "结论优先",
            "confidence": 0.88, "structured": True,
        })
    if _re.search(r"详细|深入|具体|展开|多说", question):
        candidates.append({
            "category": MemoryCategory.PREFERENCE,
            "key": "answer_depth", "value": "详细分析",
            "confidence": 0.82, "structured": True,
        })

    # ── BUSINESS_FACT — generic numeric + keyword patterns ──
    store_match = _re.search(r"(\d+)\s*[家个间]\s*[门店店铺]", question)
    if store_match:
        candidates.append({
            "category": MemoryCategory.BUSINESS_FACT,
            "key": "store_count", "value": f"{store_match.group(1)}家门店",
            "confidence": 0.85, "structured": True,
        })
    revenue_match = _re.search(r"[月年]?营收\s*(\d+)\s*万", question)
    if revenue_match:
        candidates.append({
            "category": MemoryCategory.BUSINESS_FACT,
            "key": "revenue_range", "value": f"月营收约{revenue_match.group(1)}万",
            "confidence": 0.82, "structured": True,
        })
    if _re.search(r"外卖|美团|饿了么|平台订单|线上渠道", question):
        candidates.append({
            "category": MemoryCategory.BUSINESS_FACT,
            "key": "channel_mix", "value": "依赖外卖/线上平台渠道",
            "confidence": 0.85, "structured": True,
        })
    if _re.search(r"堂食|到店|线下客流|门店客流|进店", question):
        candidates.append({
            "category": MemoryCategory.BUSINESS_FACT,
            "key": "offline_channel", "value": "堂食/线下到店渠道存在波动",
            "confidence": 0.80, "structured": False,
        })
    employee_match = _re.search(r"(\d+)\s*[个名位]\s*(员工|雇员|人)", question)
    if employee_match:
        candidates.append({
            "category": MemoryCategory.BUSINESS_FACT,
            "key": "employee_count", "value": f"{employee_match.group(1)}名员工",
            "confidence": 0.83, "structured": True,
        })

    # ── PAIN_POINT — recurring operating problems (regex word boundaries) ──
    pain_patterns = [
        (r"现金流|资金链|现金不足|回款慢|账期", "cashflow_tight", "现金流紧张"),
        (r"周转困难|资金[周压]|垫资|压款", "capital_turnover", "资金周转困难"),
        (r"利润[太低薄少]|不赚钱|亏损|毛利[太低]", "low_profit", "利润过低"),
        (r"客户流失|复购[率低]|留不住[客客]|回头客少", "customer_churn", "客户流失"),
        (r"招[工人]难|用工[荒难]|找不到[人工]|招聘", "staffing_difficulty", "招工困难"),
        (r"租金[高压力贵涨]|房租[高压力贵涨]", "rent_pressure", "租金压力大"),
        (r"平台抽[成佣]|佣金[高升涨]|平台[费扣]", "platform_commission", "平台抽成压力"),
        (r"库存积压|滞销|库存周转|囤货", "inventory_pressure", "库存积压"),
        (r"竞争[激大]|同行|价格战|卷", "competition", "行业竞争激烈"),
        (r"合规|监管|政策[风限]|红线|罚款", "compliance_risk", "合规监管风险"),
    ]
    for pattern, key, value in pain_patterns:
        if _re.search(pattern, question) and not any(
            c.get("key") == key for c in candidates
        ):
            candidates.append({
                "category": MemoryCategory.PAIN_POINT,
                "key": key, "value": value,
                "confidence": 0.75, "structured": False,
            })

    # ── INDUSTRY_CONTEXT — from explicit user statements ──
    import re
    region_match = re.search(r"(北京|上海|广州|深圳|成都|杭州|武汉|南京|重庆|天津)", question)
    if region_match:
        candidates.append({
            "category": MemoryCategory.INDUSTRY_CONTEXT,
            "key": "region", "value": region_match.group(1),
            "confidence": 0.85, "structured": True,
        })

    return candidates


# ---------------------------------------------------------------------------
# Memory extraction — learning mode
# ---------------------------------------------------------------------------

_TOPIC_OF_INTEREST = [
    (r"成本|费用|花钱|省钱|便宜|贵", "cost_management", "成本管控"),
    (r"利润|赚钱|盈利|毛利|净利", "profit_analysis", "利润分析"),
    (r"库存|囤货|压货|缺货|断货", "inventory_management", "库存管理"),
    (r"合规|法规|政策|红线|罚款|不允许", "compliance", "合规风控"),
    (r"扩张|开店|增加门店|扩展|扩大", "expansion", "扩张策略"),
    (r"新入行|刚开始|没经验|零基础", "beginner_onboarding", "零基础入门"),
]


def extract_learning_memories(
    question: str,
    answer: str,
    chain_node_id: str | None = None,
    existing_memories: list[dict] | None = None,
) -> list[dict]:
    """Extract memory candidates from a learning interaction.

    Tracks the user's learning progress — which chain nodes they've studied
    and what topics they're interested in.

    V2: Attempts LLM-based extraction first; falls back to regex matching.
    """
    # ── Try LLM-based extraction first ──
    try:
        llm_candidates = _extract_memories_via_llm(
            question, answer,
            chain_node_id=chain_node_id,
            existing_memories=existing_memories,
        )
        if llm_candidates:
            return llm_candidates
    except Exception:
        logger.debug("LLM extraction threw unexpectedly, falling back to regex", exc_info=True)

    # ── Fallback: regex-based extraction ──
    logger.debug("Falling back to regex-based memory extraction for learning")
    import re as _re
    candidates: list[dict] = []

    # ── CHAIN NODE PROGRESS ──
    if chain_node_id:
        candidates.append({
            "category": MemoryCategory.LEARNING_PROGRESS,
            "key": f"studied_node_{chain_node_id}",
            "value": f"已学习: {chain_node_id}",
            "confidence": 0.90,
            "structured": True,
            "expires_in_days": MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS],
        })

    # ── TOPIC INTERESTS ──
    for pattern, key, value in _TOPIC_OF_INTEREST:
        if _re.search(pattern, question) and not any(
            c.get("key") == key for c in candidates
        ):
            candidates.append({
                "category": MemoryCategory.LEARNING_PROGRESS,
                "key": f"interest_{key}",
                "value": value,
                "confidence": 0.78,
                "structured": False,
                "expires_in_days": MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS],
            })

    # ── LEARNING PREFERENCES ──
    if any(w in question for w in ("详细", "深入", "具体")):
        candidates.append({
            "category": MemoryCategory.PREFERENCE,
            "key": "learning_depth",
            "value": "DEEP_DIVE",
            "confidence": 0.82,
            "structured": True,
        })
    if any(w in question for w in ("简单", "快速", "大概")):
        candidates.append({
            "category": MemoryCategory.PREFERENCE,
            "key": "learning_depth",
            "value": "FAST_OVERVIEW",
            "confidence": 0.82,
            "structured": True,
        })

    return candidates


# ---------------------------------------------------------------------------
# Transition memory extraction
# ---------------------------------------------------------------------------

def extract_transition_memories(
    from_mode: str,
    to_mode: str,
    user_question: str,
    previous_answer: str | None = None,
    chain_node_id: str | None = None,
) -> list[dict]:
    """Extract memory candidates during a mode transition.

    Captures the context of why the user is switching modes, which provides
    valuable signal about their needs and focus areas.

    Args:
        from_mode: The mode being switched from ("LEARNING" | "DIAGNOSIS").
        to_mode: The mode being switched to ("LEARNING" | "DIAGNOSIS").
        user_question: The user's transition-triggering question.
        previous_answer: The last answer from the previous mode (may be None).
        chain_node_id: The current chain node, if applicable.
    """
    candidates: list[dict] = []

    # Record the transition itself (short-lived)
    candidates.append({
        "category": MemoryCategory.LEARNING_PROGRESS,
        "key": f"transition_{from_mode.lower()}_to_{to_mode.lower()}",
        "value": f"用户从{from_mode}模式切换到{to_mode}模式",
        "confidence": 1.0,
        "structured": True,
        "expires_in_days": 7,
    })

    # If switching from learning to diagnosis, the learned topic becomes a diagnosis interest
    if from_mode == "LEARNING" and to_mode == "DIAGNOSIS" and chain_node_id:
        candidates.append({
            "category": MemoryCategory.LEARNING_PROGRESS,
            "key": f"diagnosis_from_learning_{chain_node_id}",
            "value": f"用户在学习了{chain_node_id}后尝试诊断实际业务",
            "confidence": 0.85,
            "structured": False,
            "expires_in_days": MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS],
        })

    # If switching from diagnosis to learning, the diagnosed weakness becomes a learning goal
    if from_mode == "DIAGNOSIS" and to_mode == "LEARNING" and chain_node_id:
        candidates.append({
            "category": MemoryCategory.LEARNING_PROGRESS,
            "key": f"learning_from_diagnosis_{chain_node_id}",
            "value": f"用户在诊断中发现{chain_node_id}薄弱后转向学习",
            "confidence": 0.85,
            "structured": False,
            "expires_in_days": MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS],
        })

    # Try LLM-based extraction for deeper context in the transition question
    if previous_answer:
        llm_candidates = _extract_memories_via_llm(
            user_question, previous_answer, chain_node_id=chain_node_id,
        )
        # Merge, deduplicating by (category, key)
        seen = {(c["category"], c["key"]) for c in candidates}
        for cand in llm_candidates:
            if (cand["category"], cand["key"]) not in seen:
                seen.add((cand["category"], cand["key"]))
                candidates.append(cand)

    return candidates


# ---------------------------------------------------------------------------
# Intelligent forgetting
# ---------------------------------------------------------------------------

def forget_expired(
    memories: list[dict],
    current_time: float | None = None,
) -> list[dict]:
    """Filter out memories that have passed their lifecycle expiry.

    A memory is expired if ``expires_at`` (absolute timestamp, set by Java)
    has passed. Memories without expiry info are kept.
    """
    now = current_time or time.time()
    active: list[dict] = []
    for mem in memories:
        expires_at = mem.get("expires_at")
        if expires_at is not None:
            # Absolute expiry timestamp (from Java side)
            if isinstance(expires_at, (int, float)):
                if now < expires_at:
                    active.append(mem)
                # else: expired → skip
            else:
                # Non-numeric expiry (e.g. string date) → keep
                active.append(mem)
        else:
            # No expiry info → keep
            active.append(mem)
    return active


# ---------------------------------------------------------------------------
# Backward-compatible alias
# ---------------------------------------------------------------------------

# The original extract_memory_candidates is now enhanced and renamed
extract_memory_candidates = extract_diagnosis_memories


# ---------------------------------------------------------------------------
# Vector memory sync
# ---------------------------------------------------------------------------

def should_sync_to_vector_memory(memory: dict) -> bool:
    """Whether this memory should be synced to vector storage (Qdrant).

    Only unstructured narrative memories meeting the confidence threshold
    and with ACTIVE status are synced. Structured key-value facts stay in
    MySQL only since they can be queried directly by (category, key).

    The confidence threshold (MIN_CONFIDENCE_FOR_VECTOR_SYNC = 0.70) ensures
    only reasonably certain memories consume vector storage.
    """
    if memory.get("structured", True):
        return False
    if memory.get("status", "ACTIVE") != "ACTIVE":
        return False
    if memory.get("confidence", 0.0) < MIN_CONFIDENCE_FOR_VECTOR_SYNC:
        return False
    return True


# ---------------------------------------------------------------------------
# Memory consolidation
# ---------------------------------------------------------------------------

def consolidate_memories(
    memories: list[dict],
) -> list[dict]:
    """Deduplicate and consolidate memories.

    When multiple memories share the same (category, key), keep the one with
    the highest confidence and the most recent value.
    """
    by_key: dict[tuple[str, str], dict] = {}
    for mem in memories:
        key = (mem.get("category", ""), mem.get("key", ""))
        if key not in by_key:
            by_key[key] = mem
        else:
            existing = by_key[key]
            # Keep the higher-confidence memory
            if mem.get("confidence", 0.0) > existing.get("confidence", 0.0):
                by_key[key] = mem
    return list(by_key.values())
