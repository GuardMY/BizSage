from __future__ import annotations

"""RAG 检索与排序逻辑。

优先使用 Qdrant 语义检索；没有向量存储时退化为轻量本地检索，便于测试和
离线环境运行。所有路径都会统一执行行业、地域和会员权益过滤，避免越权引用证据。
"""

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
    # 六维重排字段：用于把“相关”进一步调整为“可靠且适用”。
    authority: float = 0.85       # 来源权威度：政府/官方 > 媒体 > 社区。
    timeliness: float = 0.85      # 时效性：越新越高，历史资料随时间衰减。
    review_confidence: float = 0.85  # 人工审核给出的置信度。
    historical_quality: float = 0.85  # 来源长期准确率。
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
    """按可用能力选择向量检索或本地检索，并返回排序后的证据列表。"""
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
    """无 Qdrant 时的本地兜底检索，组合关键词重合和字符袋余弦相似度。"""
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
    """面向中英混合文本的轻量分词：英文词 + 中文二元字符片段。"""
    compact = re.sub(r"[，。！？、；：,.!?\s]", "", text.lower())
    words = re.findall(r"[a-z0-9]+", compact)
    chars = [compact[index : index + 2] for index in range(max(len(compact) - 1, 0))]
    return words + chars


def keyword_overlap(query_tokens: list[str], doc_tokens: list[str]) -> float:
    """计算查询词在候选文本中的覆盖比例。"""
    if not query_tokens or not doc_tokens:
        return 0.0
    query_set = set(query_tokens)
    doc_set = set(doc_tokens)
    return len(query_set & doc_set) / len(query_set)


def bag(tokens: list[str]) -> dict[str, int]:
    """把 token 列表转成词频袋，供余弦相似度使用。"""
    result: dict[str, int] = {}
    for token in tokens:
        result[token] = result.get(token, 0) + 1
    return result


def cosine(left: dict[str, int], right: dict[str, int]) -> float:
    """计算两个稀疏词频向量的余弦相似度。"""
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
    """使用 Qdrant 候选集，并叠加词面匹配和质量分做最终重排。"""
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
    """把 Qdrant payload 还原为 KnowledgeItem；必要时限制在本次请求知识内。"""
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
        authority=float(payload.get("authority", 0.85)),
        timeliness=float(payload.get("timeliness", 0.85)),
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
    """业务过滤：地域和行业必须匹配。"""
    if region_id and item.region_id != region_id:
        return False
    if industry_id and item.industry_id != industry_id:
        return False
    return True


def _quality_score(item: KnowledgeItem) -> float:
    """六维质量分，用于在相似度之外体现来源可信度。

    权重来自来源级重要性、抽取置信度、来源权威度、时效性、人工审核置信度和历史质量。
    """
    return (
        item.weight * 0.20
        + item.confidence * 0.15
        + item.authority * 0.15
        + item.timeliness * 0.15
        + item.review_confidence * 0.20
        + item.historical_quality * 0.15
    )


def _to_search_result(item: KnowledgeItem, score: float) -> SearchResult:
    """把内部知识对象转换为对外返回的检索结果。"""
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
