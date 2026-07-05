from fastapi.testclient import TestClient

from app import main
from app.resilience import (
    CircuitBreaker,
    CollectionJob,
    classify_dead_letter,
    fetch_with_vendor_failover,
    incremental_fingerprint,
    retry_with_backoff,
)
from app.redis_state import RedisStateStore


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
        value = self.data.get(key)
        if isinstance(value, str):
            return value.encode("utf-8")
        return value


class BrokenExistsRedis(FakeRedis):
    def exists(self, key):
        raise ConnectionError("redis exists unavailable")


class BrokenSnapshotRedis(FakeRedis):
    def setex(self, key, ttl, value):
        if key.startswith("collector:snapshot:"):
            raise ConnectionError("redis snapshot write unavailable")
        super().setex(key, ttl, value)

    def get(self, key):
        if key.startswith("collector:snapshot:"):
            raise ConnectionError("redis snapshot read unavailable")
        return super().get(key)


def test_incremental_fingerprint_marks_duplicate_by_url_and_hash():
    existing = {
        incremental_fingerprint(
            {
                "url": "https://example.com/a",
                "content": "same body",
            }
        ).fingerprint
    }

    result = incremental_fingerprint(
        {
            "url": "https://example.com/a",
            "content": "same body",
        },
        existing_fingerprints=existing,
    )

    assert result.is_duplicate is True
    assert result.content_hash
    assert isinstance(result.sim_hash, int)


def test_incremental_fingerprint_uses_redis_backed_store():
    store = RedisStateStore(FakeRedis())
    record = {"url": "https://example.test/a", "content": "hello world"}

    first = incremental_fingerprint(record, state_store=store)
    store.save_fingerprint(first.fingerprint)
    second = incremental_fingerprint(record, state_store=store)

    assert first.is_duplicate is False
    assert second.is_duplicate is True


def test_retry_with_backoff_routes_permanent_failure_to_dead_letter():
    attempts = []

    def operation():
        attempts.append("try")
        raise TimeoutError("vendor timeout")

    result = retry_with_backoff(operation, max_attempts=3)

    assert result.success is False
    assert result.attempts == 3
    assert result.dead_letter.reason == "RETRY_EXHAUSTED"
    assert classify_dead_letter(result.dead_letter.error) == "TRANSIENT_TIMEOUT"


def test_circuit_breaker_opens_and_skips_domain_until_cooldown():
    breaker = CircuitBreaker(failure_threshold=2)

    breaker.record_failure("example.com")
    breaker.record_failure("example.com")

    assert breaker.allow("example.com") is False
    assert breaker.state("example.com") == "OPEN"


def test_vendor_failover_uses_recent_snapshot_when_all_vendors_fail():
    job = CollectionJob(id="job-1", source="mock-api", queue_depth=8)

    result = fetch_with_vendor_failover(
        job,
        vendors=[
            lambda: (_ for _ in ()).throw(ConnectionError("vendor-a down")),
            lambda: (_ for _ in ()).throw(ConnectionError("vendor-b down")),
        ],
        recent_snapshot={"title": "cached snapshot", "content": "fallback"},
    )

    assert result["source"] == "recent-snapshot"
    assert result["record"]["title"] == "cached snapshot"
    assert result["telemetry"]["queue_depth"] == 8


def test_vendor_failover_reads_recent_snapshot_from_store():
    store = RedisStateStore(FakeRedis())
    store.save_recent_snapshot("vendor-a", {}, ttl_seconds=300)

    result = fetch_with_vendor_failover(
        CollectionJob(id="job-1", source="vendor-a"),
        vendors=[lambda: (_ for _ in ()).throw(RuntimeError("timeout"))],
        state_store=store,
        snapshot_key="vendor-a",
        recent_snapshot={"cached": True},
    )

    assert result["source"] == "recent-snapshot"
    assert result["record"] == {}


def test_collect_public_page_route_uses_redis_backed_dedupe(monkeypatch):
    store = RedisStateStore(FakeRedis())
    client = TestClient(main.app)
    payload = {
        "url": "https://example.test/policy",
        "html": "<html><head><title>Policy</title></head><body>Fresh update</body></html>",
        "industry_id": "general",
        "region_id": "cn-default",
        "link_id": "policy",
    }

    monkeypatch.setattr(main, "build_state_store", lambda: store)

    first = client.post("/collect/public-page", json=payload)
    second = client.post("/collect/public-page", json=payload)

    assert first.status_code == 200
    assert len(first.json()["records"]) == 1
    assert second.status_code == 200
    assert second.json()["records"] == []


