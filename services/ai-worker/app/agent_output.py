from __future__ import annotations

from dataclasses import dataclass, field

DISCLAIMER_LEARNING = (
    "免责声明：本学习内容基于行业公开知识和已入库情报生成，仅供商业知识学习参考，不构成投资、法律或财务建议。"
)

DISCLAIMER_DIAGNOSIS = (
    "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。"
)

TIMELINESS_LEARNING = "基于BizSage静态基线知识和已入库情报生成，知识更新可能存在延迟。"
TIMELINESS_DIAGNOSIS = "基于V1静态基线知识和已入库情报生成。"

SELF_CHECK_FAILED = "SELF_CHECK_FAILED"


@dataclass
class OutputSections:
    key_findings: str = ""
    risk_alerts: str = ""
    actionable_steps: str = ""
    supporting_evidence: str = ""


@dataclass
class SourceRef:
    id: str
    title: str
    source_url: str
    source_id: str
    confidence: float
    score: float
    entitlement: str = "FREE"


@dataclass
class AgentOutput:
    mode: str
    answer: str
    sections: OutputSections = field(default_factory=OutputSections)
    sources: list[SourceRef] = field(default_factory=list)
    confidence: str = "MEDIUM"
    timeliness: str = ""
    self_check_status: str = "PASSED"
    disclaimer: str = ""
    chain_node_id: str | None = None
    suggested_actions: list[str] = field(default_factory=list)
    memory_candidates: list[dict] = field(default_factory=list)
    recommendation_candidates: list[dict] = field(default_factory=list)
    current_topic: str | None = None
    next_best_topics: list[str] = field(default_factory=list)
    workflow_stage: str = "INTRO"
    profile_missing_fields: list[str] = field(default_factory=list)
    completion_signal: str | None = None
    recommended_questions: list[dict] = field(default_factory=list)


def format_learning_output(
    answer: str,
    sources: list[SourceRef],
    chain_node_id: str | None = None,
    memory_candidates: list[dict] | None = None,
    *,
    recommendation_candidates: list[dict] | None = None,
    current_topic: str | None = None,
    next_best_topics: list[str] | None = None,
    workflow_stage: str = "INTRO",
    profile_missing_fields: list[str] | None = None,
    completion_signal: str | None = None,
    key_findings: str = "",
    risk_alerts: str = "",
    actionable_steps: str = "",
    supporting_evidence: str = "",
    confidence: str = "MEDIUM",
    self_check_status: str = "PASSED",
) -> AgentOutput:
    return AgentOutput(
        mode="LEARNING",
        answer=answer,
        sections=OutputSections(
            key_findings=key_findings or _extract_section(answer, "关键发现"),
            risk_alerts=risk_alerts or _extract_section(answer, "风险提示"),
            actionable_steps=actionable_steps or _extract_section(answer, "可行动建议"),
            supporting_evidence=supporting_evidence or _extract_section(answer, "证据支撑"),
        ),
        sources=sources,
        confidence=confidence,
        timeliness=TIMELINESS_LEARNING,
        self_check_status=self_check_status,
        disclaimer=DISCLAIMER_LEARNING,
        chain_node_id=chain_node_id,
        suggested_actions=_build_learning_suggestions(chain_node_id),
        memory_candidates=memory_candidates or [],
        recommendation_candidates=recommendation_candidates or [],
        current_topic=current_topic,
        next_best_topics=next_best_topics or [],
        workflow_stage=workflow_stage,
        profile_missing_fields=profile_missing_fields or [],
        completion_signal=completion_signal,
    )


