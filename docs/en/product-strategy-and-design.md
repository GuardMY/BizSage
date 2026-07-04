# BizSage Product Strategy and Product Design Blueprint

This document is the English counterpart of `product-strategy-and-design-zh-CN.md`. It defines product positioning, target users, dual-Agent product design, interaction principles, AI behavior, data quality expectations, and industry-chain modeling rules.

## 1. Market and Technical Direction

BizSage is designed for the current Agent product direction: Agentic RAG, runtime engineering, layered memory, multi-Agent orchestration, symbolic-neural hybrid reasoning, standardized tool calling, context engineering, GraphRAG-style knowledge retrieval, full-link observability, local fallback models, multi-model routing, and vertical industry intelligence collection.

The product must not be a generic chatbot. It must behave as an industry-chain reasoning product backed by structured knowledge, dynamic intelligence, compliance controls, and user operating data.

## 2. Product Positioning

BizSage is a dual-mode industry intelligence Agent platform:

- **Industry-learning Agent**: helps zero-base entrepreneurs understand industry structure, rules, hidden norms, metrics, risks, and opportunities.
- **Operating-diagnosis Agent**: helps operating merchants diagnose business problems from first-party operating data, local market conditions, supply chain issues, channel performance, risks, and short-term opportunities.

The product's core difference is the combination of:

1. Full industry-chain structured decomposition.
2. Explicit standard processes.
3. Hidden industry rules and practical operating norms.
4. Quantified node metrics.
5. Dynamic intelligence from public, human, paid, and user-owned data.
6. AI chain reasoning with evidence, confidence, timeliness, and disclaimers.

## 3. Target Users

Primary users:

- Zero-base entrepreneurs who need fast industry onboarding.
- Existing small and medium merchants who need operating diagnosis.
- Local business operators who need regional intelligence and practical actions.
- Paid users who need higher-value reports, paid-circle intelligence, and custom intelligence work orders.

Internal users:

- Operations staff maintaining intelligence and knowledge.
- Reviewers validating intelligence and conflicts.
- Administrators configuring sources, permissions, alerts, and compliance text.

## 4. Dual-Mode Product Design

### 4.1 Industry-Learning Agent

The learning Agent must provide:

1. Industry overview and entry threshold explanation.
2. Seven-chain-node guided learning.
3. Core metrics, hidden rules, risk points, and opportunity points.
4. Fast-start mode and full-chain deep-learning mode.
5. Node summary, stage summary, and complete industry report.
6. One-click transition from learning to diagnosis when a business problem is discovered.

### 4.2 Operating-Diagnosis Agent

The diagnosis Agent must ask structured questions and collect:

- City/region and business context.
- Revenue, profit, cost, customer flow, conversion, and inventory.
- Channel status and supplier situation.
- Local operating pain points.
- User-described exceptional conditions.

Diagnosis dimensions:

1. Local market adaptation.
2. Supply-chain cost.
3. Inventory and warehousing.
4. Channel traffic.
5. Pricing and profit.
6. Compliance risk.
7. Peer competition gap.
8. Short-term opportunity capture.

Output must include root cause, estimated impact, priority, concrete action steps, evidence, confidence, timeliness, and disclaimers.

### 4.3 Dual-Mode Linkage

Learning and diagnosis must share the same knowledge and intelligence foundation. Users can jump from diagnosis to targeted learning for weak chain nodes, and from learning to diagnosis when the user wants to evaluate a real business.

## 5. Product Architecture and Feature Requirements

### 5.1 Core System Foundation

The system must support industry switching, knowledge-base retrieval, dynamic intelligence retrieval, user-private data weighting, report generation, auditability, and entitlement-aware content access.

### 5.2 Five-Layer Data Storage Concept

Product design assumes separated storage for structured knowledge, vector semantic knowledge, dynamic intelligence, time-series snapshots, and user-private operating data.

### 5.3 User-Side Features

User-facing features include industry selection, dual-Agent conversation, structured guided collection, chain-node browsing, source/evidence display, risk/opportunity cards, confidence labels, reports, favorites, and history.

### 5.4 Operations-Side Features

