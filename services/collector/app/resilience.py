from __future__ import annotations

"""采集链路韧性工具。

包含增量指纹、重试退避、死信分类、简单熔断和供应商失败转移。目标是让采集任务在
第三方源不稳定时尽量降级可用，同时把失败原因和兜底来源留在 telemetry 中。
"""

import hashlib
import time
from dataclasses import dataclass
from typing import Callable, Iterable

from app.governance import simhash


@dataclass(frozen=True)
class FingerprintResult:
    """采集记录的增量去重结果。"""
    fingerprint: str
    content_hash: str
    sim_hash: int
    is_duplicate: bool


@dataclass(frozen=True)
class DeadLetter:
    """重试耗尽后的失败摘要。"""
    reason: str
    error: str


@dataclass(frozen=True)
class RetryResult:
    """重试执行结果，成功时带 value，失败时带 dead_letter。"""
    success: bool
    attempts: int
    value: object | None = None
    dead_letter: DeadLetter | None = None


@dataclass(frozen=True)
class CollectionJob:
    """一次采集任务的最小遥测信息。"""
    id: str
    source: str
    queue_depth: int = 0


def incremental_fingerprint(
    record: dict,
    existing_fingerprints: set[str] | None = None,
    state_store=None,
) -> FingerprintResult:
    """基于 URL + 内容哈希生成增量指纹，并检查内存/Redis 中是否已出现。"""
    content = str(record.get("content", ""))
    url = str(record.get("url", ""))
    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    fingerprint = hashlib.sha256(f"{url}|{content_hash}".encode("utf-8")).hexdigest()
    known = existing_fingerprints or set()
    is_duplicate = fingerprint in known
    if state_store is not None:
        try:
            if state_store.has_fingerprint(fingerprint):
                is_duplicate = True
        except Exception:  # noqa: BLE001 - Redis degradation should not block collection.
            # Redis 只是增强去重能力，故障时允许采集继续，由后续治理再兜底。
            pass
    return FingerprintResult(
        fingerprint=fingerprint,
        content_hash=content_hash,
        sim_hash=simhash(content),
        is_duplicate=is_duplicate,
    )


def retry_with_backoff(operation: Callable[[], object], max_attempts: int = 3, base_delay_seconds: float = 0) -> RetryResult:
    """执行带指数退避的重试，失败后返回可分类的死信结果。"""
    last_error = ""
    for attempt in range(1, max_attempts + 1):
        try:
            return RetryResult(success=True, attempts=attempt, value=operation())
        except Exception as exception:  # noqa: BLE001 - classification captures the original message.
            last_error = str(exception)
            if base_delay_seconds > 0 and attempt < max_attempts:
                time.sleep(base_delay_seconds * (2 ** (attempt - 1)))
    return RetryResult(
        success=False,
        attempts=max_attempts,
        dead_letter=DeadLetter(reason="RETRY_EXHAUSTED", error=last_error),
    )


def classify_dead_letter(error: str) -> str:
    """把异常文本归类为后续监控/工单可识别的失败原因。"""
    lowered = error.lower()
    if "timeout" in lowered:
        return "TRANSIENT_TIMEOUT"
    if "permission" in lowered or "forbidden" in lowered:
        return "PERMISSION_DENIED"
    if "schema" in lowered or "parse" in lowered:
        return "BAD_PAYLOAD"
    return "UNKNOWN_FAILURE"


class CircuitBreaker:
    """按域名统计失败次数的轻量熔断器。"""
    def __init__(self, failure_threshold: int = 3) -> None:
        self.failure_threshold = failure_threshold
        self._failures: dict[str, int] = {}

    def record_failure(self, domain: str) -> None:
        """记录域名采集失败。"""
        self._failures[domain] = self._failures.get(domain, 0) + 1

    def allow(self, domain: str) -> bool:
        """判断当前域名是否仍允许继续请求。"""
        return self._failures.get(domain, 0) < self.failure_threshold

    def state(self, domain: str) -> str:
        """返回域名熔断状态。"""
        return "OPEN" if not self.allow(domain) else "CLOSED"


def fetch_with_vendor_failover(
    job: CollectionJob,
    vendors: Iterable[Callable[[], dict]],
    recent_snapshot: dict | None = None,
    state_store=None,
    snapshot_key: str | None = None,
) -> dict:
    """依次尝试供应商，全部失败时回退到 Redis 最近快照或传入快照。"""
    errors: list[str] = []
    for vendor in vendors:
        try:
            record = vendor()
            if state_store is not None and snapshot_key is not None:
                try:
                    state_store.save_recent_snapshot(snapshot_key, record, ttl_seconds=900)
                except Exception:  # noqa: BLE001 - Vendor success should survive cache write failure.
                    # 缓存写失败不应掩盖供应商本次成功结果。
                    pass
            return {
                "source": "vendor",
                "record": record,
                "telemetry": {"job_id": job.id, "queue_depth": job.queue_depth, "errors": errors},
            }
        except Exception as exception:  # noqa: BLE001 - failover records vendor errors.
            # 单个供应商失败只记录错误，继续尝试下一个供应商。
            errors.append(str(exception))
    cached = None
    if state_store is not None and snapshot_key is not None:
        try:
            cached = state_store.load_recent_snapshot(snapshot_key)
        except Exception:  # noqa: BLE001 - Cache read failure should degrade to empty fallback.
            # 快照读取失败时仍返回空兜底，避免采集接口直接 500。
            cached = None
    record = cached if cached is not None else recent_snapshot or {}
    return {
        "source": "recent-snapshot",
        "record": record,
        "telemetry": {"job_id": job.id, "queue_depth": job.queue_depth, "errors": errors},
    }
