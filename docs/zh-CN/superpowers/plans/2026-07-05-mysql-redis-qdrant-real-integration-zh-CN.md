# MySQL Redis Qdrant 真实接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变当前 Web 对外契约的前提下，让 BizSage 真实使用 MySQL 作为 API 存储、Redis 作为 collector 运行时状态、Qdrant 作为 AI worker 向量后端。

**Architecture:** 保持 API 契约稳定，并延续当前 `JdbcTemplate` 的 MySQL-backed stores。为 `services/collector` 增加聚焦的 Redis adapter，用于指纹去重和最近快照 fallback；为 `services/ai-worker` 增加聚焦的 Qdrant adapter 和确定性 embeddings，使向量 upsert 与向量 search 成为主检索路径，同时现有业务过滤规则仍保留在 Python 侧。

**Tech Stack:** Spring Boot + `JdbcTemplate`、MySQL、FastAPI、`redis`、`qdrant-client`、Python pytest，以及仓库中已存在的 Docker Compose 环境变量。

---

### Task 1：锁定 API 的 MySQL 契约并暴露知识列表接口

**Files:**
- Modify: `services/api/src/main/java/com/bizsage/api/knowledge/KnowledgeController.java`
- Modify: `services/api/src/main/java/com/bizsage/api/knowledge/KnowledgeStore.java`
- Modify: `services/api/src/test/java/com/bizsage/api/BusinessWorkflowApiTest.java`
- Modify: `services/api/src/test/java/com/bizsage/api/V2GrayReleaseApiTest.java`

- [ ] **Step 1：先写知识列表接口的失败测试**

```java
@Test
void operatorsCanListImportedKnowledgeItems() throws Exception {
  String token = login("operator", "password");

  mvc.perform(post("/api/knowledge/import")
      .header("Authorization", "Bearer " + token)
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
          {
            "title":"Redis fingerprint baseline",
            "content":"Collector dedupe records must be queryable for vector sync.",
            "industryId":"general",
            "regionId":"cn-default",
            "linkId":"collector-runtime",
            "sourceId":"seed-runtime"
          }
          """))
    .andExpect(status().isOk());

  mvc.perform(get("/api/knowledge")
      .header("Authorization", "Bearer " + token))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.code").value("OK"))
    .andExpect(jsonPath("$.data[0].title").value("Redis fingerprint baseline"));
}
```

- [ ] **Step 2：运行测试，确认它先失败**

Run: `mvn -Dtest=BusinessWorkflowApiTest test`

Expected: FAIL，因为当前还没有 `GET /api/knowledge`。

- [ ] **Step 3：补最小实现**

```java
// KnowledgeController.java
@GetMapping
ApiResponse<List<KnowledgeItem>> list(HttpServletRequest request) {
  return ApiResponse.ok(store.list(), request.getAttribute(RequestIds.ATTRIBUTE).toString());
}
```

```java
// KnowledgeStore.java
public List<KnowledgeItem> list() {
  return jdbcTemplate.query(baseSelect() + " order by id", mapper());
}
```

- [ ] **Step 4：重新运行测试，确认转绿**

Run: `mvn -Dtest=BusinessWorkflowApiTest test`

Expected: PASS，新加的 `GET /api/knowledge` 断言通过。

- [ ] **Step 5：运行完整 API 测试**

Run: `mvn test`

Expected: PASS，认证、会话、报告、V2 灰度测试均无回归。

- [ ] **Step 6：提交**

```bash
git add services/api/src/main/java/com/bizsage/api/knowledge/KnowledgeController.java services/api/src/main/java/com/bizsage/api/knowledge/KnowledgeStore.java services/api/src/test/java/com/bizsage/api/BusinessWorkflowApiTest.java services/api/src/test/java/com/bizsage/api/V2GrayReleaseApiTest.java
git commit -m "feat: expose mysql-backed knowledge listing"
```

### Task 2：为 Collector 增加 Redis 运行时状态适配层

**Files:**
- Modify: `services/collector/requirements.txt`
- Create: `services/collector/app/redis_state.py`
- Create: `services/collector/tests/test_redis_state.py`

- [ ] **Step 1：先写 Redis adapter 的失败测试**

