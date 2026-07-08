# BizSage V2 Core Feature Gap Analysis

Analysis date: 2026-07-08
Scope: V1 MVP → V2 Production High-Availability Engineering Release
Methodology: Cross-reference V1/V2 milestone documents, product-milestones.md, actual codebase implementation, and V1/V2 verification results.

## Summary

BizSage V1 MVP is a closed-loop internal validation. V2 (active) targets production engineering for gray release with a small paid-user cohort. Of the **8 milestone groups in V2**, none is fully complete. **36 core features remain unimplemented** across V2 and V3.

Current codebase covers: basic auth/RBAC, conversation CRUD, streaming diagnosis & learning agents, dual-agent mode transition, three-tier memory, RAG with vector search, four collector paths, conflict detection engine, admin CRUD operations, and bilingual Web UI.

---

## V2 M1 — Operations Foundation

V2 requires multi-level caching, environment separation, backup/restore, monitoring, alerts, and SLA statistics. **6 items, all unimplemented.**

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| 1 | 4-layer Redis cache | Crawler page, API response, global knowledge, dimension-local intelligence caches | ❌ Not implemented |
| 2 | Environment separation | dev / test / staging / production-gray environments | ❌ Only local dev exists |
| 3 | Backup/restore scripts | MySQL + Qdrant backup/restore with rehearsed drills | ❌ Scripts referenced, never executed |
| 4 | Full-link monitoring | 7-dimension metrics: API, collector, AI worker, cache, queue, database, business | ❌ Metrics pipeline not built; admin monitoring view shows hardcoded status |
| 5 | 3-level alert rules | P0/P1/P2 alert rules with escalation owners | ❌ Alert CRUD API exists, alert engine not implemented |
| 6 | SLA statistics | Uptime/latency/error-rate SLA dashboards | ❌ Not started |

---

## V2 M2 — Resilient Collection

V2 requires incremental crawling, proxy rotation, vendor failover, API cache, and collection telemetry. **5 items missing.**

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| 7 | Proxy-vendor rotation + IP cooldown | Multi-vendor proxy rotation with IP-level cooldown | ❌ Not implemented |
| 8 | Multi-vendor API failover | Third-party API vendor failover with automatic switching | ⚠️ `fetch_with_vendor_failover` skeleton exists, mock-only |
| 9 | API response cache + cost control | Cache third-party API responses, track per-vendor cost | ❌ Not implemented |
| 10 | Tiered retry + exponential backoff | Configurable retry tiers with exponential backoff | ⚠️ Resilience helpers exist but retry tiers not wired |
| 11 | Collection telemetry dashboard | Success rate, API volume, cache hit rate, source health | ❌ No telemetry collection or dashboard |

---

## V2 M3 — Data Governance & Knowledge

V2 requires conflict engine, time-series snapshots, optimistic locking, multi-source merge, four-layer data isolation, and dynamic knowledge base. **6 items missing.**

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| 12 | Time-series snapshots | Daily / weekly / monthly snapshots of intelligence state | ❌ SnapshotView stub exists, no actual snapshot storage |
| 13 | Row-level optimistic locking | Versioned updates with conflict detection on concurrent writes | ❌ Not implemented |
| 14 | Multi-source intelligent merge | Merge intelligence from multiple sources on the same fact | ❌ Not implemented |
| 15 | Four-layer data isolation | user-private / region / industry / paid-free isolation | ⚠️ Only paid-free isolation exists |
| 16 | Dynamic real-time knowledge base | Live intelligence ingestion into retrieval corpus | ❌ Still static seed knowledge + manual import |
| 17 | Knowledge lifecycle management | Expiry, deprecation, replacement lifecycle states | ❌ Not implemented |

---

## V2 M4 — RAG & AI Reasoning Controls

V2 requires six-dimension reranking, layered prompt library, multi-model routing, model degradation fallback, and context compression. **5 items missing or incomplete.**

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| 18 | Six-dimension rerank (complete) | authority / timeliness / region / industry / review_confidence / historical_quality | ⚠️ Dimensions defined; actual rerank logic incomplete |
| 19 | Layered prompt library | role / industry / region / compliance / format / source-disclaimer layers | ⚠️ PromptAssembler exists but not systematically used |
| 20 | Multi-model routing | Route to optimal model by cost/latency/capability | ⚠️ ModelRouter skeleton exists, single-provider only |
| 21 | Model degradation fallback | Auto-switch to backup model on primary failure | ❌ Not implemented |
| 22 | Context compression engine | Token-budget-aware compression for long evidence | ⚠️ CompressConfig model exists, algorithm completeness unverified |

---

## V2 M5 — Learning Agent & Dual-Agent Linkage

**Mostly complete.** Core dual-agent flow (diagnosis ↔ learning), three-tier memory with LLM-based extraction and intelligent forgetting, and transition endpoint are all implemented.

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| — | All M5 core features | Diagnosis ↔ Learning with memory | ✅ Implemented |

