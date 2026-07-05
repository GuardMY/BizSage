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

/**
 * Self-check status values from the AI worker.
 * - PASSED: normal success
 * - NEEDS_REVIEW: conflicting evidence, needs human review
 * - INSUFFICIENT_EVIDENCE: not enough knowledge to answer
 * - LLM_NOT_CONFIGURED: worker is up but no LLM key configured
 * - LLM_CALL_FAILED: worker is up but LLM call errored
 */
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

/**
 * Structured error received from the SSE stream or API response
 * when the AI worker or LLM is unavailable.
 */
export type DiagnosisError = {
  error: "WORKER_UNREACHABLE" | "WORKER_TIMEOUT" | "LLM_NOT_CONFIGURED" | "WORKER_ERROR";
  message: string;
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

export type OpsMetrics = {
  grayCohort: string;
  cacheHitRateTarget: number;
  crawlerRtoMinutesTarget: number;
  databaseRecoveryDataLossHoursTarget: number;
  environment: string;
};

/**
 * Thrown when the AI worker is unreachable or the LLM is not configured.
 * Carries a structured error that the UI can render explicitly.
 */
export class WorkerError extends Error {
  readonly code: DiagnosisError["error"];

  constructor(code: DiagnosisError["error"], message: string) {
    super(message);
    this.name = "WorkerError";
    this.code = code;
  }
}

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "/api";

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
  if (!response.ok) throw new Error("Create conversation failed");
  const envelope = (await response.json()) as ApiEnvelope<Conversation>;
  return envelope.data;
}

/**
 * Parse an SSE response body.
 * Returns the Diagnosis on "event: diagnosis" or throws a WorkerError on "event: error".
 */
export function parseDiagnosisEvent(raw: string): Diagnosis {
  const lines = raw.split(/\r?\n/);

  // Check for error event first
  const eventLine = lines.find((line) => line.startsWith("event:"));
  if (eventLine && eventLine.includes("error")) {
    const dataLine = lines.find((line) => line.startsWith("data:"));
    if (dataLine) {
      const errorPayload = JSON.parse(dataLine.slice("data: ".length)) as DiagnosisError;
      throw new WorkerError(errorPayload.error, errorPayload.message);
    }
    throw new WorkerError("WORKER_ERROR", "AI worker returned an error");
  }

  // Parse diagnosis data event
  const dataLine = lines.find((line) => line.startsWith("data:"));
  if (!dataLine) throw new WorkerError("WORKER_ERROR", "Diagnosis stream did not contain a data event");
  const payload = JSON.parse(dataLine.slice("data: ".length)) as Diagnosis;

  // Check for error states embedded in the status
  if (payload.selfCheckStatus === "LLM_NOT_CONFIGURED") {
    throw new WorkerError("LLM_NOT_CONFIGURED", "AI 引擎未配置，请联系管理员配置 OPENAI_COMPATIBLE_API_KEY。");
  }
  if (payload.selfCheckStatus === "LLM_CALL_FAILED") {
    throw new WorkerError("WORKER_ERROR", "上游大模型调用失败，请稍后重试。");
  }

  return {
    ...payload,
    sources: normalizeSources(payload.sources)
  };
}

export async function streamDiagnosis(token: string, conversationId: number, question: string) {
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/messages/stream`, {
    method: "POST",
    headers: {
      ...authHeaders(token),
      Accept: "text/event-stream"
    },
    body: JSON.stringify({ question })
  });

  if (!response.ok) {
    throw new WorkerError(
      "WORKER_UNREACHABLE",
      `诊断服务返回 HTTP ${response.status}，请稍后重试。`
    );
  }

  const text = await response.text();

  // Detect SSE error events inside a 200 response
  if (text.includes("event: error")) {
    const dataMatch = text.match(/data:\s*(\{.+?\})/);
    if (dataMatch) {
      try {
        const errorPayload = JSON.parse(dataMatch[1]) as DiagnosisError;
        throw new WorkerError(errorPayload.error, errorPayload.message);
      } catch (err) {
        if (err instanceof WorkerError) throw err;
      }
    }
    throw new WorkerError("WORKER_ERROR", "诊断服务发生未知错误。");
  }

  return parseDiagnosisEvent(text);
}

export async function fetchPaidIntelligence(token: string) {
  const response = await fetch(`${API_BASE}/paid-intelligence`, {
    headers: authHeaders(token)
  });
  if (!response.ok) throw new Error("Fetch paid intelligence failed");
  const envelope = (await response.json()) as ApiEnvelope<PaidIntelligence[]>;
  return envelope.data;
}

export async function fetchDiagnosisReport(token: string, question: string) {
  const response = await fetch(`${API_BASE}/reports/diagnosis?question=${encodeURIComponent(question)}`, {
    headers: authHeaders(token)
  });

  if (!response.ok) {
    if (response.status === 503) {
      throw new WorkerError(
        "WORKER_UNREACHABLE",
        "诊断报告生成失败 — AI 引擎不可用。"
      );
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
  if (!response.ok) throw new Error("Fetch ops metrics failed");
  const envelope = (await response.json()) as ApiEnvelope<OpsMetrics>;
  return envelope.data;
}

function authHeaders(token: string) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`
  };
}
