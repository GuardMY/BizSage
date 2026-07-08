# BizSage V3 Admin UI Design

## 1. Design Goal

The V3 admin UI is the operations surface for the complete BizSage commercial loop. It covers knowledge, collection, intelligence, review, risk control, membership, orders, alerts, audit, compliance, and operations reporting. It is not a secondary user-chat page. It is a daily workspace for operations staff, reviewers, crawler operators, legal/compliance staff, finance/commercial operators, administrators, and super administrators.

The admin UI should reuse the existing `apps/web` foundation: Next.js App Router, React, `lucide-react`, the workspace shell, side navigation, top identity area, cards, tables, modals, and bilingual copy structure. The admin surface must increase information density, reduce conversation-style layout, and emphasize filtering, batch actions, dual review, permission boundaries, and audit traceability.

## 2. Roles And Permission Boundaries

| Role | Core tasks | Default visible modules | High-risk limits |
|---|---|---|---|
| Super administrator | Global configuration, permissions, compliance switches, production release approval | All modules | Critical configuration requires secondary confirmation and audit logging |
| Administrator | Operations configuration, staff permissions, alert handling, reports | Control center, users/members, alerts, logs, compliance, reports | Cannot bypass dual review to view plaintext private user data |
| Operations staff | Intelligence review, human intelligence entry, knowledge inspection, user compensation | Knowledge base, intelligence, tickets, human intelligence, users/members | Cannot change system-level risk thresholds |
| Reviewer | Truth, conflict, complaint, and compliance review | Intelligence, tickets, compliance, logs | Cannot configure data sources or membership entitlements |
| Crawler operator | Sources, scheduling, proxies, collection quality, cost | Collection scheduling, alerts, logs, reports | Cannot process members or orders |
| Legal/compliance | Disclaimer copy, gray-content controls, inspection reports, audit evidence | Compliance, logs, tickets, reports | Read-only access to user business summaries |
| Finance/commercial operator | Orders, entitlements, cost, paid conversion | Commercial, users/members, reports | Cannot edit intelligence content or risk rules |
| Read-only observer | Stability, operations quality, audit lookup | Read-only control center, reports, logs | No write actions |

Every page must enforce both function permission and data permission. Data must be filtered by user, region, industry, and entitlement. Private user operating data shows desensitized summaries by default. Any reversible access must go through a dual-authorization workflow.

## 3. Information Architecture

The admin UI uses grouped left navigation, a top context bar, a main content area, and right-side detail drawers.

| Navigation group | Modules | Main pages |
|---|---|---|
| Overview | Operations control center | Business health, risk tasks, SLA, cost, review queues, commercial metrics |
| Knowledge and intelligence | Industry knowledge base, intelligence management, human intelligence, tickets | Knowledge tree, version diff, intelligence list, review workspace, conflict/rumor/complaint tickets |
| Collection and governance | Collection scheduling, data sources, keywords, risk rules, data lifecycle | Source health, job scheduling, proxy health, rule configuration, hot/warm/cold archiving |
| Commercial | Users/members, orders/entitlements, report delivery, submission incentives | User list, member compensation, order ledger, entitlement configuration, report download records |
| Operations security | Alert center, monitoring metrics, audit logs, permission management | Alert handling, service metrics, operation logs, RBAC policies |
| Compliance and reports | Compliance configuration, inspection tasks, monthly operations reports | Disclaimer copy, gray-content switch, inspection reports, operations exports |

Desktop navigation may keep the existing `320px` rail width, with an admin option to collapse to a `72px` icon rail. Mobile V3 mainly supports emergency handling and read-only lookup; complex tables degrade to horizontal scrolling or summary cards.

## 4. Global Layout And Reuse Strategy

| Existing frontend asset | Admin reuse | Required enhancement |
|---|---|---|
| `WorkspaceShell` | Reference for `AdminShell` | Navigation groups, collapsed mode, environment label, alert entry |
| `.rail` | Reuse the dark navigation style | Second-level menus, badge counts, read-only mode notice |
| `.topbar` | Reuse identity, language, and sign-out structure | Environment, tenant/industry/region filters, global search |
| `.workspaceCard` | Metric cards, detail cards, configuration blocks | Admin cards should use 8px radius or less and lighter shadows |
| `.table` / `.row` | Low-density lists | Add real tables, batch selection, pinned columns, pagination, sorting |
| `.modal` | Confirmation, detail, and authorization modals | Add right drawers, full-screen editors, dual-review dialogs |
| `lucide-react` | Continue using for buttons and navigation | Icon buttons require `title` and accessible labels |

