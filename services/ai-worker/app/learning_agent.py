"""行业学习 Agent，BizSage 双 Agent 体系的另一条主链路。

流程：用户问题 → 意图识别 → 六维 RAG → 知识重组 → 通俗化表达 → 结构化 AgentOutput。

能力范围：
- 行业新手入门
- 政策法规解释
- 经营规则拆解
- 合规红线提醒
- 行业动态理解
- 链条式递进学习
"""

from __future__ import annotations

import re
from collections.abc import Iterator
from dataclasses import dataclass

from app.agent_output import (
    DISCLAIMER_LEARNING,
    TIMELINESS_LEARNING,
    AgentOutput,
    SourceRef,
    format_learning_output,
    render_agent_output,
)
from app.context_compressor import CompressConfig, compress_context
from app.llm import LLMCallError, LLMNotConfiguredError
from app.memory import build_memory_context, extract_learning_memories
from app.model_routing.router import ModelRouter
from app.prompt_library.assembler import PromptAssembler
from app.prompt_library.layers import AgentMode
from app.rag import KnowledgeItem, SearchResult, search_knowledge
from app.reasoning_checks.retry import run_with_retry, run_with_retry_stream


# 行业链条节点：用于把宽泛学习问题落到可教学、可导航的业务环节上。

CHAIN_NODES = [
    ("raw-materials",        "原材料",       "composition, origin, supply pattern, procurement"),
    ("production",           "生产制造加工",  "process steps, capacity, labor, SOPs"),
    ("quality-control",      "质量控制",     "inspections, compliance standards, defect metrics"),
    ("warehouse-inventory",  "仓储库存",     "warehouse classes, safety stock, turnover metrics"),
    ("logistics",            "物流流通",     "trunk, local delivery, pricing, damage compensation"),
    ("channel-operations",   "渠道运营",     "agent hierarchy, pricing layers, rebates, region protection"),
    ("sales-payment",        "销售终端回款", "terminal scenarios, pricing, promotions, collection metrics"),
]


# ---------------------------------------------------------------------------
# Learning-specific types
# ---------------------------------------------------------------------------

class LearningMode:
    FAST_START = "FAST_START"
    FULL_CHAIN = "FULL_CHAIN"
    NODE_DEEP_DIVE = "NODE_DEEP_DIVE"


class IntentType:
    INDUSTRY_OVERVIEW = "INDUSTRY_OVERVIEW"
    NODE_LEARNING = "NODE_LEARNING"
    METRIC_QUESTION = "METRIC_QUESTION"
    RISK_QUESTION = "RISK_QUESTION"
    HIDDEN_RULE = "HIDDEN_RULE"
    POLICY_QUESTION = "POLICY_QUESTION"


# ---------------------------------------------------------------------------
# Lazy-initialized singletons
# ---------------------------------------------------------------------------

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


# ---------------------------------------------------------------------------
# Intent classification
# ---------------------------------------------------------------------------

# 关键词到（意图类型，链条节点）的映射；优先使用显式命中，降低误判成本。
_INTENT_PATTERNS: list[tuple[str, str, str | None]] = [
    ("行业概览|行业介绍|行业结构|市场规模|入门|了解这个行业|整体", IntentType.INDUSTRY_OVERVIEW, None),
    ("是什么行业|这个行业怎么做|怎么入行", IntentType.INDUSTRY_OVERVIEW, None),
    ("原材料|供应|采购|上游|原料", IntentType.NODE_LEARNING, "raw-materials"),
    ("生产|制造|加工|产能|工艺|OEM|代工", IntentType.NODE_LEARNING, "production"),
    ("质量|质检|品控|合规标准|不合格|缺陷", IntentType.NODE_LEARNING, "quality-control"),
    ("仓储|库存|仓库|入库|出库|安全库存|周转", IntentType.NODE_LEARNING, "warehouse-inventory"),
    ("物流|运输|配送|干线|快递|货运|时效", IntentType.NODE_LEARNING, "logistics"),
    ("渠道|代理|经销商|层级|返利|串货|区域保护", IntentType.NODE_LEARNING, "channel-operations"),
    ("销售|终端|回款|定价|促销|账期|结算", IntentType.NODE_LEARNING, "sales-payment"),
    ("指标|成本|利润率|周转率|转化率|损耗率", IntentType.METRIC_QUESTION, None),
    ("风险|陷阱|雷区|红线|禁止|罚款|合规风险", IntentType.RISK_QUESTION, None),
    ("潜规则|默认规则|行业惯例|回扣|返点|隐形|默认让步", IntentType.HIDDEN_RULE, None),
    ("政策|法规|规定|通知|新规|监管|税务", IntentType.POLICY_QUESTION, None),
]

