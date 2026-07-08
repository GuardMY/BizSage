# BizSage V1 代码审查 — 快速实现与技术债务分析

> 审查日期：2026-07-08  
> 分支：`1.1`  
> 范围：全代码库（`apps/web`、`services/api`、`services/collector`、`services/ai-worker`）

---

## 目录

1. [安全问题](#-安全问题)
2. [架构与代码质量](#-架构与代码质量)
3. [具体代码异味](#-具体代码异味)
4. [功能缺失](#-功能缺失)
5. [快速修复项（低投入高回报）](#-快速修复项低投入高回报)

---

## 🔴 安全问题

### 1. 手工 JWT 实现 — 非标准、脆弱的格式

**文件：** [services/api/src/main/java/com/bizsage/api/auth/JwtService.java](file:///e:/Code/BizSage/services/api/src/main/java/com/bizsage/api/auth/JwtService.java#L20-L25)

```java
// 当前：自定义格式 — base64(username|role|expiry).hmac_signature
String payload = user.username() + "|" + user.role().name() + "|" + expiresAt;
String encodedPayload = encode(payload);
return encodedPayload + "." + sign(encodedPayload);
```

**问题：** 这不是标准 JWT（缺少 header、算法声明、kid）。自定义格式无法被任何标准 JWT 库消费，容易产生算法混淆攻击。

**建议：** 替换为 `io.jsonwebtoken:jjwt` 或 `com.nimbusds:nimbus-jose-jwt`，生成标准的三段式 `header.payload.signature` JWT。

**优先级：** P0 — 部署前必须修复

---

### 2. 密钥硬编码在源代码中

**文件：**
- [services/api/src/main/java/com/bizsage/api/auth/JwtService.java#L16](file:///e:/Code/BizSage/services/api/src/main/java/com/bizsage/api/auth/JwtService.java#L16)
- [services/api/src/main/java/com/bizsage/api/privacy/PrivacyConfiguration.java#L11](file:///e:/Code/BizSage/services/api/src/main/java/com/bizsage/api/privacy/PrivacyConfiguration.java#L11)

```java
// JWT 密钥
@Value("${bizsage.jwt.secret:change-me-v1-dev-secret-change-me}")

// AES 加密密钥
@Value("${bizsage.privacy.key:0123456789abcdef0123456789abcdef}")
```

**问题：** 默认密钥直接写在源码注解中。即使有环境变量覆盖，这些默认值不应出现在版本控制中。

**建议：** 移除默认值。若环境变量未设置，在启动时抛出明确的配置错误，强制运维人员显式配置密钥。

**优先级：** P0 — 部署前必须修复

---

### 3. 前端默认明文密码

**文件：**
- [apps/web/app/page.tsx#L197-L198](file:///e:/Code/BizSage/apps/web/app/page.tsx#L197-L198)
- [apps/web/app/admin/page.tsx#L167-L168](file:///e:/Code/BizSage/apps/web/app/admin/page.tsx#L167-L168)

```tsx
const [username, setUsername] = useState("operator");
const [password, setPassword] = useState("password");
```

**问题：** 登录表单预填充了可用的账号密码。这在生产环境中是信息泄露风险。

**建议：** 将默认值替换为空字符串，或仅在 `process.env.NODE_ENV === "development"` 时设定。

**优先级：** P1 — 生产部署前清除

---

### 4. Token 存储在 localStorage — XSS 风险

**文件：** [apps/web/app/page.tsx#L217](file:///e:/Code/BizSage/apps/web/app/page.tsx#L217)

```tsx
window.localStorage.getItem(PROFILE_STORAGE_KEY)
```

**问题：** Bearer token 存储在 `localStorage` 中，任何 XSS 攻击都能窃取 token。

**建议：** 将 token 迁移至 `httpOnly` cookie + `SameSite=Strict` 属性。前端不再通过 JavaScript 访问 token。

**优先级：** P1 — 生产部署前修复

---

## 🟠 架构与代码质量

### 5. SSE 流解析脆弱

**文件：** [apps/web/lib/api-client.ts#L1272-L1294](file:///e:/Code/BizSage/apps/web/lib/api-client.ts#L1272-L1294)

```typescript
function parseSseEvents(raw: string): SseEvent[] {
  for (const block of raw.split(/\r?\n\r?\n/)) {
    // 如果诊断内容包含双换行符，会错误分段
  }
}
```

**问题：** 在原始文本上使用字符串 split 来解析 SSE 事件。如果诊断回答内容包含 `\n\n`，会被错误地分割为独立的事件帧。

**建议：** 使用正确的基于行的 SSE 帧读取，逐行消费 `ReadableStream`，按 `event:`/`data:` 前缀进行解析。

**优先级：** P1

---

### 6. 三个复制粘贴的 SSE 流函数

**文件：** [apps/web/lib/api-client.ts](file:///e:/Code/BizSage/apps/web/lib/api-client.ts)

| 函数 | 行数 |
|------|------|
| `streamDiagnosisEvents` | L348–L400 |
| `streamLearningEvents` | L1014–L1059 |
| `streamTransitionEvents` | L1073–L1119 |

**问题：** 三个函数逻辑几乎完全相同（~50 行每个），仅 URL 路径和错误消息不同。共约 150 行重复代码。

**建议：** 抽取为单个通用函数：

```typescript
async function streamSse(
  token: string,
  url: string,
  body: unknown,
  options: StreamDiagnosisOptions = {}
): Promise<Diagnosis>
```

**优先级：** P2

---

### 7. AI Worker 端点中重复的 LLM 错误处理

**文件：** [services/ai-worker/app/main.py](file:///e:/Code/BizSage/services/ai-worker/app/main.py)

在三个端点中逐字重复（`diagnose_endpoint` L246-266、`learn_endpoint` L308-328、`transition_endpoint` L356-375）：

```python
status = result.get("selfCheckStatus", "")
if status == LLM_NOT_CONFIGURED:
    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=503, content={...})
if status == LLM_CALL_FAILED:
    from fastapi.responses import JSONResponse
    return JSONResponse(status_code=503, content={...})
```

**建议：** 抽取为通用函数 `handle_llm_error(result)`。

**优先级：** P2

---

### 8. 巨型组件 — page.tsx（723 行）

**文件：** [apps/web/app/page.tsx](file:///e:/Code/BizSage/apps/web/app/page.tsx)

**状态变量（14 个）：**
```tsx
locale, username, password, profile, activeSection,
selectedConversationId, conversations, messageHistory,
message, diagnosis, streamingDiagnosis, report,
reportBusy, paidRows, selectedSource, busy, notice
```

**问题：** 单一组件管理认证、会话 CRUD、流式诊断、报告生成、情报展示和来源模态框。没有提取自定义 hooks，通过多层 prop drilling 向下传递 10+ 个 handler。

**建议：** 提取自定义 hooks：
- `useAuth(profile, setProfile)` — 登录/登出/会话过期
- `useConversations(profile)` — CRUD 操作
- `useDiagnosis(profile, conversationId)` — 流式诊断
- `useReport(profile)` — 报告生成

**优先级：** P2 — 在添加新功能前重构

---

### 9. Admin 页面 — 1753 行单体

**文件：** [apps/web/app/admin/page.tsx](file:///e:/Code/BizSage/apps/web/app/admin/page.tsx)

**内联视图（12 个）：**
`DashboardView`、`KnowledgeView`、`AlertsView`、`AuditView`、`ReviewsView`、`TicketsView`、`HumanView`、`RiskRulesView`、`MonitoringView`、`ListPanel`、`CompactRow`、`PanelHead`、`StatusBadge`

**建议：** 每个视图拆分为独立文件：
```
apps/web/app/admin/
├── page.tsx               # 路由 + 顶层状态
├── dashboard-view.tsx
├── knowledge-view.tsx
├── alerts-view.tsx
├── audit-view.tsx
├── reviews-view.tsx
├── tickets-view.tsx
├── human-view.tsx
├── risk-rules-view.tsx
├── monitoring-view.tsx
└── shared/                # PanelHead, StatusBadge 等
```

**优先级：** P2

---

### 10. `@ts-nocheck` 在核心逻辑中

**文件：** [apps/web/lib/conversation-workspace.ts#L1](file:///e:/Code/BizSage/apps/web/lib/conversation-workspace.ts#L1)

```typescript
// @ts-nocheck
```

**问题：** 所有函数使用 JSDoc 注释而非 TypeScript 类型。这是关键的会话路由逻辑——最需要类型安全的地方。

**建议：** 移除 `@ts-nocheck`，为所有函数参数/返回值添加 TypeScript 类型注解。

**优先级：** P2

---

### 11. 无状态管理方案

**问题：** 整个前端使用原始 `useState` + 多层 prop drilling：
- 没有 React Query / SWR 用于服务端缓存——每个 `useEffect` 手动触发 fetch，无自动重新验证、无缓存去重
- 没有 Context / Zustand 用于客户端状态
- 加载状态跨组件不一致

**建议：** 
- 引入 **TanStack Query (React Query)** 用于所有服务端状态
- 使用 **React Context** 或 **Zustand** 管理认证/profile 状态

**优先级：** P2

---

### 12. 国际化 — 内联对象字面量

**文件：** [apps/web/app/page.tsx#L39-L192](file:///e:/Code/BizSage/apps/web/app/page.tsx#L39-L192)

```tsx
const messages: Record<Locale, WorkspaceMessages> = {
  "zh-CN": {
    brandSubtitle: "对话工作台",
    loginTitle: "登录 BizSage",
    // ... 约 70 个字段
  },
  en: { /* 同样约 70 个字段 */ }
};
```

**问题：** 约 150 行双语字符串嵌入在组件内部。添加语言或修改文案需要在 JSX 逻辑中查找。

**建议：** 迁移至 `next-intl` 或 `i18next`，使用 JSON 资源文件。

**优先级：** P3

---

## 🟡 具体代码异味

### 13. `memory.py` — 脆弱的正则 fallback 提取

**文件：** [services/ai-worker/app/memory.py#L256-L313](file:///e:/Code/BizSage/services/ai-worker/app/memory.py#L256-L313)

```python
# 硬编码的中文短语 — 永远不会匹配真实用户输入
if "两家门店" in question:     # L265
if "外卖" in question:         # L271
if "堂食波动" in question:     # L277
```

**问题：** 当 LLM 不可用时，正则 fallback 匹配的是字面字符串而非语义模式。这在实际使用中完全无效。

**建议：** 要么：(a) 移除 fallback，当 LLM 不可用时优雅降级并返回空；要么 (b) 替换为通用关键词模式匹配（如 `re.search(r"\d+家[门店]", question)`）。

**优先级：** P2

---

### 14. `llm.py` — 向后兼容包装器忽略系统提示词

**文件：** [services/ai-worker/app/llm.py#L64-L82](file:///e:/Code/BizSage/services/ai-worker/app/llm.py#L64-L82)

```python
def generate_answer(question: str, context: str) -> str:
    # 注释: "the system prompt is NOT injected here"
    messages = [
        {"role": "user", "content": f"问题：{question}\n\n参考证据：\n{context}"},
    ]
```

**问题：** 遗留包装器将所有内容塞进 user 消息，不带系统提示词。这绕过了整个 `PromptAssembler` 层。

**建议：** 逐步淘汰这些包装器。强制所有调用者使用 `ModelRouter` + `PromptAssembler`。在此期间添加 `DeprecationWarning`。

**优先级：** P2

---

### 15. Collector main.py — 静默的 broad-except

**文件：** [services/collector/app/main.py#L82-L83](file:///e:/Code/BizSage/services/collector/app/main.py#L82-L83)

```python
try:
    state_store.save_fingerprint(fingerprint.fingerprint, ttl_seconds=86400)
except Exception:  # noqa: BLE001
    pass
```

**问题：** Redis 保存失败被静默吞下。无法知道去重是否正常工作。

**建议：** 至少用 `logger.warning("Redis fingerprint save failed", exc_info=True)` 替换 `pass`。

**优先级：** P2

---

### 16. 每次请求都创建新的 ModelRouter

**文件：** [services/ai-worker/app/memory.py#L169](file:///e:/Code/BizSage/services/ai-worker/app/memory.py#L169)

```python
def _extract_memories_via_llm(...):
    router = ModelRouter()  # 每次调用都新建
```

**对比：** `llm.py:53-60` 使用了模块级单例 `_router`。

**建议：** 在 `memory.py` 中复用相同的单例模式。

**优先级：** P3

---

### 17. Admin 页面内联样式

**文件：** [apps/web/app/admin/page.tsx](file:///e:/Code/BizSage/apps/web/app/admin/page.tsx)

多处内联样式：
```tsx
style={{ display: "flex", alignItems: "center", gap: "12px", flexWrap: "wrap" }}
style={{ margin: 0, fontSize: 13, color: "var(--muted)", ... }}
style={{ padding: "0 16px 12px" }}
```

**建议：** 迁移至 `globals.css` 或 CSS 模块。

**优先级：** P3

---

### 18. 搜索输入无 debounce

**文件：** [apps/web/app/admin/page.tsx#L301-L313](file:///e:/Code/BizSage/apps/web/app/admin/page.tsx#L301-L313)

```tsx
function handleSearch(q: string) {
  // 每次按键都在 6 个数组上运行 .filter()
  for (const a of alerts) ...
  for (const t of tickets) ...
  // ...
}
```

**建议：** 添加 300ms debounce。可以引入 `useDebouncedValue` hook 或使用 `lodash.debounce`。

**优先级：** P3

---

### 19. 多处 try-catch 吞没错误

**文件：** [apps/web/app/page.tsx#L222-L224](file:///e:/Code/BizSage/apps/web/app/page.tsx#L222-L224)、[apps/web/app/admin/page.tsx#L196-L198](file:///e:/Code/BizSage/apps/web/app/admin/page.tsx#L196-L198)

```tsx
try {
  return localStorage.getItem("bizsage.admin.railCollapsed") === "1";
} catch { /* noop */ }
```

**建议：** 至少用 `console.error("Failed to read localStorage:", e)` 替换。

**优先级：** P3

---

## 🔵 功能缺失

### 20. Admin 导航栏条目无实现视图

**文件：** [apps/web/app/admin/page.tsx#L672-L673](file:///e:/Code/BizSage/apps/web/app/admin/page.tsx#L672-L673)

Admin 导航定义了这些部分但从未渲染：

| 导航 | 导航键 | API 函数已存在？ |
|------|-----------|---------------------|
| Conflicts | `"conflicts"` | ✅ `fetchAdminConflicts` |
| False Intel | `"falseLedger"` | ✅ `fetchAdminFalseLedger` |
| Snapshots | `"snapshots"` | ✅ `fetchAdminSnapshots` |

在 admin 页面的 switch 语句中，这三个 case 缺少组件渲染。

**建议：** 实现 `ConflictsView`、`FalseLedgerView`、`SnapshotsView` 组件并连接到已有的 API 函数。

**优先级：** P2

---

### 21. API 端点无分页

**文件：**
- [services/api/src/main/java/com/bizsage/api/users/UserController.java#L23](file:///e:/Code/BizSage/services/api/src/main/java/com/bizsage/api/users/UserController.java#L23)
- [services/api/src/main/java/com/bizsage/api/intelligence/IntelligenceController.java#L37](file:///e:/Code/BizSage/services/api/src/main/java/com/bizsage/api/intelligence/IntelligenceController.java#L37)

```java
@GetMapping
ApiResponse<List<UserView>> listUsers(...)  // 返回全部 — 无分页

@GetMapping
ApiResponse<List<IntelligenceItem>> list(...) // 返回全部 — 无分页
```

**建议：** 添加 `page`/`size` 查询参数，返回分页响应（含 `total`、`hasMore`）。

**优先级：** P2

---

### 22. 监控数据是硬编码的桩

**文件：** [apps/web/app/admin/page.tsx#L1589-L1602](file:///e:/Code/BizSage/apps/web/app/admin/page.tsx#L1589-L1602)

```
AI Inference:       HEALTHY   ← 静态字符串
Database:           HEALTHY   ← 静态字符串
Cache Layer:        HEALTHY   ← 静态字符串
```

标注："Prometheus, CloudWatch are targeted for V3-6"

**建议：** 要么实现实际的健康检查探测（在 API 中已有 `/health` 端点），要么对这些指标使用真实的 `/health` 代理。

**优先级：** P3

---

### 23. 诊断报告是元数据桩

**文件：** [apps/web/lib/api-client.ts#L76-L85](file:///e:/Code/BizSage/apps/web/lib/api-client.ts#L76-L85)

```typescript
export type DiagnosisReport = {
  format: "PDF";  // ← 声称是 PDF，但只返回 JSON 元数据
  summary: string;
  // ... 无二进制数据，无下载链接
};
```

**建议：** 要么实现真实的 PDF 生成（例如，使用 Python `reportlab` 或 Java `iText`），要么重命名 `format` 字段为 `"METADATA"` 以保持诚实。

**优先级：** P3

---

### 24. 无 E2E 测试 / CI/CD

**现状：**
| 层 | 测试文件 | 框架 |
|-------|------------|----------|
| Collector | 5 | pytest |
| API | 9 | JUnit |
| AI Worker | 12 | pytest |
| Web | 2 | (基本) |
| **跨服务** | **0** | — |

无 CI 管道配置（`.github/workflows/` 目录不存在）。

**建议：**
- 为主要用户旅程添加至少一个端到端测试（登录 → 创建会话 → 发送诊断 → 验证 SSE 流）
- 设置 GitHub Actions 工作流来运行 `mvn test`、`pytest` 和 `npm test`
- 添加 Docker Compose 集成测试

**优先级：** P2

---

### 25. 无 React Error Boundary

**问题：** 子组件中任何未处理的异常都会导致整个应用崩溃并白屏。

**建议：** 在 `apps/web/app/components/` 下添加 `ErrorBoundary.tsx`：

```tsx
class ErrorBoundary extends React.Component<Props, { error: Error | null }> {
  componentDidCatch(error: Error) { this.setState({ error }); }
  render() {
    if (this.state.error) return <FallbackUI />;
    return this.props.children;
  }
}
```

**优先级：** P2

---

## 📊 快速修复项（低投入高回报）

按预估工作量排序：

| # | 问题 | 文件 | 工作量 |
|---|-------|------|--------|
| 1 | 提取通用的 `streamSse()` 函数，消除 150 行重复代码 | `api-client.ts` | 30 分钟 |
| 2 | 提取 `handle_llm_error()` 到 ai-worker main.py | `main.py` | 15 分钟 |
| 3 | 为 `handleSearch` 添加 300ms debounce | `admin/page.tsx` | 10 分钟 |
| 4 | 将裸 `catch {}` 替换为 `console.error` | `page.tsx`、`admin/page.tsx` | 10 分钟 |
| 5 | 将内联样式迁移至 CSS 类 | `admin/page.tsx` | 30 分钟 |
| 6 | 从 `conversation-workspace.ts` 移除 `@ts-nocheck` 并添加类型 | `lib/` | 1 小时 |
| 7 | 在 `_extract_memories_via_llm` 中复用 ModelRouter 单例 | `memory.py` | 5 分钟 |
| 8 | 添加 React Error Boundary 组件 | `components/` | 20 分钟 |
| 9 | 将 `except: pass` 替换为 `logger.warning` | `collector/main.py` | 5 分钟 |
| 10 | 为 admin Conflicts/Snapshots/FalseLedger 连接 UI | `admin/` | 2–3 小时 |

---

## 总结

该项目是一个**架构良好的 V1 MVP**，拥有完整的实现：
- ✅ Collector：治理、SimHash 冲突检测、熔断器、Excel/HTML 解析
- ✅ AI Worker：RAG 混合搜索、诊断/学习 Agent、三层记忆、模型路由、自我检查重试
- ✅ API：RBAC、会话、情报审查工作流、知识版本控制（草稿/审查/发布）

"快速实现"的核心模式是：

1. **巨型单体组件** — `page.tsx`（723 行）和 `admin/page.tsx`（1753 行）将整个功能面塞入单一文件
2. **复制粘贴代码** — 3 个相同的 SSE 流函数，3 个相同的 LLM 错误处理块
3. **手工安全实现** — 自定义 JWT 格式、硬编码密钥、token 存 localStorage
4. **静默错误吞没** — 多处 `except: pass` 和 `catch {}`
5. **缺失的 UI 连接** — 3 个 admin 导航栏条目有 API 函数但无渲染组件

**关键路径建议：** 在任何生产部署之前，优先处理安全项（JWT、密钥、token 存储）。然后在前端添加新功能之前，处理巨型组件和复制粘贴的代码。

---

*由代码审查生成 · 2026-07-08*
