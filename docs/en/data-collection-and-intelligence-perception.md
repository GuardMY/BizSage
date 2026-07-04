# BizSage Data Collection System and Intelligence Perception Engine

This document is the English counterpart of `data-collection-and-intelligence-perception-zh-CN.md`. It defines the mandatory data-collection, intelligence-processing, governance, infrastructure, and staged delivery rules that future BizSage development must follow.

## 1. Data Collection System Architecture

### 1.1 Six-Layer Closed-Loop Architecture

BizSage uses a six-layer hybrid collection architecture. The system is led by an in-house distributed crawler, supplemented by compliant third-party APIs, enriched by offline human intelligence, and topped by user-owned private operating data.

| Layer | Name | Positioning |
|------|------|-------------|
| 1 | User private operating data | Highest reasoning weight and core basis for business diagnosis |
| 2 | Human localized offline intelligence | Differentiated proprietary data moat |
| 3 | In-house distributed compliant crawler cluster | Core public web data source |
| 4 | Third-party compliant industry API layer | Standardized market, macro, and statistical data |
| 5 | Risk-control normalization and data governance | Unified cleaning, deduplication, validation, and multi-source fusion |
| 6 | Engineering operations foundation | Retry, circuit breaking, dead-letter queues, cache, monitoring, scaling, tenant isolation, snapshots, audit logs, dynamic weights, and locks |

The complete data flow is:

Multi-source collection -> rate limiting and circuit breaking -> unified task scheduling -> raw-data backup -> incremental fingerprint detection -> cleaning and text extraction -> URL and text deduplication -> dual pre-risk-control validation -> dynamic source weighting -> time-series snapshot generation -> multi-level cache write -> multi-tenant isolated storage -> row-versioned knowledge-base merge -> monitoring metric reporting -> full-link audit logging -> industry knowledge-base sync -> dual-Agent reasoning -> standardized disclaimer and evidence output.

### 1.2 Six-Dimensional Source Weight System

| Priority | Source type | Base weight | Description |
----------|-------------|-------------|-------------|
| 1 | User first-party operating data | 100% | Core basis for operating diagnosis |
| 2 | Local human intelligence / paid circle intelligence | 85% | Proprietary differentiated data |
| 3 | S/A-level authoritative crawler intelligence | 60% | Government and authoritative industry platforms |
| 4 | B-level industry information | 35% | Industry media and general information |
| 5 | C/D-level self-media information | 10% | Archive only; not used for risk or opportunity judgment |
| 6 | Static industry knowledge baseline | Fallback baseline | Used when dynamic intelligence is unavailable |

Dynamic source weighting must evaluate historical human-review pass rate, conflict and rumor frequency, freshness, long-term accuracy, baseline consistency, and compliance level. Low-quality sources must be down-weighted over time, and reliable authoritative sources may be up-weighted.

### 1.3 Four Data Source Categories

**User private operating data** has 100% reasoning weight. It is collected through guided dialogue and optional Excel import. Sensitive data must be detected, desensitized, AES-encrypted, isolated, and destroyed automatically after 90 days unless a stricter rule applies. Training authorization must be explicit and optional.

**Human localized intelligence** has 85% base weight. It covers offline oral policies, local business districts, and industry circle information that crawlers cannot access. It requires standardized entry, review, confidence scoring, regional isolation, and paid/free intelligence separation.

**In-house compliant crawler intelligence** has 60% base weight and must use layered site compliance, incremental crawling, retries, circuit breaking, dead-letter handling, deduplication, and pre-risk-control validation.

**Third-party compliant APIs** are used for official, structured, paid, or copyright-sensitive data such as commodity prices, logistics rates, customs data, macroeconomic statistics, and paid industry statistics. Paid research reports, paid market data, and paid information platforms must not be crawled.

### 1.4 In-House Crawler Cluster

The crawler is a six-layer self-developed architecture:

| Layer | Component | Responsibility |
|------|-----------|----------------|
| Scheduling control | Celery + Redis + XXL-JOB | Distributed scheduling, priority sharding, visual cron configuration |
| Worker nodes | Aiohttp + Playwright fallback | High-concurrency page fetching and dynamic rendering |
| Proxy pool | In-house multi-vendor proxy scheduler | IP health checks, tiered assignment, regional filtering |
| Extraction and cleaning | Trafilatura + filtering rules | Main-text extraction and advertisement/navigation removal |
| Deduplication | Redis Bloom filter + SimHash | URL and semantic text deduplication |
| Pre-risk-control validation | Rumor engine + old/new conflict engine | Data authenticity validation |

Production-grade crawler requirements include MD5 and SimHash incremental-change detection, tiered retry with exponential backoff, domain-level circuit breaking, queue-protection degradation, proxy-vendor failover, dead-letter classification, weekly dead-letter review, and at least 30 days of dead-letter retention.

### 1.5 Third-Party API Collection

For same-category market or statistics APIs, at least two to three vendors must be configured where feasible. API responses must be cached in Redis for 5 to 30 minutes when appropriate. After repeated vendor failures, traffic must fail over automatically. If all providers fail, the system must use recent time-series snapshot data as a fallback. API usage, failure rate, and cost details must be logged for cost control.

### 1.6 Human Intelligence Collection

Human intelligence must fill machine blind spots through local contributors, industry practitioners, merchant submissions, association cooperation, and paid expert/circle intelligence. The process must include standardized forms, operator review, confidence marking, regional tagging, paid-content tagging, rejection records, and reward activation only after review approval.

### 1.7 Global Data Governance

All collection sources must flow into the unified data gateway, be normalized, deduplicated, validated by dual risk-control engines, assigned source weights, stored into separated data stores, versioned through snapshots, and made available to the dual-Agent reasoning layer.

