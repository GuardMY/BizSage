# Documentation Bilingual Maintenance Audit

Audit date: 2026-07-04

## Audit Scope

This audit checks Markdown documentation in the repository and working tree. Dependency directories, build outputs, and test caches are excluded.

## Naming Convention

- English documentation uses the default `*.md` filename.
- Chinese documentation uses the matching `*-zh-CN.md` filename.

Examples:

- `README.md`
- `README-zh-CN.md`
- `docs/api/openapi-summary.md`
- `docs/api/openapi-summary-zh-CN.md`

## Bilingual Maintenance Implemented

- `AGENTS.md` / `AGENTS-zh-CN.md`
- `CHANGELOG.md` / `CHANGELOG-zh-CN.md`
- `README.md` / `README-zh-CN.md`
- `CONTRIBUTING.md` / `CONTRIBUTING-zh-CN.md`
- `apps/android/README.md` / `apps/android/README-zh-CN.md`
- `docs/api/openapi-summary.md` / `docs/api/openapi-summary-zh-CN.md`
- `docs/database/v1-schema.md` / `docs/database/v1-schema-zh-CN.md`
- `docs/deployment/local-deployment.md` / `docs/deployment/local-deployment-zh-CN.md`
- `docs/deployment/v1-runbook.md` / `docs/deployment/v1-runbook-zh-CN.md`
- `docs/milestones/v1-acceptance.md` / `docs/milestones/v1-acceptance-zh-CN.md`
- `docs/milestones/v1-mvp-milestones.md` / `docs/milestones/v1-mvp-milestones-zh-CN.md`
- `docs/milestones/v1-verification-results.md` / `docs/milestones/v1-verification-results-zh-CN.md`
- `docs/milestones/product-milestones.md` / `docs/milestones/product-milestones-zh-CN.md`
- `docs/standards/agent-development.md` / `docs/standards/agent-development-zh-CN.md`
- `docs/standards/api-response.md` / `docs/standards/api-response-zh-CN.md`
- `docs/standards/database-convention.md` / `docs/standards/database-convention-zh-CN.md`
- `docs/standards/documentation-bilingual-audit.md` / `docs/standards/documentation-bilingual-audit-zh-CN.md`

## Documents Missing Bilingual Maintenance

No Markdown documentation gaps remain in the current audit scope.

Generated/cache Markdown files, such as `.pytest_cache/README.md`, are excluded from project documentation maintenance.

## Follow-Up Requirements

- New documentation must be created in both English and Chinese.
- When modifying documentation, update the corresponding language version in the same commit.
- Future milestone document updates must maintain both English and Chinese versions, and record status, completed items, blockers, verification results, and next steps.
