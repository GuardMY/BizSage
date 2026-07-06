# Web Streaming And Layout Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make diagnosis responses render incrementally, keep the conversation viewport scrolled with new content, and merge logout plus user identity into one top-right control area without the ready badge.

**Architecture:** Keep the existing SSE diagnosis endpoint, but split the web client into a stream reader plus a final payload parser so the page can render partial answer state before the stream completes. Restructure the shell so the left rail can scroll independently, move the identity actions into a unified topbar card, and wire the diagnosis workspace to auto-follow streamed content.

**Tech Stack:** Next.js App Router, React state/effects, native Fetch streaming, Node test runner, repository markdown changelogs.

---

### Task 1: Lock Regression Tests

**Files:**
- Modify: `apps/web/tests/envelope.test.mjs`

- [ ] **Step 1: Write the failing test**

```javascript
test("Workspace shell removes the ready badge and merges identity with logout actions", async () => {
  const shell = readFileSync(new URL("../app/components/workspace-shell.tsx", import.meta.url), "utf8");
  assert.match(shell, /topbarIdentity/);
  assert.match(shell, /profile\.username/);
  assert.match(shell, /profile\.role/);
  assert.match(shell, /onLogout/);
  assert.doesNotMatch(shell, /className="health"/);
  assert.doesNotMatch(shell, /CheckCircle2/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --test-name-pattern "Workspace shell removes the ready badge and merges identity with logout actions"`
Expected: FAIL because `topbarIdentity` does not exist and the health badge still exists.

- [ ] **Step 3: Write minimal implementation**

```tsx
<div className="topbarIdentity">
  <div className="topbarIdentityCopy">
    <strong>{profile.username}</strong>
    <small>{profile.role} / {profile.membershipLevel}</small>
  </div>
  <button className="ghost" onClick={onLogout} type="button">
    <LogOut size={16} />
    {t.logout}
  </button>
</div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --test-name-pattern "Workspace shell removes the ready badge and merges identity with logout actions"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/web/tests/envelope.test.mjs apps/web/app/components/workspace-shell.tsx
git commit -m "test: lock workspace topbar identity layout"
```

### Task 2: Lock Streaming Client Contracts

**Files:**
- Modify: `apps/web/tests/envelope.test.mjs`
- Modify: `apps/web/lib/api-client.ts`

- [ ] **Step 1: Write the failing test**

```javascript
test("API client exposes stream helpers for incremental diagnosis rendering", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /export async function streamDiagnosisEvents/);
  assert.match(source, /response\.body\.getReader\(\)/);
  assert.match(source, /TextDecoder/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --test-name-pattern "API client exposes stream helpers for incremental diagnosis rendering"`
Expected: FAIL because `streamDiagnosisEvents` does not exist.

- [ ] **Step 3: Write minimal implementation**

```typescript
export async function streamDiagnosisEvents(...) {
  const reader = response.body?.getReader();
  const decoder = new TextDecoder();
  // consume chunks and emit partial answer updates
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --test-name-pattern "API client exposes stream helpers for incremental diagnosis rendering"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/web/tests/envelope.test.mjs apps/web/lib/api-client.ts
git commit -m "test: lock streaming diagnosis client contract"
```

### Task 3: Implement Incremental Diagnosis Rendering

**Files:**
- Modify: `apps/web/app/page.tsx`
- Modify: `apps/web/app/components/diagnosis-workspace.tsx`
- Modify: `apps/web/app/components/workspace-types.ts`
- Modify: `apps/web/lib/api-client.ts`

- [ ] **Step 1: Write the failing test**

```javascript
test("Web page uses incremental diagnosis stream state before final refresh", async () => {
  const source = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /streamDiagnosisEvents/);
  assert.match(source, /streamingDiagnosis/);
  assert.match(source, /setStreamingDiagnosis/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --test-name-pattern "Web page uses incremental diagnosis stream state before final refresh"`
Expected: FAIL because streaming state is not wired yet.

- [ ] **Step 3: Write minimal implementation**

```tsx
const [streamingDiagnosis, setStreamingDiagnosis] = useState<Diagnosis | null>(null);

const nextDiagnosis = await streamDiagnosisEvents(profile.token, conversationId, message, {
  onPartialAnswer(answer) {
    setStreamingDiagnosis((previous) => ({
      ...(previous ?? emptyDiagnosis),
      answer
    }));
  }
});
setDiagnosis(nextDiagnosis);
setStreamingDiagnosis(null);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --test-name-pattern "Web page uses incremental diagnosis stream state before final refresh"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/page.tsx apps/web/app/components/diagnosis-workspace.tsx apps/web/app/components/workspace-types.ts apps/web/lib/api-client.ts
git commit -m "feat: stream diagnosis output incrementally"
```

### Task 4: Implement Scroll And Shell Layout Fixes

**Files:**
- Modify: `apps/web/app/components/workspace-shell.tsx`
- Modify: `apps/web/app/components/conversation-sidebar.tsx`
- Modify: `apps/web/app/globals.css`

- [ ] **Step 1: Write the failing test**

```javascript
test("Workspace styles allow independent rail and content scrolling for long conversations", async () => {
  const css = readFileSync(new URL("../app/globals.css", import.meta.url), "utf8");
  assert.match(css, /\.rail\s*\{[\s\S]*overflow:\s*auto;/);
  assert.match(css, /\.workspaceScroll\s*\{[\s\S]*overflow:\s*auto;/);
  assert.match(css, /\.topbarIdentity\s*\{/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --test-name-pattern "Workspace styles allow independent rail and content scrolling for long conversations"`
Expected: FAIL because these scroll rules and class selectors are not present.

- [ ] **Step 3: Write minimal implementation**

```css
.rail {
  overflow: auto;
}

.workspaceScroll {
  overflow: auto;
}

.topbarIdentity {
  display: flex;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --test-name-pattern "Workspace styles allow independent rail and content scrolling for long conversations"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/components/workspace-shell.tsx apps/web/app/components/conversation-sidebar.tsx apps/web/app/globals.css
git commit -m "fix: keep workspace identity visible during long conversations"
```

### Task 5: Update Documentation And Verify

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `CHANGELOG-zh-CN.md`

- [ ] **Step 1: Write the changelog entry**

```markdown
## 2026-07-07
- Type: Feature/Fix
- Modules: apps/web
- Main changes: streaming diagnosis rendering, topbar identity merge, independent workspace scrolling
- Verification: npm test, npm run build
- Unfinished: browser manual verification pending if not run
```

- [ ] **Step 2: Run the focused automated checks**

Run: `npm test`
Expected: PASS with all web tests green.

- [ ] **Step 3: Run the production build check**

Run: `npm run build`
Expected: PASS with a successful Next.js production build.

- [ ] **Step 4: Review git diff**

Run: `git diff -- apps/web CHANGELOG.md CHANGELOG-zh-CN.md docs/superpowers/plans/2026-07-07-web-streaming-layout-fixes.md`
Expected: Diff shows only the intended streaming, layout, and changelog changes.

- [ ] **Step 5: Commit**

```bash
git add CHANGELOG.md CHANGELOG-zh-CN.md docs/superpowers/plans/2026-07-07-web-streaming-layout-fixes.md apps/web
git commit -m "feat: stream diagnosis output and simplify workspace identity"
```
