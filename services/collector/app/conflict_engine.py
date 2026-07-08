from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

from app.governance import contains_rumor, simhash, hamming_distance, FIXED_WEIGHTS


class ConflictBranch(Enum):
    """The five classification branches of the conflict engine."""
    SHORT_TERM_FLUCTUATION = "SHORT_TERM_FLUCTUATION"
    REGIONAL_EXCEPTION = "REGIONAL_EXCEPTION"
    PERMANENT_AUTHORITATIVE_UPDATE = "PERMANENT_AUTHORITATIVE_UPDATE"
    SUSPICIOUS_CONFLICT = "SUSPICIOUS_CONFLICT"
    FALSE_INFORMATION = "FALSE_INFORMATION"


class RoutingAction(Enum):
    """What action the system should take for each conflict branch."""
    TAG_ONLY = "TAG_ONLY"
    REVIEW_TICKET = "REVIEW_TICKET"
    ALERT = "ALERT"
    KNOWLEDGE_UPDATE = "KNOWLEDGE_UPDATE"
    FALSE_LEDGER = "FALSE_LEDGER"


@dataclass(frozen=True)
class ConflictResult:
    """Result of a single conflict classification."""
    has_conflict: bool
    conflict_branch: Optional[ConflictBranch] = None
    routing_action: Optional[RoutingAction] = None
    sim_hash_distance: int = 0
    incoming_weight: float = 0.0
    existing_weight: float = 0.0
    matched_existing_id: Optional[str] = None
    notes: str = ""


@dataclass(frozen=True)
class ConflictConfig:
    """Configurable thresholds for conflict classification."""
    sim_hash_threshold: int = 18
    authoritative_weight_threshold: float = 0.80
    min_weight_for_authoritative: float = 0.75
    rumor_simhash_distance: int = 12
    low_weight_fluctuation_threshold: float = 0.50


DEFAULT_CONFLICT_CONFIG = ConflictConfig()

# ---------------------------------------------------------------------------
# Routing table: maps each ConflictBranch to its default RoutingAction
# ---------------------------------------------------------------------------
BRANCH_TO_ROUTING: dict[ConflictBranch, RoutingAction] = {
    ConflictBranch.SHORT_TERM_FLUCTUATION: RoutingAction.TAG_ONLY,
    ConflictBranch.REGIONAL_EXCEPTION: RoutingAction.TAG_ONLY,
    ConflictBranch.PERMANENT_AUTHORITATIVE_UPDATE: RoutingAction.KNOWLEDGE_UPDATE,
    ConflictBranch.SUSPICIOUS_CONFLICT: RoutingAction.REVIEW_TICKET,
    ConflictBranch.FALSE_INFORMATION: RoutingAction.FALSE_LEDGER,
}


