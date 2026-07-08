"use client";

import {
  Activity,
  AlertTriangle,
  BookCopy,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  DatabaseZap,
  FileClock,
  GitCompareArrows,
  LayoutDashboard,
  LogIn,
  LogOut,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCheck
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { CollectionWorkspace } from "./collection-workspace";
import { DashboardView } from "./dashboard-view";
import { MonitoringView } from "./monitoring-view";
import { ConflictsView, FalseLedgerView, SnapshotsView } from "./governance-views";
import {
  approveAdminKnowledgeReview,
  createAdminHumanIntelligence,
  decideAdminReview,
  fetchAdminAlerts,
  fetchAdminAuditLogs,
  fetchAdminCollectionDeadLetters,
  fetchAdminCollectionJobs,
  fetchAdminCollectionSourceDetail,
  fetchAdminCollectionSources,
  fetchAdminDashboard,
  fetchAdminRiskRules,
  fetchAdminHumanIntelligence,
  fetchAdminIntelligenceReviews,
  fetchAdminKnowledgeDetail,
  fetchAdminKnowledgeDiff,
  fetchAdminKnowledgeInspect,
  fetchAdminKnowledgeNodes,
  fetchAdminTickets,
  fetchMe,
  login,
  logout,
  publishAdminKnowledgeVersion,
  reviewAdminHumanIntelligence,
  rollbackAdminKnowledgeNode,
  runAdminCollectionSource,
  saveAdminCollectionKeyword,
  saveAdminCollectionSource,
  saveAdminKnowledgeDraft,
  toggleAdminRiskRule,
  upsertAdminRiskRule,
  submitAdminKnowledgeReview,
  transitionAdminTicket,
  updateAdminAlert,
  AuthExpiredError,
  type AdminAlert,
  type AdminAuditLog,
  type AdminCollectionDeadLetter,
  type AdminCollectionJobRun,
  type AdminCollectionKeywordInput,
  type AdminCollectionSource,
  type AdminCollectionSourceDetail,
  type AdminCollectionSourceInput,
  type AdminDashboard,
  type AdminHumanIntelligence,
  type AdminIntelligenceReview,
  type AdminKnowledgeDetail,
  type AdminKnowledgeDiff,
  type AdminKnowledgeDraftInput,
  type AdminKnowledgeNode,
  type AdminKnowledgeVersion,
  type AdminTicket,
  type InspectionReport,
  type LoginProfile,
  type RiskRule,
  type RiskRuleUpsertInput
} from "../../lib/api-client";

type AdminSection = "dashboard" | "collection" | "knowledge" | "monitoring" | "alerts" | "audit" | "reviews" | "tickets" | "human" | "risk" | "conflicts" | "falseLedger" | "snapshots";
type NavGroup = { label: string; items: { id: AdminSection; label: string; icon: typeof LayoutDashboard }[] };
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

const emptyCollectionSourceDraft = (): AdminCollectionSourceInput => ({
  name: "",
  sourceType: "PUBLIC_PAGE",
  status: "ENABLED",
  intervalMinutes: 30,
  maxRetries: 1,
  failureThreshold: 3,
  cooldownMinutes: 30,
  regionId: "cn-default",
  industryId: "general",
  linkId: "collection",
  sourceId: "admin-collector",
  payloadJson: "{}"
});

const emptyCollectionKeywordDraft = (): AdminCollectionKeywordInput => ({
  sourceConfigId: 0,
  keyword: "",
  matchMode: "INCLUDE",
  status: "ACTIVE",
  notes: ""
});

const emptyKnowledgeDraft = (): AdminKnowledgeDraftInput => ({
  title: "",
  slug: "",
  industryId: "general",
  regionId: "cn-default",
  linkId: "sales-payment",
  summary: "",
  content: "",
  sourceUrl: "",
  confidence: 0.85,
  changeNotes: ""
});

const navGroups: NavGroup[] = [
  { label: "Overview", items: [
    { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  ]},
  { label: "Knowledge & Intel", items: [
    { id: "knowledge", label: "Knowledge", icon: BookCopy },
    { id: "reviews", label: "Reviews", icon: ClipboardCheck },
    { id: "human", label: "Human Intel", icon: UserCheck },
    { id: "tickets", label: "Tickets", icon: ShieldCheck },
  ]},
  { label: "Collection", items: [
    { id: "collection", label: "Collection", icon: DatabaseZap },
    { id: "snapshots", label: "Snapshots", icon: GitCompareArrows },
    { id: "risk", label: "Risk Rules", icon: ShieldCheck },
  ]},
  { label: "Ops & Security", items: [
    { id: "monitoring", label: "Monitoring", icon: Activity },
    { id: "alerts", label: "Alerts", icon: AlertTriangle },
    { id: "conflicts", label: "Conflicts", icon: GitCompareArrows },
    { id: "falseLedger", label: "False Intel", icon: ShieldCheck },
    { id: "audit", label: "Audit", icon: FileClock },
  ]},
  { label: "Commercial", items: [] },
  { label: "Compliance", items: [] },
];
const sectionLookup = new Map<string, string>();
for (const group of navGroups) {
  for (const item of group.items) {
    sectionLookup.set(item.id, item.label);
  }
}

export default function AdminPage() {
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [activeSection, setActiveSection] = useState<AdminSection>("dashboard");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("Sign in with an operator or administrator account.");
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [alerts, setAlerts] = useState<AdminAlert[]>([]);
  const [auditLogs, setAuditLogs] = useState<AdminAuditLog[]>([]);
  const [reviews, setReviews] = useState<AdminIntelligenceReview[]>([]);
  const [tickets, setTickets] = useState<AdminTicket[]>([]);
  const [humanRows, setHumanRows] = useState<AdminHumanIntelligence[]>([]);
  const [collectionSources, setCollectionSources] = useState<AdminCollectionSource[]>([]);
  const [collectionDetail, setCollectionDetail] = useState<AdminCollectionSourceDetail | null>(null);
  const [collectionJobs, setCollectionJobs] = useState<AdminCollectionJobRun[]>([]);
  const [collectionDeadLetters, setCollectionDeadLetters] = useState<AdminCollectionDeadLetter[]>([]);
  const [collectionSourceDraft, setCollectionSourceDraft] = useState<AdminCollectionSourceInput>(emptyCollectionSourceDraft());
  const [collectionKeywordDraft, setCollectionKeywordDraft] = useState<AdminCollectionKeywordInput>(emptyCollectionKeywordDraft());
  const [selectedSourceConfigId, setSelectedSourceConfigId] = useState<number | null>(null);
  const [knowledgeNodes, setKnowledgeNodes] = useState<AdminKnowledgeNode[]>([]);
  const [knowledgeDetail, setKnowledgeDetail] = useState<AdminKnowledgeDetail | null>(null);
  const [knowledgeDraft, setKnowledgeDraft] = useState<AdminKnowledgeDraftInput>(emptyKnowledgeDraft());
  const [knowledgeDiff, setKnowledgeDiff] = useState<AdminKnowledgeDiff | null>(null);
  const [inspectionReport, setInspectionReport] = useState<InspectionReport | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<number | null>(null);
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null);
  const [compareVersionId, setCompareVersionId] = useState<number | null>(null);
  const [auditQuery, setAuditQuery] = useState("");
  const [navCounts, setNavCounts] = useState<{ reviews: number; alerts: number; tickets: number; human: number }>({ reviews: 0, alerts: 0, tickets: 0, human: 0 });
  const [railCollapsed, setRailCollapsed] = useState(() => {
    try { return localStorage.getItem("bizsage.admin.railCollapsed") === "1"; }
    catch (e) { console.error("Failed to read railCollapsed from localStorage:", e); return false; }
  });
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<{ id: number; label: string; section: AdminSection; icon: typeof LayoutDashboard }[]>([]);
  const [showSearch, setShowSearch] = useState(false);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout>>();
  const [reviewFilter, setReviewFilter] = useState("");
  const [ticketFilter, setTicketFilter] = useState("");
  const [ticketTypeFilter, setTicketTypeFilter] = useState("");
  const [humanFilter, setHumanFilter] = useState("");
  const [riskRules, setRiskRules] = useState<RiskRule[]>([]);
  const [draftHuman, setDraftHuman] = useState<HumanDraft>({
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

  // V2: Restore profile from httpOnly cookie via /api/users/me
  useEffect(() => {
    let cancelled = false;
    fetchMe()
      .then((restoredProfile) => {
        if (!cancelled) setProfile(restoredProfile);
      })
      .catch(() => {
        // Not logged in — user will see the login form
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!profile || !isAdmin) return;
    void refreshAll(selectedNodeId ?? undefined);
  }, [profile, isAdmin]);

  useEffect(() => {
    if (!knowledgeDetail) return;
    const activeVersion = knowledgeDetail.versions.find((version) => version.versionId === selectedVersionId) ?? knowledgeDetail.versions[0];
    if (!activeVersion) return;
    setKnowledgeDraft({
      nodeId: knowledgeDetail.nodeId,
      title: activeVersion.title,
      slug: knowledgeDetail.slug,
      industryId: knowledgeDetail.industryId,
      regionId: knowledgeDetail.regionId,
      linkId: knowledgeDetail.linkId,
      summary: activeVersion.summary ?? "",
      content: activeVersion.content ?? "",
      sourceUrl: activeVersion.sourceUrl ?? "",
      confidence: Number(activeVersion.confidence ?? 0.85),
      changeNotes: activeVersion.changeNotes ?? ""
    });
  }, [knowledgeDetail, selectedVersionId]);

  const activeTitle = useMemo(
    () => sectionLookup.get(activeSection) ?? "Admin",
    [activeSection]
  );

  async function handleLogin() {
    setBusy(true);
    try {
      const nextProfile = await login(username, password);
      setProfile(nextProfile);
      setNotice(nextProfile.role === "USER" ? "This account cannot access admin operations." : "Admin API connected.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Sign in failed.");
    } finally {
      setBusy(false);
    }
  }

  function handleLogout() {
    logout().catch(() => {});
    setProfile(null);
    setDashboard(null);
    setAlerts([]);
    setAuditLogs([]);
    setReviews([]);
    setTickets([]);
    setHumanRows([]);
    setKnowledgeNodes([]);
    setKnowledgeDetail(null);
    setKnowledgeDraft(emptyKnowledgeDraft());
    setKnowledgeDiff(null);
    setSelectedNodeId(null);
    setSelectedVersionId(null);
    setCompareVersionId(null);
    setNotice("Signed out.");
  }

  function toggleRail() {
    const next = !railCollapsed;
    setRailCollapsed(next);
    try { localStorage.setItem("bizsage.admin.railCollapsed", next ? "1" : "0"); } catch (e) { console.error("Failed to persist railCollapsed:", e); }
  }

  function handleSearch(q: string) {
    setSearchQuery(q);
    // Debounce the actual search by 300ms to avoid filtering on every keystroke
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    if (!q.trim()) { setSearchResults([]); setShowSearch(false); return; }
    searchTimerRef.current = setTimeout(() => {
      const term = q.toLowerCase();
      const results: { id: number; label: string; section: AdminSection; icon: typeof LayoutDashboard }[] = [];
      for (const a of alerts) if (a.message.toLowerCase().includes(term)) results.push({ id: a.id, label: a.message, section: "alerts", icon: AlertTriangle });
      for (const t of tickets) if (t.title.toLowerCase().includes(term)) results.push({ id: t.id, label: t.title, section: "tickets", icon: ShieldCheck });
      for (const r of reviews) if (r.title.toLowerCase().includes(term)) results.push({ id: r.id, label: r.title, section: "reviews", icon: ClipboardCheck });
      for (const log of auditLogs) if (log.action.toLowerCase().includes(term) || log.actor.toLowerCase().includes(term)) results.push({ id: log.id, label: `${log.actor} / ${log.action}`, section: "audit", icon: FileClock });
      for (const n of knowledgeNodes) if (n.title.toLowerCase().includes(term)) results.push({ id: n.nodeId, label: n.title, section: "knowledge", icon: BookCopy });
      setSearchResults(results.slice(0, 10));
      setShowSearch(true);
    }, 300);
  }

  function navigateSearchResult(section: AdminSection) {
    setActiveSection(section);
    setShowSearch(false);
    setSearchQuery("");
    setSearchResults([]);
  }

  async function refreshCollection(preferredSourceId?: number) {
    const [sources, jobs, deadLetters] = await Promise.all([
      fetchAdminCollectionSources(),
      fetchAdminCollectionJobs(),
      fetchAdminCollectionDeadLetters()
    ]);
    setCollectionSources(sources);
    setCollectionJobs(jobs);
    setCollectionDeadLetters(deadLetters);
    const resolvedSourceId = preferredSourceId ?? selectedSourceConfigId ?? sources[0]?.sourceConfigId ?? null;
    if (resolvedSourceId == null) {
      setSelectedSourceConfigId(null);
      setCollectionDetail(null);
      setCollectionSourceDraft(emptyCollectionSourceDraft());
      setCollectionKeywordDraft(emptyCollectionKeywordDraft());
      return;
    }
    const detail = await fetchAdminCollectionSourceDetail(resolvedSourceId);
    setSelectedSourceConfigId(resolvedSourceId);
    setCollectionDetail(detail);
    setCollectionSourceDraft({
      sourceConfigId: detail.source.sourceConfigId,
      name: detail.source.name,
      sourceType: detail.source.sourceType,
      status: detail.source.status,
      intervalMinutes: detail.source.intervalMinutes,
      maxRetries: detail.source.maxRetries,
      failureThreshold: detail.source.failureThreshold,
      cooldownMinutes: detail.source.cooldownMinutes,
      regionId: detail.source.regionId,
      industryId: detail.source.industryId,
      linkId: detail.source.linkId,
      sourceId: detail.source.sourceId,
      payloadJson: detail.source.payloadJson
    });
    setCollectionKeywordDraft((current) => ({ ...current, sourceConfigId: resolvedSourceId }));
  }

  async function refreshKnowledge(preferredNodeId?: number) {
    const nodes = await fetchAdminKnowledgeNodes();
    setKnowledgeNodes(nodes);
    const resolvedNodeId = preferredNodeId ?? selectedNodeId ?? nodes[0]?.nodeId ?? null;
    if (resolvedNodeId == null) {
      setSelectedNodeId(null);
      setKnowledgeDetail(null);
      setKnowledgeDraft(emptyKnowledgeDraft());
      setSelectedVersionId(null);
      setCompareVersionId(null);
      setKnowledgeDiff(null);
      return;
    }
    const detail = await fetchAdminKnowledgeDetail(resolvedNodeId);
    setSelectedNodeId(resolvedNodeId);
    setKnowledgeDetail(detail);
    const defaultVersion = detail.versions[0]?.versionId ?? null;
    setSelectedVersionId((current) => (current && detail.versions.some((version) => version.versionId === current) ? current : defaultVersion));
    setCompareVersionId((current) => (current && detail.versions.some((version) => version.versionId === current) ? current : null));
    setKnowledgeDiff(null);
  }

  async function refreshAll(preferredNodeId?: number) {
    if (!profile) return;
    setBusy(true);
    try {
      const [nextDashboard, nextAlerts, nextAudit, nextReviews, nextTickets, nextHuman] = await Promise.all([
        fetchAdminDashboard(),
        fetchAdminAlerts(),
        fetchAdminAuditLogs(auditQuery),
        fetchAdminIntelligenceReviews(),
        fetchAdminTickets(),
        fetchAdminHumanIntelligence()
      ]);
      setDashboard(nextDashboard);
      setAlerts(nextAlerts.items);
      setAuditLogs(nextAudit.items);
      setReviews(nextReviews.items);
      setTickets(nextTickets.items);
      setHumanRows(nextHuman.items);
      setNavCounts({
        reviews: nextReviews.items.filter(r => r.reviewStatus === "PENDING").length,
        alerts: nextAlerts.items.filter(a => a.status !== "CLOSED").length,
        tickets: nextTickets.items.filter(t => t.status !== "CLOSED" && t.status !== "ARCHIVED").length,
        human: nextHuman.items.filter(h => h.status === "PENDING_REVIEW").length,
      });
      await refreshKnowledge(preferredNodeId);
      setNotice("Admin data refreshed from services/api.");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Admin refresh failed.");
    } finally {
      setBusy(false);
    }
  }

  async function runAction(action: () => Promise<unknown>, success: string, nodeHint?: number) {
    if (!profile) return;
    setBusy(true);
    try {
      await action();
      setNotice(success);
      await refreshAll(nodeHint);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Admin action failed.");
    } finally {
      setBusy(false);
    }
  }

  function startNewCollectionSource() {
    setSelectedSourceConfigId(null);
    setCollectionDetail(null);
    setCollectionSourceDraft(emptyCollectionSourceDraft());
    setCollectionKeywordDraft(emptyCollectionKeywordDraft());
    setActiveSection("collection");
    setNotice("Creating a new collection source.");
  }

  function startNewKnowledgeNode() {
    setSelectedNodeId(null);
    setSelectedVersionId(null);
    setCompareVersionId(null);
    setKnowledgeDiff(null);
    setKnowledgeDetail(null);
    setKnowledgeDraft(emptyKnowledgeDraft());
    setActiveSection("knowledge");
    setNotice("Creating a new knowledge node draft.");
  }

  async function loadKnowledgeDiff() {
    if (!profile || !selectedVersionId || !compareVersionId) return;
    setBusy(true);
    try {
      const diff = await fetchAdminKnowledgeDiff(compareVersionId, selectedVersionId);
      setKnowledgeDiff(diff);
      setNotice(`Loaded diff between versions ${compareVersionId} and ${selectedVersionId}.`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Load diff failed.");
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
            <p>Use an operator or super administrator account to maintain knowledge, alerts, tickets, audit logs, and human intelligence.</p>
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
    <main className={`workspace adminWorkspace ${railCollapsed ? "rail-collapsed" : ""}`}>
      <aside className="rail adminRail">
        <div className="brand">
          <span className="mark">BS</span>
          {!railCollapsed && (
            <div>
              <strong>BizSage Admin</strong>
              <small>V3 full loop</small>
            </div>
          )}
        </div>
        <button className="railToggle" onClick={toggleRail} title={railCollapsed ? "Expand sidebar" : "Collapse sidebar"} type="button">
          {railCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
        </button>
        <nav className="nav">
          {navGroups.map((group) => (
            <div className="navGroup" key={group.label}>
              <span className="navGroupLabel">{group.label}</span>
              {group.items.length === 0 ? (
                <span className="navGroupPlaceholder">Coming soon</span>
              ) : (
                group.items.map((section) => {
                  const Icon = section.icon;
                  const count = section.id === "reviews" ? navCounts.reviews
                    : section.id === "alerts" ? navCounts.alerts
                    : section.id === "tickets" ? navCounts.tickets
                    : section.id === "human" ? navCounts.human
                    : 0;
                  return (
                    <button
                      key={section.id}
                      className={`navItem ${activeSection === section.id ? "active" : ""}`}
                      onClick={() => setActiveSection(section.id)}
                      title={section.label}
                      type="button"
                    >
                      <Icon size={18} />
                      <span className="navItemLabel">{section.label}</span>
                      {count > 0 && <span className="navBadge">{count}</span>}
                    </button>
                  );
                })
              )}
            </div>
          ))}
        </nav>
        <section className="adminContext">
          <strong>{profile.username}</strong>
          <small>{profile.role} / {profile.regionId} / {profile.industryId}</small>
          <small>{profile.membershipLevel}</small>
        </section>
      </aside>

      <section className="workspaceBody adminBody">
        <header className="topbar">
          <div className="adminActionBar">
            <h1>{activeTitle}</h1>
            <span className="envBadge">{profile?.membershipLevel === "INTERNAL" ? "dev" : "prod"}</span>
            <div className="adminSearchBar">
              <Search size={14} className="adminSearchIcon" />
              <input
                value={searchQuery}
                onChange={(event) => handleSearch(event.target.value)}
                onKeyDown={(event) => { if (event.key === "Escape") { setShowSearch(false); setSearchQuery(""); } }}
                onFocus={() => { if (searchResults.length > 0) setShowSearch(true); }}
                onBlur={() => setTimeout(() => setShowSearch(false), 200)}
                placeholder="Search across all data..."
              />
              {showSearch && searchResults.length > 0 && (
                <div className="adminSearchDropdown">
                  {searchResults.map((result, index) => {
                    const Icon = result.icon;
                    return (
                      <button key={`${result.section}-${result.id}-${index}`} className="adminSearchItem" onClick={() => navigateSearchResult(result.section)} type="button">
                        <strong><Icon size={12} style={{ marginRight: 6 }} />{result.label}</strong>
                        <small>{sectionLookup.get(result.section)}</small>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
          <div className="topActions">
            <p className="adminNoticeText" style={{ maxWidth: 340, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{notice}</p>
            <button className="languageButton" onClick={() => void refreshAll()} disabled={busy} type="button">
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
          {activeSection === "monitoring" && (
            <MonitoringView
              sources={collectionSources}
              jobs={collectionJobs}
              deadLetters={collectionDeadLetters}
              knowledgeNodes={knowledgeNodes}
              alerts={alerts}
              tickets={tickets}
              reviews={reviews}
              humanRows={humanRows}
            />
          )}
          {activeSection === "collection" && (
            <CollectionWorkspace
              busy={busy}
              sources={collectionSources}
              selectedSourceId={selectedSourceConfigId}
              detail={collectionDetail}
              sourceDraft={collectionSourceDraft}
              keywordDraft={collectionKeywordDraft}
              jobs={collectionJobs}
              deadLetters={collectionDeadLetters}
              onSelectSource={(sourceConfigId) => runAction(async () => { if (profile) await refreshCollection(sourceConfigId); }, `Loaded source ${sourceConfigId}.`)}
              onStartNew={startNewCollectionSource}
              onSourceDraftChange={setCollectionSourceDraft}
              onKeywordDraftChange={setCollectionKeywordDraft}
              onSaveSource={() => runAction(() => saveAdminCollectionSource(selectedSourceConfigId ? { ...collectionSourceDraft, sourceConfigId: selectedSourceConfigId } : collectionSourceDraft), "Collection source saved.")}
              onSaveKeyword={() => runAction(() => saveAdminCollectionKeyword({ ...collectionKeywordDraft, sourceConfigId: selectedSourceConfigId ?? collectionKeywordDraft.sourceConfigId }), "Collection keyword saved.")}
              onRunSource={(sourceConfigId) => runAction(() => runAdminCollectionSource(sourceConfigId), "Collection source executed.")}
            />
          )}
          {activeSection === "knowledge" && (
            <KnowledgeView
              busy={busy}
              nodes={knowledgeNodes}
              selectedNodeId={selectedNodeId}
              detail={knowledgeDetail}
              draft={knowledgeDraft}
              diff={knowledgeDiff}
              selectedVersionId={selectedVersionId}
              compareVersionId={compareVersionId}
              inspectionReport={inspectionReport}
              onSelectNode={(nodeId) => runAction(async () => { if (profile) await refreshKnowledge(nodeId); }, `Loaded node ${nodeId}.`, nodeId)}
              onStartNew={startNewKnowledgeNode}
              onDraftChange={setKnowledgeDraft}
              onSelectVersion={setSelectedVersionId}
              onSelectCompareVersion={setCompareVersionId}
              onLoadDiff={() => void loadKnowledgeDiff()}
              onSaveDraft={() => runAction(() => saveAdminKnowledgeDraft(selectedNodeId ? { ...knowledgeDraft, nodeId: selectedNodeId } : knowledgeDraft), "Knowledge draft saved.", selectedNodeId ?? undefined)}
              onSubmitReview={(versionId) => runAction(() => submitAdminKnowledgeReview(selectedNodeId ?? 0, versionId, "Submitted from Admin V3"), "Knowledge version submitted for review.", selectedNodeId ?? undefined)}
              onApprove={(versionId) => runAction(() => approveAdminKnowledgeReview(selectedNodeId ?? 0, versionId, "Approved from Admin V3"), "Knowledge review approved.", selectedNodeId ?? undefined)}
              onPublish={(versionId) => runAction(() => publishAdminKnowledgeVersion(selectedNodeId ?? 0, versionId, "Published from Admin V3"), "Knowledge version published.", selectedNodeId ?? undefined)}
              onRollback={(versionId) => runAction(() => rollbackAdminKnowledgeNode(selectedNodeId ?? 0, versionId, "Rollback from Admin V3"), "Knowledge node rolled back.", selectedNodeId ?? undefined)}
              onInspect={() => runAction(async () => { const report = await fetchAdminKnowledgeInspect(); setInspectionReport(report); }, "Knowledge inspection complete.")}
            />
          )}
          {activeSection === "alerts" && <AlertsView rows={alerts} busy={busy} onAction={(id, action) => runAction(() => updateAdminAlert(id, action), `Alert ${action} complete.`)} />}
          {activeSection === "audit" && <AuditView rows={auditLogs} query={auditQuery} setQuery={setAuditQuery} onSearch={() => runAction(async () => {
            const next = await fetchAdminAuditLogs(auditQuery);
            setAuditLogs(next.items);
          }, "Audit logs filtered.")} />}
          {activeSection === "reviews" && <ReviewsView rows={reviews} busy={busy} onVerdict={(id, verdict) => runAction(() => decideAdminReview(id, verdict, `Admin selected ${verdict}`), `Review ${verdict} complete.`)} reviewFilter={reviewFilter} onFilterChange={(status) => runAction(async () => { setReviewFilter(status); const next = await fetchAdminIntelligenceReviews(status || undefined); setReviews(next.items); }, `Reviews filtered: ${status || "all"}`)} />}
          {activeSection === "tickets" && <TicketsView rows={tickets} busy={busy} onTransition={(id, status) => runAction(() => transitionAdminTicket(id, status, `Move ticket to ${status}`), "Ticket transition complete.")} ticketFilter={ticketFilter} ticketTypeFilter={ticketTypeFilter} onFilterChange={(status) => runAction(async () => { setTicketFilter(status); const next = await fetchAdminTickets(status || undefined); setTickets(next.items); }, `Tickets filtered: ${status || "all"}`)} onTypeFilterChange={(type) => setTicketTypeFilter(type)} />}
          {activeSection === "human" && <HumanView rows={humanRows} busy={busy} draft={draftHuman} setDraft={setDraftHuman} onCreate={() => runAction(() => createAdminHumanIntelligence(draftHuman), "Human intelligence submitted.")} onReview={(id, verdict) => runAction(() => reviewAdminHumanIntelligence(id, verdict, `Admin selected ${verdict}`), "Human intelligence review complete.")} humanFilter={humanFilter} onFilterChange={(status) => runAction(async () => { setHumanFilter(status); const next = await fetchAdminHumanIntelligence(status || undefined); setHumanRows(next.items); }, `Human intel filtered: ${status || "all"}`)} />}
          {activeSection === "risk" && <RiskRulesView rows={riskRules} busy={busy} onToggle={(id) => runAction(async () => { const updated = await toggleAdminRiskRule(id); setRiskRules(prev => prev.map(r => r.id === updated.id ? updated : r)); }, "Risk rule toggled.")} onSave={(payload) => runAction(async () => { const updated = await upsertAdminRiskRule(payload); setRiskRules(prev => { const idx = prev.findIndex(r => r.id === updated.id); if (idx >= 0) { const next = [...prev]; next[idx] = updated; return next; } return [...prev, updated]; }); }, "Risk rule saved.")} onRefresh={() => runAction(async () => { const rules = await fetchAdminRiskRules(); setRiskRules(rules); }, "Risk rules loaded.")} />}
          {activeSection === "conflicts" && <ConflictsView />}
          {activeSection === "falseLedger" && <FalseLedgerView />}
          {activeSection === "snapshots" && <SnapshotsView />}
        </div>
      </section>
    </main>
  );
}


function KnowledgeView({
  busy, nodes, selectedNodeId, detail, draft, diff, selectedVersionId, compareVersionId,
  onSelectNode, onStartNew, onDraftChange, onSelectVersion, onSelectCompareVersion,
  onLoadDiff, onSaveDraft, onSubmitReview, onApprove, onPublish, onRollback, onInspect, inspectionReport
}: {
  busy: boolean;
  nodes: AdminKnowledgeNode[];
  selectedNodeId: number | null;
  detail: AdminKnowledgeDetail | null;
  draft: AdminKnowledgeDraftInput;
  diff: AdminKnowledgeDiff | null;
  selectedVersionId: number | null;
  compareVersionId: number | null;
  inspectionReport: InspectionReport | null;
  onSelectNode: (nodeId: number) => void;
  onStartNew: () => void;
  onDraftChange: (value: AdminKnowledgeDraftInput) => void;
  onSelectVersion: (versionId: number | null) => void;
  onSelectCompareVersion: (versionId: number | null) => void;
  onLoadDiff: () => void;
  onSaveDraft: () => void;
  onSubmitReview: (versionId: number) => void;
  onApprove: (versionId: number) => void;
  onPublish: (versionId: number) => void;
  onRollback: (versionId: number) => void;
  onInspect: () => void;
}) {
  const selectedVersion = detail?.versions.find((version) => version.versionId === selectedVersionId) ?? detail?.versions[0] ?? null;
  const [editorTab, setEditorTab] = useState<"form" | "json" | "inspect">("form");
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set(["general-sales-payment", "general-supply-chain", "general-channel"]));
  const toggleGroup = (key: string) => setExpandedGroups(prev => { const next = new Set(prev); if (next.has(key)) next.delete(key); else next.add(key); return next; });

  // Build tree: industry → link → nodes
  const tree = useMemo(() => {
    const map = new Map<string, Map<string, AdminKnowledgeNode[]>>();
    for (const node of nodes) {
      const indKey = node.industryId || "general";
      const linkKey = node.linkId || "general";
      if (!map.has(indKey)) map.set(indKey, new Map());
      const linkMap = map.get(indKey)!;
      if (!linkMap.has(linkKey)) linkMap.set(linkKey, []);
      linkMap.get(linkKey)!.push(node);
    }
    return map;
  }, [nodes]);

  // JSON draft
  const jsonDraft = useMemo(() => JSON.stringify(draft, null, 2), [draft]);
  const [jsonError, setJsonError] = useState("");

  function handleJsonChange(raw: string) {
    setJsonError("");
    try {
      const parsed = JSON.parse(raw) as AdminKnowledgeDraftInput;
      onDraftChange(parsed);
    } catch { setJsonError("Invalid JSON — fix syntax to sync back to form."); }
  }

  const findingIcon = (type: string) => {
    switch (type) {
      case "NO_SOURCE": return "🔗";
      case "LOW_CONFIDENCE": return "📉";
      case "STALE_DRAFT": return "⏳";
      case "DUPLICATE_APPROVED": return "⚠️";
      default: return "•";
    }
  };

  return (
    <section className="adminKnowledgeShell">
      {/* Left: Tree navigation */}
      <aside className="workspaceCard adminPanel knowledgeListPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>Knowledge Tree</h2>
            <p>{nodes.length} maintained records</p>
          </div>
          <button className="primary" onClick={onStartNew} type="button">
            <Plus size={16} />
            New
          </button>
        </div>
        <div className="knowledgeTreeNav">
          {Array.from(tree.entries()).map(([industryId, linkMap]) => (
            <div className="treeGroup" key={industryId}>
              <button className="treeGroupHeader" onClick={() => toggleGroup(industryId)} type="button">
                <span className="treeToggle">{expandedGroups.has(industryId) ? "▾" : "▸"}</span>
                <span className="treeIndustryLabel">{industryId}</span>
                <small>{Array.from(linkMap.values()).flat().length}</small>
              </button>
              {expandedGroups.has(industryId) && Array.from(linkMap.entries()).map(([linkId, linkNodes]) => {
                const linkKey = `${industryId}-${linkId}`;
                return (
                  <div className="treeSubGroup" key={linkKey}>
                    <button className="treeSubHeader" onClick={() => toggleGroup(linkKey)} type="button">
                      <span className="treeToggle">{expandedGroups.has(linkKey) ? "▾" : "▸"}</span>
                      <span className="treeLinkLabel">{linkId}</span>
                      <small>{linkNodes.length}</small>
                    </button>
                    {expandedGroups.has(linkKey) && linkNodes.map((node) => (
                      <button
                        key={node.nodeId}
                        className={`treeLeaf ${selectedNodeId === node.nodeId ? "active" : ""}`}
                        onClick={() => onSelectNode(node.nodeId)}
                        type="button"
                      >
                        <StatusBadge value={node.status} />
                        <span>{node.title}</span>
                      </button>
                    ))}
                  </div>
                );
              })}
            </div>
          ))}
          {nodes.length === 0 && <div className="emptyRow">No knowledge nodes yet.</div>}
        </div>
      </aside>

      {/* Center: Editor with tabs */}
      <section className="workspaceCard adminPanel knowledgeEditorPanel">
        <div className="knowledgeEditorTabs">
          <button className={`knowledgeTab ${editorTab === "form" ? "active" : ""}`} onClick={() => setEditorTab("form")} type="button">Form</button>
          <button className={`knowledgeTab ${editorTab === "json" ? "active" : ""}`} onClick={() => setEditorTab("json")} type="button">JSON</button>
          <button className={`knowledgeTab ${editorTab === "inspect" ? "active" : ""}`} onClick={() => setEditorTab("inspect")} type="button">
            Inspect
            {inspectionReport && <span className="tabBadge">{inspectionReport.findings.length}</span>}
          </button>
        </div>

        {editorTab === "form" && (
          <>
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>Draft editor</h2>
                <p>Save formal knowledge drafts with scope, confidence, and version notes.</p>
              </div>
              <button className="primary" onClick={onSaveDraft} disabled={busy} type="button">Save draft</button>
            </div>
            <div className="adminFormGrid knowledgeFormGrid">
              <label>Title<input value={draft.title} onChange={(event) => onDraftChange({ ...draft, title: event.target.value })} /></label>
              <label>Slug<input value={draft.slug} onChange={(event) => onDraftChange({ ...draft, slug: event.target.value })} /></label>
              <label>Industry<input value={draft.industryId} onChange={(event) => onDraftChange({ ...draft, industryId: event.target.value })} /></label>
              <label>Region<input value={draft.regionId} onChange={(event) => onDraftChange({ ...draft, regionId: event.target.value })} /></label>
              <label>Chain node<input value={draft.linkId} onChange={(event) => onDraftChange({ ...draft, linkId: event.target.value })} /></label>
              <label>Confidence<input value={draft.confidence} onChange={(event) => onDraftChange({ ...draft, confidence: Number(event.target.value) })} type="number" min="0" max="1" step="0.01" /></label>
              <label className="wide">Summary<textarea value={draft.summary} onChange={(event) => onDraftChange({ ...draft, summary: event.target.value })} rows={4} /></label>
              <label className="wide">Content<textarea value={draft.content} onChange={(event) => onDraftChange({ ...draft, content: event.target.value })} rows={12} /></label>
              <label className="wide">Source URL<input value={draft.sourceUrl} onChange={(event) => onDraftChange({ ...draft, sourceUrl: event.target.value })} /></label>
              <label className="wide">Change notes<textarea value={draft.changeNotes} onChange={(event) => onDraftChange({ ...draft, changeNotes: event.target.value })} rows={3} /></label>
            </div>
          </>
        )}

        {editorTab === "json" && (
          <div className="knowledgeJsonEditor">
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>JSON editor</h2>
                <p>Edit the full draft payload directly. Changes sync back to the form when JSON is valid.</p>
              </div>
              <button className="primary" onClick={onSaveDraft} disabled={busy} type="button">Save draft</button>
            </div>
            <textarea
              className="jsonTextarea"
              value={jsonDraft}
              onChange={(event) => handleJsonChange(event.target.value)}
              rows={24}
              spellCheck={false}
            />
            {jsonError && <p className="jsonError">{jsonError}</p>}
          </div>
        )}

        {editorTab === "inspect" && (
          <div className="knowledgeInspectPanel">
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>Batch inspection</h2>
                <p>Covers expired records, missing sources, low confidence, and conflicts across all knowledge nodes.</p>
              </div>
              <button className="primary" onClick={onInspect} disabled={busy} type="button">
                <RefreshCw size={16} />
                Run inspection
              </button>
            </div>
            {inspectionReport ? (
              <>
                <div className="inspectSummary">
                  <span className={`adminBadge status-healthy`}>{inspectionReport.healthyNodes} healthy</span>
                  <span className={`adminBadge status-${inspectionReport.warningNodes > 0 ? "pending" : "healthy"}`}>{inspectionReport.warningNodes} warnings</span>
                  <span className={`adminBadge status-${inspectionReport.criticalNodes > 0 ? "p0" : "healthy"}`}>{inspectionReport.criticalNodes} critical</span>
                  <small>{inspectionReport.totalNodes} total nodes inspected</small>
                </div>
                {inspectionReport.findings.length === 0 ? (
                  <div className="emptyRow">All nodes pass inspection. No issues found.</div>
                ) : (
                  <div className="inspectFindingsList">
                    {inspectionReport.findings.map((finding, index) => (
                      <div className={`inspectFindingItem type-${finding.type.toLowerCase()}`} key={index}>
                        <div className="inspectFindingHead">
                          <span className="findingIcon">{findingIcon(finding.type)}</span>
                          <StatusBadge value={finding.type} />
                          <strong>{finding.title}</strong>
                          <button className="inspectNavButton" onClick={() => { setEditorTab("form"); onSelectNode(finding.nodeId); }} type="button">
                            Go to node →
                          </button>
                        </div>
                        <p>{finding.detail}</p>
                      </div>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div className="emptyRow">Click "Run inspection" to scan all knowledge nodes for issues.</div>
            )}
          </div>
        )}

        {editorTab !== "inspect" && diff && (
          <div className="knowledgeDiffPanel">
            <div className="sectionHead compact">
              <div className="sectionCopy">
                <h2>Version diff</h2>
                <p>Compare revision metadata and content before publish or rollback.</p>
              </div>
            </div>
            <div className="knowledgeDiffGrid">
              <article>
                <small>Left</small>
                <strong>{diff.leftTitle}</strong>
                <StatusBadge value={diff.leftReviewStatus} />
                <p>{diff.leftSummary}</p>
                <pre>{diff.leftContent}</pre>
              </article>
              <article>
                <small>Right</small>
                <strong>{diff.rightTitle}</strong>
                <StatusBadge value={diff.rightReviewStatus} />
                <p>{diff.rightSummary}</p>
                <pre>{diff.rightContent}</pre>
              </article>
            </div>
          </div>
        )}
      </section>

      {/* Right: Versions + publication log (unchanged) */}
      <aside className="workspaceCard adminPanel knowledgeVersionPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>Versions</h2>
            <p>{detail ? `${detail.status} / published ${detail.publishedVersionId ?? "-"}` : "Choose a node to inspect review and publication history."}</p>
          </div>
          <button className="ghost" onClick={onLoadDiff} disabled={!selectedVersionId || !compareVersionId || busy} type="button">
            <GitCompareArrows size={16} />
            Diff
          </button>
        </div>
        <div className="knowledgeVersionList">
          {(detail?.versions ?? []).map((version) => (
            <article className={`knowledgeVersionItem ${selectedVersion?.versionId === version.versionId ? "active" : ""}`} key={version.versionId}>
              <button className="knowledgeVersionSelect" onClick={() => onSelectVersion(version.versionId)} type="button">
                <strong>V{version.versionNumber} {version.title}</strong>
                <div className="knowledgeVersionMeta">
                  <StatusBadge value={version.reviewStatus} />
                  <small>{version.author} / {formatTime(version.updateTime)}</small>
                </div>
              </button>
              <div className="adminRowActions wideActions">
                <button onClick={() => onSelectCompareVersion(version.versionId)} type="button">Compare</button>
                <button onClick={() => onSubmitReview(version.versionId)} disabled={busy || version.reviewStatus !== "DRAFT"} type="button">Submit</button>
                <button onClick={() => onApprove(version.versionId)} disabled={busy || version.reviewStatus !== "IN_REVIEW"} type="button">Approve</button>
                <button onClick={() => onPublish(version.versionId)} disabled={busy || version.reviewStatus !== "APPROVED"} type="button">Publish</button>
                <button onClick={() => onRollback(version.versionId)} disabled={busy || detail?.publishedVersionId == null || version.reviewStatus !== "APPROVED"} type="button">Rollback</button>
              </div>
              {compareVersionId === version.versionId && <small>Compare baseline selected.</small>}
            </article>
          ))}
          {detail?.versions.length === 0 && <div className="emptyRow">No versions yet. Save a draft to begin.</div>}
        </div>
        <div className="knowledgePublicationList">
          <div className="sectionHead compact">
            <div className="sectionCopy">
              <h2>Publication log</h2>
              <p>Published versions sync into legacy knowledge retrieval data.</p>
            </div>
          </div>
          <div className="table">
            {(detail?.publications ?? []).map((publication) => (
              <div className="row" key={publication.publicationId}>
                <strong>{publication.action} / V{publication.versionId}</strong>
                <StatusBadge value={publication.action} />
                <small>{publication.actor} / {formatTime(publication.createTime)} / {publication.notes ?? "-"}</small>
              </div>
            ))}
          </div>
        </div>
      </aside>
    </section>
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

function ReviewsView({ rows, busy, onVerdict, reviewFilter, onFilterChange }: {
  rows: AdminIntelligenceReview[];
  busy: boolean;
  onVerdict: (id: number, verdict: "PASS" | "REJECT" | "FLAG" | "SUSPICIOUS" | "COMPLIANCE" | "PAID_INTEL" | "ARCHIVE") => void;
  reviewFilter: string;
  onFilterChange: (status: string) => void;
}) {
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const toggleExpand = (id: number) => setExpandedIds(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  const pending = rows.filter(r => r.reviewStatus === "PENDING").length;
  const completed = rows.filter(r => r.reviewStatus === "COMPLETED").length;
  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>Intelligence Review</h2>
          <p>{pending} pending / {completed} completed / {rows.length} total</p>
        </div>
        <div className="adminFilterBar">
          <span className="adminFilterLabel">Status:</span>
          <select value={reviewFilter} onChange={e => onFilterChange(e.target.value)} className="adminFilterSelect">
            <option value="">All reviews</option>
            <option value="PENDING">Pending</option>
            <option value="COMPLETED">Completed</option>
          </select>
        </div>
      </div>
      <div className="adminReviewList">
        {rows.length === 0 && <div className="emptyRow">No reviews match the selected filter.</div>}
        {rows.map((row) => {
          const expanded = expandedIds.has(row.id);
          const maxLen = 200;
          const longContent = row.content && row.content.length > maxLen;
          return (
            <article className={`adminReviewItem ${row.reviewStatus === "COMPLETED" ? "review-completed" : ""}`} key={row.id}>
              <div>
                <h3>{row.title}</h3>
                <p>{expanded || !longContent ? row.content : row.content?.slice(0, maxLen) + "…"}</p>
                {longContent && (
                  <button className="expandToggle" onClick={() => toggleExpand(row.id)} type="button">
                    {expanded ? "Collapse" : "Read full content"}
                  </button>
                )}
                <div className="reviewMeta">
                  <small>{row.sourceId} / {row.regionId} / {row.industryId}</small>
                  <small>Confidence: {row.confidence}</small>
                  {row.verdict && <StatusBadge value={row.verdict} />}
                  {row.reviewer && <small>Reviewer: {row.reviewer}</small>}
                </div>
                {row.reviewStatus === "COMPLETED" && row.reason && (
                  <small className="reviewReason">Reason: {row.reason}</small>
                )}
              </div>
              <div className="adminDecision">
                <StatusBadge value={row.reviewStatus} />
                {row.reviewStatus !== "COMPLETED" && (
                  <>
                    <button onClick={() => onVerdict(row.id, "PASS")} disabled={busy} type="button" title="Approve and publish">Pass</button>
                    <button onClick={() => onVerdict(row.id, "REJECT")} disabled={busy} type="button" title="Reject permanently">Reject</button>
                    <button onClick={() => onVerdict(row.id, "FLAG")} disabled={busy} type="button" title="Escalate for second review">Flag</button>
                    <button onClick={() => onVerdict(row.id, "SUSPICIOUS")} disabled={busy} type="button" title="Mark as suspicious, create investigation ticket">Suspect</button>
                    <button onClick={() => onVerdict(row.id, "COMPLIANCE")} disabled={busy} type="button" title="Route to legal/compliance team">Compliance</button>
                    <button onClick={() => onVerdict(row.id, "PAID_INTEL")} disabled={busy} type="button" title="Approve as paid intelligence">Paid</button>
                    <button onClick={() => onVerdict(row.id, "ARCHIVE")} disabled={busy} type="button" title="Archive without publishing">Archive</button>
                  </>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function TicketsView({ rows, busy, onTransition, ticketFilter, ticketTypeFilter, onFilterChange, onTypeFilterChange }: {
  rows: AdminTicket[];
  busy: boolean;
  onTransition: (id: number, status: string) => void;
  ticketFilter: string;
  ticketTypeFilter: string;
  onFilterChange: (status: string) => void;
  onTypeFilterChange: (type: string) => void;
}) {
  const openCount = rows.filter(t => t.status !== "CLOSED" && t.status !== "ARCHIVED").length;
  const sla = (createTime: string): { hours: number; label: string; cls: string } => {
    if (!createTime) return { hours: 0, label: "—", cls: "" };
    const diff = (Date.now() - new Date(createTime).getTime()) / (1000 * 60 * 60);
    if (diff < 4) return { hours: diff, label: `${Math.round(diff * 60)}m ago`, cls: "sla-fresh" };
    if (diff < 24) return { hours: diff, label: `${Math.round(diff)}h ago`, cls: "sla-warn" };
    return { hours: diff, label: `${Math.round(diff / 24)}d ago`, cls: "sla-overdue" };
  };
  const displayedRows = ticketTypeFilter ? rows.filter(r => r.ticketType === ticketTypeFilter) : rows;
  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>Ticket Ledger</h2>
          <p>{openCount} open / {rows.length} total</p>
        </div>
        <div className="adminFilterBar">
          <span className="adminFilterLabel">Status:</span>
          <select value={ticketFilter} onChange={e => onFilterChange(e.target.value)} className="adminFilterSelect">
            <option value="">All</option>
            <option value="NEW">New</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="WAITING_REVIEW">Waiting Review</option>
            <option value="CLOSED">Closed</option>
            <option value="ARCHIVED">Archived</option>
          </select>
          <span className="adminFilterLabel">Type:</span>
          <select value={ticketTypeFilter} onChange={e => onTypeFilterChange(e.target.value)} className="adminFilterSelect">
            <option value="">All types</option>
            <option value="review_escalation">Review Escalation</option>
            <option value="suspicious_review">Suspicious Review</option>
            <option value="compliance_review">Compliance</option>
            <option value="conflict">Conflict</option>
          </select>
        </div>
      </div>
      <div className="adminTable">
        <div className="adminTableHead ticketHead"><span>Severity</span><span>Title</span><span>Owner</span><span>Status</span><span>SLA</span><span>Actions</span></div>
        {displayedRows.length === 0 && <div className="emptyRow">No tickets match filters.</div>}
        {displayedRows.map((row) => {
          const slaInfo = sla(row.createTime);
          return (
            <div className="adminTableRow ticketRow" key={row.id}>
              <StatusBadge value={row.severity} />
              <div>
                <strong>{row.title}</strong>
                <small>{row.ticketType} / {row.nextAction ?? "—"}</small>
              </div>
              <span>{row.owner ?? "Unassigned"}</span>
              <StatusBadge value={row.status} />
              <span className={`slaCell ${slaInfo.cls}`}>{slaInfo.label}</span>
              <div className="adminRowActions">
                {row.status === "NEW" && <button onClick={() => onTransition(row.id, "CLAIMED")} disabled={busy} type="button">Claim</button>}
                {(row.status === "NEW" || row.status === "CLAIMED") && <button onClick={() => onTransition(row.id, "IN_PROGRESS")} disabled={busy} type="button">Start</button>}
                {row.status === "IN_PROGRESS" && <button onClick={() => onTransition(row.id, "WAITING_REVIEW")} disabled={busy} type="button">Review</button>}
                {row.status === "WAITING_REVIEW" && <button onClick={() => onTransition(row.id, "CLOSED")} disabled={busy} type="button">Close</button>}
                {row.status === "CLOSED" && <button onClick={() => onTransition(row.id, "ARCHIVED")} disabled={busy} type="button">Archive</button>}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function HumanView({ rows, busy, draft, setDraft, onCreate, onReview, humanFilter, onFilterChange }: {
  rows: AdminHumanIntelligence[];
  busy: boolean;
  draft: HumanDraft;
  setDraft: (value: HumanDraft) => void;
  onCreate: () => void;
  onReview: (id: number, verdict: "PASS" | "REJECT") => void;
  humanFilter: string;
  onFilterChange: (status: string) => void;
}) {
  const pending = rows.filter(r => r.status === "PENDING_REVIEW").length;
  const approved = rows.filter(r => r.status === "APPROVED").length;
  const entitlementLabel = (e: string) => {
    switch (e) {
      case "FREE": return "Free (not visible)";
      case "SEED_PAID": return "Seed Paid (visible)";
      case "PAID": return "Paid (premium)";
      case "INTERNAL": return "Internal only";
      case "LEGAL_FREEZE": return "Legal freeze (blocked)";
      default: return e;
    }
  };
  const entitlementClass = (e: string) => {
    switch (e) {
      case "FREE": return "ent-free";
      case "SEED_PAID": return "ent-seed";
      case "PAID": return "ent-paid";
      case "INTERNAL": return "ent-internal";
      case "LEGAL_FREEZE": return "ent-frozen";
      default: return "";
    }
  };
  return (
    <div className="adminGridTwo humanGrid">
      <section className="workspaceCard adminPanel">
        <div className="sectionHead">
          <div className="sectionCopy">
            <h2>Human Intelligence Entry</h2>
            <p>Record local insights. Enters review before user-facing use.</p>
          </div>
          <button className="primary" onClick={onCreate} disabled={busy} type="button">
            <Plus size={16} />
            Submit
          </button>
        </div>
        <div className="adminFormGrid">
          <label>City<input value={draft.city} onChange={(event) => setDraft({ ...draft, city: event.target.value })} /></label>
          <label>Industry ID<input value={draft.industryId} onChange={(event) => setDraft({ ...draft, industryId: event.target.value })} /></label>
          <label>Chain Node<input value={draft.linkId} onChange={(event) => setDraft({ ...draft, linkId: event.target.value })} /></label>
          <label>Region ID<input value={draft.regionId} onChange={(event) => setDraft({ ...draft, regionId: event.target.value })} /></label>
          <label>Source Type
            <select value={draft.sourceType} onChange={(event) => setDraft({ ...draft, sourceType: event.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
              <option value="local_visit">Local Visit</option>
              <option value="partner_report">Partner Report</option>
              <option value="user_submission">User Submission</option>
              <option value="field_survey">Field Survey</option>
              <option value="expert_interview">Expert Interview</option>
            </select>
          </label>
          <label>Collector<input value={draft.collector} onChange={(event) => setDraft({ ...draft, collector: event.target.value })} /></label>
          <label>Source ID<input value={draft.sourceId} onChange={(event) => setDraft({ ...draft, sourceId: event.target.value })} /></label>
          <label>Event Time<input value={draft.eventTime} onChange={(event) => setDraft({ ...draft, eventTime: event.target.value })} placeholder="e.g. 2026-07" /></label>
          <label>Confidence
            <div className="confidenceRow">
              <input type="range" min="0" max="1" step="0.01" value={draft.confidence} onChange={(event) => setDraft({ ...draft, confidence: Number(event.target.value) })} className="flex1" />
              <span className="adminBadge">{draft.confidence.toFixed(2)}</span>
            </div>
          </label>
          <label>Entitlement
            <select value={draft.entitlement} onChange={(event) => setDraft({ ...draft, entitlement: event.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
              <option value="FREE">Free (not visible to free users)</option>
              <option value="SEED_PAID">Seed Paid (visible)</option>
              <option value="PAID">Paid (premium content)</option>
              <option value="INTERNAL">Internal Only</option>
              <option value="LEGAL_FREEZE">Legal Freeze (blocked)</option>
            </select>
          </label>
          <label className="wide">Content<input value={draft.content} onChange={(event) => setDraft({ ...draft, content: event.target.value })} /></label>
        </div>
      </section>
      <section className="workspaceCard adminPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>Queue</h2>
            <p>Approved records are promoted into intelligence for retrieval/report use.</p>
          </div>
          <div className="adminFilterBar">
            <select value={humanFilter} onChange={e => onFilterChange(e.target.value)} className="adminFilterSelect">
              <option value="">All</option>
              <option value="PENDING_REVIEW">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </div>
        </div>
        <div className="table humanQueueTable">
          {rows.length === 0 && <div className="emptyRow">No human intelligence records yet.</div>}
          {rows.map((row) => (
            <div className={`row humanQueueRow ${row.status === "PENDING_REVIEW" ? "human-pending" : ""}`} key={row.id}>
              <div>
                <strong>{row.city} / {row.linkId}</strong>
                <div className="humanQueueMeta">
                  <StatusBadge value={row.status} />
                  <span className={`entBadge ${entitlementClass(row.entitlement)}`} title={entitlementLabel(row.entitlement)}>{row.entitlement}</span>
                  <small>Conf: {row.confidence}</small>
                  <small>{row.sourceType} / {row.collector}</small>
                </div>
              </div>
              <small className="humanQueueContent">{row.content}</small>
              {row.status === "PENDING_REVIEW" && (
                <div className="adminRowActions wideActions">
                  <button onClick={() => onReview(row.id, "PASS")} disabled={busy} type="button">Approve</button>
                  <button onClick={() => onReview(row.id, "REJECT")} disabled={busy} type="button">Reject</button>
                </div>
              )}
              {row.reviewer && <small className="humanReviewer">Reviewed by {row.reviewer}{row.reviewNotes ? ` — ${row.reviewNotes}` : ""}</small>}
            </div>
          ))}
        </div>
        {pending > 0 && <div className="monitoringSummary"><small>{pending} pending review, {approved} approved</small></div>}
      </section>
    </div>
  );
}

function RiskRulesView({ rows, busy, onToggle, onSave, onRefresh }: {
  rows: RiskRule[];
  busy: boolean;
  onToggle: (id: number) => void;
  onSave: (payload: RiskRuleUpsertInput) => void;
  onRefresh: () => void;
}) {
  const TABS: { key: string; label: string }[] = [
    { key: "rumor_detection", label: "Rumor" },
    { key: "conflict_judgment", label: "Conflict" },
    { key: "gray_content", label: "Gray Content" },
    { key: "ai_self_check", label: "AI Self-Check" },
    { key: "api_abuse", label: "API Abuse" },
    { key: "paid_protection", label: "Paid Protection" },
  ];
  const [activeTab, setActiveTab] = useState("rumor_detection");
  const [editId, setEditId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<RiskRuleUpsertInput>({ ruleType: "", name: "", description: "", enabled: true, thresholdValue: null, scopeJson: null, riskLevel: "MEDIUM", changeMode: "IMMEDIATE" });
  const filtered = rows.filter(r => r.ruleType === activeTab);

  function startEdit(rule: RiskRule) {
    setEditId(rule.id);
    setEditForm({ id: rule.id, ruleType: rule.ruleType, name: rule.name, description: rule.description, enabled: rule.enabled, thresholdValue: rule.thresholdValue, scopeJson: rule.scopeJson, riskLevel: rule.riskLevel, changeMode: rule.changeMode });
  }
  function cancelEdit() { setEditId(null); }
  function handleSave() { onSave(editForm); setEditId(null); }

  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>Risk Control Rules</h2>
          <p>Configure detection thresholds, enable/disable rules, and set activation modes across six risk categories.</p>
        </div>
        <button className="ghost" onClick={onRefresh} disabled={busy} type="button"><RefreshCw size={14} /> Refresh</button>
      </div>
      <div className="riskTabs">
        {TABS.map(tab => (
          <button key={tab.key} className={`riskTab ${activeTab === tab.key ? "active" : ""}`} onClick={() => setActiveTab(tab.key)} type="button">
            {tab.label}
          </button>
        ))}
      </div>
      <div className="riskRuleList">
        {filtered.length === 0 && <div className="emptyRow">No rules configured for this category. Seed data creates default rules on first startup.</div>}
        {filtered.map(rule => (
          <div className={`riskRuleItem ${rule.enabled ? "" : "rule-disabled"}`} key={rule.id}>
            {editId === rule.id ? (
              <div className="riskRuleEdit">
                <div className="adminFormGrid">
                  <label>Name<input value={editForm.name} onChange={e => setEditForm({ ...editForm, name: e.target.value })} /></label>
                  <label>Risk Level
                    <select value={editForm.riskLevel} onChange={e => setEditForm({ ...editForm, riskLevel: e.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
                      <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="CRITICAL">Critical</option>
                    </select>
                  </label>
                  <label>Change Mode
                    <select value={editForm.changeMode} onChange={e => setEditForm({ ...editForm, changeMode: e.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
                      <option value="IMMEDIATE">Immediate</option><option value="GRAY">Gray Release</option><option value="SCHEDULED">Scheduled</option>
                    </select>
                  </label>
                  <label>Threshold (0–1)<input value={editForm.thresholdValue ?? ""} onChange={e => setEditForm({ ...editForm, thresholdValue: e.target.value ? Number(e.target.value) : null })} type="number" min="0" max="1" step="0.01" /></label>
                  <label className="wide">Description<textarea value={editForm.description} onChange={e => setEditForm({ ...editForm, description: e.target.value })} rows={3} /></label>
                  <label className="wide">Scope JSON<textarea value={editForm.scopeJson ?? ""} onChange={e => setEditForm({ ...editForm, scopeJson: e.target.value })} rows={3} /></label>
                </div>
                <div className="adminRowActions adminFormActions">
                  <button className="primary" onClick={handleSave} disabled={busy} type="button">Save</button>
                  <button className="ghost" onClick={cancelEdit} type="button">Cancel</button>
                </div>
              </div>
            ) : (
              <div className="riskRuleView">
                <div className="riskRuleInfo">
                  <div className="riskRuleHead">
                    <strong>{rule.name}</strong>
                    <span className={`adminBadge status-${rule.riskLevel.toLowerCase() === "critical" ? "p0" : rule.riskLevel.toLowerCase() === "high" ? "open" : "healthy"}`}>{rule.riskLevel}</span>
                    <span className={`adminBadge ${rule.enabled ? "status-healthy" : "status-rejected"}`}>{rule.enabled ? "ENABLED" : "DISABLED"}</span>
                    <small>v{rule.version} / {rule.changeMode}</small>
                  </div>
                  <p>{rule.description}</p>
                  <div className="riskRuleMeta">
                    {rule.thresholdValue != null && <small>Threshold: {rule.thresholdValue}</small>}
                    {rule.scopeJson && <small>Scope: {rule.scopeJson}</small>}
                  </div>
                </div>
                <div className="adminRowActions">
                  <button onClick={() => startEdit(rule)} type="button">Edit</button>
                  <button onClick={() => onToggle(rule.id)} disabled={busy} type="button">{rule.enabled ? "Disable" : "Enable"}</button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </section>
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









