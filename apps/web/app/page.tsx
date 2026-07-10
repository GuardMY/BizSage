"use client";

import { FileText, LogIn } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ArchiveWorkspace } from "./components/archive-workspace";
import { ConversationSidebar } from "./components/conversation-sidebar";
import { DiagnosisWorkspace } from "./components/diagnosis-workspace";
import { WorkspaceShell } from "./components/workspace-shell";
import type { WorkspaceMessages, WorkspaceSection } from "./components/workspace-types";
import {
  nextSelectionAfterArchive,
  resolveWorkspaceSelection
} from "../lib/conversation-workspace";
import {
  archiveConversation,
  AuthExpiredError,
  createConversation,
  deleteConversation,
  fetchConversations,
  downloadDiagnosisPdf,
  fetchDiagnosisReport,
  fetchMe,
  fetchMessages,
  fetchPaidIntelligence,
  login,
  logout,
  streamDiagnosisEvents,
  updatePreferredLocale,
  WorkerError,
  type AppLocale,
  type Conversation,
  type ConversationMessage,
  type Diagnosis,
  type DiagnosisReport,
  type LoginProfile,
  type PaidIntelligence,
  type Source
} from "../lib/api-client";

type Locale = AppLocale;

const sidebarMessages: Record<
  Locale,
  Pick<
    WorkspaceMessages,
    "diagnosisConversationList" | "archiveConversationList" | "archiveConversation" | "deleteConversation"
  >
> = {
  "zh-CN": {
    diagnosisConversationList: "诊断会话列表",
    archiveConversationList: "归档会话列表",
    archiveConversation: "归档会话",
    deleteConversation: "删除会话"
  },
  en: {
    diagnosisConversationList: "Diagnosis conversation list",
    archiveConversationList: "Archive conversation list",
    archiveConversation: "Archive conversation",
    deleteConversation: "Delete conversation"
  }
};

