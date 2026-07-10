# Change Log

## 2026-07-11

### End-To-End Agent Streaming

- Change type: functional development and streaming protocol update.
- Affected modules: `services/ai-worker`, `services/api`, `apps/web`, paired API/architecture documentation, and both change logs.
- Main changes:
  - Added real OpenAI-compatible model streaming for diagnosis, learning, and cross-agent transitions, including answer-only delta parsing, provider failover, and upstream cancellation through stream closure.
  - Added streamed self-check semantics: candidate deltas are validated after generation, failed candidates emit `reset` before retry, exhausted retries are replaced by the controlled uncertain response, and only the final result is persisted.
  - Added AI Worker SSE endpoints and Java SSE consumption while preserving synchronous Agent endpoints for PDF/report compatibility.
  - Replaced API-side 24-character snapshots with immediate `status`/`delta`/`reset` forwarding, one final `diagnosis` event, no-cache/no-buffer headers, and shared final persistence for all three Agent paths.
  - Reworked the Web stream decoder to handle fragmented frames and split UTF-8 characters, append deltas, clear reset candidates, and display a bilingual retry status.
- Verification results:
  - Synced and checked CodeGraph before editing the affected call paths.
  - Verified AI Worker streaming, routing, retry, diagnosis, learning, and transition tests with `python -m pytest`; all 76 selected tests passed. The full suite reached 169 passed and 2 existing unrelated failures in stale `generate_answer` mocking and vector payload expectations.
  - Verified API streaming with `MessageStreamApiTest` and `AiWorkerClientStreamTest`; all 9 tests passed, including a delayed local HTTP stream that delivers a delta before its final result.
  - Verified `apps/web` with `npm test` (40/40 passed) and `npm run build`; the fragmented SSE decoder tests and Next.js production build passed.
- Unfinished items:
  - A live external model plus Docker/Nginx end-to-end timing drill was not run in this environment; provider behavior was verified with deterministic streaming doubles and the API transport with a delayed local HTTP server.
  - The two unrelated pre-existing AI Worker full-suite failures remain for separate test-maintenance work.

### Empty Diagnosis Composer Initial Draft

- Change type: functional repair.
- Affected modules: `apps/web` and both change logs.
- Main changes:
  - Changed the diagnosis composer draft state to start as an empty string so the input has no default submit-ready text.
  - Removed the unused bilingual default-question message field from the web page message contract.
  - Added a frontend source assertion that the diagnosis composer starts empty and no longer defines `defaultQuestion`.
- Verification results:
  - Used CodeGraph before manual file inspection to locate the diagnosis workspace and page state paths.
  - Verified `apps/web` with `npm test`; all 39 frontend tests passed.
  - Verified `apps/web` with `npm run build`; the Next.js production build passed after installing lockfile dependencies with `npm ci`.
- Unfinished items:
  - None.

## 2026-07-10

### Consolidated Flyway V1 And Admin I18n Isolation

- Change type: database migration consolidation, frontend internationalization, documentation, and test maintenance.
- Affected modules: `services/api`, `apps/web`, `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Consolidated source Flyway migrations into `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql` and removed the obsolete MySQL `V2` through `V4` migration files for resettable environments.
  - Merged the H2 admin seed/SLA setup into `services/api/src/test/resources/db/migration/h2/V1__baseline.sql` and changed the test profile to load only the H2 V1 baseline.
  - Added admin i18n helpers for backend enum/status/code labels and wired admin dashboard, collection, monitoring, governance, knowledge, alerts, audit, reviews, tickets, human intelligence, and risk-rule views through localized display text.
  - Kept API payload values stable while translating displayed enum/status values, entitlement labels, source types, risk-rule categories, and common business codes such as `general`, `cn-default`, and `sales-payment`.
  - Updated source-map documentation to describe the single consolidated MySQL Flyway baseline.
  - Updated frontend source-assertion tests to match the current user-profile restore flow and expanded admin workspace sections.
- Verification results:
  - Used CodeGraph before manual file inspection and synced CodeGraph after source edits.
  - Verified `apps/web` with `npm run build`; the Next.js production build passed.
  - Verified `apps/web` with `npm test`; all 38 frontend tests passed.
  - Verified `services/api` with `mvn test`; all 38 API tests passed with the consolidated H2 V1 baseline.
- Unfinished items:
  - Live MySQL Flyway execution against a freshly reset database was not run in this environment.

### Flyway-Owned MySQL Baseline And Idempotent Migrations

- Change type: database migration and deployment configuration change.
- Affected modules: `services/api`, `infra`, `.env.example`, AGENTS instructions, database/deployment documentation, and both change logs.
- Main changes:
  - Moved the MySQL V1 baseline into `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql`, making Flyway the single source for MySQL schema creation and upgrades.
  - Removed the parallel Docker MySQL init schema and stopped mounting `infra/mysql/init` into the all-in-one MySQL container.
  - Enabled Flyway by default through application configuration, `.env.example`, and both compose API environments so fresh databases run `V1` through the latest migration at API startup.
  - Made `V4__user_preferred_locale.sql` conditionally add `users.preferred_locale` only when the column is missing.
  - Hardened the V1 seed section with `WHERE NOT EXISTS` guards so seed data remains idempotent when SQL is inspected or rerun manually.
  - Added the development-standard rule that Flyway SQL migrations must remain idempotent and must not be duplicated by parallel Docker/MySQL init schema scripts.
- Verification results:
  - Used CodeGraph before manual file inspection to locate Flyway, database configuration, and deployment paths.
  - Verified `services/api` with `mvn -DskipTests clean compile`; compilation succeeded.
  - Ran `mvn test`; the suite did not pass because existing `MessageStreamApiTest` assertions around rolling summaries and memory refresh failed, including two `ConcurrentModification` errors. The failing tests use the H2 test profile and do not execute the MySQL Flyway migration path changed here.
- Unfinished items:
  - Docker CLI and a live MySQL client are not available in the current environment, so `docker compose -f infra/docker-compose-all.yml up -d` and real MySQL Flyway execution still need verification in a Docker-enabled environment.
  - Databases that already recorded a failed or old successful checksum for `V4__user_preferred_locale.sql` must reset the failed row or run Flyway repair before starting with the updated migration.

### User-Bound Web/Admin Language Preference

- Change type: functional change.
- Affected modules: `apps/web`, `services/api`, `infra/mysql/init/001_v1_baseline.sql`, API/database documentation, and both change logs.
- Main changes:
  - Added `preferred_locale` to the `users` schema, MySQL baseline, H2 test baseline, and MySQL Flyway V4 migration.
  - Included `preferredLocale` in login and current-user profiles, and added `PUT /api/users/me/locale` to persist `zh-CN` or `en` for the authenticated user.
  - Updated the web main page to restore, switch, and persist the shared user language preference instead of keeping language only in local component state.
  - Added admin login/topbar language switching and wired admin dashboard, collection, monitoring, and governance views to the shared user language state.
- Verification results:
  - Used CodeGraph before manual file inspection to trace auth, user profile, admin page, and frontend API paths.
  - Verified `apps/web` with `npm run build`; the Next.js production build passed.
  - Verified `services/api` with `mvn test`; all 38 tests passed.
  - Verified the focused user profile behavior with `mvn test -Dtest=V2GrayReleaseApiTest`.
- Unfinished items:
  - Existing databases that run with Flyway disabled must apply `V4__user_preferred_locale.sql` manually or otherwise add `users.preferred_locale` before deploying this change.
  - Some admin table row data and server-provided enum/status values still display as stored backend values rather than translated labels.

### Compose Service Endpoint Defaults

- Change type: deployment configuration repair.
- Affected modules: `.env.example`, `infra/docker-compose.yml`, `infra/docker-compose-all.yml`, and both change logs.
- Main changes:
  - Changed the example container-network endpoints from host-only addresses to Compose service names: `mysql`, `redis`, `qdrant`, `ai-worker`, and `collector`.
  - Updated the example MySQL, Redis, and Qdrant ports to their in-network container ports: `3306`, `6379`, and `6333`.
  - Passed `COLLECTOR_URL` into the API container in both compose files so scheduled collection calls the `collector` service instead of the API container's `localhost`.
  - Kept API-to-AI-worker traffic on the Compose service endpoint `http://ai-worker:8100`, avoiding stale host-only values from existing `.env` files.
