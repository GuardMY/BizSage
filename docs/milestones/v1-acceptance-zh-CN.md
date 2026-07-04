# V1 验收记录

## 自动化验证

- API 单元/集成测试：在 `services/api` 运行 `mvn test`。
- Collector 测试：在 `services/collector` 运行 `python -m pytest`。
- AI worker 测试：在 `services/ai-worker` 运行 `python -m pytest`。
- Web 测试：在 `apps/web` 运行 `npm test`。
- Web 生产构建：在 `apps/web` 运行 `npm run build`。

## 手工验收场景

- 使用密码 `password` 登录 `admin`、`operator` 或 `user`。
- 创建并归档会话。
- 向 `/api/conversations/{id}/messages/stream` 发送诊断请求。
- 录入、列表查看并审核情报。
- 导入静态基线知识。
- 运行四类 collector 路径，然后调用 `/govern`。
- 验证 `/api/users` 中手机号和身份证字段已脱敏。

## 已知环境缺口

- 当前环境未安装 Docker CLI，因此 compose 启动和 MySQL/Redis/Qdrant 健康检查未在此处执行。
- 本会话未执行 4 小时单节点稳定性观察。
- 50 并发请求压测记录为 M8 目标，应在服务于支持 Docker 的机器启动后执行。
