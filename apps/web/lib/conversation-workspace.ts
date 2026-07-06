// @ts-nocheck
/** @typedef {import("./api-client").Conversation} Conversation */

export type WorkspaceSection = "diagnosis" | "intelligence" | "users" | "archive";
export type ConversationSidebarDescriptor = {
  titleKey: "diagnosisConversationList" | "archiveConversationList";
  actionKey: "archiveConversation" | "deleteConversation";
};

/**
 * @param {Conversation[]} conversations
 */
export function sortConversationsNewestFirst(conversations) {
  return [...conversations].sort((left, right) => right.id - left.id);
}

/**
 * @param {Conversation[]} conversations
 */
export function partitionConversations(conversations) {
  const ordered = sortConversationsNewestFirst(conversations);
  const visible = ordered.filter((conversation) => conversation.status !== "DELETED");

  return {
    active: visible.filter((conversation) => conversation.status !== "ARCHIVED"),
    archived: visible.filter((conversation) => conversation.status === "ARCHIVED")
  };
}

export function describeConversationSidebar(section: WorkspaceSection): ConversationSidebarDescriptor {
  if (section === "archive") {
    return {
      titleKey: "archiveConversationList",
      actionKey: "deleteConversation"
    };
  }

  return {
    titleKey: "diagnosisConversationList",
    actionKey: "archiveConversation"
  };
}

/**
 * @param {{ section: WorkspaceSection; conversations: Conversation[]; selectedConversationId: number | null }} input
 */
export function resolveWorkspaceSelection(input) {
  const { active, archived } = partitionConversations(input.conversations);
  const visibleConversations = input.section === "archive" ? archived : active;
  const selectedConversationId = visibleConversations.some(
    (conversation) => conversation.id === input.selectedConversationId
  )
    ? input.selectedConversationId
    : (visibleConversations[0]?.id ?? null);

  return {
    visibleConversations,
    selectedConversationId
  };
}

/**
 * @param {{ selectedConversationId: number | null; conversations: Conversation[] }} input
 */
export function nextSelectionAfterArchive(input) {
  const { active } = partitionConversations(input.conversations);
  if (input.selectedConversationId == null) {
    return active[0]?.id ?? null;
  }

  if (active.some((conversation) => conversation.id === input.selectedConversationId)) {
    return input.selectedConversationId;
  }

  return active[0]?.id ?? null;
}
