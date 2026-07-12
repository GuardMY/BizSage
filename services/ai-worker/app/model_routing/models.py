"""Data structures for the multi-model routing system."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class ModelTier(str, Enum):
    """Four routing tiers ordered by task complexity."""

    LIGHT = "LIGHT"                # classification, extraction, keyword matching
    BALANCED = "BALANCED"          # standard Q&A and analysis
    ADVANCED = "ADVANCED"          # multi-step diagnosis, risk assessment
    TASK_SPECIFIC = "TASK_SPECIFIC"  # industry classification, NER, specialized sub-tasks


@dataclass
class ModelConfig:
    """Configuration for a single model within a tier."""

    tier: ModelTier
    provider: str           # e.g. "deepseek", "openai", "qwen"
    base_url: str
    api_key: str
    model_name: str
    timeout: int = 30
    max_tokens: int = 1024
    temperature: float = 0.3
    is_backup: bool = False


@dataclass
class RouteResult:
    """Result of a routed LLM call."""

    config: ModelConfig
    response_text: str
    tier_used: ModelTier
    failover_attempted: bool = False
    original_error: str | None = None