- Verification results:
  - Used CodeGraph and targeted file inspection to trace the scheduled collection failure through `AdminCollectionScheduler`, `AdminCollectionStore`, and `AdminCollectionMapper`.
  - Searched the deployment configuration to confirm the remaining service-to-service defaults now use Compose service names for container networking.
- Unfinished items:
  - Docker CLI is not installed in the current environment, so `docker compose config` and a live `docker compose -f infra/docker-compose-all.yml up -d` verification still need to be run in a Docker-enabled environment.

## 2026-07-09

### Alert Rule Collection Source Table Alignment

- Change type: functional repair.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Updated the circuit-breaker alert evaluator to query the current `admin_collection_sources` table instead of the retired `collection_source_configs` table.
  - Added a focused regression test proving open collection-source circuits create alerts from the current table shape.
- Verification results:
  - Used CodeGraph to inspect the alert rule and collection source paths before editing.
  - Verified with `mvn -Dtest=AlertRuleEngineTest test` in `services/api`; the focused alert-rule regression passed.
- Unfinished items:
  - None.

### Flyway Startup Pause And MySQL Init Baseline

- Change type: deployment configuration change.
- Affected modules: `services/api`, `infra`, `.env.example`, and both change logs.
- Main changes:
  - Changed API Flyway startup migrations to be disabled by default through `BIZSAGE_FLYWAY_ENABLED=false`.
  - Kept an explicit enable switch by wiring `BIZSAGE_FLYWAY_ENABLED` through both production and all-in-one compose API environments.
  - Confirmed the MySQL initialization baseline already contains the current runtime schema, including Admin, collection, SLA, risk-rule, and user-memory uniqueness tables/indexes.
- Verification results:
  - Used CodeGraph to inspect API startup/configuration paths before editing.
  - Verified with `mvn -DskipTests clean compile` in `services/api`; compilation succeeded.
- Unfinished items:
  - Docker is not installed in the current environment, so compose startup and MySQL initialization still need to be run in a Docker-enabled development environment.
  - Existing databases still require manual schema alignment before starting with Flyway disabled, because disabled Flyway will not upgrade older schemas automatically.

### User Memory Flyway V3 Idempotency Fix

- Change type: functional repair.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Made `V3__user_memory_profile_uniqueness.sql` skip the unique-constraint `ALTER TABLE` when any unique index already covers `(user_id, memory_category, memory_key, status)`.
  - Preserved the duplicate-collapse step for existing MySQL databases while allowing fresh development databases initialized from `infra/mysql/init/001_v1_baseline.sql` to pass Flyway V3.
- Verification results:
  - Used CodeGraph to inspect the memory profile code path before editing.
  - Reviewed the MySQL baseline and confirmed it already creates `uk_user_memory_profiles_natural`, matching the fresh-development startup failure.
- Unfinished items:
  - Docker and a live MySQL client are not installed in the current environment, so the fixed migration still needs to be run against a fresh development database.
  - Any development database that already recorded the failed V3 migration must be reset or repaired before the API can start again.

### User Memory Profile Compile Fix

- Change type: functional repair.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Fixed the `UserMemoryProfile` all-arguments constructor to assign `key` and `value` parameters to the mapped `memoryKey` and `memoryValue` fields.
  - Restored API compilation after the memory profile fields were renamed for MyBatis-Plus column mapping.
- Verification results:
  - Verified with `mvn -DskipTests clean compile` in `services/api`; compilation succeeded.
- Unfinished items:
  - None.

### Compose Middleware Extraction

- Change type: deployment configuration change.
- Affected modules: `infra/docker-compose.yml`, `.env.example`, and both change logs.
- Main changes:
  - Removed the in-compose MySQL, Redis, and Qdrant service definitions from `infra/docker-compose.yml`, leaving only BizSage-owned services (`api`, `ai-worker`, `collector`, `web`) and `nginx`.
  - Removed the MySQL and Qdrant named volumes that were only used by the extracted middleware services.
  - Switched API and AI worker middleware connection settings to environment-provided external addresses via `DB_URL`, `REDIS_HOST`, `REDIS_PORT`, and `QDRANT_URL`.
  - Updated `.env.example` with external middleware defaults and the new `DB_URL` and `NGINX_PORT` examples.
- Verification results:
  - Used CodeGraph to inspect `infra/docker-compose.yml` before editing and confirmed no indexed file depends on it.
  - Parsed `infra/docker-compose.yml` with PyYAML; the resulting service list is `api`, `ai-worker`, `collector`, `web`, and `nginx`, with no `volumes` section.
  - Searched the compose file to confirm no `mysql`, `redis`, or `qdrant` service blocks and no `mysql_data` or `qdrant_data` volumes remain.
- Unfinished items:
  - `docker compose config` could not be executed in this environment because the Docker CLI is not installed.
  - Target deployments must provide separately deployed MySQL, Redis, and Qdrant endpoints through the `.env` values before starting the retained services.

### Core Code Chinese Comment Enrichment

