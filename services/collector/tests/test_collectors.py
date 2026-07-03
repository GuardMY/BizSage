from pathlib import Path
from zipfile import ZipFile

from app.collectors import (
    collect_excel_business_data,
    collect_form_business_data,
    collect_mock_api,
    collect_public_page,
)


def test_form_business_data_normalizes_required_fields():
    records = collect_form_business_data(
        {
            "title": "门店现金流",
            "content": "月营收 20 万，租金占比 18%，平台佣金升高。",
            "industry_id": "general",
            "region_id": "cn-default",
            "link_id": "sales-payment",
        }
    )

    assert records[0]["source_type"] == "USER_PRIVATE_FORM"
    assert records[0]["source_id"] == "user-private"
    assert records[0]["confidence"] == 1.0
    assert records[0]["weight"] == 1.0
    assert records[0]["industry_id"] == "general"


def test_excel_business_data_reads_simple_xlsx(tmp_path: Path):
    xlsx = tmp_path / "business.xlsx"
    write_simple_xlsx(
        xlsx,
        rows=[
            ["title", "content", "industry_id", "region_id", "link_id"],
            ["库存风险", "库存周转天数偏高。", "general", "cn-default", "warehouse"],
        ],
    )

    records = collect_excel_business_data(xlsx)

    assert records[0]["source_type"] == "USER_PRIVATE_EXCEL"
    assert records[0]["title"] == "库存风险"
    assert records[0]["content"] == "库存周转天数偏高。"


def test_public_page_fetcher_extracts_title_and_body():
    html = """
    <html><head><title>政策公告</title></head>
    <body><h1>政策公告</h1><p>本地商圈临时管控调整。</p></body></html>
    """

    records = collect_public_page(
        "https://example.gov.cn/policy/1",
        html,
        industry_id="general",
        region_id="cn-default",
        link_id="policy",
    )

    assert records[0]["source_type"] == "PUBLIC_PAGE"
    assert records[0]["url"] == "https://example.gov.cn/policy/1"
    assert "本地商圈临时管控调整" in records[0]["content"]
    assert records[0]["confidence"] == 0.6


def test_mock_api_provider_is_pluggable_source():
    records = collect_mock_api(
        [
            {
                "title": "平台佣金情报",
                "content": "佣金比例存在区域变化。",
                "industry_id": "general",
                "region_id": "cn-default",
                "link_id": "channel",
            }
        ]
    )

    assert records[0]["source_type"] == "THIRD_PARTY_API_MOCK"
    assert records[0]["source_id"] == "mock-api"
    assert records[0]["weight"] == 0.85


def write_simple_xlsx(path: Path, rows: list[list[str]]) -> None:
    shared = []
    cells = []
    for row_index, row in enumerate(rows, start=1):
        cell_xml = []
        for col_index, value in enumerate(row, start=1):
            shared.append(value)
            ref = f"{chr(64 + col_index)}{row_index}"
            cell_xml.append(f'<c r="{ref}" t="s"><v>{len(shared) - 1}</v></c>')
        cells.append(f'<row r="{row_index}">{"".join(cell_xml)}</row>')

    with ZipFile(path, "w") as zf:
        zf.writestr("[Content_Types].xml", '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>')
        zf.writestr("xl/workbook.xml", "<workbook/>")
        zf.writestr(
            "xl/sharedStrings.xml",
            '<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            + "".join(f"<si><t>{value}</t></si>" for value in shared)
            + "</sst>",
        )
        zf.writestr(
            "xl/worksheets/sheet1.xml",
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
            + "".join(cells)
            + "</sheetData></worksheet>",
        )