---

## V2 M6 — Permissions, Paid Gray Release & Reports

V2 requires two-layer RBAC, paid/free isolation, PDF export, user profile, and submission incentives. **5 items missing.**

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| 23 | **PDF binary export** ⚠️ | Generate actual PDF files with sources and disclaimers | ❌ **CRITICAL** — Only JSON metadata returned; no PDF rendering |
| 24 | Two-layer RBAC | Function permission (role) + data permission (scope) | ⚠️ Function permission exists; data permission not implemented |
| 25 | User submission incentives | Review-gated incentive system for user-contributed intelligence | ❌ Not implemented |
| 26 | Paid-user gray release mechanics | Feature flags, traffic control, rollback for paid features | ❌ Not implemented |
| 27 | User profile tag system | Industry, region, scale, membership, preferences, pain points | ⚠️ Basic fields exist; comprehensive tagging not implemented |

---

## V2 M7 — Operations Console & Review Work Orders

**Largely complete.** Admin pages cover dashboard, collection, knowledge, monitoring, alerts, audit, reviews, tickets, human intelligence, and risk rules.

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| — | All M7 core features | Admin console with reviews, alerts, audit, tickets | ✅ Implemented |

---

## V2 M8 — Gray Release Verification

All verification items are **blocked on Docker-capable infrastructure**. **5 items unexecuted.**

| # | Feature | V2 Target | Current Status |
|---|---------|-----------|----------------|
| 28 | Docker infrastructure health checks | MySQL, Redis, Qdrant health checks pass | ❌ Docker CLI unavailable in current environment |
| 29 | 50-concurrent-request load test | API tolerates 50 concurrent requests | ❌ Not executed (requires Docker) |
| 30 | 4-hour (V1) + 7-day (V2) stability observation | Single-node runtime stability | ❌ Not executed |
| 31 | Failure drills | Crawler, API vendor, model provider, database failure drills | ❌ Not executed |
| 32 | Browser full-stack smoke test | Login → diagnosis → report complete flow | ❌ Not executed |

---

## V3 Full-Domain Commercial — Entirely Unplanned for Current Phase

The following 10 items are V3 scope. None has been started.

| # | Feature | V3 Target |
|---|---------|-----------|
| 33 | Dynamic source-weight engine | Real-time weighting from review pass rate, conflict frequency, freshness, consistency |
| 34 | Full data lineage | Traceability from collection through governance, storage, retrieval, reasoning, to report |
| 35 | Hot/warm/cold storage lifecycle | Tiered storage migration policies |
| 36 | Complete membership system | Tiers, plans, orders, entitlements, activation, expiration, downgrade |
| 37 | Five report types | Learning / Market Research / Diagnosis / Special Risk / Periodic Review |
| 38 | H5 mobile adaptation + weak-network fallback | Mobile-responsive UI with graceful degradation |
| 39 | Member center + history + report downloads | Full user-facing commercial portal |
| 40 | Anti-scraping + paid-content anti-export | Security controls for scraping and unauthorized export |
| 41 | Classified protection readiness | Compliance materials for classified protection review |
| 42 | 99.9% SLA readiness | Incident playbooks, load-test reports, operations runbooks, delivery assets |

---

## Prioritized Remediation Sequence

### Immediate (unblock V2 gray release)

1. **PDF binary export** — M6 critical blocker; actual PDF generation with sources/disclaimers
2. **4-layer Redis cache** — M1 foundation; measure hit rate against 70% target
3. **Monitoring & alerting pipeline** — M1 foundation; wire real metrics, not hardcoded status
4. **Docker infrastructure validation** — M8 blocker; get Docker working and health checks passing

### Short-term (complete V2 core)

5. Two-layer RBAC (data permission layer)
6. Time-series snapshots (daily/weekly/monthly)
7. Row-level optimistic locking
8. Layered prompt library (systematize the 6 prompt layers)
9. Multi-model routing + degradation fallback
10. Four-layer data isolation (user-private, region, industry, paid-free)

### Medium-term (V2 verification & V3 entry)

11. 50-concurrent load test
12. 7-day gray-release stability
13. Failure drills (crawler, API vendor, model, database)
14. V3 commercial scope lock and pricing approval

---

## References

- `docs/en/milestones/product-milestones.md` — Cross-version roadmap
- `docs/en/milestones/v1-mvp-milestones.md` — V1 scope and acceptance
- `docs/en/milestones/v2-production-high-availability-milestones.md` — V2 scope and milestones
- `docs/en/milestones/v1-verification-results.md` — V1 automated verification
- `docs/en/milestones/v2-verification-results.md` — V2 automated verification
- `docs/en/milestones/v3-full-domain-commercial-final-milestones.md` — V3 scope
- `docs/en/api/openapi-summary.md` — API surface reference
