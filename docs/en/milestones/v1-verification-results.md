# V1 Verification Results

Verification date: 2026-07-04

## Passed

- API tests: `mvn test` in `services/api`
  - Result: 12 tests passed, 0 failures.
- Collector tests: `python -m pytest` in `services/collector`
  - Result: 11 tests passed, 0 failures.
- AI worker tests: `python -m pytest` in `services/ai-worker`
  - Result: 6 tests passed, 0 failures.
- Web tests: `npm test` in `apps/web`
  - Result: 3 tests passed, 0 failures.
- Web production build: `npm run build` in `apps/web`
  - Result: Next.js production build completed successfully.

## Covered Acceptance Items

- Four collector source paths are covered by tests:
  - user private form;
  - user private Excel;
  - public page;
  - mock third-party API.
- Diagnosis responses include sources, confidence, timeliness, and disclaimer.
- No-evidence diagnosis returns an information-insufficient response.
- User list API returns masked phone and identity values.
- RBAC blocks ordinary users from listing users.
- Conversation create/list/archive flow works.
- Intelligence create/list/approve flow works.
- Knowledge import flow works.
- Core API runtime stores now use JDBC-backed persistence instead of in-memory stores.
- Web diagnosis now calls API-client helpers for login, conversation creation, SSE diagnosis, reports, paid intelligence, and ops metrics.

## Not Executed In This Environment

- Docker Compose startup and MySQL/Redis/Qdrant health checks.
  - Reason: Docker CLI is not installed or not in PATH.
  - Command attempted: `docker --version`.
- 50-concurrent-request load test.
  - Reason: full local stack was not started because Docker is unavailable.
- 4-hour single-node stability observation.
  - Reason: requires long-running local stack after Docker/runtime startup.

## Follow-Up To Fully Close V1 Acceptance

1. Install Docker Desktop or expose Docker CLI in PATH.
2. Run `infra\scripts\start-local.ps1`.
3. Start API, collector, AI worker, and Web using `docs/en/deployment/v1-runbook.md`.
4. Run a 50-concurrent-request smoke test against `/api/health` and one authenticated diagnosis endpoint.
5. Keep the stack running for 4 hours and record any crashes or error spikes.
