from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass
from typing import Callable, Iterable

from app.governance import simhash


@dataclass(frozen=True)
class FingerprintResult:
    fingerprint: str
    content_hash: str
    sim_hash: int
    is_duplicate: bool


@dataclass(frozen=True)
class DeadLetter:
    reason: str
    error: str


@dataclass(frozen=True)
class RetryResult:
    success: bool
    attempts: int
    value: object | None = None
    dead_letter: DeadLetter | None = None


@dataclass(frozen=True)
class CollectionJob:
    id: str
    source: str
    queue_depth: int = 0


def incremental_fingerprint(
    record: dict,
    existing_fingerprints: set[str] | None = None,
    state_store=None,
) -> FingerprintResult:
    content = str(record.get("content", ""))
    url = str(record.get("url", ""))
    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    fingerprint = hashlib.sha256(f"{url}|{content_hash}".encode("utf-8")).hexdigest()
    known = existing_fingerprints or set()
    is_duplicate = fingerprint in known
    if state_store is not None and state_store.has_fingerprint(fingerprint):
        is_duplicate = True
    return FingerprintResult(
        fingerprint=fingerprint,
        content_hash=content_hash,
        sim_hash=simhash(content),
        is_duplicate=is_duplicate,
    )


def retry_with_backoff(operation: Callable[[], object], max_attempts: int = 3, base_delay_seconds: float = 0) -> RetryResult:
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
    lowered = error.lower()
    if "timeout" in lowered:
        return "TRANSIENT_TIMEOUT"
    if "permission" in lowered or "forbidden" in lowered:
        return "PERMISSION_DENIED"
    if "schema" in lowered or "parse" in lowered:
        return "BAD_PAYLOAD"
    return "UNKNOWN_FAILURE"


class CircuitBreaker:
    def __init__(self, failure_threshold: int = 3) -> None:
        self.failure_threshold = failure_threshold
        self._failures: dict[str, int] = {}

    def record_failure(self, domain: str) -> None:
        self._failures[domain] = self._failures.get(domain, 0) + 1

    def allow(self, domain: str) -> bool:
        return self._failures.get(domain, 0) < self.failure_threshold

    def state(self, domain: str) -> str:
        return "OPEN" if not self.allow(domain) else "CLOSED"


def fetch_with_vendor_failover(
    job: CollectionJob,
    vendors: Iterable[Callable[[], dict]],
    recent_snapshot: dict | None = None,
    state_store=None,
    snapshot_key: str | None = None,
) -> dict:
    errors: list[str] = []
    for vendor in vendors:
        try:
            record = vendor()
            if state_store is not None and snapshot_key is not None:
                state_store.save_recent_snapshot(snapshot_key, record, ttl_seconds=900)
            return {
                "source": "vendor",
                "record": record,
                "telemetry": {"job_id": job.id, "queue_depth": job.queue_depth, "errors": errors},
            }
        except Exception as exception:  # noqa: BLE001 - failover records vendor errors.
            errors.append(str(exception))
    cached = state_store.load_recent_snapshot(snapshot_key) if state_store and snapshot_key else None
    return {
        "source": "recent-snapshot",
        "record": cached or recent_snapshot or {},
        "telemetry": {"job_id": job.id, "queue_depth": job.queue_depth, "errors": errors},
    }
