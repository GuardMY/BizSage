from __future__ import annotations

import json
from collections.abc import Iterator

from app.context_compressor import CompressConfig, compress_context
from app.llm import LLMCallError, LLMNotConfiguredError
from app.memory import build_memory_context, extract_diagnosis_memories
from app.model_routing.router import ModelRouter
from app.prompt_library.assembler import PromptAssembler
from app.prompt_library.layers import AgentMode
from app.rag import KnowledgeItem, search_knowledge
from app.reasoning_checks.retry import run_with_retry, run_with_retry_stream

DISCLAIMER = "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。"
TIMELINESS = "基于V1静态基线知识生成。"

LLM_NOT_CONFIGURED = "LLM_NOT_CONFIGURED"
LLM_CALL_FAILED = "LLM_CALL_FAILED"

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


def diagnose(
    question: str,
    *,
    knowledge: list[KnowledgeItem],
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    diagnosis_memories: list[dict] | None = None,
    diagnosis_completeness: float | None = None,
    diagnosis_missing_fields: list[str] | None = None,
    profile_missing_fields_for_report: list[str] | None = None,
    additional_information_questions: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    conflict_labels: list[str] | None = None,
    agent_mode: str = "DIAGNOSIS",
    workflow_stage: str = "INTRO",
    profile_missing_fields: list[str] | None = None,
    recommended_question_ids: list[str] | None = None,
    diagnosis_closable: bool = False,
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
    compress_config: CompressConfig | None = None,
) -> dict:
    if conflict_labels:
        return _diagnosis_error(
            "当前信息存在冲突或缺少权威支撑，需要先补充后再给出结论。",
            "NEEDS_REVIEW",
            workflow_stage,
            profile_missing_fields,
        )

    results = search_knowledge(
        question,
        knowledge,
        region_id=region_id,
        industry_id=industry_id,
        membership_level=membership_level,
        vector_store=vector_store,
        restrict_to_knowledge_ids=restrict_to_knowledge_ids,
    )
    if not results:
        return _diagnosis_error(
            "信息不足：当前知识库没有检索到可支撑该问题的证据，请补充行业、地点或经营数据后重试。",
            "INSUFFICIENT_EVIDENCE",
            workflow_stage,
            profile_missing_fields,
        )

    result_dicts = _results_to_dicts(results)
    memory_context = build_memory_context(
        recent_messages,
        conversation_summary,
        (long_term_memories or []) + (diagnosis_memories or []),
    )
    compress_cfg = compress_config or CompressConfig(total_token_budget=2100 if memory_context else 2400)
    compressed_text, _ = compress_context(result_dicts, config=compress_cfg)
    context = f"{memory_context}\n\n{compressed_text}" if memory_context else compressed_text

    system_prompt = _get_assembler().assemble(mode=AgentMode.DIAGNOSIS.value, industry_id=industry_id, region_id=region_id)
    router = _get_router()

    def llm_call(messages: list[dict]) -> str:
        result = router.call(messages=messages, task_hint="balanced", temperature=0.3, max_tokens=1024)
        return result.response_text

    initial_messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"请先自我介绍，并说明会先建立经营画像再分层诊断。\n\n问题：{question}\n\n参考证据：\n{context}"},
    ]
    initial_messages.append({"role": "system", "content": "Return only valid JSON with fields answer, diagnosisCompleteness, userProfileMemories, diagnosisMemories, diagnosisMissingFields, profileMissingFields, additionalInformationQuestions, reportReady. Memory items must contain category, key, value, confidence, structured. Return at least 10 additionalInformationQuestions, each with id, questionText, purpose, priority. answer is Markdown for the user. For diagnosis-related input, use the current user input and prior context to extract facts. If the available information is sufficient for a report, answer must include the exact sentence \u3010持有的信息已足够生成诊断报告\u3011. Otherwise, answer must ask the next highest-value question needed to complete the diagnosis. For non-diagnosis input, answer normally and leave memory fields empty when appropriate. Current persisted diagnosis state: completeness=" + str(diagnosis_completeness) + ", diagnosisMissingFields=" + str(diagnosis_missing_fields or []) + ", profileMissingFields=" + str(profile_missing_fields_for_report or []) + ", additionalInformationQuestions=" + str(additional_information_questions or [] )})

    try:
        answer = ""
        check_status = "LLM_CALL_FAILED"
        structured = None
        for _ in range(2):
            answer, check_status = run_with_retry(
                llm_call_fn=llm_call,
                messages=initial_messages,
                evidence=result_dicts,
                region_id=region_id,
                output_format=system_prompt,
            )
            try:
                structured = _parse_structured_response(answer)
                break
            except ValueError:
                initial_messages.append({
                    "role": "user",
                    "content": "The previous response was invalid. Return the complete diagnosis contract as valid JSON only, including at least 10 additionalInformationQuestions.",
                })
        if structured is None:
            return _diagnosis_error("", LLM_CALL_FAILED, workflow_stage, profile_missing_fields)
    except LLMNotConfiguredError:
        return _diagnosis_error("", LLM_NOT_CONFIGURED, workflow_stage, profile_missing_fields)
    except LLMCallError:
        return _diagnosis_error("", LLM_CALL_FAILED, workflow_stage, profile_missing_fields)

    return {
        "mode": "DIAGNOSIS",
        "answer": structured["answer"],
        "sources": _results_to_sources(results),
        "confidence": "MEDIUM",
        "timeliness": TIMELINESS,
        "selfCheckStatus": check_status,
        "disclaimer": DISCLAIMER,
        "memoryCandidates": structured["userProfileMemories"],
        "userProfileMemories": structured["userProfileMemories"],
        "diagnosisMemories": structured["diagnosisMemories"],
        "diagnosisCompleteness": structured["diagnosisCompleteness"],
        "diagnosisMissingFields": structured["diagnosisMissingFields"],
        "profileMissingFields": structured["profileMissingFields"],
        "additionalInformationQuestions": structured["additionalInformationQuestions"],
        "reportReady": structured["reportReady"],
        "workflowStage": workflow_stage,
        "profileMissingFields": structured["profileMissingFields"],
        "completionSignal": "READY" if diagnosis_closable else "CONTINUE",
        "recommendedQuestions": [
            {
                "id": item.id,
                "questionKey": item.source_id,
                "category": "profile",
                "questionText": item.title,
                "score": item.score,
                "topLevelScore": item.score,
                "usageCount": 0,
                "ratingAvg": 0,
                "ratingCount": 0,
                "sourceType": "rag",
                "sourceRef": item.source_id,
            }
            for item in []
        ],
    }


