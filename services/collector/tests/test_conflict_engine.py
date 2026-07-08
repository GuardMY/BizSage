from app.conflict_engine import (
    DEFAULT_CONFLICT_CONFIG,
    BRANCH_TO_ROUTING,
    ConflictBranch,
    ConflictConfig,
    ConflictResult,
    RoutingAction,
    detect_conflict,
    detect_conflicts,
)


def test_no_conflict_when_no_existing_records():
    incoming = {
        "title": "New policy announcement",
        "content": "Government releases new tax regulation for manufacturing.",
        "source_id": "public-page",
        "region_id": "cn-default",
        "industry_id": "general",
    }
    result = detect_conflict(incoming, [])
    assert result.has_conflict is False
    assert "No existing records" in result.notes


def test_no_conflict_when_simhash_distance_too_high():
    incoming = {
        "title": "Supply chain disruption in Asia",
        "content": "Major shipping routes are experiencing delays due to port congestion across multiple Southeast Asian hubs.",
        "source_id": "public-page",
        "region_id": "cn-default",
        "industry_id": "general",
        "weight": 0.6,
    }
    existing = [
        {
            "id": 1,
            "title": "Local tax policy",
            "content": "City government announces property tax adjustment for commercial buildings.",
            "source_id": "manual-local",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.85,
        }
    ]
    result = detect_conflict(incoming, existing)
    assert result.has_conflict is False
    assert "sufficiently similar" in result.notes.lower() or "No existing" in result.notes


def test_false_information_on_rumor_keyword():
    incoming = {
        "title": "网传重大变化",
        "content": "小道消息称行业即将全面洗牌。",
        "source_id": "public-page",
        "region_id": "cn-default",
        "industry_id": "general",
    }
    existing = [
        {
            "id": 1,
            "title": "Stable market conditions",
            "content": "Market conditions remain stable across the region.",
            "source_id": "public-page",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.6,
        }
    ]
    result = detect_conflict(incoming, existing)
    assert result.has_conflict is True
    assert result.conflict_branch == ConflictBranch.FALSE_INFORMATION
    assert result.routing_action == RoutingAction.FALSE_LEDGER
    assert "Rumor" in result.notes


def test_false_information_on_blocked_source():
    incoming = {
        "title": "Important industry update",
        "content": "Supply chain costs are rising across all regions.",
        "source_id": "blocked-spam-source",
        "region_id": "cn-default",
        "industry_id": "general",
    }
    existing = [
        {
            "id": 1,
            "title": "Industry baseline",
            "content": "Supply chain costs are stable.",
            "source_id": "public-page",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.6,
        }
    ]
    result = detect_conflict(
        incoming, existing, blocked_source_ids={"blocked-spam-source"}
    )
    assert result.has_conflict is True
    assert result.conflict_branch == ConflictBranch.FALSE_INFORMATION
    assert "blocked" in result.notes.lower()


def test_authoritative_update_on_higher_weight_source():
    incoming = {
        "title": "Authoritative policy change",
        "content": "Government official policy: tax rate reduced from thirteen percent to nine percent for manufacturing sector.",
        "source_id": "user-private",
        "region_id": "cn-default",
        "industry_id": "general",
        "weight": 1.0,
    }
    existing = [
        {
            "id": 1,
            "title": "Baseline tax policy",
            "content": "Government official policy: tax rate is thirteen percent for manufacturing sector currently.",
            "source_id": "public-page",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.6,
        }
    ]
    result = detect_conflict(incoming, existing)
    assert result.has_conflict is True
    assert result.conflict_branch == ConflictBranch.PERMANENT_AUTHORITATIVE_UPDATE
    assert result.routing_action == RoutingAction.KNOWLEDGE_UPDATE
    assert result.incoming_weight > result.existing_weight


def test_regional_exception_on_cross_region_similar_content():
    incoming = {
        "title": "Shanghai new local rules",
        "content": "Local government imposes stricter environmental regulations on factories.",
        "source_id": "manual-local",
        "region_id": "cn-shanghai",
        "industry_id": "general",
        "weight": 0.85,
    }
    existing = [
        {
            "id": 1,
            "title": "National environmental baseline",
            "content": "Local government imposes stricter environmental regulations on factories.",
            "source_id": "public-page",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.85,
        }
    ]
    result = detect_conflict(incoming, existing)
    assert result.has_conflict is True
    assert result.conflict_branch == ConflictBranch.REGIONAL_EXCEPTION
    assert result.routing_action == RoutingAction.TAG_ONLY
    assert "Region mismatch" in result.notes


