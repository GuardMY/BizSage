# 仓库指南

## 项目结构与模块组织

BizSage 按产品边界拆分。`apps/web` 是 Next.js Web 应用，包含 React UI、API client 和 `apps/web/tests` 下的 Node 测试。`services/api` 是 Spring Boot API，Java 源码位于 `src/main/java`，测试与测试资源位于 `src/test`。`services/collector` 与 `services/ai-worker` 是 Python 服务，应用代码在 `app/`，pytest 测试在 `tests/`。`infra` 保存 Docker、数据库和本地启动脚本。产品、架构、部署、API、数据库、里程碑和验证文档按双语维护在 `docs/en` 与 `docs/zh-CN`。

保持模块边界清晰：Web 只能调用 `services/api`；collector 与 worker 通过显式结构化接口交换数据。

## 构建、测试与开发命令

- `cd apps/web && npm run dev`：在 `3000` 端口启动 Web。
- `cd apps/web && npm test`：运行 `tests/*.test.mjs`。
- `cd apps/web && npm run build`：验证 Next.js 生产构建。
- `cd services/api && mvn test`：运行 Spring Boot 与 API 契约测试。
- `cd services/collector && python -m pytest`：运行 collector 测试。
- `cd services/ai-worker && python -m pytest`：运行 AI worker 测试。
- `./infra/scripts/start-all.ps1`：在 Docker 和服务依赖可用时启动本地完整栈。

## 编码风格与命名约定

新增抽象前先沿用本地既有风格。Java 使用 `com.bizsage.api` 包名、Spring Boot 约定，并通过 `code`、`message`、`data`、`requestId` 返回统一 API 响应。Python 文件使用 snake_case，pytest 文件示例为 `test_collectors.py`。Web 使用 TypeScript/React 约定、组件化命名，UI 图标优先使用 lucide。敏感数据不得明文持久化或写入日志。

## 测试规范

行为变更应在所属模块增加聚焦测试。Web 测试使用 `*.test.mjs`，API 测试使用 `*Test.java`，Python 服务测试使用 `test_*.py`。如果 Docker、压测或长稳验证无法在本地执行，记录为未执行，不要声明通过。

## 提交与 Pull Request 规范

近期提交使用 `feat:`、`docs:`、`chore:` 等简洁前缀。每次提交聚焦一个逻辑变更，避免加入 `raw-docs/`、环境文件、构建产物、依赖目录或无关工作区改动。PR 应说明变更、列出影响模块、关联 issue 或里程碑；UI 变更提供截图，并报告实际执行的验证命令。

## 文档与 Agent 注意事项

文档变更必须同步更新英文和中文版本，并更新 `CHANGELOG.md` 与 `CHANGELOG-zh-CN.md`，除非只是无语义变化的拼写修正。仓库存在 `.codegraph/` 时，理解代码路径前应先使用 CodeGraph，再使用 grep 或手动读取文件。

## Agent 开发标准

开始功能开发前，阅读相关产品、架构、实现、数据采集和风险合规治理文档：

- `docs/en/product-strategy-and-design.md` / `docs/zh-CN/product-strategy-and-design-zh-CN.md`
- `docs/en/system-architecture-and-framework.md` / `docs/zh-CN/system-architecture-and-framework-zh-CN.md`
- `docs/en/development-implementation-guide.md` / `docs/zh-CN/development-implementation-guide-zh-CN.md`
- `docs/en/data-collection-and-intelligence-perception.md` / `docs/zh-CN/data-collection-and-intelligence-perception-zh-CN.md`
- `docs/en/risk-management-and-compliance.md` / `docs/zh-CN/risk-management-and-compliance-zh-CN.md`

优先沿用仓库既有模式，并选择最小、清晰、可测试的实现。除非同步更新里程碑文档，否则不要实现当前里程碑之外的功能。不要回滚、覆盖或删除他人的工作；遇到相关改动时先理解再处理。

## 双语文档与变更日志

所有项目文档都必须维护英文和中文版本。英文文件使用 `*.md`，中文文件使用匹配的 `*-zh-CN.md`。两个版本必须表达同一事实、范围和验收标准。涉及 API、数据库、部署、里程碑、验收或开发标准的文档变更，必须检查对应语言文件。

每次功能变更，以及会改变含义的文档-only 变更，都必须更新 `CHANGELOG.md` 和 `CHANGELOG-zh-CN.md`。每条记录至少包含日期、变更类型、影响模块、主要变更、验证结果和未完成事项。

## 里程碑、实施与验证

里程碑进展发生变化时，必须同步更新中英文里程碑文档，写明状态、完成项、阻塞项、验证结果和下一步。如果验收项无法执行，记录原因、已尝试命令和补救路径。

新增行为优先写测试，再写实现。每个独立里程碑后运行对应验证命令：API 使用 `mvn test`，collector 使用 `python -m pytest`，AI worker 使用 `python -m pytest`，Web 使用 `npm test` 和 `npm run build`。如果 Docker、压测或长稳验证受环境限制无法执行，记录为未执行。

## 产品安全规则

后端 API 必须返回统一结构：`code`、`message`、`data` 和 `requestId`。AI 输出必须包含依据、时效、置信提示和免责声明；缺少依据时返回信息不足。敏感数据不得明文持久化或写入日志。
