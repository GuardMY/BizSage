# 数据库规范

所有业务表包含以下公共字段：

- `id`
- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## 隐私

- 用户私有经营数据存放在专用表中。
- 敏感字段在持久化前进行 AES 加密。
- 手机号和身份证值只以脱敏形式展示。
- 明文敏感值不得写入日志。

## V1 存储

- 热结构化数据：MySQL。
- 热缓存：Redis。
- 向量检索：Qdrant。
- 对象/冷存储和快照延后到 V1 之后。
