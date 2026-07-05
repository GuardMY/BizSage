# MySQL Redis Qdrant 真实接入设计

## 目标

将 BizSage 从“基础设施占位”升级为符合里程碑要求的真实存储接入：保留 MySQL 作为 API 权威数据源，将 Redis 接入采集链的去重与快照缓存，并让 Qdrant 成为 AI worker 的主向量检索后端。

## 范围

本设计只覆盖三项真实接入：

- `services/api` 中 MySQL 的真实读写
- `services/collector` 中 Redis 的真实运行时使用
- `services/ai-worker` 中 Qdrant 的真实向量写入与检索

本次设计明确不包含：

- Docker 全栈启动验证与长时间环境验收
- 完整故障演练与灾备实现
- 所有服务的完整多级 Redis 缓存
- 完整生产级 GraphRAG 架构

## 当前状态

### MySQL

`services/api` 已经通过 Spring `JdbcTemplate` 仓储和默认 profile 下的 MySQL 数据源在工作。当前缺口不是 API 存储是假的，而是当前环境尚未做真实运行态验证，并且测试仍然使用 H2。

### Redis

Redis 目前只存在于基础设施和环境规划里。当前没有运行中的服务导入 Redis 客户端，也没有将运行时状态持久化到 Redis。

### Qdrant

Qdrant 目前也只存在于基础设施和环境规划里。AI worker 当前使用的是 Python 进程内的 token overlap 与 cosine 相似度，并没有把向量写入或检索交给真实向量数据库。

## 推荐架构

### 1. MySQL 继续作为 API 权威数据源

保留现有 API 持久化模型：

- 用户、会话、情报、付费情报、知识元数据、审计类表继续落在 MySQL
- Spring `JdbcTemplate` 继续作为持久化访问层
- API 契约与 Web 行为保持稳定

本次不引入 ORM 迁移，以保持当前仓储形态并降低里程碑风险。

### 2. Redis 承载 Collector 运行时状态

Redis 将先接入 `services/collector` 的一条真实业务链：

- 增量指纹去重状态
- 厂商故障切换时的最近快照缓存

这意味着：

- 公共页面或 mock-api 记录的重复检测不再依赖调用方传入的内存集合
- 最近成功的 fallback 记录可跨进程重启保留，并在厂商失败时复用

Redis 本次优先只在 collector 中先落一条真实链路，不扩散到所有服务，以保证首轮真实使用聚焦且可测试。

### 3. Qdrant 成为 AI Worker 主检索后端

AI worker 的检索路径从本地打分切换为：

1. 接收知识记录
2. 通过轻量、确定性的 embedder 生成向量
3. 将向量和元数据 upsert 到 Qdrant
4. 查询时从 Qdrant 拉取 top candidates
5. 在 Python 侧继续应用 BizSage 现有重排和过滤规则

现有本地词法打分只保留为受控降级路径或测试辅助，不再作为主生产路径。

## 组件设计

### API 层

#### 职责

- 继续将业务记录写入 MySQL
- 对 Web 与内部调用者暴露稳定契约
- 提供 AI worker 可用于向量化的知识载荷

#### 计划改动

- 保留现有 `JdbcTemplate` stores
- 如果 AI worker 需要干净的知识获取源，则新增一个聚焦的 API 侧知识列表接口或 service helper
- 除非向量检索主链需要，否则不改报告和会话流程

### Collector 层

#### 职责

- 用 Redis 维护去重状态与最近快照缓存
- 保持现有韧性语义通过当前 collector API 对外可见

#### 计划改动

- 在 `services/collector` 增加 Redis 客户端依赖
- 引入一个小的运行时状态封装，例如 `redis_state.py`
- 将增量指纹存在性检查迁移为 Redis-backed 操作
- 用显式 TTL key 持久化最近成功的厂商响应

### AI Worker 层

#### 职责

- 将可检索向量写入 Qdrant
- 使用 Qdrant 作为主检索后端
- 保留 BizSage 的 region、industry、entitlement、review-confidence、historical-quality 过滤与重排逻辑

#### 计划改动

- 在 `services/ai-worker` 增加 Qdrant 客户端依赖
- 新增小型向量存储适配层，例如 `vector_store.py`
- 新增确定性 embedding 模块，例如 `embeddings.py`
- 修改 `rag.py`，让检索从 Qdrant candidates 开始，而不是只对内存 token bag 做 cosine

## 数据模型

### MySQL

本次里程碑不需要做大规模 schema 重构。现有 API 表仍然是业务权威数据源。

如果知识向量同步需要稳定元数据，则每条知识记录应继续暴露：

- `id`
- `title`
- `content`
- `source_id`
- `region_id`
- `industry_id`
- entitlement 或同等访问控制元数据

### Redis Key

Collector 的 Redis 使用应采用显式命名空间：

- `collector:fingerprint:<fingerprint>`
- `collector:snapshot:<job-or-source-key>`

