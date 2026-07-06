# Change Log

## 2026-07-06

### Web Auth Expiry Returns to Login Screen

- Change type: functional development.
- Affected modules: `apps/web`, `services/api`, and both change logs.
- Main changes:
  - Updated the API JWT authentication filter so invalid or expired bearer tokens on protected HTTP endpoints now resolve through the standard unauthorized path instead of surfacing as server errors.
  - Added a dedicated Web `AuthExpiredError` path for protected `fetch` calls, covering conversations, message history, paid intelligence, ops metrics, and diagnosis report requests.
  - Updated the Web home page to clear persisted sign-in state, return to the existing login screen, and show a session-expired notice when protected HTTP requests detect auth expiry, while keeping the current unsent draft input intact.
- Verification results:
  - In `services/api`, `mvn -Dtest=AuthAndRbacTest test` verifies invalid bearer tokens now return `401` with `code: UNAUTHORIZED`.
  - In `apps/web`, `npm test` verifies the auth-expired client path, login-screen fallback behavior, and draft-preservation source assertions.
- Unfinished items:
  - SSE diagnosis auth-expiry auto-logout is still intentionally out of scope for this change.

## 2026-07-05

### LLM Closure Implementation Plan Archive

- Change type: documentation maintenance.
- Affected modules: `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Added `docs/en/llm-closure-implementation-plan.md` as the formal English implementation-plan record for the strict user-facing LLM closure.
  - Added `docs/zh-CN/llm-closure-implementation-plan-zh-CN.md` as the matching Chinese formal document with the same scope, decisions, and acceptance intent.
  - Recorded the approved target chain `Web -> API -> AI worker -> RAG/Qdrant -> external OpenAI-compatible LLM -> API -> Web`, including strict-failure behavior, report-path closure, and health visibility requirements.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the English and Chinese formal documents and both change logs were updated together.
- Unfinished items:
  - The plan is archived as a formal implementation document; the runtime code path still needs the actual API-to-worker closure work to be implemented and verified.

### Infrastructure Host Port Exposure

- Change type: functional development.
- Affected modules: `infra/docker-compose.yml` and change logs.
- Main changes:
  - Exposed MySQL to the host with non-default port mapping `${MYSQL_PORT:-13306}:3306`.
  - Exposed Redis to the host with non-default port mapping `${REDIS_PORT:-16379}:6379`.
  - Exposed Qdrant to the host with non-default port mapping `${QDRANT_PORT:-16333}:6333`.
  - Kept container-internal service ports unchanged so existing inter-container URLs and local startup scripts continue to work.
- Verification results:
  - Reviewed `infra/scripts/start-local.ps1` and `infra/scripts/start-all.ps1`, which already wait on host ports `13306`, `16379`, and `16333`.
  - Performed a configuration-level verification by re-reading `infra/docker-compose.yml` after the change.
  - Did not run `docker compose up` in this turn, so live container reachability was not executed.
- Unfinished items:
  - A live Docker startup check is still needed to confirm the three host port mappings are reachable on the target machine.

### Conversation Schema Compatibility Fix

- Change type: functional development.
- Affected modules: `services/api` and change logs.
- Main changes:
  - Added `ConversationSchemaMigration` in `services/api` to repair legacy conversation-storage schema on startup when an existing MySQL `messages` table is missing newer conversation-memory columns.
  - Backfilled the runtime compatibility path for `message_type`, message evidence/confidence fields, active-context markers, regional metadata, and the `conversation_summaries` table so the message streaming flow can run against older databases without manual emergency SQL first.
  - Added `ConversationSchemaMigrationTest` to prove an old `messages` table can be upgraded and then support both message persistence and summary persistence.
- Verification results:
  - In `services/api`, `mvn -Dtest=ConversationSchemaMigrationTest test`: 1 test passed.
  - In `services/api`, `mvn -Dtest=MessageStreamApiTest test`: 3 tests passed.
  - In `services/api`, `mvn test`: 17 tests passed.
- Unfinished items:
  - Existing deployed databases still need one application restart so the startup migration can execute against the live schema.

### Nginx Authorization Forwarding Fix

- Change type: functional development.
- Affected modules: `infra/nginx` and change logs.
- Main changes:
  - Updated `infra/nginx/nginx.conf` so the reverse proxy explicitly forwards the incoming `Authorization` header to `services/api`.
  - Fixed the deployment path where login succeeded through nginx but authenticated API routes such as `POST /api/conversations` and `POST /api/conversations/{id}/messages/stream` were rejected with `401 Unauthorized`.
  - Kept the existing SSE buffering and timeout settings unchanged while narrowing the fix to the authentication handoff at the proxy boundary.
- Verification results:
  - Reproduced the bug against `http://192.168.31.91`: `POST /api/auth/login` returned `200 OK`, while authenticated `POST /api/conversations` returned `401 Unauthorized` before the config change.
  - Verified repository behavior in `services/api/src/test/java/com/bizsage/api/MessageStreamApiTest.java`, which shows the protected conversation and message-stream endpoints succeed when `Authorization` reaches Spring Security.
  - Did not run a full nginx reload or end-to-end post-change remote verification in this environment.
