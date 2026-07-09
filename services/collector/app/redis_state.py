import json


class RedisStateStore:
    """Collector 共享 Redis 状态封装。

    存储内容包括采集指纹、最近成功快照，以及代理池/供应商/缓存模块使用的原始键值。
    这里保持薄封装，方便上层模块自行决定失败时如何降级。
    """

    def __init__(self, client):
        self.client = client

    def has_fingerprint(self, fingerprint: str) -> bool:
        """判断采集指纹是否已存在。"""
        return bool(self.client.exists(f"collector:fingerprint:{fingerprint}"))

    def save_fingerprint(self, fingerprint: str, ttl_seconds: int | None = None) -> None:
        """保存采集指纹；可设置 TTL 形成时间窗口内去重。"""
        key = f"collector:fingerprint:{fingerprint}"
        if ttl_seconds is None:
            self.client.set(key, "1")
        else:
            self.client.setex(key, ttl_seconds, "1")

    def save_recent_snapshot(self, source_key: str, payload: dict, ttl_seconds: int) -> None:
        """保存最近一次成功采集快照，用于供应商全部失败时兜底。"""
        self.client.setex(f"collector:snapshot:{source_key}", ttl_seconds, json.dumps(payload))

    def load_recent_snapshot(self, source_key: str) -> dict | None:
        """读取最近成功采集快照。"""
        raw = self.client.get(f"collector:snapshot:{source_key}")
        if raw is None:
            return None
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8")
        return json.loads(raw)

    # 通用原始键值存储，供代理池、供应商注册表和 API 缓存复用。

    def save_raw(self, key: str, value: str, ttl_seconds: int | None = None) -> None:
        """按原始 Redis key 保存字符串值，可选 TTL。"""
        if ttl_seconds is None:
            self.client.set(key, value)
        else:
            self.client.setex(key, ttl_seconds, value)

    def load_raw(self, key: str) -> bytes | None:
        """读取原始 Redis 值；key 不存在时返回 None。"""
        return self.client.get(key)

    def incr_counter(self, key: str, amount: int = 1, ttl_seconds: int | None = None) -> int:
        """递增计数器；首次写入时可设置 TTL。"""
        value = self.client.incrby(key, amount)
        if ttl_seconds is not None and value == amount:  # first set
            self.client.expire(key, ttl_seconds)
        return value

    def get_counter(self, key: str) -> int:
        """读取计数器；不存在时返回 0。"""
        value = self.client.get(key)
        if value is None:
            return 0
        return int(value)
