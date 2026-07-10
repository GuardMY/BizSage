# BizSage 核心代码文件与外部交互

本文档是 `core-code-files-and-external-interactions.md` 的中文对应版本，用于说明 BizSage 各模块核心代码文件的职责、它们调用的内部模块，以及它们与外部系统的交互边界。

## 1. 范围与阅读规则

本文档覆盖仓库中的全部运行时模块：

- `apps/web`
- `apps/android`
- `services/api`
- `services/ai-worker`
- `services/collector`
- `infra`

为保证可读性，本文中的“核心代码文件”指满足以下一种或多种特征的文件：

- 运行入口
- API 控制器或路由处理器
- 编排服务
- 存储或同步边界
- 管理后台/运维后台入口
- 外部服务适配层
- 部署与反向代理编排文件

简单 DTO、Mapper、实体定义和测试文件默认不逐个展开，除非它们定义了重要的运行时契约。

## 2. 运行边界总览

```text
浏览器
  -> Nginx
    -> Web (Next.js)
    -> API (Spring Boot)

Web
  -> 只调用 API

API
  -> MySQL
  -> Redis
  -> AI Worker
  -> Collector（管理后台采集编排路径）

AI Worker
  -> Qdrant
  -> OpenAI-compatible LLM 提供方

Collector
  -> Redis
  -> 公共网页 / 第三方 API 载荷 / 表单载荷
```

## 3. 按模块拆解核心文件

### 3.1 `apps/web`

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `apps/web/app/page.tsx` | 用户主工作台页面。管理登录状态、语言切换、会话选择、诊断提交、报告生成、归档/删除操作与功能区切换。 | 调用 `lib/api-client.ts`；渲染多个工作台组件。 | 通过 Web API 客户端向 `/api/**` 发请求。 |
| `apps/web/lib/api-client.ts` | 浏览器侧唯一 API 边界。定义响应类型、SSE 解析、鉴权异常处理、管理后台 API 帮助方法与下载方法。 | 被所有用户页/管理页复用。 | 只调用 Spring Boot API；处理 JSON 与 SSE 流。 |
| `apps/web/lib/conversation-workspace.ts` | 纯状态辅助逻辑。负责活跃/归档会话拆分，以及归档/删除后的选中项回退策略。 | 被 `app/page.tsx` 与相关测试使用。 | 无直接外部交互。 |
| `apps/web/lib/markdown.tsx` | 助手回答的 Markdown 渲染封装。 | 被诊断区与归档消息视图使用。 | 无直接外部交互。 |
| `apps/web/app/components/workspace-shell.tsx` | 用户工作台公共壳层，包含左侧导航、顶部状态栏、身份信息与退出/切换语言操作。 | 包裹诊断、情报、用户、归档各区块。 | 无直接外部交互。 |
| `apps/web/app/components/conversation-sidebar.tsx` | 会话列表 UI，负责活跃/归档分区、行级动作与选择交互。 | 接收 `app/page.tsx` 传入的回调。 | 无直接外部交互。 |
| `apps/web/app/components/diagnosis-workspace.tsx` | 主诊断对话面板。负责渲染会话历史、SSE 增量回答、来源弹窗、付费情报侧栏与报告生成入口。 | 消费 `app/page.tsx` 提供的数据。 | 无直接外部交互；负责可视化 API 返回结果。 |
| `apps/web/app/components/archive-workspace.tsx` | 已归档会话的只读消息视图。 | 被 `app/page.tsx` 使用。 | 无直接外部交互。 |
| `apps/web/app/admin/page.tsx` | Admin V3 主控制台。管理后台登录恢复、区块路由、仪表盘刷新、采集配置编辑、知识审核流、告警/工单动作与治理视图。 | 调用 `lib/api-client.ts` 中的后台方法；渲染各子视图。 | 调用 `/api/admin/**`、`/api/ops/**` 等后台接口。 |
| `apps/web/app/admin/dashboard-view.tsx` | 管理后台仪表盘展示层，用于展示指标、告警、工单与审核队列。 | 作为管理页子视图被渲染。 | 无直接外部交互。 |
| `apps/web/app/admin/collection-workspace.tsx` | 后台采集源配置与运行面板，负责 source 配置、关键词、最近运行记录与死信展示。 | 被管理页使用。 | 展示并修改 API 持久化的采集配置。 |
| `apps/web/app/admin/governance-views.tsx` | 后台治理视图，负责冲突记录、虚假信息台账与快照页面。 | 被管理页使用。 | 通过 API 客户端读取治理接口。 |
| `apps/web/app/admin/monitoring-view.tsx` | 运维监控视图，展示 ops 指标、缓存统计、SLA 与工单类数据。 | 被管理页使用。 | 通过 API 客户端读取 `/api/ops/**`。 |
| `apps/web/app/hooks/useAuth.ts` 与 `useConversations.ts` | 可复用的认证与会话状态 Hook。 | 可供多个客户端组件复用。 | 可能间接调用 API 帮助方法。 |

