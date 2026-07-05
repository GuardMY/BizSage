from __future__ import annotations

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
        self.client = client or QdrantClient(location=":memory:")
        self.collection_name = collection_name
        self.dimensions = dimensions
        self._ensure_collection()

    def upsert_knowledge(self, items: list[KnowledgeItem]) -> None:
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
        if self.client.collection_exists(self.collection_name):
            return
        self.client.create_collection(
            collection_name=self.collection_name,
            vectors_config=models.VectorParams(
                size=self.dimensions,
                distance=models.Distance.COSINE,
            ),
        )
