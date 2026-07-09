# BizSage RAG Control-Plane and Boundary Refactor Recommendation

This document is the English counterpart of `rag-control-plane-and-boundary-refactor-recommendation-zh-CN.md`. It records the current responsibility split between `services/api` and `services/ai-worker`, explains the recommended control-plane placement, and proposes a phased RAG-boundary refactor for BizSage.

Analysis date: 2026-07-09

## 1. Executive Summary

The recommended target shape is:

- `services/api` remains the control plane.
- `services/ai-worker` becomes the full RAG execution plane.

In practical terms, this means:

- API keeps authentication, authorization, data scope, conversation ownership, final persistence, and frontend-facing failure semantics.
- AI worker owns retrieval, rerank, context compression, memory-aware prompt assembly, and final reasoning.

The repository already trends in this direction, but the current runtime still has boundary drift:

- API assembles too much RAG input content per request.
- AI worker still carries some business-filter semantics that should be treated as API-issued scope rather than worker-owned policy.

## 2. Current-State Reading

### 2.1 What the API currently owns

`services/api` currently does more than a thin gateway:

- Persists user and assistant messages.
- Loads recent messages, conversation summaries, and long-term memories.
- Loads scoped knowledge and approved intelligence from MySQL.
- Shapes the worker payload for diagnosis, learning, and transition requests.
- Persists returned memory candidates and rolling summaries.

Representative code paths include:

- `DiagnosisService`
- `LearningService`
- `ConversationSummaryStore`
- `UserMemoryStore`

### 2.2 What the AI worker currently owns

`services/ai-worker` already owns most AI-facing execution logic:

- keyword and vector retrieval
- hybrid reranking
- context compression
- memory-context assembly
- diagnosis reasoning
- learning reasoning
- memory-candidate extraction

Representative code paths include:

- `app/rag.py`
- `app/context_compressor.py`
- `app/agent.py`
- `app/learning_agent.py`
- `app/memory.py`
- `app/vector_store.py`

## 3. Why the Control Plane Should Stay in the API

The recommended control-plane placement is `services/api`, not `services/ai-worker`.

### 3.1 Reasons

- Authentication, RBAC, membership, region, and industry boundaries already live naturally in the API layer.
- Conversation state, message persistence, summary persistence, and long-term memory records are business data, not just prompt context.
- API is the natural place to preserve frontend contracts, SSE shapes, and explicit failure mapping.
- Keeping the final write path in one service simplifies idempotency, auditability, and incident debugging.

### 3.2 What would go wrong if the control plane moved to the worker

If the control plane moved to `services/ai-worker`, the worker would stop being a reasoning service and become a primary business service. That would force Python-side ownership of:

- authorization-sensitive data decisions
- cross-step write ordering
- idempotency and retry semantics
- partial-failure compensation
- audit and trace expectations currently centered around the API

That tradeoff is possible, but it is not the best fit for BizSage's current architecture or risk profile.

## 4. Current RAG Boundary Problems

### 4.1 API assembles too much RAG content

The API currently loads and forwards a large `knowledge` payload into the worker for online diagnosis and learning flows. This makes the API participate too deeply in RAG content assembly instead of limiting itself to scope and orchestration.

### 4.2 Worker still owns some business-filter semantics

The worker currently applies region, industry, and entitlement filters inside retrieval code. The worker should still enforce the supplied scope, but the meaning of that scope should come from the API contract rather than becoming an independently evolving business-policy layer inside Python.

### 4.3 The knowledge-source model is mixed

BizSage already has a Qdrant-backed retrieval path in the worker, but the API still behaves as if the online request path must inline knowledge content. This creates an unclear mental model for whether online RAG is based on:

- API-provided content batches
- worker-owned retrieval indexes
- or both at once

## 5. Recommended Boundary Model

### 5.1 API responsibilities to keep

- authentication and authorization
- conversation ownership and state
- long-term memory source-of-truth decisions
- message, summary, and memory persistence
- frontend response envelope and SSE behavior
- worker health surfacing and failure mapping
- knowledge publication, deletion, and sync governance

### 5.2 Worker responsibilities to strengthen

- query understanding
- lexical retrieval
- vector retrieval
- hybrid reranking
- evidence sufficiency judgment
- context compression
- memory-aware prompt assembly
- structured reasoning output
- memory-candidate extraction and reasoning suggestions

### 5.3 Contract direction

The online API-to-worker contract should trend toward:

- `question`
- `recent_messages`
- `conversation_summary`
- `long_term_memories`
- `region_id`
- `industry_id`
- `membership_level`
- `knowledge_filters` or `allowed_knowledge_scope`
- optional `conflict_labels`

The default online path should gradually stop sending large inline `knowledge` content batches except for:

- tests
- controlled fallback modes
- special debug or replay workflows

## 6. Recommended Migration Phases

### Phase 1: Stabilize the boundary contract

- Keep the API control plane unchanged.
- Document that API-issued scope is authoritative.
- Make the worker treat region, industry, entitlement, and similar values as contract inputs rather than locally invented policy.
- Keep knowledge sync under API governance.

### Phase 2: Reduce API-side online RAG assembly

- Stop treating inline `knowledge` payloads as the default online retrieval source.
- Prefer worker-owned retrieval indexes as the main online retrieval base.
- Keep API responsible for deciding what the worker is allowed to access.

### Phase 3: Make worker retrieval the normal path

- Let the worker retrieve primarily from its indexed store.
- Keep API-triggered publish, unpublish, and resync flows.
- Add verification for MySQL-to-Qdrant consistency after publish and delete actions.

### Phase 4: Remove duplicated rules

- Remove duplicated memory and retrieval-policy rules where the API is already the source of truth.
- Keep one authoritative definition for categories, lifecycle semantics, and confidence-related contract behavior.

## 7. Benefits of This Refactor

- Cleaner module boundaries.
- A smaller and more stable API-to-worker request shape.
- Faster iteration on retrieval and reasoning inside the AI worker.
- Lower risk of Java/Python rule drift.
- Clearer operator mental model: API controls, worker reasons.

## 8. Main Risks and Guardrails

### Risks

- Qdrant and MySQL can drift if sync governance is weak.
- Worker retrieval can overreach if scope contracts are vague.
- Online behavior can regress during the transition away from inline knowledge payloads.

### Guardrails

- Add contract tests for scope forwarding.
- Add integration tests for publish, delete, resync, and retrieval visibility.
- Keep explicit worker-health and upstream-LLM failure reporting.
- Preserve API-side final write decisions for messages, summaries, and memories.

## 9. First Recommended Work Items

The highest-value near-term tasks are:

1. Define and freeze a minimal API-to-worker RAG contract for online requests.
2. Reduce or eliminate inline `knowledge` payloads from the default diagnosis and learning paths.
3. Add end-to-end regression coverage for `scope -> retrieval -> answer -> persistence`.
4. Audit duplicated memory and retrieval rules across Java and Python, then choose one authoritative side for each rule.

## 10. Decision Summary

The recommended BizSage direction is:

- Keep the control plane in `services/api`.
- Strengthen `services/ai-worker` as the full RAG and reasoning execution plane.
- Move away from API-heavy online RAG content assembly.
- Keep authority, persistence, and frontend-facing control in the API.