- Unfinished items:
  - The target deployment still needs the updated nginx configuration to be reloaded or redeployed before the live `192.168.31.91` instance will stop returning `401`.

### Three-Layer Memory Plan Archive

- Change type: documentation maintenance.
- Affected modules: `docs/superpowers/plans` and change logs.
- Main changes:
  - Added `docs/superpowers/plans/2026-07-05-three-layer-memory.md` as the English implementation plan archive for the three-layer memory rollout.
  - Added `docs/superpowers/plans/2026-07-05-three-layer-memory-zh-CN.md` as the matching Chinese version.
  - Preserved the task-by-task rollout order covering schema, API persistence, long-term memory, AI worker support, summarization, and web verification.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the English and Chinese plan files and both change logs were updated together.
- Unfinished items:
  - The archived plan reflects the implementation breakdown reviewed on `2026-07-05`; if later execution diverges, the plan archive should be refreshed accordingly.

### Agent Standards Wording Cleanup

- Change type: documentation maintenance.
- Affected modules: `AGENTS.md`, `AGENTS-zh-CN.md`, `docs/en/standards`, `docs/zh-CN/standards`, and change logs.
- Main changes:
  - Removed the "smallest clear, testable implementation" wording from the English root agent guidance and the English agent development standards.
  - Removed the matching "最小、清晰、可测试的实现" wording from the Chinese root agent guidance and the Chinese agent development standards.
  - Kept the remaining milestone, bilingual-documentation, verification, and safety requirements unchanged.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the English and Chinese standards documents and change logs were updated together.
- Unfinished items:
  - No additional unfinished work for this wording update.

### Three-Layer Agent Memory

- Change type: functional development.
- Affected modules: `services/api`, `services/ai-worker`, `apps/web`, `infra/mysql/init`, and change logs.
- Main changes:
  - Added short-term conversation memory persistence in the API through `messages`, active-context filtering, and rolling conversation summaries.
  - Added long-term memory storage in MySQL for user preferences and reusable business facts, plus a `user_memory_embeddings` sidecar table for unstructured memories that should sync to Qdrant later.
  - Reworked the diagnosis flow so follow-up questions reuse the same conversation, read recent messages plus summaries plus long-term memory, and persist both user and assistant turns.
  - Added AI worker memory helpers for summary assembly, long-term memory extraction, and vector-sync eligibility decisions.
  - Updated the web diagnosis workflow to reuse the current conversation id until the user starts a new conversation.
- Verification results:
  - `mvn test` in `services/api`: 16 tests passed.
  - `python -m pytest` in `services/ai-worker`: 24 tests passed.
  - `npm test -- envelope.test.mjs` in `apps/web`: 9 tests passed.
  - `npm run build` in `apps/web`: Next.js production build completed successfully.
