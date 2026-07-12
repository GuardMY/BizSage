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
  workflowStage?: string;
  profileMissingFields?: string[];
  completionSignal?: string;
  recommendedQuestions?: RecommendationItem[];
  recommendationCandidates?: RecommendationItem[];
  currentTopic?: string | null;
  nextBestTopics?: string[];
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
  agentMode?: string;
  workflowStage?: string;
  profileCompleteness?: number;
  primaryIssueTags?: string | null;
  recommendedQuestionIds?: string | null;
  closedBy?: string | null;
  closedReason?: string | null;
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
    headers: authHeaders(),
    credentials: "include"
  });
  const envelope = await readProtectedEnvelope<Conversation>(response, "Archive conversation failed");
  return envelope.data;
}

export async function deleteConversation(conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/delete`, {
    method: "POST",
    headers: authHeaders(),
    credentials: "include"
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

export type RecommendationResponse = {
  agentMode: string;
  workflowStage: string;
  refreshAvailable: boolean;
  items: RecommendationItem[];
};

export async function fetchConversationRecommendations(conversationId: number) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/recommendations`, {
    headers: authHeaders(),
    credentials: "include"
  });
  const envelope = await readProtectedEnvelope<RecommendationResponse>(response, "Fetch recommendations failed");
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
  confidence: "LOW" | "MEDIUM" | "HIGH";
  timeliness: string;
  selfCheckStatus: SelfCheckStatus;
  disclaimer: string;
  chainNodeId?: string;
  suggestedActions?: string[];
  memoryCandidates?: Record<string, unknown>[];
  recommendationCandidates?: RecommendationItem[];
  currentTopic?: string | null;
  nextBestTopics?: string[];
  workflowStage?: string;
  profileMissingFields?: string[];
  completionSignal?: string;
  recommendedQuestions?: RecommendationItem[];
};

export type RecommendationItem = {
  id: number;
  questionKey: string;
  category: string;
  questionText: string;
  score: number;
  topLevelScore: number;
  usageCount: number;
  ratingAvg: number;
  ratingCount: number;
  sourceType: string;
  sourceRef: string;
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



