# BizSage 数据库设计

基线迁移文件位于 `infra/mysql/init/001_v1_baseline.sql`。

## 核心表

- `users`：开发用户、角色、加密手机号和身份证列。V2 增加会员等级和咨询偏好，用于灰度权限校验。
- `conversations`：用户拥有的诊断会话。
- `messages`：用户和 Agent 消息历史，包含来源 JSON。
- `intelligence`：免费/公开的人工录入或采集情报，等待审核。
- `paid_intelligence`：V2 付费专属情报，与免费/公开情报分开存储。
- `knowledge_items`：静态基线与已审核知识元数据。
- `raw_records`：collector 输出的标准化原始记录。
- `intelligence_snapshots`：日、周、月情报快照。
- `review_work_orders`：存疑或冲突情报复核任务。
- `alert_events`：API、collector、AI worker、缓存、队列和数据库链路的运维告警。
- `audit_logs`：核心操作审计轨迹。
- `report_jobs`：诊断报告导出元数据和来源列表。
- `collection_jobs`：collector 重试、队列深度和熔断状态。
- `dead_letter_records`：采集失败载荷和分类原因。

## 必需公共字段

业务表包含：

- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## V1/V2 说明

- 当前运行时 API 使用内存存储支撑本地 MVP 和 V2 灰度验证。
- MySQL schema 已包含 V2 持久化目标，用于下一步 repository 改造。
- 敏感值在设计持久化路径中由 API `PrivacyService` 加密。
- 完整冷存储、完整数据血缘、动态权重、订单、发票和会员中心延后到 V2 之后。