def detect_conflict(
    incoming_record: dict,
    existing_items: list[dict],
    *,
    config: ConflictConfig | None = None,
    blocked_source_ids: set[str] | None = None,
) -> ConflictResult:
    """Classify whether *incoming_record* conflicts with *existing_items*.

    Classification priority (first match wins):
    1. Rumor keyword in content or source in blocklist → FALSE_INFORMATION
    2. No existing match found → no conflict (return has_conflict=False)
    3. Incoming has higher authoritative weight → AUTHORITATIVE_UPDATE
    4. Same industry, different region → REGIONAL_EXCEPTION
    5. Close SimHash distance with low incoming weight → FLUCTUATION
    6. Otherwise → SUSPICIOUS_CONFLICT
    """
    cfg = config or DEFAULT_CONFLICT_CONFIG
    blocked = blocked_source_ids or set()

    incoming_content = str(incoming_record.get("content", ""))
    incoming_title = str(incoming_record.get("title", ""))
    incoming_source_id = str(incoming_record.get("source_id", "unknown"))
    incoming_weight = _resolve_weight(incoming_record)

    # ── Rule 1: Rumor or blocked source → FALSE_INFORMATION ──
    if incoming_source_id in blocked:
        return ConflictResult(
            has_conflict=True,
            conflict_branch=ConflictBranch.FALSE_INFORMATION,
            routing_action=RoutingAction.FALSE_LEDGER,
            notes=f"Source '{incoming_source_id}' is in the blocked-source list.",
        )

    if contains_rumor(incoming_title) or contains_rumor(incoming_content):
        return ConflictResult(
            has_conflict=True,
            conflict_branch=ConflictBranch.FALSE_INFORMATION,
            routing_action=RoutingAction.FALSE_LEDGER,
            notes="Rumor keyword detected in title or content.",
        )

    if not existing_items:
        return ConflictResult(has_conflict=False, notes="No existing records to compare against.")

    incoming_simhash = simhash(incoming_content)
    incoming_region = str(incoming_record.get("region_id", ""))
    incoming_industry = str(incoming_record.get("industry_id", ""))

    # ── Find the best-matching existing record ──
    best_match: dict | None = None
    best_distance: int = 65  # 64-bit SimHash max distance is 64

    for existing in existing_items:
        existing_content = str(existing.get("content", ""))
        if not existing_content:
            continue
        existing_simhash = existing.get("sim_hash")
        if existing_simhash is None:
            existing_simhash = simhash(existing_content)

        distance = hamming_distance(incoming_simhash, int(existing_simhash))
        if distance < best_distance:
            best_distance = distance
            best_match = existing

    # ── Rule 2: No close match → NO CONFLICT ──
    if best_match is None or best_distance > cfg.sim_hash_threshold:
        return ConflictResult(
            has_conflict=False,
            notes="No existing record with sufficiently similar SimHash found.",
        )

    existing_weight = _resolve_weight(best_match)
    existing_region = str(best_match.get("region_id", ""))
    existing_id = str(best_match.get("id", ""))

    # ── Rule 3: Authoritative incoming source trumps low-weight existing ──
    if incoming_weight >= cfg.min_weight_for_authoritative and incoming_weight > existing_weight:
        return ConflictResult(
            has_conflict=True,
            conflict_branch=ConflictBranch.PERMANENT_AUTHORITATIVE_UPDATE,
            routing_action=RoutingAction.KNOWLEDGE_UPDATE,
            sim_hash_distance=best_distance,
            incoming_weight=incoming_weight,
            existing_weight=existing_weight,
            matched_existing_id=existing_id,
            notes=f"Incoming authoritative source (w={incoming_weight:.3f}) supersedes existing (w={existing_weight:.3f}).",
        )

    # ── Rule 4: Same industry, different region → REGIONAL_EXCEPTION ──
    if incoming_region and existing_region and incoming_region != existing_region:
        return ConflictResult(
            has_conflict=True,
            conflict_branch=ConflictBranch.REGIONAL_EXCEPTION,
            routing_action=RoutingAction.TAG_ONLY,
            sim_hash_distance=best_distance,
            incoming_weight=incoming_weight,
            existing_weight=existing_weight,
            matched_existing_id=existing_id,
            notes=f"Region mismatch: incoming={incoming_region}, existing={existing_region}.",
        )

    # ── Rule 5: Close distance + low incoming weight → FLUCTUATION ──
    if best_distance <= cfg.sim_hash_threshold and incoming_weight < cfg.low_weight_fluctuation_threshold:
        return ConflictResult(
            has_conflict=True,
            conflict_branch=ConflictBranch.SHORT_TERM_FLUCTUATION,
            routing_action=RoutingAction.TAG_ONLY,
            sim_hash_distance=best_distance,
            incoming_weight=incoming_weight,
            existing_weight=existing_weight,
            matched_existing_id=existing_id,
            notes=f"Low-weight (w={incoming_weight:.3f}) similar to existing (w={existing_weight:.3f}, dist={best_distance}).",
        )

    # ── Rule 6: Unresolved → SUSPICIOUS ──
    return ConflictResult(
        has_conflict=True,
        conflict_branch=ConflictBranch.SUSPICIOUS_CONFLICT,
        routing_action=RoutingAction.REVIEW_TICKET,
        sim_hash_distance=best_distance,
        incoming_weight=incoming_weight,
        existing_weight=existing_weight,
        matched_existing_id=existing_id,
        notes=f"Unresolved conflict: entering (w={incoming_weight:.3f}) vs existing (w={existing_weight:.3f}, dist={best_distance}).",
    )


def detect_conflicts(
    incoming_records: list[dict],
    existing_intelligence: list[dict],
    *,
    config: ConflictConfig | None = None,
    blocked_source_ids: set[str] | None = None,
) -> list[ConflictResult]:
    """Batch conflict detection for multiple incoming records."""
    results: list[ConflictResult] = []
    for record in incoming_records:
        results.append(
            detect_conflict(
                record,
                existing_intelligence,
                config=config,
                blocked_source_ids=blocked_source_ids,
            )
        )
    return results


def _resolve_weight(record: dict) -> float:
    """Resolve weight from a record dict, falling back to fixed-weight lookup."""
    weight = record.get("weight")
    if weight is not None:
        return float(weight)
    source_id = str(record.get("source_id", "unknown"))
    return float(FIXED_WEIGHTS.get(source_id, 0.1))
