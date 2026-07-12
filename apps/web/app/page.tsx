"use client";

import { LogIn } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ArchiveWorkspace } from "./components/archive-workspace";
import { ConversationSidebar } from "./components/conversation-sidebar";
import { DiagnosisWorkspace } from "./components/diagnosis-workspace";
import { LearningWorkspace } from "./components/learning-workspace";
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
  fetchConversationRecommendations,
  fetchMessages,
  login,
  logout,
  streamLearningEvents,
  streamDiagnosisEvents,
  updatePreferredLocale,
  WorkerError,
  type AppLocale,
  type Conversation,
  type ConversationMessage,
  type Diagnosis,
  type DiagnosisReport,
  type LoginProfile,
  type RecommendationItem,
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

const workflowMessagesEn: Pick<
  WorkspaceMessages,
  | "navLearning"
  | "learningConversation"
  | "busyLearning"
  | "sendLearning"
  | "learningNextNode"
  | "learningCurrentBlock"
  | "learningExtensionDirection"
  | "diagnosisBusinessIssue"
  | "diagnosisMissingProfile"
  | "diagnosisHighImpactDetail"
> = {
  navLearning: "Learning",
  learningConversation: "Learning chat",
  busyLearning: "Connecting to the API and generating learning...",
  sendLearning: "Send learning",
  learningNextNode: "Next node",
  learningCurrentBlock: "Current block",
  learningExtensionDirection: "Extension direction",
  diagnosisBusinessIssue: "Business issue",
  diagnosisMissingProfile: "Missing profile field",
  diagnosisHighImpactDetail: "High-impact detail"
};

const workflowMessagesZh: Pick<
  WorkspaceMessages,
  | "navLearning"
  | "learningConversation"
  | "busyLearning"
  | "sendLearning"
  | "learningNextNode"
  | "learningCurrentBlock"
  | "learningExtensionDirection"
  | "diagnosisBusinessIssue"
  | "diagnosisMissingProfile"
  | "diagnosisHighImpactDetail"
> = {
  navLearning: "学习",
  learningConversation: "学习会话",
  busyLearning: "正在连接 API 并生成学习内容...",
  sendLearning: "发送学习",
  learningNextNode: "下一节点",
  learningCurrentBlock: "当前细分块",
  learningExtensionDirection: "延展方向",
  diagnosisBusinessIssue: "业务问题",
  diagnosisMissingProfile: "缺失画像字段",
  diagnosisHighImpactDetail: "高影响细节"
};

