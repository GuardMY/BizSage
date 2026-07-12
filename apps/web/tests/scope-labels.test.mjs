import test from "node:test";
import assert from "node:assert/strict";

test("maps scope enum values to Chinese labels", async () => {
  const labels = await import("../lib/scope-labels.ts");

  assert.equal(labels.regionLabel("default-region"), "默认地区");
  assert.equal(labels.regionLabel("cn-default"), "中国大陆");
  assert.equal(labels.industryLabel("catering"), "餐饮");
  assert.equal(labels.industryLabel("default-industry"), "通用企业");
  assert.equal(labels.industryLabel("software-internet"), "软件与互联网");
  assert.equal(labels.industryLabel("custom-123", "自定义行业"), "自定义行业");
  assert.equal(labels.roleLabel("USER"), "用户");
  assert.equal(labels.roleLabel("LEGAL_FREEZE"), "已冻结");
  assert.equal(labels.membershipLabel("SEED_PAID"), "付费版");
});
