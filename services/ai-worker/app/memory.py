"""Agent 三层记忆系统。

三层结构：
- 会话层：最近几轮消息，只服务当前对话。
- 摘要层：被压缩后的历史消息，按会话维护。
- 用户画像层：偏好、业务事实、痛点和学习进度，生命周期通常为 90-365 天。

过期记忆会被过滤，不再进入提示词上下文；结构化记忆保留在 MySQL，非结构化高置信度
记忆可同步到 Qdrant 做语义检索。

记忆提取优先使用 LLM 语义分析，失败或无结果时退回正则规则。
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 记忆分类和生命周期，与 Java API 层的持久化策略保持一致。
# ---------------------------------------------------------------------------

class MemoryCategory:
    PREFERENCE = "PREFERENCE"              # 回复风格、格式和学习偏好。
    BUSINESS_FACT = "BUSINESS_FACT"        # 门店数、渠道结构、营收区间等业务事实。
    PAIN_POINT = "PAIN_POINT"              # 反复出现的经营痛点。
    INDUSTRY_CONTEXT = "INDUSTRY_CONTEXT"  # 用户选择的行业、地域等上下文。
    LEARNING_PROGRESS = "LEARNING_PROGRESS"  # 已学习节点和兴趣主题。

    @classmethod
    def all_categories(cls) -> list[str]:
        return [cls.PREFERENCE, cls.BUSINESS_FACT, cls.PAIN_POINT,
                cls.INDUSTRY_CONTEXT, cls.LEARNING_PROGRESS]


# 生命周期按天计算，需与 Java UserMemoryStore 的过期逻辑一致。
MEMORY_LIFECYCLE: dict[str, int] = {
    MemoryCategory.PREFERENCE: 180,
    MemoryCategory.BUSINESS_FACT: 90,
    MemoryCategory.PAIN_POINT: 90,
    MemoryCategory.INDUSTRY_CONTEXT: 365,
    MemoryCategory.LEARNING_PROGRESS: 180,
}

# 进入提示词上下文的最低置信度。
MIN_CONFIDENCE_FOR_CONTEXT: float = 0.60

# 同步到向量库的最低置信度，仅用于非结构化叙事记忆。
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
    """把三层记忆压成适合放进提示词的短文本。

    最近消息和摘要直接渲染；长期画像默认已由 API 层按状态和生命周期过滤，
    这里只按置信度排序并截断数量，避免提示词被低价值记忆挤满。
    """
    sections: list[str] = []

    if conversation_summary:
        sections.append(f"会话摘要: {conversation_summary}")

    if long_term_memories:
        # API 层是生命周期和状态过滤的权威来源；Worker 只做置信度和数量保护。
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
            for item in recent_messages[-6:]  # 只保留最近 6 条，防止上下文膨胀。
        )
        sections.append(f"最近消息: {rendered}")

    return "\n".join(section for section in sections if section)


# ---------------------------------------------------------------------------
# 基于 LLM 的记忆提取；失败时会退回正则规则。
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
    """用 LLM 从一轮问答中提取长期记忆候选。

    如果 LLM 不可用、输出不是 JSON 或字段不合法，调用方会继续走正则兜底。

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

        # 带上已有记忆键，减少重复提取。
        known_context = ""
        if existing_memories:
            known_keys = {f"{m.get('category')}:{m.get('key')}" for m in existing_memories}
            known_context = f"\n已有记忆（请勿重复提取）: {', '.join(sorted(known_keys))}"

        chain_hint = ""
        if chain_node_id:
            chain_hint = f"\n当前学习节点: {chain_node_id}"

        user_prompt = (
            f"用户问题: {question}\n"
            f"助手回答: {answer[:2000]}"  # 截断过长回答，降低记忆抽取成本。
            f"{chain_hint}"
            f"{known_context}"
            f"\n\n请提取值得长期记忆的用户信息（JSON数组格式）："
        )

        result = _memory_router.call(
            messages=[
                {"role": "system", "content": _MEMORY_EXTRACTION_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            task_hint="fast",  # 轻量抽取任务，优先使用快速档模型。
            temperature=0.1,   # 低温度保证结构化结果稳定。
            max_tokens=512,
        )

        # 解析 JSON 输出，并兼容模型返回 Markdown 代码块的情况。
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

        # 校验并规范化候选记忆，避免脏字段进入 API 层持久化。
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
# 诊断模式记忆提取。
# ---------------------------------------------------------------------------

# 兼容旧调用名，文件末尾会绑定到增强后的诊断提取函数。
extract_memory_candidates = None  # defined below


def extract_diagnosis_memories(
    question: str,
    answer: str,
    existing_memories: list[dict] | None = None,
) -> list[dict]:
    """从诊断问答中提取用户偏好、业务事实和经营痛点。

    优先使用 LLM；无候选时退回关键词/正则提取。
    """
    # 优先尝试 LLM 语义抽取。
    try:
        llm_candidates = _extract_memories_via_llm(
            question, answer, existing_memories=existing_memories,
        )
        if llm_candidates:
            return llm_candidates
    except Exception:
        logger.debug("LLM extraction threw unexpectedly, falling back to regex", exc_info=True)

    # 兜底：基于关键词的正则提取，覆盖常见经营事实和痛点。
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

    # 业务事实：优先提取可结构化、可复用的数字和渠道信息。
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

    # 经营痛点：提取反复出现、会影响后续诊断策略的问题。
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

    # 行业/地域上下文：仅从用户明确表达中提取。
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
# 学习模式记忆提取。
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
    """从学习问答中提取学习进度、兴趣主题和学习偏好。

    优先使用 LLM；无候选时退回节点进度和主题关键词。
    """
    # 优先尝试 LLM 语义抽取。
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

    # 兜底：记录链条节点学习进度和主题兴趣。
    logger.debug("Falling back to regex-based memory extraction for learning")
    import re as _re
    candidates: list[dict] = []

    # 链条节点进度：短中期有效，用于推荐下一步学习路径。
    if chain_node_id:
        candidates.append({
            "category": MemoryCategory.LEARNING_PROGRESS,
            "key": f"studied_node_{chain_node_id}",
            "value": f"已学习: {chain_node_id}",
            "confidence": 0.90,
            "structured": True,
            "expires_in_days": MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS],
        })

    # 主题兴趣：用于后续学习内容排序和推荐。
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

    # 学习偏好：控制回答深度。
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
# 模式切换记忆提取。
# ---------------------------------------------------------------------------

def extract_transition_memories(
    from_mode: str,
    to_mode: str,
    user_question: str,
    previous_answer: str | None = None,
    chain_node_id: str | None = None,
) -> list[dict]:
    """在学习/诊断模式切换时提取记忆候选。

    切换原因能反映用户当前真实目标，是后续推荐和诊断的重要信号。

    Args:
        from_mode: The mode being switched from ("LEARNING" | "DIAGNOSIS").
        to_mode: The mode being switched to ("LEARNING" | "DIAGNOSIS").
        user_question: The user's transition-triggering question.
        previous_answer: The last answer from the previous mode (may be None).
        chain_node_id: The current chain node, if applicable.
    """
    candidates: list[dict] = []

    # 记录切换动作本身，生命周期较短，避免长期污染画像。
    candidates.append({
        "category": MemoryCategory.LEARNING_PROGRESS,
        "key": f"transition_{from_mode.lower()}_to_{to_mode.lower()}",
        "value": f"用户从{from_mode}模式切换到{to_mode}模式",
        "confidence": 1.0,
        "structured": True,
        "expires_in_days": 7,
    })

    # 学习转诊断：刚学过的主题变成实际经营诊断关注点。
    if from_mode == "LEARNING" and to_mode == "DIAGNOSIS" and chain_node_id:
        candidates.append({
            "category": MemoryCategory.LEARNING_PROGRESS,
            "key": f"diagnosis_from_learning_{chain_node_id}",
            "value": f"用户在学习了{chain_node_id}后尝试诊断实际业务",
            "confidence": 0.85,
            "structured": False,
            "expires_in_days": MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS],
        })

    # 诊断转学习：诊断暴露的问题变成学习目标。
    if from_mode == "DIAGNOSIS" and to_mode == "LEARNING" and chain_node_id:
        candidates.append({
            "category": MemoryCategory.LEARNING_PROGRESS,
            "key": f"learning_from_diagnosis_{chain_node_id}",
            "value": f"用户在诊断中发现{chain_node_id}薄弱后转向学习",
            "confidence": 0.85,
            "structured": False,
            "expires_in_days": MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS],
        })

    # 如果有上一轮回答，再尝试提取更深层的切换背景。
    if previous_answer:
        llm_candidates = _extract_memories_via_llm(
            user_question, previous_answer, chain_node_id=chain_node_id,
        )
        # 按 (category, key) 去重合并，避免重复候选。
        seen = {(c["category"], c["key"]) for c in candidates}
        for cand in llm_candidates:
            if (cand["category"], cand["key"]) not in seen:
                seen.add((cand["category"], cand["key"]))
                candidates.append(cand)

    return candidates