const messages: Record<Locale, WorkspaceMessages> = {
  "zh-CN": {
    ...sidebarMessages["zh-CN"],
    ...workflowMessagesZh,
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
    navUsers: "用户",
    navArchive: "归档",
    identity: "身份",
    logout: "退出登录",
    workspaceTitle: "经营诊断工作台",
    health: "已就绪",
    evidenceLine: "先看证据，再给建议。",
    newConversation: "新建会话",
    diagnosisConversation: "诊断会话",
    busyDiagnosis: "正在连接 API 并生成诊断...",
    sendDiagnosis: "发送诊断",
    diagnosisCreated: "诊断已生成。",
    diagnosisFailed: "诊断失败。",
    diagnosisRetrying: "正在校验并优化回答…",
    conversationArchived: "会话已归档。",
    loginRequired: "请先登录。",
    workerUnreachable: "诊断服务暂时不可用，请稍后重试。",
    llmNotConfigured: "AI 引擎未配置，请联系管理员。",
    workerTimeout: "诊断服务响应超时，请稍后重试。",
    workerError: "诊断服务发生错误。",
    needsReview: "需复核",
    insufficientEvidence: "证据不足",
    recommendationTitle: "推荐问题",
    recommendationEmpty: "暂无推荐",
    recommendationRefresh: "刷新",
    recommendationMissing: "请重新使用当前会话上下文进行推荐。",
    recommendationContinue: "用这个问题继续",
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
    usersSummary: "当前登录身份与会话上下文。",
    conversationContext: "当前工作内容围绕所选会话展开。",
    noConversationContext: "当前模块没有可用会话。"
  },
  en: {
    ...sidebarMessages.en,
    ...workflowMessagesEn,
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
    navLearning: "Learning",
    navUsers: "Users",
    navArchive: "Archive",
    identity: "Identity",
    logout: "Sign out",
    workspaceTitle: "Operating Diagnosis Workspace",
    health: "Ready",
    evidenceLine: "Evidence first, answer second.",
    newConversation: "New conversation",
    diagnosisConversation: "Diagnosis chat",
    learningConversation: "Learning chat",
    busyDiagnosis: "Connecting to the API and generating diagnosis...",
    busyLearning: "Connecting to the API and generating learning...",
    sendDiagnosis: "Send diagnosis",
    sendLearning: "Send learning",
    diagnosisCreated: "Diagnosis generated.",
    diagnosisFailed: "Diagnosis failed.",
    diagnosisRetrying: "Validating and refining the answer...",
    conversationArchived: "Conversation archived.",
    loginRequired: "Please sign in first.",
    workerUnreachable: "Diagnosis service is currently unavailable. Please try again later.",
    llmNotConfigured: "AI engine is not configured. Please contact the administrator.",
    workerTimeout: "Diagnosis service timed out. Please try again later.",
    workerError: "Diagnosis service encountered an error.",
    needsReview: "Needs Review",
    insufficientEvidence: "Insufficient Evidence",
    recommendationTitle: "Recommended questions",
    recommendationEmpty: "No recommendations yet",
    recommendationRefresh: "Refresh",
    recommendationMissing: "Refresh suggestions using the current conversation context.",
    recommendationContinue: "Use this question",
    learningNextNode: "Next node",
    learningCurrentBlock: "Current block",
    learningExtensionDirection: "Extension direction",
    diagnosisBusinessIssue: "Business issue",
    diagnosisMissingProfile: "Missing profile field",
    diagnosisHighImpactDetail: "High-impact detail",
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
  const [message, setMessage] = useState("");
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null);
  const [learningDiagnosis, setLearningDiagnosis] = useState<Diagnosis | null>(null);
  const [streamingDiagnosis, setStreamingDiagnosis] = useState<Diagnosis | null>(null);
  const [streamingLearning, setStreamingLearning] = useState<Diagnosis | null>(null);
  const [report, setReport] = useState<DiagnosisReport | null>(null);
  const [reportBusy, setReportBusy] = useState(false);
  const [recommendationRows, setRecommendationRows] = useState<RecommendationItem[]>([]);
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
      setConversations([]);
      setMessageHistory([]);
      setSelectedConversationId(null);
      setRecommendationRows([]);
      setLearningDiagnosis(null);
      setStreamingLearning(null);
      return;
    }

    let cancelled = false;
    Promise.allSettled([fetchConversations()]).then(([nextConversations]) => {
      if (cancelled) return;
      const failures = [nextConversations].filter(
        (result): result is PromiseRejectedResult => result.status === "rejected"
      );
      if (failures.some((result) => result.reason instanceof AuthExpiredError)) {
        handleSessionExpired();
        return;
      }

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
    setLearningDiagnosis(null);
    setStreamingDiagnosis(null);
    setStreamingLearning(null);
    setReport(null);
    setSelectedConversationId(null);
    setRecommendationRows([]);
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
    setLearningDiagnosis(null);
    setStreamingDiagnosis(null);
    setStreamingLearning(null);
    setReport(null);
    setSelectedConversationId(null);
    setRecommendationRows([]);
    setSelectedSource(null);
    setReportBusy(false);
    setNotice(t.sessionExpired);
  }

  function resetConversationOutputs() {
    setDiagnosis(null);
    setLearningDiagnosis(null);
    setStreamingDiagnosis(null);
    setStreamingLearning(null);
    setReport(null);
    setSelectedSource(null);
    setRecommendationRows([]);
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
      setActiveSection("learning");
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
    setLearningDiagnosis(null);
    setStreamingDiagnosis(null);
    setStreamingLearning(null);
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
        },
        onStatus(status) {
          if (status.state === "retrying") {
            setNotice(t.diagnosisRetrying);
          }
        }
      });
      setStreamingDiagnosis(nextDiagnosis);
      setDiagnosis(nextDiagnosis);
      setRecommendationRows(nextDiagnosis.recommendedQuestions ?? nextDiagnosis.recommendationCandidates ?? []);

      try {
        const updatedMessages = await fetchMessages(conversationId);
        setMessageHistory(updatedMessages);
        setStreamingDiagnosis(null);
        const nextRecommendations = await fetchConversationRecommendations(conversationId);
        setRecommendationRows(nextRecommendations.items);
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

  async function submitLearning() {
    if (!profile) {
      setNotice(t.loginRequired);
      return;
    }

    setBusy(true);
    setLearningDiagnosis(null);
    setStreamingLearning(null);
    setReport(null);

    try {
      const created = selectedConversationId ? null : await createConversation(t.newConversationTitle);
      const conversationId = selectedConversationId ?? created!.id;
      if (created) {
        setSelectedConversationId(conversationId);
        setConversations((previous) => [created, ...previous]);
      }

      const nextLearning = await streamLearningEvents(conversationId, { question: message }, {
        onPartialAnswer(answer) {
          setStreamingLearning((previous) => ({
            answer,
            confidence: previous?.confidence ?? "LOW",
            disclaimer: previous?.disclaimer ?? "",
            selfCheckStatus: previous?.selfCheckStatus,
            sources: previous?.sources ?? [],
            timeliness: previous?.timeliness ?? "Streaming"
          }));
        }
      });
      setStreamingLearning(nextLearning);
      setLearningDiagnosis(nextLearning);
      setRecommendationRows(nextLearning.recommendationCandidates ?? []);
      setNotice(t.diagnosisCreated);
      setActiveSection("learning");
    } catch (error) {
      setStreamingLearning(null);
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }

      if (error instanceof WorkerError) {
        setNotice(error.message || t.workerError);
      } else {
        setNotice(error instanceof Error ? error.message : t.diagnosisFailed);
      }
    } finally {
      setStreamingLearning(null);
      setBusy(false);
    }
  }

  async function handleRefreshRecommendations() {
    if (!selectedConversation) return;
    try {
      const nextRecommendations = await fetchConversationRecommendations(selectedConversation.id);
      setRecommendationRows(nextRecommendations.items);
    } catch (error) {
      if (error instanceof AuthExpiredError) {
        handleSessionExpired();
        return;
      }
      setNotice(error instanceof Error ? error.message : t.workerError);
    }
  }

  function handleUseRecommendation(item: RecommendationItem) {
    setMessage(item.questionText);
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
            onRefreshRecommendations={handleRefreshRecommendations}
            onUseRecommendation={handleUseRecommendation}
            onSubmitDiagnosis={submitDiagnosis}
            recommendationRows={recommendationRows}
            report={report}
            reportBusy={reportBusy}
            selectedConversation={selectedConversation}
            streamingDiagnosis={streamingDiagnosis}
            t={t}
          />
        )}

        {activeSection === "learning" && (
          <LearningWorkspace
            busy={busy}
            conversation={selectedConversation}
            diagnosis={learningDiagnosis}
            message={message}
            messageHistory={messageHistory}
            onMessageChange={setMessage}
            onRefreshRecommendations={handleRefreshRecommendations}
            onSubmitLearning={submitLearning}
            onUseRecommendation={handleUseRecommendation}
            recommendationRows={recommendationRows}
            streamingDiagnosis={streamingLearning}
            t={t}
          />
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
