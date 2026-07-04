# BizSage V1 API 摘要

基础路径：`/api`

所有 JSON 端点返回统一结构：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "uuid"
}
```

## 认证

- `POST /auth/login`
  - 请求体：`username`、`password`
  - 返回：token、username、role、regionId、industryId

## 用户

- `GET /users`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
  - 返回脱敏后的手机号和身份证字段。

## 会话

- `POST /conversations`
  - 请求体：`title`
- `GET /conversations`
- `POST /conversations/{id}/archive`
- `POST /conversations/{id}/messages/stream`
  - 输出：`text/event-stream`
  - 请求体：`question`
  - 发送 `event: diagnosis`，包含 answer、sources、confidence、timeliness 和 disclaimer。

## 情报

- `POST /intelligence`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
  - 请求体：title、content、url、industryId、regionId、linkId、sourceId
- `GET /intelligence`
- `POST /intelligence/{id}/approve`

## 知识库

- `POST /knowledge/import`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
  - 请求体：title、content、industryId、regionId、linkId、sourceId

## Worker API

Collector：

- `POST /collect/form`
- `POST /collect/public-page`
- `POST /collect/mock-api`
- `POST /govern`

AI worker：

- `POST /rag/search`
- `POST /agent/diagnose`
