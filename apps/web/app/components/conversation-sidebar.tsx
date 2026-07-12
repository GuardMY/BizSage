import { Archive, FilePlus2, MessageSquareMore, Trash2 } from "lucide-react";
import { describeConversationSidebar, partitionArchivedConversations } from "../../lib/conversation-workspace";
import type { Conversation, WorkspaceMessages, WorkspaceSection } from "./workspace-types";

type ConversationSidebarProps = {
  activeSection: WorkspaceSection;
  conversations: Conversation[];
  onArchiveConversation: (id: number) => void;
  onDeleteConversation: (id: number) => void;
  onNewConversation: () => void;
  onSelectConversation: (id: number) => void;
  selectedConversationId: number | null;
  t: WorkspaceMessages;
};

export function ConversationSidebar({
  activeSection,
  conversations,
  onArchiveConversation,
  onDeleteConversation,
  onNewConversation,
  onSelectConversation,
  selectedConversationId,
  t
}: ConversationSidebarProps) {
  const sidebar = describeConversationSidebar(activeSection);
  const title = t[sidebar.titleKey];
  const showArchiveAction = sidebar.actionKey === "archiveConversation";
  const showDeleteAction = sidebar.actionKey === "deleteConversation";
  const archivedGroups = partitionArchivedConversations(conversations);
  const conversationGroups = activeSection === "archive"
    ? [
        { label: t.diagnosisConversation, items: archivedGroups.diagnosis },
        { label: t.learningConversation, items: archivedGroups.learning }
      ]
    : [{ label: null, items: conversations }];

  return (
    <section className="conversationSidebar" aria-label={title}>
      <div className="conversationSidebarHead">
        <div>
          <h2>{title}</h2>
        </div>
        {activeSection !== "archive" && (
          <button className="ghost sidebarAction" onClick={onNewConversation}>
            <FilePlus2 size={16} />
            {t.newConversation}
          </button>
        )}
      </div>

      <div className="conversationList">
        {conversations.length === 0 && (
          <div className="emptySidebarState">
            <MessageSquareMore size={18} />
            <strong>{t.noConversations}</strong>
            <small>
              {activeSection === "archive" ? t.noArchivedMessages : t.selectConversation}
            </small>
          </div>
        )}

        {conversationGroups.map((group) => group.items.length > 0 && (
          <div key={group.label ?? "conversations"} className="conversationGroup">
            {group.label && <h3 className="conversationGroupTitle">{group.label}</h3>}
            {group.items.map((conversation) => (
          <div
            key={conversation.id}
            className={`conversationItem ${conversation.id === selectedConversationId ? "active" : ""}`}
          >
            <button
              className="conversationItemSelect"
              onClick={() => onSelectConversation(conversation.id)}
              type="button"
            >
              <span className="conversationItemBody">
                <strong>{conversation.title}</strong>
                <small>{conversation.regionId} / {conversation.industryId}</small>
              </span>
            </button>
            <span className="conversationItemActions">
              {showArchiveAction && (
                <button
                  className="conversationRowAction"
                  onClick={(event) => {
                    event.stopPropagation();
                    onArchiveConversation(conversation.id);
                  }}
                  type="button"
                  aria-label={t.archiveConversation}
                >
                  <Archive size={14} />
                </button>
              )}
              {showDeleteAction && (
                <button
                  className="conversationRowAction danger"
                  onClick={(event) => {
                    event.stopPropagation();
                    onDeleteConversation(conversation.id);
                  }}
                  type="button"
                  aria-label={t.deleteConversation}
                >
                  <Trash2 size={14} />
                </button>
              )}
            </span>
          </div>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}
