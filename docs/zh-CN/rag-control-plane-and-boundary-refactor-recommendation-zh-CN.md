# BizSage RAG 控制面与边界重构建议

本文是 `rag-control-plane-and-boundary-refactor-recommendation.md` 的中文对应版本，用于记录 `services/api` 与 `services/ai-worker` 当前的职责拆分、推荐的控制面放置位置，以及面向 BizSage 的分阶段 RAG 边界重构方案。

分析日期：2026-07-09

## 1. 结论摘要

推荐的目标形态是：

- `services/api` 继续作为控制面。
- `services/ai-worker` 演进为完整的 RAG 执行面。

落到实际职责上，就是：

- API 保留鉴权、授权、数据范围、会话归属、最终持久化以及面向前端的失败语义。
- AI worker 负责检索、重排、上下文压缩、带记忆的 prompt 组装和最终推理。

仓库当前已经部分朝这个方向演进，但运行时边界仍有漂移：

- API 在单次请求里组装了过多 RAG 输入内容。
- AI worker 仍承载了一部分更适合被视为 API 下发范围的业务过滤语义。

## 2. 当前状态解读

### 2.1 API 当前承担的内容

`services/api` 目前并不只是薄网关，而是还承担了较重的编排职责：

- 持久化用户消息和 assistant 消息。
- 读取 recent messages、conversation summary 和 long-term memories。
- 从 MySQL 加载带范围约束的知识和已审批情报。
- 为 diagnosis、learning、transition 组装 worker 请求。
- 落库 worker 返回的 memory candidate 与滚动摘要。

代表性代码路径包括：

- `DiagnosisService`
- `LearningService`
- `ConversationSummaryStore`
- `UserMemoryStore`

### 2.2 AI worker 当前承担的内容

`services/ai-worker` 已经承载了大部分 AI 执行逻辑：

- 关键词检索与向量检索
- 混合重排
- 上下文压缩
- memory context 组装
- diagnosis 推理
- learning 推理
- memory candidate 提取

代表性代码路径包括：

- `app/rag.py`
- `app/context_compressor.py`
- `app/agent.py`
- `app/learning_agent.py`
- `app/memory.py`
- `app/vector_store.py`

## 3. 为什么控制面建议继续放在 API

推荐将控制面继续放在 `services/api`，而不是迁到 `services/ai-worker`。

### 3.1 原因

- 鉴权、RBAC、会员等级、地域和行业边界天然已经落在 API 层。
- 会话状态、消息持久化、摘要持久化和长期记忆记录本质上是业务数据，而不只是 prompt 上下文。
- API 是维护前端契约、SSE 返回形态和显式失败映射的自然位置。
- 把最终写路径留在一个服务里，更利于幂等、审计和事故排查。

### 3.2 如果控制面迁到 worker，会发生什么

如果控制面迁到 `services/ai-worker`，worker 将不再只是推理服务，而会变成主业务服务。那就意味着 Python 侧要承担：

- 涉及权限的数据裁决
- 多步骤写入顺序控制
- 幂等与重试语义
- 部分失败后的补偿
- 当前以 API 为中心的审计与链路追踪要求

这条路并非不可行，但并不符合 BizSage 目前的架构阶段与风险偏好。

## 4. 当前 RAG 边界存在的问题

### 4.1 API 组装了过多 RAG 内容

API 当前会在在线 diagnosis 和 learning 路径中加载并转发较大的 `knowledge` 内容包给 worker。这让 API 过深参与了 RAG 内容装配，而不只是负责范围控制与编排。

### 4.2 worker 仍持有部分业务过滤语义

worker 当前会在检索代码内部应用 region、industry 和 entitlement 过滤。worker 仍应执行输入范围，但这些范围的语义应来自 API 契约，而不是在 Python 里演化成一套独立的业务策略。

### 4.3 知识源模型是混合的

BizSage 已经拥有基于 Qdrant 的 worker 检索路径，但 API 的在线请求行为仍像是必须把知识内容整包内联传给 worker。这会让人难以判断在线 RAG 到底主要基于：

