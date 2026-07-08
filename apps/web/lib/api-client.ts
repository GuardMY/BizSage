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

type StreamDiagnosisOptions = {
  onPartialAnswer?: (answer: string) => void;
};

type SseEvent = {
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
  token: string;
  username: string;
  role: string;
  regionId: string;
  industryId: string;
  membershipLevel: "INTERNAL" | "FREE" | "SEED_PAID";
  consultationPreferences: string;
};

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
    body: JSON.stringify({ username, password })
  });
  if (!response.ok) throw new Error("Login failed");
  const envelope = (await response.json()) as ApiEnvelope<LoginProfile>;
  return envelope.data;
}

export async function createConversation(token: string, title: string) {
  const response = await fetch(`${API_BASE}/conversations`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ title })
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Create conversation failed");
  return envelope.data;
}

export async function fetchConversations(token: string) {
  const response = await fetch(`${API_BASE}/conversations`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<Conversation[]>(response, "Fetch conversations failed");
  return envelope.data;
}

export async function archiveConversation(token: string, conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/archive`, {
    method: "POST",
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Archive conversation failed");
  return envelope.data;
}

export async function deleteConversation(token: string, conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/delete`, {
    method: "POST",
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Delete conversation failed");
  return envelope.data;
}

export async function fetchMessages(token: string, conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/messages`, {
    headers: authHeaders(token)
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

export async function streamDiagnosisEvents(
  token: string,
  conversationId: number,
  question: string,
  options: StreamDiagnosisOptions = {}
) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/messages/stream`, {
    method: "POST",
    headers: {
      ...authHeaders(token),
      Accept: "text/event-stream"
    },
    body: JSON.stringify({ question })
  });

  if (!response.ok) {
    throw new WorkerError("WORKER_UNREACHABLE", `Diagnosis stream returned HTTP ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new WorkerError("WORKER_ERROR", "Diagnosis stream body is unavailable");
  }

  const decoder = new TextDecoder();
  let text = "";
  let lastPartialAnswer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      text += decoder.decode();
      break;
    }

    text += decoder.decode(value, { stream: true });
    const partialAnswer = readPartialAnswer(text);
    if (partialAnswer != null && partialAnswer !== lastPartialAnswer) {
      lastPartialAnswer = partialAnswer;
      options.onPartialAnswer?.(partialAnswer);
    }
  }

  if (text.includes("event: error")) {
    const errorPayload = findLatestErrorPayload(text);
    if (errorPayload) {
      throw new WorkerError(errorPayload.error, errorPayload.message);
    }
    throw new WorkerError("WORKER_ERROR", "Diagnosis service returned an unknown streaming error");
  }

  return parseDiagnosisEvent(text);
}

export async function streamDiagnosis(token: string, conversationId: number, question: string) {
  return streamDiagnosisEvents(token, conversationId, question);
}

export async function fetchPaidIntelligence(token: string) {
  const response = await fetch(`${API_BASE}/paid-intelligence`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<PaidIntelligence[]>(response, "Fetch paid intelligence failed");
  return envelope.data;
}

export async function fetchDiagnosisReport(token: string, question: string) {
  const response = await fetch(`${API_BASE}/reports/diagnosis?question=${encodeURIComponent(question)}`, {
    headers: authHeaders(token)
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

export async function fetchOpsMetrics(token: string) {
  const response = await fetch(`${API_BASE}/ops/metrics`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<OpsMetrics>(response, "Fetch ops metrics failed");
  return envelope.data;
}


export async function fetchAdminDashboard(token: string) {
  const response = await fetch(`${API_BASE}/admin/dashboard`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminDashboard>(response, "Fetch admin dashboard failed");
  return envelope.data;
}

export async function fetchAdminAlerts(token: string, status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/alerts${query}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminAlert>>(response, "Fetch admin alerts failed");
  return envelope.data;
}

export async function updateAdminAlert(token: string, alertId: number, action: "acknowledge" | "claim" | "close") {
  const response = await fetch(`${API_BASE}/admin/alerts/${alertId}/${action}`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ notes: action })
  });
  const envelope = await readProtectedEnvelope<AdminAlert>(response, "Update admin alert failed");
  return envelope.data;
}

export async function fetchAdminAuditLogs(token: string, query = "") {
  const suffix = query ? `?q=${encodeURIComponent(query)}` : "";
  const response = await fetch(`${API_BASE}/admin/audit-logs${suffix}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminAuditLog>>(response, "Fetch admin audit logs failed");
  return envelope.data;
}

export async function fetchAdminIntelligenceReviews(token: string, status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/intelligence-reviews${query}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminIntelligenceReview>>(response, "Fetch admin reviews failed");
  return envelope.data;
}

export async function decideAdminReview(token: string, reviewId: number, verdict: "PASS" | "REJECT" | "FLAG" | "SUSPICIOUS" | "COMPLIANCE" | "PAID_INTEL" | "ARCHIVE", notes: string) {
  const response = await fetch(`${API_BASE}/admin/intelligence-reviews/${reviewId}/verdict`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ verdict, notes })
  });
  const envelope = await readProtectedEnvelope<AdminIntelligenceReview>(response, "Decide admin review failed");
  return envelope.data;
}