_NODE_ID_BY_SLUG: dict[str, str] = {slug: slug for slug, _, _ in CHAIN_NODES}
_NODE_SLUGS_BY_KEYWORD: list[tuple[re.Pattern, str]] = [
    (re.compile(r"原材料|供应|采购|上游|原料|供应商"), "raw-materials"),
    (re.compile(r"生产|制造|加工|产能|工艺|OEM|代工|工厂"), "production"),
    (re.compile(r"质量|质检|品控|合格率|不合格|缺陷"), "quality-control"),
    (re.compile(r"仓储|库存|仓库|入库|出库|安全库存|周转天数"), "warehouse-inventory"),
    (re.compile(r"物流|运输|配送|干线|快递|货运|冷链"), "logistics"),
    (re.compile(r"渠道|代理|经销商|层级|返利|串货|区域保护|分销"), "channel-operations"),
    (re.compile(r"销售|终端|回款|定价|促销|账期|结算|客单价|翻台"), "sales-payment"),
]


def classify_learning_intent(question: str) -> tuple[str, str | None]:
    """识别学习意图，并尽可能解析出对应链条节点。"""
    for pattern_str, intent_type, node_id in _INTENT_PATTERNS:
        if re.search(pattern_str, question):
            if intent_type == IntentType.NODE_LEARNING and node_id is None:
                resolved = _resolve_node(question)
                return (intent_type, resolved)
            return (intent_type, node_id)

    resolved = _resolve_node(question)
    if resolved:
        return (IntentType.NODE_LEARNING, resolved)
    return (IntentType.INDUSTRY_OVERVIEW, None)


def _resolve_node(question: str) -> str | None:
    """用节点关键词把问题映射到具体链条环节。"""
    for pattern, node_id in _NODE_SLUGS_BY_KEYWORD:
        if pattern.search(question):
            return node_id
    return None


def filter_knowledge_by_node(
    knowledge: list[KnowledgeItem],
    chain_node_id: str | None,
) -> list[KnowledgeItem]:
    """按链条节点过滤知识；没有节点时保留完整知识池。"""
    if chain_node_id is None:
        return knowledge
    return [k for k in knowledge if k.link_id == chain_node_id]


# ---------------------------------------------------------------------------
# Learning Agent — main entry point
# ---------------------------------------------------------------------------