- Change type: documentation-only code comments.
- Affected modules: `services/ai-worker/app`, `services/collector/app`, `services/api/src/main/java/com/bizsage/api`, and both change logs.
- Main changes:
  - Added and refined Chinese module, method, and inline comments for the AI Worker diagnosis, learning, transition, RAG, context compression, memory, model routing, vector-store, and self-check retry paths.
  - Added Chinese comments for Collector collection adapters, governance filtering, conflict classification, Redis state, resilience, API cache, proxy rotation, vendor failover, and HTTP entry points.
  - Added Chinese comments for API core orchestration around AI Worker calls, SSE message streaming, diagnosis/learning persistence, authentication, request IDs, startup knowledge sync, memory embedding sync, Collector integration, admin collection scheduling, snapshots, and privacy helpers.
  - Replaced a visible mojibake comment in `AdminCollectionStore` with a readable Chinese explanation while leaving runtime behavior unchanged.
- Verification results:
  - Verified the CodeGraph index was up to date before selecting core paths.
  - Verified Python syntax with `python -m compileall services/ai-worker/app services/collector/app`; all modified Python modules compiled successfully.
  - Ran `mvn test` in `services/api`; the run reached tests but failed existing business assertions unrelated to comment-only edits, including knowledge/intelligence list expectations, operations work-order shape, admin collection/knowledge lifecycle 500s, and rolling-summary count assertions.
  - Ran `mvn -DskipTests clean compile` in `services/api`; clean compilation failed in unmodified `UserMemoryProfile.java` because `key` and `value` symbols are unresolved.
- Unfinished items:
  - Existing API compile/test failures remain and should be handled separately from this comment-only cleanup.
  - Some peripheral Java modules outside the selected core paths still contain older English `V2` comments and can be localized in a follow-up pass if desired.

### RAG Control-Plane Refactor Recommendation Archive

- Change type: documentation.
- Affected modules: `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Added `docs/en/rag-control-plane-and-boundary-refactor-recommendation.md` to capture the current API/AI-worker RAG split, the recommended control-plane placement, and a phased boundary-refactor path.
  - Added the paired Chinese document `docs/zh-CN/rag-control-plane-and-boundary-refactor-recommendation-zh-CN.md` with the same scope, conclusions, migration phases, risks, and first work items.
  - Recorded the recommended target shape as `API = control plane` and `AI worker = RAG execution plane`, while explicitly noting the current boundary drift around inline knowledge assembly and worker-owned business-filter semantics.
- Verification results:
  - Verified the new English and Chinese documents keep the same facts, recommendations, migration phases, risks, and next-step guidance.
- Unfinished items:
  - The recommendation is not yet implemented in runtime code; the online diagnosis and learning paths still need contract changes and regression coverage before the boundary can be tightened.
  - The current repository still needs a follow-up implementation plan that maps the proposed boundary changes to concrete Java and Python code paths.

### User Memory Upsert Hardening

- Change type: functional repair.
- Affected modules: `services/api`, `infra/mysql`, and both change logs.
- Main changes:
  - Added a natural-key uniqueness rule for `user_memory_profiles` on `(user_id, memory_category, memory_key, status)` in both the MySQL baseline and the H2 test baseline.
  - Added Flyway migration `V3__user_memory_profile_uniqueness.sql` to collapse same-key duplicates deterministically before applying the new unique constraint in existing MySQL environments.
  - Reworked `UserMemoryStore.saveOrRefresh()` into an update-first, insert-second flow with duplicate-key retry handling, so concurrent or repeated writes refresh the existing active memory instead of creating multiple rows.
  - Added a regression test proving repeated writes to the same active memory key keep exactly one active row while updating the stored value.
- Verification results:
  - Verified with `mvn -Dtest=MessageStreamApiTest test -q` in `services/api`; the targeted message-stream suite passed with the new uniqueness and refresh semantics.
- Unfinished items:
  - The current hardening guarantees one row per natural key and status, but it does not yet introduce richer conflict states such as `SUPERSEDED` or `CONFLICTED` for mutually incompatible memory values.
  - Production rollout still needs a real MySQL startup validation to confirm Flyway `V3` applies cleanly on top of existing environments.

### Online Vector Memory Path Disabled By Default

- Change type: functional repair.
- Affected modules: `services/api`, `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Removed online vector-memory writes from `DiagnosisService` and `LearningService`, so unstructured memory candidates now persist only to the authoritative MySQL memory store during normal agent interactions.
  - Put `MemorySyncScheduler` behind the new `bizsage.memory.vector-sync-enabled` switch and set the default to `false`, preserving the vector-sync code path for future reactivation without letting it run in the current online architecture.
  - Added an API regression test proving an unstructured memory candidate is still saved into `user_memory_profiles` while `user_memory_embeddings` remains unchanged.
  - Updated the paired implementation-status milestone documents to reflect that rolling summaries and MySQL-backed long-term memory are live, while the vector-memory path is intentionally disabled until retrieval is designed end to end.
- Verification results:
  - Verified with `mvn -Dtest=MessageStreamApiTest test -q` in `services/api`; the targeted message-stream suite passed, including the new unstructured-memory regression case.
  - Verified with `PYTHONPATH=. pytest tests/test_memory_v2.py -q` in `services/ai-worker`; 15 tests passed.
- Unfinished items:
  - The retained `/memory/sync` endpoint, embedding store, and scheduler code are now dormant by default and still need a proper retrieval/read-path design before they should be re-enabled online.
  - No migration has yet removed historical `user_memory_embeddings` rows or introduced cleanup/governance for already-synced vector-memory data.

### Agent Memory Summary Continuity And Source-Of-Truth Alignment

- Change type: functional repair.
- Affected modules: `services/api`, `services/ai-worker`, and both change logs.
- Main changes:
  - Reworked conversation summarization in `DiagnosisService`, `LearningService`, and `ConversationSummaryStore` to keep a single active rolling summary per conversation while carrying forward previously summarized context instead of dropping older turns.
  - Marked API-backed long-term memories as originating from `mysql:user_memory_profiles` before sending them to the AI worker, clarifying that MySQL is the authoritative memory store and the worker consumes pre-filtered memories rather than re-deciding lifecycle expiry.
  - Updated the AI worker's `build_memory_context()` contract to trust API-filtered long-term memories, and added a regression test that ensures worker-side prompt assembly no longer discards memories based on stale local expiry metadata.
  - Renamed internal `UserMemoryProfile` field mappings away from reserved-word-backed property names so MyBatis-Plus generates H2-safe SQL during memory queries and refreshes.
  - Added a message-stream regression test that verifies rolling summaries keep only one active summary row while preserving the earliest summarized rounds across multiple summarization passes.
