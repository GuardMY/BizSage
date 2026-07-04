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

export type Diagnosis = {
  answer: string;
  sources: Source[];
  confidence: "LOW" | "MEDIUM" | "HIGH";
  timeliness: string;
  selfCheckStatus?: "PASSED" | "NEEDS_REVIEW" | "INSUFFICIENT_EVIDENCE";
  disclaimer: string;
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

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080/api";

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

export function parseDiagnosisEvent(raw: string): Diagnosis {
  const dataLine = raw
    .split(/\r?\n/)
    .find((line) => line.startsWith("data: "));
  if (!dataLine) throw new Error("Diagnosis stream did not contain a data event");
  const payload = JSON.parse(dataLine.slice("data: ".length)) as Diagnosis;
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
  if (!response.ok) throw new Error("Stream diagnosis failed");
  return parseDiagnosisEvent(await response.text());
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
  if (!response.ok) throw new Error("Fetch diagnosis report failed");
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