补充说明：

- Web 不会直接调用 worker、Redis、MySQL、Qdrant 或 collector。
- SSE 面向用户的流式解析集中在 `lib/api-client.ts`，渐进渲染集中在 `diagnosis-workspace.tsx`。

### 3.2 `apps/android`

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `apps/android/README.md` | 未来 Android 客户端的占位说明与接口兼容约定。 | 无。 | 说明未来会复用现有认证、会话、流式消息、情报与来源展示接口。 |

补充说明：

- 当前没有 Android 运行时代码。
- 现有后端契约已经按未来 Android 复用同一套 API 的方向设计。

### 3.3 `services/api`

#### 入口与平台层

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/api/src/main/java/com/bizsage/api/BizSageApiApplication.java` | Spring Boot 启动入口；开启定时任务与 MyBatis Mapper 扫描。 | 启动整个 API 模块。 | 启动 HTTP 服务与定时任务。 |
| `services/api/src/main/resources/application.yml` | 统一配置端口、MySQL、Redis 缓存、JWT、隐私密钥、灰度、告警阈值、记忆同步与 actuator。 | 配置全部 Spring 模块。 | 定义 API 到 MySQL、Redis、AI worker 的连接。 |
| `services/api/src/main/java/com/bizsage/api/common/ApiResponse.java` | 统一 API 信封结构。 | 被几乎全部控制器使用。 | 统一所有返回给 Web 的 JSON 格式。 |
| `services/api/src/main/java/com/bizsage/api/common/ApiControllerAdvice.java` | 统一异常到信封响应的转换。 | 包裹控制器异常。 | 统一客户端可见错误结构。 |
| `services/api/src/main/java/com/bizsage/api/common/RequestIdFilter.java` | 为请求注入 requestId。 | 被所有请求路径复用。 | 让响应与日志都能做链路追踪。 |
| `services/api/src/main/java/com/bizsage/api/cache/CacheConfig.java` 与 `CacheMetrics.java` | Redis 缓存配置与命中/未命中遥测。 | 被会话与 ops 模块使用。 | 通过 Spring Cache 访问 Redis。 |

#### 认证、用户与访问控制

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/api/src/main/java/com/bizsage/api/auth/SecurityConfiguration.java` | 定义无状态安全策略、JWT 过滤器接入、CORS 与未授权/无权限处理。 | 使用 `JwtAuthenticationFilter`。 | 保护全部 API 请求。 |
| `services/api/src/main/java/com/bizsage/api/auth/AuthController.java` | 登录/退出接口。签发 JWT 并写入 `httpOnly` Cookie。 | 使用 `UserStore` 与 `JwtService`。 | 通过 HTTP 处理浏览器登录与登出。 |
| `services/api/src/main/java/com/bizsage/api/auth/JwtAuthenticationFilter.java` 与 `JwtService.java` | 从请求 Cookie/Header 中读取并校验 JWT，构建 Spring 安全主体。 | 被 Spring Security 使用。 | 校验浏览器会话令牌。 |
| `services/api/src/main/java/com/bizsage/api/users/UserController.java` 与 `UserStore.java` | 当前用户资料查询与用户持久化边界。 | 被认证与工作台控制器调用。 | 访问 MySQL 中的用户数据。 |
| `services/api/src/main/java/com/bizsage/api/governance/DataIsolationService.java` 与 `DataScope.java` | 负责地域/行业/会员范围控制与冻结账号拦截。 | 被会话、知识、情报等过滤逻辑使用。 | 在返回数据前执行数据范围约束。 |

