# BizSage MySQL 数据表目录

## 范围与唯一事实来源

本文档归档截至 2026-07-11 由 `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql` 创建的 MySQL schema。API 启动时由 Flyway 统一负责 MySQL schema 的创建、校验与升级，Flyway 是唯一事实来源。

当前基线创建 30 张应用数据表。Flyway 还会自行创建并管理 `flyway_schema_history`，该基础设施元数据表不计入下方 30 张表。基线定义了索引和唯一约束，但没有声明外键约束；`user_id`、`conversation_id`、`node_id` 等字段表示逻辑关联，其完整性由应用工作流维护。

## 账号、会话与记忆

| 数据表 | 用途 | 关键关联与控制 |
| --- | --- | --- |
| `users` | 保存登录身份、角色、会员等级、咨询偏好、web/admin 共用语言偏好，以及加密的手机号和身份信息。 | 主键为 `id`，`username` 唯一；`region_id` 和 `industry_id` 定义默认业务范围。 |
| `conversations` | 保存用户拥有的诊断会话及其活跃/归档状态。 | `user_id` 逻辑关联 `users.id`，并按 `user_id` 建索引；`owner_username` 保留当前工作流使用的展示/登录归属。 |
| `messages` | 保存用户与 Agent 消息、证据来源、置信度/自检元数据和活跃上下文状态。 | `conversation_id` 逻辑关联 `conversations.id` 并建索引；`summary_group_id` 用于把消息关联到压缩分组。 |
| `conversation_summaries` | 保存带版本的滚动摘要，以及每份摘要覆盖的闭区间消息 ID。 | `conversation_id` 逻辑关联 `conversations.id` 并建索引；`active` 标识当前摘要。 |
| `user_memory_profiles` | 保存结构化长期用户记忆，例如偏好和可复用业务事实。 | 逻辑关联用户、来源会话和来源消息；`(user_id, memory_category, memory_key, status)` 唯一，查询索引和过期字段用于生命周期管理。 |
| `user_memory_embeddings` | 跟踪非结构化记忆文本及其与 Qdrant 的同步状态。 | 可选关联 `user_memory_profiles`，同时记录用户、来源会话/消息、Qdrant point ID、同步时间、过期时间和状态；按 `(user_id, status)` 建索引。 |

## 情报、冲突与治理

| 数据表 | 用途 | 关键关联与控制 |
| --- | --- | --- |
| `intelligence` | 保存待审核或已完成审核的免费/公开采集情报与人工录入情报。 | `content_hash` 唯一，用于精确去重；`sim_hash` 用于相似性检查；状态和行业/地区范围均有索引。 |
| `paid_intelligence` | 将需要权益的情报与免费/公开情报分开保存。 | 状态及 `(industry_id, region_id, entitlement)` 建有索引；内容 hash 与相似 hash 支持去重工作流。 |
| `intelligence_snapshots` | 保存按范围聚合的日/周/月类 JSON 快照及其保留期元数据。 | `parent_snapshot_id` 支持快照血缘；范围字段建有索引，`retention_days` 和 `expires_at` 控制过期。 |
| `false_information_ledger` | 归档被驳回、证伪或命中谣言规则的情报，并记录原因和归档责任人。 | 可选关联原始情报；原因、范围和内容 hash 均有索引，便于调查和重复识别。 |
| `conflict_resolutions` | 记录相似情报的冲突分支、双方权重、路由决策和解决说明。 | 可选记录传入/既有情报 ID 和复核工单 ID；冲突分支、范围和传入情报均有索引。 |
| `review_work_orders` | 为可疑或冲突业务记录提供通用复核队列。 | 通过 `target_type` 与 `target_id` 标识对象；按 `(status, reason)` 建索引以支持任务分派。 |
| `alert_events` | 保存 API、collector、AI worker、缓存、队列和数据库链路的运维告警。 | 按 `(status, alert_level)` 建索引；负责人和更新时间支持确认与处理闭环。 |
| `audit_logs` | 保存核心的操作人/动作/对象/结果审计轨迹。 | 按 `(action, actor)` 建索引；`target_type` 和字符串类型的 `target_id` 可审计不同类型资源。 |

## Admin 复核、知识、采集与风控

