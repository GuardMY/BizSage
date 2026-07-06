import { Archive, Eye, FileText, LockKeyhole, Search, Send } from "lucide-react";
import Markdown from "../../lib/markdown";
import type {
  Conversation,
  ConversationMessage,
  Diagnosis,
  DiagnosisReport,
  PaidIntelligence,
  Source,
  WorkspaceMessages
} from "./workspace-types";

type DiagnosisWorkspaceProps = {
  busy: boolean;
  diagnosis: Diagnosis | null;
  message: string;
  messageHistory: ConversationMessage[];
  onArchiveConversation: () => void;
  onGenerateReport: () => void;
  onMessageChange: (value: string) => void;
  onOpenSource: (source: Source) => void;
  onSubmitDiagnosis: () => void;
  paidRows: PaidIntelligence[];
  report: DiagnosisReport | null;
  reportBusy: boolean;
  selectedConversation: Conversation | null;
  t: WorkspaceMessages;
};

export function DiagnosisWorkspace({
  busy,
  diagnosis,
  message,
  messageHistory,
  onArchiveConversation,
  onGenerateReport,
  onMessageChange,
  onOpenSource,
  onSubmitDiagnosis,
  paidRows,
  report,
  reportBusy,
  selectedConversation,
  t
}: DiagnosisWorkspaceProps) {
  return (
    <div className="workspacePanelGrid">
      <section className="dialogue">
        <div className="sectionHead">
          <div className="sectionCopy">
            <h2>{selectedConversation?.title ?? t.diagnosisConversation}</h2>
            <p>{selectedConversation ? t.conversationContext : t.selectConversation}</p>
          </div>
          {selectedConversation && (
            <button className="ghost" onClick={onArchiveConversation} type="button">
              <Archive size={16} />
              {t.archiveCurrent}
            </button>
          )}
        </div>

        <div className="messages">
          {selectedConversation == null && messageHistory.length === 0 && !busy && !diagnosis && (
            <div className="guidePrompt">
              <Search size={20} />
              <strong>{t.selectConversation}</strong>
              <p>{t.evidenceLine}</p>
            </div>
          )}

          {messageHistory.map((messageItem) => (
            <MessageBubble
              key={messageItem.id}
              messageItem={messageItem}
              onOpenSource={onOpenSource}
              t={t}
            />
          ))}

          {busy && (
            <div className="bubble agent muted">
              <span className="loadingDots"><span /><span /><span /></span>
              {t.busyDiagnosis}
            </div>
          )}

          {diagnosis && (
            <div
              className={`bubble agent${
                diagnosis.selfCheckStatus && diagnosis.selfCheckStatus !== "PASSED"
                  ? ` status-${diagnosis.selfCheckStatus.toLowerCase()}`
                  : ""
              }`}
            >
              <Markdown content={diagnosis.answer} />
              <div className="meta">
                <span>{diagnosis.confidence}</span>
                {diagnosis.selfCheckStatus && diagnosis.selfCheckStatus !== "PASSED" && (
                  <span
                    className={
                      diagnosis.selfCheckStatus === "NEEDS_REVIEW"
                        ? "flag needsReview"
                        : diagnosis.selfCheckStatus === "INSUFFICIENT_EVIDENCE"
                          ? "flag insufficientEvidence"
                          : ""
                    }
                  >
                    {labelForStatus(diagnosis.selfCheckStatus, t)}
                  </span>
                )}
                <span>{diagnosis.timeliness}</span>
              </div>
              {diagnosis.sources.length > 0 && (
                <div className="sourceLine">
                  {diagnosis.sources.map((source) => (
                    <button key={source.id} onClick={() => onOpenSource(source)} type="button">
                      <Eye size={14} />
                      {source.title}
                    </button>
                  ))}
                </div>
              )}
              <small>{diagnosis.disclaimer}</small>
            </div>
          )}
        </div>

        <div className="composer">
          <Search size={18} />
          <input value={message} onChange={(event) => onMessageChange(event.target.value)} />
          <button className="primary icon" onClick={onSubmitDiagnosis} disabled={busy} title={t.sendDiagnosis} type="button">
            <Send size={18} />
          </button>
        </div>
      </section>

      <aside className="workspaceAside">
        <section className="workspaceCard">
          <div className="sectionHead compact">
            <h2><FileText size={16} /> {t.reportTitle}</h2>
          </div>
          <div className="table">
            {report ? (
              <div className="row">
                <strong>{report.format} {t.reportMetadata}</strong>
                <span>{report.selfCheckStatus}</span>
                <small>{report.summary}</small>
              </div>
            ) : diagnosis ? (
              <div className="row">
                <div className="reportGenerateArea">
                  <small>{t.reportEmptyDetail}</small>
                  <button className="primary" onClick={onGenerateReport} disabled={reportBusy} type="button">
                    <FileText size={16} />
                    {reportBusy ? t.generatingReport : t.generateReport}
                  </button>
                </div>
              </div>
            ) : (
              <EmptyCard title={t.reportEmptyTitle} detail={t.reportEmptyDetail} />
            )}
          </div>
        </section>

        <section className="workspaceCard">
          <div className="sectionHead compact">
            <h2><LockKeyhole size={16} /> {t.paidTitle}</h2>
          </div>
          <div className="table">
            {paidRows.length === 0 && (
              <EmptyCard title={t.paidEmptyTitle} detail={t.paidEmptyDetail} />
            )}
            {paidRows.map((row) => (
              <div className="row" key={row.id}>
                <strong>{row.title}</strong>
                <span>{row.status}</span>
                <small>{row.entitlement} / {row.regionId} / {row.industryId}</small>
              </div>
            ))}
          </div>
        </section>
      </aside>
    </div>
  );
}

