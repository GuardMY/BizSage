# V1 数据库设计

基线迁移文件位于 `infra/mysql/init/001_v1_baseline.sql`。

## 核心表

- `users`：开发用户、角色、加密手机号和身份证列。
- `conversations`：用户拥有的诊断会话。
- `messages`：用户和 Agent 消息历史，包含来源 JSON。
- `intelligence`：人工录入或采集得到、等待审核的情报。
- `knowledge_items`：静态基线与已审核知识元数据。
- `raw_records`：collector 输出的标准化原始记录。

## 必需公共字段

业务表包含：

- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## V1 说明

- 当前运行时 API 使用内存存储支撑本地 MVP 验证。
- MySQL schema 已为下一步持久化改造准备好。
- 敏感值在设计持久化路径中由 API `PrivacyService` 加密。
- 快照、冷存储、数据血缘和动态权重延后到 V1 之后。
