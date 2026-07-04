# BizSage V3 Full-Domain Commercial Final Milestones

## V3 Scope

V3 is the full-domain commercial final release. It turns the V2 production-gray system into a full commercial product with complete monetization, dynamic data governance, full security controls, multi-terminal delivery, auditability, operational reporting, and 99.9% SLA readiness.

V3 is governed by the documents under `docs/en/` and their Chinese counterparts under `docs/zh-CN/`. It implements the V3 subset of the cross-version roadmap in `docs/en/milestones/product-milestones.md`.

The system must prove:

- the commercial loop from membership purchase to entitlement, report delivery, and operations is complete;
- dynamic source weighting, data lineage, cleanup, and hot/warm/cold lifecycle are operational;
- paid intelligence and tenant data are physically and logically isolated;
- PC and H5 terminals are usable with graceful degradation;
- security, compliance, audit, and operations assets are ready for commercial launch.

## Out Of Scope

- A separate V4 stage; the source roadmap ends at V3.
- Unapproved ecosystem marketplace or third-party plugin platform.
- Implementing unrelated features outside the approved commercial launch scope.
- Claiming 99.9% production SLA without measured evidence and operations assets.

## Module Map

| Module | V3 Responsibility | Inputs | Outputs | Dependencies | V3 Deliverables | Acceptance |
| --- | --- | --- | --- | --- | --- | --- |
| `infra` | Commercial reliability and operations | Production metrics, cost data, incidents | SLA dashboard, cost reports, runbooks | Monitoring, backup, deployment pipeline | 99.9% readiness evidence, incident playbooks, load-test report | SLA and recovery targets are measurable |
| `services/api` | Commercial gateway and entitlement core | Orders, plans, users, roles, entitlements | Access decisions, member data, reports | Payment/order service design, MySQL, Redis | Membership, orders, entitlements, audit, anti-export controls | Commercial access paths are isolated and auditable |
| `services/collector` | Full intelligence production operations | Source costs, collection telemetry, review outcomes | Dynamic source metrics, intelligence reports | Governance engine, source registry | Source cost governance, paid-intelligence protection, intelligence production dashboards | Low-quality or costly sources can be governed |
| `services/ai-worker` | Full dual-Agent commercial reasoning | User profile, dynamic weights, evidence, entitlement | Personalized answers and reports | RAG, model routing, prompt library | Dynamic weighting in reasoning, full report generation support | Reports and answers remain sourced and compliant |
| `apps/web` | PC and H5 commercial product | User membership, reports, history, alerts | Member center, report downloads, mobile UI | API and assets | H5 adaptation, weak-network fallback, member center, history | PC and mobile workflows are usable |
| `docs` | Commercial delivery assets | Verification, architecture, operations, incidents | Delivery package | All modules | Architecture docs, deployment scripts, ops manual, incident plans, API docs | Commercial handoff package is complete |

## Milestone Schedule

### M0 V2 Gray Exit And Commercial Scope Lock

- Review V2 gray-release results and incident records.
- Approve pricing, membership tiers, entitlement model, report portfolio, compliance assumptions, and launch checklist.
- Lock V3 security, reliability, data-isolation, cost, and delivery targets.

Acceptance: V3 starts only after V2 exit criteria and commercial assumptions are approved.

### M1 Dynamic Data Governance

- Implement real-time dynamic source weighting from review pass rate, conflict frequency, freshness, baseline consistency, and compliance level.
- Implement full data lineage from collection through cleaning, governance, storage, retrieval, reasoning, and report output.
- Implement stale, dead-letter, redundant snapshot, and test-dirty-data cleanup.
- Implement hot/warm/cold storage migration policies.

Acceptance: each commercial intelligence item can be traced backward, weighted dynamically, and lifecycle-managed.

### M2 Full Multi-Tenant And Paid Intelligence Isolation

- Complete four-layer multi-tenant physical isolation for user-private, region, industry, and entitlement data.
- Add paid-intelligence sub-store and export restrictions.
- Add vertical and horizontal authorization checks for all commercial APIs.
- Add administrator audit trail for sensitive data access.

