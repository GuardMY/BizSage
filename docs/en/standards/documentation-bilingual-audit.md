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
- `docs/en/api/openapi-summary.md`
- `docs/zh-CN/api/openapi-summary-zh-CN.md`

## Bilingual Maintenance Implemented

- `AGENTS.md` / `AGENTS-zh-CN.md`
- `CHANGELOG.md` / `CHANGELOG-zh-CN.md`
- `README.md` / `README-zh-CN.md`
- `CONTRIBUTING.md` / `CONTRIBUTING-zh-CN.md`
- `apps/android/README.md` / `apps/android/README-zh-CN.md`
- `docs/en/data-collection-and-intelligence-perception.md` / `docs/zh-CN/data-collection-and-intelligence-perception-zh-CN.md`
- `docs/en/development-implementation-guide.md` / `docs/zh-CN/development-implementation-guide-zh-CN.md`
- `docs/en/product-strategy-and-design.md` / `docs/zh-CN/product-strategy-and-design-zh-CN.md`
- `docs/en/risk-management-and-compliance.md` / `docs/zh-CN/risk-management-and-compliance-zh-CN.md`
- `docs/en/system-architecture-and-framework.md` / `docs/zh-CN/system-architecture-and-framework-zh-CN.md`
- `docs/en/api/openapi-summary.md` / `docs/zh-CN/api/openapi-summary-zh-CN.md`
- `docs/en/database/v1-schema.md` / `docs/zh-CN/database/v1-schema-zh-CN.md`
- `docs/en/deployment/local-deployment.md` / `docs/zh-CN/deployment/local-deployment-zh-CN.md`
- `docs/en/deployment/v1-runbook.md` / `docs/zh-CN/deployment/v1-runbook-zh-CN.md`
- `docs/en/milestones/v1-acceptance.md` / `docs/zh-CN/milestones/v1-acceptance-zh-CN.md`
- `docs/en/milestones/v1-mvp-milestones.md` / `docs/zh-CN/milestones/v1-mvp-milestones-zh-CN.md`
- `docs/en/milestones/v1-verification-results.md` / `docs/zh-CN/milestones/v1-verification-results-zh-CN.md`
- `docs/en/milestones/v2-production-high-availability-milestones.md` / `docs/zh-CN/milestones/v2-production-high-availability-milestones-zh-CN.md`
- `docs/en/milestones/v3-full-domain-commercial-final-milestones.md` / `docs/zh-CN/milestones/v3-full-domain-commercial-final-milestones-zh-CN.md`
- `docs/en/milestones/product-milestones.md` / `docs/zh-CN/milestones/product-milestones-zh-CN.md`
- `docs/en/standards/agent-development.md` / `docs/zh-CN/standards/agent-development-zh-CN.md`
- `docs/en/standards/api-response.md` / `docs/zh-CN/standards/api-response-zh-CN.md`
- `docs/en/standards/database-convention.md` / `docs/zh-CN/standards/database-convention-zh-CN.md`
- `docs/en/standards/documentation-bilingual-audit.md` / `docs/zh-CN/standards/documentation-bilingual-audit-zh-CN.md`

## Documents Missing Bilingual Maintenance

No Markdown documentation gaps remain in the current audit scope.

Generated/cache Markdown files, such as `.pytest_cache/README.md`, are excluded from project documentation maintenance.

## Follow-Up Requirements

- New documentation must be created in both English and Chinese.
- New documentation under `docs/` must be placed in `docs/en/` and `docs/zh-CN/`.
- When modifying documentation, update the corresponding language version in the same commit.
- Future milestone document updates must maintain both English and Chinese versions, and record status, completed items, blockers, verification results, and next steps.