- Verification results:
  - Verified with `PYTHONPATH=. pytest tests/test_agent.py -q` in `services/ai-worker`; 24 tests passed.
  - Verified with `mvn -Dtest=MessageStreamApiTest test -q` in `services/api`; the targeted SSE/message-summary integration suite passed after the memory query mapping fix.
- Unfinished items:
  - The vector-memory path still only guarantees async write/sync behavior; retrieval back into online agent context remains for a later phase.
  - Memory upsert semantics are still `select + update/insert`; unique constraints and stronger atomicity are still pending.

### API MyBatis-Plus Migration Kickoff

- Change type: functional development.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Replaced the API service's direct JDBC starter dependency with `mybatis-plus-spring-boot3-starter`, enabled `@MapperScan`, and added base MyBatis-Plus configuration in `services/api/src/main/resources/application.yml`.
  - Converted core persistence records used by the user, conversation, knowledge, intelligence, review-ticket, message, summary, and memory flows into MyBatis-Plus entity classes with `@TableName`, `@TableId`, and targeted `@TableField` mappings while preserving record-style accessors for existing callers.
  - Added first-wave mapper interfaces for the migrated entities and moved the `UserStore`, `ConversationStore`, `KnowledgeStore`, `IntelligenceStore`, `PaidIntelligenceStore`, `AdminReviewStore`, `ConversationMessageStore`, `ConversationSummaryStore`, `UserMemoryStore`, and `UserMemoryEmbeddingStore` implementations onto MyBatis-Plus query/update APIs.
  - Restored the auth login envelope's `token` field after the entity migration exposed an existing response-shape regression, and switched test-profile caching to `simple` to avoid Redis dependency during local Maven runs.
- Verification results:
  - Verified with `mvn -DskipTests compile` in `services/api`; the API module compiles with the new MyBatis-Plus baseline.
  - Verified with targeted Maven tests that the authentication response shape is restored and core CRUD paths reach runtime, but the narrowed suite still reports failures in existing paginated API assertions and unmigrated persistence areas.
- Unfinished items:
  - Complete the remaining JDBC-to-MyBatis migration in admin, governance, ops, and snapshot-related classes that still use `JdbcTemplate`.
  - Reconcile API test expectations around paginated envelopes versus flat lists, then rerun the full `services/api` Maven test suite after the remaining persistence paths are migrated.

### Admin JdbcTemplate Residual Migration Completion

- Change type: functional development.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Replaced the remaining admin-side `JdbcTemplate` implementations in `AdminStore`, `AdminKnowledgeStore`, and `AdminCollectionStore` with MyBatis mapper-based persistence.
  - Added dedicated admin mapper interfaces for dashboard telemetry, alert/audit/review/ticket/human-intelligence flows, knowledge lifecycle queries and writes, and collection-source/job/dead-letter persistence.
  - Preserved existing admin API contracts while keeping knowledge publication sync to `knowledge_items`, AI worker Qdrant sync, collection-triggered intelligence creation, and audit-log writes on the MyBatis path.
- Verification results:
  - Verified with `rg -n "JdbcTemplate" services/api/src/main/java/com/bizsage/api`; admin residual references were removed from the API source tree.
  - Planned follow-up verification with `mvn -DskipTests compile` and targeted admin/governance API tests after the mapper migration settled.
- Unfinished items:
  - Run the full compile and targeted Maven suites against the new admin mapper path, then fix any mapper-SQL or H2 compatibility regressions that surface.

### Flyway Database Migration Adoption

- Change type: functional repair.
- Affected modules: `services/api`, `infra/mysql`, `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Added Flyway to the API service and configured startup validation/incremental migration execution from `services/api/src/main/resources/db/migration/mysql/`.
  - Removed the runtime `AdminSchemaMigration` and `ConversationSchemaMigration` code paths plus SLA runtime table creation, so schema changes now come from SQL scripts instead of Java bean initialization.
  - Expanded `infra/mysql/init/001_v1_baseline.sql` to include the latest admin risk-rule, collection-compliance, SLA, and admin seed data definitions, and added a matching Flyway incremental script for upgrades from the manual baseline.
  - Repaired malformed `knowledge_items` seed SQL in the MySQL baseline so a brand-new database can import `001_v1_baseline.sql` without string-literal syntax failures before Flyway takes over.
  - Replaced the test schema bootstrap with scripted H2 migration resources to keep test fixtures aligned with the production schema shape while preserving the local `password` login fixtures used by API tests.
  - Updated paired English and Chinese database, deployment, and admin design documents to describe the new workflow: run the MySQL baseline manually first, then let Flyway validate and upgrade on service startup.
- Verification results:
  - Verified the API module still compiles after removing the runtime schema-migration classes and introducing Flyway dependencies.
  - Planned targeted Maven verification for auth/admin/governance flows against the refreshed H2 bootstrap and for startup migration behavior against the new Flyway configuration.
- Unfinished items:
  - Execute the targeted Maven test suite and a real MySQL startup drill to confirm the manual-baseline-plus-Flyway path end to end.

### Gray-Release YAML Binding Repair

- Change type: functional repair.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Replaced the `GrayReleaseService` SpEL-based `@Value("#{${...}}")` feature-flag injection with native Spring Boot configuration binding via a new `GrayReleaseProperties` class.
  - Converted `bizsage.gray-release.features.*.allowed-memberships` in `services/api/src/main/resources/application.yml` from comma-delimited strings to standard YAML lists, so paid-intelligence and PDF-export cohorts bind safely at startup.
  - Added a focused regression test covering gray-release property binding and feature evaluation without requiring the full database-backed application context.
- Verification results:
  - Verified with `mvn -Dtest=GrayReleaseServiceConfigurationTest test` in `services/api`; the targeted binding test passes and confirms standard list properties load without SpEL parsing errors.
  - Attempted `mvn -Dtest=V2GrayReleaseApiTest test`, but the suite is currently blocked by a pre-existing `schema-test.sql` syntax/encoding failure unrelated to this gray-release change.
- Unfinished items:
  - Repair the malformed SQL seed content in `services/api/src/test/resources/schema-test.sql`, then re-run the full gray-release integration suite.

### API Compile Compatibility Fixes

- Change type: functional repair.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Added the missing `java.io.IOException` import in `AdminCollectionStore` so JSON payload parsing compiles again.
  - Updated `CollectorClient` to use Spring `ParameterizedTypeReference<Map<String, Object>>` with `RestClient.ResponseSpec.body(...)`, matching Spring Boot 3.3 / Spring Framework 6.1 expectations.
  - Reworked `PdfReportGenerator` for PDFBox 3.0 by switching to `PDType1Font`, replacing removed `PDPageContentStream#getFont()` usage with explicit font tracking, and normalizing the affected report strings/comments.