def diagnose_stream(
    question: str,
    *,
    knowledge: list[KnowledgeItem],
    recent_messages: list[dict] | None = None,
    conversation_summary: str | None = None,
    long_term_memories: list[dict] | None = None,
    diagnosis_memories: list[dict] | None = None,
    diagnosis_completeness: float | None = None,
    diagnosis_missing_fields: list[str] | None = None,
    profile_missing_fields_for_report: list[str] | None = None,
    additional_information_questions: list[dict] | None = None,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    conflict_labels: list[str] | None = None,
    agent_mode: str = "DIAGNOSIS",
    workflow_stage: str = "INTRO",
    profile_missing_fields: list[str] | None = None,
    recommended_question_ids: list[str] | None = None,
    diagnosis_closable: bool = False,
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
    compress_config: CompressConfig | None = None,
) -> Iterator[dict]:
    result = diagnose(
        question,
        knowledge=knowledge,
        recent_messages=recent_messages,
        conversation_summary=conversation_summary,
        long_term_memories=long_term_memories,
        diagnosis_memories=diagnosis_memories,
        diagnosis_completeness=diagnosis_completeness,
        diagnosis_missing_fields=diagnosis_missing_fields,
        profile_missing_fields_for_report=profile_missing_fields_for_report,
        additional_information_questions=additional_information_questions,
        region_id=region_id,
        industry_id=industry_id,
        membership_level=membership_level,
        conflict_labels=conflict_labels,
        agent_mode=agent_mode,
        workflow_stage=workflow_stage,
        profile_missing_fields=profile_missing_fields,
        recommended_question_ids=recommended_question_ids,
        diagnosis_closable=diagnosis_closable,
        vector_store=vector_store,
        restrict_to_knowledge_ids=restrict_to_knowledge_ids,
        compress_config=compress_config,
    )
    yield {"event": "delta", "text": result["answer"], "attempt": 1}
    yield {"event": "result", "result": result, "attempt": 1}


