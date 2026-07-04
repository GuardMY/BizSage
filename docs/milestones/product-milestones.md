# BizSage Product Milestones

## Purpose

This document is the cross-version milestone roadmap for BizSage. It defines the planned product evolution beyond the current V1 MVP while keeping implementation scope controlled by the active version milestone document.

The active implementation milestone is V1. Later versions are planning targets only until their own detailed milestone documents are created and approved.

## Version Roadmap

| Version | Theme | Target Users | Primary Outcome | Status |
| --- | --- | --- | --- | --- |
| V1 | Internal validation MVP | Internal users and operators | Prove the data-to-diagnosis loop | Active |
| V2 | Closed beta and operations hardening | Pilot customers and operators | Make diagnosis workflows usable in controlled customer scenarios | Planned |
| V3 | Commercial readiness | Paying business users, operators, and admins | Add monetization, reporting, Android, and customer operations | Planned |
| V4 | Scale and ecosystem | Larger customer groups and integration partners | Improve scale, resilience, governance, and external integrations | Planned |

## V1 Internal Validation MVP

Detailed plan: `docs/milestones/v1-mvp-milestones.md`.

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

## V2 Closed Beta And Operations Hardening

Detailed V2 milestone document: not created yet.

### Scope

- Convert V1 validation flows into closed-beta workflows for selected customer scenarios.
- Expand operator review, approval, rejection, and correction workflows.
- Improve source quality controls, manual audit trails, and evidence lifecycle management.
- Add configurable industry and region baselines without dynamic model routing.
- Add basic usage analytics for product and operations review.
- Improve deployment scripts, backup routines, and failure recovery playbooks for a single production-like environment.

### Entry Criteria

- V1 exit criteria are met or all remaining failures have approved remediation tasks.
- V1 API, database, deployment, and acceptance documents are current in both languages.
- Pilot customer scenarios and data boundaries are documented.

### Exit Criteria

- Pilot users can complete diagnosis and review workflows with operator support.
- Operators can trace accepted and rejected intelligence changes.
- Beta usage, failure, and feedback data can be exported for review.
- Deployment and backup runbooks support repeatable closed-beta operation.

## V3 Commercial Readiness

Detailed V3 milestone document: not created yet.

### Scope

- Introduce paid membership, entitlement checks, customer plans, and billing integration boundaries.
- Add PDF or shareable report generation after diagnosis quality is validated.
- Implement Android application workflows if V2 closed-beta evidence confirms mobile demand.
- Add customer-facing account management, organization management, and support workflows.
- Add stronger compliance controls, retention policies, and customer data export flows.
- Expand observability from operational logs into product, service, and model quality dashboards.

### Entry Criteria

- V2 closed-beta acceptance is complete.
- Commercial pricing, entitlement model, and compliance assumptions are approved.
- Mobile scope is validated with real user demand instead of placeholder assumptions.

### Exit Criteria

- Paying users can access entitled diagnosis capabilities.
- Operators and admins can manage customers, plans, and support workflows.
- Reports are generated with source traceability and privacy controls.
- Android scope is delivered or explicitly deferred with approved rationale.

## V4 Scale And Ecosystem

Detailed V4 milestone document: not created yet.

### Scope

- Add high availability, disaster recovery, autoscaling, and production-grade monitoring.
- Introduce multi-model routing only after measurable quality, cost, or latency needs justify it.
- Improve dynamic source weighting and feedback-driven ranking.
- Add third-party integrations, partner APIs, and controlled data exchange.
- Support larger customer groups, multi-tenant isolation improvements, and governance automation.

### Entry Criteria

- V3 commercial workflows are stable.
- Production usage shows clear scale, integration, or resilience requirements.
- Reliability, security, and cost targets are measurable.

### Exit Criteria

- Production runtime meets agreed availability and recovery targets.
- External integrations operate through documented and governed APIs.
- Model/source ranking changes are measurable, auditable, and reversible.
- Platform operations can scale without relying on manual intervention for routine incidents.

## Cross-Version Rules

- Each version must have its own detailed milestone document before implementation starts.
- Later-version functionality must not be implemented during V1 unless the V1 milestone document is updated and the scope change is accepted.
- Documentation must be maintained in English and Chinese in the same change.
- Functional changes must update `CHANGELOG.md` and `CHANGELOG-zh-CN.md`.
- Milestone progress changes must update the corresponding milestone documents.
- Web, API, workers, collectors, infrastructure, and Android modules must remain directory-isolated and communicate through documented interfaces.

## Current Status

- Active version: V1.
- Active detailed milestone document: `docs/milestones/v1-mvp-milestones.md`.
- Next planned action: finish V1 acceptance and document any remaining gaps in the V1 verification results.
