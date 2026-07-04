# 文档双语维护审计

审计日期：2026-07-04

## 审计范围

本次审计检查仓库内已跟踪或工作区内的 Markdown 文档，不包含依赖目录、构建产物和测试缓存。

## 命名规则

- 英文文档默认使用 `*.md` 文件名。
- 中文文档使用对应的 `*-zh-CN.md` 文件名。

示例：

- `README.md`
- `README-zh-CN.md`
- `docs/api/openapi-summary.md`
- `docs/api/openapi-summary-zh-CN.md`

## 已实现双语维护

- `AGENTS.md` / `AGENTS-zh-CN.md`
- `CHANGELOG.md` / `CHANGELOG-zh-CN.md`
- `README.md` / `README-zh-CN.md`
- `CONTRIBUTING.md` / `CONTRIBUTING-zh-CN.md`
- `apps/android/README.md` / `apps/android/README-zh-CN.md`
- `docs/api/openapi-summary.md` / `docs/api/openapi-summary-zh-CN.md`
- `docs/database/v1-schema.md` / `docs/database/v1-schema-zh-CN.md`
- `docs/deployment/local-deployment.md` / `docs/deployment/local-deployment-zh-CN.md`
- `docs/deployment/v1-runbook.md` / `docs/deployment/v1-runbook-zh-CN.md`
- `docs/milestones/v1-acceptance.md` / `docs/milestones/v1-acceptance-zh-CN.md`
- `docs/milestones/v1-mvp-milestones.md` / `docs/milestones/v1-mvp-milestones-zh-CN.md`
- `docs/milestones/v1-verification-results.md` / `docs/milestones/v1-verification-results-zh-CN.md`
- `docs/milestones/product-milestones.md` / `docs/milestones/product-milestones-zh-CN.md`
- `docs/standards/agent-development.md` / `docs/standards/agent-development-zh-CN.md`
- `docs/standards/api-response.md` / `docs/standards/api-response-zh-CN.md`
- `docs/standards/database-convention.md` / `docs/standards/database-convention-zh-CN.md`
- `docs/standards/documentation-bilingual-audit.md` / `docs/standards/documentation-bilingual-audit-zh-CN.md`

## 尚未实现双语维护的文档

当前审计范围内没有剩余 Markdown 文档缺口。

生成/缓存类 Markdown 文件，例如 `.pytest_cache/README.md`，不纳入项目文档维护范围。

## 后续要求

- 新增文档必须同时创建英文和中文版本。
- 修改文档时，必须在同一次提交中更新对应语言版本。
- 里程碑文档后续更新时，必须同步维护英文和中文版本，并记录状态、完成项、阻塞项、验证结果和下一步。
