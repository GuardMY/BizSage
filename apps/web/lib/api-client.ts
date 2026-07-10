export type ApiEnvelope<T> = {
  code: string;
  message: string;
  data: T;
  requestId: string;
};

export type Source = {
  id: string;
  title: string;
  sourceUrl: string;
  sourceId: string;
  confidence: number;
  score?: number;
  entitlement?: "FREE" | "PAID";
};

export type SelfCheckStatus =
  | "PASSED"
  | "NEEDS_REVIEW"
  | "INSUFFICIENT_EVIDENCE"
  | "LLM_NOT_CONFIGURED"
  | "LLM_CALL_FAILED";

export type Diagnosis = {
  answer: string;
  sources: Source[];
  confidence: "LOW" | "MEDIUM" | "HIGH";
  timeliness: string;
  selfCheckStatus?: SelfCheckStatus;
  disclaimer: string;
};

export type DiagnosisError = {
  error: "WORKER_UNREACHABLE" | "WORKER_TIMEOUT" | "LLM_NOT_CONFIGURED" | "WORKER_ERROR";
  message: string;
};

export type AgentStreamStatus = {
  state: "started" | "validating" | "retrying";
  mode?: "DIAGNOSIS" | "LEARNING";
  attempt: number;
  message?: string;
};

export type StreamDiagnosisOptions = {
  onPartialAnswer?: (answer: string) => void;
  onStatus?: (status: AgentStreamStatus) => void;
};

export type SseEvent = {
  event: string;
  data: string;
};

export type Conversation = {
  id: number;
  title: string;
  status: string;
  regionId: string;
  industryId: string;
};

export type LoginProfile = {
  username: string;
  role: string;
  regionId: string;
  industryId: string;
  membershipLevel: "INTERNAL" | "FREE" | "SEED_PAID";
  consultationPreferences: string;
  preferredLocale: AppLocale;
};

export type AppLocale = "zh-CN" | "en";

export type PaidIntelligence = {
  id: number;
  title: string;
  status: string;
  sourceId: string;
  regionId: string;
  industryId: string;
  entitlement: "PAID";
};

export type DiagnosisReport = {
  format: "PDF";
  question: string;
  summary: string;
  sources: Source[];
  confidence: "LOW" | "MEDIUM" | "HIGH";
  timeliness: string;
  selfCheckStatus: string;
  disclaimer: string;
};

export type ConversationMessage = {
  id: number;
  conversationId: number;
  sender: "USER" | "ASSISTANT";
  messageType: string;
  content: string;
  sourcesJson: string | null;
  confidence: string | null;
  timeliness: string | null;
  selfCheckStatus: string | null;
  activeContext: boolean;
  summaryGroupId: number | null;
  createTime: string;
};

export type OpsMetrics = {
  grayCohort: string;
  cacheHitRateTarget: number;
  crawlerRtoMinutesTarget: number;
  databaseRecoveryDataLossHoursTarget: number;
  environment: string;
};


export type AdminMetric = {
  key: string;
  label: string;
  value: string;
  status: string;
  detail: string;
};

export type AdminAlert = {
  id: number;
  level: string;
  component: string;
  message: string;
  status: string;
  owner: string | null;
  regionId: string;
  industryId: string;
  createTime: string;
  updateTime: string;
};

export type AdminAuditLog = {
  id: number;
  actor: string;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  regionId: string;
  industryId: string;
  createTime: string;
};

export type AdminIntelligenceReview = {
  id: number;
  intelligenceId: number;
  title: string;
  content: string;
  url: string | null;
  status: string;
  reviewStatus: string;
  verdict: string | null;
  reviewer: string | null;
  reason: string | null;
  confidence: number;
  regionId: string;
  industryId: string;
  sourceId: string;
  createTime: string;
  updateTime: string;
};

export type AdminTicket = {
  id: number;
  ticketType: string;
  severity: string;
  targetType: string;
  targetId: number;
  title: string;
  status: string;
  owner: string | null;
  nextAction: string | null;
  regionId: string;
  industryId: string;
  createTime: string;
  updateTime: string;
};

export type AdminHumanIntelligence = {
  id: number;
  city: string;
  industryId: string;
  linkId: string;
  content: string;
  sourceType: string;
  collector: string;
  eventTime: string | null;
  confidence: number;
  entitlement: string;
  status: string;
  reviewer: string | null;
  reviewNotes: string | null;
  regionId: string;
  sourceId: string;
  createTime: string;
  updateTime: string;
};

export type AdminList<T> = {
  items: T[];
  total: number;
  summary: Record<string, unknown>;
};

