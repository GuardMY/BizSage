# BizSage 数据库设计

手工基线初始化脚本位于 `infra/mysql/init/001_v1_baseline.sql`。
执行完基线脚本后，API 服务会在启动时通过
`services/api/src/main/resources/db/migration/mysql/` 下的 Flyway 增量脚本
校验并升级数据库结构。

## 核心表

- `users`：开发账号、角色、加密手机号和身份证列。V2 增加会员等级和咨询偏好，用于灰度权限校验。
- `conversations`：用户拥有的诊断会话。
- `messages`：用户与 Agent 的消息历史，包含来源 JSON。
- `intelligence`：免费或公开的人工录入/采集情报，待审核。
- `paid_intelligence`：V2 付费专属情报，与免费/公开情报分开存储。
- `knowledge_items`：静态基线知识和已发布知识元数据。
- `raw_records`：采集器标准化后的原始记录。
- `intelligence_snapshots`：日、周、月情报快照。
- `review_work_orders`：存疑或冲突情报的复核工单。
- `alert_events`：API、collector、AI worker、缓存、队列和数据库链路的运维告警。
- `audit_logs`：核心操作审计日志。
- `report_jobs`：诊断报告导出元数据与来源列表。
- `collection_jobs`：采集重试、队列深度和熔断状态。
- `dead_letter_records`：采集失败负载和分类原因。

## 必备公共字段

业务表统一包含：

- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## 迁移说明

- 新 MySQL 库必须先手工执行 `001_v1_baseline.sql`，再启动 API。
- API 启动后由 Flyway 记录基线版本，并自动执行后续增量升级脚本。
- 敏感字段在持久化前由 API `PrivacyService` 加密。
- 完整冷热分层、全量数据血缘、动态权重、订单、发票和会员中心能力仍延后到 V2 之后。
