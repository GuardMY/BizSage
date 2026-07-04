# BizSage Product Milestones

## Purpose

This document is the cross-version milestone roadmap for BizSage. Its iteration stages follow `raw-docs/行业智能创业Agent平台全域完整架构设计文档（V4.0_全域封顶终版）配套分阶段落地开发规划说明书.pdf`.

The active implementation milestone is V2. V1 automated verification is complete, and the remaining V1 environment gaps are tracked as V2 M0 remediation items. V3 remains a planned target until V2 gray-release exit criteria are met and approved.

## Governing Inputs

All future functional development must be checked against the following governing documents before implementation starts:

- `docs/en/product-strategy-and-design.md` / `docs/zh-CN/product-strategy-and-design-zh-CN.md`
- `docs/en/system-architecture-and-framework.md` / `docs/zh-CN/system-architecture-and-framework-zh-CN.md`
- `docs/en/development-implementation-guide.md` / `docs/zh-CN/development-implementation-guide-zh-CN.md`
- `docs/en/data-collection-and-intelligence-perception.md` / `docs/zh-CN/data-collection-and-intelligence-perception-zh-CN.md`
- `docs/en/risk-management-and-compliance.md` / `docs/zh-CN/risk-management-and-compliance-zh-CN.md`

The source stage model is:

1. V1: MVP minimum viable version, internal validation.
2. V2: Production high-availability engineering version, external gray release.
3. V3: Full-domain commercial final version, full production launch.

## Version Roadmap

| Version | Source-stage name | Theme | Primary outcome | Status |
| --- | --- | --- | --- | --- |
| V1 | MVP minimum viable version | Internal validation and business loop proof | Basic data collection, basic knowledge base, single diagnosis Agent conversation, simple Web console, and basic gateway | Completed with documented environment gaps |
| V2 | Production high-availability engineering version | External gray release and production hardening | Complete lower-level engineering capabilities, improve AI reasoning, complete permission system, support small-scale paid-user gray release | Active |
| V3 | Full-domain commercial final version | Full commercial launch and complete product loop | Complete upper-layer business, moat capabilities, monetization, full security, all-terminal adaptation, and 99.9% SLA readiness | Planned |

## Stage Coverage Matrix

| Governing area | V1 MVP | V2 production engineering | V3 commercial final |
| --- | --- | --- | --- |
| Operations foundation | Single-node Redis/cache, local/basic logs, simple scheduling, basic rate limiting and simple circuit breaking. | Four-layer Redis cache, elastic cluster scaling, full circuit breaking/degradation/dead-letter queue, disaster recovery, four isolated environments, monitoring dashboards, three-level alerts, SLA statistics. | Cost accounting, crawler security hardening, full SLA dashboard, standardized runbooks, incident playbooks, load-test assets, gray release with dual review and rollback. |
| Data collection and intelligence | Four basic source paths: user private business data, manual local intelligence, basic public-page crawler, and single-provider third-party API/mock provider. | Incremental crawling, anti-block controls, multi-vendor proxy rotation, third-party API redundancy/failover, API cache cost control, time-series fallback, collection telemetry. | Full crawler security rules, source cost governance, paid-intelligence protection, and operational reporting for intelligence production. |
| Data governance | Field normalization, article extraction, URL and SimHash dedupe, basic rumor keyword filtering, fixed source weights, basic storage locks. | Seven-layer old/new information conflict engine, daily/weekly/monthly snapshots, row-level optimistic locks, intelligent multi-source merge, basic four-layer data isolation. | Dynamic source-weight engine, full data lineage, automatic stale/dead-letter/test-data cleanup, hot/warm/cold storage lifecycle. |
| Knowledge and RAG | Static industry baseline knowledge, basic batch/manual entry, basic vector search, source-weight rerank, context truncation, no-evidence fallback. | Dynamic real-time intelligence knowledge base, independent time-series version store, AI knowledge refinement, lifecycle management, time-series/region/industry/member retrieval filters, six-dimension rerank, context compression, conflict marking. | Full report-grade knowledge assets, complete audit search, refined user profile tags, and commercial report/data delivery readiness. |
| AI and Agent | Basic diagnosis Agent flow: user question -> RAG -> model/mock -> sourced answer. | Multi-model routing, five-check reasoning self-test, layered prompt library, model degradation, full basic learning Agent, three-level memory, two-Agent jump flow, standardized sourced output. | Full dual-Agent capability, dynamic weighting in reasoning, all report types, commercial personalization, safety and compliance audit loop. |
| Commercial and permissions | Basic roles and simple operations console. Paid membership, billing, PDF reports, and full commercial workflows are out of scope. | PDF diagnosis report export, complete two-layer RBAC, free/paid intelligence isolation, user profile basics, user submission incentive basics, paid-user gray release. | Complete membership tiers, orders, entitlements, paid intelligence sub-store, full user incentive operations, member center, report download, and commercial operations automation. |
| Terminal and delivery | Web login, conversation, diagnosis, source modal, intelligence entry/list/review, user list. Android remains placeholder-only. | Standard PDF export, operations monitoring dashboard, intelligence review work orders. | Complete H5 mobile adaptation, weak-network fallback, member center, historical conversations, report download, audit-log console, monthly operations report export. |
| Risk and compliance | Unified API envelope, source/timeliness/confidence/disclaimer output, sensitive-data masking/encryption, no-evidence fallback, basic RBAC, documented verification gaps. | Full-link logs retained for at least 6 months, complete privacy masking, no plaintext privacy leakage, permission isolation, fault self-healing, gray release stability. | Classified protection readiness, complete audit logs, privacy encryption/masking, data isolation, full security controls, legal evidence support, all-terminal fallback. |

