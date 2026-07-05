# LLM 严格闭环实施计划

本文档记录 BizSage 用户侧 LLM 主链路的严格闭环实施方案，目标是让诊断输出走设计中的运行时链路，而不再由 `services/api` 本地拼装回答。

## 1. 目标

目标闭环链路为：

`Web -> services/api -> services/ai-worker -> RAG/Qdrant -> 外部 OpenAI-compatible LLM -> services/api -> Web`

本次闭环覆盖：

- 会话诊断流式返回；
- 诊断报告生成；
- AI worker 与上游 LLM 的运行时健康可见性。

本方案明确将 API 本地诊断拼装逻辑移出用户可见主路径，同时移除 AI worker 在真实诊断路径中的 mock 回答兜底，确保运行状态可观测且真实。

## 2. 概述

本次实施将把用户可见的诊断职责从 `services/api` 的本地字符串拼装迁移到 `services/ai-worker`，同时保持 `apps/web` 的现有调用入口不变。

最终用户侧行为为：

- Web 继续调用现有 API 入口；
- API 成为鉴权、编排和持久化层；
- AI worker 成为唯一的诊断与报告推理引擎；
- worker 不可用或真实 LLM 不可用时，必须显式失败，而不是静默兜底；
- 无证据和冲突证据仍保留为受控业务输出，而不是传输层失败。

## 3. 目标架构行为

### 3.1 主请求路径

用户侧诊断链路为：

1. `apps/web` 通过现有 API 路由提交诊断请求。
2. `services/api` 完成鉴权，并解析会员等级、地域、行业、会话状态和记忆上下文。
3. `services/api` 组装发往 worker 的请求体，包含问题、最近消息、会话摘要、长期记忆和知识证据。
4. `services/api` 调用 `services/ai-worker /agent/diagnose`。
5. `services/ai-worker` 执行证据检索、过滤和 LLM 生成。
6. `services/api` 持久化返回的助手消息，并封装为现有前端响应结构。
7. `apps/web` 渲染结果以及任何显式失败状态。

### 3.2 职责边界

`apps/web` 继续负责：

- 登录状态与 token 处理；
- 提交诊断和报告请求；
- 渲染诊断正文、来源、置信度、时效、免责声明，以及显式失败状态。

`services/api` 继续负责：

- 认证和授权；
- 统一 API 响应封装；
- 会话与记忆持久化；
- AI worker 请求体组装；
- worker 健康暴露与失败映射；
- 面向 Web 的 SSE 封装。

`services/ai-worker` 成为以下职责的唯一执行层：

- 检索与重排；
- 冲突/无证据的受控推理判断；
- 真实外部 LLM 调用；
- 结构化诊断载荷生成。

### 3.3 失败策略

本次闭环采用严格失败策略：

- AI worker 不可用时，API 必须显式失败；
- AI worker 可达但未配置真实 LLM 时，worker 必须显式失败；
- 上游 LLM 返回错误或空回答时，worker 必须显式失败；
- API 不允许回退到当前本地模板回答；
- worker 不允许在真实诊断路径返回 mock 回答。

下列业务级受控输出仍然允许保留：

- 证据不足 -> 受控 `INSUFFICIENT_EVIDENCE` 输出；
- 证据冲突或缺少支撑 -> 受控 `NEEDS_REVIEW` 输出。

这些不属于传输层失败。

## 4. 对外接口与行为变更

### 4.1 面向 Web 的 API

对外入口保持不变：

- `POST /api/conversations/{id}/messages/stream`
- `GET /api/reports/diagnosis`

变化发生在内部行为：

- 两个接口都必须使用 AI worker 作为诊断/推理引擎；
- 两个接口都不得再使用 API 本地诊断拼装作为用户可见主路径。

### 4.2 API 与 worker 的内部契约

API 发往 worker 的请求体必须包含：

- question；
- knowledge evidence；
- recent messages；
- conversation summary；
- long-term memories；
- region ID；
- industry ID；
- membership level；
- 可用时附带 conflict labels。

API 期望的 worker 响应必须包含：

- answer；
- sources；
- confidence；
- timeliness；
- self-check status；
- disclaimer。

如果报告接口仍需要 `summary` 字段而 worker 返回 `answer`，则由 API 做稳定映射，但不应无意破坏前端现有报告契约。

### 4.3 健康与可观测性

运行时需要能区分以下状态：

- API 正常，worker 异常；
- worker 正常，但真实 LLM 配置缺失；
- worker 正常，但上游 LLM 调用失败；
- 诊断链路返回的是受控的“无证据”或“需复核”业务输出。

目标是让运维能看见真实闭环状态，而不是被本地兜底文本掩盖问题。

## 5. 实施步骤

### 5.1 API 诊断委托改造

- 将 API 本地诊断拼装路径替换为 worker 客户端调用。
- 保持消息持久化、摘要持久化和记忆持久化继续在 API 层完成。
- 在数据被流式返回或返回给前端之前，对 worker 响应进行校验和映射。

### 5.2 报告路径闭环

- 将报告生成重构为复用和聊天一致的 worker 推理主链路。
- 保持报告元数据和 API envelope 对前端的稳定性。

### 5.3 AI worker 真实 LLM 严格模式

- 从真实诊断端点移除 mock 回答兜底。
- 将缺少 `OPENAI_COMPATIBLE_API_KEY` 或缺少真实模型可用性视为显式失败。
- 将上游 LLM HTTP 失败或空 completion 视为显式失败。

### 5.4 前端失败态渲染

- 保持当前 API 入口与鉴权流不变。
- 为 worker 不可用、LLM 未就绪、诊断载荷结构异常、API 显式失败等场景补齐清晰展示。
- 保持成功场景与现有来源卡片、元数据卡片的兼容性。

## 6. 测试与验收

### 6.1 API 验证

- 会话流成功路径确实委托 worker，并返回结构化诊断数据；
- 报告成功路径确实委托 worker，并保留报告响应结构；
- worker 连接失败时返回显式失败，且绝不回退旧的本地模板回答；
- worker 返回 `5xx`、超时、非法 JSON 或缺字段时，API 返回显式失败；
- 会员等级、地域和行业上下文能正确透传到 worker。

### 6.2 AI worker 验证

- 只有在真实 LLM 路径已配置且返回有效内容时，诊断才成功；
- 缺少 key 或缺少真实模型就绪条件时显式失败；
- 上游 LLM 错误或空内容时显式失败；
- 无证据和冲突证据仍返回受控推理输出。

### 6.3 Web 验证

- 聊天成功渲染基于 worker 的真实诊断结果；
- SSE 失败状态能被解析并清晰展示；
- 报告失败状态可见，且不会误渲染陈旧或伪造的诊断文本；
- `npm test` 和 `npm run build` 继续作为实施后的验证路径。

## 7. 假设

- 本次闭环同时包含聊天、报告和 worker/LLM 健康可见性。
- `apps/web` 仍必须只调用 `services/api`，不直接访问 AI worker。
- 仓库目标拓扑保持为 `API -> AI worker -> Qdrant / 外部 OpenAI-compatible LLM`。
- 本次上线优先“严格失败”，而不是隐藏式降级。
