from __future__ import annotations

"""Collector 服务 HTTP 入口。

负责接收表单、网页和第三方 API 数据，执行增量去重、供应商失败转移、治理清洗
和冲突检测。真正的入库由 API/Admin 层决定，collector 只返回规范化结果和冲突信息。
"""

import hashlib
import logging
import os

from fastapi import FastAPI

logger = logging.getLogger(__name__)
from pydantic import BaseModel, Field
import redis

from app.collectors import collect_form_business_data, collect_mock_api, collect_public_page
from app.conflict_engine import ConflictConfig, ConflictResult, detect_conflicts
from app.governance import govern_records
from app.redis_state import RedisStateStore
from app.resilience import CollectionJob, fetch_with_vendor_failover, incremental_fingerprint

app = FastAPI(title="BizSage Collector", version="0.1.0")


def build_state_store() -> RedisStateStore:
    """构建 Redis 状态存储，用于指纹、快照、代理和供应商状态共享。"""
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
    """按增量指纹过滤重复记录，并把新指纹写入 Redis。"""
    deduped: list[dict] = []
    for record in records:
        fingerprint = incremental_fingerprint(record, state_store=state_store)
        if fingerprint.is_duplicate:
            continue
        if state_store is not None:
            try:
                state_store.save_fingerprint(fingerprint.fingerprint, ttl_seconds=86400)
            except Exception:
                logger.warning("Redis fingerprint save failed — deduplication may be degraded", exc_info=True)
        deduped.append(record)
    return deduped


def build_mock_api_snapshot_key(items: list[dict], snapshot_key: str | None = None) -> str | None:
    """为 mock API 批次生成稳定快照键；调用方显式传入时优先使用。"""
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
    """带供应商失败转移和最近快照兜底的 mock API 采集。"""
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
        # 快照代表已经去重过的最近成功结果，不再写入本次指纹。
        return records
    return dedupe_records(records, state_store)


@app.get("/health")
def health() -> dict:
    """健康检查端点。"""
    return {"status": "UP"}


@app.post("/collect/form")
def collect_form(request: FormBusinessDataRequest) -> dict:
    """采集用户私有表单数据；私有数据不走公共去重快照。"""
    return {"records": collect_form_business_data(request.model_dump())}


@app.post("/collect/public-page")
def collect_page(request: PublicPageRequest) -> dict:
    """采集公开页面，并使用 Redis 指纹做跨请求去重。"""
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
    """采集第三方 API mock 数据，并在供应商失败时使用最近快照。"""
    return {
        "records": collect_mock_api_with_resilience(
            request.items,
            build_state_store(),
            snapshot_key=request.snapshot_key,
        )
    }


@app.post("/govern")
def govern(request: GovernRequest) -> dict:
    """执行治理清洗；可选地与现有情报做冲突检测。"""
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
    """独立冲突检测接口，用于 Admin/API 层在入库前预判分流。"""
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
    """把冲突检测结果转换成跨服务传输友好的字典。"""
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
