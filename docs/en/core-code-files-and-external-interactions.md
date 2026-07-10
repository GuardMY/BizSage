# BizSage Core Code Files and External Interactions

This document is the English counterpart of `core-code-files-and-external-interactions-zh-CN.md`. It explains what the core code files in each BizSage module do, which internal modules they call, and which external systems they interact with.

## 1. Scope and Reading Rules

This document covers all repository runtime modules:

- `apps/web`
- `apps/android`
- `services/api`
- `services/ai-worker`
- `services/collector`
- `infra`

For readability, "core code files" means the files that define one or more of the following:

- runtime entrypoints
- API controllers or route handlers
- orchestration services
- storage or synchronization boundaries
- admin/ops consoles
- external service adapters
- deployment and reverse-proxy wiring

Simple DTO, mapper, entity, and test files are not described one by one unless they define an important runtime contract.

## 2. Runtime Boundary Summary

```text
Browser
  -> Nginx
    -> Web (Next.js)
    -> API (Spring Boot)

Web
  -> API only

API
  -> MySQL
  -> Redis
  -> AI Worker
  -> Collector (admin collection orchestration path)

AI Worker
  -> Qdrant
  -> OpenAI-compatible LLM provider

Collector
  -> Redis
  -> Public pages / third-party API payloads / form payloads
```

## 3. Module-by-Module Core Files

### 3.1 `apps/web`

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `apps/web/app/page.tsx` | Main user workspace page. Owns login state, locale toggle, conversation selection, diagnosis submission, report generation, archive/delete actions, and section switching. | Calls `lib/api-client.ts`; renders workspace components. | Sends browser requests to `/api/**` through the Web API client. |
| `apps/web/lib/api-client.ts` | Single browser-side API boundary. Defines response types, SSE parsing, authentication handling, admin API helpers, and file download helpers. | Used by all user/admin pages. | Calls the Spring Boot API only; handles JSON and SSE streams. |
| `apps/web/lib/conversation-workspace.ts` | Pure state helper for splitting active/archive conversations and resolving selection fallback after archive/delete. | Used by `app/page.tsx` tests and workspace state. | No direct external interaction. |
| `apps/web/lib/markdown.tsx` | Markdown rendering wrapper for assistant answers. | Used by diagnosis/archive message views. | No direct external interaction. |
| `apps/web/app/components/workspace-shell.tsx` | Shared shell for the user workspace, including left rail, top bar, identity display, and logout/language controls. | Wraps diagnosis/intelligence/users/archive sections. | No direct external interaction. |
| `apps/web/app/components/conversation-sidebar.tsx` | Conversation list UI with active/archive actions and selection handling. | Receives page-level callbacks from `app/page.tsx`. | No direct external interaction. |
| `apps/web/app/components/diagnosis-workspace.tsx` | Main diagnosis chat panel. Renders conversation history, partial SSE answers, source popups, paid intelligence, and report generation entry. | Consumes data from `app/page.tsx`. | No direct external interaction; visualizes API results. |
| `apps/web/app/components/archive-workspace.tsx` | Read-only archive view for old messages. | Used by `app/page.tsx`. | No direct external interaction. |
| `apps/web/app/admin/page.tsx` | Main Admin V3 console. Owns admin login recovery, section routing, dashboard refresh, collection config editing, knowledge review flow, alert/ticket actions, and governance screens. | Calls admin helpers in `lib/api-client.ts`; renders admin subviews. | Calls `/api/admin/**`, `/api/ops/**`, and related admin endpoints. |
| `apps/web/app/admin/dashboard-view.tsx` | Dashboard presentation for admin metrics, alerts, tickets, and review queues. | Render-only view under admin page. | No direct external interaction. |
| `apps/web/app/admin/collection-workspace.tsx` | Admin collection-source editor and operations panel for source configs, keywords, recent runs, and dead letters. | Used by admin page. | Displays and mutates API-backed collection config data. |
| `apps/web/app/admin/governance-views.tsx` | Admin views for conflict records, false-information ledger, and snapshots. | Used by admin page. | Reads governance endpoints via the API client. |
| `apps/web/app/admin/monitoring-view.tsx` | Monitoring view for ops metrics, cache statistics, SLA, and work-order style data. | Used by admin page. | Reads `/api/ops/**` endpoints via the API client. |
| `apps/web/app/hooks/useAuth.ts` and `apps/web/app/hooks/useConversations.ts` | Supporting hooks for reusable auth and conversation state logic. | Available for shared client logic. | May call API helpers indirectly. |

