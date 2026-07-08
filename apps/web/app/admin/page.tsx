"use client";

import {
  AlertTriangle,
  ClipboardCheck,
  FileClock,
  LayoutDashboard,
  LogIn,
  LogOut,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCheck
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  createAdminHumanIntelligence,
  decideAdminReview,
  fetchAdminAlerts,
  fetchAdminAuditLogs,
  fetchAdminDashboard,
  fetchAdminHumanIntelligence,
  fetchAdminIntelligenceReviews,
  fetchAdminTickets,
  login,
  reviewAdminHumanIntelligence,
  transitionAdminTicket,
  updateAdminAlert,
  type AdminAlert,
  type AdminAuditLog,
  type AdminDashboard,
  type AdminHumanIntelligence,
  type AdminIntelligenceReview,
  type AdminTicket,
  type LoginProfile
} from "../../lib/api-client";

type AdminSection = "dashboard" | "alerts" | "audit" | "reviews" | "tickets" | "human";
type HumanDraft = {
  city: string;
  industryId: string;
  linkId: string;
  content: string;
  sourceType: string;
  collector: string;
  eventTime: string;
  confidence: number;
  entitlement: string;
  regionId: string;
  sourceId: string;
};

const PROFILE_STORAGE_KEY = "bizsage.web.profile";

const sections: Array<{ id: AdminSection; label: string; icon: typeof LayoutDashboard }> = [
  { id: "dashboard", label: "Control", icon: LayoutDashboard },
  { id: "alerts", label: "Alerts", icon: AlertTriangle },
  { id: "audit", label: "Audit", icon: FileClock },
  { id: "reviews", label: "Reviews", icon: ClipboardCheck },
  { id: "tickets", label: "Tickets", icon: ShieldCheck },
  { id: "human", label: "Human Intel", icon: UserCheck }
];

