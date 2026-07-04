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
- If a naming pattern already exists, follow it. For example, use `*.zh-CN.md` for Chinese and `*.en.md` or `*.md` for English.
- If the corresponding language version does not exist yet, create it before finishing the change.
- Both language versions must express the same facts, scope, and acceptance criteria. Neither version may become an outdated summary.
- For documentation changes involving APIs, databases, deployment, milestones, acceptance, or development standards, always check the matching language version.

## 3. Implementation Flow

- Read the relevant milestone, API, database, and deployment documents before starting.
- For new behavior, prefer writing tests before implementation.
- Progress milestones in M0 to M8 order. Cross-milestone changes must explain why they are necessary.
- After each independent milestone, run the relevant verification command and commit the result clearly.
- If verification cannot be executed, record the reason, attempted command, and follow-up remediation in the acceptance or verification results document.

## 4. Code Rules

- Backend APIs must return the unified envelope: `code`, `message`, `data`, and `requestId`.
- Sensitive data must not be persisted or logged in plaintext.
- AI output must include evidence, timeliness, confidence cues, and a disclaimer. If there is no evidence, return an information-insufficient response.
- The Web app may call only `services/api`; it must not call workers or databases directly.
- Python workers and collectors must exchange structured data through explicit interfaces.

## 5. Verification Rules

- API: run `mvn test` in `services/api`.
- Collector: run `python -m pytest` in `services/collector`.
- AI worker: run `python -m pytest` in `services/ai-worker`.
- Web: run `npm test` and `npm run build` in `apps/web`.
- If Docker, load testing, or long-running stability verification cannot run because of environment limits, record them as not executed instead of claiming they passed.

## 6. Commit Rules

- Use clear milestone commit prefixes such as `chore:`, `feat:`, and `docs:`.
- Documentation commits must include both Chinese and English versions.
- Do not commit `raw-docs/`, local environment files, build outputs, or dependency directories.
- Before committing, check `git status --short` to avoid staging unrelated files.
