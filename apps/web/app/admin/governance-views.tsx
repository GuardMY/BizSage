"use client";

import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import {
  fetchAdminConflicts,
  fetchAdminFalseLedger,
  fetchAdminSnapshots,
  type ConflictResolutionRecord,
  type FalseLedgerItem,
  type SnapshotSummary
} from "../../lib/api-client";
import { adminCodeLabel, adminText, type AdminLocale } from "./admin-i18n";

export function ConflictsView({ locale }: { locale: AdminLocale }) {
  const [rows, setRows] = useState<ConflictResolutionRecord[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const t = (zh: string, en: string) => adminText(locale, zh, en);

  async function refresh() {
    setBusy(true);
    try {
      const result = await fetchAdminConflicts();
      setRows(result.items);
      setNotice(t(`已加载 ${result.total} 条冲突记录。`, `Loaded ${result.total} conflict records.`));
    } catch (e) {
      setNotice(e instanceof Error ? e.message : t("加载冲突记录失败。", "Failed to load conflicts."));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>{t("冲突处理", "Conflict Resolutions")}</h2>
          <p>{t("基于 SimHash 的冲突检测：新情报与已有记录匹配后按五类分支处理。", "SimHash-based conflict detection: incoming intelligence matched against existing records with 5-branch classification.")}</p>
        </div>
        <button className="ghost" onClick={refresh} disabled={busy} type="button">
          <RefreshCw size={14} /> {t("刷新", "Refresh")}
        </button>
      </div>
      {notice && <p style={{ padding: "0 16px", color: "var(--muted)", fontSize: 13 }}>{notice}</p>}
      <div className="adminTable">
        <div className="adminTableHead">
          <span>{t("分支", "Branch")}</span><span>{t("距离", "Distance")}</span><span>{t("动作", "Action")}</span><span>{t("权重", "Weights")}</span><span>{t("处理人", "Resolved By")}</span>
        </div>
        {rows.length === 0 && <div className="emptyRow">{t("暂无冲突记录。新情报采集时会自动检测冲突。", "No conflict records yet. Conflicts are detected automatically when new intelligence is collected.")}</div>}
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <StatusBadge value={row.conflictBranch} locale={locale} />
            <span>{row.simHashDistance}</span>
            <span>{adminCodeLabel(locale, row.routingAction)}</span>
            <small>{t("新", "in")}: {row.incomingWeight.toFixed(2)} / {t("旧", "ex")}: {row.existingWeight.toFixed(2)}</small>
            <small>{row.resolvedBy || "-"}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

export function FalseLedgerView({ locale }: { locale: AdminLocale }) {
  const [rows, setRows] = useState<FalseLedgerItem[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const t = (zh: string, en: string) => adminText(locale, zh, en);

  async function refresh() {
    setBusy(true);
    try {
      const result = await fetchAdminFalseLedger();
      setRows(result.items);
      setNotice(t(`已加载 ${result.total} 条台账记录。`, `Loaded ${result.total} ledger records.`));
    } catch (e) {
      setNotice(e instanceof Error ? e.message : t("加载虚假信息台账失败。", "Failed to load false ledger."));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>{t("虚假信息台账", "False Information Ledger")}</h2>
          <p>{t("被标记为虚假或误导的情报，由冲突引擎或谣言关键词检测路由到此处。", "Intelligence items flagged as false/misleading. Routed here by the conflict engine or rumor keyword detection.")}</p>
        </div>
        <button className="ghost" onClick={refresh} disabled={busy} type="button">
          <RefreshCw size={14} /> {t("刷新", "Refresh")}
        </button>
      </div>
      {notice && <p style={{ padding: "0 16px", color: "var(--muted)", fontSize: 13 }}>{notice}</p>}
      <div className="adminTable">
        <div className="adminTableHead">
          <span>{t("标题", "Title")}</span><span>{t("原因", "Reason")}</span><span>{t("谣言关键词", "Rumor Keyword")}</span><span>{t("归档人", "Archived By")}</span>
        </div>
        {rows.length === 0 && <div className="emptyRow">{t("暂无虚假信息记录。被标记的内容会自动出现在这里。", "No false information records. Flagged content appears here automatically.")}</div>}
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <strong>{row.title}</strong>
            <span>{adminCodeLabel(locale, row.conflictReason)}</span>
            <small>{row.matchedRumorKeyword || "-"}</small>
            <small>{row.archivedBy}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

export function SnapshotsView({ locale }: { locale: AdminLocale }) {
  const [rows, setRows] = useState<SnapshotSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [typeFilter, setTypeFilter] = useState("DAILY");
  const t = (zh: string, en: string) => adminText(locale, zh, en);

  async function refresh(filter: string = typeFilter) {
    setBusy(true);
    try {
      const result = await fetchAdminSnapshots(filter);
      setRows(result);
      setNotice(t(`已加载 ${result.length} 个快照。`, `${result.length} snapshots loaded.`));
    } catch (e) {
      setNotice(e instanceof Error ? e.message : t("加载快照失败。", "Failed to load snapshots."));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>{t("情报快照", "Intelligence Snapshots")}</h2>
          <p>{t("用于差异比较和审计追踪的时间序列快照。", "Time-series snapshots for diff comparison and audit trail.")}</p>
        </div>
        <div className="adminFilterBar">
          <select
            value={typeFilter}
            onChange={(e) => { setTypeFilter(e.target.value); refresh(e.target.value); }}
            className="adminFilterSelect"
          >
            <option value="DAILY">{t("每日", "Daily")}</option>
            <option value="WEEKLY">{t("每周", "Weekly")}</option>
            <option value="MONTHLY">{t("每月", "Monthly")}</option>
            <option value="MANUAL">{t("手动", "Manual")}</option>
          </select>
          <button className="ghost" onClick={() => refresh()} disabled={busy} type="button">
            <RefreshCw size={14} /> {t("刷新", "Refresh")}
          </button>
        </div>
      </div>
      {notice && <p style={{ padding: "0 16px", color: "var(--muted)", fontSize: 13 }}>{notice}</p>}
      <div className="adminTable">
        <div className="adminTableHead">
          <span>{t("类型", "Type")}</span><span>{t("范围", "Scope")}</span><span>{t("记录数", "Records")}</span><span>{t("保留期", "Retention")}</span><span>{t("过期", "Expires")}</span><span>{t("创建时间", "Created")}</span>
        </div>
        {rows.length === 0 && <div className="emptyRow">{t("未找到快照。调度器会自动创建快照。", "No snapshots found. Snapshots are created automatically by the scheduler.")}</div>}
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <StatusBadge value={row.snapshotType} locale={locale} />
            <span>{row.scopeKey}</span>
            <span>{row.recordCount}</span>
            <small>{row.retentionDays}d</small>
            <small>{row.expiresAt ? formatTime(row.expiresAt) : "-"}</small>
            <small>{formatTime(row.createTime)}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

function StatusBadge({ value, locale }: { value: string; locale: AdminLocale }) {
  return <span className={`adminBadge status-${value.toLowerCase().replaceAll("_", "-")}`}>{adminCodeLabel(locale, value)}</span>;
}

function formatTime(value: string) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}
