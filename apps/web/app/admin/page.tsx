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
  updatePreferredLocale,
  AuthExpiredError,
  type AppLocale,
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
import { adminCodeLabel, adminCodeWithRaw, adminText, normalizeLocale, type AdminLocale } from "./admin-i18n";

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

function navGroupsFor(locale: AdminLocale): NavGroup[] {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  return [
  { label: t("总览", "Overview"), items: [
    { id: "dashboard", label: t("仪表盘", "Dashboard"), icon: LayoutDashboard },
  ]},
  { label: t("知识与情报", "Knowledge & Intel"), items: [
    { id: "knowledge", label: t("知识", "Knowledge"), icon: BookCopy },
    { id: "reviews", label: t("复核", "Reviews"), icon: ClipboardCheck },
    { id: "human", label: t("人工情报", "Human Intel"), icon: UserCheck },
    { id: "tickets", label: t("工单", "Tickets"), icon: ShieldCheck },
  ]},
  { label: t("采集", "Collection"), items: [
    { id: "collection", label: t("采集", "Collection"), icon: DatabaseZap },
    { id: "snapshots", label: t("快照", "Snapshots"), icon: GitCompareArrows },
    { id: "risk", label: t("风控规则", "Risk Rules"), icon: ShieldCheck },
  ]},
  { label: t("运维与安全", "Ops & Security"), items: [
    { id: "monitoring", label: t("监控", "Monitoring"), icon: Activity },
    { id: "alerts", label: t("告警", "Alerts"), icon: AlertTriangle },
    { id: "conflicts", label: t("冲突", "Conflicts"), icon: GitCompareArrows },
    { id: "falseLedger", label: t("虚假情报", "False Intel"), icon: ShieldCheck },
    { id: "audit", label: t("审计", "Audit"), icon: FileClock },
  ]},
  { label: t("商业化", "Commercial"), items: [] },
  { label: t("合规", "Compliance"), items: [] },
  ];
}

function sectionLabel(locale: AdminLocale, section: AdminSection) {
  for (const group of navGroupsFor(locale)) {
    const item = group.items.find((candidate) => candidate.id === section);
    if (item) return item.label;
  }
  return "Admin";
}

