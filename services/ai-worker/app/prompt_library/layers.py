"""Data structures for the layered prompt library."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class AgentMode(str, Enum):
    DIAGNOSIS = "DIAGNOSIS"
    LEARNING = "LEARNING"


class PromptLayerName(str, Enum):
    ROLE = "role"
    INDUSTRY = "industry"
    REGION = "region"
    COMPLIANCE = "compliance"
    OUTPUT_FORMAT = "output_format"
    SOURCE_DISCLAIMER = "source_disclaimer"


@dataclass(frozen=True)
class PromptLayer:
    """A single composable prompt layer with its content."""

    name: PromptLayerName
    content: str
    mode: AgentMode | None = None  # None = mode-agnostic
    industry_id: str | None = None  # None = default (no industry override)
    region_id: str | None = None  # None = default (no region override)