- Unfinished items:
  - The API diagnosis path now uses the new memory-aware local diagnosis service; the cross-service API-to-AI-worker runtime handoff is prepared by schema and worker helpers but is not yet wired as the production execution path.
  - Qdrant synchronization is currently recorded in MySQL as pending semantic-memory work items; no live background sync job was added in this change.
  - Manual browser verification and full-stack Docker startup were not executed in this environment.

### Component Interaction And Data Flow Archive

- Change type: documentation maintenance.
- Affected modules: `docs/en`, `docs/zh-CN`, and change logs.
- Main changes:
  - Added `docs/en/component-interactions-and-data-flows.md` as the archived English reference for current and target runtime interaction maps.
  - Added `docs/zh-CN/component-interactions-and-data-flows-zh-CN.md` with matching Chinese content.
  - Documented both Mermaid and ASCII diagrams for the current implementation map, target architecture map, diagnosis request sequence, and collection/ingestion sequence.
  - Recorded the current implementation gaps between the repository's live code paths and the target architecture flow.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the English and Chinese documents were added as a matched pair and both change logs were updated in the same change.
- Unfinished items:
  - The archived flow maps describe the repository state reviewed on `2026-07-05`; they will need refresh when the API-to-AI-worker diagnosis path or collector-to-storage automation changes.

### MySQL Redis Qdrant Real Integration

- Change type: functional development.
- Affected modules: `services/api`, `services/collector`, `services/ai-worker`, and V2 verification documentation.
- Main changes:
  - Kept the API MySQL persistence path on `JdbcTemplate` and exposed `GET /api/knowledge` for real knowledge listing.
  - Added a Redis-backed collector runtime state adapter and wired route-level fingerprint deduplication plus recent snapshot fallback into real execution paths.
  - Added deterministic embeddings and a Qdrant vector-store adapter, then switched AI worker retrieval to Qdrant-first while preserving Python-side region, industry, entitlement, and quality reranking.
  - Hardened AI worker request isolation so request-scoped knowledge cannot leak into later Qdrant-backed searches or diagnosis calls.
- Verification results:
  - `mvn test` in `services/api`: 14 tests passed.
  - `python -m pytest` in `services/collector`: 22 tests passed.
  - `python -m pytest` in `services/ai-worker`: 21 tests passed.
- Unfinished items:
  - Docker Compose startup and live MySQL, Redis, and Qdrant health validation were not executed in this environment.
  - Backup and restore drills, load testing, and long-running gray-release stability observation remain pending.

### Web Authenticated Identity Panel Guard

- Change type: functional maintenance.
- Affected modules: `apps/web`.
- Main changes:
  - Added a regression test that keeps the main workspace behind the login gate.
  - Added a regression test that verifies the authenticated identity panel contains profile details instead of login controls.
  - Added an accessible label to the authenticated identity panel so the state boundary is explicit and testable.
- Verification results:
  - `npm test` in `apps/web`: 6 tests passed.
  - `npm run build` in `apps/web`: Next.js production build completed successfully.
- Unfinished items:
  - Manual browser login flow was not executed because the local API service was not started in this turn.

### Agent Standards Merge

- Change type: documentation maintenance.
- Affected modules: root contributor documentation and change log.
- Main changes:
  - Added the contents of `AGENTS-b.md` into `AGENTS.md` as agent development standards.
  - Added matching Chinese guidance to `AGENTS-zh-CN.md`.
  - Preserved contributor guide sections while restoring governance, bilingual documentation, milestone, verification, and safety rules.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the English and Chinese root guides both contain the merged agent standards.
- Unfinished items:
  - None.

### Contributor Guide Refresh

- Change type: documentation maintenance.
- Affected modules: root contributor documentation and change log.
- Main changes:
  - Recreated `AGENTS.md` as a concise repository contributor guide.
  - Updated `AGENTS-zh-CN.md` with matching Chinese guidance.
  - Documented project structure, local commands, style, testing, PR expectations, and agent-specific notes.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the matching Chinese guide and change log entry were updated in the same change.
