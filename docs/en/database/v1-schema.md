# BizSage MySQL Table Catalog

## Scope And Source Of Truth

This catalog describes the MySQL schema created by `services/api/src/main/resources/db/migration/mysql/V1__baseline.sql` as of 2026-07-11. Flyway is the single source for MySQL schema creation, validation, and upgrades when the API starts.

The baseline creates 30 application tables. Flyway also creates and owns `flyway_schema_history`; that infrastructure metadata table is not counted below. The baseline deliberately declares indexes and uniqueness rules but no foreign-key constraints. References such as `user_id`, `conversation_id`, and `node_id` are logical relationships whose integrity is maintained by application workflows.

## Account, Conversation, And Memory

| Table | Purpose | Key relationships and controls |
| --- | --- | --- |
| `users` | Stores login identity, role, membership level, consultation preferences, shared web/admin locale, and encrypted phone/identity values. | Primary key `id`; `username` is unique. `region_id` and `industry_id` define the default business scope. |
| `conversations` | Stores user-owned diagnosis conversations and their active/archive status. | `user_id` logically references `users.id`; indexed by `user_id`. `owner_username` preserves the display/login owner used by current workflows. |
| `messages` | Stores user and Agent messages, evidence sources, confidence/self-check metadata, and active-context state. | `conversation_id` logically references `conversations.id` and is indexed. `summary_group_id` associates messages with a compaction group. |
| `conversation_summaries` | Stores versioned rolling summaries and the inclusive message-ID range covered by each summary. | `conversation_id` logically references `conversations.id` and is indexed; `active` selects the current summary. |
| `user_memory_profiles` | Stores structured long-term user memories such as preferences and reusable business facts. | Logical references to `users`, source conversations, and source messages. `(user_id, memory_category, memory_key, status)` is unique; lookup and expiry fields support lifecycle management. |
| `user_memory_embeddings` | Tracks unstructured memory text and its synchronization state with Qdrant. | Optionally links to `user_memory_profiles`; also records the user, source conversation/message, Qdrant point ID, sync timestamp, expiry, and status. Indexed by `(user_id, status)`. |

## Intelligence, Conflict, And Governance

| Table | Purpose | Key relationships and controls |
| --- | --- | --- |
| `intelligence` | Stores free/public collected or manually entered intelligence awaiting or completing review. | `content_hash` is unique for exact deduplication; `sim_hash` supports similarity checks. Status and industry/region scope are indexed. |
| `paid_intelligence` | Stores entitlement-gated intelligence separately from free/public intelligence. | Status and `(industry_id, region_id, entitlement)` are indexed; content and similarity hashes support deduplication workflows. |
| `intelligence_snapshots` | Stores scoped daily/weekly/monthly-style aggregate payloads with retention metadata. | `parent_snapshot_id` supports snapshot lineage. Scope is indexed; `retention_days` and `expires_at` control expiration. |
| `false_information_ledger` | Archives rejected, disproved, or rumor-matched intelligence with the reason and responsible actor. | Optionally links to original intelligence. Reason, scope, and content hash are indexed for investigation and repeat detection. |
| `conflict_resolutions` | Records similarity-conflict branches, compared weights, routing decisions, and resolution notes. | Optionally records incoming/existing intelligence IDs and a review ticket ID. Branch, scope, and incoming intelligence are indexed. |
| `review_work_orders` | Provides a generic review queue for suspicious or conflicting business records. | `target_type` plus `target_id` identifies the subject; `(status, reason)` is indexed for work assignment. |
| `alert_events` | Stores operational alerts for API, collector, AI worker, cache, queue, and database flows. | `(status, alert_level)` is indexed; owner and update time support acknowledgement and resolution. |
| `audit_logs` | Stores the core actor/action/target/result audit trail. | `(action, actor)` is indexed. `target_type` and string `target_id` allow auditing heterogeneous resources. |

## Admin Review, Knowledge, Collection, And Risk