```python
def test_fingerprint_store_round_trip():
    store = RedisStateStore(FakeRedis())
    fingerprint = "fp-123"

    assert store.has_fingerprint(fingerprint) is False

    store.save_fingerprint(fingerprint)

    assert store.has_fingerprint(fingerprint) is True


def test_recent_snapshot_round_trip():
    store = RedisStateStore(FakeRedis())
    payload = {"source": "vendor", "record": {"title": "snapshot"}}

    store.save_recent_snapshot("market-api", payload, ttl_seconds=300)

    assert store.load_recent_snapshot("market-api") == payload
```

- [ ] **Step 2：运行测试，确认它先失败**

Run: `python -m pytest tests/test_redis_state.py -q`

Expected: FAIL，因为当前还没有 `RedisStateStore` 和 `FakeRedis`。

- [ ] **Step 3：增加 Redis 依赖和最小 adapter**

```python
# requirements.txt
redis==5.2.1
```

```python
# redis_state.py
import json


class RedisStateStore:
    def __init__(self, client):
        self.client = client

    def has_fingerprint(self, fingerprint: str) -> bool:
        return bool(self.client.exists(f"collector:fingerprint:{fingerprint}"))

    def save_fingerprint(self, fingerprint: str, ttl_seconds: int | None = None) -> None:
        key = f"collector:fingerprint:{fingerprint}"
        if ttl_seconds is None:
            self.client.set(key, "1")
        else:
            self.client.setex(key, ttl_seconds, "1")

    def save_recent_snapshot(self, source_key: str, payload: dict, ttl_seconds: int) -> None:
        self.client.setex(f"collector:snapshot:{source_key}", ttl_seconds, json.dumps(payload))

    def load_recent_snapshot(self, source_key: str) -> dict | None:
        raw = self.client.get(f"collector:snapshot:{source_key}")
        if raw is None:
            return None
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8")
        return json.loads(raw)
```

- [ ] **Step 4：为测试补一个小型 FakeRedis**

```python
class FakeRedis:
    def __init__(self):
        self.data = {}

    def exists(self, key):
        return 1 if key in self.data else 0

    def set(self, key, value):
        self.data[key] = value

    def setex(self, key, ttl, value):
        self.data[key] = value

    def get(self, key):
        return self.data.get(key)
```

- [ ] **Step 5：重新运行测试，确认转绿**

Run: `python -m pytest tests/test_redis_state.py -q`

Expected: PASS。

- [ ] **Step 6：提交**

```bash
git add services/collector/requirements.txt services/collector/app/redis_state.py services/collector/tests/test_redis_state.py
git commit -m "feat: add collector redis runtime state adapter"
```

### Task 3：把 Collector 去重和快照 fallback 真正接到 Redis

**Files:**
- Modify: `services/collector/app/resilience.py`
- Modify: `services/collector/app/main.py`
- Modify: `services/collector/tests/test_resilience.py`

- [ ] **Step 1：先写 collector 集成失败测试**

```python
def test_incremental_fingerprint_uses_redis_backed_store():
    store = RedisStateStore(FakeRedis())
    record = {"url": "https://example.test/a", "content": "hello world"}

    first = incremental_fingerprint(record, state_store=store)
    store.save_fingerprint(first.fingerprint)
    second = incremental_fingerprint(record, state_store=store)

    assert first.is_duplicate is False
    assert second.is_duplicate is True


def test_vendor_failover_reads_recent_snapshot_from_store():
    store = RedisStateStore(FakeRedis())
    store.save_recent_snapshot("vendor-a", {"cached": True}, ttl_seconds=300)

    result = fetch_with_vendor_failover(
        CollectionJob(id="job-1", source="vendor-a"),
        vendors=[lambda: (_ for _ in ()).throw(RuntimeError("timeout"))],
        state_store=store,
        snapshot_key="vendor-a",
    )

    assert result["source"] == "recent-snapshot"
    assert result["record"] == {"cached": True}
```

- [ ] **Step 2：运行韧性测试，确认它先失败**

Run: `python -m pytest tests/test_resilience.py -q`

Expected: FAIL，因为当前 resilience 函数还不接收 Redis-backed state store。

