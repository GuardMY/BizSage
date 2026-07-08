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

    # ── V2: Generic raw key-value storage for proxy-pool, vendor registry, API cache ──

    def save_raw(self, key: str, value: str, ttl_seconds: int | None = None) -> None:
        """Save an arbitrary string value under a Redis key, with optional TTL."""
        if ttl_seconds is None:
            self.client.set(key, value)
        else:
            self.client.setex(key, ttl_seconds, value)

    def load_raw(self, key: str) -> bytes | None:
        """Load a raw bytes value from Redis. Returns None if key not found."""
        return self.client.get(key)

    def incr_counter(self, key: str, amount: int = 1, ttl_seconds: int | None = None) -> int:
        """Increment a counter. Sets TTL on first call."""
        value = self.client.incrby(key, amount)
        if ttl_seconds is not None and value == amount:  # first set
            self.client.expire(key, ttl_seconds)
        return value

    def get_counter(self, key: str) -> int:
        """Read a counter value. Returns 0 if key not found."""
        value = self.client.get(key)
        if value is None:
            return 0
        return int(value)
