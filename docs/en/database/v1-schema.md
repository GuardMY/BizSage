# BizSage Database Design

The baseline migration lives at `infra/mysql/init/001_v1_baseline.sql`.

## Core Tables

- `users`: development users, role, encrypted phone and identity columns. V2 adds membership level and consultation preferences for gray-release permission checks.
- `conversations`: user-owned diagnosis sessions.
- `messages`: user and Agent message history with source JSON.
- `intelligence`: free/public manually entered or collected intelligence pending approval.
- `paid_intelligence`: V2 paid-only intelligence stored separately from free/public intelligence.
- `knowledge_items`: static baseline and approved knowledge metadata.
- `raw_records`: normalized collector output.
- `intelligence_snapshots`: daily, weekly, and monthly intelligence snapshots.
- `review_work_orders`: suspicious/conflicting intelligence review tasks.
- `alert_events`: operations alerts for API, collector, AI worker, cache, queue, and database flows.
- `audit_logs`: core operation audit trail.
- `report_jobs`: diagnosis report export metadata and source lists.
- `collection_jobs`: collector retry, queue-depth, and circuit-breaker state.
- `dead_letter_records`: failed collection payloads with classified reasons.

## Required Common Fields

Business tables include:

- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## V1/V2 Notes

- Runtime API currently uses in-memory stores for local MVP and V2 gray-release validation.
- The MySQL schema now includes V2 persistence targets for the next repository pass.
- Sensitive values are encrypted by the API `PrivacyService` before designed persistence.
- Full cold storage, full lineage, dynamic weights, orders, invoices, and member center are deferred beyond V2.
