# BizSage Risk Management and Compliance System

This document is the English counterpart of `risk-management-and-compliance-zh-CN.md`. It defines the mandatory risk taxonomy, mitigation controls, data quality standards, launch compliance checklist, and responsibility model for BizSage.

## 1. Risk Landscape

BizSage covers three core modules: industry-learning Agent, operating-diagnosis Agent, and active web intelligence perception. Risks are grouped into four categories with 22 concrete risks.

| Category | Count | Focus | Priority |
|----------|-------|-------|----------|
| Legal and compliance | 8 | Crawling compliance, privacy, copyright, qualification, cross-border data, content compliance | Highest |
| Commercial competition | 4 | Product copying, vertical tools, large-platform pressure, pricing competition | High |
| Technical system | 6 | Anti-crawler failure, hallucination, knowledge errors, database pressure, security, API dependency | High |
| Operations and user | 4 | User loss claims, local mismatch, paid-service disputes, content public-opinion risk | Medium |

## 2. Legal and Compliance Risks

### F1. Crawler Infringement

Bulk crawling of commercial websites, paid reports, and commodity data can create copyright and unfair-competition risk. Paid data must be obtained through compliant official APIs or licensed procurement. Public crawler behavior must respect site rules, rate limits, and reasonable citation boundaries.

### F2. Reputation Infringement

Negative intelligence, rumors, or unverified industry gossip can cause defamation risk. Single-source negative information must be marked suspicious and weakened; formal risk labeling requires authoritative multi-source support.

### F3. Policy Interpretation Error

AI policy interpretation may mislead merchants. Policy outputs require fixed disclaimers, source links, timeliness labels, and no definitive legal or business guarantee.

### F4. Privacy Leakage

User revenue, supplier, address, customer, contact, and account information must be desensitized, encrypted, isolated, lifecycle-controlled, and never used for training without explicit authorization.

### F5. Copyright Infringement

Industry processes, metrics, books, reports, and articles must not be copied into the knowledge base without authorization. Knowledge entries should be rewritten, sourced, and limited to compliant use.

### F6. Unlicensed Consulting

Outputs must not imply licensed investment, legal, tax, or regulated consulting. They must be framed as business reference and require offline professional consultation for major decisions.

### F7. Cross-Border Data Compliance

Cross-border crawling, overseas data collection, and data export must follow applicable filing and compliance requirements. If compliance cannot be confirmed, the data must not be collected or used.

### F8. Gray Industry Rules

Kickbacks, hidden rebates, channel manipulation, and similar gray rules must be treated carefully. They may be marked as risk awareness content but must not be presented as recommended actions.

## 3. Commercial Competition Risks

### C1. Product Copying

The product may be copied if it only exposes generic workflows. The moat must come from proprietary local intelligence, dynamic data, structured chain knowledge, operational review data, and high-quality diagnosis workflows.

### C2. Vertical Tool Interception

Niche vertical tools may capture specific industries. BizSage must support industry-specific chain branches and regional intelligence to remain competitive.

### C3. Large-Platform Pressure

Large SaaS or e-commerce platforms may add free or bundled features. BizSage should emphasize cross-platform, local, and full-chain intelligence that platform-native tools cannot easily provide.

### C4. Low-Price Competition

Competitors may undercut pricing. Paid differentiation must be tied to exclusive intelligence, reports, work orders, and operational value rather than generic chat.

## 4. Technical System Risks

### T1. Anti-Crawler Failure

Crawler failures can interrupt dynamic intelligence updates. Mitigation requires source health checks, retry, proxy rotation, circuit breaking, dead-letter handling, fallback APIs, and operations alerts.

### T2. Large-Model Hallucination

AI may fabricate metrics, rules, or policies. Mitigation requires RAG evidence, information-insufficient responses, five-check self-test, conflict marking, and mandatory source/timeliness/confidence/disclaimer output.

### T3. Knowledge-Base Error

Human entry or stale data may mislead users. Mitigation requires review, source marking, versioning, user correction feedback, monthly inspection, and rollback.

### T4. Database Pressure

Large intelligence and conversation data can degrade service. Mitigation requires indexes, hot/warm/cold storage, archiving, cache, query optimization, and capacity monitoring.

### T5. Security Breach

Security incidents can expose private operating data and admin configuration. Mitigation requires encryption, least privilege, audit logs, penetration testing, secret management, backup, and incident response.

### T6. API Dependency

Third-party model or data APIs may fail, rate-limit, change price, or shut down. Mitigation requires multi-model routing, vendor redundancy, caching, local fallback or standard-answer fallback, and cost monitoring.

## 5. Operations and User Risks

### O1. User Loss Claims

Users may claim losses after following diagnosis suggestions. Outputs must be non-guaranteed references, include disclaimers, and encourage professional local consultation for major investment.

### O2. Regional Mismatch

Generic industry rules may not fit local markets. Outputs must filter by region, mark missing local coverage, and avoid overconfident recommendations.

### O3. Paid Fulfillment Disputes