#### 用户会话与诊断主链路

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/api/src/main/java/com/bizsage/api/conversations/ConversationController.java` | 创建/列出/归档/删除会话，并带有缓存与数据范围校验。 | 使用 `ConversationStore`、`UserStore`、`CacheMetrics` 与 `DataIsolationService`。 | 对 MySQL 中的会话数据读写。 |
| `services/api/src/main/java/com/bizsage/api/conversations/ConversationStore.java` | 会话 SQL 持久化边界。 | 被会话与消息链路使用。 | MySQL 读写边界。 |
| `services/api/src/main/java/com/bizsage/api/messages/MessageController.java` | 诊断、学习、模式切换三类 SSE 接口，以及消息历史列表接口。 | 使用 `DiagnosisService`、`LearningService`、`ConversationStore`、`ConversationMessageStore` 与 `UserStore`。 | 向 Web 返回 SSE 流。 |
| `services/api/src/main/java/com/bizsage/api/messages/DiagnosisService.java` | 诊断编排核心。持久化用户/助手消息，加载知识与记忆上下文，调用 AI worker，保存记忆候选，并推进会话摘要。 | 使用 `AiWorkerClient`、消息/摘要存储、`UserMemoryStore`、`KnowledgeStore` 与 `IntelligenceStore`。 | 调用 AI worker，并读写 MySQL。 |
| `services/api/src/main/java/com/bizsage/api/messages/LearningService.java` | 学习 Agent 与双 Agent 切换编排核心，持久化模式与诊断服务类似。 | 使用 `AiWorkerClient`、消息/摘要存储、记忆存储、知识存储与情报存储。 | 调用 AI worker，并读写 MySQL。 |
| `services/api/src/main/java/com/bizsage/api/messages/ConversationMessageStore.java` 与 `ConversationSummaryStore.java` | 原始消息与压缩会话摘要的持久化边界。 | 被诊断、学习、报告链路复用。 | MySQL 读写边界。 |

#### 知识、情报、记忆与 worker 同步

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/api/src/main/java/com/bizsage/api/knowledge/KnowledgeController.java` 与 `KnowledgeStore.java` | 面向用户的知识查询/元数据接口与知识持久化边界。 | 既可直接对外，也会被诊断/学习流程加载。 | 访问 MySQL 知识数据。 |
| `services/api/src/main/java/com/bizsage/api/intelligence/IntelligenceController.java` 与 `IntelligenceStore.java` | 情报查询与持久化路径。 | 给诊断上下文加载提供数据。 | 访问 MySQL 情报数据。 |
| `services/api/src/main/java/com/bizsage/api/intelligence/PaidIntelligenceController.java` 与 `PaidIntelligenceStore.java` | 面向会员等级的付费情报列表接口。 | 结合灰度与权益过滤。 | 将付费情报返回给 Web。 |
| `services/api/src/main/java/com/bizsage/api/memory/UserMemoryStore.java` | 用户记忆持久化、生命周期刷新与活跃记忆查询。 | 被诊断/学习链路和记忆同步调度使用。 | 访问 MySQL 记忆表。 |
| `services/api/src/main/java/com/bizsage/api/memory/UserMemoryEmbeddingStore.java` 与 `MemorySyncScheduler.java` | 非结构化记忆的向量同步暂存与定时推送逻辑。 | 使用 `AiWorkerClient`。 | 将记忆向量同步到 AI worker/Qdrant 路径。 |
| `services/api/src/main/java/com/bizsage/api/worker/AiWorkerClient.java` | API 到 AI worker 的主 HTTP 适配器。处理 diagnose、learn、transition、knowledge sync/delete、memory sync 与健康检查。 | 被诊断、学习、启动同步与记忆同步复用。 | 通过 HTTP 调用 FastAPI AI worker。 |
| `services/api/src/main/java/com/bizsage/api/worker/KnowledgeSyncInitializer.java` | 启动时从 MySQL 知识库向 worker/Qdrant 做全量重同步。 | 使用 `KnowledgeStore` 与 `AiWorkerClient`。 | 同步持久向量知识库。 |

