# BizSage Prompt 作用阶段归档

本文是 `prompt-stage-archive.md` 的中文对应版本，用于按模块归档仓库中实际生效的业务 prompt，并说明每个 prompt 所处的运行阶段、承担的作用以及组装方式。

## 1. 范围与归档口径

本归档覆盖 BizSage 运行时真实使用的 prompt：

- `services/ai-worker/app/prompt_library` 中的分层系统 prompt
- `services/ai-worker/app` 中诊断与学习执行阶段的 prompt
- `services/ai-worker/app/agent_transition.py` 中的双 Agent 切换 prompt
- `services/ai-worker/app/memory.py` 中的记忆提炼 prompt
- `services/ai-worker/app/llm.py` 中遗留兼容层的 prompt 入口

本归档明确不包含：

- `guidePrompt` 这类 CSS 类名
- `"system prompt"`、`"test prompt"` 这类仅用于测试占位的字符串
- 学习意图识别或正则提取所用的关键词规则，因为它们属于路由逻辑，不是发送给 LLM 的 prompt

## 2. Prompt 阶段总览

| 运行阶段 | 主要模块 | Prompt 类型 | 主要作用 |
|----------|----------|-------------|----------|
| 生成前的系统约束注入 | `prompt_library` | 分层 system prompt | 统一注入角色、行业、地域、合规、输出结构与来源免责边界 |
| 诊断最终生成 | `agent.py` | 诊断 user prompt | 将用户问题与压缩后的证据、记忆上下文拼装为诊断请求 |
| 学习最终生成 | `learning_agent.py` | 学习 user prompt | 将意图、学习模式、链路节点、记忆上下文与证据拼装为教学请求 |
| 双 Agent 模式切换 | `agent_transition.py` | 切换 prompt | 在学习与诊断之间改写同一用户目标的表达方式 |
| 回合结束后的记忆提炼 | `memory.py` | 记忆提炼 system/user prompt | 从刚完成的一轮问答中抽取可长期保留的用户画像与学习进度 |
| 遗留兼容调用 | `llm.py` | 薄包装 prompt | 维持旧接口可用，但不再具备分层 system prompt 约束 |

## 3. 按模块归档

### 3.1 `services/ai-worker/app/prompt_library`

该模块负责标准化 system prompt 阶段。`PromptAssembler` 会按固定顺序组装六层 prompt，并在诊断或学习生成前作为 `system` 消息注入。

| Prompt / 片段 | 文件 | 阶段 | 作用 |
|---------------|------|------|------|
| `ROLE_DIAGNOSIS` | `defaults.py` | 系统约束注入 | 定义诊断 Agent 身份、证据优先语气和不确定性处理方式 |
| `ROLE_LEARNING` | `defaults.py` | 系统约束注入 | 定义学习 Agent 身份、教学语气和新手友好解释方式 |
| `INDUSTRY_MANUFACTURING` | `defaults.py` | 系统约束注入 | 补充制造业特有的经营观察维度 |
| `INDUSTRY_ECOMMERCE` | `defaults.py` | 系统约束注入 | 补充电商特有的经营观察维度 |
| `INDUSTRY_LOCAL_SERVICES` | `defaults.py` | 系统约束注入 | 补充本地生活服务特有的经营观察维度 |
| `REGION_CN_HONGKONG` | `defaults.py` | 系统约束注入 | 补充香港地区的法律、税务与政策本地性提醒 |
| `REGION_CN_SHANGHAI` | `defaults.py` | 系统约束注入 | 补充上海地区的成本结构提醒 |
| `COMPLIANCE` | `defaults.py` | 系统约束注入 | 统一约束不得给出投资/法律/财务建议，并要求证据充分、隐私提醒、合规红线控制 |
| `OUTPUT_DIAGNOSIS` | `defaults.py` | 系统约束注入 | 强制诊断回答采用“关键发现/风险提示/行动建议/证据支撑”结构 |
| `OUTPUT_LEARNING` | `defaults.py` | 系统约束注入 | 强制学习回答采用“核心概念/行业案例/关键指标/下一步学习”结构 |
| `SOURCE_DISCLAIMER` | `defaults.py` | 系统约束注入 | 强制来源标注与标准免责声明 |

组装规则：

- 顺序固定为 `role -> industry -> region -> compliance -> output_format -> source_disclaimer`。
- 优先读取 `PROMPT_*` 环境变量覆盖，其次回退到由具体到通用的默认层。
- `agent.py` 用 `AgentMode.DIAGNOSIS` 组装该 prompt，`learning_agent.py` 用 `AgentMode.LEARNING` 组装该 prompt。

### 3.2 `services/ai-worker/app/agent.py`

该模块负责诊断最终生成阶段的 prompt。

