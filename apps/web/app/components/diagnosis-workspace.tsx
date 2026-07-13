import type { ReactNode } from "react";
import { Archive, Eye, FileText, Flag, Focus, LayoutList, LockKeyhole, RefreshCcw, Search, Send } from "lucide-react";
import { useEffect, useRef } from "react";
import Markdown from "../../lib/markdown";
import { industryLabel } from "../../lib/scope-labels";
import type {
  Conversation,
  ConversationMessage,
  Diagnosis,
  DiagnosisReport,
  AdditionalInformationQuestion,
  RecommendationItem,
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
  onRefreshRecommendations: () => void;
  onUseAdditionalQuestion: (item: AdditionalInformationQuestion) => void;
  onSubmitDiagnosis: () => void;
  recommendationRows: RecommendationItem[];
  report: DiagnosisReport | null;
  reportBusy: boolean;
  selectedConversation: Conversation | null;
  streamingDiagnosis: Diagnosis | null;
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
  onRefreshRecommendations,
  onUseAdditionalQuestion,
  onSubmitDiagnosis,
  recommendationRows,
  report,
  reportBusy,
  selectedConversation,
  streamingDiagnosis,
  t
}: DiagnosisWorkspaceProps) {
  const hasAssistantReply = messageHistory.some((messageItem) => messageItem.sender === "ASSISTANT");
  const displayedDiagnosis = streamingDiagnosis ?? (!hasAssistantReply ? diagnosis : null);
  const diagnosisMetadata = streamingDiagnosis ?? diagnosis;
  const messagesRef = useRef<HTMLDivElement | null>(null);
  const additionalQuestions = diagnosisMetadata?.additionalInformationQuestions
    ?? readAdditionalQuestions(selectedConversation?.recommendedQuestionIds);
  const completeness = diagnosisMetadata?.diagnosisCompleteness ?? selectedConversation?.profileCompleteness ?? 0;
  const threshold = diagnosisMetadata?.diagnosisCompletenessThreshold ?? 80;

  useEffect(() => {
    if (!messagesRef.current) return;
    messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
  }, [busy, displayedDiagnosis, messageHistory]);

  return (
    <div className="workspacePanelGrid conversationWorkspace">
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

        <div className="messages" ref={messagesRef}>
          {!hasAssistantReply && !displayedDiagnosis && (
            <div className="bubble agent introBubble">
              <div className="introBadge"><Focus size={16} /> Diagnosis Agent</div>
              <Markdown content={renderDiagnosisIntro(t)} />
              <div className="draftPlan">
                {buildDraftPlan().map((item) => (
                  <div className="draftPlanItem" key={item}>
                    <LayoutList size={14} />
                    <span>{item}</span>
                  </div>
                ))}
              </div>
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

          {busy && !displayedDiagnosis && (
            <div className="bubble agent muted">
              <span className="loadingDots"><span /><span /><span /></span>
              {t.busyDiagnosis}
            </div>
          )}

          {displayedDiagnosis && (
            <div
              className={`bubble agent${
                displayedDiagnosis.selfCheckStatus && displayedDiagnosis.selfCheckStatus !== "PASSED"
                  ? ` status-${displayedDiagnosis.selfCheckStatus.toLowerCase()}`
                  : ""
              }${streamingDiagnosis ? " streaming" : ""}`}
            >
              <Markdown content={displayedDiagnosis.answer} />
              <div className="meta">
                <span>{displayedDiagnosis.confidence}</span>
                {displayedDiagnosis.selfCheckStatus && displayedDiagnosis.selfCheckStatus !== "PASSED" && (
                  <span
                    className={
                      displayedDiagnosis.selfCheckStatus === "NEEDS_REVIEW"
                        ? "flag needsReview"
                        : displayedDiagnosis.selfCheckStatus === "INSUFFICIENT_EVIDENCE"
                          ? "flag insufficientEvidence"
                          : ""
                    }
                  >
                    {labelForStatus(displayedDiagnosis.selfCheckStatus, t)}
                  </span>
                )}
                <span>{displayedDiagnosis.timeliness}</span>
              </div>
              {displayedDiagnosis.sources.length > 0 && (
                <div className="sourceLine">
                  {displayedDiagnosis.sources.map((source) => (
                    <button key={source.id} onClick={() => onOpenSource(source)} type="button">
                      <Eye size={14} />
                      {source.title}
                    </button>
                  ))}
                </div>
              )}
              <small>{displayedDiagnosis.disclaimer}</small>
            </div>
          )}
        </div>

        <div className="composer">
          <Search size={18} />
          <textarea
            value={message}
            onChange={(event) => onMessageChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key !== "Enter" || event.shiftKey) return;
              event.preventDefault();
              onSubmitDiagnosis();
            }}
          />
          <button className="primary icon" onClick={onSubmitDiagnosis} disabled={busy} title={t.sendDiagnosis} type="button">
            <Send size={18} />
          </button>
        </div>
      </section>

      <aside className="workspaceAside">
        <section className="workspaceCard railGroup">
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
            ) : displayedDiagnosis || hasAssistantReply ? (
              <div className="row">
                <div className="reportGenerateArea">
                  <small>{t.reportEmptyDetail}</small>
                  <button className={`primary reportButton ${completeness >= threshold ? "reportReady" : "reportIncomplete"}`} onClick={onGenerateReport} disabled={reportBusy} type="button">
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

        <section className="workspaceCard railGroup">
          <div className="sectionHead compact">
            <h2><LockKeyhole size={16} /> 可继续补充的信息</h2>
          </div>
          <div className="diagnosisProgress">
            <div className="progressLabel"><span>诊断信息完整度</span><strong>{Math.round(completeness)}%</strong></div>
            <div className="progressTrack"><div className="progressValue" style={{ width: `${Math.max(0, Math.min(100, completeness))}%` }} /></div>
            <small>报告阈值 {threshold}%</small>
          </div>
          <div className="table">
            {completeness >= threshold && additionalQuestions.length > 0 ? additionalQuestions.map((item) => (
              <div className="row" key={String(item.id)}>
                <strong>{item.questionText}</strong>
                {item.purpose && <small>{item.purpose}</small>}
                <button className="ghost" onClick={() => onUseAdditionalQuestion(item)} type="button">
                  <Search size={14} />
                  让 Agent 提问
                </button>
              </div>
            )) : (
              <EmptyCard title={completeness >= threshold ? "暂无补充问题" : "信息尚未达到展示条件"} detail="达到完整度阈值后，Agent 会提供可继续补充的信息。" />
            )}
          </div>
        </section>
      </aside>
    </div>
  );
}

