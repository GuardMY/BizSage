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
  mode?: ConversationModeSection;
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
    : null;

  return {
    visibleConversations,
    selectedConversationId
  };
}

export function partitionArchivedConversations(conversations: Conversation[]) {
  const { archived } = partitionConversations(conversations);
  return {
    diagnosis: archived.filter((conversation) => conversation.agentMode !== "LEARNING"),
    learning: archived.filter((conversation) => conversation.agentMode === "LEARNING")
  };
}

export function conversationModeForSection(section: WorkspaceSection): ConversationModeSection | null {
  if (section === "diagnosis") return "diagnosis";
  if (section === "learning") return "learning";
  return null;
}

export function nextSelectionAfterArchive(input: ArchiveSelectionInput): number | null {
  const { active } = partitionConversations(input.conversations);
  const visibleActive = input.mode === undefined
    ? active
    : active.filter((conversation) => input.mode === "learning"
      ? conversation.agentMode === "LEARNING"
      : conversation.agentMode !== "LEARNING");
  if (input.selectedConversationId == null) {
    return visibleActive[0]?.id ?? null;
  }

  if (visibleActive.some((conversation) => conversation.id === input.selectedConversationId)) {
    return input.selectedConversationId;
  }

  return visibleActive[0]?.id ?? null;
}
