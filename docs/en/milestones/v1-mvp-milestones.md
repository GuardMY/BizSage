# BizSage V1 MVP Milestones

## V1 Scope

V1 is an internal-validation MVP. It covers the P0 chain from data collection to
governance, knowledge base, RAG retrieval, diagnosis Agent output, Web
conversation, and a simple operations console.

V1 is governed by the five product, architecture, implementation, data-collection,
and risk/compliance documents listed in `docs/en/milestones/product-milestones.md`.
It intentionally implements only the MVP/P0 subset of those documents; deferred
capabilities must remain out of scope unless this milestone is updated.

The system must prove:

- four source ingestion paths can produce normalized records;
- governance can clean, deduplicate, weight, and store knowledge;
- RAG can retrieve evidence;
- the diagnosis Agent can answer with sources and disclaimers;
- Web users can complete login, conversation, diagnosis, and source inspection;
- operators can enter, list, and approve intelligence.

## Out Of Scope

- Android implementation.
- Paid membership and entitlement billing.
- PDF report generation.
- Multi-model routing.
- Dynamic source weighting.
- Time-series snapshots and historical rollback.
- High availability, disaster recovery, and full monitoring dashboards.
- Full V2/V3 commercial operations workflows.

## Module Map

| Module | Responsibility | Inputs | Outputs | Dependencies | V1 Deliverables | Acceptance |
| --- | --- | --- | --- | --- | --- | --- |
| `apps/web` | Web diagnosis and ops console | API responses, auth token, stream events | User actions, rendered diagnosis, ops forms | `services/api` | Login, chat, source modal, intelligence entry/list, user list | User and operator workflows complete |
| `apps/android` | Future Android placeholder | API contract docs | README only | None | Deferred notice | No Android code required |
| `services/api` | Main gateway API | Web requests, worker responses, MySQL/Redis data | Unified API responses | MySQL, Redis, AI worker, collector | Auth, RBAC, users, conversations, intelligence, knowledge metadata | Tests pass and envelope is consistent |
| `services/ai-worker` | RAG and diagnosis | Query, user context, knowledge evidence | Diagnosis answer and sources | Qdrant, API, LLM provider | Keyword/vector search, rerank, mock/OpenAI-compatible LLM | Evidence-backed answers and no-evidence fallback |
| `services/collector` | Data ingestion | Forms, Excel files, URLs, mock API config | Normalized raw records | API, MySQL-compatible schema | Four V1 source adapters | All source types create normalized records |
| `infra` | Local runtime | Environment variables | Running MySQL, Redis, Qdrant | Docker | Compose file, migrations, scripts | Health checks pass |
| `docs` | Delivery knowledge | Plan and implemented behavior | Runbooks and acceptance docs | All modules | API, DB, deployment, acceptance docs | New developer can run V1 |

## Milestone Schedule

### M0 Repo And Standards

- Initialize git repository.
- Create monorepo layout.
- Add root README, env template, contribution notes, API response convention,
  database convention, Android placeholder, and this milestone document.

Acceptance: repo boots with documented commands and module boundaries are clear.

### M1 Infrastructure

- Add Docker Compose for MySQL, Redis, Qdrant, and local service networking.
- Add database migration baseline.
- Add single-machine deployment script and environment examples.

Acceptance: local infrastructure starts cleanly and health checks pass.

### M2 Core API

- Implement Spring Boot service for auth, JWT, roles, unified response, request
  logging, and basic rate limiting.
- Implement roles: `SUPER_ADMIN`, `OPERATOR`, `USER`.
- Implement user, conversation, message, intelligence, and knowledge metadata
  storage.

Acceptance: auth/RBAC tests pass; APIs return `code/message/data/requestId`.

### M3 Data Collection

- Implement Python collector with user private business data, manual
  intelligence intake, low anti-crawler public-page fetcher, and mock third-party
  API provider.
- Normalize source tags, industry ID, link ID, region ID, confidence, and
  weight.

Acceptance: all four source types can write normalized raw records.

### M4 Data Governance And Knowledge Base

- Implement field normalization, URL dedupe, SimHash dedupe, basic
  rumor-keyword filtering, and fixed source weights.
- Implement static industry baseline knowledge import and manual/batch entry.
- Implement AES encryption for sensitive private data and masked display.

Acceptance: duplicate inputs collapse correctly; sensitive data is never stored
or displayed as plain text.

### M5 RAG And Agent

- Implement Python AI worker with keyword search, vector search,
  source-weight rerank, and context truncation.
- Implement OpenAI-compatible LLM adapter and mock fallback.
- Implement diagnosis flow: user question -> RAG -> model -> sourced answer.
- Enforce no-evidence behavior.

Acceptance: diagnosis output includes sources, timeliness note, confidence cue,
and disclaimer.

### M6 Web App

- Implement Next.js login, conversation list, diagnosis chat with streaming
  output, and source modal.
- Implement ops pages: intelligence entry, intelligence list, user list.
- Keep the UI work-focused and dense.

Acceptance: user can complete login -> create conversation -> ask diagnosis ->
view sources; operator can enter and approve intelligence.

### M7 Integration And Documentation

- Wire Web to Spring API and API to Python workers.
- Add OpenAPI docs, database design docs, deployment docs, and V1 acceptance
  guide.

Acceptance: a new developer can run the full stack from docs.

### M8 Verification And Hardening

- Run backend tests, Python tests, Web tests, and a basic 50-concurrent-request
  load test.
- Run privacy checks for phone and identity masking.
- Record 4-hour single-node stability result or exact pending validation.

Acceptance: V1 checklist passes or failures are documented with exact
remediation tasks.

## Acceptance Checklist

- [ ] Four source ingestion paths work.
- [ ] Diagnosis answers include sources.
- [ ] Phone and identity values are masked in responses and UI.
- [ ] No plain sensitive values are persisted in designed storage paths.
- [ ] Single API endpoint tolerates 50 concurrent requests in local validation.
- [ ] Single-node runtime survives a 4-hour observation window.
- [ ] Deployment docs are complete enough for a new developer.

## Assumptions

- V1 Android is intentionally deferred; only `apps/android/README.md` exists.
- Third-party API collection remains mock/pluggable until real credentials are
  supplied.
- LLM uses OpenAI-compatible configuration and mock mode when no key is present.
- V2/V3 features are excluded unless a later milestone document expands scope.
