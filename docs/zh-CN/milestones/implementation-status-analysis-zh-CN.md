# BizSage 核心功能架构实现状态分析

分析日期：2026-07-08（基于 1.1 分支代码审查更新）

## 1. 总体进度概览

| 版本 | 状态 | 完成度估计 |
|------|------|-----------|
| V1 MVP | ✅ 已基本完成 | ~92%（核心功能代码完成，环境验证有缺口） |
| V2 生产高可用 | 🔄 进行中 | ~55%（V1 已闭合，V2 核心组件大量就位） |
| V3 全域商业 | 📋 计划中 | ~15%（Admin V3 部分功能已前置实现） |

**相比上次分析的主要提升：**
- 学习 Agent 从零代码变为完整实现（+1 个 Agent 闭环）
- 双 Agent 双向转换已实现（学习⇄诊断一键跳转）
- 冲突引擎五分支全部落地到代码（非仅文档层面）
- 时间序列快照（日/周/月）调度生成已实现
- 上下文压缩器完整实现
- 三层记忆系统以 API/MySQL 为真相源闭环；向量记忆同步代码保留，但在线链路默认停用，等待检索闭环设计完成后再恢复
- 诊断主链路从 API → AI Worker → RAG → LLM 已严格闭合

---

## 2. 九层架构逐层分析

### 2.1 运维工程基础

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 单节点 Redis 缓存 | V1 | ✅ 已实现 — `services/collector` 使用 Redis 做指纹去重、快照存储 |
| 基础日志 | V1 | ✅ 已实现 — Spring Boot / Next.js 日志体系 |
| 简单 API 限流 | V1 | ✅ 已实现 — `SecurityConfiguration` + 限流 |
| 简单熔断 | V1 | ✅ 已实现 — collector `CircuitBreaker` + API `AdminCollectionStore` 断路器模式 |
| **多级缓存 (L1-L4)** | V2 | ❌ 未实现 — 架构设计文档有定义但未构建 |
| **弹性集群扩容** | V2 | ❌ 未实现 |
| **完整熔断/降级/死信队列** | V2 | ⚠️ 部分 — collector 有 `CircuitBreaker` + `DeadLetter` + `retry_with_backoff`；API 有 `dead_letter_records` 表；全局未贯通 |
| **灾备** | V2 | ❌ 未实现 — 备份脚本存在但未执行演练 |
| **四环境隔离 (dev/test/staging/prod)** | V2 | ❌ 未实现 — 仅本地单节点 |
| **监控面板 + 三级告警** | V2 | ⚠️ 部分 — Admin 面板有基础指标和告警中心，无 Prometheus/Grafana 完整监控栈 |
| **成本核算** | V3 | ❌ 未实现 |
| **全 SLA 面板 + 99.9%** | V3 | ❌ 未实现 |

### 2.2 多源数据采集

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 用户私有业务数据（表单） | V1 | ✅ 已实现 — `collect_form_business_data` |
| Excel 导入 | V1 | ✅ 已实现 — `collect_excel_business_data` |
| 基础公网爬虫 | V1 | ✅ 已实现 — `collect_public_page`（HTML 解析） |
| 第三方 API（Mock） | V1 | ✅ 已实现 — `collect_mock_api` + 供应商故障切换 |
| AES 加密 | V1 | ✅ 已实现 — `PrivacyService` + `PrivacyConfiguration` |
| 基础 Bloom/SimHash 去重 | V1 | ✅ 已实现 — `governance.py` SimHash + Redis 指纹 |
| **增量爬虫指纹检测** | V2 | ⚠️ 部分 — Redis 指纹 + `incremental_fingerprint` 存在；MD5+SimHash 变更检测未贯通 |
| **反爬加固** | V2 | ❌ 未实现 |
| **多供应商代理轮换** | V2 | ⚠️ 部分 — `fetch_with_vendor_failover` 已实现供应商故障切换框架；多供应商代理池未建 |
| **第三方 API 冗余/故障切换** | V2 | ✅ 已实现 — `fetch_with_vendor_failover` + Redis 快照回退 |
| **API 缓存（付费API降本）** | V2 | ❌ 未实现 |
| **采集遥测（成功率/API量/缓存命中率）** | V2 | ⚠️ Admin 有基础指标和 `collection_job_runs` 记录，不完整 |
| **全爬虫安全规则** | V3 | ❌ 未实现 |
| **源成本治理** | V3 | ❌ 未实现 |
| **付费情报保护** | V3 | ⚠️ `paid_intelligence` 表隔离 + 基于 entitlement 的 API 过滤存在，前端未闭环 |

