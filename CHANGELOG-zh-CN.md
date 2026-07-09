# 变更日志

## 2026-07-09

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

