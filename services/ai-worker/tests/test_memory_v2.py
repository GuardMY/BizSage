"""Tests for the memory sync endpoint and V2 memory features."""

import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.memory import (
    MemoryCategory,
    MEMORY_LIFECYCLE,
    MIN_CONFIDENCE_FOR_CONTEXT,
    MIN_CONFIDENCE_FOR_VECTOR_SYNC,
    build_memory_context,
    consolidate_memories,
    extract_diagnosis_memories,
    extract_learning_memories,
    extract_transition_memories,
    forget_expired,
    should_sync_to_vector_memory,
)

client = TestClient(app)


# ── Health check ───────────────────────────────────────────────────

def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "UP"


# ── Memory sync endpoint ───────────────────────────────────────────

class FakeQdrantClient:
    """In-memory Qdrant substitute so tests don't need a running Qdrant."""

    def __init__(self, location=None, **kwargs):
        self.collections: dict[str, dict] = {}

    def collection_exists(self, collection_name: str) -> bool:
        return collection_name in self.collections

    def create_collection(self, collection_name: str, vectors_config, **kwargs):
        self.collections[collection_name] = {}

    def upsert(self, collection_name: str, points, **kwargs):
        if collection_name not in self.collections:
            self.collections[collection_name] = {}
        for p in points:
            self.collections[collection_name][p.id] = {"vector": p.vector, "payload": p.payload}


@pytest.fixture
def _mock_qdrant(monkeypatch):
    """Replace QdrantClient with in-memory fake for endpoint tests."""
    monkeypatch.setattr("app.main.QdrantClient", FakeQdrantClient)
    monkeypatch.setattr("app.vector_store.QdrantClient", FakeQdrantClient)


def test_memory_sync_empty_request(_mock_qdrant):
    resp = client.post("/memory/sync", json={"memories": []})
    assert resp.status_code == 200
    data = resp.json()
    assert data["synced"] == 0