### 2.3 全域数据治理

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 字段标准化 | V1 | ✅ 已实现 — `normalize_fields` |
| 文本提取 | V1 | ✅ 已实现 — `extract_title` + `extract_body_text` |
| URL 去重 + SimHash 去重 | V1 | ✅ 已实现 — `govern_records` |
| 谣言关键词过滤 | V1 | ✅ 已实现 — `contains_rumor` |
| 固定源权重 | V1 | ✅ 已实现 — `FIXED_WEIGHTS` |
| **七层新旧信息冲突引擎** | V2 | ✅ 已实现 — `conflict_engine.py` 五分支全实现（`SHORT_TERM_FLUCTUATION`/`REGIONAL_EXCEPTION`/`PERMANENT_AUTHORITATIVE_UPDATE`/`SUSPICIOUS_CONFLICT`/`FALSE_INFORMATION`），API 端 `ConflictStore` 持久化 + `false_information_ledger` |
| **日/周/月时间序列快照** | V2 | ✅ 已实现 — `SnapshotService` + `SnapshotScheduler`（日 02:00/周周日 03:00/月1日 04:00），含快照对比差异和自动清理 |
| **行级乐观锁** | V2 | ❌ 未实现 |
| **多源智能合并** | V2 | ❌ 未实现 |
| **四层数据隔离** | V2 | ⚠️ `DataScope`（区域/行业/会员/管理员）模型存在并已应用到 `listScoped` 查询；未覆盖所有数据操作 |
| **动态源权重引擎** | V3 | ❌ 未实现 |
| **全数据血缘追踪** | V3 | ❌ 未实现 |
| **垃圾数据自动清理** | V3 | ❌ 未实现 — 快照有自动清理，通用垃圾清理未实现 |
| **热/温/冷存储生命周期** | V3 | ❌ 未实现 |

### 2.4 智能知识中台

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 静态行业基线知识库 | V1 | ✅ 已实现 — MySQL `knowledge_items` 表 + FULLTEXT 索引 |
| MySQL 结构化存储 | V1 | ✅ 已实现 |
| 向量批量存储 | V1 | ✅ 已实现 — Qdrant 集成 + 启动自动同步 `KnowledgeSyncInitializer` |
| 基础去重 | V1 | ✅ 已实现 |
| **动态实时情报知识库** | V2 | ✅ 已实现 — `AdminCollectionScheduler` 每15秒自动采集 → `intelligence` 表 → 审批后自动同步 Qdrant |
| **时间序列版本知识存储** | V2 | ✅ 已实现 — `admin_knowledge_versions` + `admin_knowledge_publications` 发布/回滚审计链 |
| **AI 知识精炼引擎（合并/去重/标签）** | V2 | ❌ 未实现 — 知识库有版本管理和人工审核流程，无 AI 自动精炼 |
| **知识生命周期管理** | V2 | ⚠️ Admin V3-3 有完整版本生命周期（草稿→审核→批准→发布→回滚）+ `Inspect`（过期草稿/低置信度检测）；无自动归档/过期机制 |

### 2.5 多级 RAG 检索

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 关键词检索 | V1 | ✅ 已实现 — `keyword_overlap`（0.65 权重） |
| 向量语义检索 | V1 | ✅ 已实现 — Qdrant + `embed_text`（0.70 权重） |
| 简单源权重 rerank | V1 | ✅ 已实现 — `_quality_score`（权重0.35 + 置信度0.2 + 审核置信度0.25 + 历史质量0.2） |
| 上下文截断 | V1 | ✅ 已实现 — `limit=5` |
| 信息不足响应 | V1 | ✅ 已实现 — `INSUFFICIENT_EVIDENCE` |
| **时间序列检索** | V2 | ❌ 未实现 — 无时间衰减权重、无时间范围过滤 |
| **区域/行业/会员过滤** | V2 | ✅ 已实现 — `_matches_business_filters` |
| **六维 rerank（权威/时效/区域/行业/审核/历史）** | V2 | ⚠️ 质量分融合四维（权重/置信度/审核置信度/历史质量）；权威性和时效性维度缺失；无独立重排序阶段 |
| **上下文压缩** | V2 | ✅ 已实现 — `context_compressor.py`（合并相似项→预算按比例分配→句子边界截断）；诊断用 2100-2400 token 预算 |
| **冲突标记** | V2 | ✅ 已实现 — `conflict_labels` → `NEEDS_REVIEW` + 阻断回答 |