export default function AdminPage() {
  const [locale, setLocale] = useState<AppLocale>("zh-CN");
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [activeSection, setActiveSection] = useState<AdminSection>("dashboard");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("请使用运营或管理员账号登录。");
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
    if (typeof window === "undefined") return false;
    try { return window.localStorage.getItem("bizsage.admin.railCollapsed") === "1"; }
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
  const t = useCallback((zh: string, en: string) => adminText(locale, zh, en), [locale]);
  const navGroups = useMemo(() => navGroupsFor(locale), [locale]);

  // V2: Restore profile from httpOnly cookie via /api/users/me
  useEffect(() => {
    let cancelled = false;
    fetchMe()
      .then((restoredProfile) => {
        if (!cancelled) {
          const restoredLocale = normalizeLocale(restoredProfile.preferredLocale);
          setLocale(restoredLocale);
          setProfile({ ...restoredProfile, preferredLocale: restoredLocale });
        }
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
    () => sectionLabel(locale, activeSection),
    [activeSection, locale]
  );

  async function toggleLocale() {
    const previousLocale = locale;
    const nextLocale: AppLocale = locale === "zh-CN" ? "en" : "zh-CN";
    setLocale(nextLocale);
    setNotice(adminText(nextLocale, "语言偏好已保存。", "Language preference saved."));
    if (!profile) return;

    setProfile({ ...profile, preferredLocale: nextLocale });
    try {
      const updatedProfile = await updatePreferredLocale(nextLocale);
      setProfile({ ...updatedProfile, preferredLocale: normalizeLocale(updatedProfile.preferredLocale) });
    } catch (error) {
      setLocale(previousLocale);
      setProfile({ ...profile, preferredLocale: previousLocale });
      setNotice(error instanceof Error ? error.message : adminText(previousLocale, "语言偏好保存失败。", "Failed to save language preference."));
    }
  }

  async function handleLogin() {
    setBusy(true);
    try {
      const nextProfile = await login(username, password);
      const nextLocale = normalizeLocale(nextProfile.preferredLocale);
      setLocale(nextLocale);
      setProfile({ ...nextProfile, preferredLocale: nextLocale });
      setNotice(nextProfile.role === "USER" ? adminText(nextLocale, "此账号不能访问管理操作。", "This account cannot access admin operations.") : adminText(nextLocale, "管理 API 已连接。", "Admin API connected."));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t("登录失败。", "Sign in failed."));
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
    setNotice(t("已退出登录。", "Signed out."));
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
      setNotice(t("管理端数据已从 services/api 刷新。", "Admin data refreshed from services/api."));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t("管理端刷新失败。", "Admin refresh failed."));
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
      setNotice(error instanceof Error ? error.message : t("管理端操作失败。", "Admin action failed."));
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
    setNotice(t("正在创建新采集源。", "Creating a new collection source."));
  }

  function startNewKnowledgeNode() {
    setSelectedNodeId(null);
    setSelectedVersionId(null);
    setCompareVersionId(null);
    setKnowledgeDiff(null);
    setKnowledgeDetail(null);
    setKnowledgeDraft(emptyKnowledgeDraft());
    setActiveSection("knowledge");
    setNotice(t("正在创建新知识节点草稿。", "Creating a new knowledge node draft."));
  }

  async function loadKnowledgeDiff() {
    if (!profile || !selectedVersionId || !compareVersionId) return;
    setBusy(true);
    try {
      const diff = await fetchAdminKnowledgeDiff(compareVersionId, selectedVersionId);
      setKnowledgeDiff(diff);
      setNotice(t(`已加载版本 ${compareVersionId} 与 ${selectedVersionId} 的差异。`, `Loaded diff between versions ${compareVersionId} and ${selectedVersionId}.`));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t("加载差异失败。", "Load diff failed."));
    } finally {
      setBusy(false);
    }
  }

  if (!profile) {
    return (
      <main className="loginScreen">
        <button className="languageButton loginLanguage" onClick={() => void toggleLocale()} type="button">
          {locale === "zh-CN" ? "English" : "中文"}
        </button>
        <section className="loginCard">
          <div className="brand loginBrand">
            <span className="mark">BS</span>
            <div>
              <strong>BizSage Admin</strong>
              <small>{t("V3 运营控制台", "V3 operations console")}</small>
            </div>
          </div>
          <div className="loginCopy">
            <h1>{t("管理端登录", "Admin sign in")}</h1>
            <p>{t("使用运营或超级管理员账号维护知识、告警、工单、审计日志和人工情报。", "Use an operator or super administrator account to maintain knowledge, alerts, tickets, audit logs, and human intelligence.")}</p>
          </div>
          <div className="loginForm">
            <label>{t("账号", "Username")}<input value={username} onChange={(event) => setUsername(event.target.value)} /></label>
            <label>{t("密码", "Password")}<input value={password} onChange={(event) => setPassword(event.target.value)} type="password" /></label>
            <button className="primary" onClick={handleLogin} disabled={busy} type="button">
              <LogIn size={16} />
              {busy ? t("登录中...", "Signing in...") : t("登录", "Sign in")}
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
            <h1>{t("需要权限", "Permission required")}</h1>
            <p>{t(`${profile.username} 当前身份为 ${profile.role}。Admin V3 需要 OPERATOR 或 SUPER_ADMIN。`, `${profile.username} is signed in as ${profile.role}. Admin V3 requires OPERATOR or SUPER_ADMIN.`)}</p>
          </div>
          <button className="ghost" onClick={handleLogout} type="button">
            <LogOut size={16} />
            {t("退出登录", "Sign out")}
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
              <small>{t("V3 闭环", "V3 full loop")}</small>
            </div>
          )}
        </div>
        <button className="railToggle" onClick={toggleRail} title={railCollapsed ? t("展开侧栏", "Expand sidebar") : t("折叠侧栏", "Collapse sidebar")} type="button">
          {railCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
        </button>
        <nav className="nav">
          {navGroups.map((group) => (
            <div className="navGroup" key={group.label}>
              <span className="navGroupLabel">{group.label}</span>
              {group.items.length === 0 ? (
                <span className="navGroupPlaceholder">{t("即将开放", "Coming soon")}</span>
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
                placeholder={t("搜索全部数据...", "Search across all data...")}
              />
              {showSearch && searchResults.length > 0 && (
                <div className="adminSearchDropdown">
                  {searchResults.map((result, index) => {
                    const Icon = result.icon;
                    return (
                      <button key={`${result.section}-${result.id}-${index}`} className="adminSearchItem" onClick={() => navigateSearchResult(result.section)} type="button">
                        <strong><Icon size={12} style={{ marginRight: 6 }} />{result.label}</strong>
                        <small>{sectionLabel(locale, result.section)}</small>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
          <div className="topActions">
            <p className="adminNoticeText" style={{ maxWidth: 340, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{notice}</p>
            <button className="languageButton" onClick={() => void toggleLocale()} type="button">
              {locale === "zh-CN" ? "English" : "中文"}
            </button>
            <button className="languageButton" onClick={() => void refreshAll()} disabled={busy} type="button">
              <RefreshCw size={16} />
              {t("刷新", "Refresh")}
            </button>
            <button className="ghost" onClick={handleLogout} type="button">
              <LogOut size={16} />
              {t("退出登录", "Sign out")}
            </button>
          </div>
        </header>

        <div className="workspaceScroll adminScroll">
          {activeSection === "dashboard" && <DashboardView dashboard={dashboard} locale={locale} />}
          {activeSection === "monitoring" && (
            <MonitoringView
              locale={locale}
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
              locale={locale}
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
              locale={locale}
              nodes={knowledgeNodes}
              selectedNodeId={selectedNodeId}
              detail={knowledgeDetail}
              draft={knowledgeDraft}
              diff={knowledgeDiff}
              selectedVersionId={selectedVersionId}
              compareVersionId={compareVersionId}
              inspectionReport={inspectionReport}
              onSelectNode={(nodeId) => runAction(async () => { if (profile) await refreshKnowledge(nodeId); }, t(`已加载节点 ${nodeId}。`, `Loaded node ${nodeId}.`), nodeId)}
              onStartNew={startNewKnowledgeNode}
              onDraftChange={setKnowledgeDraft}
              onSelectVersion={setSelectedVersionId}
              onSelectCompareVersion={setCompareVersionId}
              onLoadDiff={() => void loadKnowledgeDiff()}
              onSaveDraft={() => runAction(() => saveAdminKnowledgeDraft(selectedNodeId ? { ...knowledgeDraft, nodeId: selectedNodeId } : knowledgeDraft), t("知识草稿已保存。", "Knowledge draft saved."), selectedNodeId ?? undefined)}
              onSubmitReview={(versionId) => runAction(() => submitAdminKnowledgeReview(selectedNodeId ?? 0, versionId, "Submitted from Admin V3"), t("知识版本已提交复核。", "Knowledge version submitted for review."), selectedNodeId ?? undefined)}
              onApprove={(versionId) => runAction(() => approveAdminKnowledgeReview(selectedNodeId ?? 0, versionId, "Approved from Admin V3"), t("知识复核已通过。", "Knowledge review approved."), selectedNodeId ?? undefined)}
              onPublish={(versionId) => runAction(() => publishAdminKnowledgeVersion(selectedNodeId ?? 0, versionId, "Published from Admin V3"), t("知识版本已发布。", "Knowledge version published."), selectedNodeId ?? undefined)}
              onRollback={(versionId) => runAction(() => rollbackAdminKnowledgeNode(selectedNodeId ?? 0, versionId, "Rollback from Admin V3"), t("知识节点已回滚。", "Knowledge node rolled back."), selectedNodeId ?? undefined)}
              onInspect={() => runAction(async () => { const report = await fetchAdminKnowledgeInspect(); setInspectionReport(report); }, t("知识巡检完成。", "Knowledge inspection complete."))}
            />
          )}
          {activeSection === "alerts" && <AlertsView locale={locale} rows={alerts} busy={busy} onAction={(id, action) => runAction(() => updateAdminAlert(id, action), t(`告警 ${adminCodeLabel(locale, action)} 已完成。`, `Alert ${adminCodeLabel(locale, action)} complete.`))} />}
          {activeSection === "audit" && <AuditView rows={auditLogs} query={auditQuery} setQuery={setAuditQuery} onSearch={() => runAction(async () => {
            const next = await fetchAdminAuditLogs(auditQuery);
            setAuditLogs(next.items);
          }, t("审计日志已筛选。", "Audit logs filtered."))} locale={locale} />}
          {activeSection === "reviews" && <ReviewsView locale={locale} rows={reviews} busy={busy} onVerdict={(id, verdict) => runAction(() => decideAdminReview(id, verdict, `Admin selected ${verdict}`), t(`复核 ${adminCodeLabel(locale, verdict)} 已完成。`, `Review ${adminCodeLabel(locale, verdict)} complete.`))} reviewFilter={reviewFilter} onFilterChange={(status) => runAction(async () => { setReviewFilter(status); const next = await fetchAdminIntelligenceReviews(status || undefined); setReviews(next.items); }, t(`复核筛选：${status ? adminCodeLabel(locale, status) : "全部"}`, `Reviews filtered: ${status ? adminCodeLabel(locale, status) : "all"}`))} />}
          {activeSection === "tickets" && <TicketsView locale={locale} rows={tickets} busy={busy} onTransition={(id, status) => runAction(() => transitionAdminTicket(id, status, `Move ticket to ${status}`), t("工单状态已流转。", "Ticket transition complete."))} ticketFilter={ticketFilter} ticketTypeFilter={ticketTypeFilter} onFilterChange={(status) => runAction(async () => { setTicketFilter(status); const next = await fetchAdminTickets(status || undefined); setTickets(next.items); }, t(`工单筛选：${status ? adminCodeLabel(locale, status) : "全部"}`, `Tickets filtered: ${status ? adminCodeLabel(locale, status) : "all"}`))} onTypeFilterChange={(type) => setTicketTypeFilter(type)} />}
          {activeSection === "human" && <HumanView locale={locale} rows={humanRows} busy={busy} draft={draftHuman} setDraft={setDraftHuman} onCreate={() => runAction(() => createAdminHumanIntelligence(draftHuman), t("人工情报已提交。", "Human intelligence submitted."))} onReview={(id, verdict) => runAction(() => reviewAdminHumanIntelligence(id, verdict, `Admin selected ${verdict}`), t("人工情报复核完成。", "Human intelligence review complete."))} humanFilter={humanFilter} onFilterChange={(status) => runAction(async () => { setHumanFilter(status); const next = await fetchAdminHumanIntelligence(status || undefined); setHumanRows(next.items); }, t(`人工情报筛选：${status ? adminCodeLabel(locale, status) : "全部"}`, `Human intel filtered: ${status ? adminCodeLabel(locale, status) : "all"}`))} />}
          {activeSection === "risk" && <RiskRulesView locale={locale} rows={riskRules} busy={busy} onToggle={(id) => runAction(async () => { const updated = await toggleAdminRiskRule(id); setRiskRules(prev => prev.map(r => r.id === updated.id ? updated : r)); }, t("风控规则已切换。", "Risk rule toggled."))} onSave={(payload) => runAction(async () => { const updated = await upsertAdminRiskRule(payload); setRiskRules(prev => { const idx = prev.findIndex(r => r.id === updated.id); if (idx >= 0) { const next = [...prev]; next[idx] = updated; return next; } return [...prev, updated]; }); }, t("风控规则已保存。", "Risk rule saved."))} onRefresh={() => runAction(async () => { const rules = await fetchAdminRiskRules(); setRiskRules(rules); }, t("风控规则已加载。", "Risk rules loaded."))} />}
          {activeSection === "conflicts" && <ConflictsView locale={locale} />}
          {activeSection === "falseLedger" && <FalseLedgerView locale={locale} />}
          {activeSection === "snapshots" && <SnapshotsView locale={locale} />}
        </div>
      </section>
    </main>
  );
}


function KnowledgeView({
  busy, locale, nodes, selectedNodeId, detail, draft, diff, selectedVersionId, compareVersionId,
  onSelectNode, onStartNew, onDraftChange, onSelectVersion, onSelectCompareVersion,
  onLoadDiff, onSaveDraft, onSubmitReview, onApprove, onPublish, onRollback, onInspect, inspectionReport
}: {
  busy: boolean;
  locale: AdminLocale;
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
  const t = (zh: string, en: string) => adminText(locale, zh, en);
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
    } catch { setJsonError(t("JSON 无效，请修正语法后同步回表单。", "Invalid JSON. Fix syntax to sync back to the form.")); }
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
            <h2>{t("知识树", "Knowledge tree")}</h2>
            <p>{nodes.length} {t("条维护记录", "maintained records")}</p>
          </div>
          <button className="primary" onClick={onStartNew} type="button">
            <Plus size={16} />
            {t("新建", "New")}
          </button>
        </div>
        <div className="knowledgeTreeNav">
          {Array.from(tree.entries()).map(([industryId, linkMap]) => (
            <div className="treeGroup" key={industryId}>
              <button className="treeGroupHeader" onClick={() => toggleGroup(industryId)} type="button">
                <span className="treeToggle">{expandedGroups.has(industryId) ? "▾" : "▸"}</span>
                <span className="treeIndustryLabel">{adminCodeWithRaw(locale, industryId)}</span>
                <small>{Array.from(linkMap.values()).flat().length}</small>
              </button>
              {expandedGroups.has(industryId) && Array.from(linkMap.entries()).map(([linkId, linkNodes]) => {
                const linkKey = `${industryId}-${linkId}`;
                return (
                  <div className="treeSubGroup" key={linkKey}>
                    <button className="treeSubHeader" onClick={() => toggleGroup(linkKey)} type="button">
                      <span className="treeToggle">{expandedGroups.has(linkKey) ? "▾" : "▸"}</span>
	                      <span className="treeLinkLabel">{adminCodeWithRaw(locale, linkId)}</span>
                      <small>{linkNodes.length}</small>
                    </button>
                    {expandedGroups.has(linkKey) && linkNodes.map((node) => (
                      <button
                        key={node.nodeId}
                        className={`treeLeaf ${selectedNodeId === node.nodeId ? "active" : ""}`}
                        onClick={() => onSelectNode(node.nodeId)}
                        type="button"
                      >
	                        <StatusBadge value={node.status} locale={locale} />
                        <span>{node.title}</span>
                      </button>
                    ))}
                  </div>
                );
              })}
            </div>
          ))}
	          {nodes.length === 0 && <div className="emptyRow">{t("暂无知识节点。", "No knowledge nodes yet.")}</div>}
        </div>
      </aside>

      {/* Center: Editor with tabs */}
      <section className="workspaceCard adminPanel knowledgeEditorPanel">
        <div className="knowledgeEditorTabs">
	          <button className={`knowledgeTab ${editorTab === "form" ? "active" : ""}`} onClick={() => setEditorTab("form")} type="button">{t("表单", "Form")}</button>
	          <button className={`knowledgeTab ${editorTab === "json" ? "active" : ""}`} onClick={() => setEditorTab("json")} type="button">JSON</button>
	          <button className={`knowledgeTab ${editorTab === "inspect" ? "active" : ""}`} onClick={() => setEditorTab("inspect")} type="button">
	            {t("巡检", "Inspect")}
            {inspectionReport && <span className="tabBadge">{inspectionReport.findings.length}</span>}
          </button>
        </div>

        {editorTab === "form" && (
          <>
            <div className="sectionHead compact">
              <div className="sectionCopy">
	                <h2>{t("草稿编辑器", "Draft editor")}</h2>
	                <p>{t("保存带范围、置信度和版本说明的正式知识草稿。", "Save formal knowledge drafts with scope, confidence, and version notes.")}</p>
	              </div>
	              <button className="primary" onClick={onSaveDraft} disabled={busy} type="button">{t("保存草稿", "Save draft")}</button>
	            </div>
	            <div className="adminFormGrid knowledgeFormGrid">
	              <label>{t("标题", "Title")}<input value={draft.title} onChange={(event) => onDraftChange({ ...draft, title: event.target.value })} /></label>
	              <label>{t("Slug", "Slug")}<input value={draft.slug} onChange={(event) => onDraftChange({ ...draft, slug: event.target.value })} title={adminCodeWithRaw(locale, draft.slug)} /></label>
	              <label>{t("行业", "Industry")}<input value={draft.industryId} onChange={(event) => onDraftChange({ ...draft, industryId: event.target.value })} title={adminCodeWithRaw(locale, draft.industryId)} /></label>
	              <label>{t("区域", "Region")}<input value={draft.regionId} onChange={(event) => onDraftChange({ ...draft, regionId: event.target.value })} title={adminCodeWithRaw(locale, draft.regionId)} /></label>
	              <label>{t("链路节点", "Chain node")}<input value={draft.linkId} onChange={(event) => onDraftChange({ ...draft, linkId: event.target.value })} title={adminCodeWithRaw(locale, draft.linkId)} /></label>
	              <label>{t("置信度", "Confidence")}<input value={draft.confidence} onChange={(event) => onDraftChange({ ...draft, confidence: Number(event.target.value) })} type="number" min="0" max="1" step="0.01" /></label>
	              <label className="wide">{t("摘要", "Summary")}<textarea value={draft.summary} onChange={(event) => onDraftChange({ ...draft, summary: event.target.value })} rows={4} /></label>
	              <label className="wide">{t("内容", "Content")}<textarea value={draft.content} onChange={(event) => onDraftChange({ ...draft, content: event.target.value })} rows={12} /></label>
	              <label className="wide">{t("来源 URL", "Source URL")}<input value={draft.sourceUrl} onChange={(event) => onDraftChange({ ...draft, sourceUrl: event.target.value })} /></label>
	              <label className="wide">{t("变更说明", "Change notes")}<textarea value={draft.changeNotes} onChange={(event) => onDraftChange({ ...draft, changeNotes: event.target.value })} rows={3} /></label>
            </div>
          </>
        )}

        {editorTab === "json" && (
          <div className="knowledgeJsonEditor">
            <div className="sectionHead compact">
              <div className="sectionCopy">
	                <h2>{t("JSON 编辑器", "JSON editor")}</h2>
	                <p>{t("直接编辑完整草稿载荷。JSON 有效时会同步回表单。", "Edit the full draft payload directly. Changes sync back to the form when JSON is valid.")}</p>
	              </div>
	              <button className="primary" onClick={onSaveDraft} disabled={busy} type="button">{t("保存草稿", "Save draft")}</button>
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
	                <h2>{t("批量巡检", "Batch inspection")}</h2>
	                <p>{t("覆盖所有知识节点中的过期记录、缺失来源、低置信度和冲突。", "Covers expired records, missing sources, low confidence, and conflicts across all knowledge nodes.")}</p>
              </div>
              <button className="primary" onClick={onInspect} disabled={busy} type="button">
                <RefreshCw size={16} />
	                {t("运行巡检", "Run inspection")}
              </button>
            </div>
            {inspectionReport ? (
              <>
                <div className="inspectSummary">
	                  <span className={`adminBadge status-healthy`}>{inspectionReport.healthyNodes} {t("健康", "healthy")}</span>
	                  <span className={`adminBadge status-${inspectionReport.warningNodes > 0 ? "pending" : "healthy"}`}>{inspectionReport.warningNodes} {t("警告", "warnings")}</span>
	                  <span className={`adminBadge status-${inspectionReport.criticalNodes > 0 ? "p0" : "healthy"}`}>{inspectionReport.criticalNodes} {t("严重", "critical")}</span>
	                  <small>{inspectionReport.totalNodes} {t("个节点已巡检", "total nodes inspected")}</small>
                </div>
                {inspectionReport.findings.length === 0 ? (
	                  <div className="emptyRow">{t("所有节点均通过巡检，未发现问题。", "All nodes pass inspection. No issues found.")}</div>
                ) : (
                  <div className="inspectFindingsList">
                    {inspectionReport.findings.map((finding, index) => (
                      <div className={`inspectFindingItem type-${finding.type.toLowerCase()}`} key={index}>
                        <div className="inspectFindingHead">
                          <span className="findingIcon">{findingIcon(finding.type)}</span>
	                          <StatusBadge value={finding.type} locale={locale} />
                          <strong>{finding.title}</strong>
                          <button className="inspectNavButton" onClick={() => { setEditorTab("form"); onSelectNode(finding.nodeId); }} type="button">
	                            {t("查看节点", "Go to node")}
                          </button>
                        </div>
                        <p>{finding.detail}</p>
                      </div>
                    ))}
                  </div>
                )}
              </>
            ) : (
	              <div className="emptyRow">{t("点击“运行巡检”扫描所有知识节点。", "Click Run inspection to scan all knowledge nodes for issues.")}</div>
            )}
          </div>
        )}

        {editorTab !== "inspect" && diff && (
          <div className="knowledgeDiffPanel">
            <div className="sectionHead compact">
              <div className="sectionCopy">
	                <h2>{t("版本差异", "Version diff")}</h2>
	                <p>{t("发布或回滚前比较修订元数据和内容。", "Compare revision metadata and content before publish or rollback.")}</p>
              </div>
            </div>
            <div className="knowledgeDiffGrid">
              <article>
	                <small>{t("左侧", "Left")}</small>
                <strong>{diff.leftTitle}</strong>
	                <StatusBadge value={diff.leftReviewStatus} locale={locale} />
                <p>{diff.leftSummary}</p>
                <pre>{diff.leftContent}</pre>
              </article>
              <article>
	                <small>{t("右侧", "Right")}</small>
                <strong>{diff.rightTitle}</strong>
	                <StatusBadge value={diff.rightReviewStatus} locale={locale} />
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
	            <h2>{t("版本", "Versions")}</h2>
	            <p>{detail ? `${adminCodeLabel(locale, detail.status)} / ${t("已发布", "published")} ${detail.publishedVersionId ?? "-"}` : t("选择节点查看复核与发布历史。", "Choose a node to inspect review and publication history.")}</p>
          </div>
          <button className="ghost" onClick={onLoadDiff} disabled={!selectedVersionId || !compareVersionId || busy} type="button">
            <GitCompareArrows size={16} />
	            {t("比较", "Diff")}
          </button>
        </div>
        <div className="knowledgeVersionList">
          {(detail?.versions ?? []).map((version) => (
            <article className={`knowledgeVersionItem ${selectedVersion?.versionId === version.versionId ? "active" : ""}`} key={version.versionId}>
              <button className="knowledgeVersionSelect" onClick={() => onSelectVersion(version.versionId)} type="button">
                <strong>V{version.versionNumber} {version.title}</strong>
                <div className="knowledgeVersionMeta">
                  <StatusBadge value={version.reviewStatus} locale={locale} />
                  <small>{version.author} / {formatTime(version.updateTime)}</small>
                </div>
              </button>
              <div className="adminRowActions wideActions">
                <button onClick={() => onSelectCompareVersion(version.versionId)} type="button">{t("比较", "Compare")}</button>
                <button onClick={() => onSubmitReview(version.versionId)} disabled={busy || version.reviewStatus !== "DRAFT"} type="button">{t("提交", "Submit")}</button>
                <button onClick={() => onApprove(version.versionId)} disabled={busy || version.reviewStatus !== "IN_REVIEW"} type="button">{t("通过", "Approve")}</button>
                <button onClick={() => onPublish(version.versionId)} disabled={busy || version.reviewStatus !== "APPROVED"} type="button">{t("发布", "Publish")}</button>
                <button onClick={() => onRollback(version.versionId)} disabled={busy || detail?.publishedVersionId == null || version.reviewStatus !== "APPROVED"} type="button">{t("回滚", "Rollback")}</button>
              </div>
              {compareVersionId === version.versionId && <small>{t("已选择比较基线。", "Compare baseline selected.")}</small>}
            </article>
          ))}
          {detail?.versions.length === 0 && <div className="emptyRow">{t("暂无版本，请先保存草稿。", "No versions yet. Save a draft to begin.")}</div>}
        </div>
        <div className="knowledgePublicationList">
          <div className="sectionHead compact">
            <div className="sectionCopy">
              <h2>{t("发布日志", "Publication log")}</h2>
              <p>{t("已发布版本会同步到旧版知识检索数据。", "Published versions sync into legacy knowledge retrieval data.")}</p>
            </div>
          </div>
          <div className="table">
            {(detail?.publications ?? []).map((publication) => (
              <div className="row" key={publication.publicationId}>
                <strong>{adminCodeLabel(locale, publication.action)} / V{publication.versionId}</strong>
                <StatusBadge value={publication.action} locale={locale} />
                <small>{publication.actor} / {formatTime(publication.createTime)} / {publication.notes ?? "-"}</small>
              </div>
            ))}
          </div>
        </div>
      </aside>
    </section>
  );
}

function AlertsView({ locale, rows, busy, onAction }: { locale: AdminLocale; rows: AdminAlert[]; busy: boolean; onAction: (id: number, action: "acknowledge" | "claim" | "close") => void }) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  return (
    <section className="workspaceCard adminPanel">
      <PanelHead title={t("告警中心", "Alert center")} detail={t("确认、认领或关闭生产告警。每个动作都会写入审计日志。", "Acknowledge, claim, or close production alerts. Every action writes an audit log.")} />
      <div className="adminTable">
        <div className="adminTableHead"><span>{t("级别", "Level")}</span><span>{t("组件", "Component")}</span><span>{t("消息", "Message")}</span><span>{t("状态", "Status")}</span><span>{t("操作", "Actions")}</span></div>
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <StatusBadge value={row.level} locale={locale} />
            <strong>{row.component}</strong>
            <span>{row.message}</span>
            <StatusBadge value={row.status} locale={locale} />
            <div className="adminRowActions">
              <button title={t("确认告警", "Acknowledge alert")} onClick={() => onAction(row.id, "acknowledge")} disabled={busy} type="button">{t("确认", "Ack")}</button>
              <button title={t("认领告警", "Claim alert")} onClick={() => onAction(row.id, "claim")} disabled={busy} type="button">{t("认领", "Claim")}</button>
              <button title={t("关闭告警", "Close alert")} onClick={() => onAction(row.id, "close")} disabled={busy} type="button">{t("关闭", "Close")}</button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function AuditView({ locale, rows, query, setQuery, onSearch }: { locale: AdminLocale; rows: AdminAuditLog[]; query: string; setQuery: (value: string) => void; onSearch: () => void }) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>{t("审计日志", "Audit logs")}</h2>
          <p>{t("按操作者、动作、目标类型或目标 ID 搜索。", "Search by actor, action, target type, or target id.")}</p>
        </div>
        <div className="adminSearch">
          <Search size={16} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t("搜索审计日志", "Search audit logs")} />
          <button className="primary" onClick={onSearch} type="button">{t("搜索", "Search")}</button>
        </div>
      </div>
      <div className="adminTable">
        <div className="adminTableHead"><span>{t("操作者", "Actor")}</span><span>{t("动作", "Action")}</span><span>{t("目标", "Target")}</span><span>{t("结果", "Result")}</span><span>{t("时间", "Time")}</span></div>
        {rows.map((row) => (
          <div className="adminTableRow" key={row.id}>
            <strong>{row.actor}</strong>
            <span>{adminCodeLabel(locale, row.action)}</span>
            <span>{adminCodeLabel(locale, row.targetType)}:{row.targetId}</span>
            <StatusBadge value={row.result} locale={locale} />
            <small>{formatTime(row.createTime)}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

function ReviewsView({ locale, rows, busy, onVerdict, reviewFilter, onFilterChange }: {
  locale: AdminLocale;
  rows: AdminIntelligenceReview[];
  busy: boolean;
  onVerdict: (id: number, verdict: "PASS" | "REJECT" | "FLAG" | "SUSPICIOUS" | "COMPLIANCE" | "PAID_INTEL" | "ARCHIVE") => void;
  reviewFilter: string;
  onFilterChange: (status: string) => void;
}) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const toggleExpand = (id: number) => setExpandedIds(prev => { const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next; });
  const pending = rows.filter(r => r.reviewStatus === "PENDING").length;
  const completed = rows.filter(r => r.reviewStatus === "COMPLETED").length;
  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>{t("情报复核", "Intelligence review")}</h2>
          <p>{pending} {t("待处理", "pending")} / {completed} {t("已完成", "completed")} / {rows.length} {t("总计", "total")}</p>
        </div>
        <div className="adminFilterBar">
          <span className="adminFilterLabel">{t("状态：", "Status:")}</span>
          <select value={reviewFilter} onChange={e => onFilterChange(e.target.value)} className="adminFilterSelect">
            <option value="">{t("全部复核", "All reviews")}</option>
            <option value="PENDING">{adminCodeLabel(locale, "PENDING")}</option>
            <option value="COMPLETED">{adminCodeLabel(locale, "COMPLETED")}</option>
          </select>
        </div>
      </div>
      <div className="adminReviewList">
        {rows.length === 0 && <div className="emptyRow">{t("没有匹配当前筛选的复核记录。", "No reviews match the selected filter.")}</div>}
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
	                    {expanded ? t("收起", "Collapse") : t("阅读全文", "Read full content")}
                  </button>
                )}
                <div className="reviewMeta">
                  <small>{row.sourceId} / {row.regionId} / {row.industryId}</small>
	                  <small>{t("置信度：", "Confidence:")} {row.confidence}</small>
	                  {row.verdict && <StatusBadge value={row.verdict} locale={locale} />}
	                  {row.reviewer && <small>{t("复核人：", "Reviewer:")} {row.reviewer}</small>}
	                </div>
	                {row.reviewStatus === "COMPLETED" && row.reason && (
	                  <small className="reviewReason">{t("原因：", "Reason:")} {row.reason}</small>
	                )}
              </div>
              <div className="adminDecision">
	                <StatusBadge value={row.reviewStatus} locale={locale} />
                {row.reviewStatus !== "COMPLETED" && (
                  <>
	                    <button onClick={() => onVerdict(row.id, "PASS")} disabled={busy} type="button" title={t("通过并发布", "Approve and publish")}>{adminCodeLabel(locale, "PASS")}</button>
	                    <button onClick={() => onVerdict(row.id, "REJECT")} disabled={busy} type="button" title={t("永久拒绝", "Reject permanently")}>{adminCodeLabel(locale, "REJECT")}</button>
	                    <button onClick={() => onVerdict(row.id, "FLAG")} disabled={busy} type="button" title={t("升级二次复核", "Escalate for second review")}>{adminCodeLabel(locale, "FLAG")}</button>
	                    <button onClick={() => onVerdict(row.id, "SUSPICIOUS")} disabled={busy} type="button" title={t("标记为可疑并创建调查工单", "Mark as suspicious, create investigation ticket")}>{t("可疑", "Suspect")}</button>
	                    <button onClick={() => onVerdict(row.id, "COMPLIANCE")} disabled={busy} type="button" title={t("转交法务/合规团队", "Route to legal/compliance team")}>{adminCodeLabel(locale, "COMPLIANCE")}</button>
	                    <button onClick={() => onVerdict(row.id, "PAID_INTEL")} disabled={busy} type="button" title={t("批准为付费情报", "Approve as paid intelligence")}>{t("付费", "Paid")}</button>
	                    <button onClick={() => onVerdict(row.id, "ARCHIVE")} disabled={busy} type="button" title={t("不发布并归档", "Archive without publishing")}>{adminCodeLabel(locale, "ARCHIVE")}</button>
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

function TicketsView({ locale, rows, busy, onTransition, ticketFilter, ticketTypeFilter, onFilterChange, onTypeFilterChange }: {
  locale: AdminLocale;
  rows: AdminTicket[];
  busy: boolean;
  onTransition: (id: number, status: string) => void;
  ticketFilter: string;
  ticketTypeFilter: string;
  onFilterChange: (status: string) => void;
  onTypeFilterChange: (type: string) => void;
}) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  const openCount = rows.filter(t => t.status !== "CLOSED" && t.status !== "ARCHIVED").length;
  const sla = (createTime: string): { hours: number; label: string; cls: string } => {
    if (!createTime) return { hours: 0, label: "-", cls: "" };
    const diff = (Date.now() - new Date(createTime).getTime()) / (1000 * 60 * 60);
    if (diff < 4) return { hours: diff, label: t(`${Math.round(diff * 60)} 分钟前`, `${Math.round(diff * 60)}m ago`), cls: "sla-fresh" };
    if (diff < 24) return { hours: diff, label: t(`${Math.round(diff)} 小时前`, `${Math.round(diff)}h ago`), cls: "sla-warn" };
    return { hours: diff, label: t(`${Math.round(diff / 24)} 天前`, `${Math.round(diff / 24)}d ago`), cls: "sla-overdue" };
  };
  const displayedRows = ticketTypeFilter ? rows.filter(r => r.ticketType === ticketTypeFilter) : rows;
  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead">
        <div className="sectionCopy">
          <h2>{t("工单台账", "Ticket ledger")}</h2>
          <p>{openCount} {t("未结", "open")} / {rows.length} {t("总计", "total")}</p>
        </div>
        <div className="adminFilterBar">
          <span className="adminFilterLabel">{t("状态：", "Status:")}</span>
          <select value={ticketFilter} onChange={e => onFilterChange(e.target.value)} className="adminFilterSelect">
            <option value="">{t("全部", "All")}</option>
            <option value="NEW">{adminCodeLabel(locale, "NEW")}</option>
            <option value="IN_PROGRESS">{adminCodeLabel(locale, "IN_PROGRESS")}</option>
            <option value="WAITING_REVIEW">{adminCodeLabel(locale, "WAITING_REVIEW")}</option>
            <option value="CLOSED">{adminCodeLabel(locale, "CLOSED")}</option>
            <option value="ARCHIVED">{adminCodeLabel(locale, "ARCHIVED")}</option>
          </select>
          <span className="adminFilterLabel">{t("类型：", "Type:")}</span>
          <select value={ticketTypeFilter} onChange={e => onTypeFilterChange(e.target.value)} className="adminFilterSelect">
            <option value="">{t("全部类型", "All types")}</option>
            <option value="review_escalation">{adminCodeLabel(locale, "review_escalation")}</option>
            <option value="suspicious_review">{adminCodeLabel(locale, "suspicious_review")}</option>
            <option value="compliance_review">{adminCodeLabel(locale, "compliance_review")}</option>
            <option value="conflict">{adminCodeLabel(locale, "conflict")}</option>
          </select>
        </div>
      </div>
      <div className="adminTable">
        <div className="adminTableHead ticketHead"><span>{t("严重级别", "Severity")}</span><span>{t("标题", "Title")}</span><span>{t("负责人", "Owner")}</span><span>{t("状态", "Status")}</span><span>SLA</span><span>{t("操作", "Actions")}</span></div>
        {displayedRows.length === 0 && <div className="emptyRow">{t("没有匹配筛选条件的工单。", "No tickets match filters.")}</div>}
        {displayedRows.map((row) => {
          const slaInfo = sla(row.createTime);
          return (
            <div className="adminTableRow ticketRow" key={row.id}>
	              <StatusBadge value={row.severity} locale={locale} />
              <div>
                <strong>{row.title}</strong>
	                <small>{adminCodeLabel(locale, row.ticketType)} / {row.nextAction ?? "-"}</small>
	              </div>
	              <span>{row.owner ?? t("未分配", "Unassigned")}</span>
	              <StatusBadge value={row.status} locale={locale} />
              <span className={`slaCell ${slaInfo.cls}`}>{slaInfo.label}</span>
              <div className="adminRowActions">
	                {row.status === "NEW" && <button onClick={() => onTransition(row.id, "CLAIMED")} disabled={busy} type="button">{t("认领", "Claim")}</button>}
	                {(row.status === "NEW" || row.status === "CLAIMED") && <button onClick={() => onTransition(row.id, "IN_PROGRESS")} disabled={busy} type="button">{t("开始", "Start")}</button>}
	                {row.status === "IN_PROGRESS" && <button onClick={() => onTransition(row.id, "WAITING_REVIEW")} disabled={busy} type="button">{t("复核", "Review")}</button>}
	                {row.status === "WAITING_REVIEW" && <button onClick={() => onTransition(row.id, "CLOSED")} disabled={busy} type="button">{t("关闭", "Close")}</button>}
	                {row.status === "CLOSED" && <button onClick={() => onTransition(row.id, "ARCHIVED")} disabled={busy} type="button">{t("归档", "Archive")}</button>}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function HumanView({ locale, rows, busy, draft, setDraft, onCreate, onReview, humanFilter, onFilterChange }: {
  locale: AdminLocale;
  rows: AdminHumanIntelligence[];
  busy: boolean;
  draft: HumanDraft;
  setDraft: (value: HumanDraft) => void;
  onCreate: () => void;
  onReview: (id: number, verdict: "PASS" | "REJECT") => void;
  humanFilter: string;
  onFilterChange: (status: string) => void;
}) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  const pending = rows.filter(r => r.status === "PENDING_REVIEW").length;
  const approved = rows.filter(r => r.status === "APPROVED").length;
  const entitlementLabel = (e: string) => {
    switch (e) {
      case "FREE": return t("免费（不可见）", "Free (not visible)");
      case "SEED_PAID": return t("种子付费（可见）", "Seed paid (visible)");
      case "PAID": return t("付费（高级内容）", "Paid (premium)");
      case "INTERNAL": return t("仅内部可见", "Internal only");
      case "LEGAL_FREEZE": return t("法务冻结（阻断）", "Legal freeze (blocked)");
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
            <h2>{t("人工情报录入", "Human intelligence entry")}</h2>
            <p>{t("记录本地洞察。面向用户使用前会进入复核。", "Record local insights. Enters review before user-facing use.")}</p>
          </div>
          <button className="primary" onClick={onCreate} disabled={busy} type="button">
            <Plus size={16} />
            {t("提交", "Submit")}
          </button>
        </div>
        <div className="adminFormGrid">
          <label>{t("城市", "City")}<input value={draft.city} onChange={(event) => setDraft({ ...draft, city: event.target.value })} /></label>
          <label>{t("行业 ID", "Industry ID")}<input value={draft.industryId} onChange={(event) => setDraft({ ...draft, industryId: event.target.value })} title={adminCodeWithRaw(locale, draft.industryId)} /></label>
          <label>{t("链路节点", "Chain node")}<input value={draft.linkId} onChange={(event) => setDraft({ ...draft, linkId: event.target.value })} title={adminCodeWithRaw(locale, draft.linkId)} /></label>
          <label>{t("区域 ID", "Region ID")}<input value={draft.regionId} onChange={(event) => setDraft({ ...draft, regionId: event.target.value })} title={adminCodeWithRaw(locale, draft.regionId)} /></label>
          <label>{t("来源类型", "Source type")}
            <select value={draft.sourceType} onChange={(event) => setDraft({ ...draft, sourceType: event.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
              <option value="local_visit">{adminCodeLabel(locale, "local_visit")}</option>
              <option value="partner_report">{adminCodeLabel(locale, "partner_report")}</option>
              <option value="user_submission">{adminCodeLabel(locale, "user_submission")}</option>
              <option value="field_survey">{adminCodeLabel(locale, "field_survey")}</option>
              <option value="expert_interview">{adminCodeLabel(locale, "expert_interview")}</option>
            </select>
          </label>
          <label>{t("采集人", "Collector")}<input value={draft.collector} onChange={(event) => setDraft({ ...draft, collector: event.target.value })} title={adminCodeWithRaw(locale, draft.collector)} /></label>
          <label>{t("来源 ID", "Source ID")}<input value={draft.sourceId} onChange={(event) => setDraft({ ...draft, sourceId: event.target.value })} title={adminCodeWithRaw(locale, draft.sourceId)} /></label>
          <label>{t("事件时间", "Event time")}<input value={draft.eventTime} onChange={(event) => setDraft({ ...draft, eventTime: event.target.value })} placeholder={t("例如 2026-07", "e.g. 2026-07")} /></label>
          <label>{t("置信度", "Confidence")}
            <div className="confidenceRow">
              <input type="range" min="0" max="1" step="0.01" value={draft.confidence} onChange={(event) => setDraft({ ...draft, confidence: Number(event.target.value) })} className="flex1" />
              <span className="adminBadge">{draft.confidence.toFixed(2)}</span>
            </div>
          </label>
          <label>{t("权益", "Entitlement")}
            <select value={draft.entitlement} onChange={(event) => setDraft({ ...draft, entitlement: event.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
              <option value="FREE">{t("免费（免费用户不可见）", "Free (not visible to free users)")}</option>
              <option value="SEED_PAID">{t("种子付费（可见）", "Seed paid (visible)")}</option>
              <option value="PAID">{t("付费（高级内容）", "Paid (premium content)")}</option>
              <option value="INTERNAL">{t("仅内部", "Internal only")}</option>
              <option value="LEGAL_FREEZE">{t("法务冻结（阻断）", "Legal freeze (blocked)")}</option>
            </select>
          </label>
          <label className="wide">{t("内容", "Content")}<input value={draft.content} onChange={(event) => setDraft({ ...draft, content: event.target.value })} /></label>
        </div>
      </section>
      <section className="workspaceCard adminPanel">
        <div className="sectionHead compact">
          <div className="sectionCopy">
            <h2>{t("队列", "Queue")}</h2>
            <p>{t("通过的记录会提升为情报，用于检索和报告。", "Approved records are promoted into intelligence for retrieval/report use.")}</p>
          </div>
          <div className="adminFilterBar">
            <select value={humanFilter} onChange={e => onFilterChange(e.target.value)} className="adminFilterSelect">
              <option value="">{t("全部", "All")}</option>
              <option value="PENDING_REVIEW">{adminCodeLabel(locale, "PENDING_REVIEW")}</option>
              <option value="APPROVED">{adminCodeLabel(locale, "APPROVED")}</option>
              <option value="REJECTED">{adminCodeLabel(locale, "REJECTED")}</option>
            </select>
          </div>
        </div>
        <div className="table humanQueueTable">
          {rows.length === 0 && <div className="emptyRow">{t("暂无人工情报记录。", "No human intelligence records yet.")}</div>}
          {rows.map((row) => (
            <div className={`row humanQueueRow ${row.status === "PENDING_REVIEW" ? "human-pending" : ""}`} key={row.id}>
              <div>
                <strong>{row.city} / {row.linkId}</strong>
                <div className="humanQueueMeta">
	                  <StatusBadge value={row.status} locale={locale} />
	                  <span className={`entBadge ${entitlementClass(row.entitlement)}`} title={entitlementLabel(row.entitlement)}>{adminCodeLabel(locale, row.entitlement)}</span>
	                  <small>{t("置信度：", "Conf:")} {row.confidence}</small>
	                  <small>{adminCodeLabel(locale, row.sourceType)} / {adminCodeLabel(locale, row.collector)}</small>
                </div>
              </div>
              <small className="humanQueueContent">{row.content}</small>
              {row.status === "PENDING_REVIEW" && (
                <div className="adminRowActions wideActions">
	                  <button onClick={() => onReview(row.id, "PASS")} disabled={busy} type="button">{t("通过", "Approve")}</button>
	                  <button onClick={() => onReview(row.id, "REJECT")} disabled={busy} type="button">{t("拒绝", "Reject")}</button>
	                </div>
	              )}
	              {row.reviewer && <small className="humanReviewer">{t("复核人", "Reviewed by")} {row.reviewer}{row.reviewNotes ? ` - ${row.reviewNotes}` : ""}</small>}
            </div>
          ))}
        </div>
	        {pending > 0 && <div className="monitoringSummary"><small>{pending} {t("待复核", "pending review")}, {approved} {t("已通过", "approved")}</small></div>}
      </section>
    </div>
  );
}

function RiskRulesView({ locale, rows, busy, onToggle, onSave, onRefresh }: {
  locale: AdminLocale;
  rows: RiskRule[];
  busy: boolean;
  onToggle: (id: number) => void;
  onSave: (payload: RiskRuleUpsertInput) => void;
  onRefresh: () => void;
}) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  const TABS: { key: string; label: string }[] = [
    { key: "rumor_detection", label: adminCodeLabel(locale, "rumor_detection") },
    { key: "conflict_judgment", label: adminCodeLabel(locale, "conflict_judgment") },
    { key: "gray_content", label: adminCodeLabel(locale, "gray_content") },
    { key: "ai_self_check", label: adminCodeLabel(locale, "ai_self_check") },
    { key: "api_abuse", label: adminCodeLabel(locale, "api_abuse") },
    { key: "paid_protection", label: adminCodeLabel(locale, "paid_protection") },
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
          <h2>{t("风控规则", "Risk control rules")}</h2>
          <p>{t("配置六类风险的检测阈值、启停状态和生效模式。", "Configure detection thresholds, enable/disable rules, and set activation modes across six risk categories.")}</p>
        </div>
        <button className="ghost" onClick={onRefresh} disabled={busy} type="button"><RefreshCw size={14} /> {t("刷新", "Refresh")}</button>
      </div>
      <div className="riskTabs">
        {TABS.map(tab => (
          <button key={tab.key} className={`riskTab ${activeTab === tab.key ? "active" : ""}`} onClick={() => setActiveTab(tab.key)} type="button">
            {tab.label}
          </button>
        ))}
      </div>
      <div className="riskRuleList">
        {filtered.length === 0 && <div className="emptyRow">{t("该类别暂无已配置规则。首次启动时种子数据会创建默认规则。", "No rules configured for this category. Seed data creates default rules on first startup.")}</div>}
        {filtered.map(rule => (
          <div className={`riskRuleItem ${rule.enabled ? "" : "rule-disabled"}`} key={rule.id}>
            {editId === rule.id ? (
              <div className="riskRuleEdit">
                <div className="adminFormGrid">
	                  <label>{t("名称", "Name")}<input value={editForm.name} onChange={e => setEditForm({ ...editForm, name: e.target.value })} /></label>
	                  <label>{t("风险级别", "Risk level")}
	                    <select value={editForm.riskLevel} onChange={e => setEditForm({ ...editForm, riskLevel: e.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
	                      <option value="LOW">{adminCodeLabel(locale, "LOW")}</option><option value="MEDIUM">{adminCodeLabel(locale, "MEDIUM")}</option><option value="HIGH">{adminCodeLabel(locale, "HIGH")}</option><option value="CRITICAL">{adminCodeLabel(locale, "CRITICAL")}</option>
	                    </select>
	                  </label>
	                  <label>{t("变更模式", "Change mode")}
	                    <select value={editForm.changeMode} onChange={e => setEditForm({ ...editForm, changeMode: e.target.value })} className="adminFilterSelect" style={{ width: "100%" }}>
	                      <option value="IMMEDIATE">{adminCodeLabel(locale, "IMMEDIATE")}</option><option value="GRAY">{adminCodeLabel(locale, "GRAY")}</option><option value="SCHEDULED">{adminCodeLabel(locale, "SCHEDULED")}</option>
	                    </select>
	                  </label>
	                  <label>{t("阈值 (0-1)", "Threshold (0-1)")}<input value={editForm.thresholdValue ?? ""} onChange={e => setEditForm({ ...editForm, thresholdValue: e.target.value ? Number(e.target.value) : null })} type="number" min="0" max="1" step="0.01" /></label>
	                  <label className="wide">{t("描述", "Description")}<textarea value={editForm.description} onChange={e => setEditForm({ ...editForm, description: e.target.value })} rows={3} /></label>
	                  <label className="wide">{t("范围 JSON", "Scope JSON")}<textarea value={editForm.scopeJson ?? ""} onChange={e => setEditForm({ ...editForm, scopeJson: e.target.value })} rows={3} /></label>
                </div>
                <div className="adminRowActions adminFormActions">
	                  <button className="primary" onClick={handleSave} disabled={busy} type="button">{t("保存", "Save")}</button>
	                  <button className="ghost" onClick={cancelEdit} type="button">{t("取消", "Cancel")}</button>
                </div>
              </div>
            ) : (
              <div className="riskRuleView">
                <div className="riskRuleInfo">
                  <div className="riskRuleHead">
                    <strong>{rule.name}</strong>
	                    <span className={`adminBadge status-${rule.riskLevel.toLowerCase() === "critical" ? "p0" : rule.riskLevel.toLowerCase() === "high" ? "open" : "healthy"}`}>{adminCodeLabel(locale, rule.riskLevel)}</span>
	                    <span className={`adminBadge ${rule.enabled ? "status-healthy" : "status-rejected"}`}>{adminCodeLabel(locale, rule.enabled ? "ENABLED" : "DISABLED")}</span>
	                    <small>v{rule.version} / {adminCodeLabel(locale, rule.changeMode)}</small>
                  </div>
                  <p>{rule.description}</p>
                  <div className="riskRuleMeta">
	                    {rule.thresholdValue != null && <small>{t("阈值：", "Threshold:")} {rule.thresholdValue}</small>}
	                    {rule.scopeJson && <small>{t("范围：", "Scope:")} {rule.scopeJson}</small>}
                  </div>
                </div>
                <div className="adminRowActions">
	                  <button onClick={() => startEdit(rule)} type="button">{t("编辑", "Edit")}</button>
	                  <button onClick={() => onToggle(rule.id)} disabled={busy} type="button">{rule.enabled ? t("停用", "Disable") : t("启用", "Enable")}</button>
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
      <PanelHead title={title} detail={`${rows.length}`} />
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

function StatusBadge({ value, locale = "zh-CN" }: { value: string; locale?: AdminLocale }) {
  return <span className={`adminBadge status-${value.toLowerCase().replaceAll("_", "-")}`}>{adminCodeLabel(locale, value)}</span>;
}

function formatTime(value: string) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}