def test_collect_mock_api_route_uses_recent_snapshot_fallback(monkeypatch):
    store = RedisStateStore(FakeRedis())
    client = TestClient(main.app)

    monkeypatch.setattr(main, "build_state_store", lambda: store)

    first = client.post(
        "/collect/mock-api",
        json={
            "snapshot_key": "vendor-a",
            "items": [
                {
                    "title": "Platform fee update",
                    "content": "Commission changed this week.",
                    "industry_id": "general",
                    "region_id": "cn-default",
                    "link_id": "channel",
                }
            ]
        },
    )
    second = client.post("/collect/mock-api", json={"items": [], "snapshot_key": "vendor-a"})

    assert first.status_code == 200
    assert len(first.json()["records"]) == 1
    assert second.status_code == 200
    assert len(second.json()["records"]) == 1
    assert second.json()["records"][0]["title"] == "Platform fee update"


def test_collect_mock_api_route_preserves_multiple_items_and_dedupes(monkeypatch):
    store = RedisStateStore(FakeRedis())
    client = TestClient(main.app)

    monkeypatch.setattr(main, "build_state_store", lambda: store)

    payload = {
        "snapshot_key": "multi-items",
        "items": [
            {
                "title": "Platform fee update",
                "content": "Commission changed this week.",
                "industry_id": "general",
                "region_id": "cn-default",
                "link_id": "channel",
                "url": "https://example.test/mock/1",
            },
            {
                "title": "Delivery slowdown",
                "content": "Average delivery times increased.",
                "industry_id": "general",
                "region_id": "cn-default",
                "link_id": "logistics",
                "url": "https://example.test/mock/2",
            },
        ]
    }

    first = client.post("/collect/mock-api", json=payload)
    second = client.post("/collect/mock-api", json=payload)

    assert first.status_code == 200
    assert len(first.json()["records"]) == 2
    assert second.status_code == 200
    assert second.json()["records"] == []


def test_collect_public_page_route_stays_available_when_redis_lookup_fails(monkeypatch):
    store = RedisStateStore(BrokenExistsRedis())
    client = TestClient(main.app)

    monkeypatch.setattr(main, "build_state_store", lambda: store)

    response = client.post(
        "/collect/public-page",
        json={
            "url": "https://example.test/policy",
            "html": "<html><head><title>Policy</title></head><body>Fresh update</body></html>",
            "industry_id": "general",
            "region_id": "cn-default",
            "link_id": "policy",
        },
    )

    assert response.status_code == 200
    assert len(response.json()["records"]) == 1


def test_collect_mock_api_route_stays_available_when_snapshot_cache_fails(monkeypatch):
    store = RedisStateStore(BrokenSnapshotRedis())
    client = TestClient(main.app)

    monkeypatch.setattr(main, "build_state_store", lambda: store)

    first = client.post(
        "/collect/mock-api",
        json={
            "snapshot_key": "broken-cache",
            "items": [
                {
                    "title": "Platform fee update",
                    "content": "Commission changed this week.",
                    "industry_id": "general",
                    "region_id": "cn-default",
                    "link_id": "channel",
                }
            ]
        },
    )
    second = client.post("/collect/mock-api", json={"items": [], "snapshot_key": "broken-cache"})

    assert first.status_code == 200
    assert len(first.json()["records"]) == 1
    assert second.status_code == 200
    assert second.json()["records"] == []


def test_collect_mock_api_route_requires_matching_snapshot_key_for_fallback(monkeypatch):
    store = RedisStateStore(FakeRedis())
    client = TestClient(main.app)

    monkeypatch.setattr(main, "build_state_store", lambda: store)

    first = client.post(
        "/collect/mock-api",
        json={
            "snapshot_key": "caller-a",
            "items": [
                {
                    "title": "Caller A update",
                    "content": "Caller A snapshot",
                    "industry_id": "general",
                    "region_id": "cn-default",
                    "link_id": "channel",
                }
            ],
        },
    )
    second = client.post("/collect/mock-api", json={"items": [], "snapshot_key": "caller-b"})

    assert first.status_code == 200
    assert len(first.json()["records"]) == 1
    assert second.status_code == 200
    assert second.json()["records"] == []
