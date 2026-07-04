# BizSage Full-System Development Implementation Guide

This document is the English counterpart of `development-implementation-guide-zh-CN.md`. It defines the mandatory phased implementation plan, module development rules, APIs, database, frontend, operations, security, testing, and launch criteria for BizSage.

## 1. Phased Delivery Plan

BizSage delivery follows three implementation iterations from the source phased development plan:

| Version | Stage | Core value | Launch gate |
|---------|-------|------------|-------------|
| V1 | MVP minimum viable version | Internal validation and end-to-end business loop | Basic data collection, baseline knowledge base, single basic Agent conversation, simple frontend, basic gateway |
| V2 | Production high-availability engineering version | External gray release and production hardening | Monitoring, recovery, circuit breaking, cache, RBAC, compliant desensitization, and small paid-user gray release |
| V3 | Full-domain commercial final version | Full commercial launch | Complete monetization, security, multi-terminal delivery, auditability, and 99.9% SLA readiness |

All implementation tasks must be classified as P0, P1, or P2:

- `P0`: mandatory launch gate.
- `P1`: important business capability.
- `P2`: optimization or deferred enhancement.

Development must not implement later-stage functionality inside the active stage unless the bilingual milestone documents are updated and the scope change is approved.

## 2. Nine-Layer Architecture Priority Matrix

### 2.1 Operations Foundation

V1 includes single-node Redis cache, basic logs, single-node scheduling, simple API rate limiting, and simple circuit breaking. V2 adds multi-level Redis cache, distributed scaling, complete circuit/degradation/dead-letter handling, disaster recovery, environment isolation, monitoring dashboards, tiered alerts, SLA statistics, and automated operations reports. V3 adds full resource cost accounting, crawler security hardening, monthly SLA reports, DR manuals, stress-test reports, and controlled gray release.

### 2.2 Multi-Source Data Collection

V1 includes guided user data collection, Excel import, basic AES storage, basic human-intelligence forms, basic crawler, basic Bloom deduplication, one proxy vendor, and one API vendor. V2 adds incremental crawler detection, anti-crawler hardening, multi-vendor proxy rotation, API redundancy, API cache, snapshot fallback, and full collection logs. V3 completes cost controls, wider vendor coverage, mature local-intelligence operations, and full paid-intelligence workflows.

### 2.3 Global Data Governance

V1 includes field normalization, text extraction, URL and SimHash deduplication, basic rumor keyword filtering, fixed source weights, and simple database locks. V2 adds full seven-layer old/new conflict comparison, time-series snapshots, row-level optimistic locks, multi-source merge, and basic four-layer tenant isolation. V3 adds real-time dynamic source weighting, complete data lineage, automated junk-data cleanup, and full hot/warm/cold storage policy.

### 2.4 Intelligent Knowledge Middle Platform

V1 includes a static industry baseline knowledge base, structured MySQL storage, vector batch storage, and basic deduplication. V2 adds a dynamic real-time intelligence knowledge base, time-series version knowledge storage, AI knowledge refinement, tagging, merge, and lifecycle management. V3 completes full knowledge operation, auditability, and commercial-grade governance.

### 2.5 Multi-Level RAG Retrieval

V1 supports keyword retrieval, vector semantic retrieval, simple source-weight reranking, basic context truncation, and an information-insufficient response when evidence is missing. V2 adds time-series retrieval, region filtering, industry filtering, member-permission filtering, multi-dimensional reranking, context compression, and conflict marking. V3 optimizes full commercial retrieval quality and entitlement-aware data access.

### 2.6 Large Model Reasoning Service

V1 uses one fixed model, basic common prompts, and simple timeout fallback. V2 adds multi-model routing, five-check reasoning self-test, layered prompt library, cache fallback, standard answers, and rate-limit queueing. V3 completes commercial-grade model operations, cost control, traceability, and quality management.

### 2.7 Dual-Agent Business Core

V1 implements the basic operating-diagnosis Agent chain: user question -> RAG retrieval -> reasoning output, with fixed-weight rules, short-term memory, evidence, and disclaimer. V2 adds the industry-learning Agent, three-level memory, one-click cross-Agent transitions, standardized output templates, and paid/free intelligence isolation. V3 completes both Agents, profile-driven personalization, report integration, and commercial workflows.

### 2.8 Commercial Business Service

V1 includes basic session management, simple RBAC roles, and basic intent recognition. V2 adds PDF diagnosis reports, dual-layer RBAC, paid/free intelligence isolation, user profile tags, and submission incentive flow. V3 adds five standard report types, full membership/orders/entitlements, commercial delivery records, advanced profile management, and full monetization workflows.

### 2.9 Terminal Output

V1 includes a simple web console and basic conversation UI. V2 adds operations dashboards, review work orders, report export, and alert views. V3 adds H5 mobile adaptation, audit console, full admin operations, monthly operations reports, and complete terminal delivery.

## 3. Module Development Standards

The system is organized around the following module families: product shell, industry-learning Agent, operating-diagnosis Agent, server-side intelligence perception engine, information blind-spot completion, false-information blocking, old/new conflict handling, database schema, unified external API, frontend interaction and standard phrasing, admin system, operations/security/backup/alerting, milestone priority and scheduling, testing, and launch compliance.

