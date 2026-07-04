# BizSage V2 Production High-Availability Milestones

## V2 Scope

V2 is the production high-availability engineering release. It turns the V1 internal-validation MVP into a gray-release system that can support a small paid-user cohort with monitored stability, recoverability, permission isolation, PDF diagnosis reports, and stronger AI reasoning controls.

V2 is governed by the documents under `docs/en/` and their Chinese counterparts under `docs/zh-CN/`. It implements the V2/P0 subset of the cross-version roadmap in `docs/en/milestones/product-milestones.md`.

The system must prove:

- core services tolerate single-component failures without interrupting the core diagnosis path;
- crawler, API, cache, queue, and AI failures have explicit degradation or failover paths;
- production-gray permissions isolate free and paid intelligence;
- reasoning output passes conflict, timeliness, region, logic, and compliance checks;
- operators can monitor, review, and recover the system during gray release.

## Out Of Scope

- Full commercial membership, order, invoice, and customer-success automation.
- Complete dynamic source-weight engine; V2 may prepare metrics but V3 owns full automation.
- Full data lineage across every record; V2 records enough lineage for review and rollback.
- H5 full mobile experience and member center.
- Five complete report types; V2 delivers the operating-diagnosis PDF only.
- 99.9% commercial SLA commitment; V2 records gray-release stability evidence.

## Module Map

| Module | V2 Responsibility | Inputs | Outputs | Dependencies | V2 Deliverables | Acceptance |
| --- | --- | --- | --- | --- | --- | --- |
| `infra` | Production-gray runtime foundation | Environment profiles, service metrics | Isolated environments, backups, alerts | Docker/K8s-ready scripts, MySQL, Redis, Qdrant | Multi-level cache, backup/restore scripts, monitoring config, alert rules | Recovery drill and alert validation recorded |
| `services/api` | High-availability API gateway | Web requests, worker data, entitlement state | Unified responses, PDF jobs, audit logs | MySQL, Redis, workers | Rate limiting, two-layer RBAC, data permission checks, request tracing | Auth, permission, and envelope tests pass |
| `services/collector` | Resilient data collection | Source configs, crawler/API jobs | Normalized records, collection telemetry | Redis queues, proxy pool, API vendors | Incremental crawling, retry, circuit breaking, dead-letter queues, API failover | Invalid requests drop by target and failure paths are testable |
| `services/ai-worker` | Production-grade RAG and reasoning | Query, evidence, user profile, conflict labels | Sourced answer, self-check status, fallback answer | Vector store, model providers, API | Multi-model routing, five-check self-test, prompt library, degradation | No-evidence and conflict cases return controlled output |
| `apps/web` | Gray-release product and ops console | API data, streams, report links | User diagnosis, reports, ops actions | `services/api` | PDF export entry, paid/free state display, dashboards, review tickets | User and operator gray-release flows complete |
| `docs` | Operational delivery knowledge | Implemented behavior, verification results | Runbooks, milestone records, acceptance notes | All modules | Updated API, DB, deployment, acceptance, and verification docs | New operator can run gray-release checklist |

## Milestone Schedule

### M0 V1 Exit Review And Gray Scope Lock

- Review V1 acceptance results.
- Record any approved V1 remediation tasks.
- Lock V2 gray-release user scope, paid/free data boundary, rollback criteria, and support window.

Acceptance: V2 starts only when V1 exit gaps are documented and gray-release boundaries are approved.

### M1 Operations Foundation

- Add multi-level Redis cache strategy for crawler pages, API responses, global knowledge, and dimension-local intelligence.
- Add environment separation for development, test, staging, and production-gray.
- Add backup/restore scripts for MySQL and vector data.
- Add monitoring metrics for API, collector, AI worker, cache, queue, database, and business flows.
- Add three-level alert rules and response owners.

Acceptance: backup restore is rehearsed, dashboards show core metrics, and alerts can be triggered in a controlled test.

### M2 Resilient Collection

- Add incremental crawler fingerprinting with page MD5 and body SimHash.
- Add tiered retry, exponential backoff, domain circuit breaking, and dead-letter classification.
- Add proxy-vendor rotation and IP cooldown.
- Add third-party API multi-vendor failover, response cache, and recent snapshot fallback.
- Add collection telemetry for success rate, API volume, cache hit rate, and source health.

Acceptance: crawler failure, API vendor failure, and queue backlog all enter defined fallback paths.

### M3 Data Governance And Knowledge

- Add old/new information conflict engine with short-term fluctuation, regional exception, permanent authoritative update, suspicious conflict, and false-information branches.
- Add daily, weekly, and monthly time-series snapshots.
- Add row-level optimistic locking and multi-source merge for knowledge updates.
- Add basic four-layer data isolation for user-private, regional, industry, and paid/free data.
- Add dynamic real-time intelligence knowledge base and lifecycle metadata.