| 数据表 | 用途 | 关键关联与控制 |
| --- | --- | --- |
| `admin_intelligence_reviews` | 保存情报的 Admin 复核状态、结论、复核人、原因和证据。 | `intelligence_id` 逻辑关联 `intelligence.id`；复核状态/结论和情报 ID 均有索引。 |
| `admin_tickets` | 保存 Admin 运维/治理工单及其严重级别、负责人、下一步动作和状态。 | 通过 `target_type` 与 `target_id` 标识对象；状态/严重级别和目标对象均有索引。 |
| `admin_human_intelligence` | 保存人工提交的一线情报及地点、采集人、权益、置信度和复核状态。 | 状态/权益和行业/地区范围均有索引。 |
| `admin_knowledge_nodes` | 保存受管知识文章的稳定身份和生命周期版本指针。 | 草稿、待审和已发布版本 ID 逻辑关联 `admin_knowledge_versions.id`；`(slug, industry_id, region_id, link_id)` 唯一。 |
| `admin_knowledge_versions` | 保存受管知识的每个内容版本及其作者/复核工作流元数据。 | `node_id` 逻辑关联 `admin_knowledge_nodes.id`；`(node_id, version_number)` 唯一，复核查询路径建有索引。 |
| `admin_knowledge_publications` | 记录知识版本的发布、回滚及相关生命周期动作。 | `node_id` 和 `version_id` 分别逻辑关联受管知识节点/版本，两条发布查询路径均有索引。 |
| `admin_collection_sources` | 保存采集源配置、调度、重试/熔断状态、合规说明、代理配置和最近运行遥测。 | 状态/下次运行时间及熔断/失败次数索引支持调度和恢复；JSON 字段保存特定来源的配置。 |
| `admin_collection_keywords` | 保存某一采集源的包含/排除类关键词规则。 | `source_config_id` 逻辑关联 `admin_collection_sources.id`；来源/状态/匹配模式建有联合索引。 |
| `admin_collection_job_runs` | 保存单次采集执行的触发方式、状态、记录数、时间和错误详情。 | `source_config_id` 逻辑关联采集源配置，`job_id` 用于关联所属任务；两条路径均有索引。 |
| `admin_risk_rules` | 保存可配置的风险阈值、范围、级别、启用状态、生效模式和版本。 | `scope_json` 支持规则专属作用范围，`version` 跟踪配置演进。 |

## 运维、交付、知识与采集入口

| 数据表 | 用途 | 关键关联与控制 |
| --- | --- | --- |
| `sla_data_points` | 保存一个时间窗口内的请求量、错误数、延迟分位数和可用性聚合值。 | 按 `window_start` 建索引以支持时序查询。 |
| `report_jobs` | 保存用户报告导出的类型/状态、自检结果和证据来源列表。 | `user_id` 逻辑关联 `users.id`；按 `(user_id, report_type)` 建索引。 |
| `collection_jobs` | 保存采集任务状态、队列深度、重试次数和熔断状态。 | 按 `(status, source_type)` 建索引，供 worker 和运维视图查询。 |
| `dead_letter_records` | 保存采集失败载荷、分类原因和错误信息，用于重放或调查。 | `job_id` 可选关联采集任务；原因字段建有索引。 |
| `knowledge_items` | 保存可直接检索的基线或已批准知识内容及来源元数据。 | `(title, content)` 建有全文索引，`(industry_id, region_id)` 建有范围索引。 |
| `raw_records` | 保存 collector 标准化输出，供下游情报/知识处理。 | 来源类型和行业/地区范围均有索引；`metadata_json` 保留特定来源属性。 |

## 公共字段约定

大多数带业务范围的记录会使用下列部分或全部字段；这些是字段约定，并不表示每张表都包含全部字段：

- `source_id`：数据来源或生产子系统。
- `weight`：来源/业务置信权重。
- `region_id`、`industry_id`，以及部分表中的 `link_id`：类似租户维度的业务范围。
- `create_time`、`update_time`：创建与最后更新时间；追加型事件表可能有意不设置 `update_time`。
- `status`、`review_status` 或其他领域状态字段：由归属工作流控制的生命周期状态。

敏感值在持久化前由 API `PrivacyService` 加密。JSON 字段用于结构可变的证据、载荷、范围和提供方专属配置；查询关键状态保留为带索引的强类型字段。

## 迁移与维护说明

- 新 MySQL 数据库在 API 启动时由 Flyway 从 `V1__baseline.sql` 自动初始化。
- 没有 Flyway 历史的非空 MySQL 数据库会通过 `baseline-on-migrate=true` 按版本 1 建立基线。运维人员必须确认该数据库已经符合 V1 基线，因为 baseline 只记录迁移历史，不会重新创建缺失的 V1 对象。
- Flyway SQL 迁移必须保持幂等：schema 变更和种子数据都要加保护条件，避免重复执行或已升级数据库启动失败。
- 新增数据表或字段必须通过 Flyway 完成，并在同一次变更中同步更新中英文目录和两份变更日志。
- 完整冷热分层、完整数据血缘、动态权重、订单、发票和会员中心存储仍不在当前基线范围内。