Notes:

- The Web app never calls workers, Redis, MySQL, Qdrant, or the collector directly.
- SSE user-facing streams are parsed in `lib/api-client.ts` and rendered progressively in `diagnosis-workspace.tsx`.

### 3.2 `apps/android`

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `apps/android/README.md` | Placeholder contract note for a future Android client. | None. | Describes intended reuse of existing auth, conversation, streaming, intelligence, and source-display APIs. |

Notes:

- No Android runtime code exists yet.
- The current backend contracts are designed so Android can later reuse the same API surface as the Web app.

### 3.3 `services/api`

#### Entrypoint and platform files

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/api/src/main/java/com/bizsage/api/BizSageApiApplication.java` | Spring Boot entrypoint; enables scheduling and MyBatis mapper scanning. | Boots all API modules. | Starts the HTTP server and scheduled jobs. |
| `services/api/src/main/resources/application.yml` | Central runtime wiring for ports, MySQL, Redis cache, JWT, privacy keys, gray release, alert thresholds, memory sync, and actuator endpoints. | Configures all Spring modules. | Defines API connections to MySQL, Redis, and AI worker. |
| `services/api/src/main/java/com/bizsage/api/common/ApiResponse.java` | Unified API envelope contract. | Used by nearly all controllers. | Shapes all HTTP JSON responses to the Web app. |
| `services/api/src/main/java/com/bizsage/api/common/ApiControllerAdvice.java` | Shared exception-to-envelope conversion. | Wraps controller errors. | Standardizes client-visible error responses. |
| `services/api/src/main/java/com/bizsage/api/common/RequestIdFilter.java` | Injects request IDs into request scope. | Used across all request paths. | Makes tracing visible across HTTP responses and logs. |
| `services/api/src/main/java/com/bizsage/api/cache/CacheConfig.java` and `CacheMetrics.java` | Redis cache setup and hit/miss telemetry. | Used by conversation and ops modules. | Talks to Redis through Spring Cache. |

#### Auth, user, and access control

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/api/src/main/java/com/bizsage/api/auth/SecurityConfiguration.java` | Defines stateless security, JWT filter insertion, CORS, and unauthorized/forbidden handlers. | Uses `JwtAuthenticationFilter`. | Guards every API request. |
| `services/api/src/main/java/com/bizsage/api/auth/AuthController.java` | Login/logout endpoints. Issues JWTs and stores them in `httpOnly` cookies. | Uses `UserStore` and `JwtService`. | Handles browser sign-in/sign-out over HTTP. |
| `services/api/src/main/java/com/bizsage/api/auth/JwtAuthenticationFilter.java` and `JwtService.java` | Read/validate JWT from request cookie/header and build Spring security principal. | Used by Spring Security. | Validates browser session tokens. |
| `services/api/src/main/java/com/bizsage/api/users/UserController.java` and `UserStore.java` | Current-user profile lookup and user persistence/query boundary. | Used by auth and workspace controllers. | Reads/writes user data in MySQL. |
| `services/api/src/main/java/com/bizsage/api/governance/DataIsolationService.java` and `DataScope.java` | Region/industry/membership scoping and account-freeze checks. | Used by conversation and knowledge/intelligence filtering. | Enforces data-scope rules before returning data. |

