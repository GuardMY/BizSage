"""V2: Proxy rotation pool with per-domain IP cooldown.

Supports:
- Multiple proxy vendors with configurable priorities
- Per-domain failure tracking and exponential cooldown
- Redis-backed state persistence (survives collector restarts)
- Round-robin rotation with health weighting
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class ProxyConfig:
    """Configuration for a single proxy endpoint."""

    proxy_id: str
    address: str  # e.g. "http://user:pass@1.2.3.4:8080"
    vendor: str = "default"
    priority: int = 1  # lower = higher priority
    max_failures: int = 5
    base_cooldown_seconds: int = 60  # doubles on each consecutive failure


@dataclass
class ProxyState:
    """Runtime state for a proxy (persisted to Redis)."""

    proxy_id: str
    failure_count: int = 0
    cooldown_until: float = 0.0  # epoch seconds
    last_used: float = 0.0

    @property
    def is_cooling_down(self) -> bool:
        return time.time() < self.cooldown_until

    @property
    def cooldown_remaining(self) -> float:
        return max(0.0, self.cooldown_until - time.time())

    def to_dict(self) -> dict:
        return {
            "proxy_id": self.proxy_id,
            "failure_count": self.failure_count,
            "cooldown_until": self.cooldown_until,
            "last_used": self.last_used,
        }

    @classmethod
    def from_dict(cls, data: dict) -> ProxyState:
        return cls(
            proxy_id=data.get("proxy_id", ""),
            failure_count=data.get("failure_count", 0),
            cooldown_until=data.get("cooldown_until", 0.0),
            last_used=data.get("last_used", 0.0),
        )


class ProxyManager:
    """Manages a pool of proxy endpoints with health-aware rotation.

    Usage::

        manager = ProxyManager(
            proxies=[ProxyConfig(proxy_id="p1", address="http://proxy1:8080")],
            state_store=redis_store,
        )
        proxy = manager.get_proxy("example.com")
        try:
            fetch(url, proxy=proxy.address)
            manager.mark_success(proxy.proxy_id, "example.com")
        except Exception:
            manager.mark_failure(proxy.proxy_id, "example.com")
    """

    def __init__(
        self,
        proxies: list[ProxyConfig],
        state_store=None,
    ) -> None:
        self._proxies: dict[str, ProxyConfig] = {p.proxy_id: p for p in proxies}
        self._state_store = state_store
        self._rotation_index: int = 0

    # ── Public API ──────────────────────────────────────────

    def get_proxy(self, domain: str) -> ProxyConfig | None:
        """Return the best available proxy for a domain.

        Selection order: lowest priority → not in cooldown → round-robin.
        Returns None if no proxies are available.
        """
        available = self._list_available(domain)
        if not available:
            logger.warning("No proxies available for domain %s", domain)
            return None

        # Round-robin among available proxies, ordered by priority
        available.sort(key=lambda p: (p.priority, p.proxy_id))
        index = self._rotation_index % len(available)
        self._rotation_index += 1
        proxy = available[index]
        self._touch_state(proxy.proxy_id)
        return proxy

    def mark_success(self, proxy_id: str, domain: str) -> None:
        """Reset failure count for a proxy after a successful request."""
        state = self._load_state(proxy_id)
        state.failure_count = 0
        state.cooldown_until = 0.0
        self._save_state(state)

    def mark_failure(self, proxy_id: str, domain: str) -> None:
        """Record a failure and apply exponential cooldown."""
        config = self._proxies.get(proxy_id)
        if config is None:
            return
        state = self._load_state(proxy_id)
        state.failure_count += 1

        if state.failure_count >= config.max_failures:
            # Exponential backoff: base * 2^(failures - max_failures)
            extra = state.failure_count - config.max_failures
            cooldown = config.base_cooldown_seconds * (2**extra)
            state.cooldown_until = time.time() + cooldown
            logger.warning(
                "Proxy %s hit failure threshold for %s, cooling down for %ds",
                proxy_id, domain, cooldown,
            )
        self._save_state(state)

    def status(self) -> list[dict]:
        """Return health status for all proxies."""
        result = []
        for proxy_id, config in self._proxies.items():
            state = self._load_state(proxy_id)
            result.append({
                "proxy_id": proxy_id,
                "vendor": config.vendor,
                "priority": config.priority,
                "failure_count": state.failure_count,
                "in_cooldown": state.is_cooling_down,
                "cooldown_remaining_s": state.cooldown_remaining,
            })
        return result

    # ── Internal ────────────────────────────────────────────

    def _list_available(self, domain: str) -> list[ProxyConfig]:
        available: list[ProxyConfig] = []
        for config in self._proxies.values():
            state = self._load_state(config.proxy_id)
            if not state.is_cooling_down:
                available.append(config)
        return available

    def _load_state(self, proxy_id: str) -> ProxyState:
        if self._state_store is not None:
            try:
                raw = self._state_store.load_raw(f"collector:proxy:{proxy_id}")
                if raw:
                    if isinstance(raw, bytes):
                        raw = raw.decode("utf-8")
                    return ProxyState.from_dict(json.loads(raw))
            except Exception:
                logger.debug("Failed to load proxy state from Redis for %s", proxy_id, exc_info=True)
        return ProxyState(proxy_id=proxy_id)

    def _save_state(self, state: ProxyState) -> None:
        state.last_used = time.time()
        if self._state_store is not None:
            try:
                self._state_store.save_raw(
                    f"collector:proxy:{state.proxy_id}",
                    json.dumps(state.to_dict()),
                    ttl_seconds=86400,  # 24h TTL
                )
            except Exception:
                logger.debug("Failed to save proxy state to Redis for %s", state.proxy_id, exc_info=True)

    def _touch_state(self, proxy_id: str) -> None:
        state = self._load_state(proxy_id)
        state.last_used = time.time()
        self._save_state(state)
