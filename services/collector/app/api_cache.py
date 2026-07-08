"""V2: Third-party API response cache with cost tracking.

Caches API responses in Redis to:
- Reduce redundant third-party API calls (cost savings)
- Speed up repeated queries for the same data
- Track per-vendor call volume and cost

Cache TTL is configurable per source type.
"""

from __future__ import annotations

import hashlib
import json
import logging
import time
from dataclasses import dataclass

logger = logging.getLogger(__name__)

# Default cache TTLs per source type (seconds)
DEFAULT_TTL: dict[str, int] = {
    "MARKET_DATA": 300,       # 5 min
    "NEWS_API": 600,          # 10 min
    "ECONOMIC_INDICATORS": 3600,  # 1 hour
    "MOCK_API": 900,          # 15 min
}


@dataclass
class CachedResponse:
    """A cached API response with metadata."""

    source_type: str
    query_hash: str
    response: dict
    cached_at: float
    ttl_seconds: int


class ApiCacheManager:
    """Manages caching of third-party API responses in Redis.

    Usage::

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

    # ── Public API ──────────────────────────────────────────

    def get(self, source_type: str, query: dict) -> dict | None:
        """Retrieve a cached response. Returns None on cache miss."""
        query_hash = self._hash_query(query)
        key = f"collector:api-cache:{source_type}:{query_hash}"

        if self._state_store is None:
            self._miss_count[source_type] = self._miss_count.get(source_type, 0) + 1
            return None

        try:
            raw = self._state_store.load_raw(key)
            if raw is None:
                self._miss_count[source_type] = self._miss_count.get(source_type, 0) + 1
                return None

            if isinstance(raw, bytes):
                raw = raw.decode("utf-8")

            data = json.loads(raw)
            self._hit_count[source_type] = self._hit_count.get(source_type, 0) + 1
            logger.debug("API cache hit for %s:%s", source_type, query_hash[:8])
            return data.get("response")
        except Exception:
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
        """Cache an API response with TTL."""
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
        """Return cache hit rate (0.0-1.0) for a source type, or aggregate."""
        if source_type:
            hits = self._hit_count.get(source_type, 0)
            misses = self._miss_count.get(source_type, 0)
        else:
            hits = sum(self._hit_count.values())
            misses = sum(self._miss_count.values())
        total = hits + misses
        return hits / total if total > 0 else -1.0

    def stats(self) -> dict:
        """Return cache statistics per source type."""
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

    # ── Internal ────────────────────────────────────────────

    @staticmethod
    def _hash_query(query: dict) -> str:
        """Stable hash of query parameters for cache key."""
        canonical = json.dumps(query, sort_keys=True, ensure_ascii=True)
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]
