from __future__ import annotations

"""轻量确定性文本向量。

当前实现使用哈希向量，适合本地测试和低成本验证；它不是高质量语义 embedding。
线上如接入真实 embedding 服务，可保持 embed_text() 的函数签名不变来替换内部实现。
"""

import hashlib
import math
import re


def embed_text(text: str, dimensions: int = 32) -> list[float]:
    """把文本映射为固定维度的归一化哈希向量。"""
    if dimensions <= 0:
        raise ValueError("dimensions must be positive")

    tokens = _tokenize(text)
    if not tokens:
        return [0.0] * dimensions

    vector = [0.0] * dimensions
    for token in tokens:
        # 使用稳定哈希把 token 投影到固定桶中，保证同一文本多次运行结果一致。
        digest = hashlib.blake2b(token.encode("utf-8"), digest_size=16).digest()
        index = int.from_bytes(digest[:4], "big") % dimensions
        vector[index] += 1.0

    norm = math.sqrt(sum(value * value for value in vector))
    if norm == 0:
        return [0.0] * dimensions
    return [value / norm for value in vector]


def _tokenize(text: str) -> list[str]:
    """中英混合分词：英文/数字词加中文二字片段。"""
    compact = re.sub(r"[，。！？、；：,.!?\s]", "", text.lower())
    words = re.findall(r"[a-z0-9]+", compact)
    chars = [compact[index : index + 2] for index in range(max(len(compact) - 1, 0))]
    return words + chars