const messages: Record<Locale, WorkspaceMessages> = {
  "zh-CN": {
    ...sidebarMessages["zh-CN"],
    brandSubtitle: "对话工作台",
    loginTitle: "登录 BizSage",
    loginIntro: "未登录用户只能访问独立登录界面。登录后进入经营诊断工作台和对话管理界面。",
    username: "账号",
    password: "密码",
    login: "登录",
    loggingIn: "登录中...",
    loginNotice: "请登录 API 后开始诊断。",
    loginSuccess: "已连接 API。",
    loginFailed: "登录失败，请检查账号和密码。",
    sessionExpired: "登录已失效，请重新登录。",
    languageToggle: "English",
    languageSaved: "语言偏好已保存。",
    navDiagnosis: "诊断",
    navIntelligence: "情报",
    navUsers: "用户",
    navArchive: "归档",
    identity: "身份",
    logout: "退出登录",
    workspaceTitle: "经营诊断工作台",
    health: "已就绪",
    evidenceLine: "先看证据，再给建议。",
    newConversation: "新建会话",
    diagnosisConversation: "诊断会话",
    defaultQuestion: "餐饮门店现金流怎么诊断？",
    busyDiagnosis: "正在连接 API 并生成诊断...",
    sendDiagnosis: "发送诊断",
    diagnosisCreated: "诊断已生成。",
    diagnosisFailed: "诊断失败。",
    conversationArchived: "会话已归档。",
    loginRequired: "请先登录。",
    workerUnreachable: "诊断服务暂时不可用，请稍后重试。",
    llmNotConfigured: "AI 引擎未配置，请联系管理员。",
    workerTimeout: "诊断服务响应超时，请稍后重试。",
    workerError: "诊断服务发生错误。",
    needsReview: "需复核",
    insufficientEvidence: "证据不足",
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
    selectConversation: "选择会话或直接发送问题开始新会话。",
    noConversations: "暂无会话",
    newConversationTitle: "新诊断会话",
    activeConversations: "当前会话",
    archivedConversations: "已归档会话",
    archiveCurrent: "归档当前会话",
    archiveReadonly: "归档区仅展示历史消息，不支持继续提问或生成新报告。",
    selectedConversation: "当前上下文",
    noArchivedMessages: "归档会话会在这里按只读方式展示。",
    intelligenceSummary: "围绕当前会话上下文查看可见情报。",
    usersSummary: "当前登录身份与会话上下文。",
    conversationContext: "当前工作内容围绕所选会话展开。",
    noConversationContext: "当前模块没有可用会话。"
  },
  en: {
    ...sidebarMessages.en,
    brandSubtitle: "Conversation workspace",
    loginTitle: "Sign in to BizSage",
    loginIntro: "Signed-out users only see this login screen. After sign-in, the operating diagnosis workspace and conversation manager open.",
    username: "Username",
    password: "Password",
    login: "Sign in",
    loggingIn: "Signing in...",
    loginNotice: "Sign in to the API to start diagnosis.",
    loginSuccess: "Connected to API.",
    loginFailed: "Sign-in failed. Check the username and password.",
    sessionExpired: "Your session expired. Please sign in again.",
    languageToggle: "中文",
    languageSaved: "Language preference saved.",
    navDiagnosis: "Diagnosis",
    navIntelligence: "Intelligence",
    navUsers: "Users",
    navArchive: "Archive",
    identity: "Identity",
    logout: "Sign out",
    workspaceTitle: "Operating Diagnosis Workspace",
    health: "Ready",
    evidenceLine: "Evidence first, answer second.",
    newConversation: "New conversation",
    diagnosisConversation: "Diagnosis chat",
    defaultQuestion: "How should a restaurant diagnose cash flow?",
    busyDiagnosis: "Connecting to the API and generating diagnosis...",
    sendDiagnosis: "Send diagnosis",
    diagnosisCreated: "Diagnosis generated.",
    diagnosisFailed: "Diagnosis failed.",
    conversationArchived: "Conversation archived.",
    loginRequired: "Please sign in first.",
    workerUnreachable: "Diagnosis service is currently unavailable. Please try again later.",
    llmNotConfigured: "AI engine is not configured. Please contact the administrator.",
    workerTimeout: "Diagnosis service timed out. Please try again later.",
    workerError: "Diagnosis service encountered an error.",
    needsReview: "Needs Review",
    insufficientEvidence: "Insufficient Evidence",
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
    generateReport: "Generate report",
    generatingReport: "Generating report...",
    selectConversation: "Select a conversation or send a question to start a new one.",
    noConversations: "No conversations",
    newConversationTitle: "New diagnosis chat",
    activeConversations: "Current conversations",
    archivedConversations: "Archived conversations",
    archiveCurrent: "Archive current conversation",
    archiveReadonly: "The archive view is read-only. Continue diagnosis and report generation from an active conversation.",
    selectedConversation: "Current context",
    noArchivedMessages: "Archived conversations appear here in read-only mode.",
    intelligenceSummary: "Review visible intelligence in the context of the selected conversation.",
    usersSummary: "Current sign-in identity and conversation context.",
    conversationContext: "The active workspace follows the selected conversation.",
    noConversationContext: "No conversation is available for this module."
  }
};

