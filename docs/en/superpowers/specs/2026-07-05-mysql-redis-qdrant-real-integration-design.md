# MySQL Redis Qdrant Real Integration Design

## Goal

Upgrade BizSage from infrastructure placeholders to real milestone-aligned storage integration by keeping MySQL as the authoritative API data store, adding Redis to live collection deduplication and snapshot caching, and making Qdrant the primary vector retrieval backend for the AI worker.

## Scope

This design covers only three real integrations:

- MySQL real read and write usage in `services/api`
- Redis real runtime usage in `services/collector`
- Qdrant real vector write and search usage in `services/ai-worker`

This design intentionally excludes:

- full Docker startup verification and long-running environment validation
- complete failure-drill and disaster-recovery implementation
- full multi-level Redis caching across every service
- full production GraphRAG architecture

## Current State

### MySQL

`services/api` already uses Spring `JdbcTemplate` stores and a MySQL runtime datasource in the default profile. The current gap is not that API storage is fake, but that the real runtime path is not yet validated in this environment and test coverage still uses H2.

### Redis

Redis exists only as infrastructure and environment planning. No current runtime service imports a Redis client or persists operational state into Redis.

### Qdrant

Qdrant exists only as infrastructure and environment planning. The AI worker currently performs in-process token overlap and cosine similarity in Python memory and does not write or search vectors in a real vector database.

## Recommended Architecture

### 1. MySQL Remains The Source Of Truth For API Data

Keep the existing API persistence model:

- users, conversations, intelligence, paid intelligence, knowledge metadata, audit-style tables remain in MySQL
- Spring `JdbcTemplate` remains the persistence access layer
- API contracts and Web behavior remain stable

No ORM migration is introduced. This preserves the current repository shape and minimizes milestone risk.

### 2. Redis Powers Collector Runtime State

Redis will be connected to one real operational chain in `services/collector`:

- incremental fingerprint deduplication state
- recent snapshot fallback cache for vendor failover

This means:

- duplicate public-page or mock-api records can be detected against Redis-backed state instead of only in-memory inputs
- recent successful fallback records can survive process restarts and be reused during vendor failure paths

Redis will be used by the collector first, not spread across all services in this milestone. This keeps the first real Redis usage focused and testable.

### 3. Qdrant Becomes The Primary Retrieval Backend In AI Worker

The AI worker retrieval path changes from local-only scoring to:

1. receive knowledge items
2. create embeddings locally with a deterministic lightweight embedder
3. upsert vectors plus metadata into Qdrant
4. search Qdrant for top candidates
5. apply existing BizSage reranking and filtering rules in Python

The existing local lexical scoring remains only as a controlled fallback or test helper, not as the primary production path.

## Component Design

### API Layer

#### Responsibilities

- continue writing business records to MySQL
- expose stable contracts to Web and internal callers
- provide knowledge payloads that the AI worker can vectorize

#### Planned Changes

- keep current `JdbcTemplate` stores
- add a focused API-side knowledge listing endpoint or service helper if the AI worker needs a clean fetch source for vector sync
- keep report and conversation flows unchanged unless needed for vector-backed retrieval

### Collector Layer

#### Responsibilities

- use Redis for deduplication state and recent snapshot cache
- keep current resilience semantics visible through existing collector APIs

#### Planned Changes

- add a Redis client dependency in `services/collector`
- introduce a small runtime store wrapper, for example `redis_state.py`
- move incremental fingerprint existence checks into Redis-backed operations
- persist recent successful vendor responses under explicit TTL-backed keys

### AI Worker Layer

#### Responsibilities

- write searchable vectors to Qdrant
- search Qdrant as the main retrieval backend
- preserve BizSage region, industry, entitlement, review-confidence, and historical-quality filtering/reranking

#### Planned Changes

- add Qdrant client dependency in `services/ai-worker`
- add a small vector store adapter, for example `vector_store.py`
- add a deterministic embedding module, for example `embeddings.py`
- modify `rag.py` so retrieval starts from Qdrant candidates instead of only in-memory cosine over token bags

## Data Model

### MySQL

No major schema redesign is required for this milestone. Existing API tables remain authoritative for business records.

If knowledge vector sync requires metadata stability, each knowledge record must continue to expose:

- `id`
- `title`
- `content`
- `source_id`
- `region_id`
- `industry_id`
- entitlement or equivalent access metadata where applicable

### Redis Keys

Collector Redis usage should use explicit namespaced keys:

