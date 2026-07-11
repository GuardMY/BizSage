# 学习与诊断 Agent 引导式工作流设计
**日期：** 2026-07-11

## 目标

为 Learning Agent 和 Diagnosis Agent 增加一套引导式工作流，包括自我介绍、右侧快捷提问栏、基于上下文的下一步建议、持久化的高频问题池，以及诊断完成控制。

本设计覆盖：
- Web UI 交互
- 后端 API 数据结构与接口
- `services/ai-worker` 的 prompt、上下文和输出结构
- 问题池持久化与排序逻辑

## 背景与缺口

当前实现已经具备诊断、学习、模式切换、SSE 流式输出、RAG、记忆和报告生成功能。仍然缺少的部分包括：
- Agent 不能稳定地先做自我介绍并说明交互方式
- 右侧推荐栏不能基于当前对话动态刷新
- 没有共享的高频问题池和稳定的排序模型
- 诊断没有显式暴露计划、画像建立阶段、继续补充信息路径和完成信号

## 设计原则

1. 右侧建议必须高可用，尽量减少用户思考和输入成本。
2. 建议需要是动态的，但不能只依赖实时 LLM 输出；历史使用行为也必须影响排序。
3. 学习和诊断要共享同一套数据骨架，但保留不同的意图、节奏和终止规则。
4. 诊断必须主动推动用户补齐经营画像，而不是过早给出空泛结论。
5. 用户始终应当可以继续提问、点击快捷问题或刷新建议。

## 用户体验

### 1. Learning Agent

首次进入 Learning Agent 时，应输出固定格式的自我介绍：
- 说明自己是行业学习助手
- 说明可以帮助用户理解节点、规则、指标、风险和机会
- 说明会基于当前上下文推荐下一步最值得学习的内容
- 说明右侧推荐栏可以点击，并且可以刷新

右侧推荐栏应展示三类内容：
- 用户下一步最可能想了解的节点
- 当前节点下更细粒度的区块
- 与当前上下文相关的延展方向

### 2. Diagnosis Agent

首次进入 Diagnosis Agent 时，应输出固定格式的自我介绍：
- 说明自己是经营诊断助手
- 说明会先建立经营基线，再分层诊断
- 说明会通过结构化提问来推进诊断
- 说明右侧推荐栏会给出可能的经营问题和缺失信息

在诊断开始阶段，Agent 应展示一份草案计划，例如：
- 用户所在行业
- 业务是否为线下 / 实体经营
- 大致投资规模
- 门店 / 仓库 / 团队规模
- 基础营收、成本、利润、流量和渠道情况

右侧推荐栏应展示三类内容：
- 最可能的经营问题
- 当前阶段缺失的画像字段
- 最可能影响诊断结果的细节

## 推荐栏逻辑

### 1. 实时候选生成

每轮对话结束后，AI Worker 应生成 `recommendationCandidates`：
- Learning Agent 候选包括可学习的节点、区块、方向和细节
- Diagnosis Agent 候选包括后续经营问题、画像字段和风险点

候选来源包括：
- 当前用户问题
- 当前对话上下文
- 命中的知识、情报和记忆
- 当前 Agent 状态

### 2. 高频问题池

每个行业都应维护一个持久化问题池，细粒度包括：
- 节点级问题
- 区块级问题
- 细节级问题
- 追问级问题

每条记录需保存：
- `topLevelScore`：LLM 结合行业结构判断出的优先级
- `usageCount`：历史点击 / 使用次数
- `ratingAvg`：用户平均评分
- `ratingCount`：评分次数
- `lastUsedAt`：最后使用时间
- `status`：启用、隐藏或归档

### 3. 排序规则

最终排序分应为混合分数：
- LLM 判断分
- top-level score
- 使用频率
- 评分分数
- 新鲜度奖励或惩罚

排序目标：
- 保持与当前上下文相关
- 持续浮现历史上真正有用的问题
- 避免推荐长期塌缩成一小组总是最热的问题

### 4. 刷新行为

如果用户不喜欢当前建议，可以刷新：
- Learning Agent 刷新为“我下一步应该学什么？”
- Diagnosis Agent 刷新为“我下一步应该补什么经营信息？”

