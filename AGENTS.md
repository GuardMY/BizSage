# Repository Guidelines

## Project Structure & Module Organization

BizSage is split by product boundary. `apps/web` contains the Next.js app, React UI, API client code, and Node tests under `apps/web/tests`. `services/api` is the Spring Boot API; Java source lives in `src/main/java`, with tests in `src/test`. `services/collector` and `services/ai-worker` are Python services with code in `app/` and pytest suites in `tests/`. `infra` contains Docker, database, and local startup scripts. Bilingual documentation lives under `docs/en` and `docs/zh-CN`.

Keep module boundaries strict: the web app calls only `services/api`; collectors and workers exchange structured data through explicit interfaces.

## Build, Test, and Development Commands

- `cd apps/web && npm run dev`: start the web app on port `3000`.
- `cd apps/web && npm test`: run Node test files in `tests/*.test.mjs`.
- `cd apps/web && npm run build`: verify the Next.js production build.
- `cd services/api && mvn test`: run Spring Boot and API contract tests.
- `cd services/collector && python -m pytest`: run collector tests.
- `cd services/ai-worker && python -m pytest`: run AI worker tests.
- `./infra/scripts/start-all.ps1`: start the local full stack when Docker and service prerequisites are available.

## Coding Style & Naming Conventions

Follow existing local style before adding abstractions. Java uses package `com.bizsage.api`, Spring Boot conventions, and unified API responses with `code`, `message`, `data`, and `requestId`. Python modules use snake_case files and pytest names such as `test_collectors.py`. Web code uses TypeScript/React conventions and component-oriented names. Do not persist or log sensitive data in plaintext.

## Testing Guidelines

Add focused tests for behavior changes in the owning module. Prefer `*.test.mjs` for web tests, `*Test.java` for API tests, and `test_*.py` for Python services. If Docker, load, or long-running verification cannot run locally, record it as not executed instead of claiming success.

## Commit & Pull Request Guidelines

Recent history uses concise prefixes such as `feat:`, `docs:`, and `chore:`. Keep commits scoped and avoid staging `raw-docs/`, environment files, build outputs, dependency folders, or unrelated dirty files. Pull requests should describe the change, list affected modules, link issues or milestones, include UI screenshots when relevant, and report exact verification commands.

## Documentation & Agent Notes

Documentation changes must update matching English and Chinese files, including `CHANGELOG.md` and `CHANGELOG-zh-CN.md` unless the edit is spelling-only. When `.codegraph/` exists, use CodeGraph before grep or manual file reads to understand code paths.

## Agent Development Standards

Before functional work, read the relevant product, architecture, implementation, data-collection, and risk/compliance documents:

- `docs/en/product-strategy-and-design.md` / `docs/zh-CN/product-strategy-and-design-zh-CN.md`
- `docs/en/system-architecture-and-framework.md` / `docs/zh-CN/system-architecture-and-framework-zh-CN.md`
- `docs/en/development-implementation-guide.md` / `docs/zh-CN/development-implementation-guide-zh-CN.md`
- `docs/en/data-collection-and-intelligence-perception.md` / `docs/zh-CN/data-collection-and-intelligence-perception-zh-CN.md`
- `docs/en/risk-management-and-compliance.md` / `docs/zh-CN/risk-management-and-compliance-zh-CN.md`

Prefer existing repository patterns. Do not implement work outside the current milestone unless the milestone documents are updated in the same change. Never revert, overwrite, or delete others' work; understand related changes before acting.

## Bilingual Documentation & Change Logs

Maintain all project documentation in English and Chinese. English files use `*.md`; Chinese files use matching `*-zh-CN.md` names. Both versions must express the same facts, scope, and acceptance criteria. For API, database, deployment, milestone, acceptance, or development-standard changes, always check the paired language file.

Every functional change, and every documentation-only change that affects meaning, must update `CHANGELOG.md` and `CHANGELOG-zh-CN.md`. Each entry must include the date, change type, affected modules, main changes, verification results, and unfinished items.

## Milestones, Implementation, and Verification

When milestone progress changes, update the matching English and Chinese milestone documents with status, completed items, blockers, verification results, and next steps. Record unrun acceptance checks with the reason, attempted command, and remediation path.

For new behavior, prefer tests before implementation. Run the relevant verification command after each independent milestone: API `mvn test`, collector `python -m pytest`, AI worker `python -m pytest`, and web `npm test` plus `npm run build`. If Docker, load testing, or long-running stability checks cannot run because of environment limits, record them as not executed.

## Product Safety Rules

Backend APIs must return the unified envelope: `code`, `message`, `data`, and `requestId`. AI output must include evidence, timeliness, confidence cues, and a disclaimer; if evidence is missing, return an information-insufficient response. Sensitive data must not be persisted or logged in plaintext.