#### 报告、灰度、隐私、健康与运维

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/api/src/main/java/com/bizsage/api/reports/DiagnosisReportController.java` | 诊断报告元数据接口与 PDF 下载接口。 | 使用 `DiagnosisReportService`、`PdfReportGenerator`、`UserStore` 与 `GrayReleaseService`。 | 返回 JSON 元数据与 PDF 二进制下载。 |
| `services/api/src/main/java/com/bizsage/api/reports/DiagnosisReportService.java` 与 `PdfReportGenerator.java` | 根据诊断上下文重建报告内容并渲染 PDF。 | 使用消息/知识上下文与 PDF 生成逻辑。 | 为 Web 下载路径生成报告数据。 |
| `services/api/src/main/java/com/bizsage/api/grayrelease/GrayReleaseService.java` 与 `GrayReleaseProperties.java` | 负责付费情报、PDF 导出、未来高级 RAG 功能的灰度控制。 | 被报告与商业能力复用。 | 控制不同用户可见的功能范围。 |
| `services/api/src/main/java/com/bizsage/api/privacy/PrivacyService.java` 与 `PrivacyConfiguration.java` | 用户私有经营数据的加密/脱敏支持。 | 在敏感经营数据存储或转换时使用。 | 在持久化前保护敏感数据。 |
| `services/api/src/main/java/com/bizsage/api/health/HealthController.java` | 轻量级 API 健康检查接口。 | 无。 | 被运维、反向代理和部署脚本使用。 |
| `services/api/src/main/java/com/bizsage/api/ops/OpsController.java`、`AlertRuleEngine.java`、`AlertScheduler.java`、`SlaService.java`、`SlaScheduler.java` 与 `SlaStore.java` | 运行指标、缓存统计、SLA 报表、告警评估与定时运维遥测。 | 使用 JDBC、缓存指标和 SLA 存储。 | 为管理后台和定时告警链路提供运维数据。 |

#### 管理后台、治理与采集控制面

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/api/src/main/java/com/bizsage/api/admin/AdminController.java` | Admin V3 主 API 面。覆盖仪表盘、告警、审计、审核队列、工单、人工情报、知识工作流、采集源配置、风控规则与治理台账。 | 使用 `AdminStore`、`AdminKnowledgeStore`、`AdminCollectionStore` 与 `ConflictStore`。 | 为 Web 管理后台提供数据与动作接口。 |
| `services/api/src/main/java/com/bizsage/api/admin/AdminStore.java` | 聚合后台仪表盘、告警、工单、审核、人工情报、风控规则与审计数据。 | 被 `AdminController` 使用。 | 主要是 MySQL 管理数据读写边界。 |
| `services/api/src/main/java/com/bizsage/api/admin/AdminKnowledgeStore.java` | 知识编辑工作流：草稿、提交审核、审批、发布、回滚、diff 与巡检。 | 被 `AdminController` 调用；并将发布结果同步回用户侧知识表。 | 写 MySQL 知识工作流表，并触发 worker 同步。 |
| `services/api/src/main/java/com/bizsage/api/admin/AdminCollectionStore.java` | 采集源配置、关键词、运行历史、死信与手动执行编排。 | 被 `AdminController` 调用；可能调用 `CollectorClient`。 | 将后台请求接到 collector 控制与持久化数据。 |
| `services/api/src/main/java/com/bizsage/api/admin/CollectorClient.java` | API/Admin 到 collector 的 HTTP 适配器。 | 被采集管理逻辑使用。 | 通过 HTTP 调用 collector。 |
| `services/api/src/main/java/com/bizsage/api/governance/ConflictStore.java` | 冲突记录与虚假信息台账的持久化/查询边界。 | 被后台治理接口使用。 | 访问 MySQL 治理表。 |
| `services/api/src/main/java/com/bizsage/api/governance/SnapshotController.java`、`SnapshotService.java`、`SnapshotScheduler.java` 与 `SnapshotStore.java` | 快照生成、列表、对比、清理与定时生成。 | 被后台快照页与调度器使用。 | 访问 MySQL 快照表。 |

