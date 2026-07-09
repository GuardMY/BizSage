from __future__ import annotations

"""采集数据治理规则。

治理阶段负责把原始采集记录清洗成可入库情报：过滤传闻、URL 去重、近似文本去重、
固定来源权重、内容哈希和 SimHash 标记。它只做确定性规则，不创建人工审核工单。
"""

import hashlib
import re

RUMOR_KEYWORDS = ("网传", "未经证实", "小道消息", "据说", "rumor", "unverified", "allegedly")

# 固定来源权重体现来源可信度：用户私有数据最高，mock/低可信 API 最低。
FIXED_WEIGHTS = {
    "user-private": 1.0,
    "manual-local": 0.85,
    "public-page": 0.6,
    "mock-api": 0.35,
}

BLOCKED_SOURCE_IDS: set[str] = set()


def govern_records(records: list[dict]) -> list[dict]:
    """对采集记录执行清洗、去重、权重和哈希标记。"""
    seen_urls: set[str] = set()
    seen_simhashes: list[int] = []
    governed: list[dict] = []

    for record in records:
        normalized = normalize_fields(record)
        if contains_rumor(normalized["title"]) or contains_rumor(normalized["content"]):
            # 传闻类内容不进入后续冲突检测，直接在治理阶段丢弃。
            continue
        url = normalized.get("url")
        if url and url in seen_urls:
            continue
        current_simhash = simhash(normalized["content"])
        if any(hamming_distance(current_simhash, previous) <= 3 for previous in seen_simhashes):
            # SimHash 距离很近时视为本批次近重复，保留先出现的记录。
            continue
        normalized["weight"] = apply_fixed_weight(normalized.get("source_id", "unknown"))
        normalized["confidence"] = normalized.get("confidence", normalized["weight"])
        normalized["content_hash"] = content_hash(normalized["content"])
        normalized["sim_hash"] = current_simhash
        if url:
            seen_urls.add(url)
        seen_simhashes.append(current_simhash)
        governed.append(normalized)
    return governed


def normalize_fields(record: dict) -> dict:
    """补齐治理所需字段，并压缩标题/正文中的多余空白。"""
    normalized = dict(record)
    normalized["title"] = " ".join(str(normalized.get("title", "")).split())
    normalized["content"] = " ".join(str(normalized.get("content", "")).split())
    normalized["industry_id"] = normalized.get("industry_id") or "general"
    normalized["region_id"] = normalized.get("region_id") or "cn-default"
    normalized["link_id"] = normalized.get("link_id") or "unknown-link"
    normalized["source_id"] = normalized.get("source_id") or "unknown"
    return normalized


def contains_rumor(text: str) -> bool:
    """判断文本是否包含传闻或未经证实的关键词。"""
    return any(keyword in text for keyword in RUMOR_KEYWORDS)


def apply_fixed_weight(source_id: str) -> float:
    """根据 source_id 应用固定来源权重，未知来源降到最低可信档。"""
    return FIXED_WEIGHTS.get(source_id, 0.1)


def content_hash(text: str) -> str:
    """计算正文 SHA-256，用于精确重复识别和审计追踪。"""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def simhash(text: str) -> int:
    """计算 64 位 SimHash，用于近似重复和冲突候选匹配。"""
    tokens = tokenize(text)
    if not tokens:
        return 0
    vector = [0] * 64
    for token in tokens:
        value = int(hashlib.blake2b(token.encode("utf-8"), digest_size=8).hexdigest(), 16)
        for index in range(64):
            bit = 1 << index
            vector[index] += 1 if value & bit else -1
    result = 0
    for index, score in enumerate(vector):
        if score >= 0:
            result |= 1 << index
    return result


def tokenize(text: str) -> list[str]:
    """生成中文二字片段；移除“明显”等弱区分词，降低误判。"""
    compact = re.sub(r"[，。！？、；：,.!?\s]", "", text)
    compact = compact.replace("明显", "")
    if len(compact) <= 2:
        return [compact] if compact else []
    return [compact[index : index + 2] for index in range(len(compact) - 1)]


def hamming_distance(left: int, right: int) -> int:
    """计算两个 64 位 SimHash 的汉明距离。"""
    return (left ^ right).bit_count()