# ---------------------------------------------------------------------------
# 智能遗忘。
# ---------------------------------------------------------------------------

def forget_expired(
    memories: list[dict],
    current_time: float | None = None,
) -> list[dict]:
    """过滤已经超过生命周期的记忆。

    expires_at 是 Java 层写入的绝对时间戳；没有过期信息的记忆默认保留。
    """
    now = current_time or time.time()
    active: list[dict] = []
    for mem in memories:
        expires_at = mem.get("expires_at")
        if expires_at is not None:
            # Java 侧写入的绝对过期时间戳。
            if isinstance(expires_at, (int, float)):
                if now < expires_at:
                    active.append(mem)
                # 已过期则跳过。
            else:
                # 非数字过期值（例如字符串日期）无法安全判断，保守保留。
                active.append(mem)
        else:
            # 没有过期信息时保守保留。
            active.append(mem)
    return active


# ---------------------------------------------------------------------------
# 兼容旧名称。
# ---------------------------------------------------------------------------

# 原 extract_memory_candidates 已增强并改名为 extract_diagnosis_memories。
extract_memory_candidates = extract_diagnosis_memories


# ---------------------------------------------------------------------------
# 向量记忆同步判定。
# ---------------------------------------------------------------------------

def should_sync_to_vector_memory(memory: dict) -> bool:
    """判断一条记忆是否需要同步到 Qdrant。

    只有 ACTIVE 且高置信度的非结构化叙事记忆会进入向量库；结构化键值事实留在 MySQL，
    因为它们可以直接按 category/key 查询，不需要语义检索。
    """
    if memory.get("structured", True):
        return False
    if memory.get("status", "ACTIVE") != "ACTIVE":
        return False
    if memory.get("confidence", 0.0) < MIN_CONFIDENCE_FOR_VECTOR_SYNC:
        return False
    return True


# ---------------------------------------------------------------------------
# 记忆合并。
# ---------------------------------------------------------------------------

def consolidate_memories(
    memories: list[dict],
) -> list[dict]:
    """按 (category, key) 去重合并记忆。

    同一键出现多条候选时保留置信度最高的一条。
    """
    by_key: dict[tuple[str, str], dict] = {}
    for mem in memories:
        key = (mem.get("category", ""), mem.get("key", ""))
        if key not in by_key:
            by_key[key] = mem
        else:
            existing = by_key[key]
            # 保留置信度更高的记忆。
            if mem.get("confidence", 0.0) > existing.get("confidence", 0.0):
                by_key[key] = mem
    return list(by_key.values())
