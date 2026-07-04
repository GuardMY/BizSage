# Database Convention

All business tables include these common fields:

- `id`
- `create_time`
- `update_time`
- `source_id`
- `weight`
- `region_id`
- `industry_id`

## Privacy

- User private business data lives in dedicated tables.
- Sensitive fields are AES encrypted before persistence.
- Phone and identity-card values are shown only in masked form.
- Plain sensitive values must not be logged.

## V1 Storage

- Hot structured data: MySQL.
- Hot cache: Redis.
- Vector search: Qdrant.
- Object/cold storage and snapshots are deferred beyond V1.
