from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.collectors import collect_form_business_data, collect_mock_api, collect_public_page
from app.governance import govern_records

app = FastAPI(title="BizSage Collector", version="0.1.0")


class FormBusinessDataRequest(BaseModel):
    title: str
    content: str
    industry_id: str = "general"
    region_id: str = "cn-default"
    link_id: str = "business-data"


class PublicPageRequest(BaseModel):
    url: str
    html: str
    industry_id: str = "general"
    region_id: str = "cn-default"
    link_id: str = "public-page"


class MockApiRequest(BaseModel):
    items: list[dict] = Field(default_factory=list)


class GovernRequest(BaseModel):
    records: list[dict] = Field(default_factory=list)


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


@app.post("/collect/form")
def collect_form(request: FormBusinessDataRequest) -> dict:
    return {"records": collect_form_business_data(request.model_dump())}


@app.post("/collect/public-page")
def collect_page(request: PublicPageRequest) -> dict:
    return {
        "records": collect_public_page(
            request.url,
            request.html,
            industry_id=request.industry_id,
            region_id=request.region_id,
            link_id=request.link_id,
        )
    }


@app.post("/collect/mock-api")
def collect_api(request: MockApiRequest) -> dict:
    return {"records": collect_mock_api(request.items)}


@app.post("/govern")
def govern(request: GovernRequest) -> dict:
    return {"records": govern_records(request.records)}
