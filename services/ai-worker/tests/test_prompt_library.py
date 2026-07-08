"""Tests for the layered prompt library."""

import os

import pytest

from app.prompt_library.assembler import PromptAssembler
from app.prompt_library.defaults import (
    ROLE_DIAGNOSIS,
    ROLE_LEARNING,
    COMPLIANCE,
    DEFAULT_LAYERS,
)
from app.prompt_library.layers import AgentMode, PromptLayerName


class TestPromptAssembler:
    """Tests for PromptAssembler — layer resolution and assembly."""

    def test_assemble_diagnosis_returns_non_empty_prompt(self):
        assembler = PromptAssembler()
        prompt = assembler.assemble(mode=AgentMode.DIAGNOSIS.value)
        assert len(prompt) > 100
        assert "BizSage" in prompt
        assert "经营诊断" in prompt or "诊断助手" in prompt

    def test_assemble_learning_returns_non_empty_prompt(self):
        assembler = PromptAssembler()
        prompt = assembler.assemble(mode=AgentMode.LEARNING.value)
        assert len(prompt) > 100
        assert "学习" in prompt

    def test_diagnosis_and_learning_prompts_differ(self):
        assembler = PromptAssembler()
        diag = assembler.assemble(mode=AgentMode.DIAGNOSIS.value)
        learn = assembler.assemble(mode=AgentMode.LEARNING.value)
        assert diag != learn

    def test_compliance_layer_present_in_both_modes(self):
        assembler = PromptAssembler()
        diag = assembler.assemble(mode=AgentMode.DIAGNOSIS.value)
        learn = assembler.assemble(mode=AgentMode.LEARNING.value)
        assert "合规约束" in diag
        assert "合规约束" in learn

    def test_industry_layer_included_when_specified(self):
        assembler = PromptAssembler()
        prompt = assembler.assemble(
            mode=AgentMode.DIAGNOSIS.value, industry_id="manufacturing"
        )
        assert "制造业" in prompt

    def test_industry_layer_omitted_for_unknown_industry(self):
        assembler = PromptAssembler()
        prompt = assembler.assemble(
            mode=AgentMode.DIAGNOSIS.value, industry_id="aerospace"
        )
        # Should still assemble successfully — just without industry layer
        assert "BizSage" in prompt

    def test_region_layer_included_when_specified(self):
        assembler = PromptAssembler()
        prompt = assembler.assemble(
            mode=AgentMode.DIAGNOSIS.value, region_id="cn-hongkong"
        )
        assert "香港" in prompt

    def test_env_var_overrides_role_layer(self, monkeypatch):
        monkeypatch.setenv("PROMPT_ROLE_DIAGNOSIS", "自定义角色：你是一个超级分析师。")
        assembler = PromptAssembler()
        prompt = assembler.assemble(mode=AgentMode.DIAGNOSIS.value)
        assert "超级分析师" in prompt
        assert "BizSage" not in prompt  # overridden

    def test_env_var_overrides_compliance_layer(self, monkeypatch):
        custom = "【自定义合规】不得做任何建议。"
        monkeypatch.setenv("PROMPT_COMPLIANCE", custom)
        assembler = PromptAssembler()
        prompt = assembler.assemble(mode=AgentMode.DIAGNOSIS.value)
        assert custom in prompt
        assert "合规约束" not in prompt  # overridden

    def test_env_var_overrides_output_format(self, monkeypatch):
        monkeypatch.setenv("PROMPT_OUTPUT_DIAGNOSIS", "使用JSON格式输出。")
        assembler = PromptAssembler()
        prompt = assembler.assemble(mode=AgentMode.DIAGNOSIS.value)
        assert "JSON" in prompt


class TestDefaultLayers:
    """Tests for default layer content completeness."""

    def test_all_six_layers_have_content_for_diagnosis(self):
        for layer in PromptLayerName:
            key = (layer, AgentMode.DIAGNOSIS.value, None, None)
            key_generic = (layer, None, None, None)
            has_mode_specific = key in DEFAULT_LAYERS and DEFAULT_LAYERS[key]
            has_generic = key_generic in DEFAULT_LAYERS and DEFAULT_LAYERS[key_generic]
            has_industry_specific = any(
                k[0] == layer and DEFAULT_LAYERS[k]
                for k in DEFAULT_LAYERS
            )
            assert has_mode_specific or has_generic or has_industry_specific, \
                f"Layer {layer} has no default for DIAGNOSIS"

    def test_role_defaults_not_empty(self):
        assert len(ROLE_DIAGNOSIS) > 20
        assert len(ROLE_LEARNING) > 20
        assert ROLE_DIAGNOSIS != ROLE_LEARNING

    def test_compliance_default_not_empty(self):
        assert len(COMPLIANCE) > 50
        assert "免责" in COMPLIANCE or "法律" in COMPLIANCE

    def test_industry_defaults_exist(self):
        assert any("制造" in DEFAULT_LAYERS.get(k, "") for k in DEFAULT_LAYERS
                   if k[0] == PromptLayerName.INDUSTRY)
