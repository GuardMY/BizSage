import { Archive, FilePlus2, MessageSquareMore, Trash2 } from "lucide-react";
import { describeConversationSidebar } from "../../lib/conversation-workspace";
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

  return (
    <section className="conversationSidebar" aria-label={title}>
      <div className="conversationSidebarHead">
        <div>
          <p className="conversationSidebarEyebrow">{t.selectedConversation}</p>
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

        {conversations.map((conversation) => (
          <button
            key={conversation.id}
            className={`conversationItem ${conversation.id === selectedConversationId ? "active" : ""}`}
            onClick={() => onSelectConversation(conversation.id)}
            type="button"
          >
            <span className="conversationItemBody">
              <strong>{conversation.title}</strong>
              <small>{conversation.regionId} / {conversation.industryId}</small>
            </span>
            <span className="conversationItemActions">
              {showArchiveAction && (
                <span
                  className="ghost conversationRowAction"
                  onClick={(event) => {
                    event.stopPropagation();
                    onArchiveConversation(conversation.id);
                  }}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      event.stopPropagation();
                      onArchiveConversation(conversation.id);
                    }
                  }}
                  aria-label={t.archiveConversation}
                >
                  <Archive size={14} />
                </span>
              )}
              {showDeleteAction && (
                <span
                  className="ghost conversationRowAction danger"
                  onClick={(event) => {
                    event.stopPropagation();
                    onDeleteConversation(conversation.id);
                  }}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      event.stopPropagation();
                      onDeleteConversation(conversation.id);
                    }
                  }}
                  aria-label={t.deleteConversation}
                >
                  <Trash2 size={14} />
                </span>
              )}
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}
