"""ModelRouter：四档模型路由器，支持主模型→备模型→降档的失败转移链。"""

from __future__ import annotations

import os

from app.llm import LLMCallError, LLMNotConfiguredError
from app.model_routing.models import ModelConfig, ModelTier, RouteResult
from app.model_routing.providers import call_provider

# 某一档模型全部失败时的降档顺序；不包含 TASK_SPECIFIC，避免专用任务误降级。
_DEGRADE_ORDER: tuple[ModelTier, ...] = (
    ModelTier.ADVANCED,
    ModelTier.BALANCED,
    ModelTier.LIGHT,
)


class ModelRouter:
    """四档模型路由器。

    每档（LIGHT / BALANCED / ADVANCED / TASK_SPECIFIC）读取以下配置::

        <TIER>_PRIMARY_MODEL     <TIER>_PRIMARY_BASE_URL     <TIER>_PRIMARY_API_KEY
        <TIER>_BACKUP_MODEL      <TIER>_BACKUP_BASE_URL      <TIER>_BACKUP_API_KEY

    没有分档配置时退回 OPENAI_COMPATIBLE_*，保证旧部署可继续运行。
    """

    def __init__(self) -> None:
        self._tiers: dict[ModelTier, list[ModelConfig]] = {}
        self._load_configs()

    # 配置加载。

    def _load_configs(self) -> None:
        """从环境变量加载所有模型档位配置。"""
        for tier in ModelTier:
            primary = self._read_tier_config(tier, is_backup=False)
            if primary:
                self._tiers.setdefault(tier, []).append(primary)
            backup = self._read_tier_config(tier, is_backup=True)
            if backup:
                self._tiers.setdefault(tier, []).append(backup)

        # 没有任何分档配置时，使用旧版 OPENAI_COMPATIBLE_* 作为 BALANCED 档兜底。
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
        """从旧版 OPENAI_COMPATIBLE_* 环境变量构建兼容配置。"""
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

    # 档位解析。

    def resolve_tier(
        self,
        task_hint: str | None = None,
        context: dict | None = None,
    ) -> ModelTier:
        """为任务解析合适的模型档位。

        显式 task_hint 优先，其次按 context 中的任务意图自动判断，最后使用 DEFAULT_TIER。
        """
        # 显式 hint 优先，调用方可以强制轻量/平衡/高级档。
        if task_hint and task_hint.upper() in ModelTier.__members__:
            return ModelTier[task_hint.upper()]

        # 根据任务意图自动选择档位：抽取类轻量，风控/归因类更重。
        if context:
            intent = context.get("intent_type", "")
            if intent in {"CLASSIFICATION", "EXTRACTION", "KEYWORD_MATCHING"}:
                return ModelTier.LIGHT
            if intent in {"INDUSTRY_CLASSIFICATION", "NER"}:
                return ModelTier.TASK_SPECIFIC
            if intent in {"RISK_ASSESSMENT", "ATTRIBUTION"}:
                return ModelTier.ADVANCED

        # 安全默认值：环境变量无效时回到 BALANCED。
        default_tier = os.getenv("DEFAULT_TIER", "balanced").upper()
        if default_tier in ModelTier.__members__:
            return ModelTier[default_tier]
        return ModelTier.BALANCED

    # 主入口。

    def call(
        self,
        messages: list[dict],
        task_hint: str | None = None,
        context: dict | None = None,
        temperature: float | None = None,
        max_tokens: int | None = None,
        require_tier: ModelTier | None = None,
    ) -> RouteResult:
        """按档位路由模型调用，并执行失败转移。

        顺序：解析档位 → 同档主/备模型 → 跨档降级 → 全部失败抛出 LLMCallError。
        """
        tier = require_tier or self.resolve_tier(task_hint, context)
        configs = self._tiers.get(tier) or self._tiers.get(ModelTier.BALANCED, [])

        if not configs:
            raise LLMNotConfiguredError(
                f"No model configured for tier {tier.value}"
            )

        primary_config: ModelConfig | None = None
        last_error: LLMCallError | None = None

        # 同档失败转移：主模型失败后尝试备模型。
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
                raise  # 凭据缺失属于配置错误，重试或降级都无法自动修复。
            except LLMCallError as e:
                last_error = e
                if primary_config is None:
                    primary_config = config

        # 跨档降级：保持可用性，但在 RouteResult 中记录原始错误。
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