### 2.6 大模型推理服务

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 单一固定模型 | V1 | ✅ 已实现 — DeepSeek（OpenAI 兼容） |
| 基础通用 Prompt | V1 | ✅ 已实现 — 诊断用 `SYSTEM_PROMPT` + 学习用独立系统提示 |
| 简单超时降级 | V1 | ✅ 已实现 — `LLMCallError` + `LLMNotConfiguredError` → HTTP 503 |
| **多模型路由** | V2 | ❌ 未实现 — 仅支持单模型配置 |
| **五检推理自检（事实/时效/区域/逻辑/合规）** | V2 | ❌ 未实现 — 仅有 `selfCheckStatus: NEEDS_REVIEW` 冲突标记 |
| **分层 Prompt 库** | V2 | ❌ 未实现 — 诊断/学习各自有独立 system prompt，但无分层 Prompt 库 |
| **缓存降级 + 标准答案降级** | V2 | ❌ 未实现 — 失败直接返回空 |
| **模型成本核算** | V3 | ❌ 未实现 |

### 2.7 双Agent业务核心

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 基础诊断 Agent 链 | V1 | ✅ 已实现 — `agent.py::diagnose()`（RAG→压缩→LLM→结构化返回） |
| 短时会话记忆 | V1 | ✅ 已实现 — `memory.py` 最近6条消息上下文 |
| 证据+免责声明输出 | V1 | ✅ 已实现 — sources + disclaimer |
| **行业学习 Agent** | V2 | ✅ 已实现 — `learning_agent.py` 完整实现（7 链节点 × 6 意图类型 × 3 学习模式），支持意图自动分类和节点过滤 |
| **三级记忆（短期/长期/画像）** | V2 | ⚠️ 已部分闭环 — 滚动摘要和 MySQL 长期记忆已在线启用；`UserMemoryEmbeddingStore` 与向量同步代码仍保留，但在线链路默认停用，待检索闭环补齐后再恢复 |
| **双 Agent 一键跳转** | V2 | ✅ 已实现 — `agent_transition.py` 双向转换（学习→诊断注入节点知识，诊断→学习注入薄弱环节），含 `execute_transition()` 和前端预览 `build_transition_context()` |
| **标准化双 Agent 输出模板** | V2 | ⚠️ 学习 Agent 已使用 `AgentOutput` 标准格式（key_findings/risk_alerts/actionable_steps/supporting_evidence）；诊断 Agent 仍使用旧版字典格式，尚未迁移 |
| **付费/免费情报隔离** | V2 | ✅ 已实现 — 基于 entitlement 的 `DataScope.canAccessPaid()` |
| **全域双 Agent 个性化** | V3 | ❌ 未实现 |
| **五种报告类型** | V3 | ❌ 未实现 — 仅有诊断报告元数据 |
| **安全合规审计回路** | V3 | ❌ 未实现 |

### 2.8 商业业务服务

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| 基础会话管理 | V1 | ✅ 已实现 — 创建/列表/归档/软删除 + 用户隔离 |
| 简单 RBAC 角色 | V1 | ✅ 已实现 — `SUPER_ADMIN/OPERATOR/USER` + JWT HMAC-SHA256 |
| **PDF 诊断报告导出** | V2 | ⚠️ 报告元数据 API 存在（`DiagnosisReportService`），format 字段写死 "PDF" 但输出为 JSON，无 PDF 二进制生成 |
| **双层 RBAC（功能+数据权限）** | V2 | ⚠️ 功能权限完整（`@PreAuthorize` + 方法级安全）；数据权限有 `DataScope` 四层模型（区域/行业/会员/管理员）并应用于情报/知识查询，但未覆盖所有 API |
| **用户画像标签** | V2 | ⚠️ `membershipLevel` / `consultationPreferences` 字段存在；AI 端未做深层画像挖掘 |
| **用户投稿激励基础** | V2 | ❌ 未实现 |
| **种子付费用户灰度** | V2 | ✅ 已实现 — `seed_paid` 账号 + 付费情报 API 隔离 |
| **完整会员等级/订单/权益** | V3 | ❌ 未实现 |
| **付费情报子店** | V3 | ⚠️ `paid_intelligence` 表隔离 + API 过滤存在；前端仅有权益标签展示，无独立子店 |

