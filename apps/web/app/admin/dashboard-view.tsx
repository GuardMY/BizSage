"use client";

import type { AdminDashboard } from "../../lib/api-client";

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

function StatusBadge({ value }: { value: string }) {
  return <span className={`adminBadge status-${value.toLowerCase().replaceAll("_", "-")}`}>{value}</span>;
}

export function DashboardView({ dashboard }: { dashboard: AdminDashboard | null }) {
  if (!dashboard) return <div className="emptyRow">Loading dashboard...</div>;
  return (
    <div className="adminStack">
      <section className="adminMetricGrid">
        {(dashboard?.metrics ?? []).map((metric) => (
          <article className={`adminMetric status-${metric.status}`} key={metric.key}>
            <small>{metric.label}</small>
            <strong>{metric.value}</strong>
            <span>{metric.detail}</span>
          </article>
        ))}
      </section>
      <div className="adminGridTwo">
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title="Risk & Alerts" detail="Open alerts and escalation tickets needing immediate attention." />
          {dashboard.urgentAlerts.length === 0 && dashboard.openTickets.length === 0 ? (
            <div className="emptyRow">No open risks.</div>
          ) : (
            <>
              {dashboard.urgentAlerts.map((alert) => (
                <div className={`dashboardRow status-${alert.level === "P0" ? "critical" : "warning"}`} key={`alert-${alert.id}`}>
                  <div className="dashboardRowMain">
                    <StatusBadge value={alert.level} />
                    <strong>{alert.message}</strong>
                  </div>
                  <div className="dashboardRowMeta">
                    <small>{alert.component} / {alert.status}</small>
                    <small>{alert.owner ?? "Unassigned"}</small>
                  </div>
                </div>
              ))}
              {dashboard.openTickets.map((ticket) => (
                <div className={`dashboardRow status-${ticket.severity === "P0" ? "critical" : "warning"}`} key={`ticket-${ticket.id}`}>
                  <div className="dashboardRowMain">
                    <StatusBadge value={ticket.severity} />
                    <strong>{ticket.title}</strong>
                  </div>
                  <div className="dashboardRowMeta">
                    <small>{ticket.ticketType} / {ticket.status}</small>
                    <small>{ticket.owner ?? "Unassigned"}</small>
                  </div>
                </div>
              ))}
            </>
          )}
        </section>
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title="Production Health" detail="Collection pipeline, circuit breakers, and system metrics." />
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>Collection pipeline</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "collectionSuccessRate")?.value ?? "—"} success rate</small>
              <small>{dashboard.metrics.find(m => m.key === "collectionSources")?.value ?? "0"} active sources</small>
            </div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>Crawler circuits</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "crawlerHealth")?.value ?? "—"}</small>
            </div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>API availability</strong></div>
            <div className="dashboardRowMeta"><small>Operational</small></div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>AI self-check</strong></div>
            <div className="dashboardRowMeta"><small>— (V3-6)</small></div>
          </div>
        </section>
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title="Intelligence Production" detail="Pending reviews, human intelligence queue, and knowledge records." />
          <div className="dashboardRow status-warning">
            <div className="dashboardRowMain">
              <StatusBadge value="PENDING" />
              <strong>Pending reviews</strong>
            </div>
            <div className="dashboardRowMeta">
              <small>{dashboard.pendingReviews.length} items in queue</small>
            </div>
          </div>
          {dashboard.pendingReviews.slice(0, 3).map((review) => (
            <div className="dashboardRow status-warning" key={`review-${review.id}`}>
              <div className="dashboardRowMain">
                <strong style={{ fontSize: 13 }}>{review.title}</strong>
              </div>
              <div className="dashboardRowMeta">
                <small>{review.regionId} / {review.industryId} / confidence {review.confidence}</small>
              </div>
            </div>
          ))}
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>Knowledge nodes</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "knowledgeNodes")?.value ?? "0"} maintained</small>
            </div>
          </div>
          <div className="dashboardRow status-healthy">
            <div className="dashboardRowMain"><strong>Human intelligence</strong></div>
            <div className="dashboardRowMeta">
              <small>{dashboard.metrics.find(m => m.key === "humanIntel")?.value ?? "0"} total records</small>
            </div>
          </div>
        </section>
        <section className="workspaceCard adminPanel adminDashboardSection">
          <PanelHead title="Commercial" detail="Orders, revenue, paid conversion, and report delivery." />
          <div className="emptyRow">
            <p>Commercial dashboard coming in V3-5.</p>
            <small>Order amount, paid conversion, SLA availability</small>
          </div>
        </section>
      </div>
    </div>
  );
}
