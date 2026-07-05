from app.redis_state import FakeRedis, RedisStateStore


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
