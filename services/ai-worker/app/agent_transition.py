"""双 Agent 模式切换：在学习和诊断之间保留上下文。

用户可以从“学习行业知识”自然切换到“诊断真实业务”，也可以从诊断暴露的问题
回到学习模式补课。切换时会保留摘要、最近消息和切换原因，避免目标 Agent
把问题当作一轮孤立对话。

切换规则：
- LEARNING → DIAGNOSIS：当前学习节点成为诊断关注维度。
- DIAGNOSIS → LEARNING：诊断中暴露的薄弱环节成为学习主题。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from app.agent import diagnose, diagnose_stream
from app.agent_output import (
    DISCLAIMER_DIAGNOSIS,
    DISCLAIMER_LEARNING,
    TIMELINESS_DIAGNOSIS,
    TIMELINESS_LEARNING,
    format_diagnosis_output,
    format_learning_output,
    render_agent_output,
)
from app.learning_agent import CHAIN_NODES, learn, learn_stream
from app.memory import MemoryCategory, build_memory_context, extract_transition_memories
from app.rag import KnowledgeItem


# ---------------------------------------------------------------------------
# Types
# ---------------------------------------------------------------------------

class AgentMode:
    LEARNING = "LEARNING"
    DIAGNOSIS = "DIAGNOSIS"


@dataclass
class TransitionContext:
    from_mode: str
    to_mode: str
    chain_node_id: str | None       # 当前学习/诊断节点，会成为目标 Agent 的关注点。
    preserved_context: str          # 切换前对话摘要。
    suggested_question: str          # 给前端展示的目标模式建议问题。


# ---------------------------------------------------------------------------
# Transition prompt builders
# ---------------------------------------------------------------------------

def build_transition_prompt(
    from_mode: str,
    to_mode: str,
    user_question: str,
    chain_node_id: str | None = None,
    conversation_summary: str | None = None,
) -> str:
    """构造连接两个模式的过渡提示词。

    提示词会交代用户在原模式做了什么、为什么切换，以及目标模式应聚焦哪里。
    """
    if from_mode == AgentMode.LEARNING and to_mode == AgentMode.DIAGNOSIS:
        return _learn_to_diagnose_prompt(user_question, chain_node_id, conversation_summary)
    elif from_mode == AgentMode.DIAGNOSIS and to_mode == AgentMode.LEARNING:
        return _diagnose_to_learn_prompt(user_question, chain_node_id, conversation_summary)
    else:
        # Direct mode (no transition needed) — return question as-is
        return user_question


def _learn_to_diagnose_prompt(
    question: str,
    chain_node_id: str | None,
    conversation_summary: str | None,
) -> str:
    """学习到诊断：把刚学过的链条节点转成诊断视角。"""
    node_name = _node_display_name(chain_node_id) if chain_node_id else "行业知识"

    parts = [
        "用户刚刚学习了以下行业知识：",
    ]
    if conversation_summary:
        parts.append(f"学习内容摘要：{conversation_summary}")
    parts.append(f"用户现在想用学到的{node_name}相关知识来诊断自己的实际经营问题。")
    parts.append(f"用户的诊断提问：{question}")
    parts.append("请结合用户学习过的行业知识，给出针对性更强的诊断分析。")
    if chain_node_id:
        parts.append(f"诊断应重点关注{node_name}相关维度。")
    return "\n".join(parts)


def _diagnose_to_learn_prompt(
    question: str,
    chain_node_id: str | None,
    conversation_summary: str | None,
) -> str:
    """诊断到学习：把诊断中暴露的薄弱点转成学习目标。"""
    node_name = _node_display_name(chain_node_id) if chain_node_id else "相关领域"

    parts = [
        "用户刚刚完成了经营诊断分析：",
    ]
    if conversation_summary:
        parts.append(f"诊断结论摘要：{conversation_summary}")
    parts.append(f"诊断显示用户在{node_name}方面存在薄弱环节或需要进一步了解。")
    parts.append(f"用户的学习提问：{question}")
    parts.append("请针对用户在诊断中暴露的薄弱环节，提供系统的行业知识学习指导。")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# Transition execution
# ---------------------------------------------------------------------------

def execute_transition(
    from_mode: str,
    to_mode: str,
    user_question: str,
    *,
    knowledge: list[KnowledgeItem],
    chain_node_id: str | None = None,
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
) -> dict:
    """执行双 Agent 模式切换。

    先构造过渡提示词，再把请求路由到目标 Agent，并追加切换记忆候选。
    """
    # 构造带切换上下文的问题，目标 Agent 不需要知道调用来自哪个端点。
    augmented_question = build_transition_prompt(
        from_mode=from_mode,
        to_mode=to_mode,
        user_question=user_question,
        chain_node_id=chain_node_id,
        conversation_summary=conversation_summary,
    )

    # 记录用户为什么切换模式；这类信号有助于后续个性化学习或诊断。
    previous_answer = _extract_last_assistant_content(recent_messages)
    transition_candidates = extract_transition_memories(
        from_mode=from_mode,
        to_mode=to_mode,
        user_question=user_question,
        previous_answer=previous_answer,
        chain_node_id=chain_node_id,
    )
    all_memories = (long_term_memories or []) + transition_candidates

    # 目标 Agent 仍能看到原模式摘要，避免上下文断裂。
    augmented_summary = _build_transition_summary(
        from_mode, to_mode, conversation_summary
    )

    # 按目标模式路由到对应 Agent。
    if to_mode == AgentMode.DIAGNOSIS:
        return diagnose(
            augmented_question,
            knowledge=knowledge,
            recent_messages=recent_messages,
            conversation_summary=augmented_summary,
            long_term_memories=all_memories,
            region_id=region_id,
            industry_id=industry_id,
            membership_level=membership_level,
            vector_store=vector_store,
            restrict_to_knowledge_ids=restrict_to_knowledge_ids,
        )
    elif to_mode == AgentMode.LEARNING:
        return learn(
            augmented_question,
            knowledge=knowledge,
            chain_node_id=chain_node_id,
            recent_messages=recent_messages,
            conversation_summary=augmented_summary,
            long_term_memories=all_memories,
            region_id=region_id,
            industry_id=industry_id,
            membership_level=membership_level,
            vector_store=vector_store,
            restrict_to_knowledge_ids=restrict_to_knowledge_ids,
        )
    else:
        raise ValueError(f"Unknown target mode: {to_mode}")


def execute_transition_stream(
    from_mode: str,
    to_mode: str,
    user_question: str,
    *,
    knowledge: list[KnowledgeItem],
    chain_node_id: str | None = None,
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
):
    """Stream a transition through the selected target Agent."""
    augmented_question = build_transition_prompt(
        from_mode=from_mode,
        to_mode=to_mode,
        user_question=user_question,
        chain_node_id=chain_node_id,
        conversation_summary=conversation_summary,
    )
    previous_answer = _extract_last_assistant_content(recent_messages)
    transition_candidates = extract_transition_memories(
        from_mode=from_mode,
        to_mode=to_mode,
        user_question=user_question,
        previous_answer=previous_answer,
        chain_node_id=chain_node_id,
    )
    all_memories = (long_term_memories or []) + transition_candidates
    augmented_summary = _build_transition_summary(
        from_mode, to_mode, conversation_summary
    )

    if to_mode == AgentMode.DIAGNOSIS:
        yield from diagnose_stream(
            augmented_question,
            knowledge=knowledge,
            recent_messages=recent_messages,
            conversation_summary=augmented_summary,
            long_term_memories=all_memories,
            region_id=region_id,
            industry_id=industry_id,
            membership_level=membership_level,
            vector_store=vector_store,
            restrict_to_knowledge_ids=restrict_to_knowledge_ids,
        )
        return
    if to_mode == AgentMode.LEARNING:
        yield from learn_stream(
            augmented_question,
            knowledge=knowledge,
            chain_node_id=chain_node_id,
            recent_messages=recent_messages,
            conversation_summary=augmented_summary,
            long_term_memories=all_memories,
            region_id=region_id,
            industry_id=industry_id,
            membership_level=membership_level,
            vector_store=vector_store,
            restrict_to_knowledge_ids=restrict_to_knowledge_ids,
        )
        return
    raise ValueError(f"Unknown target mode: {to_mode}")


# ---------------------------------------------------------------------------
# Transition context builder
# ---------------------------------------------------------------------------

def build_transition_context(
    from_mode: str,
    to_mode: str,
    chain_node_id: str | None = None,
    conversation_summary: str | None = None,
) -> TransitionContext:
    """构造给调用方展示的轻量切换预览。

    真正的切换执行由 execute_transition() 完成。
    """
    node_name = _node_display_name(chain_node_id) if chain_node_id else "当前领域"
    preserved = conversation_summary or "无先前对话摘要"

    if from_mode == AgentMode.LEARNING and to_mode == AgentMode.DIAGNOSIS:
        suggested = f"基于我学到的{node_name}知识，帮我诊断一下我的实际经营情况"
    elif from_mode == AgentMode.DIAGNOSIS and to_mode == AgentMode.LEARNING:
        suggested = f"帮我系统学习一下诊断中发现的薄弱环节——{node_name}"
    else:
        suggested = ""

    return TransitionContext(
        from_mode=from_mode,
        to_mode=to_mode,
        chain_node_id=chain_node_id,
        preserved_context=preserved,
        suggested_question=suggested,
    )


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _build_transition_summary(
    from_mode: str,
    to_mode: str,
    existing_summary: str | None,
) -> str | None:
    """在会话摘要中追加模式切换元信息。"""
    parts = []
    if existing_summary:
        parts.append(existing_summary)
    parts.append(f"[模式切换: {from_mode} → {to_mode}]")
    return " | ".join(parts)


def _node_display_name(node_id: str | None) -> str:
    """将链条节点 ID 转成人类可读名称。"""
    if node_id is None:
        return "未知"
    for slug, name, _ in CHAIN_NODES:
        if slug == node_id:
            return name
    return node_id


def _extract_last_assistant_content(
    recent_messages: list[dict] | None,
) -> str | None:
    """从最近消息中取出最后一条助手回答。

    切换记忆提取会用它理解“刚刚讨论了什么”。
    """
    if not recent_messages:
        return None
    for msg in reversed(recent_messages):
        if msg.get("role") == "assistant":
            content = msg.get("content", "")
            if content:
                return content
    return None
