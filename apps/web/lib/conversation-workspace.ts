import type { Conversation } from "./api-client";

export type WorkspaceSection = "diagnosis" | "learning" | "intelligence" | "users" | "archive";

export type ConversationSidebarDescriptor = {
  titleKey: "diagnosisConversationList" | "archiveConversationList";
  actionKey: "archiveConversation" | "deleteConversation";
};

export type WorkspaceSelectionInput = {
  section: WorkspaceSection;
  conversations: Conversation[];
  selectedConversationId: number | null;
};

export type WorkspaceSelectionResult = {
  visibleConversations: Conversation[];
  selectedConversationId: number | null;
};

export type ArchiveSelectionInput = {
  selectedConversationId: number | null;
  conversations: Conversation[];
};

export function sortConversationsNewestFirst(conversations: Conversation[]): Conversation[] {
  return [...conversations].sort((left, right) => right.id - left.id);
}

export function partitionConversations(conversations: Conversation[]) {
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

export function resolveWorkspaceSelection(input: WorkspaceSelectionInput): WorkspaceSelectionResult {
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

export function nextSelectionAfterArchive(input: ArchiveSelectionInput): number | null {
  const { active } = partitionConversations(input.conversations);
  if (input.selectedConversationId == null) {
    return active[0]?.id ?? null;
  }

  if (active.some((conversation) => conversation.id === input.selectedConversationId)) {
    return input.selectedConversationId;
  }

  return active[0]?.id ?? null;
}