## V1 MVP Minimum Viable Version

Detailed plan: `docs/en/milestones/v1-mvp-milestones.md`.

### Scope

- Four source ingestion paths through mock or pluggable adapters.
- Data governance, field normalization, deduplication, fixed source weights, rumor filtering, and sensitive data protection.
- Static knowledge base, RAG retrieval, and diagnosis Agent with sourced output.
- Web login, conversation, diagnosis chat, source inspection, and simple ops console.
- Local single-node infrastructure, runbooks, and acceptance documentation.

### Exit Criteria

- Four source types can ingest normalized records.
- Diagnosis answers include sources, timeliness note, confidence cue, and disclaimer.
- No-evidence requests return an information-insufficient response.
- Sensitive phone and identity fields are encrypted or masked as designed.
- 50 concurrent API requests and 4-hour single-node observation are completed or documented with exact remediation tasks.

### Out Of Scope

- Android implementation beyond placeholder documentation.
- Paid membership, entitlement billing, and commercial order flows.
- PDF reports.
- Multi-model routing.
- Dynamic source weighting.
- High availability, disaster recovery, and full monitoring dashboards.

## V2 Production High-Availability Engineering Version

Detailed plan: `docs/en/milestones/v2-production-high-availability-milestones.md`.

### Scope

- Complete production engineering foundation: multi-level cache, elastic scaling, full circuit breaking/degradation/dead-letter queues, disaster recovery, environment isolation, monitoring, alerts, and SLA statistics.
- Add high-availability data collection: incremental crawler, proxy rotation, failure switching, multi-vendor third-party APIs, API cache, time-series fallback, and collection telemetry.
- Add advanced governance: old/new conflict engine, time-series snapshots, row-level optimistic locking, multi-source merge, and basic four-layer data isolation.
- Add dynamic intelligence knowledge base, time-series knowledge store, AI knowledge refinement, complete lifecycle management, and enhanced RAG filters/reranking.
- Add multi-model routing, five-check reasoning self-test, layered prompt library, degradation fallback, learning Agent basics, Agent memory, and two-Agent jump flow.
- Add PDF diagnosis report export, two-layer RBAC, free/paid intelligence isolation, user profile basics, submission incentive basics, operations dashboards, and review work orders.

### Entry Criteria

