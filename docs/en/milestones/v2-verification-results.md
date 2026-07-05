# V2 Verification Results

Verification date: 2026-07-05

## Passed In Current Environment

- API full test suite after MySQL knowledge listing integration: `mvn test` in `services/api`
  - Result: 14 tests passed, 0 failures.
- Collector full test suite after Redis runtime wiring: `python -m pytest` in `services/collector`
  - Result: 22 tests passed, 0 failures.
- AI worker full test suite after Qdrant-first retrieval switch: `python -m pytest` in `services/ai-worker`
  - Result: 21 tests passed, 0 failures.
- Web login gate and bilingual UI tests: `npm test` in `apps/web`
  - Result: 5 tests passed, 0 failures.
- Web production build after login gate and bilingual UI update: `npm run build` in `apps/web`
  - Result: Next.js production build completed successfully.
- Web API-client tests: `npm test` in `apps/web`
  - Result: 3 tests passed, 0 failures.
- Web production build: `npm run build` in `apps/web`
  - Result: Next.js production build completed successfully.

## Covered V2 Acceptance Items

- V2 login profile returns `membershipLevel` and `consultationPreferences`.
- Seed paid user `seed_paid` can authenticate for gray-release validation.
- Paid intelligence is managed through independent `/api/paid-intelligence` APIs and hidden from free users.
- Diagnosis report export metadata includes sources, timeliness, confidence, self-check status, and disclaimer.
- Free-user diagnosis reports exclude paid evidence; seed paid users receive approved paid evidence.
- Operator-only operations APIs expose metrics, review work orders, alerts, and audit logs.
- Operations review, alert, and audit surfaces now query persisted V2 tables instead of returning only hard-coded data.
- API stores for users, conversations, intelligence, paid intelligence, and knowledge use JDBC-backed repositories.
- `GET /api/knowledge` now exposes the MySQL-backed knowledge listing contract used for real knowledge sync and verification.
- User-visible diagnosis and seed knowledge text is readable UTF-8 Chinese in API, AI worker, Web, and corrected database seed paths.
- Web login, diagnosis submission, report metadata, paid intelligence, and ops metrics use real API-client calls.
- Signed-out Web users now see only the standalone login screen; the authenticated workspace supports Chinese/English in-page switching and logout state cleanup.
- Collector resilience helpers now use real Redis-backed runtime state for incremental fingerprint deduplication and recent snapshot fallback.
- AI retrieval now writes vectors to Qdrant and queries Qdrant first, while region, industry, entitlement, and quality reranking stay in Python and self-check still blocks suspicious conflicts with controlled output.

## Not Executed In This Environment

- Docker Compose startup and MySQL/Redis/Qdrant health checks.
  - Reason: Docker CLI was not available during this verification pass in this environment.
  - Command to run: `docker --version`, then `infra\scripts\start-local.ps1`.
  - Remediation: install Docker Desktop or expose Docker CLI in PATH, then rerun infrastructure startup.
- Backup and restore drill.
  - Reason: requires running Docker containers and durable test data.
  - Commands to run: `infra\scripts\backup-mysql.ps1`, `infra\scripts\restore-mysql.ps1 -InputPath <backup.sql>`, `infra\scripts\backup-qdrant.ps1`, `infra\scripts\restore-qdrant.ps1 -InputPath <backup.tar>`.
  - Remediation: execute on a Docker-capable staging machine and record RTO/RPO.
- 50-concurrent-request load test.
  - Reason: full local stack was not started because Docker verification is pending.
  - Command to run: use a load tool against `/api/health` and one authenticated diagnosis endpoint after stack startup.
  - Remediation: run after Docker health checks pass.
- 4-hour V1 stability observation and 7-day V2 gray stability.
  - Reason: requires long-running deployed environment.
  - Command to run: monitor API, collector, AI worker, Web, Redis, MySQL, and Qdrant for the required duration.
  - Remediation: schedule observation during staging or gray release and record incidents.
- Browser-based manual login flow.
  - Reason: local API service and full infrastructure stack were not started during this verification pass.
  - Command to run: start the API service, run `npm run dev` in `apps/web`, then verify `operator/password` login, language switching, diagnosis submission, and logout in a browser.
  - Remediation: execute during the next local full-stack smoke pass.

## Current Exit Status

V2 is active but not ready to exit. MySQL-backed API listing, Redis-backed collector runtime state, Qdrant-first AI retrieval, and existing Web login gate/bilingual UI checks pass in the current codebase, while recovery drills, load testing, Docker health checks, browser-based full-stack smoke testing, and gray stability evidence remain open.