The visual language should keep the current green, steel-blue, amber, and red status system, but admin pages should mainly use white and light-gray work surfaces. Alert, risk, and compliance states may use color as a secondary cue, but must always include text labels.

## 5. Key Page Designs

### 5.1 Operations Control Center

The first screen is an actionable workspace, not a welcome or marketing page.

Top metrics: new intelligence today, pending intelligence reviews, P0/P1 alerts, API success rate, crawler success rate, AI self-check pass rate, cache hit rate, order revenue, paid conversion, and monthly SLA availability.

The main area is split into risk tasks, production health, intelligence production, and commercial operations. Each area shows status, trend, owner, and next action, with entry points for review, alert acknowledgment, logs, degradation playbooks, order detail, and report export.

### 5.2 Industry Knowledge Base Management

Use a three-column layout: industry and chain tree on the left, knowledge-node editor in the center, versions/evidence/review panel on the right.

Required controls include the industry chain tree, form/JSON content editor modes, version diff, dual review, and batch inspection. A submitter cannot approve their own change. Batch inspection should cover expired records, missing sources, conflicts, and low confidence.

### 5.3 Collection Scheduling

Organize this page around source -> job -> storage quality -> cost. Suggested columns: source name, type, weight, industry coverage, region coverage, crawl cycle, concurrency limit, success rate, block rate, cost, latest crawl, status, owner, actions.

The detail drawer shows compliance notes, request strategy, proxy pool, retry policy, dead-letter queue, 24-hour trend, linked keywords, and blacklist rules. High-risk actions such as disabling a core source, raising concurrency, or skipping compliance checks must show impact scope and rollback instructions.

### 5.4 Intelligence Management And Review Workspace

The intelligence page must support both list search and queue-style review.

| View | Scenario | Core interaction |
|---|---|---|
| Overview | Understand new, pending, archived, and paid intelligence | Metric cards, trends, quick filters |
| List | Search, filter, archive, export in bulk | Multi-condition filtering, column sorting, batch actions |
| Review workspace | Judge truth, conflict, and severity one item at a time | Evidence on the left, content in the center, verdict panel on the right |
| Detail drawer | Inspect context without leaving the list | Source, lineage, similar intelligence, history |

Review verdicts: pass, reject, mark suspicious, escalate ticket, route to compliance, set as paid intelligence, archive. Every verdict records reviewer, time, reason, evidence, and before/after field differences.

### 5.5 Ticket Ledger

Ticket ledgers cover false rumors, old/new conflicts, complaints, alerts, compliance inspections, and sensitive-data access requests. Use a queue layout: filters on the left, ticket table in the center, handling timeline on the right. Each ticket shows severity, affected industry/region, linked intelligence, status, owner, remaining SLA, and next action.

Recommended state flow: new -> claimed -> in progress -> waiting for review -> closed -> archived. P0 tickets must support owner escalation and notification records.

### 5.6 Human Intelligence Management

Human intelligence fills local blind spots, paid-circle information, user submissions, and partner sources. Entry fields: city, industry, chain node, content, source type, collector, event time, confidence, entitlement level, attachments, desensitization note, expiration. New entries enter the review queue by default and do not immediately participate in user-facing reasoning.

Paid-intelligence permissions must clearly show: hidden from free users, normal paid, premium paid, internal only, and legal freeze. Frozen content must be blocked from API output and report citations.

### 5.7 Risk Rule Configuration

Risk pages use six tabs: rumor detection, conflict judgment, gray content, AI output self-check, API abuse protection, and paid-content protection. Use sliders or numeric inputs for thresholds, switches for enablement, multi-selects for scope, segmented controls for risk level, and radio choices for activation mode. Before saving, show impact scope, rollback version, and gray-release environment.