export default function AdminPage() {
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [username, setUsername] = useState("operator");
  const [password, setPassword] = useState("password");
  const [activeSection, setActiveSection] = useState<AdminSection>("dashboard");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("Sign in with an operator or administrator account.");
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [alerts, setAlerts] = useState<AdminAlert[]>([]);
  const [auditLogs, setAuditLogs] = useState<AdminAuditLog[]>([]);
  const [reviews, setReviews] = useState<AdminIntelligenceReview[]>([]);
  const [tickets, setTickets] = useState<AdminTicket[]>([]);
  const [humanRows, setHumanRows] = useState<AdminHumanIntelligence[]>([]);
  const [auditQuery, setAuditQuery] = useState("");
  const [draftHuman, setDraftHuman] = useState({
    city: "Shanghai",
    industryId: "general",
    linkId: "sales-payment",
    content: "Local operators report supplier prepayment pressure.",
    sourceType: "local_visit",
    collector: "operator",
    eventTime: "2026-07",
    confidence: 0.72,
    entitlement: "PAID",
    regionId: "cn-default",
    sourceId: "manual-admin"
  });

  const isAdmin = profile?.role === "SUPER_ADMIN" || profile?.role === "OPERATOR";

  useEffect(() => {
    try {
      const rawProfile = window.localStorage.getItem(PROFILE_STORAGE_KEY);
      if (!rawProfile) return;
      setProfile(JSON.parse(rawProfile) as LoginProfile);
    } catch {
      window.localStorage.removeItem(PROFILE_STORAGE_KEY);
    }
  }, []);

  useEffect(() => {
    if (!profile || !isAdmin) return;
    void refreshAll();
  }, [profile, isAdmin]);

  const activeTitle = useMemo(
    () => sections.find((section) => section.id === activeSection)?.label ?? "Admin",
    [activeSection]
  );

  async function handleLogin() {
    setBusy(true);
    try {
      const nextProfile = await login(username, password);
      setProfile(nextProfile);
      window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(nextProfile));
      setNotice(nextProfile.role === "USER" ? "This account cannot access admin operations." : "Admin API connected.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Sign in failed.");
    } finally {
      setBusy(false);
    }
  }

  function handleLogout() {
    window.localStorage.removeItem(PROFILE_STORAGE_KEY);
    setProfile(null);
    setDashboard(null);
    setAlerts([]);
    setAuditLogs([]);
    setReviews([]);
    setTickets([]);
    setHumanRows([]);
    setNotice("Signed out.");
  }

  async function refreshAll() {
    if (!profile) return;
    setBusy(true);
    try {
      const [nextDashboard, nextAlerts, nextAudit, nextReviews, nextTickets, nextHuman] = await Promise.all([
        fetchAdminDashboard(profile.token),
        fetchAdminAlerts(profile.token),
        fetchAdminAuditLogs(profile.token, auditQuery),
        fetchAdminIntelligenceReviews(profile.token),
        fetchAdminTickets(profile.token),
        fetchAdminHumanIntelligence(profile.token)
      ]);
      setDashboard(nextDashboard);
      setAlerts(nextAlerts.items);
      setAuditLogs(nextAudit.items);
      setReviews(nextReviews.items);
      setTickets(nextTickets.items);
      setHumanRows(nextHuman.items);
      setNotice("Admin data refreshed from services/api.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Admin refresh failed.");
    } finally {
      setBusy(false);
    }
  }

  async function runAction(action: () => Promise<unknown>, success: string) {
    if (!profile) return;
    setBusy(true);
    try {
      await action();
      setNotice(success);
      await refreshAll();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Admin action failed.");
    } finally {
      setBusy(false);
    }
  }

  if (!profile) {
    return (
      <main className="loginScreen">
        <section className="loginCard">
          <div className="brand loginBrand">
            <span className="mark">BS</span>
            <div>
              <strong>BizSage Admin</strong>
              <small>V3 operations console</small>
            </div>
          </div>
          <div className="loginCopy">
            <h1>Admin sign in</h1>
            <p>Use an operator or super administrator account to maintain intelligence, alerts, tickets, audit logs, and human intelligence.</p>
          </div>
          <div className="loginForm">
            <label>Username<input value={username} onChange={(event) => setUsername(event.target.value)} /></label>
            <label>Password<input value={password} onChange={(event) => setPassword(event.target.value)} type="password" /></label>
            <button className="primary" onClick={handleLogin} disabled={busy} type="button">
              <LogIn size={16} />
              {busy ? "Signing in..." : "Sign in"}
            </button>
            <small>{notice}</small>
          </div>
        </section>
      </main>
    );
  }

  if (!isAdmin) {
    return (
      <main className="loginScreen">
        <section className="loginCard">
          <div className="loginCopy">
            <h1>Permission required</h1>
            <p>{profile.username} is signed in as {profile.role}. Admin V3 requires OPERATOR or SUPER_ADMIN.</p>
          </div>
          <button className="ghost" onClick={handleLogout} type="button">
            <LogOut size={16} />
            Sign out
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="workspace adminWorkspace">
      <aside className="rail adminRail">
        <div className="brand">
          <span className="mark">BS</span>
          <div>
            <strong>BizSage Admin</strong>
            <small>V3-1 / V3-2</small>
          </div>
        </div>
        <nav className="nav">
          {sections.map((section) => {
            const Icon = section.icon;
            return (
              <button
                key={section.id}
                className={`navItem ${activeSection === section.id ? "active" : ""}`}
                onClick={() => setActiveSection(section.id)}
                title={section.label}
                type="button"
              >
                <Icon size={18} />
                {section.label}
              </button>
            );
          })}
        </nav>
        <section className="adminContext">
          <strong>{profile.username}</strong>
          <small>{profile.role} / {profile.regionId} / {profile.industryId}</small>
          <small>{profile.membershipLevel}</small>
        </section>
      </aside>

      <section className="workspaceBody adminBody">
        <header className="topbar">
          <div>
            <h1>{activeTitle}</h1>
            <p>{notice}</p>
          </div>
          <div className="topActions">
            <button className="languageButton" onClick={refreshAll} disabled={busy} type="button">
              <RefreshCw size={16} />
              Refresh
            </button>
            <button className="ghost" onClick={handleLogout} type="button">
              <LogOut size={16} />
              Sign out
            </button>
          </div>
        </header>

        <div className="workspaceScroll adminScroll">
          {activeSection === "dashboard" && <DashboardView dashboard={dashboard} />}
          {activeSection === "alerts" && (
            <AlertsView
              rows={alerts}
              busy={busy}
              onAction={(id, action) => runAction(() => updateAdminAlert(profile.token, id, action), `Alert ${action} complete.`)}
            />
          )}
          {activeSection === "audit" && (
            <AuditView
              rows={auditLogs}
              query={auditQuery}
              setQuery={setAuditQuery}
              onSearch={() => runAction(async () => {
                const next = await fetchAdminAuditLogs(profile.token, auditQuery);
                setAuditLogs(next.items);
              }, "Audit logs filtered.")}
            />
          )}
          {activeSection === "reviews" && (
            <ReviewsView
              rows={reviews}
              busy={busy}
              onVerdict={(id, verdict) => runAction(() => decideAdminReview(profile.token, id, verdict, `Admin selected ${verdict}`), `Review ${verdict} complete.`)}
            />
          )}
          {activeSection === "tickets" && (
            <TicketsView
              rows={tickets}
              busy={busy}
              onTransition={(id, status) => runAction(() => transitionAdminTicket(profile.token, id, status, `Move ticket to ${status}`), "Ticket transition complete.")}
            />
          )}
          {activeSection === "human" && (
            <HumanView
              rows={humanRows}
              busy={busy}
              draft={draftHuman}
              setDraft={setDraftHuman}
              onCreate={() => runAction(() => createAdminHumanIntelligence(profile.token, draftHuman), "Human intelligence submitted.")}
              onReview={(id, verdict) => runAction(() => reviewAdminHumanIntelligence(profile.token, id, verdict, `Admin selected ${verdict}`), "Human intelligence review complete.")}
            />
          )}
        </div>
      </section>
    </main>
  );
}

