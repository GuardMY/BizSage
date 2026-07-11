import type { ReactNode } from "react";
import { BookOpen, ChevronRight, RefreshCcw, Search, Sparkles } from "lucide-react";
import { useEffect, useMemo, useRef } from "react";
import Markdown from "../../lib/markdown";
import type { Conversation, ConversationMessage, Diagnosis, RecommendationItem, WorkspaceMessages } from "./workspace-types";

type LearningWorkspaceProps = {
  busy: boolean;
  conversation: Conversation | null;
  diagnosis: Diagnosis | null;
  message: string;
  messageHistory: ConversationMessage[];
  onMessageChange: (value: string) => void;
  onRefreshRecommendations: () => void;
  onSubmitLearning: () => void;
  onUseRecommendation: (item: RecommendationItem) => void;
  recommendationRows: RecommendationItem[];
  streamingDiagnosis: Diagnosis | null;
  t: WorkspaceMessages;
};

export function LearningWorkspace({
  busy,
  conversation,
  diagnosis,
  message,
  messageHistory,
  onMessageChange,
  onRefreshRecommendations,
  onSubmitLearning,
  onUseRecommendation,
  recommendationRows,
  streamingDiagnosis,
  t
}: LearningWorkspaceProps) {
  const messagesRef = useRef<HTMLDivElement | null>(null);
  const hasAssistantReply = messageHistory.some((item) => item.sender === "ASSISTANT");
  const displayedDiagnosis = streamingDiagnosis ?? (!hasAssistantReply ? diagnosis : null);
  const recommendationItems = recommendationRows.length > 0 ? recommendationRows : displayedDiagnosis?.recommendationCandidates ?? [];
  const firstEntry = !hasAssistantReply && !displayedDiagnosis;
  const railSections = useMemo(() => buildLearningRail(recommendationItems), [recommendationItems]);

  useEffect(() => {
    if (!messagesRef.current) return;
    messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
  }, [busy, displayedDiagnosis, messageHistory]);

  return (
    <div className="workspacePanelGrid learningWorkspace">
      <section className="dialogue">
        <div className="sectionHead">
          <div className="sectionCopy">
            <h2>{conversation?.title ?? t.learningConversation}</h2>
            <p>{conversation ? t.conversationContext : t.selectConversation}</p>
          </div>
          <button className="ghost" onClick={onRefreshRecommendations} type="button">
            <RefreshCcw size={16} />
            {t.recommendationRefresh}
          </button>
        </div>

        <div className="messages" ref={messagesRef}>
          {firstEntry && (
            <div className="bubble agent introBubble">
              <div className="introBadge"><BookOpen size={16} /> Learning Agent</div>
              <Markdown content={renderLearningIntro()} />
            </div>
          )}

          {messageHistory.map((messageItem) => (
            <div className={`bubble ${messageItem.sender === "USER" ? "user" : "agent"}`} key={messageItem.id}>
              {messageItem.sender === "ASSISTANT" ? <Markdown content={messageItem.content} /> : <p>{messageItem.content}</p>}
            </div>
          ))}

          {busy && !displayedDiagnosis && (
            <div className="bubble agent muted">
              <span className="loadingDots"><span /><span /><span /></span>
              {t.busyLearning}
            </div>
          )}

          {displayedDiagnosis && (
            <div className="bubble agent">
              <Markdown content={displayedDiagnosis.answer} />
              <div className="meta">
                <span>{displayedDiagnosis.confidence}</span>
                <span>{displayedDiagnosis.timeliness}</span>
              </div>
            </div>
          )}
        </div>

        <div className="composer">
          <Search size={18} />
          <input value={message} onChange={(event) => onMessageChange(event.target.value)} />
          <button className="primary icon" onClick={onSubmitLearning} disabled={busy} title={t.sendLearning} type="button">
            <Sparkles size={18} />
          </button>
        </div>
      </section>

      <aside className="workspaceAside learningRail">
        <RailPanel title={t.learningNextNode} icon={<ChevronRight size={16} />} items={railSections.nextNode} onUseRecommendation={onUseRecommendation} t={t} />
        <RailPanel title={t.learningCurrentBlock} icon={<BookOpen size={16} />} items={railSections.currentBlock} onUseRecommendation={onUseRecommendation} t={t} />
        <RailPanel title={t.learningExtensionDirection} icon={<Sparkles size={16} />} items={railSections.extensionDirection} onUseRecommendation={onUseRecommendation} t={t} />
      </aside>
    </div>
  );
}

function RailPanel({
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
    <section className="workspaceCard railGroup">
      <div className="sectionHead compact">
        <h2>{icon} {title}</h2>
      </div>
      <div className="table">
        {items.length === 0 ? (
          <div className="emptyRow">
            <Search size={24} />
            <strong>{title}</strong>
            <small>{t.recommendationMissing}</small>
          </div>
        ) : (
          items.map((item) => (
            <div className="row" key={item.id}>
              <strong>{item.questionText}</strong>
              <span>{item.category}</span>
              <small>{item.sourceType} / {item.score.toFixed(2)}</small>
              <button className="ghost" onClick={() => onUseRecommendation(item)} type="button">
                <Search size={14} />
                {t.recommendationContinue}
              </button>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

function buildLearningRail(items: RecommendationItem[]) {
  const nextNode = items.filter((item) => matchesAny(item, ["node", "next", "下一", "节点"])).slice(0, 3);
  const currentBlock = items.filter((item) => matchesAny(item, ["block", "section", "block", "区块", "细分"])).slice(0, 3);
  const extensionDirection = items.filter((item) => !nextNode.includes(item) && !currentBlock.includes(item)).slice(0, 3);
  return { nextNode, currentBlock, extensionDirection };
}

function matchesAny(item: RecommendationItem, needles: string[]) {
  const haystack = `${item.questionText} ${item.category} ${item.sourceType}`.toLowerCase();
  return needles.some((needle) => haystack.includes(needle.toLowerCase()));
}

function renderLearningIntro() {
  return [
    "我是 BizSage 的 Learning Agent。",
    "我会帮你理解节点、规则、指标、风险和机会。",
    "我会优先根据当前上下文推荐下一步最值得学习的内容。",
    "右侧 rail 可以点击，也可以刷新。"
  ].join("\n\n");
}
