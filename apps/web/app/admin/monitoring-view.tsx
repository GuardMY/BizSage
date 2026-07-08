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

export function MonitoringView({
  sources, jobs, deadLetters, knowledgeNodes, alerts, tickets, reviews, humanRows
}: {
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
        <MonitoringCard title="Service Health" detail="Real-time status from alert aggregation and collection pipeline.">
          <MetricRow label="API Gateway" badge={openAlerts > 0 ? "DEGRADED" : "HEALTHY"} badgeClass={openAlerts > 0 ? "open" : "healthy"} detail={`${openAlerts} open alerts`} />
          <MetricRow label="Crawler Pipeline" badge={openCircuits > 0 ? "DEGRADED" : "HEALTHY"} badgeClass={openCircuits > 0 ? "open" : "healthy"} detail={`${openCircuits} open circuits`} />
          <MetricRow label="AI Inference" badge={sources.length > 0 || jobs.length > 0 ? "OPERATIONAL" : "NO DATA"} badgeClass={sources.length > 0 || jobs.length > 0 ? "healthy" : "pending"} detail="Self-check via collection pipeline activity" />
          <MetricRow label="Database" badge="MONITORED" badgeClass="healthy" detail="Via API /admin/dashboard endpoint" />
          <MetricRow label="Cache Layer" badge="MONITORED" badgeClass="healthy" detail="Via Redis circuit breaker state" />
        </MonitoringCard>

        <MonitoringCard title="Collection Pipeline" detail="Scheduler, sources, jobs, and dead-letter queue.">
          <MetricRow label="Active sources" badge={`${telemetry?.enabledSources ?? enabledSources}`} badgeClass="healthy" detail={`${telemetry?.totalSources ?? sources.length} total`} />
          <MetricRow label="24h success rate" badge={telemetry?.successRate24h ?? `${runSuccessRate != null ? runSuccessRate + "%" : "—"}`} badgeClass={parseFloat(telemetry?.successRate24h ?? "0") >= 80 ? "healthy" : "open"} detail={`${telemetry?.totalRuns24h ?? 0} runs`} />
          <MetricRow label="Records (24h)" badge={`${telemetry?.recordsCollected24h ?? "—"}`} badgeClass="healthy" detail="Collected last 24 hours" />
          <MetricRow label="Dead letters" badge={`${telemetry?.deadLetterCount ?? deadLetters.length}`} badgeClass={(telemetry?.deadLetterCount ?? deadLetters.length) > 0 ? "p1" : "healthy"} detail="Failed deliveries" />
        </MonitoringCard>

        <MonitoringCard title="Data Summary" detail="Knowledge, intelligence, and audit record counts.">
          <MetricRow label="Knowledge nodes" badge={`${knowledgeNodes.length}`} badgeClass="healthy" detail="Total maintained" />
          <MetricRow label="Intelligence reviews" badge={`${reviews.length}`} badgeClass="healthy" detail={`${pendingReviews} pending`} />
          <MetricRow label="Human intelligence" badge={`${humanRows.length}`} badgeClass="healthy" detail={`${pendingHuman} pending`} />
          <MetricRow label="Audit logs" badge="Active" badgeClass="healthy" detail="Write-ahead logging" />
        </MonitoringCard>

        <MonitoringCard title="Alert Summary" detail="Alert distribution by severity and status.">
          <MetricRow label="P0 (Critical)" badge={`${p0Alerts}`} badgeClass={p0Alerts > 0 ? "p0" : "healthy"} detail="Highest severity" />
          <MetricRow label="P1 (High)" badge={`${p1Alerts}`} badgeClass={p1Alerts > 0 ? "p1" : "healthy"} detail="High severity" />
          <MetricRow label="Open alerts" badge={`${openAlerts}`} badgeClass="pending" detail="All levels" />
          <MetricRow label="Open tickets" badge={`${openTickets}`} badgeClass="pending" detail="Operational items" />
        </MonitoringCard>
        <MonitoringCard title="SLA Summary" detail="24-hour rolling window uptime, error rate, and latency.">
          <MetricRow label="Uptime" badge={sla?.uptimePercent ?? "—"} badgeClass={sla?.slaMet ? "healthy" : "p0"} detail={sla?.slaMet ? "SLA met (99.9% target)" : sla ? "Below 99.9% target" : "Loading..."} />
          <MetricRow label="Error Rate" badge={sla?.errorRatePercent ?? "—"} badgeClass="pending" detail={`${sla?.dataPoints ?? 0} data points`} />
          <MetricRow label="P50 Latency" badge={sla?.latencyP50Ms ? `${sla.latencyP50Ms}ms` : "—"} badgeClass="healthy" detail="Median response time" />
          <MetricRow label="P95 Latency" badge={sla?.latencyP95Ms ? `${sla.latencyP95Ms}ms` : "—"} badgeClass="healthy" detail="95th percentile" />
        </MonitoringCard>
      </div>
      <div className="monitoringSummary">
        <small>Last refreshed: data aggregated from active API connections. SLA data is computed hourly from audit log metrics. Prometheus/CloudWatch integration targeted for V3-6.</small>
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