#### User conversation and diagnosis path

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/api/src/main/java/com/bizsage/api/conversations/ConversationController.java` | Create/list/archive/delete conversations with cache and data-scope enforcement. | Uses `ConversationStore`, `UserStore`, `CacheMetrics`, and `DataIsolationService`. | Reads/writes conversation rows in MySQL. |
| `services/api/src/main/java/com/bizsage/api/conversations/ConversationStore.java` | SQL-backed conversation persistence. | Used by conversation and message flows. | MySQL read/write boundary. |
| `services/api/src/main/java/com/bizsage/api/messages/MessageController.java` | Real incremental SSE endpoints for diagnosis, learning, and cross-agent transition. It forwards worker `status`/`delta`/`reset` events and emits one final `diagnosis`. | Uses `DiagnosisService`, `LearningService`, `ConversationStore`, `ConversationMessageStore`, and `UserStore`. | Flushes SSE events to the Web app with proxy buffering disabled. |
| `services/api/src/main/java/com/bizsage/api/messages/DiagnosisService.java` | User-diagnosis orchestrator. Persists user/assistant turns, loads knowledge and memory context, calls the AI worker, stores memory candidates, and rolls summaries forward. | Uses `AiWorkerClient`, message/summary stores, `UserMemoryStore`, `KnowledgeStore`, and `IntelligenceStore`. | Calls AI worker; reads/writes MySQL. |
| `services/api/src/main/java/com/bizsage/api/messages/LearningService.java` | Learning-agent and transition orchestrator with the same persistence pattern as diagnosis. | Uses `AiWorkerClient`, message/summary stores, memory store, knowledge store, and intelligence store. | Calls AI worker; reads/writes MySQL. |
| `services/api/src/main/java/com/bizsage/api/messages/ConversationMessageStore.java` and `ConversationSummaryStore.java` | Persistent storage for raw messages and compressed conversation summaries. | Used by diagnosis/learning/report flows. | MySQL read/write boundary. |

#### Knowledge, intelligence, memory, and worker sync

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/api/src/main/java/com/bizsage/api/knowledge/KnowledgeController.java` and `KnowledgeStore.java` | End-user knowledge metadata/query path and persistence boundary for curated knowledge items. | Used directly and by diagnosis/learning orchestration. | MySQL knowledge access. |
| `services/api/src/main/java/com/bizsage/api/intelligence/IntelligenceController.java` and `IntelligenceStore.java` | End-user/admin intelligence query and persistence path. | Feeds diagnosis context loading. | MySQL intelligence access. |
| `services/api/src/main/java/com/bizsage/api/intelligence/PaidIntelligenceController.java` and `PaidIntelligenceStore.java` | Membership-gated intelligence list path used by the Web app. | Uses gray release and entitlement-aware storage filtering. | Returns paid intelligence data to the Web app. |
| `services/api/src/main/java/com/bizsage/api/memory/UserMemoryStore.java` | User memory persistence, lifecycle refresh, and active-memory lookup. | Used by diagnosis/learning flows and memory sync scheduler. | MySQL memory access. |
| `services/api/src/main/java/com/bizsage/api/memory/UserMemoryEmbeddingStore.java` and `MemorySyncScheduler.java` | Vector-sync staging for unstructured memories and scheduled pushing to the AI worker/Qdrant path. | Uses `AiWorkerClient`. | Syncs memory embeddings toward the AI worker. |
| `services/api/src/main/java/com/bizsage/api/worker/AiWorkerClient.java` | Main HTTP adapter from API to AI worker. It supports synchronous calls plus incremental SSE consumption for diagnose, learn, and transition. | Used by diagnosis, learning, startup sync, and memory sync. | Calls FastAPI AI worker and closes the upstream stream when downstream delivery stops. |
| `services/api/src/main/java/com/bizsage/api/worker/KnowledgeSyncInitializer.java` | Startup resync path from MySQL knowledge to worker/Qdrant. | Uses `KnowledgeStore` and `AiWorkerClient`. | Syncs the persistent vector knowledge store. |

