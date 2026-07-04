# V1/V2 Implementation Closure Design

## Scope

This change closes the most important implementation gaps found in the governing-document review:

1. Replace API in-memory stores with MySQL-backed repositories for the core V1/V2 paths.
2. Repair user-visible garbled text in API, AI worker, Web, and seed SQL.
3. Connect the Web console to real API calls for login, diagnosis, paid intelligence, report metadata, and operations metrics.
4. Re-run repository verification commands and update verification records for commands that cannot run.
5. Turn V2 skeleton data into traceable persisted data where practical, without implementing full V3 commercial workflows.

## Design

The API will use Spring `JdbcTemplate` repositories instead of adding a larger ORM layer. This keeps the current controller contracts stable while making runtime data survive process restarts. Repository tests will cover login users, conversations, knowledge import, free intelligence, paid intelligence entitlement filtering, and diagnosis report source filtering.

The AI worker and Web app will keep their current simple structure. The worker will return readable UTF-8 Chinese fallback text with evidence, timeliness, confidence, self-check status, and disclaimer. The Web app will remain a single work console, but its core interactions will call `lib/api-client.ts` instead of static mock data.

The collector V2 resilience helpers remain lightweight in this pass. Verification documents will distinguish freshly executed checks from Docker, load, and long-running checks that remain blocked by the environment.

## Non-Goals

- No full JPA migration.
- No full Celery/Redis/XXL-JOB crawler platform.
- No complete GraphRAG or Qdrant production integration.
- No membership billing, orders, invoices, or member center.
- No full PDF binary generation; report metadata remains the V2 handoff surface unless time permits.

## Acceptance

- API tests prove repository-backed behavior and unified response contracts.
- AI worker tests prove readable output and controlled no-evidence/conflict responses.
- Web tests prove API-client parsing and at least one real data-loading path.
- Documentation, milestone verification records, and changelogs are updated in English and Chinese.
