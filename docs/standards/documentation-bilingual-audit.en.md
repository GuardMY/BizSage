# Documentation Bilingual Maintenance Audit

Audit date: 2026-07-04

## Audit Scope

This audit checks Markdown documentation in the repository and working tree. Dependency directories, build outputs, and test caches are excluded.

## Bilingual Maintenance Already Implemented

- `AGENTS.md`
- `AGENTS.en.md`
- `CHANGELOG.zh-CN.md`
- `CHANGELOG.en.md`
- `docs/standards/agent-development.zh-CN.md`
- `docs/standards/agent-development.en.md`
- `docs/standards/documentation-bilingual-audit.zh-CN.md`
- `docs/standards/documentation-bilingual-audit.en.md`

## Documents Missing Bilingual Maintenance

The following documents currently have only one language version. When any of them is modified later, the corresponding language version must be created or updated in the same change:

- `README.md`
- `CONTRIBUTING.md`
- `apps/android/README.md`
- `docs/api/openapi-summary.md`
- `docs/database/v1-schema.md`
- `docs/deployment/local-deployment.md`
- `docs/deployment/v1-runbook.md`
- `docs/milestones/v1-acceptance.md`
- `docs/milestones/v1-mvp-milestones.md`
- `docs/milestones/v1-verification-results.md`
- `docs/standards/api-response.md`
- `docs/standards/database-convention.md`

## Follow-Up Requirements

- New documentation must be created in both Chinese and English.
- When modifying the single-language historical documents listed above, add the missing language version in the same commit.
- Future milestone document updates must maintain both Chinese and English versions, and record status, completed items, blockers, verification results, and next steps.