- V1 exit criteria are met, or all remaining V1 failures have approved remediation tasks.
- V1 API, database, deployment, acceptance, and milestone documents are current in both languages.
- Production-gray target scenarios, data boundaries, and rollback criteria are documented.

### Exit Criteria

- Single-component failure does not interrupt core business; crawler RTO is under 10 minutes and third-party API failure can switch provider.
- Database backup can restore service with data loss no greater than 6 hours.
- API cache hit rate is at least 70%, and incremental crawling reduces invalid requests by at least 60%.
- Full-link logs are retained for at least 6 months, privacy masking is complete, and no plaintext privacy leakage is found.
- Paid-user permission opening and dedicated diagnosis PDF reports work in gray release.
- Gray release runs for 7 days without major incidents.
- AI reasoning has conflict marking and no cross-region erroneous output in validation scenarios.

## V3 Full-Domain Commercial Final Version

Detailed plan: `docs/en/milestones/v3-full-domain-commercial-final-milestones.md`.

### Scope

- Implement dynamic source weights, full data lineage, automatic stale/dead-letter/test-data cleanup, and full hot/warm/cold storage lifecycle.
- Complete four-layer multi-tenant physical isolation, paid intelligence sub-store, full member entitlement system, orders, plans, and customer operations.
- Add all five report types: learning, market research, diagnosis, special risk, and periodic review.
- Complete refined user profile tags, personalized operations, full user-submission incentives, and automated commercial operations.
- Complete global gateway and security: anti-scraping, paid-content anti-export, vertical/horizontal authorization checks, input/output risk audit, and administrator audit trail.
- Complete H5 mobile adaptation, weak-network degradation, member center, historical conversations, report download, audit-log console, monthly operations report export, standardized delivery assets, and 99.9% SLA readiness.

### Entry Criteria

- V2 gray release exit criteria are met.
- Commercial pricing, membership tiers, entitlement model, compliance assumptions, and delivery assets are approved.
- Security, reliability, data-isolation, and cost targets are measurable.

### Exit Criteria

- Availability SLA is at least 99.9%, with annual downtime no greater than 8.76 hours.
- Commercial loop is complete: paid membership, entitlement isolation, report delivery, and user-submission incentives are automated.
- Compliance readiness covers full-link audit logs, privacy encryption/masking, data isolation, and security controls for classified protection review.
- Data governance supports automatic dynamic-weight updates, full lineage tracing, and automatic garbage-data cleanup without storage bloat.
- PC and H5 mobile terminals are fully usable, with friendly fallback for weak network and interface failures.
- Delivery assets are complete: architecture docs, deployment scripts, operations manual, incident playbooks, load-test report, and API docs.

## Current Alignment Findings

- The previous roadmap split out an extra V4 scale/ecosystem stage. That does not match the source PDF, which defines exactly three iterations: V1, V2, and V3.
- The corrected roadmap now maps high availability, disaster recovery, multi-model routing, full RBAC, and basic paid-user gray release to V2.
- The corrected roadmap maps dynamic source weighting, full data lineage, complete commercial membership, full security, H5 mobile adaptation, audit console, operations reports, and 99.9% SLA to V3.
- The current detailed V1 milestone is still aligned with the PDF's V1/P0 internal-validation stage.
- Detailed V2 and V3 milestone documents now exist in both English and Chinese.
- The five governing documents now live under `docs/en/` and `docs/zh-CN/` with matching language counterparts.

## Cross-Version Rules

- Each version must have its own detailed milestone document before implementation starts.
- Later-version functionality must not be implemented during V1 unless the V1 milestone document is updated and the scope change is accepted.
- Documentation must be maintained in English and Chinese in the same change.
- Functional changes must update `CHANGELOG.md` and `CHANGELOG-zh-CN.md`.
- Milestone progress changes must update the corresponding milestone documents.
- Web, API, workers, collectors, infrastructure, and Android modules must remain directory-isolated and communicate through documented interfaces.

## Current Status

- Active version: V1.
- Current detailed milestone document: `docs/en/milestones/v1-mvp-milestones.md`.
- Next planned action: finish V1 acceptance, then approve V2 entry criteria before V2 implementation.
