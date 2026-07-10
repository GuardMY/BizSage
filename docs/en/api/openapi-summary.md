# BizSage API Summary

Base path: `/api`

All JSON endpoints return:

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "uuid"
}
```

## Auth

- `POST /auth/login`
  - Body: `username`, `password`
  - Returns: token, username, role, regionId, industryId, membershipLevel, consultationPreferences, preferredLocale.

## Users

- `GET /users`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Returns masked phone and identity fields plus V2 profile fields.
- `GET /users/me`
  - Returns the current authenticated user's profile, including `preferredLocale`.
- `PUT /users/me/locale`
  - Body: `preferredLocale` (`zh-CN` or `en`)
  - Persists the current user's shared web/admin language preference and returns the updated profile.

## Conversations

- `POST /conversations`
  - Body: `title`
- `GET /conversations`
- `POST /conversations/{id}/archive`
- `POST /conversations/{id}/messages/stream`
  - Produces: `text/event-stream`
  - Body: `question`
  - Streams `status`, `delta`, and optional `reset` events before one terminal `diagnosis` event.
- `POST /conversations/{id}/messages/learn/stream`
  - Produces: `text/event-stream`
  - Body: `question`, optional `chainNodeId` and `learningMode`.
- `POST /conversations/{id}/messages/transition/stream`
  - Produces: `text/event-stream`
  - Body: `fromMode`, `toMode`, `question`, and optional `chainNodeId`.

All three Agent streams use the same event contract:

- `status`: state (`started`, `validating`, or `retrying`), mode, and attempt number.
- `delta`: the next answer text; clients append it to the current candidate.
- `reset`: clears the candidate before self-check retry or provider failover.
- `diagnosis`: the single final structured answer.
- `error`: a stable worker error code and safe message.

Only the final structured answer is persisted as an assistant message.

## Intelligence

- `POST /intelligence`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Body: title, content, url, industryId, regionId, linkId, sourceId
- `GET /intelligence`
- `POST /intelligence/{id}/approve`

## Paid Intelligence

- `POST /paid-intelligence`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Body: title, content, url, industryId, regionId, linkId, sourceId
  - Stores paid-only intelligence separately from free/public intelligence.
- `POST /paid-intelligence/{id}/approve`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
- `GET /paid-intelligence`
  - Operators can see all paid intelligence.
  - Seed paid users see approved paid intelligence matching their region and industry.
  - Free users receive an empty list.

## Knowledge

- `POST /knowledge/import`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Body: title, content, industryId, regionId, linkId, sourceId

## Reports

- `GET /reports/diagnosis?question=...`
  - Returns diagnosis report metadata with format `PDF`, sources, confidence, timeliness, selfCheckStatus, and disclaimer.
  - Free users receive reports without paid evidence.
  - Seed paid users receive approved paid evidence.

## Operations

- `GET /ops/metrics`
- `GET /ops/alerts`
- `GET /ops/review-work-orders`
- `GET /ops/audit-logs`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Returns V2 gray-release metrics, alert state, suspicious/conflict review work orders, and audit log entries.

## Worker APIs

Collector:

- `POST /collect/form`
- `POST /collect/public-page`
- `POST /collect/mock-api`
- `POST /govern`
- V2 collector helpers cover incremental fingerprints, retries, circuit breaker state, dead-letter classification, and recent snapshot fallback.

AI worker:

- `POST /rag/search`
  - Supports V2 filters: region, industry, and membership level.
- `POST /agent/diagnose`
  - Returns selfCheckStatus and controlled output for suspicious conflicts or insufficient evidence.
- `POST /agent/diagnose/stream`
- `POST /agent/learn/stream`
- `POST /agent/transition/stream`
  - Internal API-to-worker SSE endpoints that relay model deltas, resets, and one final `result` event.
  - Existing synchronous Agent endpoints remain available for report generation and compatibility.
