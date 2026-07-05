# 变更日志

## 2026-07-05

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
