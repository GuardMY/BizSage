# BizSage API 摘要

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
  - 返回：token、username、role、regionId、industryId、membershipLevel、consultationPreferences、preferredLocale。

## 用户

- `GET /users`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
  - 返回脱敏后的手机号和身份字段，以及 V2 用户画像字段。
- `GET /users/me`
  - 返回当前已认证用户资料，包括 `preferredLocale`。
- `PUT /users/me/locale`
  - 请求体：`preferredLocale`（`zh-CN` 或 `en`）
  - 持久化当前用户在 web 主页面和 admin 页面共用的语言偏好，并返回更新后的用户资料。

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

## 付费情报

- `POST /paid-intelligence`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
  - 请求体：title、content、url、industryId、regionId、linkId、sourceId
  - 付费专属情报与免费/公开情报分开存储。
- `POST /paid-intelligence/{id}/approve`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
- `GET /paid-intelligence`
  - 运营角色可查看全部付费情报。
  - 种子付费用户可查看与其地域和行业匹配的已审核付费情报。
  - 免费用户返回空列表。

## 知识库

- `POST /knowledge/import`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
  - 请求体：title、content、industryId、regionId、linkId、sourceId

## 报告

- `GET /reports/diagnosis?question=...`
  - 返回经营诊断报告元数据，包含 format `PDF`、sources、confidence、timeliness、selfCheckStatus 和 disclaimer。
  - 免费用户报告不包含付费依据。
  - 种子付费用户报告包含已审核付费依据。

## 运维

- `GET /ops/metrics`
- `GET /ops/alerts`
- `GET /ops/review-work-orders`
- `GET /ops/audit-logs`
  - 角色：`SUPER_ADMIN`、`OPERATOR`
  - 返回 V2 灰度指标、告警状态、可疑/冲突复核工单和审计日志。
