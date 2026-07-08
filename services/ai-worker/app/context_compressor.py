"""Context compression for the RAG retrieval pipeline.

Compresses retrieved knowledge items to fit within the model's context window
while preserving the most relevant information.  The compressor:

1.  Merges near-duplicate results (high content overlap).
2.  Truncates very long items to a per-item token budget.
3.  Distributes the total token budget across items proportionally by score.
4.  Falls back gracefully when the budget is very tight.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field


# ---------------------------------------------------------------------------
# Data types
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class CompressedItem:
    """A single compressed knowledge item."""
    id: str
    title: str
    content: str
    source_url: str
    source_id: str
    score: float
    original_length: int       # chars before compression
    compressed_length: int     # chars after compression
    merged_from: list[str] = field(default_factory=list)  # ids of items merged in


@dataclass(frozen=True)
class CompressConfig:
    """Tunable parameters for the context compressor."""

    # Total token budget for the compressed context (prompt tokens).
    total_token_budget: int = 2400

    # Minimum characters guaranteed per item (ensures every result is represented).
    min_chars_per_item: int = 80

    # Maximum characters for any single item after truncation.
    max_chars_per_item: int = 1200

    # Jaccard similarity threshold above which two items are merged.
    merge_similarity_threshold: float = 0.70

    # Estimated characters per token (conservative for mixed CJK/ASCII text).
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
    """Compress a list of search-result dicts into a token-budgeted context string.

    Returns ``(context_text, compressed_items)``.
    """
    cfg = config or DEFAULT_CONFIG

    if not items:
        return "", []

    # ── Step 1: merge items with highly overlapping content ──
    merged = _merge_similar(items, cfg.merge_similarity_threshold)

    # ── Step 2: sort by score (highest first) ──
    merged.sort(key=lambda m: m.get("score", 0.0), reverse=True)

    # ── Step 3: distribute token budget proportionally ──
    total_score = sum(m.get("score", 0.0) for m in merged) or len(merged)
    budget_per_item = _distribute_budget(merged, total_score, cfg)

    # ── Step 4: truncate each item to its allocated budget ──
    compressed: list[CompressedItem] = []
    for item, budget in zip(merged, budget_per_item):
        compressed.append(_truncate_item(item, budget, cfg.min_chars_per_item, cfg.max_chars_per_item))

    # ── Step 5: build the context string ──
    lines: list[str] = []
    for ci in compressed:
        merged_note = ""
        if ci.merged_from:
            merged_note = f" [合并自: {', '.join(ci.merged_from[:3])}]"
        lines.append(f"【{ci.title}】(来源:{ci.source_id}, 相关度:{ci.score:.2f}){merged_note}\n{ci.content}")

    context = "\n\n".join(lines)
    return context, compressed


def estimate_tokens(text: str, chars_per_token: float = 3.2) -> int:
    """Conservative token count estimate for mixed CJK/ASCII text."""
    return max(1, int(len(text) / chars_per_token))


# ---------------------------------------------------------------------------
# Merge logic — Jaccard similarity on character trigrams
# ---------------------------------------------------------------------------

def _merge_similar(items: list[dict], threshold: float) -> list[dict]:
    """Greedily merge items whose content trigram-Jaccard exceeds *threshold*."""
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
                # Merge: keep the higher-scored item, append merged ids
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
    """Character trigrams after whitespace normalization."""
    cleaned = re.sub(r"\s+", "", text)
    if len(cleaned) < 3:
        return {cleaned}
    return {cleaned[i:i + 3] for i in range(len(cleaned) - 2)}


def _jaccard(left: set[str], right: set[str]) -> float:
    """Jaccard similarity coefficient."""
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
    """Distribute character budget across items by score, with guardrails."""
    total_chars = int(config.total_token_budget * config.chars_per_token)
    n = len(items)
    min_guaranteed = config.min_chars_per_item * n

    if total_chars <= min_guaranteed:
        # Budget too tight — give every item the minimum
        return [config.min_chars_per_item] * n

    # Reserve minimum for each item, distribute the rest by score
    remaining = total_chars - min_guaranteed
    budgets: list[int] = []
    for item in items:
        share = int(remaining * (item.get("score", 0.0) / total_score))
        budgets.append(config.min_chars_per_item + share)

    # Clamp to max_chars_per_item and redistribute overflow
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
    """Truncate a single item's content to fit within *char_budget*."""
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
    """Truncate *text* to at most *budget* characters at a sentence boundary."""
    if len(text) <= budget:
        return text

    # Try to find the last sentence break within budget
    truncated = text[:budget]
    match = None
    for m in _SENTENCE_END.finditer(truncated):
        match = m

    if match and match.end() > budget * 0.5:
        # Found a good boundary in the latter half of the budget window
        return truncated[:match.end()] + "…"

    # Fall back to hard cut at budget with ellipsis
    return truncated[:budget - 1] + "…"
