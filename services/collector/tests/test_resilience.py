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
    store.save_recent_snapshot("vendor-a", {"cached": True}, ttl_seconds=300)

    result = fetch_with_vendor_failover(
        CollectionJob(id="job-1", source="vendor-a"),
        vendors=[lambda: (_ for _ in ()).throw(RuntimeError("timeout"))],
        state_store=store,
        snapshot_key="vendor-a",
    )

    assert result["source"] == "recent-snapshot"
    assert result["record"] == {"cached": True}