#### Schema 与迁移

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql`、`V2__admin_schema_and_seed.sql`、`V3__user_memory_profile_uniqueness.sql` 与 `V4__user_preferred_locale.sql` | 幂等 Flyway 迁移，负责 MySQL 基线、管理后台 schema 与种子数据、记忆唯一性约束和用户语言偏好。 | 在 API 启动时加载。 | 作为唯一 schema 来源创建并升级 MySQL schema。 |

### 3.4 `services/ai-worker`

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/ai-worker/app/main.py` | FastAPI 入口。定义 health、RAG 搜索、诊断、学习、模式切换、知识同步/删除与记忆同步接口。 | 调用 `agent.py`、`learning_agent.py`、`agent_transition.py`、`rag.py` 与 `vector_store.py`。 | 与 API 层、Qdrant 交互。 |
| `services/ai-worker/app/agent.py` | 诊断 Agent 核心。完成冲突预检查、RAG 检索、上下文压缩、Prompt 组装、模型路由、自检重试与记忆候选抽取。 | 使用 `rag.py`、`memory.py`、`context_compressor.py`、`prompt_library`、`model_routing` 与 `reasoning_checks`。 | 通过路由后的模型客户端调用外部 LLM。 |
| `services/ai-worker/app/learning_agent.py` | 学习 Agent 核心。负责意图识别、链路节点聚焦、证据压缩、学习型 Prompt 构建与结构化输出。 | 使用 `rag.py`、`memory.py`、`agent_output.py`、`prompt_library` 与 `reasoning_checks`。 | 通过模型路由调用外部 LLM。 |
| `services/ai-worker/app/agent_transition.py` | 双 Agent 模式切换器。构建切换感知 Prompt，保留摘要与记忆，并路由到目标 Agent。 | 使用 `agent.py`、`learning_agent.py` 与 `memory.py`。 | 除目标 Agent 发起的 LLM 调用外，无额外外部交互。 |
| `services/ai-worker/app/rag.py` | 知识检索引擎。定义 `KnowledgeItem`、本地词法检索、向量检索融合、业务过滤与六维重排质量评分。 | 被诊断、学习与 `/rag/search` 复用。 | 使用 embeddings，并可走 Qdrant 向量检索。 |
| `services/ai-worker/app/vector_store.py` | Qdrant 薄适配层，负责知识向量 upsert 与搜索。 | 被 `main.py` 与 `rag.py` 使用。 | 通过客户端访问 Qdrant。 |
| `services/ai-worker/app/memory.py` | 三层记忆上下文构建、LLM/正则记忆抽取、自动遗忘、向量同步判断与记忆合并。 | 被诊断、学习与模式切换复用。 | 记忆抽取时会间接通过模型路由调用 LLM。 |
| `services/ai-worker/app/llm.py` | OpenAI-compatible 提供方配置封装与旧兼容方法。 | 被模型路由和旧封装路径间接使用。 | 读取提供方环境变量并访问外部 LLM 接口。 |
| `services/ai-worker/app/context_compressor.py` | 将检索出的证据压缩到模型上下文预算内。 | 被诊断与学习流程调用。 | 无直接外部交互。 |
| `services/ai-worker/app/model_routing/router.py`、`models.py` 与 `providers.py` | 根据任务类型选择模型/提供方并执行调用。 | 被诊断、学习、记忆抽取与自检重试使用。 | 调用外部 OpenAI-compatible 模型服务。 |
| `services/ai-worker/app/prompt_library/assembler.py`、`layers.py` 与 `defaults.py` | 负责诊断/学习模式的分层 Prompt 组装。 | 被诊断与学习流程使用。 | 无直接外部交互。 |
| `services/ai-worker/app/reasoning_checks/checks.py` 与 `retry.py` | 生成后自检与重试策略。 | 被诊断与学习流程使用。 | 除重复模型调用外，无其他外部交互。 |
| `services/ai-worker/app/embeddings.py` | 用于本地/向量检索和记忆同步的确定性向量生成。 | 被 `rag.py`、`vector_store.py` 与 `main.py` 使用。 | 支撑 Qdrant 向量写入与搜索。 |
| `services/ai-worker/app/agent_output.py` | 学习/诊断响应的结构化输出格式化。 | 主要被学习 Agent 与模式切换路径使用。 | 无直接外部交互。 |