- [ ] **Step 3：为 resilience 函数增加显式 store hook**

```python
def incremental_fingerprint(record: dict, existing_fingerprints: set[str] | None = None, state_store=None) -> FingerprintResult:
    content = str(record.get("content", ""))
    url = str(record.get("url", ""))
    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    fingerprint = hashlib.sha256(f"{url}|{content_hash}".encode("utf-8")).hexdigest()
    known = existing_fingerprints or set()
    is_duplicate = fingerprint in known
    if state_store is not None and state_store.has_fingerprint(fingerprint):
        is_duplicate = True
    return FingerprintResult(
        fingerprint=fingerprint,
        content_hash=content_hash,
        sim_hash=simhash(content),
        is_duplicate=is_duplicate,
    )
```

```python
def fetch_with_vendor_failover(job, vendors, recent_snapshot=None, state_store=None, snapshot_key: str | None = None) -> dict:
    errors = []
    for vendor in vendors:
        try:
            record = vendor()
            if state_store is not None and snapshot_key is not None:
                state_store.save_recent_snapshot(snapshot_key, record, ttl_seconds=900)
            return {
                "source": "vendor",
                "record": record,
                "telemetry": {"job_id": job.id, "queue_depth": job.queue_depth, "errors": errors},
            }
        except Exception as exception:
            errors.append(str(exception))

    cached = state_store.load_recent_snapshot(snapshot_key) if state_store and snapshot_key else None
    return {
        "source": "recent-snapshot",
        "record": cached or recent_snapshot or {},
        "telemetry": {"job_id": job.id, "queue_depth": job.queue_depth, "errors": errors},
    }
```

- [ ] **Step 4：在 FastAPI app 中构建 Redis store**

```python
import os
import redis

from app.redis_state import RedisStateStore


def build_state_store():
    client = redis.Redis(
        host=os.getenv("REDIS_HOST", "localhost"),
        port=int(os.getenv("REDIS_PORT", "16379")),
        decode_responses=False,
    )
    return RedisStateStore(client)
```

- [ ] **Step 5：运行完整 collector 套件**

Run: `python -m pytest`

Expected: PASS，包括现有 collector、governance、resilience 测试。

- [ ] **Step 6：提交**

```bash
git add services/collector/app/resilience.py services/collector/app/main.py services/collector/tests/test_resilience.py
git commit -m "feat: wire collector dedupe and snapshot fallback to redis"
```

### Task 4：为 AI Worker 增加 Qdrant 向量存储和确定性 Embeddings

**Files:**
- Modify: `services/ai-worker/requirements.txt`
- Create: `services/ai-worker/app/embeddings.py`
- Create: `services/ai-worker/app/vector_store.py`
- Create: `services/ai-worker/tests/test_vector_store.py`

- [ ] **Step 1：先写向量存储失败测试**

```python
def test_upsert_points_serializes_knowledge_metadata():
    client = FakeQdrantClient()
    store = QdrantVectorStore(client, collection_name="bizsage_knowledge")
    item = KnowledgeItem(
        id="k1",
        title="Cashflow baseline",
        content="Watch table turnover and rent ratio.",
        source_url="seed://cashflow",
        source_id="seed",
        weight=0.9,
        confidence=0.8,
        industry_id="general",
        region_id="cn-default",
    )

    store.upsert_knowledge([item])

    assert client.upserts[0]["collection_name"] == "bizsage_knowledge"
    assert client.upserts[0]["points"][0]["payload"]["title"] == "Cashflow baseline"


def test_search_returns_payload_back_to_rag_shape():
    client = FakeQdrantClient(search_hits=[{
        "id": "k1",
        "score": 0.88,
        "payload": {"title": "Cashflow baseline", "content": "Watch turnover", "source_url": "seed://cashflow", "source_id": "seed", "weight": 0.9, "confidence": 0.8, "industry_id": "general", "region_id": "cn-default", "entitlement": "FREE", "review_confidence": 0.85, "historical_quality": 0.85}
    }])
    store = QdrantVectorStore(client, collection_name="bizsage_knowledge")

    results = store.search([0.1, 0.2, 0.3], limit=3)

    assert results[0]["id"] == "k1"
    assert results[0]["score"] == 0.88
```

