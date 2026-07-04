# V1 Acceptance Record

## Automated Verification

- API unit/integration tests: `mvn test` in `services/api`.
- Collector tests: `python -m pytest` in `services/collector`.
- AI worker tests: `python -m pytest` in `services/ai-worker`.
- Web tests: `npm test` in `apps/web`.
- Web production build: `npm run build` in `apps/web`.

## Manual Acceptance Scenarios

- Login with `admin`, `operator`, or `user` using password `password`.
- Create and archive a conversation.
- Send a diagnosis request to `/api/conversations/{id}/messages/stream`.
- Enter, list, and approve intelligence.
- Import static baseline knowledge.
- Run all four collector paths and then `/govern`.
- Verify user phone and identity fields are masked in `/api/users`.

## Known Environment Gaps

- Docker CLI is not installed in this environment, so compose startup and MySQL/Redis/Qdrant health checks were not executed here.
- The 4-hour single-node stability observation was not run in this session.
- The 50-concurrent-request load test is documented as an M8 target and should be run after services are started on a Docker-capable machine.