All rule changes must create audit logs and support version rollback.

### 5.8 Users, Members, Orders, And Entitlements

Organize user/member pages by profile -> entitlements -> usage records -> risk and compliance.

| Page | Core information | Actions |
|---|---|---|
| User list | Account, region, industry, member level, last active time, report count, risk flags | Search, freeze, note, view summary |
| User detail | Profile, tags, conversation summaries, reports, orders, entitlements | Compensate membership, adjust role, request data access |
| Member entitlements | Free, normal paid, premium paid, enterprise/internal | Configure visible intelligence, report quota, export permission |
| Order ledger | Order id, amount, status, channel, invoice, refund | Query, export, refund review |
| Submission incentives | Submissions, reviews, rewards, leaderboard | Review submission, settle membership time |

Administrators must not directly view plaintext user operating data. Data access requires a sensitive-data request ticket, dual approval, time-limited scope, field-limited display, watermarking, and audit logging.

### 5.9 Alert Center And Monitoring Metrics

The alert center is grouped by P0/P1/P2 and supports acknowledge, claim, silence, escalate, convert to ticket, and close. Metrics are grouped into service health, database, crawler, API, AI reasoning, cache, queue, business metrics, and cost metrics. Each metric page provides current value, threshold, trend, linked logs, and suggested handling steps.

P0 alert detail must show trigger time, impact scope, linked services, recent changes, automatic degradation result, notification records, owner, recovery target, and postmortem entry.

### 5.10 Audit Logs

Audit logs must support request id, user, administrator, intelligence, report, order, time, IP, and operation type search. Log categories include operation logs, crawl logs, AI reasoning logs, permission logs, sensitive-data access logs, order logs, compliance logs, and complaint logs.

Detail pages must show request chain, before/after field changes, matched permission, data scope, desensitization state, result code, exception summary, and exportable audit evidence packages.

### 5.11 Compliance Configuration And Inspection

Compliance pages include disclaimer copy, service agreement, privacy agreement, gray-content hiding, blocked-information blind spots, monthly inspections, and legal evidence packages. Compliance copy editors require versioning, preview, review submission, publishing, and rollback. The gray-content one-click hide switch must show affected intelligence count, report citation count, and user-facing display changes.

Monthly inspection tasks include knowledge inspection, crawler compliance, AI output sampling, user authorization check, paid-content isolation check, and audit-log retention check.

### 5.12 Monthly Operations Reports

The report center provides fixed templates and custom exports. Fixed templates: intelligence production monthly report, user growth monthly report, paid conversion monthly report, alerts and SLA monthly report, compliance inspection monthly report, cost monthly report, report delivery monthly report. Export formats: PDF, CSV, and internal audit package. Exporting paid intelligence or sensitive logs must record export scope and approval ticket id.

## 6. Critical Business Flows

```text
Intelligence entry to user visibility:
collection / human entry -> cleaning and deduplication -> risk and conflict detection -> pending review queue -> approval -> entitlement confirmation -> knowledge/intelligence sync -> user-side retrieval and report citation -> traceable admin audit

Knowledge version release:
draft edit -> automatic field validation -> source and confidence check -> submit for review -> second-person approval -> gray release -> metric observation -> full release or rollback

P0 alert handling:
alert triggered -> automatic notification -> administrator claims -> inspect impact scope and recent changes -> execute playbook or degradation -> recovery verification -> close ticket -> generate postmortem record

Sensitive data access:
administrator starts request -> enters reason, fields, and duration -> second administrator/legal approval -> temporary grant -> watermarked desensitized view -> automatic expiration -> audit log archived
```

## 7. Components And Interaction Standards

| Component | Scenario | Standard |
|---|---|---|
| Metric card | Control center, monitoring, commercial pages | Show value, delta, threshold status, and click-through detail |
| Data table | Intelligence, users, orders, logs | Filtering, sorting, pagination, column configuration, batch actions |
| Detail drawer | Intelligence, user, ticket, alert | Open from the right without losing list context |
| Full-screen editor | Knowledge nodes, compliance copy, complex rules | Draft, preview, version diff |
| Review panel | Intelligence, knowledge, compliance, sensitive access | Evidence, verdict, reason, next owner |
| Confirmation modal | High-risk actions | Impact scope, rollback path, secondary typed confirmation |
| Status tag | Alerts, reviews, orders, entitlements | Color plus text, never color-only |
| Global search | Request id, user, intelligence, order, ticket | Quick navigation with permission filtering |

