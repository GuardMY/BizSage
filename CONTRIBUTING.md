# Contributing

## Development Rules

- Keep module boundaries explicit. Web calls `services/api`; it must not call
  Python workers or databases directly.
- Keep V1 focused on P0 scope. Do not add V2/V3 features unless a milestone
  document is updated first.
- Add tests for behavior before production code where practical.
- Preserve unified API responses: `code`, `message`, `data`, and `requestId`.
- Never persist sensitive user data in plaintext.

## Commit Style

Use concise milestone commits:

- `chore: initialize v1 monorepo`
- `feat: add core api auth`
- `feat: add collector ingestion`
- `feat: add rag diagnosis worker`
- `feat: add web console`
