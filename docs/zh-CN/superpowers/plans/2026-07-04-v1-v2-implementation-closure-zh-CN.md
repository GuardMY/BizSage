# V1/V2 实装收敛实施计划

> **面向 agent 工作者：** 必须使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务执行本计划。步骤使用复选框（`- [ ]`）追踪。

**目标：** 关闭 V1/V2 前五项实现差距：持久化、可读文本、Web 真实 API、验证记录和 V2 骨架数据可追踪。

**架构：** 保持控制器契约稳定，用聚焦的 `JdbcTemplate` 仓库替换 API 内存存储。AI worker 与 Web 保持简单结构，同时把可见行为接入真实数据。

**技术栈：** Spring Boot、JDBC、MySQL 兼容 schema、FastAPI/Pydantic、Next.js/React、Node tests、pytest、Maven。

---

### 任务 1：API 持久化

- [ ] 为会话、情报、付费情报和知识的仓库持久化行为添加失败测试。
- [ ] 使用现有 schema 将内存 store 替换为 JDBC 实现。
- [ ] 保持控制器响应 envelope 不变。
- [ ] 在 `services/api` 执行 `mvn test`。

### 任务 2：可读文本

- [ ] 添加或更新测试，断言诊断、信息不足和免责声明文本为可读中文。
- [ ] 替换 Java、Python、Web 和 SQL 中的乱码 seed 文案。
- [ ] 执行 API、AI worker 和 Web 测试。

### 任务 3：Web 真实 API 流程

- [ ] 为 `api-client` 解析和数据加载 helper 添加或调整 Web 测试。
- [ ] 将登录、诊断提交、付费情报、报告元数据和运维指标接入 API 调用。
- [ ] 仅在错误或空状态保留静态兜底。
- [ ] 在 `apps/web` 执行 `npm test` 和 `npm run build`。

### 任务 4：验证记录

- [ ] 执行当前环境可运行的验证命令。
- [ ] 使用最新结果和阻塞项更新 V1/V2 验证文档。
- [ ] 更新英文和中文变更日志。

### 任务 5：V2 可追踪性

- [ ] 在可行范围内，将 V2 报告、审核、审计和运维数据持久化或从现有表查询。
- [ ] 保持 V3 商业流程不进入本轮范围。
- [ ] 文档更新后再次执行完整验证命令。
