# 会话列表归档与删除实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Web 会话列表增加直接归档/删除动作，并补齐 API 侧的会话软删除能力。

**Architecture:** 继续通过现有 `status` 字段管理会话生命周期。API 新增软删除端点，并在会话列表中过滤已删除记录；Web 侧则基于现有工作区选择辅助逻辑，渲染按页签区分的列表标题和行级操作。

**Tech Stack:** Next.js、React、TypeScript、Node test runner、Spring Boot、MockMvc、Maven

---

### Task 1: 用失败测试锁定状态行为

**Files:**
- Modify: `apps/web/tests/conversation-workspace.test.mjs`
- Modify: `services/api/src/test/java/com/bizsage/api/BusinessWorkflowApiTest.java`

- [ ] **Step 1: 先写 Web 失败回归测试**

```javascript
test("ignores deleted conversations in workspace partitions", async () => {
  const { partitionConversations } = await import("../lib/conversation-workspace.ts");
  const conversations = [
    { id: 4, title: "Deleted", status: "DELETED", regionId: "cn", industryId: "retail" },
    { id: 3, title: "Archived", status: "ARCHIVED", regionId: "cn", industryId: "retail" },
    { id: 2, title: "Active", status: "ACTIVE", regionId: "cn", industryId: "retail" }
  ];

  const result = partitionConversations(conversations);

  assert.deepEqual(result.active.map((item) => item.id), [2]);
  assert.deepEqual(result.archived.map((item) => item.id), [3]);
});
```

- [ ] **Step 2: 运行 Web 测试并确认它失败**

Run: `npm test -- conversation-workspace.test.mjs`
Expected: FAIL，因为已删除会话当前仍会进入归档分组。

- [ ] **Step 3: 再写 API 失败集成测试**

```java
@Test
void archivedConversationCanBeSoftDeletedAndDisappearsFromList() throws Exception {
  String token = login("user");
  long conversationId = createConversation(token, "待删除归档会话");

  mvc.perform(post("/api/conversations/" + conversationId + "/archive")
      .header("Authorization", "Bearer " + token))
    .andExpect(status().isOk());

  mvc.perform(post("/api/conversations/" + conversationId + "/delete")
      .header("Authorization", "Bearer " + token))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.status").value("DELETED"));

  mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data[?(@.id == " + conversationId + ")]").isEmpty());
}
```

- [ ] **Step 4: 运行 API 测试并确认它失败**

Run: `mvn -Dtest=BusinessWorkflowApiTest#archivedConversationCanBeSoftDeletedAndDisappearsFromList test`
Expected: FAIL，因为 `/delete` 端点还不存在，而且列表也还没过滤软删除记录。

### Task 2: 实现 API 软删除

**Files:**
- Modify: `services/api/src/main/java/com/bizsage/api/conversations/ConversationStore.java`
- Modify: `services/api/src/main/java/com/bizsage/api/conversations/ConversationController.java`

- [ ] **Step 1: 增加最小化 Store 能力**

```java
public Conversation softDelete(String ownerUsername, long id) {
  int updated = jdbcTemplate.update("""
      update conversations
         set status = 'DELETED', update_time = current_timestamp
       where id = ? and owner_username = ? and status = 'ARCHIVED'
      """, id, ownerUsername);
  if (updated == 0) {
    throw new IllegalArgumentException("conversation not found");
  }
  return findForOwner(ownerUsername, id);
}
```

- [ ] **Step 2: 在列表结果中过滤删除行**

```java
where owner_username = ?
  and status <> 'DELETED'
```

- [ ] **Step 3: 暴露删除端点**

```java
@PostMapping("/{id}/delete")
ApiResponse<Conversation> delete(@PathVariable long id, Principal principal, HttpServletRequest request) {
  return ApiResponse.ok(conversationStore.softDelete(principal.getName(), id), requestId(request));
}
```

- [ ] **Step 4: 重新运行 API 测试**

Run: `mvn -Dtest=BusinessWorkflowApiTest#archivedConversationCanBeSoftDeletedAndDisappearsFromList test`
Expected: PASS

### Task 3: 实现 Web 侧边栏动作与文案

**Files:**
- Modify: `apps/web/lib/conversation-workspace.ts`
- Modify: `apps/web/lib/api-client.ts`
- Modify: `apps/web/app/components/workspace-types.ts`
- Modify: `apps/web/app/components/conversation-sidebar.tsx`
- Modify: `apps/web/app/page.tsx`

- [ ] **Step 1: 在工作区分组时排除已删除会话**

```typescript
if (conversation.status === "DELETED") {
  continue;
}
```

- [ ] **Step 2: 增加 API Client 删除方法**

```typescript
export async function deleteConversation(token: string, conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/delete`, {
    method: "POST",
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Delete conversation failed");
  return envelope.data;
}
```

- [ ] **Step 3: 扩展工作区文案与侧边栏参数**

```typescript
diagnosisConversationList: string;
archiveConversationList: string;
archiveConversation: string;
deleteConversation: string;
```

- [ ] **Step 4: 渲染按页签切换的列表标题与行级动作**

```tsx
const title = activeSection === "archive" ? t.archiveConversationList : t.diagnosisConversationList;
```

```tsx
{showArchiveAction && (
  <button onClick={(event) => { event.stopPropagation(); onArchiveConversation(conversation.id); }} />
)}
{showDeleteAction && (
  <button onClick={(event) => { event.stopPropagation(); onDeleteConversation(conversation.id); }} />
)}
```

- [ ] **Step 5: 接入页面级处理函数**

```typescript
await archiveConversation(profile.token, conversationId);
await deleteConversation(profile.token, conversationId);
```

- [ ] **Step 6: 重新运行 Web 测试套件**

Run: `npm test`
Expected: PASS

### Task 4: 记录变更

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `CHANGELOG-zh-CN.md`

- [ ] **Step 1: 添加英文变更日志条目**

```markdown
### Conversation Sidebar Archive/Delete Actions
```

- [ ] **Step 2: 添加中文变更日志条目**

```markdown
### 会话侧边栏归档与删除动作
```

- [ ] **Step 3: 运行聚焦验证**

Run:
- `npm test`
- `mvn -Dtest=BusinessWorkflowApiTest test`

Expected:
- Web 回归测试通过。
- API 会话工作流测试通过，并覆盖新的删除路径。
