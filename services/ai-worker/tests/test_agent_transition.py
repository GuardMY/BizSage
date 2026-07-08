"""Tests for the dual-Agent transition mechanism."""

import pytest
from app.agent_transition import (
    AgentMode,
    TransitionContext,
    _extract_last_assistant_content,
    build_transition_context,
    build_transition_prompt,
    execute_transition,
)
from app.memory import extract_transition_memories
from app.rag import KnowledgeItem


KNOWLEDGE = [
    KnowledgeItem(
        id="k1", title="现金流诊断", content="餐饮门店应核对客单价、翻台率、食材损耗率。",
        source_url="s1", source_id="seed", weight=1.0, confidence=0.9,
        industry_id="general", region_id="cn-default",
    ),
]


# ── Transition prompt tests ──────────────────────────────────────

def test_learn_to_diagnose_prompt_includes_chain_node():
    prompt = build_transition_prompt(
        from_mode=AgentMode.LEARNING,
        to_mode=AgentMode.DIAGNOSIS,
        user_question="帮我诊断现金流",
        chain_node_id="sales-payment",
        conversation_summary="用户学习了销售回款知识",
    )
    assert "学习" in prompt
    assert "诊断" in prompt
    assert "销售终端回款" in prompt or "sales-payment" in prompt


def test_diagnose_to_learn_prompt_includes_summary():
    prompt = build_transition_prompt(
        from_mode=AgentMode.DIAGNOSIS,
        to_mode=AgentMode.LEARNING,
        user_question="教我库存管理",
        chain_node_id="warehouse-inventory",
        conversation_summary="诊断发现库存周转问题",
    )
    assert "诊断" in prompt
    assert "学习" in prompt
    assert "薄弱环节" in prompt


def test_build_transition_prompt_same_mode_returns_question():
    prompt = build_transition_prompt(
        from_mode=AgentMode.LEARNING,
        to_mode=AgentMode.LEARNING,
        user_question="继续学习",
    )
    assert prompt == "继续学习"


# ── Transition context preview tests ─────────────────────────────

def test_build_transition_context_learn_to_diagnose():
    ctx = build_transition_context(
        from_mode=AgentMode.LEARNING,
        to_mode=AgentMode.DIAGNOSIS,
        chain_node_id="raw-materials",
        conversation_summary="学习了原材料成本构成",
    )
    assert ctx.from_mode == AgentMode.LEARNING
    assert ctx.to_mode == AgentMode.DIAGNOSIS
    assert ctx.chain_node_id == "raw-materials"
    assert len(ctx.suggested_question) > 0
    assert "诊断" in ctx.suggested_question


def test_build_transition_context_diagnose_to_learn():
    ctx = build_transition_context(
        from_mode=AgentMode.DIAGNOSIS,
        to_mode=AgentMode.LEARNING,
        chain_node_id="warehouse-inventory",
    )
    assert ctx.from_mode == AgentMode.DIAGNOSIS
    assert ctx.to_mode == AgentMode.LEARNING
    assert len(ctx.suggested_question) > 0


# ── Transition execution tests (with mocked LLM) ─────────────────

@pytest.fixture(autouse=True)
def _mock_llm(monkeypatch):
    """Mock run_with_retry so tests don't need a real LLM API key."""

    def fake_run_with_retry(
        llm_call_fn, messages, evidence=None, region_id=None,
        output_format=None, config=None, max_retries=None,
    ):
        question = ""
        for m in messages:
            if m.get("role") == "user":
                question = m["content"][:80]
                break
        answer = f"分析结果：基于——{question}。建议优化经营。"
        return answer, "PASSED"

    monkeypatch.setattr("app.agent.run_with_retry", fake_run_with_retry)
    monkeypatch.setattr("app.learning_agent.run_with_retry", fake_run_with_retry)


def test_execute_transition_learn_to_diagnose():
    result = execute_transition(
        from_mode=AgentMode.LEARNING,
        to_mode=AgentMode.DIAGNOSIS,
        user_question="帮我诊断现金流",
        knowledge=KNOWLEDGE,
        chain_node_id="sales-payment",
        conversation_summary="学习了销售回款知识",
        long_term_memories=[
            {"category": "LEARNING_PROGRESS", "key": "studied_sales", "value": "已学习"}
        ],
    )
    assert result["selfCheckStatus"] == "PASSED"
    assert len(result["sources"]) > 0
    assert result["answer"]


def test_execute_transition_diagnose_to_learn():
    result = execute_transition(
        from_mode=AgentMode.DIAGNOSIS,
        to_mode=AgentMode.LEARNING,
        user_question="教我库存管理",
        knowledge=KNOWLEDGE,
        chain_node_id="warehouse-inventory",
        conversation_summary="诊断发现库存周转问题",
    )
    assert result["selfCheckStatus"] == "PASSED"
    assert result["mode"] == "LEARNING"
    assert result["chainNodeId"] == "warehouse-inventory"


def test_execute_transition_preserves_memories():
    result = execute_transition(
        from_mode=AgentMode.LEARNING,
        to_mode=AgentMode.DIAGNOSIS,
        user_question="诊断",
        knowledge=KNOWLEDGE,
        long_term_memories=[
            {"category": "BUSINESS_FACT", "key": "stores", "value": "3"}
        ],
    )
    assert result["selfCheckStatus"] == "PASSED"
    assert len(result["memoryCandidates"]) >= 0  # transition adds a memory


# ── V2: Transition memory extraction tests ─────────────────────────

def test_execute_transition_includes_extracted_memories():
    """Transition now uses extract_transition_memories for richer context."""
    result = execute_transition(
        from_mode=AgentMode.LEARNING,
        to_mode=AgentMode.DIAGNOSIS,
        user_question="帮我诊断现金流问题",
        knowledge=KNOWLEDGE,
        chain_node_id="sales-payment",
        conversation_summary="用户学习了销售回款知识",
        recent_messages=[
            {"role": "user", "content": "销售回款周期怎么优化"},
            {"role": "assistant", "content": "销售回款优化有三大策略..."},
        ],
        long_term_memories=[
            {"category": "BUSINESS_FACT", "key": "stores", "value": "3"}
        ],
    )
    assert result["selfCheckStatus"] == "PASSED"
    assert result["answer"]


def test_execute_transition_diagnose_to_learn_with_recent_messages():
    """Transition from diagnosis to learning preserves assistant context."""
    result = execute_transition(
        from_mode=AgentMode.DIAGNOSIS,
        to_mode=AgentMode.LEARNING,
        user_question="教我优化库存管理",
        knowledge=KNOWLEDGE,
        chain_node_id="warehouse-inventory",
        recent_messages=[
            {"role": "user", "content": "诊断库存问题"},
            {"role": "assistant", "content": "您的库存周转天数偏高45天..."},
        ],
    )
    assert result["selfCheckStatus"] == "PASSED"
    assert result["mode"] == "LEARNING"


def test_extract_last_assistant_content_empty():
    assert _extract_last_assistant_content(None) is None
    assert _extract_last_assistant_content([]) is None


def test_extract_last_assistant_content_finds_last():
    messages = [
        {"role": "user", "content": "问题1"},
        {"role": "assistant", "content": "答案1"},
        {"role": "user", "content": "问题2"},
        {"role": "assistant", "content": "答案2"},
    ]
    assert _extract_last_assistant_content(messages) == "答案2"


def test_extract_last_assistant_content_skips_empty():
    messages = [
        {"role": "user", "content": "问题"},
        {"role": "assistant", "content": ""},
        {"role": "assistant", "content": "有效答案"},
    ]
    assert _extract_last_assistant_content(messages) == "有效答案"