def format_diagnosis_output(
    answer: str,
    sources: list[SourceRef],
    memory_candidates: list[dict] | None = None,
    *,
    recommended_questions: list[dict] | None = None,
    workflow_stage: str = "INTRO",
    profile_missing_fields: list[str] | None = None,
    completion_signal: str | None = None,
    key_findings: str = "",
    risk_alerts: str = "",
    actionable_steps: str = "",
    supporting_evidence: str = "",
    confidence: str = "MEDIUM",
    self_check_status: str = "PASSED",
) -> AgentOutput:
    return AgentOutput(
        mode="DIAGNOSIS",
        answer=answer,
        sections=OutputSections(
            key_findings=key_findings or _extract_section(answer, "关键发现"),
            risk_alerts=risk_alerts or _extract_section(answer, "风险提示"),
            actionable_steps=actionable_steps or _extract_section(answer, "可行动建议"),
            supporting_evidence=supporting_evidence or _extract_section(answer, "证据支撑"),
        ),
        sources=sources,
        confidence=confidence,
        timeliness=TIMELINESS_DIAGNOSIS,
        self_check_status=self_check_status,
        disclaimer=DISCLAIMER_DIAGNOSIS,
        memory_candidates=memory_candidates or [],
        workflow_stage=workflow_stage,
        profile_missing_fields=profile_missing_fields or [],
        completion_signal=completion_signal,
        recommended_questions=recommended_questions or [],
    )


def render_agent_output(output: AgentOutput) -> dict:
    return {
        "mode": output.mode,
        "answer": output.answer,
        "sections": {
            "keyFindings": output.sections.key_findings,
            "riskAlerts": output.sections.risk_alerts,
            "actionableSteps": output.sections.actionable_steps,
            "supportingEvidence": output.sections.supporting_evidence,
        },
        "sources": [
            {
                "id": s.id,
                "title": s.title,
                "sourceUrl": s.source_url,
                "sourceId": s.source_id,
                "confidence": s.confidence,
                "score": s.score,
                "entitlement": s.entitlement,
            }
            for s in output.sources
        ],
        "confidence": output.confidence,
        "timeliness": output.timeliness,
        "selfCheckStatus": output.self_check_status,
        "disclaimer": output.disclaimer,
        "chainNodeId": output.chain_node_id,
        "suggestedActions": output.suggested_actions,
        "memoryCandidates": output.memory_candidates,
        "recommendationCandidates": output.recommendation_candidates,
        "currentTopic": output.current_topic,
        "nextBestTopics": output.next_best_topics,
        "workflowStage": output.workflow_stage,
        "profileMissingFields": output.profile_missing_fields,
        "completionSignal": output.completion_signal,
        "recommendedQuestions": output.recommended_questions,
    }


def render_legacy_format(output: AgentOutput) -> dict:
    return {
        "answer": output.answer,
        "sources": [
            {
                "id": s.id,
                "title": s.title,
                "sourceUrl": s.source_url,
                "sourceId": s.source_id,
                "confidence": s.confidence,
                "score": s.score,
                "entitlement": s.entitlement,
            }
            for s in output.sources
        ],
        "confidence": output.confidence,
        "timeliness": output.timeliness,
        "selfCheckStatus": output.self_check_status,
        "disclaimer": output.disclaimer,
        "memoryCandidates": output.memory_candidates,
    }


def _extract_section(text: str, label: str) -> str:
    for prefix in (f"{label}：", f"{label}:", f"【{label}】"):
        if prefix in text:
            _, rest = text.split(prefix, 1)
            for terminator in ("\n2", "\n3", "\n4", "\n【", "\n\n关键", "\n\n风险", "\n\n可行动", "\n\n证据"):
                if terminator in rest:
                    rest = rest.split(terminator, 1)[0]
            return rest.strip()
    return ""


def _build_learning_suggestions(chain_node_id: str | None) -> list[str]:
    node_suggestions = {
        "raw-materials": ["了解原材料成本结构", "探索供应链上游风险", "进入生产制造环节学习"],
        "production": ["了解制造工艺与产能", "探索质量管控要点", "进入仓储库存环节学习"],
        "quality-control": ["了解质检标准体系", "探索合规风险红线", "进入仓储库存环节学习"],
        "warehouse-inventory": ["了解库存周转指标", "探索安全库存策略", "进入物流流通环节学习"],
        "logistics": ["了解物流成本结构", "探索时效管控方法", "进入渠道运营环节学习"],
        "channel-operations": ["了解代理层级体系", "探索返利策略设计", "进入销售终端环节学习"],
        "sales-payment": ["了解回款周期管理", "探索定价策略优化", "查看全链条总结"],
    }
    default = ["了解行业概况", "选择感兴趣的链条节点", "深入探索具体指标"]
    return node_suggestions.get(chain_node_id or "", default)
