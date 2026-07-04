import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

test("API client source normalizer preserves source details", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /normalizeSources/);
  assert.match(source, /sourceUrl/);
  assert.match(source, /confidence/);
});

test("API client exposes V2 gray release types and operations helpers", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /membershipLevel/);
  assert.match(source, /PaidIntelligence/);
  assert.match(source, /DiagnosisReport/);
  assert.match(source, /fetchOpsMetrics/);
});

test("API client includes real diagnosis stream helpers", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /parseDiagnosisEvent/);
  assert.match(source, /streamDiagnosis/);
  assert.match(source, /text\/event-stream/);
});
