"use client";

import {
  Archive,
  Bot,
  CheckCircle2,
  ChevronDown,
  Database,
  Eye,
  FilePlus2,
  FileText,
  Gauge,
  Languages,
  LogIn,
  LogOut,
  LockKeyhole,
  Search,
  Send,
  ShieldCheck,
  UserRound
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import Markdown from "../lib/markdown";
import {
  AuthExpiredError,
  createConversation,
  fetchConversations,
  fetchDiagnosisReport,
  fetchMessages,
  fetchOpsMetrics,
  fetchPaidIntelligence,
  login,
  streamDiagnosis,
  WorkerError,
  type Conversation,
  type ConversationMessage,
  type Diagnosis,
  type DiagnosisReport,
  type LoginProfile,
  type OpsMetrics,
  type PaidIntelligence,
  type Source
} from "../lib/api-client";

type Locale = "zh-CN" | "en";
type WorkspaceSection = "diagnosis" | "intelligence" | "users" | "archive";

const PROFILE_STORAGE_KEY = "bizsage.web.profile";

const messages: Record<Locale, {
  brandSubtitle: string;
  loginTitle: string;
  loginIntro: string;
  username: string;
  password: string;
  login: string;
  loggingIn: string;
  loginNotice: string;
  loginSuccess: string;
  loginFailed: string;
  sessionExpired: string;
  languageToggle: string;
  navDiagnosis: string;
  navIntelligence: string;
  navUsers: string;
  navArchive: string;
  identity: string;
  logout: string;
  workspaceTitle: string;
  health: string;
  evidenceLine: string;
  newConversation: string;
  diagnosisConversation: string;
  defaultQuestion: string;
  busyDiagnosis: string;
  sendDiagnosis: string;
  diagnosisCreated: string;
  diagnosisFailed: string;
  loginRequired: string;
  workerUnreachable: string;
  llmNotConfigured: string;
  workerTimeout: string;
  workerError: string;
  needsReview: string;
  insufficientEvidence: string;
  metricsTitle: string;
  grayCohort: string;
  apiCacheTarget: string;
  crawlerRtoTarget: string;
  dbRecoveryTarget: string;
  paidTitle: string;
  paidEmptyTitle: string;
  paidEmptyDetail: string;
  reportTitle: string;
  reportMetadata: string;
  reportEmptyTitle: string;
  reportEmptyDetail: string;
  source: string;
  sourceUrl: string;
  confidence: string;
  score: string;
  close: string;
  generateReport: string;
  generatingReport: string;
  selectConversation: string;
  noConversations: string;
  newConversationTitle: string;
}> = {
  "zh-CN": {
    brandSubtitle: "V2 灰度工作台",
    loginTitle: "登录 BizSage",
    loginIntro: "未登录用户只能访问独立登录界面。登录后进入经营诊断与灰度运营工作台。",
    username: "账号",
    password: "密码",
    login: "登录",
    loggingIn: "登录中...",
    loginNotice: "请登录 API 后开始诊断。",
    loginSuccess: "已连接 API。",
    loginFailed: "登录失败，请检查账号和密码。",
    sessionExpired: "登录已失效，请重新登录。",
    languageToggle: "English",
    navDiagnosis: "诊断",
    navIntelligence: "情报",
    navUsers: "用户",
    navArchive: "归档",
    identity: "身份",
    logout: "退出登录",
    workspaceTitle: "经营诊断工作台",
    health: "V2 M0 已锁定",
    evidenceLine: "先看证据，再给建议。",
    newConversation: "新建",
    diagnosisConversation: "诊断会话",
    defaultQuestion: "餐饮门店现金流怎么诊断？",
    busyDiagnosis: "正在连接 API 并生成诊断...",
    sendDiagnosis: "发送诊断",
    diagnosisCreated: "诊断已生成。",
    diagnosisFailed: "诊断失败。",
    loginRequired: "请先登录。",
    workerUnreachable: "诊断服务暂时不可用，请稍后重试。",
    llmNotConfigured: "AI 引擎未配置，请联系管理员。",
    workerTimeout: "诊断服务响应超时，请稍后重试。",
    workerError: "诊断服务发生错误。",
    needsReview: "需复核",
    insufficientEvidence: "证据不足",
    metricsTitle: "V2 灰度指标",
    grayCohort: "灰度批次",
    apiCacheTarget: "API 缓存目标",
    crawlerRtoTarget: "Crawler RTO 目标",
    dbRecoveryTarget: "数据库恢复目标",
    paidTitle: "付费情报",
    paidEmptyTitle: "无可见付费情报",
    paidEmptyDetail: "免费用户或当前账号无匹配权益时不会显示付费数据。",
    reportTitle: "诊断报告",
    reportMetadata: "报告元数据",
    reportEmptyTitle: "暂无报告",
    reportEmptyDetail: "提交诊断后展示报告元数据。",
    source: "来源",
    sourceUrl: "地址",
    confidence: "置信度",
    score: "检索分",
    close: "关闭",
    generateReport: "生成诊断报告",
    generatingReport: "正在生成报告...",
    selectConversation: "选择会话",
    noConversations: "暂无会话",
    newConversationTitle: "新诊断会话"
  },
  en: {
    brandSubtitle: "V2 gray workspace",
    loginTitle: "Sign in to BizSage",
    loginIntro: "Signed-out users only see this login screen. After sign-in, the diagnosis and gray-release workspace opens.",
    username: "Username",
    password: "Password",
    login: "Sign in",
    loggingIn: "Signing in...",
    loginNotice: "Sign in to the API to start diagnosis.",
    loginSuccess: "Connected to API.",
    loginFailed: "Sign-in failed. Check the username and password.",
    sessionExpired: "Your session expired. Please sign in again.",
    languageToggle: "中文",
    navDiagnosis: "Diagnosis",
    navIntelligence: "Intelligence",
    navUsers: "Users",
    navArchive: "Archive",
    identity: "Identity",
    logout: "Sign out",
    workspaceTitle: "Operating Diagnosis Workspace",
    health: "V2 M0 locked",
    evidenceLine: "Evidence first, answer second.",
    newConversation: "New",
    diagnosisConversation: "Diagnosis Chat",
    defaultQuestion: "How should a restaurant diagnose cash flow?",
    busyDiagnosis: "Connecting to the API and generating diagnosis...",
    sendDiagnosis: "Send diagnosis",
    diagnosisCreated: "Diagnosis generated.",
    diagnosisFailed: "Diagnosis failed.",
    loginRequired: "Please sign in first.",
    workerUnreachable: "Diagnosis service is currently unavailable. Please try again later.",
    llmNotConfigured: "AI engine is not configured. Please contact the administrator.",
    workerTimeout: "Diagnosis service timed out. Please try again later.",
    workerError: "Diagnosis service encountered an error.",
    needsReview: "Needs Review",
    insufficientEvidence: "Insufficient Evidence",
    metricsTitle: "V2 Gray Metrics",
    grayCohort: "Gray cohort",
    apiCacheTarget: "API cache target",
    crawlerRtoTarget: "Crawler RTO target",
    dbRecoveryTarget: "DB recovery target",
    paidTitle: "Paid Intelligence",
    paidEmptyTitle: "No visible paid intelligence",
    paidEmptyDetail: "Free users or accounts without matching entitlement do not see paid data.",
    reportTitle: "Diagnosis Report",
    reportMetadata: "Report metadata",
    reportEmptyTitle: "No report yet",
    reportEmptyDetail: "Submit a diagnosis to show report metadata.",
    source: "Source",
    sourceUrl: "URL",
    confidence: "Confidence",
    score: "Retrieval score",
    close: "Close",
    generateReport: "Generate Report",
    generatingReport: "Generating report...",
    selectConversation: "Select conversation",
    noConversations: "No conversations",
    newConversationTitle: "New Diagnosis Chat"
  }
};

export default function Home() {
  const [locale, setLocale] = useState<Locale>("zh-CN");
  const [username, setUsername] = useState("operator");
  const [password, setPassword] = useState("password");
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [activeSection, setActiveSection] = useState<WorkspaceSection>("diagnosis");
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [messageHistory, setMessageHistory] = useState<ConversationMessage[]>([]);
  const [message, setMessage] = useState(messages["zh-CN"].defaultQuestion);
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null);
  const [report, setReport] = useState<DiagnosisReport | null>(null);
  const [reportBusy, setReportBusy] = useState(false);
  const [paidRows, setPaidRows] = useState<PaidIntelligence[]>([]);
  const [metrics, setMetrics] = useState<OpsMetrics | null>(null);
  const [selectedSource, setSelectedSource] = useState<Source | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(messages["zh-CN"].loginNotice);
  const [convDropdownOpen, setConvDropdownOpen] = useState(false);
  const convDropdownRef = useRef<HTMLDivElement>(null);

  const t = messages[locale];

  useEffect(() => {
    try {
      const rawProfile = window.localStorage.getItem(PROFILE_STORAGE_KEY);
      if (!rawProfile) return;
      const savedProfile = JSON.parse(rawProfile) as LoginProfile;
      setProfile(savedProfile);
    } catch {
      window.localStorage.removeItem(PROFILE_STORAGE_KEY);
    }
  }, []);

  useEffect(() => {
    if (!profile) {
      window.localStorage.removeItem(PROFILE_STORAGE_KEY);
      setPaidRows([]);
      setMetrics(null);
      setConversations([]);
      setMessageHistory([]);
      setSelectedConversationId(null);
      return;
    }

    window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));

    let cancelled = false;
    Promise.allSettled([
      fetchPaidIntelligence(profile.token),
      fetchOpsMetrics(profile.token),
      fetchConversations(profile.token)
    ]).then(([nextPaidRows, nextMetrics, nextConversations]) => {
      if (cancelled) return;
      const failures = [nextPaidRows, nextMetrics, nextConversations].filter(
        (result): result is PromiseRejectedResult => result.status === "rejected"
      );
      if (failures.some((result) => result.reason instanceof AuthExpiredError)) {
        handleSessionExpired();
        return;
      }
      setPaidRows(nextPaidRows.status === "fulfilled" ? nextPaidRows.value : []);
      setMetrics(nextMetrics.status === "fulfilled" ? nextMetrics.value : null);
      const convs = nextConversations.status === "fulfilled" ? nextConversations.value : [];
      setConversations(convs);
      if (convs.length > 0 && !selectedConversationId) {
        setSelectedConversationId(convs[0].id);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [profile, selectedConversationId, t.sessionExpired]);

  // Load message history when a conversation is selected
  useEffect(() => {
    if (!profile || !selectedConversationId) {
      setMessageHistory([]);
      return;
    }

    let cancelled = false;
    fetchMessages(profile.token, selectedConversationId)
      .then((msgs) => {
        if (!cancelled) setMessageHistory(msgs);
      })
      .catch((error) => {
        if (error instanceof AuthExpiredError) {
          handleSessionExpired();
          return;
        }
        if (!cancelled) setMessageHistory([]);
      });

    return () => {
      cancelled = true;
    };
  }, [profile, selectedConversationId, t.sessionExpired]);

  // Close conversation dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (convDropdownRef.current && !convDropdownRef.current.contains(event.target as Node)) {
        setConvDropdownOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const status = useMemo(() => {
    if (!profile) return t.loginNotice;
    return `${profile.username} / ${profile.membershipLevel} / ${profile.regionId} / ${profile.industryId}`;
  }, [profile, t.loginNotice]);

  function toggleLocale() {
    setLocale(locale === "zh-CN" ? "en" : "zh-CN");
  }

  async function handleLogin() {
    setBusy(true);
    try {
      const nextProfile = await login(username, password);
      setProfile(nextProfile);
      setActiveSection("diagnosis");
      setNotice(t.loginSuccess);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t.loginFailed);
    } finally {
      setBusy(false);
    }
  }

  function handleLogout() {
    window.localStorage.removeItem(PROFILE_STORAGE_KEY);
    setProfile(null);
    setActiveSection("diagnosis");
    setConversations([]);
    setMessageHistory([]);
    setDiagnosis(null);
    setReport(null);
    setSelectedConversationId(null);
    setPaidRows([]);
    setMetrics(null);
    setSelectedSource(null);
    setConvDropdownOpen(false);
    setReportBusy(false);
    setNotice(t.loginNotice);
  }

  function handleSessionExpired() {
    window.localStorage.removeItem(PROFILE_STORAGE_KEY);
    setProfile(null);
    setActiveSection("diagnosis");
    setConversations([]);
    setMessageHistory([]);
    setDiagnosis(null);
    setReport(null);
    setSelectedConversationId(null);
    setPaidRows([]);
    setMetrics(null);
    setSelectedSource(null);
    setConvDropdownOpen(false);
    setReportBusy(false);
    setNotice(t.sessionExpired);
  }

  async function submitDiagnosis() {
    if (!profile) {
      setNotice(t.loginRequired);
      return;
    }
    setBusy(true);
    setDiagnosis(null);
    setReport(null);
    try {
      const created = selectedConversationId ? null : await createConversation(profile.token, t.newConversationTitle);
      const conversationId = selectedConversationId ?? created!.id;
      if (created) {
        setSelectedConversationId(conversationId);
        setConversations((prev) => [created, ...prev]);
      }
      const nextDiagnosis = await streamDiagnosis(profile.token, conversationId, message);
      setDiagnosis(nextDiagnosis);
      // Reload messages to include the new diagnosis in history
      try {
        const updatedMessages = await fetchMessages(profile.token, conversationId);
        setMessageHistory(updatedMessages);
      } catch (error) {
        if (error instanceof AuthExpiredError) {
          handleSessionExpired();
          return;
        }
        // Non-fatal if message reload fails
      }
      setNotice(t.diagnosisCreated);
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      if (error instanceof WorkerError) {
        // Map specific worker error codes to localized messages
        switch (error.code) {
          case "WORKER_UNREACHABLE":
            setNotice(t.workerUnreachable);
            break;
          case "LLM_NOT_CONFIGURED":
            setNotice(t.llmNotConfigured);
            break;
          case "WORKER_TIMEOUT":
            setNotice(t.workerTimeout);
            break;
          default:
            setNotice(error.message || t.workerError);
        }
      } else {
        setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
      }
    } finally {
      setBusy(false);
    }
  }

  async function generateReport() {
    if (!profile) return;
    setReportBusy(true);
    try {
      const nextReport = await fetchDiagnosisReport(profile.token, message);
      setReport(nextReport);
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      if (error instanceof WorkerError) {
        setNotice(error.message);
      } else {
        setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
      }
    } finally {
      setReportBusy(false);
    }
  }

  function handleSelectConversation(id: number) {
    setSelectedConversationId(id);
    setDiagnosis(null);
    setReport(null);
    setConvDropdownOpen(false);
  }

  async function handleNewConversation() {
    if (!profile) return;
    try {
      const created = await createConversation(profile.token, t.newConversationTitle);
      setConversations((prev) => [created, ...prev]);
      setSelectedConversationId(created.id);
      setDiagnosis(null);
      setReport(null);
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
    }
  }

  if (!profile) {
    return (
      <main className="loginScreen">
        <button className="languageButton loginLanguage" onClick={toggleLocale}>
          <Languages size={16} />
          {t.languageToggle}
        </button>
        <section className="loginCard">
          <div className="brand loginBrand">
            <span className="mark">BS</span>
            <div>
              <strong>BizSage</strong>
              <small>{t.brandSubtitle}</small>
            </div>
          </div>
          <div className="loginCopy">
            <h1>{t.loginTitle}</h1>
            <p>{t.loginIntro}</p>
          </div>
          <div className="loginForm">
            <label>
              {t.username}
              <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
            </label>
            <label>
              {t.password}
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                autoComplete="current-password"
              />
            </label>
            <button className="primary" onClick={handleLogin} disabled={busy}>
              <LogIn size={16} />
              {busy ? t.loggingIn : t.login}
            </button>
            <small>{notice}</small>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="workspace">
      <aside className="rail">
        <div className="brand">
          <span className="mark">BS</span>
          <div>
            <strong>BizSage</strong>
            <small>{t.brandSubtitle}</small>
          </div>
        </div>
        <nav className="nav">
          <button
            className={`navItem ${activeSection === "diagnosis" ? "active" : ""}`}
            title={t.diagnosisConversation}
            onClick={() => setActiveSection("diagnosis")}
          ><Bot size={18} />{t.navDiagnosis}</button>
          <button
            className={`navItem ${activeSection === "intelligence" ? "active" : ""}`}
            title={t.navIntelligence}
            onClick={() => setActiveSection("intelligence")}
          ><Database size={18} />{t.navIntelligence}</button>
          <button
            className={`navItem ${activeSection === "users" ? "active" : ""}`}
            title={t.navUsers}
            onClick={() => setActiveSection("users")}
          ><UserRound size={18} />{t.navUsers}</button>
          <button
            className={`navItem ${activeSection === "archive" ? "active" : ""}`}
            title={t.navArchive}
            onClick={() => setActiveSection("archive")}
          ><Archive size={18} />{t.navArchive}</button>
        </nav>
        <section className="identityPanel" aria-label={t.identity}>
          <div className="panelTitle"><ShieldCheck size={16} />{t.identity}</div>
          <strong>{profile.username}</strong>
          <small>{profile.role} / {profile.membershipLevel}</small>
          <small>{profile.regionId} / {profile.industryId}</small>
        </section>
      </aside>

      <section className="main">
        <header className="topbar">
          <div>
            <h1>{t.workspaceTitle}</h1>
            <p>{status}</p>
          </div>
          <div className="topActions">
            <button className="languageButton" onClick={toggleLocale}>
              <Languages size={16} />
              {t.languageToggle}
            </button>
            <button className="ghost" onClick={handleLogout}>
              <LogOut size={16} />
              {t.logout}
            </button>
            <div className="health"><span className="healthDot" /><CheckCircle2 size={16} />{t.health}</div>
          </div>
        </header>

        {activeSection === "diagnosis" && (
          <div className="grid">
            <section className="dialogue">
              <div className="sectionHead">
                <div className="convSelector" ref={convDropdownRef}>
                  <button
                    className="convSelectBtn"
                    onClick={() => setConvDropdownOpen(!convDropdownOpen)}
                  >
                    <h2>
                      {selectedConversationId
                        ? (conversations.find(c => c.id === selectedConversationId)?.title ?? t.diagnosisConversation)
                        : (conversations.length > 0 ? t.selectConversation : t.diagnosisConversation)}
                    </h2>
                    <ChevronDown size={16} className={`chevron ${convDropdownOpen ? "chevronOpen" : ""}`} />
                  </button>
                  {convDropdownOpen && (
                    <div className="convDropdown">
                      {conversations.length === 0 && (
                        <div className="convDropdownEmpty">{t.noConversations}</div>
                      )}
                      {conversations.map((conv) => (
                        <button
                          key={conv.id}
                          className={`convDropdownItem ${conv.id === selectedConversationId ? "active" : ""}`}
                          onClick={() => handleSelectConversation(conv.id)}
                        >
                          <span className="convTitle">{conv.title}</span>
                          <span className="convStatus">{conv.status}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <button className="ghost" onClick={handleNewConversation}>
                  <FilePlus2 size={16} />{t.newConversation}
                </button>
              </div>
              <div className="messages">
                {/* History messages */}
                {messageHistory.map((msg) => (
                  <div key={msg.id} className={`bubble ${msg.sender === "USER" ? "user" : "agent"}`}>
                    {msg.sender === "ASSISTANT" ? (
                      <Markdown content={msg.content} />
                    ) : (
                      <p>{msg.content}</p>
                    )}
                    {msg.confidence && (
                      <div className="meta">
                        <span>{msg.confidence}</span>
                        {msg.selfCheckStatus && (
                          <span className={msg.selfCheckStatus === "NEEDS_REVIEW" ? "flag needsReview" : msg.selfCheckStatus === "INSUFFICIENT_EVIDENCE" ? "flag insufficientEvidence" : ""}>
                            {msg.selfCheckStatus === "NEEDS_REVIEW"
                              ? `⚠ ${t.needsReview}`
                              : msg.selfCheckStatus === "INSUFFICIENT_EVIDENCE"
                              ? `ℹ ${t.insufficientEvidence}`
                              : msg.selfCheckStatus}
                          </span>
                        )}
                        <span>{msg.timeliness}</span>
                      </div>
                    )}
                  </div>
                ))}
                {/* Current input (preview) */}
                {!busy && !diagnosis && messageHistory.length === 0 && (
                  <div className="bubble user">{message}</div>
                )}
                {busy && <div className="bubble agent muted"><span className="loadingDots"><span /><span /><span /></span>{t.busyDiagnosis}</div>}
                {diagnosis && (
                  <div className={`bubble agent${diagnosis.selfCheckStatus && diagnosis.selfCheckStatus !== "PASSED" ? ` status-${diagnosis.selfCheckStatus.toLowerCase()}` : ""}`}>
                    <Markdown content={diagnosis.answer} />
                    <div className="meta">
                      <span>{diagnosis.confidence}</span>
                      <span className={diagnosis.selfCheckStatus === "NEEDS_REVIEW" ? "flag needsReview" : diagnosis.selfCheckStatus === "INSUFFICIENT_EVIDENCE" ? "flag insufficientEvidence" : ""}>
                        {diagnosis.selfCheckStatus === "NEEDS_REVIEW"
                          ? `⚠ ${t.needsReview}`
                          : diagnosis.selfCheckStatus === "INSUFFICIENT_EVIDENCE"
                          ? `ℹ ${t.insufficientEvidence}`
                          : diagnosis.selfCheckStatus ?? "PASSED"}
                      </span>
                      <span>{diagnosis.timeliness}</span>
                    </div>
                    {diagnosis.sources.length > 0 && (
                      <div className="sourceLine">
                        {diagnosis.sources.map((source) => (
                          <button key={source.id} onClick={() => setSelectedSource(source)}><Eye size={14} />{source.title}</button>
                        ))}
                      </div>
                    )}
                    <small>{diagnosis.disclaimer}</small>
                  </div>
                )}
              </div>
              <div className="composer">
                <Search size={18} />
                <input value={message} onChange={(event) => setMessage(event.target.value)} />
                <button className="primary icon" onClick={submitDiagnosis} disabled={busy} title={t.sendDiagnosis}><Send size={18} /></button>
              </div>
            </section>

            <aside className="ops">
              <MetricsPanel metrics={metrics} t={t} />
              <PaidIntelligencePanel paidRows={paidRows} t={t} />
              <ReportPanel report={report} diagnosis={diagnosis} reportBusy={reportBusy} onGenerateReport={generateReport} t={t} />
            </aside>
          </div>
        )}

        {activeSection === "intelligence" && (
          <div className="grid">
            <section className="dialogue">
              <div className="sectionHead">
                <div><h2>{t.navIntelligence}</h2><p>{t.evidenceLine}</p></div>
              </div>
              <div className="table">
                {paidRows.length === 0 && <EmptyRow title={t.paidEmptyTitle} detail={t.paidEmptyDetail} />}
                {paidRows.map((row) => (
                  <div className="row" key={row.id}>
                    <strong>{row.title}</strong>
                    <span>{row.status}</span>
                    <small>{row.entitlement} / {row.regionId} / {row.industryId}</small>
                  </div>
                ))}
              </div>
            </section>

            <aside className="ops">
              <MetricsPanel metrics={metrics} t={t} />
              <ReportPanel report={report} diagnosis={diagnosis} reportBusy={reportBusy} onGenerateReport={generateReport} t={t} />
            </aside>
          </div>
        )}

        {activeSection === "users" && (
          <div className="grid">
            <section className="dialogue">
              <div className="sectionHead">
                <div><h2>{t.navUsers}</h2><p>{status}</p></div>
              </div>
              <div className="table">
                <div className="row">
                  <strong>{profile.username}</strong>
                  <span>{profile.role}</span>
                  <small>{profile.membershipLevel} / {profile.regionId} / {profile.industryId}</small>
                </div>
              </div>
            </section>

            <aside className="ops">
              <MetricsPanel metrics={metrics} t={t} />
              <PaidIntelligencePanel paidRows={paidRows} t={t} />
            </aside>
          </div>
        )}

        {activeSection === "archive" && (
          <div className="grid">
            <section className="dialogue">
              <div className="sectionHead">
                <div><h2>{t.navArchive}</h2><p>{t.reportMetadata}</p></div>
              </div>
              <div className="table">
                {report ? (
                  <div className="row">
                    <strong>{report.format} {t.reportMetadata}</strong>
                    <span>{report.selfCheckStatus}</span>
                    <small>{report.summary}</small>
                  </div>
                ) : (
                  <EmptyRow title={t.reportEmptyTitle} detail={t.reportEmptyDetail} />
                )}
              </div>
            </section>

            <aside className="ops">
              <MetricsPanel metrics={metrics} t={t} />
              <PaidIntelligencePanel paidRows={paidRows} t={t} />
            </aside>
          </div>
        )}
      </section>

      {selectedSource && (
        <div className="modalBackdrop" onClick={() => setSelectedSource(null)}>
          <section className="modal" onClick={(event) => event.stopPropagation()}>
            <h2>{selectedSource.title}</h2>
            <dl>
              <dt>{t.source}</dt><dd>{selectedSource.sourceId}</dd>
              <dt>{t.sourceUrl}</dt><dd>{selectedSource.sourceUrl}</dd>
              <dt>{t.confidence}</dt><dd>{selectedSource.confidence}</dd>
              <dt>{t.score}</dt><dd>{selectedSource.score ?? "-"}</dd>
            </dl>
            <button className="primary" onClick={() => setSelectedSource(null)}>{t.close}</button>
          </section>
        </div>
      )}
    </main>
  );
}

function MetricsPanel({ metrics, t }: { metrics: OpsMetrics | null; t: (typeof messages)[Locale] }) {
  return (
    <section className="opsBlock">
      <div className="sectionHead compact"><h2><Gauge size={16} /> {t.metricsTitle}</h2></div>
      <div className="metricGrid">
        <Metric label={t.grayCohort} value={metrics?.grayCohort ?? "-"} />
        <Metric label={t.apiCacheTarget} value={metrics ? `${Math.round(metrics.cacheHitRateTarget * 100)}%` : "-"} />
        <Metric label={t.crawlerRtoTarget} value={metrics ? `< ${metrics.crawlerRtoMinutesTarget} min` : "-"} />
        <Metric label={t.dbRecoveryTarget} value={metrics ? `<= ${metrics.databaseRecoveryDataLossHoursTarget} h` : "-"} />
      </div>
    </section>
  );
}

function PaidIntelligencePanel({ paidRows, t }: { paidRows: PaidIntelligence[]; t: (typeof messages)[Locale] }) {
  return (
    <section className="opsBlock">
      <div className="sectionHead compact"><h2><LockKeyhole size={16} /> {t.paidTitle}</h2></div>
      <div className="table">
        {paidRows.length === 0 && <EmptyRow title={t.paidEmptyTitle} detail={t.paidEmptyDetail} />}
        {paidRows.map((row) => (
          <div className="row" key={row.id}>
            <strong>{row.title}</strong>
            <span>{row.status}</span>
            <small>{row.entitlement} / {row.regionId} / {row.industryId}</small>
          </div>
        ))}
      </div>
    </section>
  );
}

function ReportPanel({
  report,
  diagnosis,
  reportBusy,
  onGenerateReport,
  t
}: {
  report: DiagnosisReport | null;
  diagnosis: Diagnosis | null;
  reportBusy: boolean;
  onGenerateReport: () => void;
  t: (typeof messages)[Locale];
}) {
  return (
    <section className="opsBlock">
      <div className="sectionHead compact"><h2><FileText size={16} /> {t.reportTitle}</h2></div>
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
              <button className="primary" onClick={onGenerateReport} disabled={reportBusy}>
                <FileText size={16} />
                {reportBusy ? t.generatingReport : t.generateReport}
              </button>
            </div>
          </div>
        ) : (
          <EmptyRow title={t.reportEmptyTitle} detail={t.reportEmptyDetail} />
        )}
      </div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function EmptyRow({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="emptyRow">
      <FileText size={28} />
      <strong>{title}</strong>
      <small>{detail}</small>
    </div>
  );
}
