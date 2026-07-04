# 贡献指南

## 开发规则

- 保持模块边界清晰。Web 只调用 `services/api`，不得直接调用 Python worker 或数据库。
- V1 聚焦 P0 范围。除非先更新里程碑文档，否则不要添加 V2/V3 功能。
- 行为变更优先先写测试，再写实现。
- 保持统一 API 响应：`code`、`message`、`data`、`requestId`。
- 永远不要明文持久化敏感用户数据。
- 所有文档必须维护英文默认 `*.md` 和中文 `*-zh-CN.md` 两个版本。
- 每次功能变更必须同步更新 `CHANGELOG.md` 和 `CHANGELOG-zh-CN.md`。
- 每次里程碑进度更新必须同步更新对应里程碑文档。

## 提交风格

使用简洁的里程碑提交信息：

- `chore: initialize v1 monorepo`
- `feat: add core api auth`
- `feat: add collector ingestion`
- `feat: add rag diagnosis worker`
- `feat: add web console`