- Verification results:
  - Static source verification completed for the three reported compiler failures and their dependency-specific API usage.
  - Verified with `mvn -DskipTests compile` in `services/api`; the module now builds successfully.
- Unfinished items:
  - Re-run the Docker image build to confirm the containerized `services/api` path stays aligned with the local Maven compile result.
## 2026-07-08

### Dual-Agent Business Core: Learning Agent, Three-Tier Memory, Transition, and Standardized Output

- Change type: functional development.
- Affected modules: `services/ai-worker`, `apps/web`, and both change logs.
- Main changes:
  - **Industry Learning Agent**: Added `services/ai-worker/app/learning_agent.py` implementing the second half of BizSage's dual-Agent system. Supports intent classification (INDUSTRY_OVERVIEW, NODE_LEARNING, METRIC_QUESTION, RISK_QUESTION, HIDDEN_RULE, POLICY_QUESTION) with keyword-based chain-node matching across all 7 nodes. Three learning modes: FAST_START (overview), FULL_CHAIN (systematic deep learning), NODE_DEEP_DIVE (focused node study). Uses a learning-specific system prompt optimized for plain-language teaching. Added `POST /agent/learn` endpoint.
  - **Three-tier memory system**: Enhanced `services/ai-worker/app/memory.py` with five memory categories (PREFERENCE, BUSINESS_FACT, PAIN_POINT, INDUSTRY_CONTEXT, LEARNING_PROGRESS), lifecycle-aware expiry (90-365 days per category), intelligent forgetting via `forget_expired()`, learning-specific memory extraction via `extract_learning_memories()`, and enhanced diagnosis memory extraction with pain-point and industry-context pattern matching.
  - **Dual-Agent transition**: Added `services/ai-worker/app/agent_transition.py` supporting context-preserving mode switches. Learning鈫扗iagnosis transition carries the studied chain node as the diagnosis focus area. Diagnosis鈫扡earning transition identifies weak areas and suggests targeted learning. Added `POST /agent/transition` unified endpoint with `TransitionContext` preview for frontend display.
  - **Standardized dual-Agent output**: Added `services/ai-worker/app/agent_output.py` implementing a unified `AgentOutput` format used by both Agents. Structured sections (key findings, risk alerts, actionable steps, supporting evidence), source traceability, confidence labels, timeliness notes, compliance disclaimers, chain-node context (learning mode), and suggested next actions. Both `render_agent_output()` (full format) and `render_legacy_format()` (backward-compatible diagnosis format) output.
  - **Data model**: Extended `KnowledgeItem` with optional `link_id` field for chain-node filtering; updated `parse_knowledge()` to extract `linkId`/`link_id` from API payloads.
  - **Frontend**: Added `AgentLearnRequest`, `AgentTransitionRequest`, `AgentOutput`, `fetchAgentLearn()`, and `fetchAgentTransition()` to the API client.
- Verification results:
  - 32 new Python tests across 3 test files (6 output, 18 learning agent, 8 transition), all passing.
  - All 25 existing ai-worker tests (7 agent + 18 compressor) continue to pass 鈥?zero regressions.
  - Total: 57/57 tests passing in `services/ai-worker`.
- Unfinished items:
  - Browser-level end-to-end validation of Learning鈫扗iagnosis鈫扡earning transition loop.
  - Web UI: dedicated learning-mode workspace with chain-node navigator (follows existing DiagnosisWorkspace pattern).

### RAG Context Compression

- Change type: functional development.
- Affected modules: `services/ai-worker` and both change logs.
- Main changes:
  - Added `services/ai-worker/app/context_compressor.py` implementing a content-aware context compressor for RAG retrieval results. The compressor: (1) merges near-duplicate knowledge items via character-trigram Jaccard similarity (threshold 0.70), (2) distributes token budget proportionally by relevance score with configurable min/max per-item limits, (3) truncates long content at sentence boundaries preserving readability, (4) estimates token counts conservatively for mixed CJK/ASCII text.
  - Integrated the compressor into `agent.py::diagnose()` 鈥?the naive `"\\n".join(...)` context builder is replaced with a `compress_context()` pipeline that memory-aware token budgeting (2100 tokens when memory context is present, 2400 otherwise).
  - Exposed optional `compress_config` in `DiagnoseRequest` allowing callers to tune `total_token_budget`, `min_chars_per_item`, `max_chars_per_item`, and `merge_similarity_threshold` per request.
- Verification results:
  - 18 new unit tests in `test_context_compressor.py` cover trigram extraction, Jaccard similarity, merge logic (higher-score-as-primary), token estimation, single/multi-item compression, duplicate merging, score-proportional budget distribution, min-char guarantees, and sentence-boundary truncation.
  - All 7 existing `test_agent.py` tests continue to pass with the compression-integrated pipeline.
- Unfinished items:
  - None. Compression is transparent to existing callers 鈥?the default config matches previous behavior for short contexts while protecting against context-window overflow for long results.

### Data Governance: Conflict Engine, Snapshots, and Data Isolation

- Change type: functional development.
- Affected modules: `services/collector`, `services/api`, `apps/web`, `infra/mysql`, and both change logs.
- Main changes:
  - **Seven-layer conflict engine**: Added `services/collector/app/conflict_engine.py` with five-branch classification (short-term fluctuation, regional exception, authoritative update, suspicious conflict, false information) based on SimHash distance comparison, source weight evaluation, and rumor/blocklist detection. Added `POST /govern/conflict-check` FastAPI endpoint and integrated optional conflict detection into the existing `/govern` endpoint.
  - **Time-series snapshots**: Added Java `SnapshotStore`, `SnapshotService`, `SnapshotController`, and `SnapshotScheduler` under `services/api/.../governance/`. Supports DAILY (full dump, 30-day retention), WEEKLY (aggregated by link_id, 12-week retention), and MONTHLY (trend data, 12-month retention) snapshots with automatic scheduled generation and retention cleanup. Added `GET/POST /api/admin/snapshots/**` endpoints.
  - **Four-layer data isolation**: Added `DataIsolationService`, `DataScope`, and scoped query methods (`listScoped`) to `IntelligenceStore` for user-private, regional, industry, and paid/free entitlement filtering. Added `ConflictStore` for conflict resolution persistence and false-information ledger management. Added `GET /api/admin/governance/conflicts` and `GET /api/admin/governance/false-ledger` endpoints.
  - **Database**: Added `false_information_ledger`, `conflict_resolutions` tables and extended `intelligence_snapshots` with `retention_days`, `record_count`, `parent_snapshot_id`, `expires_at` columns in MySQL init, H2 test schema, and `AdminSchemaMigration`.
  - **Frontend**: Added Snapshot, Conflicts, and False Intel navigation entries to the `/admin` console plus corresponding `api-client.ts` types and fetch functions.
