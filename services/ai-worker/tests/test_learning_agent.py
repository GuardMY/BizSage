"""Tests for the Industry Learning Agent."""

import pytest
from app.learning_agent import (
    CHAIN_NODES,
    LearningMode,
    IntentType,
    classify_learning_intent,
    filter_knowledge_by_node,
    learn,
    _node_display_name,
)
from app.rag import KnowledgeItem


# ── Test knowledge ───────────────────────────────────────────────

KNOWLEDGE = [
    KnowledgeItem(
        id="k1", title="原材料成本构成", content="原材料成本包括采购价、运输费和关税。",
        source_url="s1", source_id="seed", weight=1.0, confidence=0.9,
        industry_id="general", region_id="cn-default", link_id="raw-materials",
    ),
    KnowledgeItem(
        id="k2", title="库存周转优化", content="库存周转天数控制在30天以内为健康水平。",
        source_url="s2", source_id="seed", weight=0.85, confidence=0.85,
        industry_id="general", region_id="cn-default", link_id="warehouse-inventory",
    ),
    KnowledgeItem(
        id="k3", title="销售回款策略", content="缩短账期可以显著改善现金流。",
        source_url="s3", source_id="seed", weight=0.9, confidence=0.88,
        industry_id="general", region_id="cn-default", link_id="sales-payment",
    ),
]


# ── Intent classification tests ──────────────────────────────────

def test_classify_industry_overview():
    intent, node = classify_learning_intent("我想了解这个行业的基本情况")
    assert intent == IntentType.INDUSTRY_OVERVIEW
    assert node is None


def test_classify_node_learning_raw_materials():
    intent, node = classify_learning_intent("原材料的采购成本怎么控制")
    assert intent == IntentType.NODE_LEARNING
    assert node == "raw-materials"


def test_classify_node_learning_warehouse():
    intent, node = classify_learning_intent("如何优化库存周转天数")
    assert intent == IntentType.NODE_LEARNING
    assert node == "warehouse-inventory"


def test_classify_metric_question():
    intent, node = classify_learning_intent("这个行业的平均利润率是多少")
    assert intent == IntentType.METRIC_QUESTION


def test_classify_risk_question():
    intent, node = classify_learning_intent("做这个行业有哪些合规风险红线")
    assert intent == IntentType.RISK_QUESTION


def test_classify_hidden_rule():
    intent, node = classify_learning_intent("行业里有哪些潜规则和默认让步")
    assert intent == IntentType.HIDDEN_RULE


def test_classify_policy_question():
    intent, node = classify_learning_intent("最新的行业监管政策是什么")
    assert intent == IntentType.POLICY_QUESTION


def test_classify_fallback_to_overview():
    intent, node = classify_learning_intent("你好")
    assert intent == IntentType.INDUSTRY_OVERVIEW


# ── Knowledge filtering tests ────────────────────────────────────

def test_filter_knowledge_by_node_filters_correctly():
    filtered = filter_knowledge_by_node(KNOWLEDGE, "raw-materials")
    assert len(filtered) == 1
    assert filtered[0].id == "k1"


def test_filter_knowledge_none_returns_all():
    filtered = filter_knowledge_by_node(KNOWLEDGE, None)
    assert len(filtered) == 3


def test_filter_knowledge_unknown_node_returns_empty():
    filtered = filter_knowledge_by_node(KNOWLEDGE, "nonexistent-node")
    assert len(filtered) == 0


# ── Learning Agent tests (with mocked LLM) ───────────────────────

@pytest.fixture(autouse=True)
def _mock_llm(monkeypatch):
    """Mock run_with_retry so tests don't need a real LLM API key or ModelRouter."""

    def fake_run_with_retry(
        llm_call_fn, messages, evidence=None, region_id=None,
        output_format=None, config=None, max_retries=None,
    ):
        answer = (
            "关于学习指南。"
            "核心概念：行业基础知识概述。"
            "实践案例：真实商业场景中的应用。"
            "关键指标：关注成本、效率和合规。"
            "下一步建议：深入学习具体链条节点。"
        )
        return answer, "PASSED"

    monkeypatch.setattr("app.learning_agent.run_with_retry", fake_run_with_retry)


def test_learn_returns_structured_output():
    result = learn(
        "原材料成本怎么控制",
        knowledge=KNOWLEDGE,
        chain_node_id="raw-materials",
        learning_mode=LearningMode.FAST_START,
    )
    assert result["mode"] == "LEARNING"
    assert "answer" in result
    assert "sources" in result
    assert result["chainNodeId"] == "raw-materials"
    assert result["confidence"] == "MEDIUM"
    assert result["disclaimer"]
    assert "suggestedActions" in result
    assert result["selfCheckStatus"] == "PASSED"


def test_learn_insufficient_evidence_when_no_knowledge():
    result = learn("新能源补贴政策", knowledge=[])
    assert result["selfCheckStatus"] == "INSUFFICIENT_EVIDENCE"
    assert result["confidence"] == "LOW"
    assert result["sources"] == []


def test_learn_auto_detects_chain_node():
    result = learn(
        "库存管理有什么技巧",
        knowledge=KNOWLEDGE,
    )
    assert result["chainNodeId"] == "warehouse-inventory"


# ── Chain node display ───────────────────────────────────────────

def test_node_display_name_known():
    assert _node_display_name("raw-materials") == "原材料"


def test_node_display_name_unknown():
    assert _node_display_name("nonexistent") == "nonexistent"


def test_node_display_name_none():
    assert _node_display_name(None) == "未知"


def test_all_seven_nodes_have_names():
    assert len(CHAIN_NODES) == 7
    for slug, name, desc in CHAIN_NODES:
        assert len(slug) > 0
        assert len(name) > 0
        assert len(desc) > 0