补充说明：

- API 层已把 AI worker 当作诊断、学习与模式切换的唯一推理引擎。
- 当请求直接携带知识集合时，`main.py` 会创建临时 Qdrant collection；启动/后台同步则使用持久 collection。

### 3.5 `services/collector`

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `services/collector/app/main.py` | FastAPI 入口，提供表单采集、公共页面采集、mock API 采集、治理与冲突检测接口。 | 调用 `collectors.py`、`governance.py`、`conflict_engine.py`、`redis_state.py` 与 `resilience.py`。 | 访问 Redis，并接收上游源数据载荷。 |
| `services/collector/app/collectors.py` | 表单、Excel、公共网页和 mock API 的原始记录抽取与归一化。 | 被 `main.py` 使用。 | 解析浏览器/后台提交的数据和类第三方载荷。 |
| `services/collector/app/governance.py` | 第一阶段治理：字段归一化、谣言词过滤、URL 去重、SimHash 去重与固定权重赋值。 | 被 `main.py` 和 `conflict_engine.py` 使用。 | 无直接外部交互。 |
| `services/collector/app/conflict_engine.py` | 五分支冲突分类器，在打标签、复核工单、告警、知识更新和虚假信息台账之间做路由判断。 | 被治理接口调用。 | 无直接外部交互；向调用方返回治理结论。 |
| `services/collector/app/resilience.py` | 增量指纹、重试退避、死信分类、熔断与带快照回退的 vendor failover。 | 被 `main.py` 使用。 | 在可用时依赖 Redis 状态。 |
| `services/collector/app/redis_state.py` | Redis 状态存储，保存去重指纹、最近快照、通用键值和计数器。 | 被采集韧性、API 缓存、代理池和 vendor registry 复用。 | 访问 Redis。 |
| `services/collector/app/api_cache.py` | 第三方 API 响应缓存管理器，包含 hit/miss 统计与按源类型 TTL 行为。 | 作为可选采集支持模块使用。 | 访问 Redis。 |
| `services/collector/app/vendor_registry.py` | 第三方 API 多 vendor 注册表，带健康度、降级和成本统计。 | 作为可选采集支持模块使用。 | 使用 Redis 状态，并建模外部 API vendor。 |
| `services/collector/app/proxy_pool.py` | 代理轮换管理器，带冷却时间与轮询选择。 | 作为类爬虫采集路径的支持模块使用。 | 使用 Redis 状态，并建模外部代理服务。 |

补充说明：

- 当前 collector 主要把归一化/治理后的 records 返回给调用方；自动写入 MySQL/Qdrant 的全闭环更多还是由 collector 之外的流程编排。
- Collector 对 Redis 的使用主要集中在去重指纹、快照回退和支持性状态持久化。

### 3.6 `infra`

| 文件 | 主要职责 | 内部调用 | 外部交互 |
|------|----------|----------|----------|
| `infra/docker-compose.yml` | 类生产拓扑，只编排 BizSage 自有服务和 Nginx，中间件由外部提供。 | 启动 API、AI worker、collector、Web 与 Nginx 容器。 | 定义应用服务容器网络与外部中间件端点。 |
| `infra/docker-compose-all.yml` | 完整本地编排变体，包含 MySQL、Redis、Qdrant、API、AI worker、collector、Web 与 Nginx。 | 启动本地基础设施和应用容器。 | 定义本地容器网络、依赖关系和中间件持久化卷。 |
| `infra/nginx/nginx.conf` | 反向代理入口。将 `/api/` 路由到 Spring Boot，将 `/` 路由到 Next.js，并为 SSE 关闭缓冲。 | 位于 Web 与 API 前方。 | 提供浏览器统一入口。 |
| `infra/scripts/start-local.ps1` 与 `start-all.ps1` | 本地基础设施或整栈启动脚本。 | 封装 Docker Compose 命令。 | 通过 PowerShell 启动本地依赖。 |
| `infra/scripts/health-check.ps1` | 本地环境健康检查脚本。 | 在启动后探测服务。 | 检查运行时接口与依赖健康状态。 |
| `infra/scripts/backup-*.ps1` 与 `restore-*.ps1` | MySQL 与 Qdrant 的备份/恢复辅助脚本。 | 被运维人员使用。 | 在恢复流程中操作持久化后端。 |

