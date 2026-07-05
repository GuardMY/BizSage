from app import redis_state
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


def test_production_module_does_not_define_fake_redis():
    assert hasattr(redis_state, "RedisStateStore") is True
    assert hasattr(redis_state, "FakeRedis") is False


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