Invalid or malicious information must be archived to a false-information ledger and blocked from storage. Suspicious information without authoritative support must become a human-review ticket. Valid information enters normal storage, cache, snapshot, and reasoning flows.

## 2. Data Infrastructure

### 2.1 Four-Level Cache Architecture

| Level | Cache object | Purpose |
------|--------------|---------|
| L1 | Crawler page cache | Reduce duplicate crawling and proxy consumption |
| L2 | API response cache | Reduce paid calls and external rate-limit pressure |
| L3 | Global industry knowledge cache | Improve Agent query latency |
| L4 | Dimension-local cache | Cache by city, industry, and local intelligence segment |

Cache TTL rules include permanent static baseline refresh, 5-30 minute market/API cache, two-hour temporary hot-event cache, and current-session-only user private data cache.

### 2.2 Multi-Tenant Four-Layer Isolation

Data isolation must separate user-private data, regional local intelligence, industry-specific data, and paid/free intelligence. Access must be filtered by identity, role, region, industry, and entitlement.

### 2.3 Monitoring and Alerts

The system must monitor crawler success rate, API call volume and error rate, cache hit rate, queue backlog, source health, intelligence matching quality, AI reasoning quality, and business metrics. Severe intelligence or infrastructure events must trigger dashboard alerts and administrator notifications.

### 2.4 Full-Link Compliance Audit Logs

Collection behavior, entry operations, review records, data authorization, crawler logs, AI reasoning logs, admin operations, and complaint handling must be retained for no less than six months unless a stricter rule applies. Logs must support traceability by request, intelligence, user, and operation identifiers.

## 3. Intelligence Perception Engine

### 3.1 Seven-Layer Engine

The engine contains multi-source scheduling, cleaning and normalization, NLP semantic parsing and chain binding, opportunity/risk judgment, knowledge-base synchronization, review/alert/admin modules, and standardized external APIs.

### 3.2 Collection Strategy

Public online data should use a compliant crawler and official APIs together. Offline regional data should use local contributors, associations, communities, and merchant submissions. High-end circle intelligence should be obtained through compliant paid supply, desensitization, and special intelligence work orders.

### 3.3 Processing Pipeline

The pipeline must extract title, body, publish time, source, region, industry, chain node, event type, opportunity/risk flag, severity, quantitative change, influence cycle, confidence, and lineage information.

### 3.4 Intelligence Grading and Distribution

Opportunity and risk intelligence must be graded from observation to fatal level. Fatal-level intelligence can trigger active alerts, homepage pinning, admin notifications, and Agent reasoning priority.

### 3.5 Malicious False Information Blocking

The system must identify emotional incitement, coordinated spam, old-news recycling, competitor smear content, single-source unsupported claims, and old/new data conflicts. The five-layer blocking loop is source blacklist, semantic identification, multi-source verification, source-weight downgrading, and transparent frontend disclaimers.

## 4. Staged Implementation Combination

### 4.1 Scenario Coverage

The five collection scenarios are public online industry data, offline regional niche information, high-end circle intelligence, user first-party private operating data, and emergency fallback collection for sudden market or regulatory changes.

### 4.2 MVP / P0

MVP should use the in-house compliant crawler, selected third-party APIs, guided user data collection, and a simple manual operations-reporting channel. Offline circle intelligence is deferred unless the milestone explicitly accepts the scope change.

### 4.3 Growth / P1

The growth stage should build the long-term standard collection mix: in-house crawler plus APIs, local intelligence contributor network, user-submission incentives, paid circle intelligence, custom intelligence work orders, high-tier user data authorization APIs, and emergency manual reporting.

### 4.4 Mature / P2

The mature stage should optimize coverage and cost by expanding association partnerships, comparing API vendors, automating submission pre-filtering, and building intelligence asset dashboards.

## Appendix A. Core Data Tables

The core data model includes raw intelligence data, structured intelligence judgment, industry crawler keyword configuration, data source configuration, and intelligence alert records. Fields must preserve source, original URL, title, body, crawl time, publish time, region, SimHash, industry IDs, chain node IDs, event tags, opportunity/risk type, severity, quantitative changes, impact cycle, reasoning text, confidence, review flags, and expiry flags.

## Appendix B. Tooling Choices

The preferred crawler implementation is an in-house distributed crawler because it provides native compliance control, business coupling, risk-control hooks, and flexible scheduling. Aiohttp is the main static-page engine, Playwright is the dynamic-rendering fallback, Trafilatura is the main text-extraction tool, Celery + Redis is the core distributed queue, XXL-JOB is the visual scheduling tool, Redis is used for proxy scheduling and Bloom deduplication, and SimHash is used for semantic text deduplication.

## Appendix C. Mandatory Landing Constraints

1. Do not crawl paid reports, paid market data, or paid information platforms; use official compliant APIs.
2. Self-media and forum excerpts must stay within reasonable citation limits and must not copy full articles.
3. All intelligence and knowledge records must bind industry ID, chain node ID, region, confidence, and source weight.
4. All Agent outputs must include evidence, timeliness, confidence cues, risk notes, and disclaimers.
5. User private operating data must be AES-encrypted, desensitized, isolated, and destroyed after 90 days unless explicitly retained under a compliant rule.
6. Full-link operation logs must be retained for at least six months.
7. Multi-source knowledge updates must use row-level version locking and merge logic.
8. Third-party market/statistics APIs must have fallback vendors, cache, and snapshot fallback where feasible.
9. Crawler clusters must enable incremental crawling, tiered retry, circuit breaking, degradation, and dead-letter queues.
10. All data APIs and admin pages must enforce authorization and data isolation.

## Source Documents

This document corresponds to the Chinese merged governing document and consolidates the data-collection, backend intelligence-engine, and staged collection-solution source materials preserved under `raw-docs/`.