Operations features include industry knowledge maintenance, intelligence review, crawler/source management, risk-rule configuration, user/member management, alert center, audit logs, and compliance copy management.

### 5.5 Permission and User System

Permissions must distinguish free users, paid users, operations staff, reviewers, administrators, and super administrators. Paid/free intelligence must be isolated by entitlement.

## 6. UI/UX Standards

The first screen should be the actual usable product surface. The main layout should prioritize work efficiency:

- Industry selector and current context must be visible.
- Dual-Agent entry points must be clear.
- Conversation, chain-node knowledge, evidence, and report actions must be easy to access.
- Output must show source, time, confidence, and disclaimer without requiring the user to search for them.
- Admin interfaces should be dense, scannable, and operations-oriented.

The product should not use decorative marketing pages as the core experience.

## 7. AI Interaction Standards

Agent interaction must be semi-dialogue guided rather than open-ended vague chat. The system must:

- Ask only necessary next questions.
- Keep structured state during diagnosis.
- Avoid pretending to know when evidence is insufficient.
- Translate professional terms into understandable business language.
- Connect single-point answers to upstream and downstream industry-chain effects.
- Mark regional exceptions, short-term fluctuations, suspicious information, and information gaps.

## 8. Data Quality Standards

Data used by the product must be complete, sourced, tagged, deduplicated, time-aware, region-aware, and confidence-scored. Data records must bind industry ID, chain node ID, region, source, publish/crawl time, severity where applicable, and permission/entitlement state.

AI answers must prefer user private data over local human intelligence, local intelligence over authoritative crawler intelligence, and authoritative crawler intelligence over static baseline knowledge when making operating diagnosis.

## 9. Agent Product Detailed Design

### 9.1 Eight Agent Capability Modules

The product design maps to planning, retrieval, risk control, diagnosis reasoning, learning guidance, reporting, memory, and operations/audit capabilities. Implementations may be service-based rather than literally separate Agents, but responsibilities must remain explicit.

### 9.2 Guided Interaction Mechanism

The learning Agent supports fast onboarding and full-chain learning. The diagnosis Agent follows a fixed diagnostic sequence to prevent missing key operating facts.

### 9.3 Two-Dimensional Guidance

The product must support both:

- Industry-wide key concern guidance.
- Full-chain node-by-node guidance.

Users should be able to move between a concern and its corresponding chain node.

### 9.4 Smart Summaries

The product must support immediate node summaries, stage summaries, and complete industry reports. Summaries must be concise, structured, sourced, and reusable.

## 10. Industry Classification and Chain Modeling

BizSage uses a default seven-node chain:

1. Raw materials.
2. Production/manufacturing/processing.
3. Quality control.
4. Warehousing and inventory.
5. Logistics and circulation.
6. Channel and operations.
7. Sales, terminal, and collection.

Industry variants can disable, replace, or extend nodes:

- Heavy manufacturing keeps most nodes and adds upstream extraction, outsourcing, and compliance branches.
- E-commerce and local-life businesses add traffic, platform, after-sales, and reverse-logistics branches.
- Fresh food and agriculture add seasonality, cold chain, loss, and origin rules.
- Cross-border businesses add customs, bonded storage, foreign exchange, and overseas channel branches.
- Pure service businesses remove physical production nodes and focus on staffing, fulfillment, quality control, traffic, and payment collection.

The backend must provide configuration switches for industry-specific chain branches and metrics.

## 11. Product Acceptance Rules

Product delivery is acceptable only when:

- The dual-Agent flow can run end to end.
- Outputs include evidence, timeliness, confidence, and disclaimers.
- User private data is collected, desensitized, encrypted, isolated, and lifecycle-controlled.
- Paid/free intelligence is separated.
- Regional and industry filters work.
- Reports can be generated where the milestone requires them.
- Admin review and audit flows exist for intelligence, risk, and compliance.

## Source Documents

This document corresponds to the Chinese merged governing document and consolidates the product strategy, Agent technology matching, dual-mode PRD, industry-chain knowledge-base design, and industry classification source materials preserved under `raw-docs/`.
