from app.agent import diagnose
from app.rag import KnowledgeItem, search_knowledge


KNOWLEDGE = [
    KnowledgeItem(
        id="k1",
        title="餐饮现金流",
        content="餐饮门店应核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。",
        source_url="seed://restaurant-cashflow",
        source_id="seed-baseline",
        weight=1.0,
        confidence=0.9,
        industry_id="general",
        region_id="cn-default",
    ),
    KnowledgeItem(
        id="k2",
        title="库存风险",
        content="库存周转天数过高会压占现金流，并倒逼渠道低价清货。",
        source_url="seed://inventory-risk",
        source_id="seed-baseline",
        weight=0.85,
        confidence=0.85,
        industry_id="general",
        region_id="cn-default",
    ),
]


def test_search_knowledge_reranks_by_keyword_and_weight():
    results = search_knowledge("餐饮门店平台佣金怎么诊断", KNOWLEDGE)

    assert results[0].id == "k1"
    assert results[0].score > 0


def test_diagnosis_returns_information_insufficient_without_evidence():
    result = diagnose("新能源门店补贴政策", knowledge=[])

    assert "信息不足" in result["answer"]
    assert result["sources"] == []
    assert result["confidence"] == "LOW"
    assert "免责声明" in result["disclaimer"]


def test_diagnosis_includes_sources_timeliness_confidence_and_disclaimer():
    result = diagnose("餐饮门店现金流怎么诊断", knowledge=KNOWLEDGE)

    assert "餐饮门店现金流怎么诊断" in result["answer"]
    assert result["sources"][0]["id"] == "k1"
    assert result["confidence"] == "MEDIUM"
    assert result["timeliness"] == "基于V1静态基线知识和已入库情报生成。"
    assert "不构成投资" in result["disclaimer"]