Paid users may dispute delayed intelligence, limited diagnosis, or report quality. Paid entitlements, refresh frequency, and service boundaries must be explicit.

### O4. Content Public-Opinion Risk

Long-term display of negative company content or gray rules can trigger complaints. Complaint channels, takedown/review flows, and content governance are required.

## 6. Risk Mitigation Matrix

Mitigation controls include pre-avoidance, in-process control, and post-event fallback:

- Data-source whitelisting and compliance tiering.
- Robots and site-rule checks where applicable.
- No crawling of paid content.
- Reasonable excerpt limits and source attribution.
- Multi-source verification.
- Negative-content weakening and complaint handling.
- Mandatory AI disclaimer and evidence display.
- User authorization and privacy lifecycle controls.
- Full-link audit logs.
- Monthly legal and operations inspection.
- Security scans and penetration tests.
- Backup and disaster recovery drills.

## 7. Data Quality Standards

### 7.1 Quality Dimensions

Data quality must cover completeness, accuracy, timeliness, source traceability, tag binding, deduplication, conflict resolution, and compliance status.

### 7.2 Quantitative Targets

| Metric | Target | Alert threshold | Unacceptable threshold |
|--------|--------|-----------------|------------------------|
| Tag completeness | >= 99.5% | < 98% | < 95% |
| Sampled content accuracy | >= 99% | < 97% | < 95% |
| Old/new conflict rate | <= 0.5% | > 1% | > 3% |
| Intelligence storage latency | <= 30 minutes | > 1 hour | > 4 hours |
| Outdated active-data share | <= 5% | > 10% | > 20% |
| Source labeling | 100% | < 100% | Not acceptable |
| Original-copying rate | 0% | > 0% | > 5% |

### 7.3 Knowledge-Base Entry Standards

Knowledge entries must be original rewritten content, sourced when based on public references, tagged by chain node, risk type, impact object, and metric ID, and reviewed before release. Paid proprietary content must not be copied.

### 7.4 Intelligence Data Quality

Intelligence must pass URL and text deduplication, garbage filtering, main-text extraction, standardized fields, numeric extraction, source compliance checks, and structured field completion.

## 8. Quality Monitoring

Automated monitoring must track crawler success rate, abnormal metric fluctuation, storage latency, source health, and knowledge-base correction/error rate. Manual inspections include monthly full knowledge-base inspection, weekly intelligence sampling, seasonal expired-data cleanup, and monthly compliance review.

## 9. Data Lifecycle

| Data type | Retention | Handling |
|-----------|-----------|----------|
| Normal conversation data | 90 days | Automatic deletion/archive at expiry |
| Paid-user historical reports | User-retained where allowed | Raw conversations expire; desensitized summaries may remain |
| Active intelligence | 90 days | High-performance storage |
| Historical intelligence | 90 days to 1 year | Mark outdated and weaken display |
| Archived intelligence | Over 1 year | Cold archive |
| Operation logs | >= 6 months | Audit evidence |
| User operating secrets | Until user deletion/account cancellation or explicit lifecycle | Encrypted, destroyed on deletion |

## 10. Launch Compliance Checklist

Before launch, confirm:

1. Software copyright or applicable registration/filing.
2. Public privacy policy, service agreement, and disclaimer.
3. Full legal review of knowledge base, crawler rules, and AI output templates.
4. Server/domain filing where required.
5. Backup and failover validation with RTO/RPO records.
6. Compliance training records for product, engineering, operations, and support.

## 11. Audit Preparation

Maintain user agreements, disclaimers, crawler compliance rules, data classification lists, cross-border data documentation, crawler logs, AI dialogue logs, admin logs, complaint records, authorization records, penetration test reports, code security checks, encryption audits, and permission audits.

## 12. Compliance Incident Response

Incidents must be classified into three levels. Level-one incidents such as lawsuit, regulatory contact, or data leakage require immediate function shutdown where needed, legal handling, evidence preservation, external communication, and remediation. Level-two incidents require a response within 48 hours. Level-three incidents are handled on the same day.

## 13. Risk Responsibility Model

| Role | Responsibility |
|------|----------------|
| Legal owner | Compliance review, contracts, complaints, regulatory communication |
| Product owner | Risk-function design, disclaimer flows, compliant user experience |
| Technical owner | Security architecture, encryption, backup, penetration testing |
| Operations owner | Content inspection, disputes, paid fulfillment |
| Ops/SRE owner | Monitoring, incident response, DR drills |
| Crawler owner | Crawler compliance, source management, anti-crawler handling |
| All staff | Report risks and complete compliance training |

## 14. Continuous Review

BizSage must run monthly risk review, quarterly compliance review, annual risk summary, monthly knowledge-base compliance inspection, monthly crawler compliance inspection, monthly privacy compliance inspection, quarterly security testing, and regular emergency-plan drills.

## Source Documents

This document corresponds to the Chinese merged governing document and consolidates the full-platform risk list, mitigation measures, PRD data quality standards, and launch compliance requirements preserved under `raw-docs/`.