def test_memory_sync_with_memories(_mock_qdrant):
    resp = client.post("/memory/sync", json={
        "memories": [
            {"id": 1, "memory_text": "用户偏好先给结论再给证据的回复风格",
             "user_id": 100, "source_conversation_id": 200},
            {"id": 2, "memory_text": "用户有两家门店主要做外卖",
             "user_id": 100, "source_conversation_id": 200},
        ],
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["synced"] == 2
    assert len(data["results"]) == 2
    for r in data["results"]:
        assert r["status"] == "SYNCED"
        assert "embedding_id" in r
        assert "qdrant_point_id" in r


def test_memory_sync_skips_empty_text(_mock_qdrant):
    resp = client.post("/memory/sync", json={
        "memories": [
            {"id": 1, "memory_text": "", "user_id": 100},
            {"id": 2, "memory_text": "   ", "user_id": 100},
        ],
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["synced"] == 0


# ── Memory categories ──────────────────────────────────────────────

def test_all_memory_categories_have_lifecycle():
    for category in MemoryCategory.all_categories():
        assert category in MEMORY_LIFECYCLE, f"{category} missing lifecycle"


def test_memory_lifecycle_values():
    assert MEMORY_LIFECYCLE[MemoryCategory.PREFERENCE] == 180
    assert MEMORY_LIFECYCLE[MemoryCategory.BUSINESS_FACT] == 90
    assert MEMORY_LIFECYCLE[MemoryCategory.PAIN_POINT] == 90
    assert MEMORY_LIFECYCLE[MemoryCategory.INDUSTRY_CONTEXT] == 365
    assert MEMORY_LIFECYCLE[MemoryCategory.LEARNING_PROGRESS] == 180


# ── Confidence thresholds ──────────────────────────────────────────

def test_min_confidence_for_context_is_reasonable():
    """Context filter should be between 0.5 and 0.8."""
    assert 0.5 <= MIN_CONFIDENCE_FOR_CONTEXT <= 0.8


def test_min_confidence_for_vector_sync_is_stricter_than_context():
    """Vector sync should require at least as much confidence as context use."""
    assert MIN_CONFIDENCE_FOR_VECTOR_SYNC >= MIN_CONFIDENCE_FOR_CONTEXT


# ── Integration: memory round-trip ─────────────────────────────────

def test_extract_then_filter_then_build():
    """Full flow: extract memories → filter → build context."""
    # Simulate a diagnosis interaction
    question = "我在北京有两家门店，主要做外卖生意。最近招工难，利润太低了。"
    answer = "根据你的情况，建议优化外卖平台运营，同时考虑灵活用工方案..."

    candidates = extract_diagnosis_memories(question, answer)

    # All extracted memories should be valid
    for c in candidates:
        assert c["category"] in MemoryCategory.all_categories()
        assert c["key"]
        assert c["value"]
        assert 0 <= c["confidence"] <= 1.0

    # Build context — should include the memories
    context = build_memory_context(long_term_memories=candidates)
    assert "长期记忆" in context


def test_learning_extraction_with_chain_node():
    """Learning extraction includes chain node progress."""
    candidates = extract_learning_memories(
        "原材料成本怎么控制？详细讲讲",
        "原材料成本管控有三大要点...",
        chain_node_id="raw-materials",
    )
    # Must have a 'studied_node_raw-materials' entry (from regex fallback)
    keys = {c["key"] for c in candidates}
    assert any(k.startswith("studied_node_") for k in keys), f"Got keys: {keys}"


# ── Transition: bidirectional context ──────────────────────────────

def test_transition_learn_to_diagnose_with_previous_answer():
    """When transitioning with previous_answer, richer memories are extracted."""
    candidates = extract_transition_memories(
        from_mode="LEARNING",
        to_mode="DIAGNOSIS",
        user_question="基于我学到的原材料知识，帮我诊断成本问题",
        chain_node_id="raw-materials",
        previous_answer="原材料成本占餐饮总成本的30-40%...",
    )
    # Should have at least transition + diagnosis_from_learning records
    assert len(candidates) >= 2
    transitions = [c for c in candidates if c["key"].startswith("transition_")]
    assert len(transitions) >= 1


def test_transition_without_previous_answer():
    """Transition without previous_answer still produces basic records."""
    candidates = extract_transition_memories(
        from_mode="DIAGNOSIS",
        to_mode="LEARNING",
        user_question="教我提升利润",
        chain_node_id="profit-analysis",
    )
    assert len(candidates) >= 1
    assert any(c["key"].startswith("transition_") for c in candidates)


# ── Consolidation edge cases ───────────────────────────────────────

def test_consolidate_handles_duplicate_keys():
    memories = [
        {"category": "PREFERENCE", "key": "style", "value": "v1", "confidence": 0.7},
        {"category": "PREFERENCE", "key": "style", "value": "v2", "confidence": 0.9},
        {"category": "PREFERENCE", "key": "style", "value": "v3", "confidence": 0.5},
    ]
    result = consolidate_memories(memories)
    assert len(result) == 1
    assert result[0]["value"] == "v2"  # Highest confidence wins


def test_consolidate_treats_different_categories_as_distinct():
    memories = [
        {"category": "PREFERENCE", "key": "speed", "value": "fast", "confidence": 0.8},
        {"category": "BUSINESS_FACT", "key": "speed", "value": "slow", "confidence": 0.9},
    ]
    result = consolidate_memories(memories)
    assert len(result) == 2


# ── Forgetting edge cases ──────────────────────────────────────────

def test_forget_expired_handles_mixed_expiry():
    import time
    now = time.time()
    memories = [
        {"category": "PREFERENCE", "key": "a", "value": "1",
         "expires_at": now + 10000},
        {"category": "BUSINESS_FACT", "key": "b", "value": "2"},  # no expiry
        {"category": "PAIN_POINT", "key": "c", "value": "3",
         "expires_at": now - 1},  # just expired
    ]
    result = forget_expired(memories, current_time=now)
    keys = {m["key"] for m in result}
    assert "a" in keys
    assert "b" in keys
    assert "c" not in keys