| Table | Purpose | Key relationships and controls |
| --- | --- | --- |
| `admin_intelligence_reviews` | Stores Admin review workflow state, verdict, reviewer, reason, and evidence for intelligence. | `intelligence_id` logically references `intelligence.id`; review status/verdict and intelligence ID are indexed. |
| `admin_tickets` | Stores Admin operational/governance tickets and their severity, owner, next action, and state. | `target_type` plus `target_id` identifies the subject; status/severity and target are indexed. |
| `admin_human_intelligence` | Stores manually submitted field intelligence with location, collector, entitlement, confidence, and review state. | Status/entitlement and industry/region scope are indexed. |
| `admin_knowledge_nodes` | Stores the stable identity and lifecycle pointers for a managed knowledge article. | Draft, review, and published version IDs logically reference `admin_knowledge_versions.id`. `(slug, industry_id, region_id, link_id)` is unique. |
| `admin_knowledge_versions` | Stores each managed knowledge content version and its author/reviewer workflow metadata. | `node_id` logically references `admin_knowledge_nodes.id`; `(node_id, version_number)` is unique and review lookups are indexed. |
| `admin_knowledge_publications` | Records publish, rollback, and related lifecycle actions for knowledge versions. | `node_id` and `version_id` logically reference the managed node/version; both publication lookup paths are indexed. |
| `admin_collection_sources` | Stores collection-source configuration, scheduling, retry/circuit-breaker state, compliance notes, proxy configuration, and last-run telemetry. | Status/next-run and circuit/failure-count indexes support scheduling and recovery. JSON fields hold source-specific configuration. |
| `admin_collection_keywords` | Stores include/exclude-style keyword rules for a collection source. | `source_config_id` logically references `admin_collection_sources.id`; source/status/match mode are indexed. |
| `admin_collection_job_runs` | Stores one collection execution's trigger, status, item counts, timing, and error details. | `source_config_id` logically references the source configuration; `job_id` correlates the run with its job. Both paths are indexed. |
| `admin_risk_rules` | Stores configurable risk thresholds, scope, severity, enablement, rollout mode, and version. | `scope_json` supports rule-specific targeting; `version` tracks configuration evolution. |

## Operations, Delivery, Knowledge, And Ingestion

| Table | Purpose | Key relationships and controls |
| --- | --- | --- |
| `sla_data_points` | Stores aggregate request volume, error count, latency percentiles, and uptime for a time window. | Indexed by `window_start` for time-series reads. |
| `report_jobs` | Stores user report-export type/state, self-check result, and evidence source list. | `user_id` logically references `users.id`; indexed by `(user_id, report_type)`. |
| `collection_jobs` | Stores collector job status, queue depth, retry count, and circuit-breaker state. | Indexed by `(status, source_type)` for workers and operational views. |
| `dead_letter_records` | Stores failed collection payloads, classified reasons, and error messages for replay or investigation. | `job_id` optionally correlates the failed record with a collection job; reason is indexed. |
| `knowledge_items` | Stores retrieval-ready baseline or approved knowledge content and provenance metadata. | Full-text index on `(title, content)` and scope index on `(industry_id, region_id)`. |
| `raw_records` | Stores normalized collector output before downstream intelligence/knowledge processing. | Source type and industry/region scope are indexed; `metadata_json` preserves source-specific attributes. |

## Shared Column Conventions

Most scoped business records use some or all of the following columns; they are conventions, not a claim that every table contains every column:

- `source_id`: provenance or producing subsystem.
- `weight`: source/business confidence weight.
- `region_id`, `industry_id`, and sometimes `link_id`: tenant-like business scope.
- `create_time`, `update_time`: creation and last-update timestamps. Append-oriented event tables may intentionally omit `update_time`.
- `status`, `review_status`, or another domain-specific state column: lifecycle state controlled by the owning workflow.

Sensitive values are encrypted by the API `PrivacyService` before persistence. JSON columns are used for variable-shape evidence, payloads, scopes, and provider-specific configuration; query-critical state remains in typed columns with indexes.

## Migration And Maintenance Notes

- A new MySQL database is initialized by Flyway from `V1__baseline.sql` when the API starts.
- Existing non-empty MySQL databases without Flyway history use `baseline-on-migrate=true` at version 1. Operators must confirm such a database already matches the V1 baseline because baselining records history without recreating missing V1 objects.
- Flyway SQL migrations must be idempotent: guard schema changes and seed data so reruns or already-upgraded databases do not fail.
- Add new tables or columns only through Flyway and update this English/Chinese catalog plus both change logs in the same change.
- Full cold storage, complete lineage, dynamic weights, orders, invoices, and member-center storage remain deferred beyond the current baseline.
