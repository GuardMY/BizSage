"""ModelRouter — 4-tier model dispatcher with primary→backup→degrade failover chain."""

from __future__ import annotations

import os

from app.llm import LLMCallError, LLMNotConfiguredError
from app.model_routing.models import ModelConfig, ModelTier, RouteResult
from app.model_routing.providers import call_provider

# ── Tier degradation order (when a tier is completely unavailable) ──
_DEGRADE_ORDER: tuple[ModelTier, ...] = (
    ModelTier.ADVANCED,
    ModelTier.BALANCED,
    ModelTier.LIGHT,
)


class ModelRouter:
    """4-tier model router with failover chain.

    For each tier (LIGHT / BALANCED / ADVANCED / TASK_SPECIFIC), reads::

        <TIER>_PRIMARY_MODEL     <TIER>_PRIMARY_BASE_URL     <TIER>_PRIMARY_API_KEY
        <TIER>_BACKUP_MODEL      <TIER>_BACKUP_BASE_URL      <TIER>_BACKUP_API_KEY

    Falls back to legacy ``OPENAI_COMPATIBLE_*`` env vars when no tier-specific
    config is found, ensuring full backward compatibility.
    """

    def __init__(self) -> None:
        self._tiers: dict[ModelTier, list[ModelConfig]] = {}
        self._load_configs()

    # ── Configuration loading ──────────────────────────────────────────

    def _load_configs(self) -> None:
        """Load all tier configs from environment variables."""
        for tier in ModelTier:
            primary = self._read_tier_config(tier, is_backup=False)
            if primary:
                self._tiers.setdefault(tier, []).append(primary)
            backup = self._read_tier_config(tier, is_backup=True)
            if backup:
                self._tiers.setdefault(tier, []).append(backup)

        # If no tier configs at all, fall back to legacy OPENAI_COMPATIBLE_*
        if not self._tiers:
            legacy = self._build_legacy_fallback()
            if legacy:
                self._tiers[ModelTier.BALANCED] = [legacy]

    def _read_tier_config(
        self, tier: ModelTier, is_backup: bool
    ) -> ModelConfig | None:
        suffix = "BACKUP" if is_backup else "PRIMARY"
        model = os.getenv(f"{tier.value}_{suffix}_MODEL", "").strip()
        if not model:
            return None
        base_url = os.getenv(
            f"{tier.value}_{suffix}_BASE_URL",
            os.getenv("OPENAI_COMPATIBLE_BASE_URL", "https://api.deepseek.com/v1"),
        ).strip()
        api_key = os.getenv(
            f"{tier.value}_{suffix}_API_KEY",
            os.getenv("OPENAI_COMPATIBLE_API_KEY", ""),
        ).strip()
        provider = (
            os.getenv(f"{tier.value}_{suffix}_PROVIDER", "").strip()
            or tier.value.lower()
        )
        return ModelConfig(
            tier=tier,
            provider=provider,
            base_url=base_url,
            api_key=api_key,
            model_name=model,
            is_backup=is_backup,
        )

    def _build_legacy_fallback(self) -> ModelConfig | None:
        """Build a ModelConfig from legacy OPENAI_COMPATIBLE_* env vars."""
        api_key = os.getenv("OPENAI_COMPATIBLE_API_KEY", "").strip()
        if not api_key:
            return None
        return ModelConfig(
            tier=ModelTier.BALANCED,
            provider=os.getenv("LLM_PROVIDER", "deepseek"),
            base_url=os.getenv(
                "OPENAI_COMPATIBLE_BASE_URL", "https://api.deepseek.com/v1"
            ),
            api_key=api_key,
            model_name=os.getenv("OPENAI_COMPATIBLE_MODEL", "deepseek-chat"),
        )

    # ── Tier resolution ─────────────────────────────────────────────────

    def resolve_tier(
        self,
        task_hint: str | None = None,
        context: dict | None = None,
    ) -> ModelTier:
        """Resolve the appropriate tier for a task.

        Args:
            task_hint: explicit hint (``"light"``, ``"balanced"``, ``"advanced"``,
                       ``"task_specific"``).
            context: optional dict for auto-detection, e.g. ``{"intent_type": "RISK_ASSESSMENT"}``.

        Returns:
            The resolved ``ModelTier``.
        """
        # Explicit hint takes precedence
        if task_hint and task_hint.upper() in ModelTier.__members__:
            return ModelTier[task_hint.upper()]

        # Auto-detect from context
        if context:
            intent = context.get("intent_type", "")
            if intent in {"CLASSIFICATION", "EXTRACTION", "KEYWORD_MATCHING"}:
                return ModelTier.LIGHT
            if intent in {"INDUSTRY_CLASSIFICATION", "NER"}:
                return ModelTier.TASK_SPECIFIC
            if intent in {"RISK_ASSESSMENT", "ATTRIBUTION"}:
                return ModelTier.ADVANCED

        # Safe default
        default_tier = os.getenv("DEFAULT_TIER", "balanced").upper()
        if default_tier in ModelTier.__members__:
            return ModelTier[default_tier]
        return ModelTier.BALANCED

    # ── Main entry point ────────────────────────────────────────────────

    def call(
        self,
        messages: list[dict],
        task_hint: str | None = None,
        context: dict | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
        require_tier: ModelTier | None = None,
    ) -> RouteResult:
        """Route to appropriate model tier with failover chain.

        1. Resolve tier (explicit > auto-detect > default).
        2. Try primary model in the tier.
        3. On failure, try backup model in the same tier.
        4. If entire tier is exhausted, degrade to the next available tier.
        5. If all tiers fail, raise ``LLMCallError``.

        Returns:
            ``RouteResult`` with response text and routing metadata.
        """
        tier = require_tier or self.resolve_tier(task_hint, context)
        configs = self._tiers.get(tier) or self._tiers.get(ModelTier.BALANCED, [])

        if not configs:
            raise LLMNotConfiguredError(
                f"No model configured for tier {tier.value}"
            )

        primary_config: ModelConfig | None = None
        last_error: LLMCallError | None = None

        # ── Within-tier failover ──
        for config in configs:
            try:
                response = call_provider(config, messages, temperature, max_tokens)
                return RouteResult(
                    config=config,
                    response_text=response,
                    tier_used=tier,
                    failover_attempted=primary_config is not None,
                )
            except LLMNotConfiguredError:
                raise  # Don't retry — missing credentials won't fix themselves
            except LLMCallError as e:
                last_error = e
                if primary_config is None:
                    primary_config = config

        # ── Cross-tier degradation ──
        for degrade_tier in _DEGRADE_ORDER:
            if degrade_tier == tier:
                continue
            degrade_configs = self._tiers.get(degrade_tier, [])
            for config in degrade_configs:
                try:
                    response = call_provider(
                        config, messages, temperature, max_tokens
                    )
                    return RouteResult(
                        config=config,
                        response_text=response,
                        tier_used=degrade_tier,
                        failover_attempted=True,
                        original_error=str(last_error),
                    )
                except LLMCallError:
                    continue

        raise LLMCallError(
            f"All models failed for tier {tier.value} "
            f"(and degrade path exhausted): {last_error}"
        )
