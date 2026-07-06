# Conversation List Archive/Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add direct archive/delete actions to the web conversation list while introducing API-backed conversation soft deletion.

**Architecture:** Keep conversation lifecycle state-driven through the existing `status` field. Extend the API with a soft-delete endpoint and filter deleted records from conversation listings, then wire the web sidebar to render section-specific titles and row-level actions against the existing workspace-selection helper.

**Tech Stack:** Next.js, React, TypeScript, Node test runner, Spring Boot, MockMvc, Maven

---

### Task 1: Lock the status behavior with failing tests

**Files:**
- Modify: `apps/web/tests/conversation-workspace.test.mjs`
- Modify: `services/api/src/test/java/com/bizsage/api/BusinessWorkflowApiTest.java`

- [ ] **Step 1: Write the failing web regression test**

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

- [ ] **Step 2: Run the web test to verify it fails**

Run: `npm test -- conversation-workspace.test.mjs`
Expected: FAIL because deleted conversations still enter the archived partition.

- [ ] **Step 3: Write the failing API integration test**

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

- [ ] **Step 4: Run the API test to verify it fails**

Run: `mvn -Dtest=BusinessWorkflowApiTest#archivedConversationCanBeSoftDeletedAndDisappearsFromList test`
Expected: FAIL because `/delete` does not exist yet and deleted filtering is absent.

### Task 2: Implement API soft deletion

**Files:**
- Modify: `services/api/src/main/java/com/bizsage/api/conversations/ConversationStore.java`
- Modify: `services/api/src/main/java/com/bizsage/api/conversations/ConversationController.java`

- [ ] **Step 1: Add minimal store support**

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

- [ ] **Step 2: Filter deleted rows from list results**

```java
where owner_username = ?
  and status <> 'DELETED'
```

- [ ] **Step 3: Expose the delete endpoint**

```java
@PostMapping("/{id}/delete")
ApiResponse<Conversation> delete(@PathVariable long id, Principal principal, HttpServletRequest request) {
  return ApiResponse.ok(conversationStore.softDelete(principal.getName(), id), requestId(request));
}
```

- [ ] **Step 4: Re-run the API test**

Run: `mvn -Dtest=BusinessWorkflowApiTest#archivedConversationCanBeSoftDeletedAndDisappearsFromList test`
Expected: PASS

### Task 3: Implement web sidebar actions and labels

**Files:**
- Modify: `apps/web/lib/conversation-workspace.ts`
- Modify: `apps/web/lib/api-client.ts`
- Modify: `apps/web/app/components/workspace-types.ts`
- Modify: `apps/web/app/components/conversation-sidebar.tsx`
- Modify: `apps/web/app/page.tsx`

- [ ] **Step 1: Exclude deleted conversations from workspace partitioning**

```typescript
if (conversation.status === "DELETED") {
  continue;
}
```

- [ ] **Step 2: Add API client delete helper**

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

- [ ] **Step 3: Extend workspace copy and sidebar props**

```typescript
diagnosisConversationList: string;
archiveConversationList: string;
archiveConversation: string;
deleteConversation: string;
```

- [ ] **Step 4: Render section-specific sidebar title and row actions**

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

- [ ] **Step 5: Wire page-level handlers**

```typescript
await archiveConversation(profile.token, conversationId);
await deleteConversation(profile.token, conversationId);
```

- [ ] **Step 6: Re-run the web test suite**

Run: `npm test`
Expected: PASS

### Task 4: Record the change

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `CHANGELOG-zh-CN.md`

- [ ] **Step 1: Add English changelog entry**

```markdown
### Conversation Sidebar Archive/Delete Actions
```

- [ ] **Step 2: Add matching Chinese changelog entry**

```markdown
### 会话侧边栏归档与删除动作
```

- [ ] **Step 3: Run focused verification**

Run:
- `npm test`
- `mvn -Dtest=BusinessWorkflowApiTest test`

Expected:
- Web regression suite passes.
- API conversation workflow tests pass with the new delete path.
