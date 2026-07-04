# Agent Development Standards

This document defines the development standards for AI coding agents, automated development agents, and human collaborators working on BizSage.

## 1. Core Principles

- Understand the project documents, existing code structure, and current git state before making changes.
- Prefer existing repository patterns. When no pattern exists, choose the smallest clear and testable implementation.
- Do not implement V2/V3 scope unless the milestone documents have been updated.
- Do not revert or overwrite existing work from others. If related changes conflict, understand them before acting.
- Keep module directories isolated: Web, Android, API, AI worker, collector, infra, and docs must preserve their boundaries.

## 2. Documentation Rules

- All project documentation must be maintained in both Chinese and English.
- When adding or modifying documentation, add or update the corresponding Chinese and English versions in the same change.
- English documentation uses the default `*.md` filename.
- Chinese documentation uses the matching `*-zh-CN.md` filename.
- If the corresponding language version does not exist yet, create it before finishing the change.
- Both language versions must express the same facts, scope, and acceptance criteria. Neither version may become an outdated summary.
- For documentation changes involving APIs, databases, deployment, milestones, acceptance, or development standards, always check the matching language version.

## 3. Change Log Maintenance

- Every functional change must be recorded in the change log.
- The English change log is `CHANGELOG.md`.
- The Chinese change log is `CHANGELOG-zh-CN.md`.
- The same functional change must update both change log files.
- Each entry must include at least the date, change type, affected modules, main changes, verification results, and unfinished items.
- Documentation-only changes must also be recorded unless they are spelling-only fixes that do not change meaning.

## 4. Project Milestone Maintenance

- Whenever milestone progress changes, update the corresponding milestone document in the same change.
- Milestone documents must be maintained in both Chinese and English.
- Each update must include current status, completed items, blockers, verification results, and next steps.
- If an acceptance item cannot be run in the current environment, record the reason, attempted command, and remediation steps in the milestone or verification document.
- Do not record milestone progress only in chat or commit messages; it must be written to repository documentation.

## 5. Implementation Flow

- Read the relevant milestone, API, database, and deployment documents before starting.
- For new behavior, prefer writing tests before implementation.
- Progress milestones in M0 to M8 order. Cross-milestone changes must explain why they are necessary.
- After each independent milestone, run the relevant verification command and commit the result clearly.
- If verification cannot be executed, record the reason, attempted command, and follow-up remediation in the acceptance or verification results document.

## 6. Code Rules

- Backend APIs must return the unified envelope: `code`, `message`, `data`, and `requestId`.
- Sensitive data must not be persisted or logged in plaintext.
- AI output must include evidence, timeliness, confidence cues, and a disclaimer. If there is no evidence, return an information-insufficient response.
- The Web app may call only `services/api`; it must not call workers or databases directly.
- Python workers and collectors must exchange structured data through explicit interfaces.

## 7. Verification Rules

- API: run `mvn test` in `services/api`.
- Collector: run `python -m pytest` in `services/collector`.
- AI worker: run `python -m pytest` in `services/ai-worker`.
- Web: run `npm test` and `npm run build` in `apps/web`.
- If Docker, load testing, or long-running stability verification cannot run because of environment limits, record them as not executed instead of claiming they passed.

## 8. Commit Rules

- Use clear milestone commit prefixes such as `chore:`, `feat:`, and `docs:`.
- Documentation commits must include both Chinese and English versions.
- Do not commit `raw-docs/`, local environment files, build outputs, or dependency directories.
- Before committing, check `git status --short` to avoid staging unrelated files.
