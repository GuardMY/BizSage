# 变更日志

## 2026-07-11

### Learning 与 Diagnosis 引导式工作流 UI

- 变更类型：功能性前端工作流更新。
- 影响模块：`apps/web` 和两份变更日志。
- 主要变更：
  - 新增独立的 Learning 工作区入口，首轮显示固定自我介绍，并在右侧 rail 中按“下一节点 / 当前细分块 / 延展方向”三类展示。
  - 更新 Diagnosis 工作区，首轮显示固定自我介绍与开场 draft plan，并在右侧 rail 中按“业务问题 / 缺失画像字段 / 高影响细节”三类展示。
  - 保持原有诊断流与推荐流不变，同时让新的 Learning 流走同一套会话壳体。
- 验证结果：
  - 已在 `apps/web` 运行 `npm test`，41 个前端测试全部通过。
  - 确认共享工作区壳体、双语文案以及新的 Learning/Diagnosis 组件在当前 UI 测试套件下可正常编译与运行。
- 未完成事项：
  - 当前 rail 分组仍依赖前端对现有推荐项的启发式分类；如果后端补充更明确的分组信号，准确度会更高。

### MySQL V2 conversations 迁移兼容性修复

- 变更类型：功能性数据库迁移修复。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 将 MySQL V2 中 `ALTER TABLE conversations ADD COLUMN IF NOT EXISTS ...` 的批量写法改为基于 `information_schema.columns` 的逐列幂等判断。
  - 保留学习与诊断会话字段以及种子数据不变，同时让迁移能够兼容当前 MySQL 解析器。
- 验证结果：
  - 已从 Flyway 启动堆栈中确认失败 SQL 片段，并定位到 `services/api/src/main/resources/db/migration/mysql/V2__learning_diagnosis_guided_workflow.sql`。
  - 已检查 `V1__baseline.sql` 中的 MySQL 基线 schema，确认 `conversations` 表尚未定义这些工作流新字段。
  - 已对更新后的迁移脚本做静态复核，确认列级幂等保护逻辑完整。
- 未完成事项：
  - 仍需在真实 MySQL 环境执行一次 Flyway，确认应用能端到端正常启动。

### 将学习诊断工作流合并进 V1 基线

- 变更类型：数据库迁移合并。
- 影响模块：`services/api`、H2 测试 schema 和两份变更日志。
- 主要变更：
  - 将学习与诊断工作流需要的 `conversations` 字段，以及 `question_pools` 表和种子数据合并进 MySQL `V1__baseline.sql`。
  - 将同一套工作流 schema 与种子数据同步合并进 H2 `V1__baseline.sql` 测试基线。
  - 删除独立的 `V2__learning_diagnosis_guided_workflow.sql` 迁移文件，并移除额外的 H2 schema 加载引用，让新环境只通过单一基线完成初始化。
- 验证结果：
  - 已确认当前仓库在这条工作流变更上只面向全新数据库。
  - 已核对 MySQL 与 H2 基线都在运行时代码使用前定义了工作流字段和 `question_pools`。
  - 已确认 `application-test.yml` 现在只加载 H2 的 `V1__baseline.sql` 基线。
- 未完成事项：
  - 仍需执行一次全新 MySQL 启动和相关 API 测试，确认合并后的基线端到端可用。

### API DiagnoseResponse 测试构造器修复

- 变更类型：功能测试修复。
- 影响模块：`services/api`，以及两份变更日志。
- 主要变更：
  - 更新 API 测试夹具，使其与当前 `DiagnoseResponse` record 签名保持一致，适配 worker 响应契约新增字段后的变化。
  - 在 mocked response 中补齐新的 `mode`、`chainNodeId`、`sections`、`recommendationCandidates`、`currentTopic`、`nextBestTopics`、`workflowStage`、`profileMissingFields`、`completionSignal` 和 `recommendedQuestions` 字段。
- 验证结果：
  - 已确认失败来自 `mvn package -DskipTests -B -Dmaven.artifact.threads=10` 的 `testCompile` 阶段，而不是 Docker 分层本身。
  - 本次修改后仍需执行完整重建验证。
- 未完成事项：
  - 仍需运行完整 API 测试和 Docker 重建，确认修复端到端生效。

### Prompt 作用阶段归档

- 变更类型：文档归档。
- 影响模块：`services/ai-worker`、配对 prompt 归档文档和两份变更日志。
- 主要变更：
  - 新增一份双语 prompt 归档文档，按模块整理仓库中真实生效的业务 prompt，不再把运行时 prompt 与 UI 类名或测试占位字符串混在一起。
  - 归档了分层 system prompt、诊断生成 prompt、学习生成 prompt、双 Agent 切换 prompt、回合后记忆提炼 prompt，以及已废弃的遗留兼容 prompt 包装层。
  - 补充说明每个 prompt 入口在运行链路中的位置、组装输入和职责，便于从系统约束注入一直追到生成阶段与回合后提炼阶段。
- 验证结果：
  - 读取相关调用链前已检查当前 `.codegraph/codegraph.db`，并借助索引结构确认实际承载 prompt 的运行时模块。
  - 已将归档内容与 `services/ai-worker/app/prompt_library`、`agent.py`、`learning_agent.py`、`agent_transition.py`、`memory.py` 和 `llm.py` 逐一核对，并排除了 Web 与测试文件中只是“像 prompt”的非运行时字符串。
  - 已确认中英文文档覆盖相同的 prompt 范围、模块分组、阶段定义和排除项。
- 未完成事项：
  - 本次仅为文档归档，未执行运行时测试套件。

### MySQL 全量数据表目录归档

- 变更类型：数据库文档归档。
- 影响模块：配对数据库文档和两份变更日志。
- 主要变更：
  - 将 V1 数据库文档从不完整的核心表清单扩展为按业务域分组的目录，覆盖合并后 MySQL Flyway 基线中的全部 30 张应用数据表。
  - 记录每张表的用途、逻辑关联、重要唯一/索引控制，以及相关生命周期或保留期行为。
  - 明确应用 ID 关联不是数据库强制外键，公共字段属于约定而非每张表的必备列，Flyway 自管的 `flyway_schema_history` 不计入 30 张应用表。
- 验证结果：
  - 检查 Flyway 与文档路径前已使用当前 CodeGraph 索引，并确认索引为最新状态。
  - 将目录表名与 `V1__baseline.sql` 中全部 `CREATE TABLE IF NOT EXISTS` 语句逐一比对；中英文版本均完整覆盖 30 张应用表。
  - 已确认中英文目录描述相同的范围、关联、控制、迁移行为和未完成事项。
- 未完成事项：
  - 未查询真实 MySQL 元数据；本目录反映仓库内权威 Flyway 基线，不涵盖具体环境中的 schema 漂移。

### Agent 端到端真实流式输出

- 变更类型：功能开发与流式协议更新。
- 影响模块：`services/ai-worker`、`services/api`、`apps/web`、配对 API/架构文档和两份变更日志。
- 主要变更：
  - 为诊断、学习和双 Agent 模式切换新增 OpenAI-compatible 模型真实流式调用，覆盖仅回答正文的增量解析、提供方故障转移和通过关闭流实现的上游取消。
  - 新增流式自检语义：候选完成后执行验证，失败候选在重试前发送 `reset`，重试耗尽后替换为受控的信息存疑文案，并且只有最终结果会持久化。
  - 新增 AI Worker SSE 端点和 Java SSE 消费逻辑，同时保留 PDF/报告兼容所需的同步 Agent 端点。
  - 移除 API 按 24 字符生成快照的模拟流，改为即时转发 `status`/`delta`/`reset`、发送唯一最终 `diagnosis`，补充无缓存/无缓冲响应头，并让三条 Agent 链路复用最终持久化逻辑。
  - 重构 Web 流解码器，使其支持跨 chunk 帧、拆分的 UTF-8 字符、delta 追加和 reset 清空，并显示中英文重试状态。
- 验证结果：
  - 修改相关调用链前已同步并检查 CodeGraph。
  - 已通过 `python -m pytest` 验证 AI Worker 的流式、路由、重试、诊断、学习和切换测试，所选 76 项全部通过。完整套件为 169 项通过、2 项既有非相关失败，分别涉及过时的 `generate_answer` mock 和向量 payload 预期。
  - 已通过 `MessageStreamApiTest` 和 `AiWorkerClientStreamTest` 验证 API 流式链路，9 项测试全部通过；其中延迟本地 HTTP 测试确认最终结果完成前即可收到 delta。
  - 已在 `apps/web` 执行 `npm test`（40/40 通过）和 `npm run build`；分片 SSE 解码测试与 Next.js 生产构建均通过。
- 未完成事项：
  - 当前环境未执行真实外部模型加 Docker/Nginx 的端到端时延演练；提供方行为由确定性流式测试替身验证，API 传输由延迟本地 HTTP 服务验证。
  - 完整 AI Worker 套件中 2 项与本次无关的既有失败仍需单独维护测试。

### 诊断输入框初始草稿置空

- 变更类型：功能修复。
- 影响模块：`apps/web` 和两份变更日志。
- 主要变更：
  - 将诊断对话输入框的草稿状态初始值改为空字符串，避免出现可直接提交的默认文本。
  - 移除 web 页面消息契约中不再使用的中英文默认问题字段。
  - 新增前端源码断言，验证诊断输入框初始为空且不再定义 `defaultQuestion`。
- 验证结果：
  - 修改前已使用 CodeGraph 定位诊断工作区和页面状态路径。
  - 已在 `apps/web` 执行 `npm test`；39 个前端测试全部通过。
  - 已在 `apps/web` 执行 `npm run build`；通过 `npm ci` 安装 lockfile 依赖后，Next.js 生产构建通过。
- 未完成事项：
  - 无。

## 2026-07-10

### 合并 Flyway V1 与 admin 国际化隔离