- API 提供的内容批次
- worker 自有的检索索引
- 还是两者并存且职责不清

## 5. 推荐的边界模型

### 5.1 建议保留在 API 的职责

- 鉴权与授权
- 会话归属与状态
- 长期记忆的真相源裁决
- message、summary、memory 的最终持久化
- 面向前端的统一响应和 SSE 行为
- worker 健康状态透出与失败映射
- 知识发布、删除与同步治理

### 5.2 建议强化到 worker 的职责

- query understanding
- 词法检索
- 向量检索
- 混合重排
- 证据充分性判断
- 上下文压缩
- 带记忆的 prompt 组装
- 结构化推理输出
- memory candidate 提取与推理建议生成

### 5.3 推荐的契约方向

在线 API 到 worker 的契约应逐步收敛为：

- `question`
- `recent_messages`
- `conversation_summary`
- `long_term_memories`
- `region_id`
- `industry_id`
- `membership_level`
- `knowledge_filters` 或 `allowed_knowledge_scope`
- 可选的 `conflict_labels`

默认在线主路径应逐步停止传输大体量内联 `knowledge` 内容包，除非属于以下场景：

- 测试
- 可控降级模式
- 特殊调试或回放流程

## 6. 推荐的迁移阶段

### 第一阶段：稳定边界契约

- 保持 API 控制面不变。
- 明确文档：API 下发的 scope 是权威输入。
- 让 worker 将 region、industry、entitlement 等视为契约输入，而不是本地自定义业务策略。
- 继续由 API 治理知识同步。

### 第二阶段：减少 API 侧在线 RAG 组装

- 停止把内联 `knowledge` payload 作为默认在线检索输入源。
- 优先把 worker 自有检索索引作为在线检索底座。
- API 继续负责裁定 worker 被允许访问的范围。

### 第三阶段：让 worker 检索成为标准路径

- 让 worker 主要从自己的索引存储中完成检索。
- API 继续保留发布、下架和全量重同步触发职责。
- 为 MySQL 到 Qdrant 的一致性增加发布后、删除后校验。

### 第四阶段：清理重复规则

- 清理 API 已是权威源时仍在其他位置重复定义的 memory 与 retrieval 规则。
- 对分类、生命周期语义和 confidence 相关契约保持单点权威定义。

## 7. 这次重构的收益

- 模块边界更清晰。
- API 到 worker 的请求形态更小、更稳定。
- AI worker 内的检索与推理可更快迭代。
- Java/Python 规则漂移风险更低。
- 对运维和研发来说，系统心智模型更清楚：API 负责控制，worker 负责推理。

## 8. 主要风险与护栏

### 风险

- 如果同步治理薄弱，Qdrant 与 MySQL 可能漂移。
- 如果 scope 契约不明确，worker 检索可能越界。
- 从“内联知识”迁移到“worker 自主检索”的过程中，在线行为可能回归。

### 护栏

- 增加 scope 透传契约测试。
- 增加发布、删除、重同步、检索可见性的集成测试。
- 保留显式的 worker 健康状态与上游 LLM 失败透出。
- message、summary、memory 的最终落库裁决继续留在 API。

## 9. 首批建议工作项

近期性价比最高的任务是：

1. 定义并冻结在线请求最小 API-to-worker RAG 契约。
2. 从默认 diagnosis 和 learning 路径中减少或移除内联 `knowledge` payload。
3. 增加 `scope -> retrieval -> answer -> persistence` 的端到端回归覆盖。
4. 审计 Java 与 Python 两侧重复的 memory 与 retrieval 规则，并为每条规则确定唯一权威端。

## 10. 决策摘要

推荐的 BizSage 方向是：

- 控制面继续放在 `services/api`。
- 将 `services/ai-worker` 强化为完整的 RAG 与推理执行面。
- 逐步退出 API 重度参与的在线 RAG 内容装配。
- 将权威裁决、持久化和面向前端的控制持续保留在 API。
