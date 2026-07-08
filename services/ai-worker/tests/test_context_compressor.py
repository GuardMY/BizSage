"""Tests for the context compression module."""

import pytest
from app.context_compressor import (
    DEFAULT_CONFIG,
    CompressConfig,
    compress_context,
    estimate_tokens,
)
from app.context_compressor import _jaccard, _merge_similar, _trigrams


# ── Trigram / Jaccard utilities ──────────────────────────────────

def test_trigrams_empty_text():
    assert _trigrams("") == {""}
    assert _trigrams("ab") == {"ab"}


def test_trigrams_normal_text():
    tri = _trigrams("hello world")
    # whitespace is stripped: "helloworld" → 8 trigrams
    assert len(tri) == 8
    assert "hel" in tri
    assert "rld" in tri


def test_jaccard_identical():
    left = {"a", "b", "c"}
    right = {"a", "b", "c"}
    assert _jaccard(left, right) == 1.0


def test_jaccard_disjoint():
    assert _jaccard({"a"}, {"b"}) == 0.0


def test_jaccard_partial():
    left = {"a", "b", "c"}
    right = {"a", "b", "d", "e"}
    # intersection = {a,b} (2), union = {a,b,c,d,e} (5)
    assert _jaccard(left, right) == 0.4


# ── Merge logic ──────────────────────────────────────────────────

def test_merge_similar_combines_overlapping_content():
    items = [
        {"id": "a", "title": "T1", "content": "库存周转天数过高会压占现金流，并倒逼渠道低价清货。", "score": 0.85},
        {"id": "b", "title": "T2", "content": "库存周转天数过高会压占现金流，并倒逼渠道低价清货，影响利润。", "score": 0.80},
        {"id": "c", "title": "T3", "content": "完全不相关的内容关于新能源补贴政策。", "score": 0.60},
    ]
    merged = _merge_similar(items, threshold=0.60)
    # a and b should be merged (very similar), c stays separate
    assert len(merged) == 2
    merged_ids = {m["id"] for m in merged}
    assert "c" in merged_ids
    # Either a or b is kept as primary
    assert ("a" in merged_ids) or ("b" in merged_ids)


def test_merge_higher_score_becomes_primary():
    items = [
        {"id": "low", "title": "Lower", "content": "相同内容完全相同完全相同完全相同。", "score": 0.60},
        {"id": "high", "title": "Higher", "content": "相同内容完全相同完全相同完全相同。", "score": 0.95},
    ]
    merged = _merge_similar(items, threshold=0.70)
    assert len(merged) == 1
    assert merged[0]["id"] == "high"
    assert "low" in merged[0].get("_merged_ids", [])


# ── Token estimation ─────────────────────────────────────────────

def test_estimate_tokens_empty():
    assert estimate_tokens("") == 1


def test_estimate_tokens_chinese():
    # 32 characters / 3.2 = 10 tokens
    text = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十ab"
    assert estimate_tokens(text) == 10


def test_estimate_tokens_custom_ratio():
    text = "hello world " * 10  # 120 chars
    assert estimate_tokens(text, chars_per_token=4.0) == 30


# ── Full compression pipeline ────────────────────────────────────

def test_compress_empty_returns_empty():
    text, items = compress_context([])
    assert text == ""
    assert items == []


def test_compress_single_item_preserved():
    items = [
        {
            "id": "k1", "title": "餐饮现金流", "score": 0.9,
            "content": "餐饮门店应核对客单价和翻台率。",
            "source_url": "s1", "source_id": "seed",
        }
    ]
    text, compressed = compress_context(items)
    assert len(compressed) == 1
    assert compressed[0].id == "k1"
    assert compressed[0].original_length == len(items[0]["content"])
    assert "餐饮现金流" in text
    assert "客单价" in text


def test_compress_truncates_long_content():
    very_long = "这是一个非常长的文本。" * 200  # ~2400 chars
    items = [
        {
            "id": "long", "title": "Long Doc", "score": 0.9,
            "content": very_long,
            "source_url": "", "source_id": "test",
        }
    ]
    text, compressed = compress_context(items)
    assert len(compressed) == 1
    # Should be truncated to max_chars_per_item (1200) or less
    assert compressed[0].compressed_length < compressed[0].original_length
    assert compressed[0].compressed_length <= 1200
    # Should end with truncation marker
    assert text.rstrip().endswith("…")


