from app.governance import apply_fixed_weight, govern_records, simhash


def test_fixed_source_weights_follow_v1_order():
    assert apply_fixed_weight("user-private") == 1.0
    assert apply_fixed_weight("manual-local") == 0.85
    assert apply_fixed_weight("public-page") == 0.6
    assert apply_fixed_weight("mock-api") == 0.35
    assert apply_fixed_weight("unknown") == 0.1


def test_governance_filters_rumor_keywords():
    records = [
        {
            "title": "未经证实消息",
            "content": "网传某商圈全部关闭。",
            "url": "https://example.gov.cn/a",
            "source_id": "public-page",
            "industry_id": "general",
            "region_id": "cn-default",
            "link_id": "policy",
        }
    ]

    assert govern_records(records) == []


def test_governance_dedupes_by_url_and_simhash():
    records = [
        {
            "title": "库存风险",
            "content": "库存周转天数偏高会压占现金流。",
            "url": "https://example.gov.cn/a",
            "source_id": "manual-local",
            "industry_id": "general",
            "region_id": "cn-default",
            "link_id": "warehouse",
        },
        {
            "title": "库存风险重复",
            "content": "库存周转天数偏高会压占现金流。",
            "url": "https://example.gov.cn/a",
            "source_id": "public-page",
            "industry_id": "general",
            "region_id": "cn-default",
            "link_id": "warehouse",
        },
        {
            "title": "库存风险近似",
            "content": "库存周转天数偏高，会明显压占现金流。",
            "url": "https://example.gov.cn/b",
            "source_id": "public-page",
            "industry_id": "general",
            "region_id": "cn-default",
            "link_id": "warehouse",
        },
    ]

    governed = govern_records(records)

    assert len(governed) == 1
    assert governed[0]["weight"] == 0.85
    assert governed[0]["content_hash"]
    assert governed[0]["sim_hash"] == simhash("库存周转天数偏高会压占现金流。")