Acceptance: conflicting intelligence is marked or routed to review, snapshots are queryable, and data permission filters prevent cross-boundary reads.

### M4 RAG And AI Reasoning Controls

- Add time-series, region, industry, and entitlement filters to retrieval.
- Add six-dimension reranking: authority, timeliness, region, industry, review confidence, and historical quality.
- Add context compression for long intelligence.
- Add multi-model routing and degradation fallback.
- Add five-check self-test for fact, timeliness, region, logic, and compliance.
- Add layered prompt library for role, industry, region, compliance, format, and source/disclaimer output.

Acceptance: validation scenarios demonstrate no unsupported conclusion, conflict marking, region filtering, and fallback behavior.

### M5 Learning Agent And Dual-Agent Linkage

- Add basic industry-learning Agent flow.
- Add short-term conversation memory, basic long-term user profile memory, and automatic 90-day forgetting rule.
- Add one-click transition between diagnosis and learning contexts.
- Standardize dual-Agent output format with evidence, timeliness, confidence, and disclaimer.

Acceptance: users can move from diagnosis to targeted learning and from learning to diagnosis without losing industry context.

### M6 Permissions, Paid Gray Release, And Reports

- Add two-layer RBAC: function permission and data permission.
- Isolate free and paid intelligence.
- Add user profile basics: industry, region, membership level, and consultation preferences.
- Add user submission incentive basics with review-gated activation.
- Add operating-diagnosis PDF export with sources and disclaimers.

Acceptance: paid users can access gray-release paid intelligence and PDF reports; free users cannot read paid-only records.

### M7 Operations Console And Review Work Orders

- Add monitoring dashboards for collection, API, AI, cache, queue, and business metrics.
- Add intelligence review work orders for suspicious information and conflicts.
- Add alert center and handling status.
- Add audit-log search for core operations.

Acceptance: operators can find a failed collection task, review a suspicious intelligence item, and trace the related logs.

### M8 Gray Release Verification

- Run API, collector, AI worker, and Web test commands.
- Run backup recovery drill and document RTO/RPO.
- Run targeted failure drills for crawler, API vendor, model provider, and database.
- Run privacy masking and permission-isolation checks.
- Record 7-day gray-release stability, incidents, and remediation tasks.

Acceptance: V2 exits only when verification is complete or each failure has an approved remediation task and owner.

## Acceptance Checklist

- [ ] V1 exit gaps are reviewed and documented.
- [ ] Multi-level cache is configured and measured.
- [ ] Incremental crawling reduces invalid requests by at least 60% in validation data.
- [ ] API cache hit rate reaches at least 70% for cacheable API scenarios.
- [ ] Crawler RTO is under 10 minutes in drill conditions.
- [ ] Database restore shows data loss no greater than 6 hours.
- [ ] Full-link logs are retained for at least 6 months.
- [ ] No plaintext sensitive user data is found in designed storage or logs.
- [ ] Free and paid intelligence are isolated by permission checks.
- [ ] Diagnosis PDF exports include sources, timeliness, confidence, and disclaimer.
- [ ] AI self-check marks conflicts and blocks unsupported conclusions.
- [ ] Gray release runs for 7 days without major incident, or incidents have approved remediation.

## Verification Commands

- API: run `mvn test` in `services/api`.
- Collector: run `python -m pytest` in `services/collector`.
- AI worker: run `python -m pytest` in `services/ai-worker`.
- Web: run `npm test` and `npm run build` in `apps/web`.
- Documentation: run the bilingual Markdown pair check after moving or editing docs.

If Docker, load testing, recovery drill, or 7-day stability verification cannot run in the current environment, record the attempted command, reason, and remediation steps in the V2 verification document.

## Current Status

- Status: active.
- M0 decision: approved for internal operators plus seed paid users.
- Paid/free boundary: paid intelligence uses independent `paid_intelligence` storage and API filtering; V3 billing, invoices, and member center remain out of scope.
- Completed items: V2 gray-release skeleton now includes seed paid login profile fields, paid intelligence API isolation, diagnosis report export metadata, operations metrics/review/audit APIs, collector retry/circuit/dead-letter helpers, AI retrieval entitlement filters, and reasoning self-check status.
- Blockers: Docker startup, 50-concurrent load test, 4-hour V1 stability observation, backup restore drill, failure drills, and 7-day gray stability still require a Docker-capable or long-running environment.
- Verification results: record current command results in `docs/en/milestones/v2-verification-results.md`.
- Next steps: replace in-memory V2 stores with persistent repositories, run recovery drills on Docker-capable infrastructure, and continue M1-M8 hardening.
