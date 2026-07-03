import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

test("API client source normalizer preserves source details", async () => {
  const source = readFileSync(new URL("../lib/api-client.ts", import.meta.url), "utf8");
  assert.match(source, /normalizeSources/);
  assert.match(source, /sourceUrl/);
  assert.match(source, /confidence/);
});
