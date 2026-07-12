import type { Conversation } from "./api-client";

export type WorkspaceSection = "diagnosis" | "learning" | "users" | "archive";

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

export type ConversationModeSection = "diagnosis" | "learning";

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
  const mode = conversationModeForSection(input.section);
  const visibleConversations = input.section === "archive"
    ? archived
    : mode === null
      ? active
      : active.filter((conversation) => mode === "learning"
        ? conversation.agentMode === "LEARNING"
        : conversation.agentMode !== "LEARNING");
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

export function conversationModeForSection(section: WorkspaceSection): ConversationModeSection | null {
  if (section === "diagnosis") return "diagnosis";
  if (section === "learning") return "learning";
  return null;
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