#### Reports, gray release, privacy, health, and ops

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/api/src/main/java/com/bizsage/api/reports/DiagnosisReportController.java` | Metadata and PDF report endpoints for diagnosis outputs. | Uses `DiagnosisReportService`, `PdfReportGenerator`, `UserStore`, and `GrayReleaseService`. | Returns JSON metadata and downloadable PDF bytes. |
| `services/api/src/main/java/com/bizsage/api/reports/DiagnosisReportService.java` and `PdfReportGenerator.java` | Rebuild report content from diagnosis context and render PDF bytes. | Uses message/knowledge context and PDF generation. | Produces report data for the Web app download path. |
| `services/api/src/main/java/com/bizsage/api/grayrelease/GrayReleaseService.java` and `GrayReleaseProperties.java` | Feature gating for paid intelligence, PDF export, and future advanced RAG rollout. | Used by report and commercial features. | Controls which browser users see which features. |
| `services/api/src/main/java/com/bizsage/api/privacy/PrivacyService.java` and `PrivacyConfiguration.java` | Encryption/desensitization support for user-private operating data flows. | Used where sensitive business data is stored or transformed. | Protects sensitive data before persistence. |
| `services/api/src/main/java/com/bizsage/api/health/HealthController.java` | Lightweight API health endpoint. | None. | Used by operators, reverse proxy, and deployment scripts. |
| `services/api/src/main/java/com/bizsage/api/ops/OpsController.java`, `AlertRuleEngine.java`, `AlertScheduler.java`, `SlaService.java`, `SlaScheduler.java`, and `SlaStore.java` | Operational metrics, cache stats, SLA reports, alert evaluation, and scheduled ops telemetry. | Uses JDBC, cache metrics, and SLA storage. | Exposes monitoring data to the admin UI and scheduled alert loops. |

#### Admin, governance, and collection control plane

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/api/src/main/java/com/bizsage/api/admin/AdminController.java` | Main Admin V3 API surface. Covers dashboard, alerts, audit logs, review queue, tickets, human intelligence, knowledge workflow, collection source config, risk rules, and governance ledgers. | Uses `AdminStore`, `AdminKnowledgeStore`, `AdminCollectionStore`, and `ConflictStore`. | Serves the Web admin console. |
| `services/api/src/main/java/com/bizsage/api/admin/AdminStore.java` | Aggregates admin dashboard, alerts, tickets, reviews, human intelligence, risk rules, and audit data. | Used by `AdminController`. | Mostly a MySQL admin read/write boundary. |
| `services/api/src/main/java/com/bizsage/api/admin/AdminKnowledgeStore.java` | Knowledge editorial workflow: drafts, submit review, approve, publish, rollback, diff, and inspection. | Used by `AdminController`; syncs publication back into user-facing knowledge storage. | Writes MySQL knowledge workflow tables and triggers worker sync. |
| `services/api/src/main/java/com/bizsage/api/admin/AdminCollectionStore.java` | Collection source config, keyword rules, run history, dead letters, and manual run orchestration. | Used by `AdminController`; may use `CollectorClient` and persistence tables. | Connects admin API requests to collector-execution control data. |
| `services/api/src/main/java/com/bizsage/api/admin/CollectorClient.java` | HTTP adapter from API/admin to collector service. | Used by collection management logic. | Calls collector HTTP endpoints. |
| `services/api/src/main/java/com/bizsage/api/governance/ConflictStore.java` | Governance conflict and false-ledger persistence/query boundary. | Used by admin governance endpoints. | Reads/writes governance tables in MySQL. |
| `services/api/src/main/java/com/bizsage/api/governance/SnapshotController.java`, `SnapshotService.java`, `SnapshotScheduler.java`, and `SnapshotStore.java` | Snapshot generation, listing, diffing, cleanup, and scheduled creation. | Used by admin snapshots UI and scheduler. | Reads/writes MySQL snapshot tables. |

