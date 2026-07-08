"""Tests for the multi-model routing system."""

import pytest

from app.llm import LLMCallError, LLMNotConfiguredError
from app.model_routing.models import ModelConfig, ModelTier, RouteResult
from app.model_routing.router import ModelRouter


class TestModelTier:
    """Tests for tier resolution."""

    def test_resolve_explicit_tier(self):
        router = ModelRouter()
        tier = router.resolve_tier(task_hint="light")
        assert tier == ModelTier.LIGHT

    def test_resolve_explicit_tier_case_insensitive(self):
        router = ModelRouter()
        tier = router.resolve_tier(task_hint="ADVANCED")
        assert tier == ModelTier.ADVANCED

    def test_resolve_default_tier(self):
        router = ModelRouter()
        tier = router.resolve_tier(task_hint=None)
        assert tier == ModelTier.BALANCED

    def test_resolve_from_context_intent(self):
        router = ModelRouter()
        tier = router.resolve_tier(
            context={"intent_type": "RISK_ASSESSMENT"}
        )
        assert tier == ModelTier.ADVANCED

    def test_resolve_light_from_context(self):
        router = ModelRouter()
        tier = router.resolve_tier(
            context={"intent_type": "CLASSIFICATION"}
        )
        assert tier == ModelTier.LIGHT


class TestModelRouterLegacyFallback:
    """Tests that the router falls back to legacy OPENAI_COMPATIBLE_*."""

    def test_legacy_fallback_creates_balanced_config(self, monkeypatch):
        monkeypatch.setenv("OPENAI_COMPATIBLE_API_KEY", "sk-test-legacy")
        monkeypatch.setenv("OPENAI_COMPATIBLE_MODEL", "deepseek-v4")
        # Ensure no tier-specific vars are set
        for tier in ModelTier:
            monkeypatch.delenv(f"{tier.value}_PRIMARY_MODEL", raising=False)
        router = ModelRouter()
        assert ModelTier.BALANCED in router._tiers
        configs = router._tiers[ModelTier.BALANCED]
        assert len(configs) >= 1
        assert configs[0].model_name == "deepseek-v4"

    def test_no_config_raises_on_call(self, monkeypatch):
        """Without any API key at all, calling should raise."""
        monkeypatch.delenv("OPENAI_COMPATIBLE_API_KEY", raising=False)
        for tier in ModelTier:
            monkeypatch.delenv(f"{tier.value}_PRIMARY_MODEL", raising=False)
            monkeypatch.delenv(f"{tier.value}_PRIMARY_API_KEY", raising=False)
        router = ModelRouter()
        with pytest.raises(LLMNotConfiguredError):
            router.call(
                messages=[{"role": "user", "content": "test"}],
                task_hint="balanced",
            )


class TestModelRouterTierSpecific:
    """Tests with tier-specific env vars."""

    def test_tier_primary_config_loaded(self, monkeypatch):
        monkeypatch.setenv("BALANCED_PRIMARY_MODEL", "test-model")
        monkeypatch.setenv("BALANCED_PRIMARY_API_KEY", "sk-test")
        monkeypatch.setenv("BALANCED_PRIMARY_BASE_URL", "https://test.api/v1")
        router = ModelRouter()
        configs = router._tiers.get(ModelTier.BALANCED, [])
        assert len(configs) >= 1
        primary = configs[0]
        assert primary.model_name == "test-model"
        assert primary.api_key == "sk-test"
        assert not primary.is_backup

    def test_tier_backup_config_loaded(self, monkeypatch):
        monkeypatch.setenv("BALANCED_PRIMARY_MODEL", "primary-model")
        monkeypatch.setenv("BALANCED_PRIMARY_API_KEY", "sk-primary")
        monkeypatch.setenv("BALANCED_BACKUP_MODEL", "backup-model")
        monkeypatch.setenv("BALANCED_BACKUP_API_KEY", "sk-backup")
        router = ModelRouter()
        configs = router._tiers.get(ModelTier.BALANCED, [])
        assert len(configs) == 2
        assert configs[1].model_name == "backup-model"
        assert configs[1].is_backup


class TestRouteResult:
    """Tests for RouteResult dataclass."""

    def test_route_result_fields(self):
        config = ModelConfig(
            tier=ModelTier.BALANCED,
            provider="test",
            base_url="http://test",
            api_key="sk-test",
            model_name="test-model",
        )
        result = RouteResult(
            config=config,
            response_text="hello",
            tier_used=ModelTier.BALANCED,
        )
        assert result.response_text == "hello"
        assert result.tier_used == ModelTier.BALANCED
        assert not result.failover_attempted
