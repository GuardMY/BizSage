# BizSage Agent 开发规范

本文件是仓库根目录的 Agent 行为规范。AI 编码 Agent、自动化开发 Agent 和人工协作者在本项目中工作时必须遵循本文件。

## 1. 基本原则

- 开始修改前，先阅读相关项目文档、现有代码结构和当前 git 状态。
- 后续所有功能开发还必须阅读并遵循以下产品、架构、实现、数据采集和风险合规治理文档：
  - `docs/en/product-strategy-and-design.md` / `docs/zh-CN/product-strategy-and-design-zh-CN.md`
  - `docs/en/system-architecture-and-framework.md` / `docs/zh-CN/system-architecture-and-framework-zh-CN.md`
  - `docs/en/development-implementation-guide.md` / `docs/zh-CN/development-implementation-guide-zh-CN.md`
  - `docs/en/data-collection-and-intelligence-perception.md` / `docs/zh-CN/data-collection-and-intelligence-perception-zh-CN.md`
  - `docs/en/risk-management-and-compliance.md` / `docs/zh-CN/risk-management-and-compliance-zh-CN.md`
- 优先沿用仓库已有模式；没有模式时，选择最小、清晰、可测试的实现。
- 不实现当前里程碑以外的功能，除非里程碑文档已经同步更新。
- 不回滚、覆盖或删除他人已有改动；遇到相关冲突时先理解再处理。
- 保持模块目录隔离：`apps/web`、`apps/android`、`services/api`、`services/ai-worker`、`services/collector`、`infra`、`docs` 各自维护边界。

## 2. 文档双语维护

- 所有项目文档都必须维护中文和英文两个版本。
- 英文文档默认使用 `*.md` 文件名。
- 中文文档使用对应的 `*-zh-CN.md` 文件名。
- `docs/` 下文档按语言归档：英文位于 `docs/en/`，中文位于 `docs/zh-CN/`。
- 新增或修改文档时，必须在同一次改动中新增或更新对应的中文和英文版本。
- 如果对应语言版本还不存在，完成前必须创建。
- 两个语言版本必须表达同一事实、同一范围、同一验收标准；不得让其中一个版本成为过期摘要。
- 涉及接口、数据库、部署、里程碑、验收、开发规范的文档变更，必须同时检查对应语言版本。

## 3. Change Log 维护

- 每次功能变更都必须写入 change log。
- 英文 change log 使用 `CHANGELOG.md`。
- 中文 change log 使用 `CHANGELOG-zh-CN.md`。
- 同一次功能变更必须同时更新两个 change log 文件。
- 记录内容至少包含日期、变更类型、影响模块、主要变更、验证结果和未完成事项。
- 文档-only 变更也必须记录，除非只是修正拼写且不改变含义。

## 4. 项目里程碑维护

- 每次里程碑进度有更新，必须同步更新对应里程碑文档。
- 里程碑文档必须维护中文和英文两个版本。
- 更新内容至少包含当前状态、完成项、阻塞项、验证结果和下一步。
- 如果某项验收无法在当前环境执行，必须在里程碑或验证结果文档中写明原因、已尝试命令和补救步骤。
- 禁止只在聊天记录或提交信息中记录里程碑进展；必须落到仓库文档中。

## 5. 实施流程

- 开始前读取相关里程碑、API、数据库和部署文档。
- 对新增行为优先写测试，再写实现。
- 里程碑按计划顺序推进；跨里程碑改动必须说明原因。
- 每个独立里程碑完成后运行对应验证命令，并用清晰提交记录保存。
- 如果验证无法执行，必须记录为未执行项，而不是声明通过。

## 6. 代码规范

- 后端 API 统一返回 `code`、`message`、`data`、`requestId`。
- 敏感数据不得明文持久化或写入日志。
- AI 输出必须包含依据、时效、置信提示和免责声明；无依据时返回信息不足。
- Web 只能调用 `services/api`，不得直接调用 worker 或数据库。
- Python worker 与 collector 通过明确接口交换结构化数据。

## 7. 验证规范

- API：在 `services/api` 运行 `mvn test`。
- Collector：在 `services/collector` 运行 `python -m pytest`。
- AI worker：在 `services/ai-worker` 运行 `python -m pytest`。
- Web：在 `apps/web` 运行 `npm test` 和 `npm run build`。
- Docker、并发压测、长稳验证如果受环境限制无法执行，必须记录为未执行项，而不是声明通过。

## 8. 提交规范

- 里程碑提交使用清晰动词，例如 `chore:`, `feat:`, `docs:`。
- 文档变更提交必须同时包含中英文版本。
- 不把 `raw-docs/`、本地环境文件、构建产物、依赖目录提交到仓库。
- 提交前检查 `git status --short`，确认没有误加入无关文件。