#### Schema and migrations

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql` | Consolidated idempotent Flyway baseline for the MySQL schema, admin schema and seed data, memory uniqueness, and user locale preference. | Loaded at API startup. | Creates the MySQL schema as the single Flyway-managed schema source for resettable environments. |

### 3.4 `services/ai-worker`

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/ai-worker/app/main.py` | FastAPI entrypoint. Defines health, RAG search, diagnosis, learning, transition, knowledge sync/delete, and memory sync endpoints. | Calls `agent.py`, `learning_agent.py`, `agent_transition.py`, `rag.py`, and `vector_store.py`. | Talks to API layer and Qdrant. |
| `services/ai-worker/app/agent.py` | Diagnosis Agent core. Runs conflict pre-check, RAG retrieval, context compression, prompt assembly, model routing, self-check retry, and memory candidate extraction. | Uses `rag.py`, `memory.py`, `context_compressor.py`, `prompt_library`, `model_routing`, and `reasoning_checks`. | Calls the external LLM through the routed model client. |
| `services/ai-worker/app/learning_agent.py` | Learning Agent core. Detects intent, optionally narrows to a chain node, compresses evidence, builds a learning-specific prompt, and returns structured learning output plus memory candidates. | Uses `rag.py`, `memory.py`, `agent_output.py`, `prompt_library`, and `reasoning_checks`. | Calls the external LLM through model routing. |
| `services/ai-worker/app/agent_transition.py` | Cross-agent mode switcher. Builds transition-aware prompts, carries forward summaries/memories, and routes to the target agent. | Uses `agent.py`, `learning_agent.py`, and `memory.py`. | No direct external interaction beyond whichever target agent invokes the LLM. |
| `services/ai-worker/app/rag.py` | Knowledge retrieval engine. Defines `KnowledgeItem`, local lexical scoring, vector retrieval fusion, business filters, and six-dimension rerank quality scoring. | Used by diagnosis, learning, and raw `/rag/search`. | Uses embeddings and optionally Qdrant-backed vector search. |
| `services/ai-worker/app/vector_store.py` | Thin Qdrant adapter for upsert and search of knowledge vectors. | Used by `main.py` and `rag.py`. | Talks to Qdrant over HTTP/gRPC client APIs. |
| `services/ai-worker/app/memory.py` | Three-tier memory context builder, LLM/regex memory extraction, forgetting, vector-sync eligibility, and memory consolidation. | Used by diagnosis, learning, and transitions. | Indirectly calls the LLM through model routing for extraction. |
| `services/ai-worker/app/llm.py` | Compatibility wrapper around the OpenAI-compatible provider configuration and deprecated direct answer helpers. | Used indirectly by model routing and older wrappers. | Reads provider env vars and calls the LLM endpoint. |
| `services/ai-worker/app/context_compressor.py` | Compresses retrieved evidence to fit model context budgets. | Used by diagnosis and learning. | No direct external interaction. |
| `services/ai-worker/app/model_routing/router.py`, `models.py`, and `providers.py` | Select models/providers and execute synchronous or `stream: true` calls. Streaming failover emits a reset before replacement output. | Used by diagnosis, learning, memory extraction, and self-check retries. | Parses external OpenAI-compatible SSE while forwarding answer content only. |
| `services/ai-worker/app/prompt_library/assembler.py`, `layers.py`, and `defaults.py` | Layered prompt composition for diagnosis and learning modes. | Used by diagnosis and learning. | No direct external interaction. |
| `services/ai-worker/app/reasoning_checks/checks.py` and `retry.py` | Post-generation checks for synchronous and streamed candidates. Failed streamed candidates are reset before retry and never become final results. | Used by diagnosis and learning. | No direct external interaction besides repeated model calls. |
| `services/ai-worker/app/embeddings.py` | Deterministic embedding generation used for local/vector retrieval and memory sync. | Used by `rag.py`, `vector_store.py`, and `main.py`. | Supports Qdrant vector writes/searches. |
| `services/ai-worker/app/agent_output.py` | Structured output formatting for learning/diagnosis responses. | Used mainly by the Learning Agent and transition path. | No direct external interaction. |