刷新必须保留：
- 当前对话上下文
- 当前行业
- 当前 Agent 状态
- 最近已展示问题的黑名单

## 诊断工作流状态

Diagnosis 应按以下状态推进：

1. `INTRO`
- 说明功能和交互方式
- 展示当前诊断计划

2. `PROFILE_GATHERING`
- 收集基础经营事实
- 优先补齐缺失字段

3. `ISSUE_HYPOTHESIS`
- LLM 总结 1-3 个最可能的核心经营问题

4. `DETAIL_PROBING`
- 追问那些会改变诊断结果的关键细节

5. `READY_TO_CLOSE`
- LLM 判断信息已足够生成报告
- 用户仍可选择继续补充

6. `CLOSED`
- 生成诊断报告
- 持久化结论、证据和记忆候选

## 完成规则

诊断是否完成，应由 LLM 与规则共同判断：
- 经营画像达到最低完整度阈值
- 核心问题集收敛到 1-3 个主要问题
- 关键冲突或缺口已被显式标注
- 建议已经能拆分为可执行优先级项

允许用户继续补充信息的情况包括：
- 用户明确表示还想继续补充
- LLM 标记当前状态仍不充分
- 关键指标存在冲突
- 用户希望在关闭前先得到解释

## API 设计

### 1. 会话元数据

为学习和诊断会话增加持久化工作流字段：
- `agentMode`
- `workflowStage`
- `profileCompleteness`
- `primaryIssueTags`
- `recommendedQuestionIds`
- `closedBy`
- `closedReason`

### 2. Recommendation API

增加推荐接口，返回：
- 当前推荐问题列表
- 每条推荐的来源与排序分
- 是否允许刷新
- blacklist / dedupe 信息

### 3. Question Pool API

增加问题池管理接口，用于：
- 查询行业问题池
- 新建或更新问题
- 批量写入 usageCount
- 写入评分
- 刷新 top-question 缓存

### 4. Diagnosis State API

增加工作流状态接口，用于：
- 上报当前诊断阶段
- 上报是否允许关闭
- 上报用户是否选择继续补充

## AI Worker 设计

### 1. Learning Agent

Learning Agent 输出应扩展为：
- `answer`
- `recommendationCandidates`
- `currentTopic`
- `nextBestTopics`
- `memoryCandidates`

### 2. Diagnosis Agent

Diagnosis Agent 输出应扩展为：
- `answer`
- `recommendedQuestions`
- `workflowStage`
- `profileMissingFields`
- `completionSignal`
- `memoryCandidates`

### 3. Prompt 结构

系统 prompt 应新增两组约束：
- 自我介绍与交互说明
- 引导式推荐与收口判断

诊断 prompt 还应补充：
- 主动收集画像字段
- 先问经营基线
- 缺失字段优先
- 用户可以继续补充

学习 prompt 还应补充：
- 基于当前上下文推荐下一步最值得学习的内容
- 避免一次性输出过多
- 平衡热门问题和长尾问题

## 排序与持久化

问题池应按行业持久化，并可选按地区和会员层级分层。

更新策略：
- 点击快捷问题时，`usageCount + 1`
- 用户评分时，更新 `ratingAvg` 和 `ratingCount`
- LLM 重判时，更新 `topLevelScore`
- 定时任务周期性重排并回写缓存字段

## 兼容性

1. 现有单轮诊断和学习接口必须继续可用。
2. 推荐 payload 应保持向后兼容，旧客户端可以忽略新增字段。
3. 诊断完成信号应先作为软状态，而不是硬中断。
4. 如果右侧推荐栏为空，仍必须允许手动输入。

## 验收标准

Learning Agent：
- 首次进入时会介绍自己及交互方式
- 右侧展示基于上下文的下一步学习问题
- 快捷问题能从行业问题池中排序产出
- 支持刷新建议

Diagnosis Agent：
- 首次进入时会介绍自己及交互方式
- 早期能展示诊断计划和缺失画像字段
- 右侧能展示最可能的经营问题
- 当 LLM 判断诊断已就绪时能发出收口信号
- 在生成报告前仍允许用户继续补充信息

