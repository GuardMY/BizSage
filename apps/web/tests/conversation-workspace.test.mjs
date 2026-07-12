import test from "node:test";
import assert from "node:assert/strict";

const workspaceModule = "../lib/conversation-workspace.ts";

test("partitions conversations into newest-first active and archived lists", async () => {
  const { partitionConversations } = await import(workspaceModule);
  const conversations = [
    { id: 2, title: "Older active", status: "ACTIVE", regionId: "cn", industryId: "retail" },
    { id: 5, title: "Newest archived", status: "ARCHIVED", regionId: "cn", industryId: "retail" },
    { id: 9, title: "Newest active", status: "ACTIVE", regionId: "cn", industryId: "retail" },
    { id: 1, title: "Older archived", status: "ARCHIVED", regionId: "cn", industryId: "retail" }
  ];

  const result = partitionConversations(conversations);

  assert.deepEqual(
    result.active.map((item) => item.id),
    [9, 2]
  );
  assert.deepEqual(
    result.archived.map((item) => item.id),
    [5, 1]
  );
});

test("ignores deleted conversations in workspace partitions", async () => {
  const { partitionConversations } = await import(workspaceModule);
  const conversations = [
    { id: 7, title: "Deleted", status: "DELETED", regionId: "cn", industryId: "retail" },
    { id: 5, title: "Archived", status: "ARCHIVED", regionId: "cn", industryId: "retail" },
    { id: 3, title: "Active", status: "ACTIVE", regionId: "cn", industryId: "retail" }
  ];

  const result = partitionConversations(conversations);

  assert.deepEqual(result.active.map((item) => item.id), [3]);
  assert.deepEqual(result.archived.map((item) => item.id), [5]);
});

test("separates archived diagnosis and learning conversations", async () => {
  const { partitionArchivedConversations } = await import(workspaceModule);
  const conversations = [
    { id: 9, title: "Archived learning", status: "ARCHIVED", agentMode: "LEARNING", regionId: "cn", industryId: "retail" },
    { id: 7, title: "Archived diagnosis", status: "ARCHIVED", agentMode: "DIAGNOSIS", regionId: "cn", industryId: "retail" },
    { id: 5, title: "Legacy diagnosis", status: "ARCHIVED", regionId: "cn", industryId: "retail" }
  ];

  const result = partitionArchivedConversations(conversations);

  assert.deepEqual(result.diagnosis.map((item) => item.id), [7, 5]);
  assert.deepEqual(result.learning.map((item) => item.id), [9]);
});

test("resolves visible conversations and keeps active selection inside non-archive sections", async () => {
  const { resolveWorkspaceSelection } = await import(workspaceModule);
  const conversations = [
    { id: 2, title: "Active", status: "ACTIVE", regionId: "cn", industryId: "retail" },
    { id: 8, title: "Archived", status: "ARCHIVED", regionId: "cn", industryId: "retail" }
  ];

  const result = resolveWorkspaceSelection({
    section: "diagnosis",
    selectedConversationId: 8,
    conversations
  });

  assert.deepEqual(
    result.visibleConversations.map((item) => item.id),
    [2]
  );
  assert.equal(result.selectedConversationId, 2);
});

test("switching into archive falls back to the newest archived conversation", async () => {
  const { resolveWorkspaceSelection } = await import(workspaceModule);
  const conversations = [
    { id: 3, title: "Active", status: "ACTIVE", regionId: "cn", industryId: "retail" },
    { id: 6, title: "Archived latest", status: "ARCHIVED", regionId: "cn", industryId: "retail" },
    { id: 4, title: "Archived earlier", status: "ARCHIVED", regionId: "cn", industryId: "retail" }
  ];

  const result = resolveWorkspaceSelection({
    section: "archive",
    selectedConversationId: 3,
    conversations
  });

  assert.deepEqual(
    result.visibleConversations.map((item) => item.id),
    [6, 4]
  );
  assert.equal(result.selectedConversationId, 6);
});

test("after archiving the selected conversation it picks the next newest active thread", async () => {
  const { nextSelectionAfterArchive } = await import(workspaceModule);
  const conversations = [
    { id: 11, title: "Selected", status: "ACTIVE", regionId: "cn", industryId: "retail" },
    { id: 9, title: "Next", status: "ACTIVE", regionId: "cn", industryId: "retail" },
    { id: 2, title: "Older", status: "ACTIVE", regionId: "cn", industryId: "retail" }
  ];

  const result = nextSelectionAfterArchive({
    selectedConversationId: 11,
    conversations: conversations.map((item) =>
      item.id === 11 ? { ...item, status: "ARCHIVED" } : item
    )
  });

  assert.equal(result, 9);
});

test("returns null selection when the target section has no visible conversations", async () => {
  const { resolveWorkspaceSelection } = await import(workspaceModule);
  const conversations = [
    { id: 1, title: "Only archived", status: "ARCHIVED", regionId: "cn", industryId: "retail" }
  ];

  const result = resolveWorkspaceSelection({
    section: "diagnosis",
    selectedConversationId: 1,
    conversations
  });

  assert.deepEqual(result.visibleConversations, []);
  assert.equal(result.selectedConversationId, null);
});

test("filters active conversations by diagnosis and learning mode", async () => {
  const { resolveWorkspaceSelection } = await import(workspaceModule);
  const conversations = [
    { id: 9, title: "Learning", status: "ACTIVE", agentMode: "LEARNING", regionId: "cn", industryId: "retail" },
    { id: 7, title: "Diagnosis", status: "ACTIVE", agentMode: "DIAGNOSIS", regionId: "cn", industryId: "retail" },
    { id: 5, title: "Legacy diagnosis", status: "ACTIVE", regionId: "cn", industryId: "retail" }
  ];

  const diagnosis = resolveWorkspaceSelection({ section: "diagnosis", selectedConversationId: null, conversations });
  const learning = resolveWorkspaceSelection({ section: "learning", selectedConversationId: null, conversations });

  assert.deepEqual(diagnosis.visibleConversations.map((item) => item.id), [7, 5]);
  assert.equal(diagnosis.selectedConversationId, 7);
  assert.deepEqual(learning.visibleConversations.map((item) => item.id), [9]);
  assert.equal(learning.selectedConversationId, 9);
});

test("returns diagnosis and archive sidebar labels and row actions by section", async () => {
  const { describeConversationSidebar } = await import(workspaceModule);

  assert.deepEqual(describeConversationSidebar("diagnosis"), {
    titleKey: "diagnosisConversationList",
    actionKey: "archiveConversation"
  });

  assert.deepEqual(describeConversationSidebar("archive"), {
    titleKey: "archiveConversationList",
    actionKey: "deleteConversation"
  });
});
