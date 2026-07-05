"use client";

import {
  Archive,
  Bot,
  CheckCircle2,
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
import { useMemo, useState } from "react";
import {
  createConversation,
  fetchDiagnosisReport,
  fetchOpsMetrics,
  fetchPaidIntelligence,
  login,
  streamDiagnosis,
  type Diagnosis,
  type DiagnosisReport,
  type LoginProfile,
  type OpsMetrics,
  type PaidIntelligence,
  type Source
} from "../lib/api-client";

type Locale = "zh-CN" | "en";

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
    close: "关闭"
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
    close: "Close"
  }
};

export default function Home() {
  const [locale, setLocale] = useState<Locale>("zh-CN");
  const [username, setUsername] = useState("operator");
  const [password, setPassword] = useState("password");
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [message, setMessage] = useState(messages["zh-CN"].defaultQuestion);
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null);
  const [report, setReport] = useState<DiagnosisReport | null>(null);
  const [paidRows, setPaidRows] = useState<PaidIntelligence[]>([]);
  const [metrics, setMetrics] = useState<OpsMetrics | null>(null);
  const [selectedSource, setSelectedSource] = useState<Source | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(messages["zh-CN"].loginNotice);

  const t = messages[locale];

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
      setNotice(t.loginSuccess);

      const [nextPaidRows, nextMetrics] = await Promise.allSettled([
        fetchPaidIntelligence(nextProfile.token),
        fetchOpsMetrics(nextProfile.token)
      ]);
      setPaidRows(nextPaidRows.status === "fulfilled" ? nextPaidRows.value : []);
      setMetrics(nextMetrics.status === "fulfilled" ? nextMetrics.value : null);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t.loginFailed);
    } finally {
      setBusy(false);
    }
  }

  function handleLogout() {
    setProfile(null);
    setDiagnosis(null);
    setReport(null);
    setPaidRows([]);
    setMetrics(null);
    setSelectedSource(null);
    setNotice(t.loginNotice);
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
      const conversation = await createConversation(profile.token, t.diagnosisConversation);
      const nextDiagnosis = await streamDiagnosis(profile.token, conversation.id, message);
      setDiagnosis(nextDiagnosis);
      const nextReport = await fetchDiagnosisReport(profile.token, message);
      setReport(nextReport);
      setNotice(t.diagnosisCreated);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
    } finally {
      setBusy(false);
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
          <button className="navItem active" title={t.diagnosisConversation}><Bot size={18} />{t.navDiagnosis}</button>
          <button className="navItem" title={t.navIntelligence}><Database size={18} />{t.navIntelligence}</button>
          <button className="navItem" title={t.navUsers}><UserRound size={18} />{t.navUsers}</button>
          <button className="navItem" title={t.navArchive}><Archive size={18} />{t.navArchive}</button>
        </nav>
        <section className="identityPanel">
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
            <div className="health"><CheckCircle2 size={18} />{t.health}</div>
          </div>
        </header>

        <div className="grid">
          <section className="dialogue">
            <div className="sectionHead">
              <div><h2>{t.diagnosisConversation}</h2><p>{t.evidenceLine}</p></div>
              <button className="ghost" onClick={() => setDiagnosis(null)}><FilePlus2 size={16} />{t.newConversation}</button>
            </div>
            <div className="messages">
              <div className="bubble user">{message}</div>
              {busy && <div className="bubble agent muted">{t.busyDiagnosis}</div>}
              {diagnosis && (
                <div className="bubble agent">
                  <p>{diagnosis.answer}</p>
                  <div className="meta">
                    <span>{diagnosis.confidence}</span>
                    <span>{diagnosis.selfCheckStatus ?? "PASSED"}</span>
                    <span>{diagnosis.timeliness}</span>
                  </div>
                  <div className="sourceLine">
                    {diagnosis.sources.map((source) => (
                      <button key={source.id} onClick={() => setSelectedSource(source)}><Eye size={14} />{source.title}</button>
                    ))}
                  </div>
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
            <section className="opsBlock">
              <div className="sectionHead compact"><h2><Gauge size={16} /> {t.metricsTitle}</h2></div>
              <div className="metricGrid">
                <Metric label={t.grayCohort} value={metrics?.grayCohort ?? "-"} />
                <Metric label={t.apiCacheTarget} value={metrics ? `${Math.round(metrics.cacheHitRateTarget * 100)}%` : "-"} />
                <Metric label={t.crawlerRtoTarget} value={metrics ? `< ${metrics.crawlerRtoMinutesTarget} min` : "-"} />
                <Metric label={t.dbRecoveryTarget} value={metrics ? `<= ${metrics.databaseRecoveryDataLossHoursTarget} h` : "-"} />
              </div>
            </section>
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
            <section className="opsBlock">
              <div className="sectionHead compact"><h2><FileText size={16} /> {t.reportTitle}</h2></div>
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
          </aside>
        </div>
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
    <div className="row">
      <strong>{title}</strong>
      <span>EMPTY</span>
      <small>{detail}</small>
    </div>
  );
}
