# LLM Closure Implementation Plan

This document records the implementation plan for closing the BizSage user-facing LLM path so that diagnosis output uses the intended runtime chain instead of API-local assembled answers.

## 1. Goal

The target closed loop is:

`Web -> services/api -> services/ai-worker -> RAG/Qdrant -> external OpenAI-compatible LLM -> services/api -> Web`

This closure must cover:

- conversation diagnosis streaming;
- diagnosis report generation;
- runtime and health visibility for the AI worker and upstream LLM.

The plan intentionally removes API-local diagnosis assembly from the user-facing main path. It also removes AI-worker mock-answer fallback from the real diagnosis path so that the runtime state is observable and truthful.

## 2. Summary

The implementation will move the visible diagnosis responsibility from `services/api` local string assembly to `services/ai-worker`, while preserving the existing frontend entrypoints in `apps/web`.

The final user-facing behavior is:

- the web app keeps calling the existing API endpoints;
- the API becomes the authenticated orchestration and persistence layer;
- the AI worker becomes the single diagnosis and report reasoning engine;
- missing worker availability or missing real LLM availability becomes an explicit failure, not a silent fallback;
- no-evidence and conflicting-evidence branches remain controlled business outputs, not transport failures.

## 3. Planned Architecture Behavior

### 3.1 Primary request path

The user-facing diagnosis flow will be:

1. `apps/web` submits the diagnosis request through the existing API route.
2. `services/api` authenticates the user, resolves membership, region, industry, conversation state, and memory context.
3. `services/api` assembles the worker payload, including question, recent messages, conversation summary, long-term memories, and knowledge evidence.
4. `services/api` calls `services/ai-worker /agent/diagnose`.
5. `services/ai-worker` performs evidence retrieval, filtering, and LLM generation.
6. `services/api` persists the returned assistant result and wraps it into the existing frontend-facing response shape.
7. `apps/web` renders the result and any explicit failure state.

### 3.2 Responsibility boundaries

`apps/web` remains responsible for:

- login state and token handling;
- submitting diagnosis and report requests;
- rendering diagnosis output, sources, confidence, timeliness, disclaimer, and explicit failure states.

`services/api` remains responsible for:

- authentication and authorization;
- unified API envelope behavior;
- conversation and memory persistence;
- payload assembly for the AI worker;
- worker health surfacing and failure mapping;
- SSE wrapping for the web client.

`services/ai-worker` becomes responsible for:

- retrieval and reranking;
- conflict/no-evidence controlled reasoning decisions;
- real external LLM invocation;
- structured diagnosis payload generation.

### 3.3 Failure posture

This closure uses a strict-failure policy:

- if the AI worker is unavailable, the API must fail explicitly;
- if the AI worker is reachable but not configured for a real LLM, the worker must fail explicitly;
- if the upstream LLM returns an error or empty answer, the worker must fail explicitly;
- the API must not fall back to its current local diagnosis template;
- the worker must not return mock answers on the real diagnosis path.

Business-level controlled outputs are still allowed when they are legitimate reasoning outcomes:

- insufficient evidence -> controlled `INSUFFICIENT_EVIDENCE` output;
- conflicting or unsupported evidence -> controlled `NEEDS_REVIEW` output.

These are not considered transport failures.

## 4. Public Interface and Behavior Changes

### 4.1 Web-facing API

The public entrypoints stay unchanged:

- `POST /api/conversations/{id}/messages/stream`
- `GET /api/reports/diagnosis`

The behavioral change is internal:

- both endpoints must use the AI worker as the diagnosis/reasoning engine;
- neither endpoint may use API-local diagnosis assembly as the user-facing main path.

### 4.2 Internal service contract

The API-to-worker contract must include:

- question;
- knowledge evidence;
- recent messages;
- conversation summary;
- long-term memories;
- region ID;
- industry ID;
- membership level;
- conflict labels when available.

The worker response consumed by the API must include:

- answer;
- sources;
- confidence;
- timeliness;
- self-check status;
- disclaimer.

If the report endpoint needs a `summary` field while the worker returns `answer`, the API must perform a stable mapping without changing the public report contract unexpectedly.

### 4.3 Health and observability

The runtime must expose enough signal to distinguish:

- API healthy, worker unhealthy;
- worker healthy, but real LLM configuration missing;
- worker healthy, but upstream LLM call failing;
- diagnosis path returning a controlled no-evidence or needs-review business output.

The intent is to make real closure observable by operators instead of hiding failures behind local fallback text.

## 5. Implementation Steps

### 5.1 API diagnosis delegation

- Replace the API-local diagnosis assembly path with a worker client call.
- Keep message persistence, summary persistence, and memory persistence in the API layer.
- Validate and map worker payloads before they are streamed or returned to the web app.

### 5.2 Report-path closure

- Refactor report generation to reuse the same worker-backed reasoning path as chat.
- Keep report metadata and API envelope behavior stable for the frontend.

### 5.3 AI worker strict real-LLM mode

- Remove mock-answer fallback from the real diagnosis endpoint.
- Treat missing `OPENAI_COMPATIBLE_API_KEY` or equivalent real model readiness as an explicit failure.
- Treat upstream LLM HTTP failures or empty completions as explicit failures.

### 5.4 Frontend failure rendering

- Preserve current API entrypoints and auth flow.
- Add clear UI handling for worker unavailability, missing LLM readiness, malformed diagnosis payloads, and explicit API failure responses.
- Keep successful diagnosis rendering compatible with the existing source and metadata cards.

## 6. Testing and Acceptance

### 6.1 API verification

- conversation stream success path delegates to the worker and returns structured diagnosis data;
- report success path delegates to the worker and preserves the report response shape;
- worker connection failure returns explicit failure and never returns the old local template answer;
- worker `5xx`, timeout, malformed JSON, or missing required fields return explicit API failures;
- membership, region, and industry context are forwarded correctly to the worker.

### 6.2 AI worker verification

- diagnosis succeeds only when a real LLM path is configured and returns content;
- missing key or missing real model readiness causes explicit failure;
- upstream LLM error or empty content causes explicit failure;
- no-evidence and conflicting-evidence still return controlled reasoning outputs.

### 6.3 Web verification

- chat successfully renders worker-backed diagnosis results;
- SSE failure states are parsed and shown clearly;
- report failure states are visible and do not render stale or fake diagnosis text;
- `npm test` and `npm run build` remain part of the implementation verification path.

## 7. Assumptions

- The closure includes chat, report, and worker/LLM health visibility together.
- `apps/web` must keep calling only `services/api`, not the AI worker directly.
- The repository target architecture remains `API -> AI worker -> Qdrant / external OpenAI-compatible LLM`.
- Strict failure is preferred over hidden degradation for this rollout.