Notes:

- The API layer treats the AI worker as the sole inference engine for diagnosis, learning, and transition.
- `main.py` uses temporary per-request Qdrant collections when request knowledge is supplied directly, while startup/admin sync uses the persistent collection.

### 3.5 `services/collector`

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `services/collector/app/main.py` | FastAPI entrypoint for form intake, public-page collection, mock API intake, governance, and conflict check endpoints. | Calls `collectors.py`, `governance.py`, `conflict_engine.py`, `redis_state.py`, and `resilience.py`. | Talks to Redis and receives upstream source payloads. |
| `services/collector/app/collectors.py` | Raw record extraction and normalization for forms, Excel, public pages, and mock APIs. | Used by `main.py`. | Parses browser/admin submitted data and third-party-like payloads. |
| `services/collector/app/governance.py` | First-stage governance: normalization, rumor filtering, URL dedupe, SimHash dedupe, and fixed-weight assignment. | Used by `main.py` and `conflict_engine.py`. | No direct external interaction. |
| `services/collector/app/conflict_engine.py` | Five-branch conflict classifier that decides between tag-only, review ticket, alert, knowledge update, or false-ledger routing. | Used by governance endpoints. | No direct external interaction; returns governance decisions to callers. |
| `services/collector/app/resilience.py` | Incremental fingerprinting, retry/backoff, dead-letter classification, circuit breaking, and vendor failover with snapshot fallback. | Used by `main.py`. | Uses Redis state when available. |
| `services/collector/app/redis_state.py` | Redis-backed storage for fingerprints, recent snapshots, raw key/value state, and counters. | Used by collection resilience, API cache, proxy pool, and vendor registry. | Talks to Redis. |
| `services/collector/app/api_cache.py` | Redis cache manager for third-party API responses with hit/miss and cost-friendly TTL behavior. | Optional support module for collector vendor integrations. | Talks to Redis. |
| `services/collector/app/vendor_registry.py` | Multi-vendor API provider registry with health/degradation tracking and cost reporting. | Optional support module for collector vendor integrations. | Uses Redis-backed state and models external API vendors. |
| `services/collector/app/proxy_pool.py` | Proxy rotation manager with cooldown persistence and round-robin selection. | Optional support module for crawler-like collection paths. | Uses Redis-backed state and models external proxy services. |

Notes:

- The current collector returns normalized/governed records to the caller; full automatic persistence into MySQL/Qdrant is still mainly orchestrated outside the collector itself.
- Collector Redis usage is mostly for dedupe fingerprints, snapshot fallback, and support-state persistence.

### 3.6 `infra`

| File | Main responsibility | Internal calls | External interactions |
|------|---------------------|----------------|-----------------------|
| `infra/docker-compose.yml` | Production-style topology for BizSage-owned services and Nginx, with middleware supplied externally. | Starts API, AI worker, collector, Web, and Nginx containers. | Defines container networking for application services and external middleware endpoints. |
| `infra/docker-compose-all.yml` | Full local compose variant with MySQL, Redis, Qdrant, API, AI worker, collector, Web, and Nginx. | Starts local infrastructure and application containers. | Defines local container networking, dependency wiring, and persistent middleware volumes. |
| `infra/nginx/nginx.conf` | Reverse proxy entrypoint. Routes `/api/` to Spring Boot and `/` to Next.js; disables buffering for SSE. | Sits in front of Web and API. | Serves the browser-facing HTTP entrypoint. |
| `infra/scripts/start-local.ps1` and `start-all.ps1` | Startup helpers for infrastructure or the full local stack. | Wrap Docker Compose commands. | Start local dependencies from PowerShell. |
| `infra/scripts/health-check.ps1` | Local environment health probe script. | Queries services after startup. | Checks runtime endpoints and dependency health. |
| `infra/scripts/backup-*.ps1` and `restore-*.ps1` | Backup/restore helpers for MySQL and Qdrant. | Used by operators. | Interact with persistence backends during recovery workflows. |

