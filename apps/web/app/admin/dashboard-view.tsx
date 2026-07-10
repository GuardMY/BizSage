"use client";

import type { AdminDashboard } from "../../lib/api-client";
import { adminCodeLabel, adminText, type AdminLocale } from "./admin-i18n";

function PanelHead({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="sectionHead">
      <div className="sectionCopy">
        <h2>{title}</h2>
        <p>{detail}</p>
      </div>
    </div>
  );
}

function StatusBadge({ value, locale }: { value: string; locale: AdminLocale }) {
  return <span className={`adminBadge status-${value.toLowerCase().replaceAll("_", "-")}`}>{adminCodeLabel(locale, value)}</span>;
}

export function DashboardView({ dashboard, locale }: { dashboard: AdminDashboard | null; locale: AdminLocale }) {
  const t = (zh: string, en: string) => adminText(locale, zh, en);
  const metricLabel = (key: string, fallback: string) => ({
    pendingReviews: t("待复核", "Pending reviews"),
    openTickets: t("未结工单", "Open tickets"),
    openAlerts: t("未处理告警", "Open alerts"),
    auditLogs: t("审计日志", "Audit logs"),
    humanIntel: t("人工情报", "Human intelligence"),
    p0Alerts: t("P0 告警", "P0 alerts"),
    collectionSources: t("采集源", "Collection sources"),
    collectionSuccessRate: t("采集成功率", "Collection success rate"),
    knowledgeNodes: t("知识节点", "Knowledge nodes"),
    crawlerHealth: t("爬虫熔断", "Crawler circuits")
  }[key] ?? fallback);
  const metricDetail = (key: string, fallback: string) => ({
    pendingReviews: t("等待复核员判定的情报。", "Intelligence waiting for reviewer verdict."),
    openTickets: t("仍在处理中的运营台账。", "Operational ledger items still active."),
    openAlerts: t("仍需确认的告警。", "Alerts that still require acknowledgement."),
    auditLogs: t("已记录的管理员操作。", "Recorded administrator operations."),
    humanIntel: t("已提交的本地人工情报。", "Submitted local intelligence records."),
    p0Alerts: t("最高严重级别的未处理告警。", "Highest severity open alerts."),
    collectionSources: t("启用中的数据源配置。", "Active data source configurations."),
    collectionSuccessRate: t("近期采集任务成功率。", "Recent collection run success rate."),
    knowledgeNodes: t("维护中的知识记录。", "Maintained knowledge records."),
    crawlerHealth: t("开启的熔断器状态。", "Open circuit breaker states.")
  }[key] ?? fallback);

  if (!dashboard) return <div className="emptyRow">{t("正在加载仪表盘...", "Loading dashboard...")}</div>;

  return (
    <div className="adminStack">
      <section className="adminMetricGrid">
        {dashboard.metrics.map((metric) => (
          <article className={`adminMetric status-${metric.status}`} key={metric.key}>
            <small>{metricLabel(metric.key, metric.label)}</small>
            <strong>{metric.value}</strong>
            <span>{metricDetail(metric.key, metric.detail)}</span>
          </article>
        ))}
      </section>
      <div className="adminGridTwo">
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title={t("风险与告警", "Risk & Alerts")} detail={t("需要立即处理的未结告警和升级工单。", "Open alerts and escalation tickets needing immediate attention.")} />
          {dashboard.urgentAlerts.length === 0 && dashboard.openTickets.length === 0 ? (
            <div className="emptyRow">{t("暂无未处理风险。", "No open risks.")}</div>
          ) : (
            <>
              {dashboard.urgentAlerts.map((alert) => (
                <div className={`dashboardRow status-${alert.level === "P0" ? "critical" : "warning"}`} key={`alert-${alert.id}`}>
                  <div className="dashboardRowMain">
                    <StatusBadge value={alert.level} locale={locale} />
                    <strong>{alert.message}</strong>
                  </div>
                  <div className="dashboardRowMeta">
                    <small>{alert.component} / {adminCodeLabel(locale, alert.status)}</small>
                    <small>{alert.owner ?? t("未分配", "Unassigned")}</small>
                  </div>
                </div>
              ))}
              {dashboard.openTickets.map((ticket) => (
                <div className={`dashboardRow status-${ticket.severity === "P0" ? "critical" : "warning"}`} key={`ticket-${ticket.id}`}>
                  <div className="dashboardRowMain">
                    <StatusBadge value={ticket.severity} locale={locale} />
                    <strong>{ticket.title}</strong>
                  </div>
                  <div className="dashboardRowMeta">
                    <small>{adminCodeLabel(locale, ticket.ticketType)} / {adminCodeLabel(locale, ticket.status)}</small>
                    <small>{ticket.owner ?? t("未分配", "Unassigned")}</small>
                  </div>
                </div>
              ))}
            </>
          )}
        </section>
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title={t("生产健康", "Production Health")} detail={t("采集链路、熔断器和系统指标。", "Collection pipeline, circuit breakers, and system metrics.")} />
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>{t("采集链路", "Collection pipeline")}</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "collectionSuccessRate")?.value ?? "-"} {t("成功率", "success rate")}</small>
              <small>{dashboard.metrics.find(m => m.key === "collectionSources")?.value ?? "0"} {t("个启用源", "active sources")}</small>
            </div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>{t("爬虫熔断", "Crawler circuits")}</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "crawlerHealth")?.value ?? "-"}</small>
            </div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>{t("API 可用性", "API availability")}</strong></div>
            <div className="dashboardRowMeta"><small>{t("运行中", "Operational")}</small></div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>{t("AI 自检", "AI self-check")}</strong></div>
            <div className="dashboardRowMeta"><small>- (V3-6)</small></div>
          </div>
        </section>
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title={t("情报生产", "Intelligence Production")} detail={t("待复核、人工情报队列和知识记录。", "Pending reviews, human intelligence queue, and knowledge records.")} />
          <div className="dashboardRow status-warning">
            <div className="dashboardRowMain">
              <StatusBadge value="PENDING" locale={locale} />
              <strong>{t("待复核", "Pending reviews")}</strong>
            </div>
            <div className="dashboardRowMeta">
              <small>{dashboard.pendingReviews.length} {t("条排队中", "items in queue")}</small>
            </div>
          </div>
          {dashboard.pendingReviews.slice(0, 3).map((review) => (
            <div className="dashboardRow status-warning" key={`review-${review.id}`}>
              <div className="dashboardRowMain">
                <strong style={{ fontSize: 13 }}>{review.title}</strong>
              </div>
              <div className="dashboardRowMeta">
                <small>{review.regionId} / {review.industryId} / {t("置信度", "confidence")} {review.confidence}</small>
              </div>
            </div>
          ))}
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>{t("知识节点", "Knowledge nodes")}</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "knowledgeNodes")?.value ?? "0"} {t("条维护中", "maintained")}</small>
            </div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>{t("人工情报", "Human intelligence")}</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "humanIntel")?.value ?? "0"} {t("条记录", "total records")}</small>
            </div>
          </div>
        </section>
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title={t("商业化", "Commercial")} detail={t("订单、收入、付费转化和报告交付。", "Orders, revenue, paid conversion, and report delivery.")} />
          <div className="emptyRow">
            <p>{t("商业化仪表盘将在 V3-5 接入。", "Commercial dashboard coming in V3-5.")}</p>
            <small>{t("订单金额、付费转化、SLA 可用性", "Order amount, paid conversion, SLA availability")}</small>
          </div>
        </section>
      </div>
    </div>
  );
}