export type AdminDashboard = {
  metrics: AdminMetric[];
  urgentAlerts: AdminAlert[];
  openTickets: AdminTicket[];
  pendingReviews: AdminIntelligenceReview[];
  recentAuditLogs: AdminAuditLog[];
};
/** V2: Paginated response from list endpoints. */
export type PagedResponse<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
  hasMore: boolean;
};

export class WorkerError extends Error {
  readonly code: DiagnosisError["error"];

  constructor(code: DiagnosisError["error"], message: string) {
    super(message);
    this.name = "WorkerError";
    this.code = code;
  }
}

export class AuthExpiredError extends Error {
  readonly code = "UNAUTHORIZED";

  constructor(message = "Authentication expired") {
    super(message);
    this.name = "AuthExpiredError";
  }
}

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "/api";

async function readEnvelope<T>(response: Response): Promise<ApiEnvelope<T> | null> {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return null;
  }
  return (await response.json()) as ApiEnvelope<T>;
}

async function readProtectedEnvelope<T>(response: Response, fallbackMessage: string): Promise<ApiEnvelope<T>> {
  const envelope = await readEnvelope<T>(response);

  if (response.status === 401 || envelope?.code === "UNAUTHORIZED") {
    throw new AuthExpiredError(envelope?.message || "authentication required");
  }

  if (!response.ok || !envelope) {
    throw new Error(fallbackMessage);
  }

  return envelope;
}

export function normalizeSources(raw: unknown): Source[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((item) => {
    const value = item as Record<string, unknown>;
    return {
      id: String(value.id ?? ""),
      title: String(value.title ?? ""),
      sourceUrl: String(value.sourceUrl ?? value.source_url ?? ""),
      sourceId: String(value.sourceId ?? value.source_id ?? ""),
      confidence: Number(value.confidence ?? 0),
      score: value.score === undefined ? undefined : Number(value.score),
      entitlement: value.entitlement === "PAID" ? "PAID" : "FREE"
    };
  });
}

export async function login(username: string, password: string) {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",  // V2: accept httpOnly cookie
    body: JSON.stringify({ username, password })
  });
  if (!response.ok) throw new Error("Login failed");
  const envelope = (await response.json()) as ApiEnvelope<LoginProfile>;
  return envelope.data;
}

/** V2: Restore user profile from httpOnly cookie via the /me endpoint.
 *  Replaces localStorage-based profile restoration. */
export async function fetchMe() {
  const response = await fetch(`${API_BASE}/users/me`, {
    headers: authHeaders(),
    credentials: "include",
  });
  const envelope = await readProtectedEnvelope<LoginProfile>(response, "Fetch profile failed");
  return envelope.data;
}

export async function updatePreferredLocale(preferredLocale: AppLocale) {
  const response = await fetch(`${API_BASE}/users/me/locale`, {
    method: "PUT",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ preferredLocale })
  });
  const envelope = await readProtectedEnvelope<LoginProfile>(response, "Update language failed");
  return envelope.data;
}

/** V2: Log out by clearing the httpOnly cookie server-side. */
export async function logout() {
  await fetch(`${API_BASE}/auth/logout`, {
    method: "POST",
    credentials: "include",
  });
}

export async function createConversation(title: string) {
  const response = await fetch(`${API_BASE}/conversations`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ title })
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Create conversation failed");
  return envelope.data;
}

export async function fetchConversations(page = 1, size = 50) {
  const response = await fetch(`${API_BASE}/conversations?page=${page}&size=${size}`, {
    headers: authHeaders(),
    credentials: "include",
  });
  const envelope = await readProtectedEnvelope<PagedResponse<Conversation>>(response, "Fetch conversations failed");
  return envelope.data;
}

