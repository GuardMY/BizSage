import json

import pytest

from app.agent import _parse_structured_response


def test_structured_diagnosis_response_is_normalized():
    payload = {
        "answer": "## conclusion",
        "diagnosisCompleteness": 82,
        "userProfileMemories": [{"category": "PREFERENCE", "key": "style", "value": "concise", "confidence": 0.9, "structured": True}],
        "diagnosisMemories": [],
        "diagnosisMissingFields": ["cost"],
        "profileMissingFields": [],
        "additionalInformationQuestions": [{"id": str(i), "questionText": f"question {i}", "purpose": "detail", "priority": i} for i in range(10)],
        "reportReady": True,
    }
    result = _parse_structured_response(json.dumps(payload))
    assert result["diagnosisCompleteness"] == 82
    assert result["userProfileMemories"][0]["key"] == "style"
    assert result["reportReady"] is True


def test_structured_diagnosis_rejects_invalid_completeness():
    with pytest.raises(ValueError):
        _parse_structured_response('{"answer":"ok","diagnosisCompleteness":101}')


def test_structured_diagnosis_rejects_duplicate_questions():
    payload = {
        "answer": "ok",
        "diagnosisCompleteness": 80,
        "additionalInformationQuestions": [
            {"id": str(i), "questionText": "same question" if i in (1, 2) else f"question {i}",
             "purpose": "detail", "priority": i}
            for i in range(10)
        ],
        "reportReady": True,
    }
    with pytest.raises(ValueError):
        _parse_structured_response(json.dumps(payload))


def test_structured_diagnosis_rejects_non_boolean_report_ready():
    payload = {
        "answer": "ok",
        "diagnosisCompleteness": 80,
        "additionalInformationQuestions": [
            {"id": str(i), "questionText": f"question {i}", "purpose": "detail", "priority": i}
            for i in range(10)
        ],
        "reportReady": "true",
    }
    with pytest.raises(ValueError):
        _parse_structured_response(json.dumps(payload))