export async function fetchAdminTickets(token: string, status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/tickets${query}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminTicket>>(response, "Fetch admin tickets failed");
  return envelope.data;
}

export async function transitionAdminTicket(token: string, ticketId: number, status: string, nextAction: string) {
  const response = await fetch(`${API_BASE}/admin/tickets/${ticketId}/transition`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ status, nextAction, notes: nextAction })
  });
  const envelope = await readProtectedEnvelope<AdminTicket>(response, "Transition admin ticket failed");
  return envelope.data;
}

export async function fetchAdminHumanIntelligence(token: string, status?: string) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/human-intelligence${query}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminList<AdminHumanIntelligence>>(response, "Fetch human intelligence failed");
  return envelope.data;
}

export async function createAdminHumanIntelligence(
  token: string,
  payload: Pick<AdminHumanIntelligence, "city" | "industryId" | "linkId" | "content" | "sourceType" | "collector" | "entitlement" | "regionId" | "sourceId"> & {
    eventTime: string;
    confidence: number;
  }
) {
  const response = await fetch(`${API_BASE}/admin/human-intelligence`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminHumanIntelligence>(response, "Create human intelligence failed");
  return envelope.data;
}

export async function reviewAdminHumanIntelligence(token: string, id: number, verdict: "PASS" | "REJECT", notes: string) {
  const response = await fetch(`${API_BASE}/admin/human-intelligence/${id}/review`, {
    method: "POST",
    headers: authHeaders(token),
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

export async function fetchAdminKnowledgeNodes(token: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeNode[]>(response, "Fetch admin knowledge nodes failed");
  return envelope.data;
}

export async function fetchAdminKnowledgeDetail(token: string, nodeId: number) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Fetch admin knowledge detail failed");
  return envelope.data;
}

export async function saveAdminKnowledgeDraft(token: string, payload: AdminKnowledgeDraftInput) {
  const response = await fetch(`${API_BASE}/admin/knowledge/drafts`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Save admin knowledge draft failed");
  return envelope.data;
}

export async function submitAdminKnowledgeReview(token: string, nodeId: number, versionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/versions/${versionId}/submit-review`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Submit admin knowledge review failed");
  return envelope.data;
}

export async function approveAdminKnowledgeReview(token: string, nodeId: number, versionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/versions/${versionId}/approve`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Approve admin knowledge review failed");
  return envelope.data;
}

export async function publishAdminKnowledgeVersion(token: string, nodeId: number, versionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/versions/${versionId}/publish`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Publish admin knowledge version failed");
  return envelope.data;
}

export async function rollbackAdminKnowledgeNode(token: string, nodeId: number, targetVersionId: number, notes: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/nodes/${nodeId}/rollback`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ targetVersionId, notes })
  });
  const envelope = await readProtectedEnvelope<AdminKnowledgeDetail>(response, "Rollback admin knowledge node failed");
  return envelope.data;
}