export async function archiveConversation(conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/archive`, {
    method: "POST",
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Archive conversation failed");
  return envelope.data;
}

export async function deleteConversation(conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/delete`, {
    method: "POST",
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Delete conversation failed");
  return envelope.data;
}

export async function fetchMessages(conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/messages`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<ConversationMessage[]>(response, "Fetch messages failed");
  return envelope.data;
}

export function parseDiagnosisEvent(raw: string): Diagnosis {
  const errorPayload = findLatestErrorPayload(raw);
  if (errorPayload) {
    throw new WorkerError(errorPayload.error, errorPayload.message);
  }

  const payload = findLatestDiagnosisPayload(raw);
  if (!payload) throw new WorkerError("WORKER_ERROR", "Diagnosis stream did not contain a data event");

  if (payload.selfCheckStatus === "LLM_NOT_CONFIGURED") {
    throw new WorkerError("LLM_NOT_CONFIGURED", "AI engine is not configured");
  }
  if (payload.selfCheckStatus === "LLM_CALL_FAILED") {
    throw new WorkerError("WORKER_ERROR", "Upstream model call failed");
  }

  return {
    ...payload,
    sources: normalizeSources(payload.sources)
  };
}

async function streamSse<T>(
  url: string,
  body: unknown,
  parseResult: (raw: string) => T,
  errorContext: string,
  options: StreamDiagnosisOptions = {}
): Promise<T> {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      ...authHeaders(),
      Accept: "text/event-stream"
    },
    credentials: "include",
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    throw new WorkerError("WORKER_UNREACHABLE", `${errorContext} stream returned HTTP ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new WorkerError("WORKER_ERROR", `${errorContext} stream body is unavailable`);
  }

  const frameDecoder = new SseFrameDecoder();
  let partialAnswer = "";
  let finalEvent = "";

  const handleEvent = (event: SseEvent) => {
    if (event.event === "delta") {
      const payload = JSON.parse(event.data) as { text?: unknown };
      if (typeof payload.text === "string") {
        partialAnswer += payload.text;
        options.onPartialAnswer?.(partialAnswer);
      }
      return;
    }
    if (event.event === "reset") {
      partialAnswer = "";
      options.onPartialAnswer?.("");
      return;
    }
    if (event.event === "status") {
      options.onStatus?.(JSON.parse(event.data) as AgentStreamStatus);
      return;
    }
    if (event.event === "diagnosis") {
      finalEvent = `event: diagnosis\ndata: ${event.data}\n\n`;
      return;
    }
    if (event.event === "error") {
      let payload: DiagnosisError | null = null;
      try {
        payload = JSON.parse(event.data) as DiagnosisError;
      } catch {
        // The stable fallback below handles malformed worker errors.
      }
      throw new WorkerError(
        payload?.error ?? "WORKER_ERROR",
        payload?.message ?? `${errorContext} service returned an unknown streaming error`
      );
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      for (const event of frameDecoder.finish()) handleEvent(event);
      break;
    }
    for (const event of frameDecoder.push(value)) handleEvent(event);
  }

  if (!finalEvent) {
    throw new WorkerError("WORKER_ERROR", `${errorContext} stream ended without a final diagnosis event`);
  }
  return parseResult(finalEvent);
}

export async function streamDiagnosisEvents(
  conversationId: number,
  question: string,
  options: StreamDiagnosisOptions = {}
) {
  return streamSse(
    `${API_BASE}/conversations/${conversationId}/messages/stream`,
    { question },
    parseDiagnosisEvent,
    "Diagnosis",
    options
  );
}

export async function streamDiagnosis(conversationId: number, question: string) {
  return streamDiagnosisEvents(conversationId, question);
}

