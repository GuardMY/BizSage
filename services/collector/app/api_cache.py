"""第三方 API 响应缓存和成本统计。

将第三方 API 响应写入 Redis，用于减少重复调用、加速相同查询，并统计供应商调用量。

不同 source_type 可以配置不同 TTL。
"""

from __future__ import annotations

import hashlib
import json
import logging
import time
from dataclasses import dataclass

logger = logging.getLogger(__name__)

# 各来源类型的默认缓存 TTL，单位秒。
DEFAULT_TTL: dict[str, int] = {
    "MARKET_DATA": 300,       # 5 min
    "NEWS_API": 600,          # 10 min
    "ECONOMIC_INDICATORS": 3600,  # 1 hour
    "MOCK_API": 900,          # 15 min
}


@dataclass
class CachedResponse:
    """带缓存元数据的 API 响应。"""

    source_type: str
    query_hash: str
    response: dict
    cached_at: float
    ttl_seconds: int


class ApiCacheManager:
    """管理第三方 API 响应缓存。

    使用示例::

        cache = ApiCacheManager(state_store=redis_store)
        cached = cache.get("MARKET_DATA", {"symbol": "AAPL"})
        if cached:
            return cached
        response = call_external_api(...)
        cache.put("MARKET_DATA", {"symbol": "AAPL"}, response, ttl=300)
    """

    def __init__(self, state_store=None) -> None:
        self._state_store = state_store
        self._hit_count: dict[str, int] = {}
        self._miss_count: dict[str, int] = {}

    # 对外 API。

    def get(self, source_type: str, query: dict) -> dict | None:
        """读取缓存响应；未命中或 Redis 异常时返回 None。"""
        query_hash = self._hash_query(query)
        key = f"collector:api-cache:{source_type}:{query_hash}"

        if self._state_store is None:
            self._miss_count[source_type] = self._miss_count.get(source_type, 0) + 1
            return None

        try:
            raw = self._state_store.load_raw(key)
            if raw is None:
                # 未命中时只更新内存计数，不把缓存缺失视为错误。
                self._miss_count[source_type] = self._miss_count.get(source_type, 0) + 1
                return None

            if isinstance(raw, bytes):
                raw = raw.decode("utf-8")

            data = json.loads(raw)
            self._hit_count[source_type] = self._hit_count.get(source_type, 0) + 1
            logger.debug("API cache hit for %s:%s", source_type, query_hash[:8])
            return data.get("response")
        except Exception:
            # 缓存是性能优化，读取失败不应阻断真实 API 调用。
            self._miss_count[source_type] = self._miss_count.get(source_type, 0) + 1
            logger.debug("API cache read failed for %s", source_type, exc_info=True)
            return None

    def put(
        self,
        source_type: str,
        query: dict,
        response: dict,
        ttl: int | None = None,
        vendor_id: str | None = None,
    ) -> None:
        """按 source_type 和查询参数写入缓存。"""
        query_hash = self._hash_query(query)
        key = f"collector:api-cache:{source_type}:{query_hash}"
        effective_ttl = ttl or DEFAULT_TTL.get(source_type, 600)

        payload = json.dumps({
            "source_type": source_type,
            "query_hash": query_hash,
            "response": response,
            "cached_at": time.time(),
            "ttl_seconds": effective_ttl,
            "vendor_id": vendor_id,
        })

        if self._state_store is not None:
            try:
                self._state_store.save_raw(key, payload, ttl_seconds=effective_ttl)
                logger.debug("API cache stored for %s:%s (ttl=%ds)", source_type, query_hash[:8], effective_ttl)
            except Exception:
                logger.debug("API cache write failed for %s", source_type, exc_info=True)

    def get_hit_rate(self, source_type: str | None = None) -> float:
        """返回指定来源或整体的缓存命中率；无样本时返回 -1.0。"""
        if source_type:
            hits = self._hit_count.get(source_type, 0)
            misses = self._miss_count.get(source_type, 0)
        else:
            hits = sum(self._hit_count.values())
            misses = sum(self._miss_count.values())
        total = hits + misses
        return hits / total if total > 0 else -1.0

    def stats(self) -> dict:
        """返回各来源类型的缓存统计。"""
        result = {}
        all_types = set(self._hit_count.keys()) | set(self._miss_count.keys())
        for st in all_types:
            hits = self._hit_count.get(st, 0)
            misses = self._miss_count.get(st, 0)
            total = hits + misses
            result[st] = {
                "hits": hits,
                "misses": misses,
                "total": total,
                "hit_rate": f"{hits/total:.2f}" if total > 0 else "N/A",
            }
        return result

    # 内部工具。

    @staticmethod
    def _hash_query(query: dict) -> str:
        """把查询参数规范化后生成稳定缓存键哈希。"""
        canonical = json.dumps(query, sort_keys=True, ensure_ascii=True)
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]