export async function fetchAdminKnowledgeDiff(token: string, leftVersionId: number, rightVersionId: number) {
  const response = await fetch(`${API_BASE}/admin/knowledge/diff?leftVersionId=${leftVersionId}&rightVersionId=${rightVersionId}`, {
    headers: authHeaders(token)
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

export async function fetchAdminKnowledgeInspect(token: string) {
  const response = await fetch(`${API_BASE}/admin/knowledge/inspect`, {
    headers: authHeaders(token)
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

export async function fetchAdminCollectionSources(token: string) {
  const response = await fetch(`${API_BASE}/admin/collection/sources`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionSource[]>(response, "Fetch admin collection sources failed");
  return envelope.data;
}

export async function fetchAdminCollectionSourceDetail(token: string, sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionSourceDetail>(response, "Fetch admin collection source detail failed");
  return envelope.data;
}

export async function saveAdminCollectionSource(token: string, payload: AdminCollectionSourceInput) {
  const response = await fetch(`${API_BASE}/admin/collection/sources`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionSourceDetail>(response, "Save admin collection source failed");
  return envelope.data;
}

export async function fetchAdminCollectionKeywords(token: string, sourceConfigId?: number) {
  const suffix = sourceConfigId ? `?sourceConfigId=${sourceConfigId}` : "";
  const response = await fetch(`${API_BASE}/admin/collection/keywords${suffix}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionKeyword[]>(response, "Fetch admin collection keywords failed");
  return envelope.data;
}

export async function saveAdminCollectionKeyword(token: string, payload: AdminCollectionKeywordInput) {
  const response = await fetch(`${API_BASE}/admin/collection/keywords`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionKeyword>(response, "Save admin collection keyword failed");
  return envelope.data;
}

export async function runAdminCollectionSource(token: string, sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}/run`, {
    method: "POST",
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionJobRun>(response, "Run admin collection source failed");
  return envelope.data;
}

export async function fetchAdminCollectionJobs(token: string, status?: string) {
  const suffix = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await fetch(`${API_BASE}/admin/collection/jobs${suffix}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionJobRun[]>(response, "Fetch admin collection jobs failed");
  return envelope.data;
}

export async function fetchAdminCollectionDeadLetters(token: string) {
  const response = await fetch(`${API_BASE}/admin/collection/dead-letters`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<AdminCollectionDeadLetter[]>(response, "Fetch admin collection dead letters failed");
  return envelope.data;
}

export async function archiveCollectionSource(token: string, sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}/archive`, {
    method: "POST",
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<string>(response, "Archive collection source failed");
  return envelope.data;
}

export async function restoreCollectionSource(token: string, sourceConfigId: number) {
  const response = await fetch(`${API_BASE}/admin/collection/sources/${sourceConfigId}/restore`, {
    method: "POST",
    headers: authHeaders(token)
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

export async function fetchAdminRiskRules(token: string) {
  const response = await fetch(`${API_BASE}/admin/risk-rules`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<RiskRule[]>(response, "Fetch risk rules failed");
  return envelope.data;
}

export async function upsertAdminRiskRule(token: string, payload: RiskRuleUpsertInput) {
  const response = await fetch(`${API_BASE}/admin/risk-rules`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
  const envelope = await readProtectedEnvelope<RiskRule>(response, "Upsert risk rule failed");
  return envelope.data;
}

export async function toggleAdminRiskRule(token: string, ruleId: number) {
  const response = await fetch(`${API_BASE}/admin/risk-rules/${ruleId}/toggle`, {
    method: "POST",
    headers: authHeaders(token)
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
  token: string,
  conversationId: number,
  payload: LearnMessageRequest,
  options: StreamDiagnosisOptions = {}
) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/messages/learn/stream`, {
    method: "POST",
    headers: {
      ...authHeaders(token),
      Accept: "text/event-stream"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new WorkerError("WORKER_UNREACHABLE", `Learning stream returned HTTP ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new WorkerError("WORKER_ERROR", "Learning stream body is unavailable");
  }

  const decoder = new TextDecoder();
  let text = "";
  let lastPartialAnswer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) { text += decoder.decode(); break; }
    text += decoder.decode(value, { stream: true });
    const partialAnswer = readPartialAnswer(text);
    if (partialAnswer != null && partialAnswer !== lastPartialAnswer) {
      lastPartialAnswer = partialAnswer;
      options.onPartialAnswer?.(partialAnswer);
    }
  }

  if (text.includes("event: error")) {
    const errorPayload = findLatestErrorPayload(text);
    if (errorPayload) throw new WorkerError(errorPayload.error, errorPayload.message);
    throw new WorkerError("WORKER_ERROR", "Learning service returned an unknown streaming error");
  }

  return parseDiagnosisEvent(text) as unknown as AgentOutput;
}

export async function streamLearning(
  token: string,
  conversationId: number,
  payload: LearnMessageRequest
) {
  return streamLearningEvents(token, conversationId, payload);
}

/**
 * Stream a dual-Agent mode transition through the Java API.
 */
export async function streamTransitionEvents(
  token: string,
  conversationId: number,
  payload: TransitionMessageRequest,
  options: StreamDiagnosisOptions = {}
) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/messages/transition/stream`, {
    method: "POST",
    headers: {
      ...authHeaders(token),
      Accept: "text/event-stream"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new WorkerError("WORKER_UNREACHABLE", `Transition stream returned HTTP ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new WorkerError("WORKER_ERROR", "Transition stream body is unavailable");
  }

  const decoder = new TextDecoder();
  let text = "";
  let lastPartialAnswer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) { text += decoder.decode(); break; }
    text += decoder.decode(value, { stream: true });
    const partialAnswer = readPartialAnswer(text);
    if (partialAnswer != null && partialAnswer !== lastPartialAnswer) {
      lastPartialAnswer = partialAnswer;
      options.onPartialAnswer?.(partialAnswer);
    }
  }

  if (text.includes("event: error")) {
    const errorPayload = findLatestErrorPayload(text);
    if (errorPayload) throw new WorkerError(errorPayload.error, errorPayload.message);
    throw new WorkerError("WORKER_ERROR", "Transition service returned an unknown streaming error");
  }

  return parseDiagnosisEvent(text) as unknown as AgentOutput;
}

export async function streamTransition(
  token: string,
  conversationId: number,
  payload: TransitionMessageRequest
) {
  return streamTransitionEvents(token, conversationId, payload);
}

/** @deprecated — use streamLearning() which routes through the Java API with proper session management */
export async function fetchAgentLearn(token: string, payload: LearnMessageRequest & { knowledge?: Record<string, unknown>[]; recentMessages?: Record<string, unknown>[]; conversationSummary?: string; longTermMemories?: Record<string, unknown>[]; regionId?: string; industryId?: string; membershipLevel?: string }) {
  const response = await fetch(`${API_BASE}/conversations/0/messages/learn/stream`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new WorkerError("WORKER_UNREACHABLE", `Learning request failed: HTTP ${response.status}`);
  const text = await response.text();
  return parseDiagnosisEvent(text) as unknown as AgentOutput;
}

/** @deprecated — use streamTransition() which routes through the Java API with proper session management */
export async function fetchAgentTransition(token: string, payload: TransitionMessageRequest & { knowledge?: Record<string, unknown>[]; recentMessages?: Record<string, unknown>[]; conversationSummary?: string; longTermMemories?: Record<string, unknown>[]; regionId?: string; industryId?: string; membershipLevel?: string }) {
  const response = await fetch(`${API_BASE}/conversations/0/messages/transition/stream`, {
    method: "POST",
    headers: authHeaders(token),
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

export async function fetchAdminConflicts(token: string, region = "cn-default", industry = "general") {
  const response = await fetch(
    `${API_BASE}/admin/governance/conflicts?region=${region}&industry=${industry}&limit=50`,
    { headers: authHeaders(token) }
  );
  const envelope = await readProtectedEnvelope<AdminList<ConflictResolutionRecord>>(response, "Fetch conflicts failed");
  return envelope.data;
}

export async function fetchAdminFalseLedger(token: string, region = "cn-default", industry = "general") {
  const response = await fetch(
    `${API_BASE}/admin/governance/false-ledger?region=${region}&industry=${industry}&limit=50`,
    { headers: authHeaders(token) }
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
  token: string,
  type = "DAILY",
  region = "cn-default",
  industry = "general"
) {
  const response = await fetch(
    `${API_BASE}/admin/snapshots?type=${type}&region=${region}&industry=${industry}&limit=50`,
    { headers: authHeaders(token) }
  );
  const envelope = await readProtectedEnvelope<SnapshotSummary[]>(response, "Fetch snapshots failed");
  return envelope.data;
}

export async function fetchAdminSnapshotDetail(token: string, id: number) {
  const response = await fetch(`${API_BASE}/admin/snapshots/${id}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<SnapshotDetail>(response, "Fetch snapshot detail failed");
  return envelope.data;
}

export async function fetchAdminSnapshotDiff(token: string, id: number, compare: number) {
  const response = await fetch(`${API_BASE}/admin/snapshots/${id}/diff?compare=${compare}`, {
    headers: authHeaders(token)
  });
  const envelope = await readProtectedEnvelope<SnapshotCompareResult>(response, "Fetch snapshot diff failed");
  return envelope.data;
}

function authHeaders(token: string) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`
  };
}

function readPartialAnswer(raw: string) {
  return findLatestDiagnosisPayload(raw)?.answer ?? null;
}

function parseSseEvents(raw: string): SseEvent[] {
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




