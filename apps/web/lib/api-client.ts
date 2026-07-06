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
  const lines = raw.split(/\r?\n/);
  const eventLine = lines.find((line) => line.startsWith("event:"));
  if (eventLine && eventLine.includes("error")) {
    const dataLine = lines.find((line) => line.startsWith("data:"));
    if (dataLine) {
      const errorPayload = JSON.parse(dataLine.slice("data: ".length)) as DiagnosisError;
      throw new WorkerError(errorPayload.error, errorPayload.message);
    }
    throw new WorkerError("WORKER_ERROR", "AI worker returned an error");
  }

  const dataLine = lines.find((line) => line.startsWith("data:"));
  if (!dataLine) throw new WorkerError("WORKER_ERROR", "Diagnosis stream did not contain a data event");
  const payload = JSON.parse(dataLine.slice("data: ".length)) as Diagnosis;

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
    const dataMatch = text.match(/data:\s*(\{.+?\})/);
    if (dataMatch) {
      try {
        const errorPayload = JSON.parse(dataMatch[1]) as DiagnosisError;
        throw new WorkerError(errorPayload.error, errorPayload.message);
      } catch (error) {
        if (error instanceof WorkerError) throw error;
      }
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

function authHeaders(token: string) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`
  };
}

function readPartialAnswer(raw: string) {
  const marker = '"answer":"';
  const start = raw.indexOf(marker);
  if (start === -1) {
    return null;
  }

  let current = "";
  let escaping = false;

  for (let index = start + marker.length; index < raw.length; index += 1) {
    const char = raw[index];

    if (escaping) {
      if (char === "u") {
        const codePoint = raw.slice(index + 1, index + 5);
        if (codePoint.length < 4 || /[^0-9a-f]/i.test(codePoint)) {
          break;
        }
        current += String.fromCharCode(Number.parseInt(codePoint, 16));
        index += 4;
      } else {
        current += decodeEscapedCharacter(char);
      }
      escaping = false;
      continue;
    }

    if (char === "\\") {
      escaping = true;
      continue;
    }

    if (char === "\"") {
      return current;
    }

    current += char;
  }

  return current;
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