## 4. 外部交互流程

### 4.1 浏览器诊断流程

```text
浏览器
  -> apps/web/app/page.tsx
  -> apps/web/lib/api-client.ts
  -> POST /api/conversations/{id}/messages/stream
  -> MessageController
  -> DiagnosisService
  -> AiWorkerClient
  -> AI Worker /agent/diagnose
  -> rag.py + agent.py + model routing
  -> Qdrant + 外部 LLM
  -> API SSE 帧
  -> Web 渐进渲染
```

### 4.2 浏览器学习与双 Agent 切换流程

```text
浏览器
  -> apps/web/lib/api-client.ts
  -> /messages/learn/stream 或 /messages/transition/stream
  -> MessageController
  -> LearningService
  -> AiWorkerClient
  -> AI Worker /agent/learn 或 /agent/transition
  -> learning_agent.py 或 agent_transition.py
  -> Qdrant + 外部 LLM
  -> SSE 返回 Web
```

### 4.3 报告导出流程

```text
Web 报告按钮
  -> GET /api/reports/diagnosis
  -> DiagnosisReportController / DiagnosisReportService
  -> 返回报告元数据 JSON

Web PDF 下载
  -> GET /api/reports/diagnosis/pdf
  -> GrayReleaseService 灰度校验
  -> PdfReportGenerator
  -> 返回 PDF 二进制
```

### 4.4 管理后台采集与治理流程

```text
后台页面
  -> apps/web/app/admin/page.tsx
  -> apps/web/lib/api-client.ts
  -> /api/admin/collection/** 与 /api/admin/governance/**
  -> AdminController
  -> AdminCollectionStore / ConflictStore / SnapshotStore
  -> 可选 CollectorClient -> collector endpoints
  -> MySQL 持久化 + collector 响应
  -> 后台界面刷新
```

### 4.5 知识发布与向量同步流程

```text
后台知识发布
  -> /api/admin/knowledge/**
  -> AdminKnowledgeStore
  -> 写入知识工作流表
  -> 同步发布结果到 knowledge_items
  -> AiWorkerClient.syncKnowledge()/syncAllKnowledge()
  -> AI Worker /knowledge/sync
  -> Qdrant 持久 collection
```

### 4.6 采集韧性与去重流程

```text
源载荷 / 公共页面 / mock API 数据
  -> collector main.py
  -> collectors.py 归一化
  -> resilience.py 指纹/快照处理
  -> redis_state.py
  -> governance.py
  -> conflict_engine.py（可选）
  -> 返回治理后的 records
```

## 5. 实际阅读顺序建议

如果是新同学，希望用最短路径理解代码库，建议顺序如下：

1. 先读 `README.md`。
2. 再读本文档和 `docs/zh-CN/component-interactions-and-data-flows-zh-CN.md`。
3. 然后沿运行入口顺序读：
   - `apps/web/app/page.tsx`
   - `services/api/.../MessageController.java`
   - `services/api/.../DiagnosisService.java`
   - `services/api/.../worker/AiWorkerClient.java`
   - `services/ai-worker/app/main.py`
   - `services/ai-worker/app/agent.py`
   - `services/collector/app/main.py`
   - `infra/docker-compose.yml`

## 来源说明

本文档基于 `2026-07-09` 的仓库检查整理，来源包括：

- `.codegraph/codegraph.db`
- `apps/web`、`services/api`、`services/ai-worker`、`services/collector`、`infra` 下的运行时代码
- 既有架构文档，重点包括 `docs/zh-CN/component-interactions-and-data-flows-zh-CN.md` 与 `docs/zh-CN/system-architecture-and-framework-zh-CN.md`
