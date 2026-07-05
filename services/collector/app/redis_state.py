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
