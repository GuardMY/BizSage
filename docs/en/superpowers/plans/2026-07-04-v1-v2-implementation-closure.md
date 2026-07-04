# V1/V2 Implementation Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the top five V1/V2 implementation gaps: persistence, readable text, real Web API calls, verification records, and traceable V2 skeleton data.

**Architecture:** Keep controller contracts stable and replace in-memory API stores with focused `JdbcTemplate` repositories. Keep AI worker and Web structures simple while connecting visible behavior to real data.

**Tech Stack:** Spring Boot, JDBC, MySQL-compatible schema, FastAPI/Pydantic, Next.js/React, Node tests, pytest, Maven.

---

### Task 1: API Persistence

- [ ] Add failing API tests for repository-backed conversations, intelligence, paid intelligence, and knowledge behavior.
- [ ] Replace in-memory stores with JDBC implementations using the existing schema.
- [ ] Keep controller response envelopes unchanged.
- [ ] Run `mvn test` in `services/api`.

### Task 2: Readable Text

- [ ] Add/update tests that assert readable Chinese output for diagnosis, no-evidence, and disclaimer text.
- [ ] Replace garbled seed strings in Java, Python, Web, and SQL.
- [ ] Run API, AI worker, and Web tests.

### Task 3: Web Real API Flow

- [ ] Add/adjust Web tests for `api-client` parsing and data-loading helpers.
- [ ] Wire login, diagnosis submission, paid intelligence, report metadata, and ops metrics to API calls.
- [ ] Keep static fallback only for error/empty states.
- [ ] Run `npm test` and `npm run build` in `apps/web`.

### Task 4: Verification Records

- [ ] Run available verification commands.
- [ ] Update V1/V2 verification documents with fresh results and blocked checks.
- [ ] Update English and Chinese changelogs.

### Task 5: V2 Traceability

- [ ] Persist or query V2 report/review/audit/ops data where practical from existing tables.
- [ ] Keep V3 commercial workflows out of scope.
- [ ] Run full verification commands again after documentation updates.