- 变更类型：数据库迁移合并、前端国际化、文档与测试维护。
- 影响模块：`services/api`、`apps/web`、`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 将源 Flyway 迁移合并到 `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql`，并删除面向可重置环境已不再需要的 MySQL `V2` 至 `V4` 迁移文件。
  - 将 H2 的 admin 种子数据和 SLA 初始化合入 `services/api/src/test/resources/db/migration/h2/V1__baseline.sql`，并将测试 profile 改为只加载 H2 V1 基线。
  - 新增 admin 枚举/状态/code 标签的 i18n helper，并将 admin 仪表盘、采集、监控、治理、知识、告警、审计、复核、工单、人工情报和风控规则视图接入本地化显示文案。
  - 保持提交给 API 的 payload 值稳定，同时翻译界面展示的枚举/状态、权益标签、来源类型、风控规则类别，以及 `general`、`cn-default`、`sales-payment` 等常见业务 code。
  - 更新源码地图文档，说明当前 MySQL Flyway 只保留合并后的 V1 基线。
  - 更新前端源码断言测试，使其匹配当前用户资料恢复流程和扩展后的 admin 工作台页面集合。
- 验证结果：
  - 修改前已使用 CodeGraph 检查相关路径，源码修改后已同步 CodeGraph。
  - 已在 `apps/web` 执行 `npm run build`；Next.js 生产构建通过。
  - 已在 `apps/web` 执行 `npm test`；38 个前端测试全部通过。
  - 已在 `services/api` 执行 `mvn test`；合并后的 H2 V1 基线下 38 个 API 测试全部通过。
- 未完成事项：
  - 当前环境未对全新重置的真实 MySQL 数据库执行 Flyway 实机验证。

### Flyway 托管的 MySQL 基线与幂等迁移

- 变更类型：数据库迁移与部署配置调整。
- 影响模块：`services/api`、`infra`、`.env.example`、AGENTS 规范、数据库/部署文档和两份变更日志。
- 主要变更：
  - 将 MySQL V1 基线迁入 `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql`，由 Flyway 统一负责 MySQL schema 创建与升级。
  - 删除并行的 Docker MySQL 初始化 schema，并取消全量 compose 中对 `infra/mysql/init` 的 MySQL 初始化挂载。
  - 在应用配置、`.env.example` 和两份 compose 的 API 环境变量中默认启用 Flyway，使全新数据库在 API 启动时自动执行 `V1` 到最新迁移。
  - 将 `V4__user_preferred_locale.sql` 改为仅在 `users.preferred_locale` 缺失时才补充字段。
  - 为 V1 种子数据补充 `WHERE NOT EXISTS` 保护条件，使 SQL 被检查或手工重跑时保持幂等。
  - 在开发规范中新增要求：Flyway SQL 迁移必须保持幂等，且不得再用并行 Docker/MySQL 初始化脚本重复 Flyway 管理的 schema。
- 验证结果：
  - 修改前已使用 CodeGraph 定位 Flyway、数据库配置和部署路径。
  - 已在 `services/api` 执行 `mvn -DskipTests clean compile`；编译通过。
  - 已执行 `mvn test`；测试套件未通过，失败集中在既有 `MessageStreamApiTest` 的滚动摘要、记忆刷新断言，以及两处 `ConcurrentModification` 错误。失败测试使用 H2 test profile，不会执行本次修改的 MySQL Flyway 迁移路径。
- 未完成事项：
  - 当前环境未安装 Docker CLI 和可用 MySQL 客户端，因此仍需在具备 Docker 的环境中执行 `docker compose -f infra/docker-compose-all.yml up -d` 并验证真实 MySQL Flyway 执行。
  - 已经记录 `V4__user_preferred_locale.sql` 失败历史或旧成功 checksum 的数据库，需要先重置失败记录或执行 Flyway repair 后再用更新后的迁移启动。

### 用户绑定的 web/admin 语言偏好

- 变更类型：功能变更。
- 影响模块：`apps/web`、`services/api`、`infra/mysql/init/001_v1_baseline.sql`、API/数据库文档和两份变更日志。
- 主要变更：
  - 在 `users` 表结构、MySQL 基线、H2 测试基线和 MySQL Flyway V4 迁移中新增 `preferred_locale`。
  - 登录资料和当前用户资料返回 `preferredLocale`，并新增 `PUT /api/users/me/locale`，用于为当前认证用户持久化 `zh-CN` 或 `en`。
  - web 主页面改为从用户资料恢复、切换并保存共用语言偏好，不再只保存在本地组件状态中。
  - admin 登录页/顶部栏新增语言切换，并将 admin 仪表盘、采集、监控、治理视图接入同一个用户语言状态。
- 验证结果：
  - 修改前已使用 CodeGraph 追踪认证、用户资料、admin 页面和前端 API 路径。
  - 已在 `apps/web` 执行 `npm run build`；Next.js 生产构建通过。
  - 已在 `services/api` 执行 `mvn test`；全部 38 个测试通过。
  - 已通过 `mvn test -Dtest=V2GrayReleaseApiTest` 定向验证用户资料语言偏好行为。
- 未完成事项：
  - 如果现有数据库以 Flyway 停用模式运行，部署前必须手动应用 `V4__user_preferred_locale.sql`，或以其他方式补齐 `users.preferred_locale` 字段。
  - admin 部分表格行数据以及后端返回的枚举/状态值仍按存储值展示，尚未全部映射为翻译标签。

### Compose 服务端点默认值修复

- 变更类型：部署配置修复。
- 影响模块：`.env.example`、`infra/docker-compose.yml`、`infra/docker-compose-all.yml` 和两份变更日志。
- 主要变更：
  - 将示例容器网络端点从仅宿主机可用的地址调整为 Compose 服务名：`mysql`、`redis`、`qdrant`、`ai-worker` 和 `collector`。
  - 将示例 MySQL、Redis、Qdrant 端口改为容器网络内部端口：`3306`、`6379` 和 `6333`。
  - 在两份 compose 文件中为 API 容器显式传入 `COLLECTOR_URL`，避免定时采集从 API 容器内访问自身的 `localhost`。
  - 将 API 到 AI Worker 的流量固定到 Compose 服务端点 `http://ai-worker:8100`，避免既有 `.env` 中残留的宿主机地址覆盖容器网络配置。
- 验证结果：
  - 修改前已使用 CodeGraph 和定向文件检查追踪定时采集错误路径，覆盖 `AdminCollectionScheduler`、`AdminCollectionStore` 和 `AdminCollectionMapper`。
  - 已检索部署配置，确认剩余服务间默认连接值使用 Compose 服务名以适配容器网络。
- 未完成事项：
  - 当前环境未安装 Docker CLI，因此仍需在具备 Docker 的环境中执行 `docker compose config` 和 `docker compose -f infra/docker-compose-all.yml up -d` 做实机验证。

## 2026-07-09

### 告警规则采集源表对齐

- 变更类型：功能修复。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 将熔断告警评估器改为查询当前版本的 `admin_collection_sources` 表，不再访问已废弃的 `collection_source_configs` 表。
  - 新增定向回归测试，验证当前采集源表结构中的 OPEN 熔断状态会生成告警。
- 验证结果：
  - 修改前已使用 CodeGraph 查看告警规则与采集源相关路径。
  - 已在 `services/api` 中执行 `mvn -Dtest=AlertRuleEngineTest test`；定向告警规则回归测试通过。
- 未完成事项：
  - 无。

### Flyway 启动暂停与 MySQL 初始化基线

- 变更类型：部署配置调整。
- 影响模块：`services/api`、`infra`、`.env.example` 和两份变更日志。
- 主要变更：
  - 将 API 启动阶段的 Flyway 迁移改为通过 `BIZSAGE_FLYWAY_ENABLED=false` 默认停用。
  - 保留显式启用开关，并在生产 compose 与全量 compose 的 API 环境变量中透传 `BIZSAGE_FLYWAY_ENABLED`。
  - 确认 MySQL 初始化基线已经包含当前运行版本所需表结构，包括 Admin、采集、SLA、风控规则以及用户记忆唯一键相关表和索引。
- 验证结果：
  - 修改前已使用 CodeGraph 查看 API 启动与配置相关路径。
  - 已在 `services/api` 中执行 `mvn -DskipTests clean compile`；编译通过。
- 未完成事项：
  - 当前环境未安装 Docker，因此 compose 启动与 MySQL 初始化仍需在具备 Docker 的开发环境中实际运行验证。
  - 已存在的旧数据库仍需人工对齐 schema 后再以 Flyway 停用模式启动，因为停用 Flyway 后不会自动升级旧结构。

### 用户记忆 Flyway V3 幂等性修复

- 变更类型：功能修复。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 将 `V3__user_memory_profile_uniqueness.sql` 调整为当 `(user_id, memory_category, memory_key, status)` 已存在任意唯一索引时跳过唯一约束 `ALTER TABLE`。
  - 保留已有 MySQL 数据库的重复数据归并步骤，同时让通过 `infra/mysql/init/001_v1_baseline.sql` 初始化的全新开发库能够通过 Flyway V3。
- 验证结果：
  - 修改前已使用 CodeGraph 查看记忆 Profile 相关代码路径。
  - 已检查 MySQL 基线，确认其已经创建 `uk_user_memory_profiles_natural`，与全新开发库启动失败现象一致。
- 未完成事项：
  - 当前环境未安装 Docker 和可用 MySQL 客户端，因此仍需在全新开发数据库上实际运行修复后的迁移。
  - 已经记录 V3 失败历史的开发库，需要先重置或 repair 后 API 才能再次启动。

### 用户记忆 Profile 编译修复

- 变更类型：功能修复。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 修复 `UserMemoryProfile` 全参数构造函数，将 `key` 与 `value` 参数赋值到已映射的 `memoryKey` 与 `memoryValue` 字段。
  - 在记忆 Profile 字段为 MyBatis-Plus 列映射重命名后，恢复 API 编译。
- 验证结果：
  - 已在 `services/api` 中执行 `mvn -DskipTests clean compile`；编译通过。
- 未完成事项：
  - 无。

### Compose 中间件部署剥离

- 变更类型：部署配置调整。
- 影响模块：`infra/docker-compose.yml`、`.env.example` 和两份变更日志。
- 主要变更：
  - 从 `infra/docker-compose.yml` 中移除 MySQL、Redis、Qdrant 三个由 compose 直接部署的中间件服务，仅保留 BizSage 自有服务（`api`、`ai-worker`、`collector`、`web`）和 `nginx`。
  - 删除仅供已剥离中间件使用的 MySQL 与 Qdrant 命名卷。
  - 将 API 与 AI worker 的中间件连接地址改为通过环境变量提供，覆盖 `DB_URL`、`REDIS_HOST`、`REDIS_PORT` 和 `QDRANT_URL`。
  - 更新 `.env.example`，补充外部中间件默认示例以及新的 `DB_URL`、`NGINX_PORT` 示例值。
- 验证结果：
  - 修改前已使用 CodeGraph 查看 `infra/docker-compose.yml`，确认没有其他索引文件依赖该 compose 文件。
  - 已使用 PyYAML 解析 `infra/docker-compose.yml`；解析后的服务列表为 `api`、`ai-worker`、`collector`、`web` 和 `nginx`，且不再包含 `volumes` 段。
  - 已检索 compose 文件，确认不再存在 `mysql`、`redis`、`qdrant` 服务块，也不再存在 `mysql_data` 或 `qdrant_data` 命名卷。
- 未完成事项：
  - 当前环境未安装 Docker CLI，因此无法执行 `docker compose config` 做 Docker 级配置校验。
  - 目标部署环境启动保留服务前，仍需先单独部署 MySQL、Redis、Qdrant，并在 `.env` 中提供可访问的连接地址。

### 核心代码中文注释完善

- 变更类型：仅代码注释的文档更新。
- 影响模块：`services/ai-worker/app`、`services/collector/app`、`services/api/src/main/java/com/bizsage/api` 和两份变更日志。
- 主要变更：
  - 为 AI Worker 的诊断、学习、模式切换、RAG、上下文压缩、记忆、模型路由、向量存储和自检重试链路补充并优化中文模块注释、方法注释与关键行内注释。
  - 为 Collector 的采集适配、治理过滤、冲突分类、Redis 状态、韧性处理、API 缓存、代理轮换、供应商失败转移和 HTTP 入口补充中文注释。
  - 为 API 核心编排链路补充中文注释，覆盖 AI Worker 调用、SSE 消息流、诊断/学习持久化、认证、请求 ID、启动知识同步、记忆向量同步、Collector 集成、后台采集调度、快照和隐私辅助工具。
  - 将 `AdminCollectionStore` 中一处可见乱码注释替换为可读中文说明，未改变运行时行为。
- 验证结果：
  - 已在选择核心路径前确认 CodeGraph 索引处于最新状态。
  - 已执行 `python -m compileall services/ai-worker/app services/collector/app`，修改过的 Python 模块均通过语法编译。
  - 已在 `services/api` 中执行 `mvn test`；测试进入运行阶段，但失败于既有业务断言，与本次仅注释修改无直接关系，包括知识/情报列表预期、运维工单响应形态、后台采集/知识生命周期 500、滚动摘要数量断言等。
  - 已在 `services/api` 中执行 `mvn -DskipTests clean compile`；干净编译失败于未修改的 `UserMemoryProfile.java`，原因是 `key` 与 `value` 符号无法解析。
- 未完成事项：
  - API 现有编译与测试失败仍需作为独立问题处理，不属于本次注释清理范围。
  - 所选核心路径之外的部分外围 Java 模块仍保留旧的英文 `V2` 注释，如需可在后续继续本地化。

### RAG 控制面重构建议归档

