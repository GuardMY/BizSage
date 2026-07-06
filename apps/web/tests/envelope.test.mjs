import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

test("API client source normalizer preserves source details", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /normalizeSources/);
  assert.match(source, /sourceUrl/);
  assert.match(source, /confidence/);
});

test("API client exposes paid intelligence and diagnosis report helpers", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /membershipLevel/);
  assert.match(source, /PaidIntelligence/);
  assert.match(source, /DiagnosisReport/);
});

test("API client includes real diagnosis stream helpers", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /parseDiagnosisEvent/);
  assert.match(source, /streamDiagnosis/);
  assert.match(source, /text\/event-stream/);
});

test("API client exposes stream helpers for incremental diagnosis rendering", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /export async function streamDiagnosisEvents/);
  assert.match(source, /response\.body\?\.getReader\(\)/);
  assert.match(source, /new TextDecoder\(\)/);
  assert.match(source, /onPartialAnswer/);
});

test("API client exposes a dedicated auth-expired error path for protected fetch requests", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /class AuthExpiredError extends Error/);
  assert.match(source, /response\.status === 401/);
  assert.match(source, /code === "UNAUTHORIZED"/);
  assert.match(source, /throw new AuthExpiredError/);
});

test("Web page defines bilingual UI messages and language switching without gray-release wording", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /type Locale = "zh-CN" \| "en"/);
  assert.match(source, /const messages: Record<Locale/);
  assert.match(source, /setLocale\(locale === "zh-CN" \? "en" : "zh-CN"\)/);
  assert.match(source, /English/);
  assert.match(source, /中文/);
  assert.doesNotMatch(source, /V2/);
  assert.doesNotMatch(source, /gray/i);
});

test("Web page gates workspace behind a standalone login screen", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /if \(!profile\)/);
  assert.match(source, /loginScreen/);
  assert.match(source, /handleLogout/);
  assert.match(source, /setPaidRows\(\[\]\)/);
  assert.match(source, /setSelectedSource\(null\)/);
});

test("Workspace shell shows profile identity after login without embedding login controls", async () => {
  const shell = readFileSync(new URL("../app/components/workspace-shell.tsx", import.meta.url), "utf8");
  const identityStart = shell.indexOf("<section className=\"topbarIdentity\" aria-label={t.identity}>");
  const identityEnd = shell.indexOf("</section>", identityStart);
  assert.notEqual(identityStart, -1);
  assert.notEqual(identityEnd, -1);

  const identityPanel = shell.slice(identityStart, identityEnd);
  assert.match(identityPanel, /profile\.username/);
  assert.match(identityPanel, /profile\.role/);
  assert.match(identityPanel, /profile\.membershipLevel/);
  assert.doesNotMatch(identityPanel, /<input/);
  assert.doesNotMatch(identityPanel, /LogIn/);
  assert.doesNotMatch(identityPanel, /handleLogin/);
});

test("Workspace shell removes the ready badge and merges identity with logout actions", async () => {
  const shell = readFileSync(new URL("../app/components/workspace-shell.tsx", import.meta.url), "utf8");
  assert.match(shell, /topbarIdentity/);
  assert.match(shell, /profile\.username/);
  assert.match(shell, /profile\.role/);
  assert.match(shell, /profile\.membershipLevel/);
  assert.match(shell, /onLogout/);
  assert.doesNotMatch(shell, /className="health"/);
  assert.doesNotMatch(shell, /CheckCircle2/);
});

test("Web page composes the new workspace shell and conversation helpers", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /WorkspaceShell/);
  assert.match(source, /resolveWorkspaceSelection/);
  assert.match(source, /nextSelectionAfterArchive/);
  assert.doesNotMatch(source, /fetchOpsMetrics/);
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
  assert.match(source, /streamDiagnosisEvents\(profile\.token, conversationId, message/);
});

test("Web page uses incremental diagnosis stream state before final refresh", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /streamDiagnosisEvents/);
  assert.match(source, /streamingDiagnosis/);
  assert.match(source, /setStreamingDiagnosis/);
});

test("Workspace shell navigation buttons update visible workspace sections", async () => {
  const source = readFileSync(new URL("../app/components/workspace-shell.tsx", import.meta.url), "utf8");
  assert.match(source, /activeSection/);
  assert.match(source, /setActiveSection\("intelligence"\)/);
  assert.match(source, /setActiveSection\("users"\)/);
  assert.match(source, /setActiveSection\("archive"\)/);
  assert.match(source, /className=\{`navItem \$\{activeSection ===/);
});

test("Workspace layout supports a persistent sidebar shell and responsive content columns", async () => {
  const source = readFileSync(new URL("../app/globals.css", import.meta.url), "utf8");
  assert.match(source, /\.workspace\s*\{[\s\S]*min-height:\s*100vh;/);
  assert.match(source, /\.workspace\s*\{[\s\S]*grid-template-columns:\s*320px 1fr;/);
  assert.match(source, /\.conversationSidebar\s*\{/);
  assert.match(source, /\.workspaceBody\s*\{/);
  assert.match(source, /\.workspacePanelGrid\s*\{/);
  assert.match(source, /@media \(max-width:\s*980px\)[\s\S]*\.workspace\s*\{[\s\S]*grid-template-columns:\s*1fr;/);
});

test("Workspace styles allow independent rail and content scrolling for long conversations", async () => {
  const source = readFileSync(new URL("../app/globals.css", import.meta.url), "utf8");
  assert.match(source, /\.rail\s*\{[\s\S]*overflow:\s*auto;/);
  assert.match(source, /\.workspaceScroll\s*\{[\s\S]*overflow:\s*auto;/);
  assert.match(source, /\.topbarIdentity\s*\{/);
});

test("Diagnosis workspace auto-follows streaming conversation updates", async () => {
  const source = readFileSync(new URL("../app/components/diagnosis-workspace.tsx", import.meta.url), "utf8");
  assert.match(source, /messagesRef/);
  assert.match(source, /scrollTop = messagesRef\.current\.scrollHeight/);
  assert.match(source, /streamingDiagnosis/);
});

test("Diagnosis workspace avoids rendering a second standalone assistant bubble after history refresh", async () => {
  const source = readFileSync(new URL("../app/components/diagnosis-workspace.tsx", import.meta.url), "utf8");
  assert.match(source, /const hasAssistantReply = messageHistory\.some\(\(messageItem\) => messageItem\.sender === "ASSISTANT"\);/);
  assert.doesNotMatch(source, /\{diagnosis && \(\s*<div[\s\S]*?<Markdown content=\{diagnosis\.answer\} \/>[\s\S]*?\)\}/);
  assert.match(source, /\) : displayedDiagnosis \|\| hasAssistantReply \? \(/);
});
