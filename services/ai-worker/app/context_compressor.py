"""RAG 检索结果的上下文压缩器。

把命中的知识压到模型上下文预算内，同时尽量保留高相关、高质量证据。压缩流程：

1. 合并高度重复的结果。
2. 按相关度分配每条证据的字符预算。
3. 对超长证据按句子边界截断。
4. 预算极紧时保证每条证据至少有最小展示长度。
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field


# ---------------------------------------------------------------------------
# Data types
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class CompressedItem:
    """单条压缩后的知识项。"""
    id: str
    title: str
    content: str
    source_url: str
    source_id: str
    score: float
    original_length: int       # 压缩前字符数。
    compressed_length: int     # 压缩后字符数。
    merged_from: list[str] = field(default_factory=list)  # 被合并进来的知识 ID。


@dataclass(frozen=True)
class CompressConfig:
    """上下文压缩的可调参数。"""

    # 压缩后上下文可占用的总 token 预算。
    total_token_budget: int = 2400

    # 每条证据的最小字符数，防止低分证据完全消失。
    min_chars_per_item: int = 80

    # 单条证据截断后的最大字符数。
    max_chars_per_item: int = 1200

    # 三元字符 Jaccard 相似度超过该阈值时合并。
    merge_similarity_threshold: float = 0.70

    # 中英混合文本的保守字符/token 估算。
    chars_per_token: float = 3.2


DEFAULT_CONFIG = CompressConfig()


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def compress_context(
    items: list[dict],
    *,
    config: CompressConfig | None = None,
) -> tuple[str, list[CompressedItem]]:
    """把检索结果压缩成有 token 预算约束的上下文文本。

    返回值为 ``(context_text, compressed_items)``。
    """
    cfg = config or DEFAULT_CONFIG

    if not items:
        return "", []

    # 第一步：合并高度重复的证据，减少同义内容占用上下文。
    merged = _merge_similar(items, cfg.merge_similarity_threshold)

    # 第二步：按相关度排序，优先保护高分证据。
    merged.sort(key=lambda m: m.get("score", 0.0), reverse=True)

    # 第三步：按分数比例分配字符预算。
    total_score = sum(m.get("score", 0.0) for m in merged) or len(merged)
    budget_per_item = _distribute_budget(merged, total_score, cfg)

    # 第四步：把每条证据截断到预算范围内。
    compressed: list[CompressedItem] = []
    for item, budget in zip(merged, budget_per_item):
        compressed.append(_truncate_item(item, budget, cfg.min_chars_per_item, cfg.max_chars_per_item))

    # 第五步：拼成最终提示词上下文，保留来源和相关度。
    lines: list[str] = []
    for ci in compressed:
        merged_note = ""
        if ci.merged_from:
            merged_note = f" [合并自: {', '.join(ci.merged_from[:3])}]"
        lines.append(f"【{ci.title}】(来源:{ci.source_id}, 相关度:{ci.score:.2f}){merged_note}\n{ci.content}")

    context = "\n\n".join(lines)
    return context, compressed


def estimate_tokens(text: str, chars_per_token: float = 3.2) -> int:
    """中英混合文本的保守 token 数估算。"""
    return max(1, int(len(text) / chars_per_token))


# ---------------------------------------------------------------------------
# Merge logic — Jaccard similarity on character trigrams
# ---------------------------------------------------------------------------

def _merge_similar(items: list[dict], threshold: float) -> list[dict]:
    """贪心合并三元字符 Jaccard 相似度超过阈值的证据。"""
    if len(items) <= 1:
        return [dict(it) for it in items]

    remaining = [dict(it) for it in items]
    merged: list[dict] = []

    while remaining:
        current = remaining.pop(0)
        current_trigrams = _trigrams(current.get("content", ""))
        merged_ids = current.pop("_merged_ids", [])

        i = 0
        while i < len(remaining):
            other = remaining[i]
            other_trigrams = _trigrams(other.get("content", ""))
            if _jaccard(current_trigrams, other_trigrams) >= threshold:
                # 合并时保留分数更高的证据作为主记录，其余记录只留下 ID 追踪。
                if other.get("score", 0) > current.get("score", 0):
                    # Swap — other becomes primary
                    current, other = other, current
                    current_trigrams = _trigrams(current.get("content", ""))
                merged_ids.append(other.get("id", "unknown"))
                merged_ids.extend(other.pop("_merged_ids", []))
                remaining.pop(i)
            else:
                i += 1

        if merged_ids:
            current["_merged_ids"] = merged_ids
        merged.append(current)

    return merged


def _trigrams(text: str) -> set[str]:
    """去除空白后的字符三元组。"""
    cleaned = re.sub(r"\s+", "", text)
    if len(cleaned) < 3:
        return {cleaned}
    return {cleaned[i:i + 3] for i in range(len(cleaned) - 2)}


def _jaccard(left: set[str], right: set[str]) -> float:
    """Jaccard 相似系数。"""
    if not left and not right:
        return 1.0
    if not left or not right:
        return 0.0
    return len(left & right) / len(left | right)


# ---------------------------------------------------------------------------
# Budget distribution
# ---------------------------------------------------------------------------

def _distribute_budget(
    items: list[dict],
    total_score: float,
    config: CompressConfig,
) -> list[int]:
    """按分数分配字符预算，并应用最小/最大保护线。"""
    total_chars = int(config.total_token_budget * config.chars_per_token)
    n = len(items)
    min_guaranteed = config.min_chars_per_item * n

    if total_chars <= min_guaranteed:
        # 预算过紧时，每条证据都只给最小展示长度。
        return [config.min_chars_per_item] * n

    # 先预留最小长度，再把剩余预算按分数比例分配。
    remaining = total_chars - min_guaranteed
    budgets: list[int] = []
    for item in items:
        share = int(remaining * (item.get("score", 0.0) / total_score))
        budgets.append(config.min_chars_per_item + share)

    # 超过单条上限的预算会回收，再分给尚未达到上限的证据。
    overflow = 0
    for i in range(n):
        if budgets[i] > config.max_chars_per_item:
            overflow += budgets[i] - config.max_chars_per_item
            budgets[i] = config.max_chars_per_item

    # Distribute overflow to non-maxed items
    while overflow > 0:
        distributed = False
        for i in range(n):
            if budgets[i] < config.max_chars_per_item and overflow > 0:
                budgets[i] += 1
                overflow -= 1
                distributed = True
        if not distributed:
            break

    return budgets


# ---------------------------------------------------------------------------
# Truncation
# ---------------------------------------------------------------------------

def _truncate_item(
    item: dict,
    char_budget: int,
    min_chars: int,
    max_chars: int,
) -> CompressedItem:
    """把单条证据截断到指定字符预算内。"""
    content = str(item.get("content", ""))
    title = str(item.get("title", ""))
    score = float(item.get("score", 0.0))
    original_len = len(content)

    budget = max(min_chars, min(char_budget, max_chars))

    if len(content) <= budget:
        return CompressedItem(
            id=str(item.get("id", "")),
            title=title,
            content=content,
            source_url=str(item.get("source_url", "")),
            source_id=str(item.get("source_id", "")),
            score=score,
            original_length=original_len,
            compressed_length=len(content),
            merged_from=list(item.get("_merged_ids", [])),
        )

    # Truncate at sentence boundary when possible
    truncated = _truncate_at_boundary(content, budget)
    return CompressedItem(
        id=str(item.get("id", "")),
        title=title,
        content=truncated,
        source_url=str(item.get("source_url", "")),
        source_id=str(item.get("source_id", "")),
        score=score,
        original_length=original_len,
        compressed_length=len(truncated),
        merged_from=list(item.get("_merged_ids", [])),
    )


_SENTENCE_END = re.compile(r"[。！？.!?\n]")

def _truncate_at_boundary(text: str, budget: int) -> str:
    """优先在句子边界截断文本，找不到合适边界时硬截断。"""
    if len(text) <= budget:
        return text

    # 在预算窗口内寻找最后一个句末符号，避免截断在半句话中间。
    truncated = text[:budget]
    match = None
    for m in _SENTENCE_END.finditer(truncated):
        match = m

    if match and match.end() > budget * 0.5:
        # 只有边界落在后半段时才采用，避免丢掉太多有效信息。
        return truncated[:match.end()] + "…"

    # 兜底硬截断，并追加省略号提示内容被压缩。
    return truncated[:budget - 1] + "…"