def learn(
    question: str,
    *,
    knowledge: list[KnowledgeItem],
    chain_node_id: str | None = None,
    learning_mode: str = LearningMode.FAST_START,
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
    compress_config: CompressConfig | None = None,
) -> dict:
    """运行行业学习 Agent，并返回统一 Agent 输出结构。"""
    # 自动识别意图和链条节点；显式传入的 chain_node_id 优先。
    intent_type, detected_node = classify_learning_intent(question)
    effective_node = chain_node_id or detected_node

    # 优先使用目标节点知识，若该节点没有知识则退回全量知识池，避免过早空结果。
    node_knowledge = filter_knowledge_by_node(knowledge, effective_node)

    # RAG 仍会执行地域、行业和会员过滤，保证学习资料可用且不越权。
    results = search_knowledge(
        question,
        node_knowledge or knowledge,
        region_id=region_id,
        industry_id=industry_id,
        membership_level=membership_level,
        vector_store=vector_store,
        restrict_to_knowledge_ids=restrict_to_knowledge_ids,
    )

    if not results:
        return render_agent_output(
            format_learning_output(
                answer="信息不足：当前知识库中没有检索到可支撑该学习问题的证据，"
                       "请尝试选择其他链条节点或更具体的问题。",
                sources=[],
                chain_node_id=effective_node,
                confidence="LOW",
                self_check_status="INSUFFICIENT_EVIDENCE",
            )
        )

    # 学习提示词会把意图、学习模式、节点和用户记忆一起传给模型。
    result_dicts = _results_to_dicts(results)
    memory_context = build_memory_context(recent_messages, conversation_summary, long_term_memories)
    compress_cfg = compress_config or CompressConfig(
        total_token_budget=2000 if memory_context else 2300,
    )
    compressed_text, _ = compress_context(result_dicts, config=compress_cfg)

    learning_prompt = _build_learning_prompt(
        question=question,
        intent_type=intent_type,
        learning_mode=learning_mode,
        chain_node_id=effective_node,
        context=compressed_text,
        memory_context=memory_context,
    )

    # 分层系统提示词控制学习 Agent 的表达边界和结构要求。
    assembler = _get_assembler()
    system_prompt = assembler.assemble(
        mode=AgentMode.LEARNING.value,
        industry_id=industry_id,
        region_id=region_id,
    )

    # 模型输出必须通过自检；失败时带反馈重试。
    router = _get_router()

    def llm_call(messages: list[dict]) -> str:
        """供自检重试器调用的模型路由包装。"""
        result = router.call(
            messages=messages,
            task_hint="balanced",
            temperature=0.5,
            max_tokens=1024,
        )
        return result.response_text

    initial_messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": learning_prompt},
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
        return render_agent_output(
            format_learning_output(
                answer="",
                sources=[],
                chain_node_id=effective_node,
                confidence="LOW",
                self_check_status="LLM_NOT_CONFIGURED",
            )
        )
    except LLMCallError:
        return render_agent_output(
            format_learning_output(
                answer="",
                sources=[],
                chain_node_id=effective_node,
                confidence="LOW",
                self_check_status="LLM_CALL_FAILED",
            )
        )

    # 统一格式化输出，并从本轮学习中提取长期记忆候选。
    output = format_learning_output(
        answer=answer,
        sources=_results_to_sources(results),
        chain_node_id=effective_node,
        memory_candidates=extract_learning_memories(question, answer, effective_node, existing_memories=long_term_memories),
        confidence="MEDIUM",
        self_check_status=check_status,
    )
    return render_agent_output(output)


