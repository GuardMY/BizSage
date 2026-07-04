# V2 验证结果

验证日期：2026-07-04

## 当前环境已通过

- API 完整测试套件：在 `services/api` 运行 `mvn test`
  - 结果：12 个测试通过，0 个失败。
- Collector 完整测试套件：在 `services/collector` 运行 `python -m pytest`
  - 结果：11 个测试通过，0 个失败。
- AI worker 完整测试套件：在 `services/ai-worker` 运行 `python -m pytest`
  - 结果：6 个测试通过，0 个失败。
- Web API client 测试：在 `apps/web` 运行 `npm test`
  - 结果：2 个测试通过，0 个失败。
- Web 生产构建：在 `apps/web` 运行 `npm run build`
  - 结果：Next.js 生产构建成功完成。

## 已覆盖的 V2 验收项

- V2 登录画像返回 `membershipLevel` 和 `consultationPreferences`。
- 种子付费用户 `seed_paid` 可登录用于灰度验证。
- 付费情报通过独立 `/api/paid-intelligence` API 管理，并对免费用户隐藏。
- 经营诊断报告导出元数据包含来源、时效、置信、自检状态和免责声明。
- 免费用户报告排除付费依据；种子付费用户可看到已审核付费依据。
- 仅运营角色可访问运维指标、复核工单、告警和审计日志 API。
- Collector 韧性辅助能力覆盖增量指纹、重试耗尽、熔断打开、死信分类和近期快照兜底。
- AI 检索按地域、行业和权益过滤；自检可用受控输出阻止存疑冲突结论。

## 当前环境未执行

- Docker Compose 启动和 MySQL/Redis/Qdrant 健康检查。
  - 原因：V1 验证时当前环境未提供 Docker CLI。
  - 待运行命令：`docker --version`，随后运行 `infra\scripts\start-local.ps1`。
  - 补救步骤：安装 Docker Desktop 或将 Docker CLI 暴露到 PATH 后重新执行基础设施启动。
- 备份与恢复演练。
  - 原因：需要正在运行的 Docker 容器和可持久化测试数据。
  - 待运行命令：`infra\scripts\backup-mysql.ps1`、`infra\scripts\restore-mysql.ps1 -InputPath <backup.sql>`、`infra\scripts\backup-qdrant.ps1`、`infra\scripts\restore-qdrant.ps1 -InputPath <backup.tar>`。
  - 补救步骤：在具备 Docker 的预发机器执行，并记录 RTO/RPO。
- 50 并发请求压测。
  - 原因：Docker 验证未完成，完整本地栈尚未启动。
  - 待运行命令：栈启动后用压测工具请求 `/api/health` 和一个已认证诊断端点。
  - 补救步骤：Docker 健康检查通过后执行。
- V1 4 小时稳定性观察和 V2 7 天灰度稳定性。
  - 原因：需要可长时间运行的部署环境。
  - 待运行命令：在要求时长内监控 API、collector、AI worker、Web、Redis、MySQL 和 Qdrant。
  - 补救步骤：在预发或灰度发布期间安排观察并记录故障。

## 当前退出状态

V2 已进入实施中，但尚不能退出。功能性灰度骨架检查已通过，恢复演练、压测、Docker 健康检查和灰度稳定性证据仍未完成。
