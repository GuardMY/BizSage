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

type Props = {
  busy: boolean;
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
  return (
    <section className="adminCollectionShell">
      <aside className="workspaceCard adminPanel collectionSourceListPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>Sources</h2>
            <p>{sources.length} configured collectors</p>
          </div>
          <button className="primary" onClick={onStartNew} type="button">
            <Plus size={16} />
            New
          </button>
        </div>
        <div className="knowledgeNodeList">
          {sources.map((source) => (
            <button key={source.sourceConfigId} className={`knowledgeNodeItem ${selectedSourceId === source.sourceConfigId ? "active" : ""}`} onClick={() => onSelectSource(source.sourceConfigId)} type="button">
              <strong>{source.name}</strong>
              <div className="knowledgeNodeMeta">
                <span className={`adminBadge status-${source.status.toLowerCase()}`}>{source.status}</span>
                <small>{source.sourceType} / {source.intervalMinutes}m / {source.circuitState}</small>
              </div>
            </button>
          ))}
        </div>
      </aside>

      <section className="workspaceCard adminPanel collectionEditorPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>Source config</h2>
            <p>Real scheduler-backed source definitions for the existing collector service.</p>
          </div>
          <div className="adminRowActions">
            {selectedSourceId != null && (
              <button onClick={() => onRunSource(selectedSourceId)} disabled={busy} type="button" title="Run source now">
                <Play size={14} />
                Run
              </button>
            )}
            <button className="primary" onClick={onSaveSource} disabled={busy} type="button">
              <DatabaseZap size={16} />
              Save source
            </button>
          </div>
        </div>
        <div className="adminFormGrid collectionFormGrid">
          <label>Name<input value={sourceDraft.name} onChange={(event) => onSourceDraftChange({ ...sourceDraft, name: event.target.value })} /></label>
          <label>Type<input value={sourceDraft.sourceType} onChange={(event) => onSourceDraftChange({ ...sourceDraft, sourceType: event.target.value })} /></label>
          <label>Status<input value={sourceDraft.status} onChange={(event) => onSourceDraftChange({ ...sourceDraft, status: event.target.value })} /></label>
          <label>Interval (m)<input value={sourceDraft.intervalMinutes} onChange={(event) => onSourceDraftChange({ ...sourceDraft, intervalMinutes: Number(event.target.value) })} type="number" min="1" /></label>
          <label>Max retries<input value={sourceDraft.maxRetries} onChange={(event) => onSourceDraftChange({ ...sourceDraft, maxRetries: Number(event.target.value) })} type="number" min="1" /></label>
          <label>Failure threshold<input value={sourceDraft.failureThreshold} onChange={(event) => onSourceDraftChange({ ...sourceDraft, failureThreshold: Number(event.target.value) })} type="number" min="1" /></label>
          <label>Cooldown (m)<input value={sourceDraft.cooldownMinutes} onChange={(event) => onSourceDraftChange({ ...sourceDraft, cooldownMinutes: Number(event.target.value) })} type="number" min="1" /></label>
          <label>Region<input value={sourceDraft.regionId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, regionId: event.target.value })} /></label>
          <label>Industry<input value={sourceDraft.industryId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, industryId: event.target.value })} /></label>
          <label>Chain node<input value={sourceDraft.linkId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, linkId: event.target.value })} /></label>
          <label>Source ID<input value={sourceDraft.sourceId} onChange={(event) => onSourceDraftChange({ ...sourceDraft, sourceId: event.target.value })} /></label>
          <label className="wide">Payload JSON<textarea value={sourceDraft.payloadJson} onChange={(event) => onSourceDraftChange({ ...sourceDraft, payloadJson: event.target.value })} rows={10} /></label>
        </div>
        <div className="adminGridTwo collectionBottomGrid">
          <section className="nestedPanel">
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>Keywords</h2>
                <p>Include/exclude filters after collector governance.</p>
              </div>
              <button className="primary" onClick={onSaveKeyword} disabled={busy || !keywordDraft.sourceConfigId} type="button">Save keyword</button>
            </div>
            <div className="adminFormGrid">
              <label>Keyword<input value={keywordDraft.keyword} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, keyword: event.target.value })} /></label>
              <label>Mode<input value={keywordDraft.matchMode} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, matchMode: event.target.value })} /></label>
              <label>Status<input value={keywordDraft.status} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, status: event.target.value })} /></label>
              <label className="wide">Notes<input value={keywordDraft.notes} onChange={(event) => onKeywordDraftChange({ ...keywordDraft, notes: event.target.value })} /></label>
            </div>
            <div className="table">
              {(detail?.keywords ?? []).map((keyword: AdminCollectionKeyword) => (
                <div className="row" key={keyword.keywordId}>
                  <strong>{keyword.keyword}</strong>
                  <span>{keyword.matchMode}</span>
                  <small>{keyword.status} / {keyword.notes ?? "-"}</small>
                </div>
              ))}
            </div>
          </section>
          <section className="nestedPanel">
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>Recent runs</h2>
                <p>Manual and scheduled executions share the same persistence path.</p>
              </div>
            </div>
            <div className="table">
              {(detail?.recentRuns ?? []).map((run) => (
                <div className="row" key={run.runId}>
                  <strong>{run.triggerType} / {run.status}</strong>
                  <span>{run.recordsPersisted} kept</span>
                  <small>{run.sourceType} / retry {run.retryCount} / {run.errorMessage ?? "no error"}</small>
                </div>
              ))}
            </div>
          </section>
        </div>
      </section>

      <aside className="workspaceCard adminPanel collectionOpsPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>Operations</h2>
            <p>Queue runs and dead letters from real execution.</p>
          </div>
        </div>
        <div className="collectionOpsStack">
          <section>
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>Job runs</h2>
                <p>{jobs.length} recent rows</p>
              </div>
            </div>
            <div className="table">
              {jobs.slice(0, 8).map((job) => (
                <div className="row" key={job.runId}>
                  <strong>{job.sourceName}</strong>
                  <span>{job.status}</span>
                  <small>{job.triggerType} / collected {job.recordsCollected} / persisted {job.recordsPersisted}</small>
                </div>
              ))}
            </div>
          </section>
          <section>
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>Dead letters</h2>
                <p>{deadLetters.length} failed rows</p>
              </div>
            </div>
            <div className="table">
              {deadLetters.slice(0, 8).map((letter) => (
                <div className="row" key={letter.deadLetterId}>
                  <strong>{letter.sourceName ?? "Unknown source"}</strong>
                  <span>{letter.reason}</span>
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

