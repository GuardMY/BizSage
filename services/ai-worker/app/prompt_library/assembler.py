"""PromptAssembler — composes 6 prompt layers into a single system prompt at runtime.

Resolution order for each layer:
1. Environment variable override (PROMPT_*)
2. Mode+industry+region-specific default
3. Mode-specific default
4. Region-specific default
5. Generic default
6. Empty string (layer skipped)
"""

from __future__ import annotations

import os

from app.prompt_library.defaults import DEFAULT_LAYERS
from app.prompt_library.layers import AgentMode, PromptLayerName

# ── Layer ordering (assembly order = layers 1→6) ──
_LAYER_ORDER: list[PromptLayerName] = [
    PromptLayerName.ROLE,
    PromptLayerName.INDUSTRY,
    PromptLayerName.REGION,
    PromptLayerName.COMPLIANCE,
    PromptLayerName.OUTPUT_FORMAT,
    PromptLayerName.SOURCE_DISCLAIMER,
]

# ── Env-var name mapping: (layer, mode, industry_id, region_id) → PROMPT_* key ──
# Only the most specific candidates are tried; others fall through to defaults.


class PromptAssembler:
    """Composes 6 prompt layers into a single system prompt at runtime."""

    def __init__(self) -> None:
        self._overrides: dict[str, str] = {}
        self._load_env_overrides()

    # ── Env overrides ──────────────────────────────────────────────────

    def _load_env_overrides(self) -> None:
        """Scan environment for PROMPT_* vars and cache them."""
        prefix = "PROMPT_"
        for key, value in os.environ.items():
            if key.startswith(prefix) and value.strip():
                self._overrides[key] = value.strip()

    # ── Layer resolution ───────────────────────────────────────────────

    def _env_key_for(
        self,
        layer: PromptLayerName,
        mode: str | None,
        industry_id: str | None,
        region_id: str | None,
    ) -> str | None:
        """Build the PROMPT_* env-var key for a given (layer, mode, industry, region)."""
        if layer == PromptLayerName.ROLE and mode:
            return f"PROMPT_ROLE_{mode}"
        if layer == PromptLayerName.INDUSTRY and industry_id:
            return f"PROMPT_INDUSTRY_{industry_id.upper().replace('-', '_').replace(' ', '_')}"
        if layer == PromptLayerName.REGION and region_id:
            return f"PROMPT_REGION_{region_id.upper().replace('-', '_').replace(' ', '_')}"
        if layer == PromptLayerName.COMPLIANCE:
            return "PROMPT_COMPLIANCE"
        if layer == PromptLayerName.OUTPUT_FORMAT and mode:
            return f"PROMPT_OUTPUT_{mode}"
        if layer == PromptLayerName.SOURCE_DISCLAIMER:
            return "PROMPT_SOURCE_DISCLAIMER"
        return None

    def _get_layer(
        self,
        layer: PromptLayerName,
        mode: str,
        industry_id: str | None,
        region_id: str | None,
    ) -> str:
        """Resolve layer content.

        Priority: env override > mode+industry+region default > mode default >
                  region default > generic default > empty string.
        """
        # 1. Environment override
        env_key = self._env_key_for(layer, mode, industry_id, region_id)
        if env_key and env_key in self._overrides:
            return self._overrides[env_key]

        # 2. Fall back to defaults — try progressively less specific keys
        candidates = [
            (layer, mode, industry_id, region_id),
            (layer, mode, industry_id, None),
            (layer, mode, None, region_id),
            (layer, None, industry_id, region_id),
            (layer, None, industry_id, None),
            (layer, mode, None, None),
            (layer, None, None, region_id),
            (layer, None, None, None),
        ]
        for candidate in candidates:
            if candidate in DEFAULT_LAYERS:
                return DEFAULT_LAYERS[candidate]

        return ""  # Layer not configured

    # ── Assembly ────────────────────────────────────────────────────────

    def assemble(
        self,
        mode: str = AgentMode.DIAGNOSIS.value,
        industry_id: str | None = None,
        region_id: str | None = None,
    ) -> str:
        """Assemble the full system prompt from all 6 layers.

        Args:
            mode: ``"DIAGNOSIS"`` or ``"LEARNING"``.
            industry_id: optional industry context override (e.g. ``"manufacturing"``).
            region_id: optional region context override (e.g. ``"cn-hongkong"``).

        Returns:
            Assembled system prompt string with layers joined by double newlines.
        """
        parts: list[str] = []
        for layer in _LAYER_ORDER:
            content = self._get_layer(layer, mode, industry_id, region_id)
            if content:
                parts.append(content)
        return "\n\n".join(parts)
