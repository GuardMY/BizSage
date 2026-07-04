# BizSage System Architecture and Technical Framework

This document is the English counterpart of `system-architecture-and-framework-zh-CN.md`. It defines the mandatory knowledge-base architecture, industry-chain model, technology choices, system evolution, architecture decisions, and core process flows for BizSage.

## 1. Knowledge-Base Main Framework

### 1.1 Core Product Positioning

Ordinary industry knowledge bases store public definitions and processes. BizSage's differentiator is:

- Full industrial-chain structured decomposition.
- Explicit standard processes.
- Hidden industry rules and practical norms.
- Quantified node metrics.
- Large-model chain reasoning.

The goal is to help a zero-base user understand the full business logic of an industry from raw materials to payment collection within seven days, including how the industry works, default rules, profit nodes, risk blind spots, and key metrics.

Applicable industries include manufacturing, consumer goods, building materials, automotive, medicine, agriculture, chemicals, and local service industries with physical or operational chains.

### 1.2 Six Core Product Modules

1. **Industry overview**: industry category, market size, upstream/downstream map, leading companies, entry threshold, profit model, core profit zones, and weak-profit segments.
2. **Full-chain structured knowledge base**: seven standard chain nodes, each with explicit processes, metrics, hidden rules, risks, and upstream/downstream linkage.
3. **Large-model intelligent parsing engine**: chain reasoning, beginner teaching, reverse analysis, and metric explanation.
4. **Industry metric database**: cost, efficiency, loss, turnover, conversion, channel, and quality-control metrics.
5. **Hidden-rule knowledge base**: industry practices, settlement norms, default concessions, channel rules, logistics pricing, rebates, and other practical rules with compliance boundaries.
6. **Personal learning and favorites**: learning paths, saved nodes, saved metrics, AI notes, mind maps, and review.

### 1.3 Frontend Interaction Shape

The intended product surface includes a left industry-chain tree, middle node detail area, right AI conversation panel, top industry overview map, and floating current-node metric summary.

## 2. Seven-Node Industry Chain Model

All industries use a default seven-node chain and may extend or disable nodes by configuration.

1. **Raw materials**: composition, origin, supply pattern, seasonality, procurement, hidden procurement norms, and cost metrics.
2. **Production/manufacturing/processing**: process steps, capacity, labor, OEM/self-production, SOPs, hidden production norms, yield and efficiency metrics.
3. **Quality control**: inspections, nonconforming-product handling, compliance standards, hidden quality norms, defect metrics.
4. **Warehousing and inventory**: warehouse classes, inbound/outbound, safety stock, seasonal stocking, hidden inventory rules, turnover metrics.
5. **Logistics and circulation**: trunk, local delivery, special lines, pricing, timeliness, damage compensation, hidden logistics charges, cost metrics.
6. **Channel and operations**: agent hierarchy, pricing layers, support policies, stock pressure, rebates, region protection, channel metrics.
7. **Sales, terminal, and collection**: terminal scenarios, pricing, promotions, account period, settlement, negotiation norms, conversion and collection metrics.

## 3. Knowledge Classification and Storage

### 3.1 Dual Knowledge-Base Physical Isolation

| Dimension | Static industry baseline knowledge base | Dynamic real-time intelligence knowledge base |
|-----------|------------------------------------------|----------------------------------------------|
| Content | Industry-chain structure, operating rules, baseline policies, common sense, compliance red lines | Latest policies, local market changes, commodity prices, temporary controls, regional rules |
| Update | Batch update with review, not real-time arbitrary writes | Real-time incremental storage after governance |
| Purpose | Lowest-fallback reasoning baseline and hallucination control | Current diagnosis and market analysis |

### 3.2 Three-Layer Storage

1. **Structured knowledge base (MySQL)** for metrics, rules, parameters, exact queries, calculations, and statistics.
2. **Vector semantic knowledge base** for natural-language Q&A, semantic association, and cross-text reasoning.
3. **Time-series version knowledge base** for snapshots, policy evolution, and historical comparison.

### 3.3 Knowledge Refinement AI Engine

The refinement engine must merge same-source and same-topic duplicate knowledge, remove expired or low-value fragments, standardize fields, bind tags, and produce high-quality model-ready knowledge material.

### 3.4 Knowledge Lifecycle

Raw intelligence -> AI refinement -> separated storage -> snapshot generation -> dynamic heat updates -> low-quality downgrade -> expired archive or elimination.

## 4. Technology Stack Choices

| Component | Choice | Reason |
|-----------|--------|--------|
| Task scheduling | XXL-JOB + Celery + Redis | Visual scheduled management plus distributed priority queues |
| Crawler | Aiohttp + lxml / Playwright | Static high-concurrency crawling plus dynamic-rendering fallback |
| Text extraction | Trafilatura | High-quality body extraction and noise filtering |
| Structured database | MySQL | Business data, metrics, rules, and parameters |
| Vector engine | Dedicated vector database | Semantic search and natural-language reasoning |
| Cache | Multi-level Redis | Cache, distributed lock, queue, and fast access |
| Encryption | AES | User private operating data encryption |
| Privacy desensitization | NLP recognition | Phone, ID, address, and sensitive field masking |

Data collection strategy includes scheduled policy collection, four-hour cost/freight increments, daily market information collection, one-hour tracking for major events for seven days, proxy scheduling, anti-crawler handling, and retry/alert rules.

AI reasoning strategy includes model routing, five-check validation, and degradation fallback through cache, standard answers, or queueing.

## 5. Architecture Evolution

