"""代理轮换池，支持按域名失败冷却。

支持多供应商优先级、失败指数冷却、Redis 状态持久化和健康感知轮换。
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class ProxyConfig:
    """单个代理节点配置。"""

    proxy_id: str
    address: str  # 例如 "http://user:pass@1.2.3.4:8080"
    vendor: str = "default"
    priority: int = 1  # 数字越小优先级越高。
    max_failures: int = 5
    base_cooldown_seconds: int = 60  # 连续失败后按指数倍数增加。


@dataclass
class ProxyState:
    """代理运行时状态，可持久化到 Redis。"""

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
    """管理代理池并按健康状态轮换。

    使用示例::

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

    # 对外 API。

    def get_proxy(self, domain: str) -> ProxyConfig | None:
        """为目标域名选择一个可用代理。

        选择顺序：优先级 → 未冷却 → 轮询；没有可用代理时返回 None。
        """
        available = self._list_available(domain)
        if not available:
            logger.warning("No proxies available for domain %s", domain)
            return None

        # 在同一优先级序列中轮询，避免所有请求压到同一个代理。
        available.sort(key=lambda p: (p.priority, p.proxy_id))
        index = self._rotation_index % len(available)
        self._rotation_index += 1
        proxy = available[index]
        self._touch_state(proxy.proxy_id)
        return proxy

    def mark_success(self, proxy_id: str, domain: str) -> None:
        """请求成功后清空代理失败计数和冷却状态。"""
        state = self._load_state(proxy_id)
        state.failure_count = 0
        state.cooldown_until = 0.0
        self._save_state(state)

    def mark_failure(self, proxy_id: str, domain: str) -> None:
        """记录代理失败，并在达到阈值后进入指数冷却。"""
        config = self._proxies.get(proxy_id)
        if config is None:
            return
        state = self._load_state(proxy_id)
        state.failure_count += 1

        if state.failure_count >= config.max_failures:
            # 指数退避：base * 2^(failures - max_failures)。
            extra = state.failure_count - config.max_failures
            cooldown = config.base_cooldown_seconds * (2**extra)
            state.cooldown_until = time.time() + cooldown
            logger.warning(
                "Proxy %s hit failure threshold for %s, cooling down for %ds",
                proxy_id, domain, cooldown,
            )
        self._save_state(state)

    def status(self) -> list[dict]:
        """返回所有代理的健康状态。"""
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

    # 内部工具。

    def _list_available(self, domain: str) -> list[ProxyConfig]:
        """列出当前未处于冷却期的代理。"""
        available: list[ProxyConfig] = []
        for config in self._proxies.values():
            state = self._load_state(config.proxy_id)
            if not state.is_cooling_down:
                available.append(config)
        return available

    def _load_state(self, proxy_id: str) -> ProxyState:
        """从 Redis 读取代理状态，读取失败时退回默认健康状态。"""
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
        """保存代理状态；Redis 写失败只记录调试日志。"""
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
        """更新代理最近使用时间。"""
        state = self._load_state(proxy_id)
        state.last_used = time.time()
        self._save_state(state)
