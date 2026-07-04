# 文档双语维护审计

审计日期：2026-07-04

## 审计范围

本次审计检查仓库内已跟踪或工作区内的 Markdown 文档，不包含依赖目录、构建产物和测试缓存。

## 已实现双语维护

- `AGENTS.md`
- `AGENTS.en.md`
- `CHANGELOG.zh-CN.md`
- `CHANGELOG.en.md`
- `docs/standards/agent-development.zh-CN.md`
- `docs/standards/agent-development.en.md`
- `docs/standards/documentation-bilingual-audit.zh-CN.md`
- `docs/standards/documentation-bilingual-audit.en.md`

## 尚未实现双语维护的文档

以下文档目前只有单一语言版本，后续修改任意一项时必须同步创建或更新对应语言版本：

- `README.md`
- `CONTRIBUTING.md`
- `apps/android/README.md`
- `docs/api/openapi-summary.md`
- `docs/database/v1-schema.md`
- `docs/deployment/local-deployment.md`
- `docs/deployment/v1-runbook.md`
- `docs/milestones/v1-acceptance.md`
- `docs/milestones/v1-mvp-milestones.md`
- `docs/milestones/v1-verification-results.md`
- `docs/standards/api-response.md`
- `docs/standards/database-convention.md`

## 后续要求

- 新增文档必须同时创建中文和英文版本。
- 修改上述单语历史文档时，必须在同一次提交中补齐对应语言版本。
- 里程碑文档后续更新时，必须同步维护中英文版本，并记录状态、完成项、阻塞项、验证结果和下一步。
