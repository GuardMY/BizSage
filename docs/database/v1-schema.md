# V1 Database Design

The baseline migration lives at `infra/mysql/init/001_v1_baseline.sql`.

## Core Tables

- `users`: development users, role, encrypted phone and identity columns.
- `conversations`: user-owned diagnosis sessions.
- `messages`: user and Agent message history with source JSON.
- `intelligence`: manually entered or collected intelligence pending approval.
- `knowledge_items`: static baseline and approved knowledge metadata.
- `raw_records`: normalized collector output.

## Required Common Fields

Business tables include:

- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## V1 Notes

- Runtime API currently uses in-memory stores for local MVP validation.
- The MySQL schema is ready for the next persistence pass.
- Sensitive values are encrypted by the API `PrivacyService` before designed persistence.
- Snapshots, cold storage, data lineage, and dynamic weights are deferred beyond V1.