- Unfinished items:
  - None.

### Web Login Gate And Bilingual Interface

- Change type: functional development.
- Affected modules: `apps/web` and V2 documentation.
- Main changes:
  - Changed the Web app so signed-out users see only a standalone BizSage login screen.
  - Added Chinese/English in-page language switching on both the login screen and authenticated workspace.
  - Added logout cleanup for profile, diagnosis, report, paid intelligence, metrics, and source modal state.
  - Updated V2 milestone and verification records, and restored readable UTF-8 Chinese for affected documentation.
- Verification results:
  - `npm test` in `apps/web`: 5 tests passed.
  - `npm run build` in `apps/web`: Next.js production build completed successfully.
- Unfinished items:
  - Manual browser login flow was not executed because the local API service was not started in this turn.

## 2026-07-04

### Local Full-Stack Startup Script

- Change type: functional development.
- Affected modules: `infra`, local deployment documentation, and change log.
- Main changes:
  - Added `infra/scripts/start-all.ps1` to start Docker infrastructure, API, AI worker, collector, and Web from one command.
  - Added hidden background process startup with logs and PID files under `logs/local/`.
  - Added options for skipping infrastructure, skipping dependency installation, and opening the Web URL.
  - Updated English and Chinese local deployment docs with the one-command path.
- Verification results:
  - PowerShell parser check for `infra/scripts/start-all.ps1`: `START_ALL_SYNTAX_OK`.
- Unfinished items:
  - Full runtime startup was not executed because it depends on local Docker and long-running services.

## 2026-07-04

### V1/V2 Implementation Closure

- Change type: functional development.
- Affected modules: `services/api`, `services/ai-worker`, `apps/web`, `infra`, and `docs`.
- Main changes:
  - Replaced core API in-memory stores for users, conversations, intelligence, paid intelligence, and knowledge with JDBC-backed repositories and H2-backed test schema.
  - Fixed user-visible garbled diagnosis and seed knowledge text in the API, AI worker, Web console, and database seed correction path.
  - Connected the Web console to real API-client calls for login, conversation creation, SSE diagnosis, diagnosis report metadata, paid intelligence, and ops metrics.
  - Changed V2 ops review, alert, and audit APIs to query persisted tables instead of hard-coded lists.
  - Updated V1/V2 verification records and V2 milestone status in English and Chinese.
- Verification results:
  - `mvn test` in `services/api`: 12 tests passed.
  - `python -m pytest` in `services/collector`: 11 tests passed.
  - `python -m pytest` in `services/ai-worker`: 6 tests passed.
  - `npm test` in `apps/web`: 3 tests passed.
  - `npm run build` in `apps/web`: production build completed successfully.
- Unfinished items:
  - Docker startup, backup restore drill, 50-concurrent load test, 4-hour V1 stability observation, true PDF binary export, and 7-day V2 gray stability remain pending.

## 2026-07-04

### V2 Gray-Release Engineering Skeleton

- Change type: functional development.
- Affected modules: `services/api`, `services/collector`, `services/ai-worker`, `apps/web`, `infra`, `docs`, and database baseline.
- Main changes:
  - Activated V2 after M0 scope lock for internal operators plus seed paid users.
  - Added V2 login profile fields, seed paid user, independent paid intelligence APIs, paid/free permission filtering, diagnosis report metadata, and operator metrics/review/audit APIs.
  - Added collector resilience helpers for incremental fingerprints, retry exhaustion, circuit breaker state, dead-letter classification, and recent snapshot fallback.
  - Added AI worker entitlement-aware retrieval filters and reasoning self-check status for suspicious conflicts and insufficient evidence.
  - Added Web V2 gray metrics, paid intelligence, review, and audit panels.
  - Added MySQL/Qdrant backup and restore scripts, V2 schema targets, API/database/deployment docs, and V2 verification records.
