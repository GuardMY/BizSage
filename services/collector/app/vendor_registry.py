"""第三方 API 多供应商注册表。

按 source_type 管理多个供应商，并跟踪健康度、优先级、限流命中和调用成本。
供应商状态可写入 Redis，collector 重启后仍能延续降级判断。
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class VendorConfig:
    """单个第三方 API 供应商配置。"""

    vendor_id: str
    source_type: str  # 例如 "MARKET_DATA"、"NEWS_API"、"ECONOMIC_INDICATORS"。
    api_key: str = ""
    base_url: str = ""
    priority: int = 1  # 数字越小越优先。
    rate_limit_per_minute: int = 60
    cost_per_call: float = 0.0
    max_consecutive_failures: int = 5
    health_score: float = 1.0  # 0.0-1.0，初始健康。


@dataclass
class VendorState:
    """供应商运行时状态，可持久化到 Redis。"""

    vendor_id: str
    success_count: int = 0
    failure_count: int = 0
    consecutive_failures: int = 0
    last_success: float = 0.0
    last_failure: float = 0.0
    rate_limit_hits: int = 0
    degraded: bool = False


class VendorRegistry:
    """带健康感知失败转移的供应商注册表。

    使用示例::

        registry = VendorRegistry(
            vendors=[
                VendorConfig(vendor_id="vendor-a", source_type="MARKET_DATA", priority=1),
                VendorConfig(vendor_id="vendor-b", source_type="MARKET_DATA", priority=2),
            ],
            state_store=redis_store,
        )
        vendors = registry.get_healthy_vendors("MARKET_DATA")
        for vendor in vendors:
            try:
                result = call_api(vendor)
                registry.record_success(vendor.vendor_id)
                break
            except Exception:
                registry.record_failure(vendor.vendor_id)
    """

    def __init__(
        self,
        vendors: list[VendorConfig],
        state_store=None,
    ) -> None:
        self._vendors: dict[str, VendorConfig] = {v.vendor_id: v for v in vendors}
        self._state_store = state_store
        self._call_counts: dict[str, int] = {}  # in-memory cost tracking

    # 对外 API。

    def get_healthy_vendors(self, source_type: str) -> list[VendorConfig]:
        """返回指定来源类型可用的供应商列表。

        正常情况下排除已降级供应商；若全部降级则 fail-open 返回全部，避免数据源彻底中断。
        """
        candidates = [v for v in self._vendors.values() if v.source_type == source_type]
        if not candidates:
            return []

        # 排序规则：未降级优先，其次优先级，再按健康分倒序。
        healthy = [v for v in candidates if not self._is_degraded(v.vendor_id)]
        if healthy:
            healthy.sort(key=lambda v: (v.priority, -v.health_score))
            return healthy

        # 全部降级时开放失败：仍按优先级返回全部供应商，由调用方决定是否尝试。
        logger.warning("All vendors degraded for source_type=%s — failing open", source_type)
        candidates.sort(key=lambda v: v.priority)
        return candidates

    def record_success(self, vendor_id: str) -> None:
        """记录一次供应商成功调用，并逐步恢复健康分。"""
        self._call_counts[vendor_id] = self._call_counts.get(vendor_id, 0) + 1
        state = self._load_state(vendor_id)
        state.success_count += 1
        state.consecutive_failures = 0
        state.last_success = time.time()
        state.degraded = False
        # 成功后小步恢复健康分，避免一次成功就完全抹平历史故障。
        config = self._vendors.get(vendor_id)
        if config and config.health_score < 1.0:
            config.health_score = min(1.0, config.health_score + 0.1)
        self._save_state(state)

    def record_failure(self, vendor_id: str) -> None:
        """记录一次供应商失败，并在连续失败达到阈值时降级。"""
        config = self._vendors.get(vendor_id)
        if config is None:
            return
        state = self._load_state(vendor_id)
        state.failure_count += 1
        state.consecutive_failures += 1
        state.last_failure = time.time()

        if state.consecutive_failures >= config.max_consecutive_failures:
            state.degraded = True
            config.health_score = max(0.0, config.health_score - 0.3)
            logger.warning(
                "Vendor %s degraded after %d consecutive failures (health=%.2f)",
                vendor_id, state.consecutive_failures, config.health_score,
            )
        self._save_state(state)

    def record_rate_limit(self, vendor_id: str) -> None:
        """记录供应商限流命中，用于后续容量和成本分析。"""
        state = self._load_state(vendor_id)
        state.rate_limit_hits += 1
        self._save_state(state)

    def get_cost_report(self) -> dict:
        """返回当前进程会话内各供应商调用成本。"""
        report = {}
        for vendor_id, count in self._call_counts.items():
            config = self._vendors.get(vendor_id)
            cost = config.cost_per_call * count if config else 0.0
            report[vendor_id] = {
                "calls": count,
                "cost_per_call": config.cost_per_call if config else 0.0,
                "total_cost": cost,
            }
        return report

    def status(self) -> list[dict]:
        """返回所有供应商健康状态。"""
        result = []
        for vendor_id, config in self._vendors.items():
            state = self._load_state(vendor_id)
            result.append({
                "vendor_id": vendor_id,
                "source_type": config.source_type,
                "priority": config.priority,
                "health_score": f"{config.health_score:.2f}",
                "degraded": state.degraded,
                "success_count": state.success_count,
                "failure_count": state.failure_count,
                "consecutive_failures": state.consecutive_failures,
                "rate_limit_hits": state.rate_limit_hits,
            })
        return result

    # 内部工具。

    def _is_degraded(self, vendor_id: str) -> bool:
        """判断供应商是否处于降级状态。"""
        state = self._load_state(vendor_id)
        return state.degraded

    def _load_state(self, vendor_id: str) -> VendorState:
        """从 Redis 读取供应商状态，读取失败时返回默认状态。"""
        if self._state_store is not None:
            try:
                raw = self._state_store.load_raw(f"collector:vendor:{vendor_id}")
                if raw:
                    import json as _json
                    if isinstance(raw, bytes):
                        raw = raw.decode("utf-8")
                    data = _json.loads(raw)
                    return VendorState(
                        vendor_id=data.get("vendor_id", vendor_id),
                        success_count=data.get("success_count", 0),
                        failure_count=data.get("failure_count", 0),
                        consecutive_failures=data.get("consecutive_failures", 0),
                        last_success=data.get("last_success", 0.0),
                        last_failure=data.get("last_failure", 0.0),
                        rate_limit_hits=data.get("rate_limit_hits", 0),
                        degraded=data.get("degraded", False),
                    )
            except Exception:
                logger.debug("Failed to load vendor state from Redis", exc_info=True)
        return VendorState(vendor_id=vendor_id)

    def _save_state(self, state: VendorState) -> None:
        """保存供应商状态；Redis 写失败只记录调试日志。"""
        if self._state_store is not None:
            try:
                import json as _json
                self._state_store.save_raw(
                    f"collector:vendor:{state.vendor_id}",
                    _json.dumps({
                        "vendor_id": state.vendor_id,
                        "success_count": state.success_count,
                        "failure_count": state.failure_count,
                        "consecutive_failures": state.consecutive_failures,
                        "last_success": state.last_success,
                        "last_failure": state.last_failure,
                        "rate_limit_hits": state.rate_limit_hits,
                        "degraded": state.degraded,
                    }),
                    ttl_seconds=86400,
                )
            except Exception:
                logger.debug("Failed to save vendor state to Redis", exc_info=True)