export async function fetchPaidIntelligence() {
  const response = await fetch(`${API_BASE}/paid-intelligence`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<PaidIntelligence[]>(response, "Fetch paid intelligence failed");
  return envelope.data;
}

export async function fetchDiagnosisReport(question: string) {
  const response = await fetch(`${API_BASE}/reports/diagnosis?question=${encodeURIComponent(question)}`, {
    headers: authHeaders()
  });

  if (!response.ok) {
    const envelope = await readEnvelope<DiagnosisReport>(response);
    if (response.status === 401 || envelope?.code === "UNAUTHORIZED") {
      throw new AuthExpiredError(envelope?.message || "authentication required");
    }
    if (response.status === 503) {
      throw new WorkerError("WORKER_UNREACHABLE", "Diagnosis report generation failed because AI is unavailable");
    }
    throw new Error(`Fetch diagnosis report failed (HTTP ${response.status})`);
  }

  const envelope = (await response.json()) as ApiEnvelope<DiagnosisReport>;
  return envelope.data;
}

/** V2: Download a diagnosis report as a PDF file. Triggers a browser download. */
export async function downloadDiagnosisPdf(question: string) {
  const response = await fetch(`${API_BASE}/reports/diagnosis/pdf?question=${encodeURIComponent(question)}`, {
    headers: authHeaders()
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new AuthExpiredError("authentication required");
    }
    if (response.status === 503) {
      throw new WorkerError("WORKER_UNREACHABLE", "PDF generation failed because AI is unavailable");
    }
    throw new Error(`PDF download failed (HTTP ${response.status})`);
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "BizSage-Diagnosis-Report.pdf";
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  window.URL.revokeObjectURL(url);
}

export async function fetchOpsMetrics() {
  const response = await fetch(`${API_BASE}/ops/metrics`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<OpsMetrics>(response, "Fetch ops metrics failed");
  return envelope.data;
}

export type CacheStats = {
  caches: Record<string, { hits: number; misses: number; total: number; hitRate: string }>;
  aggregateHitRate: string;
  targetHitRate: number;
  meetsTarget: boolean;
};

export async function fetchOpsCacheStats() {
  const response = await fetch(`${API_BASE}/ops/cache-stats`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<CacheStats>(response, "Fetch cache stats failed");
  return envelope.data;
}

export type SlaStats = {
  window: string;
  from: string;
  to: string;
  dataPoints: number;
  uptime: string;
  uptimePercent: string;
  errorRate: string;
  errorRatePercent: string;
  latencyP50Ms: string;
  latencyP95Ms: string;
  latencyP99Ms: string;
  targetUptime: string;
  slaMet: boolean;
};

export async function fetchOpsSla(window: string = "24h") {
  const response = await fetch(`${API_BASE}/ops/sla?window=${encodeURIComponent(window)}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<SlaStats>(response, "Fetch SLA stats failed");
  return envelope.data;
}


export async function fetchAdminDashboard() {
  const response = await fetch(`${API_BASE}/admin/dashboard`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminDashboard>(response, "Fetch admin dashboard failed");
  return envelope.data;
}

export async function fetchAdminAlerts(status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/alerts${query}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminAlert>>(response, "Fetch admin alerts failed");
  return envelope.data;
}

export async function updateAdminAlert(alertId: number, action: "acknowledge" | "claim" | "close") {
  const response = await fetch(`${API_BASE}/admin/alerts/${alertId}/${action}`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ notes: action })
  });
  const envelope = await readProtectedEnvelope<AdminAlert>(response, "Update admin alert failed");
  return envelope.data;
}

export async function fetchAdminAuditLogs(query = "") {
  const suffix = query ? `?q=${encodeURIComponent(query)}` : "";
  const response = await fetch(`${API_BASE}/admin/audit-logs${suffix}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminAuditLog>>(response, "Fetch admin audit logs failed");
  return envelope.data;
}

export async function fetchAdminIntelligenceReviews(status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/intelligence-reviews${query}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminIntelligenceReview>>(response, "Fetch admin reviews failed");
  return envelope.data;
}

export async function decideAdminReview(reviewId: number, verdict: "PASS" | "REJECT" | "FLAG" | "SUSPICIOUS" | "COMPLIANCE" | "PAID_INTEL" | "ARCHIVE", notes: string) {
  const response = await fetch(`${API_BASE}/admin/intelligence-reviews/${reviewId}/verdict`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ verdict, notes })
  });
  const envelope = await readProtectedEnvelope<AdminIntelligenceReview>(response, "Decide admin review failed");
  return envelope.data;
}

export async function fetchAdminTickets(status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/tickets${query}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminTicket>>(response, "Fetch admin tickets failed");
  return envelope.data;
}

export async function transitionAdminTicket(ticketId: number, status: string, nextAction: string) {
  const response = await fetch(`${API_BASE}/admin/tickets/${ticketId}/transition`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ status, nextAction, notes: nextAction })
  });
  const envelope = await readProtectedEnvelope<AdminTicket>(response, "Transition admin ticket failed");
  return envelope.data;
}

export async function fetchAdminHumanIntelligence(status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/human-intelligence${query}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminHumanIntelligence>>(response, "Fetch human intelligence failed");
  return envelope.data;
}

export async function createAdminHumanIntelligence(
  payload: Pick<AdminHumanIntelligence, "city" | "industryId" | "linkId" | "content" | "sourceType" | "collector" | "entitlement" | "regionId" | "sourceId"> & {
    eventTime: string;
    confidence: number;
  }
) {
  const response = await fetch(`${API_BASE}/admin/human-intelligence`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminHumanIntelligence>(response, "Create human intelligence failed");
  return envelope.data;
}

export async function reviewAdminHumanIntelligence(id: number, verdict: "PASS" | "REJECT", notes: string) {
  const response = await fetch(`${API_BASE}/admin/human-intelligence/${id}/review`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ verdict, notes })
  });
  const envelope = await readProtectedEnvelope<AdminHumanIntelligence>(response, "Review human intelligence failed");
  return envelope.data;
}

export type AdminKnowledgeNode = {
  nodeId: number;
  title: string;
  slug: string;
  industryId: string;
  regionId: string;
  linkId: string;
  status: string;
  publishedVersionId: number | null;
  versionCount: number;
  updateTime: string;
};

export type AdminKnowledgeVersion = {
  versionId: number;
  nodeId: number;
  versionNumber: number;
  title: string;
  summary: string;
  content: string;
  sourceUrl: string | null;
  reviewStatus: string;
  author: string;
  reviewer: string | null;
  reviewNotes: string | null;
  changeNotes: string | null;
  confidence: number;
  createdByAction: string;
  createTime: string;
  updateTime: string;
};

export type AdminKnowledgePublication = {
  publicationId: number;
  nodeId: number;
  versionId: number;
  action: string;
  actor: string;
  notes: string | null;
  createTime: string;
};