Backend And AI Worker：
- 推荐数据可持久化
- 问题池可按行业查询、更新和排序
- 流式接口可以携带推荐与阶段信息
- 诊断与学习输出可以扩展且不破坏现有主路径

## 2026-07-11 实现进度回顾

### 整体判断

当前实现已经完成了不少表结构、接口和返回字段骨架，但这套引导式工作流还没有形成真正的端到端闭环。

按模块粗略估算：
- 数据库与持久化骨架：约 75%
- 后端接口与 payload 骨架：约 70%
- AI 引导式工作流真实生效：约 30%
- 前端引导式体验：约 35%
- 本设计整体闭环完成度：约 30%

### 规格对照表

| 规格点 | 状态 | 当前实现 | 主要遗漏 |
|---|---|---|---|
| Learning Agent 首次固定自我介绍 | 部分实现 | 学习链路已经支持 `recommendationCandidates`、`currentTopic`、`nextBestTopics` 等引导字段。 | 当前 Web UI 没有独立的 Learning workspace，因此“首次进入固定介绍”没有形成真实前台体验。 |
| Learning 右侧 rail：下一节点 / 当前细分块 / 延展方向 | 部分实现 | 后端与 Worker 已预留学习侧推荐字段。 | 前端没有学习侧推荐栏，推荐也还没有与实时对话状态混排。 |
| Diagnosis Agent 首次固定自我介绍 | 部分实现 | 诊断 prompt 已明确要求先自我介绍，并说明会先建立经营画像再分层诊断。 | 目前仍主要依赖 prompt 约束，而不是稳定的首次进入模板，也没有与后续轮次显式区分。 |
| Diagnosis 开场展示 draft plan | 未实现 | 会话与响应里已经有工作流字段骨架。 | 还没有真正生成并展示行业、线上线下、投资额、规模、营收成本、流量渠道等开场计划。 |
| Diagnosis 右侧 rail：业务问题 / 缺失画像字段 / 高影响细节 | 部分实现 | 前端已有诊断推荐栏。 | 当前只展示扁平问题列表，没有按三类推荐意图分组。 |
| 每轮 AI Worker 产出 recommendation candidates | 部分实现 | 诊断侧已返回 `recommendedQuestions`，学习侧已返回 `recommendationCandidates`。 | 诊断推荐目前更像是 RAG Top 结果改写，而不是综合当前问题、上下文、记忆和状态的候选生成。 |
| 高频问题池持久化 | 已实现 | `question_pools` 表、实体、store、controller 和种子数据均已存在。 | 持久化层面暂无明显结构缺口。 |
| 问题池字段：score / usage / rating / last-used / status | 已实现 | 已具备 `topLevelScore`、`usageCount`、`ratingAvg`、`ratingCount`、`lastUsedAt`、`status`。 | schema 层暂无明显缺口。 |
| 混合排序：LLM + topLevel + usage + rating + freshness | 部分实现 | 推荐服务已结合 top-level 分、usage 和 rating。 | 仍缺少实时 LLM 相关性分、freshness 奖惩，以及防止长期热榜塌缩的逻辑。 |
| Refresh 保留上下文 / 行业 / 状态 / 黑名单 | 部分实现 | 已有 refresh 接口和前端刷新按钮。 | 当前 refresh 基本还是重新读问题池，没有基于当前对话动态重算，也没有真正落实 blacklist / dedupe。 |
| Diagnosis 状态机：`INTRO` 到 `CLOSED` | 未实现 | `workflowStage` 已在 Java、TypeScript 和数据库中预留。 | 当前服务链路里基本仍固定传 `INTRO`，没有真正推进到画像收集、问题假设、细节追问、可收口和关闭阶段。 |
| Completion rules 与可收口判断 | 未实现 | `completionSignal` 和 `diagnosisClosable` 已在协议中预留。 | Java 端当前固定传 `diagnosisClosable=false`，也没有画像完整度、问题收敛度等规则判断。 |
| 用户可继续补充信息再关闭 | 未实现 | 返回里已有 `completionSignal`。 | 没有“继续补充信息/确认收口”的 UI、状态持久化和后端流程。 |
| 会话工作流元数据持久化 | 部分实现 | 会话 schema 与 store 已支持 `agentMode`、`workflowStage`、`profileCompleteness`、`primaryIssueTags`、`recommendedQuestionIds`、`closedBy`、`closedReason`。 | 诊断与学习主链路没有通过 `ConversationStore.updateWorkflow(...)` 把真实状态写回。 |
| Recommendation API | 已实现 | `/api/conversations/{conversationId}/recommendations` 已可返回推荐列表。 | 返回结果仍更接近静态问题池读取，而不是本设计里的动态引导推荐契约。 |
| Question Pool API | 已实现 | 查询、upsert、rating、usage、refresh 接口均已提供。 | 更高层的运维重排与缓存刷新编排还没有。 |
| Diagnosis State API | 未实现 | 目前还没有单独的 workflow-state 接口。 | 不能独立查询当前阶段、是否可关闭、是否继续补充。 |
| Learning 输出扩展 | 部分实现 | Java 端学习 payload 已包含 `recommendationCandidates`、`currentTopic`、`nextBestTopics`、`workflowStage`、`profileMissingFields`、`completionSignal`。 | Worker 请求模型没有接住对应工作流控制字段，前端也没有学习工作流展示。 |
| Diagnosis 输出扩展 | 部分实现 | 诊断返回已包含 `recommendedQuestions`、`workflowStage`、`profileMissingFields`、`completionSignal`。 | 大多数值仍偏静态或浅逻辑，且没有形成持久化和 UI 闭环。 |
| Prompt 扩展：介绍、推荐、收口判断 | 部分实现 | 诊断 prompt 已包含“基线优先”的引导要求。 | 设计里两组更完整的 guided workflow prompt 约束，还没有在两个 Agent 上都稳定落地。 |
| 主动收集画像字段与缺口优先级 | 未实现 | 目前只有部分 prompt 倾向。 | 没有结构化缺口识别，也没有按缺口优先级追问。 |
| 推荐点击写 usageCount | 部分实现 | 后端已有 `recordUsage(...)`，服务端也会记录推荐项 usage。 | 前端点击推荐目前只是把问题填入输入框，没有把“点击即使用”作为独立用户动作确认回写。 |
| rating 写回 | 后端已实现 | rating API 已存在。 | 前端没有评分入口。 |
| LLM 重判 `topLevelScore` | 未实现 | 目前只能通过 upsert 手动写入。 | 缺少自动重评分机制。 |
| 定时重排与缓存回写 | 未实现 | 还没有定时任务式的重排流程。 | 周期性重排和缓存分数回写尚未落地。 |
| 按行业持久化，可选 region / membership 分层 | 部分实现 | 当前已按 `industryId`、`regionId`、`agentMode` 分层。 | 仍缺少 membership tier 分层。 |
| 兼容既有诊断与学习主路径 | 已实现 | 现有接口和 payload 仍保持可兼容的增量扩展。 | 暂未发现明显兼容性缺口。 |
| completion signal 先做 soft state | 已实现 | 当前 `completionSignal` 仍只是软状态字段。 | 协议层暂无明显缺口。 |
| right rail 为空时仍允许手输 | 已实现 | 诊断输入框始终可用。 | 暂无明显缺口。 |

### 当前最关键的 5 个缺口

1. Java API 虽然已经传递不少工作流控制字段，但 AI Worker 请求模型还没有接住大部分字段，导致这些值实际上被忽略。
2. 会话工作流元数据在 schema 和 store 里已经存在，但诊断和学习主链路没有真实回写阶段、完整度、问题标签、推荐 ID 和关闭原因。
3. 诊断状态机并没有真正运行起来，`workflowStage` 基本仍停留在 `INTRO`。
4. 推荐刷新还不是真动态计算，当前更接近重新读取静态问题池。
5. 前端还没有把学习工作流、诊断计划、缺失画像提示、收口控制和“继续补充信息”动作真正做出来。
