from __future__ import annotations

import hashlib
import math
import re


def embed_text(text: str, dimensions: int = 32) -> list[float]:
    if dimensions <= 0:
        raise ValueError("dimensions must be positive")

    tokens = _tokenize(text)
    if not tokens:
        return [0.0] * dimensions

    vector = [0.0] * dimensions
    for token in tokens:
        digest = hashlib.blake2b(token.encode("utf-8"), digest_size=16).digest()
        index = int.from_bytes(digest[:4], "big") % dimensions
        vector[index] += 1.0

    norm = math.sqrt(sum(value * value for value in vector))
    if norm == 0:
        return [0.0] * dimensions
    return [value / norm for value in vector]


def _tokenize(text: str) -> list[str]:
    compact = re.sub(r"[，。！？、；：,.!?\s]", "", text.lower())
    words = re.findall(r"[a-z0-9]+", compact)
    chars = [compact[index : index + 2] for index in range(max(len(compact) - 1, 0))]
    return words + chars