function MessageBubble({
  messageItem,
  onOpenSource,
  t
}: {
  messageItem: ConversationMessage;
  onOpenSource: (source: Source) => void;
  t: WorkspaceMessages;
}) {
  const sources = readSources(messageItem.sourcesJson);

  return (
    <div className={`bubble ${messageItem.sender === "USER" ? "user" : "agent"}`}>
      {messageItem.sender === "ASSISTANT" ? <Markdown content={messageItem.content} /> : <p>{messageItem.content}</p>}
      {(messageItem.confidence || messageItem.timeliness || messageItem.selfCheckStatus) && (
        <div className="meta">
          {messageItem.confidence && <span>{messageItem.confidence}</span>}
          {messageItem.selfCheckStatus && messageItem.selfCheckStatus !== "PASSED" && (
            <span
              className={
                messageItem.selfCheckStatus === "NEEDS_REVIEW"
                  ? "flag needsReview"
                  : messageItem.selfCheckStatus === "INSUFFICIENT_EVIDENCE"
                    ? "flag insufficientEvidence"
                    : ""
              }
            >
              {labelForStatus(messageItem.selfCheckStatus, t)}
            </span>
          )}
          {messageItem.timeliness && <span>{messageItem.timeliness}</span>}
        </div>
      )}
      {sources.length > 0 && (
        <div className="sourceLine">
          {sources.map((source) => (
            <button key={source.id} onClick={() => onOpenSource(source)} type="button">
              <Eye size={14} />
              {source.title}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function EmptyCard({ detail, title }: { detail: string; title: string }) {
  return (
    <div className="emptyRow">
      <FileText size={28} />
      <strong>{title}</strong>
      <small>{detail}</small>
    </div>
  );
}

function readSources(rawSources: string | null) {
  if (!rawSources) {
    return [];
  }

  try {
    const parsed = JSON.parse(rawSources) as Source[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function labelForStatus(status: string, t: WorkspaceMessages) {
  if (status === "NEEDS_REVIEW") return t.needsReview;
  if (status === "INSUFFICIENT_EVIDENCE") return t.insufficientEvidence;
  return status;
}