- `collector:fingerprint:<fingerprint>`
- `collector:snapshot:<job-or-source-key>`

Each key must have a documented TTL policy:

- fingerprint keys may be long-lived or milestone-configured
- snapshot keys should use short operational TTLs suitable for failover fallback

### Qdrant Payload

Each vector record should contain:

- `id`
- `title`
- `content`
- `source_url`
- `source_id`
- `industry_id`
- `region_id`
- `entitlement`
- `weight`
- `confidence`
- `review_confidence`
- `historical_quality`

This keeps retrieval filtering aligned with BizSage governance documents.

## Runtime Flow

### Collector With Redis

1. collector receives a collection request
2. normalized record fingerprint is computed
3. Redis is checked for prior fingerprint presence
4. duplicate records are flagged without depending on in-memory caller state
5. successful vendor responses are cached as recent snapshots in Redis
6. vendor failover reads Redis snapshot when all vendors fail

### AI Worker With Qdrant

1. API or tests provide knowledge records
2. AI worker embeds each record
3. vectors are upserted into Qdrant collection
4. diagnosis or RAG search embeds the user query
5. Qdrant returns nearest candidates
6. Python applies entitlement, region, industry, and quality reranking
7. downstream diagnosis uses those filtered sources

## Testing Strategy

### MySQL

- keep current API suite passing
- keep H2-backed test profile for fast unit and integration coverage
- add at least one repository-level test proving persistence assumptions remain aligned with MySQL-style SQL behavior

This milestone does not require replacing test H2 with testcontainers or live MySQL, because the requested scope is real integration in code paths, not full Docker validation.

### Redis

- add collector tests around Redis-backed fingerprint persistence
- add collector tests for recent snapshot cache read and write
- ensure tests fail first when Redis-backed store is absent or bypassed

Tests may use a fake in-memory Redis substitute only if the production code depends on a clear adapter interface and a separate integration-oriented path exists. If a lightweight local Redis-compatible path is practical, prefer that.

### Qdrant

- add AI worker tests for vector upsert calls
- add AI worker tests for vector search calls
- add retrieval tests proving entitlement and region filtering still happen after Qdrant candidate retrieval
- keep a fallback-path test for controlled no-Qdrant or empty-result scenarios

## Error Handling

### Redis

- collector should fail closed for malformed state writes
- collector may fall back to empty snapshot behavior when Redis has no cached snapshot
- duplicate detection must not silently treat Redis failures as valid duplicate hits

### Qdrant

- if Qdrant is unavailable, the AI worker may return a controlled fallback path using the existing local scorer only if the response remains explicitly bounded and test-covered
- unsupported or empty retrieval must still produce the current information-insufficient or low-confidence behavior

## Acceptance Criteria

This design is complete when all of the following are true:

- API business persistence continues through real `JdbcTemplate` plus MySQL runtime configuration
- collector reads and writes real Redis runtime state for deduplication and recent snapshot caching
- AI worker writes vectors to Qdrant and searches Qdrant for retrieval
- current region, industry, entitlement, confidence, and reranking rules still apply after storage integration
- relevant module tests pass with fresh verification evidence

## Files Expected To Change

Likely API files:

- `services/api/src/main/resources/application.yml`
- `services/api/src/main/java/com/bizsage/api/...` only where knowledge fetch helpers are needed
- `services/api/src/test/java/com/bizsage/api/...`

Likely collector files:

- `services/collector/requirements.txt`
- `services/collector/app/main.py`
- `services/collector/app/resilience.py`
- `services/collector/app/collectors.py` or a new `services/collector/app/redis_state.py`
- `services/collector/tests/...`

Likely AI worker files:

- `services/ai-worker/requirements.txt`
- `services/ai-worker/app/main.py`
- `services/ai-worker/app/rag.py`
- new `services/ai-worker/app/embeddings.py`
- new `services/ai-worker/app/vector_store.py`
- `services/ai-worker/tests/...`

## Risks And Tradeoffs

- Qdrant integration adds the largest implementation surface because the current AI worker is intentionally simple.
- Redis can easily sprawl if introduced in multiple services at once; this design prevents that by limiting first use to collector runtime state.
- Replacing local-only retrieval with Qdrant must preserve current business filtering semantics, otherwise retrieval quality may regress even if infrastructure is more real.

## Recommendation

Implement in this order:

1. lock MySQL API behavior and tests
2. add Redis-backed collector runtime state
3. add Qdrant-backed AI retrieval

That order preserves a stable API baseline while introducing Redis and Qdrant in narrowly testable slices.