def learn_stream(
    question: str,
    *,
    knowledge: list[KnowledgeItem],
    chain_node_id: str | None = None,
    learning_mode: str = LearningMode.FAST_START,
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
    compress_config: CompressConfig | None = None,
) -> Iterator[dict]:
    """Yield learning deltas and one terminal result event."""
    intent_type, detected_node = classify_learning_intent(question)
    effective_node = chain_node_id or detected_node
    node_knowledge = filter_knowledge_by_node(knowledge, effective_node)
    results = search_knowledge(
        question,
        node_knowledge or knowledge,
        region_id=region_id,
        industry_id=industry_id,
        membership_level=membership_level,
        vector_store=vector_store,
        restrict_to_knowledge_ids=restrict_to_knowledge_ids,
    )

    if not results:
        result = render_agent_output(
            format_learning_output(
                answer="信息不足：当前知识库中没有检索到可支撑该学习问题的证据，"
                       "请尝试选择其他链条节点或更具体的问题。",
                sources=[],
                chain_node_id=effective_node,
                confidence="LOW",
                self_check_status="INSUFFICIENT_EVIDENCE",
            )
        )
        yield {"event": "delta", "text": result["answer"], "attempt": 1}
        yield {"event": "result", "result": result, "attempt": 1}
        return

    result_dicts = _results_to_dicts(results)
    memory_context = build_memory_context(
        recent_messages, conversation_summary, long_term_memories
    )
    compress_cfg = compress_config or CompressConfig(
        total_token_budget=2000 if memory_context else 2300,
    )
    compressed_text, _ = compress_context(result_dicts, config=compress_cfg)
    learning_prompt = _build_learning_prompt(
        question=question,
        intent_type=intent_type,
        learning_mode=learning_mode,
        chain_node_id=effective_node,
        context=compressed_text,
        memory_context=memory_context,
    )
    system_prompt = _get_assembler().assemble(
        mode=AgentMode.LEARNING.value,
        industry_id=industry_id,
        region_id=region_id,
    )
    initial_messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": learning_prompt},
    ]
    router = _get_router()

    def llm_stream(messages: list[dict]) -> Iterator[dict]:
        yield from router.stream(
            messages=messages,
            task_hint="balanced",
            temperature=0.5,
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
        result = render_agent_output(
            format_learning_output(
                answer=answer,
                sources=_results_to_sources(results),
                chain_node_id=effective_node,
                memory_candidates=extract_learning_memories(
                    question,
                    answer,
                    effective_node,
                    existing_memories=long_term_memories,
                ),
                confidence="MEDIUM",
                self_check_status=event["selfCheckStatus"],
            )
        )
        yield {"event": "result", "result": result, "attempt": event["attempt"]}
        return


# ---------------------------------------------------------------------------
# Prompt construction
# ---------------------------------------------------------------------------

def _build_learning_prompt(
    *,
    question: str,
    intent_type: str,
    learning_mode: str,
    chain_node_id: str | None,
    context: str,
    memory_context: str,
) -> str:
    """构造学习模式下发送给 LLM 的完整用户提示词。"""
    node_name = _node_display_name(chain_node_id) if chain_node_id else "全链条"

    mode_hint = {
        LearningMode.FAST_START: f"用户正在进行快速入门学习，当前聚焦节点：{node_name}。请给出简洁的概览式回答。",
        LearningMode.FULL_CHAIN: f"用户正在进行全链条深度学习，当前聚焦节点：{node_name}。请给出详细的系统性回答，并关联上下游节点。",
        LearningMode.NODE_DEEP_DIVE: f"用户正在深度研究节点：{node_name}。请给出深入的技术性回答，包含指标数据和实践细节。",
    }.get(learning_mode, "")

    intent_hint = {
        IntentType.INDUSTRY_OVERVIEW: "用户想要了解行业全貌。",
        IntentType.NODE_LEARNING: f"用户想要学习{node_name}相关的知识。",
        IntentType.METRIC_QUESTION: "用户询问具体的指标数据。",
        IntentType.RISK_QUESTION: "用户关心风险点和合规红线。",
        IntentType.HIDDEN_RULE: "用户想了解行业隐形规则和惯例。",
        IntentType.POLICY_QUESTION: "用户询问政策法规相关内容。",
    }.get(intent_type, "")

    parts = [f"问题：{question}", f"学习模式：{mode_hint}", f"意图分析：{intent_hint}"]
    if memory_context:
        parts.append(f"用户背景：{memory_context}")
    parts.append(f"参考证据：\n{context}")

    return "\n\n".join(parts)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _node_display_name(node_id: str | None) -> str:
    """将链条节点 ID 转成人类可读名称。"""
    if node_id is None:
        return "未知"
    for slug, name, _ in CHAIN_NODES:
        if slug == node_id:
            return name
    return node_id


def _results_to_dicts(results: list[SearchResult]) -> list[dict]:
    """把检索结果压成自检和上下文压缩使用的证据字典。"""
    return [
        {
            "id": r.id, "title": r.title, "content": r.content,
            "source_url": r.source_url, "source_id": r.source_id, "score": r.score,
        }
        for r in results
    ]


def _results_to_sources(results: list[SearchResult]) -> list[SourceRef]:
    """把检索结果转换为统一输出中的来源引用。"""
    return [
        SourceRef(
            id=r.id, title=r.title, source_url=r.source_url,
            source_id=r.source_id, confidence=r.confidence,
            score=r.score, entitlement=r.entitlement,
        )
        for r in results
    ]