function RailSection({
  title,
  icon,
  items,
  onUseRecommendation,
  t
}: {
  title: string;
  icon: ReactNode;
  items: RecommendationItem[];
  onUseRecommendation: (item: RecommendationItem) => void;
  t: WorkspaceMessages;
}) {
  return (
    <section className="railSection">
      <div className="sectionHead compact">
        <h3>{icon} {title}</h3>
      </div>
      <div className="table">
        {items.length === 0 ? (
          <EmptyCard title={t.recommendationEmpty} detail={t.recommendationMissing} />
        ) : items.map((row) => (
          <div className="row" key={row.id}>
            <strong>{row.questionText}</strong>
            {row.industryId && <span>{industryLabel(row.industryId)}</span>}
            <span>{row.category}</span>
            <small>{row.sourceType} / {row.score.toFixed(2)} / {row.usageCount}</small>
            <button className="ghost" onClick={() => onUseRecommendation(row)} type="button">
              <Search size={14} />
              {t.recommendationContinue}
            </button>
          </div>
        ))}
      </div>
    </section>
  );
}

function buildDiagnosisRail(items: RecommendationItem[]) {
  const businessIssue = items.filter((item) => matchesAny(item, ["issue", "问题", "risk", "冲突", "痛点"])).slice(0, 3);
  const missingProfile = items.filter((item) => matchesAny(item, ["profile", "画像", "缺失", "字段", "attribute"])).slice(0, 3);
  const highImpactDetail = items.filter((item) => !businessIssue.includes(item) && !missingProfile.includes(item)).slice(0, 3);
  return { businessIssue, missingProfile, highImpactDetail };
}

function matchesAny(item: RecommendationItem, needles: string[]) {
  const haystack = `${item.questionText} ${item.category} ${item.sourceType}`.toLowerCase();
  return needles.some((needle) => haystack.includes(needle.toLowerCase()));
}

function renderDiagnosisIntro(t: WorkspaceMessages) {
  return [
    "我是 BizSage 的 Diagnosis Agent。",
    "我会先建立经营画像，再推进分层诊断。",
    "我会通过结构化提问补齐关键经营信息。",
    "右侧 rail 会展示可能的经营问题、缺失画像字段和高影响细节。"
  ].join("\n\n");
}

function buildDraftPlan() {
  return [
    "所在行业",
    "业务是否线上/线下/实体经营",
    "大致投资规模",
    "门店 / 仓库 / 团队规模",
    "基础营收、成本、利润、流量和渠道情况"
  ];
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

function readAdditionalQuestions(rawQuestions: string | null | undefined) {
  if (!rawQuestions) return [];
  try {
    const parsed = JSON.parse(rawQuestions) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is NonNullable<Diagnosis["additionalInformationQuestions"]>[number] => {
      if (!item || typeof item !== "object") return false;
      const question = item as Record<string, unknown>;
      return (typeof question.id === "string" || typeof question.id === "number")
        && typeof question.questionText === "string";
    });
  } catch {
    return [];
  }
}

function labelForStatus(status: string, t: WorkspaceMessages) {
  if (status === "NEEDS_REVIEW") return t.needsReview;
  if (status === "INSUFFICIENT_EVIDENCE") return t.insufficientEvidence;
  return status;
}