def test_compress_merges_duplicates():
    base_content = "库存周转天数、滞销库存占比和上游账期会共同影响现金流风险。" * 3  # ~123 chars × 3
    items = [
        {
            "id": "a", "title": "库存风险 A", "score": 0.90,
            "content": base_content + "额外细节一。",
            "source_url": "", "source_id": "test",
        },
        {
            "id": "b", "title": "库存风险 B", "score": 0.85,
            "content": base_content + "额外细节二。",
            "source_url": "", "source_id": "test",
        },
        {
            "id": "c", "title": "无关内容", "score": 0.60,
            "content": "完全不同的新能源政策内容。",
            "source_url": "", "source_id": "test",
        },
    ]
    text, compressed = compress_context(items)
    # a and b should merge → 2 compressed items
    assert len(compressed) == 2
    # The merged item should have merged_from
    merged_item = next((ci for ci in compressed if ci.merged_from), None)
    assert merged_item is not None


def test_compress_distributes_budget_by_score():
    items = [
        {
            "id": "high", "title": "High Score", "score": 0.95,
            "content": "关键经营指标分析报告内容" * 50,  # ~500 chars
            "source_url": "", "source_id": "test",
        },
        {
            "id": "low", "title": "Low Score", "score": 0.30,
            "content": "次要补充背景信息内容" * 50,  # ~500 chars
            "source_url": "", "source_id": "test",
        },
    ]
    # Use a tight budget so truncation kicks in
    tight_config = CompressConfig(
        total_token_budget=200,  # ~640 chars total, ~320 per item after min deduction
        min_chars_per_item=80,
        max_chars_per_item=500,
    )
    text, compressed = compress_context(items, config=tight_config)
    assert len(compressed) == 2
    high_item = next(ci for ci in compressed if ci.id == "high")
    low_item = next(ci for ci in compressed if ci.id == "low")
    # Higher-scored item should get more character budget
    assert high_item.compressed_length > low_item.compressed_length


def test_compress_respects_min_chars():
    tight_config = CompressConfig(
        total_token_budget=50,  # ~160 chars for 3 items
        min_chars_per_item=40,
        max_chars_per_item=1200,
    )
    items = [
        {"id": "k0", "title": "T0", "content": "库存周转分析内容" * 10,
         "score": 0.5, "source_url": "", "source_id": ""},
        {"id": "k1", "title": "T1", "content": "现金流风险评估内容" * 10,
         "score": 0.5, "source_url": "", "source_id": ""},
        {"id": "k2", "title": "T2", "content": "渠道营销策略内容" * 10,
         "score": 0.5, "source_url": "", "source_id": ""},
    ]
    text, compressed = compress_context(items, config=tight_config)
    assert len(compressed) == 3
    for ci in compressed:
        # Each item gets at least min_chars
        assert ci.compressed_length >= 40


def test_compress_with_memory_reserve():
    """Simulates the memory-aware budget used in agent.py."""
    items = [
        {
            "id": "k1", "title": "Test", "score": 0.9,
            "content": "测试内容" * 50,
            "source_url": "s", "source_id": "seed",
        }
    ]
    cfg = CompressConfig(total_token_budget=2100)
    text, compressed = compress_context(items, config=cfg)
    assert len(compressed) == 1
    assert len(text) > 0


# ── Truncation boundary behavior ─────────────────────────────────

def test_truncation_at_sentence_boundary():
    content = "第一句话是关于库存周转的详细分析说明。第二句话是关于现金流风险的评估报告。第三句话是关于渠道营销的长期策略建议。第四句话是关于供应链优化的具体执行方案。" * 3
    items = [
        {
            "id": "k1", "title": "Test", "score": 0.9,
            "content": content,
            "source_url": "", "source_id": "",
        }
    ]
    cfg = CompressConfig(total_token_budget=400, max_chars_per_item=200)
    text, compressed = compress_context(items, config=cfg)
    assert len(compressed) == 1
    # Content should be truncated
    assert compressed[0].compressed_length < compressed[0].original_length
    # Should end at a sentence boundary (。or …)
    last_char = text.strip()[-1]
    assert last_char in {"。", "…"}
