from __future__ import annotations

import html
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile


def collect_form_business_data(payload: dict) -> list[dict]:
    return [
        normalize_record(
            source_type="USER_PRIVATE_FORM",
            source_id="user-private",
            title=payload["title"],
            content=payload["content"],
            industry_id=payload["industry_id"],
            region_id=payload["region_id"],
            link_id=payload["link_id"],
            confidence=1.0,
            weight=1.0,
        )
    ]


def collect_excel_business_data(path: str | Path) -> list[dict]:
    rows = read_simple_xlsx(Path(path))
    if not rows:
        return []
    headers = [cell.strip() for cell in rows[0]]
    records = []
    for row in rows[1:]:
        values = dict(zip(headers, row, strict=False))
        if not values.get("title") or not values.get("content"):
            continue
        records.append(
            normalize_record(
                source_type="USER_PRIVATE_EXCEL",
                source_id="user-private",
                title=values["title"],
                content=values["content"],
                industry_id=values.get("industry_id", "general"),
                region_id=values.get("region_id", "cn-default"),
                link_id=values.get("link_id", "business-data"),
                confidence=1.0,
                weight=1.0,
            )
        )
    return records


def collect_public_page(
    url: str,
    html_text: str,
    *,
    industry_id: str,
    region_id: str,
    link_id: str,
) -> list[dict]:
    title = extract_title(html_text) or url
    body = extract_body_text(html_text)
    return [
        normalize_record(
            source_type="PUBLIC_PAGE",
            source_id="public-page",
            title=title,
            content=body,
            industry_id=industry_id,
            region_id=region_id,
            link_id=link_id,
            confidence=0.6,
            weight=0.6,
            url=url,
        )
    ]


def collect_mock_api(items: list[dict]) -> list[dict]:
    return [
        normalize_record(
            source_type="THIRD_PARTY_API_MOCK",
            source_id="mock-api",
            title=item["title"],
            content=item["content"],
            industry_id=item.get("industry_id", "general"),
            region_id=item.get("region_id", "cn-default"),
            link_id=item.get("link_id", "third-party"),
            confidence=float(item.get("confidence", 0.85)),
            weight=float(item.get("weight", 0.85)),
            url=item.get("url"),
        )
        for item in items
    ]


def normalize_record(
    *,
    source_type: str,
    source_id: str,
    title: str,
    content: str,
    industry_id: str,
    region_id: str,
    link_id: str,
    confidence: float,
    weight: float,
    url: str | None = None,
) -> dict:
    return {
        "source_type": source_type,
        "source_id": source_id,
        "title": " ".join(title.split()),
        "content": " ".join(content.split()),
        "industry_id": industry_id,
        "region_id": region_id,
        "link_id": link_id,
        "confidence": confidence,
        "weight": weight,
        "url": url,
    }


def read_simple_xlsx(path: Path) -> list[list[str]]:
    ns = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    with ZipFile(path) as zf:
        shared_xml = ET.fromstring(zf.read("xl/sharedStrings.xml"))
        shared_strings = [
            "".join(node.itertext())
            for node in shared_xml.findall("x:si", ns)
        ]
        sheet = ET.fromstring(zf.read("xl/worksheets/sheet1.xml"))
        rows = []
        for row in sheet.findall(".//x:row", ns):
            values = []
            for cell in row.findall("x:c", ns):
                value_node = cell.find("x:v", ns)
                if value_node is None:
                    values.append("")
                    continue
                if cell.attrib.get("t") == "s":
                    values.append(shared_strings[int(value_node.text or "0")])
                else:
                    values.append(value_node.text or "")
            rows.append(values)
    return rows


def extract_title(html_text: str) -> str:
    match = re.search(r"<title[^>]*>(.*?)</title>", html_text, flags=re.I | re.S)
    return html.unescape(match.group(1)).strip() if match else ""


def extract_body_text(html_text: str) -> str:
    body_match = re.search(r"<body[^>]*>(.*?)</body>", html_text, flags=re.I | re.S)
    raw = body_match.group(1) if body_match else html_text
    text = re.sub(r"<script.*?</script>|<style.*?</style>", " ", raw, flags=re.I | re.S)
    text = re.sub(r"<[^>]+>", " ", text)
    return " ".join(html.unescape(text).split())