- Verification results:
  - Python conflict engine has 13 unit tests covering all 5 branches, batch detection, custom configs, and edge cases.
  - Java `GovernanceApiTest` covers snapshot lifecycle (generate/list/compare/cleanup), conflict resolution listing, false-ledger listing, and RBAC enforcement.
  - All existing tests in `services/collector`, `services/api`, and `apps/web` are expected to continue passing.
- Unfinished items:
  - Run Python, Maven, and npm test suites in environments with the required toolchains.
  - Browser-level verification of new admin navigation entries.

### Core Architecture Implementation Status Analysis

- Change type: documentation.
- Affected modules: `docs/en/milestones`, `docs/zh-CN/milestones`, and both change logs.
- Main changes:
  - Added bilingual implementation status analysis documents (`docs/en/milestones/implementation-status-analysis.md` and `docs/zh-CN/milestones/implementation-status-analysis-zh-CN.md`) that compare the full V4.0 target architecture against the current codebase.
  - Analyzed all nine architecture layers with per-capability implementation status (V1/V2/V3), identified five critical unclosed links, and summarized completion rates across nine domains.
  - Documented Admin V3 phased delivery status (V3-1 through V3-6) and recorded remaining environment verification gaps.
- Verification results:
  - Confirmed the English and Chinese documents describe the same findings, statuses, and gaps.
  - Cross-referenced against the five governing architecture documents, three milestone documents, and the actual source code under `apps/web`, `services/api`, `services/ai-worker`, `services/collector`, and `infra`.
- Unfinished items:
  - None. This is a documentation-only snapshot of the current implementation state.

### Admin V3 Chinese Documentation Repair

- Change type: documentation repair.
- Affected modules: `docs/zh-CN/admin-v3-ui-design-zh-CN.md` and both change logs.
- Main changes:
  - Re-saved the Admin V3 Chinese UI design document as UTF-8 with BOM so common Windows editors no longer mis-detect the file encoding and display garbled Chinese text.
  - Restored the corrupted `## 12. Current Admin-V3-3 Implementation Status` section in the Chinese document so it matches the paired English source again.
- Verification results:
  - Verified the repaired document now starts with a UTF-8 BOM and reads back as valid Chinese text.
  - Compared the repaired tail section against `docs/en/admin-v3-ui-design.md` to confirm the restored scope and implementation notes match.
- Unfinished items:
  - No runtime test suite was required for this documentation-only repair.
### Admin-V3-1 And Admin-V3-2 Implementation

