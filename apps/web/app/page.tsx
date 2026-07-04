"use client";

import { Archive, Bot, CheckCircle2, Database, Eye, FilePlus2, FileText, Gauge, LogIn, LockKeyhole, Search, Send, ShieldCheck, UserRound } from "lucide-react";
import { useMemo, useState } from "react";
import type { Diagnosis, Source } from "../lib/api-client";

const mockSources: Source[] = [
  {
    id: "seed-restaurant-cashflow",
    title: "餐饮门店现金流基础诊断",
    sourceUrl: "seed://v1/restaurant-cashflow",
    sourceId: "seed-baseline",
    confidence: 0.9,
    score: 0.82
  }
];

const mockDiagnosis: Diagnosis = {
  answer:
    "针对当前门店现金流诊断，先核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。若平台佣金上升而翻台率没有同步提升，应优先检查渠道投放效率和菜品毛利结构。",
  sources: mockSources,
  confidence: "MEDIUM",
  timeliness: "基于V1静态基线知识和已入库情报生成",
  disclaimer: "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务承诺。"
};

const intelligenceRows = [
  { title: "本地餐饮平台佣金调整", status: "APPROVED", source: "manual-local", weight: "0.85" },
  { title: "库存周转风险基线", status: "PENDING", source: "seed-baseline", weight: "0.85" },
  { title: "商圈临时管控公告", status: "PENDING", source: "public-page", weight: "0.60" }
];

const users = [
  { username: "admin", role: "SUPER_ADMIN", region: "cn-default", industry: "general" },
  { username: "operator", role: "OPERATOR", region: "cn-default", industry: "general" },
  { username: "user", role: "USER", region: "cn-default", industry: "general" },
  { username: "seed_paid", role: "USER", region: "cn-default", industry: "general", membership: "SEED_PAID" }
];

const v2Metrics = [
  { label: "Gray cohort", value: "internal + seed paid" },
  { label: "API cache target", value: "70%" },
  { label: "Crawler RTO target", value: "< 10 min" },
  { label: "DB recovery target", value: "<= 6 h" }
];

const paidRows = [
  { title: "Paid margin warning", status: "APPROVED", entitlement: "PAID" },
  { title: "Seed paid rent benchmark", status: "APPROVED", entitlement: "PAID" }
];

const reviewRows = [
  { title: "review-v2-001", status: "PENDING_REVIEW", reason: "SUSPICIOUS_CONFLICT" },
  { title: "audit-v2-m0", status: "APPROVED", reason: "V2_M0_SCOPE_LOCK" }
];

export default function Home() {
  const [loggedIn, setLoggedIn] = useState(false);
  const [message, setMessage] = useState("餐饮门店现金流怎么诊断？");
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(mockDiagnosis);
  const [selectedSource, setSelectedSource] = useState<Source | null>(null);
  const [streaming, setStreaming] = useState(false);

  const status = useMemo(() => (loggedIn ? "已连接 V1 API 契约" : "本地演示模式"), [loggedIn]);

  function submitDiagnosis() {
    setStreaming(true);
    setDiagnosis(null);
    window.setTimeout(() => {
      setDiagnosis({
        ...mockDiagnosis,
        answer: `针对「${message}」，V1 诊断建议先围绕已检索依据核对关键经营变量。` + mockDiagnosis.answer
      });
      setStreaming(false);
    }, 360);
  }

  return (
    <main className="workspace">
      <aside className="rail">
        <div className="brand">
          <span className="mark">BS</span>
          <div>
            <strong>BizSage</strong>
            <small>V1 diagnosis console</small>
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
          <label>账号<input defaultValue="operator" /></label>
          <label>密码<input defaultValue="password" type="password" /></label>
          <button className="primary" onClick={() => setLoggedIn(true)}><LogIn size={16} />登录</button>
        </section>
      </aside>

      <section className="main">
        <header className="topbar">
          <div>
            <h1>经营诊断工作台</h1>
            <p>{status} / 行业 general / 地域 cn-default</p>
          </div>
          <div className="health"><CheckCircle2 size={18} />V2 M0 locked</div>
        </header>

        <div className="grid">
          <section className="dialogue">
            <div className="sectionHead">
              <div><h2>诊断会话</h2><p>RAG evidence first, answer second.</p></div>
              <button className="ghost"><FilePlus2 size={16} />新建</button>
            </div>
            <div className="messages">
              <div className="bubble user">{message}</div>
              {streaming && <div className="bubble agent muted">正在检索知识库并生成诊断...</div>}
              {diagnosis && (
                <div className="bubble agent">
                  <p>{diagnosis.answer}</p>
                  <div className="meta">
                    <span>{diagnosis.confidence}</span>
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
              <button className="primary icon" onClick={submitDiagnosis} title="发送诊断"><Send size={18} /></button>
            </div>
          </section>

          <aside className="ops">
            <section className="opsBlock">
              <div className="sectionHead compact"><h2><Gauge size={16} /> V2 Gray Metrics</h2></div>
              <div className="metricGrid">
                {v2Metrics.map((metric) => (
                  <div className="metric" key={metric.label}>
                    <span>{metric.label}</span>
                    <strong>{metric.value}</strong>
                  </div>
                ))}
              </div>
            </section>
            <section className="opsBlock">
              <div className="sectionHead compact"><h2><LockKeyhole size={16} /> Paid Intelligence</h2></div>
              <div className="table">
                {paidRows.map((row) => (
                  <div className="row" key={row.title}>
                    <strong>{row.title}</strong>
                    <span>{row.status}</span>
                    <small>{row.entitlement} only / hidden from FREE users</small>
                  </div>
                ))}
              </div>
            </section>
            <section className="opsBlock">
              <div className="sectionHead compact"><h2><FileText size={16} /> Review & Audit</h2></div>
              <div className="table">
                {reviewRows.map((row) => (
                  <div className="row" key={row.title}>
                    <strong>{row.title}</strong>
                    <span>{row.status}</span>
                    <small>{row.reason}</small>
                  </div>
                ))}
              </div>
            </section>
            <section className="opsBlock">
              <div className="sectionHead compact"><h2>情报队列</h2><button className="ghost">录入</button></div>
              <div className="table">
                {intelligenceRows.map((row) => (
                  <div className="row" key={row.title}>
                    <strong>{row.title}</strong>
                    <span>{row.status}</span>
                    <small>{row.source} / w {row.weight}</small>
                  </div>
                ))}
              </div>
            </section>
            <section className="opsBlock">
              <div className="sectionHead compact"><h2>用户</h2></div>
              <div className="table">
                {users.map((user) => (
                  <div className="row" key={user.username}>
                    <strong>{user.username}</strong>
                    <span>{user.role}</span>
                    <small>{user.region} / {user.industry}</small>
                  </div>
                ))}
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
              <dt>检索分</dt><dd>{selectedSource.score}</dd>
            </dl>
            <button className="primary" onClick={() => setSelectedSource(null)}>关闭</button>
          </section>
        </div>
      )}
    </main>
  );
}
