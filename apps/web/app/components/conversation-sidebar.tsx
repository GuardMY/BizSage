import { FilePlus2, MessageSquareMore } from "lucide-react";
import type { Conversation, WorkspaceMessages, WorkspaceSection } from "./workspace-types";

type ConversationSidebarProps = {
  activeSection: WorkspaceSection;
  conversations: Conversation[];
  onNewConversation: () => void;
  onSelectConversation: (id: number) => void;
  selectedConversationId: number | null;
  t: WorkspaceMessages;
};

export function ConversationSidebar({
  activeSection,
  conversations,
  onNewConversation,
  onSelectConversation,
  selectedConversationId,
  t
}: ConversationSidebarProps) {
  const title = activeSection === "archive" ? t.archivedConversations : t.activeConversations;

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
            <strong>{conversation.title}</strong>
            <small>{conversation.regionId} / {conversation.industryId}</small>
          </button>
        ))}
      </div>
    </section>
  );
}
