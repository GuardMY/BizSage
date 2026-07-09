from __future__ import annotations

"""Qdrant 向量存储适配层。

对上层 RAG 屏蔽 Qdrant SDK 细节：负责集合初始化、知识点写入和检索结果
payload 规范化。点 ID 使用业务 ID 的 uuid5，确保重复同步时是幂等 upsert。
"""

import uuid
from dataclasses import asdict

from qdrant_client import QdrantClient, models

from app.embeddings import embed_text
from app.rag import KnowledgeItem


class QdrantVectorStore:
    def __init__(
        self,
        client: QdrantClient | None = None,
        *,
        collection_name: str = "knowledge",
        dimensions: int = 32,
    ) -> None:
        """初始化向量集合；默认使用内存 Qdrant 便于单元测试。"""
        self.client = client or QdrantClient(location=":memory:")
        self.collection_name = collection_name
        self.dimensions = dimensions
        self._ensure_collection()

    def upsert_knowledge(self, items: list[KnowledgeItem]) -> None:
        """写入知识向量；空列表直接返回，避免无意义的 Qdrant 调用。"""
        if not items:
            return

        points = [
            models.PointStruct(
                id=str(uuid.uuid5(uuid.NAMESPACE_URL, item.id)),
                vector=embed_text(f"{item.title} {item.content}", dimensions=self.dimensions),
                payload=asdict(item),
            )
            for item in items
        ]
        self.client.upsert(self.collection_name, points)

    def search(self, query_vector: list[float], limit: int = 5) -> list[dict]:
        """查询相似知识点，并把 SDK 响应压成 RAG 层需要的字典格式。"""
        response = self.client.query_points(
            collection_name=self.collection_name,
            query=query_vector,
            limit=limit,
            with_payload=True,
        )
        return [
            {
                "id": str(point.id),
                "score": float(point.score),
                "payload": dict(point.payload or {}),
            }
            for point in response.points
        ]

    def _ensure_collection(self) -> None:
        """集合不存在时按当前维度创建；已存在则复用。"""
        if self.client.collection_exists(self.collection_name):
            return
        self.client.create_collection(
            collection_name=self.collection_name,
            vectors_config=models.VectorParams(
                size=self.dimensions,
                distance=models.Distance.COSINE,
            ),
        )
