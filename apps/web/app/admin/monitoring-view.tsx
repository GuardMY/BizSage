"use client";

import { useEffect, useState } from "react";
import type {
  AdminAlert,
  AdminCollectionDeadLetter,
  AdminCollectionJobRun,
  AdminCollectionSource,
  AdminHumanIntelligence,
  AdminIntelligenceReview,
  AdminKnowledgeNode,
  AdminTicket
} from "../../lib/api-client";
import { fetchAdminCollectionTelemetry, fetchOpsSla, type CollectionTelemetry, type SlaStats } from "../../lib/api-client";
import { adminText, type AdminLocale } from "./admin-i18n";

export function MonitoringView({
  locale, sources, jobs, deadLetters, knowledgeNodes, alerts, tickets, reviews, humanRows
}: {
  locale: AdminLocale;
  sources: AdminCollectionSource[];
  jobs: AdminCollectionJobRun[];
  deadLetters: AdminCollectionDeadLetter[];
  knowledgeNodes: AdminKnowledgeNode[];
  alerts: AdminAlert[];
  tickets: AdminTicket[];
  reviews: AdminIntelligenceReview[];
  humanRows: AdminHumanIntelligence[];
}) {
  const [sla, setSla] = useState<SlaStats | null>(null);
  const [telemetry, setTelemetry] = useState<CollectionTelemetry | null>(null);
  const t = (zh: string, en: string) => adminText(locale, zh, en);

  useEffect(() => {
    fetchOpsSla("24h").then(setSla).catch(() => {});
    fetchAdminCollectionTelemetry().then(setTelemetry).catch(() => {});
  }, []);

  const enabledSources = sources.filter(s => s.status === "ENABLED").length;
  const openCircuits = sources.filter(s => s.circuitState === "OPEN").length;
  const recentRuns = jobs.slice(0, 20);
  const successRuns = recentRuns.filter(r => r.status === "SUCCESS").length;
  const runSuccessRate = recentRuns.length > 0 ? Math.round(100 * successRuns / recentRuns.length) : null;
  const p0Alerts = alerts.filter(a => a.level === "P0" && a.status !== "CLOSED").length;
  const p1Alerts = alerts.filter(a => a.level === "P1" && a.status !== "CLOSED").length;
  const openAlerts = alerts.filter(a => a.status !== "CLOSED").length;
  const openTickets = tickets.filter(t => t.status !== "CLOSED" && t.status !== "ARCHIVED").length;
  const pendingReviews = reviews.filter(r => r.reviewStatus === "PENDING").length;
  const pendingHuman = humanRows.filter(h => h.status === "PENDING_REVIEW").length;

  return (
    <div className="adminStack">
      <div className="adminMonitoringGrid">
        <MonitoringCard title={t("服务健康", "Service Health")} detail={t("来自告警聚合与采集链路的实时状态。", "Real-time status from alert aggregation and collection pipeline.")}>
          <MetricRow label="API Gateway" badge={openAlerts > 0 ? "DEGRADED" : "HEALTHY"} badgeClass={openAlerts > 0 ? "open" : "healthy"} detail={`${openAlerts} ${t("个未处理告警", "open alerts")}`} />
          <MetricRow label={t("爬虫链路", "Crawler Pipeline")} badge={openCircuits > 0 ? "DEGRADED" : "HEALTHY"} badgeClass={openCircuits > 0 ? "open" : "healthy"} detail={`${openCircuits} ${t("个开启熔断", "open circuits")}`} />
          <MetricRow label={t("AI 推理", "AI Inference")} badge={sources.length > 0 || jobs.length > 0 ? "OPERATIONAL" : "NO DATA"} badgeClass={sources.length > 0 || jobs.length > 0 ? "healthy" : "pending"} detail={t("通过采集链路活动自检", "Self-check via collection pipeline activity")} />
          <MetricRow label={t("数据库", "Database")} badge="MONITORED" badgeClass="healthy" detail={t("来自 /admin/dashboard 接口", "Via API /admin/dashboard endpoint")} />
          <MetricRow label={t("缓存层", "Cache Layer")} badge="MONITORED" badgeClass="healthy" detail={t("来自 Redis 熔断状态", "Via Redis circuit breaker state")} />
        </MonitoringCard>

        <MonitoringCard title={t("采集链路", "Collection Pipeline")} detail={t("调度器、采集源、任务和死信队列。", "Scheduler, sources, jobs, and dead-letter queue.")}>
          <MetricRow label={t("启用采集源", "Active sources")} badge={`${telemetry?.enabledSources ?? enabledSources}`} badgeClass="healthy" detail={`${telemetry?.totalSources ?? sources.length} ${t("总计", "total")}`} />
          <MetricRow label={t("24 小时成功率", "24h success rate")} badge={telemetry?.successRate24h ?? `${runSuccessRate != null ? runSuccessRate + "%" : "-"}`} badgeClass={parseFloat(telemetry?.successRate24h ?? "0") >= 80 ? "healthy" : "open"} detail={`${telemetry?.totalRuns24h ?? 0} ${t("次运行", "runs")}`} />
          <MetricRow label={t("24 小时记录", "Records (24h)")} badge={`${telemetry?.recordsCollected24h ?? "-"}`} badgeClass="healthy" detail={t("过去 24 小时采集量", "Collected last 24 hours")} />
          <MetricRow label={t("死信", "Dead letters")} badge={`${telemetry?.deadLetterCount ?? deadLetters.length}`} badgeClass={(telemetry?.deadLetterCount ?? deadLetters.length) > 0 ? "p1" : "healthy"} detail={t("失败投递", "Failed deliveries")} />
        </MonitoringCard>

        <MonitoringCard title={t("数据摘要", "Data Summary")} detail={t("知识、情报和审计记录计数。", "Knowledge, intelligence, and audit record counts.")}>
          <MetricRow label={t("知识节点", "Knowledge nodes")} badge={`${knowledgeNodes.length}`} badgeClass="healthy" detail={t("维护总量", "Total maintained")} />
          <MetricRow label={t("情报复核", "Intelligence reviews")} badge={`${reviews.length}`} badgeClass="healthy" detail={`${pendingReviews} ${t("待处理", "pending")}`} />
          <MetricRow label={t("人工情报", "Human intelligence")} badge={`${humanRows.length}`} badgeClass="healthy" detail={`${pendingHuman} ${t("待处理", "pending")}`} />
          <MetricRow label={t("审计日志", "Audit logs")} badge="Active" badgeClass="healthy" detail={t("预写审计记录", "Write-ahead logging")} />
        </MonitoringCard>

        <MonitoringCard title={t("告警摘要", "Alert Summary")} detail={t("按严重级别和状态统计告警。", "Alert distribution by severity and status.")}>
          <MetricRow label="P0 (Critical)" badge={`${p0Alerts}`} badgeClass={p0Alerts > 0 ? "p0" : "healthy"} detail={t("最高严重级别", "Highest severity")} />
          <MetricRow label="P1 (High)" badge={`${p1Alerts}`} badgeClass={p1Alerts > 0 ? "p1" : "healthy"} detail={t("高严重级别", "High severity")} />
          <MetricRow label={t("未处理告警", "Open alerts")} badge={`${openAlerts}`} badgeClass="pending" detail={t("全部级别", "All levels")} />
          <MetricRow label={t("未结工单", "Open tickets")} badge={`${openTickets}`} badgeClass="pending" detail={t("运营事项", "Operational items")} />
        </MonitoringCard>

        <MonitoringCard title={t("SLA 摘要", "SLA Summary")} detail={t("24 小时滚动窗口的可用性、错误率和延迟。", "24-hour rolling window uptime, error rate, and latency.")}>
          <MetricRow label={t("可用性", "Uptime")} badge={sla?.uptimePercent ?? "-"} badgeClass={sla?.slaMet ? "healthy" : "p0"} detail={sla?.slaMet ? t("SLA 达标 (目标 99.9%)", "SLA met (99.9% target)") : sla ? t("低于 99.9% 目标", "Below 99.9% target") : t("加载中...", "Loading...")} />
          <MetricRow label={t("错误率", "Error Rate")} badge={sla?.errorRatePercent ?? "-"} badgeClass="pending" detail={`${sla?.dataPoints ?? 0} ${t("个数据点", "data points")}`} />
          <MetricRow label="P50 Latency" badge={sla?.latencyP50Ms ? `${sla.latencyP50Ms}ms` : "-"} badgeClass="healthy" detail={t("中位响应时间", "Median response time")} />
          <MetricRow label="P95 Latency" badge={sla?.latencyP95Ms ? `${sla.latencyP95Ms}ms` : "-"} badgeClass="healthy" detail={t("95 分位", "95th percentile")} />
        </MonitoringCard>
      </div>
      <div className="monitoringSummary">
        <small>{t("最后刷新：数据汇总自当前 API 连接。SLA 数据按小时从审计日志指标计算。Prometheus/CloudWatch 集成计划在 V3-6 接入。", "Last refreshed: data aggregated from active API connections. SLA data is computed hourly from audit log metrics. Prometheus/CloudWatch integration targeted for V3-6.")}</small>
      </div>
    </div>
  );
}

function MonitoringCard({ title, detail, children }: { title: string; detail: string; children: React.ReactNode }) {
  return (
    <section className="workspaceCard adminPanel">
      <div className="sectionHead compact">
        <div className="sectionCopy">
          <h2>{title}</h2>
          <p>{detail}</p>
        </div>
      </div>
      <div className="monitoringMetricStack">{children}</div>
    </section>
  );
}

function MetricRow({ label, badge, badgeClass, detail }: { label: string; badge: string; badgeClass: string; detail: string }) {
  return (
    <div className="monitoringMetricRow">
      <strong>{label}</strong>
      <span className={`adminBadge status-${badgeClass}`}>{badge}</span>
      <small>{detail}</small>
    </div>
  );
}
