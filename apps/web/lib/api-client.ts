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
};

export type Diagnosis = {
  answer: string;
  sources: Source[];
  confidence: "LOW" | "MEDIUM" | "HIGH";
  timeliness: string;
  disclaimer: string;
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
      score: value.score === undefined ? undefined : Number(value.score)
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
  const envelope = (await response.json()) as ApiEnvelope<{
    token: string;
    username: string;
    role: string;
    regionId: string;
    industryId: string;
  }>;
  return envelope.data;
}

export async function createConversation(token: string, title: string) {
  const response = await fetch(`${API_BASE}/conversations`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ title })
  });
  if (!response.ok) throw new Error("Create conversation failed");
  return response.json();
}

function authHeaders(token: string) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`
  };
}
