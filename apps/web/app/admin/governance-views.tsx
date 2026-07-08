"use client";

import { useEffect, useState } from "react";
import { RefreshCw, type LucideIcon } from "lucide-react";
import {
  fetchAdminConflicts,
  fetchAdminFalseLedger,
  type ConflictResolutionRecord,
  type FalseLedgerItem
} from "../../lib/api-client";

// ── Conflicts View ────────────────────────────────────────────

export function ConflictsView() {
  const [rows, setRows] = useState<ConflictResolutionRecord[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  async function refresh() {
    setBusy(true);
    try {
      const result = await fetchAdminConflicts();
      setRows(result.items);
      setNotice(`Loaded ${result.total} conflict records.`);
    } catch (e) {
      setNotice(e instanceof Error ? e.message : "Failed to load conflicts.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>Conflict Resolutions</h2>
          <p>SimHash-based conflict detection: incoming intelligence matched against existing records with 5-branch classification.</p>
        </div>
        <button className="ghost" onClick={refresh} disabled={busy} type="button">
          <RefreshCw size={14} /> Refresh
        </button>
      </div>
      {notice && <p style={{ padding: "0 16px", color: "var(--muted)", fontSize: 13 }}>{notice}</p>}
      <div className="adminTable">
        <div className="adminTableHead">
          <span>Branch</span><span>Distance</span><span>Action</span><span>Weights</span><span>Resolved By</span>
        </div>
        {rows.length === 0 && <div className="emptyRow">No conflict records yet. Conflicts are detected automatically when new intelligence is collected.</div>}
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <StatusBadge value={row.conflictBranch} />
            <span>{row.simHashDistance}</span>
            <span>{row.routingAction}</span>
            <small>in: {row.incomingWeight.toFixed(2)} / ex: {row.existingWeight.toFixed(2)}</small>
            <small>{row.resolvedBy || "—"}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

// ── False Ledger View ─────────────────────────────────────────

export function FalseLedgerView() {
  const [rows, setRows] = useState<FalseLedgerItem[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  async function refresh() {
    setBusy(true);
    try {
      const result = await fetchAdminFalseLedger();
      setRows(result.items);
      setNotice(`Loaded ${result.total} ledger records.`);
    } catch (e) {
      setNotice(e instanceof Error ? e.message : "Failed to load false ledger.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>False Information Ledger</h2>
          <p>Intelligence items flagged as false/misleading. Routed here by the conflict engine or rumor keyword detection.</p>
        </div>
        <button className="ghost" onClick={refresh} disabled={busy} type="button">
          <RefreshCw size={14} /> Refresh
        </button>
      </div>
      {notice && <p style={{ padding: "0 16px", color: "var(--muted)", fontSize: 13 }}>{notice}</p>}
      <div className="adminTable">
        <div className="adminTableHead">
          <span>Title</span><span>Reason</span><span>Rumor Keyword</span><span>Archived By</span>
        </div>
        {rows.length === 0 && <div className="emptyRow">No false information records. Flagged content appears here automatically.</div>}
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <strong>{row.title}</strong>
            <span>{row.conflictReason}</span>
            <small>{row.matchedRumorKeyword || "—"}</small>
            <small>{row.archivedBy}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

// ── Snapshots View ────────────────────────────────────────────

import {
  fetchAdminSnapshots,
  type SnapshotSummary
} from "../../lib/api-client";

export function SnapshotsView() {
  const [rows, setRows] = useState<SnapshotSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [typeFilter, setTypeFilter] = useState("DAILY");

  async function refresh(filter: string = typeFilter) {
    setBusy(true);
    try {
      const result = await fetchAdminSnapshots(filter);
      setRows(result);
      setNotice(`${result.length} snapshots loaded.`);
    } catch (e) {
      setNotice(e instanceof Error ? e.message : "Failed to load snapshots.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>Intelligence Snapshots</h2>
          <p>Time-series snapshots for diff comparison and audit trail.</p>
        </div>
        <div className="adminFilterBar">
          <select
            value={typeFilter}
            onChange={(e) => { setTypeFilter(e.target.value); refresh(e.target.value); }}
            className="adminFilterSelect"
          >
            <option value="DAILY">Daily</option>
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
            <option value="MANUAL">Manual</option>
          </select>
          <button className="ghost" onClick={() => refresh()} disabled={busy} type="button">
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>
      {notice && <p style={{ padding: "0 16px", color: "var(--muted)", fontSize: 13 }}>{notice}</p>}
      <div className="adminTable">
        <div className="adminTableHead">
          <span>Type</span><span>Scope</span><span>Records</span><span>Retention</span><span>Expires</span><span>Created</span>
        </div>
        {rows.length === 0 && <div className="emptyRow">No snapshots found. Snapshots are created automatically by the scheduler.</div>}
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <StatusBadge value={row.snapshotType} />
            <span>{row.scopeKey}</span>
            <span>{row.recordCount}</span>
            <small>{row.retentionDays}d</small>
            <small>{row.expiresAt ? formatTime(row.expiresAt) : "—"}</small>
            <small>{formatTime(row.createTime)}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

// ── Shared helpers ────────────────────────────────────────────

function StatusBadge({ value }: { value: string }) {
  return <span className={`adminBadge status-${value.toLowerCase().replaceAll("_", "-")}`}>{value}</span>;
}

function formatTime(value: string) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}
