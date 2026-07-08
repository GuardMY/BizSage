"""Dual-Agent transition — context-preserving mode switch.

Enables users to seamlessly move between Learning and Diagnosis modes
without losing their conversational context or industry focus.

Transitions:
- **LEARNING → DIAGNOSIS**: "Based on what you learned about X, let's diagnose your actual business."
  The current chain node becomes the diagnosis focus area.
- **DIAGNOSIS → LEARNING**: "Your diagnosis shows weakness in Y. Let me teach you about best practices."
  The weak area identified in diagnosis becomes the learning topic.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from app.agent import diagnose
from app.agent_output import (
    DISCLAIMER_DIAGNOSIS,
    DISCLAIMER_LEARNING,
    TIMELINESS_DIAGNOSIS,
    TIMELINESS_LEARNING,
    format_diagnosis_output,
    format_learning_output,
    render_agent_output,
)
from app.learning_agent import CHAIN_NODES, learn
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
    chain_node_id: str | None       # node being studied → becomes diagnosis focus
    preserved_context: str          # summary of what was discussed before transition
    suggested_question: str          # auto-generated question for the target Agent


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
    """Build a transition-aware prompt that connects the two modes.

    The prompt includes:
    - What the user was doing in the previous mode
    - Why they're switching
    - What the target mode should focus on
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
    """Build prompt: Learning → Diagnosis."""
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
    """Build prompt: Diagnosis → Learning."""
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
    """Execute a dual-Agent transition.

    Builds a transition-aware prompt and routes to the target Agent.
    """
    # ── Build transition-aware prompt ──
    augmented_question = build_transition_prompt(
        from_mode=from_mode,
        to_mode=to_mode,
        user_question=user_question,
        chain_node_id=chain_node_id,
        conversation_summary=conversation_summary,
    )

    # ── Extract transition memories and append to long-term memories ──
    # Captures why the user is switching and what they learned/diagnosed
    previous_answer = _extract_last_assistant_content(recent_messages)
    transition_candidates = extract_transition_memories(
        from_mode=from_mode,
        to_mode=to_mode,
        user_question=user_question,
        previous_answer=previous_answer,
        chain_node_id=chain_node_id,
    )
    all_memories = (long_term_memories or []) + transition_candidates

    # Allow the target Agent to also see recent messages from the previous mode
    augmented_summary = _build_transition_summary(
        from_mode, to_mode, conversation_summary
    )

    # ── Route to target Agent ──
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


# ---------------------------------------------------------------------------
# Transition context builder
# ---------------------------------------------------------------------------

def build_transition_context(
    from_mode: str,
    to_mode: str,
    chain_node_id: str | None = None,
    conversation_summary: str | None = None,
) -> TransitionContext:
    """Build a TransitionContext for the caller (e.g., Web frontend) to display.

    This is a lightweight preview — the full transition is handled by
    ``execute_transition()``.
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
    """Augment conversation summary with transition metadata."""
    parts = []
    if existing_summary:
        parts.append(existing_summary)
    parts.append(f"[模式切换: {from_mode} → {to_mode}]")
    return " | ".join(parts)


def _node_display_name(node_id: str | None) -> str:
    if node_id is None:
        return "未知"
    for slug, name, _ in CHAIN_NODES:
        if slug == node_id:
            return name
    return node_id


def _extract_last_assistant_content(
    recent_messages: list[dict] | None,
) -> str | None:
    """Extract the last assistant message content from recent messages.

    Used during transitions to provide context about what was just discussed
    in the previous mode, enabling richer transition memory extraction.
    """
    if not recent_messages:
        return None
    for msg in reversed(recent_messages):
        if msg.get("role") == "assistant":
            content = msg.get("content", "")
            if content:
                return content
    return None