- [ ] **Step 2：运行 AI worker 测试，确认它先失败**

Run: `python -m pytest tests/test_vector_store.py -q`

Expected: FAIL，因为当前还没有 vector store 模块。

- [ ] **Step 3：增加依赖和确定性 embedding 代码**

```python
# requirements.txt
qdrant-client==1.12.1
```

```python
# embeddings.py
import hashlib


def embed_text(text: str, dimensions: int = 32) -> list[float]:
    values = [0.0] * dimensions
    for token in text.lower().split():
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        for index in range(dimensions):
            values[index] += (digest[index % len(digest)] / 255.0) - 0.5
    norm = sum(value * value for value in values) ** 0.5
    if norm == 0:
        return values
    return [value / norm for value in values]
```

- [ ] **Step 4：补 Qdrant adapter**

```python
class QdrantVectorStore:
    def __init__(self, client, collection_name: str):
        self.client = client
        self.collection_name = collection_name

    def upsert_knowledge(self, items: list[KnowledgeItem]) -> None:
        points = []
        for item in items:
            vector = embed_text(f"{item.title} {item.content}")
            points.append({
                "id": item.id,
                "vector": vector,
                "payload": {
                    "title": item.title,
                    "content": item.content,
                    "source_url": item.source_url,
                    "source_id": item.source_id,
                    "weight": item.weight,
                    "confidence": item.confidence,
                    "industry_id": item.industry_id,
                    "region_id": item.region_id,
                    "entitlement": item.entitlement,
                    "review_confidence": item.review_confidence,
                    "historical_quality": item.historical_quality,
                },
            })
        self.client.upsert(collection_name=self.collection_name, points=points)
```

- [ ] **Step 5：重新运行测试，确认转绿**

Run: `python -m pytest tests/test_vector_store.py -q`

Expected: PASS。

- [ ] **Step 6：提交**

```bash
git add services/ai-worker/requirements.txt services/ai-worker/app/embeddings.py services/ai-worker/app/vector_store.py services/ai-worker/tests/test_vector_store.py
git commit -m "feat: add qdrant vector store primitives"
```

### Task 5：让 Qdrant 成为 AI 检索主路径

**Files:**
- Modify: `services/ai-worker/app/rag.py`
- Modify: `services/ai-worker/app/main.py`
- Modify: `services/ai-worker/tests/test_v2_reasoning.py`
- Modify: `services/ai-worker/tests/test_agent.py`
- Create: `services/ai-worker/tests/test_qdrant_rag.py`

- [ ] **Step 1：先写 Qdrant-backed 检索失败测试**

```python
def test_search_knowledge_prefers_qdrant_candidates_before_python_filters():
    store = FakeVectorStore(results=[
        {"id": "paid-1", "score": 0.91, "payload": {"title": "Paid rent alert", "content": "Rent risk", "source_url": "seed://paid", "source_id": "seed", "weight": 0.9, "confidence": 0.88, "industry_id": "general", "region_id": "cn-default", "entitlement": "PAID", "review_confidence": 0.9, "historical_quality": 0.9}},
        {"id": "free-1", "score": 0.77, "payload": {"title": "Free baseline", "content": "Cashflow baseline", "source_url": "seed://free", "source_id": "seed", "weight": 0.8, "confidence": 0.8, "industry_id": "general", "region_id": "cn-default", "entitlement": "FREE", "review_confidence": 0.85, "historical_quality": 0.85}},
    ])

    results = search_knowledge(
        "cash flow",
        [],
        membership_level="FREE",
        vector_store=store,
    )

    assert [item.id for item in results] == ["free-1"]


def test_search_endpoint_upserts_knowledge_before_querying_qdrant():
    client = FakeVectorStore(results=[])
    request = SearchRequest(query="cash flow", knowledge=[{"id": "k1", "title": "Baseline", "content": "Turnover matters"}])

    response = search(request, vector_store=client)

    assert client.upserted_ids == ["k1"]
    assert response["results"] == []
```

- [ ] **Step 2：运行 AI worker 检索测试，确认它先失败**

Run: `python -m pytest tests/test_qdrant_rag.py -q`

Expected: FAIL，因为当前 `search_knowledge` 和 route 还不接受 vector store 主路径。