## 4. External Interaction Flows

### 4.1 Browser diagnosis flow

```text
Browser
  -> apps/web/app/page.tsx
  -> apps/web/lib/api-client.ts
  -> POST /api/conversations/{id}/messages/stream
  -> MessageController
  -> DiagnosisService
  -> AiWorkerClient
  -> AI Worker /agent/diagnose/stream
  -> rag.py + agent.py + streaming model routing/self-check
  -> Qdrant + external LLM SSE
  -> API forwards status/delta/reset frames
  -> API persists the final result and emits diagnosis
  -> Web progressive rendering
```

### 4.2 Browser learning and agent transition flow

```text
Browser
  -> apps/web/lib/api-client.ts
  -> /messages/learn/stream or /messages/transition/stream
  -> MessageController
  -> LearningService
  -> AiWorkerClient
  -> AI Worker /agent/learn/stream or /agent/transition/stream
  -> learning_agent.py or agent_transition.py
  -> Qdrant + external LLM SSE
  -> status/delta/reset plus one final diagnosis event back to Web
```

### 4.3 Report export flow

```text
Web report button
  -> GET /api/reports/diagnosis
  -> DiagnosisReportController / DiagnosisReportService
  -> report metadata JSON

Web PDF download
  -> GET /api/reports/diagnosis/pdf
  -> GrayReleaseService gate
  -> PdfReportGenerator
  -> PDF bytes download
```

### 4.4 Admin collection and governance flow

```text
Admin page
  -> apps/web/app/admin/page.tsx
  -> apps/web/lib/api-client.ts
  -> /api/admin/collection/** and /api/admin/governance/**
  -> AdminController
  -> AdminCollectionStore / ConflictStore / SnapshotStore
  -> optional CollectorClient -> collector endpoints
  -> MySQL persistence + collector responses
  -> admin UI refresh
```

### 4.5 Knowledge publication and vector sync flow

```text
Admin knowledge publish
  -> /api/admin/knowledge/**
  -> AdminKnowledgeStore
  -> write knowledge workflow tables
  -> sync publication into knowledge_items
  -> AiWorkerClient.syncKnowledge()/syncAllKnowledge()
  -> AI Worker /knowledge/sync
  -> Qdrant persistent collection
```

### 4.6 Collection resilience and dedupe flow

```text
Source payload / public page / mock API data
  -> collector main.py
  -> collectors.py normalization
  -> resilience.py fingerprint/snapshot handling
  -> redis_state.py
  -> governance.py
  -> conflict_engine.py (optional)
  -> governed records returned to caller
```

## 5. Practical Reading Order

If someone new to the codebase wants the shortest path to understanding:

1. Read `README.md`.
2. Read this document and `docs/en/component-interactions-and-data-flows.md`.
3. Follow the runtime entrypoints:
   - `apps/web/app/page.tsx`
   - `services/api/.../MessageController.java`
   - `services/api/.../DiagnosisService.java`
   - `services/api/.../worker/AiWorkerClient.java`
   - `services/ai-worker/app/main.py`
   - `services/ai-worker/app/agent.py`
   - `services/collector/app/main.py`
   - `infra/docker-compose.yml`

## Source Context

This document was compiled from repository inspection on `2026-07-09`, including:

- `.codegraph/codegraph.db`
- runtime code under `apps/web`, `services/api`, `services/ai-worker`, `services/collector`, and `infra`
- existing architecture docs, especially `docs/en/component-interactions-and-data-flows.md` and `docs/en/system-architecture-and-framework.md`