Admin buttons should prefer icon buttons for common commands such as view, edit, archive, export, refresh, rollback, acknowledge, and close. Commands that cannot be understood from an icon alone should use icon plus short text. Every icon button must provide a hover tooltip.

## 8. Data And API Requirements

The admin UI must call only `services/api`. It must not call databases, collectors, AI workers, or monitoring components directly. V3 admin APIs should cover the control center, knowledge base, collection, intelligence, tickets, human intelligence, risk control, users/members, commercial operations, alerts/monitoring, audit logs, and compliance/reports.

Every write action must return a `requestId`. Successful front-end notices should include an entry point to view the corresponding audit log.

## 9. Acceptance Criteria

| Dimension | Acceptance criteria |
|---|---|
| Scope completeness | Covers the full V3 admin scope and supports commercial, compliance, audit, and operations loops |
| Permission isolation | Roles see and operate only authorized data; sensitive access requires dual approval |
| Intelligence loop | Intelligence can be traced from collection/entry, review, entitlement, sync, citation, to audit |
| Knowledge loop | Knowledge edit, diff, dual review, publish, and rollback are complete |
| Operations loop | Alerts can be claimed, handled, closed, reviewed, and linked to metrics/logs |
| Compliance loop | Disclaimer copy, gray content, inspections, and evidence packages can be managed |
| Commercial loop | Users, members, orders, entitlements, report delivery, and submission incentives can be queried and handled |
| Reuse | Extends the existing Next.js workspace visual and component system without adding unnecessary heavy UI frameworks |
| Accessibility | Icon buttons have accessible labels, tables support keyboard focus, high-risk actions use explicit copy |
| Responsiveness | Desktop supports full operations; mobile supports alerts, tickets, logs, and critical details in read-only/light-action mode |

## 10. Suggested Delivery Phases

| Phase | Delivery |
|---|---|
| Admin-V3-1 | `AdminShell`, navigation, permission gate, control center, alert entry, basic log search |
| Admin-V3-2 | Intelligence management, review workspace, ticket ledger, human intelligence |
| Admin-V3-3 | Knowledge-base management, version diff, dual review, publish/rollback |
| Admin-V3-4 | Collection scheduling, data sources, keywords, risk rules, data lifecycle |
| Admin-V3-5 | Users/members, orders/entitlements, report delivery, submission incentives |
| Admin-V3-6 | Compliance configuration, inspection tasks, monthly reports, audit evidence packages, mobile emergency views |

Each phase must update bilingual documentation and change logs, then add role permissions, API contracts, acceptance cases, and regression tests.

## 11. Current Admin-V3-1 / Admin-V3-2 Implementation Status

The first complete vertical loop has entered implementation. Scope includes real database tables, startup auto-migration, `services/api` admin endpoints, the `apps/web` `/admin` console, and source-level regression tests.

Implemented backend capabilities include dashboard aggregation, alert listing and acknowledge/claim/close actions, audit-log search, intelligence review and verdicts, ticket listing and transitions, and human-intelligence entry and review. Approved human intelligence is promoted into the main intelligence records; admin write actions create audit logs.

Implemented tables include `admin_intelligence_reviews`, `admin_tickets`, and `admin_human_intelligence`, while reusing the existing `alert_events`, `audit_logs`, and `intelligence` tables. Table definitions are included in the MySQL initialization script and are also created by API startup migration for existing databases.

Implemented frontend capabilities include the standalone `/admin` route, admin sign-in and role gate, control center, alert center, audit logs, intelligence review, ticket ledger, and human-intelligence entry/review. The page calls `/api/admin/**` directly and does not use frontend mock data.

Verification note: the current execution environment does not provide `mvn`, `node`, or `npm`, so Maven and Web test commands cannot run here. `AdminV3ApiTest` and Web source regression tests were added and should be run in an environment with the required toolchain.