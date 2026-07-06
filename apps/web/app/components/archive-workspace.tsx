import { Archive, FileText } from "lucide-react";
import Markdown from "../../lib/markdown";
import type { Conversation, ConversationMessage, WorkspaceMessages } from "./workspace-types";

type ArchiveWorkspaceProps = {
  messageHistory: ConversationMessage[];
  selectedConversation: Conversation | null;
  t: WorkspaceMessages;
};

export function ArchiveWorkspace({ messageHistory, selectedConversation, t }: ArchiveWorkspaceProps) {
  return (
    <div className="workspacePanelGrid archivePanelGrid">
      <section className="dialogue">
        <div className="sectionHead">
          <div className="sectionCopy">
            <h2>{selectedConversation?.title ?? t.navArchive}</h2>
            <p>{t.archiveReadonly}</p>
          </div>
          <div className="contextBadge">
            <Archive size={16} />
            {t.navArchive}
          </div>
        </div>

        <div className="messages">
          {selectedConversation == null && (
            <div className="guidePrompt">
              <Archive size={20} />
              <strong>{t.archivedConversations}</strong>
              <p>{t.noArchivedMessages}</p>
            </div>
          )}

          {selectedConversation != null && messageHistory.length === 0 && (
            <div className="guidePrompt">
              <FileText size={20} />
              <strong>{t.reportEmptyTitle}</strong>
              <p>{t.noArchivedMessages}</p>
            </div>
          )}

          {messageHistory.map((messageItem) => (
            <div key={messageItem.id} className={`bubble ${messageItem.sender === "USER" ? "user" : "agent"}`}>
              {messageItem.sender === "ASSISTANT" ? <Markdown content={messageItem.content} /> : <p>{messageItem.content}</p>}
              {(messageItem.confidence || messageItem.timeliness || messageItem.selfCheckStatus) && (
                <div className="meta">
                  {messageItem.confidence && <span>{messageItem.confidence}</span>}
                  {messageItem.selfCheckStatus && <span>{messageItem.selfCheckStatus}</span>}
                  {messageItem.timeliness && <span>{messageItem.timeliness}</span>}
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      <aside className="workspaceAside">
        <section className="workspaceCard readonlyCard">
          <div className="sectionHead compact">
            <h2><Archive size={16} /> {t.navArchive}</h2>
          </div>
          <div className="readonlyNote">
            <strong>{t.archiveReadonly}</strong>
            <small>{selectedConversation?.title ?? t.selectConversation}</small>
          </div>
        </section>
      </aside>
    </div>
  );
}