每类 key 都要有清晰 TTL 策略：

- fingerprint key 可长存或按里程碑配置
- snapshot key 应使用适合故障切换的短时 TTL

### Qdrant Payload

每条向量记录应包含：

- `id`
- `title`
- `content`
- `source_url`
- `source_id`
- `industry_id`
- `region_id`
- `entitlement`
- `weight`
- `confidence`
- `review_confidence`
- `historical_quality`

这样可保证检索过滤与 BizSage 治理文档保持一致。

## 运行时流程

### 带 Redis 的 Collector

1. collector 接收采集请求
2. 计算标准化记录的 fingerprint
3. 到 Redis 中检查该 fingerprint 是否已存在
4. 重复记录的识别不再依赖内存调用上下文
5. 成功厂商响应以最近快照形式写入 Redis
6. 当所有厂商失败时，从 Redis 读取最近快照做 fallback

### 带 Qdrant 的 AI Worker

1. API 或测试传入知识记录
2. AI worker 对每条记录生成 embedding
3. 将向量 upsert 到 Qdrant collection
4. 诊断或 RAG 查询时先对用户 query 做 embedding
5. 从 Qdrant 获取最相近候选
6. Python 继续做 entitlement、region、industry、quality 重排
7. 下游诊断基于这些过滤后的来源生成结果

## 测试策略

### MySQL

- 保持当前 API 套件继续通过
- 保持 H2 测试 profile 作为快速单测和集成测试基础
- 至少补一条 repository 级测试，证明持久化假设仍与 MySQL 风格 SQL 对齐

本次里程碑不要求把 H2 全部替换为 testcontainers 或实时 MySQL，因为用户本次要求的是代码路径中的真实接入，不是完整 Docker 验证。

### Redis

- 为 Redis-backed 指纹持久化补 collector 测试
- 为最近快照缓存的读写补 collector 测试
- 保证在 Redis-backed store 缺失或被绕过时，测试会先失败

如果必须用测试替身，也只能在生产代码先抽出清晰 adapter 接口的前提下使用；只要本地轻量 Redis-compatible 路径可行，就优先让真实调用路径进入测试。

### Qdrant

- 为向量 upsert 调用补 AI worker 测试
- 为向量 search 调用补 AI worker 测试
- 为“Qdrant 返回候选后仍继续做 entitlement 与 region 过滤”补检索测试
- 保留一条无 Qdrant 或空结果时的受控 fallback 测试

## 错误处理

### Redis

- collector 对非法状态写入应 fail closed
- 当 Redis 中没有最近快照时，可退化为“无快照 fallback”
- Redis 故障不能被静默当成“已命中重复”

### Qdrant

- 如果 Qdrant 不可用，AI worker 只能在显式、受测的前提下退回现有本地 scorer
- 无支持证据或空检索结果时，仍必须保留当前 information-insufficient 或 low-confidence 输出行为

## 验收标准

当以下条件全部满足时，本设计视为完成：

- API 业务持久化继续通过真实 `JdbcTemplate` 加 MySQL 运行时配置
- collector 为去重和最近快照缓存读写真实 Redis 运行时状态
- AI worker 将向量写入 Qdrant，并通过 Qdrant 进行主检索
- region、industry、entitlement、confidence 与重排规则在接入真实存储后仍继续生效
- 相关模块测试在新鲜验证中通过

## 预期改动文件

可能改动的 API 文件：

- `services/api/src/main/resources/application.yml`
- `services/api/src/main/java/com/bizsage/api/...` 中仅在需要 knowledge fetch helper 时做小改动
- `services/api/src/test/java/com/bizsage/api/...`

可能改动的 collector 文件：

- `services/collector/requirements.txt`
- `services/collector/app/main.py`
- `services/collector/app/resilience.py`
- `services/collector/app/collectors.py` 或新增 `services/collector/app/redis_state.py`
- `services/collector/tests/...`

可能改动的 AI worker 文件：

- `services/ai-worker/requirements.txt`
- `services/ai-worker/app/main.py`
- `services/ai-worker/app/rag.py`
- 新增 `services/ai-worker/app/embeddings.py`
- 新增 `services/ai-worker/app/vector_store.py`
- `services/ai-worker/tests/...`

## 风险与取舍

- Qdrant 接入是本次最大改动面，因为当前 AI worker 设计本来就刻意保持简单。
- Redis 很容易在首轮接入时扩散到多个服务；本设计通过限制其首轮仅服务 collector 运行时状态来避免范围失控。
- 用 Qdrant 替换本地检索主路径后，必须确保当前业务过滤语义不丢失，否则基础设施更真实，检索质量反而可能退化。

## 推荐实施顺序

1. 锁定 MySQL API 行为和测试
2. 接入 Redis-backed collector 运行时状态
3. 接入 Qdrant-backed AI 检索

这个顺序能先稳住 API 基线，再用小而清晰的切片把 Redis 和 Qdrant 接进去。
