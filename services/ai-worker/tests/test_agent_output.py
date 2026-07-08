"""Tests for the standardized Agent output formatter."""

import pytest
from app.agent_output import (
    DISCLAIMER_LEARNING,
    DISCLAIMER_DIAGNOSIS,
    AgentOutput,
    SourceRef,
    format_diagnosis_output,
    format_learning_output,
    render_agent_output,
    render_legacy_format,
)


def test_format_learning_output_basic():
    output = format_learning_output(
        answer="原材料成本是制造业的核心成本之一。",
        sources=[
            SourceRef(id="k1", title="原材料成本", source_url="s1", source_id="seed",
                       confidence=0.9, score=0.95),
        ],
        chain_node_id="raw-materials",
        confidence="MEDIUM",
        self_check_status="PASSED",
    )
    assert output.mode == "LEARNING"
    assert output.chain_node_id == "raw-materials"
    assert output.disclaimer == DISCLAIMER_LEARNING
    assert len(output.sources) == 1
    assert len(output.suggested_actions) > 0


def test_format_diagnosis_output_basic():
    output = format_diagnosis_output(
        answer="您的库存周转天数偏高，建议优化安全库存水平。",
        sources=[
            SourceRef(id="k1", title="库存风险", source_url="s1", source_id="seed",
                       confidence=0.85, score=0.90),
        ],
        confidence="MEDIUM",
        self_check_status="PASSED",
    )
    assert output.mode == "DIAGNOSIS"
    assert output.chain_node_id is None
    assert output.disclaimer == DISCLAIMER_DIAGNOSIS
    assert len(output.suggested_actions) == 0


def test_render_agent_output_includes_all_fields():
    output = format_learning_output(
        answer="测试回答内容。",
        sources=[],
        chain_node_id="production",
    )
    result = render_agent_output(output)
    assert result["mode"] == "LEARNING"
    assert "sections" in result
    assert result["sections"]["keyFindings"] is not None
    assert result["sections"]["riskAlerts"] is not None
    assert result["sections"]["actionableSteps"] is not None
    assert result["sections"]["supportingEvidence"] is not None
    assert result["confidence"] == "MEDIUM"
    assert result["timeliness"]
    assert result["disclaimer"]
    assert result["chainNodeId"] == "production"
    assert "suggestedActions" in result
    assert "memoryCandidates" in result


def test_render_legacy_format_backward_compatible():
    output = format_diagnosis_output(
        answer="诊断结果。",
        sources=[SourceRef(id="k1", title="T", source_url="", source_id="s",
                            confidence=0.8, score=0.9)],
        confidence="MEDIUM",
        self_check_status="PASSED",
    )
    result = render_legacy_format(output)
    assert "answer" in result
    assert "sources" in result
    assert "confidence" in result
    assert "timeliness" in result
    assert "selfCheckStatus" in result
    assert "disclaimer" in result
    assert "memoryCandidates" in result
    # Legacy format does NOT include mode, chainNodeId, suggestedActions, sections
    assert "mode" not in result
    assert "chainNodeId" not in result


def test_learning_output_includes_memory_candidates():
    output = format_learning_output(
        answer="学习内容。",
        sources=[],
        memory_candidates=[
            {"category": "LEARNING_PROGRESS", "key": "studied_node", "value": "test"},
        ],
    )
    assert len(output.memory_candidates) == 1


def test_suggested_actions_vary_by_node():
    raw = format_learning_output("答", [], chain_node_id="raw-materials")
    warehouse = format_learning_output("答", [], chain_node_id="warehouse-inventory")
    unknown = format_learning_output("答", [], chain_node_id=None)

    assert raw.suggested_actions != warehouse.suggested_actions
    assert len(unknown.suggested_actions) > 0
