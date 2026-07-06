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