- Verification results:
  - `mvn test` in `services/api`: 12 tests passed.
  - `python -m pytest` in `services/collector`: 11 tests passed.
  - `python -m pytest` in `services/ai-worker`: 6 tests passed.
  - `npm test` in `apps/web`: 2 tests passed.
  - `npm run build` in `apps/web`: production build completed successfully.
- Unfinished items:
  - Docker startup, backup restore drill, 50-concurrent load test, 4-hour V1 stability observation, and 7-day V2 gray stability remain not executed in this environment and are tracked in the V2 verification document.

## 2026-07-04

### Local Service Startup Commands

- Change type: documentation maintenance.
- Affected modules: local deployment documentation and change log.
- Main changes:
  - Added local startup commands for the API service, AI worker, collector, and web app to `docs/en/deployment/local-deployment.md`.
  - Documented the default local ports: API `8080`, AI worker `8100`, collector `8200`, and web app `3000`.
  - Kept the documented web service boundary that the web app should call only the API service directly.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the matching Chinese deployment document was updated in the same change.
- Unfinished items:
  - None.

## 2026-07-04

### Milestone Detail Completion And Docs Language Reorganization

- Change type: documentation maintenance.
- Affected modules: `docs/`, milestone documentation, root agent standards, bilingual audit, and change log.
- Main changes:
  - Added detailed V2 production high-availability milestone documents:
    - `docs/en/milestones/v2-production-high-availability-milestones.md`
    - `docs/zh-CN/milestones/v2-production-high-availability-milestones-zh-CN.md`
  - Added detailed V3 full-domain commercial final milestone documents:
    - `docs/en/milestones/v3-full-domain-commercial-final-milestones.md`
    - `docs/zh-CN/milestones/v3-full-domain-commercial-final-milestones-zh-CN.md`
  - Reorganized `docs/` into language directories: English documentation under `docs/en/`, Chinese documentation under `docs/zh-CN/`.
  - Updated product milestone roadmap, root agent standards, and bilingual audit docs for the new paths.
- Verification results:
  - Documentation language pair check returned `NO_MISSING_DOCS_LANGUAGE_PAIRS`.
  - Stale old-path scan for `docs/api`, `docs/database`, `docs/deployment`, `docs/milestones`, `docs/standards`, and old top-level governing document paths returned no matches.
  - Confirmed `docs/` now contains only `docs/en/` and `docs/zh-CN/`, with 19 Markdown files in each language directory.
- Unfinished items:
  - V2 and V3 are still planned stages; implementation requires meeting and approving each stage's entry criteria.

## 2026-07-04

### Governing Documentation Bilingual Closure

- Change type: documentation maintenance.
- Affected modules: governing documentation and change log.
- Main changes:
  - Added English counterparts for the five merged Chinese governing documents:
    - `docs/en/data-collection-and-intelligence-perception.md`
    - `docs/en/development-implementation-guide.md`
    - `docs/en/product-strategy-and-design.md`
    - `docs/en/risk-management-and-compliance.md`
    - `docs/en/system-architecture-and-framework.md`
  - Preserved the same governing scope across product strategy, system architecture, implementation planning, data collection/intelligence perception, and risk/compliance.
  - Closed the previously recorded bilingual documentation gap for these five governing documents.
- Verification results:
  - Markdown bilingual pair check returned `NO_MISSING_PAIRS` after excluding generated/cache directories.
  - Confirmed all five English governing documents exist with their matching `*-zh-CN.md` counterparts.
- Unfinished items:
  - Superseded by the milestone detail completion entry above.

## 2026-07-04

### Milestone Stage Realignment

- Change type: documentation maintenance.
- Affected modules: product milestone documentation and change log.
- Main changes:
  - Realigned the cross-version roadmap to the source PDF stage model: V1 MVP minimum viable version, V2 production high-availability engineering version, and V3 full-domain commercial final version.
  - Removed the previously separate V4 scale/ecosystem stage because the source PDF defines only three iterations.
  - Mapped high availability, disaster recovery, multi-model routing, complete RBAC, and basic paid-user gray release to V2.
  - Mapped dynamic source weighting, full data lineage, complete commercial membership, full security, H5 mobile adaptation, audit console, operations reports, and 99.9% SLA readiness to V3.