### 2.9 终端输出

| 能力 | 目标版本 | 状态 |
|------|---------|------|
| Web 登录/会话/诊断/来源 | V1 | ✅ 已实现 — 含 SSE 流式诊断 + 来源引用 |
| 简单运营控制台 | V1 | ✅ 已实现 — Admin V3-1 至 V3-4 |
| **运营监控面板** | V2 | ✅ 已实现 — Admin V3-1（服务健康/采集管道/数据摘要/告警分布） |
| **情报审核工单** | V2 | ✅ 已实现 — Admin V3-2（7 种裁决 + 自动工单创建） |
| **告警中心** | V2 | ✅ 已实现 — Admin V3-1（确认/认领/关闭） |
| **审计日志** | V2 | ✅ 已实现 — Admin V3-1（可搜索审计日志） |
| **H5 移动端适配** | V3 | ❌ 未实现 — 有响应式 CSS 断点（≤980px/≤560px），无独立移动端 |
| **弱网降级** | V3 | ❌ 未实现 |
| **会员中心** | V3 | ❌ 未实现 |
| **月度运营报告导出** | V3 | ❌ 未实现 |

---

## 3. 核心未闭合链路

根据 1.1 分支代码审查更新：

### 3.1 诊断链路已闭合（✅ 已解决）

- **当前**：`API → DiagnosisService → AiWorkerClient.diagnose() → AI Worker /agent/diagnose → Qdrant RAG → 外部 LLM → SSE 流式返回`
- API 端严格失败模式：AI Worker 不可用时返回 `event: error` SSE 帧，带机器可读错误码（`WORKER_UNREACHABLE`/`LLM_NOT_CONFIGURED`/`WORKER_TIMEOUT`）
- **状态**：已闭合。原分析中标记的 `API → 本地 DiagnosisService（组装JSON）` 已替换为完整链路。

### 3.2 采集→存储自动化链路已闭合（✅ 已解决）

- **当前**：`AdminCollectionScheduler（每15秒） → CollectorClient → Collector 微服务 → /govern（可选冲突检测） → AdminCollectionStore → 自动创建 intelligence 记录 + 审核工单`
- Collector 端提供 `/collect/*` + `/govern` + `/govern/conflict-check` 端点
- API 端 `CollectorClient` 支持嵌入回退模式（`embedded://` URI 前缀）
- **状态**：已闭合。原分析中"调用方手动调 `/govern`"已替换为全自动调度管道。

### 3.3 学习 Agent 已完整实现（✅ 已解决）

- `learning_agent.py`：7 链节点（原材料→生产制造加工→质量控制→仓储库存→物流流通→渠道运营→销售终端回款）
- 6 意图类型：行业概览、节点学习、指标问答、风险问答、隐藏规则、政策问答
- 3 学习模式：快速入门、全链学习、节点深潜
- 前端 `POST /api/conversations/{id}/messages/learn/stream` SSE 流式端点
- 12 个单元测试覆盖所有意图和模式
- **状态**：已闭合。原分析中"零行代码"已变为完整实现。

### 3.4 冲突引擎已完整实现（✅ 已解决）

- Collector 端 `conflict_engine.py`：五分支全实现（`SHORT_TERM_FLUCTUATION`/`REGIONAL_EXCEPTION`/`PERMANENT_AUTHORITATIVE_UPDATE`/`SUSPICIOUS_CONFLICT`/`FALSE_INFORMATION`）
- 五路由操作：`TAG_ONLY`/`REVIEW_TICKET`/`ALERT`/`KNOWLEDGE_UPDATE`/`FALSE_LEDGER`
- 可配置阈值：SimHash 距离、权威权重门槛、低权重波动阈值
- API 端 `ConflictStore`：冲突记录持久化 + 虚假信息台账（`false_information_ledger`）
- 10 个单元测试覆盖全部 6 条分类规则
- **状态**：已闭合。原分析中"仅在文档层面"已变为完整代码实现。

