from __future__ import annotations

import hashlib
import os

from fastapi import FastAPI
from pydantic import BaseModel, Field
import redis

from app.collectors import collect_form_business_data, collect_mock_api, collect_public_page
from app.conflict_engine import ConflictConfig, ConflictResult, detect_conflicts
from app.governance import govern_records
from app.redis_state import RedisStateStore
from app.resilience import CollectionJob, fetch_with_vendor_failover, incremental_fingerprint

app = FastAPI(title="BizSage Collector", version="0.1.0")


def build_state_store() -> RedisStateStore:
    client = redis.Redis(
        host=os.getenv("REDIS_HOST", "localhost"),
        port=int(os.getenv("REDIS_PORT", "16379")),
        decode_responses=False,
    )
    return RedisStateStore(client)


class FormBusinessDataRequest(BaseModel):
    title: str
    content: str
    industry_id: str = "general"
    region_id: str = "cn-default"
    link_id: str = "business-data"


class PublicPageRequest(BaseModel):
    url: str
    html: str
    industry_id: str = "general"
    region_id: str = "cn-default"
    link_id: str = "public-page"


class MockApiRequest(BaseModel):
    items: list[dict] = Field(default_factory=list)
    snapshot_key: str | None = None


class GovernRequest(BaseModel):
    records: list[dict] = Field(default_factory=list)
    with_conflicts: bool = False
    existing_intelligence: list[dict] = Field(default_factory=list)
    blocked_source_ids: list[str] = Field(default_factory=list)
    conflict_config_override: dict | None = None


class GovernResponse(BaseModel):
    records: list[dict]
    conflicts: list[dict] = Field(default_factory=list)


class ConflictCheckRequest(BaseModel):
    incoming_records: list[dict] = Field(default_factory=list)
    existing_items: list[dict] = Field(default_factory=list)
    config_override: dict | None = None
    blocked_source_ids: list[str] = Field(default_factory=list)


class ConflictCheckResponse(BaseModel):
    conflicts: list[dict]


def dedupe_records(records: list[dict], state_store: RedisStateStore | None) -> list[dict]:
    deduped: list[dict] = []
    for record in records:
        fingerprint = incremental_fingerprint(record, state_store=state_store)
        if fingerprint.is_duplicate:
            continue
        if state_store is not None:
            try:
                state_store.save_fingerprint(fingerprint.fingerprint, ttl_seconds=86400)
            except Exception:  # noqa: BLE001 - Redis degradation should not block collection.
                pass
        deduped.append(record)
    return deduped


def build_mock_api_snapshot_key(items: list[dict], snapshot_key: str | None = None) -> str | None:
    if snapshot_key:
        return snapshot_key
    if not items:
        return None

    first = items[0]
    raw = "|".join(
        [
            str(first.get("link_id", "")),
            str(first.get("source_id", "")),
            str(first.get("region_id", "")),
            str(first.get("industry_id", "")),
            str(first.get("url", "")),
        ]
    )
    return f"mock-api:{hashlib.sha256(raw.encode('utf-8')).hexdigest()}"


def collect_mock_api_with_resilience(
    items: list[dict],
    state_store: RedisStateStore | None,
    snapshot_key: str | None = None,
) -> list[dict]:
    snapshot_key = build_mock_api_snapshot_key(items, snapshot_key)
    failover = fetch_with_vendor_failover(
        CollectionJob(id="collect-mock-api", source="mock-api", queue_depth=len(items)),
        vendors=[lambda: {"items": items}] if items else [],
        state_store=state_store,
        snapshot_key=snapshot_key,
    )
    record = failover["record"]
    if not record:
        return []
    records = collect_mock_api(record.get("items", []))
    if failover["source"] == "recent-snapshot":
        return records
    return dedupe_records(records, state_store)


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


@app.post("/collect/form")
def collect_form(request: FormBusinessDataRequest) -> dict:
    return {"records": collect_form_business_data(request.model_dump())}


@app.post("/collect/public-page")
def collect_page(request: PublicPageRequest) -> dict:
    state_store = build_state_store()
    return {
        "records": dedupe_records(
            collect_public_page(
                request.url,
                request.html,
                industry_id=request.industry_id,
                region_id=request.region_id,
                link_id=request.link_id,
            ),
            state_store,
        )
    }


@app.post("/collect/mock-api")
def collect_api(request: MockApiRequest) -> dict:
    return {
        "records": collect_mock_api_with_resilience(
            request.items,
            build_state_store(),
            snapshot_key=request.snapshot_key,
        )
    }


@app.post("/govern")
def govern(request: GovernRequest) -> dict:
    governed = govern_records(request.records)
    conflicts: list[dict] = []
    if request.with_conflicts and request.existing_intelligence:
        cfg = ConflictConfig(**(request.conflict_config_override or {}))
        blocked = set(request.blocked_source_ids)
        results = detect_conflicts(
            governed,
            request.existing_intelligence,
            config=cfg,
            blocked_source_ids=blocked,
        )
        conflicts = [_conflict_result_to_dict(r) for r in results if r.has_conflict]
    return {"records": governed, "conflicts": conflicts}


@app.post("/govern/conflict-check")
def conflict_check(request: ConflictCheckRequest) -> ConflictCheckResponse:
    cfg = ConflictConfig(**(request.config_override or {}))
    blocked = set(request.blocked_source_ids)
    results = detect_conflicts(
        request.incoming_records,
        request.existing_items,
        config=cfg,
        blocked_source_ids=blocked,
    )
    return ConflictCheckResponse(
        conflicts=[_conflict_result_to_dict(r) for r in results]
    )


def _conflict_result_to_dict(result: ConflictResult) -> dict:
    return {
        "has_conflict": result.has_conflict,
        "conflict_branch": result.conflict_branch.value if result.conflict_branch else None,
        "routing_action": result.routing_action.value if result.routing_action else None,
        "sim_hash_distance": result.sim_hash_distance,
        "incoming_weight": result.incoming_weight,
        "existing_weight": result.existing_weight,
        "matched_existing_id": result.matched_existing_id,
        "notes": result.notes,
    }