- Verification results:
  - Reviewed `raw-docs/行业智能创业Agent平台全域完整架构设计文档（V4.0_全域封顶终版）配套分阶段落地开发规划说明书.pdf` through the extracted text in `raw-docs/txt/`.
  - Confirmed the previous roadmap did not match the source stage count because it included V4.
- Unfinished items:
  - Superseded by the milestone detail completion entry above.
  - English counterparts for the five merged Chinese governing documents were added in the bilingual closure entry above.

## 2026-07-04

### Governing Specification Alignment

- Change type: documentation maintenance.
- Affected modules: root agent standards and milestone documentation.
- Main changes:
  - Added the five governing product, architecture, implementation, data-collection, and risk/compliance documents to the root agent standards.
  - Added a specification coverage matrix to the cross-version product milestone roadmap.
  - Clarified that V1 implements only the MVP/P0 subset of the governing documents and that later capabilities require detailed bilingual milestones before implementation.
- Verification results:
  - Reviewed the active V1 milestone and cross-version roadmap against the five governing documents.
  - Initial Markdown bilingual pair check reported five missing English counterparts for the newly merged Chinese governing documents; those were added in the bilingual closure entry above.
- Unfinished items:
  - Superseded by the milestone detail completion entry above.

## 2026-07-04

### Product Milestone Roadmap

- Change type: documentation maintenance.
- Affected modules: milestone documentation and product planning.
- Main changes:
  - Added the cross-version product milestone roadmap in `docs/en/milestones/product-milestones.md`.
  - Added the matching Chinese version in `docs/zh-CN/milestones/product-milestones-zh-CN.md`.
  - Initially defined V1 as the active implementation version and V2/V3/V4 as planning targets only; this was superseded by the milestone stage realignment above.
- Verification results:
  - Markdown bilingual pair check returned `NO_MISSING_PAIRS`.
  - Old naming and stale reference scan returned no matches.
- Unfinished items:
  - Superseded by the stage realignment above; current deferred detailed milestones are V2 and V3.

## 2026-07-04

### Bilingual Documentation Backfill

- Change type: documentation maintenance.
- Affected modules: root documentation, Android docs, API docs, database docs, deployment docs, milestone docs, acceptance docs, and standards docs.
- Main changes:
  - Standardized bilingual naming: English uses default `*.md`; Chinese uses `*-zh-CN.md`.
  - Renamed existing bilingual files to the new convention, including `AGENTS.md`, `AGENTS-zh-CN.md`, `CHANGELOG.md`, and `CHANGELOG-zh-CN.md`.
  - Added Chinese versions for historical single-language documents.
  - Updated agent development standards and root `AGENTS.md` with the new naming convention.
  - Updated the bilingual documentation audit to show no remaining Markdown documentation gaps, excluding generated/cache files.
- Verification results:
  - Checked Markdown files outside dependency, build, and cache directories.
  - Confirmed each English `*.md` documentation file has a matching `*-zh-CN.md` file where required.
- Unfinished items:
  - None for Markdown documentation currently in scope.

## 2026-07-04

### Documentation Standards

- Change type: documentation and process standards.
- Affected modules: root-level agent standards, documentation standards, change log, and milestone maintenance process.
- Main changes:
  - Added root-level `AGENTS.md` as the primary entry point for agent development standards.
  - Added the matching English root version `AGENTS.md`.
  - Added the requirement that every functional change must update both `CHANGELOG.md` and `CHANGELOG-zh-CN.md`.
  - Added the requirement that every milestone progress update must update the milestone documents.
  - Added a bilingual documentation maintenance audit report.
- Verification results:
  - Confirmed that the key bilingual rules exist in both Chinese and English standards.
  - Generated the current list of documents missing bilingual maintenance.
- Unfinished items:
  - Superseded by the bilingual documentation backfill above. See `docs/en/standards/documentation-bilingual-audit.md`.
