"use client";

import { DatabaseZap, Play, Plus } from "lucide-react";
import type {
  AdminCollectionDeadLetter,
  AdminCollectionJobRun,
  AdminCollectionKeyword,
  AdminCollectionKeywordInput,
  AdminCollectionSource,
  AdminCollectionSourceDetail,
  AdminCollectionSourceInput
} from "../../lib/api-client";
import { adminCodeLabel, adminCodeWithRaw, adminText, type AdminLocale } from "./admin-i18n";

type Props = {
  busy: boolean;
  locale: AdminLocale;
  sources: AdminCollectionSource[];
  selectedSourceId: number | null;
  detail: AdminCollectionSourceDetail | null;
  sourceDraft: AdminCollectionSourceInput;
  keywordDraft: AdminCollectionKeywordInput;
  jobs: AdminCollectionJobRun[];
  deadLetters: AdminCollectionDeadLetter[];
  onSelectSource: (sourceConfigId: number) => void;
  onStartNew: () => void;
  onSourceDraftChange: (value: AdminCollectionSourceInput) => void;
  onKeywordDraftChange: (value: AdminCollectionKeywordInput) => void;
  onSaveSource: () => void;
  onSaveKeyword: () => void;
  onRunSource: (sourceConfigId: number) => void;
};

export function CollectionWorkspace({
  busy,
  locale,
  sources,
  selectedSourceId,
  detail,
  sourceDraft,
  keywordDraft,
  jobs,
  deadLetters,
  onSelectSource,
  onStartNew,
  onSourceDraftChange,
  onKeywordDraftChange,
  onSaveSource,
  onSaveKeyword,
  onRunSource
}: Props) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);

  return (
    <section className="adminCollectionShell">
      <aside className="workspaceCard adminPanel collectionSourceListPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>{t("采集源", "Sources")}</h2>
            <p>{sources.length} {t("个已配置采集器", "configured collectors")}</p>
          </div>
          <button className="primary" onClick={onStartNew} type="button">
            <Plus size={16} />
            {t("新建", "New")}
          </button>
        </div>
        <div className="knowledgeNodeList">
          {sources.map((source) => (
            <button key={source.sourceConfigId} className={`knowledgeNodeItem ${selectedSourceId === source.sourceConfigId ? "active" : ""}`} onClick={() => onSelectSource(source.sourceConfigId)} type="button">
              <strong>{source.name}</strong>
              <div className="knowledgeNodeMeta">
                <span className={`adminBadge status-${source.status.toLowerCase()}`}>{adminCodeLabel(locale, source.status)}</span>
                <small>{adminCodeWithRaw(locale, source.sourceType)} / {source.intervalMinutes}m / {adminCodeLabel(locale, source.circuitState)}</small>
              </div>
            </button>
          ))}
        </div>
      </aside>

      <section className="workspaceCard adminPanel collectionEditorPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>{t("采集源配置", "Source config")}</h2>
            <p>{t("面向现有采集服务的真实调度源定义。", "Real scheduler-backed source definitions for the existing collector service.")}</p>
          </div>
          <div className="adminRowActions">
            {selectedSourceId != null && (
              <button onClick={() => onRunSource(selectedSourceId)} disabled={busy} type="button" title={t("立即运行采集源", "Run source now")}>
                <Play size={14} />
                {t("运行", "Run")}
              </button>
            )}
            <button className="primary" onClick={onSaveSource} disabled={busy} type="button">
              <DatabaseZap size={16} />
              {t("保存采集源", "Save source")}
            </button>
          </div>
        </div>
        <div className="adminFormGrid collectionFormGrid">
          <label>{t("名称", "Name")}<input value={sourceDraft.name} onChange={(event) => onSourceDraftChange({ ...sourceDraft, name: event.target.value })} /></label>
          <label>{t("类型", "Type")}<input value={sourceDraft.sourceType} onChange={(event) => onSourceDraftChange({ ...sourceDraft, sourceType: event.target.value })} /></label>
          <label>{t("状态", "Status")}<input value={sourceDraft.status} onChange={(event) => onSourceDraftChange({ ...sourceDraft, status: event.target.value })} /></label>
          <label>{t("间隔(分钟)", "Interval (m)")}<input value={sourceDraft.intervalMinutes} onChange={(event) => onSourceDraftChange({ ...sourceDraft, intervalMinutes: Number(event.target.value) })} type="number" min="1" /></label>
          <label>{t("最大重试", "Max retries")}<input value={sourceDraft.maxRetries} onChange={(event) => onSourceDraftChange({ ...sourceDraft, maxRetries: Number(event.target.value) })} type="number" min="1" /></label>
          <label>{t("失败阈值", "Failure threshold")}<input value={sourceDraft.failureThreshold} onChange={(event) => onSourceDraftChange({ ...sourceDraft, failureThreshold: Number(event.target.value) })} type="number" min="1" /></label>
          <label>{t("冷却(分钟)", "Cooldown (m)")}<input value={sourceDraft.cooldownMinutes} onChange={(event) => onSourceDraftChange({ ...sourceDraft, cooldownMinutes: Number(event.target.value) })} type="number" min="1" /></label>
          <label>{t("区域", "Region")}<input value={sourceDraft.regionId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, regionId: event.target.value })} /></label>
          <label>{t("行业", "Industry")}<input value={sourceDraft.industryId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, industryId: event.target.value })} /></label>
          <label>{t("链路节点", "Chain node")}<input value={sourceDraft.linkId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, linkId: event.target.value })} /></label>
          <label>{t("来源 ID", "Source ID")}<input value={sourceDraft.sourceId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, sourceId: event.target.value })} /></label>
          <label className="wide">{t("Payload JSON", "Payload JSON")}<textarea value={sourceDraft.payloadJson} onChange={(event) => onSourceDraftChange({ ...sourceDraft, payloadJson: event.target.value })} rows={10} /></label>
        </div>
        <div className="adminGridTwo collectionBottomGrid">
          <section className="nestedPanel">
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>{t("关键词", "Keywords")}</h2>
                <p>{t("采集治理后的包含/排除过滤。", "Include/exclude filters after collector governance.")}</p>
              </div>
              <button className="primary" onClick={onSaveKeyword} disabled={busy || !keywordDraft.sourceConfigId} type="button">{t("保存关键词", "Save keyword")}</button>
            </div>
            <div className="adminFormGrid">
              <label>{t("关键词", "Keyword")}<input value={keywordDraft.keyword} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, keyword: event.target.value })} /></label>
              <label>{t("模式", "Mode")}<input value={keywordDraft.matchMode} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, matchMode: event.target.value })} /></label>
              <label>{t("状态", "Status")}<input value={keywordDraft.status} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, status: event.target.value })} /></label>
              <label className="wide">{t("备注", "Notes")}<input value={keywordDraft.notes} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, notes: event.target.value })} /></label>
            </div>
            <div className="table">
              {(detail?.keywords ?? []).map((keyword: AdminCollectionKeyword) => (
                <div className="row" key={keyword.keywordId}>
                  <strong>{keyword.keyword}</strong>
                  <span>{adminCodeLabel(locale, keyword.matchMode)}</span>
                  <small>{adminCodeLabel(locale, keyword.status)} / {keyword.notes ?? "-"}</small>
                </div>
              ))}
            </div>
          </section>
          <section className="nestedPanel">
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>{t("近期运行", "Recent runs")}</h2>
                <p>{t("手动和调度执行共用同一持久化路径。", "Manual and scheduled executions share the same persistence path.")}</p>
              </div>
            </div>
            <div className="table">
              {(detail?.recentRuns ?? []).map((run) => (
                <div className="row" key={run.runId}>
                  <strong>{adminCodeLabel(locale, run.triggerType)} / {adminCodeLabel(locale, run.status)}</strong>
                  <span>{run.recordsPersisted} {t("保留", "kept")}</span>
                  <small>{adminCodeWithRaw(locale, run.sourceType)} / {t("重试", "retry")} {run.retryCount} / {run.errorMessage ?? t("无错误", "no error")}</small>
                </div>
              ))}
            </div>
          </section>
        </div>
      </section>

      <aside className="workspaceCard adminPanel collectionOpsPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>{t("运行态", "Operations")}</h2>
            <p>{t("真实执行中的队列运行和死信记录。", "Queue runs and dead letters from real execution.")}</p>
          </div>
        </div>
        <div className="collectionOpsStack">
          <section>
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>{t("任务运行", "Job runs")}</h2>
                <p>{jobs.length} {t("条近期记录", "recent rows")}</p>
              </div>
            </div>
            <div className="table">
              {jobs.slice(0, 8).map((job) => (
                <div className="row" key={job.runId}>
                  <strong>{job.sourceName}</strong>
                  <span>{adminCodeLabel(locale, job.status)}</span>
                  <small>{adminCodeLabel(locale, job.triggerType)} / {t("采集", "collected")} {job.recordsCollected} / {t("入库", "persisted")} {job.recordsPersisted}</small>
                </div>
              ))}
            </div>
          </section>
          <section>
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>{t("死信队列", "Dead letters")}</h2>
                <p>{deadLetters.length} {t("条失败记录", "failed rows")}</p>
              </div>
            </div>
            <div className="table">
              {deadLetters.slice(0, 8).map((letter) => (
                <div className="row" key={letter.deadLetterId}>
                  <strong>{letter.sourceName ?? t("未知来源", "Unknown source")}</strong>
                  <span>{adminCodeLabel(locale, letter.reason)}</span>
                  <small>{letter.errorMessage}</small>
                </div>
              ))}
            </div>
          </section>
        </div>
      </aside>
    </section>
  );
}