Each module must define:

- Business goal and non-goals.
- Data inputs and outputs.
- Storage tables and indexes where applicable.
- API contracts.
- Permission and audit requirements.
- Acceptance criteria and verification commands.
- Deferred V2/V3 items if outside the active milestone.

## 4. Database Design

The database must cover at least the core domains of users, sessions, industries, chain nodes, knowledge entries, intelligence raw data, intelligence judgments, reports, audit logs, alerts, permissions, data sources, crawler tasks, review tickets, and commercial entitlements.

Design requirements:

- Structured business data belongs in MySQL.
- Vector semantic data belongs in the vector engine.
- Time-series snapshots and cold historical intelligence must be stored separately from hot operational tables.
- User private operating data must be encrypted and isolated.
- Knowledge updates must use row-level optimistic locks and version fields.
- Indexes must support industry, region, chain node, time, severity, source, and permission filtering.

## 5. API Specification

All backend APIs must return the unified envelope:

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "trace-id"
}
```

The web app may call only `services/api`; it must not call workers, collectors, databases, or queues directly.

API categories include:

- Auth and user/session APIs.
- Industry and knowledge-base APIs.
- Intelligence query, chain-node intelligence, major-event pinning, semantic retrieval, and diagnosis-linked intelligence APIs.
- Operating-diagnosis Agent APIs.
- Industry-learning Agent APIs.
- Report generation and export APIs.
- Admin APIs for data sources, keywords, review, tickets, alerts, users, permissions, and audit logs.

Errors must use a stable error-code system. Permission failures, parameter errors, resource-not-found, rate-limit, AI timeout, AI self-check failure, external service failure, database error, and risk-control blocking must be distinguishable.

## 6. Frontend and Interaction

Frontend must present the usable product directly, not a marketing landing page. The core experience is a dual-Agent interface with industry selection, learning mode, diagnosis mode, structured outputs, evidence/source display, confidence and timeliness labels, disclaimer text, report export, and cross-Agent transitions.

Admin UI must include data-source management, crawler scheduling, intelligence review, ticket management, alert center, user/member management, permission management, audit logs, compliance configuration, and operations dashboards.

AI output must always include evidence, timeliness, confidence cues, and a disclaimer. If evidence is missing, return an information-insufficient response instead of fabricating conclusions.

## 7. Operations and Security

Operations architecture includes Redis cache, distributed scheduling, ELK-style logging, Prometheus/Grafana-style monitoring, backup and recovery, physical environment isolation, Docker/K8s where appropriate, and release rollback.

Security rules:

- User-sensitive data must be AES-encrypted and never persisted or logged in plaintext.
- All API traffic must pass through gateway authentication and rate limiting.
- RBAC must enforce function permission and data permission.
- Four-layer multi-tenant data isolation must be enforced for user, region, industry, and entitlement.
- Logs must be retained for audit and traceability.
- Backup recovery, penetration testing, and release security checks are required before production launch.

## 8. Testing and Acceptance

Testing covers function, compliance, performance, and risk control:

- Dual-Agent conversations must complete without broken flows.
- Intelligence collection, cleaning, NLP parsing, judgment, and synchronization must run end to end.
- Knowledge-base CRUD, review, and batch inspection must work.
- Reports must generate and export without corrupted text.
- User data collection, encryption, desensitization, display masking, and lifecycle deletion must work.
- Admin module CRUD and permission isolation must be correct.
- Crawler compliance, AI output compliance, user authorization, and gray-content handling must pass review.

Stage acceptance:

| Dimension | V1 | V2 | V3 |
|-----------|----|----|----|
| Data chain | Four source types can be collected, cleaned, and stored | Incremental crawling reduces invalid requests and API cache is effective | Dynamic weights and lineage are complete |
| AI chain | Diagnoses can be generated from knowledge and intelligence with sources | Hallucination is reduced and conflicts are marked | Five-check self-test is complete and commercial quality is reached |
| Stability | Single-node 24h stability and basic concurrency | Component failure does not interrupt business, RTO target is defined | SLA >= 99.9% |
| Compliance | Sensitive data is desensitized and plaintext storage is forbidden | Logs retained >= 6 months and privacy process is complete | Security compliance audit target is met |
| Delivery | API, database, frontend, and deployment docs | PDF reports, dashboards, and review tickets | Full architecture docs, operations manuals, incident plans, stress-test reports |

## 9. Launch Compliance

Before launch, the project must complete software copyright registration or applicable filing, publish privacy/service/disclaimer documents, complete legal review of knowledge base, crawler rules, and AI templates, complete server/domain filing where applicable, validate backup and failover, and archive compliance training records.

## 10. Verification Commands

Use the repository verification rules:

- API: `mvn test` in `services/api`.
- Collector: `python -m pytest` in `services/collector`.
- AI worker: `python -m pytest` in `services/ai-worker`.
- Web: `npm test` and `npm run build` in `apps/web`.

If Docker, load testing, or long-running stability verification cannot run, record it as not executed with reason, attempted command, and remediation steps.

## Source Documents

This document corresponds to the Chinese merged governing document and consolidates the phased V4.0 implementation plan and full-system final development design materials preserved under `raw-docs/`.