- Change type: functional development.
- Affected modules: `services/api`, `infra/mysql`, `apps/web`, `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Added real Admin V3 database persistence with `admin_intelligence_reviews`, `admin_tickets`, and `admin_human_intelligence`, plus MySQL initialization and API startup auto-migration support.
  - Added `/api/admin/**` endpoints for dashboard metrics, alert handling, audit-log search, intelligence review verdicts, ticket transitions, and human-intelligence entry/review, with admin write actions recorded in `audit_logs`.
  - Added a standalone `/admin` Web console that connects to the real admin APIs and covers the Admin-V3-1/V3-2 workspace: control center, alerts, audit logs, intelligence reviews, tickets, and human intelligence.
  - Added backend `AdminV3ApiTest` coverage and Web source regression coverage for the admin API client, page, and styles.
- Verification results:
  - Attempted `mvn -Dtest=AdminV3ApiTest test` in `services/api`, but the current execution environment does not provide `mvn`.
  - Attempted `npm test` in `apps/web`, but the current execution environment does not provide `npm` or `node`.
  - Performed static source checks for the new admin API, schema, page, and styles after the toolchain commands were unavailable.
- Unfinished items:
  - Run the added backend and Web test suites in an environment with Maven and Node/npm available.
  - Browser-level visual verification of `/admin` remains pending.
### V3 Admin UI Design Documentation

- Change type: documentation.
- Affected modules: `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Added the V3 administrator backend UI design as paired English and Chinese documents.
  - Defined the admin information architecture, role boundaries, reusable Next.js workspace patterns, key page designs, core workflows, API needs, and acceptance criteria.
  - Covered full V3 operations scope including knowledge, collection, intelligence review, tickets, risk rules, users/members, orders, alerts, audit logs, compliance, and monthly reports.
- Verification results:
  - Documentation-only change; no service test suite was required.
  - Confirmed the English and Chinese admin UI design documents describe the same scope, workflows, acceptance criteria, and phased delivery plan.
- Unfinished items:
  - The UI design is not yet implemented in `apps/web`; API contracts and page-level tests still need to be added during development.

### Agent Requirement Clarification Rule

- Change type: documentation.
- Affected modules: `AGENTS.md`, `AGENTS-zh-CN.md`, and both change logs.
- Main changes:
  - Added an agent collaboration rule requiring one question at a time before final plans or implementation for functionality additions or changes.
  - Clarified that follow-up questions should continue until the agent has about 95% confidence in the user's real needs, goals, boundaries, and acceptance criteria.
- Verification results:
  - Confirmed the English and Chinese agent documents carry the same requirement.
- Unfinished items:
  - None.

## 2026-07-07

### Web Conversation Sidebar And Header Polish

- Change type: functional development.
- Affected modules: `apps/web` and both change logs.
- Main changes:
  - Reworked the conversation list row structure so archive and delete actions use explicit icon buttons instead of nested interactive spans inside the row button, which restores visible action icons and keeps the row markup valid.
  - Constrained the diagnosis workspace scroll boundaries so the shell-level right column stops scrolling in the diagnosis section, the conversation message stream scrolls independently, and the right-side report/intelligence column keeps its own overflow handling.
  - Compressed the top-right identity block into two lines and removed the visible "Current context" label from the sidebar and users panel.
- Verification results:
  - In `apps/web`, `npm test` passes with 31/31 tests green, including the new regressions for row action buttons, diagnosis-only scroll containment, compact identity metadata, and removing the current-context summary row.
- Unfinished items:
  - No visual browser pass was run in this change set, so the update is verified by source-based regression coverage only.

### Diagnosis SSE Multi-Frame Streaming Fix

- Change type: functional development.
- Affected modules: `apps/web`, `services/api`, and both change logs.
- Main changes:
  - Updated the API message streaming endpoint to emit multiple `diagnosis` SSE frames for one diagnosis request instead of returning a single terminal payload, so the browser can render visible progressive output.
  - Updated the Web diagnosis stream parser to read complete SSE frames and always use the latest `diagnosis` payload, which fixes the previous behavior where the first streamed `answer` frame could pin the UI and block later updates.
  - Added regression coverage for multi-frame SSE responses in `services/api` and for latest-frame stream parsing expectations in `apps/web`.
- Verification results:
  - In `services/api`, `mvn -Dtest=MessageStreamApiTest test` passes with 4/4 tests green, including the new multi-frame SSE regression.
  - In `apps/web`, `npm test -- envelope.test.mjs` passes with 27/27 tests green, including the new latest-frame stream parsing assertion.
- Unfinished items:
  - The backend currently streams progressive snapshots derived from the completed diagnosis payload, not token-by-token upstream model events.

### Web Streaming Diagnosis And Identity Layout Fixes

- Change type: functional development.
- Affected modules: `apps/web`, `docs/superpowers/plans/2026-07-07-web-streaming-layout-fixes.md`, and both change logs.
- Main changes:
  - Added incremental Web diagnosis rendering through a streamed SSE reader so the assistant reply can appear progressively before the history refresh finishes.
  - Merged signed-in identity details and logout controls into one top-right block, removed the ready badge, and switched the topbar status line to show the current workspace notice instead of duplicating profile metadata.
  - Updated the workspace shell and diagnosis pane to keep the rail and message column independently scrollable, and auto-follow the latest streamed reply so long conversations no longer leave the identity area feeling pinned over the session.
- Verification results:
  - In `apps/web`, `npm test` passes with 26/26 tests green, including new source assertions for incremental streaming, merged identity actions, and scroll-follow behavior.
  - In `apps/web`, `npm run build` passes, confirming the updated shell, stream reader, and diagnosis workspace compile in production mode.
- Unfinished items:
  - The backend still emits a final SSE diagnosis event rather than token-by-token model events, so perceived streaming now depends on incremental transport delivery of that event payload in the browser.

### Web Diagnosis Reply De-duplication

- Change type: functional development.
- Affected modules: `apps/web` and both change logs.
- Main changes:
  - Fixed the diagnosis conversation workspace so it no longer renders a second standalone assistant bubble after the same reply has already been loaded into `messageHistory`.
  - Kept the transient `diagnosis` state only as a pre-refresh fallback, which preserves the immediate reply experience while preventing the duplicate two-card rendering seen in the active session.
  - Updated the report-generation entry condition so the report action remains available when a conversation already contains assistant replies in history.
- Verification results:
  - In `apps/web`, `npm test` passes with a new regression assertion that locks the no-duplicate assistant-bubble behavior in `DiagnosisWorkspace`.
- Unfinished items:
  - A live browser verification is still recommended to confirm the active-session render now matches the post-refresh view during real diagnosis requests.

## 2026-07-06

### Web Conversation IA Refresh

- Change type: functional development.
- Affected modules: `apps/web` and both change logs.
- Main changes:
  - Reworked the Web home page into a persistent conversation-management shell with extracted `WorkspaceShell`, `ConversationSidebar`, `DiagnosisWorkspace`, and `ArchiveWorkspace` components.
  - Removed Web-visible `V2` and gray-release wording and deleted the metrics panel plus the page-level `fetchOpsMetrics` dependency, while keeping the existing top-level modules `Diagnosis`, `Intelligence`, `Users`, and `Archive`.
  - Added a pure conversation workspace helper plus a new regression test suite that locks section-based filtering, newest-first ordering, archive fallback selection, and post-archive next-selection behavior.
- Verification results:
  - In `apps/web`, `npm test` passes with the new conversation workspace regression coverage and refreshed source-level IA assertions.
  - In `apps/web`, `npm run build` passes, confirming the refactored page, new components, and conversation helper compile successfully in production mode.
- Unfinished items:
  - Live browser verification on the target deployment is still recommended to confirm the responsive sidebar shell and archive read-only experience against real data volumes.

### Web Workspace Viewport Height Fit

- Change type: functional development.
- Affected modules: `apps/web` and both change logs.
- Main changes:
  - Reworked the desktop workspace layout so the main content column now uses a fixed viewport-height shell with internal height distribution instead of relying on a chat card `min-height` derived from `calc(100vh - ...)`.
  - Updated the diagnosis grid, dialogue panel, and right-side ops stack to inherit constrained height from their parents and scroll internally when needed.
  - Added a Web regression test that asserts the workspace CSS keeps the diagnosis area within the first viewport at browser 100% zoom.
- Verification results:
  - In `apps/web`, `npm test` now covers the new viewport-fit layout regression assertions together with the existing source-level Web checks.
- Unfinished items:
  - A live browser verification on the target deployment is still recommended to confirm the 100% zoom experience matches the CSS regression intent across real viewport sizes.

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
  - Removed the matching "鏈€灏忋€佹竻鏅般€佸彲娴嬭瘯鐨勫疄鐜? wording from the Chinese root agent guidance and the Chinese agent development standards.
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
  - Reviewed `raw-docs/琛屼笟鏅鸿兘鍒涗笟Agent骞冲彴鍏ㄥ煙瀹屾暣鏋舵瀯璁捐鏂囨。锛圴4.0_鍏ㄥ煙灏侀《缁堢増锛夐厤濂楀垎闃舵钀藉湴寮€鍙戣鍒掕鏄庝功.pdf` through the extracted text in `raw-docs/txt/`.
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
## 2026-07-07

### Conversation Sidebar Archive/Delete Actions

- Change type: functional development.
- Affected modules: `apps/web`, `services/api`, and both change logs.
- Main changes:
  - Added API-backed conversation soft deletion with a new `POST /api/conversations/{id}/delete` endpoint that only deletes archived conversations and keeps deleted rows out of normal conversation listings.
  - Updated the Web conversation workspace helper so deleted conversations are excluded from active/archive partitions and the sidebar can describe section-specific titles plus row actions.
  - Added direct archive and delete controls to the Web conversation sidebar and wired page-level handlers to update list state, selection fallback, and notices after row actions.
- Verification results:
  - In `apps/web`, `npm test -- conversation-workspace.test.mjs` now covers deleted-conversation filtering and section-specific sidebar labels/actions.
  - In `services/api`, `mvn -Dtest=BusinessWorkflowApiTest#archivedConversationCanBeSoftDeletedAndDisappearsFromList test` verifies archived conversations can be soft-deleted and no longer appear in the conversation list.
- Unfinished items:
  - A live browser verification is still recommended to confirm the row-action affordances and archive/delete flow feel right with real user data and localized copy.

## 2026-07-08
### Admin V3 Chinese Documentation Repair

- Change type: documentation repair.
- Affected modules: `docs/zh-CN/admin-v3-ui-design-zh-CN.md` and both change logs.
- Main changes:
  - Re-saved the Admin V3 Chinese UI design document as UTF-8 with BOM so common Windows editors no longer mis-detect the file encoding and display garbled Chinese text.
  - Restored the corrupted `## 12. Current Admin-V3-3 Implementation Status` section in the Chinese document so it matches the paired English source again.
- Verification results:
  - Verified the repaired document now starts with a UTF-8 BOM and reads back as valid Chinese text.
  - Compared the repaired tail section against `docs/en/admin-v3-ui-design.md` to confirm the restored scope and implementation notes match.
- Unfinished items:
  - No runtime test suite was required for this documentation-only repair.

## 2026-07-09

### Core Code Files and External Interaction Documentation

- Change type: documentation update.
- Affected modules: `docs/en`, `docs/zh-CN`, and both change logs.
- Main changes:
  - Added a new bilingual architecture-reference document that maps each runtime module to its core code files, including Web workspace files, Admin V3 files, API controllers/services/stores, AI worker orchestration files, collector governance/resilience files, and infrastructure entry files.
  - Documented the actual external interaction boundaries for browser, API, AI worker, collector, Redis, MySQL, Qdrant, and OpenAI-compatible model providers, with explicit separation between internal calls and external dependencies.
  - Added practical end-to-end flow summaries for diagnosis SSE, learning/transition SSE, report export, admin collection/governance, knowledge publication sync, and collector deduplication/resilience.
- Verification results:
  - Verified the new document contents against `.codegraph/codegraph.db`, repository runtime entrypoints, and the existing component-interaction and architecture documents.
  - Confirmed the English and Chinese documents describe the same module scope, file responsibilities, and interaction flows.
- Unfinished items:
  - This is a documentation-only change, so no runtime test suite was executed.

### Alert Scheduler Collection Run Table Fix

- Change type: functional bug fix.
- Affected modules: `services/api` and both change logs.
- Main changes:
  - Updated `AlertRuleEngine` to read collector run telemetry from `admin_collection_job_runs` instead of the non-existent legacy table name `collection_job_runs`.
  - Aligned the collector failure-rate window filter with the actual schema by using `start_time` instead of the unsupported `run_time` column.
  - Restored scheduled alert evaluation compatibility with the Admin V3 collection telemetry schema already used by the rest of the API.
- Verification results:
  - Confirmed the failing scheduler stack trace points to `AlertRuleEngine.evaluateCollectorFailureRate`.
  - Verified `admin_collection_job_runs` is the table created in both MySQL and H2 baseline schemas and the table used by the admin collection stores.
  - Re-checked the API source tree after the fix to confirm the alert rule no longer references `collection_job_runs`.
- Unfinished items:
  - The scheduler should still be exercised against the target runtime database to confirm the alert cycle completes without SQL exceptions end to end.

### MySQL V2 Admin Migration Compatibility Fix

- Change type: functional bug fix.
- Affected modules: `services/api`, `infra/mysql`, and both change logs.
- Main changes:
  - Removed the two `ALTER TABLE admin_collection_sources ADD COLUMN IF NOT EXISTS ...` statements from the MySQL `V2__admin_schema_and_seed.sql` migration.
  - Kept the V2 seed data intact because `compliance_notes` and `proxy_config` already exist in the baseline MySQL schema bootstrap.
  - Prevented Flyway from failing on MySQL with SQL state `42000` / error code `1064` during V2 migration startup.
- Verification results:
  - Confirmed the failing statements were located in `services/api/src/main/resources/db/migration/mysql/V2__admin_schema_and_seed.sql`.
  - Confirmed `admin_collection_sources` already includes `compliance_notes` and `proxy_config` in `infra/mysql/init/001_v1_baseline.sql`, so removing the duplicate V2 column additions preserves schema completeness.
  - Performed static migration review to ensure the remaining V2 statements still execute in order after the duplicate column additions were removed.
- Unfinished items:
  - Flyway migration should still be re-run against the target MySQL environment to confirm startup succeeds end-to-end.

### Admin-V3-3 Knowledge Management Closed Loop

- Change type: functional development.
- Affected modules: `services/api`, `infra/mysql`, `apps/web`, admin design docs, and both change logs.
- Main changes:
  - Added formal knowledge-management persistence with `admin_knowledge_nodes`, `admin_knowledge_versions`, and `admin_knowledge_publications`, and wired the same schema into MySQL bootstrap, H2 test schema, and API startup auto-migration.
  - Added real `/api/admin/knowledge/**` endpoints for node listing, node detail, draft save, submit review, second-person approval, publish, rollback, and version diff.
  - Added knowledge publication synchronization back into `knowledge_items` so published admin knowledge is persisted for existing user-side retrieval flows.
  - Extended `/admin` with a dedicated Knowledge workspace covering node navigation, draft editing, version actions, diff viewing, rollback, and publication history.
  - Extended backend and frontend source-level regression coverage for the Admin-V3-3 lifecycle.
- Verification results:
  - Performed static code-path verification for schema definitions, controller routes, lifecycle validation rules, API client helpers, admin page wiring, and source-test coverage additions.
  - The current execution environment does not provide `mvn`, `node`, or `npm`, so Maven and Web test commands could not be executed here.
- Unfinished items:
  - Run backend tests and Web tests in an environment with Maven and Node.js installed.
  - A live browser verification of the `/admin` knowledge workspace is still recommended after the frontend toolchain is available.

### API MyBatis-Plus Migration Self-Check Closure

- Change type: functional bug fix and regression alignment.
- Affected modules: `services/api`, `services/api/src/test/resources`, `services/api/src/test/java`, and both change logs.
- Main changes:
  - Completed the remaining `JdbcTemplate` to MyBatis-Plus/MyBatis mapper migration in the API admin path and re-verified that `services/api/src/main/java/com/bizsage/api` no longer contains `JdbcTemplate` references.
  - Fixed H2 admin seed compatibility for explicit-ID bootstrap rows by resetting identity sequences for `admin_knowledge_nodes`, `admin_knowledge_versions`, and `admin_collection_sources`.
  - Added CLOB-to-string normalization in migrated admin stores so H2 test reads remain compatible with mapper-backed persistence for knowledge content and collection payload JSON.
  - Updated API regression tests to match the current paged response contracts for `/api/users`, `/api/conversations`, `/api/intelligence`, and `/api/knowledge`, and removed brittle assumptions about admin audit/review list ordering.
- Verification results:
  - Verified `rg -n "JdbcTemplate" services/api/src/main/java/com/bizsage/api` returns no matches after the migration cleanup.
  - Verified `mvn -q -DskipTests compile` passes in `services/api`.
  - Verified `mvn -q "-Dtest=AdminV3ApiTest,GovernanceApiTest,V2GrayReleaseApiTest,AuthAndRbacTest,BusinessWorkflowApiTest" test` passes in `services/api`.
- Unfinished items:
  - A broader backend regression sweep is still recommended if the team wants full-suite confidence beyond the targeted migration-related tests.