### 3.5 PDF 报告未闭环（🟡 重要）

- 报告元数据 API 完整（来源、置信度、时效性、自检状态、免责声明）
- `DiagnosisReport.format` 字段写死 "PDF" 但返回 JSON
- 无真正的 PDF 二进制生成、HTML 渲染或文件下载
- **状态**：仍未闭合。

### 3.6 新增：标准化输出格式未统一（🟡 次要）

- 学习 Agent 已迁移到 `AgentOutput` 标准格式（含 `sections`/`chainNodeId`/`suggestedActions`）
- 诊断 Agent 仍返回旧版字典格式，缺少结构化分段输出
- `agent_output.py` 中 `format_diagnosis_output()` 已定义但 `agent.py::diagnose()` 未调用
- **状态**：需将诊断 Agent 迁移到标准格式以统一双 Agent 输出

---

## 4. 各领域汇总

| 领域 | 上次完成度 | 本次完成度 | 关键变化 |
|------|-----------|-----------|---------|
| 基础设施 | 60% | 65% | Docker Compose 全套服务 + Dockerfile 验证完整；监控/灾备/多环境仍缺失 |
| 数据采集 | 55% | 65% | 供应商故障切换 + Redis 快照回退 + 死信队列完整实现 |
| 数据治理 | 30% | 55% | **冲突引擎五分支完整实现** + **时间序列快照调度生成** + 虚假信息台账 |
| 知识中台 | 45% | 60% | 动态情报自动采集同步 + 知识版本完整生命周期 + 启动 Qdrant 同步 |
| RAG 检索 | 55% | 70% | **上下文压缩器完整实现**（合并/分配/截断） |
| AI 推理 | 25% | 30% | 学习 Agent 有独立 system prompt 和温度参数（0.5 vs 0.3）；仍为单模型 |
| 双 Agent | 20% | 60% | **学习 Agent 完整实现** + **双向转换** + **三层记忆主链路闭环**（MySQL/摘要在线，向量记忆默认停用） + 标准输出（诊断待迁移） |
| 商业服务 | 15% | 30% | 四层数据隔离模型落地 + 付费情报 API 隔离 + 种子付费用户灰度 |
| 终端 | 45% | 50% | 前端新增学习/转换 SSE 流式支持；Snapshots/Conflicts/FalseLedger 有 API 无 UI 渲染 |

---

## 5. Admin V3 分阶段交付状态

根据 `admin-v3-ui-design-zh-CN.md` 的分阶段交付计划和代码审查：

| 阶段 | 范围 | 状态 |
|------|------|------|
| Admin-V3-1 | AdminShell、导航、权限网关、控制中心、告警入口、基础日志搜索 | ✅ 已实现 |
| Admin-V3-2 | 情报管理、审核工作台、工单台账、人工情报 | ✅ 已实现 |
| Admin-V3-3 | 知识库管理、版本对比、双审、发布/回滚、批量巡检 | ✅ 已实现 |
| Admin-V3-4 | 采集调度、数据源、关键词、任务运行、死信队列 | ✅ 已实现（核心） |
| Admin-V3-4（后半） | 风控规则（6标签页）、数据生命周期、代理池健康 | ⚠️ 风控规则 CRUD + 启用/禁用已实现；数据生命周期/代理池健康未开始 |
| Admin-V3-5 | 用户/会员、订单/权益、报告交付、投稿激励 | ❌ 未开始 |
| Admin-V3-6 | 合规配置、检查任务、月度报告、审计证据包、移动端视图 | ❌ 未开始 |

**前端已知缺口**：快照列表/对比、冲突记录、虚假信息台账的导航项在侧栏已定义但无对应视图渲染（API 端点均已存在）。

---

## 6. 验证缺口

以下项目在代码中已记录为完成，但在当前环境中无法验证：

