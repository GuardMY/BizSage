# V1 验证结果

验证日期：2026-07-04

## 已通过

- API 测试：在 `services/api` 运行 `mvn test`
  - 结果：12 个测试通过，0 失败。
- Collector 测试：在 `services/collector` 运行 `python -m pytest`
  - 结果：11 个测试通过，0 失败。
- AI worker 测试：在 `services/ai-worker` 运行 `python -m pytest`
  - 结果：6 个测试通过，0 失败。
- Web 测试：在 `apps/web` 运行 `npm test`
  - 结果：3 个测试通过，0 失败。
- Web 生产构建：在 `apps/web` 运行 `npm run build`
  - 结果：Next.js 生产构建成功完成。

## 已覆盖验收项

- 四类 collector source path 已由测试覆盖：
  - 用户私有表单；
  - 用户私有 Excel；
  - 公开网页；
  - mock 第三方 API。
- 诊断响应包含来源、置信度、时效和免责声明。
- 无依据诊断返回信息不足。
- 用户列表 API 返回脱敏手机号和身份证。
- RBAC 阻止普通用户查看用户列表。
- 会话创建、列表和归档流程可用。
- 情报创建、列表和审核流程可用。
- 知识导入流程可用。
- 核心 API 运行时存储已改为 JDBC 持久化路径，不再依赖内存 store。
- Web 诊断流程已通过 API client helper 连接登录、会话创建、SSE 诊断、报告、付费情报和运维指标。

## 当前环境未执行

- Docker Compose 启动和 MySQL/Redis/Qdrant 健康检查。
  - 原因：Docker CLI 未安装或不在 PATH 中。
  - 已尝试命令：`docker --version`。
- 50 并发请求压测。
  - 原因：Docker 不可用，完整本地栈未启动。
- 4 小时单节点稳定性观察。
  - 原因：需要在 Docker/运行时启动后长期运行完整本地栈。

## 完整关闭 V1 验收的后续步骤

1. 安装 Docker Desktop 或将 Docker CLI 暴露到 PATH。
2. 运行 `infra\scripts\start-local.ps1`。
3. 按 `docs/zh-CN/deployment/v1-runbook-zh-CN.md` 启动 API、collector、AI worker 和 Web。
4. 对 `/api/health` 和一个认证诊断端点运行 50 并发 smoke test。
5. 保持完整栈运行 4 小时，并记录崩溃或错误波动。
