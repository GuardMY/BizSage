# BizSage V1 API Summary

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
  - Returns: token, username, role, regionId, industryId

## Users

- `GET /users`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Returns masked phone and identity fields.

## Conversations

- `POST /conversations`
  - Body: `title`
- `GET /conversations`
- `POST /conversations/{id}/archive`
- `POST /conversations/{id}/messages/stream`
  - Produces: `text/event-stream`
  - Body: `question`
  - Emits `event: diagnosis` with answer, sources, confidence, timeliness, and disclaimer.

## Intelligence

- `POST /intelligence`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Body: title, content, url, industryId, regionId, linkId, sourceId
- `GET /intelligence`
- `POST /intelligence/{id}/approve`

## Knowledge

- `POST /knowledge/import`
  - Roles: `SUPER_ADMIN`, `OPERATOR`
  - Body: title, content, industryId, regionId, linkId, sourceId

## Worker APIs

Collector:

- `POST /collect/form`
- `POST /collect/public-page`
- `POST /collect/mock-api`
- `POST /govern`

AI worker:

- `POST /rag/search`
- `POST /agent/diagnose`
