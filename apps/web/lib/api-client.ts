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

export async function decideAdminReview(token: string, reviewId: number, verdict: "PASS" | "REJECT" | "FLAG", notes: string) {
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