| 项目 | 所需工具 | 阻塞原因 |
|------|---------|---------|
| Docker Compose 全栈启动 | `docker` CLI | 当前环境不可用 |
| Maven 测试套件（9个测试类） | `mvn` | 当前环境不可用 |
| Web 测试套件 (`npm test`) | `node`/`npm` | 当前环境不可用 |
| AI Worker 测试套件（60个测试） | `pytest` | 当前环境不可用 |
| Collector 测试套件（29个测试） | `pytest` | 当前环境不可用 |
| 50并发负载测试 | 全栈运行 | Docker 未验证 |
| 4小时 V1 稳定性 | 长时间运行环境 | 未安排 |
| 备份/恢复演练 | Docker + 测试数据 | Docker 未验证 |
| 7天 V2 灰度稳定性 | 部署环境 | 未开始 |
| 浏览器视觉验证 | 全栈运行 | 未开始 |

---

## 7. 参考文档

本分析基于以下文档和 1.1 分支代码审查：

- `docs/zh-CN/system-architecture-and-framework-zh-CN.md` — 系统架构与技术框架
- `docs/zh-CN/product-strategy-and-design-zh-CN.md` — 产品策略与双Agent设计
- `docs/zh-CN/development-implementation-guide-zh-CN.md` — 分阶段交付与九层矩阵
- `docs/zh-CN/data-collection-and-intelligence-perception-zh-CN.md` — 数据采集与治理
- `docs/zh-CN/risk-management-and-compliance-zh-CN.md` — 风险与合规管控
- `docs/zh-CN/component-interactions-and-data-flows-zh-CN.md` — 当前vs目标交互图
- `docs/zh-CN/admin-v3-ui-design-zh-CN.md` — V3管理员后台UI设计
- `docs/zh-CN/milestones/product-milestones-zh-CN.md` — 跨版本路线图
- `docs/zh-CN/milestones/v1-mvp-milestones-zh-CN.md` — V1 MVP详细里程碑
- `docs/zh-CN/milestones/v2-production-high-availability-milestones-zh-CN.md` — V2详细里程碑
- `docs/zh-CN/milestones/v3-full-domain-commercial-final-milestones-zh-CN.md` — V3详细里程碑
- `docs/zh-CN/milestones/v2-verification-results-zh-CN.md` — V2验证结果
- 源代码：`apps/web`、`services/api`、`services/ai-worker`、`services/collector`、`infra`

---

## 8. 变更摘要（相比上次分析）

### 已闭合的关键链路（原标记 🔴 或 🟡）

| 原链接 | 原状态 | 当前状态 | 变更说明 |
|--------|--------|---------|---------|
| 3.1 诊断链路 | 🔴 严重 | ✅ 已闭合 | API 严格调用 AI Worker，SSE 流式返回 |
| 3.2 采集→存储自动化 | 🔴 严重 | ✅ 已闭合 | AdminCollectionScheduler 自动调度 + 自动创建情报/工单 |
| 3.3 学习 Agent | 🔴 严重 | ✅ 已闭合 | `learning_agent.py` 157 行完整实现 |
| 3.4 冲突引擎 | 🟡 重要 | ✅ 已闭合 | `conflict_engine.py` 五分支 + 10 个测试 |

### 模块完成度显著提升

| 模块 | 原 → 新 | 提升 | 关键驱动因素 |
|------|--------|------|-------------|
| 双 Agent | 20% → 60% | +40% | 学习 Agent + 双向转换 + 三层记忆主链路 + 标准输出 |
| 数据治理 | 30% → 55% | +25% | 冲突引擎 + 时间序列快照 + 虚假信息台账 |
| RAG 检索 | 55% → 70% | +15% | 上下文压缩器完整实现 |
| 知识中台 | 45% → 60% | +15% | 动态情报自动同步 + 知识版本生命周期 + 启动同步 |
| 商业服务 | 15% → 30% | +15% | 四层数据隔离 + 付费情报 API 隔离 |
| 数据采集 | 55% → 65% | +10% | 供应商故障切换 + Redis 快照回退 + 死信队列 |

### 新增的已知缺口

- **3.6 标准化输出格式未统一**：诊断 Agent 尚未迁移到 `AgentOutput` 标准格式（学习 Agent 已完成迁移）
- **前端 Snapshots/Conflicts/FalseLedger 无 UI 渲染**：API 端点完整但前端侧栏仅有导航占位