- 变更类型：文档更新。
- 影响模块：`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 新增 `docs/en/rag-control-plane-and-boundary-refactor-recommendation.md`，归档当前 API 与 AI worker 的 RAG 职责拆分、推荐的控制面放置位置以及分阶段边界重构路径。
  - 新增对应中文文档 `docs/zh-CN/rag-control-plane-and-boundary-refactor-recommendation-zh-CN.md`，保持相同的范围、结论、迁移阶段、风险与首批工作项。
  - 明确记录推荐目标形态为 `API = 控制面`、`AI worker = RAG 执行面`，并指出当前在线链路在内联知识装配和 worker 自带业务过滤语义上的边界漂移。
- 验证结果：
  - 已核对新增中英文文档在事实、建议、迁移阶段、风险和后续动作上保持一致。
- 未完成事项：
  - 本次仅归档重构建议，尚未修改运行时代码；在线 diagnosis 与 learning 主链仍需后续调整契约并补齐回归测试后，才能真正收紧边界。
  - 仓库后续仍需补一份面向具体 Java 与 Python 代码路径的实施计划，将本文建议映射为可执行改造任务。

### 用户记忆 Upsert 加固

- 变更类型：功能修复。
- 影响模块：`services/api`、`infra/mysql` 和两份变更日志。
- 主要变更：
  - 为 `user_memory_profiles` 增加了 `(user_id, memory_category, memory_key, status)` 自然键唯一约束，并同步更新 MySQL 基线与 H2 测试基线。
  - 新增 Flyway 迁移 `V3__user_memory_profile_uniqueness.sql`，在已有 MySQL 环境中先按确定性规则折叠同自然键重复数据，再施加新的唯一约束。
  - 将 `UserMemoryStore.saveOrRefresh()` 重构为“优先更新、其次插入、遇到重复键再重试更新”的流程，使重复或并发写入同一 active memory 时会刷新既有记录，而不是继续新增多条。
  - 新增回归测试，验证同一个 active memory key 被重复写入时最终仍只保留一条 active 记录，同时值会被更新。
- 验证结果：
  - 已在 `services/api` 中执行 `mvn -Dtest=MessageStreamApiTest test -q`，定向消息流测试在新的唯一约束和刷新语义下通过。
- 未完成事项：
  - 当前加固仅保证同一自然键与状态下最多一条记录，尚未引入 `SUPERSEDED`、`CONFLICTED` 等更丰富的冲突状态来表达互斥记忆值。
  - 生产发布前仍需要一次真实 MySQL 启动验证，确认 Flyway `V3` 能在现有环境上平滑执行。

### 在线向量记忆链路默认停用

- 变更类型：功能修复。
- 影响模块：`services/api`、`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 从 `DiagnosisService` 与 `LearningService` 中移除了在线向量记忆写入职责，因此正常 Agent 交互中的非结构化记忆候选现在只会落到权威 MySQL 记忆存储，不再继续写入向量记忆队列。
  - 为 `MemorySyncScheduler` 增加了新的 `bizsage.memory.vector-sync-enabled` 开关，并将默认值设为 `false`，保留未来重新启用所需的向量同步代码路径，但不再让它在当前在线架构中默认运行。
  - 新增 API 回归测试，验证非结构化记忆候选仍会写入 `user_memory_profiles`，同时 `user_memory_embeddings` 保持不变。
  - 同步更新中英文实现状态里程碑文档，明确当前在线的是滚动摘要和 MySQL 长期记忆，而向量记忆链路处于有代码骨架但默认停用的状态，等待检索闭环设计完成后再恢复。
- 验证结果：
  - 已在 `services/api` 中执行 `mvn -Dtest=MessageStreamApiTest test -q`，定向消息流测试通过，包含新的非结构化记忆回归用例。
  - 已在 `services/ai-worker` 中执行 `PYTHONPATH=. pytest tests/test_memory_v2.py -q`，15 个测试通过。
- 未完成事项：
  - 保留下来的 `/memory/sync` 端点、embedding store 与 scheduler 代码目前默认休眠，仍需先补齐检索/读路径设计后才适合重新接入在线链路。
  - 目前尚未对历史 `user_memory_embeddings` 数据做迁移清理，也未增加已同步向量记忆数据的治理与回收机制。

### Agent 记忆摘要连续性与真相源对齐

- 变更类型：功能修复。
- 影响模块：`services/api`、`services/ai-worker` 和两份变更日志。
- 主要变更：
  - 重构了 `DiagnosisService`、`LearningService` 与 `ConversationSummaryStore` 的会话摘要逻辑，改为每个会话只保留一条 active 的滚动摘要，并在新摘要中持续携带此前已经压缩过的上下文，避免老轮次信息随着消息失活而丢失。
  - 在 API 向 AI worker 传递长期记忆时补充 `mysql:user_memory_profiles` 来源标记，明确 MySQL 才是权威记忆存储，worker 只消费已筛选好的长期记忆，不再自行重新判定生命周期。
  - 更新 AI worker 侧 `build_memory_context()` 的契约，使其信任 API 过滤后的长期记忆，并新增回归测试，确保 prompt 拼装不会再因为本地过期元数据而误删记忆。
  - 将 `UserMemoryProfile` 的内部字段映射从保留字风格属性名调整为更稳妥的命名，避免 MyBatis-Plus 在记忆查询与刷新时生成 H2 不兼容 SQL。
  - 新增消息流回归测试，验证滚动摘要在多次摘要后仍只保留一条 active 摘要记录，同时保留最早已摘要轮次的内容。
- 验证结果：
  - 已在 `services/ai-worker` 中执行 `PYTHONPATH=. pytest tests/test_agent.py -q`，24 个测试通过。
  - 已在 `services/api` 中执行 `mvn -Dtest=MessageStreamApiTest test -q`，修复记忆查询映射后，定向 SSE/摘要集成测试通过。
- 未完成事项：
  - 向量记忆链路目前仍只保证异步写入与同步，尚未实现回注到在线 Agent 上下文的检索闭环，留待下一阶段处理。
  - 记忆 upsert 语义目前仍是 `select + update/insert`，唯一约束与更强的原子性改造尚未完成。

### API MyBatis-Plus 迁移启动

- 变更类型：功能开发。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 将 API 服务的直接 JDBC starter 依赖替换为 `mybatis-plus-spring-boot3-starter`，启用 `@MapperScan`，并在 `services/api/src/main/resources/application.yml` 中补充基础 MyBatis-Plus 配置。
  - 把用户、会话、知识、情报、审核工单、消息、摘要和记忆链路中使用的核心持久化 `record` 改造成带有 `@TableName`、`@TableId` 和必要 `@TableField` 映射的 MyBatis-Plus 实体，同时保留 record 风格访问器，尽量不打断现有调用方。
  - 为首批迁移实体新增 Mapper 接口，并将 `UserStore`、`ConversationStore`、`KnowledgeStore`、`IntelligenceStore`、`PaidIntelligenceStore`、`AdminReviewStore`、`ConversationMessageStore`、`ConversationSummaryStore`、`UserMemoryStore` 和 `UserMemoryEmbeddingStore` 的实现切换到 MyBatis-Plus 的查询与更新 API。
  - 修复了登录接口响应中缺失 `token` 的回归问题；同时把测试环境缓存切回 `simple`，避免本地 Maven 验证强依赖 Redis。
- 验证结果：
  - 已在 `services/api` 中通过 `mvn -DskipTests compile`，确认 API 模块可基于新的 MyBatis-Plus 基线完成编译。
  - 已通过定向 Maven 测试确认认证响应结构恢复，且核心 CRUD 路径可以运行到业务层；但缩小范围后的测试仍暴露出分页响应断言和未迁移持久层区域的既有失败。
- 未完成事项：
  - 继续完成仍在使用 `JdbcTemplate` 的后台、治理、运维和快照相关类的 JDBC 到 MyBatis 迁移。
  - 在剩余持久层迁移完成后，对齐分页响应与测试断言之间的差异，并重新运行 `services/api` 全量 Maven 测试。

### Admin 残留 JdbcTemplate 迁移收口

- 变更类型：功能开发。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 将 `AdminStore`、`AdminKnowledgeStore` 和 `AdminCollectionStore` 中剩余的 `JdbcTemplate` 实现全部替换为基于 MyBatis Mapper 的持久化访问。
  - 为后台仪表盘遥测、告警/审计/复核/工单/人工情报流程、知识生命周期读写，以及采集源/任务/死信等链路新增专用 Admin Mapper 接口。
  - 在迁移后保持现有管理端 API 返回结构不变，同时保留知识发布同步 `knowledge_items`、AI worker 的 Qdrant 同步、采集自动生成情报与审核单、以及审计日志写入能力。
- 验证结果：
  - 已通过 `rg -n "JdbcTemplate" services/api/src/main/java/com/bizsage/api` 复核，API 源码树中的 admin 残留引用已清理。
  - 已规划继续执行 `mvn -DskipTests compile` 与定向 admin/governance API 测试，验证 Mapper 迁移后的编译与运行兼容性。
- 未完成事项：
  - 仍需跑完整编译和定向 Maven 测试，并根据结果修复可能暴露出的 Mapper SQL 或 H2 兼容性问题。

### 引入 Flyway 数据库迁移

- 变更类型：功能修复。
- 影响模块：`services/api`、`infra/mysql`、`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 为 API 服务引入 Flyway，并配置从 `services/api/src/main/resources/db/migration/mysql/` 执行启动校验与增量迁移。
  - 移除了运行期 `AdminSchemaMigration`、`ConversationSchemaMigration` 以及 SLA 运行期建表逻辑，数据库结构变更统一改由 SQL 脚本驱动，不再依赖 Java Bean 初始化。
  - 扩展 `infra/mysql/init/001_v1_baseline.sql`，补齐当前最新的后台风控规则、采集合规字段、SLA 表以及后台种子数据，并新增与之配套的 Flyway 增量升级脚本，用于从手工基线继续升级。
  - 修复了 MySQL 基线脚本中 `knowledge_items` 种子数据的损坏 SQL 字面量，确保全新数据库在 Flyway 接管前可以先成功导入 `001_v1_baseline.sql`。
  - 将测试库初始化改为脚本化 H2 迁移资源，尽量与生产 schema 保持一致，同时保留 API 测试依赖的本地 `password` 登录种子。
  - 同步更新中英文数据库、部署和管理员后台设计文档，明确新的数据库流程：先手工执行 MySQL 基线脚本，再由 Flyway 在服务启动时校验并升级。
- 验证结果：
  - 已确认在移除运行期 schema 迁移类并引入 Flyway 依赖后，API 模块仍可继续编译。
  - 已规划针对认证、后台、治理流程的 Maven 定向验证，以及基于新 Flyway 配置的启动迁移验证。
- 未完成事项：
  - 仍需执行定向 Maven 测试和一次真实 MySQL 启动演练，确认“手工基线 + Flyway 升级”链路端到端可用。

### 灰度发布 YAML 绑定修复

- 变更类型：功能修复。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 新增 `GrayReleaseProperties`，将 `GrayReleaseService` 原先依赖 SpEL 的 `@Value("#{${...}}")` 特性开关注入改为 Spring Boot 原生配置绑定。
  - 将 `services/api/src/main/resources/application.yml` 中 `bizsage.gray-release.features.*.allowed-memberships` 从逗号分隔字符串改为标准 YAML 列表，避免 paid-intelligence 和 PDF-export 灰度人群在启动时解析失败。
  - 新增一个聚焦型回归测试，在不依赖完整数据库上下文的前提下验证灰度配置绑定和特性判断逻辑。
- 验证结果：
  - 已在 `services/api` 中运行 `mvn -Dtest=GrayReleaseServiceConfigurationTest test`，定向绑定测试通过，确认标准列表属性可正常加载，且不会再触发 SpEL 解析错误。
  - 已尝试运行 `mvn -Dtest=V2GrayReleaseApiTest test`，但该套件当前被 `schema-test.sql` 中既有的 SQL 语法/编码问题阻塞，与本次灰度修复无直接关系。
- 未完成事项：
  - 修复 `services/api/src/test/resources/schema-test.sql` 中损坏的种子 SQL 内容后，重新执行完整的灰度发布集成测试。

### API 编译兼容性修复

- 变更类型：功能修复。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 在 `AdminCollectionStore` 中补回缺失的 `java.io.IOException` 导入，恢复 JSON 负载解析相关编译。
  - 将 `CollectorClient` 调整为使用 Spring 的 `ParameterizedTypeReference<Map<String, Object>>` 调用 `RestClient.ResponseSpec.body(...)`，匹配 Spring Boot 3.3 / Spring Framework 6.1 的类型要求。
  - 按 PDFBox 3.0 API 重写 `PdfReportGenerator` 的字体与换行处理：改用 `PDType1Font`，移除已不存在的 `PDPageContentStream#getFont()` 依赖，并顺手清理受影响的报表字符串与注释乱码。