### 5.1 V1.0 Concept Architecture

BizSage is a six-dimensional entrepreneurial diagnosis Agent platform combining static industry knowledge, user first-party operating data, real-time public intelligence, authenticity risk control, old/new conflict handling, and human blind-spot completion.

The three core directions are:

1. Industry-learning cognition Agent.
2. Operating-diagnosis review Agent.
3. Active web intelligence perception engine.

### 5.2 Seven-Layer Backend Intelligence Engine

1. Multi-source collection scheduling.
2. Cleaning and normalization.
3. NLP semantic parsing and chain binding.
4. Opportunity/risk judgment.
5. Knowledge-base synchronization and storage.
6. Admin configuration, review, alerting, audit, and permissions.
7. API service output.

### 5.3 Six-Dimensional Information Loop

1. Static industry standard knowledge base.
2. User first-party operating data.
3. Real-time public dynamic intelligence.
4. Human localized blind-spot completion.
5. Paid circle intelligence.
6. Authenticity and old/new conflict risk-control system.

### 5.4 V4.0 Full-Domain Architecture

The full architecture contains nine layers:

1. Operations engineering foundation.
2. Multi-source data collection.
3. Global data governance.
4. Intelligent knowledge middle platform.
5. Multi-level RAG retrieval enhancement.
6. Large-model reasoning service.
7. Dual-Agent intelligent business core.
8. Commercial business service.
9. Terminal display and output.

The V4.0 architecture completes knowledge middle platform, RAG retrieval, model reasoning, dual-Agent core, commercial services, gateway security, and terminal output on top of the lower data chain.

## 6. RAG Architecture

Six-dimensional precise retrieval must combine keyword retrieval, vector semantic retrieval, time-series historical retrieval, user-region filtering, industry filtering, and membership/permission filtering.

Reranking must consider source authority, content timeliness, region match, industry match, human-review confidence, and historical reasoning accuracy.

Context compression must summarize long intelligence, merge similar knowledge, and adapt content length to the model context window.

Anti-hallucination constraints:

1. If no evidence exists, do not invent conclusions.
2. If information conflicts or is unclear, mark it as suspicious.
3. Every reasoning point must bind source, snapshot version, and publish time where available.

## 7. Agent Framework

### 7.1 Industry-Learning Agent

Capabilities include beginner industry education, policy explanation, operating rules, compliance red lines, real-time industry dynamics, and structured progressive learning.

Workflow: user question -> intent classification -> six-dimensional RAG -> knowledge refinement and compression -> plain-language reconstruction -> structured output.

### 7.2 Operating-Diagnosis Agent

Capabilities include eight-dimensional operating diagnosis, root-cause analysis, cost/profit calculations, peer/local comparison, risk/opportunity discovery, and actionable remediation plans.

User private operating data has the highest reasoning weight. Local human intelligence, authoritative crawler intelligence, and static baseline knowledge are used according to source-weight rules.

### 7.3 Agent Memory

The system supports short-term conversation memory, long-term user profile memory, and intelligent automatic forgetting. Invalid memories older than the configured lifecycle must be cleaned or reduced.

### 7.4 Agent Output Rules

All Agent outputs must include source traceability, information timeliness, confidence level, and standardized compliance disclaimer.

## 8. Prompt Engineering

Prompt types include role prompts, industry prompts, regional prompts, compliance/risk prompts, structured-output prompts, and source/disclaimer prompts.

The reasoning self-check must validate factual correctness, timeliness, regional fit, logical consistency, and compliance risk. Failure should trigger re-reasoning or an information-insufficient response.

## 9. Core Architecture Decisions

- Static baseline knowledge and dynamic intelligence must be physically isolated.
- Opportunity/risk judgment uses four levels: observation, neutral, important, fatal.
- User private operating data has 100% reasoning priority.
- Source weights must become dynamic over time.
- RAG and output validation form a mandatory anti-hallucination loop.
- Prompt engineering must be standardized across the platform.

## 10. Core Process Flows

### 10.1 Data Collection

User entries, Agent flows, crawler data, API data, human intelligence, and private operating data enter the unified data gateway, then flow through backup, cleaning, deduplication, dual risk-control validation, source weighting, separated storage, and knowledge synchronization.

### 10.2 Task Scheduling

XXL-JOB schedules tasks into Celery + Redis queues. Tasks are split by priority, handled by crawler nodes, passed through proxy scheduling, page-type detection, extraction, deduplication, and gateway submission. Failures trigger proxy switching, retry, or alerts.

### 10.3 Human and User Private Data Collection

Human intelligence uses standardized entry, review, authenticity judgment, confidence marking, regional tagging, and isolated storage. User private data uses guided diagnosis collection, optional Excel import, privacy prompts, NLP masking, AES storage, authorization options, lifecycle deletion, and highest reasoning weight.

### 10.4 Intelligence Perception

Normalized intelligence passes through malicious-information detection and old/new conflict comparison. False data is blocked, short-term fluctuations are tagged, regional exceptions update local data only, authoritative permanent updates update the knowledge base, and unsupported conflicts become review tickets.

### 10.5 Diagnosis Generation

The learning Agent reads static knowledge, crawler intelligence, and human local intelligence. The diagnosis Agent first reads encrypted private operating data, then local intelligence and industry baseline data. Both outputs append standardized disclaimer text and data-quality annotations before frontend display or PDF export.

## Source Documents

This document corresponds to the Chinese merged governing document and consolidates the main knowledge-base framework, full-system whitepaper, V4.0 architecture, and system-flow source materials preserved under `raw-docs/`.
