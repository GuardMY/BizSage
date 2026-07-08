from __future__ import annotations

import math
import re
from dataclasses import dataclass

from app.embeddings import embed_text


@dataclass(frozen=True)
class KnowledgeItem:
    id: str
    title: str
    content: str
    source_url: str
    source_id: str
    weight: float
    confidence: float
    industry_id: str
    region_id: str
    entitlement: str = "FREE"
    review_confidence: float = 0.85
    historical_quality: float = 0.85
    link_id: str = ""


@dataclass(frozen=True)
class SearchResult:
    id: str
    title: str
    content: str
    source_url: str
    source_id: str
    weight: float
    confidence: float
    score: float
    entitlement: str = "FREE"


def search_knowledge(
    query: str,
    knowledge: list[KnowledgeItem],
    limit: int = 5,
    region_id: str | None = None,
    industry_id: str | None = None,
    membership_level: str = "FREE",
    vector_store: object | None = None,
    restrict_to_knowledge_ids: bool = False,
) -> list[SearchResult]:
    if vector_store is not None:
        return _search_with_vector_store(
            query,
            knowledge,
            limit=limit,
            region_id=region_id,
            industry_id=industry_id,
            membership_level=membership_level,
            vector_store=vector_store,
            restrict_to_knowledge_ids=restrict_to_knowledge_ids,
        )

    return _search_locally(
        query,
        knowledge,
        limit=limit,
        region_id=region_id,
        industry_id=industry_id,
        membership_level=membership_level,
    )


def _search_locally(
    query: str,
    knowledge: list[KnowledgeItem],
    *,
    limit: int,
    region_id: str | None,
    industry_id: str | None,
    membership_level: str,
) -> list[SearchResult]:
    query_tokens = tokenize(query)
    results: list[SearchResult] = []
    for item in knowledge:
        if not _matches_business_filters(
            item,
            region_id=region_id,
            industry_id=industry_id,
            membership_level=membership_level,
        ):
            continue
        text = f"{item.title} {item.content}"
        keyword_score = keyword_overlap(query_tokens, tokenize(text))
        vector_score = cosine(bag(query_tokens), bag(tokenize(text)))
        quality = _quality_score(item)
        score = (keyword_score * 0.65 + vector_score * 0.35) * quality
        if score > 0:
            results.append(_to_search_result(item, score))
    return sorted(results, key=lambda result: result.score, reverse=True)[:limit]


def tokenize(text: str) -> list[str]:
    compact = re.sub(r"[，。！？、；：,.!?\s]", "", text.lower())
    words = re.findall(r"[a-z0-9]+", compact)
    chars = [compact[index : index + 2] for index in range(max(len(compact) - 1, 0))]
    return words + chars


def keyword_overlap(query_tokens: list[str], doc_tokens: list[str]) -> float:
    if not query_tokens or not doc_tokens:
        return 0.0
    query_set = set(query_tokens)
    doc_set = set(doc_tokens)
    return len(query_set & doc_set) / len(query_set)


def bag(tokens: list[str]) -> dict[str, int]:
    result: dict[str, int] = {}
    for token in tokens:
        result[token] = result.get(token, 0) + 1
    return result


def cosine(left: dict[str, int], right: dict[str, int]) -> float:
    if not left or not right:
        return 0.0
    dot = sum(value * right.get(key, 0) for key, value in left.items())
    left_norm = math.sqrt(sum(value * value for value in left.values()))
    right_norm = math.sqrt(sum(value * value for value in right.values()))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return dot / (left_norm * right_norm)


def _search_with_vector_store(
    query: str,
    knowledge: list[KnowledgeItem],
    *,
    limit: int,
    region_id: str | None,
    industry_id: str | None,
    membership_level: str,
    vector_store: object,
    restrict_to_knowledge_ids: bool,
) -> list[SearchResult]:
    query_tokens = tokenize(query)
    dimensions = int(getattr(vector_store, "dimensions", 32))
    query_vector = embed_text(query, dimensions=dimensions)
    raw_candidates = vector_store.search(query_vector, limit=max(limit * 3, 10))
    knowledge_by_id = {item.id: item for item in knowledge}

    results: list[SearchResult] = []
    for candidate in raw_candidates:
        item = _candidate_to_knowledge_item(
            candidate.get("payload", {}),
            knowledge_by_id,
            restrict_to_knowledge_ids=restrict_to_knowledge_ids,
        )
        if item is None:
            continue
        if not _matches_business_filters(
            item,
            region_id=region_id,
            industry_id=industry_id,
            membership_level=membership_level,
        ):
            continue

        text = f"{item.title} {item.content}"
        keyword_score = keyword_overlap(query_tokens, tokenize(text))
        lexical_vector_score = cosine(bag(query_tokens), bag(tokenize(text)))
        semantic_score = max(float(candidate.get("score", 0.0)), 0.0)
        score = (semantic_score * 0.7 + keyword_score * 0.15 + lexical_vector_score * 0.15) * _quality_score(item)
        if score > 0:
            results.append(_to_search_result(item, score))

    return sorted(results, key=lambda result: result.score, reverse=True)[:limit]


def _candidate_to_knowledge_item(
    payload: dict,
    knowledge_by_id: dict[str, KnowledgeItem],
    *,
    restrict_to_knowledge_ids: bool,
) -> KnowledgeItem | None:
    item_id = str(payload.get("id") or "")
    if item_id and item_id in knowledge_by_id:
        return knowledge_by_id[item_id]
    if restrict_to_knowledge_ids:
        return None
    if not payload or "title" not in payload or "content" not in payload:
        return None
    return KnowledgeItem(
        id=item_id or str(payload.get("source_id") or payload.get("sourceId") or "unknown"),
        title=str(payload["title"]),
        content=str(payload["content"]),
        source_url=str(payload.get("source_url") or payload.get("sourceUrl") or ""),
        source_id=str(payload.get("source_id") or payload.get("sourceId") or "unknown"),
        weight=float(payload.get("weight", 0.85)),
        confidence=float(payload.get("confidence", 0.85)),
        industry_id=str(payload.get("industry_id") or payload.get("industryId") or "general"),
        region_id=str(payload.get("region_id") or payload.get("regionId") or "cn-default"),
        entitlement=str(payload.get("entitlement", "FREE")),
        review_confidence=float(payload.get("review_confidence", payload.get("reviewConfidence", 0.85))),
        historical_quality=float(payload.get("historical_quality", payload.get("historicalQuality", 0.85))),
    )


def _matches_business_filters(
    item: KnowledgeItem,
    *,
    region_id: str | None,
    industry_id: str | None,
    membership_level: str,
) -> bool:
    if region_id and item.region_id != region_id:
        return False
    if industry_id and item.industry_id != industry_id:
        return False
    if item.entitlement == "PAID" and membership_level not in {"SEED_PAID", "INTERNAL"}:
        return False
    return True


def _quality_score(item: KnowledgeItem) -> float:
    return (
        item.weight * 0.35
        + item.confidence * 0.2
        + item.review_confidence * 0.25
        + item.historical_quality * 0.2
    )


def _to_search_result(item: KnowledgeItem, score: float) -> SearchResult:
    return SearchResult(
        id=item.id,
        title=item.title,
        content=item.content,
        source_url=item.source_url,
        source_id=item.source_id,
        weight=item.weight,
        confidence=item.confidence,
        score=round(score, 6),
        entitlement=item.entitlement,
    )