- 验证结果：
  - 已针对本次日志里的三组编译失败及其依赖版本 API 用法完成静态源码核对。
  - 已在 `services/api` 中通过 `mvn -DskipTests compile` 完成验证，模块现已恢复编译通过。
- 未完成事项：
  - 仍建议重新运行 Docker 镜像构建，确认容器内 `services/api` 的构建路径与本地 Maven 编译结果保持一致。
## 2026-07-08

### 双Agent业务核心：学习Agent、三级记忆、模式切换与标准化输出

- 变更类型：功能开发。
- 影响模块：`services/ai-worker`、`apps/web` 和双边变更日志。
- 主要变更：
  - **行业学习Agent**：新增 `services/ai-worker/app/learning_agent.py`，实现 BizSage 双Agent系统的另一半。支持意图分类（行业概览、节点学习、指标问答、风险问答、隐形规则、政策问答），通过关键词匹配覆盖全部7个链条节点。三种学习模式：FAST_START（概览）、FULL_CHAIN（系统深度学习）、NODE_DEEP_DIVE（聚焦节点研究）。使用针对通俗教学优化的学习专用系统提示词。新增 `POST /agent/learn` 端点。
  - **三级记忆系统**：增强 `services/ai-worker/app/memory.py`，新增五种记忆类别（PREFERENCE、BUSINESS_FACT、PAIN_POINT、INDUSTRY_CONTEXT、LEARNING_PROGRESS），支持按类别的生命周期过期（90-365天），通过 `forget_expired()` 实现智能自动遗忘，通过 `extract_learning_memories()` 提取学习专用记忆，增强诊断记忆提取，支持痛点识别和行业上下文模式匹配。
  - **双Agent模式切换**：新增 `services/ai-worker/app/agent_transition.py`，支持上下文保持的模式切换。学习→诊断切换将当前学习的链条节点带入诊断焦点。诊断→学习切换识别薄弱环节并建议针对性学习。新增 `POST /agent/transition` 统一端点和供前端展示的 `TransitionContext` 预览。
  - **标准化双Agent输出**：新增 `services/ai-worker/app/agent_output.py`，实现两个Agent共享的统一 `AgentOutput` 格式。结构化分段（关键发现、风险提示、可行动建议、证据支撑）、来源可追溯、置信度标签、时效性说明、合规免责声明、链条节点上下文（学习模式）和建议的后续操作。支持 `render_agent_output()`（完整格式）和 `render_legacy_format()`（向后兼容的诊断格式）。
  - **数据模型**：为 `KnowledgeItem` 新增可选的 `link_id` 字段用于链条节点过滤；更新 `parse_knowledge()` 从 API 负载中提取 `linkId`/`link_id`。
  - **前端**：在 API 客户端中新增 `AgentLearnRequest`、`AgentTransitionRequest`、`AgentOutput`、`fetchAgentLearn()` 和 `fetchAgentTransition()`。
- 验证结果：
  - 三个测试文件中共 32 个新 Python 测试用例（6 个输出、18 个学习Agent、8 个模式切换），全部通过。
  - 全部 25 个现有 ai-worker 测试用例（7 个诊断Agent + 18 个上下文压缩器）继续通过——零回归。
  - 合计：`services/ai-worker` 中 57/57 个测试用例全部通过。
- 未完成项：
  - 学习→诊断→学习切换回路的浏览器级端到端验证。
  - Web UI：专用学习模式工作区（含链条节点导航器，遵循现有 DiagnosisWorkspace 模式）。

### RAG 上下文压缩

- 变更类型：功能开发。
- 影响模块：`services/ai-worker` 和双边变更日志。
- 主要变更：
  - 新增 `services/ai-worker/app/context_compressor.py`，实现了面向 RAG 检索结果的内容感知上下文压缩器。压缩器功能包括：(1) 通过字符三元组 Jaccard 相似度（阈值 0.70）合并近似重复的知识条目，(2) 按相关度分数比例分配 token 预算，支持可配置的每项最小/最大字符限制，(3) 在句子边界处截断长文本以保持可读性，(4) 对混合中英文文本进行保守的 token 数量估算。
  - 将压缩器集成到 `agent.py::diagnose()` 中——原先简单的 `"\n".join(...)` 上下文拼接方式替换为 `compress_context()` 管线，支持内存感知的 token 预算分配（有记忆上下文时 2100 tokens，无记忆上下文时 2400 tokens）。
  - 在 `DiagnoseRequest` 中暴露可选的 `compress_config` 参数，允许调用方按请求调整 `total_token_budget`、`min_chars_per_item`、`max_chars_per_item` 和 `merge_similarity_threshold`。
- 验证结果：
  - `test_context_compressor.py` 中 18 个新增单元测试，覆盖三元组提取、Jaccard 相似度、合并逻辑（高分优先）、token 估算、单/多项压缩、重复合并、按分分配预算、最小字符保障和句子边界截断。
  - 全部 7 个现有 `test_agent.py` 测试在集成压缩管线后继续全部通过。
- 未完成项：
  - 无。压缩对现有调用方透明——默认配置在短上下文中表现与之前一致，同时在长结果场景下防止上下文窗口溢出。

### 数据治理：冲突引擎、时间序列快照与四层数据隔离

- 变更类型：功能开发。
- 影响模块：`services/collector`、`services/api`、`apps/web`、`infra/mysql` 和双边变更日志。
- 主要变更：
  - **七层冲突引擎**：新增 `services/collector/app/conflict_engine.py`，实现基于 SimHash 距离对比、来源权重评估和谣言/黑名单检测的五分支分类（短期波动、区域例外、权威更新、可疑冲突、虚假信息）。新增 `POST /govern/conflict-check` FastAPI 端点，并在现有 `/govern` 端点中集成可选的冲突检测。
  - **时间序列快照**：在 `services/api/.../governance/` 下新增 Java `SnapshotStore`、`SnapshotService`、`SnapshotController` 和 `SnapshotScheduler`。支持每日（完整转储，保留30天）、每周（按 link_id 聚合，保留12周）和每月（趋势数据，保留12个月）快照，具备自动定时生成和留存清理功能。新增 `GET/POST /api/admin/snapshots/**` 端点。
  - **四层数据隔离**：新增 `DataIsolationService`、`DataScope`，并在 `IntelligenceStore` 中添加带作用域限制的查询方法（`listScoped`），实现用户私有、区域、行业和付费/免费权益四个维度的过滤。新增 `ConflictStore` 用于冲突解决结果持久化和虚假信息台账管理。新增 `GET /api/admin/governance/conflicts` 和 `GET /api/admin/governance/false-ledger` 端点。
  - **数据库**：新增 `false_information_ledger`、`conflict_resolutions` 表，并为 `intelligence_snapshots` 扩展了 `retention_days`、`record_count`、`parent_snapshot_id`、`expires_at` 列，同步更新了 MySQL 初始化脚本、H2 测试 schema 和 `AdminSchemaMigration`。
  - **前端**：在 `/admin` 控制台添加了快照、冲突和虚假情报导航入口，以及对应的 `api-client.ts` 类型和请求函数。
- 验证结果：
  - Python 冲突引擎包含13个单元测试，覆盖全部5个分支、批量检测、自定义配置和边界情况。
  - Java `GovernanceApiTest` 覆盖快照生命周期（生成/列表/对比/清理）、冲突解决列表、虚假情报台账列表和 RBAC 权限校验。
  - `services/collector`、`services/api` 和 `apps/web` 中的现有测试预期全部继续通过。
- 未完成项：
  - 在具备所需工具链的环境中运行 Python、Maven 和 npm 测试套件。
  - 新增管理导航入口的浏览器级视觉验证。

### 核心架构实现状态分析

- 变更类型：文档。
- 影响模块：`docs/en/milestones`、`docs/zh-CN/milestones` 和双边变更日志。
- 主要变更：
  - 新增双语实现状态分析文档（`docs/en/milestones/implementation-status-analysis.md` 和 `docs/zh-CN/milestones/implementation-status-analysis-zh-CN.md`），对比了 V4.0 目标架构与当前代码库的实现差距。
  - 分析了全部九层架构的逐能力实现状态（V1/V2/V3），识别了五条关键未闭合链路，并按九大领域汇总了完成度。
  - 记录了 Admin V3 分阶段交付状态（V3-1 至 V3-6）和剩余环境验证缺口。
- 验证结果：
  - 确认中英文文档描述了一致的发现、状态和缺口。
  - 与五份治理架构文档、三份里程碑文档以及 `apps/web`、`services/api`、`services/ai-worker`、`services/collector`、`infra` 下的实际源码交叉比对。
- 未完成项：
  - 无。此为当前实现状态的文档快照。

### Admin V3 中文文档修复

- 变更类型：文档修复。
- 影响模块：`docs/zh-CN/admin-v3-ui-design-zh-CN.md` 和两份变更日志。
- 主要变更：
  - 将 Admin V3 中文界面设计文档重新保存为 `UTF-8 with BOM`，避免常见 Windows 编辑器误判编码后出现中文乱码。
  - 恢复中文文档中损坏的 `## 12. Admin-V3-3 当前实现状态` 段落，使其再次与配套英文文档保持一致。
- 验证结果：
  - 已验证修复后的文档包含 UTF-8 BOM，并可按正确中文内容读取。
  - 已对照 `docs/en/admin-v3-ui-design.md` 末尾内容，确认恢复后的范围与实现说明一致。
- 未完成事项：
  - 本次仅为文档修复，无需运行服务测试套件。
### Admin-V3-1 与 Admin-V3-2 实现