export type AdminKnowledgeDetail = {
  nodeId: number;
  title: string;
  slug: string;
  industryId: string;
  regionId: string;
  linkId: string;
  status: string;
  draftVersionId: number | null;
  reviewVersionId: number | null;
  publishedVersionId: number | null;
  versions: AdminKnowledgeVersion[];
  publications: AdminKnowledgePublication[];
};

export type AdminKnowledgeDiff = {
  leftVersionId: number;
  rightVersionId: number;
  leftTitle: string;
  rightTitle: string;
  leftSummary: string;
  rightSummary: string;
  leftContent: string;
  rightContent: string;
  leftSourceUrl: string | null;
  rightSourceUrl: string | null;
  leftConfidence: number;
  rightConfidence: number;
  leftReviewStatus: string;
  rightReviewStatus: string;
};

export type AdminKnowledgeDraftInput = {
  nodeId?: number;
  title: string;
  slug: string;
  industryId: string;
  regionId: string;
  linkId: string;
  summary: string;
  content: string;
  sourceUrl: string;
  confidence: number;
  changeNotes: string;
};

export async function fetchAdminKnowledgeNodes() {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeNode[]>(response, "Fetch admin knowledge nodes failed");
  return envelope.data;
}

export async function fetchAdminKnowledgeDetail(nodeId: number) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Fetch admin knowledge detail failed");
  return envelope.data;
}

