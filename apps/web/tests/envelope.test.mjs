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

test("API client exposes a dedicated auth-expired error path for protected fetch requests", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /class AuthExpiredError extends Error/);
  assert.match(source, /response\.status === 401/);
  assert.match(source, /code === "UNAUTHORIZED"/);
  assert.match(source, /throw new AuthExpiredError/);
});

test("Web page defines bilingual UI messages and language switching", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /type Locale = "zh-CN" \| "en"/);
  assert.match(source, /const messages: Record<Locale/);
  assert.match(source, /setLocale\(locale === "zh-CN" \? "en" : "zh-CN"\)/);
  assert.match(source, /English/);
  assert.match(source, /中文/);
});

test("Web page gates workspace behind a standalone login screen", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /if \(!profile\)/);
  assert.match(source, /loginScreen/);
  assert.match(source, /handleLogout/);
  assert.match(source, /setPaidRows\(\[\]\)/);
  assert.match(source, /setSelectedSource\(null\)/);
});

test("Web page shows profile identity after login without embedding login controls", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  const identityStart = source.indexOf("<section className=\"identityPanel\" aria-label={t.identity}>");
  const identityEnd = source.indexOf("</section>", identityStart);
  assert.notEqual(identityStart, -1);
  assert.notEqual(identityEnd, -1);

  const identityPanel = source.slice(identityStart, identityEnd);
  assert.match(identityPanel, /profile\.username/);
  assert.match(identityPanel, /profile\.role/);
  assert.match(identityPanel, /profile\.membershipLevel/);
  assert.doesNotMatch(identityPanel, /<input/);
  assert.doesNotMatch(identityPanel, /LogIn/);
  assert.doesNotMatch(identityPanel, /handleLogin/);
});

test("Web page persists the signed-in profile across refresh", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /localStorage/);
  assert.match(source, /useEffect/);
  assert.match(source, /setProfile\(nextProfile\)/);
  assert.match(source, /JSON\.stringify\(profile\)/);
  assert.match(source, /JSON\.parse/);
});

test("Web page returns to the login screen on auth expiry without clearing draft input", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /sessionExpired/);
  assert.match(source, /AuthExpiredError/);
  assert.match(source, /window\.localStorage\.removeItem\(PROFILE_STORAGE_KEY\)/);
  assert.match(source, /setProfile\(null\)/);
  assert.match(source, /setNotice\(t\.sessionExpired\)/);
  assert.doesNotMatch(source, /setMessage\(messages\["zh-CN"\]\.defaultQuestion\)/);
});

test("Web page keeps the same conversation id for follow-up diagnosis", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /selectedConversationId/);
  assert.match(source, /setSelectedConversationId/);
  assert.match(source, /selectedConversationId \?\?/);
  assert.match(source, /streamDiagnosis\(profile\.token, conversationId, message\)/);
});

test("Web page navigation buttons update visible workspace sections", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /activeSection/);
  assert.match(source, /setActiveSection\("intelligence"\)/);
  assert.match(source, /setActiveSection\("users"\)/);
  assert.match(source, /setActiveSection\("archive"\)/);
  assert.match(source, /className=\{`navItem \$\{activeSection ===/);
});