- [ ] **Step 3：实现 Qdrant-first 检索并保留 Python 侧过滤**

```python
def search_knowledge(query, knowledge, limit=5, region_id=None, industry_id=None, membership_level="FREE", vector_store=None):
    if vector_store is None:
        return _search_knowledge_locally(query, knowledge, limit, region_id, industry_id, membership_level)

    if knowledge:
        vector_store.upsert_knowledge(knowledge)

    candidates = vector_store.search(embed_text(query), limit=max(limit * 3, 10))
    results = []
    for candidate in candidates:
        payload = candidate["payload"]
        if region_id and payload["region_id"] != region_id:
            continue
        if industry_id and payload["industry_id"] != industry_id:
            continue
        if payload.get("entitlement", "FREE") == "PAID" and membership_level not in {"SEED_PAID", "INTERNAL"}:
            continue
        quality = (
            payload["weight"] * 0.35
            + payload["confidence"] * 0.2
            + payload.get("review_confidence", 0.85) * 0.25
            + payload.get("historical_quality", 0.85) * 0.2
        )
        score = candidate["score"] * quality
        results.append(SearchResult(
            id=str(candidate["id"]),
            title=payload["title"],
            content=payload["content"],
            source_url=payload["source_url"],
            source_id=payload["source_id"],
            weight=float(payload["weight"]),
            confidence=float(payload["confidence"]),
            score=round(score, 6),
            entitlement=payload.get("entitlement", "FREE"),
        ))
    return sorted(results, key=lambda result: result.score, reverse=True)[:limit]
```

- [ ] **Step 4：在 `main.py` 中构建 vector store**

```python
import os
from qdrant_client import QdrantClient

from app.vector_store import QdrantVectorStore


def build_vector_store():
    client = QdrantClient(url=os.getenv("QDRANT_URL", "http://localhost:16333"))
    return QdrantVectorStore(client, collection_name=os.getenv("QDRANT_COLLECTION", "bizsage_knowledge"))
```

- [ ] **Step 5：运行完整 AI worker 套件**

Run: `python -m pytest`

Expected: PASS，包括现有 diagnosis 和 V2 reasoning 覆盖。

- [ ] **Step 6：提交**

```bash
git add services/ai-worker/app/rag.py services/ai-worker/app/main.py services/ai-worker/tests/test_v2_reasoning.py services/ai-worker/tests/test_agent.py services/ai-worker/tests/test_qdrant_rag.py
git commit -m "feat: switch ai worker retrieval to qdrant"
```

### Task 6：更新里程碑记录与变更日志

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `CHANGELOG-zh-CN.md`
- Modify: `docs/en/milestones/v2-verification-results.md`
- Modify: `docs/zh-CN/milestones/v2-verification-results-zh-CN.md`

- [ ] **Step 1：在实现全部转绿后补文档**

```md
- Date: 2026-07-05
- Type: feat
- Modules: services/api, services/collector, services/ai-worker, infra
- Main changes: MySQL-backed API contract preserved, Redis-backed collector dedupe and snapshot caching added, Qdrant-backed AI retrieval added.
- Verification: `mvn test`, `python -m pytest` in collector, `python -m pytest` in ai-worker
- Unfinished items: Docker startup and live infrastructure drills still not executed in this environment.
```

- [ ] **Step 2：重新运行要求的验证命令**

Run:

```bash
cd services/api && mvn test
cd services/collector && python -m pytest
cd services/ai-worker && python -m pytest
```

Expected: 全部通过，并保留新鲜输出。

- [ ] **Step 3：在 V2 验证文档中记录通过项和未执行项**

```md
- Passed in current environment: API, collector, and AI worker suites
- Not executed: Docker compose startup, live MySQL/Redis/Qdrant health validation
- Reason: environment limitation / Docker CLI unavailable
- Remediation: rerun on Docker-capable staging machine
```

- [ ] **Step 4：提交**

```bash
git add CHANGELOG.md CHANGELOG-zh-CN.md docs/en/milestones/v2-verification-results.md docs/zh-CN/milestones/v2-verification-results-zh-CN.md
git commit -m "docs: record mysql redis qdrant integration verification"
```
