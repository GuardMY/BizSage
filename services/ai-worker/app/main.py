from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.agent import diagnose
from app.rag import KnowledgeItem, search_knowledge

app = FastAPI(title="BizSage AI Worker", version="0.1.0")

SEED_KNOWLEDGE = [
    KnowledgeItem(
        id="seed-restaurant-cashflow",
        title="餐饮门店现金流基础诊断",
        content="餐饮门店诊断应优先核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。",
        source_url="seed://v1/restaurant-cashflow",
        source_id="seed-baseline",
        weight=1.0,
        confidence=0.9,
        industry_id="general",
        region_id="cn-default",
    ),
    KnowledgeItem(
        id="seed-inventory-risk",
        title="实体供应链库存风险",
        content="库存周转天数、呆滞库存占比和上游账期会共同影响现金流风险。",
        source_url="seed://v1/inventory-risk",
        source_id="seed-baseline",
        weight=0.85,
        confidence=0.85,
        industry_id="general",
        region_id="cn-default",
    ),
]


class SearchRequest(BaseModel):
    query: str
    knowledge: list[dict] = Field(default_factory=list)


class DiagnoseRequest(BaseModel):
    question: str
    knowledge: list[dict] = Field(default_factory=list)


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


@app.post("/rag/search")
def search(request: SearchRequest) -> dict:
    knowledge = parse_knowledge(request.knowledge) or SEED_KNOWLEDGE
    return {"results": [result.__dict__ for result in search_knowledge(request.query, knowledge)]}


@app.post("/agent/diagnose")
def diagnose_endpoint(request: DiagnoseRequest) -> dict:
    knowledge = parse_knowledge(request.knowledge) or SEED_KNOWLEDGE
    return diagnose(request.question, knowledge=knowledge)


def parse_knowledge(items: list[dict]) -> list[KnowledgeItem]:
    return [
        KnowledgeItem(
            id=str(item.get("id")),
            title=item["title"],
            content=item["content"],
            source_url=item.get("source_url") or item.get("sourceUrl") or "",
            source_id=item.get("source_id") or item.get("sourceId") or "unknown",
            weight=float(item.get("weight", 0.85)),
            confidence=float(item.get("confidence", 0.85)),
            industry_id=item.get("industry_id") or item.get("industryId") or "general",
            region_id=item.get("region_id") or item.get("regionId") or "cn-default",
        )
        for item in items
    ]