function DashboardView({ dashboard }: { dashboard: AdminDashboard | null }) {
  return (
    <div className="adminStack">
      <section className="adminMetricGrid">
        {(dashboard?.metrics ?? []).map((metric) => (
          <article className={`adminMetric status-${metric.status}`} key={metric.key}>
            <small>{metric.label}</small>
            <strong>{metric.value}</strong>
            <span>{metric.detail}</span>
          </article>
        ))}
      </section>
      <section className="adminGridTwo">
        <ListPanel title="Urgent alerts" rows={dashboard?.urgentAlerts ?? []} render={(row) => <CompactRow title={row.message} badge={row.level} detail={`${row.component} / ${row.status}`} />} />
        <ListPanel title="Open tickets" rows={dashboard?.openTickets ?? []} render={(row) => <CompactRow title={row.title} badge={row.severity} detail={`${row.ticketType} / ${row.status}`} />} />
        <ListPanel title="Pending reviews" rows={dashboard?.pendingReviews ?? []} render={(row) => <CompactRow title={row.title} badge={row.reviewStatus} detail={`${row.regionId} / ${row.industryId}`} />} />
        <ListPanel title="Recent audit logs" rows={dashboard?.recentAuditLogs ?? []} render={(row) => <CompactRow title={row.action} badge={row.result} detail={`${row.actor} / ${row.targetType}:${row.targetId}`} />} />
      </section>
    </div>
  );
}