export default function Home() {
  const [locale, setLocale] = useState<Locale>("zh-CN");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [profile, setProfile] = useState<LoginProfile | null>(null);
  const [activeSection, setActiveSection] = useState<WorkspaceSection>("diagnosis");
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [messageHistory, setMessageHistory] = useState<ConversationMessage[]>([]);
  const [message, setMessage] = useState(messages["zh-CN"].defaultQuestion);
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null);
  const [streamingDiagnosis, setStreamingDiagnosis] = useState<Diagnosis | null>(null);
  const [report, setReport] = useState<DiagnosisReport | null>(null);
  const [reportBusy, setReportBusy] = useState(false);
  const [paidRows, setPaidRows] = useState<PaidIntelligence[]>([]);
  const [selectedSource, setSelectedSource] = useState<Source | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(messages["zh-CN"].loginNotice);

  const t = messages[locale];

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
        // Not logged in — that's fine, user will see the login form
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!profile) {
      setPaidRows([]);
      setConversations([]);
      setMessageHistory([]);
      setSelectedConversationId(null);
      return;
    }

    let cancelled = false;
    Promise.allSettled([
      fetchPaidIntelligence(),
      fetchConversations(),
    ]).then(([nextPaidRows, nextConversations]) => {
      if (cancelled) return;
      const failures = [nextPaidRows, nextConversations].filter(
        (result): result is PromiseRejectedResult => result.status === "rejected"
      );
      if (failures.some((result) => result.reason instanceof AuthExpiredError)) {
        handleSessionExpired();
        return;
      }

      setPaidRows(nextPaidRows.status === "fulfilled" ? nextPaidRows.value : []);
      // V2: Paginated response — extract items array
      setConversations(nextConversations.status === "fulfilled" ? nextConversations.value.items : []);
    });

    return () => {
      cancelled = true;
    };
  }, [profile, t.sessionExpired]);

  const workspaceSelection = useMemo(
    () =>
      resolveWorkspaceSelection({
        section: activeSection,
        selectedConversationId,
        conversations
      }),
    [activeSection, selectedConversationId, conversations]
  );

  useEffect(() => {
    if (workspaceSelection.selectedConversationId !== selectedConversationId) {
      setSelectedConversationId(workspaceSelection.selectedConversationId);
    }
  }, [selectedConversationId, workspaceSelection.selectedConversationId]);

  const resolvedSelectedConversationId = workspaceSelection.selectedConversationId;
  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === resolvedSelectedConversationId) ?? null,
    [conversations, resolvedSelectedConversationId]
  );

  useEffect(() => {
    if (!profile || !resolvedSelectedConversationId) {
      setMessageHistory([]);
      return;
    }

    let cancelled = false;
    fetchMessages(resolvedSelectedConversationId)
      .then((messagesResult) => {
        if (!cancelled) setMessageHistory(messagesResult);
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
  }, [profile, resolvedSelectedConversationId, t.sessionExpired]);

  async function toggleLocale() {
    const previousLocale = locale;
    const nextLocale: Locale = locale === "zh-CN" ? "en" : "zh-CN";
    setLocale(nextLocale);
    setNotice(profile ? messages[nextLocale].languageSaved : messages[nextLocale].loginNotice);
    if (!profile) return;

    setProfile({ ...profile, preferredLocale: nextLocale });
    try {
      const updatedProfile = await updatePreferredLocale(nextLocale);
      setProfile({ ...updatedProfile, preferredLocale: normalizeLocale(updatedProfile.preferredLocale) });
    } catch (error) {
      setLocale(previousLocale);
      setProfile({ ...profile, preferredLocale: previousLocale });
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      setNotice(error instanceof Error ? error.message : messages[previousLocale].workerError);
    }
  }

  async function handleLogin() {
    setBusy(true);
    try {
      const nextProfile = await login(username, password);
      const nextLocale = normalizeLocale(nextProfile.preferredLocale);
      setLocale(nextLocale);
      setProfile({ ...nextProfile, preferredLocale: nextLocale });
      setActiveSection("diagnosis");
      setNotice(messages[nextLocale].loginSuccess);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t.loginFailed);
    } finally {
      setBusy(false);
    }
  }

  function handleLogout() {
    // V2: httpOnly cookie is cleared by the server; no localStorage to manage.
    logout().catch(() => {});
    setProfile(null);
    setActiveSection("diagnosis");
    setConversations([]);
    setMessageHistory([]);
    setDiagnosis(null);
    setStreamingDiagnosis(null);
    setReport(null);
    setSelectedConversationId(null);
    setPaidRows([]);
    setSelectedSource(null);
    setReportBusy(false);
    setNotice(t.loginNotice);
  }

  function handleSessionExpired() {
    setProfile(null);
    setActiveSection("diagnosis");
    setConversations([]);
    setMessageHistory([]);
    setDiagnosis(null);
    setStreamingDiagnosis(null);
    setReport(null);
    setSelectedConversationId(null);
    setPaidRows([]);
    setSelectedSource(null);
    setReportBusy(false);
    setNotice(t.sessionExpired);
  }

  function resetConversationOutputs() {
    setDiagnosis(null);
    setStreamingDiagnosis(null);
    setReport(null);
    setSelectedSource(null);
  }

  function handleSelectConversation(id: number) {
    setSelectedConversationId(id);
    resetConversationOutputs();
  }

  async function handleNewConversation() {
    if (!profile) return;
    try {
      const created = await createConversation(t.newConversationTitle);
      setConversations((previous) => [created, ...previous]);
      setSelectedConversationId(created.id);
      setActiveSection("diagnosis");
      resetConversationOutputs();
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
    }
  }

  async function handleArchiveConversation() {
    if (!profile || selectedConversation == null) return;

    try {
      const archivedConversation = await archiveConversation(selectedConversation.id);
      const updatedConversations = conversations.map((conversation) =>
        conversation.id === archivedConversation.id ? archivedConversation : conversation
      );
      setConversations(updatedConversations);
      setSelectedConversationId(
        nextSelectionAfterArchive({
          selectedConversationId: selectedConversation.id,
          conversations: updatedConversations
        })
      );
      resetConversationOutputs();
      setNotice(t.conversationArchived);
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
    }
  }

  async function handleArchiveConversationFromList(conversationId: number) {
    if (!profile) return;

    try {
      const archivedConversation = await archiveConversation(conversationId);
      const updatedConversations = conversations.map((conversation) =>
        conversation.id === archivedConversation.id ? archivedConversation : conversation
      );
      setConversations(updatedConversations);
      setSelectedConversationId(
        nextSelectionAfterArchive({
          selectedConversationId: conversationId,
          conversations: updatedConversations
        })
      );
      if (selectedConversation?.id === conversationId) {
        resetConversationOutputs();
      }
      setNotice(t.conversationArchived);
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
    }
  }

  async function handleDeleteConversation(conversationId: number) {
    if (!profile) return;

    try {
      await deleteConversation(conversationId);
      setConversations((previous) => previous.filter((conversation) => conversation.id !== conversationId));
      if (selectedConversation?.id === conversationId) {
        resetConversationOutputs();
      }
      setNotice(t.deleteConversation);
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
    }
  }

  async function submitDiagnosis() {
    if (!profile) {
      setNotice(t.loginRequired);
      return;
    }

    setBusy(true);
    setDiagnosis(null);
    setStreamingDiagnosis(null);
    setReport(null);

    try {
      const created = selectedConversationId ? null : await createConversation(t.newConversationTitle);
      const conversationId = selectedConversationId ?? created!.id;
      if (created) {
        setSelectedConversationId(conversationId);
        setConversations((previous) => [created, ...previous]);
      }

      const nextDiagnosis = await streamDiagnosisEvents(conversationId, message, {
        onPartialAnswer(answer) {
          setStreamingDiagnosis((previous) => ({
            answer,
            confidence: previous?.confidence ?? "LOW",
            disclaimer: previous?.disclaimer ?? "",
            selfCheckStatus: previous?.selfCheckStatus,
            sources: previous?.sources ?? [],
            timeliness: previous?.timeliness ?? "Streaming"
          }));
        }
      });
      setStreamingDiagnosis(nextDiagnosis);
      setDiagnosis(nextDiagnosis);

      try {
        const updatedMessages = await fetchMessages(conversationId);
        setMessageHistory(updatedMessages);
      } catch (error) {
        if (error instanceof AuthExpiredError) {
          handleSessionExpired();
          return;
        }
      }

      setNotice(t.diagnosisCreated);
      setActiveSection("diagnosis");
    } catch (error) {
      setStreamingDiagnosis(null);
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }

      if (error instanceof WorkerError) {
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
            break;
        }
      } else {
        setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
      }
    } finally {
      setStreamingDiagnosis(null);
      setBusy(false);
    }
  }

  async function generateReport() {
    if (!profile) return;
    setReportBusy(true);
    try {
      // Load report metadata for the sidebar display
      const nextReport = await fetchDiagnosisReport(message);
      setReport(nextReport);
      // Trigger PDF binary download
      await downloadDiagnosisPdf(message);
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

  if (!profile) {
    return (
      <main className="loginScreen">
        <button className="languageButton loginLanguage" onClick={toggleLocale} type="button">
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
            <button className="primary" onClick={handleLogin} disabled={busy} type="button">
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
    <>
      <WorkspaceShell
        activeSection={activeSection}
        onLogout={handleLogout}
        onToggleLocale={toggleLocale}
        profile={profile}
        setActiveSection={setActiveSection}
        sidebar={
          <ConversationSidebar
            activeSection={activeSection}
            conversations={workspaceSelection.visibleConversations}
            onArchiveConversation={handleArchiveConversationFromList}
            onDeleteConversation={handleDeleteConversation}
            onNewConversation={handleNewConversation}
            onSelectConversation={handleSelectConversation}
            selectedConversationId={resolvedSelectedConversationId}
            t={t}
          />
        }
        status={notice}
        t={t}
      >
        {activeSection === "diagnosis" && (
          <DiagnosisWorkspace
            busy={busy}
            diagnosis={diagnosis}
            message={message}
            messageHistory={messageHistory}
            onArchiveConversation={handleArchiveConversation}
            onGenerateReport={generateReport}
            onMessageChange={setMessage}
            onOpenSource={setSelectedSource}
            onSubmitDiagnosis={submitDiagnosis}
            paidRows={paidRows}
            report={report}
            reportBusy={reportBusy}
            selectedConversation={selectedConversation}
            streamingDiagnosis={streamingDiagnosis}
            t={t}
          />
        )}

        {activeSection === "intelligence" && (
          <div className="workspacePanelGrid simpleGrid">
            <section className="workspaceCard">
              <div className="sectionHead">
                <div className="sectionCopy">
                  <h2>{t.navIntelligence}</h2>
                  <p>{selectedConversation ? t.intelligenceSummary : t.noConversationContext}</p>
                </div>
              </div>
              <div className="table">
                {paidRows.length === 0 && <SimpleEmptyCard title={t.paidEmptyTitle} detail={t.paidEmptyDetail} />}
                {paidRows.map((row) => (
                  <div className="row" key={row.id}>
                    <strong>{row.title}</strong>
                    <span>{row.status}</span>
                    <small>{row.entitlement} / {row.regionId} / {row.industryId}</small>
                  </div>
                ))}
              </div>
            </section>
          </div>
        )}

        {activeSection === "users" && (
          <div className="workspacePanelGrid simpleGrid">
            <section className="workspaceCard">
              <div className="sectionHead">
                <div className="sectionCopy">
                  <h2>{t.navUsers}</h2>
                  <p>{selectedConversation ? t.usersSummary : t.noConversationContext}</p>
                </div>
              </div>
              <div className="table">
                <div className="row">
                  <strong>{profile.username}</strong>
                  <span>{profile.role}</span>
                  <small>{profile.membershipLevel} / {profile.regionId} / {profile.industryId}</small>
                </div>
              </div>
            </section>
          </div>
        )}

        {activeSection === "archive" && (
          <ArchiveWorkspace
            messageHistory={messageHistory}
            selectedConversation={selectedConversation}
            t={t}
          />
        )}
      </WorkspaceShell>

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
            <button className="primary" onClick={() => setSelectedSource(null)} type="button">{t.close}</button>
          </section>
        </div>
      )}
    </>
  );
}

function normalizeLocale(value: string | undefined): Locale {
  return value === "en" ? "en" : "zh-CN";
}

function SimpleEmptyCard({ detail, title }: { detail: string; title: string }) {
  return (
    <div className="emptyRow">
      <FileText size={28} />
      <strong>{title}</strong>
      <small>{detail}</small>
    </div>
  );
}
