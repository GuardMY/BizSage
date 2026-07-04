# BizSage V1 MVP 里程碑

## V1 范围

V1 是内部验证 MVP，覆盖从数据采集、数据治理、知识库、RAG 检索、诊断 Agent 输出、Web 对话到简易运营后台的 P0 链路。

系统必须验证：

- 四类数据源可以生成标准化记录；
- 治理链路可以清洗、去重、加权并存储知识；
- RAG 可以检索证据；
- 诊断 Agent 可以输出带来源和免责声明的回答；
- Web 用户可以完成登录、会话、诊断和溯源查看；
- 运营人员可以录入、查看并审核情报。

## 不在范围内

- Android 实现。
- 付费会员和权益计费。
- PDF 报告生成。
- 多模型路由。
- 动态信源权重。
- 时序快照和历史回滚。
- 高可用、灾备和完整监控大盘。
- 完整 V2/V3 商业化运营流程。

## 模块地图

| 模块 | 职责 | 输入 | 输出 | 依赖 | V1 交付物 | 验收 |
| --- | --- | --- | --- | --- | --- | --- |
| `apps/web` | Web 诊断与运营后台 | API 响应、认证 token、流事件 | 用户操作、诊断展示、运营表单 | `services/api` | 登录、聊天、溯源弹窗、情报录入/列表、用户列表 | 用户和运营流程可完成 |
| `apps/android` | 未来 Android 占位 | API 契约文档 | 仅 README | 无 | 延后说明 | V1 不要求 Android 代码 |
| `services/api` | 主网关 API | Web 请求、worker 响应、MySQL/Redis 数据 | 统一 API 响应 | MySQL、Redis、AI worker、collector | 认证、RBAC、用户、会话、情报、知识元数据 | 测试通过且响应结构一致 |
| `services/ai-worker` | RAG 与诊断 | 查询、用户上下文、知识证据 | 诊断回答和来源 | Qdrant、API、LLM provider | 关键词/向量检索、重排、mock/OpenAI-compatible LLM | 有依据回答和无依据兜底 |
| `services/collector` | 数据采集 | 表单、Excel、URL、mock API 配置 | 标准化原始记录 | API、MySQL-compatible schema | 四类 V1 source adapter | 所有源类型可生成标准化记录 |
| `infra` | 本地运行底座 | 环境变量 | 运行中的 MySQL、Redis、Qdrant | Docker | Compose 文件、迁移、脚本 | 健康检查通过 |
| `docs` | 交付知识 | 计划和实现行为 | 运行手册和验收文档 | 所有模块 | API、DB、部署、验收文档 | 新开发者可运行 V1 |

## 里程碑计划

### M0 仓库与规范

- 初始化 git 仓库。
- 创建 monorepo 布局。
- 添加 README、环境模板、贡献说明、API 响应规范、数据库规范、Android 占位和本里程碑文档。

验收：仓库有可理解的启动说明，模块边界清晰。

### M1 基础设施

- 添加 MySQL、Redis、Qdrant 和本地服务网络的 Docker Compose。
- 添加数据库基线迁移。
- 添加单机部署脚本和环境示例。

验收：本地基础设施可启动并通过健康检查。

### M2 核心 API

- 实现 Spring Boot 认证、JWT、角色、统一响应、请求日志和基础限流。
- 实现角色：`SUPER_ADMIN`、`OPERATOR`、`USER`。
- 实现用户、会话、消息、情报和知识元数据存储。

验收：认证/RBAC 测试通过；API 返回 `code/message/data/requestId`。

### M3 数据采集

- 实现用户私有经营数据、人工情报、低反爬公开网页抓取和 mock 第三方 API provider。
- 标准化 source tag、industry ID、link ID、region ID、confidence 和 weight。

验收：四类源都能写入标准化原始记录。

### M4 数据治理与知识库

- 实现字段归一化、URL 去重、SimHash 去重、基础谣言关键词过滤和固定信源权重。
- 实现静态行业基线知识导入和人工/批量录入。
- 实现用户私有敏感数据 AES 加密和脱敏展示。

验收：重复输入能正确合并；敏感数据不会明文存储或展示。

### M5 RAG 与 Agent

- 实现关键词检索、向量检索、信源权重重排和上下文截断。
- 实现 OpenAI-compatible LLM adapter 和 mock fallback。
- 实现诊断链路：用户问题 -> RAG -> 模型 -> 带来源回答。
- 强制无依据兜底。

验收：诊断输出包含来源、时效、置信提示和免责声明。

### M6 Web 应用

- 实现 Next.js 登录、会话列表、流式诊断聊天和溯源弹窗。
- 实现运营页：情报录入、情报列表、用户列表。
- UI 保持工作台式、信息密集、克制。

验收：用户可完成登录 -> 新建会话 -> 提问诊断 -> 查看来源；运营可录入并审核情报。

### M7 集成与文档

- 连接 Web、Spring API 和 Python workers。
- 添加 OpenAPI 摘要、数据库设计、部署文档和 V1 验收指南。

验收：新开发者可按文档运行完整栈。

### M8 验证与加固

- 运行后端、Python、Web 测试和基础 50 并发请求压测。
- 运行手机号和身份证脱敏检查。
- 记录 4 小时单节点稳定性结果或明确待验证项。

验收：V1 检查项通过，或失败项有明确修复任务。

## 验收清单

- [ ] 四类数据源采集路径可用。
- [ ] 诊断回答包含来源。
- [ ] 手机号和身份证在响应与 UI 中脱敏。
- [ ] 设计存储路径中不持久化明文敏感值。
- [ ] 单接口可承受本地 50 并发验证。
- [ ] 单节点运行通过 4 小时观察。
- [ ] 部署文档足够新开发者使用。

## 假设

- V1 Android 有意延后，只保留 `apps/android/README.md`。
- 第三方 API 采集在提供真实凭证前保持 mock/可插拔。
- LLM 使用 OpenAI-compatible 配置，未配置密钥时使用 mock 模式。
- V2/V3 功能不在范围内，除非后续里程碑文档扩展范围。
