# BizSage 数据库设计

V1 基线 schema 位于 `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql`。API 服务启动时会通过 `services/api/src/main/resources/db/migration/mysql/` 下的 Flyway 迁移脚本创建、校验并升级 MySQL schema；Flyway 是 MySQL schema 的唯一来源。

## 核心表

- `users`：开发账号、角色、加密手机号和身份字段。V2 增加会员等级、咨询偏好和 `preferred_locale`，用于灰度权限校验以及 web 主页面/admin 页面共用的语言偏好。
- `conversations`：用户拥有的诊断会话。
- `messages`：用户与 Agent 的消息历史，包含来源 JSON。
- `intelligence`：免费或公开的人工录入/采集情报，待审核。
- `paid_intelligence`：V2 付费专属情报，与免费/公开情报分开存储。
- `knowledge_items`：静态基线和已发布知识元数据。
- `raw_records`：采集器标准化后的原始记录。
- `intelligence_snapshots`：日、周、月情报快照。
- `review_work_orders`：可疑或冲突情报的复核工单。
- `alert_events`：API、collector、AI worker、缓存、队列和数据库链路的运维告警。
- `audit_logs`：核心操作审计日志。
- `report_jobs`：诊断报告导出元数据与来源列表。
- `collection_jobs`：采集重试、队列深度和熔断状态。
- `dead_letter_records`：采集失败载荷和分类原因。

## 必备公共字段

业务表统一包含：

- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## 迁移说明

- 新 MySQL 库在 API 启动时由 Flyway 从 `V1__baseline.sql` 自动初始化。
- 没有 Flyway 历史的非空旧 MySQL 库会通过 `baseline-on-migrate=true` 按版本 1 记录基线，然后在服务启动时继续执行后续幂等的 schema/数据升级脚本。
- Flyway SQL 迁移必须保持幂等：schema 变更和种子数据都要加保护条件，避免重复执行或已升级数据库启动失败。
- 敏感字段在持久化前由 API `PrivacyService` 加密。
- 完整冷热分层、全量数据血缘、动态权重、订单、发票和会员中心能力仍推迟到 V2 之后。