export async function saveAdminKnowledgeDraft(payload: AdminKnowledgeDraftInput) {
  const response = await fetch(`${API_BASE}/admin/knowledge/drafts`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Save admin knowledge draft failed");
  return envelope.data;
}

export async function submitAdminKnowledgeReview(nodeId: number, versionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/versions/${versionId}/submit-review`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Submit admin knowledge review failed");
  return envelope.data;
}

export async function approveAdminKnowledgeReview(nodeId: number, versionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/versions/${versionId}/approve`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Approve admin knowledge review failed");
  return envelope.data;
}

export async function publishAdminKnowledgeVersion(nodeId: number, versionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/versions/${versionId}/publish`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Publish admin knowledge version failed");
  return envelope.data;
}

export async function rollbackAdminKnowledgeNode(nodeId: number, targetVersionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/rollback`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify({ targetVersionId, notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Rollback admin knowledge node failed");
  return envelope.data;
}

export async function fetchAdminKnowledgeDiff(leftVersionId: number, rightVersionId: number) {
  const response = await fetch(`${API_BASE}/admin/knowledge/diff?leftVersionId=${leftVersionId}&rightVersionId=${rightVersionId}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDiff>(response, "Fetch admin knowledge diff failed");
  return envelope.data;
}

export type InspectionFinding = {
  type: string;
  nodeId: number;
  title: string;
  detail: string;
};

export type InspectionReport = {
  findings: InspectionFinding[];
  totalNodes: number;
  healthyNodes: number;
  warningNodes: number;
  criticalNodes: number;
};

export async function fetchAdminKnowledgeInspect() {
  const response = await fetch(`${API_BASE}/admin/knowledge/inspect`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<InspectionReport>(response, "Fetch admin knowledge inspection failed");
  return envelope.data;
}

export type AdminCollectionSource = {
  sourceConfigId: number;
  name: string;
  sourceType: string;
  status: string;
  intervalMinutes: number;
  maxRetries: number;
  failureThreshold: number;
  cooldownMinutes: number;
  circuitState: string;
  failureCount: number;
  regionId: string;
  industryId: string;
  linkId: string;
  sourceId: string;
  payloadJson: string;
  nextRunTime: string | null;
  lastRunTime: string | null;
  lastStatus: string;
  lastError: string | null;
  createTime: string;
  updateTime: string;
};

export type AdminCollectionKeyword = {
  keywordId: number;
  sourceConfigId: number;
  keyword: string;
  matchMode: string;
  status: string;
  notes: string | null;
  createTime: string;
  updateTime: string;
};

export type AdminCollectionJobRun = {
  runId: number;
  jobId: number;
  sourceConfigId: number;
  sourceName: string;
  sourceType: string;
  triggerType: string;
  status: string;
  queueDepth: number;
  retryCount: number;
  circuitState: string;
  recordsCollected: number;
  recordsFiltered: number;
  recordsPersisted: number;
  errorMessage: string | null;
  startTime: string;
  finishTime: string | null;
};

export type AdminCollectionDeadLetter = {
  deadLetterId: number;
  jobId: number | null;
  sourceName: string | null;
  reason: string;
  errorMessage: string;
  payloadJson: string | null;
  createTime: string;
};

export type AdminCollectionSourceDetail = {
  source: AdminCollectionSource;
  keywords: AdminCollectionKeyword[];
  recentRuns: AdminCollectionJobRun[];
};

export type AdminCollectionSourceInput = {
  sourceConfigId?: number;
  name: string;
  sourceType: string;
  status: string;
  intervalMinutes: number;
  maxRetries: number;
  failureThreshold: number;
  cooldownMinutes: number;
  regionId: string;
  industryId: string;
  linkId: string;
  sourceId: string;
  payloadJson: string;
};

export type AdminCollectionKeywordInput = {
  keywordId?: number;
  sourceConfigId: number;
  keyword: string;
  matchMode: string;
  status: string;
  notes: string;
};

export async function fetchAdminCollectionSources() {
  const response = await fetch(`${API_BASE}/admin/collection/sources`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminCollectionSource[]>(response, "Fetch admin collection sources failed");
  return envelope.data;
}

export async function fetchAdminCollectionSourceDetail(sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminCollectionSourceDetail>(response, "Fetch admin collection source detail failed");
  return envelope.data;
}

export async function saveAdminCollectionSource(payload: AdminCollectionSourceInput) {
  const response = await fetch(`${API_BASE}/admin/collection/sources`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionSourceDetail>(response, "Save admin collection source failed");
  return envelope.data;
}

export async function fetchAdminCollectionKeywords(sourceConfigId?: number) {
  const suffix = sourceConfigId ? `?sourceConfigId=${sourceConfigId}` : "";
  const response = await fetch(`${API_BASE}/admin/collection/keywords${suffix}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminCollectionKeyword[]>(response, "Fetch admin collection keywords failed");
  return envelope.data;
}

export async function saveAdminCollectionKeyword(payload: AdminCollectionKeywordInput) {
  const response = await fetch(`${API_BASE}/admin/collection/keywords`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionKeyword>(response, "Save admin collection keyword failed");
  return envelope.data;
}

export async function runAdminCollectionSource(sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}/run`, {
    method: "POST",
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminCollectionJobRun>(response, "Run admin collection source failed");
  return envelope.data;
}

export async function fetchAdminCollectionJobs(status?: string) {
  const suffix = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/collection/jobs${suffix}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminCollectionJobRun[]>(response, "Fetch admin collection jobs failed");
  return envelope.data;
}

export async function fetchAdminCollectionDeadLetters() {
  const response = await fetch(`${API_BASE}/admin/collection/dead-letters`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<AdminCollectionDeadLetter[]>(response, "Fetch admin collection dead letters failed");
  return envelope.data;
}

/** V2: Collection telemetry for monitoring dashboards. */
export type CollectionTelemetry = {
  totalSources: number;
  enabledSources: number;
  openCircuits: number;
  deadLetterCount: number;
  totalRuns24h: number;
  successRate24h: string;
  recordsCollected24h: number;
  recentRuns: { status: string; records_collected: number; start_time: string; source_name: string }[];
};

export async function fetchAdminCollectionTelemetry() {
  const response = await fetch(`${API_BASE}/admin/collection/telemetry`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<CollectionTelemetry>(response, "Fetch collection telemetry failed");
  return envelope.data;
}

export async function archiveCollectionSource(sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}/archive`, {
    method: "POST",
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<string>(response, "Archive collection source failed");
  return envelope.data;
}

export async function restoreCollectionSource(sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}/restore`, {
    method: "POST",
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<string>(response, "Restore collection source failed");
  return envelope.data;
}

export type RiskRule = {
  id: number;
  ruleType: string;
  name: string;
  description: string;
  enabled: boolean;
  thresholdValue: number | null;
  scopeJson: string | null;
  riskLevel: string;
  changeMode: string;
  version: number;
  createTime: string;
  updateTime: string;
};

export type RiskRuleUpsertInput = {
  id?: number;
  ruleType: string;
  name: string;
  description: string;
  enabled: boolean;
  thresholdValue: number | null;
  scopeJson: string | null;
  riskLevel: string;
  changeMode: string;
};

export async function fetchAdminRiskRules() {
  const response = await fetch(`${API_BASE}/admin/risk-rules`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<RiskRule[]>(response, "Fetch risk rules failed");
  return envelope.data;
}

export async function upsertAdminRiskRule(payload: RiskRuleUpsertInput) {
  const response = await fetch(`${API_BASE}/admin/risk-rules`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<RiskRule>(response, "Upsert risk rule failed");
  return envelope.data;
}

export async function toggleAdminRiskRule(ruleId: number) {
  const response = await fetch(`${API_BASE}/admin/risk-rules/${ruleId}/toggle`, {
    method: "POST",
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<RiskRule>(response, "Toggle risk rule failed");
  return envelope.data;
}
/* --- Dual-Agent: Learning & Transition --- */

export type LearnMessageRequest = {
  question: string;
  chainNodeId?: string;
  learningMode?: "FAST_START" | "FULL_CHAIN" | "NODE_DEEP_DIVE";
};

export type TransitionMessageRequest = {
  fromMode: "LEARNING" | "DIAGNOSIS";
  toMode: "LEARNING" | "DIAGNOSIS";
  question: string;
  chainNodeId?: string;
};

export type AgentOutputSections = {
  keyFindings: string;
  riskAlerts: string;
  actionableSteps: string;
  supportingEvidence: string;
};

export type AgentOutput = {
  mode: "LEARNING" | "DIAGNOSIS";
  answer: string;
  sections: AgentOutputSections;
  sources: Source[];
  confidence: string;
  timeliness: string;
  selfCheckStatus: string;
  disclaimer: string;
  chainNodeId?: string;
  suggestedActions?: string[];
  memoryCandidates?: Record<string, unknown>[];
};

/**
 * Stream learning events from the Java API (which delegates to AI Worker).
 * Follows the same SSE pattern as streamDiagnosisEvents.
 */
export async function streamLearningEvents(
  conversationId: number,
  payload: LearnMessageRequest,
  options: StreamDiagnosisOptions = {}
) {
  return streamSse(
    `${API_BASE}/conversations/${conversationId}/messages/learn/stream`,
    payload,
    (raw) => parseDiagnosisEvent(raw) as unknown as AgentOutput,
    "Learning",
    options
  );
}

export async function streamLearning(
  conversationId: number,
  payload: LearnMessageRequest
) {
  return streamLearningEvents(conversationId, payload);
}

/**
 * Stream a dual-Agent mode transition through the Java API.
 */
export async function streamTransitionEvents(
  conversationId: number,
  payload: TransitionMessageRequest,
  options: StreamDiagnosisOptions = {}
) {
  return streamSse(
    `${API_BASE}/conversations/${conversationId}/messages/transition/stream`,
    payload,
    (raw) => parseDiagnosisEvent(raw) as unknown as AgentOutput,
    "Transition",
    options
  );
}

export async function streamTransition(
  conversationId: number,
  payload: TransitionMessageRequest
) {
  return streamTransitionEvents(conversationId, payload);
}

/** @deprecated — use streamLearning() which routes through the Java API with proper session management */
export async function fetchAgentLearn(payload: LearnMessageRequest & { knowledge?: Record<string, unknown>[]; recentMessages?: Record<string, unknown>[]; conversationSummary?: string; longTermMemories?: Record<string, unknown>[]; regionId?: string; industryId?: string; membershipLevel?: string }) {
  const response = await fetch(`${API_BASE}/conversations/0/messages/learn/stream`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new WorkerError("WORKER_UNREACHABLE", `Learning request failed: HTTP ${response.status}`);
  const text = await response.text();
  return parseDiagnosisEvent(text) as unknown as AgentOutput;
}

/** @deprecated — use streamTransition() which routes through the Java API with proper session management */
export async function fetchAgentTransition(payload: TransitionMessageRequest & { knowledge?: Record<string, unknown>[]; recentMessages?: Record<string, unknown>[]; conversationSummary?: string; longTermMemories?: Record<string, unknown>[]; regionId?: string; industryId?: string; membershipLevel?: string }) {
  const response = await fetch(`${API_BASE}/conversations/0/messages/transition/stream`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include",
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new WorkerError("WORKER_UNREACHABLE", `Transition request failed: HTTP ${response.status}`);
  const text = await response.text();
  return parseDiagnosisEvent(text) as unknown as AgentOutput;
}

/* --- Governance / Conflict Engine --- */

export type ConflictResolutionRecord = {
  id: number;
  incomingIntelligenceId: number | null;
  existingIntelligenceId: number | null;
  conflictBranch: string;
  simHashDistance: number;
  incomingWeight: number;
  existingWeight: number;
  routingAction: string;
  reviewTicketId: number | null;
  resolvedBy: string;
  notes: string;
  regionId: string;
  industryId: string;
  linkId: string;
  createTime: string;
};

export type FalseLedgerItem = {
  id: number;
  originalIntelligenceId: number | null;
  title: string;
  contentHash: string;
  conflictReason: string;
  matchedRumorKeyword: string | null;
  regionId: string;
  industryId: string;
  archivedBy: string;
  createTime: string;
};

export async function fetchAdminConflicts(region = "cn-default", industry = "general") {
  const response = await fetch(
    `${API_BASE}/admin/governance/conflicts?region=${region}&industry=${industry}&limit=50`,
    { headers: authHeaders(), credentials: "include" }
  );
  const envelope = await readProtectedEnvelope<AdminList<ConflictResolutionRecord>>(response, "Fetch conflicts failed");
  return envelope.data;
}

export async function fetchAdminFalseLedger(region = "cn-default", industry = "general") {
  const response = await fetch(
    `${API_BASE}/admin/governance/false-ledger?region=${region}&industry=${industry}&limit=50`,
    { headers: authHeaders(), credentials: "include" }
  );
  const envelope = await readProtectedEnvelope<AdminList<FalseLedgerItem>>(response, "Fetch false ledger failed");
  return envelope.data;
}

/* --- Governance / Snapshots --- */

export type SnapshotSummary = {
  id: number;
  snapshotType: string;
  scopeKey: string;
  regionId: string;
  industryId: string;
  retentionDays: number;
  recordCount: number;
  expiresAt: string | null;
  createTime: string;
};

export type SnapshotDetail = SnapshotSummary & {
  payloadJson: string;
  parentSnapshotId: number | null;
};

export type SnapshotCompareResult = {
  leftSnapshotId: number;
  rightSnapshotId: number;
  added: string[];
  removed: string[];
  changed: string[];
};

export async function fetchAdminSnapshots(
  type = "DAILY",
  region = "cn-default",
  industry = "general"
) {
  const response = await fetch(
    `${API_BASE}/admin/snapshots?type=${type}&region=${region}&industry=${industry}&limit=50`,
    { headers: authHeaders(), credentials: "include" }
  );
  const envelope = await readProtectedEnvelope<SnapshotSummary[]>(response, "Fetch snapshots failed");
  return envelope.data;
}

export async function fetchAdminSnapshotDetail(id: number) {
  const response = await fetch(`${API_BASE}/admin/snapshots/${id}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<SnapshotDetail>(response, "Fetch snapshot detail failed");
  return envelope.data;
}

export async function fetchAdminSnapshotDiff(id: number, compare: number) {
  const response = await fetch(`${API_BASE}/admin/snapshots/${id}/diff?compare=${compare}`, {
    headers: authHeaders()
  });
  const envelope = await readProtectedEnvelope<SnapshotCompareResult>(response, "Fetch snapshot diff failed");
  return envelope.data;
}

function authHeaders(): Record<string, string> {
  return {
    "Content-Type": "application/json"
  };
}

export class SseFrameDecoder {
  private readonly decoder = new TextDecoder();
  private buffer = "";

  push(value: Uint8Array): SseEvent[] {
    this.buffer += this.decoder.decode(value, { stream: true });
    return this.drain(false);
  }

  finish(): SseEvent[] {
    this.buffer += this.decoder.decode();
    return this.drain(true);
  }

  private drain(includeRemainder: boolean): SseEvent[] {
    const events: SseEvent[] = [];
    let boundary = this.buffer.match(/\r?\n\r?\n/);
    while (boundary?.index !== undefined) {
      const end = boundary.index + boundary[0].length;
      events.push(...parseSseEvents(this.buffer.slice(0, end)));
      this.buffer = this.buffer.slice(end);
      boundary = this.buffer.match(/\r?\n\r?\n/);
    }
    if (includeRemainder && this.buffer.trim()) {
      events.push(...parseSseEvents(`${this.buffer}\n\n`));
      this.buffer = "";
    }
    return events;
  }
}

export function parseSseEvents(raw: string): SseEvent[] {
  const events: SseEvent[] = [];

  for (const block of raw.split(/\r?\n\r?\n/)) {
    const lines = block.split(/\r?\n/);
    let event = "message";
    const dataLines: string[] = [];

    for (const line of lines) {
      if (line.startsWith("event:")) {
        event = line.slice("event:".length).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice("data:".length).trimStart());
      }
    }

    if (dataLines.length > 0) {
      events.push({ event, data: dataLines.join("\n") });
    }
  }

  return events;
}

function findLatestDiagnosisPayload(raw: string): Diagnosis | null {
  const events = parseSseEvents(raw);
  for (let index = events.length - 1; index >= 0; index -= 1) {
    if (events[index].event !== "diagnosis") continue;
    try {
      const payload = JSON.parse(events[index].data) as Diagnosis;
      return {
        ...payload,
        sources: normalizeSources(payload.sources)
      };
    } catch {
      continue;
    }
  }
  return null;
}

function findLatestErrorPayload(raw: string): DiagnosisError | null {
  const events = parseSseEvents(raw);
  for (let index = events.length - 1; index >= 0; index -= 1) {
    if (events[index].event !== "error") continue;
    try {
      return JSON.parse(events[index].data) as DiagnosisError;
    } catch {
      continue;
    }
  }
  return null;
}

function decodeEscapedCharacter(char: string) {
  switch (char) {
    case "\"":
      return "\"";
    case "\\":
      return "\\";
    case "/":
      return "/";
    case "b":
      return "\b";
    case "f":
      return "\f";
    case "n":
      return "\n";
    case "r":
      return "\r";
    case "t":
      return "\t";
    default:
      return char;
  }
}