function AlertsView({ rows, busy, onAction }: { rows: AdminAlert[]; busy: boolean; onAction: (id: number, action: "acknowledge" | "claim" | "close") => void }) {
  return (
    <section className="workspaceCard adminPanel">
      <PanelHead title="Alert center" detail="Acknowledge, claim, or close production alerts. Every action writes an audit log." />
      <div className="adminTable">
        <div className="adminTableHead"><span>Level</span><span>Component</span><span>Message</span><span>Status</span><span>Actions</span></div>
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <StatusBadge value={row.level} />
            <strong>{row.component}</strong>
            <span>{row.message}</span>
            <StatusBadge value={row.status} />
            <div className="adminRowActions">
              <button title="Acknowledge alert" onClick={() => onAction(row.id, "acknowledge")} disabled={busy} type="button">Ack</button>
              <button title="Claim alert" onClick={() => onAction(row.id, "claim")} disabled={busy} type="button">Claim</button>
              <button title="Close alert" onClick={() => onAction(row.id, "close")} disabled={busy} type="button">Close</button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function AuditView({ rows, query, setQuery, onSearch }: { rows: AdminAuditLog[]; query: string; setQuery: (value: string) => void; onSearch: () => void }) {
  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>Audit logs</h2>
          <p>Search by actor, action, target type, or target id.</p>
        </div>
        <div className="adminSearch">
          <Search size={16} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search audit logs" />
          <button className="primary" onClick={onSearch} type="button">Search</button>
        </div>
      </div>
      <div className="adminTable">
        <div className="adminTableHead"><span>Actor</span><span>Action</span><span>Target</span><span>Result</span><span>Time</span></div>
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <strong>{row.actor}</strong>
            <span>{row.action}</span>
            <span>{row.targetType}:{row.targetId}</span>
            <StatusBadge value={row.result} />
            <small>{formatTime(row.createTime)}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

function ReviewsView({ rows, busy, onVerdict }: { rows: AdminIntelligenceReview[]; busy: boolean; onVerdict: (id: number, verdict: "PASS" | "REJECT" | "FLAG") => void }) {
  return (
    <section className="workspaceCard adminPanel">
      <PanelHead title="Intelligence review" detail="Resolve pending intelligence into pass, reject, or escalation ticket states." />
      <div className="adminReviewList">
        {rows.map((row) => (
          <article className="adminReviewItem" key={row.id}>
            <div>
              <h3>{row.title}</h3>
              <p>{row.content}</p>
              <small>{row.sourceId} / {row.regionId} / {row.industryId} / confidence {row.confidence}</small>
            </div>
            <div className="adminDecision">
              <StatusBadge value={row.reviewStatus} />
              <button onClick={() => onVerdict(row.id, "PASS")} disabled={busy || row.reviewStatus === "COMPLETED"} type="button">Pass</button>
              <button onClick={() => onVerdict(row.id, "REJECT")} disabled={busy || row.reviewStatus === "COMPLETED"} type="button">Reject</button>
              <button onClick={() => onVerdict(row.id, "FLAG")} disabled={busy || row.reviewStatus === "COMPLETED"} type="button">Flag</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function TicketsView({ rows, busy, onTransition }: { rows: AdminTicket[]; busy: boolean; onTransition: (id: number, status: string) => void }) {
  return (
    <section className="workspaceCard adminPanel">
      <PanelHead title="Ticket ledger" detail="Move operational tickets through claimed, in-progress, review, and closed states." />
      <div className="adminTable">
        <div className="adminTableHead"><span>Severity</span><span>Title</span><span>Owner</span><span>Status</span><span>Actions</span></div>
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <StatusBadge value={row.severity} />
            <span>{row.title}</span>
            <span>{row.owner ?? "Unassigned"}</span>
            <StatusBadge value={row.status} />
            <div className="adminRowActions">
              <button onClick={() => onTransition(row.id, "IN_PROGRESS")} disabled={busy} type="button">Start</button>
              <button onClick={() => onTransition(row.id, "WAITING_REVIEW")} disabled={busy} type="button">Review</button>
              <button onClick={() => onTransition(row.id, "CLOSED")} disabled={busy} type="button">Close</button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function HumanView({
  rows,
  busy,
  draft,
  setDraft,
  onCreate,
  onReview
}: {
  rows: AdminHumanIntelligence[];
  busy: boolean;
  draft: HumanDraft;
  setDraft: (value: HumanDraft) => void;
  onCreate: () => void;
  onReview: (id: number, verdict: "PASS" | "REJECT") => void;
}) {
  return (
    <div className="adminGridTwo humanGrid">
      <section className="workspaceCard adminPanel">
        <div className="sectionHead">
          <div className="sectionCopy">
            <h2>Human intelligence entry</h2>
            <p>Create a real backend record. It enters review before user-facing use.</p>
          </div>
          <button className="primary" onClick={onCreate} disabled={busy} type="button">
            <Plus size={16} />
            Submit
          </button>
        </div>
        <div className="adminFormGrid">
          <label>City<input value={draft.city} onChange={(event) => setDraft({ ...draft, city: event.target.value })} /></label>
          <label>Industry<input value={draft.industryId} onChange={(event) => setDraft({ ...draft, industryId: event.target.value })} /></label>
          <label>Chain node<input value={draft.linkId} onChange={(event) => setDraft({ ...draft, linkId: event.target.value })} /></label>
          <label>Entitlement<input value={draft.entitlement} onChange={(event) => setDraft({ ...draft, entitlement: event.target.value })} /></label>
          <label className="wide">Content<input value={draft.content} onChange={(event) => setDraft({ ...draft, content: event.target.value })} /></label>
        </div>
      </section>
      <section className="workspaceCard adminPanel">
        <PanelHead title="Human intelligence queue" detail="Approved records are promoted into intelligence for retrieval/report use." />
        <div className="table">
          {rows.map((row) => (
            <div className="row" key={row.id}>
              <strong>{row.city} / {row.linkId}</strong>
              <StatusBadge value={row.status} />
              <small>{row.content}</small>
              <div className="adminRowActions wideActions">
                <button onClick={() => onReview(row.id, "PASS")} disabled={busy || row.status !== "PENDING_REVIEW"} type="button">Approve</button>
                <button onClick={() => onReview(row.id, "REJECT")} disabled={busy || row.status !== "PENDING_REVIEW"} type="button">Reject</button>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function ListPanel<T>({ title, rows, render }: { title: string; rows: T[]; render: (row: T) => ReactNode }) {
  return (
    <section className="workspaceCard adminPanel">
      <PanelHead title={title} detail={`${rows.length} records`} />
      <div className="table">{rows.map((row, index) => <div className="row" key={index}>{render(row)}</div>)}</div>
    </section>
  );
}

function CompactRow({ title, badge, detail }: { title: string; badge: string; detail: string }) {
  return (
    <>
      <strong>{title}</strong>
      <StatusBadge value={badge} />
      <small>{detail}</small>
    </>
  );
}

function PanelHead({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="sectionHead">
      <div className="sectionCopy">
        <h2>{title}</h2>
        <p>{detail}</p>
      </div>
    </div>
  );
}

function StatusBadge({ value }: { value: string }) {
  return <span className={`adminBadge status-${value.toLowerCase().replaceAll("_", "-")}`}>{value}</span>;
}

function formatTime(value: string) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}
