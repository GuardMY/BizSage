from __future__ import annotations

import math
import re
from dataclasses import dataclass


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
) -> list[SearchResult]:
    query_tokens = tokenize(query)
    results: list[SearchResult] = []
    for item in knowledge:
        if region_id and item.region_id != region_id:
            continue
        if industry_id and item.industry_id != industry_id:
            continue
        if item.entitlement == "PAID" and membership_level not in {"SEED_PAID", "INTERNAL"}:
            continue
        text = f"{item.title} {item.content}"
        keyword_score = keyword_overlap(query_tokens, tokenize(text))
        vector_score = cosine(bag(query_tokens), bag(tokenize(text)))
        quality = (
            item.weight * 0.35
            + item.confidence * 0.2
            + item.review_confidence * 0.25
            + item.historical_quality * 0.2
        )
        score = (keyword_score * 0.65 + vector_score * 0.35) * quality
        if score > 0:
            results.append(
                SearchResult(
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
            )
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