| Prompt / 片段 | 阶段 | 作用 | 主要输入 |
|---------------|------|------|----------|
| 组装后的诊断 `system_prompt` | 生成前 system 阶段 | 在模型看到用户问题前施加六层诊断约束 | mode、`industry_id`、`region_id` |
| 诊断 `user` 消息：`question + evidence` | 诊断最终生成 | 将真实诊断任务连同压缩证据和记忆上下文发送给模型 | 原始问题、压缩后的 RAG 证据、记忆上下文 |

执行特征：

- 该模块的 `user` prompt 故意保持简短，结构要求和合规要求主要由 system prompt 承担。
- 如果存在冲突标签或完全没有可用证据，该模块会直接返回受控结果，不进入 LLM 生成 prompt 阶段。

### 3.3 `services/ai-worker/app/learning_agent.py`

该模块负责学习最终生成阶段的 prompt。

| Prompt / 片段 | 阶段 | 作用 | 主要输入 |
|---------------|------|------|----------|
| 组装后的学习 `system_prompt` | 生成前 system 阶段 | 在生成前施加六层学习约束 | mode、`industry_id`、`region_id` |
| `_build_learning_prompt(...)` 结果 | 学习最终生成 | 将学习任务转换为结构化教学请求 | 用户问题、意图类型、学习模式、链路节点、记忆上下文、压缩证据 |
| `mode_hint` 片段 | 学习最终生成 | 告诉模型当前是快速入门、全链路学习还是节点深挖 | `learning_mode`、链路节点 |
| `intent_hint` 片段 | 学习最终生成 | 告诉模型用户关注行业概览、节点学习、指标、风险、潜规则还是政策问题 | `intent_type`、链路节点 |

执行特征：

- 学习 prompt 比诊断 prompt 更丰富，因为它还要携带教学模式和意图解释。
- 记忆上下文进入 `user` prompt，而不是写进 system prompt，因为它属于当前回合的动态信息。

### 3.4 `services/ai-worker/app/agent_transition.py`

该模块负责模式切换阶段的 prompt。

| Prompt / 片段 | 阶段 | 作用 | 主要输入 |
|---------------|------|------|----------|
| `_learn_to_diagnose_prompt(...)` | 双 Agent 模式切换 | 把学习对话改写为面向诊断的提问 | 当前用户问题、链路节点、学习摘要 |
| `_diagnose_to_learn_prompt(...)` | 双 Agent 模式切换 | 把诊断对话改写为面向学习的提问 | 当前用户问题、链路节点、诊断摘要 |
| `build_transition_prompt(...)` | 双 Agent 模式切换 | 选择正确的切换 prompt 构造器；同模式时原样返回问题 | 来源模式、目标模式 |

执行特征：

- 这些切换 prompt 不是独立 system prompt，而是“问题改写器”，用于给目标 Agent 喂一个带上下文的用户问题。
- 改写完成后，目标 Agent 仍然会继续叠加自己的分层 system prompt 和常规生成 prompt。

### 3.5 `services/ai-worker/app/memory.py`

该模块负责回合结束后的记忆提炼 prompt。

| Prompt / 片段 | 阶段 | 作用 | 主要输入 |
|---------------|------|------|----------|
| `_MEMORY_EXTRACTION_SYSTEM_PROMPT` | 回合后记忆提炼 | 定义提炼分类、置信度规则、JSON 输出结构和禁止编造要求 | 无；静态 system 指令 |
| `_extract_memories_via_llm()` 中的 `user_prompt` | 回合后记忆提炼 | 提供刚完成的问题、回答、可选链路节点与既有记忆键，用于去重提炼 | 用户问题、助手回答、链路节点、既有记忆 |

执行特征：

- 该 prompt 在诊断、学习或切换结果已经生成后执行。
- 如果提炼模型失败或返回非法 JSON，代码会退回正则提取，不会阻塞主 Agent 结果。

### 3.6 `services/ai-worker/app/llm.py`

该模块保留遗留兼容 prompt 路径。

| Prompt / 片段 | 阶段 | 作用 | 当前状态 |
|---------------|------|------|----------|
| `generate_answer()` 的单条 `user` 消息 | 遗留直接生成 | 只发送 `question + evidence`，不带 system prompt | 已废弃；绕过 `PromptAssembler` |
| `generate_answer_learning()` 的单条 `user` 消息 | 遗留直接生成 | 发送旧版学习上下文，不带分层约束 | 已废弃；绕过 `PromptAssembler` |

运行说明：

- 这些包装器仍然是代码中的 prompt 入口，但已明确标记为 deprecated，不应再作为新逻辑接入点。

## 4. 按阶段阅读的建议视图

如果从运行链路而不是文件名来理解 prompt，仓库中的 prompt 更适合分成四层：

1. `prompt_library`：稳定的 system 治理层
2. `agent.py` 与 `learning_agent.py`：任务执行层
3. `agent_transition.py`：模式切换适配层
4. `memory.py`：回合后提炼层

这种分法比只按文件名或只按 Agent 类型归类，更贴近仓库当前的真实调用顺序。