- 变更类型：功能开发。
- 影响模块：`services/api`、`infra/mysql`、`apps/web`、`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 新增 Admin V3 真实数据库持久化，包含 `admin_intelligence_reviews`、`admin_tickets`、`admin_human_intelligence`，并同步 MySQL 初始化脚本和 API 启动自动补表。
  - 新增 `/api/admin/**` 接口，覆盖总控台指标、告警处理、审计日志检索、情报复核裁决、工单流转、人工情报录入与审核，后台写操作会写入 `audit_logs`。
  - 新增独立 `/admin` Web 管理台，直接对接真实后台接口，覆盖 Admin-V3-1/V3-2 工作台：总控台、告警、日志审计、情报复核、台账工单和人工情报。
  - 新增后端 `AdminV3ApiTest` 覆盖和 Web 源码回归测试，锁定后台 API client、页面和样式入口。
- 验证结果：
  - 已尝试在 `services/api` 运行 `mvn -Dtest=AdminV3ApiTest test`，但当前执行环境未提供 `mvn`。
  - 已尝试在 `apps/web` 运行 `npm test`，但当前执行环境未提供 `npm` 或 `node`。
  - 在工具链不可用后，已对新增后台 API、schema、页面和样式执行静态源码检查。
- 未完成事项：
  - 需要在具备 Maven 与 Node/npm 的环境运行新增后端和 Web 测试套件。
  - `/admin` 的浏览器级视觉验证仍待执行。
### V3 管理员后台界面设计文档

- 变更类型：文档。
- 影响模块：`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 新增 V3 管理员后台界面设计的中英文配对文档。
  - 明确后台信息架构、角色权限边界、现有 Next.js 工作台复用方式、关键页面设计、核心业务流程、API 需求和验收标准。
  - 覆盖 V3 完整运营范围，包括知识库、采集调度、情报复核、台账工单、风控规则、用户会员、订单权益、告警监控、日志审计、合规配置和月度报表。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认中英文管理员后台设计文档表达相同范围、流程、验收标准和分阶段落地计划。
- 未完成事项：
  - 本次仅完成界面设计，尚未在 `apps/web` 中实现；API 契约和页面级测试需在开发阶段补充。

### Agent 需求澄清规则

- 变更类型：文档。
- 影响模块：`AGENTS.md`、`AGENTS-zh-CN.md` 和两份变更日志。
- 主要变更：
  - 新增 agent 协作规则：涉及功能新增或变更时，在给出最终方案或实施前必须一次只向用户提一个问题。
  - 明确需要根据用户回答持续追问，直到约有 95% 的信心理解用户真实需求、目标、边界和验收标准。
- 验证结果：
  - 已确认英文和中文 agents 文档表达了同一要求。
- 未完成事项：
  - 无。

## 2026-07-07

### Web 会话侧栏与顶栏细节修复

- 变更类型：功能开发。
- 影响模块：`apps/web` 和两份变更日志。
- 主要变更：
  - 重构会话列表单行结构，让归档和删除动作改为显式图标按钮，不再把交互 `span` 嵌套在整行按钮里，从而恢复可见图标并保持有效标记结构。
  - 收紧诊断工作区滚动边界，让诊断区右侧外层容器不再整体滚动，消息流区域独立滚动，右侧报告/情报列继续保留各自的溢出处理。
  - 将右上角身份信息压缩为两行展示，并移除侧栏和用户面板里可见的“当前上下文”字样。
- 验证结果：
  - 在 `apps/web` 运行 `npm test`，31/31 测试通过，新增回归断言覆盖会话行操作按钮、诊断区滚动约束、紧凑身份信息和移除当前上下文摘要行。
- 未完成事项：
  - 本次未额外执行浏览器级视觉走查，当前以源码级回归测试作为验证依据。

### 诊断 SSE 多帧流式修复

- 变更类型：功能开发。
- 影响模块：`apps/web`、`services/api` 和两份变更日志。
- 主要变更：
  - 更新 API 消息流接口，让单次诊断请求会连续输出多个 `diagnosis` SSE 帧，而不再只在结束时返回一个最终负载，从而让浏览器能够看到明确的渐进式输出。
  - 更新 Web 端诊断流解析逻辑，按完整 SSE 帧读取并始终使用最新的 `diagnosis` 负载，修复此前 UI 可能停留在第一段 `answer`、后续更新无法继续刷新的问题。
  - 为 `services/api` 补充多帧 SSE 回归测试，并为 `apps/web` 补充“读取最新流式帧”相关回归断言。
- 验证结果：
  - 在 `services/api` 运行 `mvn -Dtest=MessageStreamApiTest test`，4/4 测试通过，覆盖新的多帧 SSE 回归场景。
  - 在 `apps/web` 运行 `npm test -- envelope.test.mjs`，27/27 测试通过，覆盖新的最新帧流式解析断言。
- 未完成事项：
  - 后端当前输出的是基于最终诊断结果拆分出来的渐进快照，而不是上游模型逐 token 的实时事件。

### Web 流式诊断与身份布局修复

- 变更类型：功能开发。
- 影响模块：`apps/web`、`docs/superpowers/plans/2026-07-07-web-streaming-layout-fixes.md` 和两份变更日志。
- 主要变更：
  - 为 Web 诊断链路补充增量 SSE 读取，让 assistant 回复可以在历史消息刷新完成前渐进显示。
  - 将登录身份信息与退出登录操作合并到右上角统一区域，移除“已就绪”徽标，并把顶栏状态文案改成当前工作区提示，不再重复展示档案信息。
  - 更新工作区外壳和诊断消息面板，让左侧栏与消息区各自独立滚动，并在流式回复过程中自动跟随到底部，避免长对话时身份区显得被底部内容覆盖。
- 验证结果：
  - 在 `apps/web` 运行 `npm test`，26/26 测试通过，新增断言覆盖增量流式渲染、身份操作合并和滚动跟随行为。
  - 在 `apps/web` 运行 `npm run build`，确认更新后的外壳、流式读取逻辑和诊断工作区可在生产模式下成功编译。
- 未完成事项：
  - 后端当前仍输出最终单个 SSE 诊断事件，而不是逐 token 模型事件，因此当前“流式”体验仍依赖浏览器对该事件负载的增量传输呈现。

### Web 诊断回复去重修复

- 变更类型：功能开发。
- 影响模块：`apps/web` 和两份变更日志。
- 主要变更：
  - 修复诊断对话工作区，在相同 assistant 回复已经进入 `messageHistory` 后，不再额外渲染第二个独立回复气泡。
  - 保留瞬时 `diagnosis` 状态作为历史消息刷新前的兜底显示，既保住了首条回复的即时反馈，也避免了当前会话里出现两个相同消息框。
  - 调整报告生成入口条件，让已有 assistant 历史回复的会话仍然可以继续显示报告操作入口。
- 验证结果：
  - 在 `apps/web` 运行 `npm test`，新增的 `DiagnosisWorkspace` 回归断言通过，锁定 assistant 回复不重复渲染的行为。
- 未完成事项：
  - 仍建议做一次真实浏览器复验，确认诊断请求进行中的当前会话视图已与刷新后视图保持一致。

## 2026-07-06

### Web 对话信息架构重构

- 变更类型：功能开发。
- 影响模块：`apps/web` 和两份变更日志。
- 主要变更：
  - 将 Web 首页重构为持久化对话管理外壳，并拆分出 `WorkspaceShell`、`ConversationSidebar`、`DiagnosisWorkspace`、`ArchiveWorkspace` 四个组件。
  - 去除 Web 可见的 `V2` 与灰度发布表述，删除指标面板以及页面级 `fetchOpsMetrics` 依赖，同时保留 `诊断`、`情报`、`用户`、`归档` 四个顶层模块。
  - 新增纯会话工作区规则辅助模块和一组回归测试，锁定分区过滤、按最新优先排序、切入归档时的回退选中，以及归档当前会话后的下一条选中逻辑。
- 验证结果：
  - 在 `apps/web` 运行 `npm test`，新的会话工作区回归测试与更新后的源码级信息架构断言全部通过。
  - 在 `apps/web` 运行 `npm run build`，确认重构后的页面、新组件和会话辅助逻辑可在生产模式下成功编译。
- 未完成事项：
  - 仍建议在目标部署环境做一次真实浏览器复验，确认响应式侧栏外壳与归档只读体验在真实数据量下符合预期。

### Web 工作区首屏高度贴合修复

- 变更类型：功能开发。
- 影响模块：`apps/web` 和两份变更日志。
- 主要变更：
  - 重构桌面端工作区布局，让主内容列改为固定视口高度的外壳加内部高度分配，不再依赖聊天面板 `calc(100vh - ...)` 的最小高度硬减。
  - 更新诊断区网格、对话面板和右侧运维栏的高度继承方式，在需要时改为各自内部滚动，避免浏览器 100% 缩放下底部内容掉到首屏之外。
  - 为 Web 新增一个布局回归测试，断言工作区 CSS 在浏览器 100% 缩放时会把诊断区保持在首屏范围内。
- 验证结果：
  - 在 `apps/web` 运行 `npm test`，新增的首屏高度回归断言与现有 Web 源码级检查一并通过。
- 未完成事项：
  - 仍建议在目标部署环境做一次真实浏览器复验，确认 100% 缩放体验在实际视口尺寸下与本次 CSS 回归意图一致。

### Web 认证失效返回登录界面

- 变更类型：功能开发。
- 影响模块：`apps/web`、`services/api` 和两份变更日志。
- 主要变更：
  - 更新 API 的 JWT 认证过滤器，让受保护 HTTP 接口上的无效或过期 bearer token 统一走标准未认证返回路径，而不再冒泡成服务端错误。
  - 为 Web 端受保护的 `fetch` 请求新增专门的 `AuthExpiredError` 路径，覆盖会话列表、消息历史、付费情报、运维指标和诊断报告请求。
  - 更新 Web 首页，在受保护 HTTP 请求检测到认证失效时清除持久化登录态、回到现有登录界面，并显示会话失效提示，同时保留当前未发送的草稿输入内容。
- 验证结果：
  - 在 `services/api` 运行 `mvn -Dtest=AuthAndRbacTest test`，验证无效 bearer token 现在返回 `401` 且 `code: UNAUTHORIZED`。
  - 在 `apps/web` 运行 `npm test`，验证认证失效客户端路径、回退登录界面行为和草稿保留相关源码断言。
- 未完成事项：
  - SSE 诊断链路上的认证失效自动退登仍按本次范围约束保持不变。

## 2026-07-05

### LLM 严格闭环实施计划归档

- 变更类型：文档维护。
- 影响模块：`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 新增 `docs/en/llm-closure-implementation-plan.md`，作为用户侧 LLM 严格闭环的英文正式实施计划文档。
  - 新增 `docs/zh-CN/llm-closure-implementation-plan-zh-CN.md`，补齐范围、决策和验收意图一致的中文正式文档。
  - 归档了已确认的目标链路 `Web -> API -> AI worker -> RAG/Qdrant -> 外部 OpenAI-compatible LLM -> API -> Web`，并明确记录严格失败策略、报告路径闭环和健康可见性要求。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认中英文正式文档和两份变更日志在同一次变更中同步更新。
- 未完成事项：
  - 该计划现已作为正式实施文档归档；运行时代码中的 API 到 worker 严格闭环仍需后续按计划实施并完成验证。

### 基础设施宿主机端口暴露

- 变更类型：功能开发。
- 影响模块：`infra/docker-compose.yml` 和变更日志。
- 主要变更：
  - 将 MySQL 暴露到宿主机，并使用非默认端口映射 `${MYSQL_PORT:-13306}:3306`。
  - 将 Redis 暴露到宿主机，并使用非默认端口映射 `${REDIS_PORT:-16379}:6379`。
  - 将 Qdrant 暴露到宿主机，并使用非默认端口映射 `${QDRANT_PORT:-16333}:6333`。
  - 保持容器内部服务端口不变，确保现有容器间访问地址和本地启动脚本无需额外调整。
- 验证结果：
  - 已检查 `infra/scripts/start-local.ps1` 和 `infra/scripts/start-all.ps1`，确认它们本来就在等待宿主机端口 `13306`、`16379`、`16333`。
  - 已在修改后重新读取 `infra/docker-compose.yml`，完成配置级核对。
  - 本轮未执行 `docker compose up`，因此没有进行在线容器连通性验证。
- 未完成事项：
  - 仍需在目标机器上执行一次 Docker 启动检查，确认三个宿主机端口映射都可正常访问。

### 会话库表兼容性修复

- 变更类型：功能开发。
- 影响模块：`services/api` 和变更日志。
- 主要变更：
  - 在 `services/api` 新增 `ConversationSchemaMigration`，用于在应用启动时修补遗留会话存储结构，解决已有 MySQL `messages` 表缺少新版会话记忆字段时的兼容性问题。
  - 为运行时兼容路径补齐 `message_type`、消息证据与置信度字段、活跃上下文标记、区域元数据，以及 `conversation_summaries` 表，使消息流接口在旧库结构上也能启动后自修复，而不必先手工执行紧急 SQL。
  - 新增 `ConversationSchemaMigrationTest`，验证旧版 `messages` 表可被升级，并在升级后正常支持消息持久化与摘要持久化。
- 验证结果：
  - 在 `services/api` 运行 `mvn -Dtest=ConversationSchemaMigrationTest test`：1 个测试通过。
  - 在 `services/api` 运行 `mvn -Dtest=MessageStreamApiTest test`：3 个测试通过。
  - 在 `services/api` 运行 `mvn test`：17 个测试通过。
- 未完成事项：
  - 现有部署数据库仍需要重启一次应用，才能让启动期迁移逻辑真正作用到在线库表结构。

### Nginx Authorization 转发修复

- 变更类型：功能开发。
- 影响模块：`infra/nginx` 和变更日志。
- 主要变更：
  - 更新 `infra/nginx/nginx.conf`，让反向代理显式将进入请求的 `Authorization` 请求头转发给 `services/api`。
  - 修复经 nginx 登录成功后，`POST /api/conversations` 与 `POST /api/conversations/{id}/messages/stream` 等受保护接口仍被返回 `401 Unauthorized` 的部署链路问题。
  - 保持现有 SSE 缓冲和超时设置不变，将修复范围限定在代理边界的鉴权传递上。
- 验证结果：
  - 已针对 `http://192.168.31.91` 复现问题：`POST /api/auth/login` 返回 `200 OK`，但携带鉴权的 `POST /api/conversations` 在配置修改前返回 `401 Unauthorized`。
  - 已核对仓库中的 `services/api/src/test/java/com/bizsage/api/MessageStreamApiTest.java`，确认只要 `Authorization` 能到达 Spring Security，conversation 和 message stream 受保护接口会通过。
  - 当前环境未执行 nginx 热重载或修改后的远程端到端复验。
- 未完成事项：
  - 目标部署仍需要重新加载或重新部署更新后的 nginx 配置，`192.168.31.91` 上的在线实例才会停止返回 `401`。

### 三层记忆实施计划归档

- 变更类型：文档维护。
- 影响模块：`docs/superpowers/plans` 和变更日志。
- 主要变更：
  - 新增 `docs/superpowers/plans/2026-07-05-three-layer-memory.md`，作为三层记忆落地方案的英文实施计划归档。
  - 新增 `docs/superpowers/plans/2026-07-05-three-layer-memory-zh-CN.md`，补齐对应中文版本。
  - 保留了按任务拆分的实施顺序，覆盖 schema、API 持久化、长期记忆、AI worker 支持、自动摘要和 Web 验证。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认中英文计划文档与中英文变更日志在同一次变更中同步更新。
- 未完成事项：
  - 本次归档反映的是 `2026-07-05` 审阅时的实施拆解；如果后续执行路径发生偏离，需要同步刷新该计划归档。

### Agent 规范措辞清理

- 变更类型：文档维护。
- 影响模块：`AGENTS.md`、`AGENTS-zh-CN.md`、`docs/en/standards`、`docs/zh-CN/standards` 和变更日志。
- 主要变更：
  - 从英文根级 Agent 指南和英文 Agent 开发规范中移除了 “smallest clear, testable implementation” 相关表述。
  - 从中文根级 Agent 指南和中文 Agent 开发规范中移除了“最小、清晰、可测试的实现”相关表述。
  - 保留其余里程碑、双语文档、验证和安全约束不变。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认中英文规范文档与中英文变更日志在同一次变更中同步更新。
- 未完成事项：
  - 本次措辞调整无额外未完成事项。

### Agent 三层记忆实现

- 变更类型：功能开发。
- 影响模块：`services/api`、`services/ai-worker`、`apps/web`、`infra/mysql/init` 和变更日志。
- 主要变更：
  - 在 API 侧新增短期会话记忆持久化，基于 `messages` 表、活跃上下文过滤和滚动会话摘要实现连续诊断上下文。
  - 在 MySQL 中新增长期记忆存储，用于保存用户偏好和可复用经营事实，并补充 `user_memory_embeddings` 侧表，为后续将非结构化长期记忆同步到 Qdrant 预留审计与映射信息。
  - 重构诊断链路，使追问可以复用同一个 conversation，组合最近消息、会话摘要和长期记忆，再同时落库用户消息与 assistant 响应。
  - 为 AI worker 新增长期记忆提取、摘要上下文组装和是否进入向量记忆的判定辅助逻辑。
  - 更新 Web 诊断工作流，在用户主动新建前复用当前 conversation id，而不是每次提问都重新建会话。
- 验证结果：
  - 在 `services/api` 运行 `mvn test`：16 个测试通过。
  - 在 `services/ai-worker` 运行 `python -m pytest`：24 个测试通过。
  - 在 `apps/web` 运行 `npm test -- envelope.test.mjs`：9 个测试通过。
  - 在 `apps/web` 运行 `npm run build`：Next.js 生产构建成功完成。
- 未完成事项：
  - 当前 API 诊断主链已接入新的本地记忆诊断服务；跨服务的 `API -> AI worker` 生产运行时接线已完成数据模型和 worker 辅助逻辑准备，但尚未切为默认执行路径。
  - Qdrant 同步目前只在 MySQL 中记录为待处理的语义记忆工作项，本次未新增在线后台同步任务。
  - 当前环境未执行浏览器手工验证和全栈 Docker 启动验证。

### 组件交互与数据流归档

- 变更类型：文档维护。
- 影响模块：`docs/en`、`docs/zh-CN` 和变更日志。
- 主要变更：
  - 新增 `docs/en/component-interactions-and-data-flows.md`，作为当前实现版和目标架构版交互图的英文归档文档。
  - 新增 `docs/zh-CN/component-interactions-and-data-flows-zh-CN.md`，补齐对应中文版本。
  - 归档了当前实现图、目标架构图、诊断请求时序图、采集入库时序图的 Mermaid 与 ASCII 两种表达。
  - 记录了当前仓库实际运行链路与目标架构闭环之间尚未完全打通的部分。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认英文与中文文档成对新增，并且中英文变更日志在同一变更中同步更新。
- 未完成事项：
  - 本次归档反映的是 `2026-07-05` 审阅时的仓库状态；当 `API -> AI worker` 主诊断链路或 `collector -> 存储` 自动化闭环发生变化时，需要同步刷新本文档。

### MySQL、Redis、Qdrant 真实接入

- 变更类型：功能开发。
- 影响模块：`services/api`、`services/collector`、`services/ai-worker` 和 V2 验证文档。
- 主要变更：
  - 保持 API 侧基于 `JdbcTemplate` 的 MySQL 持久化路径不变，并新增 `GET /api/knowledge`，用于真实知识列表查询。
  - 新增 Redis 驱动的 collector 运行时状态适配层，并将指纹去重与近期快照兜底真实接入到路由执行链路。
  - 新增确定性 embedding 与 Qdrant 向量存储适配层，并将 AI worker 检索切换为 Qdrant 优先，同时保留 Python 侧的地域、行业、权益和质量重排规则。
  - 加固 AI worker 的请求隔离，避免请求级知识在后续 Qdrant 检索或诊断调用中泄漏到其他请求。
- 验证结果：
  - 在 `services/api` 运行 `mvn test`：14 个测试通过。
  - 在 `services/collector` 运行 `python -m pytest`：22 个测试通过。
  - 在 `services/ai-worker` 运行 `python -m pytest`：21 个测试通过。
- 未完成事项：
  - 当前环境未执行 Docker Compose 启动以及 MySQL、Redis、Qdrant 的在线健康验证。
  - 备份恢复演练、压测和长时间灰度稳定性观察仍待执行。

### Web 登录后身份框防回归

- 变更类型：功能维护。
- 影响模块：`apps/web`。
- 主要变更：
  - 新增回归测试，确保 Web 主工作台仍然位于登录门禁之后。
  - 新增回归测试，确保登录后的身份框只展示用户画像信息，不嵌入登录控件。
  - 为登录后的身份框增加可访问标签，使状态边界更明确并可测试。
- 验证结果：
  - 在 `apps/web` 运行 `npm test`：6 个测试通过。
  - 在 `apps/web` 运行 `npm run build`：Next.js 生产构建成功完成。
- 未完成事项：
  - 本轮未启动本地 API 服务，因此未执行浏览器内真实登录手工流程。

### Agent 标准合并

- 变更类型：文档维护。
- 影响模块：根目录贡献者文档和变更日志。
- 主要变更：
  - 将 `AGENTS-b.md` 的内容补充到 `AGENTS.md`，形成 Agent 开发标准章节。
  - 在 `AGENTS-zh-CN.md` 中补充对应中文说明。
  - 保留贡献者指南结构，同时恢复治理文档、双语维护、里程碑、验证和安全规则。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认英文和中文根目录指南都包含合并后的 Agent 标准。
- 未完成事项：
  - 无。

### 贡献者指南刷新

- 变更类型：文档维护。
- 影响模块：根目录贡献者文档和变更日志。
- 主要变更：
  - 重新创建 `AGENTS.md`，作为简洁的仓库贡献者指南。
  - 同步更新 `AGENTS-zh-CN.md`，保持中文指南一致。
  - 记录项目结构、本地命令、编码风格、测试、PR 要求和 Agent 注意事项。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认中文指南和中文变更日志在同一变更中同步更新。
- 未完成事项：
  - 无。

### Web 登录门禁与中英双界面

- 变更类型：功能开发。
- 影响模块：`apps/web` 和 V2 文档。
- 主要变更：
  - 将 Web 应用改为未登录用户只显示独立 BizSage 登录界面。
  - 在登录页和已登录工作台增加页面内中文/English 语言切换。
  - 增加退出登录清理逻辑，清空用户画像、诊断、报告、付费情报、运维指标和来源弹窗状态。
  - 同步更新 V2 里程碑与验证记录，并修复相关中文文档为可读 UTF-8 文本。
- 验证结果：
  - 在 `apps/web` 运行 `npm test`：5 个测试通过。
  - 在 `apps/web` 运行 `npm run build`：Next.js 生产构建成功完成。
- 未完成事项：
  - 本轮未启动本地 API 服务，因此未执行浏览器内真实登录手工流程。

## 2026-07-04

### 本地完整栈一键启动脚本

- 变更类型：功能开发。
- 影响模块：`infra`、本地部署文档和变更日志。
- 主要变更：
  - 新增 `infra/scripts/start-all.ps1`，用于通过一个命令启动 Docker 基础设施、API、AI worker、collector 和 Web。
  - 增加隐藏后台进程启动，并将日志和 PID 文件写入 `logs/local/`。
  - 增加跳过基础设施、跳过依赖安装和打开 Web 地址的参数。
  - 同步更新英中文本地部署文档的一键启动路径。
- 验证结果：
  - `infra/scripts/start-all.ps1` PowerShell 解析检查返回：`START_ALL_SYNTAX_OK`。
- 未完成事项：
  - 未执行完整运行时启动，因为它依赖本地 Docker 和长时间运行服务。

## 2026-07-04

### V1/V2 实装收敛

- 变更类型：功能开发。
- 影响模块：`services/api`、`services/ai-worker`、`apps/web`、`infra` 和 `docs`。
- 主要变更：
  - 将用户、会话、情报、付费情报和知识等核心 API 内存 store 替换为 JDBC 持久化仓库，并增加 H2 测试 schema。
  - 修复 API、AI worker、Web 控制台和数据库 seed 修正路径中的用户可见诊断/知识乱码。
  - 将 Web 控制台接入真实 API-client 调用，覆盖登录、会话创建、SSE 诊断、诊断报告元数据、付费情报和运维指标。
  - 将 V2 运维复核、告警和审计 API 改为查询持久化表，不再只返回硬编码列表。
  - 同步更新英中文 V1/V2 验证记录和 V2 里程碑状态。
- 验证结果：
  - 在 `services/api` 运行 `mvn test`：12 个测试通过。
  - 在 `services/collector` 运行 `python -m pytest`：11 个测试通过。
  - 在 `services/ai-worker` 运行 `python -m pytest`：6 个测试通过。
  - 在 `apps/web` 运行 `npm test`：3 个测试通过。
  - 在 `apps/web` 运行 `npm run build`：生产构建成功完成。
- 未完成事项：
  - Docker 启动、备份恢复演练、50 并发压测、V1 4 小时稳定性观察、真正 PDF 二进制导出和 V2 7 天灰度稳定性仍待执行。

## 2026-07-04

### V2 灰度工程骨架

- 变更类型：功能开发。
- 影响模块：`services/api`、`services/collector`、`services/ai-worker`、`apps/web`、`infra`、`docs` 和数据库基线。
- 主要变更：
  - 在 M0 范围锁定后将 V2 设为实施中，灰度对象为内部运营人员和种子付费用户。
  - 增加 V2 登录画像字段、种子付费用户、独立付费情报 API、免费/付费权限过滤、诊断报告元数据，以及运营指标/复核/审计 API。
  - 增加 collector 韧性辅助能力：增量指纹、重试耗尽、熔断状态、死信分类和近期快照兜底。
  - 增加 AI worker 权益感知检索过滤，以及针对存疑冲突和依据不足的推理自检状态。
  - 增加 Web V2 灰度指标、付费情报、复核和审计面板。
  - 增加 MySQL/Qdrant 备份恢复脚本、V2 schema 目标、API/数据库/部署文档和 V2 验证记录。
- 验证结果：
  - 在 `services/api` 运行 `mvn test`：12 个测试通过。
  - 在 `services/collector` 运行 `python -m pytest`：11 个测试通过。
  - 在 `services/ai-worker` 运行 `python -m pytest`：6 个测试通过。
  - 在 `apps/web` 运行 `npm test`：2 个测试通过。
  - 在 `apps/web` 运行 `npm run build`：生产构建成功完成。
- 未完成事项：
  - Docker 启动、备份恢复演练、50 并发压测、V1 4 小时稳定性观察和 V2 7 天灰度稳定性在当前环境未执行，已记录到 V2 验证文档。

## 2026-07-04

### 本地服务启动命令

- 变更类型：文档维护。
- 影响模块：本地部署文档和变更日志。
- 主要变更：
  - 在 `docs/zh-CN/deployment/local-deployment-zh-CN.md` 中补充 API 服务、AI Worker、Collector 和 Web 应用的本地启动命令。
  - 记录默认本地端口：API `8080`、AI Worker `8100`、Collector `8200`、Web 应用 `3000`。
  - 保持 Web 服务边界说明：Web 应用只能直接调用 API 服务。
- 验证结果：
  - 仅文档变更，无需运行服务测试套件。
  - 已确认对应英文部署文档在同一变更中同步更新。
- 未完成事项：
  - 无。

## 2026-07-04

### 里程碑详细规划补齐与 docs 语言目录重组

- 变更类型：文档维护。
- 影响模块：`docs/`、里程碑文档、根目录 Agent 规范、双语审计和变更日志。
- 主要变更：
  - 补齐 V2 生产高可用工程版详细里程碑文档：
    - `docs/en/milestones/v2-production-high-availability-milestones.md`
    - `docs/zh-CN/milestones/v2-production-high-availability-milestones-zh-CN.md`
  - 补齐 V3 全域商业化封顶终版详细里程碑文档：
    - `docs/en/milestones/v3-full-domain-commercial-final-milestones.md`
    - `docs/zh-CN/milestones/v3-full-domain-commercial-final-milestones-zh-CN.md`
  - 将 `docs/` 重组为语言目录：英文文档归档到 `docs/en/`，中文文档归档到 `docs/zh-CN/`。
  - 更新产品里程碑路线图、根目录 Agent 规范和双语审计文档中的新路径。
- 验证结果：
  - 文档语言配对检查返回 `NO_MISSING_DOCS_LANGUAGE_PAIRS`。
  - 针对 `docs/api`、`docs/database`、`docs/deployment`、`docs/milestones`、`docs/standards` 和旧顶层治理文档路径的旧引用扫描无匹配结果。
  - 已确认 `docs/` 当前仅包含 `docs/en/` 和 `docs/zh-CN/` 两个目录，两个语言目录各有 19 份 Markdown 文档。
- 未完成事项：
  - V2 和 V3 仍为规划阶段；实施前必须满足并批准各自进入标准。

## 2026-07-04

### 治理文档双语闭环

- 变更类型：文档维护。
- 影响模块：治理文档和变更日志。
- 主要变更：
  - 为 5 份合并中文治理文档补齐英文对应版本：
    - `docs/en/data-collection-and-intelligence-perception.md`
    - `docs/en/development-implementation-guide.md`
    - `docs/en/product-strategy-and-design.md`
    - `docs/en/risk-management-and-compliance.md`
    - `docs/en/system-architecture-and-framework.md`
  - 保持产品战略、系统架构、开发实现、数据采集与情报感知、风险合规五个治理范围的英文文档覆盖。
  - 关闭此前记录的 5 份治理文档英文版缺失问题。
- 验证结果：
  - 排除生成/缓存目录后，Markdown 双语配对检查返回 `NO_MISSING_PAIRS`。
  - 已确认 5 份英文治理文档均存在对应的 `*-zh-CN.md` 中文版本。
- 未完成事项：
  - 已由上方“里程碑详细规划补齐与 docs 语言目录重组”条目关闭。

## 2026-07-04

### 里程碑阶段重对齐

- 变更类型：文档维护。
- 影响模块：产品里程碑文档和变更日志。
- 主要变更：
  - 按源 PDF 阶段模型重排跨版本路线图：V1 MVP 最小可用版、V2 生产高可用工程版、V3 全域商业化封顶终版。
  - 删除原先单独拆出的 V4 规模化与生态阶段，因为源 PDF 只定义三次迭代。
  - 将高可用、灾备、多模型路由、完整 RBAC 和基础付费用户灰度归入 V2。
  - 将动态信源权重、完整数据血缘、完整商业会员、完整安全、H5 移动端、审计后台、运营报表和 99.9% SLA 能力归入 V3。
- 验证结果：
  - 已通过 `raw-docs/txt/` 下的抽取文本核对 `raw-docs/行业智能创业Agent平台全域完整架构设计文档（V4.0_全域封顶终版）配套分阶段落地开发规划说明书.pdf`。
  - 确认旧路线图包含 V4，因此与源文档阶段数量不一致。
- 未完成事项：
  - 已由上方“里程碑详细规划补齐与 docs 语言目录重组”条目关闭。
  - 5 份合并中文治理文档的英文对应版本已在上方“双语闭环”条目中补齐。

## 2026-07-04

### 治理规范对齐检查

- 变更类型：文档维护。
- 影响模块：根目录 Agent 规范和里程碑文档。
- 主要变更：
  - 将 5 份产品、架构、实现、数据采集和风险合规治理文档加入根目录 Agent 规范。
  - 在跨版本产品里程碑路线图中新增规范覆盖矩阵。
  - 明确 V1 仅实现治理文档中的 MVP/P0 子集，后续能力必须先补充详细双语里程碑后才能实施。
- 验证结果：
  - 已用 5 份治理文档检查当前 V1 里程碑和跨版本路线图。
  - 初始 Markdown 双语配对检查曾报告 5 份新增中文治理文档缺少英文对应版本；已在上方“双语闭环”条目中补齐。
- 未完成事项：
  - 已由上方“里程碑详细规划补齐与 docs 语言目录重组”条目关闭。

## 2026-07-04

### raw-docs 文档合并（15 → 5）

- 变更类型：文档维护。
- 影响模块：`docs/` 下新增 5 份合并文档，`raw-docs/` 源 PDF 保留本地归档。
- 主要变更：
  - 将 `raw-docs/` 下 15 个 PDF 产品/技术文档去重合并为 5 个结构化 Markdown 文档。
  - 新增 `docs/zh-CN/product-strategy-and-design-zh-CN.md` — 产品战略与产品设计蓝图（合并 PDF 1/2/3/5/6）。
  - 新增 `docs/zh-CN/system-architecture-and-framework-zh-CN.md` — 系统架构与技术框架（合并 PDF 4/11/12/10）。
  - 新增 `docs/zh-CN/development-implementation-guide-zh-CN.md` — 全系统开发实现指南（合并 PDF 13/14）。
  - 新增 `docs/zh-CN/data-collection-and-intelligence-perception-zh-CN.md` — 数据采集系统与情报感知引擎（合并 PDF 8/15/17）。
  - 新增 `docs/zh-CN/risk-management-and-compliance-zh-CN.md` — 风险管理与合规体系（合并 PDF 7 + 补充）。
  - 删除 2 个重复 PDF（V4.0 架构下划线版、数据收集 V1.0 旧版）。
- 验证结果：
  - 关键数字指标（8大Agent/17模块/9架构层/7层情报/六层闭环/22项风险/10张数据表）均在合并文档中完整保留。
  - `raw-docs/` 已在 `.gitignore` 中排除。
- 未完成事项：
  - 5 份合并文档的英文版已在上方“双语闭环”条目中补齐。

### 产品全版本里程碑路线图

- 变更类型：文档维护。
- 影响模块：里程碑文档和产品规划。
- 主要变更：
  - 新增跨版本产品里程碑路线图 `docs/en/milestones/product-milestones.md`。
  - 新增对应中文版本 `docs/zh-CN/milestones/product-milestones-zh-CN.md`。
  - 最初明确 V1 是当前生效实施版本，V2/V3/V4 仅作为规划目标；该表述已被上方“里程碑阶段重对齐”取代。
- 验证结果：
  - Markdown 双语配对检查返回 `NO_MISSING_PAIRS`。
  - 旧命名和过期引用扫描无匹配结果。
- 未完成事项：
  - 已被上方阶段重对齐取代；当前延后详细里程碑为 V2 和 V3。

## 2026-07-04

### 双语文档补齐

- 变更类型：文档维护。
- 影响模块：根目录文档、Android 文档、API 文档、数据库文档、部署文档、里程碑文档、验收文档和标准文档。
- 主要变更：
  - 统一双语命名：英文使用默认 `*.md`，中文使用 `*-zh-CN.md`。
  - 将已有双语文件迁移到新命名，包括 `AGENTS.md`、`AGENTS-zh-CN.md`、`CHANGELOG.md` 和 `CHANGELOG-zh-CN.md`。
  - 为历史单语文档补齐中文版本。
  - 在 Agent 开发规范和根目录 `AGENTS.md` 中同步新命名规则。
  - 更新双语文档审计报告，确认当前范围内 Markdown 文档无剩余缺口，依赖、构建和缓存文件除外。
- 验证结果：
  - 已检查依赖、构建和缓存目录之外的 Markdown 文档。
  - 已确认需要维护双语的英文 `*.md` 文档都有对应 `*-zh-CN.md` 文件。
- 未完成事项：
  - 当前范围内 Markdown 文档无剩余缺口。

## 2026-07-04

### 文档规范

- 变更类型：文档与流程规范。
- 影响模块：根目录 Agent 规范、文档标准、change log、里程碑维护流程。
- 主要变更：
  - 新增根目录 `AGENTS.md`，作为 Agent 开发规范主入口。
  - 新增英文根目录版本 `AGENTS.md`。
  - 新增要求：每次功能变更必须同步更新 `CHANGELOG.md` 和 `CHANGELOG-zh-CN.md`。
  - 新增要求：每次里程碑进度更新必须同步更新里程碑文档。
  - 新增文档双语维护审计报告。
- 验证结果：
  - 已检查双语关键规则在中文与英文规范中存在。
  - 已生成当时缺少双语维护的文档清单。
- 未完成事项：
  - 已由双语文档补齐变更关闭。详见 `docs/zh-CN/standards/documentation-bilingual-audit-zh-CN.md`。
## 2026-07-07

### 会话侧边栏归档与删除动作

- 变更类型：功能开发。
- 影响模块：`apps/web`、`services/api` 和两份变更日志。
- 主要变更：
  - 为 API 新增基于状态的会话软删除能力，增加 `POST /api/conversations/{id}/delete` 接口，仅允许删除已归档会话，并让普通会话列表默认过滤 `DELETED` 记录。
  - 更新 Web 会话工作区辅助逻辑，使已删除会话不会再进入活跃/归档分区，同时让侧边栏可根据分区返回对应的标题和行级动作定义。
  - 在 Web 会话侧边栏中加入直接归档与删除按钮，并接通页面级状态更新、选中项回退和操作提示。
- 验证结果：
  - 在 `apps/web` 运行 `npm test -- conversation-workspace.test.mjs`，验证已删除会话过滤以及分区化侧边栏标题/动作回归测试通过。
  - 在 `services/api` 运行 `mvn -Dtest=BusinessWorkflowApiTest#archivedConversationCanBeSoftDeletedAndDisappearsFromList test`，验证已归档会话可被软删除且不会再出现在会话列表中。
- 未完成事项：
  - 仍建议做一次真实浏览器复验，确认行级动作的可发现性以及归档/删除流程在真实数据和本地化文案下都符合预期。

## 2026-07-08
### Admin V3 中文文档修复

- 变更类型：文档修复。
- 影响模块：`docs/zh-CN/admin-v3-ui-design-zh-CN.md` 和两份变更日志。
- 主要变更：
  - 将 Admin V3 中文界面设计文档重新保存为 `UTF-8 with BOM`，避免常见 Windows 编辑器误判编码后出现中文乱码。
  - 恢复中文文档中损坏的 `## 12. Admin-V3-3 当前实现状态` 段落，使其再次与配套英文文档保持一致。
- 验证结果：
  - 已验证修复后的文档包含 UTF-8 BOM，并可按正确中文内容读取。
  - 已对照 `docs/en/admin-v3-ui-design.md` 末尾内容，确认恢复后的范围与实现说明一致。
- 未完成事项：
  - 本次仅为文档修复，无需运行服务测试套件。

## 2026-07-09

### 核心代码文件与外部交互文档补充

- 变更类型：文档更新。
- 影响模块：`docs/en`、`docs/zh-CN` 和两份变更日志。
- 主要变更：
  - 新增一份双语架构参考文档，按运行模块梳理核心代码文件，覆盖 Web 工作台、Admin V3 页面、API 控制器/服务/存储、AI worker 编排文件、collector 治理/韧性文件以及基础设施入口文件。
  - 补充浏览器、API、AI worker、collector、Redis、MySQL、Qdrant 与 OpenAI-compatible 模型提供方之间的真实外部交互边界，并明确区分内部调用关系与外部依赖关系。
  - 新增诊断 SSE、学习/模式切换 SSE、报告导出、后台采集与治理、知识发布同步、collector 去重与韧性等端到端流程摘要。
- 验证结果：
  - 已将新文档内容与 `.codegraph/codegraph.db`、仓库运行入口代码以及现有组件交互/系统架构文档逐项核对。
  - 已确认英文与中文文档覆盖相同的模块范围、文件职责和交互流程。
- 未完成事项：
  - 本次仅为文档更新，未执行运行时测试套件。

### Alert 调度器采集运行表修复

- 变更类型：功能缺陷修复。
- 影响模块：`services/api` 和两份变更日志。
- 主要变更：
  - 将 `AlertRuleEngine` 的采集运行遥测查询切换到真实存在的 `admin_collection_job_runs`，不再引用不存在的旧表名 `collection_job_runs`。
  - 将采集失败率窗口统计的时间列改为真实 schema 中的 `start_time`，不再使用不存在的 `run_time` 列。
  - 让定时告警评估重新与 Admin V3 已落地的采集遥测表结构保持一致。
- 验证结果：
  - 已确认调度器报错堆栈直接指向 `AlertRuleEngine.evaluateCollectorFailureRate`。
  - 已核对 MySQL 与 H2 基线 schema 都创建的是 `admin_collection_job_runs`，且管理后台采集存储逻辑也统一使用该表。
  - 已在修复后重新检索 API 源码，确认告警规则中不再残留 `collection_job_runs` 引用。
- 未完成事项：
  - 仍需在目标运行环境连真实数据库跑一次调度周期，确认告警评估已端到端不再抛出 SQL 异常。

### MySQL V2 管理后台迁移兼容性修复

- 变更类型：功能缺陷修复。
- 影响模块：`services/api`、`infra/mysql` 和两份变更日志。
- 主要变更：
  - 从 MySQL 的 `V2__admin_schema_and_seed.sql` 迁移脚本中删除两条 `ALTER TABLE admin_collection_sources ADD COLUMN IF NOT EXISTS ...` 语句。
  - 保留 V2 其余种子数据逻辑不变，因为 `compliance_notes` 与 `proxy_config` 已经存在于 MySQL 基线初始化 schema 中。
  - 避免 Flyway 在执行 V2 迁移启动时因 MySQL 返回 SQL state `42000` / error code `1064` 而失败。
- 验证结果：
  - 已确认报错语句位于 `services/api/src/main/resources/db/migration/mysql/V2__admin_schema_and_seed.sql`。
  - 已确认 `infra/mysql/init/001_v1_baseline.sql` 中的 `admin_collection_sources` 已包含 `compliance_notes` 和 `proxy_config`，删除 V2 中的重复补列不会破坏 schema 完整性。
  - 已完成静态迁移检查，确认删除重复补列后其余 V2 语句的执行顺序保持不变。
- 未完成事项：
  - 仍需在目标 MySQL 环境重新执行 Flyway 迁移，确认应用启动已端到端恢复正常。

### Admin-V3-3 ֪ʶ������ʵ�ջ�

- ������ͣ����ܿ�����
- Ӱ��ģ�飺`services/api`��`infra/mysql`��`apps/web`��Admin ����ĵ��Լ���Ӣ�ı����־��
- ��Ҫ�����
  - ������ʽ֪ʶ����־û��ṹ `admin_knowledge_nodes`��`admin_knowledge_versions`��`admin_knowledge_publications`����ͬ������ MySQL ��ʼ���ű���H2 ���� schema �� API ����Զ������߼���
  - ������ʵ `/api/admin/knowledge/**` �ӿڣ����ǽڵ��б���ڵ����顢�ݸ屣�桢�ύ���ˡ��ڶ�������������������ع��Ͱ汾�Աȡ�
  - �����ѷ���֪ʶ��д `knowledge_items` ��ͬ���߼�����֤����Ա�������֪ʶ������������û��������·��
  - ��չ `/admin`���������� Knowledge ��������֧�ֽڵ㵼�����ݸ�༭���汾����������鿴���ع��ͷ�����ʷ��
  - ͬ������ Admin-V3-3 �������ڵĺ�˲�����ǰ��Դ�뼶�ع鸲�ǡ�
- ��֤�����
  - �ѶԱ�ṹ���塢������·�ɡ���������У�����ǰ�� API client��Admin ҳ����ߺ�Դ�뼶���Բ�����о�̬�˶ԡ�
  - ��ǰִ�л���δ�ṩ `mvn`��`node` �� `npm`����˱����޷��ڴ˻���ʵ��ִ�� Maven �� Web �������
- δ������
  - ���ھ߱� Maven �� Node.js �������Ļ��������к�˲��Ժ� Web ���ԡ�
  - ��ǰ�˹��������ú��Խ���� `/admin` ֪ʶ������ִ��һ����ʵ�������֤��

### API MyBatis-Plus 迁移自检收口

- 变更类型：功能缺陷修复与回归对齐。
- 影响模块：`services/api`、`services/api/src/test/resources`、`services/api/src/test/java` 和两份变更日志。
- 主要变更：
  - 完成 API 管理后台路径中剩余的 `JdbcTemplate` 到 MyBatis-Plus/MyBatis Mapper 的迁移收尾，并重新确认 `services/api/src/main/java/com/bizsage/api` 已无 `JdbcTemplate` 引用。
  - 为 H2 管理后台种子数据中带显式 ID 的启动记录补齐自增序列重置，覆盖 `admin_knowledge_nodes`、`admin_knowledge_versions` 和 `admin_collection_sources`。
  - 在迁移后的管理后台 store 中补充 `CLOB` 到字符串的兼容转换，确保 H2 测试环境下的知识内容与采集 `payloadJson` 能稳定通过 Mapper 读写。
  - 将 `/api/users`、`/api/conversations`、`/api/intelligence`、`/api/knowledge` 的回归测试断言更新为当前分页响应契约，并去掉对后台审核/审计列表首条顺序的脆弱假设。
- 验证结果：
  - 已验证 `rg -n "JdbcTemplate" services/api/src/main/java/com/bizsage/api` 无任何匹配结果。
  - 已验证 `services/api` 下执行 `mvn -q -DskipTests compile` 通过。
  - 已验证 `services/api` 下执行 `mvn -q "-Dtest=AdminV3ApiTest,GovernanceApiTest,V2GrayReleaseApiTest,AuthAndRbacTest,BusinessWorkflowApiTest" test` 通过。
- 未完成事项：
  - 如果团队需要超出迁移相关范围的更高信心，仍建议补跑更大范围的后端全量回归测试。

## 2026-07-11

### 学习与诊断引导式工作流实现进度回顾

- 变更类型：文档评审。
- 影响模块：`docs/superpowers/specs`、`apps/web`、`services/api`、`services/ai-worker` 和两份变更日志。
- 主要变更：
  - 在配对的学习与诊断引导式工作流设计文档中新增“实现进度回顾”章节。
  - 按原始规格逐项记录当前数据库、后端 API、AI Worker 行为和前端体验的完成情况。
  - 汇总哪些能力已经实现、哪些还停留在骨架阶段、哪些关键工作流行为仍然缺失，包括诊断状态机、动态推荐刷新、会话工作流回写以及学习侧前端流程。
  - 额外整理了 5 个最高优先级缺口，便于后续直接据此拆分实现任务。
- 验证结果：
  - 已重新核对配对的中英文设计文档，确认两份文档在范围和结论上保持一致。
  - 已将回顾内容与 `apps/web`、`services/api`、`services/ai-worker`、会话工作流存储逻辑以及问题池 schema 的当前实现逐项交叉核对。
  - 已确认本次更新仅修改文档，不涉及运行时代码行为变化。
- 未完成事项：
  - 文档中标记的工作流缺口仍需在前端、API、AI Worker 与持久化链路中继续实现。

### 学习与诊断 Agent 引导式工作流设计

- 变更类型：文档设计。
- 影响模块：`apps/web`、`services/api`、`services/ai-worker`、配对设计文档和两份变更日志。
- 主要变更：
  - 新增学习与诊断 Agent 引导式工作流设计，补齐自我介绍、右侧快捷提问栏、动态推荐刷新、问题池持久化排序和诊断结束条件。
  - 明确学习 Agent 需要基于当前对话实时推荐下一步可能想了解的行业节点、区块、方向与细节，并引入行业级 top 问题池，结合 LLM 判断、top 级别、使用次数和评分进行排序。
  - 明确诊断 Agent 需要先介绍自身功能与交互逻辑，在前期给出诊断计划并主动追问经营画像缺口，右侧推荐最可能出现的经营问题，必要时提示用户继续补充信息后再生成诊断报告。
  - 设计了后端会话状态、推荐栏、问题池和诊断状态接口，以及 AI Worker 输出结构与 prompt 约束的扩展方案。
- 验证结果：
  - 已对照现有原始设计文档与当前实现链路完成静态梳理。
  - 已确认本次仅新增设计文档，不涉及运行时代码变更。
- 未完成事项：
  - 尚未实现 API、AI Worker、前端与持久化逻辑。