def test_short_term_fluctuation_on_low_weight_similar():
    incoming = {
        "title": "Temporary price dip",
        "content": "Market prices dropped slightly this week due to seasonal overstock inventory buildup.",
        "source_id": "public-page",
        "region_id": "cn-default",
        "industry_id": "general",
        "weight": 0.35,
    }
    existing = [
        {
            "id": 1,
            "title": "Stable price history",
            "content": "Market prices dropped slightly this week due to seasonal overstock inventory buildup and liquidation.",
            "source_id": "manual-local",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.85,
        }
    ]
    result = detect_conflict(incoming, existing)
    assert result.has_conflict is True
    assert result.conflict_branch == ConflictBranch.SHORT_TERM_FLUCTUATION
    assert result.routing_action == RoutingAction.TAG_ONLY
    assert "Low-weight" in result.notes


def test_suspicious_conflict_on_unresolved_case():
    incoming = {
        "title": "Supplier payment cycle change",
        "content": "Multiple suppliers now require shorter payment cycles across the industry sector.",
        "source_id": "public-page",
        "region_id": "cn-default",
        "industry_id": "general",
        "weight": 0.6,
    }
    existing = [
        {
            "id": 1,
            "title": "Standard payment baseline",
            "content": "Multiple suppliers have recently revised their payment cycle requirements for the industry sector.",
            "source_id": "public-page",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.6,
        }
    ]
    result = detect_conflict(incoming, existing)
    assert result.has_conflict is True
    assert result.conflict_branch == ConflictBranch.SUSPICIOUS_CONFLICT
    assert result.routing_action == RoutingAction.REVIEW_TICKET


def test_batch_detection_returns_results_for_all_records():
    incoming = [
        {
            "title": "Rumor article",
            "content": "网传所有供应商将涨价。",
            "source_id": "public-page",
            "region_id": "cn-default",
            "industry_id": "general",
        },
        {
            "title": "Unique new intelligence",
            "content": "Completely novel market intelligence about a new tech sector.",
            "source_id": "manual-local",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.85,
        },
    ]
    existing: list[dict] = []
    results = detect_conflicts(incoming, existing)
    assert len(results) == 2
    assert results[0].conflict_branch == ConflictBranch.FALSE_INFORMATION
    assert results[1].has_conflict is False


def test_custom_config_overrides_defaults():
    config = ConflictConfig(sim_hash_threshold=20, min_weight_for_authoritative=0.99)
    incoming = {
        "title": "Minor update",
        "content": "Slight adjustment to quality control standards.",
        "source_id": "public-page",
        "region_id": "cn-default",
        "industry_id": "general",
        "weight": 0.6,
    }
    existing = [
        {
            "id": 1,
            "title": "QC baseline",
            "content": "Established quality control standards for manufacturing.",
            "source_id": "public-page",
            "region_id": "cn-default",
            "industry_id": "general",
            "weight": 0.6,
        }
    ]
    result = detect_conflict(incoming, existing, config=config)
    # With sim_hash_threshold=20, different content may still be within threshold.
    # With min_weight_for_authoritative=0.99, incoming weight 0.6 cannot trigger authoritative update.
    if result.has_conflict:
        assert result.conflict_branch != ConflictBranch.PERMANENT_AUTHORITATIVE_UPDATE


def test_branch_to_routing_mapping_is_complete():
    for branch in ConflictBranch:
        assert branch in BRANCH_TO_ROUTING, f"Missing routing for {branch}"


def test_empty_content_does_not_crash():
    incoming = {"title": "", "content": "", "source_id": "unknown"}
    existing = [{"id": 1, "title": "", "content": "", "source_id": "unknown"}]
    result = detect_conflict(incoming, existing)
    assert result.has_conflict is False


def test_default_config_values():
    cfg = DEFAULT_CONFLICT_CONFIG
    assert cfg.sim_hash_threshold == 18
    assert cfg.authoritative_weight_threshold == 0.80
    assert cfg.min_weight_for_authoritative == 0.75
