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
  LogIn,
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

export default function Home() {
  const [username, setUsername] = useState("operator");
  const [password, setPassword] = useState("password");
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [message, setMessage] = useState("餐饮门店现金流怎么诊断？");
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null);
  const [report, setReport] = useState<DiagnosisReport | null>(null);
  const [paidRows, setPaidRows] = useState<PaidIntelligence[]>([]);
  const [metrics, setMetrics] = useState<OpsMetrics | null>(null);
  const [selectedSource, setSelectedSource] = useState<Source | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("请登录 API 后开始诊断。");

  const status = useMemo(() => {
    if (!profile) return "未连接 API";
    return `${profile.username} / ${profile.membershipLevel} / ${profile.regionId} / ${profile.industryId}`;
  }, [profile]);

  async function handleLogin() {
    setBusy(true);
    try {
      const nextProfile = await login(username, password);
      setProfile(nextProfile);
      setNotice("已连接 API。");

      const [nextPaidRows, nextMetrics] = await Promise.allSettled([
        fetchPaidIntelligence(nextProfile.token),
        fetchOpsMetrics(nextProfile.token)
      ]);
      setPaidRows(nextPaidRows.status === "fulfilled" ? nextPaidRows.value : []);
      setMetrics(nextMetrics.status === "fulfilled" ? nextMetrics.value : null);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "登录失败。");
    } finally {
      setBusy(false);
    }
  }

  async function submitDiagnosis() {
    if (!profile) {
      setNotice("请先登录。");
      return;
    }
    setBusy(true);
    setDiagnosis(null);
    setReport(null);
    try {
      const conversation = await createConversation(profile.token, "经营诊断");
      const nextDiagnosis = await streamDiagnosis(profile.token, conversation.id, message);
      setDiagnosis(nextDiagnosis);
      const nextReport = await fetchDiagnosisReport(profile.token, message);
      setReport(nextReport);
      setNotice("诊断已生成。");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "诊断失败。");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="workspace">
      <aside className="rail">
        <div className="brand">
          <span className="mark">BS</span>
          <div>
            <strong>BizSage</strong>
            <small>V2 gray console</small>
          </div>
        </div>
        <nav className="nav">
          <button className="navItem active" title="诊断对话"><Bot size={18} />诊断</button>
          <button className="navItem" title="情报管理"><Database size={18} />情报</button>
          <button className="navItem" title="用户列表"><UserRound size={18} />用户</button>
          <button className="navItem" title="归档会话"><Archive size={18} />归档</button>
        </nav>
        <section className="loginPanel">
          <div className="panelTitle"><ShieldCheck size={16} />身份</div>
          <label>账号<input value={username} onChange={(event) => setUsername(event.target.value)} /></label>
          <label>密码<input value={password} onChange={(event) => setPassword(event.target.value)} type="password" /></label>
          <button className="primary" onClick={handleLogin} disabled={busy}><LogIn size={16} />登录</button>
          <small>{notice}</small>
        </section>
      </aside>

      <section className="main">
        <header className="topbar">
          <div>
            <h1>经营诊断工作台</h1>
            <p>{status}</p>
          </div>
          <div className="health"><CheckCircle2 size={18} />V2 M0 locked</div>
        </header>

        <div className="grid">
          <section className="dialogue">
            <div className="sectionHead">
              <div><h2>诊断会话</h2><p>Evidence first, answer second.</p></div>
              <button className="ghost" onClick={() => setDiagnosis(null)}><FilePlus2 size={16} />新建</button>
            </div>
            <div className="messages">
              <div className="bubble user">{message}</div>
              {busy && <div className="bubble agent muted">正在连接 API 并生成诊断...</div>}
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
              <button className="primary icon" onClick={submitDiagnosis} disabled={busy} title="发送诊断"><Send size={18} /></button>
            </div>
          </section>

          <aside className="ops">
            <section className="opsBlock">
              <div className="sectionHead compact"><h2><Gauge size={16} /> V2 Gray Metrics</h2></div>
              <div className="metricGrid">
                <Metric label="Gray cohort" value={metrics?.grayCohort ?? "-"} />
                <Metric label="API cache target" value={metrics ? `${Math.round(metrics.cacheHitRateTarget * 100)}%` : "-"} />
                <Metric label="Crawler RTO target" value={metrics ? `< ${metrics.crawlerRtoMinutesTarget} min` : "-"} />
                <Metric label="DB recovery target" value={metrics ? `<= ${metrics.databaseRecoveryDataLossHoursTarget} h` : "-"} />
              </div>
            </section>
            <section className="opsBlock">
              <div className="sectionHead compact"><h2><LockKeyhole size={16} /> Paid Intelligence</h2></div>
              <div className="table">
                {paidRows.length === 0 && <EmptyRow title="无可见付费情报" detail="免费用户或未登录时不会显示付费数据。" />}
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
              <div className="sectionHead compact"><h2><FileText size={16} /> Diagnosis Report</h2></div>
              <div className="table">
                {report ? (
                  <div className="row">
                    <strong>{report.format} metadata</strong>
                    <span>{report.selfCheckStatus}</span>
                    <small>{report.summary}</small>
                  </div>
                ) : (
                  <EmptyRow title="暂无报告" detail="提交诊断后展示报告元数据。" />
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
              <dt>来源</dt><dd>{selectedSource.sourceId}</dd>
              <dt>地址</dt><dd>{selectedSource.sourceUrl}</dd>
              <dt>置信度</dt><dd>{selectedSource.confidence}</dd>
              <dt>检索分</dt><dd>{selectedSource.score ?? "-"}</dd>
            </dl>
            <button className="primary" onClick={() => setSelectedSource(null)}>关闭</button>
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
