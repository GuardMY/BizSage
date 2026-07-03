from __future__ import annotations

import hashlib
import re

RUMOR_KEYWORDS = ("网传", "未经证实", "小道消息", "据说")

FIXED_WEIGHTS = {
    "user-private": 1.0,
    "manual-local": 0.85,
    "public-page": 0.6,
    "mock-api": 0.35,
}


def govern_records(records: list[dict]) -> list[dict]:
    seen_urls: set[str] = set()
    seen_simhashes: list[int] = []
    governed: list[dict] = []

    for record in records:
        normalized = normalize_fields(record)
        if contains_rumor(normalized["title"]) or contains_rumor(normalized["content"]):
            continue
        url = normalized.get("url")
        if url and url in seen_urls:
            continue
        current_simhash = simhash(normalized["content"])
        if any(hamming_distance(current_simhash, previous) <= 3 for previous in seen_simhashes):
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
    normalized = dict(record)
    normalized["title"] = " ".join(str(normalized.get("title", "")).split())
    normalized["content"] = " ".join(str(normalized.get("content", "")).split())
    normalized["industry_id"] = normalized.get("industry_id") or "general"
    normalized["region_id"] = normalized.get("region_id") or "cn-default"
    normalized["link_id"] = normalized.get("link_id") or "unknown-link"
    normalized["source_id"] = normalized.get("source_id") or "unknown"
    return normalized


def contains_rumor(text: str) -> bool:
    return any(keyword in text for keyword in RUMOR_KEYWORDS)


def apply_fixed_weight(source_id: str) -> float:
    return FIXED_WEIGHTS.get(source_id, 0.1)


def content_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def simhash(text: str) -> int:
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
    compact = re.sub(r"[，。！？、；：,.!?\s]", "", text)
    compact = compact.replace("明显", "")
    if len(compact) <= 2:
        return [compact] if compact else []
    return [compact[index : index + 2] for index in range(len(compact) - 1)]


def hamming_distance(left: int, right: int) -> int:
    return (left ^ right).bit_count()