Acceptance: permission tests prove cross-tenant, cross-region, cross-industry, and free-to-paid access is blocked.

### M3 Membership, Orders, Entitlements, And Commercial Operations

- Add membership tiers, plans, order records, entitlement activation, expiration, and downgrade behavior.
- Add paid report download and entitlement verification.
- Add customer operations fields for member lifecycle and retention.
- Complete user submission incentive operations with review, reward, abuse control, and audit.

Acceptance: purchase-to-entitlement, entitlement-to-content, expiration, and downgrade flows are testable and logged.

### M4 Full Report Portfolio

- Add five report types: learning report, market research report, operating diagnosis report, special risk report, and periodic review report.
- Standardize report sections, evidence, confidence, timeliness, disclaimer, and export format.
- Add report history, download, and permission checks.

Acceptance: every report type can be generated with evidence and downloaded only by authorized users.

### M5 Full Dual-Agent Personalization

- Complete refined user profile tags for industry, region, scale, membership, preferences, pain points, and history.
- Use dynamic source weights in reasoning.
- Add personalized recommendations and follow-up learning/diagnosis actions.
- Complete safety and compliance audit loop for all Agent outputs.

Acceptance: commercial scenarios produce personalized, sourced, region-aware, and compliant outputs.

### M6 Security, Compliance, And Audit Console

- Add anti-scraping and paid-content anti-export controls.
- Add input and output risk audit.
- Add complete administrator operation audit console.
- Prepare classified-protection readiness materials where applicable.
- Add legal-evidence export for audit logs and content lineage.

Acceptance: security review covers sensitive APIs, paid content, admin actions, audit logs, and incident evidence.

### M7 Terminal Delivery And Operations Reports

- Add complete H5 mobile adaptation and weak-network fallback.
- Add member center, historical conversations, report downloads, and subscription/entitlement views.
- Add monthly operations report export for source cost, intelligence production, model cost, usage, conversion, and incidents.
- Add standardized delivery assets for operations and support.

Acceptance: PC and H5 users can complete commercial workflows, and operations can export monthly reports.

### M8 SLA, Load, DR, And Launch Readiness

- Run load testing and record capacity, latency, and bottlenecks.
- Run disaster recovery and rollback drills.
- Validate SLA dashboard and downtime accounting.
- Complete architecture docs, deployment scripts, operations manual, incident playbooks, load-test report, and API docs.
- Complete launch compliance checklist and signoff.

Acceptance: V3 exits only when commercial launch evidence is recorded or each exception has approved remediation and owner.

## Acceptance Checklist

- [ ] V2 gray-release exit criteria are met.
- [ ] Dynamic source weighting is active and auditable.
- [ ] Full data lineage can trace collection to report output.
- [ ] Hot/warm/cold storage lifecycle is active.
- [ ] Four-layer tenant and entitlement isolation is enforced.
- [ ] Membership tiers, orders, entitlements, expiration, and downgrade flows work.
- [ ] Paid intelligence sub-store is protected from free users and bulk export.
- [ ] Five report types generate with evidence, timeliness, confidence, and disclaimers.
- [ ] PC and H5 terminals support commercial workflows.
- [ ] Monthly operations report export works.
- [ ] Security controls cover API authorization, paid content, admin audit, and input/output risk.
- [ ] SLA readiness evidence supports at least 99.9% target.
- [ ] Delivery assets are complete.

## Verification Commands

- API: run `mvn test` in `services/api`.
- Collector: run `python -m pytest` in `services/collector`.
- AI worker: run `python -m pytest` in `services/ai-worker`.
- Web: run `npm test` and `npm run build` in `apps/web`.
- Documentation: run the bilingual Markdown pair check after moving or editing docs.
- Commercial readiness: record load-test, DR, security, and launch-compliance evidence in the V3 verification document.

If long-running load, SLA, DR, or security verification cannot run in the current environment, record the attempted command, reason, risk, and remediation plan.

## Current Status

- Status: planned.
- Prerequisite: V2 gray-release exit and approved commercial-launch plan.
- Next action: create V3 verification-results document when V3 implementation starts.
