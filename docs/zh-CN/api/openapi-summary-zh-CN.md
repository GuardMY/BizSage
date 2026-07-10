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
  - 在唯一的最终 `diagnosis` 事件前流式发送 `status`、`delta` 和可选的 `reset` 事件。
- `POST /conversations/{id}/messages/learn/stream`
  - 输出：`text/event-stream`
  - 请求体：`question`，以及可选的 `chainNodeId`、`learningMode`。
- `POST /conversations/{id}/messages/transition/stream`
  - 输出：`text/event-stream`
  - 请求体：`fromMode`、`toMode`、`question`，以及可选的 `chainNodeId`。

三条 Agent 流统一使用以下事件契约：

- `status`：包含状态（`started`、`validating` 或 `retrying`）、模式和尝试次数。
- `delta`：下一段回答文本，客户端将其追加到当前候选。
- `reset`：自检重试或提供方故障转移前清空当前候选。
- `diagnosis`：唯一的最终结构化回答。
- `error`：稳定的 worker 错误码和安全文案。

只有最终结构化回答会作为助手消息持久化。

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

## Worker API

Collector：

- `POST /collect/form`
- `POST /collect/public-page`
- `POST /collect/mock-api`
- `POST /govern`
- V2 Collector 辅助能力覆盖增量指纹、重试、熔断状态、死信分类和最近快照回退。

AI worker：

- `POST /rag/search`
  - 支持地域、行业和会员等级过滤。
- `POST /agent/diagnose`
  - 对可疑冲突或证据不足场景返回 selfCheckStatus 和受控输出。
- `POST /agent/diagnose/stream`
- `POST /agent/learn/stream`
- `POST /agent/transition/stream`
  - API 到 worker 的内部 SSE 端点，传递模型增量、reset 和唯一的最终 `result` 事件。
  - 现有同步 Agent 端点继续用于报告生成和兼容场景。