def _diagnosis_error(answer: str, status: str, workflow_stage: str, profile_missing_fields: list[str] | None) -> dict:
    return {
        "mode": "DIAGNOSIS",
        "answer": answer,
        "sources": [],
        "confidence": "LOW",
        "timeliness": TIMELINESS,
        "selfCheckStatus": status,
        "disclaimer": DISCLAIMER,
        "memoryCandidates": [],
        "userProfileMemories": [],
        "diagnosisMemories": [],
        "diagnosisCompleteness": 0,
        "diagnosisMissingFields": [],
        "additionalInformationQuestions": [],
        "workflowStage": workflow_stage,
        "profileMissingFields": profile_missing_fields or [],
        "completionSignal": "CONTINUE",
        "recommendedQuestions": [],
        "recommendationCandidates": [],
        "currentTopic": None,
        "nextBestTopics": [],
    }


def _parse_structured_response(raw: str) -> dict:
    try:
        candidate = json.loads(raw)
    except (TypeError, json.JSONDecodeError) as exc:
        raise ValueError("diagnosis response must be valid JSON") from exc
    if not isinstance(candidate, dict) or not isinstance(candidate.get("answer"), str):
        raise ValueError("diagnosis response must be a JSON object with a string answer")
    completeness = candidate.get("diagnosisCompleteness", 0)
    if not isinstance(completeness, (int, float)) or not 0 <= completeness <= 100:
        raise ValueError("diagnosisCompleteness must be between 0 and 100")
    questions = candidate.get("additionalInformationQuestions", [])
    if not isinstance(questions, list):
        raise ValueError("additionalInformationQuestions must be an array")
    if len(questions) < 10:
        raise ValueError("additionalInformationQuestions must contain at least 10 items")
    return {
        "answer": candidate["answer"],
        "diagnosisCompleteness": completeness,
        "userProfileMemories": _normalize_memories(candidate.get("userProfileMemories")),
        "diagnosisMemories": _normalize_memories(candidate.get("diagnosisMemories")),
        "diagnosisMissingFields": _string_list(candidate.get("diagnosisMissingFields")),
        "profileMissingFields": _string_list(candidate.get("profileMissingFields")),
        "additionalInformationQuestions": questions,
        "reportReady": bool(candidate.get("reportReady", False)),
    }


def _normalize_memories(value) -> list[dict]:
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict) and item.get("key") and item.get("value")]


def _string_list(value) -> list[str]:
    return [item for item in value if isinstance(item, str)] if isinstance(value, list) else []


def _results_to_dicts(results):
    return [
        {
            "id": item.id,
            "title": item.title,
            "content": item.content,
            "source_url": item.source_url,
            "source_id": item.source_id,
            "score": item.score,
        }
        for item in results
    ]


def _results_to_sources(results):
    return [
        {
            "id": item.id,
            "title": item.title,
            "sourceUrl": item.source_url,
            "sourceId": item.source_id,
            "confidence": item.confidence,
            "score": item.score,
            "entitlement": item.entitlement,
        }
        for item in results
    ]
