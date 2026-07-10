import type { AppLocale } from "../../lib/api-client";

export type AdminLocale = AppLocale;

export function adminText(locale: AdminLocale, zh: string, en: string) {
  return locale === "zh-CN" ? zh : en;
}

const codeLabels: Record<string, { zh: string; en: string }> = {
  ACTIVE: { zh: "启用", en: "Active" },
  acknowledge: { zh: "确认", en: "acknowledge" },
  ACKNOWLEDGED: { zh: "已确认", en: "Acknowledged" },
  ADMIN_V3_BOOTSTRAP: { zh: "管理端 V3 初始化", en: "Admin V3 bootstrap" },
  AI_SELF_CHECK: { zh: "AI 自检", en: "AI self-check" },
  APPROVED: { zh: "已通过", en: "Approved" },
  ARCHIVE: { zh: "归档", en: "Archive" },
  ARCHIVED: { zh: "已归档", en: "Archived" },
  CLAIMED: { zh: "已认领", en: "Claimed" },
  CLOSED: { zh: "已关闭", en: "Closed" },
  COMPLIANCE: { zh: "合规复核", en: "Compliance" },
  COMPLETED: { zh: "已完成", en: "Completed" },
  CRITICAL: { zh: "严重", en: "Critical" },
  DAILY: { zh: "每日", en: "Daily" },
  DEGRADED: { zh: "降级", en: "Degraded" },
  DISABLED: { zh: "已停用", en: "Disabled" },
  DRAFT: { zh: "草稿", en: "Draft" },
  ENABLED: { zh: "已启用", en: "Enabled" },
  FLAG: { zh: "标记", en: "Flag" },
  FREE: { zh: "免费", en: "Free" },
  GRAY: { zh: "灰度", en: "Gray release" },
  HEALTHY: { zh: "健康", en: "Healthy" },
  HIGH: { zh: "高", en: "High" },
  IDLE: { zh: "空闲", en: "Idle" },
  IMMEDIATE: { zh: "立即生效", en: "Immediate" },
  IN_PROGRESS: { zh: "处理中", en: "In progress" },
  IN_REVIEW: { zh: "复核中", en: "In review" },
  INCLUDE: { zh: "包含", en: "Include" },
  INTERNAL: { zh: "内部", en: "Internal" },
  LEGAL_FREEZE: { zh: "法务冻结", en: "Legal freeze" },
  LOW: { zh: "低", en: "Low" },
  MANUAL: { zh: "手动", en: "Manual" },
  MEDIUM: { zh: "中", en: "Medium" },
  MOCK_API: { zh: "模拟 API", en: "Mock API" },
  MONITORED: { zh: "已监控", en: "Monitored" },
  MONTHLY: { zh: "每月", en: "Monthly" },
  NEW: { zh: "新建", en: "New" },
  NO_DATA: { zh: "无数据", en: "No data" },
  NO_SOURCE: { zh: "缺少来源", en: "No source" },
  OPERATIONAL: { zh: "运行正常", en: "Operational" },
  P0: { zh: "P0 严重", en: "P0 Critical" },
  P1: { zh: "P1 高", en: "P1 High" },
  P2: { zh: "P2 中", en: "P2 Medium" },
  PAID: { zh: "付费", en: "Paid" },
  PAID_INTEL: { zh: "付费情报", en: "Paid intelligence" },
  PASS: { zh: "通过", en: "Pass" },
  PASSED: { zh: "已通过", en: "Passed" },
  PENDING: { zh: "待处理", en: "Pending" },
  PENDING_REVIEW: { zh: "待复核", en: "Pending review" },
  PUBLIC_PAGE: { zh: "公开页面", en: "Public page" },
  PUBLISH: { zh: "发布", en: "Publish" },
  PUBLISHED: { zh: "已发布", en: "Published" },
  REJECT: { zh: "拒绝", en: "Reject" },
  REJECTED: { zh: "已拒绝", en: "Rejected" },
  RUNNING: { zh: "运行中", en: "Running" },
  SAVE_DRAFT: { zh: "保存草稿", en: "Save draft" },
  SCHEDULED: { zh: "定时", en: "Scheduled" },
  SEED: { zh: "种子", en: "Seed" },
  SEED_PAID: { zh: "种子付费", en: "Seed paid" },
  SUCCESS: { zh: "成功", en: "Success" },
  SUSPICIOUS: { zh: "可疑", en: "Suspicious" },
  WAITING_REVIEW: { zh: "等待复核", en: "Waiting review" },
  WEEKLY: { zh: "每周", en: "Weekly" },
  api_abuse: { zh: "API 滥用", en: "API abuse" },
  "admin-collector": { zh: "管理采集器", en: "Admin collector" },
  "admin-collector-mock": { zh: "管理模拟采集器", en: "Admin mock collector" },
  "admin-collector-public": { zh: "管理公开采集器", en: "Admin public collector" },
  "admin-knowledge": { zh: "管理知识库", en: "Admin knowledge" },
  "ai_self_check": { zh: "AI 自检", en: "AI self-check" },
  all: { zh: "全部", en: "All" },
  "cn-default": { zh: "中国默认区域", en: "China default region" },
  collection: { zh: "采集", en: "Collection" },
  close: { zh: "关闭", en: "close" },
  "compliance_review": { zh: "合规复核", en: "Compliance review" },
  "conflict": { zh: "冲突", en: "Conflict" },
  "conflict_judgment": { zh: "新旧冲突判断", en: "New/old conflict judgment" },
  "expert_interview": { zh: "专家访谈", en: "Expert interview" },
  "field_survey": { zh: "实地调研", en: "Field survey" },
  general: { zh: "通用行业", en: "General industry" },
  "gray_content": { zh: "灰色内容", en: "Gray content" },
  "local_visit": { zh: "本地走访", en: "Local visit" },
  "manual-admin": { zh: "人工管理录入", en: "Manual admin entry" },
  "operator": { zh: "运营人员", en: "Operator" },
  claim: { zh: "认领", en: "claim" },
  "paid_protection": { zh: "付费内容保护", en: "Paid protection" },
  "partner_report": { zh: "合作方报告", en: "Partner report" },
  "review_escalation": { zh: "复核升级", en: "Review escalation" },
  "rumor_detection": { zh: "谣言检测", en: "Rumor detection" },
  "sales-payment": { zh: "销售回款链路", en: "Sales payment chain" },
  "store-rent-pressure-playbook": { zh: "门店租金压力手册", en: "Store rent pressure playbook" },
  "supply-chain": { zh: "供应链", en: "Supply chain" },
  "suspicious_review": { zh: "可疑复核", en: "Suspicious review" },
  "user_submission": { zh: "用户提交", en: "User submission" }
};

export function adminCodeLabel(locale: AdminLocale, value: string | null | undefined) {
  if (!value) return "-";
  const label = codeLabels[value];
  return label ? adminText(locale, label.zh, label.en) : value;
}

export function adminCodeWithRaw(locale: AdminLocale, value: string | null | undefined) {
  if (!value) return "-";
  const label = adminCodeLabel(locale, value);
  return label === value ? value : `${label} (${value})`;
}

export function normalizeLocale(value: string | undefined): AdminLocale {
  return value === "en" ? "en" : "zh-CN";
}
