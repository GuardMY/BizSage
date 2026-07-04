# Change Log

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
